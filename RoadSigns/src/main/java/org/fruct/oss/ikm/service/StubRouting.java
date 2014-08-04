package org.fruct.oss.ikm.service;

import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;

public class StubRouting implements IRouting {
	@Override
	public void prepare(GeoPoint from) {
	}

	@Override
	public PointList route(GeoPoint to) {
		return null;
	}

	@Override
	public void route(PointDesc[] targetPoints, float radius, RoutingCallback callback) {

	}

	@Override
	public GeoPoint getNearestRoadNode(GeoPoint current) {
		return null;
	}

	@Override
	public void reset(GeoPoint userPosition) {
	}

	@Override
	public void setEncoder(String encoding) {

	}
}
