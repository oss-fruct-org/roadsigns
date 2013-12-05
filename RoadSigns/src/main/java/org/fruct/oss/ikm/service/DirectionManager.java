package org.fruct.oss.ikm.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;

import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Pair;

import com.graphhopper.util.DistanceCalc3D;
import com.graphhopper.util.PointList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectionManager {
	private static Logger log = LoggerFactory.getLogger(DirectionManager.class);

	public interface Listener {
		void directionsUpdated(List<Direction> directions, GeoPoint center);
	}
	
	private Listener listener;
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public final int BATCH_SIZE = 10;
	
	private int radius = 45;
	private Routing routing;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private GeoPoint userPosition;
	private Location location;

	// POI, for that directions ready
	private Map<PointDesc, PointList> readyPoints = new HashMap<PointDesc, PointList>();
	
	// POI, that pass filters
	private List<PointDesc> activePoints = new ArrayList<PointDesc>();
	
	public DirectionManager(Routing routing) {
		this.routing = routing;
	}
	
	private Comparator<PointDesc> distanceComparator = new Comparator<PointDesc>() {
		private GeoPoint point = new GeoPoint(0, 0);
		
		@Override
		public int compare(PointDesc lhs, PointDesc rhs) {
			point.setCoordsE6((int) (location.getLatitude() * 1e6), (int) (location.getLongitude() * 1e6));
			
			int d1 = lhs.toPoint().distanceTo(point);
			int d2 = rhs.toPoint().distanceTo(point);
			
			return d1 - d2;
		}
	};
	
	public void calculateForPoints(final List<PointDesc> points) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				activePoints = new ArrayList<PointDesc>(points);
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
				if (nearestNode == null || current.distanceTo(nearestNode) > 40)
					return;

				DirectionManager.this.userPosition = nearestNode;
				routing.reset(userPosition);
				
				readyPoints.clear();
				// doCalculateForPoints();
			}
		});
	}
	
	private void preparePoints() {
		int nearest;
		
		try {
			String nearestStr = PreferenceManager.getDefaultSharedPreferences(App.getContext()).getString(SettingsActivity.NEAREST_POINTS, "");
			nearest = Integer.parseInt(nearestStr);
		} catch (NumberFormatException ex) {
			nearest = 0;
		}
		
		// Retaing only ${nearest} POI's 		
		if (nearest > 0 && activePoints.size() > nearest) {
			Collections.sort(activePoints, distanceComparator);			
			activePoints = activePoints.subList(0, nearest);
		}
	}
	
	private void doCalculateForPoints() {
		if (activePoints == null || userPosition == null)
			return;
		
		preparePoints();
		
		long last = System.nanoTime();
		
		// Process no more than BATCH_SIZE points at once
		int pointsProcessed = 0;
		boolean needContinue = false;
		
		int dbgPointsProcessed = 0, dbgPointsCache = 0;
		
		// Hash table mapping road direction to POI list
		for (PointDesc point : activePoints) {	
			dbgPointsProcessed++;
			
			// If direction for this point already calculated, skip it
			if (readyPoints.containsKey(point)) {
				dbgPointsCache++;
				continue;
			}

			PointList path = routing.route(point.toPoint());
			if (path == null || path.getSize() < 2)
				continue;
			
			readyPoints.put(point, path);
			
			int dist = (int) path.calculateDistance(new DistanceCalc3D());
			point.setDistance(dist);
			
			pointsProcessed++;
			if (pointsProcessed >= BATCH_SIZE) {
				needContinue = true;
				break;
			}
		}
		
		long curr = System.nanoTime();
		log.info("Routing results " + (curr - last) / 1e9 + " cache/total = " + dbgPointsCache + "/" + dbgPointsProcessed);
		
		sendResult();
		
		if (needContinue) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					doCalculateForPoints();
				}
			});
		}
	}
	
	private void sendResult() {
		if (readyPoints.isEmpty())
			return;
		
		HashMap<GeoPoint, Direction> directions = new HashMap<GeoPoint, Direction>();
		for (PointDesc point : activePoints) {
			PointList path = readyPoints.get(point);
			
			if (path != null) {
				Pair<GeoPoint, GeoPoint> dirPair = getDirectionNode(userPosition, path);

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
		ArrayList<Direction> lastResultDirections = new ArrayList<Direction>(directions.values());
		if (listener != null)
			listener.directionsUpdated(lastResultDirections, userPosition);
	}
	
	private Pair<GeoPoint, GeoPoint> getDirectionNode(final GeoPoint current, PointList path) {				
		GeoPoint point = new GeoPoint(0, 0);
		GeoPoint prev = Utils.copyGeoPoint(current);
		
		//point.
		
		for (int i = 1; i < path.getSize(); i++) {
			point.setCoordsE6((int) (path.getLatitude(i) * 1e6), (int) (path.getLongitude(i) * 1e6));
			
			final int dist = current.distanceTo(point);
			if (dist > radius) {
				final GeoPoint a = prev;
				final GeoPoint b = point;
				final double d = a.distanceTo(b) + 2;
				final float bearing = (float) a.bearingTo(b);
				
				// TODO: catch exceptions
				double sol = Utils.solve(0, d, 0.1, new Utils.FunctionDouble() {
					@Override
					public double apply(double x) {
						GeoPoint mid = a.destinationPoint(x, bearing);
						double distFromCenter = current.distanceTo(mid);
						return distFromCenter - (radius + 2);
					}
				});

				GeoPoint target = a.destinationPoint(sol, bearing);
				
				return Pair.create(a, target);
			}
			prev = Utils.copyGeoPoint(point);
		}
		
		return Pair.create(current, prev);
	}

	public void setRadius(int radius) {
		if (this.radius != radius) {
			this.radius = radius;
			sendResult();
		}
	}
}