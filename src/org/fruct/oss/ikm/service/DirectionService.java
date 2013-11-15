package org.fruct.oss.ikm.service;

import static org.fruct.oss.ikm.Utils.log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.poi.PointsManager.PointsListener;
import org.fruct.oss.ikm.service.DirectionManager.Listener;
import org.osmdroid.util.GeoPoint;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Pair;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.util.PointList;

public class DirectionService extends Service implements PointsListener, Listener {	
	// Extras
	public static final String DIRECTIONS_RESULT = "org.fruct.oss.ikm.GET_DIRECTIONS_RESULT";
	public static final String CENTER = "org.fruct.oss.ikm.CENTER";
	public static final String LOCATION = "org.fruct.oss.ikm.LOCATION";
	public static final String POINTS = "org.fruct.oss.ikm.POINTS";
	
	// Broadcasts
	public static final String DIRECTIONS_READY = "org.fruct.oss.ikm.GET_DIRECTIONS_READY";
	public static final String LOCATION_CHANGED = "org.fruct.oss.ikm.LOCATION_CHANGED";
	
	private static final String MOCK_PROVIDER = "mock-provider";
		
	private DirectionManager dirManager;
	private Routing routing;
	private IBinder binder = new DirectionBinder();
	
	private LocationManager locationManager;
	private LocationListener locationListener;
	
	// Last query result
	private ArrayList<Direction> lastResultDirections;
	private GeoPoint lastResultCenter;
	private Location lastResultLocation;
	
	private Location lastLocation;
	
	
	private boolean disableRealLocation = false;
	
	public class DirectionBinder extends android.os.Binder {
		public DirectionService getService() {
			return DirectionService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	
	@Override
	public void onCreate() {
		super.onCreate();
		log("DirectionService created");
		
		PointsManager.getInstance().addListener(this);
		
		routing = new OneToManyRouting();
		dirManager = new DirectionManager(routing);
		dirManager.setListener(this);
		dirManager.calculateForPoints(PointsManager.getInstance()
				.getFilteredPoints());
	}
	
	@Override
	public void onDestroy() {
		log("DirectionService destroyed");
		if (locationManager != null) {
			locationManager.removeUpdates(locationListener);
			
			if (locationManager.isProviderEnabled(MOCK_PROVIDER)) {
				locationManager.clearTestProviderEnabled(MOCK_PROVIDER);
				locationManager.removeTestProvider(MOCK_PROVIDER);
			}
			
			locationManager = null;
		}
		
		PointsManager.getInstance().removeListener(this);
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public void fakeLocation(GeoPoint current) {
		if (current == null)
			return;

		if (locationManager != null && locationManager.isProviderEnabled(MOCK_PROVIDER)) {
			float bearing;
			
			if (lastLocation != null) {
				GeoPoint last = new GeoPoint(lastLocation);
				bearing = (float) last.bearingTo(current);
				log("fakeLocation last = " + last + ", current = " + current + ", bearing = " + bearing);
			} else {
				bearing = 0;
				log("fakeLocation current = " + current + ", bearing = " + bearing);
			}
			
			Location location = new Location(MOCK_PROVIDER);
			location.setLatitude(current.getLatitudeE6() / 1e6);
			location.setLongitude(current.getLongitudeE6() / 1e6);
			location.setTime(System.currentTimeMillis());
			location.setBearing(bearing); 
			location.setAccuracy(1);
			
			if (Build.VERSION.SDK_INT > 17) {
				location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
			}

			locationManager.setTestProviderLocation(MOCK_PROVIDER, location);
		}
	}
	
	private void tryRegisterLocationProvider(String name, LocationListener listener) {
		try {
			locationManager.requestLocationUpdates(name, 5000, 50, locationListener);
		} catch (Exception ex) {
		}
	}
	
	public void startTracking() {
		if (locationManager != null) {
			notifyLocationChanged(lastLocation);
			
			if (lastResultDirections != null)
				sendResult(lastResultDirections, lastResultCenter, lastResultLocation);
		
			return;
		}
		
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationListener = new LocationListener() {
			@Override
			public void onStatusChanged(String provider, int status, Bundle extras) {
			}
			
			@Override
			public void onProviderEnabled(String provider) {
			}
			
			@Override
			public void onProviderDisabled(String provider) {
			}
			
			@Override
			public void onLocationChanged(Location location) {
				log("DirectionService.onLocationChanged " + location);
				lastLocation = location;
				notifyLocationChanged(location);
								
				//List<PointDesc> points = PointsManager.getInstance().getFilteredPoints();
				dirManager.updateLocation(lastLocation);
			}
		};
		
		if (!disableRealLocation) {
			tryRegisterLocationProvider(LocationManager.NETWORK_PROVIDER, locationListener);
			tryRegisterLocationProvider(LocationManager.GPS_PROVIDER, locationListener);
		}
		
		try {
			locationManager.addTestProvider(MOCK_PROVIDER, false, false,
						false, false, false, false, true, 0, 5);
				
			locationManager.setTestProviderEnabled(MOCK_PROVIDER, true);
			locationManager.requestLocationUpdates(MOCK_PROVIDER, 1000, 5,
						locationListener);
		} catch (SecurityException ex) {
			ex.printStackTrace();
		} catch (IllegalArgumentException ex) {
			ex.printStackTrace();
		}
	}
	
	/**
	 * Disable network and gps location provider for testing purposes.
	 */
	public void disableRealLocation() {
		disableRealLocation = true;
	}
	
	private void notifyLocationChanged(Location location) {
		if (location == null)
			return;
		
		Intent intent = new Intent(LOCATION_CHANGED);
		intent.putExtra(LOCATION, (Parcelable) location);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
		
	public PointList findPath(GeoPoint from, GeoPoint to) {
		return routing.findPath(from, to);
	}
	
	public Location getLastLocation() {
		return lastLocation;
	}
	
	@Override
	public void filterStateChanged(List<PointDesc> newList,
			List<PointDesc> added, List<PointDesc> removed) {
		Utils.log("Added " + added);
		Utils.log("Removed " + removed);

		dirManager.calculateForPoints(newList);
	}
	
	@Override
	public void directionsUpdated(List<Direction> directions, GeoPoint center) {
		this.lastResultDirections = new ArrayList<Direction>(directions);
		this.lastResultCenter = center;
		
		sendResult(lastResultDirections, lastResultCenter, lastLocation);
	}
	
	private void sendResult(ArrayList<Direction> directions, GeoPoint center, Location location) {
		Intent intent = new Intent(DIRECTIONS_READY);
		intent.putParcelableArrayListExtra(DIRECTIONS_RESULT, directions);
		intent.putExtra(CENTER, (Parcelable) center);
		intent.putExtra(LOCATION, (Parcelable) location);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
}
