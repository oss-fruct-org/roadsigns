package org.fruct.oss.ikm.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.drawer.DrawerActivity;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.points.Point;
import org.fruct.oss.ikm.points.gets.Gets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DetailsFragment extends Fragment {
	public static final String ARG_POINT = "org.fruct.oss.ikm.ARG_POINT";

	public static DetailsFragment newInstance(Point point) {
		Bundle args = new Bundle();
		args.putParcelable(ARG_POINT, point);

		DetailsFragment detailsFragment = new DetailsFragment();
		detailsFragment.setArguments(args);
		return detailsFragment;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof DrawerActivity) {
			((DrawerActivity) activity).onSectionAttached(getString(R.string.title_activity_points),
					ActionBar.NAVIGATION_MODE_STANDARD);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.details_fragment, container, false);

		final Point point = getArguments().getParcelable(ARG_POINT);

		ImageView imageView = (ImageView) view.findViewById(R.id.image_view);
		TextView titleView = (TextView) view.findViewById(R.id.title_text);
		TextView descView = (TextView) view.findViewById(R.id.details_text);
		TextView searchView = (TextView) view.findViewById(R.id.wikipedia_link_text_view);

		Button placeButton = (Button) view.findViewById(R.id.show_place_button);
		placeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), DrawerActivity.class);
				intent.setAction(MapFragment.ACTION_CENTER_MAP);
				intent.putExtra(MapFragment.ARG_MAP_CENTER, (Parcelable) point.toPoint());
				startActivity(intent);
			}
		});

		Button pathButton = (Button) view.findViewById(R.id.search_place_button);
		pathButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), DrawerActivity.class);
				intent.setAction(MapFragment.ACTION_SHOW_PATH);
				intent.putExtra(MapFragment.ARG_SHOW_PATH_TARGET, (Parcelable) point.toPoint());
				startActivity(intent);
			}
		});

		if (point.hasPhoto()) {
			ImageLoader.getInstance().displayImage(point.getPhoto(), imageView);
		}

		// Show web button if description of point represents URL
		if (point.isDescriptionUrl()) {
			Button webButton = (Button) view.findViewById(R.id.browse_button);
			webButton.setVisibility(View.VISIBLE);

			webButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					Uri uri = Uri.parse(point.getDescription());
					intent.setData(uri);
					startActivity(intent);
				}
			});
		}

		titleView.setText(point.getName());

		descView.setAutoLinkMask(Linkify.WEB_URLS);

		// Setup wikipedia search
		String wikipediaUrl = "https://ru.wikipedia.org/wiki/Special:Search?search=" + Uri.encode(point.getName());

		searchView.setClickable(true);
		searchView.setMovementMethod(LinkMovementMethod.getInstance());
		searchView.setText(
				Html.fromHtml("<a href=\"" + wikipediaUrl + "\">" + getString(R.string.str_search_wikipedia) + "</a>")
		);

		if (point.getDescription().isEmpty())
			descView.setVisibility(View.GONE);
		else
			descView.setText(point.getDescription());

		return view;
	}


	@Override
	public void onDestroy() {
		super.onDestroy();
	}
}
