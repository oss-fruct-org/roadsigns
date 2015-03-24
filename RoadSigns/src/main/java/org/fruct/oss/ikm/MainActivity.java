package org.fruct.oss.ikm;


import static org.fruct.oss.ikm.utils.Utils.log;
import org.fruct.oss.ikm.fragment.MapFragment;
import org.fruct.oss.mapcontent.content.ContentService;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;

public class MainActivity extends ActionBarActivity {
	private static Logger log = LoggerFactory.getLogger(MainActivity.class);

	// Actions
	public static final String CENTER_MAP = "org.fruct.oss.ikm.ACTION_CENTER_MAP"; // arg MapFragment.ARG_MAP_CENTER
	public static final String SHOW_PATH = "org.fruct.oss.ikm.ACTION_SHOW_PATH";
	
	public static final String SHOW_PATH_TARGET = "org.fruct.oss.ikm.SHOW_PATH_TARGET";

	private Thread debugThread;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		startService(new Intent(this, ContentService.class));
	}
}
