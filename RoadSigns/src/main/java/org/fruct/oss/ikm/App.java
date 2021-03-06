package org.fruct.oss.ikm;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiscCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.download.BaseImageDownloader;

import org.fruct.oss.ikm.points.PointsAccess;
import org.fruct.oss.ikm.utils.Utils;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.osmdroid.tileprovider.BitmapPool;
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.WeakHashMap;

public class App extends Application {
	public interface Clearable {
		void clear();
	}

	private static Logger log = LoggerFactory.getLogger(App.class);

	private static Context context;
	private static App app;
	private PointsAccess pointsAccess;

	private final String[] OLD_DEFAULT_GETS_SERVERS = { "http://oss.fruct.org/projects/gets/service" };

	private static WeakHashMap<Clearable, Object> clearables = new WeakHashMap<>();

	@Override
	public void onCreate() {
		super.onCreate();
	
		App.context = getApplicationContext();
		App.app = this;

		PreferenceManager.setDefaultValues(App.context, R.xml.preferences_rs, false);

		updatePreferences();

		AndroidGraphicFactory.createInstance(this);
		hackOsmdroidCache();

		// Setup image loader
		File imageCacheDir = new File(context.getCacheDir(), "image-cache");
		setupImageLoader(imageCacheDir);

		// Setup points database
		pointsAccess = new PointsAccess(context);
	}

	private void updatePreferences() {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
		String currentGetsServer = pref.getString(SettingsActivity.GETS_SERVER, SettingsActivity.GETS_SERVER_DEFAULT);

		if (currentGetsServer == null) {
			// It should not happen due to setDefaultValues
			return;
		}

		// Only default values will be updated
		for (String oldServer : OLD_DEFAULT_GETS_SERVERS) {
			if (currentGetsServer.startsWith(oldServer)) {
				pref.edit().putString(SettingsActivity.GETS_SERVER, SettingsActivity.GETS_SERVER_DEFAULT).apply();
				Toast.makeText(context, getString(R.string.str_gets_server_updated), Toast.LENGTH_SHORT).show();
				return;
			}
		}
	}

	private void hackOsmdroidCache() {
		// XXX: hack osmdroid cache path. This should must be removed as soon as osmdroid allows customisation
		File newCacheFile = new File(context.getCacheDir(), "osmdroid");
		File tilesFile = new File(newCacheFile, "tiles");

		newCacheFile.mkdirs();
		tilesFile.mkdirs();

		if (newCacheFile.isDirectory() && tilesFile.isDirectory()) {
			try {
				Class<?> cls = OpenStreetMapTileProviderConstants.class;

				Utils.setFinalStatic(cls.getField("OSMDROID_PATH"), newCacheFile);
				Utils.setFinalStatic(cls.getField("TILE_PATH_BASE"), tilesFile);

				//Utils.setFinalStatic(cls.getField("TILE_MAX_CACHE_SIZE_BYTES"), 10 * 1024 * 1024);
				//Utils.setFinalStatic(cls.getField("TILE_TRIM_CACHE_SIZE_BYTES"), 8 * 1024 * 1024);
			} catch (Exception ex) {
				// Let osmdroid write to external storage
				log.error("Can't hack osmdroid", ex);
			}
		}
	}

	private ImageLoader setupImageLoader(File imageCacheDir) {
		ImageLoader imageLoader = ImageLoader.getInstance();

		DisplayImageOptions defaultOptions = new DisplayImageOptions.Builder()
				.cacheInMemory(true)
				.cacheOnDisk(true)
				.build();

		ImageLoaderConfiguration imageLoaderConfiguration = new ImageLoaderConfiguration.Builder(this)
				.diskCacheSize(10 * 1024 * 1024)
				.diskCache(new UnlimitedDiscCache(imageCacheDir))
				.defaultDisplayImageOptions(defaultOptions)
				.imageDownloader(new BaseImageDownloader(this))
				.build();

		imageLoader.init(imageLoaderConfiguration);

		return imageLoader;
	}

	public static Context getContext() {
		if (context == null)
			throw new IllegalStateException("Application not initialized yet");
		
		return context;
	}

	public static App getInstance() {
		if (app == null)
			throw new IllegalStateException("Application not initialized yet");

		return App.app;
	}

	public PointsAccess getPointsAccess() {
		if (pointsAccess == null)
			throw new IllegalStateException("Application not initialized yet");

		return pointsAccess;
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		log.info("onLowMemory called");
		for (Clearable clearable : clearables.keySet()) {
			clearable.clear();
		}

		BitmapPool.getInstance().clearBitmapPool();
	}

	public static void addClearable(Clearable clearable) {
		clearables.put(clearable, log /* dummy object */);
	}
}
