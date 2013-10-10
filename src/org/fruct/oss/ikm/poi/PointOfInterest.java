package org.fruct.oss.ikm.poi;

import org.fruct.oss.ikm.graph.MapVertex;
import org.fruct.oss.ikm.graph.Road;

public class PointOfInterest {	
	private Road road;
	private MapVertex roadVertex;
	private PointDesc desc;
	
	public PointOfInterest(PointDesc desc) {
		this.desc = desc;
	}
	
	public PointDesc getDesc() {
		return desc;
	}

	public void setRoad(Road road) {
		this.road = road;
		
		int min = 0;
		MapVertex minMv = null;
		for (MapVertex mv : road.getPath()) {
			int dist = mv.getNode().distanceTo(desc.toPoint());
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
