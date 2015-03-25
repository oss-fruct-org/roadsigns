package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.drawer.DrawerActivity;
import org.fruct.oss.ikm.drawer.MultiPanel;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.poi.AllFilter;
import org.fruct.oss.ikm.poi.Filter;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

class PointAdapter extends ArrayAdapter<PointDesc> {
	private static Logger log = LoggerFactory.getLogger(PointAdapter.class);

	private int resource;
	private List<PointDesc> points;

	class Tag {
		TextView textView;
		TextView distanceView;
		ImageView imageView;
	}

	public PointAdapter(Context context, int resource, List<PointDesc> points) {
		super(context, resource, points);

		this.resource = resource;
		this.points = points;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = ((Activity) getContext()).getLayoutInflater();
		View view = null;

		Tag tag = null;

		if (convertView != null && convertView.getTag() != null) {
			tag = (Tag) convertView.getTag();
			if (tag instanceof Tag) {
				view = convertView;
			}
		}

		if (view == null) {
			view = inflater.inflate(resource, parent, false);
			assert view != null;

			tag = new Tag();
			tag.textView = (TextView) view.findViewById(android.R.id.text1);
			tag.distanceView = (TextView) view.findViewById(android.R.id.text2);
			tag.imageView = (ImageView) view.findViewById(android.R.id.icon1);
			view.setTag(tag);
		}

		PointDesc point = points.get(position);

		tag.textView.setText(point.getName());
		if (point.getRelativeDirection() != null) {
			tag.imageView.setImageResource(point.getRelativeDirection().getIconId());
			tag.imageView.setContentDescription(point.getRelativeDirection().getDescription());
			tag.imageView.setVisibility(View.VISIBLE);
			tag.distanceView.setVisibility(View.VISIBLE);
		} else {
			tag.imageView.setVisibility(View.GONE);
		}

		int distance = point.getDistance();
		if (distance > 0 && distance != Integer.MAX_VALUE) {
			tag.distanceView.setText(Utils.stringMeters(distance));
			tag.distanceView.setVisibility(View.VISIBLE);
		} else {
			tag.distanceView.setVisibility(View.GONE);
		}
		
		return view;
	}
}

public class PointsFragment extends ListFragment implements TextWatcher, PointsManager.PointsListener {
	private static Logger log = LoggerFactory.getLogger(PointsFragment.class);

	public static final String ACTION_SHOW_DETAILS = "org.fruct.oss.ikm.ACTION_SHOW_DETAILS";
	public static final String ACTION_SHOW_POINTS = "org.fruct.oss.ikm.ACTION_SHOW_POINTS";
	public static final String ARG_POINTS = "org.fruct.oss.ikm.fragment.POI_LIST";

	private List<PointDesc> poiList;
	private List<PointDesc> shownList = new ArrayList<>();
	private Filter currentFilter = null;
	
	private String searchText;

	private TextView loadingView;

	private MultiPanel multiPanel;

	public static PointsFragment newInstance() {
		return new PointsFragment();
	}

	public static Fragment newInstance(ArrayList<PointDesc> points) {
		PointsFragment pointsFragment = new PointsFragment();

		Bundle args = new Bundle();
		args.putParcelableArrayList(ARG_POINTS, points);
		pointsFragment.setArguments(args);

		return pointsFragment;
	}


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Activity activity = getActivity();
		if (activity instanceof DrawerActivity) {
			((DrawerActivity) activity).onSectionAttached(getString(R.string.title_activity_points),
					ActionBar.NAVIGATION_MODE_TABS);
			multiPanel = (MultiPanel) activity;
		}

		setHasOptionsMenu(true);
		PointsManager.getInstance().addListener(this);

		if (getArguments() != null) {
			poiList = getArguments().getParcelableArrayList(ARG_POINTS);
		} else {
			poiList = PointsManager.getInstance().getAllPoints();
		}

		updateList();
	}

	@Override
	public void onDestroy() {
		PointsManager.getInstance().removeListener(this);

		super.onDestroy();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);

		inflater.inflate(R.menu.points_fragment, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_refresh) {
			loadingView.setText(getActivity().getString(R.string.str_loading_items));
			loadingView.setVisibility(View.VISIBLE);

			PointsManager.getInstance().refresh();
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.point_list_layout, container, false);
		loadingView = (TextView) view.findViewById(R.id.progress_text);

		int selectedBar = 0;
		if (savedInstanceState != null) {
			selectedBar = savedInstanceState.getInt("selectedBar", 0);
		}

		EditText searchBar = (EditText) view.findViewById(R.id.search_field);
		searchBar.addTextChangedListener(this);

		setupFilterBar(selectedBar);
		return view;
	}

	private void updateList() {
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
				R.layout.point_list_item,
				shownList);

		adapter.sort(new Comparator<PointDesc>() {
			@Override
			public int compare(PointDesc plhs, PointDesc prhs) {
				int lhs = plhs.getDistance();
				int rhs = prhs.getDistance();
				return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
			}
		});

		setListAdapter(adapter);
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		PointDesc point = shownList.get(position);
		multiPanel.pushFragment(DetailsFragment.newInstance(point));
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
		actionBar.removeAllTabs();

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
		log.debug("onTextChanged " + s);
		searchText = s.toString().toLowerCase(Locale.getDefault());
		updateList();
	}

	@Override
	public void filterStateChanged(List<PointDesc> newList) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				poiList = PointsManager.getInstance().getAllPoints();
				updateList();
				setupFilterBar(0);

				loadingView.setVisibility(View.GONE);
			}
		});
	}

	@Override
	public void errorDownloading() {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getActivity(), R.string.error_downloading, Toast.LENGTH_LONG).show();
				loadingView.setVisibility(View.GONE);
			}
		});
	}
}
