package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.Utils;

public class PointsManager {
	private boolean needUpdate = true;

	private String categoryFilter = "";
	private List<PointDesc> points;
	private List<PointDesc> filteredPoints = new ArrayList<PointDesc>();

	private PointsManager(PointLoader loader) {
		points = loader.getPoints();
	}
	
	public List<PointDesc> getPoints() {
		ensureValid(); 
		return filteredPoints;
	}
	
	public List<PointDesc> filterPoints(List<PointDesc> list) {
		ArrayList<PointDesc> ret = new ArrayList<PointDesc>();
		filterPoints(list, ret);
		return ret;
	}
	
	private void filterPoints(List<PointDesc> in, List<PointDesc> out) {
		out.clear();
		Utils.select(in, out, new Utils.Predicate<PointDesc>() {
			public boolean apply(PointDesc point) {
				return categoryFilter == null 
						|| categoryFilter.length() == 0 
						|| categoryFilter.equals(point.getCategory());
			};
		});
	}

	public void setFilter(String categoryFilter) {
		this.categoryFilter = categoryFilter;
		needUpdate = true;
	}
	
	public String getFilter() {
		return categoryFilter;
	}

	public static PointsManager getInstance() {
		return Holder.instance;
	}

	private void ensureValid() {
		if (needUpdate) {
			needUpdate = true;
			filterPoints(points, filteredPoints);

		}
	}

	private static class Holder {
		private static final PointsManager instance = new PointsManager(new StubPointLoader());
	}


}
