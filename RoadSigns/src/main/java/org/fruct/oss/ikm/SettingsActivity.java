package org.fruct.oss.ikm;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	public static final String STORE_LOCATION = "store_location";
	public static final String WARN_PROVIDERS_DISABLED = "warn_providers_disabled";
	public static final String NEAREST_POINTS = "nearest_points";
	public static final String SHOW_ACCURACY = "show_accuracy";
	public static final String OFFLINE_MAP = "offline_map";
	public static final String AUTOZOOM = "autozoom";
	public static final String NAVIGATION_DATA = "navigation_data";

	private CheckBoxPreference storeLocationsPref;
	private ListPreference nearestPointsPref;
	private EditTextPreference offlineMapPref;
	private EditTextPreference navigationDataPref;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		storeLocationsPref = (CheckBoxPreference) findPreference(STORE_LOCATION);
		nearestPointsPref = (ListPreference) findPreference(NEAREST_POINTS);
		offlineMapPref = (EditTextPreference) findPreference(OFFLINE_MAP);
		navigationDataPref = (EditTextPreference) findPreference(NAVIGATION_DATA);
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		final SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
		updateNearestPoints(sharedPreferences);
		updateEditBoxPreference(sharedPreferences, OFFLINE_MAP, offlineMapPref);
		updateEditBoxPreference(sharedPreferences, NAVIGATION_DATA, navigationDataPref);

		sharedPreferences.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		
		super.onPause();
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(STORE_LOCATION)) {
			
		} else if (key.equals(NEAREST_POINTS)) {
			updateNearestPoints(sharedPreferences);
		} else if (key.equals(OFFLINE_MAP)) {
			updateEditBoxPreference(sharedPreferences, OFFLINE_MAP, offlineMapPref);
		} else if (key.equals(NAVIGATION_DATA)) {
			updateEditBoxPreference(sharedPreferences, NAVIGATION_DATA, navigationDataPref);
		}
	}

	private void updateEditBoxPreference(SharedPreferences sharedPreferences, String key, EditTextPreference pref) {
		String value = sharedPreferences.getString(key, "");
		if (value == null || value.isEmpty()) {
			pref.setSummary(android.R.string.no);
		} else {
			pref.setSummary(value);
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
