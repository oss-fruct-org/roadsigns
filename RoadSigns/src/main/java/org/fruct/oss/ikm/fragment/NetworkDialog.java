package org.fruct.oss.ikm.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.view.ContextThemeWrapper;
import android.widget.CheckBox;

import org.fruct.oss.ikm.OnlineContentActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.Utils;

public class NetworkDialog extends DialogFragment implements DialogInterface.OnClickListener{
	private CheckBox checkbox;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(
				new ContextThemeWrapper(getActivity(), Utils.getDialogTheme()));

		builder.setMessage("Network unavailable");
		builder.setPositiveButton("Use offline map", this);
		builder.setNegativeButton("Keep using online map", this);

		checkbox = new CheckBox(new ContextThemeWrapper(getActivity(), Utils.getDialogTheme()));
		checkbox.setText(R.string.warn_providers_disable);
		builder.setView(checkbox);

		return builder.create();
	}

	@Override
	public void onClick(DialogInterface dialogInterface, int i) {
		if (checkbox.isChecked()) {
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
			pref.edit().putBoolean(SettingsActivity.WARN_NETWORK_DISABLED, true).commit();
		}

		if (i == AlertDialog.BUTTON_POSITIVE) {
			Intent intent = new Intent(getActivity(), OnlineContentActivity.class);
			intent.putExtra(OnlineContentActivity.ARG_REMOTE_CONTENT_URL, "https://dl.dropboxusercontent.com/sh/x3qzpqcrqd7ftys/akCI8POpzn/all.xml");
			intent.putExtra(OnlineContentActivity.ARG_LOCAL_STORAGE, "roadsigns-maps");
			intent.putExtra(OnlineContentActivity.ARG_PREF_KEY, SettingsActivity.OFFLINE_MAP);
			getActivity().startActivity(intent);
		}
	}
}
