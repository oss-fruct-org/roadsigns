package org.fruct.oss.ikm;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
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
import org.fruct.oss.ikm.storage.IContentConnection;
import org.fruct.oss.ikm.storage.IContentItem;
import org.fruct.oss.ikm.storage.NetworkProvider;
import org.fruct.oss.ikm.storage.RemoteContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ContentListItem {
	IContentItem item;
	RemoteContent.LocalContentState state;
	boolean isDownloading;
	int downloaded;
	View view;
}

class ContentAdapter extends ArrayAdapter<ContentListItem> {
	class Tag {
		TextView text1;
		TextView text2;
		ImageView icon;
		ContentListItem contentItem;
	}

	private final int resource;

	public ContentAdapter(Context context, int resource, List<ContentListItem> objects) {
		super(context, resource, objects);
		//int i = android.R.layout.
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
			tag = new Tag();
			tag.text1 = (TextView) view.findViewById(android.R.id.text1);
			tag.text2 = (TextView) view.findViewById(android.R.id.text2);
			tag.icon = (ImageView) view.findViewById(android.R.id.icon1);
			tag.contentItem = item;
			view.setTag(tag);
		}

		if (item.item.getDescription() != null)
			tag.text1.setText(item.item.getDescription());
		else
			tag.text1.setText(item.item.getName());

		if (item.isDownloading) {
			float mbMax = (float) item.item.getSize() / (1024 * 1024);
			float mbCurrent = (float) item.downloaded / (1024 * 1024);
			tag.text2.setText(String.format(Locale.getDefault(), "%.3f/%.3f MB", mbCurrent, mbMax));
		} else {
			float mbSize = (float) item.item.getSize() / (1024 * 1024);
			tag.text2.setText(String.format(Locale.getDefault(), "%.3f MB", mbSize));
		}

		int resId = 0;
		switch (item.state) {
		case NOT_EXISTS:
			resId = android.R.drawable.ic_input_add;
			break;
		case UP_TO_DATE:
			resId = android.R.drawable.ic_input_get;
			break;
		case NEEDS_UPDATE:
			resId = android.R.drawable.ic_input_delete;
			break;
		}

		tag.icon.setImageResource(resId);
		item.view = view;

		return view;
	}
}

public class OnlineContentActivity extends ActionBarActivity implements AdapterView.OnItemClickListener, PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener {
	private static Logger log = LoggerFactory.getLogger(OnlineContentActivity.class);

	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private NetworkProvider provider;
	private FileStorage storage;
	private RemoteContent remoteContent;
	private ListView listView;
	private ContentAdapter adapter;

	private MenuItem downloadItem;
	private MenuItem deleteItem;
	private MenuItem updateItem;
	private ContentListItem currentItem;
	private List<IContentItem> remoteList;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.online_content_layout);

		listView = (ListView) findViewById(R.id.list);
		listView.setOnItemClickListener(this);

		provider = new NetworkProvider();
		storage = FileStorage.createExternalStorage("roadsigns-maps");
		remoteContent = new RemoteContent(storage);

		try {
			initialize();
		} catch (IOException e) {
			// TODO: handle this exception
		}

		loadContentList();
		setUpActionBar();
	}

	@Override
	protected void onDestroy() {
		if (storage != null) {
			storage.interrupt();
		}

		super.onDestroy();
	}

	private void initialize() throws IOException {
		remoteContent.initialize();
	}

	private void setContentList(List<IContentItem> list) {
		List<ContentListItem> adapterList = Utils.map(list, new Utils.Function<ContentListItem, IContentItem>() {
			@Override
			public ContentListItem apply(IContentItem item) {
				ContentListItem adapterItem = new ContentListItem();
				adapterItem.item = item;
				adapterItem.state = remoteContent.checkLocalState(item);
				return adapterItem;
			}
		});

		adapter = new ContentAdapter(this, R.layout.point_list_item, adapterList);
		listView.setAdapter(adapter);
	}

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

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		log.debug("Clicked{}", i);

		ContentListItem listItem = adapter.getItem(i);
		currentItem = listItem;

		PopupMenu menu = new PopupMenu(this, view);
		switch (listItem.state) {
		case NEEDS_UPDATE:
			updateItem = menu.getMenu().add("Update");
			deleteItem = menu.getMenu().add("Delete");
			break;
		case NOT_EXISTS:
			downloadItem = menu.getMenu().add("Download");
			break;
		case UP_TO_DATE:
			deleteItem = menu.getMenu().add("Delete");
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

	private void setItemDownloadStatus(final ContentListItem item, final int current) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				item.downloaded = current;
				ContentAdapter.Tag tag = (ContentAdapter.Tag) item.view.getTag();
				float mbMax = (float) item.item.getSize() / (1024 * 1024);
				float mbCurrent = (float) item.downloaded / (1024 * 1024);
				tag.text2.setText(String.format(Locale.getDefault(), "%.3f/%.3f MB", mbCurrent, mbMax));
			}
		});
	}

	private void downloadItem(final ContentListItem listItem) {
		IContentItem item = listItem.item;
		IContentConnection conn = null;
		listItem.downloaded = 0;
		listItem.isDownloading = true;

		try {
			conn = provider.loadContentItem(item.getUrl());

			ProgressInputStream progressStream = new ProgressInputStream(conn.getStream(), item.getSize(),
					10000, new ProgressInputStream.ProgressListener() {
				@Override
				public void update(int current, int max) {
					setItemDownloadStatus(listItem, current);
				}
			});

			remoteContent.storeContentItem(item, storage, progressStream);
			showToast("Successfully downloaded");

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// Update list view
					setContentList(remoteList);
				}
			});

		} catch (InterruptedIOException ex) {
			showToast("Downloading interupted");
		} catch (IOException e) {
			e.printStackTrace();
			showToast("Error downloading");
		} finally {
			listItem.isDownloading = false;
			if (conn != null)
				conn.close();
		}
	}

	private void asyncDownloadItem(final ContentListItem listItem) {
		new Thread() {
			@Override
			public void run() {
				downloadItem(listItem);
			}
		}.start();
	}

	private void deleteItem(IContentItem item) {
		try {
			remoteContent.deleteContentItem(item, storage);
			showToast("Successfully deleted");
			// Update list view
			setContentList(remoteList);
		} catch (IOException e) {
			e.printStackTrace();
			showToast("Can not delete");
		}
	}

	@Override
	public boolean onMenuItemClick(MenuItem menuItem) {
		if (menuItem == downloadItem || menuItem == updateItem) {
			asyncDownloadItem(currentItem);
		} else if (menuItem == deleteItem) {
			deleteItem(currentItem.item);
		}

		return true;
	}

	@Override
	public void onDismiss(PopupMenu popupMenu) {
		downloadItem = deleteItem = updateItem = null;
		currentItem = null;
	}
}
