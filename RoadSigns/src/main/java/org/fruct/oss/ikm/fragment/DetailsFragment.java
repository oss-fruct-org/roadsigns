package org.fruct.oss.ikm.fragment;

import org.fruct.oss.ikm.DetailsActivity;
import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.poi.PointDesc;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

public class DetailsFragment extends Fragment {
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if (container == null)
			return null;
		
		super.onCreateView(inflater, container, savedInstanceState);
		View view = inflater.inflate(R.layout.details_fragment, null, false);
		return view;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		final PointDesc desc = (PointDesc) getArguments().getParcelable(DetailsActivity.POINT_ARG);
		assert desc != null;

		TextView titleView = (TextView) getActivity().findViewById(R.id.title_text);
		TextView descView = (TextView) getActivity().findViewById(R.id.details_text);
		
		if (titleView == null || descView == null)
			return;
		
		ImageButton placeButton = (ImageButton) getActivity().findViewById(R.id.show_place_button);
		placeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), MainActivity.class);
				
				intent.putExtra(MapFragment.MAP_CENTER, (Parcelable) desc.toPoint());
				startActivity(intent);
			}
		});
		
		ImageButton pathButton = (ImageButton) getActivity().findViewById(R.id.search_place_button);
		pathButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), MainActivity.class);
				intent.setAction(MainActivity.SHOW_PATH);
				
				intent.putExtra(MainActivity.SHOW_PATH_TARGET, (Parcelable) desc.toPoint());
				startActivity(intent);
			}
		});

		titleView.setText(desc.getName());
		//descView.setText(Html.fromHtml(desc.getDescription()));
		descView.setText(desc.getDescription());
	}
}
