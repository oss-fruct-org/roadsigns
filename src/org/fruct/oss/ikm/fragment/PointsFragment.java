package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;

import org.fruct.oss.ikm.DetailsActivity;
import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.poi.PointDesc;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import static org.fruct.oss.ikm.Utils.log;

public class PointsFragment extends ListFragment {
	private ArrayList<PointDesc> poiList;
	private boolean isDualPane;
	private int selectedIndex = 0;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		try {
			@SuppressWarnings("unchecked")
			ArrayList<PointDesc> poiList = getActivity().getIntent()
					.getParcelableArrayListExtra(MapFragment.POINTS);
			this.poiList = poiList;
			
			ArrayList<String> poiNames = new ArrayList<String>();

			for (PointDesc point : poiList) {
				poiNames.add(point.getName());
			}
			
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(
					getActivity(), 
					getListItemlayout(),
					poiNames);
			setListAdapter(adapter);

		} catch (ClassCastException ex) {
			ex.printStackTrace();
		}
		
		return super.onCreateView(inflater, container, savedInstanceState);
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static int getListItemlayout() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
				? android.R.layout.simple_list_item_activated_1
				: android.R.layout.simple_list_item_1;
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		showDetails(position);
	}
	
	public void showDetails(int index) {
		selectedIndex = index;
		PointDesc pointDesc = poiList.get(index);

		if (isDualPane) {
			getListView().setItemChecked(index, true);
			
			DetailsFragment fragment = new DetailsFragment();
			Bundle args = new Bundle();
			args.putSerializable(DetailsActivity.POINT_ARG, pointDesc);
			fragment.setArguments(args);
			getActivity().getSupportFragmentManager().beginTransaction()
				.replace(R.id.point_details, fragment, "details")
				.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
				.commit();
		} else {
			Intent intent = new Intent(getActivity(), DetailsActivity.class);
			intent.putExtra(DetailsActivity.POINT_ARG, (Parcelable) pointDesc);
			startActivity(intent);
		}
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		View details = getActivity().findViewById(R.id.point_details);
		if (details != null && details.getVisibility() == View.VISIBLE)
			isDualPane = true;
		else
			isDualPane = false;
		
		// Remove detail fragment when switching from two-panel mode to one-panel mode
		Fragment detailFragment = getActivity().getSupportFragmentManager().findFragmentByTag("details");
		if (detailFragment != null && !isDualPane) {
			getActivity().getSupportFragmentManager().beginTransaction().remove(detailFragment).commit();
		}
		
		if (savedInstanceState != null) {
			selectedIndex = savedInstanceState.getInt("index", 0);
		}
		
		if (isDualPane) {
			getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
			showDetails(selectedIndex);
		}
		
		// If intent has action SHOW_DETAILS, show details activity immediately
		if (PointsActivity.SHOW_DETAILS.equals(getActivity().getIntent().getAction())) {
			int index = getActivity().getIntent().getIntExtra(PointsActivity.DETAILS_INDEX, -1);
			log("PointsFragment receive action SHOW_DETAILS. extras = " + getActivity().getIntent().getExtras());
			
			if (-1 == index)
				index = 0;
			
			showDetails(index);
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	
		outState.putInt("index", selectedIndex);
	}
}
