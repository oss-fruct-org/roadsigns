package org.fruct.oss.ikm;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;

import org.fruct.oss.mapcontent.content.ContentService;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnection;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnectionListener;
import org.fruct.oss.mapcontent.content.utils.DirUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener, ContentServiceConnectionListener {
	private static final Logger log = LoggerFactory.getLogger(SettingsActivity.class);

	public static final String WARN_PROVIDERS_DISABLED = "warn_providers_disabled";
	public static final String WARN_NETWORK_DISABLED = "warn_network_disabled";
	public static final String WARN_NAVIGATION_DATA_DISABLED = "warn_navigation_data_disabled";
	public static final String WARN_UPDATE_READY = "warn_update_ready";

    public static final String START_TRACKING_MODE = "start_tracking_mode";
    public static final String NEAREST_POINTS = "nearest_points";
	public static final String SHOW_ACCURACY = "show_accuracy";
	public static final String AUTOZOOM = "autozoom";

	public static final String USE_OFFLINE_MAP = "use_offline_map2";
	public static final String AUTOREGION = "autoregion2";

	public static final String MAPMATCHING = "mapmatching";

	//public static final String

	public static final String GETS_SERVER = "gets_server";
	public static final String GETS_RADIUS = "gets_radius";

	public static final String VEHICLE = "vehicle";

	public static final String GETS_SERVER_DEFAULT = "http://gets.cs.petrsu.ru/gets/service/";
	public static final int GETS_RADIUS_DEFAULT = 200000;

	private SharedPreferences pref;

	private CheckBoxPreference storeLocationsPref;
	private ListPreference nearestPointsPref;
	private ListPreference vehiclePref;
	private EditTextPreference getsServerPref;

	private ListPreference storagePathPref;
	private ListPreference getsRadius;

	private ContentServiceConnection contentServiceConnection = new ContentServiceConnection(this);

	@Override
	public void onContentServiceReady(ContentService contentService) {

	}

	@Override
	public void onContentServiceDisconnected() {

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences_rs);
		addPreferencesFromResource(org.fruct.oss.mapcontent.R.xml.preferences);

		storeLocationsPref = (CheckBoxPreference) findPreference(START_TRACKING_MODE);
		nearestPointsPref = (ListPreference) findPreference(NEAREST_POINTS);
		vehiclePref = (ListPreference) findPreference(VEHICLE);

		storagePathPref = (ListPreference) findPreference(org.fruct.oss.mapcontent.content.Settings.PREF_STORAGE_PATH);
		getsRadius = (ListPreference) findPreference(GETS_RADIUS);

		Preference offlineMapPreference = findPreference(USE_OFFLINE_MAP);
		Preference autoRegionPreference = findPreference(AUTOREGION);

		getPreferenceScreen().removePreference(offlineMapPreference);
		getPreferenceScreen().removePreference(autoRegionPreference);

		getsServerPref = (EditTextPreference) findPreference(GETS_SERVER);

		pref = PreferenceManager.getDefaultSharedPreferences(this);
	}

	@Override
	protected void onStart() {
		super.onStart();
		final SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();

		updateStoragePath(sharedPreferences, storagePathPref);
		updateGetsServerSummary(sharedPreferences);
		setupResetGetsServerPreference();
	}

	@Override
	protected void onResume() {
		super.onResume();

		final SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
		updateRadius(sharedPreferences);
		updateNearestPoints(sharedPreferences);
		updateVehicle();

		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		contentServiceConnection.bindService(this);
	}

	@Override
	protected void onPause() {
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

		contentServiceConnection.unbindService(this);

		super.onPause();
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		switch (key) {
		case NEAREST_POINTS:
			updateNearestPoints(sharedPreferences);
			break;
		case GETS_SERVER:
			updateGetsServerSummary(sharedPreferences);
			break;
		case VEHICLE:
			updateVehicle();
			break;
		case GETS_RADIUS:
			updateRadius(sharedPreferences);
			break;
		}
	}

	private void updateGetsServerSummary(SharedPreferences sharedPreferences) {
		String value = sharedPreferences.getString(GETS_SERVER, GETS_SERVER_DEFAULT);
		if (value == null || value.isEmpty()) {
			getsServerPref.setSummary(GETS_SERVER_DEFAULT);
		} else {
			getsServerPref.setSummary(value);
		}
	}

	private void setupResetGetsServerPreference() {
		Preference resetServerButton = findPreference("button_reset_gets_server");

		String currentValue = pref.getString(GETS_SERVER, GETS_SERVER_DEFAULT);
		if (GETS_SERVER_DEFAULT.equals(currentValue)) {
			PreferenceCategory getsCategory = (PreferenceCategory) findPreference("gets_preferences");
			getsCategory.removePreference(resetServerButton);
		} else {
			resetServerButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					pref.edit().putString(GETS_SERVER, GETS_SERVER_DEFAULT).apply();
					return true;
				}
			});

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

	private void updateVehicle() {
		vehiclePref.setSummary(vehiclePref.getEntry());
	}

	private void updateStoragePath(SharedPreferences pref, ListPreference storagePathPref) {
		DirUtil.StorageDirDesc[] storagePaths = DirUtil.getPrivateStorageDirs(this);

		String[] names = new String[storagePaths.length];
		String[] paths = new String[storagePaths.length];

		String currentValue = pref.getString(org.fruct.oss.mapcontent.content.Settings.PREF_STORAGE_PATH, null);
		int currentNameRes = -1;

		for (int i = 0; i < storagePaths.length; i++) {
			names[i] = getString(storagePaths[i].nameRes);
			paths[i] = storagePaths[i].path;

			if (paths[i].equals(currentValue)) {
				currentNameRes = storagePaths[i].nameRes;
			}
		}

		storagePathPref.setEntryValues(paths);
		storagePathPref.setEntries(names);

		if (currentValue != null && currentNameRes != -1)
			storagePathPref.setSummary(currentNameRes);
	}

	private void updateRadius(SharedPreferences pref) {
		String value = pref.getString(GETS_RADIUS, String.valueOf(GETS_RADIUS_DEFAULT));
		int index = getsRadius.findIndexOfValue(value);
		if (index >= 0) {
			getsRadius.setSummary(getString(R.string.pref_radius) + " " + getsRadius.getEntries()[index]);
		}
	}

	/*private DataService.MigrateListener migrateListener = new DataService.MigrateListener() {
		private ProgressDialog dialog;

		@Override
		public void migrateFile(String name, int n, int max) {
			if (dialog == null) {
				dialog = ProgressDialog.show(SettingsActivity.this, "Copying", "Copying...", false, false);
			}

			dialog.setMax(max);
			dialog.setProgress(n);
			dialog.setMessage("Copying " + name);
		}

		@Override
		public void migrateFinished() {
			if (dialog != null) {
				dialog.dismiss();
				dialog = null;
			}

			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
			updateStoragePath(pref, storagePathPref);

			Toast.makeText(SettingsActivity.this, "Local content successfully moved", Toast.LENGTH_LONG).show();
		}

		@Override
		public void migrateError() {
			if (dialog != null) {
				dialog.dismiss();
				dialog = null;
			}

			Toast.makeText(SettingsActivity.this, "Cannot move local content", Toast.LENGTH_LONG).show();
		}
	};*/
}
