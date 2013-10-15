package org.fruct.oss.ikm.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class AdjacencyList {
	private ArrayList<ArrayList<Integer>> adjList = new ArrayList<ArrayList<Integer>>();
	
	private ArrayList<Integer> getAdjacentVertices(int idx) {
		while (idx >= adjList.size()) {
			adjList.add(new ArrayList<Integer>());
		}
		
		return adjList.get(idx);
	}
	
	public void insertPath(List<Integer> path) {
		for (int i = 0; i < path.size() - 1; i++) {
			insertEdge(path.get(i), path.get(i + 1));
			insertEdge(path.get(i + 1), path.get(i));
		}
	}
	
	public void insertEdge(int source, int dest) {
		getAdjacentVertices(source).add(dest);
	}
	
	public static AdjacencyList loadFromDatabase(SQLiteDatabase db) {
		Map<Long, Integer> idsMap = new HashMap<Long, Integer>();
		AdjacencyList graph = new AdjacencyList();
		
		long startTime = System.nanoTime();
		
		int ctr = 0;
		Cursor nodes = db.rawQuery("select * from nodes", null);
		while (nodes.moveToNext()) {
			long id = nodes.getLong(0);
			double lat = nodes.getDouble(1);
			double lon = nodes.getDouble(2);

			//graph.addNode(lat, lon);
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

					graph.insertPath(currentWay);
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

			graph.insertPath(currentWay);
		}
		
		ways.close();
		
		Log.d("qwe", "Roads loaded " + (System.nanoTime() - startTime) / 1e9);
		
		return graph;
	}

}
