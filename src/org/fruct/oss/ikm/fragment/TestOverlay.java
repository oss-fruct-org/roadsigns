package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.Direction.RelativeDirection;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.SafeDrawOverlay;
import org.osmdroid.views.safecanvas.ISafeCanvas;
import org.osmdroid.views.safecanvas.SafePaint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.widget.Button;

class Item {
	static final int MARGIN = 4;
	static final int PADDING = 4;
	
	PointDesc pointDesc;
	int width, height;
	
	public static SafePaint paint = new SafePaint();
	public static SafePaint paint2 = new SafePaint();
	
	static {
		paint.setColor(0x10000000);
		
		paint2.setColor(0xffffffff);
		paint2.setTextAlign(Align.LEFT);
		paint2.setTextSize(16);
		paint2.setAntiAlias(true);
	}
	
	Item(int width, int height, PointDesc point) {
		this.width = width;
		this.height = height;
		this.pointDesc = point;
	}
	
	void draw(ISafeCanvas canvas) {
		/*Button button = new Button(App.getContext());
		button.setText("Hello world");
		
		Bitmap bm = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
		Canvas bmCanvas = new Canvas(bm);
		button.draw(bmCanvas);*/
		
		canvas.drawRect(MARGIN, MARGIN, width - MARGIN, height - MARGIN, paint);
		//canvas.getWrappedCanvas().drawBitmap(bm, 0, 0, new Paint());
		//canvas.getSafeCanvas().drawCircle(0, 0, 10, new Paint());
		canvas.drawText(pointDesc.getName(), MARGIN + PADDING, height / 2, paint2);
	}
}

class DirectionPanel {
	private int x;
	private int y;
	private int width;
	private int height;

	private List<Direction> directions;
	private List<Item> items;

	private SafePaint borderPaint;
	
	DirectionPanel(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		
		
		borderPaint = new SafePaint();
		borderPaint.setColor(0x20080040);
	}
	
	void setSize(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	void setDirections(List<Direction> directions) {
		this.directions = directions;
		
		// Update items
		items = new ArrayList<Item>();
		for (Direction dir : directions) {
			// Points in direction
			List<PointDesc> points = dir.getPoints();
		
			for (PointDesc point : points) {
				items.add(new Item(128, 64, point));
			}
		}
	}

	void draw(ISafeCanvas canvas) {
		if (directions == null || directions.isEmpty())
			return;
		
		canvas.drawRect(x, y, width + x, height + y, borderPaint);
		
		
		int pos = 0;
		for (Item item : items) {
			canvas.save();
			canvas.translate(x, y + pos);
			
			item.draw(canvas);
			
			canvas.restore();
			
			pos += 1 + item.height;
		}
	}

	public boolean onSingleTapUp(int xx, int yy, MapView mapView) {
		Utils.log("Overlay panel clicked xx=%d, yy=%d, x=%d, y=%d, width=%d, height=%d", xx, yy, x, y, width, height);
		if (xx >= x && yy >= y && xx < width + x && yy < height + y) {
			Utils.log("Hello world");
			return true;
		}
		
		return false;
	}
}

public class TestOverlay extends SafeDrawOverlay {
	public static final int PANEL_WIDTH = 128;
	
	private MapView mapView;
	private Matrix matrix = new Matrix();

	private List<Direction> directions;
	private float bearing;

	private EnumMap<Direction.RelativeDirection, DirectionPanel> panels = new EnumMap<Direction.RelativeDirection, DirectionPanel>(
			Direction.RelativeDirection.class);

	public TestOverlay(Context ctx, MapView mapView) {
		super(ctx);

		this.mapView = mapView;

		panels.put(RelativeDirection.LEFT, new DirectionPanel(0, 40, 40, mapView.getHeight() - 80));
		panels.put(RelativeDirection.RIGHT, new DirectionPanel(mapView.getWidth() - 40, 40, 40, mapView.getHeight() - 80));
	}

	private Point point = new Point();
	@Override
	protected void drawSafe(ISafeCanvas canvas, MapView mapView, boolean shadow) {
		if (shadow == true)
			return;
		
		Projection proj = mapView.getProjection();
		
		matrix.setTranslate(0, 0);
		matrix.postTranslate(0, 0);

		canvas.save();

		proj.toMapPixels(mapView.getMapCenter(), point);
		canvas.translate(point.x, point.y);
		canvas.rotate(-mapView.getMapOrientation());
		canvas.translate(-mapView.getWidth() / 2, -mapView.getHeight() / 2);

		panels.get(RelativeDirection.LEFT).setSize(0, PANEL_WIDTH, PANEL_WIDTH, mapView.getHeight() - PANEL_WIDTH * 2);
		panels.get(RelativeDirection.RIGHT).setSize(mapView.getWidth() - PANEL_WIDTH, PANEL_WIDTH, PANEL_WIDTH, mapView.getHeight() - PANEL_WIDTH * 2);
		
		for (Entry<Direction.RelativeDirection, DirectionPanel> entry : panels.entrySet()) {
			RelativeDirection dir = entry.getKey();
			DirectionPanel panel = entry.getValue();
			
			panel.draw(canvas);
		}
		
		canvas.restore();
	}

	public void setDirections(List<Direction> directions, final float bearing) {
		this.directions = directions;
		this.bearing = bearing;
		
		for (Entry<Direction.RelativeDirection, DirectionPanel> entry : panels.entrySet()) {
			final RelativeDirection relDir = entry.getKey();
			final DirectionPanel panel = entry.getValue();
			
			ArrayList<Direction> resultList = new ArrayList<Direction>();
			Utils.select(directions, resultList, new Utils.Predicate<Direction>() {
				public boolean apply(Direction dir) {
					return dir.getRelativeDirection(bearing) == relDir;
				};
			});
			
			panel.setDirections(resultList);
		}
	}
	
	@Override
	public boolean onSingleTapUp(MotionEvent event, MapView mapView) {
		Utils.log(""+ event.getX() + "  " + event.getY());
		
		Projection proj = mapView.getProjection();
		Rect rect = proj.getScreenRect();
		Point p = proj.fromMapPixels((int) event.getX(), (int) event.getY(), null);
		
		for (DirectionPanel panel : panels.values()) {
			if (panel.onSingleTapUp(p.x - rect.centerX() + rect.width() / 2,
					                p.y - rect.centerY() + rect.height() / 2, mapView))
				return true;
		}
		
		return super.onTouchEvent(event, mapView);
	}
}
