package org.fruct.oss.ikm.events;

import org.osmdroid.util.GeoPoint;

public class TargetPointEvent {
	private GeoPoint geoPoint;

	public TargetPointEvent(GeoPoint geoPoint) {
		this.geoPoint = geoPoint;
	}

	public GeoPoint getGeoPoint() {
		return geoPoint;
	}
}
