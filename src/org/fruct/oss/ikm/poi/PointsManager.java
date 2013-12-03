package org.fruct.oss.ikm.poi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.Utils;

import android.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PointsManager {
	private static Logger log = LoggerFactory.getLogger(PointsManager.class);

	public interface PointsListener {
		void filterStateChanged(List<PointDesc> newList, List<PointDesc> added,
				List<PointDesc> removed);
	}
	
	private boolean needUpdate = true;

	private List<PointDesc> points;
	private List<PointDesc> filteredPoints = new ArrayList<PointDesc>();
	
	private List<Filter> filters = new ArrayList<Filter>();	
	
	private List<PointsListener> listeners = new ArrayList<PointsManager.PointsListener>();
	
	private PointsManager(PointLoader loader) {
		points = Collections.unmodifiableList(loader.getPoints());
		createFiltersFromPoints(points);
		log.info("{} points total loaded", points.size());

		for (PointDesc point : points) {
			log.trace(point.toString());
		}
	}

	private void createFiltersFromPoints(List<PointDesc> points) {
		Set<String> names = new HashSet<String>();

		for (PointDesc point : points)
			names.add(point.getCategory());

		for (String str : names) {
			CategoryFilter filter = new CategoryFilter(str, str);
			filters.add(filter);
		}
	}
	
	public void addListener(PointsListener listener) {
		listeners.add(listener);
	}
	
	public void removeListener(PointsListener listener) {
		listeners.remove(listener);
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
		return Collections.unmodifiableList(ret);
	}
	
	private void filterPoints(List<PointDesc> in, List<PointDesc> out) {
		out.clear();
		Utils.select(in, out, new Utils.Predicate<PointDesc>() {
			public boolean apply(PointDesc point) {
				for (Filter filter : filters) {
					if (filter.isActive() && filter.accepts(point))
						return true;
				}
				
				return false;
			}
		});
	}

	public List<Filter> getFilters() {
		return Collections.unmodifiableList(filters);
	}
	
	public void notifyFiltersUpdated() {
		List<PointDesc> oldFilteredPoints = filteredPoints;
		List<PointDesc> filteredPoints = new ArrayList<PointDesc>();
		filterPoints(points, filteredPoints);
		
		List<PointDesc> addedPoints = new ArrayList<PointDesc>(filteredPoints);
		List<PointDesc> removedPoints = new ArrayList<PointDesc>(oldFilteredPoints);
		
		addedPoints.removeAll(oldFilteredPoints);
		removedPoints.removeAll(filteredPoints);
		
		this.filteredPoints = filteredPoints;
		
		for (PointsListener listener : listeners) {
			listener.filterStateChanged(filteredPoints, addedPoints, removedPoints);
		}
	}
	
	private void ensureValid() {
		if (needUpdate) {
			needUpdate = false;
			ArrayList<PointDesc> newArray = new ArrayList<PointDesc>();
			filterPoints(points, newArray);
			filteredPoints = Collections.unmodifiableList(newArray);
		}
	}
	

	public synchronized static PointsManager getInstance(PointLoader loader) {
		if (instance == null) {
			instance = new PointsManager(loader);
		}
		
		return instance;
	}

	public synchronized static PointsManager getInstance() {
		if (instance == null) {
			if (false)
				return instance = new PointsManager(new StubPointLoader());

			log.debug("Loading points from assets");
			MultiPointLoader multiLoader = new MultiPointLoader();

			for (int i = 1; i < 14; i++) {
				String filename = String.format("karelia-poi/%02d.js", i);

				JSONPointLoader jsonLoader;
				try {
					jsonLoader = JSONPointLoader.createForAsset(filename);
				} catch (IOException ex) {
					ex.printStackTrace();
					log.warn("Can not load POI from file {}", filename);
					continue;
				}
				multiLoader.addLoader(jsonLoader);
			}

			instance = new PointsManager(multiLoader);
		}
		return instance;
	}
	
	/**
	 * For unit testing
	 */
	public static void resetInstance() {
		instance = null;
	}

	private static volatile PointsManager instance;
}
