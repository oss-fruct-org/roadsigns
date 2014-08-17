package org.fruct.oss.ikm.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.support.v4.content.LocalBroadcastManager;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestLinesOverlay extends Overlay implements Closeable {
	public static final String BROADCAST = "org.fruct.oss.ikm.TEST_LINES_OVERLAY";

	private final Context ctx;
	private final Paint linePaint;
	private final MapView mapView;
	private final Paint pointPaint;

	private List<Line> lineList = new ArrayList<Line>();
	private Point point1 = new Point();
	private Point point2 = new Point();

	public TestLinesOverlay(Context ctx, MapView mapView) {
		super(ctx);
		this.ctx = ctx;
		this.mapView = mapView;

		LocalBroadcastManager.getInstance(ctx).registerReceiver(receiver, new IntentFilter(BROADCAST));

		linePaint = new Paint();
		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setStrokeWidth(8);
		linePaint.setColor(0xFF5A009D);
		linePaint.setAntiAlias(true);

		pointPaint = new Paint();
		pointPaint.setStyle(Paint.Style.FILL);
		pointPaint.setColor(0xFFee4400);
		pointPaint.setAntiAlias(true);
	}


	@Override
	public void close() throws IOException {
		LocalBroadcastManager.getInstance(ctx).unregisterReceiver(receiver);
	}

	@Override
	protected void draw(Canvas c, MapView mapView, boolean shadow) {
		if (!shadow) {
			return;
		}

		Projection proj = mapView.getProjection();

		for (Line line : lineList) {
			proj.toPixels(line.from, point1);
			proj.toPixels(line.to, point2);

			c.drawLine(point1.x, point1.y, point2.x, point2.y, linePaint);
			c.drawCircle(point2.x, point2.y, 16, pointPaint);
		}
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			double[] lines = intent.getDoubleArrayExtra("lines");

			lineList.clear();
			for (int i = 0; i < lines.length; i += 4) {
				lineList.add(new Line(new GeoPoint(lines[i], lines[i + 1]),
						new GeoPoint(lines[i + 2], lines[i + 3])));
			}

			mapView.invalidate();
		}
	};

	private static class Line {
		private Line(GeoPoint from, GeoPoint to) {
			this.from = from;
			this.to = to;
		}

		GeoPoint from;
		GeoPoint to;
	}
}
