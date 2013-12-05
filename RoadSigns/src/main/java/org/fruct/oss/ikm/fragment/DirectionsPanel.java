package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.service.Direction;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;

public class DirectionsPanel extends GridView implements OnItemClickListener {
	private List<Direction> directions;
	private List<PointDesc> points;
	
	private boolean isOverlayHidden = true;
	
	public DirectionsPanel(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setOnItemClickListener(this);
	}
	
	public void setOverlayHidden(boolean isHidden) {
		this.isOverlayHidden = isHidden;
		
		if (isOverlayHidden)
			setVisibility(View.GONE);
		else if (directions != null && !directions.isEmpty())
			setVisibility(View.VISIBLE);
	}
	
	public void setDirections(ArrayList<Direction> directions) {
		this.directions = directions;

		// Hide panel of no points in direction or parent view hidden
		if (isOverlayHidden || directions == null || directions.isEmpty()) {
			setVisibility(View.GONE);
			return;
		} else {
			setVisibility(View.VISIBLE);
		}

		// Generate plain array of PointDesc from Direction' array
		List<String> strings = new ArrayList<String>();
		points = new ArrayList<PointDesc>();
		for (Direction direction : directions) {
			for (PointDesc p : direction.getPoints()) {
				strings.add(p.getName());
				points.add(p);
			}
		}
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), R.layout.direction_panel_list_item, R.id.direction_panel_list_item, strings);
		setAdapter(adapter);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
		PointDesc point = points.get(pos);
		
		Bundle bundle = new Bundle();
		bundle.putParcelable("pointdesc", point);
		
		Intent intent = new Intent(getContext(), PointsActivity.class);
		intent.setAction(PointsActivity.SHOW_DETAILS);
		intent.putExtra(PointsActivity.DETAILS_INDEX, bundle);
		
		getContext().startActivity(intent);
	}
}
