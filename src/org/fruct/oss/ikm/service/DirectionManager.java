package org.fruct.oss.ikm.service;

import static org.fruct.oss.ikm.Utils.log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;

import android.location.Location;
import android.util.Pair;

import com.graphhopper.util.DistanceCalc3D;
import com.graphhopper.util.PointList;

public class DirectionManager {
	public interface Listener {
		void directionsUpdated(List<Direction> directions, GeoPoint center);
	}
	
	private Listener listener;
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}

	private Routing routing;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private GeoPoint userPosition;
	private Location location;
	private ArrayList<Direction> lastResultDirections;
	
	//private HashMap<GeoPoint, Direction> readyDirections = new HashMap<GeoPoint, Direction>();
	
	// POI, for that directions ready
	private Map<PointDesc, Pair<GeoPoint, GeoPoint>> readyPoints = new HashMap<PointDesc, Pair<GeoPoint, GeoPoint>>();
	
	// POI, that pass filters
	private Set<PointDesc> activePoints = new HashSet<PointDesc>();
	
	public DirectionManager(Routing routing) {
		this.routing = routing;
	}
	
	public void calculateForPoints(final List<PointDesc> points) {		
		executor.execute(new Runnable() {
			@Override
			public void run() {
				activePoints = new HashSet<PointDesc>(points);
				
				doCalculateForPoints();
			}
		});
	}
	
	public void updateLocation(final Location location) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				DirectionManager.this.location = location;
				GeoPoint current = new GeoPoint(location);
				
				// Find nearest road node
				GeoPoint nearestNode = routing.getNearestRoadNode(current);
				if (current.distanceTo(nearestNode) > 40)
					return;
				current = nearestNode;
				
				DirectionManager.this.userPosition = nearestNode;
				routing.reset(userPosition);
				
				readyPoints.clear();
				doCalculateForPoints();
			}
		});
	}
	
	private void doCalculateForPoints() {
		if (activePoints == null || userPosition == null)
			return;
		
		long last = System.nanoTime();
		
		// Hash table mapping road direction to POI list
		for (PointDesc point : activePoints) {	
			log("Processing POI");
			
			// If direction for this point already calculated, skip it
			if (readyPoints.containsKey(point)) {
				log("From cache");
				continue;
			}

			PointList path = routing.route(point.toPoint());
			if (path == null)
				continue;
			
			if (path.getSize() < 2)
				continue;
			
			//GeoPoint directionNode = new GeoPoint(path.getLatitude(1), path.getLongitude(1));
			
			Pair<GeoPoint, GeoPoint> directionPair = getDirectionNode(userPosition, path);
			readyPoints.put(point, directionPair);
			
			int dist = (int) path.calculateDistance(new DistanceCalc3D());
			point.setDistance(dist);
		}
		
		long curr = System.nanoTime();
		log("Routing time " + (curr - last) / 1e9);
		
		HashMap<GeoPoint, Direction> directions = new HashMap<GeoPoint, Direction>();
		for (PointDesc point : activePoints) {
			Pair<GeoPoint, GeoPoint> dirPair = readyPoints.get(point);
			if (dirPair != null) {
				GeoPoint node1 = dirPair.first;
				GeoPoint node2 = dirPair.second;
				
				Direction direction = directions.get(node2);
				
				if (direction == null) {
					direction = new Direction(node1, node2);
					directions.put(node2, direction);
				}
				direction.addPoint(point);
				point.setRelativeDirection(direction.getRelativeDirection(location.getBearing()));
			}
		}
		
		// XXX: reset relative direction to non-active POI
		lastResultDirections = new ArrayList<Direction>(directions.values());
		if (listener != null)
			listener.directionsUpdated(lastResultDirections, userPosition);
	}
	
	private Pair<GeoPoint, GeoPoint> getDirectionNode(GeoPoint current, PointList path) {		
		final int radius = 150;
		
		GeoPoint point = new GeoPoint(0, 0);
		GeoPoint prev = Utils.copyGeoPoint(current);
		
		for (int i = 1; i < path.getSize(); i++) {
			point.setCoordsE6((int) (path.getLatitude(i) * 1e6), (int) (path.getLongitude(i) * 1e6));
			
			int dist = current.distanceTo(point);
			if (dist > radius) {
				return Pair.create(current, point);
			}
			prev = Utils.copyGeoPoint(point);
		}
		
		return Pair.create(current, prev);
	}

}
