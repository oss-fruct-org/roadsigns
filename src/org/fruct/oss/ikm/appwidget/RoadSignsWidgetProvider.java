package org.fruct.oss.ikm.appwidget;

import org.fruct.oss.ikm.R;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;
import static org.fruct.oss.ikm.Utils.log;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
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
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		for (int i : appWidgetIds) {
			Intent intent = new Intent(context, WidgetService.class);
			intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, i);
			intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
			
			RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.roadsigns_appwidget);
			
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				rv.setRemoteAdapter(i, R.id.widget_list_view, intent);
			else
				rv.setRemoteAdapter(R.id.widget_list_view, intent);
			
			rv.setEmptyView(R.id.widget_list_view, android.R.id.empty);
			
			// Set up 'tracking button' callback
			Intent buttonIntent = new Intent(context, UpdateService.class);
			buttonIntent.setAction(UpdateService.TRACKING_BUTTON_CLICKED);
			PendingIntent pendingIntent = PendingIntent.getService(context, 0,
					buttonIntent, 0);
			
			rv.setOnClickPendingIntent(R.id.tracking_button, pendingIntent);

			appWidgetManager.updateAppWidget(i, rv);
		}
		
		super.onUpdate(context, appWidgetManager, appWidgetIds);
	}
}
