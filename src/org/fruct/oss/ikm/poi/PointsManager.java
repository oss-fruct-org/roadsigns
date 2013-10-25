package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.Utils;

public class PointsManager {
	private boolean needUpdate = true;

	private String categoryFilter = "";
	private List<PointDesc> points;
	private List<PointDesc> filteredPoints = new ArrayList<PointDesc>();

	private PointsManager() {
		points = new StubPointLoader().getPoints();
	}
	
	public List<PointDesc> getPoints() {
		ensureValid(); 
		return filteredPoints;
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
			
			filteredPoints.clear();
			Utils.select(points, filteredPoints, new Utils.Predicate<PointDesc>() {
				public boolean apply(PointDesc point) {
					return categoryFilter == null 
							|| categoryFilter.length() == 0 
							|| categoryFilter.equals(point.getCategory());
				};
			});
		}
	}

	private static class Holder {
		private static final PointsManager instance = new PointsManager();
	}


}
