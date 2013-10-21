package org.fruct.oss.ikm.appwidget;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.service.Direction;
import org.osmdroid.util.GeoPoint;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ListProvider implements RemoteViewsService.RemoteViewsFactory {
	private ArrayList<PointDesc> items = new ArrayList<PointDesc>();
	private GeoPoint center;
	private Location location;
	
	private Context context;
	
	public static List<Direction> directions;
	public static Location slocation;
	
	public ListProvider(Context context, Intent intent) {
		this.context = context;
		/*this.id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);*/
	}
	
	@Override 
	public int getCount() {
		return items.size();
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public RemoteViews getLoadingView() {
		return null;
	}

	@Override
	public RemoteViews getViewAt(int position) {
		PointDesc point = items.get(position);
		
		// Absolute bearing of point of interest
		float pointDir = (float) center.bearingTo(point.toPoint());
		
		// Absolute bearing of device
		float deviceDir = location.getBearing();
		
		float relativeBearing = pointDir - deviceDir;
		
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.direction_list_item);
		remoteViews.setTextViewText(android.R.id.text1, items.get(position).getName());
		remoteViews.setTextViewText(android.R.id.text2, "" + relativeBearing);
		remoteViews.setImageViewResource(android.R.id.icon, R.drawable.ic_action_place);
	
		return remoteViews;
	}

	@Override
	public int getViewTypeCount() {
		return 1;
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public void onCreate() {
	}

	@Override
	public void onDataSetChanged() {
		setDirections(directions, slocation);
	}

	@Override
	public void onDestroy() {
		
	}

	public void setDirections(List<Direction> directions, Location location) {
		this.location = location;
		items.clear();
		if (directions == null)
			return;
		
		for (Direction direction : directions) {
			center = direction.getCenter();
			for (PointDesc point : direction.getPoints()) {
				items.add(point);
			}
		}
	}
}
