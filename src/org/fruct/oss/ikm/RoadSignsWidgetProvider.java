package org.fruct.oss.ikm;

import static org.fruct.oss.ikm.Utils.log;

import java.util.ArrayList;

import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.StubPointProvider;
import org.fruct.oss.ikm.service.DirectionService;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;



public class RoadSignsWidgetProvider extends AppWidgetProvider {
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		super.onUpdate(context, appWidgetManager, appWidgetIds);
		
		Intent intent = new Intent(context, UpdateService.class);
		context.startService(intent);
	}
	
	public static class UpdateService extends Service {
		private BroadcastReceiver directionsReceiver;

		@Override
		public IBinder onBind(Intent arg0) {
			return null;
		}
		
		@Override
		public void onCreate() {
			log("on create");
			super.onCreate();
			LocalBroadcastManager.getInstance(this).registerReceiver(directionsReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					log("qwe asd zxc");
				}
			}, new IntentFilter(DirectionService.GET_DIRECTIONS_READY));
		
			Intent intent = new Intent(this, DirectionService.class);
			intent.setAction(DirectionService.START_FOLLOWING);
			intent.putParcelableArrayListExtra(DirectionService.POINTS, 
					new ArrayList<PointDesc>(new StubPointProvider().getPoints(0, 0, 0)));
			startService(intent);
		}
		
		@Override
		public void onDestroy() {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(directionsReceiver);
			
			stopService(new Intent(this, DirectionService.class));
			
			super.onDestroy();
		}
	}
}
