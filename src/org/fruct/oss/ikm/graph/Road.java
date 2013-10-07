package org.fruct.oss.ikm.graph;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.poi.PointOfInterest;

public class Road
{
	private String name = "noname";
	List<MapVertex> road;
	List<PointOfInterest> poi = new ArrayList<PointOfInterest>();
	
	Road(List<MapVertex> road) {
		this.road = road;
	}
	
	public Road() {
		road = new ArrayList<MapVertex>();
	}

	public void addPointOfInterest(PointOfInterest point) {
		poi.add(point);
	}
	
	public List<PointOfInterest> getPointsOfInterest() {
		return poi;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}