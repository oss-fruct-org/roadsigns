package org.fruct.oss.ikm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import org.fruct.oss.ikm.storage.RemoteContent;

import java.util.ArrayList;
import java.util.List;

public class ContentDialog extends DialogFragment implements DialogInterface.OnClickListener, DialogInterface.OnMultiChoiceClickListener {
	private boolean[] active;
	private String[] strings;

	interface Listener {
		void downloadsSelected(List<Integer> items);
	}

	private Listener listener;

	public ContentDialog() {
	}

	public ContentDialog(List<RemoteContent.StorageItem> storageItems) {
		strings = new String[storageItems.size()];
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

	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putBooleanArray("active", active);
		outState.putStringArray("strings", strings);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			listener = (Listener) activity;
		} catch (ClassCastException ex) {
			throw new ClassCastException(activity.toString() + "must implement " + Listener.class.toString());
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		builder.setPositiveButton("Download", this);
		builder.setNegativeButton(android.R.string.cancel, this);

		builder.setTitle("Downloads");

		if (savedInstanceState != null) {
			strings = savedInstanceState.getStringArray("strings");
			active = savedInstanceState.getBooleanArray("active");
		}

		builder.setMultiChoiceItems(strings, active, this);

		return builder.create();
	}

	@Override
	public void onClick(DialogInterface dialogInterface, int i) {
		if (i == DialogInterface.BUTTON_POSITIVE && listener != null) {
			List<Integer> ret = new ArrayList<Integer>();

			for (int j = 0; j < active.length; j++) {
				if (active[j])
					ret.add(j);
			}

			listener.downloadsSelected(ret);
		}
	}

	@Override
	public void onClick(DialogInterface dialogInterface, int i, boolean b) {
		active[i] = b;
	}
}
