package org.fruct.oss.ikm.service;

import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Pair;

import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DirectionManager implements IRouting.RoutingCallback {
	private static Logger log = LoggerFactory.getLogger(DirectionManager.class);

	public interface Listener {
		void directionsUpdated(List<Direction> directions, GeoPoint center);
		void pathReady(PointList pointList);
	}
	
	private Listener listener;
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public final int BATCH_SIZE = 10;
	
	private int radius = 45;
	private IRouting routing;

	private GeoPoint userPosition;
	private Location location;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private Future<?> calculationTask;

		// POI, for that directions ready
	private Map<PointDesc, Pair<GeoPoint, GeoPoint>> readyPoints = new HashMap<PointDesc, Pair<GeoPoint, GeoPoint>>();
	
	// POI, that pass filters
	private List<PointDesc> activePoints = new ArrayList<PointDesc>();
	
	public DirectionManager(IRouting routing) {
		this.routing = routing;
	}

	public void setRouting(IRouting routing) {
		// TODO: no thread safe
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

	// TODO: add support for interrupting to OneToManyRouting
	private void interruptCurrentCalculation() {
		synchronized (executor) {
			if (calculationTask != null) {
				calculationTask.cancel(true);
				calculationTask = null;
			}
		}
	}

	public void calculateForPoints(final List<PointDesc> points) {
		interruptCurrentCalculation();

		calculationTask = executor.submit(new Runnable() {
			@Override
			public void run() {
				activePoints = new ArrayList<PointDesc>(points);

				doCalculateForPoints();
			}
		});
	}
	
	public void updateLocation(final Location location) {
		interruptCurrentCalculation();

		executor.execute(new Runnable() {
			@Override
			public void run() {
				DirectionManager.this.location = location;
				GeoPoint current = new GeoPoint(location);

				// Find nearest road node
				// Can throw if not initialized, ignoring
				GeoPoint nearestNode = routing.getNearestRoadNode(current);				
				if (nearestNode == null || current.distanceTo(nearestNode) > 40)
					return;

				DirectionManager.this.userPosition = nearestNode;
				readyPoints.clear();

				// Can throw if not initialized, ignore
				routing.reset(userPosition);
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
		
		// Retain only ${nearest} POI's
		if (nearest > 0 && activePoints.size() > nearest) {
			Collections.sort(activePoints, distanceComparator);			
			activePoints = activePoints.subList(0, nearest);
		}
	}

	private void doCalculateForPoints() {
		if (activePoints == null || userPosition == null)
			return;

		preparePoints();
		routing.route(activePoints.toArray(new PointDesc[activePoints.size()]), radius, this);
		sendResult();
	}

	@Override
	public void pointReady(GeoPoint center, GeoPoint target, PointDesc pointDesc) {
		readyPoints.put(pointDesc, Pair.create(center, target));
		if (readyPoints.size() % BATCH_SIZE == 0) {
			sendResult();
		}
	}

	private void sendResult() {
		if (readyPoints.isEmpty())
			return;
		
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
		ArrayList<Direction> lastResultDirections = new ArrayList<Direction>(directions.values());
		if (listener != null)
			listener.directionsUpdated(lastResultDirections, userPosition);
	}

	public void setRadius(int radius) {
		if (this.radius != radius) {
			this.radius = radius;
			sendResult();
		}
	}

	public void findPath(final GeoPoint to) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				doFindPath(to);
			}
		});
	}

	public void doFindPath(GeoPoint to) {
		try {
			if (routing instanceof GHRouting) {
				PointList pointList = routing.route(to);

				if (listener != null)
					listener.pathReady(pointList);
			}
		} catch (Exception ex) {
			log.error("findPath throw exception", ex);
		}
	}

	public void interrupt() {
		interruptCurrentCalculation();
	}

	public void setEncoder(String encoder) {
		if (routing != null)
			routing.setEncoder(encoder);
	}
}
