package org.fruct.oss.ikm.events;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;

public class PathEvent {
	private final ArrayList<GeoPoint> pathArray;

	public PathEvent(ArrayList<GeoPoint> pathArray) {
		this.pathArray = pathArray;
	}

	public ArrayList<GeoPoint> getPathArray() {
		return pathArray;
	}
}
