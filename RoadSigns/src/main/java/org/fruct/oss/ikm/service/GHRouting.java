package org.fruct.oss.ikm.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.Location2IDFullIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.utils.Region;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public abstract class GHRouting {
	protected static Logger log = LoggerFactory.getLogger(GHRouting.class);
	protected final LocationIndexCache locationIndexCache;

	private String path;
	private GeoPoint oldGeoPoint = null;
	private boolean isInitialized = false;
	private boolean isInitializationFailed = false;
	protected GraphHopper hopper;
	private LocationIndex[] locationIndexArray;
	private NodeAccess nodeAccess;

	private Region region;

	public GHRouting(String path, LocationIndexCache locationIndexCache) {
		this.path = path;
		this.locationIndexCache = locationIndexCache;
	}

	private LocationIndex[] createLocationIndexArray() {
		ArrayList<LocationIndex> arr = new ArrayList<LocationIndex>();

		// Default index
		arr.add(hopper.getLocationIndex());

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

			locationIndexArray = createLocationIndexArray();
			nodeAccess = hopper.getGraph().getNodeAccess();

			FileInputStream polygonFileStream = new FileInputStream(path + "/polygon.poly");
			region = new Region(polygonFileStream);
			polygonFileStream.close();

			if (res) {
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
			int id = index.findID(geoPoint.getLatitude(), geoPoint.getLongitude());
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

	public boolean isInner(double lat, double lon) {
		ensureInitialized();
		return region.testHit(lat, lon);
	}

	public abstract void prepare(int fromId);

	public abstract  PointList route(GeoPoint to);
	public abstract void route(PointDesc[] targetPoints, float radius, RoutingCallback callback);

	public abstract void setEncoder(String encoding);

	public abstract IMapMatcher createMapMatcher();

	public abstract IMapMatcher createSimpleMapMatcher();

	public interface RoutingCallback {
		void pointReady(GeoPoint center, GeoPoint target, PointDesc pointDesc);
	}
}
