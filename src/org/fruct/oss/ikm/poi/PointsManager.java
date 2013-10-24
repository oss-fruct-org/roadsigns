package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;

public class PointsManager {
	private boolean needUpdate = true;

	private String categoryFilter;
	private ArrayList<PointDesc> points;

	public List<PointDesc> getPoints() {
		return points;
	}

	public void setFilter(String categoryFilter) {
		this.categoryFilter = categoryFilter;
		needUpdate = true;
	}

	public static PointsManager getInstance() {
		return Holder.instance;
	}

	private void ensureValid() {
		if (needUpdate) {
			needUpdate = true;
		}
	}

	private static class Holder {
		private static final PointsManager instance = new PointsManager();
	}

	private PointsManager() {
	}
}
