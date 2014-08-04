package org.fruct.oss.ikm;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.util.Pair;

import org.fruct.oss.ikm.fragment.MapFragment;
import org.fruct.oss.ikm.storage.RemoteContent;
import org.fruct.oss.ikm.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class OnlineContentPreference extends ListPreference implements RemoteContent.Listener {
	private static Logger log = LoggerFactory.getLogger(OnlineContentPreference.class);
	private final String contentType;
	private final RemoteContent remoteContent;

	public OnlineContentPreference(Context context, AttributeSet attrs) {
		super(context, attrs);

		setEntries(new CharSequence[]{});
		setEntryValues(new CharSequence[]{});
		//setPositiveButtonText(android.R.string.ok);
		setNegativeButtonText(android.R.string.cancel);

		contentType = attrs.getAttributeValue(null, "contentType");
		remoteContent = RemoteContent.getInstance(MapFragment.REMOTE_CONTENT_URLS, "roadsigns-maps");
		remoteContent.addListener(this);
		remoteContent.startInitialize(false);
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		super.onDismiss(dialog);
		remoteContent.removeListener(this);
	}

	public void setData(List<RemoteContent.StorageItem> list) {
		Pair<List<String>, List<String>> entries = Utils.map2(list, new Utils.Function2<String, String, RemoteContent.StorageItem>() {
			@Override
			public Pair<String, String> apply(RemoteContent.StorageItem storageItem) {
				if (!storageItem.getItem().getType().equals(contentType)
						|| storageItem.getState() == RemoteContent.LocalContentState.NOT_EXISTS)
					return null;

				String entryName = storageItem.getItem().getDescription();
				String entryValue = remoteContent.getPath(storageItem.getItem());
				return Pair.create(entryName, entryValue);
			}
		});

		entries.first.add(getContext().getString(android.R.string.no));
		entries.second.add("");

		setEntries(entries.first.toArray(new String[1]));
		setEntryValues(entries.second.toArray(new String[1]));
	}

	@Override
	public void listReady(final List<RemoteContent.StorageItem> list) {
		Context ctx = getContext();
		if (ctx instanceof Activity) {
			((Activity) ctx).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					setData(list);
				}
			});
		} else {
			setData(list);
		}
	}

	@Override
	public void downloadStateUpdated(RemoteContent.StorageItem item, int downloaded, int max) {
	}

	@Override
	public void downloadFinished(RemoteContent.StorageItem item) {
	}

	@Override
	public void errorDownloading(RemoteContent.StorageItem item, IOException e) {
	}

	@Override
	public void errorInitializing(IOException e) {
	}

	@Override
	public void downloadInterrupted(RemoteContent.StorageItem sItem) {
	}
}
