package org.fruct.oss.ikm.service;

import org.osmdroid.util.GeoPoint;

import com.graphhopper.GHRequest;
import com.graphhopper.util.PointList;

public class CHRouting extends GHRouting {
	private GeoPoint from;

	public CHRouting(String filePath) {
		super(filePath);
	}

	@Override
	public void prepare(GeoPoint from) {
		this.from = from;
	}

	@Override
	public PointList route(GeoPoint to) {
		GHRequest req = new GHRequest(from.getLatitudeE6() / 1e6, from.getLongitudeE6() / 1e6,
				to.getLatitudeE6() / 1e6, to.getLatitudeE6() / 1e6);
		
		try {
			return hopper.route(req).getPoints();
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
