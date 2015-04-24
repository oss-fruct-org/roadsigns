package org.fruct.oss.ikm.events;

import org.fruct.oss.ikm.points.gets.Category;

import java.util.List;

public class PointsUpdatedEvent {
	private boolean isSuccess;

	public PointsUpdatedEvent(boolean isSuccess) {
		this.isSuccess = isSuccess;
	}

	public boolean isSuccess() {
		return isSuccess;
	}
}