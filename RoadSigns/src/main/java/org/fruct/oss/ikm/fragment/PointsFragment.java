package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.drawer.DrawerActivity;
import org.fruct.oss.ikm.drawer.MultiPanel;
import org.fruct.oss.ikm.events.EventReceiver;
import org.fruct.oss.ikm.events.PointsUpdatedEvent;
import org.fruct.oss.ikm.points.Point;
import org.fruct.oss.ikm.points.PointsUpdateService;
import org.fruct.oss.ikm.points.gets.Category;
import org.fruct.oss.ikm.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.Context;
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

import de.greenrobot.event.EventBus;

class PointAdapter extends ArrayAdapter<Point> {
	private int resource;
	private List<Point> points;

	class Tag {
		TextView textView;
		TextView distanceView;
		ImageView imageView;
	}

	public PointAdapter(Context context, int resource, List<Point> points) {
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
			tag.imageView = (ImageView) view.findViewById(android.R.id.icon2);
			view.setTag(tag);
		}

		Point point = points.get(position);

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

public class PointsFragment extends ListFragment implements TextWatcher {
	private static Logger log = LoggerFactory.getLogger(PointsFragment.class);

	public static final String ACTION_SHOW_DETAILS = "org.fruct.oss.ikm.ACTION_SHOW_DETAILS";
	public static final String ACTION_SHOW_POINTS = "org.fruct.oss.ikm.ACTION_SHOW_POINTS";
	public static final String ARG_POINTS = "org.fruct.oss.ikm.fragment.POI_LIST";

	private List<Point> poiList;
	private List<Point> shownList = new ArrayList<>();

	private List<Category> categories;

	private Category currentCategory = null;

	private String searchText;

	private TextView loadingView;

	private MultiPanel multiPanel;

	public static Fragment newInstance(ArrayList<Point> points) {
		PointsFragment pointsFragment = new PointsFragment();

		Bundle args = new Bundle();
		args.putParcelableArrayList(ARG_POINTS, points);
		pointsFragment.setArguments(args);

		return pointsFragment;
	}

	public PointsFragment() {
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


		EventBus.getDefault().register(this);

		setHasOptionsMenu(true);
	}

	@Override
	public void onDestroy() {
		EventBus.getDefault().unregister(this);

		ActionBar actionBar = getActionBar();
		actionBar.removeAllTabs();

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

			PointsUpdateService.startDefault(getActivity());
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.point_list_layout, container, false);
		loadingView = (TextView) view.findViewById(R.id.progress_text);

		EditText searchBar = (EditText) view.findViewById(R.id.search_field);
		searchBar.addTextChangedListener(this);

		int selectedBar = 0;
		if (savedInstanceState != null) {
			selectedBar = savedInstanceState.getInt("selectedBar", 0);
		}

		categories = new ArrayList<>(App.getInstance().getPointsAccess().loadCategories());
		setupFilterBar(selectedBar);


		if (getArguments() != null) {
			poiList = getArguments().getParcelableArrayList(ARG_POINTS);
		} else {
			// TODO: should be called in AsyncTask
			poiList = App.getInstance().getPointsAccess().loadAll();
		}
		updateList();

		return view;
	}

	private void updateList() {
		shownList.clear();
		Utils.select(poiList, shownList, new Utils.Predicate<Point>() {
			@Override
			public boolean apply(Point t) {
				if (currentCategory != null && currentCategory.getId() != t.getCategory().getId())
					return false;
				else //noinspection RedundantIfStatement
					if (searchText != null
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

		adapter.sort(new Comparator<Point>() {
			@Override
			public int compare(Point plhs, Point prhs) {
				int lhs = plhs.getDistance();
				int rhs = prhs.getDistance();
				return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
			}
		});

		setListAdapter(adapter);
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		Point point = shownList.get(position);
		multiPanel.pushFragment(DetailsFragment.newInstance(point));
	}

	private void setupAllTab(boolean isSelected) {
		ActionBarActivity activity = (ActionBarActivity) getActivity();
		ActionBar actionBar = activity.getSupportActionBar();

		Tab tab = activity.getSupportActionBar().newTab();
		tab.setText(R.string.pref_all);

		tab.setTabListener(new ActionBar.TabListener() {
			@Override
			public void onTabUnselected(Tab arg0, FragmentTransaction arg1) {

			}

			@Override
			public void onTabSelected(Tab arg0, FragmentTransaction arg1) {
				currentCategory = null;
				updateList();
			}

			@Override
			public void onTabReselected(Tab arg0, FragmentTransaction arg1) {

			}
		});

		actionBar.addTab(tab, isSelected);
	}

	private void setupTab(final Category category, boolean isSelected) {
		ActionBarActivity activity = (ActionBarActivity) getActivity();
		ActionBar actionBar = activity.getSupportActionBar();

		Tab tab = activity.getSupportActionBar().newTab();
		tab.setText(category.getName());
		
		tab.setTabListener(new ActionBar.TabListener() {
			@Override
			public void onTabUnselected(Tab arg0, FragmentTransaction arg1) {
				
			}
			
			@Override
			public void onTabSelected(Tab arg0, FragmentTransaction arg1) {
				currentCategory = category;
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
		actionBar.removeAllTabs();

		setupAllTab(false);

		int c = 1;
		for (final Category category : categories) {
			if (selectedBar == c) {
				currentCategory = category;
			}

			setupTab(category, selectedBar == c);
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

	@EventReceiver
	public void onEventMainThread(PointsUpdatedEvent pointsUpdatedEvent) {
		if (!pointsUpdatedEvent.isSuccess()) {
			Toast.makeText(getActivity(), R.string.error_downloading, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(getActivity(), R.string.download_finished, Toast.LENGTH_LONG).show();

			int selectedTab = getActionBar().getSelectedNavigationIndex();
			this.categories = pointsUpdatedEvent.getCategories();
			this.poiList = App.getInstance().getPointsAccess().loadAll();

			setupFilterBar(selectedTab);
			updateList();
		}

		loadingView.setVisibility(View.GONE);
	}
}
