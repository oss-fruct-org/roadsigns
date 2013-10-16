package org.fruct.oss.ikm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.util.PointList;

public class DirectionService extends Service {
	public static final String GET_DIRECTIONS_READY = "org.fruct.oss.ikm.GET_DIRECTIONS_READY";
	public static final String GET_DIRECTIONS_RESULT = "org.fruct.oss.ikm.GET_DIRECTIONS_RESULT";
	
	public static final String GET_DIRECTIONS = "org.fruct.oss.ikm.GET_DIRECTIONS";
	public static final String CENTER = "org.fruct.oss.ikm.CENTER";
	public static final String POINTS = "org.fruct.oss.ikm.POINTS";
	
	private boolean isInitialized = false;
	
	private GraphHopper hopper;
	private List<Intent> tasks = Collections.synchronizedList(new ArrayList<Intent>());
	private volatile Thread workingThread;
	
	@Override
	public synchronized int onStartCommand(final Intent intent, int flags, int startId) {
		if (!intent.getAction().equals(GET_DIRECTIONS))
			return START_STICKY;

		tasks.add(0, intent);

		if (workingThread == null) {
			workingThread = new Thread() {
				@Override
				public void run() {
					while (!tasks.isEmpty()) {
						Intent task = tasks.remove(tasks.size() - 1);
						processGetDirections(task.getExtras());
					}
					
					synchronized (DirectionService.this) {
						workingThread = null;
					}
				}
			};
			workingThread.start();
		}

		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	private void processGetDirections(Bundle extras) {
		initialize();
		GeoPoint current = (GeoPoint) extras.getParcelable(CENTER);
		ArrayList<Parcelable> points = extras.getParcelableArrayList(POINTS);
		
		if (current == null || points == null)
			return;
		
		long last = System.nanoTime();
		
		// Find nearest node
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
		
		final HashMap<GeoPoint, ArrayList<PointDesc>> directions = new HashMap<GeoPoint, ArrayList<PointDesc>>();
		for (Parcelable ppoint : points) {			
			PointDesc point = (PointDesc) ppoint;
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
			
			GeoPoint directionNode = new GeoPoint(path.getLatitude(1), path.getLongitude(1));
						
			ArrayList<PointDesc> pointsInDirection = directions.get(directionNode);
			if (pointsInDirection == null) {
				pointsInDirection = new ArrayList<PointDesc>();
				directions.put(directionNode, pointsInDirection);
			}
			
			pointsInDirection.add(point);
		}
		
		long curr = System.nanoTime();
		Log.d("qwe", "" + (curr - last) / 1e9);
		
		Intent intent = new Intent(GET_DIRECTIONS_READY);
		intent.putExtra(GET_DIRECTIONS_RESULT, directions);
		intent.putExtra(CENTER, (Parcelable) current);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void initialize() {
		if (isInitialized)
			return;
		isInitialized = true;
		
		hopper = new GraphHopper().forMobile();
		
		try {
			hopper.setCHShortcuts("shortest");
			boolean res = hopper.load(Environment.getExternalStorageDirectory().getPath()  + "/graphhopper/maps/karelia-gh");
			Log.d("qwe", "GraphHopper loaded " + res);
		} catch (Exception ex) {
			ex.printStackTrace();
			return;
		}
	}
}
