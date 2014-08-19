package org.fruct.oss.ikm.storage;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import org.fruct.oss.ikm.DataService;
import org.fruct.oss.ikm.DigestInputStream;
import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.OnlineContentActivity;
import org.fruct.oss.ikm.ProgressInputStream;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.fragment.MapFragment;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.utils.bind.BindHelper;
import org.fruct.oss.ikm.utils.bind.BindHelperBinder;
import org.fruct.oss.ikm.utils.bind.BindSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public class RemoteContentService extends Service implements DataService.DataListener {
	private static final Logger log = LoggerFactory.getLogger(RemoteContentService.class);
	private static final int NOTIFY_ID = 11;

	public static final String[] REMOTE_CONTENT_URLS = {
			"http://oss.fruct.org/projects/roadsigns/root.xml"};
	public static final int REPORT_INTERVAL = 100000;

	private Handler handler = new Handler(Looper.getMainLooper());

	private final LocalBinder binder = new LocalBinder();
	private SharedPreferences pref;

	private final List<Listener> listeners = new ArrayList<Listener>();

	private NetworkStorage networkStorage;
	private WritableDirectoryStorage mainLocalStorage;
	private List<ContentStorage> localStorages = new ArrayList<ContentStorage>();

	private KeyValue digestCache;

	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private RegionsTask regionsTask;
	private final List<Future<?>> downloadTasks = new ArrayList<Future<?>>();

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
		mainLocalStorage = new WritableDirectoryStorage(digestCache, currentStoragePath + "/storage");

		String[] additionalStoragePaths = Utils.getExternalDirs(this);
		for (String path : additionalStoragePaths) {
			DirectoryStorage storage = new DirectoryStorage(digestCache, path);
			localStorages.add(storage);
		}

		refresh();
	}

	@Override
	public void onDestroy() {
		digestCache.close();

		if (dataService != null) {
			dataService.removeDataListener(this);
		}

		executor.shutdownNow();
		if (regionsTask != null) {
			regionsTask.cancel(true);
			regionsTask = null;
		}

		log.debug("RemoteContentService destroyed");
		BindHelper.autoUnbind(this, this);
		super.onDestroy();
	}

	public void downloadItem(ContentItem contentItem) {
		final NetworkContentItem remoteItem = (NetworkContentItem) contentItem;

		Future<?> downloadTask = executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					PendingIntent contentIntent = PendingIntent.getActivity(RemoteContentService.this, 0,
							new Intent(RemoteContentService.this, MainActivity.class), 0);

					NotificationCompat.Builder builder = new NotificationCompat.Builder(RemoteContentService.this);
					builder.setSmallIcon(R.drawable.ic_launcher)
							.setContentTitle("Road Signs")
							.setContentText("Downloading " + remoteItem.getName())
							.setContentIntent(contentIntent);

					startForeground(NOTIFY_ID, builder.build());
					asyncDownloadItem(remoteItem);
				} finally {
					stopForeground(true);
				}
			}
		});

		synchronized (downloadTasks) {
			downloadTasks.add(downloadTask);
			for (Iterator<Future<?>> iterator = downloadTasks.iterator(); iterator.hasNext(); ) {
				Future<?> task = iterator.next();
				if (task.isDone()) {
					iterator.remove();
				}
			}
		}
	}

	public void addListener(Listener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	public void removeListener(Listener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
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
					List<ContentItem> localItems = new ArrayList<ContentItem>();

					mainLocalStorage.updateContentList();
					localItems.addAll(mainLocalStorage.getContentList());

					for (ContentStorage storage : localStorages) {
						try {
							storage.updateContentList();
							localItems.addAll(storage.getContentList());
						} catch (IOException e) {
							log.warn("Can't load additional local storage");
						}
					}

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

			ContentItem item = mainLocalStorage.storeContentItem(remoteItem, inputStream);

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
			tryActivateItem(item);
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
				synchronized (listeners) {
					for (Listener listener : listeners) {
						listener.remoteListReady(items);
					}
				}
			}
		});
	}

	private void notifyDownloadStateUpdated(final ContentItem item, final int downloaded, final int max) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {

					for (Listener listener : listeners) {
						listener.downloadStateUpdated(item, downloaded, max);
					}
				}
			}
		});
	}

	private void notifyDownloadFinished(final ContentItem localItem, final ContentItem remoteItem) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					for (Listener listener : listeners) {
						listener.downloadFinished(localItem, remoteItem);
					}
				}
			}
		});
	}

	private void notifyDownloadInterrupted(final ContentItem remoteItem) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					for (Listener listener : listeners) {
						listener.downloadInterrupted(remoteItem);
					}
				}
			}
		});
	}

	private void notifyErrorDownload(final ContentItem remoteItem, final IOException ex) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {

					for (Listener listener : listeners) {
						listener.errorDownloading(remoteItem, ex);
					}
				}
			}
		});
	}

	private void notifyErrorInitializing(final IOException ex) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					for (Listener listener : listeners) {
						listener.errorInitializing(ex);
					}
				}
			}
		});
	}

	public void interrupt() {
		synchronized (downloadTasks) {
			for (Future<?> task : downloadTasks) {
				task.cancel(true);
			}
			downloadTasks.clear();
		}
	}

	@Override
	public void dataPathChanged(final String newDataPath) {
		if (mainLocalStorage != null) {
			executor.shutdownNow();
			if (regionsTask != null) {
				regionsTask.cancel(true);
			}

			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					try {
						executor.awaitTermination(10, TimeUnit.SECONDS);
						if (regionsTask != null) {
							regionsTask.get();
						}
					} catch (InterruptedException ignore) {
					} catch (ExecutionException ignored) {
					}

					executor = null;
					regionsTask = null;

					return null;
				}

				@Override
				protected void onPostExecute(Void aVoid) {
					executor = Executors.newSingleThreadExecutor();

					currentStoragePath = newDataPath;
					mainLocalStorage.migrate(newDataPath + "/storage");
					dataService.dataListenerReady();
				}
			}.execute();
		}
	}

	@Override
	public int getPriority() {
		return 0;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void activateRegionByLocation(double latitude, double longitude) {
		RegionsTask regionsTask = new RegionsTask() {
			@Override
			protected void onPostExecute(String id) {
				if (id != null) {
					activateRegionById(id);
				}
			}
		};

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			regionsTask.execute(new RegionsTask.RegionTasksArg(localItems, latitude, longitude));
		} else {
			regionsTask.executeOnExecutor(executor,
					new RegionsTask.RegionTasksArg(localItems, latitude, longitude));
		}
	}

	public void activateRegionById(String regionId) {
		pref.edit().putString(SettingsActivity.CURRENT_REGION, regionId).apply();
		pref.edit().remove(SettingsActivity.NAVIGATION_DATA).apply();
		pref.edit().remove(SettingsActivity.OFFLINE_MAP).apply();

		for (ContentItem contentItem : localItems) {
			tryActivateItem(contentItem);
		}
	}

	private void tryActivateItem(ContentItem contentItem) {
		String regionId = pref.getString(SettingsActivity.CURRENT_REGION, null);
		if (regionId == null) {
			return;
		}

		if (contentItem.getRegionId().equals(regionId)) {
			if (contentItem.getType().equals("graphhopper-map")) {
				pref.edit().putString(SettingsActivity.NAVIGATION_DATA, contentItem.getName()).apply();
			} else if (contentItem.getType().equals("mapsforge-map")) {
				pref.edit().putString(SettingsActivity.OFFLINE_MAP, contentItem.getName()).apply();
			}
		}
	}

	public boolean deleteContentItem(ContentItem contentItem) {
		if (!localItems.contains(contentItem)
				|| !contentItem.getStorage().equals(mainLocalStorage.getStorageName())) {
			// Can delete only from main storage
			return true;
		}

		if (contentItem.getType().equals("graphhopper-map")) {
			String map = pref.getString(SettingsActivity.NAVIGATION_DATA, null);
			if (contentItem.getName().equals(map)) {
				return false;
			}

			mainLocalStorage.deleteContentItem(contentItem);
			localItems.remove(contentItem);
			notifyLocalListReady(localItems);
		} else if (contentItem.getType().equals("mapsforge-map")) {
			String map = pref.getString(SettingsActivity.OFFLINE_MAP, null);
			if (contentItem.getName().equals(map)) {
				return false;
			}

			mainLocalStorage.deleteContentItem(contentItem);
			localItems.remove(contentItem);
			notifyLocalListReady(localItems);
		}

		return true;
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
