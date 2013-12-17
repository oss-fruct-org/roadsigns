package org.fruct.oss.ikm.service;

import org.osmdroid.util.GeoPoint;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.util.PointList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GHRouting implements IRouting {
	private static Logger log = LoggerFactory.getLogger(GHRouting.class);

	private String path;
	private GeoPoint oldGeoPoint = null;
	private boolean isInitialized = false;
	protected GraphHopper hopper;

	public abstract void prepare(GeoPoint from);
	public abstract PointList route(GeoPoint to);

	public GHRouting(String path) {
		this.path = path;
	}

	public void reset(GeoPoint from) {
		initialize();
		
		if (!from.equals(oldGeoPoint))
			prepare(from);
	}
	
	public void initialize() {
		if (isInitialized )
			return;
		
		try {
			hopper = new GraphHopper().forMobile();
			hopper.disableCHShortcuts();
			boolean res = hopper.load(path);
			if (res) {
				log.info("graphopper for path {} successfully initialized", path);
			} else {
				log.error("Cannot initialize graphhopper for path {}", path);
			}
			isInitialized = true;
		} catch (Exception th) {
			log.error("graphhopper initialization for path" + path + "finished with exception", th);

			th.printStackTrace();
		}
	}

	public GeoPoint getNearestRoadNode(GeoPoint current) {
		initialize();
		
		Location2IDIndex index = hopper.getLocationIndex();
		AbstractFlagEncoder encoder = hopper.getEncodingManager().getEncoder("CAR");
		DefaultEdgeFilter filter = new DefaultEdgeFilter(encoder);
		
		int nodeId = index.findClosest(current.getLatitudeE6()/1e6,
				current.getLongitudeE6()/1e6, filter).getClosestNode();
		
		if (nodeId < 0)
			return null;
		
		double lat = hopper.getGraph().getLatitude(nodeId);
		double lon = hopper.getGraph().getLongitude(nodeId);

		return new GeoPoint(lat, lon);
	}

	public PointList findPath(GeoPoint from, GeoPoint to) {
		initialize();

		log.debug("findPath enter");
		try {
			prepare(from);

			return route(to);
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		} finally {
			log.debug("findPath exit");
		}
	}
}
