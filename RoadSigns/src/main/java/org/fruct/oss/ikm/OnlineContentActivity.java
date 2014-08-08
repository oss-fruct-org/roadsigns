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
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.fruct.oss.ikm.fragment.MapFragment;
import org.fruct.oss.ikm.storage.RemoteContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class ContentListItem {
	List<RemoteContent.StorageItem> contentItems;
	String name;
}

class ContentAdapter extends ArrayAdapter<ContentListItem> {
	class Tag {
		View parent;
		TextView text1;
		TextView text2;
		ImageView icon;
		TextView text3;

		// Item corresponding second text line
		RemoteContent.StorageItem item1;

		// Item corresponding third text line
		RemoteContent.StorageItem item2;
	}

	private final int resource;

	public ContentAdapter(Context context, int resource, List<ContentListItem> objects) {
		super(context, resource, objects);
		this.resource = resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ContentListItem item = getItem(position);
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
			tag.text1 = (TextView) view.findViewById(android.R.id.text1);
			tag.text2 = (TextView) view.findViewById(android.R.id.text2);
			tag.text3 = (TextView) view.findViewById(R.id.text3);
			tag.icon = (ImageView) view.findViewById(android.R.id.icon1);
			view.setTag(tag);
			tag.parent = view;

			/*for (IContentItem contentItem : item) {
				item.contentItem(tag);
			}*/
		}

		for (RemoteContent.StorageItem sItem : item.contentItems)
			sItem.setTag(tag);

		tag.text1.setText(item.name);
		tag.icon.setVisibility(View.GONE);

		int idx = 0;
		TextView[] views = {tag.text2, tag.text3};
		for (TextView view2 : views)
			view2.setVisibility(View.GONE);

		for (RemoteContent.StorageItem sItem : item.contentItems) {
			boolean active = false;
			boolean needUpdate = false;

			if (sItem.getState() == RemoteContent.LocalContentState.NOT_EXISTS)
				active = true;

			if (sItem.getState() == RemoteContent.LocalContentState.NEEDS_UPDATE) {
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
			if (sItem.getItem().getType().equals("graphhopper-map")) {
				text = getContext().getString(R.string.navigation_data);
			} else if (sItem.getItem().getType().equals("mapsforge-map")) {
				text = getContext().getString(R.string.offline_map);
			}

			if (needUpdate)
				text += " (" + getContext().getString(R.string.update_availabe) +")";
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
}

public class OnlineContentActivity extends ActionBarActivity
		implements AdapterView.OnItemClickListener, PopupMenu.OnMenuItemClickListener,
					PopupMenu.OnDismissListener, RemoteContent.Listener, ContentDialog.Listener {

	static Logger log = LoggerFactory.getLogger(OnlineContentActivity.class);

	private RemoteContent remoteContent;
	private ListView listView;
	private ContentAdapter adapter;

	private MenuItem downloadItem;
	private MenuItem useItem;

	// Last selected item
	private ContentListItem currentItem;
	private String currentItemName;

	private String currentActiveName;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		log.trace("onCreate");

		if (savedInstanceState != null)
			currentItemName = savedInstanceState.getString("current-item-idx");

		setContentView(R.layout.online_content_layout);

		listView = (ListView) findViewById(R.id.list);
		listView.setOnItemClickListener(this);

		// TODO: initialize local storage path on first run
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

		remoteContent = RemoteContent.getInstance(pref.getString(SettingsActivity.STORAGE_PATH, null));
		remoteContent.addListener(this);

		setUpActionBar();

		remoteContent.startInitialize(false);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("current-item-idx", currentItemName);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		remoteContent.removeListener(this);
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
				remoteContent.startInitialize(true);
			}
			break;

		case R.id.action_stop:
			remoteContent.stopAll();
			break;
		}

		return super.onOptionsItemSelected(item);
	}

	private void setContentList(List<RemoteContent.StorageItem> list) {
		log.trace("setContentList");

		Map<String /* name */, ContentListItem> listItems = new HashMap<String, ContentListItem>();
		for (RemoteContent.StorageItem item : list) {
			final String description = item.getItem().getDescription();
			ContentListItem listItem = listItems.get(description);
			if (listItem == null) {
				listItem = new ContentListItem();
				listItems.put(description, listItem);
				listItem.name = description;
				listItem.contentItems = new ArrayList<RemoteContent.StorageItem>();
			}

			listItem.contentItems.add(item);
		}

		// Restore saved currentItem
		if (currentItemName != null)
			currentItem = listItems.get(currentItemName);

		adapter = new ContentAdapter(this, R.layout.point_list_item, new ArrayList<ContentListItem>(listItems.values()));
		listView.setAdapter(adapter);
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		PopupMenu menu = new PopupMenu(this, view);
		currentItem = adapter.getItem(i);
		currentItemName = currentItem.name;

		if (currentItem.contentItems.size() > 0)
			useItem = menu.getMenu().add(R.string.use);
		downloadItem = menu.getMenu().add(R.string.download);

		menu.setOnDismissListener(this);
		menu.setOnMenuItemClickListener(this);

		menu.show();
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

	private void deleteItem(RemoteContent.StorageItem item) {
		try {
			remoteContent.deleteContentItem(item);
			showToast("Successfully deleted");
		} catch (IOException e) {
			e.printStackTrace();
			showToast("Can not delete");
		}
	}

	@Override
	public boolean onMenuItemClick(MenuItem menuItem) {
		if (menuItem == downloadItem) {
			final ContentDialog dialog = new ContentDialog(currentItem.contentItems);

			dialog.show(getSupportFragmentManager(), "content-dialog");
		} else if (menuItem == useItem) {
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

			final SharedPreferences.Editor edit = pref.edit();
			for (RemoteContent.StorageItem sItem : currentItem.contentItems) {
				if (sItem.getItem().getType().equals("mapsforge-map")) {
					pref.edit().remove(SettingsActivity.OFFLINE_MAP).apply(); // Force notification
					edit.putString(SettingsActivity.OFFLINE_MAP, remoteContent.getPath(sItem.getItem()));
				}

				if (sItem.getItem().getType().equals("graphhopper-map")) {
					pref.edit().remove(SettingsActivity.NAVIGATION_DATA).apply(); // Force notification
					edit.putString(SettingsActivity.NAVIGATION_DATA, remoteContent.getPath(sItem.getItem()));
				}
			}
			edit.apply();

			finish();
		}

		return true;
	}

	@Override
	public void onDismiss(PopupMenu popupMenu) {
		downloadItem = useItem = null;
	}

	@Override
	public void listReady(final List<RemoteContent.StorageItem> list) {
		runOnUiThread(new Runnable() {
			@Override
				public void run() {
				setContentList(list);
			}
		});
	}

	@Override
	public void downloadStateUpdated(final RemoteContent.StorageItem item, final int downloaded, final int max) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ContentAdapter.Tag tag = (ContentAdapter.Tag) item.getTag();

				TextView textView = tag.item1 == item ? tag.text2 : tag.text3;
				textView.setTypeface(null, Typeface.NORMAL);

				float mbMax = (float) max / (1024 * 1024);
				float mbCurrent = (float) downloaded / (1024 * 1024);
				textView.setText(String.format(Locale.getDefault(), "%.3f/%.3f MB", mbCurrent, mbMax));
			}
		});
	}

	@Override
	public void downloadFinished(RemoteContent.StorageItem item) {
		showToast(getString(R.string.download_finished));
	}

	@Override
	public void errorDownloading(RemoteContent.StorageItem item, IOException e) {
		showToast(getString(R.string.error_downloading));
	}

	@Override
	public void errorInitializing(IOException e) {
		showToast(getString(R.string.no_networks_offline));
	}

	@Override
	public void downloadInterrupted(RemoteContent.StorageItem sItem) {
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
			remoteContent.startDownloading(currentItem.contentItems.get(i));
	}
}
