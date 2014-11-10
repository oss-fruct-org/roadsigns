package org.fruct.oss.ikm;


import static org.fruct.oss.ikm.utils.Utils.log;
import org.fruct.oss.ikm.fragment.MapFragment;
import org.fruct.oss.mapcontent.content.RemoteContentService;
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
	public static final String CENTER_MAP = "org.fruct.oss.ikm.CENTER_MAP"; // arg MapFragment.MAP_CENTER
	public static final String SHOW_PATH = "org.fruct.oss.ikm.SHOW_PATH";
	
	public static final String SHOW_PATH_TARGET = "org.fruct.oss.ikm.SHOW_PATH_TARGET";

	private Thread debugThread;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/*
		final ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
		final ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

		debugThread = new Thread() {
			@Override
			public void run() {
				while (!Thread.interrupted()) {
					manager.getMemoryInfo(memInfo);
					log.trace("Available memory: {} kb", memInfo.availMem / 1024);

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		};*/
		//debugThread.start();

		startService(new Intent(this, RemoteContentService.class));
		startService(new Intent(this, DataService.class));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		//debugThread.interrupt();
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
