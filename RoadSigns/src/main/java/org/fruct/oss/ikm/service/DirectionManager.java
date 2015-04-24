package org.fruct.oss.ikm.service;

import android.location.Location;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Pair;

import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.events.EventReceiver;
import org.fruct.oss.ikm.events.LocationEvent;
import org.fruct.oss.ikm.events.PointsUpdatedEvent;
import org.fruct.oss.ikm.events.RoutingFinishedEvent;
import org.fruct.oss.ikm.events.RoutingStartedEvent;
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
import java.util.Iterator;
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
	private String regionId;

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

	private boolean isTrackingMode = false;

	// POI, for that directions ready
	private Map<Point, Pair<GeoPoint, GeoPoint>> readyPoints = new HashMap<>();
	
	// POI, that pass filters
	private List<Point> activePoints = new ArrayList<>();

	private boolean isClosed;
	
	public DirectionManager(@NonNull GHRouting routing, @NonNull String regionId) {
		this.regionId = regionId;
		this.executor = Executors.newSingleThreadExecutor();
		this.routing = routing;

		EventBus.getDefault().registerSticky(this);
	}

	public synchronized void shutdown() {
		EventBus.getDefault().unregister(this);
		log.debug("ASD unregistered");

		isClosed = true;

		listener = null;
		interrupt();

		executor.shutdownNow();
	}

	public void awaitShutdown() {
		try {
			executor.awaitTermination(10, TimeUnit.SECONDS);
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
		log.debug("calculateForActivePoints");
		if (isTrackingMode) {
			interrupt();
		} else {
			log.debug("no tracking mode");
		}

		calculationTask = executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					activePoints = App.getInstance().getPointsAccess().loadActive();
					log.debug("Routing for {} points", activePoints.size());
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

	private void updateLocation(final Location location, final int node) {
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
			String nearestStr = PreferenceManager.getDefaultSharedPreferences(
					App.getContext()).getString(SettingsActivity.NEAREST_POINTS, "");
			nearest = Integer.parseInt(nearestStr);
		} catch (NumberFormatException ex) {
			nearest = 0;
		}

		// Remove points from other regions
		for (Iterator<Point> iterator = activePoints.iterator(); iterator.hasNext(); ) {
			Point point = iterator.next();

			if (!regionId.equals(point.getRegionId(4))) {
				iterator.remove();
			}
		}

		// Retain only $nearest points
		if (nearest > 0 && activePoints.size() > nearest) {
			Collections.sort(activePoints, distanceComparator);			
			activePoints = activePoints.subList(0, nearest);
		}

		Point.invalidateData();
		log.debug("{} points retained for region id {} and limit {}", activePoints.size(), regionId, nearest);
	}

	private void doCalculateForPoints() {
		if (activePoints == null || userPosition == null) {
			log.debug("No active points or user position {} {}", activePoints, userPosition);
			return;
		}

		//Point.resetAllData();
		log.debug("Starting directions routing");
		EventBus.getDefault().postSticky(new RoutingStartedEvent());
		try {
			preparePoints();
			routing.route(activePoints.toArray(new Point[activePoints.size()]), radius, this);
			sendResult();

		} finally {
			log.debug("End directions routing");
			EventBus.getDefault().removeStickyEvent(RoutingStartedEvent.class);
			EventBus.getDefault().post(new RoutingFinishedEvent());
		}
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
		synchronized (this) {
			if (!isClosed && listener != null) {
				listener.pathReady(pointList);
			}
		}
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

		ArrayList<Direction> lastResultDirections = new ArrayList<>(directions.values());
		synchronized (this) {
			if (!isClosed && listener != null) {
				listener.directionsUpdated(lastResultDirections, userPosition);
			}
		}
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
		log.debug("location received");

		if (locationEvent.getMatchedNode() < 0) {
			log.debug("no matched node");
			return;
		}

		updateLocation(locationEvent.getLocation(), locationEvent.getMatchedNode());

		if (targetPoint != null) {
			updatePath(targetPoint);
		}

		calculateForActivePoints();
	}

	@EventReceiver
	public void onEventMainThread(TrackingModeEvent trackingModeEvent) {
		isTrackingMode = trackingModeEvent.isInTrackingMode();

		calculateForActivePoints();
	}

	@EventReceiver
	public void onEventMainThread(TargetPointEvent targetPointEvent) {
		targetPoint = targetPointEvent.getGeoPoint();
		updatePath(targetPoint);
	}

	@EventReceiver
	public void onEventMainThread(PointsUpdatedEvent event) {
		if (event.isSuccess()) {
			calculateForActivePoints();
		}
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
