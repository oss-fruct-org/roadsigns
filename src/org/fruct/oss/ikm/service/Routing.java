package org.fruct.oss.ikm.service;

import org.osmdroid.util.GeoPoint;

import com.graphhopper.util.PointList;

public abstract class Routing {
	private GeoPoint oldGeoPoint = null;
	
	protected abstract void prepare(GeoPoint from);
	public abstract PointList route(GeoPoint to);
	
	public void reset(GeoPoint from) {
		if (!from.equals(oldGeoPoint))
			prepare(from);
	}
}
