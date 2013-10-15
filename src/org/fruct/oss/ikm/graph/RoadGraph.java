package org.fruct.oss.ikm.graph;

import static java.lang.Math.sqrt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fruct.oss.ikm.poi.PointOfInterest;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class RoadGraph extends Graph {
	private List<Road> roads = new ArrayList<Road>();
	private List<PointOfInterest> poi = new ArrayList<PointOfInterest>();
	private List<MapVertex> crossroads = new ArrayList<MapVertex>();
	
	public Road getRoad(int id) {
		return roads.get(id);
	}
	
	// XXX: may be overflow
	public static int distanceToSegment(IGeoPoint P, GeoPoint A, GeoPoint B) {
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
	
	public static int distanceToRoad(IGeoPoint p, Road r) {
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
		
		Road road = nearestRoad(point.getDesc().toPoint(), null);
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
		graph.addNode(61.778562,34.30766); // Chapaeva koltso		5
		graph.addNode(61.772209,34.287146); // Chapaeva a			6
		
		graph.addNode(61.789662,34.351131); // Antic a 				7
		graph.addNode(61.787366,34.354371); // Antic/Lenina			8
		graph.addNode(61.780579,34.363920); // Antic b				9
		
		graph.addRoad(new int[] {0, 1, 8, 3}).setName("Lenina"); // Lenina
		graph.addRoad(new int[] {1, 2}).setName("Kirova"); // Kirova
		graph.addRoad(new int[] {3, 4}).setName("Shotmana"); // Shotmana
		graph.addRoad(new int[] {4, 5, 6}).setName("Chapaeva"); // Chapaeva
		graph.addRoad(new int[] {7, 8, 9}).setName("Anticainena");
		
		graph.initialize();
		
		return graph;
	}
	
	public static RoadGraph loadFromDatabase(SQLiteDatabase db) {
		Map<Long, Integer> idsMap = new HashMap<Long, Integer>();
		RoadGraph graph = new RoadGraph();
		
		long startTime = System.nanoTime();
		
		int ctr = 0;
		Cursor nodes = db.rawQuery("select * from nodes", null);
		while (nodes.moveToNext()) {
			long id = nodes.getLong(0);
			double lat = nodes.getDouble(1);
			double lon = nodes.getDouble(2);

			graph.addNode(lat, lon);
			idsMap.put(id, ctr++);
		}
		nodes.close();
		
		Log.d("qwe", "Nodes loaded " + (System.nanoTime() - startTime) / 1e9);

		String currentWayName = "";
		List<Integer> currentWay = new ArrayList<Integer>();
		long currentWayId = -1;
		
		Cursor ways = db.rawQuery("select wayId,nodeId,name"
									+ " from nodeways inner join ways"
									+ " on nodeways.wayId = ways.id"
									+ " order by wayId,nodeways.rowId", null);
		while (ways.moveToNext()) {
			long wayId;
			long realNodeId = -1;
			int nodeId;
			String name;
			try {
				wayId = ways.getLong(0);
				realNodeId = ways.getLong(1);
				nodeId = idsMap.get(realNodeId);
				name = ways.getString(2);
			} catch (Exception ex) {
				Log.d("qwe", "Way contains node that not in node list: " + realNodeId);
				continue;
			}
			
			// TODO: remove duplicated code
			if (currentWayId != wayId) {
				if (currentWayId != -1) {
					int c = 0;
					int[] arr = new int[currentWay.size()];
					
					for (int id : currentWay)
						arr[c++] = id;

					graph.addRoad(arr).setName(currentWayName);
				}
				
				currentWayName = name;
				currentWayId = wayId;
				currentWay.clear();
			}
			
			currentWay.add(nodeId);
		}
		
		if (currentWayId != -1) {
			int c = 0;
			int[] arr = new int[currentWay.size()];
			
			for (int id : currentWay)
				arr[c++] = id;

			graph.addRoad(arr).setName(currentWayName);
		}
		
		ways.close();
		
		Log.d("qwe", "Roads loaded " + (System.nanoTime() - startTime) / 1e9);
		
		graph.initialize();
		return graph;
	}
}
