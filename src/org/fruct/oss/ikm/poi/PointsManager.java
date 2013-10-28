package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.Utils;

import android.util.Log;

public class PointsManager {
	private boolean needUpdate = true;

	private String categoryFilter = "";
	private List<PointDesc> points;
	private List<PointDesc> filteredPoints = new ArrayList<PointDesc>();

	private PointsManager(PointLoader loader) {
		Log.d("roadsigns", "PointsManager");
		points = loader.getPoints();
	}
	
	public List<PointDesc> getFilteredPoints() {
		ensureValid(); 
		return filteredPoints;
	}
	
	public List<PointDesc> getAllPoints() {
		return points;
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

	public synchronized static PointsManager getInstance(PointLoader loader) {
		if (instance == null) {
			instance = new PointsManager(loader);
		}
		
		return instance;
	}
	
	public synchronized static PointsManager getInstance() {
		if (instance == null) {
			instance = new PointsManager(new StubPointLoader());
		}
		
		return instance;
	}
	
	/**
	 * For unit testing
	 */
	public static void resetInstance() {
		instance = null;
	}

	private void ensureValid() {
		if (needUpdate) {
			needUpdate = true;
			filterPoints(points, filteredPoints);

		}
	}

	private static volatile PointsManager instance;
}
