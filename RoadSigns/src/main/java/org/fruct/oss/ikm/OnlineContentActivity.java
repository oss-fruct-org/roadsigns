package org.fruct.oss.ikm;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.fruct.oss.ikm.storage.RemoteContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class ContentDialog extends DialogFragment implements DialogInterface.OnClickListener, DialogInterface.OnMultiChoiceClickListener {
	private boolean[] active;

	interface Listener {
		void downloadsSelected(List<RemoteContent.StorageItem> items);
	}
	
	private List<RemoteContent.StorageItem> storageItems;
	private Listener listener;
	
	ContentDialog(List<RemoteContent.StorageItem> storageItem, Listener listener) {
		this.storageItems = storageItem;
		this.listener = listener;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		builder.setPositiveButton("Download", this);
		builder.setNegativeButton(android.R.string.cancel, this);

		builder.setTitle("Downloads");

		String[] strings = new String[storageItems.size()];
		active = new boolean[storageItems.size()];

		for (int i = 0; i < storageItems.size(); i++) {
			RemoteContent.StorageItem sItem = storageItems.get(i);

			String type = sItem.getItem().getType();
			if (type.equals("mapsforge-map"))
				strings[i] = "Offline map";
			else if (type.equals("graphhopper-map"))
				strings[i] = "Navigation data";

			active[i] = (sItem.getState() != RemoteContent.LocalContentState.UP_TO_DATE);
		}

		builder.setMultiChoiceItems(strings, active, this);

		return builder.create();
	}

	@Override
	public void onClick(DialogInterface dialogInterface, int i) {
		if (i == DialogInterface.BUTTON_POSITIVE && listener != null) {
			List<RemoteContent.StorageItem> ret = new ArrayList<RemoteContent.StorageItem>();

			for (int j = 0; j < active.length; j++) {
				if (active[j])
					ret.add(storageItems.get(j));
			}
			
			listener.downloadsSelected(ret);
		}
	}

	@Override
	public void onClick(DialogInterface dialogInterface, int i, boolean b) {
		active[i] = b;
	}
}

class ContentListItem {
	List<RemoteContent.StorageItem> contentItems;
	String name;
}

class ContentAdapter extends ArrayAdapter<ContentListItem> {
	class Tag {
		TextView text1;
		TextView text2;
		ImageView icon;
		RemoteContent.StorageItem storageItem;
		TextView text3;
	}

	private final int resource;
	private String currentActiveName;

	public ContentAdapter(Context context, int resource, List<ContentListItem> objects) {
		super(context, resource, objects);
		this.resource = resource;
	}

	public void setCurrentItemName(String currentActiveName) {
		this.currentActiveName = currentActiveName;
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
			for (RemoteContent.StorageItem sItem : item.contentItems)
				sItem.setTag(tag);
			/*for (IContentItem contentItem : item) {
				item.contentItem(tag);
			}*/
		}

		tag.text1.setText(item.name);
		tag.icon.setVisibility(View.GONE);
		/*float mbSize = (float) item.getItem().getDownloadSize() / (1024 * 1024);
		tag.text2.setText(String.format(Locale.getDefault(), "%.3f MB", mbSize));*/

		/*int resId = 0;
		switch (item.getState()) {
		case NOT_EXISTS:
			resId = android.R.drawable.ic_input_add;
			break;
		case UP_TO_DATE:
			resId = android.R.drawable.ic_input_get;
			break;
		case NEEDS_UPDATE:
			resId = android.R.drawable.ic_input_delete;
			break;
		case DELETED_FROM_SERVER:
			resId = android.R.drawable.ic_notification_clear_all;
		}*/

		//OnlineContentActivity.log.trace("{} {} " + item.getState().toString(), item.getItem().getName(), currentActiveName);
		/*if (item.getName().equals(currentActiveName)
				&& (item.getState() == RemoteContent.LocalContentState.UP_TO_DATE
					|| item.getState() == RemoteContent.LocalContentState.NEEDS_UPDATE)) {
			tag.text3.setVisibility(View.VISIBLE);
			tag.text3.setText("Current item");
		} else {
			tag.text3.setVisibility(View.GONE);
		}*/

		//tag.icon.setImageResource(resId);

		return view;
	}
}

public class OnlineContentActivity extends ActionBarActivity
		implements AdapterView.OnItemClickListener, PopupMenu.OnMenuItemClickListener,
					PopupMenu.OnDismissListener, RemoteContent.Listener, ContentDialog.Listener {
	public static final String ARG_REMOTE_CONTENT_URL = "org.fruct.oss.ikm.REMOTE_CONTENT_URL";
	public static final String ARG_LOCAL_STORAGE = "org.fruct.oss.ikm.LOCAL_STORAGE";
	public static final String ARG_PREF_KEY = "org.fruct.oss.ikm.PREF_KEY";

	static Logger log = LoggerFactory.getLogger(OnlineContentActivity.class);

	private RemoteContent remoteContent;
	private ListView listView;
	private ContentAdapter adapter;

	private MenuItem downloadItem;
	private MenuItem useItem;

	private ContentListItem currentItem;
	private String pref_key;
	private String currentActiveName;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		log.trace("onCreate");

		setContentView(R.layout.online_content_layout);

		listView = (ListView) findViewById(R.id.list);
		listView.setOnItemClickListener(this);

		String remoteContentUrl = getIntent().getStringExtra(ARG_REMOTE_CONTENT_URL);
		String localContentUrl = getIntent().getStringExtra(ARG_LOCAL_STORAGE);
		pref_key = getIntent().getStringExtra(ARG_PREF_KEY);

		remoteContent = RemoteContent.getInstance(remoteContentUrl, localContentUrl);

		setUpActionBar();

		remoteContent.setListener(this);

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		String currentPath = pref.getString(pref_key, null);

		log.trace("currentPath {}", currentPath);
		if (currentPath != null) {
			assert currentPath.lastIndexOf('/') + 1 <= currentPath.length();
			currentActiveName = currentPath.substring(currentPath.lastIndexOf('/') + 1);
			log.trace("currentActiveName {}", currentActiveName);

			if (currentActiveName.isEmpty())
				currentActiveName = null;
		}

		remoteContent.startInitialize();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		remoteContent.setListener(null);
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

		adapter = new ContentAdapter(this, R.layout.point_list_item, new ArrayList<ContentListItem>(listItems.values()));
		adapter.setCurrentItemName(currentActiveName);
		listView.setAdapter(adapter);
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		PopupMenu menu = new PopupMenu(this, view);
		currentItem = adapter.getItem(i);

		useItem = menu.getMenu().add("Use");
		downloadItem = menu.getMenu().add("Download");

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
			final ContentDialog dialog = new ContentDialog(currentItem.contentItems, this);
			dialog.show(getSupportFragmentManager(), "content-dialog");
		} else if (menuItem == useItem) {
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
			pref.edit().remove(pref_key).apply(); // Force notification

			final SharedPreferences.Editor edit = pref.edit();
			for (RemoteContent.StorageItem sItem : currentItem.contentItems) {
				if (sItem.getItem().getType().equals("mapsforge-map"))
					edit.putString(SettingsActivity.OFFLINE_MAP, remoteContent.getPath(sItem.getItem()));
				if (sItem.getItem().getType().equals("graphhopper-map"))
					edit.putString(SettingsActivity.NAVIGATION_DATA, remoteContent.getPath(sItem.getItem()));
			}
			edit.apply();

			finish();
		}

		return true;
	}

	@Override
	public void onDismiss(PopupMenu popupMenu) {
		downloadItem = null;
		currentItem = null;
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
				float mbMax = (float) max / (1024 * 1024);
				float mbCurrent = (float) downloaded / (1024 * 1024);
				tag.text2.setText(String.format(Locale.getDefault(), "%.3f/%.3f MB", mbCurrent, mbMax));
			}
		});
	}

	@Override
	public void downloadFinished(RemoteContent.StorageItem item) {
		showToast("Download finished");
	}

	@Override
	public void errorDownloading(RemoteContent.StorageItem item, IOException e) {
		showToast("Error downloading");
	}

	@Override
	public void downloadsSelected(List<RemoteContent.StorageItem> items) {
		for (RemoteContent.StorageItem item : items) {
			remoteContent.startDownloading(item);
		}
	}
}
