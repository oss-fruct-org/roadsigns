package org.fruct.oss.ikm.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.events.DirectionsEvent;
import org.fruct.oss.ikm.events.LocationEvent;
import org.fruct.oss.ikm.events.PathEvent;
import org.fruct.oss.mapcontent.content.ContentItem;
import org.fruct.oss.mapcontent.content.ContentListenerAdapter;
import org.fruct.oss.mapcontent.content.ContentManagerImpl;
import org.fruct.oss.mapcontent.content.ContentService;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnection;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnectionListener;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.greenrobot.event.EventBus;

public class DirectionService extends Service implements
		DirectionManager.Listener,
		OnSharedPreferenceChangeListener,
		LocationReceiver.Listener, ContentServiceConnectionListener {
	private static Logger log = LoggerFactory.getLogger(DirectionService.class);

	public static final String MOCK_PROVIDER = "mock-provider";

	private ExecutorService executor = Executors.newSingleThreadExecutor();

	private ContentService remoteContent;
	private ContentServiceConnection remoteContentServiceConnection = new ContentServiceConnection(this);

	private final Object dirManagerMutex = new Object();
	private DirectionManager dirManager;

	private final Object routingMutex = new Object();

	private GHRouting routing;

	private IMapMatcher mapMatcher;

	private LocationReceiver locationReceiver;

	private Location lastRawLocation;

	private LocationIndexCache locationIndexCache;
	private ContentItem recommendedContentItem;

	private SharedPreferences pref;

	private IBinder binder = new DirectionBinder();
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

		pref = PreferenceManager.getDefaultSharedPreferences(this);

		locationReceiver = new LocationReceiver(this);
		locationReceiver.start();
		locationReceiver.setListener(this);

		locationIndexCache = new LocationIndexCache(this);

		pref.registerOnSharedPreferenceChangeListener(this);

		remoteContentServiceConnection.bindService(this);

		log.debug("created");
	}

	@Override
	public void onDestroy() {
		log.debug("DirectionService destroyed");
		if (locationReceiver != null && locationReceiver.isStarted()) {
			locationReceiver.stop();
		}

		//EventBus.getDefault().unregister(this);

		pref.unregisterOnSharedPreferenceChangeListener(this);

		if (remoteContent != null) {
			remoteContent.removeItemListener(remoteContentListener);
		}

		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (dirManager != null) {
					asyncCloseDirectionManager();
					locationIndexCache.close();
				}
			}
		});
		executor.shutdown();

		remoteContentServiceConnection.unbindService(this);
	}

	@Override
	public void onContentServiceReady(ContentService contentService) {
		remoteContent = contentService;
		remoteContent.addItemListener(remoteContentListener);
		updateDirectionsManager(false);
	}

	@Override
	public void onContentServiceDisconnected() {
		remoteContent = null;
	}

	@Nullable
	private GHRouting createRouting(String navigationPath) {
		if (new File(navigationPath + "/nodes").exists()) {
			OneToManyRouting routing = new OneToManyRouting(navigationPath, locationIndexCache);

			// Apply encoder from preferences
			String vehicle = pref.getString(SettingsActivity.VEHICLE, "CAR");
			routing.setEncoder(vehicle);
			locationReceiver.setVehicle(vehicle);
			if (!routing.ensureInitialized()) {
				return null;
			}

			return routing;
		} else
			return null;
	}

	private void updateDirectionsManager(boolean forceUpdate) {
		log.debug("update direction manager. forceUpdate = {}", forceUpdate);

		final String navigationPath;
		if (recommendedContentItem == null) {
			navigationPath = locationIndexCache.getNavigationPath();
			log.debug("  loading stored path: {}", navigationPath);
		} else {
			navigationPath = remoteContent.requestContentItem(recommendedContentItem);
			log.debug("  loading recommended path: {}", navigationPath);

			String oldNavigationPath = locationIndexCache.getNavigationPath();
			if (oldNavigationPath != null) {
				if (!oldNavigationPath.equals(navigationPath)) {
					log.debug("  resetting location index cache");
					locationIndexCache.reset(recommendedContentItem.getRegionId(), navigationPath);
				} else if (!forceUpdate) {
					// Don't update direction manager if old navigation path equals new
					log.debug("  skipping update");
					return;
				}
			}
		}

		if (navigationPath == null) {
			// TODO: if path loaded from pref and null, request recommended item
			log.debug("  navigation path is null, skipping update");
			return;
		}

		executor.execute(new Runnable() {
			@Override
			public void run() {
				asyncUpdateDirectionsManager(navigationPath);
			}
		});
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
			oldDirManager.shutdown();
			oldDirManager.awaitShutdown();
		}

		if (oldRouting != null) {
			oldRouting.close();
		}
	}

	private void asyncUpdateDirectionsManager(String navigationPath) {
		asyncCloseDirectionManager();

		synchronized (routingMutex) {
			routing = createRouting(navigationPath);
			if (routing == null) {
				return;
			}

			updateMapMatcher();
		}

		synchronized (dirManagerMutex) {
			dirManager = new DirectionManager(routing, locationIndexCache.getRegionId());
			dirManager.setListener(DirectionService.this);
			if (lastRawLocation != null) {
				asyncNewLocation(lastRawLocation);
			}
		}
	}

	public void fakeLocation(GeoPoint current) {
		if (current == null)
			return;

		if (locationReceiver.isStarted()) {
			float bearing;
			float speed;

			if (lastRawLocation != null) {
				GeoPoint last = new GeoPoint(lastRawLocation);
				bearing = (float) last.bearingTo(current);

				speed = (float) last.distanceTo(current) / ((System.currentTimeMillis() - lastRawLocation.getTime()) / 1000);

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

	public void realLocation() {
		locationReceiver.sendLastLocation();
	}

	/*@EventReceiver
	public void onEventMainThread(TrackingModeEvent trackingModeEvent) {
		if (!trackingModeEvent.isInTrackingMode()) {
			EventBus.getDefault().removeStickyEvent(DirectionsEvent.class);
		} else {
			if (lastRawLocation != null) {
				newLocation(lastRawLocation);
			}
		}
	}*/

	@Override
	public void newLocation(final Location location) {
		lastRawLocation = location;

		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					asyncNewLocation(location);
				} catch (Exception ex) {
					log.error("Exception during new location processing", ex);
				}
			}
		});
	}

	public void asyncNewLocation(Location location) {
		synchronized (routingMutex) {
			if (mapMatcher != null) {
				mapMatcher.updateLocation(location);

				Location matchedLocation = mapMatcher.getMatchedLocation();
				int matchedNode = mapMatcher.getMatchedNode();

				if (matchedLocation != null) {
					EventBus.getDefault().postSticky(new LocationEvent(matchedLocation, matchedNode));
				} else {
					EventBus.getDefault().postSticky(new LocationEvent(location));
				}
			} else {
				EventBus.getDefault().postSticky(new LocationEvent(location));
			}
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences pref,
										  String key) {
		log.trace("DirectionService.onSharedPreferenceChanged {}", key);
		switch (key) {
		case SettingsActivity.NEAREST_POINTS:
			synchronized (dirManagerMutex) {
				if (dirManager != null && lastRawLocation != null) {
					newLocation(lastRawLocation);
				}
			}
			break;
		case SettingsActivity.VEHICLE:
			if (routing != null) {
				String vehicle = pref.getString(key, "CAR");
				routing.setEncoder(vehicle);
				locationReceiver.setVehicle(vehicle);
				updateMapMatcher();

			}
			break;
		case SettingsActivity.MAPMATCHING:
			updateMapMatcher();
			break;
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
		EventBus.getDefault().postSticky(new DirectionsEvent(center, directions));
	}

	@Override
	public void pathReady(PointList pointList) {
		if (pointList == null) {
			EventBus.getDefault().removeStickyEvent(PathEvent.class);
			return;
		}

		ArrayList<GeoPoint> pathArray = new ArrayList<>();
		for (int i = 0; i < pointList.getSize(); i++)
			pathArray.add(new GeoPoint(pointList.getLatitude(i), pointList.getLongitude(i)));

		PathEvent pathEvent = new PathEvent(pathArray);
		EventBus.getDefault().postSticky(pathEvent);
	}

	private ContentListenerAdapter remoteContentListener = new ContentListenerAdapter() {
		@Override
		public void recommendedRegionItemReady(ContentItem contentItem) {
			if (!contentItem.getType().equals(ContentManagerImpl.GRAPHHOPPER_MAP)) {
				return;
			}

			recommendedContentItem = contentItem;
			updateDirectionsManager(false);
		}

		@Override
		public void requestContentReload() {
			if (recommendedContentItem == null) {
				return;
			}

			updateDirectionsManager(true);
		}
	};
}