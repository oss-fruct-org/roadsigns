package org.fruct.oss.ikm.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.widget.Toast;

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
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class PointsOverlay extends Overlay {
	private final int itemSize;
	private final DisplayImageOptions displayOptions;
	private final MapView mapView;

	private List<Item> items = new ArrayList<>();

	private Rect screenClipRect = new Rect();

	private android.graphics.Point point1 = new android.graphics.Point();

	private final Drawable markerDrawable;
	private final Drawable markerClickedDrawable;

	private Rect markerPadding = new Rect();

	private Item touchedItem;


	public PointsOverlay(Context ctx, MapView mapView) {
		super(ctx);
		this.mapView = mapView;

		markerDrawable = ctx.getResources().getDrawable(R.drawable.marker);
		markerClickedDrawable = ctx.getResources().getDrawable(R.drawable.marker_clicked);
		assert markerDrawable != null;

		markerDrawable.getPadding(markerPadding);

		itemSize = Utils.getDP(24);

		displayOptions = new DisplayImageOptions.Builder()
				.cacheOnDisk(true)
				.cacheInMemory(false)
				.imageScaleType(ImageScaleType.EXACTLY)
				.build();
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
			// Allow collect this bitmap
			item.keeperReference = null;
			item.iconRequested = false;

			return;
		}

		// If item returned to screen, then store strong reference again
		Bitmap iconBitmap = item.keeperReference = item.iconBitmap.get();
		if (!item.iconRequested && iconBitmap == null && item.point.hasPhoto()) {
			item.iconRequested = true;

			String photoUrl = item.point.getPhoto();
			ImageLoader.getInstance().displayImage(photoUrl,
					new ItemImageAware(photoUrl, new ImageSize(itemSize * 2, itemSize * 2), item),
					displayOptions);
		}

		Drawable drawable = item == touchedItem ? markerClickedDrawable : markerDrawable;

		drawable.setBounds(point1.x - itemSize - markerPadding.left,
				point1.y - 2 * itemSize - markerPadding.bottom - markerPadding.top,
				point1.x + itemSize + markerPadding.right,
				point1.y);
		drawable.draw(canvas);

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

	@Override
	public boolean onTouchEvent(MotionEvent event, MapView mapView) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			touchedItem = testHit(event, mapView);
			mapView.invalidate();
		} else if (touchedItem != null && event.getAction() == MotionEvent.ACTION_UP) {
			Intent intent = new Intent(mapView.getContext(), DrawerActivity.class);
			intent.setAction(PointsFragment.ACTION_SHOW_DETAILS);
			intent.putExtra(DetailsFragment.ARG_POINT, (Parcelable) touchedItem.point);
			mapView.getContext().startActivity(intent);

			touchedItem = null;
			mapView.invalidate();
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

		return ix >= -itemSize && iy >= 0 && ix <= itemSize && iy <= 2 * itemSize;
	}

	private Item testHit(MotionEvent e, MapView mapView) {
		for (Item item : items) {
			if (testHit(e, mapView, item)) {
				return item;
			}
		}
		return null;
	}

	private class Item {
		private final GeoPoint geoPoint;
		private final Point point;

		Bitmap keeperReference;
		boolean iconRequested;
		Reference<Bitmap> iconBitmap = new WeakReference<>(null);

		Item(GeoPoint geoPoint, Point point) {
			this.geoPoint = geoPoint;
			this.point = point;
		}
	}

	class ItemImageAware extends NonViewAware {
		private final Item item;

		public ItemImageAware(String imageUri, ImageSize imageSize, Item item) {
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
