package org.fruct.oss.ikm.service;

import com.graphhopper.util.PointList;

import org.osmdroid.util.GeoPoint;

public interface IRouting {
	void prepare(GeoPoint from);
	PointList route(GeoPoint to);
	GeoPoint getNearestRoadNode(GeoPoint current);
	void reset(GeoPoint userPosition);
	void setEncoder(String encoding);
}
