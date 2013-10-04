package org.fruct.oss.ikm.poi;

import org.osmdroid.util.GeoPoint;

public class PointOfInterest {
	private int latE6, lonE6;
	private String name;
	
	public PointOfInterest(String name, int latE6, int lonE6) {
		this.name = name;
		this.latE6 = latE6;
		this.lonE6 = lonE6;
	}
	
	public String getName() {
		return name;
	}
	
	public GeoPoint toPoint() {
		return new GeoPoint(latE6, lonE6);
	}
}
