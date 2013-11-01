package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.Utils.Predicate;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.Direction.RelativeDirection;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class DirectionsListAdapter extends BaseAdapter {
	private float bearing;
	private RelativeDirection relativeDirection;
	private Context context;
	private LayoutInflater inflater;
	private List<PointDesc> points;
	
	private boolean isEnabled = false;
	
	public DirectionsListAdapter(Context context,
			RelativeDirection relativeDirection) {
		this.relativeDirection = relativeDirection;
		this.context = context;
		
		inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	public void setPoints(List<Direction> directions, final float bearing) {
		Direction direction = Utils.find(directions, new Predicate<Direction>() {
			@Override
			public boolean apply(Direction t) {
				return t.getRelativeDirection(bearing) == relativeDirection;
			}
		});

		if (direction == null) {
			isEnabled = false;
			return;
		} else {
			isEnabled = true;
		}
		
		this.points = direction.getPoints();
		this.bearing = bearing;
		
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		if (points == null)
			return 0;
		else		
			return points.size();
	}

	@Override
	public Object getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
		
		TextView textView = (TextView) view.findViewById(android.R.id.text1);
		PointDesc point = points.get(position);
		textView.setText(point.getName());
		
		return view;
	}

}
