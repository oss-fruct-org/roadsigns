package org.fruct.oss.ikm.drawer;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.text.util.Linkify;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.ViewGroup;
import android.widget.TextView;

import org.fruct.oss.ikm.HelpTabActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.fragment.ContentFragment;
import org.fruct.oss.ikm.fragment.DetailsFragment;
import org.fruct.oss.ikm.fragment.MapFragment;
import org.fruct.oss.ikm.fragment.PointsFragment;
import org.fruct.oss.ikm.points.Point;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnection;
import org.fruct.oss.mapcontent.content.helper.ContentHelper;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;

public class DrawerActivity extends ActionBarActivity
		implements NavigationDrawerFragment.NavigationDrawerCallbacks, MultiPanel {
	public static final String STATE_BACK_STACK_COUNT = "state-back-stack-count";

	private static final String TAG_PANEL_FRAGMENT = "root";

	/**
	 * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
	 */
	private NavigationDrawerFragment mNavigationDrawerFragment;

	/**
	 * Used to store the last screen title. For use in {@link #restoreActionBar()}.
	 */
	private CharSequence mTitle;

	/**
	 * Navigation mode of current fragment
	 */
	private int mNavigationMode = ActionBar.NAVIGATION_MODE_STANDARD;

	private int backStackCount;

	private ContentHelper contentHelper;

	private boolean isFromSavedState;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_drawer);

		mNavigationDrawerFragment = (NavigationDrawerFragment)
				getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
		mTitle = getTitle();

		// Set up the drawer.
		mNavigationDrawerFragment.setUp(
				R.id.navigation_drawer,
				(DrawerLayout) findViewById(R.id.drawer_layout));

		if (savedInstanceState != null) {
			backStackCount = savedInstanceState.getInt(STATE_BACK_STACK_COUNT);
		} else if (getIntent().getAction() != null) {
			processIntent(getIntent());
		}

		isFromSavedState = savedInstanceState != null;

		contentHelper = new ContentHelper(this, new ContentServiceConnection(null));
		contentHelper.enableNetworkNotifications();
		contentHelper.enableLocationProviderNotifications();
		contentHelper.enableUpdateNotifications(PendingIntent.getActivity(this, 0,
				new Intent(org.fruct.oss.mapcontent.content.fragments.ContentFragment.ACTION_UPDATE_READY, null, this, DrawerActivity.class),
				PendingIntent.FLAG_ONE_SHOT));

		contentHelper.enableContentNotifications(PendingIntent.getActivity(this, 1,
				new Intent(org.fruct.oss.mapcontent.content.fragments.ContentFragment.ACTION_SHOW_ONLINE_CONTENT, null, this, DrawerActivity.class),
				PendingIntent.FLAG_ONE_SHOT));
	}

	@Override
	protected void onStart() {
		super.onStart();
		contentHelper.onStart(isFromSavedState);
	}

	@Override
	protected void onStop() {
		contentHelper.onStop();
		super.onStop();
	}

	@Override
	public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
		super.onSaveInstanceState(outState, outPersistentState);

		outState.putInt(STATE_BACK_STACK_COUNT, backStackCount);
	}

	@Override
	public void onNavigationDrawerItemSelected(int position) {
		Fragment fragment = null;

		switch (position) {
		case 0:
			fragment = new MapFragment();
			break;

		case 1:
			fragment = new PointsFragment();
			break;

		case 2:
			fragment = ContentFragment.newInstance();
			break;

		case 3:
			startActivity(new Intent(this, HelpTabActivity.class));
			return;

		case 4:
			showAboutDialog();
			return;

		case 5:
			startActivity(new Intent(this, SettingsActivity.class));
			break;
		}

		if (fragment == null) {
			return;
		}

		setRootFragment(fragment);
	}

	private void showAboutDialog() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("Version ");
		try {
			stringBuilder.append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (PackageManager.NameNotFoundException ignore) {
			stringBuilder.append("unknown");
		}
		stringBuilder.append("\n\n");
		stringBuilder.append(getResources().getString(R.string.about_text));

		TextView textView = new TextView(new ContextThemeWrapper(this, Utils.getDialogTheme()));
		textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		textView.setTextSize(16);
		textView.setAutoLinkMask(Linkify.WEB_URLS);
		textView.setText(stringBuilder.toString());

		final int paddingDP = Utils.getDP(16);
		textView.setPadding(paddingDP, paddingDP, paddingDP, paddingDP);

		AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, Utils.getDialogTheme()));
		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(R.string.app_name);
		builder.setView(textView);

		builder.create();
		builder.show();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		String action = intent.getAction();

		if (action != null) {
			processIntent(intent);
		}
	}

	private void processIntent(Intent intent) {
		switch (intent.getAction()) {
		case PointsFragment.ACTION_SHOW_DETAILS:
			Point point = intent.getParcelableExtra(DetailsFragment.ARG_POINT);

			setRootFragment(new PointsFragment());
			pushFragment(DetailsFragment.newInstance(point));
			break;

		case PointsFragment.ACTION_SHOW_POINTS:
			ArrayList<Point> points = intent.getParcelableArrayListExtra(PointsFragment.ARG_POINTS);
			setRootFragment(PointsFragment.newInstance(points));
			break;

		case MapFragment.ACTION_SHOW_MAP:
			setRootFragment(MapFragment.newInstance());
			break;

		case MapFragment.ACTION_CENTER_MAP:
			GeoPoint geoPoint = intent.getParcelableExtra(MapFragment.ARG_MAP_CENTER);
			setRootFragment(MapFragment.newInstanceGeoPoint(geoPoint));
			break;

		case MapFragment.ACTION_SHOW_PATH:
			geoPoint = intent.getParcelableExtra(MapFragment.ARG_SHOW_PATH_TARGET);
			setRootFragment(MapFragment.newInstanceForPath(geoPoint));
			break;

		case ContentFragment.ACTION_SHOW_ONLINE_CONTENT:
			setRootFragment(ContentFragment.newInstance(true, false));
			break;

		case ContentFragment.ACTION_UPDATE_READY:
			setRootFragment(ContentFragment.newInstance(false, true));
			break;
		}
	}

	private void updateUpButton() {
		if (mNavigationDrawerFragment == null)
			return;

		if (backStackCount > 1) {
			mNavigationDrawerFragment.setUpEnabled(false);
		} else {
			mNavigationDrawerFragment.setUpEnabled(true);
		}
	}

	@Override
	public void onBackPressed() {
		FragmentManager fragmentManager = getSupportFragmentManager();

		int count = fragmentManager.getBackStackEntryCount();
		String name;
		do {
			FragmentManager.BackStackEntry entry = fragmentManager.getBackStackEntryAt(--count);
			fragmentManager.popBackStack();
			name = entry.getName();
		} while (name == null);
		backStackCount--;

		if (name.equals(TAG_PANEL_FRAGMENT)) {
			finish();
		} else {
			updateUpButton();
		}
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return super.onSupportNavigateUp();
	}

	public void onSectionAttached(String title, int navigationMode) {
		mTitle = title;
		mNavigationMode = navigationMode;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		restoreActionBar();
		return super.onPrepareOptionsMenu(menu);
	}

	public void restoreActionBar() {
		ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(mNavigationMode);
		actionBar.setDisplayShowTitleEnabled(true);
		actionBar.setTitle(mTitle);
	}

	public void setRootFragment(Fragment fragment) {
		mNavigationMode = ActionBar.NAVIGATION_MODE_STANDARD;
		FragmentManager fragmentManager = getSupportFragmentManager();

		fragmentManager.popBackStack(TAG_PANEL_FRAGMENT, FragmentManager.POP_BACK_STACK_INCLUSIVE);

		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.addToBackStack(TAG_PANEL_FRAGMENT);
		fragmentTransaction.replace(R.id.container, fragment, "content_fragment");
		fragmentTransaction.commit();
		backStackCount = 1;

		updateUpButton();
	}

	@Override
	public void pushFragment(Fragment fragment) {
		FragmentManager fragmentManager = getSupportFragmentManager();
		fragmentManager.beginTransaction()
				.addToBackStack("fragment-transaction-" + fragment.hashCode())
				.replace(R.id.container, fragment)
				.commit();

		backStackCount++;
		updateUpButton();
	}

	@Override
	public void popFragment() {
		onBackPressed();
		updateUpButton();
	}
}
