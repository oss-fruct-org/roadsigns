package org.fruct.oss.ikm.points;

import android.os.AsyncTask;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.points.gets.Category;
import org.fruct.oss.ikm.points.gets.Gets;
import org.fruct.oss.ikm.points.gets.GetsException;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GetsAsyncTask extends AsyncTask<GetsAsyncTask.Params, Integer, GetsAsyncTask.Result> {
	private static final Logger log = LoggerFactory.getLogger(GetsAsyncTask.class);

	private final String server;
	private final boolean skipCategory;
	private final Gets gets;
	private final PointsAccess pointsAccess;

	protected Exception exception;

	public GetsAsyncTask(String server, boolean skipCategory) {
		this.server = server;
		this.skipCategory = skipCategory;
		this.gets = new Gets(server);
		this.pointsAccess = App.getInstance().getPointsAccess();
	}

	@Override
	protected Result doInBackground(Params... params) {
		Params param = params[0];
		log.info("Starting points refresh for {}:{}, radius {} categories {}",
				param.lat, param.lon, param.radius, skipCategory);

		GeoPoint geoPoint = new GeoPoint(param.lat, param.lon);
		double radius = param.radius;

		List<Point> allLoadedPoints = new ArrayList<>();

		try {
			List<Category> categories;
			if (!skipCategory) {
				categories = gets.getCategories();
				pointsAccess.insertCategories(categories);
			} else {
				categories = pointsAccess.loadActiveCategories();
			}

			for (int i = 0; i < categories.size(); i++) {
				if (isCancelled())
					return null;

				Category category = categories.get(i);
				List<Point> points = gets.getPoints(category, geoPoint, (int) radius);

				pointsAccess.insertPoints(points);
				allLoadedPoints.addAll(points);

				publishProgress(i, categories.size());
			}

			Result result = new Result();
			result.categories = categories;
			result.points = allLoadedPoints;
			result.lat = param.lat;
			result.lon = param.lon;

			log.info("Finished points refresh");

			return result;
		} catch (IOException e) {
			exception = e;
			log.error("IO error while refreshing points", e);
		} catch (GetsException e) {
			exception = e;
			log.error("Server error while refreshing points", e);
		}

		return null;
	}

	public void execute(double lat, double lon, double radius) {
		execute(new Params(lat, lon, radius));
	}

	public static class Params {
		private double lat;
		private double lon;
		private double radius;

		public Params(double lat, double lon, double radius) {
			this.lat = lat;
			this.lon = lon;
			this.radius = radius;
		}
	}

	public static class Result {
		private List<Category> categories;
		private List<Point> points;

		private double lat, lon;

		public List<Category> getCategories() {
			return categories;
		}

		public List<Point> getPoints() {
			return points;
		}

		public double getLat() {
			return lat;
		}

		public double getLon() {
			return lon;
		}
	}
}
