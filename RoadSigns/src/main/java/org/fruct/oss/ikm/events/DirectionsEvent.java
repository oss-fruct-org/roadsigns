package org.fruct.oss.ikm.events;

import org.fruct.oss.ikm.service.Direction;
import org.osmdroid.util.GeoPoint;

import java.util.List;

public class DirectionsEvent {
	private GeoPoint centerPoint;
	private List<Direction> directions;

	public DirectionsEvent(GeoPoint centerPoint, List<Direction> directions) {
		this.centerPoint = centerPoint;
		this.directions = directions;
	}

	public GeoPoint getCenterPoint() {
		return centerPoint;
	}

	public List<Direction> getDirections() {
		return directions;
	}
}
