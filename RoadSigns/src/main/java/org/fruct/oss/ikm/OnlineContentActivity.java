package org.fruct.oss.ikm;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.fruct.oss.ikm.storage.ContentItem;
import org.fruct.oss.ikm.storage.RemoteContentService;
import org.fruct.oss.ikm.utils.bind.BindHelper;
import org.fruct.oss.ikm.utils.bind.BindSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class ContentListItem implements Comparable<ContentListItem> {
	List<ContentListSubItem> contentSubItems;
	String name;

	@Override
	public int compareTo(ContentListItem another) {
		return name.compareTo(another.name);
	}

}

class ContentListSubItem implements Comparable<ContentListSubItem> {
	ContentListSubItem(ContentItem contentItem, OnlineContentActivity.LocalContentState state) {
		this.contentItem = contentItem;
		this.state = state;
	}

	ContentItem contentItem;
	OnlineContentActivity.LocalContentState state;
	Object tag;

	@Override
	public int compareTo(ContentListSubItem another) {
		return contentItem.getType().compareTo(another.contentItem.getType());
	}
}

class ContentAdapter extends BaseAdapter {
	private List<ContentListItem> items;
	private final Context context;
	private final int resource;

	public ContentAdapter(Context context, int resource, List<ContentListItem> objects) {
		this.resource = resource;
		this.items = objects;
		this.context = context;
	}

	public void setItems(List<ContentListItem> items) {
		this.items = items;
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return items.size();
	}

	@Override
	public ContentListItem getItem(int position) {
		return items.get(position);
	}

	@Override
	public long getItemId(int position) {
		return getItem(position).name.hashCode();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ContentListItem item = getItem(position);
		LayoutInflater inflater = ((Activity) context).getLayoutInflater();
		View view = null;
		Holder tag = null;

		if (convertView != null && convertView.getTag() != null) {
			tag = (Holder) convertView.getTag();
			if (tag instanceof Holder) {
				view = convertView;
			}
		}

		if (view == null) {
			view = inflater.inflate(resource, parent, false);
			assert view != null;

			tag = new Holder();
			tag.text1 = (TextView) view.findViewById(android.R.id.text1);
			tag.text2 = (TextView) view.findViewById(android.R.id.text2);
			tag.text3 = (TextView) view.findViewById(R.id.text3);
			tag.icon = (ImageView) view.findViewById(android.R.id.icon1);
			view.setTag(tag);
			tag.parent = view;
		}

		tag.text1.setText(item.name);
		tag.icon.setVisibility(View.GONE);

		int idx = 0;
		TextView[] views = {tag.text2, tag.text3};
		for (TextView view2 : views)
			view2.setVisibility(View.GONE);

		for (ContentListSubItem subItem : item.contentSubItems) {
			subItem.tag = tag;
			OnlineContentActivity.LocalContentState state = subItem.state;
			ContentItem sItem = subItem.contentItem;

			boolean active = false;
			boolean needUpdate = false;

			if (state == OnlineContentActivity.LocalContentState.NOT_EXISTS) {
				active = true;
			} else if (state == OnlineContentActivity.LocalContentState.NEEDS_UPDATE) {
				active = true;
				needUpdate = true;
			}

			if (idx >= views.length)
				break;

			if (idx == 0)
				tag.item1 = sItem;
			else
				tag.item2 = sItem;

			String text = "";
			if (sItem.getType().equals(RemoteContentService.GRAPHHOPPER_MAP)) {
				text = context.getString(R.string.navigation_data);
			} else if (sItem.getType().equals(RemoteContentService.MAPSFORGE_MAP)) {
				text = context.getString(R.string.offline_map);
			}

			if (needUpdate)
				text += " (" + context.getString(R.string.update_availabe) + ")";
			views[idx].setText(text);

			views[idx].setVisibility(View.VISIBLE);
			if (active)
				views[idx].setTypeface(null, Typeface.BOLD);
			else
				views[idx].setTypeface(null, Typeface.NORMAL);

			idx++;
		}

		return view;
	}

	class Holder {
		View parent;
		TextView text1;
		TextView text2;
		ImageView icon;
		TextView text3;

		// Item corresponding second text line
		ContentItem item1;

		// Item corresponding third text line
		ContentItem item2;
	}
}

public class OnlineContentActivity extends ActionBarActivity
		implements AdapterView.OnItemClickListener, RemoteContentService.Listener, ContentDialog.Listener, ActionMode.Callback, DownloadProgressFragment.OnFragmentInteractionListener, ActionBar.OnNavigationListener {

	public enum LocalContentState {
		NOT_EXISTS, NEEDS_UPDATE, UP_TO_DATE, DELETED_FROM_SERVER
	}

	private final static Logger log = LoggerFactory.getLogger(OnlineContentActivity.class);

	private ListView listView;
	private ContentAdapter adapter;

	private MenuItem downloadItem;
	private MenuItem useItem;

	// Last selected item
	private ContentListItem currentItem;
	private String currentItemName;

	private LocalContentState filteredState;

	private RemoteContentService remoteContent;

	private List<ContentItem> localItems = Collections.emptyList();
	private List<ContentItem> remoteItems = Collections.emptyList();

	private SharedPreferences pref;

	private DownloadProgressFragment downloadFragment;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null)
			currentItemName = savedInstanceState.getString("current-item-idx");

		setContentView(R.layout.online_content_layout);
		setupSpinner();

		adapter = new ContentAdapter(this, R.layout.point_list_item, Collections.<ContentListItem>emptyList());

		listView = (ListView) findViewById(R.id.list);
		listView.setOnItemClickListener(this);
		listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		setUpActionBar();

		BindHelper.autoBind(this, this);

		listView.setAdapter(adapter);

		pref = PreferenceManager.getDefaultSharedPreferences(this);

		downloadFragment = (DownloadProgressFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);
		getSupportFragmentManager().beginTransaction().hide(downloadFragment).commit();
	}

	private void setupSpinner() {
		ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		SpinnerAdapter spinnerAdapter = ArrayAdapter.createFromResource(this, R.array.content_spinner,
				android.support.v7.appcompat.R.layout.support_simple_spinner_dropdown_item);
		actionBar.setListNavigationCallbacks(spinnerAdapter, this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("current-item-idx", currentItemName);
	}

	@Override
	protected void onDestroy() {
		BindHelper.autoUnbind(this, this);
		if (remoteContent != null) {
			remoteContent.removeListener(this);
			remoteContent = null;
		}

		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.online_content, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_refresh:
			if (remoteContent != null) {
				remoteContent.refresh();
			}
			break;
		}

		return super.onOptionsItemSelected(item);
	}

	@BindSetter
	public void remoteContentServiceReady(RemoteContentService service) {
		if (service == null)
			return;

		remoteContent = service;
		remoteContent.addListener(this);

		setContentList(localItems = new ArrayList<ContentItem>(remoteContent.getLocalItems()),
				remoteItems = new ArrayList<ContentItem>(remoteContent.getRemoteItems()));
	}


	private void setContentList(final List<ContentItem> localItems, final List<ContentItem> remoteItems) {
		new GenerateContentList(localItems, remoteItems, filteredState).execute();
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setUpActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		} else {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

	private void showToast(final String string) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(OnlineContentActivity.this, string, Toast.LENGTH_SHORT).show();
			}
		});
	}

	/*private void deleteItem(ContentItem item) {
		try {
			remoteContent.deleteItem()
			showToast("Successfully deleted");
		} catch (IOException e) {
			e.printStackTrace();
			showToast("Can not delete");
		}
	}*/

	@Override
	public void localListReady(List<ContentItem> list) {
		localItems = new ArrayList<ContentItem>(list);
		setContentList(localItems, remoteItems);
	}

	@Override
	public void remoteListReady(List<ContentItem> list) {
		remoteItems = new ArrayList<ContentItem>(list);
		setContentList(localItems, remoteItems);
	}

	@Override
	public void downloadStateUpdated(ContentItem item, int downloaded, int max) {
		downloadFragment.downloadStateUpdated(item, downloaded, max);
	}

	@Override
	public void downloadFinished(ContentItem localItem, ContentItem remoteItem) {
		showToast(getString(R.string.download_finished));
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				downloadFragment.stopDownload();
			}
		});
	}

	@Override
	public void errorDownloading(ContentItem item, IOException e) {
		showToast(getString(R.string.error_downloading));
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				downloadFragment.stopDownload();
			}
		});
	}

	@Override
	public void errorInitializing(IOException e) {
		showToast(getString(R.string.no_networks_offline));
	}

	@Override
	public void downloadInterrupted(ContentItem sItem) {
		showToast(getString(R.string.download_interrupted));
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				downloadFragment.stopDownload();
				adapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void downloadsSelected(List<ContentListSubItem> items) {
		if (currentItem == null)
			return;

		downloadFragment.startDownload();
		for (ContentListSubItem item : items)
			remoteContent.downloadItem(item.contentItem);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		currentItem = adapter.getItem(position);
		startSupportActionMode(this);
	}

	@Override
	public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
		actionMode.getMenuInflater().inflate(R.menu.online_content_action, menu);

		boolean hasDeletable = false;
		boolean hasDownloadable = false;

		for (ContentListSubItem contentSubItem : currentItem.contentSubItems) {
			if (contentSubItem.contentItem.isDownloadable() || contentSubItem.state == LocalContentState.NEEDS_UPDATE) {
				hasDownloadable = true;
			}

			if (!contentSubItem.contentItem.isReadonly()) {
				hasDeletable = true;
			}
		}

		if (!hasDeletable) {
			menu.findItem(R.id.action_delete).setVisible(false);
		}

		if (!hasDownloadable) {
			menu.findItem(R.id.action_download).setVisible(false);
		}

		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
		if (menuItem.getItemId() == R.id.action_download) {
			final ContentDialog dialog = new ContentDialog();
			dialog.setStorageItems(currentItem.contentSubItems);
			dialog.show(getSupportFragmentManager(), "content-dialog");
		} else if (menuItem.getItemId() == R.id.action_delete) {
			deleteContentItem(currentItem);
		} else if (menuItem.getItemId() == R.id.action_use) {
			useContentItem(currentItem);
		} else {
			return false;
		}

		actionMode.finish();

		return false;
	}

	private void deleteContentItem(ContentListItem currentItem) {
		boolean wasError = false;
		if (remoteContent != null && !currentItem.contentSubItems.isEmpty()) {
			for (ContentListSubItem subItem : currentItem.contentSubItems) {
				if (!remoteContent.deleteContentItem(subItem.contentItem))
					wasError = true;
			}
		}

		if (wasError) {
			Toast.makeText(this, getString(R.string.str_cant_delete_active_content), Toast.LENGTH_LONG).show();
		}
	}

	private void useContentItem(ContentListItem currentItem) {
		throw new IllegalStateException("Not implemented yet");
		//if (remoteContent != null && !currentItem.contentSubItems.isEmpty()) {
		//	remoteContent.activateRegionById(currentItem.contentSubItems.get(0).contentItem.getRegionId());
		//}
	}

	@Override
	public void stopButtonPressed() {
		if (remoteContent != null) {
			remoteContent.interrupt();
		}
	}

	@Override
	public void onDestroyActionMode(ActionMode actionMode) {
		listView.clearChoices();
		listView.setItemChecked(-1, true);
	}

	@Override
	public boolean onNavigationItemSelected(int i, long l) {
		switch (i) {
		case 0: // All
			new GenerateContentList(localItems, remoteItems, filteredState = null).execute();
			return true;

		case 1: // Local
			new GenerateContentList(localItems, remoteItems, filteredState = LocalContentState.UP_TO_DATE).execute();
			return true;

		case 2: // Updates
			new GenerateContentList(localItems, remoteItems, filteredState = LocalContentState.NEEDS_UPDATE).execute();
			return true;
		}

		return false;
	}

	private class GenerateContentList extends AsyncTask<Void, Void, List<ContentListItem>> {
		private final LocalContentState filterState;
		private List<ContentItem> localItems;
		private List<ContentItem> remoteItems;

		private GenerateContentList(List<ContentItem> localItems, List<ContentItem> remoteItems, LocalContentState filterState) {
			this.localItems = localItems;
			this.remoteItems = remoteItems;
			this.filterState = filterState;
		}

		@Override
		protected List<ContentListItem> doInBackground(Void... params) {
			HashMap<String, ContentListSubItem> states
					= new HashMap<String, ContentListSubItem>(localItems.size());

			for (ContentItem item : localItems) {
				states.put(item.getName(), new ContentListSubItem(item, LocalContentState.DELETED_FROM_SERVER));
			}

			for (ContentItem remoteItem : remoteItems) {
				String name = remoteItem.getName();

				ContentListSubItem subItem = states.get(name);
				ContentItem localItem = subItem == null ? null : subItem.contentItem;

				LocalContentState newState;
				ContentItem saveItem = remoteItem;

				if (localItem == null) {
					newState = LocalContentState.NOT_EXISTS;
				} else if (!localItem.getHash().equals(remoteItem.getHash())) {
					newState = LocalContentState.NEEDS_UPDATE;
				} else {
					saveItem = localItem;
					newState = LocalContentState.UP_TO_DATE;
				}

				states.put(name, new ContentListSubItem(saveItem, newState));
			}

			HashMap<String, List<ContentListSubItem>> listViewMap
					= new HashMap<String, List<ContentListSubItem>>();

			for (Map.Entry<String, ContentListSubItem> entry : states.entrySet()) {
				String rId = entry.getValue().contentItem.getRegionId();

				if (filterState != null
						&& filterState != entry.getValue().state
						&& ((filterState != LocalContentState.UP_TO_DATE
						|| entry.getValue().state != LocalContentState.NEEDS_UPDATE))) {
							continue;
						}

				List<ContentListSubItem> l = listViewMap.get(rId);

				if (l == null) {
					l = new ArrayList<ContentListSubItem>();
					listViewMap.put(rId, l);
				}

				l.add(entry.getValue());
			}

			List<ContentListItem> listViewItems = new ArrayList<ContentListItem>();
			for (Map.Entry<String, List<ContentListSubItem>> entry : listViewMap.entrySet()) {
				ContentListItem listViewItem = new ContentListItem();

				listViewItem.name = entry.getValue().get(0).contentItem.getDescription();
				listViewItem.contentSubItems = entry.getValue();

				Collections.sort(listViewItem.contentSubItems);
				listViewItems.add(listViewItem);
			}

			Collections.sort(listViewItems);

			return listViewItems;
		}

		@Override
		protected void onPostExecute(List<ContentListItem> contentListItems) {
			if (contentListItems != null && adapter != null) {
				adapter.setItems(contentListItems);
			}
		}
	}

}
