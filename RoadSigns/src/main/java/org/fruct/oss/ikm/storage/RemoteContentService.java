package org.fruct.oss.ikm.storage;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Region;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;

import org.fruct.oss.ikm.DataService;
import org.fruct.oss.ikm.DigestInputStream;
import org.fruct.oss.ikm.ProgressInputStream;
import org.fruct.oss.ikm.utils.bind.BindHelper;
import org.fruct.oss.ikm.utils.bind.BindHelperBinder;
import org.fruct.oss.ikm.utils.bind.BindSetter;
import org.osmdroid.api.Polyline;
import org.osmdroid.bonuspack.overlays.Polygon;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class RemoteContentService extends Service implements DataService.DataListener {
	private static final Logger log = LoggerFactory.getLogger(RemoteContentService.class);

	public static final String[] REMOTE_CONTENT_URLS = {
			"http://kappa.cs.petrsu.ru/~ivashov/mordor.xml",
			"http://example.com/non-working-content-url.xml",
			"http://oss.fruct.org/projects/roadsigns/root.xml",
			"https://dl.dropboxusercontent.com/sh/x3qzpqcrqd7ftys/8uy2pMvBFW/all-root.xml"};
	public static final int REPORT_INTERVAL = 100000;

	private Handler handler = new Handler(Looper.getMainLooper());

	private final LocalBinder binder = new LocalBinder();
	private SharedPreferences pref;

	private List<Listener> listeners = new ArrayList<Listener>();

	private NetworkStorage networkStorage;
	private DirectoryStorage localStorage;

	private KeyValue digestCache;

	private ExecutorService executor = Executors.newSingleThreadExecutor();

	private volatile List<ContentItem> localItems = new ArrayList<ContentItem>();
	private volatile List<ContentItem> remoteItems = new ArrayList<ContentItem>();
	private final Map<String, ContentItem> itemsByName = new HashMap<String, ContentItem>();

	private DataService dataService;

	private String currentStoragePath;

	public RemoteContentService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return Service.START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		log.debug("RemoteContentService created");

		pref = PreferenceManager.getDefaultSharedPreferences(this);
		BindHelper.autoBind(this, this);
	}

	@BindSetter
	public void setDataService(DataService service) {
		this.dataService = service;

		if (service == null) {
			return;
		}

		dataService.addDataListener(this);
		digestCache = new KeyValue(this, "digestCache");

		networkStorage = new NetworkStorage(REMOTE_CONTENT_URLS);

		currentStoragePath = dataService.getDataPath();
		localStorage = new DirectoryStorage(digestCache, currentStoragePath + "/storage");
		refresh();
	}

	@Override
	public void onDestroy() {
		digestCache.close();

		if (dataService != null) {
			dataService.removeDataListener(this);
		}

		log.debug("RemoteContentService destroyed");
		BindHelper.autoUnbind(this, this);
		super.onDestroy();
	}

	public void downloadItem(ContentItem contentItem) {
		final NetworkContentItem remoteItem = (NetworkContentItem) contentItem;

		executor.execute(new Runnable() {
			@Override
			public void run() {
				asyncDownloadItem(remoteItem);
			}
		});
	}

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}


	public List<ContentItem> getLocalItems() {
		return localItems;
	}

	public List<ContentItem> getRemoteItems() {
		return remoteItems;
	}

	public ContentItem getContentItem(String name) {
		synchronized (itemsByName) {
			return itemsByName.get(name);
		}
	}

	public String getFilePath(ContentItem item) {
		if (item == null)
			return null;

		if (item instanceof DirectoryContentItem) {
			return ((DirectoryContentItem) item).getPath();
		} else {
			throw new IllegalArgumentException("Content item doesn't contain any path");
		}
	}

	public void refresh() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					localStorage.updateContentList();
					List<ContentItem> localItems = new ArrayList<ContentItem>();
					localItems.addAll(localStorage.getContentList());
					RemoteContentService.this.localItems = localItems;

					updateItemsByName();
					notifyLocalListReady(localItems);
				} catch (IOException e) {
					notifyErrorInitializing(e);
				}

				try {
					RemoteContentService.this.remoteItems = Collections.emptyList();
					networkStorage.updateContentList();
					List<ContentItem> remoteItems = new ArrayList<ContentItem>();
					remoteItems.addAll(networkStorage.getContentList());
					RemoteContentService.this.remoteItems = remoteItems;
				} catch (IOException e) {
					notifyErrorInitializing(e);
				} finally {
					notifyRemoteListReady(remoteItems);
				}
			}
		});
	}

	private void updateItemsByName() {
		synchronized (itemsByName) {
			itemsByName.clear();

			for (ContentItem localItem : localItems) {
				itemsByName.put(localItem.getName(), localItem);
			}
		}
	}

	private void asyncDownloadItem(final NetworkContentItem remoteItem) {
		ContentConnection conn = null;
		try {
			conn = networkStorage.loadContentItem(remoteItem.getUrl());

			InputStream inputStream = new ProgressInputStream(conn.getStream(), remoteItem.getDownloadSize(),
					REPORT_INTERVAL, new ProgressInputStream.ProgressListener() {
				@Override
				public void update(int current, int max) {
					notifyDownloadStateUpdated(remoteItem, current, max);
				}
			});

			// Setup gzip compression
			if ("gzip".equals(remoteItem.getCompression())) {
				log.info("Using gzip compression");
				inputStream = new GZIPInputStream(inputStream);
			}

			// Setup content validation
			try {
				// TODO: de-hardcode hash algorithm
				inputStream = new DigestInputStream(inputStream, "sha1", remoteItem.getHash());
			} catch (NoSuchAlgorithmException e) {
				log.warn("Unsupported hash algorithm");
			}

			ContentItem item = localStorage.storeContentItem(remoteItem, inputStream);

			// Update existing item
			boolean found = false;
			for (int i = 0, localItemsSize = localItems.size(); i < localItemsSize; i++) {
				ContentItem exItem = localItems.get(i);
				if (exItem.getName().equals(item.getName())) {
					localItems.set(i, item);
					found = true;
					break;
				}
			}

			if (!found) {
				localItems.add(item);
			}

			updateItemsByName();
			notifyLocalListReady(localItems);
			notifyDownloadFinished(item, remoteItem);
		} catch (InterruptedIOException ex) {
			notifyDownloadInterrupted(remoteItem);
		} catch (IOException e) {
			notifyErrorDownload(remoteItem, e);
		} finally {
			if (conn != null) {
				conn.close();
			}
		}
	}

	private void notifyLocalListReady(final List<ContentItem> items) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.localListReady(items);
				}
			}
		});
	}

	private void notifyRemoteListReady(final List<ContentItem> items) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.remoteListReady(items);
				}
			}
		});
	}

	private void notifyDownloadStateUpdated(final ContentItem item, final int downloaded, final int max) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.downloadStateUpdated(item, downloaded, max);
				}
			}
		});
	}

	private void notifyDownloadFinished(final ContentItem localItem, final ContentItem remoteItem) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.downloadFinished(localItem, remoteItem);
				}
			}
		});
	}

	private void notifyDownloadInterrupted(final ContentItem remoteItem) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.downloadInterrupted(remoteItem);
				}
			}
		});
	}

	private void notifyErrorDownload(final ContentItem remoteItem, final IOException ex) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.errorDownloading(remoteItem, ex);
				}
			}
		});
	}

	private void notifyErrorInitializing(final IOException ex) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.errorInitializing(ex);
				}
			}
		});
	}

	public void interrupt() {
		throw new IllegalStateException("Not supported yet");
	}

	@Override
	public void dataPathChanged(String newDataPath) {
		if (localStorage != null) {
			currentStoragePath = newDataPath;
			localStorage.migrate(newDataPath + "/storage");
			dataService.dataListenerReady();
		}
	}

	@Override
	public int getPriority() {
		return 0;
	}

	public void findRegion(double latitude, double longitude) {
		RegionsTask regionsTask = new RegionsTask() {
			@Override
			protected void onPostExecute(String s) {
				log.debug("REGION " + s);
			}
		};
		regionsTask.execute(new RegionsTask.RegionTasksArg(localItems, latitude, longitude));
	}

	public interface Listener {
		void localListReady(List<ContentItem> list);
		void remoteListReady(List<ContentItem> list);

		void downloadStateUpdated(ContentItem item, int downloaded, int max);
		void downloadFinished(ContentItem localItem, ContentItem remoteItem);

		void errorDownloading(ContentItem item, IOException e);
		void errorInitializing(IOException e);
		void downloadInterrupted(ContentItem item);
	}

	public class LocalBinder extends BindHelperBinder {
		@Override
		public RemoteContentService getService() {
			return RemoteContentService.this;
		}
	}
}
