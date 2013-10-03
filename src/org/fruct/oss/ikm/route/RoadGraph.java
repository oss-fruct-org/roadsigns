package org.fruct.oss.ikm.route;

import java.util.ArrayList;
import java.util.List;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

public class RoadGraph {
	protected GeoPoint[] nodes;
	protected int[][] roads;

	public List<GeoPoint> getRoad(int id) {
		ArrayList<GeoPoint> ret = new ArrayList<GeoPoint>();

		for (int nodeId : roads[id]) {
			ret.add(nodes[nodeId]);
		}

		return ret;
	}
	
	public int distanceToSegment(IGeoPoint P, GeoPoint A, GeoPoint B) {
		int c = A.distanceTo(B);
		
		int a = B.distanceTo(P);
		int b = A.distanceTo(P);
		int p = (c + a + b) / 2;
		int h = (int) (2 * Math.sqrt(p * (p - a) * (p - b) * (p - c)) / c);
		
		return Math.min(Math.min(a, b), h);
	}
	
	public int distanceToRoad(IGeoPoint p, int id) {
		int min = 40075000;
		
		for (int i = 0; i < roads[id].length - 1; i++) {
			GeoPoint a = nodes[roads[id][i]];
			GeoPoint b = nodes[roads[id][i + 1]];
			
			int distance = distanceToSegment(p, a, b);
			if (distance < min) {
				min = distance;
			}
		}
		
		return min;
	}
}
