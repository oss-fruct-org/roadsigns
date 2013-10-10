package org.fruct.oss.ikm.poi;

import java.io.Serializable;

import org.osmdroid.util.GeoPoint;

public class PointDesc implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private int latE6, lonE6;
	private String name;
	
	private GeoPoint geoPoint;
	
	public PointDesc(String name, int latE6, int lonE6) {
		this.name = name;
		this.latE6 = latE6;
		this.lonE6 = lonE6;
	}
	
	public String getName() {
		return name;
	}
	
	public GeoPoint toPoint() {
		return geoPoint == null ? geoPoint = new GeoPoint(latE6, lonE6)
								: geoPoint;
	}
}
