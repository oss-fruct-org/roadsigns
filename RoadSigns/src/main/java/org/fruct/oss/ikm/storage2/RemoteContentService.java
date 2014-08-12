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
import java.util.Iterator;
import java.util.List;
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

	private String currentStoragePath = null;

	private NetworkStorage networkStorage;
	private DirectoryStorage localStorage;

	private KeyValue digestCache;

	private ExecutorService executor = Executors.newSingleThreadExecutor();

	private List<ContentItem> localItems = new ArrayList<ContentItem>();
	private List<ContentItem> remoteItems = new ArrayList<ContentItem>();


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

		pref = PreferenceManager.getDefaultSharedPreferences(this);
		pref.registerOnSharedPreferenceChangeListener(this);

		currentStoragePath = getLocalStoragePath();

		networkStorage = new NetworkStorage(REMOTE_CONTENT_URLS);
		localStorage = new DirectoryStorage(digestCache, currentStoragePath);

		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					localStorage.updateContentList();
					localItems.addAll(localStorage.getContentList());
					notifyLocalListReady(localItems);

					networkStorage.updateContentList();
					remoteItems.addAll(networkStorage.getContentList());
					notifyRemoteListReady(remoteItems);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		digestCache = new KeyValue(this, "digestCache");
	}

	@Override
	public void onDestroy() {
		pref.unregisterOnSharedPreferenceChangeListener(this);

		digestCache.close();

		super.onDestroy();
	}

	private String getLocalStoragePath() {
		Utils.StorageDirDesc[] contentPaths = Utils.getPrivateStorageDirs(App.getContext());
		String contentPath = contentPaths[0].path;
		pref.edit().putString(SettingsActivity.STORAGE_PATH, contentPath).apply();
		return contentPath;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, final String key) {
		if (key.equals(SettingsActivity.STORAGE_PATH)) {
			final String newPath = pref.getString(key, null);
			if (newPath == null)
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

	public void remoteListener(Listener listener) {
		listeners.remove(listener);
	}

	private void asyncMigrateData(String newPath) {
		try {
			localStorage.migrate(newPath, new DirectoryStorage.MigrationListener() {
				@Override
				public void fileCopying(String name, int n, int max) {
					Intent intent = new Intent(BC_MIGRATE);

					Bundle extra = new Bundle();
					extra.putString("name", name);
					extra.putInt("n", n);
					extra.putInt("max", max);

					LocalBroadcastManager.getInstance(RemoteContentService.this).sendBroadcast(intent);
				}
			});

			LocalBroadcastManager.getInstance(RemoteContentService.this).sendBroadcast(new Intent(BC_MIGRATE_COMPLETE));
		} catch (IOException e) {
			LocalBroadcastManager.getInstance(RemoteContentService.this).sendBroadcast(new Intent(BC_MIGRATE_ERROR));
			pref.edit().putString(SettingsActivity.STORAGE_PATH, localStorage.getPath()).apply();
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
