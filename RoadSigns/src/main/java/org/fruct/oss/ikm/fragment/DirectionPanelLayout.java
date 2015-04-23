package org.fruct.oss.ikm.fragment;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.Direction.RelativeDirection;
import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;

public class DirectionPanelLayout extends RelativeLayout  {
	private View statusPanel;
	private EnumMap<Direction.RelativeDirection, DirectionsPanel> panels = new EnumMap<Direction.RelativeDirection, DirectionsPanel>(
			Direction.RelativeDirection.class);

	public DirectionPanelLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setStatusVisible(boolean statusVisible) {
		statusPanel.setVisibility(statusVisible ? VISIBLE : GONE);
	}
	
	public void setHidden(boolean isHidden) {
		for (DirectionsPanel panel : panels.values())
			panel.setOverlayHidden(isHidden);
	}
	
	public void initialize(MapView mapView) {
	    DirectionsPanel leftPanel = (DirectionsPanel) findViewById(R.id.directions_panel_left);
	    DirectionsPanel rightPanel = (DirectionsPanel) findViewById(R.id.directions_panel_right);
	    DirectionsPanel topPanel = (DirectionsPanel) findViewById(R.id.directions_panel_top);
	    DirectionsPanel bottomPanel = (DirectionsPanel) findViewById(R.id.directions_panel_bottom);

		statusPanel = (View) findViewById(R.id.status_panel);

		panels.put(RelativeDirection.LEFT, leftPanel);
	    panels.put(RelativeDirection.RIGHT, rightPanel);
	    panels.put(RelativeDirection.FORWARD, topPanel);
	    panels.put(RelativeDirection.BACK, bottomPanel);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
	    DirectionsPanel leftPanel = (DirectionsPanel) findViewById(R.id.directions_panel_left);
	    DirectionsPanel rightPanel = (DirectionsPanel) findViewById(R.id.directions_panel_right);
	    DirectionsPanel topPanel = (DirectionsPanel) findViewById(R.id.directions_panel_top);
	    DirectionsPanel bottomPanel = (DirectionsPanel) findViewById(R.id.directions_panel_bottom);

		
	    setSidePanelSize(leftPanel);
	    setSidePanelSize(rightPanel);
	    
	    setTopPanelSize(topPanel);
	    setTopPanelSize(bottomPanel);
	    
		super.onSizeChanged(w, h, oldw, oldh);
	}
	
	private void setSidePanelSize(DirectionsPanel panel) {
		android.view.ViewGroup.LayoutParams params = panel.getLayoutParams();
		params.height = getHeight() - Utils.getDP(169);
		
		panel.setLayoutParams(params);
	}
	
	private void setTopPanelSize(DirectionsPanel panel) {
		android.view.ViewGroup.LayoutParams params = panel.getLayoutParams();
		params.width = getWidth() - Utils.getDP(70);
		
		panel.setLayoutParams(params);
	}
	
	public void setDirections(List<Direction> directions, final float bearing) {
		for (Entry<Direction.RelativeDirection, DirectionsPanel> entry : panels.entrySet()) {
			final RelativeDirection relDir = entry.getKey();
			final DirectionsPanel panel = entry.getValue();
			
			ArrayList<Direction> resultList = new ArrayList<Direction>();
			Utils.select(directions, resultList, new Utils.Predicate<Direction>() {
				public boolean apply(Direction dir) {
					return dir.getRelativeDirection(bearing) == relDir;
				}
			});
			
			panel.setDirections(resultList);
		}
	}
}
