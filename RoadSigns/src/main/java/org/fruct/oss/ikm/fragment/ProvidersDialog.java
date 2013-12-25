package org.fruct.oss.ikm.fragment;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.SettingsActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.view.ContextThemeWrapper;
import android.widget.CheckBox;

public class ProvidersDialog extends DialogFragment implements OnClickListener {
	private CheckBox checkbox;

	private int getDialogTheme() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			return android.R.style.Theme_Dialog;
		} else {
			return android.R.style.Theme_Holo_Light_Dialog;
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(
				new ContextThemeWrapper(getActivity(), getDialogTheme()));
		
		builder.setMessage(R.string.warn_no_providers);
		builder.setPositiveButton(R.string.configure_providers, this);
		builder.setNegativeButton(android.R.string.cancel, this);
		
		// TODO: get checkbox style from current theme
		checkbox = new CheckBox(new ContextThemeWrapper(getActivity(), getDialogTheme()));
		checkbox.setText(R.string.warn_providers_disable);
		builder.setView(checkbox);
		
		return builder.create();
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
			startActivity(intent);
		}
		
		if (checkbox.isChecked()) {
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
			pref.edit().putBoolean(SettingsActivity.WARN_PROVIDERS_DISABLED, true).commit();
		}
	}
}
