package org.fruct.oss.ikm;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.fruct.oss.ikm.storage.FileStorage;
import org.fruct.oss.ikm.storage.IContentItem;
import org.fruct.oss.ikm.storage.NetworkProvider;
import org.fruct.oss.ikm.storage.RemoteContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ContentListItem {
	IContentItem item;
	RemoteContent.LocalContentState state;
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

		tag.text1.setText(item.item.getName());

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

		return view;
	}
}

public class OnlineContentActivity extends Activity implements AdapterView.OnItemClickListener {
	private static Logger log = LoggerFactory.getLogger(OnlineContentActivity.class);

	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private NetworkProvider provider;
	private FileStorage storage;
	private RemoteContent remoteContent;
	private ListView listView;
	private ContentAdapter adapter;

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
					final List<IContentItem> list = remoteContent.getContentList(provider,
							"https://dl.dropboxusercontent.com/sh/x3qzpqcrqd7ftys/qNDPelAPa_/content.xml");
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							log.debug("Received list size {}", list.size());
							setContentList(list);
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

		PopupMenu menu = new PopupMenu(this, view);
		switch (listItem.state) {
		case NEEDS_UPDATE:
			menu.getMenu().add("Update");
			menu.getMenu().add("Delete");
			break;
		case NOT_EXISTS:
			menu.getMenu().add("Download");
			break;
		case UP_TO_DATE:
			menu.getMenu().add("Delete");
			break;
		}

		menu.show();
	}
}
