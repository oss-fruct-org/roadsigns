package org.fruct.oss.ikm.service;

import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Pair;

import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.events.EventReceiver;
import org.fruct.oss.ikm.events.LocationEvent;
import org.fruct.oss.ikm.events.ScreenRadiusEvent;
import org.fruct.oss.ikm.events.TargetPointEvent;
import org.fruct.oss.ikm.events.TrackingModeEvent;
import org.fruct.oss.ikm.points.Point;
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
import java.util.concurrent.TimeUnit;

import de.greenrobot.event.EventBus;

public class DirectionManager implements GHRouting.RoutingCallback {
	private static Logger log = LoggerFactory.getLogger(DirectionManager.class);

	private Listener listener;
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public final int BATCH_SIZE = 10;
	
	private int radius = 45;
	private GHRouting routing;

	private GeoPoint targetPoint;
	private GeoPoint userPosition;
	private Location location;

	private final ExecutorService executor;

	private Future<?> calculationTask;
	private Future<?> updatePathTask;

	private boolean isTrackingMode = false;

	// POI, for that directions ready
	private Map<Point, Pair<GeoPoint, GeoPoint>> readyPoints = new HashMap<>();
	
	// POI, that pass filters
	private List<Point> activePoints = new ArrayList<>();
	
	public DirectionManager(GHRouting routing) {
		if (routing == null)
			throw new IllegalArgumentException("DirectionManager: routing argument can not be null");

		this.executor = Executors.newSingleThreadExecutor();
		this.routing = routing;

		EventBus.getDefault().registerSticky(this);
	}

	public void closeSync() {
		EventBus.getDefault().unregister(this);
		log.debug("ASD unregistered");

		listener = null;
		interrupt();

		try {
			executor.shutdownNow();
			if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
				log.error("Shutdown direction manager failed");
			}
			routing = null;
		} catch (InterruptedException ignored) {
		}
	}

	private Comparator<Point> distanceComparator = new Comparator<Point>() {
		private GeoPoint point = new GeoPoint(0, 0);
		
		@Override
		public int compare(Point lhs, Point rhs) {
			point.setCoordsE6((int) (location.getLatitude() * 1e6), (int) (location.getLongitude() * 1e6));
			
			int d1 = lhs.toPoint().distanceTo(point);
			int d2 = rhs.toPoint().distanceTo(point);
			
			return d1 - d2;
		}
	};

	private void calculateForActivePoints() {
		calculationTask = executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					activePoints = App.getInstance().getPointsAccess().loadActive();
					doCalculateForPoints();
				} catch (Exception ex) {
					log.error("Routing error: ", ex);
				}
			}
		});
	}

	public void updatePath(final GeoPoint targetPoint) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					routing.route(targetPoint, DirectionManager.this);
				} catch (Exception ex) {
					log.error("Path routing error: ", ex);
				}
			}
		});
	}

	public void updateLocation(final Location location, final int node) {
		interrupt();

		executor.execute(new Runnable() {
			@Override
			public void run() {
				DirectionManager.this.location = location;
				GeoPoint current = new GeoPoint(location);

				routing.getPoint(node, current);
				DirectionManager.this.userPosition = current;
				readyPoints.clear();

				routing.prepare(node);
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

		//Point.resetAllData();
		preparePoints();
		routing.route(activePoints.toArray(new Point[activePoints.size()]), radius, this);
		sendResult();
	}

	@Override
	public void pointReady(GeoPoint center, GeoPoint target, Point point) {
		readyPoints.put(point, Pair.create(center, target));
		if (readyPoints.size() % BATCH_SIZE == 0) {
			sendResult();
		}
	}

	@Override
	public void pathUpdated(PointList pointList) {
		if (listener != null)
			listener.pathReady(pointList);
	}

	private void sendResult() {
		if (readyPoints.isEmpty())
			return;
		
		HashMap<GeoPoint, Direction> directions = new HashMap<>();
		for (Point point : activePoints) {
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
		ArrayList<Direction> lastResultDirections = new ArrayList<>(directions.values());
		if (listener != null)
			listener.directionsUpdated(lastResultDirections, userPosition);
	}

	@EventReceiver
	public void onEventMainThread(ScreenRadiusEvent screenRadiusEvent) {
		int newRadius = screenRadiusEvent.getRadius();
		if (this.radius != newRadius) {
			this.radius = newRadius;
		}

		if (isTrackingMode) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					if (DirectionManager.this.radius != radius) {
						DirectionManager.this.radius = radius;
						sendResult();
					}
				}
			});
		}
	}

	@EventReceiver
	public void onEventMainThread(LocationEvent locationEvent) {
		log.debug("ASD received");

		if (locationEvent.getMatchedNode() < 0)
			return;

		updateLocation(locationEvent.getLocation(), locationEvent.getMatchedNode());

		if (targetPoint != null) {
			updatePath(targetPoint);
		}

		if (isTrackingMode) {
			interrupt();
			calculateForActivePoints();
		}
	}

	@EventReceiver
	public void onEventMainThread(TrackingModeEvent trackingModeEvent) {
		isTrackingMode = trackingModeEvent.isInTrackingMode();
	}

	@EventReceiver
	public void onEventMainThread(TargetPointEvent targetPointEvent) {
		targetPoint = targetPointEvent.getGeoPoint();
		updatePath(targetPoint);
	}

	private void interrupt() {
		if (calculationTask != null) {
			calculationTask.cancel(true);
			calculationTask = null;
		}
	}

	public interface Listener {
		void directionsUpdated(List<Direction> directions, GeoPoint center);
		void pathReady(PointList pointList);
	}
}
