package org.fruct.oss.ikm.points;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.events.LocationEvent;
import org.fruct.oss.ikm.events.PointsUpdatedEvent;
import org.fruct.oss.mapcontent.content.ContentService;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnection;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnectionListener;
import org.fruct.oss.mapcontent.content.utils.RegionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.greenrobot.event.EventBus;

public class PointsUpdateService extends Service implements ContentServiceConnectionListener {
	private static final long OBSOLETE_TIME = 60 * 60 * 24 * 30 * 1000l; // 30 days

	private static final Logger log = LoggerFactory.getLogger(PointsUpdateService.class);

	private static final String PREF_LAST_REFRESH_LAT
			= "org.fruct.oss.ikm.points.PointsUpdateService.LAST_REFRESH_LAT";
	private static final String PREF_LAST_REFRESH_LON
			= "org.fruct.oss.ikm.points.PointsUpdateService.LAST_REFRESH_LON";
	private static final String PREF_LAST_REFRESH_TIME
			= "org.fruct.oss.ikm.points.PointsUpdateService.LAST_REFRESH_TIME";

	private static final String ACTION_REFRESH
			= "org.fruct.oss.ikm.points.PointsUpdateService.ACTION_REFRESH";
	private static final String ACTION_TRY_REFRESH
			= "org.fruct.oss.ikm.points.PointsUpdateService.ACTION_TRY_REFRESH";

	// Both arguments optional
	public static final String ARG_RADIUS
			= "org.fruct.oss.ikm.points.PointsUpdateService.ACTION_REFRESH.ARG_RADIUS";
	public static final String ARG_LOCATION
			= "org.fruct.oss.ikm.points.PointsUpdateService.ACTION_REFRESH.ARG_LOCATION";
	public static final String ARG_SKIP_CATEGORIES
			= "org.fruct.oss.ikm.points.PointsUpdateService.ACTION_REFRESH.ARG_SKIP_CATEGORIES";

	private static final float REFRESH_DELTA_TIME = 3600000;

	private SharedPreferences pref;
	private GetsAsyncTask getsAsyncTask;

	private ContentServiceConnection contentServiceConnection = new ContentServiceConnection(this);

	private ExecutorService regionsExecutor = Executors.newSingleThreadExecutor();
	private ContentService contentService;

	private boolean isRegionCacheUpdateScheduled;

	public PointsUpdateService() {
	}

	public static void startDefault(Context context) {
		Intent intent = new Intent(PointsUpdateService.ACTION_REFRESH, null,
				context, PointsUpdateService.class);
		context.startService(intent);
	}

	public static void startRefreshActive(Context context, Location location, int radius) {
		Intent intent = new Intent(PointsUpdateService.ACTION_REFRESH, null,
				context, PointsUpdateService.class);

		intent.putExtra(ARG_LOCATION, location);
		intent.putExtra(ARG_RADIUS, radius);
		intent.putExtra(ARG_SKIP_CATEGORIES, true);

		context.startService(intent);
	}

	public static void startTryRefresh(Context context) {
		Intent intent = new Intent(PointsUpdateService.ACTION_TRY_REFRESH, null,
				context, PointsUpdateService.class);
		context.startService(intent);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		pref = PreferenceManager.getDefaultSharedPreferences(this);
		contentServiceConnection.bindService(this);
	}

	@Override
	public void onDestroy() {
		if (getsAsyncTask != null) {
			getsAsyncTask.cancel(true);
		}
		regionsExecutor.shutdownNow();
		contentServiceConnection.unbindService(this);
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null || intent.getAction() == null) {
			return START_NOT_STICKY;
		}

		String action = intent.getAction();
		switch (action) {
		case ACTION_REFRESH:
			Location location = intent.getParcelableExtra(ARG_LOCATION);
			if (location == null) {
				LocationEvent lastLocationEvent = EventBus.getDefault().getStickyEvent(LocationEvent.class);
				if (lastLocationEvent != null) {
					location = lastLocationEvent.getLocation();
				}
			}

			//noinspection ConstantConditions
			if (location == null) {
				log.warn("Trying refresh points without location");
				EventBus.getDefault().post(new PointsUpdatedEvent(false, null));
				return START_NOT_STICKY;
			}

			int radius = intent.getIntExtra(ARG_RADIUS,
					Integer.parseInt(pref.getString(SettingsActivity.GETS_RADIUS, "-1")));

			boolean skipCategories = intent.getBooleanExtra(ARG_SKIP_CATEGORIES, false);

			refresh(location, radius, skipCategories);
			break;

		case ACTION_TRY_REFRESH:
			maintain();
			break;
		}

		return START_NOT_STICKY;
	}

	private void maintain() {
		long deleted = App.getInstance().getPointsAccess().deleteOlderThan(System.currentTimeMillis() - OBSOLETE_TIME);

		log.info("{} old points deleted while maintaining points database", deleted);

		LocationEvent locationEvent = EventBus.getDefault().getStickyEvent(LocationEvent.class);
		if (locationEvent == null) {
			return;
		}

		float dist[] = new float[1];
		float lastRefreshLat = pref.getFloat(PREF_LAST_REFRESH_LAT, 0);
		float lastRefreshLon = pref.getFloat(PREF_LAST_REFRESH_LON, 0);
		long lastRefreshTime = pref.getLong(PREF_LAST_REFRESH_TIME, 0);

		int refreshRadius = Integer.parseInt(pref.getString(SettingsActivity.GETS_RADIUS,
				String.valueOf(SettingsActivity.GETS_RADIUS_DEFAULT)));

		Location currentLocation = locationEvent.getLocation();
		Location.distanceBetween(lastRefreshLat, lastRefreshLon,
				currentLocation.getLatitude(), currentLocation.getLongitude(), dist);

		if (dist[0] > refreshRadius / 4 || System.currentTimeMillis() - lastRefreshTime > REFRESH_DELTA_TIME) {
			refresh(currentLocation, refreshRadius, false);
		}

		scheduleRegionsCacheUpdate();
	}

	private void refresh(Location location, int radius, boolean skipCategories) {
		if (getsAsyncTask != null && getsAsyncTask.getStatus() != AsyncTask.Status.FINISHED){
			return;
		}

		getsAsyncTask = new Task(pref.getString(SettingsActivity.GETS_SERVER,
				SettingsActivity.GETS_SERVER_DEFAULT), skipCategories);
		getsAsyncTask.execute(location.getLatitude(), location.getLongitude(), radius);
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public synchronized void onContentServiceReady(ContentService contentService) {
		this.contentService = contentService;

		if (isRegionCacheUpdateScheduled) {
			scheduleRegionsCacheUpdate();
		}
	}

	@Override
	public synchronized void onContentServiceDisconnected() {
		this.contentService = null;
	}

	private void scheduleRegionsCacheUpdate() {
		if (contentService == null) {
			isRegionCacheUpdateScheduled = true;
			return;
		} else {
			isRegionCacheUpdateScheduled = false;
		}

		regionsExecutor.execute(new Runnable() {
			@Override
			public void run() {
				long lastRegionsUpdate = contentService.getRegionCache().getLastRefreshTime();
				List<Point> points = App.getInstance().getPointsAccess().loadPointsWithoutRegion(lastRegionsUpdate);
				for (Point point : points) {
					updatePointRegion(point);
				}
			}
		});
	}

	private synchronized void updatePointRegion(Point point) {
		log.trace("Checking region for point {}", point.getName());
		Location pointLocation = new Location("no-provider");
		pointLocation.setLatitude(point.toPoint().getLatitude());
		pointLocation.setLongitude(point.toPoint().getLongitude());

		List<RegionCache.RegionDesc> regions = contentService.getRegionCache().findRegions(pointLocation);

		for (RegionCache.RegionDesc regionDesc : regions) {
			log.trace("\tRegion {} found", regionDesc.name);
			point.setRegionId(regionDesc.regionId, regionDesc.adminLevel);
		}

		point.setRegionUpdateTime(System.currentTimeMillis());
		App.getInstance().getPointsAccess().insertPoint(point, false);
	}

	private class Task extends GetsAsyncTask {
		public Task(String server, boolean skipCategories) {
			super(server, skipCategories);
		}

		@Override
		protected void onPostExecute(Result result) {
			super.onPostExecute(result);

			if (result != null) {
				EventBus.getDefault().post(new PointsUpdatedEvent(true, result.getCategories()));
				scheduleRegionsCacheUpdate();
				pref.edit().putFloat(PREF_LAST_REFRESH_LAT, (float) result.getLat())
						.putFloat(PREF_LAST_REFRESH_LON, (float) result.getLon())
						.putLong(PREF_LAST_REFRESH_TIME, System.currentTimeMillis())
						.apply();
			} else {
				EventBus.getDefault().post(new PointsUpdatedEvent(false, null));
			}
		}

		@Override
		protected void onCancelled() {
			EventBus.getDefault().post(new PointsUpdatedEvent(false, null));
		}
	}
}
