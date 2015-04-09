package org.fruct.oss.ikm.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.view.MotionEvent;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.assist.ViewScaleType;
import com.nostra13.universalimageloader.core.imageaware.NonViewAware;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.drawer.DrawerActivity;
import org.fruct.oss.ikm.points.Point;
import org.fruct.oss.ikm.utils.Utils;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class PointsOverlay extends Overlay {
	private final int itemSizeLarge;
	private final int itemSizeSmall;

	private final DisplayImageOptions displayOptions;
	private final MapView mapView;

	private List<Point> points = new ArrayList<>();
	private List<Item> items = new ArrayList<>();

	private Rect screenClipRect = new Rect();

	private android.graphics.Point point1 = new android.graphics.Point();

	private final Drawable markerDrawable;
	private final Drawable markerClickedDrawable;
	private final Drawable markerCluster;

	private Rect markerPadding = new Rect();

	private Item touchedItem;

	private int lastUpdateZoom = -1;

	public PointsOverlay(Context ctx, MapView mapView) {
		super(ctx);
		this.mapView = mapView;

		markerDrawable = ctx.getResources().getDrawable(R.drawable.marker);
		markerClickedDrawable = ctx.getResources().getDrawable(R.drawable.marker_clicked);
		markerCluster = ctx.getResources().getDrawable(R.drawable.marker_cluster);
		assert markerDrawable != null;

		markerDrawable.getPadding(markerPadding);

		itemSizeLarge = Utils.getDP(24);
		itemSizeSmall = itemSizeLarge / 2;

		displayOptions = new DisplayImageOptions.Builder()
				.cacheOnDisk(true)
				.cacheInMemory(false)
				.imageScaleType(ImageScaleType.EXACTLY)
				.build();
	}

	public void setPoints(List<Point> points) {
		this.points = Collections.unmodifiableList(points);
		lastUpdateZoom = -1;
	}

	private void updateItems() {
		items.clear();
		lastUpdateZoom = mapView.getZoomLevel();

		List<Cluster> clusters = new ArrayList<>();

		double clusteringRadius = convertRadiusToMeters(mapView);
		List<Point> tmpPoints = new ArrayList<>(points);

		while (!tmpPoints.isEmpty()) {
			Point point = tmpPoints.remove(tmpPoints.size() - 1);

			Cluster cluster = new Cluster(point.toPoint());
			cluster.addPoint(point);

			Iterator<Point> iterator = tmpPoints.iterator();
			while (iterator.hasNext()) {
				Point otherPoint = iterator.next();
				if (otherPoint.toPoint().distanceTo(point.toPoint()) < clusteringRadius) {
					cluster.addPoint(otherPoint);
					iterator.remove();
				}
			}

			clusters.add(cluster);
		}

		for (Cluster cluster : clusters) {
			if (cluster.points.size() == 1) {
				Point point = cluster.points.get(0);
				if (point.hasPhoto()) {
					items.add(new PhotoPointItem(point.toPoint(), point));
				} else {
					items.add(new PointItem(point.toPoint(), point));
				}
			} else {
				items.add(cluster);
			}
		}
	}

	@Override
	protected void draw(Canvas canvas, MapView mapView, boolean shadow) {
		if (shadow) {
			return;
		}

		if (lastUpdateZoom != mapView.getZoomLevel()) {
			updateItems();
		}

		mapView.getIntrinsicScreenRect(screenClipRect);
		screenClipRect.inset(-itemSizeLarge, -itemSizeLarge);

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
			// Allow collect this bitmap
			item.onScreenOut();
			return;
		}

		item.onDraw(canvas, mapView, point1);

		// If item returned to screen, then store strong reference again

	}

	@Override
	public boolean onTouchEvent(MotionEvent event, MapView mapView) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			touchedItem = testHit(event, mapView);

			if (touchedItem != null) {
				mapView.invalidate();
				return true;
			} else {
				return false;
			}
		} else if (touchedItem != null && event.getAction() == MotionEvent.ACTION_UP) {
			touchedItem.onClick();

			touchedItem = null;
			mapView.invalidate();
			return true;
		}

		return false;
	}

	private boolean testHit(MotionEvent e, MapView mapView, Item item) {
		final Projection proj = mapView.getProjection();
		final Rect screenRect = proj.getIntrinsicScreenRect();

		final int x = screenRect.left + (int) e.getX();
		final int y = screenRect.top + (int) e.getY();

		proj.toPixels(item.geoPoint, point1);

		final int ix = point1.x - x;
		final int iy = point1.y - y;

		return ix >= -itemSizeLarge && iy >= 0 && ix <= itemSizeLarge && iy <= 2 * itemSizeLarge;
	}

	private Item testHit(MotionEvent e, MapView mapView) {
		for (Item item : items) {
			if (testHit(e, mapView, item)) {
				return item;
			}
		}
		return null;
	}

	// Copypasted and modified from
	// https://github.com/MKergall/osmbonuspack/blob/master/OSMBonusPack/src/org/osmdroid/bonuspack/clustering/RadiusMarkerClusterer.java
	private double convertRadiusToMeters(MapView mapView) {
		Rect screenRect = mapView.getIntrinsicScreenRect(null);

		int screenWidth = screenRect.right - screenRect.left;
		int screenHeight = screenRect.bottom - screenRect.top;

		BoundingBoxE6 bb = mapView.getBoundingBox();

		double diagonalInMeters = bb.getDiagonalLengthInMeters();
		double diagonalInPixels = Math.sqrt(screenWidth * screenWidth + screenHeight * screenHeight);
		double metersInPixel = diagonalInMeters / diagonalInPixels;

		// 2 * itemSizeLarge = clustering radius
		return 2 * itemSizeLarge * metersInPixel;
	}

	private class Item {
		final GeoPoint geoPoint;


		Item(GeoPoint geoPoint) {
			this.geoPoint = geoPoint;
		}

		boolean isTouched() {
			return this == touchedItem;
		}

		public void onScreenOut() {

		}

		public void onDraw(Canvas canvas, MapView mapView, android.graphics.Point position) {

		}

		public void onClick() {

		}
	}

	private class Cluster extends Item {
		List<Point> points = new ArrayList<>();

		Cluster(GeoPoint geoPoint) {
			super(geoPoint);
		}

		public void addPoint(Point point) {
			points.add(point);
		}

		@Override
		public void onDraw(Canvas canvas, MapView mapView, android.graphics.Point position) {
			super.onDraw(canvas, mapView, position);

			int width = markerCluster.getIntrinsicWidth();
			int height = markerCluster.getIntrinsicHeight();

			markerCluster.setBounds(point1.x - width / 2,
					point1.y - height / 2,
					point1.x + width / 2,
					point1.y + height / 2);
			markerCluster.draw(canvas);
		}
	}

	private class PointItem extends Item {
		final Point point;

		public PointItem(GeoPoint geoPoint, Point point) {
			super(geoPoint);
			this.point = point;
		}

		int getSize() {
			return itemSizeSmall;
		}

		@Override
		public void onDraw(Canvas canvas, MapView mapView, android.graphics.Point position) {
			int size = getSize();

			Drawable drawable = isTouched() ? markerClickedDrawable : markerDrawable;
			drawable.setBounds(point1.x - size - markerPadding.left,
					point1.y - 2 * size - markerPadding.bottom - markerPadding.top,
					point1.x + size + markerPadding.right,
					point1.y);
			drawable.draw(canvas);
		}

		@Override
		public void onClick() {
			super.onClick();

			Intent intent = new Intent(mapView.getContext(), DrawerActivity.class);
			intent.setAction(PointsFragment.ACTION_SHOW_DETAILS);
			intent.putExtra(DetailsFragment.ARG_POINT, (Parcelable) point);
			mapView.getContext().startActivity(intent);
		}
	}

	private class PhotoPointItem extends PointItem {
		Bitmap keeperReference;
		boolean iconRequested;
		Reference<Bitmap> iconBitmap = new WeakReference<>(null);

		public PhotoPointItem(GeoPoint geoPoint, Point point) {
			super(geoPoint, point);
		}

		int getSize() {
			return itemSizeLarge;
		}

		@Override
		public void onScreenOut() {
			super.onScreenOut();
			keeperReference = null;
			iconRequested = false;
		}

		@Override
		public void onDraw(Canvas canvas, MapView mapView, android.graphics.Point position) {
			super.onDraw(canvas, mapView, position);

			Drawable drawable = isTouched() ? markerClickedDrawable : markerDrawable;

			Bitmap iconBitmap = keeperReference = this.iconBitmap.get();
			if (!iconRequested && iconBitmap == null) {
				iconRequested = true;

				String photoUrl = point.getPhoto();
				ImageLoader.getInstance().displayImage(photoUrl,
						new ItemImageAware(photoUrl, new ImageSize(itemSizeLarge * 2,
								itemSizeLarge * 2),
								this),
						displayOptions);
			}

			Rect bounds = drawable.getBounds();
			if (iconBitmap != null) {
				canvas.save();
				canvas.clipRect(bounds.left + markerPadding.left, bounds.top + markerPadding.top,
						bounds.right - markerPadding.right, bounds.bottom - markerPadding.bottom);
				canvas.drawBitmap(iconBitmap, bounds.left + markerPadding.left,
						bounds.top + markerPadding.top, null);
				canvas.restore();
			}
		}
	}

	class ItemImageAware extends NonViewAware {
		private final PhotoPointItem item;

		public ItemImageAware(String imageUri, ImageSize imageSize, PhotoPointItem item) {
			super(imageUri, imageSize, ViewScaleType.CROP);
			this.item = item;
		}

		@Override
		public boolean setImageBitmap(Bitmap bitmap) {
			item.keeperReference = bitmap;
			item.iconBitmap = new SoftReference<>(bitmap);

			mapView.invalidate();
			return true;
		}
	}
}
