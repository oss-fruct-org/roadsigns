package org.fruct.oss.ikm.fragment;

import java.util.List;

import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.DirectedLocationOverlay;

import android.content.Context;
import android.graphics.Point;
import android.view.MotionEvent;

public class ClickableDirectedLocationOverlay extends DirectedLocationOverlay {
	public static interface Listener {
		void onClick();
	}
	
	private Point screenPoint;
	
	public ClickableDirectedLocationOverlay(Context ctx, MapView mapView, GeoPoint location, float bearing) {
		super(ctx);
		setLocation(location);
		setBearing(bearing);
	} 
	
	@Override
	public boolean onSingleTapUp(MotionEvent e, MapView mapView) {
		Projection proj = mapView.getProjection();
		screenPoint = proj.toMapPixels(mLocation, screenPoint);

		float mx = e.getX();
		float my = e.getY();
		
		Point marker = proj.fromMapPixels((int) mx, (int) my, null);
		
		float dx = screenPoint.x - marker.x;
		float dy = screenPoint.y - marker.y;
		
		float d2 = dx * dx + dy * dy;
		
		if (d2 < 32 * 32) {
			if (listener != null)
				listener.onClick();
			
			return true;
		} else
			return super.onSingleTapUp(e, mapView);
	}

	private Listener listener;
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}
}
