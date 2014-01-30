package org.fruct.oss.ikm.service;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import com.graphhopper.util.PointList;
import com.graphhopper.util.Unzipper;

import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.poi.PointsManager.PointsListener;
import org.fruct.oss.ikm.service.LocationReceiver.Listener;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DirectionService extends Service implements PointsListener,
		DirectionManager.Listener, OnSharedPreferenceChangeListener, Listener {
	private static Logger log = LoggerFactory.getLogger(DirectionService.class);

	// Extras
	public static final String DIRECTIONS_RESULT = "org.fruct.oss.ikm.GET_DIRECTIONS_RESULT";
	public static final String CENTER = "org.fruct.oss.ikm.CENTER";
	public static final String LOCATION = "org.fruct.oss.ikm.LOCATION";

	// Broadcasts
	public static final String DIRECTIONS_READY = "org.fruct.oss.ikm.GET_DIRECTIONS_READY";
	public static final String LOCATION_CHANGED = "org.fruct.oss.ikm.LOCATION_CHANGED";
	
	private static final String MOCK_PROVIDER = "mock-provider";

	private DirectionManager dirManager;
	private IBinder binder = new DirectionBinder();
	
	private LocationReceiver locationReceiver;
	
	// Last query result
	private ArrayList<Direction> lastResultDirections;
	private GeoPoint lastResultCenter;
	private Location lastResultLocation;
	
	private Location lastLocation;

	private String oldNavigationPath;
	private String navigationPath;
	private String ghPath;

	private LocationIndexCache locationIndexCache;
	private Thread extractingThread;
	private SharedPreferences pref;

	public class DirectionBinder extends android.os.Binder {
		public DirectionService getService() {
			return DirectionService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();

		File internalDir = getFilesDir();
		assert internalDir != null;

		pref = getSharedPreferences("gh-dir-pref", MODE_PRIVATE);

		ghPath = getFilesDir().getPath() + "/graphhopper";

		long ghDirIndex = pref.getLong("gh-dir-index", 1);
		oldNavigationPath = ghPath + "/ghdata" + (ghDirIndex - 1);
		navigationPath = ghPath + "/ghdata" + ghDirIndex;

		locationReceiver = new LocationReceiver(this);

		locationIndexCache = new LocationIndexCache(this);


		dirManager = new DirectionManager(createRouting());
		dirManager.setListener(this);
		dirManager.calculateForPoints(PointsManager.getInstance()
				.getFilteredPoints());

		PointsManager.getInstance().addListener(this);
		PreferenceManager.getDefaultSharedPreferences(this)
				.registerOnSharedPreferenceChangeListener(this);

		log.debug("DirectionService created");
	}

	private IRouting createRouting() {
		if (new File(navigationPath + "/nodes").exists())
			return new OneToManyRouting(navigationPath, locationIndexCache);
		else
			return new StubRouting();
	}

	@Override
	public void onDestroy() {
		log.debug("DirectionService destroyed");
		if (locationReceiver != null && locationReceiver.isStarted()) {
			locationReceiver.stop();
		}

		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);

		PointsManager.getInstance().removeListener(this);

		dirManager.interrupt();
		dirManager = null;

		locationIndexCache.close();
		locationIndexCache = null;
	}
		
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public void fakeLocation(GeoPoint current) {
		if (current == null)
			return;
		
		if (locationReceiver.isStarted()) {
			float bearing;
			float speed;
			
			if (lastLocation != null) {
				GeoPoint last = new GeoPoint(lastLocation);
				bearing = (float) last.bearingTo(current);
				
				speed = (float) last.distanceTo(current) / ((System.currentTimeMillis() - lastLocation.getTime()) / 1000);
				
				log.debug("fakeLocation last = " + last + ", current = " + current + ", bearing = " + bearing);
			} else {
				bearing = 0;
				speed = 0;
				log.debug("fakeLocation current = " + current + ", bearing = " + bearing);
			}
			
			Location location = new Location(MOCK_PROVIDER);
			location.setLatitude(current.getLatitudeE6() / 1e6);
			location.setLongitude(current.getLongitudeE6() / 1e6);
			location.setTime(System.currentTimeMillis());
			location.setBearing(bearing); 
			location.setAccuracy(1);
			location.setSpeed(speed);
			
			if (Build.VERSION.SDK_INT > 17) {
				location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
			}

			locationReceiver.mockLocation(location);
		}
	}
	
	
	public void startTracking() {
		if (locationReceiver.isStarted()) {
			notifyLocationChanged(lastLocation);
			
			if (lastResultDirections != null)
				sendResult(lastResultDirections, lastResultCenter, lastResultLocation);
		
			return;
		}
		
		locationReceiver.setListener(this);
		
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		if (pref.getBoolean(SettingsActivity.STORE_LOCATION, false)) {
			locationReceiver.sendLastLocation();
		}
		
		locationReceiver.start();
	}
	
	@Override
	public void newLocation(Location location) {
		lastLocation = location;
		notifyLocationChanged(location);

		if (extractingThread != null && extractingThread.isAlive()) {
			return;
		}

		dirManager.updateLocation(lastLocation);
		dirManager.calculateForPoints(PointsManager.getInstance().getFilteredPoints());
	}
	
	/**
	 * Disable network and gps location provider for testing purposes.
	 */
	public void disableRealLocation() {
		if (!locationReceiver.isStarted()) {
			locationReceiver.disableRealLocation();
		} else {
			throw new IllegalStateException("Can't disable real location on running LocationReceiver");
		}
	}
	
	private void notifyLocationChanged(Location location) {
		if (location == null)
			return;
		
		Intent intent = new Intent(LOCATION_CHANGED);
		intent.putExtra(LOCATION, location);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	public PointList findPath(GeoPoint from, GeoPoint to) {
		return dirManager.findPath(from, to);
	}
	
	public Location getLastLocation() {
		return lastLocation;
	}
	
	@Override
	public void filterStateChanged(List<PointDesc> newList,
			List<PointDesc> added, List<PointDesc> removed) {
		dirManager.calculateForPoints(newList);
	}

	private void extractArchive(String path) {
		log.info("Extracting archive {}", path);
		File file = path == null ? null : new File(path);
		if (file == null || !file.exists() || !file.canRead())
			return;

		try {
			dirManager.interrupt();

			long ghDirIndex = pref.getLong("gh-dir-index", 1) + 1;
			String newPath = ghPath + "/ghdata" + ghDirIndex;

			new Unzipper().unzip(path, newPath, false);
			log.info("Archive file {} successfully extracted", path);

			File oldFileDir = new File(oldNavigationPath);
			Utils.deleteDir(oldFileDir);

			oldNavigationPath = navigationPath;
			navigationPath = newPath;

			pref.edit().putLong("gh-dir-index", ghDirIndex).apply();
			dirManager.setRouting(createRouting());

			log.debug("Listing graphhopper directory:");
			File ghDirFile = new File(ghPath);
			if (!ghDirFile.isDirectory() || !ghDirFile.exists()) {
				log.error("Wrong internal gh directory");
				return;
			}

			File[] files = ghDirFile.listFiles();
			if (files == null) {
				log.error("Can't list directory");
				return;
			}

			for (File f : files)
				log.debug(" {}", f.getAbsolutePath());

		} catch (IOException e) {
			log.warn("Can not extract archive file {}", path);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		log.debug("DirectionService.onSharedPreferenceChanged");
		if (key.equals(SettingsActivity.NEAREST_POINTS)) {
			List<PointDesc> points = PointsManager.getInstance().getFilteredPoints();
			dirManager.calculateForPoints(points);
		} else if (key.equals(SettingsActivity.NAVIGATION_DATA)) {
			final String archivePath = sharedPreferences.getString(SettingsActivity.NAVIGATION_DATA, null);
			if (archivePath == null)
				return;

			log.debug("Starting thread");
			locationIndexCache.reset();
			extractingThread = new Thread() {
				@Override
				public void run() {
					extractArchive(archivePath);
				}
			};
			extractingThread.start();
		}
	}

	@Override
	public void directionsUpdated(List<Direction> directions, GeoPoint center) {
		this.lastResultDirections = new ArrayList<Direction>(directions);
		this.lastResultCenter = center;
		this.lastResultLocation = lastLocation;
		
		sendResult(lastResultDirections, lastResultCenter, lastLocation);
	}
		
	private void sendResult(ArrayList<Direction> directions, GeoPoint center, Location location) {
		Intent intent = new Intent(DIRECTIONS_READY);
		intent.putParcelableArrayListExtra(DIRECTIONS_RESULT, directions);
		intent.putExtra(CENTER, (Parcelable) center);
		intent.putExtra(LOCATION, location);

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	public void setRadius(int dist) {
		dirManager.setRadius(dist);
	}
}
