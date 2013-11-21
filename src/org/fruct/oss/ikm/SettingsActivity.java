package org.fruct.oss.ikm;

import java.util.Locale;

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
		
	private CheckBoxPreference storeLocationsPref;
	private ListPreference nearestPointsPref;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		storeLocationsPref = (CheckBoxPreference) findPreference(STORE_LOCATION);
		nearestPointsPref = (ListPreference) findPreference(NEAREST_POINTS);
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
		if (key.equals(STORE_LOCATION)) {
			
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
