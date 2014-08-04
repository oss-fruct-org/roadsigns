package org.fruct.oss.ikm.appwidget;

import org.fruct.oss.ikm.utils.Utils;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViewsService;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class WidgetService extends RemoteViewsService {
	private ListProvider provider;
	private int id;
	
	@Override
	public RemoteViewsFactory onGetViewFactory(Intent intent) {
		Utils.log("WidgetService.onGetViewFactory");
		id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		return provider = new ListProvider(this.getApplicationContext(), intent);
	}

}
