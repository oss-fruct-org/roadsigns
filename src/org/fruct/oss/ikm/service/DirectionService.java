package org.fruct.oss.ikm.service;

import static org.fruct.oss.ikm.Utils.log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
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

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.util.PointList;

public class DirectionService extends Service {	
	// Extras
	public static final String DIRECTIONS_RESULT = "org.fruct.oss.ikm.GET_DIRECTIONS_RESULT";
	public static final String CENTER = "org.fruct.oss.ikm.CENTER";
	public static final String LOCATION = "org.fruct.oss.ikm.LOCATION";
	public static final String POINTS = "org.fruct.oss.ikm.POINTS";
	
	// Broadcasts
	public static final String DIRECTIONS_READY = "org.fruct.oss.ikm.GET_DIRECTIONS_READY";
	public static final String LOCATION_CHANGED = "org.fruct.oss.ikm.LOCATION_CHANGED";
	
	private static final String MOCK_PROVIDER = "mock-provider";
	
	private boolean isInitialized = false;
	
	private GraphHopper hopper;
	private ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
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
	
	private void getDirections(final Location location) {
		log("DirectionService.getDirections");
		Runnable run = new Runnable() {
			@Override
			public void run() {
				doGetDirections(location);			
			}
		};
		executor.execute(run);
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
				getDirections(location); 
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
	 * Load road graph from stream
	 * @param input stream containing graphhopper's zip archive
	 * @param name identifier 
	 * @return true if success, false otherwise
	 */
	public boolean initializeFrom(InputStream input, String name) {
		log("DirectionService.initializeFrom ENTER");
		hopper = new GraphHopper().forMobile();
		
		try {
			hopper.setCHShortcuts("shortest");
			String filename = Utils.copyToInternalStorage(this, input, "graphhopper", name + ".ghz.ghz");
			filename = filename.substring(0, filename.length() - 4); // Cut last ".ghz"
			boolean res = hopper.load(filename);
			log("GraphHopper loaded " + res);
			isInitialized = true;

			return res;
		} catch (Exception ex) {
			ex.printStackTrace();
			return false;
		} finally {
			log("DirectionService.initializeFrom EXIT");
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
	
	private GeoPoint getNearestRoadNode(GeoPoint current) {
		Location2IDIndex index = hopper.getLocationIndex();
		AbstractFlagEncoder encoder = hopper.getEncodingManager().getEncoder("CAR");
		DefaultEdgeFilter filter = new DefaultEdgeFilter(encoder);
		
		int nodeId = index.findClosest(current.getLatitudeE6()/1e6,
				current.getLongitudeE6()/1e6, filter).getClosestNode();
		
		double lat = hopper.getGraph().getLatitude(nodeId);
		double lon = hopper.getGraph().getLongitude(nodeId);
		
		GeoPoint nearestNode = new GeoPoint(lat, lon);		
		return nearestNode;
	}
	
	public PointList findPath(GeoPoint from, GeoPoint to) {
		initialize();
		
		GHRequest request = new GHRequest(
				from.getLatitudeE6() / 1e6,
				from.getLongitudeE6() / 1e6,
				to.getLatitudeE6() / 1e6,
				to.getLongitudeE6() / 1e6);
		
		GHResponse response = hopper.route(request);
		
		for (Throwable thr : response.getErrors()) {
			thr.printStackTrace();
		}
		
		PointList path = response.getPoints();
		return path;
	}
	
	public Location getLastLocation() {
		return lastLocation;
	}

	private void doGetDirections(Location location) {
		initialize();
		log("doGetDirections " + location);
		
		if (location == null)
			return;
	
		GeoPoint current = new GeoPoint(location);
		
		long last = System.nanoTime();
		
		// Find nearest road node
		GeoPoint nearestNode = getNearestRoadNode(current);
		if (current.distanceTo(nearestNode) > 40)
			return;
		current = nearestNode;
		
		List<PointDesc> points = PointsManager.getInstance().getFilteredPoints();
		
		// Hash table mapping road direction to POI list
		final HashMap<GeoPoint, Direction> directions = new HashMap<GeoPoint, Direction>();
		for (PointDesc point : points) {	
			
			PointList path = findPath(current, point.toPoint());
			
			if (path.getSize() < 2)
				continue;
			
			//GeoPoint directionNode = new GeoPoint(path.getLatitude(1), path.getLongitude(1));
			
			Pair<GeoPoint, GeoPoint> directionPair = getDirectionNode(current, path);
			
			GeoPoint node1 = directionPair.first;
			GeoPoint node2 = directionPair.second;
			
			Direction direction = directions.get(node2);
			if (direction == null) {
				direction = new Direction(node1, node2);
				directions.put(node2, direction);
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
		log("doGetDirections EXIT");
	}
	
	private Pair<GeoPoint, GeoPoint> getDirectionNode(GeoPoint current, PointList path) {
		if (false) {
			return Pair.create(current, new GeoPoint((int) (path.getLatitude(1) * 1e6),
					(int) (path.getLongitude(1) * 1e6)));
			
		}
		
		final int radius = 150;
		final float bearingLimit = 10;
		
		GeoPoint point = new GeoPoint(0, 0);
		
		float prevBearing = 0;
		GeoPoint prev = Utils.copyGeoPoint(current);
		
		for (int i = 1; i < path.getSize(); i++) {
			point.setCoordsE6((int) (path.getLatitude(i) * 1e6), (int) (path.getLongitude(i) * 1e6));
			
			int dist = current.distanceTo(point);
			if (dist > radius) {
				return Pair.create(current, point);
			}
			
			// Calculate accumulated bearing change
			/*float bearingChange = Utils.normalizeAngle((float) prev.bearingTo(point) - prevBearing);
			if (Math.abs(bearingChange) > bearingLimit) {
				prevBearing = (float) prev.bearingTo(point);
				prev = Utils.copyGeoPoint(point);
			}*/
			prev = Utils.copyGeoPoint(point);
		}
		
		return Pair.create(current, prev);
	}
	
	private void sendResult(ArrayList<Direction> directions, GeoPoint center, Location location) {
		Intent intent = new Intent(DIRECTIONS_READY);
		intent.putParcelableArrayListExtra(DIRECTIONS_RESULT, directions);
		intent.putExtra(CENTER, (Parcelable) center);
		intent.putExtra(LOCATION, (Parcelable) location);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void initialize() {
		log("DirectionService.initialize ENTER " + isInitialized);
		if (isInitialized)
			return;
		
		try {
			InputStream input = this.getAssets().open("karelia.ghz.ghz");
			initializeFrom(input, "karelia");
			input.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (Throwable th) {
			th.printStackTrace();
		}
		
		log("DirectionService.initialize EXIT");
	}
}
