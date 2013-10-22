package org.fruct.oss.ikm.appwidget;

import static org.fruct.oss.ikm.Utils.log;

import java.util.ArrayList;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.StubPointProvider;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.DirectionService;

import android.annotation.TargetApi;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.RemoteViews;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class UpdateService extends Service {
	private BroadcastReceiver directionsReceiver;
	private DirectionService directionService;
	
	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			directionService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			directionService = ((DirectionService.DirectionBinder) service).getService();
			directionService.startTracking(new StubPointProvider().getPoints(0, 0, 0));
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		Utils.log("UpdateService.onCreate");
		
		Intent intent = new Intent(this, DirectionService.class);
		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
		
		LocalBroadcastManager.getInstance(this).registerReceiver(directionsReceiver = new BroadcastReceiver() {
			@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			@Override
			public void onReceive(Context context, Intent intent) {
				log("RoadSignsWidgetProvider: onReceive");
				AppWidgetManager manager = AppWidgetManager.getInstance(context);

				ArrayList<Direction> directions = intent.getParcelableArrayListExtra(DirectionService.DIRECTIONS_RESULT);
				Location location = (Location) intent.getParcelableExtra(DirectionService.LOCATION);
				
				ComponentName name = new ComponentName(context, RoadSignsWidgetProvider.class);
      
				RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.roadsigns_appwidget);
				
				views.setTextViewText(R.id.textView1, "Size: " + directions.size());
				manager.updateAppWidget(name, views);
				
				ListProvider.sdirections = directions;
				ListProvider.slocation = location;
				
				manager.notifyAppWidgetViewDataChanged(manager.getAppWidgetIds(name), R.id.widget_list_view);
			}
		}, new IntentFilter(DirectionService.DIRECTIONS_READY));
	}
	
	@Override
	public void onDestroy() {
		Utils.log("UpdateService.onDestroy");
		LocalBroadcastManager.getInstance(this).unregisterReceiver(directionsReceiver);
		
		unbindService(serviceConnection);
		
		super.onDestroy();
	}
}