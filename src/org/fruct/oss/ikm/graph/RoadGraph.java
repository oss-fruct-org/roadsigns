package org.fruct.oss.ikm.graph;

import static java.lang.Math.sqrt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fruct.oss.ikm.poi.PointOfInterest;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

import android.util.Log;

public class RoadGraph extends Graph {
	private List<Road> roads = new ArrayList<Road>();
	private List<PointOfInterest> poi = new ArrayList<PointOfInterest>();
	private List<MapVertex> crossroads = new ArrayList<MapVertex>();
	
	public Road getRoad(int id) {
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
		
		return h;
	}
	
	public int distanceToRoad(IGeoPoint p, int id) {
		return distanceToRoad(p, roads.get(id));
	}
	
	public int distanceToRoad(IGeoPoint p, Road r) {
		int min = 40075000;
		
		List<MapVertex> road = r.road;
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
	
	public Road nearestRoad(IGeoPoint p, int[] dist_out) {
		Road ret = null;
		int min = 0;
		
		for (Road road : roads) {
			int d = distanceToRoad(p, road);

			if (d < min || ret == null) {
				ret = road;
				min = d;
			}
		}
		
		if (dist_out != null && dist_out.length > 0)
			dist_out[0] = min;
		
		return ret;
	}
	
	public MapVertex nearestCrossroad(IGeoPoint p, int[] dist_out) {
		MapVertex ret = null;
		int min = 0;
		
		for (MapVertex mv : crossroads) {
			int d = mv.node.distanceTo(p);
			
			if (d < min || ret == null) {
				ret = mv;
				min = d;
			}
		}
		
		if (dist_out != null && dist_out.length > 0) {
			dist_out[0] = min;
		}
		
		return ret;
	}
	
	public Road addRoad(int... nodes) {
		List<MapVertex> road = new ArrayList<MapVertex>();
		Road ret = new Road(road);
		
		for (int i = 0; i < nodes.length - 1; i++) {
			int n1 = nodes[i];
			int n2 = nodes[i + 1];
			
			MapVertex v1 = (MapVertex) vertices.get(n1);
			MapVertex v2 = (MapVertex) vertices.get(n2);
			addEdge(v1, v2, v1.node.distanceTo(v2.node));

			road.add(v1);
			v1.addRoad(ret);
		}
		
		MapVertex lastVertex = (MapVertex) vertices.get(nodes[nodes.length - 1]);
		road.add(lastVertex);
		lastVertex.addRoad(ret);
		roads.add(ret);
		return ret;
	}

	private void initialize() {
		for (Vertex v : vertices) {
			MapVertex mv = (MapVertex) v;
			if (mv.getRoads().size() > 1)
				crossroads.add(mv);
		}
	}
	
	public void addNode(double lat, double lon) {
		addVertex(new MapVertex(new GeoPoint(lat, lon)));
	}
	
	public void addPointOfInterest(PointOfInterest point) {
		poi.add(point);
		Road road = nearestRoad(point.toPoint(), null);
		point.setRoad(road);
		road.addPointOfInterest(point);
	}
	
	public Road roadBetweenVertex(MapVertex v1, MapVertex v2) {
		Set<Road> sv1 = new HashSet<Road>(v1.getRoads());
		Set<Road> sv2 = new HashSet<Road>(v2.getRoads());
		sv1.retainAll(sv2);
		return sv1.iterator().next();
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
		
		graph.addRoad(new int[] {0, 1, 3}).setName("Lenina"); // Lenina
		graph.addRoad(new int[] {1, 2}).setName("Kirova"); // Kirova
		graph.addRoad(new int[] {3, 4}).setName("Shotmana"); // Shotmana
		graph.addRoad(new int[] {4, 5, 6}).setName("Chapaeva"); // Chapaeva
		
		graph.initialize();
		
		return graph;
	}
}
