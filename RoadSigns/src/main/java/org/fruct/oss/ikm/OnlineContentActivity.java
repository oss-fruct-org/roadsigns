package org.fruct.oss.ikm;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.fruct.oss.ikm.storage2.ContentItem;
import org.fruct.oss.ikm.storage2.RemoteContentService;
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
			if (sItem.getType().equals("graphhopper-map")) {
				text = context.getString(R.string.navigation_data);
			} else if (sItem.getType().equals("mapsforge-map")) {
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

	public void updateDownloadString(ContentItem ci, String downloadString) {
		// This optimization can cause error after list rotation
		if (ci != lastUpdateItem) {
			for (ContentListItem cli : items) {
				for (ContentListSubItem clsi : cli.contentSubItems) {
					if (ci == clsi.contentItem) {
						lastUpdateTag = (Holder) clsi.tag;
						lastUpdateItem = ci;
						break;
					}
				}
			}
		}

		TextView textView = null;

		if (lastUpdateTag.item1 == ci) {
			textView = lastUpdateTag.text2;
		} else 	if (lastUpdateTag.item2 == ci) {
			textView = lastUpdateTag.text3;
		}

		if (textView != null) {
			textView.setTypeface(null, Typeface.NORMAL);
			textView.setText(downloadString);
		}
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

	// Optimization of download string update
	private Holder lastUpdateTag;
	private ContentItem lastUpdateItem;
}

public class OnlineContentActivity extends ActionBarActivity
		implements AdapterView.OnItemClickListener, RemoteContentService.Listener, ContentDialog.Listener, ActionMode.Callback {

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

	private String currentActiveName;

	private RemoteContentService remoteContent;

	private List<ContentItem> localItems = Collections.emptyList();
	private List<ContentItem> remoteItems = Collections.emptyList();

	private SharedPreferences pref;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null)
			currentItemName = savedInstanceState.getString("current-item-idx");

		setContentView(R.layout.online_content_layout);

		adapter = new ContentAdapter(this, R.layout.point_list_item, Collections.<ContentListItem>emptyList());

		listView = (ListView) findViewById(R.id.list);
		listView.setOnItemClickListener(this);

		setUpActionBar();

		BindHelper.autoBind(this, this);

		listView.setAdapter(adapter);

		pref = PreferenceManager.getDefaultSharedPreferences(this);
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

		case R.id.action_stop:
			remoteContent.interrupt();
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

	private void setContentList(List<ContentItem> localItems, List<ContentItem> remoteItems) {
		HashMap<String, ContentListSubItem> states
				= new HashMap<String, ContentListSubItem>(localItems.size());

		for (ContentItem item : localItems) {
			states.put(item.getName(), new ContentListSubItem(item, LocalContentState.DELETED_FROM_SERVER));
		}

		// TODO: can cause concurrent modification
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

		adapter.setItems(listViewItems);
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
		float mbMax = (float) max / (1024 * 1024);
		float mbCurrent = (float) downloaded / (1024 * 1024);
		String downloadString = String.format(Locale.getDefault(), "%.3f/%.3f MB", mbCurrent, mbMax);
		adapter.updateDownloadString(item, downloadString);
	}

	@Override
	public void downloadFinished(ContentItem localItem, ContentItem remoteItem) {
		showToast(getString(R.string.download_finished));
	}

	@Override
	public void errorDownloading(ContentItem item, IOException e) {
		showToast(getString(R.string.error_downloading));
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
				adapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void downloadsSelected(List<Integer> items) {
		if (currentItem == null)
			return;

		for (int i : items)
			remoteContent.downloadItem(currentItem.contentSubItems.get(i).contentItem);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		ContentListItem listItem = adapter.getItem(position);
		startSupportActionMode(this);

		currentItem = listItem;
	}

	@Override
	public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
		actionMode.getMenuInflater().inflate(R.menu.online_content_action, menu);
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
			actionMode.finish();
			return true;
		} else if (menuItem.getItemId() == R.id.action_delete) {
			actionMode.finish();
			throw new IllegalStateException("Not implemented yet");
		} else if (menuItem.getItemId() == R.id.action_use) {
			useContentItem(currentItem);
			actionMode.finish();
		}

		return false;
	}

	private void useContentItem(ContentListItem currentItem) {
		for (ContentListSubItem item : currentItem.contentSubItems) {
			if (item.contentItem.getType().equals("graphhopper-map")) {
				pref.edit().remove(SettingsActivity.NAVIGATION_DATA).apply();
				pref.edit().putString(SettingsActivity.NAVIGATION_DATA, item.contentItem.getName()).apply();
			} else if (item.contentItem.getType().equals("mapsforge-map")) {
				pref.edit().remove(SettingsActivity.OFFLINE_MAP).apply();
				pref.edit().putString(SettingsActivity.OFFLINE_MAP, item.contentItem.getName()).apply();
			}
		}
	}

	@Override
	public void onDestroyActionMode(ActionMode actionMode) {
	}
}
