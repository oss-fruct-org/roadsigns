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

import org.fruct.oss.ikm.DataService;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.poi.PointsManager.PointsListener;
import org.fruct.oss.ikm.storage.ContentItem;
import org.fruct.oss.ikm.storage.RemoteContentService;
import org.fruct.oss.ikm.utils.bind.BindHelper;
import org.fruct.oss.ikm.utils.bind.BindSetter;
import org.fruct.oss.ikm.utils.bind.State;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DirectionService extends Service implements
		PointsListener,
		DirectionManager.Listener,
		OnSharedPreferenceChangeListener,
		LocationReceiver.Listener,
		DataService.DataListener,
		RemoteContentService.ContentStateListener {
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
	public static final String RAW_LOCATION_CHANGED = "org.fruct.oss.ikm.RAW_LOCATION_CHANGES";

	public static final String PATH_READY = "org.fruct.oss.ikm.PATH_READY";

	private static final String MOCK_PROVIDER = "mock-provider";

	private RemoteContentService remoteContent;

	private final Object dataServiceMutex = new Object();
	private DataService dataService;

	private final Object dirManagerMutex = new Object();
	private DirectionManager dirManager;

	private final Object routingMutex = new Object();
	private GHRouting routing;

	private final AtomicBoolean setupInProgress = new AtomicBoolean(false);

	private IMapMatcher mapMatcher;

	private IBinder binder = new DirectionBinder();

	private LocationReceiver locationReceiver;

	// Last query result
	private ArrayList<Direction> lastResultDirections;
	private GeoPoint lastResultCenter;
	private Location lastResultLocation;

	private Location lastLocation;
	private Location lastMatchedLocation;
	private int lastMatchedNode;


	private String ghPath;
	private String navigationDir;
	private String currentStoragePath;

	private LocationIndexCache locationIndexCache;

	private SharedPreferences pref;
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
	public void servicesReady(DataService dataService, @State("ready") RemoteContentService remoteContentService) {
		log.trace("AAA: DirectionService setDataService");
		synchronized (dataServiceMutex) {
			this.dataService = dataService;
			this.remoteContent = remoteContentService;

			dataService.addDataListener(this);
			remoteContent.setContentStateListener(RemoteContentService.GRAPHHOPPER_MAP, this);

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

		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				synchronized (dirManagerMutex) {
					if (dirManager != null) {
						asyncCloseDirectionManager();
						locationIndexCache.close();
					}
				}
				return null;
			}
		}.execute();

		synchronized (dataServiceMutex) {
			if (dataService != null) {
				dataService.removeDataListener(this);
			}
		}

		if (remoteContent != null) {
			remoteContent.removeContentStateListener(RemoteContentService.GRAPHHOPPER_MAP);
		}

		BindHelper.autoUnbind(this, this);
	}

	private GHRouting createRouting() {
		String navigationPath = ghPath + "/" + navigationDir;

		if (new File(navigationPath + "/nodes").exists()) {
			OneToManyRouting routing = new OneToManyRouting(navigationPath, locationIndexCache);

			// Apply encoder from preferences
			routing.setEncoder(pref.getString(SettingsActivity.VEHICLE, "CAR"));
			if (!routing.ensureInitialized()) {
				return null;
			}

			return routing;
		} else
			return null;
	}

	private void updateDirectionsManager() {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				currentStoragePath = null;
				synchronized (dataServiceMutex) {
					if (dataService != null) {
						currentStoragePath = dataService.getDataPath();
					}
				}

				if (currentStoragePath != null) {
					asyncUpdateDirectionsManager();
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void aVoid) {
				if (dataService != null)
					dataService.dataListenerReady();

				startTracking();
			}
		}.execute();
	}

	private void asyncCloseDirectionManager() {
		DirectionManager oldDirManager = null;
		GHRouting oldRouting = null;

		synchronized (dirManagerMutex) {
			if (dirManager != null) {
				oldDirManager = this.dirManager;
				this.dirManager = null;
			}
		}

		synchronized (routingMutex) {
			if (routing != null) {
				oldRouting = routing;
				routing = null;
				mapMatcher = null;
			}
		}

		if (oldDirManager != null) {
			oldDirManager.closeSync();
		}

		if (oldRouting != null) {
			oldRouting.close();
		}
	}

	private void asyncUpdateDirectionsManager() {
		ghPath = currentStoragePath + "/graphhopper";
		navigationDir = pref.getString(SettingsActivity.NAVIGATION_DATA, null);

		asyncCloseDirectionManager();

		if (navigationDir == null) {
			return;
		}

		synchronized (routingMutex) {
			routing = createRouting();
			if (routing == null) {
				log.warn("Current graphhopper region invalid, disabling it");
				locationIndexCache.reset();
				remoteContent.invalidateCurrentContent(lastLocation, RemoteContentService.GRAPHHOPPER_MAP);
				return;
			}

			updateMapMatcher();
		}

		synchronized (dirManagerMutex) {
			dirManager = new DirectionManager(routing);
			dirManager.setListener(DirectionService.this);
			dirManager.setRadius(radius);
		}

		if (lastLocation != null) {
			asyncNewLocation(lastLocation);
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
			if (lastMatchedLocation != null) {
				notifyLocationChanged(lastMatchedLocation);
			} else {
				notifyLocationChanged(lastLocation);
			}

			if (lastResultDirections != null)
				sendResult(lastResultDirections, lastResultCenter, lastResultLocation);

			return;
		}

		locationReceiver.setListener(this);

		locationReceiver.start();
		locationReceiver.sendLastLocation();
	}

	@Override
	public void newLocation(final Location location) {
		lastLocation = location;

		Intent intent = new Intent(RAW_LOCATION_CHANGED);
		intent.putExtra(LOCATION, location);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				try {
					asyncNewLocation(location);
				} catch (Exception ex) {
					log.error("Exception during new location processing", ex);
					if (remoteContent != null) {
						remoteContent.invalidateCurrentContent(location, RemoteContentService.GRAPHHOPPER_MAP);
					}
				}
				return null;
			}
		}.execute();
	}

	public void asyncNewLocation(Location location) {
		synchronized (routingMutex) {
			if (mapMatcher != null) {
				mapMatcher.updateLocation(location);

				lastMatchedLocation = mapMatcher.getMatchedLocation();
				lastMatchedNode = mapMatcher.getMatchedNode();
			} else {
				notifyLocationChanged(location);
			}
		}

		if (lastMatchedLocation != null) {
			notifyLocationChanged(lastMatchedLocation);

			synchronized (dirManagerMutex) {
				if (dirManager != null) {
					dirManager.updateLocation(lastMatchedLocation, lastMatchedNode);
					dirManager.calculateForPoints(PointsManager.getInstance().getFilteredPoints());
				}
			}
		} else {
			notifyLocationChanged(location);
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

	private void notifyLocationChanged(Location matchedLocation) {
		if (matchedLocation == null)
			return;

		Intent intent = new Intent(LOCATION_CHANGED);
		intent.putExtra(MATCHED_LOCATION, matchedLocation);

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	public void findPath(GeoPoint to) {
		synchronized (dirManagerMutex) {
			if (dirManager != null) {
				dirManager.findPath(to);
			}
		}
	}

	@Override
	public void filterStateChanged(List<PointDesc> newList) {
		synchronized (dirManagerMutex) {
			if (dirManager != null) {
				dirManager.calculateForPoints(newList);
			}
		}
	}

	@Override
	public void errorDownloading() {
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences pref,
										  String key) {
		log.trace("DirectionService.onSharedPreferenceChanged {}", key);
		if (key.equals(SettingsActivity.NEAREST_POINTS)) {
			List<PointDesc> points = PointsManager.getInstance().getFilteredPoints();
			synchronized (dirManagerMutex) {
				if (dirManager != null) {
					dirManager.calculateForPoints(points);
				}
			}
		} else if (key.equals(SettingsActivity.NAVIGATION_DATA)) {
		} else if (key.equals(SettingsActivity.VEHICLE)) {
			if (routing != null) {
				routing.setEncoder(pref.getString(key, "CAR"));
				updateMapMatcher();
			}
		} else if (key.equals(SettingsActivity.MAPMATCHING)) {
			updateMapMatcher();
		}
	}

	private void updateMapMatcher() {
		boolean enabled = pref.getBoolean(SettingsActivity.MAPMATCHING, true);
		if (routing != null) {
			if (enabled) {
				mapMatcher = routing.createMapMatcher();
			} else {
				mapMatcher = routing.createSimpleMapMatcher();
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
		if (pointList == null)
			return;

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
				dirManager.calculateForPoints(PointsManager.getInstance().getFilteredPoints());
			}
		}
	}

	@Override
	public void contentItemReady(ContentItem contentItem) {
		updateDirectionsManager();
	}

	@Override
	public void contentItemDeactivated() {
		log.debug("DirectionService contentItemDeactivated");
		asyncCloseDirectionManager();
		locationIndexCache.reset();
	}
}