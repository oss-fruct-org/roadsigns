package org.fruct.oss.ikm.fragment;

import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;

import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.Direction.RelativeDirection;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.SafeDrawOverlay;
import org.osmdroid.views.safecanvas.ISafeCanvas;
import org.osmdroid.views.safecanvas.SafePaint;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Picture;

class DirectionPanel {
	private int x;
	private int y;
	private int width;
	private int height;

	private List<Direction> directions;

	private SafePaint borderPaint;
	
	DirectionPanel(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		
		
		borderPaint = new SafePaint();
		borderPaint.setColor(0xff000000);
	}

	void setDirections(List<Direction> directions) {
		this.directions = directions;
	}

	void draw(ISafeCanvas canvas, List<Direction> directions) {
		canvas.drawRect(x, y, width + x, height + y, borderPaint);
	}
}

public class TestOverlay extends SafeDrawOverlay {
	private MapView mapView;
	private Picture picture = new Picture();
	private Matrix matrix = new Matrix();

	private List<Direction> directions;
	private float bearing;

	private EnumMap<Direction.RelativeDirection, DirectionPanel> panels = new EnumMap<Direction.RelativeDirection, DirectionPanel>(
			Direction.RelativeDirection.class);

	public TestOverlay(Context ctx, MapView mapView) {
		super(ctx);

		this.mapView = mapView;
		createPicture();
		
		panels.put(RelativeDirection.LEFT, new DirectionPanel(0, 40, 40, 80));
		panels.put(RelativeDirection.RIGHT, new DirectionPanel(mapView.getWidth() - 40, 40, 40, 80));
	}

	@Override
	protected void drawSafe(ISafeCanvas canvas, MapView mapView, boolean shadow) {
		matrix.setTranslate(0, 0);
		matrix.postTranslate(0, 0);

		canvas.save();
		canvas.setMatrix(matrix);

		canvas.drawPicture(picture);

		for (Entry<Direction.RelativeDirection, DirectionPanel> entry : panels.entrySet()) {
			DirectionPanel panel = entry.getValue();
			RelativeDirection dir = entry.getKey();
			
			panel.draw(canvas, directions);
		}
		
		canvas.restore();
	}

	public void setDirections(List<Direction> directions, float bearing) {
		this.directions = directions;
		this.bearing = bearing;
	}

	private void createPicture() {
		Paint paint = new Paint();
		paint.setColor(0xffffff00);

		Canvas canvas = picture.beginRecording(32, 32);
		canvas.drawRect(0, 0, 32, 32, paint);

		picture.endRecording();
	}
}
