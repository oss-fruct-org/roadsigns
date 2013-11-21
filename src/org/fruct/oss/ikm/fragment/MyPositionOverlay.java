package org.fruct.oss.ikm.fragment;

import org.fruct.oss.ikm.Utils;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.Overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Point;
import android.location.Location;

public class MyPositionOverlay extends Overlay {
	private MapView mapView;
	private Location location;
	
	private Point point = new Point();
	private Point centerPoint = new Point();
	
	private Path path = new Path();
	
	private Paint paint;
	private Paint paintRed;
	private Paint paintAccuracy;
	
	private boolean isPaintAccuracy;
	
	public MyPositionOverlay(Context ctx, MapView mapView) {
		super(ctx);
		
		this.mapView = mapView;		
		
		paint = new Paint();
		paint.setColor(0xff00ffff);
		paint.setStyle(Style.STROKE);
		
		paintRed = new Paint();
		paintRed.setColor(0xffff0000);
		paintRed.setStyle(Style.FILL);
		paintRed.setAntiAlias(true);
		
		paintAccuracy = new Paint();
		paintAccuracy.setColor(0x1162A4B6);
		paintAccuracy.setStyle(Style.FILL_AND_STROKE);
		paintAccuracy.setAntiAlias(true);
	}
	
	@Override
	protected void draw(Canvas canvas, MapView mapView, boolean shadow) {
		if (location == null) {
			return;
		}
		
		Projection proj = mapView.getProjection();
		GeoPoint locationPoint = new GeoPoint(location);
		IGeoPoint mapCenter = mapView.getMapCenter();
		
		proj.toMapPixels(locationPoint, point);
		proj.toMapPixels(mapCenter, centerPoint);
		
		canvas.save();
		
		canvas.rotate(location.getBearing(), point.x, point.y);
		canvas.translate(point.x, point.y);

		if (isPaintAccuracy) {
			float pixels = 2 * proj.metersToEquatorPixels(location.getAccuracy());
			canvas.drawCircle(0, 0, pixels, paintAccuracy);
		}
		
		drawArrow(canvas);
		
		canvas.restore();
	}
	
	private void drawArrow(Canvas canvas) {
		path.reset();
		
		path.moveTo(0, 5);
		path.lineTo(-5, 0);
		path.lineTo(0, -20);
		path.lineTo(5, 0);
		path.close();
		
		canvas.drawPath(path, paintRed);
	}

	public void setLocation(Location myLocation) {
		this.location = myLocation;
	}
	
	public void setShowAccuracy(boolean isShow) {
		this.isPaintAccuracy = isShow;
	}
}
