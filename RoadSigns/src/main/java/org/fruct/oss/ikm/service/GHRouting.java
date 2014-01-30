package org.fruct.oss.ikm.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.index.Location2IDFullIndex;
import com.graphhopper.storage.index.Location2IDQuadtree;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.PointList;

import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;

public abstract class GHRouting implements IRouting {
	protected static Logger log = LoggerFactory.getLogger(GHRouting.class);
	protected final LocationIndexCache locationIndexCache;

	private String path;
	private GeoPoint oldGeoPoint = null;
	private boolean isInitialized = false;
	private boolean isInitializationFailed = false;
	protected GraphHopper hopper;
	private LocationIndex[] locationIndexArray;


	public abstract void prepare(GeoPoint from);
	public abstract PointList route(GeoPoint to);

	public GHRouting(String path, LocationIndexCache locationIndexCache) {
		this.path = path;
		this.locationIndexCache = locationIndexCache;
	}

	public void reset(GeoPoint from) {
		if (!ensureInitialized())
			return;
		
		if (!from.equals(oldGeoPoint))
			prepare(from);
	}

	private LocationIndex[] createLocationIndexArray() {
		ArrayList<LocationIndex> arr = new ArrayList<LocationIndex>();

		// Default index
		arr.add(hopper.getLocationIndex());

		// Quad tree index if available
		String quadTreeFileName = path + "/loc2idIndex";
		if (new File(quadTreeFileName).exists()) {
			log.info("Enabling quadtree index as fallback");
			arr.add(new Location2IDQuadtree(hopper.getGraph(), new MMapDirectory(path)));
		} else {
			log.info("Quadtree index is unavailable");
		}

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

	public GeoPoint getNearestRoadNode(GeoPoint current) {
		if (!ensureInitialized())
			return null;

		int nodeId = getPointIndex(current, false);
		
		double lat = hopper.getGraph().getLatitude(nodeId);
		double lon = hopper.getGraph().getLongitude(nodeId);

		return new GeoPoint(lat, lon);
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

				log.trace("LocationIndex found in {}", index.getClass().getName());

				return id;
			}
		}

		return -1;
	}

	public PointList findPath(GeoPoint from, GeoPoint to) {
		if (!ensureInitialized())
			return null;

		log.trace("findPath enter");
		try {
			prepare(from);
			return route(to);
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		} finally {
			log.trace("findPath exit");
		}
	}
}
