package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.utils.Utils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class DirectionsPanel extends RelativeLayout {
	private List<Direction> directions;
	private List<PointDesc> points;

	private boolean isOverlayHidden = true;
	
	public DirectionsPanel(Context context, AttributeSet attrs) {
		super(context, attrs);
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

		Collections.sort(points, new Comparator<PointDesc>() {
			@Override
			public int compare(PointDesc plhs, PointDesc prhs) {
				int lhs = plhs.getDistance();
				int rhs = prhs.getDistance();
				return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
			}
		});

		setPoints();
	}

	private void setPoints() {
		LayoutInflater inflater = LayoutInflater.from(getContext());

		boolean isVertical = getWidth() < getHeight();

		int dimension = !isVertical ? getWidth() : getHeight();
		int n = Math.min(dimension / Utils.getDP(isVertical ? 50 : 80), points.size());

		if (isVertical) {
			n--;
		}

		removeAllViews();

		LinearLayout layout = new LinearLayout(getContext());
		layout.setOrientation(isVertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
		if (!isVertical)
			layout.setGravity(Gravity.CENTER_HORIZONTAL);

		LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		params.addRule(ALIGN_PARENT_TOP);
		params.addRule(ABOVE, android.R.id.button1);
		layout.setLayoutParams(params);

		addView(layout);

		for (int i = 0; i < n; i++) {
			View panelView = inflater.inflate(R.layout.direction_panel_list_item, layout, false);
			panelView.setId(android.R.id.button1);

			TextView textView = (TextView) panelView.findViewById(R.id.direction_panel_list_item);
			textView.setText(points.get(i).getName());

			final int finalI = i;
			textView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					onItemClick(finalI);
				}
			});

			layout.addView(panelView);
		}

		// Overflow
		if (n < points.size()) {
			View panelView = inflater.inflate(R.layout.direction_panel_list_item, this, false);
			LayoutParams panelParams = new LayoutParams(Utils.getDP(80), Utils.getDP(30));
			panelParams.addRule(ALIGN_PARENT_BOTTOM);
			panelParams.addRule(CENTER_HORIZONTAL);

			panelView.setLayoutParams(panelParams);

			TextView textView = (TextView) panelView.findViewById(R.id.direction_panel_list_item);
			textView.setGravity(Gravity.CENTER);
			textView.setText("...");

			panelView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(getContext(), PointsActivity.class);
					intent.putParcelableArrayListExtra(MapFragment.POINTS, new ArrayList<PointDesc>(points));
					getContext().startActivity(intent);
				}
			});

			addView(panelView);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		if (points != null) {
			setPoints();
		}
	}

	public void onItemClick(int pos) {
		PointDesc point = points.get(pos);
		
		Bundle bundle = new Bundle();
		bundle.putParcelable("pointdesc", point);
		
		Intent intent = new Intent(getContext(), PointsActivity.class);
		intent.setAction(PointsActivity.SHOW_DETAILS);
		intent.putExtra(PointsActivity.DETAILS_INDEX, bundle);
		
		getContext().startActivity(intent);
	}
}
