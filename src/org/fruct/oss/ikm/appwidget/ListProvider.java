package org.fruct.oss.ikm.appwidget;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.Utils;
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

class ListItem {
	ListItem(PointDesc p, Direction direction) {
		this.point = p;
		this.direction = direction;
	}
	
	PointDesc point;
	Direction direction;
}

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ListProvider implements RemoteViewsService.RemoteViewsFactory {
	private ArrayList<ListItem> items = new ArrayList<ListItem>();
	
	private GeoPoint center;
	private Location location;
	
	private Context context;
	
	public static List<Direction> sdirections;
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
		PointDesc point = items.get(position).point;
		Direction direction = items.get(position).direction;
		
		// Absolute bearing of point of interest
		float pointDir = (float) center.bearingTo(direction.getDirection());
		
		// Absolute bearing of device
		float deviceDir = location.getBearing();
		
		float relativeBearing = Utils.normalizeAngle(pointDir - deviceDir);
		
		String directionDescription = "";
		int resId = R.drawable.ic_action_place;
		if (relativeBearing > 35 && relativeBearing < 135) {
			resId = R.drawable.arrow_90;
			directionDescription = "right";
		} else if (relativeBearing < -35 && relativeBearing > -135) {
			resId = R.drawable.arrow_270;
			directionDescription = "left";
		} else if (Math.abs(relativeBearing) < 35) {
			resId = R.drawable.arrow;
			directionDescription = "forward";
		} else if (Math.abs(relativeBearing) > 135) {
			resId = R.drawable.arrow_180;
			directionDescription = "back";
		}
		
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.direction_list_item);
		remoteViews.setTextViewText(android.R.id.text1, point.getName());
		remoteViews.setTextViewText(android.R.id.text2, directionDescription + "   " + relativeBearing);
		remoteViews.setImageViewResource(android.R.id.icon, resId);
	
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
		setDirections(sdirections, slocation);
	}

	@Override
	public void onDestroy() {
		
	}

	public void setDirections(List<Direction> directions, Location location) {
		if (directions == null || location == null)
			return;
		
		items.clear();

		
		this.location = location;

		
		for (Direction direction : directions) {
			center = direction.getCenter();
			for (PointDesc point : direction.getPoints()) {
				items.add(new ListItem(point, direction));
			}
		}
	}
}
