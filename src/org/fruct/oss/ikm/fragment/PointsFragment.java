package org.fruct.oss.ikm.fragment;

import static org.fruct.oss.ikm.Utils.log;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.DetailsActivity;
import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class PointsFragment extends ListFragment {
	private List<PointDesc> poiList;
	private List<PointDesc> shownList;
	
	private boolean isDualPane;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		return super.onCreateView(inflater, container, savedInstanceState);
	}
	
	private void setList(List<PointDesc> points) {
		int index = getListView().getCheckedItemPosition();
		PointDesc pointDesc = null;
		if (index >= 0)
			pointDesc = shownList.get(index);
		
		shownList = points;
		ArrayList<String> poiNames = new ArrayList<String>();

		for (PointDesc point : points) {
			poiNames.add(point.getName());
		}
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(
				getActivity(), 
				getListItemlayout(),
				poiNames);
		setListAdapter(adapter);
		
		if (pointDesc != null)
			selectIfAvailable(pointDesc, false);
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
	
	/**
	 * Select item if it in list
	 * 
	 * @param point point of interest
	 * @param alwaysSwitch switch to details window even in one-panel mode
	 */
	public void selectIfAvailable(PointDesc point, boolean alwaysSwitch) {
		int c = -1;
		for (int i = 0; i < shownList.size(); i++) {
			if (shownList.get(i).equals(point)) {
				c = i;
			}
		}
		
		
		if (-1 != c) {
			log("select " + c);
			getListView().setItemChecked(c, true);

			if ((alwaysSwitch || isDualPane)) {
				showDetails(c);
			}
		}
	}
	
	public void showDetails(int index) {
		PointDesc pointDesc = shownList.get(index);
		log("PointsFragment.showDetails isDualPane = " + isDualPane);
		if (isDualPane) {
			getListView().setItemChecked(index, true);
			
			DetailsFragment fragment = new DetailsFragment();
			Bundle args = new Bundle();
			args.putParcelable(DetailsActivity.POINT_ARG, pointDesc);
			fragment.setArguments(args);
			getActivity().getSupportFragmentManager().beginTransaction()
				.replace(R.id.point_details, fragment, "details")
				.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
				.commit();
		} else {
			Intent intent = new Intent(getActivity(), DetailsActivity.class);
			intent.setAction(DetailsActivity.POINT_ACTION);
			intent.putExtra(DetailsActivity.POINT_ARG, (Parcelable) pointDesc);
			startActivity(intent);
		}
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		try {			
			Intent intent = getActivity().getIntent();
			List<PointDesc> poiList = intent.getParcelableArrayListExtra(MapFragment.POINTS);

			if (poiList == null)
				poiList = PointsManager.getInstance().getAllPoints();
			
			this.poiList = poiList;
			setList(poiList);
		} catch (ClassCastException ex) {
			ex.printStackTrace();
		}

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
		
		int index = ListView.INVALID_POSITION;
		if (savedInstanceState != null) {
			index = savedInstanceState.getInt("index", 0);
		}
		
		if (isDualPane) {
			getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		}
		
		// If intent has action SHOW_DETAILS, show details activity immediately
		if (PointsActivity.SHOW_DETAILS.equals(getActivity().getIntent().getAction())) {
			Bundle bundle = getActivity().getIntent().getBundleExtra(PointsActivity.DETAILS_INDEX);
			log("PointsFragment receive action SHOW_DETAILS. extras = " + bundle);
			PointDesc point = bundle.getParcelable("pointdesc");
			selectIfAvailable(point, true);
		} else if (isDualPane && index != ListView.INVALID_POSITION) {
			showDetails(index);
		}
		
		setupFilterBar();
	}
	
	private void setupFilterBar() {
		ActionBarActivity activity = (ActionBarActivity) getActivity();
		ActionBar actionBar = activity.getSupportActionBar();
		
		final String[] names = {"All", "Education", "Health", "Culture", "Sport"};
		final String[] filters = {"", "education", "health", "culture", "sport"};
		final String currentFilter = PointsManager.getInstance().getFilter();
		
		for (int i = 0; i < names.length; i++) {
			Tab tab = activity.getSupportActionBar().newTab();
			tab.setText(names[i]);
			
			final int idx = i;
			tab.setTabListener(new ActionBar.TabListener() {
				@Override
				public void onTabUnselected(Tab arg0, FragmentTransaction arg1) {
					
				}
				
				@Override
				public void onTabSelected(Tab arg0, FragmentTransaction arg1) {
					PointsManager.getInstance().setFilter(filters[idx]);
					setList(PointsManager.getInstance().filterPoints(poiList));
				}
				
				@Override
				public void onTabReselected(Tab arg0, FragmentTransaction arg1) {
					
				}
			});
			
			actionBar.addTab(tab, currentFilter.equals(filters[i]));
		}
		
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setDisplayShowTitleEnabled(false);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	
		outState.putInt("index", getListView().getCheckedItemPosition());
	}
}
