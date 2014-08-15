package org.fruct.oss.ikm.service;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import com.graphhopper.util.PointList;
import com.graphhopper.util.Unzipper;

import org.fruct.oss.ikm.DataService;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.poi.PointsManager.PointsListener;
import org.fruct.oss.ikm.service.LocationReceiver.Listener;
import org.fruct.oss.ikm.storage.ContentItem;
import org.fruct.oss.ikm.storage.RemoteContentService;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.utils.bind.BindHelper;
import org.fruct.oss.ikm.utils.bind.BindSetter;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DirectionService extends Service implements PointsListener,
		DirectionManager.Listener, OnSharedPreferenceChangeListener, Listener, DataService.DataListener {
	private static Logger log = LoggerFactory.getLogger(DirectionService.class);

	// Extras
	public static final String DIRECTIONS_RESULT = "org.fruct.oss.ikm.GET_DIRECTIONS_RESULT";
	public static final String CENTER = "org.fruct.oss.ikm.CENTER";
	public static final String LOCATION = "org.fruct.oss.ikm.LOCATION";
	public static final String MATCHED_LOCATION = "org.fruct.oss.ikm.MATCHED_LOCATION";
	public static final String PATH = "org.fruct.oss.ikm.PATH";

	// Broadcasts
	public static final String DIRECTIONS_READY = "org.fruct.oss.ikm.GET_DIRECTIONS_READY";
	public static final String LOCATION_CHANGED = "org.fruct.oss.ikm.LOCATION_CHANGED";
	public static final String PATH_READY = "org.fruct.oss.ikm.PATH_READY";

	private static final String MOCK_PROVIDER = "mock-provider";

	public static final String PREF_NAVIGATION_DIR = "navigation-dir";

	private RemoteContentService remoteContent;
	private DataService dataService;

	private final Object dirManagerMutex = new Object();
	private DirectionManager dirManager;

	private IBinder binder = new DirectionBinder();

	private LocationReceiver locationReceiver;

	// Last query result
	private ArrayList<Direction> lastResultDirections;
	private GeoPoint lastResultCenter;
	private Location lastResultLocation;

	private Location lastLocation;
	private IRouting routing;
	private IMapMatcher mapMatcher;

	private String ghPath;
	private String navigationDir;
	private String currentStoragePath;

	private LocationIndexCache locationIndexCache;

	private SharedPreferences pref;
	private AsyncTask<String, Void, Void> extractTask;
	private int radius;

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

		pref = PreferenceManager.getDefaultSharedPreferences(this);

		BindHelper.autoBind(this, this);

		locationReceiver = new LocationReceiver(this);
		locationIndexCache = new LocationIndexCache(this);

		PointsManager.getInstance().addListener(this);
		pref.registerOnSharedPreferenceChangeListener(this);

		log.debug("DirectionService created");
	}

	@BindSetter
	public void setDataService(DataService service) {
		dataService = service;

		if (dataService != null) {
			dataService.addDataListener(this);
		}

		if (dataService != null && remoteContent != null) {
			updateDirectionsManager();
		}
	}

	@BindSetter
	public void setRemoteContentService(RemoteContentService service) {
		remoteContent = service;

		if (dataService != null && remoteContent != null) {
			updateDirectionsManager();
		}
	}

	@Override
	public void dataPathChanged(String newDataPath) {
		updateDirectionsManager();
	}

	@Override
	public int getPriority() {
		return 1;
	}

	@Override
	public void onDestroy() {
		log.debug("DirectionService destroyed");
		if (locationReceiver != null && locationReceiver.isStarted()) {
			locationReceiver.stop();
		}

		pref.unregisterOnSharedPreferenceChangeListener(this);

		PointsManager.getInstance().removeListener(this);

		if (extractTask != null) {
			extractTask.cancel(true);
		}

		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				synchronized (dirManagerMutex) {
					dirManager.closeSync();
					dirManager = null;
					locationIndexCache.close();
				}
				return null;
			}
		}.execute();

		if (dataService != null) {
			dataService.removeDataListener(this);
		}

		BindHelper.autoUnbind(this, this);
	}

	private IRouting createRouting() {
		String navigationPath = ghPath + "/" + navigationDir;

		if (new File(navigationPath + "/nodes").exists()) {
			OneToManyRouting routing = new OneToManyRouting(navigationPath, locationIndexCache);

			// Apply encoder from preferences
			routing.setEncoder(pref.getString(SettingsActivity.VEHICLE, "CAR"));

			return routing;
		} else
			return new StubRouting();
	}

	private void updateDirectionsManager() {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				currentStoragePath = dataService.getDataPath();
				asyncUpdateDirectionsManager();
				return null;
			}

			@Override
			protected void onPostExecute(Void aVoid) {
				dataService.dataListenerReady();
			}
		}.execute();
	}

	private void asyncUpdateDirectionsManager() {
		ghPath = currentStoragePath + "/graphhopper";
		navigationDir = pref.getString(PREF_NAVIGATION_DIR, null);

		routing = createRouting();
		mapMatcher = routing.createMapMatcher();

		DirectionManager oldDirManager = dirManager;
		synchronized (dirManagerMutex) {
			dirManager = new DirectionManager(routing);
			dirManager.setListener(DirectionService.this);
			dirManager.setRadius(radius);
			dirManager.calculateForPoints(PointsManager.getInstance().getFilteredPoints());
		}

		if (oldDirManager != null) {
			oldDirManager.closeSync();
		}
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
			notifyLocationChanged(lastLocation, mapMatcher.getMatchedLocation());

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

		if (mapMatcher == null)
			return;

		// TODO: can be time consuming
		mapMatcher.updateLocation(location);

		notifyLocationChanged(mapMatcher.getMatchedLocation(), mapMatcher.getMatchedLocation());

		synchronized (dirManagerMutex) {
			dirManager.updateLocation(mapMatcher.getMatchedLocation());
			dirManager.calculateForPoints(PointsManager.getInstance().getFilteredPoints());
		}
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

	private void notifyLocationChanged(Location location, Location matchedLocation) {
		if (location == null)
			return;

		Intent intent = new Intent(LOCATION_CHANGED);
		intent.putExtra(LOCATION, location);
		intent.putExtra(MATCHED_LOCATION, matchedLocation);

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	public void findPath(GeoPoint to) {
		dirManager.findPath(to);
	}

	@Override
	public void filterStateChanged(List<PointDesc> newList,
								   List<PointDesc> added, List<PointDesc> removed) {
		if (dirManager != null) {
			dirManager.calculateForPoints(newList);
		}
	}

	private String extractArchive(String path) {
		log.info("Extracting archive {}", path);

		File file = path == null ? null : new File(path);
		if (file == null || !file.exists() || !file.canRead())
			return null;

		try {
			String uuid = UUID.randomUUID().toString();
			String newPath = ghPath + "/ghdata" + uuid;

			new Unzipper().unzip(path, newPath, false);
			log.info("Archive file {} successfully extracted", path);

			return "ghdata" + uuid;
		} catch (IOException e) {
			log.warn("Can not extract archive file {}", path);
			return null;
		}
	}

	private void handleNavigationDataChange(String archiveName) {
		if (archiveName == null)
			return;

		ContentItem contentItem = remoteContent.getContentItem(archiveName);
		String path = remoteContent.getFilePath(contentItem);

		extractTask = new AsyncTask<String, Void, Void>() {
			@Override
			protected Void doInBackground(String... params) {
				String oldNavigationDir = pref.getString(PREF_NAVIGATION_DIR, null);

				String path = params[0];
				String newNavigationDir = extractArchive(path);

				if (newNavigationDir == null) {
					return null;
				}

				navigationDir = newNavigationDir;
				pref.edit().putString(PREF_NAVIGATION_DIR, navigationDir).apply();

				synchronized (dirManagerMutex) {
					if (dirManager != null) {
						asyncUpdateDirectionsManager();
					}
				}

				if (oldNavigationDir != null) {
					String oldNavigationPath = ghPath + "/" + oldNavigationDir;
					Utils.deleteDir(new File(oldNavigationPath));
				}
				return null;
			}
		};

		extractTask.execute(path);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
										  String key) {
		log.debug("DirectionService.onSharedPreferenceChanged");
		if (key.equals(SettingsActivity.NEAREST_POINTS)) {
			List<PointDesc> points = PointsManager.getInstance().getFilteredPoints();
			synchronized (dirManagerMutex) {
				dirManager.calculateForPoints(points);
			}
		} else if (key.equals(SettingsActivity.NAVIGATION_DATA)) {
			handleNavigationDataChange(pref.getString(SettingsActivity.NAVIGATION_DATA, null));
		} else if (key.equals(SettingsActivity.VEHICLE)) {
			if (routing != null) {
				routing.setEncoder(sharedPreferences.getString(key, "CAR"));
				mapMatcher = routing.createMapMatcher();
			}
		}
	}

	@Override
	public void directionsUpdated(List<Direction> directions, GeoPoint center) {
		this.lastResultDirections = new ArrayList<Direction>(directions);
		this.lastResultCenter = center;
		this.lastResultLocation = lastLocation;

		sendResult(lastResultDirections, lastResultCenter, lastLocation);
	}

	@Override
	public void pathReady(PointList pointList) {
		ArrayList<GeoPoint> pathArray = new ArrayList<GeoPoint>();
		for (int i = 0; i < pointList.getSize(); i++)
			pathArray.add(new GeoPoint(pointList.getLatitude(i), pointList.getLongitude(i)));

		Intent intent = new Intent(PATH_READY);
		intent.putParcelableArrayListExtra(PATH, pathArray);

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void sendResult(ArrayList<Direction> directions, GeoPoint center, Location location) {
		Intent intent = new Intent(DIRECTIONS_READY);
		intent.putParcelableArrayListExtra(DIRECTIONS_RESULT, directions);
		intent.putExtra(CENTER, (Parcelable) center);
		intent.putExtra(LOCATION, location);

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	public void setRadius(int dist) {
		this.radius = dist;
		synchronized (dirManagerMutex) {
			if (dirManager != null) {
				dirManager.setRadius(dist);
			}
		}
	}
}