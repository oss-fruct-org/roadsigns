package org.fruct.oss.ikm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.util.PointList;

import static org.fruct.oss.ikm.Utils.log;

public class DirectionService extends Service {	
	// Extras
	public static final String DIRECTIONS_RESULT = "org.fruct.oss.ikm.GET_DIRECTIONS_RESULT";
	public static final String CENTER = "org.fruct.oss.ikm.CENTER";
	public static final String LOCATION = "org.fruct.oss.ikm.LOCATION";
	public static final String POINTS = "org.fruct.oss.ikm.POINTS";
	
	// Broadcats
	public static final String DIRECTIONS_READY = "org.fruct.oss.ikm.GET_DIRECTIONS_READY";
	public static final String LOCATION_CHANGED = "org.fruct.oss.ikm.LOCATION_CHANGED";
	
	private static final String MOCK_PROVIDER = "mock-provider";
	
	private boolean isInitialized = false;
	
	private GraphHopper hopper;
	private ExecutorService executor = Executors.newFixedThreadPool(1);
	private IBinder binder = new DirectionBinder();
	
	// Point of interest for location tracking
	private List<PointDesc> storedPoints;
	
	private LocationManager locationManager;
	private LocationListener locationListener;
	
	// Last query result
	private ArrayList<Direction> lastResultDirections;
	private GeoPoint lastResultCenter;
	private Location lastResultLocation;
	
	private Location lastLocation;
	
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
	}

	public void fakeLocation(GeoPoint current) {
		if (current == null)
			return;

		if (locationManager != null && locationManager.isProviderEnabled(MOCK_PROVIDER)) {
			GeoPoint last = new GeoPoint(lastLocation);
			float bearing = (float) last.bearingTo(current);
			
			log("fakeLocation last = " + last + ", current = " + current + ", bearing = " + bearing);
			
			Location location = new Location(MOCK_PROVIDER);
			location.setLatitude(current.getLatitudeE6() / 1e6);
			location.setLongitude(current.getLongitudeE6() / 1e6);
			location.setTime(System.currentTimeMillis());
			location.setBearing(bearing); 

			locationManager.setTestProviderLocation(MOCK_PROVIDER, location);
		}
	}
	
	private void getDirections(final Location location, final List<PointDesc> points) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				doGetDirections(location, points);			
			}
		};
		executor.execute(run);
	}
	
	public void startTracking(List<PointDesc> points) {		
		if (locationManager != null) {
			notifyLocationChanged(lastLocation);
			
			if (lastResultDirections != null)
				sendResult(lastResultDirections, lastResultCenter, lastResultLocation);
		
			return;
		}

		storedPoints = points;
		if (storedPoints == null)
			return;
		
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
				lastLocation = location;
				notifyLocationChanged(location);
				getDirections(location, storedPoints); 
			}
		};
		
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 50, locationListener);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 50, locationListener);
		
		try {
			if (!locationManager.isProviderEnabled(MOCK_PROVIDER)) {
				locationManager.addTestProvider(MOCK_PROVIDER, false, false,
						false, false, false, false, false, 0, 5);
				locationManager.setTestProviderEnabled(MOCK_PROVIDER, true);
				locationManager.requestLocationUpdates(MOCK_PROVIDER, 1000, 5,
						locationListener);
			}
		} catch (SecurityException ex) {
			ex.printStackTrace();
		}
	}
	
	private void notifyLocationChanged(Location location) {
		if (location == null)
			return;
		
		Intent intent = new Intent(LOCATION_CHANGED);
		intent.putExtra(LOCATION, (Parcelable) location);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void doGetDirections(Location location, List<PointDesc> points) {
		initialize();
		
		if (location == null || points == null)
			return;
	
		GeoPoint current = new GeoPoint(location);
		
		long last = System.nanoTime();
		
		// Find nearest road node
		Location2IDIndex index = hopper.getLocationIndex();
		AbstractFlagEncoder encoder = hopper.getEncodingManager().getEncoder("CAR");
		DefaultEdgeFilter filter = new DefaultEdgeFilter(encoder);
		
		int nodeId = index.findClosest(current.getLatitudeE6()/1e6,
				current.getLongitudeE6()/1e6, filter).getClosestNode();
		
		double lat = hopper.getGraph().getLatitude(nodeId);
		double lon = hopper.getGraph().getLongitude(nodeId);
		
		GeoPoint nearestNode = new GeoPoint(lat, lon);
		if (current.distanceTo(nearestNode) > 40)
			return;
		
		current = nearestNode;
		
		// Hash table mapping road direction to POI list
		final HashMap<GeoPoint, Direction> directions = new HashMap<GeoPoint, Direction>();
		for (PointDesc point : points) {			
			GHRequest request = new GHRequest(
					current.getLatitudeE6() / 1e6,
					current.getLongitudeE6() / 1e6,
					point.toPoint().getLatitudeE6() / 1e6,
					point.toPoint().getLongitudeE6() / 1e6);
			
			GHResponse response = hopper.route(request);
			
			for (Throwable thr : response.getErrors()) {
				thr.printStackTrace();
			}
			
			PointList path = response.getPoints();
			if (path.getSize() < 2)
				continue;
			
			GeoPoint directionNode = new GeoPoint(path.getLatitude(1), path.getLongitude(1));

			Direction direction = directions.get(directionNode);
			if (direction == null) {
				direction = new Direction(current, directionNode);
				directions.put(directionNode, direction);
			}
			
			direction.addPoint(point);
		}
		
		long curr = System.nanoTime();
		log("" + (curr - last) / 1e9);
		
		lastResultDirections = new ArrayList<Direction>(directions.values());
		lastResultCenter = current;
		lastResultLocation = location;
		
		// Send result
		sendResult(lastResultDirections, lastResultCenter, lastResultLocation);
	}
	
	
	private void sendResult(ArrayList<Direction> directions, GeoPoint center, Location location) {
		Intent intent = new Intent(DIRECTIONS_READY);
		intent.putParcelableArrayListExtra(DIRECTIONS_RESULT, directions);
		intent.putExtra(CENTER, (Parcelable) center);
		intent.putExtra(LOCATION, (Parcelable) location);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void initialize() {
		if (isInitialized)
			return;
		isInitialized = true;
		
		hopper = new GraphHopper().forMobile();
		
		try {
			hopper.setCHShortcuts("shortest");
			boolean res = hopper.load(Environment.getExternalStorageDirectory()
					.getPath() + "/graphhopper/maps/karelia-gh");
			log("GraphHopper loaded " + res);
		} catch (Exception ex) {
			ex.printStackTrace();
			return;
		}
	}
}
