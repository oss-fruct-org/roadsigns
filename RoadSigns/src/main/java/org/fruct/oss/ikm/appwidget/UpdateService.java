package org.fruct.oss.ikm.appwidget;

import static org.fruct.oss.ikm.Utils.log;

import java.util.ArrayList;

import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.DirectionService;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.RemoteViews;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class UpdateService extends Service {
	public static final String TRACKING_BUTTON_CLICKED = "org.fruct.oss.ikm.TRACKING_BUTTON_CLICKED";

	private BroadcastReceiver directionsReceiver;
	private DirectionService directionService;
	private boolean isTracking = false;

	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			directionService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			directionService = ((DirectionService.DirectionBinder) service)
					.getService();
			directionService.startTracking();
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	private BroadcastReceiver updateDirectionsReceiver = new BroadcastReceiver() {
		@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
		@Override
		public void onReceive(Context context, Intent intent) {
			log("UpdateService: onReceive");

			ArrayList<Direction> directions = intent
					.getParcelableArrayListExtra(DirectionService.DIRECTIONS_RESULT);
			Location location = (Location) intent
					.getParcelableExtra(DirectionService.LOCATION);

			ListProvider.sdirections = directions;
			ListProvider.slocation = location;

			updateRemoteViews(context);
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		
		Utils.log("UpdateService.onCreate");
	}

	@Override
	public void onDestroy() {
		Utils.log("UpdateService.onDestroy");
		if (isTracking)
			stopTracking();
		super.onDestroy();
	}

	private void startTracking() {
		Intent intent = new Intent(this, DirectionService.class);
		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

		LocalBroadcastManager.getInstance(this).registerReceiver(
				updateDirectionsReceiver,
				new IntentFilter(DirectionService.DIRECTIONS_READY));
	}

	private void stopTracking() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(
				directionsReceiver);

		unbindService(serviceConnection);
	}
	
	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void updateRemoteViews(Context context) {
		AppWidgetManager manager = AppWidgetManager.getInstance(context);
		ComponentName name = new ComponentName(context, RoadSignsWidgetProvider.class);

		// Update list view
		manager.notifyAppWidgetViewDataChanged(manager.getAppWidgetIds(name),
				R.id.widget_list_view);
		
		for (int i : manager.getAppWidgetIds(name)) {
			RemoteViews views = new RemoteViews(context.getPackageName(),
					R.layout.roadsigns_appwidget);

			// Update button icon
			int imageId = isTracking ? R.drawable.ic_action_location_searching
					: R.drawable.ic_action_location_found;
			views.setImageViewResource(R.id.tracking_button, imageId);

			Intent intent = new Intent(context, WidgetService.class);
			intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, i);
			intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));

			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				views.setRemoteAdapter(i, R.id.widget_list_view, intent);
			else
				views.setRemoteAdapter(R.id.widget_list_view, intent);

			views.setEmptyView(R.id.widget_list_view, android.R.id.empty);

			// Set up list item callback
			Intent processItemIntent = new Intent(context, PointsActivity.class);
			processItemIntent.setAction(PointsActivity.SHOW_DETAILS);

			processItemIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, i);
			processItemIntent.setData(Uri.parse(processItemIntent
					.toUri(Intent.URI_INTENT_SCHEME)));
			
			PendingIntent pendingItemIntent = PendingIntent.getActivity(
					context, 0, processItemIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
			
			/*PendingIntent pendingItemIntent = PendingIntent.getBroadcast(
					context, 0, processItemIntent, PendingIntent.FLAG_UPDATE_CURRENT);*/
			
			views.setPendingIntentTemplate(R.id.widget_list_view,
					pendingItemIntent);
			
			// Set up 'tracking button' callback
			Intent buttonIntent = new Intent(context, UpdateService.class);
			buttonIntent.setAction(UpdateService.TRACKING_BUTTON_CLICKED);
			PendingIntent pendingIntent = PendingIntent.getService(context, 0,
					buttonIntent, 0);

			views.setOnClickPendingIntent(R.id.tracking_button, pendingIntent);
			
			manager.updateAppWidget(i, views);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		log("UpdateService.onStartCommand " + intent);
		
		if (intent != null && intent.getAction() != null
				&& intent.getAction().equals(TRACKING_BUTTON_CLICKED)) {
			isTracking = !isTracking;
			updateRemoteViews(this);
			
			if (isTracking)
				startTracking();
			else
				stopTracking();
			
			return START_STICKY;
		}
		
		return super.onStartCommand(intent, flags, startId);
	}
}