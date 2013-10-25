package org.fruct.oss.ikm;

import org.fruct.oss.ikm.fragment.MapFragment;
import org.osmdroid.util.GeoPoint;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;

public class MainActivity extends ActionBarActivity {
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
	
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		GeoPoint newCenter = intent.getParcelableExtra(MapFragment.MAP_CENTER);
		if (newCenter != null) {
			MapFragment mapFragment = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
			mapFragment.stopTracking();
			mapFragment.setCenter(newCenter);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}
}
