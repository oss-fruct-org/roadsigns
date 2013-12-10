package org.fruct.oss.ikm;


import static org.fruct.oss.ikm.Utils.log;
import org.fruct.oss.ikm.fragment.MapFragment;
import org.osmdroid.util.GeoPoint;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;

public class MainActivity extends ActionBarActivity {
	// Actions
	public static final String CENTER_MAP = "org.fruct.oss.ikm.CENTER_MAP"; // arg MapFragment.MAP_CENTER
	public static final String SHOW_PATH = "org.fruct.oss.ikm.SHOW_PATH";
	
	public static final String SHOW_PATH_TARGET = "org.fruct.oss.ikm.SHOW_PATH_TARGET";

	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
	
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		log("MainActivity.onNewIntent");
		
		MapFragment mapFragment = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
		
		GeoPoint newCenter = intent.getParcelableExtra(MapFragment.MAP_CENTER);
		if (newCenter != null) {
			mapFragment.setCenter(newCenter);
		}
		
		if (SHOW_PATH.equals(intent.getAction())) {
			GeoPoint target = (GeoPoint) intent.getParcelableExtra(SHOW_PATH_TARGET);
			mapFragment.showPath(target);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}
}
