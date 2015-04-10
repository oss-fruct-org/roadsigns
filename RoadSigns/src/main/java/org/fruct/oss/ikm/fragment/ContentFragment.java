package org.fruct.oss.ikm.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.ActionBar;

import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.drawer.DrawerActivity;

import java.util.List;

public class ContentFragment extends org.fruct.oss.mapcontent.content.fragments.ContentFragment {
	public static final String ACTION_SHOW_ONLINE_CONTENT = "org.fruct.oss.ikm.ACTION_SHOW_ONLINE_CONTENT";
	public static final String ACTION_UPDATE_READY = "org.fruct.oss.ikm.ACTION_UPDATE_READY";

	public static ContentFragment newInstance() {
		return newInstance(false, false);
	}

	public static ContentFragment newInstance(boolean suggestItem, boolean switchToUpdate) {
		ContentFragment fragment = new ContentFragment();

		Bundle args = new Bundle();
		args.putBoolean("suggest", suggestItem);
		args.putBoolean("update", switchToUpdate);
		fragment.setArguments(args);

		return fragment;
	}

	public static final String[] REMOTE_CONTENT_URLS = {
			"http://gets.cs.petrsu.ru/maps/root/root.xml",
			"http://kappa.cs.petrsu.ru/~ivashov/mordor2.xml",
			"http://kappa.cs.petrsu.ru/~ivashov/mordor.xml",
			"http://oss.fruct.org/projects/roadsigns/root.xml"};

	public ContentFragment() {
		super();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		((DrawerActivity) getActivity()).onSectionAttached(getString(R.string.action_download_map),
				ActionBar.NAVIGATION_MODE_LIST);

		setRootUrls(REMOTE_CONTENT_URLS);
		boolean suggest = false;

		if (getArguments() != null) {
			suggest = getArguments().getBoolean("suggest");
		}

		if (suggest) {
			suggestItem();
		}
	}
}