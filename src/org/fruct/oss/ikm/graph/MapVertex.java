package org.fruct.oss.ikm.graph;

import java.util.ArrayList;
import java.util.List;

import org.osmdroid.util.GeoPoint;

public class MapVertex extends Vertex {
	GeoPoint node;
	private List<Road> roads = new ArrayList<Road>();

	public MapVertex(GeoPoint node) {
		this.node = node;
	}
	
	public void addRoad(Road road) {
		roads.add(road);
	}
	
	public List<Road> getRoads() {
		return roads;
	}
}