package org.fruct.oss.ikm;

import static org.fruct.oss.ikm.Utils.log;

import java.util.ArrayList;

import org.fruct.oss.ikm.poi.StubPointProvider;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.DirectionService;
import org.osmdroid.util.GeoPoint;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.RemoteViews;



public class RoadSignsWidgetProvider extends AppWidgetProvider {
	@Override
	public void onEnabled(Context context) {
		super.onEnabled(context);

		context.startService(new Intent(context, UpdateService.class));
	}
	
	@Override
	public void onDisabled(Context context) {
		context.stopService(new Intent(context, UpdateService.class));
	}
	
	public static class UpdateService extends Service {
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
				directionService.startFollowing(new StubPointProvider().getPoints(0, 0, 0));
			}
		};

		@Override
		public IBinder onBind(Intent arg0) {
			return null;
		}
		
		@Override
		public void onCreate() {
			super.onCreate();
			
			Intent intent = new Intent(this, DirectionService.class);
			bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
			
			LocalBroadcastManager.getInstance(this).registerReceiver(directionsReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					log("RoadSignsWidgetProvidet: update widget");
					AppWidgetManager manager = AppWidgetManager.getInstance(context);

					ArrayList<Direction> directions = intent.getParcelableArrayListExtra(DirectionService.DIRECTIONS_RESULT);
					GeoPoint geoPoint = (GeoPoint) intent.getParcelableExtra(DirectionService.CENTER);
					
					ComponentName name = new ComponentName(context, RoadSignsWidgetProvider.class);
					RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.roadsigns_appwidget);
					
					views.setTextViewText(R.id.textView1, "Size: " + directions.size());
					manager.updateAppWidget(name, views);
				}
			}, new IntentFilter(DirectionService.DIRECTIONS_READY));
		}
		
		@Override
		public void onDestroy() {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(directionsReceiver);
			
			unbindService(serviceConnection);
			
			super.onDestroy();
		}
	}
}
