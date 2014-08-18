package org.fruct.oss.ikm;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;

import org.apache.commons.io.IOUtils;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.utils.bind.BindHelperBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DataService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
	private static final Logger log = LoggerFactory.getLogger(DataService.class);

	public static final String BC_DATA_PATH_CHANGED = "org.fruct.oss.ikm.BC_DATA_PATH_CHANGED";

	private Binder binder = new Binder();

	private SharedPreferences pref;

	private String dataPath = null;
	private String oldPath = null;

	private Handler handler;
	private MigrateListener migrateListener;

	private List<DataListener> listeners = new ArrayList<DataListener>();

	private CountDownLatch dataListenerLatch;

	public DataService() {
    }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return DataService.START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		pref = PreferenceManager.getDefaultSharedPreferences(this);

		pref.registerOnSharedPreferenceChangeListener(this);

		dataPath = pref.getString(SettingsActivity.STORAGE_PATH, null);
		if (dataPath == null) {
			Utils.StorageDirDesc[] contentPaths = Utils.getPrivateStorageDirs(App.getContext());
			dataPath = contentPaths[0].path;
			pref.edit().putString(SettingsActivity.STORAGE_PATH, dataPath).apply();
		}

		handler = new Handler(Looper.getMainLooper());
	}

	@Override
	public void onDestroy() {
		pref.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	@Override
    public IBinder onBind(Intent intent) {
		return binder;
    }

	public void setMigrateListener(MigrateListener listener) {
		migrateListener = listener;
	}

	public void addDataListener(DataListener listener) {
		listeners.add(listener);

		Collections.sort(listeners, new Comparator<DataListener>() {
			@Override
			public int compare(DataListener lhs, DataListener rhs) {
				return lhs.getPriority() < rhs.getPriority() ? -1 : (lhs.getPriority() == rhs.getPriority() ? 0 : 1);
			}
		});
	}

	public void removeDataListener(DataListener listener) {
		listeners.remove(listener);
	}

	public synchronized void dataListenerReady() {
		if (dataListenerLatch != null) {
			dataListenerLatch.countDown();
		}
	}

	public String getDataPath() {
		return dataPath;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(SettingsActivity.STORAGE_PATH)) {
			String newDataPath = sharedPreferences.getString(key, null);
			if (newDataPath != null && !newDataPath.equals(dataPath)) {
				migrateData(newDataPath);
			}
		}
	}

	private void migrateData(final String newDataPath) {
		// Migration in progress
		if (oldPath != null) {
			notifyMigrateError();
			return;
		}

		new Thread() {
			{
				setName("Migration thread");
			}
			
			public void run() {
				if (!asyncMigrateData(newDataPath)) {
					return;
				}

				oldPath = dataPath;
				dataPath = newDataPath;

				int c = 0;
				for (final DataListener listener : listeners) {
					log.trace("Processing data listener {}", listener.getClass());
					notifyMigrateFile("Updating service " + listener.getClass(), c, listeners.size());

					dataListenerLatch = new CountDownLatch(1);
					handler.post(new Runnable() {
						@Override
						public void run() {
							listener.dataPathChanged(newDataPath);
						}
					});
					while (dataListenerLatch.getCount() > 0) {
						try {
							if (!dataListenerLatch.await(10, TimeUnit.SECONDS)) {
								// Ignore timeout
								break;
							}
						} catch (InterruptedException ignored) {
						}
					}
				}

				deleteDir(new File(oldPath));
				oldPath = null;

				notifyMigrateFinished();
			}
		}.start();
	}

	private boolean asyncMigrateData(String newDataPath) {
		File fromDir = new File(dataPath);
		File toDir = new File(newDataPath);

		if (!fromDir.isDirectory()) {
			return true;
		}

		int max = countDirectory(fromDir);

		try {
			copyDirectory(fromDir, toDir, 0, max);
			return true;
		} catch (IOException e) {
			log.error("Can't migrate data directory");
			deleteDir(toDir);
			notifyMigrateError();
			return false;
		}
	}

	private int countDirectory(File dir) {
		int count = 0;
		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				count ++;
			} else {
				count += countDirectory(file);
			}
		}
		return count;
	}

	private int copyDirectory(File fromDir, File toDir, int n, int max) throws IOException {
		int count = 0;
		for (File file : fromDir.listFiles()) {
			if (file.isFile()) {
				File newFile = new File(toDir, file.getName());

				FileInputStream inputStream = new FileInputStream(file);
				FileOutputStream outputStream = new FileOutputStream(newFile);

				notifyMigrateFile(newFile.getName(), n + count++, max);

				Utils.copyStream(inputStream, outputStream);

				IOUtils.closeQuietly(inputStream);
				IOUtils.closeQuietly(outputStream);
			} else if (file.isDirectory()) {
				File newDir = new File(toDir, file.getName());
				if (!newDir.mkdir() && !newDir.isDirectory()) {
					throw new IOException("Can't create directory " + newDir.getName());
				}

				count += copyDirectory(file, newDir, count, max);
			}
		}
		return count;
	}

	private boolean deleteDir(File dir) {
		if (dir == null || !dir.isDirectory())
			return false;

		boolean success = true;
		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				if (!file.delete())
					success = false;
			} else if (file.isDirectory()) {
				if (!deleteDir(file)) {
					success = false;
				}
			}
		}

		if (success) {
			return dir.delete();
		} else {
			return false;
		}
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


	public class Binder extends BindHelperBinder {
		@Override
		public Service getService() {
			return DataService.this;
		}
	}

	public interface MigrateListener {
		void migrateFile(String name, int n, int max);
		void migrateFinished();
		void migrateError();
	}

	public interface DataListener {
		void dataPathChanged(String newDataPath);
		int getPriority();
	}
}
