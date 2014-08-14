package org.fruct.oss.ikm.fragment;

import org.fruct.oss.ikm.utils.Utils;
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
import android.view.MotionEvent;

// TODO: in this class some GeoPoint's creations on every draw call
public class MyPositionOverlay extends Overlay {
	interface OnScrollListener {
		void onScroll();
	}
	private OnScrollListener listener;

	private MapView mapView;
	private Location location;
	private Location matchedLocation;
	
	private Point point = new Point();
	private Point centerPoint = new Point();
	
	private Path path = new Path();
	
	private Paint paint;
	private Paint paintRed;
	private Paint paintAccuracy;
	private Paint paintRad;

	private boolean isPaintAccuracy;
	
	private int arrowWidth;
	private int arrowHeight;
	
	public MyPositionOverlay(Context ctx, MapView mapView) {
		super(ctx);
		
		this.mapView = mapView;		
		
		paint = new Paint();
		paint.setColor(0xff00ffff);
		paint.setStyle(Style.STROKE);
		
		paintRed = new Paint();
		paintRed.setColor(0x99ff0000);
		paintRed.setStyle(Style.FILL);
		paintRed.setAntiAlias(true);
		
		paintAccuracy = new Paint();
		paintAccuracy.setColor(0x1162A4B6);
		paintAccuracy.setStyle(Style.FILL);
		paintAccuracy.setAntiAlias(true);
		
		paintRad = new Paint();
		paintRad.setColor(0x11B66284);
		paintRad.setStyle(Style.FILL);
		paintRad.setAntiAlias(true);
		
		arrowWidth = Utils.getDP(7);
		arrowHeight = Utils.getDP(32);
	}
	
	@Override
	protected void draw(Canvas canvas, MapView mapView, boolean shadow) {
		if (shadow) {
			return;
		}

		if (location == null) {
			return;
		}

		drawLocation(canvas);
		drawMatchedLocation(canvas);
	}

	private void drawMatchedLocation(Canvas canvas) {
		if (matchedLocation == null)
			return;

		Projection proj = mapView.getProjection();
		proj.toMapPixels(new GeoPoint(matchedLocation), point);
		canvas.drawCircle(point.x, point.y, 8, paintRed);
	}

	private void drawLocation(Canvas canvas) {
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

		path.reset();
		
		path.moveTo(0, arrowWidth);
		path.lineTo(-arrowWidth, 0);
		path.lineTo(0, -arrowHeight);
		path.lineTo(arrowWidth, 0);
		path.close();
		
		canvas.drawPath(path, paintRed);
		canvas.restore();
	}



	public void setLocation(Location myLocation) {
		this.location = myLocation;
	}

	public void setMatchedLocation(Location matchedLocation) {
		this.matchedLocation = matchedLocation;
	}

	public void setShowAccuracy(boolean isShow) {
		this.isPaintAccuracy = isShow;
	}

	@Override
	public boolean onScroll(MotionEvent pEvent1, MotionEvent pEvent2, float pDistanceX, float pDistanceY, MapView pMapView) {
		if (listener != null)
			listener.onScroll();

		return super.onScroll(pEvent1, pEvent2, pDistanceX, pDistanceY, pMapView);
	}

	public void setListener(OnScrollListener listener) {
		this.listener = listener;
	}

	public void clearListener() {
		this.listener = null;
	}
}
