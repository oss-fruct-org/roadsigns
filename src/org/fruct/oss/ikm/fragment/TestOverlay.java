package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.Direction.RelativeDirection;
import org.fruct.oss.ikm.service.DirectionService;
import org.osmdroid.views.MapView;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class TestOverlay extends RelativeLayout  {
	public int panel_width = 0;
	
	private boolean isHidden = false;
	
	private MapView mapView;

	private List<Direction> directions;
	private float bearing;

	private EnumMap<Direction.RelativeDirection, DirectionsPanel> panels = new EnumMap<Direction.RelativeDirection, DirectionsPanel>(
			Direction.RelativeDirection.class);

	public TestOverlay(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public void setHidden(boolean isHidden) {
		this.isHidden = isHidden;
		
		for (DirectionsPanel panel : panels.values())
			panel.setOverlayHidden(isHidden);
	}

	
	public void initialize(MapView mapView) {
		this.mapView = mapView;
		
	    panel_width = Utils.getDP(80);
	    //Utils.log("asdasd " + panel_width);
	    //Utils.log("asdasd  " + getContext().getResources().getDisplayMetrics().density);
	    
	    DirectionsPanel leftPanel = (DirectionsPanel) findViewById(R.id.directions_panel_left);
	    DirectionsPanel rightPanel = (DirectionsPanel) findViewById(R.id.directions_panel_right);
	    DirectionsPanel topPanel = (DirectionsPanel) findViewById(R.id.directions_panel_top);
	    DirectionsPanel bottomPanel = (DirectionsPanel) findViewById(R.id.directions_panel_bottom);

	    
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
		params.width = getWidth() - Utils.getDP(80);
		
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
}
