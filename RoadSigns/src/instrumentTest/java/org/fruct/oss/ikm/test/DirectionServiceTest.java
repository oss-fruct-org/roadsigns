package org.fruct.oss.ikm.test;

import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;

import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.DirectionService;
import org.osmdroid.util.GeoPoint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.v4.content.LocalBroadcastManager;
import android.test.ServiceTestCase;
import android.util.Log;

public class DirectionServiceTest extends ServiceTestCase<DirectionService> {
	private DirectionService serv;
	private Context testContext;
	private BroadcastReceiver receiver;
	

	@Override
	protected void setUp() throws Exception {
		
		super.setUp();

		PointsManager.resetInstance();
		PointsManager manager = PointsManager.getInstance();
		manager.addPointLoader(new TestPointsLoader());
		
		if (testContext == null) {
			testContext = getContext().createPackageContext(
					"org.fruct.oss.ikm.test", Context.CONTEXT_IGNORE_SECURITY);
		}
		
		serv = ((DirectionService.DirectionBinder) bindService(new Intent()))
				.getService();
		serv.disableRealLocation();
		
		InputStream input = testContext.getAssets().open("ptz.ghz");
		assertNotNull(input);
		
		//boolean result = serv.initializeFrom(input, "ptz");
		input.close();
		//assertTrue("Can't load road graph", result);
		
		
		LocalBroadcastManager.getInstance(getContext()).registerReceiver(receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				GeoPoint geoPoint = intent.getParcelableExtra(DirectionService.CENTER);
				ArrayList<Direction> directions = intent.getParcelableArrayListExtra(DirectionService.DIRECTIONS_RESULT);
			
				Log.d("roadsigns", "RECEIVED");
			}
		}, new IntentFilter(DirectionService.DIRECTIONS_READY));
	}

	@Override
	protected void tearDown() throws Exception {
		PointsManager.resetInstance();
		LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(receiver);
		
		super.tearDown();
	}

	public DirectionServiceTest() {
		super(DirectionService.class);
	}

	public void testServiceCreation() {
		assertNotNull(serv);
	}

	public void testGraphLoading() throws Exception {
		Log.d("roadsigns", "Test thread " + Thread.currentThread().getName());
		serv.startTracking();
		serv.fakeLocation(new GeoPoint(61.786548,34.351721));
		
		//getContext().
		
		//Thread.sleep(5000);
	}
}
