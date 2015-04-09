package org.fruct.oss.ikm.fragment;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.points.Point;
import org.fruct.oss.ikm.utils.Utils;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;

public class PointsOverlay extends Overlay {
	private final int itemSize;

	private List<Item> items = new ArrayList<>();

	private Rect screenClipRect = new Rect();

	private android.graphics.Point point1 = new android.graphics.Point();

	private Drawable markerDrawable;
	private Rect markerPadding = new Rect();


	public PointsOverlay(Context ctx) {
		super(ctx);

		markerDrawable = ctx.getResources().getDrawable(R.drawable.marker);
		assert markerDrawable != null;

		markerDrawable.getPadding(markerPadding);

		itemSize = Utils.getDP(24);
	}

	public void setPoints(List<Point> points) {
		items.clear();
		for (Point point : points) {
			items.add(new Item(point.toPoint(), point));
		}
	}

	@Override
	protected void draw(Canvas canvas, MapView mapView, boolean shadow) {
		if (shadow) {
			return;
		}

		mapView.getIntrinsicScreenRect(screenClipRect);
		screenClipRect.inset(-itemSize, -itemSize);

		drawItems(canvas, mapView);
	}

	private void drawItems(Canvas canvas, MapView mapView) {
		for (Item item : items) {
			drawItem(item, canvas, mapView);
		}
	}

	private void drawItem(Item item, Canvas canvas, MapView mapView) {
		Projection proj = mapView.getProjection();

		proj.toPixels(item.geoPoint, point1);

		if (!screenClipRect.contains(point1.x, point1.y)) {
			return;
		}

		markerDrawable.setBounds(point1.x - itemSize - markerPadding.left,
				point1.y - 2 * itemSize - markerPadding.bottom - markerPadding.top,
				point1.x + itemSize + markerPadding.right,
				point1.y);
		markerDrawable.draw(canvas);
	}

	private class Item {
		private final GeoPoint geoPoint;
		private final Point point;

		Item(GeoPoint geoPoint, Point point) {
			this.geoPoint = geoPoint;
			this.point = point;
		}

	}
}
