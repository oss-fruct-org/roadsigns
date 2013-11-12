package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.Direction.RelativeDirection;
import org.osmdroid.views.MapView;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class TestOverlay extends RelativeLayout {
	public int panel_width = 0;
	
	private MapView mapView;

	private List<Direction> directions;
	private float bearing;

	private EnumMap<Direction.RelativeDirection, DirectionsPanel> panels = new EnumMap<Direction.RelativeDirection, DirectionsPanel>(
			Direction.RelativeDirection.class);

	public TestOverlay(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		
	}
	
	public void initialize(MapView mapView) {
		this.mapView = mapView;
		
	    panel_width = Utils.getDP(80);

	    DirectionsPanel leftPanel = (DirectionsPanel) findViewById(R.id.directions_panel_left);
	    DirectionsPanel rightPanel = (DirectionsPanel) findViewById(R.id.directions_panel_right);
	
	    panels.put(RelativeDirection.LEFT, leftPanel);
	    panels.put(RelativeDirection.RIGHT, rightPanel);

	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
	    DirectionsPanel leftPanel = (DirectionsPanel) findViewById(R.id.directions_panel_left);
	    DirectionsPanel rightPanel = (DirectionsPanel) findViewById(R.id.directions_panel_right);

		
	    setSidePanelSize(leftPanel);
	    setSidePanelSize(rightPanel);
	    
		super.onSizeChanged(w, h, oldw, oldh);
	}
	
	private void setSidePanelSize(DirectionsPanel panel) {
		android.view.ViewGroup.LayoutParams params = panel.getLayoutParams();
		params.height = getHeight() - Utils.getDP(30);
		
		panel.setLayoutParams(params);
	}
	
	public void setDirections(List<Direction> directions, final float bearing) {
		this.directions = directions;
		this.bearing = bearing;
		
		for (Entry<Direction.RelativeDirection, DirectionsPanel> entry : panels.entrySet()) {
			final RelativeDirection relDir = entry.getKey();
			final DirectionsPanel panel = entry.getValue();
			
			ArrayList<Direction> resultList = new ArrayList<Direction>();
			Utils.select(directions, resultList, new Utils.Predicate<Direction>() {
				public boolean apply(Direction dir) {
					return dir.getRelativeDirection(bearing) == relDir;
				};
			});
			
			panel.setDirections(resultList);
		}
	}
	
	/*@Override
	public boolean o(MotionEvent event, MapView mapView) {
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
	}*/
}
