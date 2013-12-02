package org.fruct.oss.ikm.service;

import static org.fruct.oss.ikm.Utils.log;

import java.io.IOException;
import java.io.InputStream;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.Utils;
import org.osmdroid.util.GeoPoint;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.util.PointList;

public abstract class Routing {
	private GeoPoint oldGeoPoint = null;
	private boolean isInitialized = false;
	protected GraphHopper hopper;
	
	protected abstract void prepare(GeoPoint from);
	public abstract PointList route(GeoPoint to);
	
	public void reset(GeoPoint from) {
		initialize();
		
		if (!from.equals(oldGeoPoint))
			prepare(from);
	}
	
	public void initialize() {
		if (isInitialized )
			return;
		
		try {
			InputStream input = App.getContext().getAssets().open("karelia.ghz.ghz");
			initializeFrom(input, "karelia");
			input.close();
			
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (Throwable th) {
			th.printStackTrace();
		}
	}
	
	/**
	 * Load road graph from stream
	 * @param input stream containing graphhopper's zip archive
	 * @param name identifier 
	 * @return true if success, false otherwise
	 */
	public boolean initializeFrom(InputStream input, String name) {
		log("DirectionService.initializeFrom ENTER");
		hopper = new GraphHopper().forMobile();
		
		try {
			//hopper.set
			hopper.disableCHShortcuts();
			//hopper.setCHShortcuts("shortest");
			String filename = Utils.copyToInternalStorage(App.getContext(), input, "graphhopper", name + ".ghz.ghz");
			filename = filename.substring(0, filename.length() - 4); // Cut last ".ghz"
			boolean res = hopper.load(filename);
			log("GraphHopper loaded " + res);
			isInitialized = true;

			return res;
		} catch (Exception ex) {
			ex.printStackTrace();
			return false;
		} finally {
			log("DirectionService.initializeFrom EXIT");
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

		GeoPoint nearestNode = new GeoPoint(lat, lon);		
		return nearestNode;
	}

	public PointList findPath(GeoPoint from, GeoPoint to) {
		initialize();
		
		log("findPath enter");
		try {
		
		//Routing routing = new OneToManyRouting(hopper);
		prepare(from);
		PointList list = route(to);
		
		return list;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		} finally {
			log("findPath exit");
		}
	}
}
