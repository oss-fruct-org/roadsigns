package org.fruct.oss.ikm.graph;

import static java.lang.Math.sqrt;

import java.util.ArrayList;
import java.util.List;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

import android.util.Log;

class MapVertex extends Vertex {
	GeoPoint node;

	public MapVertex(GeoPoint node) {
		this.node = node;
	}

}

public class RoadGraph extends Graph {
	private List<List<MapVertex>> roads = new ArrayList<List<MapVertex>>();
	
	public List<MapVertex> getRoad(int id) {
		return roads.get(id);
	}
	
	// XXX: may be overflow
	public int distanceToSegment(IGeoPoint P, GeoPoint A, GeoPoint B) {
		int c = A.distanceTo(B);		
		int a = B.distanceTo(P);
		int b = A.distanceTo(P);
		
		if (c * c < a * a + b * b)
			return Math.min(a, b);
		
		int p = (c + a + b) / 2;
		int h = (int) (2 * sqrt(p) * sqrt(p - a) * sqrt(p - b) * sqrt(p - c) / c);
		
		Log.d("qwe", String.format("Triangle %d %d %d %d %d", a, b, c, h, p));
									
		return h;
	}
	
	public int distanceToRoad(IGeoPoint p, int id) {
		int min = 40075000;

		List<MapVertex> road = roads.get(id);
		for (int i = 0; i < road.size() - 1; i++) {
			GeoPoint a = road.get(i).node;
			GeoPoint b = road.get(i + 1).node;
			
			int distance = distanceToSegment(p, a, b);
			if (distance < min) {
				min = distance;
			}
		}
		
		return min;
	}
	
	public void addRoad(int... nodes) {
		List<MapVertex> road = new ArrayList<MapVertex>();
		
		for (int i = 0; i < nodes.length - 1; i++) {
			int n1 = nodes[i];
			int n2 = nodes[i + 1];
			
			MapVertex v1 = (MapVertex) vertices.get(n1);
			MapVertex v2 = (MapVertex) vertices.get(n2);
			addEdge(v1, v2, v1.node.distanceTo(v2.node));
			road.add(v1);
		}
		
		road.add((MapVertex) vertices.get(vertices.size() - 1));
		roads.add(road);
	}
	
	public void addNode(double lat, double lon) {
		addVertex(new MapVertex(new GeoPoint(lat, lon)));
	}
	
	public static RoadGraph createSampleGraph() {
		RoadGraph graph = new RoadGraph();
		
		graph.addNode(61.794407,34.376839); // Lenina a 			0
		graph.addNode(61.792085,34.369522); // Lenina/Kirova 		1
		graph.addNode(61.797784,34.362291); // Kirova a			2
		graph.addNode(61.784974,34.346541); // Lenina/Shotmana	3
		graph.addNode(61.789072,34.341456); // Shotmana/Chapaeva	4
		graph.addNode(61.778562,34.30766);// Chapaeva koltso		5
		graph.addNode(61.772209,34.287146); // Chapaeva a			6
		
		graph.addRoad(new int[] {0, 1, 3}); // Lenina
		graph.addRoad(new int[] {1, 2}); // Kirova
		graph.addRoad(new int[] {3, 4}); // Shotmana
		graph.addRoad(new int[] {4, 5, 6}); // Chapaeva
		
		return graph;
	}
}
