package org.fruct.oss.ikm.service;

import android.location.Location;

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

	@Override
	public IMapMatcher createMapMatcher() {
		return new IMapMatcher() {
			public Location location;

			@Override
			public void updateLocation(Location location) {
				this.location = location;
			}

			@Override
			public Location getMatchedLocation() {
				return location;
			}
		};
	}
}
