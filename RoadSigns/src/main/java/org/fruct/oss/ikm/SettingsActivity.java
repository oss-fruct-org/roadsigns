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
	public static final String WARN_PROVIDERS_DISABLED = "warn_providers_disabled";
	public static final String WARN_NETWORK_DISABLED = "warn_network_disabled";
	public static final String WARN_NAVIGATION_DATA_DISABLED = "warn_navigation_data_disabled";

    public static final String STORE_LOCATION = "store_location";
    public static final String NEAREST_POINTS = "nearest_points";
	public static final String SHOW_ACCURACY = "show_accuracy";
	public static final String OFFLINE_MAP = "offline_map";
	public static final String AUTOZOOM = "autozoom";
	public static final String NAVIGATION_DATA = "navigation_data";
	public static final String GETS_ENABLE = "gets_enable";
	public static final String GETS_SERVER = "gets_server";

	public static final String GETS_SERVER_DEFAULT = "http://oss.fruct.org/projects/gets/service";

	private CheckBoxPreference storeLocationsPref;
	private ListPreference nearestPointsPref;

	private OnlineContentPreference offlineMapPref;
	private OnlineContentPreference navigationDataPref;

	private EditTextPreference getsServerPref;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		storeLocationsPref = (CheckBoxPreference) findPreference(STORE_LOCATION);
		nearestPointsPref = (ListPreference) findPreference(NEAREST_POINTS);

		offlineMapPref = (OnlineContentPreference) findPreference(OFFLINE_MAP);
		navigationDataPref = (OnlineContentPreference) findPreference(NAVIGATION_DATA);

		//getsServerPref = (EditTextPreference) findPreference(GETS_SERVER);
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		final SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
		updateNearestPoints(sharedPreferences);

		updateOnlineContentPreference(sharedPreferences, OFFLINE_MAP, offlineMapPref);
		updateOnlineContentPreference(sharedPreferences, NAVIGATION_DATA, navigationDataPref);
		//updateEditBoxPreference(sharedPreferences, GETS_SERVER, getsServerPref);

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
			updateOnlineContentPreference(sharedPreferences, OFFLINE_MAP, offlineMapPref);
		} else if (key.equals(NAVIGATION_DATA)) {
			updateOnlineContentPreference(sharedPreferences, NAVIGATION_DATA, navigationDataPref);
		} else if (key.equals(GETS_SERVER)) {
			updateEditBoxPreference(sharedPreferences, GETS_SERVER, getsServerPref);
		}
	}

	private void updateOnlineContentPreference(SharedPreferences sharedPreferences, String key,
											   OnlineContentPreference pref) {
		String value = sharedPreferences.getString(key, "");
		pref.setSummary(value);
	}

	private void updateEditBoxPreference(SharedPreferences sharedPreferences, String key, EditTextPreference pref) {
		String value = sharedPreferences.getString(key, "");
		if (value == null || value.isEmpty()) {
			pref.setSummary(key.equals(GETS_SERVER) ? GETS_SERVER_DEFAULT : "");
		} else {
			pref.setSummary(value);
		}
	}

	private void updateNearestPoints(SharedPreferences sharedPreferences) {
		int value = Integer.parseInt(sharedPreferences.getString(NEAREST_POINTS, "0"));
		
		String summary;
		if (value <= 0) {
			summary = "Show all points";
		} else {
			summary = "Show " + value + " nearest points";
		}
		
		nearestPointsPref.setSummary(summary);
	}
}
