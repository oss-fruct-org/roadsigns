package org.fruct.oss.ikm.events;

import org.fruct.oss.ikm.points.gets.Category;

import java.util.List;

public class PointsUpdatedEvent {
	private boolean isSuccess;
	private final List<Category> categories;

	public PointsUpdatedEvent(boolean isSuccess, List<Category> categories) {
		this.isSuccess = isSuccess;
		this.categories = categories;
	}

	public boolean isSuccess() {
		return isSuccess;
	}

	public List<Category> getCategories() {
		return categories;
	}
}