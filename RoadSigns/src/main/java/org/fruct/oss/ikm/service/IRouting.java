package org.fruct.oss.ikm.service;

import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;

public interface IRouting {
	public interface RoutingCallback {
		void pointReady(GeoPoint center, GeoPoint target, PointDesc pointDesc);
	}

	void prepare(GeoPoint from);

	PointList route(GeoPoint to);
	void route(PointDesc[] targetPoints, float radius, RoutingCallback callback);

	GeoPoint getNearestRoadNode(GeoPoint current);
	void reset(GeoPoint userPosition);
	void setEncoder(String encoding);
}
