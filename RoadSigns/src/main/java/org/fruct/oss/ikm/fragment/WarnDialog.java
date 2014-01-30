package org.fruct.oss.ikm.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.view.ContextThemeWrapper;
import android.widget.CheckBox;

import org.fruct.oss.ikm.Utils;

public class WarnDialog extends DialogFragment implements DialogInterface.OnClickListener {
	private CheckBox checkbox;

	private int messageId;
	private int configureId;
	private int disableId;
	private String disablePref;

	public WarnDialog(int messageId, int configureId, int disableId, String disablePref) {
		this.messageId = messageId;
		this.configureId = configureId;
		this.disableId = disableId;
		this.disablePref = disablePref;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(
				new ContextThemeWrapper(getActivity(), Utils.getDialogTheme()));

		builder.setMessage(messageId);
		builder.setPositiveButton(configureId, this);
		builder.setNegativeButton(android.R.string.cancel, this);

		checkbox = new CheckBox(new ContextThemeWrapper(getActivity(), Utils.getDialogTheme()));
		checkbox.setText(disableId);
		builder.setView(checkbox);

		return builder.create();
	}


	@Override
	public void onClick(DialogInterface dialogInterface, int which) {
		if (checkbox.isChecked()) {
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
			pref.edit().putBoolean(disablePref, true).commit();
		}

		if (which == AlertDialog.BUTTON_POSITIVE) {
			onAccept();
		}
	}

	protected void onAccept() {
	}
}
