package org.fruct.oss.ikm.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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

public class DetailsFragment extends Fragment {
	private static Logger log = LoggerFactory.getLogger(SearchTask.class);
	private SearchTask searchTask;

	public static final String ARG_POINT = "org.fruct.oss.ikm.ARG_POINT";

	private static class SearchItem {
		public SearchItem(String title, String url) {
			this.title = title;
			this.url = url;
		}

		@Override
		public String toString() {
			return "SearchItem{" +
					"title='" + title + '\'' +
					", url='" + url + '\'' +
					'}';
		}

		public String title;
		public String url;
	}

	private class SearchTask extends AsyncTask<String, Integer, List<SearchItem>> {
		private static final int SEARCH_LIMIT = 10;
		private static final String SEARCH_URL = "http://ru.wikipedia.org/w/api.php" +
				"?action=query" +
				"&list=search" +
				"&srsearch=%s" +
				"&srlimit=%d" +
				"&srprop=" +
				"&format=json";

		@Override
		protected List<SearchItem> doInBackground(String... strings) {
			String searchString = strings[0];
			String encodedString = Uri.encode(searchString);
			String searchUrl = String.format(SEARCH_URL, encodedString, SEARCH_LIMIT);
			String responseJson;

			log.debug("Starting wikipedia search");
			try {
				responseJson = Gets.downloadUrl(searchUrl, null);
			} catch (InterruptedIOException e) {
				e.printStackTrace();
				return null;
			} catch (IOException e) {
				log.debug("IOException, interrupting");
				e.printStackTrace();
				return null;
			}

			log.debug("Parsing response...");
			ArrayList<SearchItem> items = new ArrayList<SearchItem>(5);

			try {
				JSONObject json = new JSONObject(responseJson);
				JSONObject queryObject = json.getJSONObject("query");
				JSONArray searchArray = queryObject.getJSONArray("search");

				for (int i = 0; i < searchArray.length(); i++) {
					JSONObject item = searchArray.getJSONObject(i);
					String title = item.getString("title");
					String titleEncoded = Uri.encode(title);

					String itemUrl = "http://ru.wikipedia.org/wiki/" + titleEncoded;
					SearchItem searchItem = new SearchItem(title, itemUrl);

					items.add(searchItem);
					log.debug(searchItem.toString());
				}
			} catch (JSONException e) {
				e.printStackTrace();
				return null;
			}

			return items;
		}

		@Override
		protected void onPostExecute(final List<SearchItem> searchItems) {
			// Error downloading
			if (searchItems == null) {
				// Interrupted by error
				if (!isCancelled())
					Toast.makeText(getActivity(), "Network error", Toast.LENGTH_LONG).show();
				return;
			}

			if (searchItems.isEmpty()) {
				Toast.makeText(getActivity(), "Nothing found", Toast.LENGTH_LONG).show();
				return;
			}

			ListView searchList = (ListView) getView().findViewById(R.id.search_list);

			// Get list of page titles
			List<String> adapterList = Utils.map(searchItems, new Utils.Function<String, SearchItem>() {
				@Override
				public String apply(SearchItem searchItem) {
					return searchItem.title;
				}
			});

			// Create String adapter
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
					android.R.layout.simple_list_item_1, adapterList);
			searchList.setAdapter(adapter);
			searchList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(searchItems.get(i).url));
					getActivity().startActivity(intent);
				}
			});
			searchList.setVisibility(View.VISIBLE);
		}
	}

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

		final Point desc = getArguments().getParcelable(ARG_POINT);

		TextView titleView = (TextView) view.findViewById(R.id.title_text);
		TextView descView = (TextView) view.findViewById(R.id.details_text);

		ImageButton placeButton = (ImageButton) view.findViewById(R.id.show_place_button);
		placeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), DrawerActivity.class);
				intent.setAction(MapFragment.ACTION_CENTER_MAP);
				intent.putExtra(MapFragment.ARG_MAP_CENTER, (Parcelable) desc.toPoint());
				startActivity(intent);
			}
		});

		ImageButton pathButton = (ImageButton) view.findViewById(R.id.search_place_button);
		pathButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), DrawerActivity.class);
				intent.setAction(MapFragment.ACTION_SHOW_PATH);
				intent.putExtra(MapFragment.ARG_SHOW_PATH_TARGET, (Parcelable) desc.toPoint());
				startActivity(intent);
			}
		});

		ImageButton searchButton = (ImageButton) view.findViewById(R.id.search_button);
		searchButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String searchString = desc.getName();

				searchTask = new SearchTask();
				searchTask.execute(searchString);

				v.setClickable(false);
			}
		});

		// Show web button if description of point represents URL
		if (desc.isDescriptionUrl()) {
			ImageButton webButton = (ImageButton) view.findViewById(R.id.browse_button);
			webButton.setVisibility(View.VISIBLE);

			webButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					Uri uri = Uri.parse(desc.getDescription());
					intent.setData(uri);
					startActivity(intent);
				}
			});
		}

		titleView.setText(desc.getName());

		descView.setAutoLinkMask(Linkify.WEB_URLS);

		if (desc.getDescription().isEmpty())
			descView.setVisibility(View.GONE);
		else
			descView.setText(desc.getDescription());

		return view;
	}


	@Override
	public void onDestroy() {
		if (searchTask != null)
			searchTask.cancel(true);

		super.onDestroy();
	}
}
