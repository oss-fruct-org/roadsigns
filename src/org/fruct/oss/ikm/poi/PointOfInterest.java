package org.fruct.oss.ikm.poi;

import org.fruct.oss.ikm.graph.MapVertex;
import org.fruct.oss.ikm.graph.Road;
import org.osmdroid.util.GeoPoint;

public class PointOfInterest {
	private int latE6, lonE6;
	private String name;
	private GeoPoint geoPoint;
	
	private Road road;
	private MapVertex roadVertex;
	
	public PointOfInterest(String name, int latE6, int lonE6) {
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

	public void setRoad(Road road) {
		this.road = road;
		
		int min = 0;
		MapVertex minMv = null;
		for (MapVertex mv : road.getPath()) {
			int dist = mv.getNode().distanceTo(geoPoint);
			if (dist < min || minMv == null) {
				minMv = mv;
				min = dist;
			}
		}
		
		roadVertex = minMv;
	}
	
	public MapVertex getRoadVertex() {
		return roadVertex;
	}

	public Road getRoad() {
		return road;
	}
}
