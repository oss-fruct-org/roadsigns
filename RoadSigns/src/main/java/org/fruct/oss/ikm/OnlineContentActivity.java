package org.fruct.oss.ikm;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

import org.fruct.oss.ikm.storage.FileStorage;
import org.fruct.oss.ikm.storage.IContentItem;
import org.fruct.oss.ikm.storage.NetworkProvider;
import org.fruct.oss.ikm.storage.RemoteContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

class ContentAdapter extends ArrayAdapter<RemoteContent.StorageItem> {
	class Tag {
		TextView text1;
		TextView text2;
		ImageView icon;
		RemoteContent.StorageItem storageItem;
	}

	private final int resource;

	public ContentAdapter(Context context, int resource, List<RemoteContent.StorageItem> objects) {
		super(context, resource, objects);
		//int i = android.R.layout.
		this.resource = resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		RemoteContent.StorageItem item = getItem(position);
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
			tag = new Tag();
			tag.text1 = (TextView) view.findViewById(android.R.id.text1);
			tag.text2 = (TextView) view.findViewById(android.R.id.text2);
			tag.icon = (ImageView) view.findViewById(android.R.id.icon1);
			tag.storageItem = item;
			view.setTag(tag);
			item.setTag(tag);
		}

		if (item.getItem().getDescription() != null)
			tag.text1.setText(item.getItem().getDescription());
		else
			tag.text1.setText(item.getItem().getName());

		float mbSize = (float) item.getItem().getSize() / (1024 * 1024);
		tag.text2.setText(String.format(Locale.getDefault(), "%.3f MB", mbSize));
		//}

		int resId = 0;
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
		}

		tag.icon.setImageResource(resId);

		return view;
	}
}

public class OnlineContentActivity extends ActionBarActivity
		implements AdapterView.OnItemClickListener, PopupMenu.OnMenuItemClickListener,
					PopupMenu.OnDismissListener, RemoteContent.Listener {
	private static Logger log = LoggerFactory.getLogger(OnlineContentActivity.class);

	private FileStorage storage;
	private RemoteContent remoteContent;
	private ListView listView;
	private ContentAdapter adapter;

	private MenuItem downloadItem;
	private MenuItem deleteItem;
	private MenuItem updateItem;
	private MenuItem useItem;

	private List<IContentItem> remoteList;
	private RemoteContent.StorageItem currentItem;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.online_content_layout);

		listView = (ListView) findViewById(R.id.list);
		listView.setOnItemClickListener(this);

		NetworkProvider provider = new NetworkProvider();
		storage = FileStorage.createExternalStorage("roadsigns-maps");
		remoteContent = new RemoteContent(storage, provider, "https://dl.dropboxusercontent.com/sh/x3qzpqcrqd7ftys/qNDPelAPa_/content.xml");

		setUpActionBar();

		remoteContent.setListener(this);
		remoteContent.startInitialize();
	}

	@Override
	protected void onDestroy() {
		if (storage != null) {
			storage.interrupt();
		}

		super.onDestroy();
	}

	private void setContentList(List<RemoteContent.StorageItem> list) {
		adapter = new ContentAdapter(this, R.layout.point_list_item, list);
		listView.setAdapter(adapter);
	}
/*

	private void loadContentList() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					remoteList = remoteContent.getContentList(provider,
							"https://dl.dropboxusercontent.com/sh/x3qzpqcrqd7ftys/qNDPelAPa_/content.xml");
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							log.debug("Received list size {}", remoteList.size());
							setContentList(remoteList);
						}
					});
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}
*/

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		RemoteContent.StorageItem listItem = adapter.getItem(i);
		currentItem = listItem;

		PopupMenu menu = new PopupMenu(this, view);
		switch (listItem.getState()) {
		case NEEDS_UPDATE:
			updateItem = menu.getMenu().add("Update");
			deleteItem = menu.getMenu().add("Delete");
			useItem = menu.getMenu().add("Use");
			break;

		case NOT_EXISTS:
			downloadItem = menu.getMenu().add("Download");
			break;

		case UP_TO_DATE:
		case DELETED_FROM_SERVER:
			deleteItem = menu.getMenu().add("Delete");
			useItem = menu.getMenu().add("Use");
			break;
		}

		menu.setOnMenuItemClickListener(this);
		menu.setOnDismissListener(this);

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
		if (menuItem == downloadItem || menuItem == updateItem) {
			remoteContent.startDownloading(currentItem);
		} else if (menuItem == deleteItem) {
			deleteItem(currentItem);
		} else if (menuItem == useItem) {
			String path = remoteContent.getPath(currentItem.getItem());
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
			pref.edit().putString(SettingsActivity.OFFLINE_MAP, path).apply();
		}

		return true;
	}

	@Override
	public void onDismiss(PopupMenu popupMenu) {
		downloadItem = deleteItem = updateItem = null;
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
	public void downloadStateUpdated(final RemoteContent.StorageItem item, final int downloaded, int max) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ContentAdapter.Tag tag = (ContentAdapter.Tag) item.getTag();
				float mbMax = (float) item.getItem().getSize() / (1024 * 1024);
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
		
	}
}
