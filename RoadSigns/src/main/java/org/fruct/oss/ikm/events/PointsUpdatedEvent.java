package org.fruct.oss.ikm.events;

import org.fruct.oss.ikm.points.gets.Category;

import java.util.List;

public class PointsUpdatedEvent {
	private final boolean isSuccess;

	private final List<Category> categories;
	private final String message;

	public PointsUpdatedEvent(boolean isSuccess, List<Category> categories, String message) {
		this.isSuccess = isSuccess;
		this.categories = categories;
		this.message = message;
	}

	public boolean isSuccess() {
		return isSuccess;
	}

	public List<Category> getCategories() {
		return categories;
	}

	public String getMessage() {
		return message;
	}
}