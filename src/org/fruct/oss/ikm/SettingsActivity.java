package org.fruct.oss.ikm;

import java.util.Locale;


import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;

@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	public static final String OFFLINE_MODE = "offline_mode";
	public static final String NEAREST_POINTS = "nearest_points";
		
	private CheckBoxPreference offlineModePref;
	private EditTextPreference nearestPointsPref;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		offlineModePref = (CheckBoxPreference) findPreference(OFFLINE_MODE);
		nearestPointsPref = (EditTextPreference) findPreference(NEAREST_POINTS);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	
		updateNearestPoints(getPreferenceScreen().getSharedPreferences());
	
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		
		super.onPause();
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(OFFLINE_MODE)) {
			
		} else if (key.equals(NEAREST_POINTS)) {
			updateNearestPoints(sharedPreferences);
		}
	}
	
	private void updateNearestPoints(SharedPreferences sharedPreferences) {
		int value = Integer.parseInt(sharedPreferences.getString(NEAREST_POINTS, "0"));
		
		String summary = "";
		if (value <= 0) {
			summary = "Show all points";
		} else {
			summary = "Show " + value + " nearest points";
		}
		
		nearestPointsPref.setSummary(summary);
	}
}
