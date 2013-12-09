package org.fruct.oss.ikm;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;

public class AccountPreference extends DialogPreference {
	public AccountPreference(Context context, AttributeSet attrs) {
		super(context, attrs);

		setDialogLayoutResource(R.layout.account_preference);
		setPositiveButtonText(android.R.string.ok);
		setNegativeButtonText(android.R.string.cancel);

		setDialogIcon(null);
	}
}
