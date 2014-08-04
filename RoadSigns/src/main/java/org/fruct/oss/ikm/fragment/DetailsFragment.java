package org.fruct.oss.ikm.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
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

import org.fruct.oss.ikm.DetailsActivity;
import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.gets.Gets;
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

			ListView searchList = (ListView) getActivity().findViewById(R.id.search_list);

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

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if (container == null)
			return null;

		super.onCreateView(inflater, container, savedInstanceState);
		return inflater.inflate(R.layout.details_fragment, null, false);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		final PointDesc desc = getArguments().getParcelable(DetailsActivity.POINT_ARG);
		assert desc != null;

		TextView titleView = (TextView) getActivity().findViewById(R.id.title_text);
		TextView descView = (TextView) getActivity().findViewById(R.id.details_text);
		
		if (titleView == null || descView == null)
			return;
		
		ImageButton placeButton = (ImageButton) getActivity().findViewById(R.id.show_place_button);
		placeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), MainActivity.class);
				
				intent.putExtra(MapFragment.MAP_CENTER, (Parcelable) desc.toPoint());
				startActivity(intent);
			}
		});
		
		ImageButton pathButton = (ImageButton) getActivity().findViewById(R.id.search_place_button);
		pathButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), MainActivity.class);
				intent.setAction(MainActivity.SHOW_PATH);
				
				intent.putExtra(MainActivity.SHOW_PATH_TARGET, (Parcelable) desc.toPoint());
				startActivity(intent);
			}
		});

		ImageButton searchButton = (ImageButton) getActivity().findViewById(R.id.search_button);
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
			ImageButton webButton = (ImageButton) getActivity().findViewById(R.id.browse_button);
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

	}

	@Override
	public void onDestroy() {
		if (searchTask != null)
			searchTask.cancel(true);

		super.onDestroy();
	}
}
