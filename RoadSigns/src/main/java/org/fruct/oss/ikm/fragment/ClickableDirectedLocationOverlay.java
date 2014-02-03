package org.fruct.oss.ikm.fragment;

import java.util.List;

import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.DirectedLocationOverlay;
import org.osmdroid.views.overlay.Overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.view.MotionEvent;

public class ClickableDirectedLocationOverlay extends Overlay {
    public static interface Listener {
		void onClick();
	}
	
	private Point screenPoint = new Point();
    private Paint paint = new Paint();

    private GeoPoint location;
    private float bearing;

    private final Bitmap ARROW;
    private final DefaultResourceProxyImpl resourceProxy;


    public ClickableDirectedLocationOverlay(Context ctx, GeoPoint location, float bearing) {
		super(ctx);

        this.location = location;
        this.bearing = bearing;

        resourceProxy = new DefaultResourceProxyImpl(ctx);
        ARROW = resourceProxy.getBitmap(ResourceProxy.bitmap.direction_arrow);
    }

    @Override
    public void draw(Canvas c, MapView mapView, boolean shadow) {
        if (shadow)
            return;

        if (location == null)
            return;

        Bitmap rotatedBitmap = ARROW;
        Projection proj = mapView.getProjection();
        proj.toMapPixels(location, screenPoint);

        c.drawBitmap(rotatedBitmap, screenPoint.x - rotatedBitmap.getWidth() / 2,
                screenPoint.y - rotatedBitmap.getHeight() / 2, paint);
    }

    @Override
	public boolean onSingleTapUp(MotionEvent e, MapView mapView) {
		Projection proj = mapView.getProjection();
		screenPoint = proj.toMapPixels(location, screenPoint);

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
