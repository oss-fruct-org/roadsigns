package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.service.Direction;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class DirectionsPanel extends ListView {
	private List<Direction> directions;
	
	public DirectionsPanel(Context context, AttributeSet attrs) {
		super(context, attrs);		
	}
	
	public void setDirections(ArrayList<Direction> directions) {
		this.directions = directions;
	
		
		List<String> strings = new ArrayList<String>();
		for (Direction direction : directions) {
			for (PointDesc p : direction.getPoints()) {
				strings.add(p.getName());
			}
		}
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), R.layout.direction_panel_list_item, R.id.direction_panel_list_item, strings);
		setAdapter(adapter);
	}	
	
}
