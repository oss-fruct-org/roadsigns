package org.fruct.oss.ikm.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.ActionBar;

public class ContentFragment extends org.fruct.oss.mapcontent.content.fragments.ContentFragment {
	public static ContentFragment newInstance() {
		return new ContentFragment();
	}

	public static final String[] REMOTE_CONTENT_URLS = {
			"http://kappa.cs.petrsu.ru/~ivashov/mordor.xml",
			"http://oss.fruct.org/projects/roadsigns/root.xml"};

	public ContentFragment() {
		super();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRootUrls(REMOTE_CONTENT_URLS);
	}
}