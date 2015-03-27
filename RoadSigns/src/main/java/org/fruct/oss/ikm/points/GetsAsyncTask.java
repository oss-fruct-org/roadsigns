package org.fruct.oss.ikm.points;

import android.os.AsyncTask;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.points.gets.Category;
import org.fruct.oss.ikm.points.gets.Gets;
import org.fruct.oss.ikm.points.gets.GetsException;
import org.osmdroid.util.GeoPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GetsAsyncTask extends AsyncTask<GetsAsyncTask.Params, Integer, GetsAsyncTask.Result> {
	private final String server;
	private final Gets gets;
	private final PointsAccess pointsAccess;

	public GetsAsyncTask(String server) {
		this.server = server;
		this.gets = new Gets(server);
		this.pointsAccess = App.getInstance().getPointsAccess();
	}

	@Override
	protected Result doInBackground(Params... params) {
		Params param = params[0];

		GeoPoint geoPoint = new GeoPoint(param.lat, param.lon);
		double radius = param.radius;

		List<Point> allLoadedPoints = new ArrayList<>();

		try {
			List<Category> categories = gets.getCategories();
			pointsAccess.insertCategories(categories);

			for (int i = 0; i < categories.size(); i++) {
				Category category = categories.get(i);
				List<Point> points = gets.getPoints(category, geoPoint, (int) radius);

				pointsAccess.insertPoints(points);
				allLoadedPoints.addAll(points);

				publishProgress(i, categories.size());
			}

			Result result = new Result();
			result.categories = categories;
			result.points = allLoadedPoints;
			return result;
		} catch (IOException e) {
			// TODO: process error
		} catch (GetsException e) {
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

		public List<Category> getCategories() {
			return categories;
		}

		public List<Point> getPoints() {
			return points;
		}
	}
}
