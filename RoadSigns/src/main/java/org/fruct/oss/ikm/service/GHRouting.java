package org.fruct.oss.ikm.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.Location2IDFullIndex;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.PointList;

import org.fruct.oss.ghpriority.FootPriorityFlagEncoder;
import org.fruct.oss.ghpriority.PriorityGraphHopper;
import org.fruct.oss.ikm.points.Point;
import org.fruct.oss.ikm.utils.Utils;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public abstract class GHRouting implements Closeable {
	public static final int MAX_REGION_SEARCH = 100;
	protected static Logger log = LoggerFactory.getLogger(GHRouting.class);
	protected final LocationIndexCache locationIndexCache;

	private String path;
	private boolean isInitialized = false;
	private boolean isInitializationFailed = false;
	protected GraphHopper hopper;

	private LocationIndex baseLocationIndex;
	private LocationIndex fallbackLocationIndex;

	private final Set<GeoPoint> currentIndexTasks = new HashSet<GeoPoint>();

	private NodeAccess nodeAccess;

	protected boolean isClosed;
	protected EncodingManager encodingManager;
	protected FlagEncoder encoder;
	protected EdgeFilter edgeFilter;
	protected FastestWeighting weightCalc;
	protected String encodingString = "CAR";

	private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor();

	public GHRouting(String path, LocationIndexCache locationIndexCache) {
		this.path = path;
		this.locationIndexCache = locationIndexCache;
	}

	@Override
	public void close() {
		isClosed = true;
		hopper.close();

		synchronized (indexExecutor) {
			indexExecutor.shutdownNow();
			try {
				indexExecutor.awaitTermination(10, TimeUnit.SECONDS);
				log.debug("Location index executor successfully shutdown");
			} catch (InterruptedException e) {
				log.error("Can't shutdown location index executor in 10 seconds", e);
			}
		}
	}

	private void createLocationIndex() {
		baseLocationIndex = hopper.getLocationIndex();
		if (baseLocationIndex instanceof LocationIndexTree) {
			((LocationIndexTree) baseLocationIndex).setMaxRegionSearch(MAX_REGION_SEARCH);
		}

		fallbackLocationIndex = new Location2IDFullIndex(hopper.getGraph());
	}
	
	public void initialize() {
		if (isInitialized || isInitializationFailed)
			return;
		
		try {
			hopper = new PriorityGraphHopper().forMobile();
			hopper.setCHEnable(false);
			hopper.setEncodingManager(new EncodingManager(new ArrayList<FlagEncoder>(4) {{
				add(new CarFlagEncoder());
				add(new BikeFlagEncoder());
				add(new FootFlagEncoder());
				add(new FootPriorityFlagEncoder());
			}}, 8));

			//hopper.setPreciseIndexResolution(0);
			//hopper.setMemoryMapped();

			//hopper.setCHShortcuts("shortest");
			boolean res = hopper.load(path);
			if (res) {
				encodingManager = hopper.getEncodingManager();
				updateEncoders();

				createLocationIndex();
				nodeAccess = hopper.getGraph().getNodeAccess();

				FileInputStream polygonFileStream = new FileInputStream(path + "/polygon.poly");
				polygonFileStream.close();

				log.info("graphopper for path {} successfully initialized", path);
				isInitialized = true;
			} else {
				log.error("Cannot initialize graphhopper for path {}", path);
				isInitializationFailed = true;
			}
		} catch (Exception th) {
			log.error("graphhopper initialization for path" + path + "finished with exception", th);
			th.printStackTrace();
			isInitializationFailed = true;
		}
	}

	private void updateEncoders() {
		if (encodingManager != null) {
			encoder = encodingManager.getEncoder(encodingString);
			edgeFilter = new DefaultEdgeFilter(encoder, true, true);
			weightCalc = new FastestWeighting(encoder);
		}
	}

	protected boolean ensureInitialized() {
		initialize();
		return !isInitializationFailed;
	}

	public GeoPoint getPoint(int nodeId, GeoPoint outPoint) {
		outPoint.setLatitudeE6((int) (nodeAccess.getLatitude(nodeId) * 1e6));
		outPoint.setLongitudeE6((int) (nodeAccess.getLongitude(nodeId) * 1e6));
		return outPoint;
	}

	private Future<Integer> enqueueGeoPoint(GeoPoint geoPoint) {
		final GeoPoint geoPoint2 = Utils.copyGeoPoint(geoPoint);

		FutureTask<Integer> task = new FutureTask<Integer>(new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				return findInIndex(geoPoint2, fallbackLocationIndex, true);
			}
		});

		synchronized (indexExecutor) {
			indexExecutor.submit(task);
		}

		return task;
	}

	private int findInIndex(GeoPoint geoPoint, LocationIndex index, boolean useCache) {
		if (useCache) {
			int cachedIndex = locationIndexCache.get(geoPoint);
			if (cachedIndex != -1)
				return cachedIndex;
		}

		QueryResult result = index.findClosest(geoPoint.getLatitude(), geoPoint.getLongitude(), edgeFilter);

		int id = result.getClosestNode();
		if (id != -1) {
			if (useCache) {
				locationIndexCache.put(geoPoint, id);
			}

			log.trace("LocationIndex {} found in {} ", id, index.getClass().getName());

			return id;
		} else {
			return -1;
		}
	}

	public int getPointIndex(GeoPoint geoPoint, boolean useCache) {
		throwIfClosed();

		// Search in cache
		if (useCache) {
			int cachedIndex = locationIndexCache.get(geoPoint);
			if (cachedIndex != -1)
				return cachedIndex;
		}

		int node = findInIndex(geoPoint, baseLocationIndex, useCache);

		if (node >= 0) {
			return node;
		} else {
			synchronized (indexExecutor) {
				if (!currentIndexTasks.contains(geoPoint)) {
					enqueueGeoPoint(geoPoint);
					currentIndexTasks.add(geoPoint);
					return -1;
				}
			}
		}

		return -1;
	}

	public QueryResult getQueryResult(GeoPoint geoPoint) {
		throwIfClosed();

		QueryResult id = baseLocationIndex.findClosest(geoPoint.getLatitude(), geoPoint.getLongitude(), edgeFilter);
		if (id.isValid()) {
			log.trace("LocationIndex edge found in {}", id, baseLocationIndex.getClass().getName());
			return id;
		}

		return null;
	}

	public abstract void prepare(int fromId);

	public abstract void route(Point[] targetPoints, float radius, RoutingCallback callback);
	public abstract void route(GeoPoint to, RoutingCallback callback);

	public void setEncoder(String encoding) {
		this.encodingString = encoding;
		updateEncoders();
	}

	public abstract IMapMatcher createMapMatcher();

	public abstract IMapMatcher createSimpleMapMatcher();

	public void throwIfClosed() {
		if (isClosed) {
			throw new IllegalStateException("Routing already closed");
		}
	}

	public EncodingManager getEncodingManager() {
		throwIfClosed();
		return hopper.getEncodingManager();
	}

	public interface RoutingCallback {
		void pointReady(GeoPoint center, GeoPoint target, Point point);
		void pathUpdated(PointList path);
	}
}
