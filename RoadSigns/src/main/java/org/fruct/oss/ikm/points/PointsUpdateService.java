package org.fruct.oss.ikm.points;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.events.EventReceiver;
import org.fruct.oss.ikm.events.LocationEvent;
import org.fruct.oss.ikm.events.PointsUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.greenrobot.event.EventBus;

public class PointsUpdateService extends Service {
	private static final Logger log = LoggerFactory.getLogger(PointsUpdateService.class);

	public static final String ACTION_REFRESH = "org.fruct.oss.ikm.points.PointsUpdateService.ACTION_REFRESH";

	// Both arguments optional
	public static final String ARG_RADIUS = "org.fruct.oss.ikm.points.PointsUpdateService.ACTION_REFRESH.ARG_RADIUS";
	public static final String ARG_LOCATION = "org.fruct.oss.ikm.points.PointsUpdateService.ACTION_REFRESH.ARG_LOCATION";

	private Location location;
	private int radius;

	private SharedPreferences pref;
	private GetsAsyncTask getsAsyncTask;

	public PointsUpdateService() {
	}

	public static void startDefault(Context context) {
		Intent intent = new Intent(PointsUpdateService.ACTION_REFRESH, null, context, PointsUpdateService.class);
		context.startService(intent);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		pref = PreferenceManager.getDefaultSharedPreferences(this);
	}

	@Override
	public void onDestroy() {
		if (getsAsyncTask != null) {
			getsAsyncTask.cancel(true);
		}

		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
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

			refresh(location, radius);
		}

		return START_NOT_STICKY;
	}

	private void refresh(Location location, int radius) {
		log.info("Starting points refresh for {} and radius {}", location, radius);

		if (getsAsyncTask != null){
			getsAsyncTask.cancel(true);
		}

		getsAsyncTask = new Task(pref.getString(SettingsActivity.GETS_SERVER,
				SettingsActivity.GETS_SERVER_DEFAULT));
		getsAsyncTask.execute(location.getLatitude(), location.getLongitude(), radius);
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private class Task extends GetsAsyncTask {
		public Task(String server) {
			super(server);
		}

		@Override
		protected void onPostExecute(Result result) {
			super.onPostExecute(result);

			if (result != null) {
				EventBus.getDefault().post(new PointsUpdatedEvent(true, result.getCategories()));
			} else {
				EventBus.getDefault().post(new PointsUpdatedEvent(false, null));
			}
			// TODO: probably service can be safely finished
		}

		@Override
		protected void onCancelled() {
			EventBus.getDefault().post(new PointsUpdatedEvent(false, null));
		}
	}
}
