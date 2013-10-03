package org.fruct.oss.ikm.route;

import java.util.ArrayList;

import org.osmdroid.util.GeoPoint;


class RoadGraphHelper extends RoadGraph {
	public RoadGraphHelper(GeoPoint[] nodes, int[][] roads) {
		this.nodes = nodes;
		this.roads = roads;
	}
}

public class RoadGraphBuilder {
	private ArrayList<GeoPoint> nodes = new ArrayList<GeoPoint>();
	private ArrayList<int[]> roads = new ArrayList<int[]>();
	
	public void addNode(GeoPoint node) {
		nodes.add(node);
	}
	
	public void addNode(double lat, double lon) {
		addNode(new GeoPoint(lat, lon));
	}
	
	public boolean addRoad(int[] waypoints) {
		for (int wp : waypoints) {
			if (wp >= nodes.size())
				return false;
		}
		roads.add(waypoints);
		return true;
	}
	
	public RoadGraph toRoadGraph() {
		GeoPoint[] nodesArr = new GeoPoint[nodes.size()];
		int[][] roadsArr = new int[roads.size()][];
		
		nodes.toArray(nodesArr);
		roads.toArray(roadsArr);
		
		return new RoadGraphHelper(nodesArr, roadsArr);
	}
	
	public static RoadGraph createSampleGraph() {
		RoadGraphBuilder builder = new RoadGraphBuilder();
	
		builder.addNode(61.794407,34.376839); // Lenina a 			0
		builder.addNode(61.792085,34.369522); // Lenina/Kirova 		1
		builder.addNode(61.797784,34.362291); // Kirova a			2
		builder.addNode(61.784974,34.346541); // Lenina/Shotmana	3
		builder.addNode(61.789072,34.341456); // Shotmana/Chapaeva	4
		builder.addNode(61.778562,34.30766);// Chapaeva koltso		5
		builder.addNode(61.772209,34.287146); // Chapaeva a			6
		
		builder.addRoad(new int[] {0, 1, 3}); // Lenina
		builder.addRoad(new int[] {1, 2}); // Kirova
		builder.addRoad(new int[] {3, 4}); // Shotmana
		builder.addRoad(new int[] {4, 5, 6}); // Chapaeva
		
		return builder.toRoadGraph();
	}
}
