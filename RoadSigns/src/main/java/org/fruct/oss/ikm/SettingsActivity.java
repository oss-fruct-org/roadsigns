package org.fruct.oss.ikm;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

import org.fruct.oss.ikm.storage.RemoteContentService;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.utils.bind.BindHelper;
import org.fruct.oss.ikm.utils.bind.BindSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.fruct.oss.ikm.utils.Utils.StorageDirDesc;

@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private static final Logger log = LoggerFactory.getLogger(SettingsActivity.class);

	public static final String WARN_PROVIDERS_DISABLED = "warn_providers_disabled";
	public static final String WARN_NETWORK_DISABLED = "warn_network_disabled";
	public static final String WARN_NAVIGATION_DATA_DISABLED = "warn_navigation_data_disabled";

    public static final String STORE_LOCATION = "store_location";
    public static final String NEAREST_POINTS = "nearest_points";
	public static final String SHOW_ACCURACY = "show_accuracy";
	public static final String AUTOZOOM = "autozoom";

	public static final String OFFLINE_MAP = "offline_map";
	public static final String NAVIGATION_DATA = "navigation_data";
	public static final String CURRENT_REGION = "current_region";

	public static final String USE_OFFLINE_MAP = "use_offline_map";
	public static final String AUTOREGION = "autoregion";
	public static final String MAPMATCHING = "mapmatching";

	//public static final String

	public static final String GETS_ENABLE = "gets_enable";
	public static final String GETS_SERVER = "gets_server";
	public static final String GETS_RADIUS = "gets_radius";

	public static final String STORAGE_PATH = "storage_path";

	public static final String GETS_SERVER_DEFAULT = "http://oss.fruct.org/projects/gets/service";
	public static final String VEHICLE = "vehicle";

	private CheckBoxPreference storeLocationsPref;
	private ListPreference nearestPointsPref;
	private ListPreference vehiclePref;
	private EditTextPreference getsServerPref;

	private ListPreference storagePathPref;

	private DataService dataService;

	@BindSetter
	public void remoteContentServiceReady(DataService service) {
		if (service != null) {
			dataService = service;
			dataService.setMigrateListener(migrateListener);
		} else {
			dataService.setMigrateListener(null);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		storeLocationsPref = (CheckBoxPreference) findPreference(STORE_LOCATION);
		nearestPointsPref = (ListPreference) findPreference(NEAREST_POINTS);
		vehiclePref = (ListPreference) findPreference(VEHICLE);

		storagePathPref = (ListPreference) findPreference(STORAGE_PATH);

		//getsServerPref = (EditTextPreference) findPreference(GETS_SERVER);
	}

	@Override
	protected void onStart() {
		super.onStart();
		final SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();

		updateStoragePath(sharedPreferences, storagePathPref);
	}

	@Override
	protected void onResume() {
		super.onResume();

		final SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
		updateNearestPoints(sharedPreferences);
		updateVehicle();

		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		BindHelper.autoBind(this, this);
	}

	@Override
	protected void onPause() {
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

		BindHelper.autoUnbind(this, this);

		super.onPause();
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(STORE_LOCATION)) {
			
		} else if (key.equals(NEAREST_POINTS)) {
			updateNearestPoints(sharedPreferences);
		} else if (key.equals(GETS_SERVER)) {
			updateEditBoxPreference(sharedPreferences, GETS_SERVER, getsServerPref);
		} else if (key.equals(VEHICLE)) {
			updateVehicle();
		}
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

	private void updateVehicle() {
		vehiclePref.setSummary(vehiclePref.getEntry());
	}

	private void updateStoragePath(SharedPreferences sharedPreferences, ListPreference storagePathPref) {
		StorageDirDesc[] storagePaths = Utils.getPrivateStorageDirs(this);

		String[] names = new String[storagePaths.length];
		String[] paths = new String[storagePaths.length];

		String currentValue = sharedPreferences.getString(STORAGE_PATH, null);
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

		if (currentValue != null)
			storagePathPref.setSummary(currentNameRes);
	}

	private DataService.MigrateListener migrateListener = new DataService.MigrateListener() {
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
	};
}
