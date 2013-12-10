package org.fruct.oss.ikm.poi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.res.AssetManager;
import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.Utils;

import android.util.Log;

import org.fruct.oss.ikm.poi.gets.Gets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PointsManager {
	private static Logger log = LoggerFactory.getLogger(PointsManager.class);

	public interface PointsListener {
		void filterStateChanged(List<PointDesc> newList, List<PointDesc> added,
				List<PointDesc> removed);
	}

	private boolean needUpdate = true;

	private List<PointLoader> loaders = new ArrayList<PointLoader>();
	private ExecutorService executor = Executors.newFixedThreadPool(1);

	/**
	 * All points fetched from loaders
	 */
	private List<PointDesc> points = new ArrayList<PointDesc>();

	/**
	 * Points that accepted by at least one filter
	 */
	private List<PointDesc> filteredPoints = new ArrayList<PointDesc>();

	private List<Filter> filters = new ArrayList<Filter>();

	private List<PointsListener> listeners = new ArrayList<PointsManager.PointsListener>();

	public void addPointLoader(final PointLoader pointLoader) {
        log.trace("addPointLoader");
		loaders.add(pointLoader);

		// Schedule loader
		executor.execute(new Runnable() {
			@Override
			public void run() {
				pointLoader.loadPoints();

				// When previous method returns, points guaranteed to be ready
				List<PointDesc> newPoints = pointLoader.getPoints();
				addPoints(newPoints);
			}
		});
	}

	private synchronized void addPoints(List<PointDesc> points) {
        log.trace("Adding new points");
        this.points.addAll(points);
        needUpdate = true;

        createFiltersFromPoints(this.points);
        notifyFiltersUpdated();
    }

	private void createFiltersFromPoints(List<PointDesc> points) {
        log.trace("Recreating filters");

        Set<String> names = new HashSet<String>();

        filters.clear();
		for (PointDesc point : points)
			names.add(point.getCategory());

        for (String str : names) {
            log.trace("Filter for category {}", str);
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

	public synchronized List<PointDesc> getFilteredPoints() {
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

	public synchronized void notifyFiltersUpdated() {
		List<PointDesc> oldFilteredPoints = filteredPoints;
		List<PointDesc> filteredPoints = new ArrayList<PointDesc>();
		filterPoints(points, filteredPoints);

		List<PointDesc> addedPoints = new ArrayList<PointDesc>(filteredPoints);
		List<PointDesc> removedPoints = new ArrayList<PointDesc>(oldFilteredPoints);

		addedPoints.removeAll(oldFilteredPoints);
		removedPoints.removeAll(filteredPoints);

		this.filteredPoints = Collections.unmodifiableList(filteredPoints);
		needUpdate = false;

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

	public synchronized static PointsManager getInstance() {
		if (instance == null) {
			instance = new PointsManager();

			if (false) {
				instance.addPointLoader(new StubPointLoader());
				return instance;
			}

			log.debug("Loading points from assets");

			String[] jsonFiles;
			try {
				AssetManager am = App.getContext().getAssets();
				jsonFiles = am.list("karelia-poi");
			} catch (IOException e) {
				e.printStackTrace();
				log.warn("Can not open list of .json files");
				instance.addPointLoader(new StubPointLoader());
				return instance;
			}

			for (String filename : jsonFiles) {
				JSONPointLoader jsonLoader;
				try {
					jsonLoader = JSONPointLoader.createForAsset("karelia-poi/" + filename);
				} catch (IOException ex) {
					ex.printStackTrace();
					log.warn("Can not load POI from file {}", "karelia-poi/" + filename);
					continue;
				}
				instance.addPointLoader(jsonLoader);
			}

			// Load gets


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
