package org.fruct.oss.ikm.storage2;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.DigestInputStream;
import org.fruct.oss.ikm.ProgressInputStream;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.storage.IContentConnection;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.utils.bind.BindHelperBinder;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class RemoteContentService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
	private static final Logger log = LoggerFactory.getLogger(RemoteContentService.class);

	public static final String[] REMOTE_CONTENT_URLS = {
			"http://kappa.cs.petrsu.ru/~ivashov/mordor.xml",
			"http://example.com/non-working-content-url.xml",
			"http://oss.fruct.org/projects/roadsigns/root.xml",
			"https://dl.dropboxusercontent.com/sh/x3qzpqcrqd7ftys/8uy2pMvBFW/all-root.xml"};

	public static final String BC_MIGRATE = "org.fruct.oss.ikm.BC_MIGRATE_STARTED";
	public static final String BC_MIGRATE_ERROR = "org.fruct.oss.ikm.BC_MIGRATE_ERROR";
	public static final String BC_MIGRATE_COMPLETE = "org.fruct.oss.ikm.BC_MIGRATE_ERROR";

	private Handler handler = new Handler(Looper.getMainLooper());

	private final LocalBinder binder = new LocalBinder();
	private SharedPreferences pref;

	private List<Listener> listeners = new ArrayList<Listener>();
	private MigrateListener migrateListener;

	private String currentStoragePath = null;

	private NetworkStorage networkStorage;
	private DirectoryStorage localStorage;

	private KeyValue digestCache;

	private ExecutorService executor = Executors.newSingleThreadExecutor();

	private volatile List<ContentItem> localItems = new ArrayList<ContentItem>();
	private volatile List<ContentItem> remoteItems = new ArrayList<ContentItem>();
	private final Map<String,ContentItem> itemsByName = new HashMap<String, ContentItem>();

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

		currentStoragePath = getLocalStoragePath();

		digestCache = new KeyValue(this, "digestCache");
		networkStorage = new NetworkStorage(REMOTE_CONTENT_URLS);
		localStorage = new DirectoryStorage(digestCache, currentStoragePath);

		pref.registerOnSharedPreferenceChangeListener(this);

		refresh();
	}

	@Override
	public void onDestroy() {
		pref.unregisterOnSharedPreferenceChangeListener(this);

		digestCache.close();

		log.debug("RemoteContentService destroyed");
		super.onDestroy();
	}

	private String getLocalStoragePath() {
		String contentPath = pref.getString(SettingsActivity.STORAGE_PATH, null);

		if (contentPath == null) {
			Utils.StorageDirDesc[] contentPaths = Utils.getPrivateStorageDirs(App.getContext());
			contentPath = contentPaths[0].path;
			pref.edit().putString(SettingsActivity.STORAGE_PATH, contentPath).apply();
		}
		return contentPath;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, final String key) {
		if (key.equals(SettingsActivity.STORAGE_PATH)) {
			final String newPath = pref.getString(key, null);
			if (newPath == null || newPath.equals(localStorage.getPath()))
				return;

			executor.execute(new Runnable() {
				@Override
				public void run() {
					asyncMigrateData(newPath);
				}
			});
		}
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

	public void setMigrateListener(MigrateListener listener) {
		migrateListener = listener;
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

	private void asyncMigrateData(String newPath) {
		try {
			localStorage.migrate(newPath, new DirectoryStorage.MigrationListener() {
				@Override
				public void fileCopying(String name, int n, int max) {
					notifyMigrateFile(name, n, max);
				}
			});

			notifyMigrateFinished();
		} catch (IOException e) {
			pref.edit().putString(SettingsActivity.STORAGE_PATH, localStorage.getPath()).apply();
			notifyMigrateError();
		}
	}

	private void asyncDownloadItem(final NetworkContentItem remoteItem) {
		ContentConnection conn = null;
		try {
			conn = networkStorage.loadContentItem(remoteItem.getUrl());

			InputStream inputStream = new ProgressInputStream(conn.getStream(), remoteItem.getDownloadSize(),
					10000, new ProgressInputStream.ProgressListener() {
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

	private void notifyMigrateFile(final String name, final int n, final int max) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (migrateListener != null) {
					migrateListener.migrateFile(name, n, max);
				}
			}
		});
	}

	private void notifyMigrateFinished() {
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (migrateListener != null) {
					migrateListener.migrateFinished();
				}
			}
		});
	}

	private void notifyMigrateError() {
		handler.post(new Runnable() {
			@Override
			public void run() {
				if (migrateListener != null) {
					migrateListener.migrateError();
				}
			}
		});
	}

	public void interrupt() {
		throw new IllegalStateException("Not supported yet");
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

	public interface MigrateListener {
		void migrateFile(String name, int n, int max);
		void migrateFinished();
		void migrateError();
	}

	public class LocalBinder extends BindHelperBinder {
		@Override
		public RemoteContentService getService() {
			return RemoteContentService.this;
		}
	}
}
