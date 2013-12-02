package org.fruct.oss.ikm.fragment;

import static org.fruct.oss.ikm.Utils.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.fruct.oss.ikm.DetailsActivity;
import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.AllFilter;
import org.fruct.oss.ikm.poi.Filter;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

class PointAdapter extends ArrayAdapter<PointDesc> {
	private int resource;
	private List<PointDesc> points;

	public PointAdapter(Context context, int resource, List<PointDesc> points) {
		super(context, resource, points);

		this.resource = resource;
		this.points = points;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = ((Activity) getContext()).getLayoutInflater();
		View view;
		
		if (convertView != null)
			view = convertView;
		else
			view = inflater.inflate(resource, parent, false);
		
		ImageView imageView = (ImageView) view.findViewById(android.R.id.icon1);
		
		TextView textView = (TextView) view.findViewById(android.R.id.text1);
		TextView distanceView = (TextView) view.findViewById(android.R.id.text2);
		
		PointDesc point = points.get(position);
		
		textView.setText(point.getName());
		if (point.getRelativeDirection() != null) {
			imageView.setImageResource(point.getRelativeDirection().getIconId());
			imageView.setContentDescription(point.getRelativeDirection().getDescription());
			distanceView.setVisibility(View.VISIBLE);
		} else {
			imageView.setVisibility(View.GONE);
		}
		if (point.getDistance() > 0) {
			distanceView.setText(Utils.stringMeters(point.getDistance()));
			distanceView.setVisibility(View.VISIBLE);
		} else {
			distanceView.setVisibility(View.GONE);
		}
		
		return view;
	}
}

public class PointsFragment extends ListFragment implements TextWatcher {
	private List<PointDesc> poiList;
	private List<PointDesc> shownList = new ArrayList<PointDesc>();
	private Filter currentFilter = null;
	
	private boolean isDualPane;
	private String searchText;
	private EditText searchBar;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		return inflater.inflate(R.layout.point_list_layout, container, true);
	}
	
	private void updateList() {
		int index = getListView().getCheckedItemPosition();
		PointDesc pointDesc = null;
		if (index >= 0)
			pointDesc = shownList.get(index);
		
		shownList.clear();
		Utils.select(poiList, shownList, new Utils.Predicate<PointDesc>() {
			@Override
			public boolean apply(PointDesc t) {
				if (currentFilter != null && !currentFilter.accepts(t))
					return false;
				else if (searchText != null
						&& searchText.length() > 0
						&& !t.getName().toLowerCase(Locale.getDefault()).contains(searchText))
					return false;
				else
					return true;
			}
		});
		
		PointAdapter adapter = new PointAdapter(
				getActivity(), 
				getListItemLayout(),
				shownList);
		setListAdapter(adapter);
		
		if (pointDesc != null)
			selectIfAvailable(pointDesc, false);
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static int getListItemLayout() {
		/*return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
				? android.R.layout.simple_list_item_activated_1
				: android.R.layout.simple_list_item_1;*/
	
		return R.layout.point_list_item;
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
	
	/**
	 * Show point of interest details on details panel (dual-pane layout)
	 * or new activity (one-panel layout)
	 * 
	 * @param index of element
	 */
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
			updateList();
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

		int selectedBar = 0;
		int index = ListView.INVALID_POSITION;
		if (savedInstanceState != null) {
			index = savedInstanceState.getInt("index", 0);
			selectedBar = savedInstanceState.getInt("selectedBar", 0);
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
		
		setupFilterBar(selectedBar);
		
		searchBar = (EditText) getActivity().findViewById(R.id.search_field);
		searchBar.addTextChangedListener(this);
	}
	
	private void setupTab(final Filter filter, boolean isSelected) {
		ActionBarActivity activity = (ActionBarActivity) getActivity();
		ActionBar actionBar = activity.getSupportActionBar();

		Tab tab = activity.getSupportActionBar().newTab();
		tab.setText(filter.getString());
		
		tab.setTabListener(new ActionBar.TabListener() {
			@Override
			public void onTabUnselected(Tab arg0, FragmentTransaction arg1) {
				
			}
			
			@Override
			public void onTabSelected(Tab arg0, FragmentTransaction arg1) {
				currentFilter = filter;
				updateList();
			}
			
			@Override
			public void onTabReselected(Tab arg0, FragmentTransaction arg1) {
				
			}
		});
		
		actionBar.addTab(tab, isSelected);
	}
	
	private void setupFilterBar(int selectedBar) {
		ActionBar actionBar = getActionBar();
		List<Filter> filters = PointsManager.getInstance().getFilters();
		
		setupTab(new AllFilter(), false);
		int c = 1;
		for (final Filter filter : filters) {
			if (selectedBar == c) {
				currentFilter = filter;
			}

			setupTab(filter, selectedBar == c);
			c++;
		}
		
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setDisplayShowTitleEnabled(false);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	
		outState.putInt("index", getListView().getCheckedItemPosition());
		int selectedTab = getActionBar().getSelectedNavigationIndex();
		outState.putInt("selectedBar", selectedTab);
	}

	private ActionBar getActionBar() {
		ActionBarActivity activity = (ActionBarActivity) getActivity();
		return activity.getSupportActionBar();
	}

	@Override
	public void afterTextChanged(Editable s) {}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		log("onTextChanged " + s);
		searchText = s.toString().toLowerCase(Locale.getDefault());
		updateList();
	}
}
