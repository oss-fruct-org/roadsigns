package org.fruct.oss.ikm.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.Location2IDFullIndex;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.utils.Region;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.FileInputStream;
import java.util.ArrayList;

public abstract class GHRouting implements Closeable {
	public static final int MAX_REGION_SEARCH = 100;
	protected static Logger log = LoggerFactory.getLogger(GHRouting.class);
	protected final LocationIndexCache locationIndexCache;

	private String path;
	private boolean isInitialized = false;
	private boolean isInitializationFailed = false;
	protected GraphHopper hopper;
	private LocationIndex[] locationIndexArray;
	private NodeAccess nodeAccess;

	protected Region region;
	protected boolean isClosed;
	protected EncodingManager encodingManager;
	protected FlagEncoder encoder;
	protected EdgeFilter edgeFilter;
	protected FastestWeighting weightCalc;
	protected String encodingString = "CAR";

	public GHRouting(String path, LocationIndexCache locationIndexCache) {
		this.path = path;
		this.locationIndexCache = locationIndexCache;
	}

	@Override
	public void close() {
		isClosed = true;
		hopper.close();
	}

	private LocationIndex[] createLocationIndexArray() {
		ArrayList<LocationIndex> arr = new ArrayList<LocationIndex>();

		// Default index
		LocationIndex defaultIndex = hopper.getLocationIndex();
		if (defaultIndex instanceof LocationIndexTree) {
			((LocationIndexTree) defaultIndex).setMaxRegionSearch(MAX_REGION_SEARCH);
		}

		arr.add(defaultIndex);

		// Quad tree index if available
		/*String quadTreeFileName = path + "/loc2idIndex";
		if (new File(quadTreeFileName).exists()) {
			log.info("Enabling quadtree index as fallback");
			arr.add(new LocationIndexTree(hopper.getGraph(), new MMapDirectory(path)).prepareIndex());
		} else {
			log.info("Quadtree index is unavailable");
		}*/

		// Slow linear search index
		arr.add(new Location2IDFullIndex(hopper.getGraph()));

		return arr.toArray(new LocationIndex[1]);
	}
	
	public void initialize() {
		if (isInitialized || isInitializationFailed)
			return;
		
		try {
			hopper = new GraphHopper().forMobile();
			hopper.disableCHShortcuts();
			//hopper.setPreciseIndexResolution(0);
			//hopper.setMemoryMapped();

			//hopper.setCHShortcuts("shortest");
			boolean res = hopper.load(path);
			if (res) {
				encodingManager = hopper.getEncodingManager();
				updateEncoders();

				locationIndexArray = createLocationIndexArray();
				nodeAccess = hopper.getGraph().getNodeAccess();

				FileInputStream polygonFileStream = new FileInputStream(path + "/polygon.poly");
				region = new Region(polygonFileStream);
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

	public GeoPoint getPoint(int nodeId) {
		double lat = nodeAccess.getLatitude(nodeId);
		double lon = nodeAccess.getLongitude(nodeId);

		return new GeoPoint(lat, lon);
	}

	public GeoPoint getPoint(int nodeId, GeoPoint outPoint) {
		outPoint.setLatitudeE6((int) (nodeAccess.getLatitude(nodeId) * 1e6));
		outPoint.setLongitudeE6((int) (nodeAccess.getLongitude(nodeId) * 1e6));
		return outPoint;
	}

	public int getPointIndex(GeoPoint geoPoint, boolean useCache) {
		if (useCache) {
			int cachedIndex = locationIndexCache.get(geoPoint);
			if (cachedIndex != -1)
				return cachedIndex;
		}

		for (LocationIndex index : locationIndexArray) {
			QueryResult result = index.findClosest(geoPoint.getLatitude(), geoPoint.getLongitude(), edgeFilter);

			int id = result.getClosestNode();
			if (id != -1) {
				if (useCache) {
					locationIndexCache.put(geoPoint, id);
				}

				log.trace("LocationIndex {} found in {}", id, index.getClass().getName());

				return id;
			}
		}

		return -1;
	}

	public QueryResult getQueryResult(GeoPoint geoPoint) {
		for (LocationIndex index : locationIndexArray) {
			QueryResult id = index.findClosest(geoPoint.getLatitude(), geoPoint.getLongitude(), edgeFilter);
			if (id.isValid()) {
				log.trace("LocationIndex edge found in {}", id, index.getClass().getName());
				return id;
			}
		}
		return null;
	}


	public boolean isInner(double lat, double lon) {
		ensureInitialized();
		return region.testHit(lat, lon);
	}

	public abstract void prepare(int fromId);

	public abstract  PointList route(GeoPoint to);
	public abstract void route(PointDesc[] targetPoints, float radius, RoutingCallback callback);

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
		void pointReady(GeoPoint center, GeoPoint target, PointDesc pointDesc);
	}
}
