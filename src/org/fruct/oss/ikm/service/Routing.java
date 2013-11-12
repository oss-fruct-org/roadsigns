package org.fruct.oss.ikm.service;

import org.osmdroid.util.GeoPoint;

import com.graphhopper.routing.Path;
import com.graphhopper.util.PointList;

public interface Routing {
	void prepare(GeoPoint from);
	PointList route(GeoPoint to);
}
