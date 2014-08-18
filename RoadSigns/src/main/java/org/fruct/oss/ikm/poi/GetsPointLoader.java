package org.fruct.oss.ikm.poi;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.poi.gets.CategoriesList;
import org.fruct.oss.ikm.poi.gets.Gets;
import org.fruct.oss.ikm.poi.gets.LoginException;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GetsPointLoader extends PointLoader {
	private static Logger log = LoggerFactory.getLogger(GetsPointLoader.class);

	public static final String PREF_LAST_LAT = "gets-loader-latE6";
	public static final String PREF_LAST_LON = "gets-loader-lonE6";
	public static final String PREF_LAST_TIME = "gets-loader-time";

	public static final int POINT_UPDATE_DISTANCE = 5000;
	public static final long POINT_UPDATE_TIME = 3600 * 24;

	private final SharedPreferences pref;

	private Gets gets;

	private GeoPoint lastPosition;
	private GeoPoint currentPosition;
	private long lastTime;

	private int radius = 5000;

	private boolean needUpdate = false;

	public GetsPointLoader(String url) {
		gets = new Gets(url);

		pref = PreferenceManager.getDefaultSharedPreferences(App.getContext());
		int latE6 = pref.getInt(PREF_LAST_LAT, 0);
		int lonE6 = pref.getInt(PREF_LAST_LON, 0);
		lastTime = pref.getLong(PREF_LAST_TIME, -1);

		if (lastTime >= 0) {
			lastPosition = new GeoPoint(latE6, lonE6);
		}
	}

	@Override
	public void loadPoints() throws IOException, LoginException {
		log.trace("GetsPointLoader.loadPoints");

		if (currentPosition != null) {
			List<PointDesc> points = new ArrayList<PointDesc>();
			List<CategoriesList.Category> categories = gets.getCategories();

			for (CategoriesList.Category cat : categories) {
				try {
					points.addAll(gets.getPoints(cat, currentPosition, radius));
				} catch (IOException ex) {
				}
			}

			if (!points.isEmpty()) {
				notifyPointsReady(points);
			}

			lastPosition = Utils.copyGeoPoint(currentPosition);
			lastTime = System.currentTimeMillis();
			needUpdate = false;

			pref.edit().putInt(PREF_LAST_LAT, lastPosition.getLatitudeE6())
					.putInt(PREF_LAST_LON, lastPosition.getLongitudeE6())
					.putLong(PREF_LAST_TIME, lastTime).apply();
		}
	}

	@Override
	public String getName() {
		return "GetsPointLoader";
	}

	@Override
	public void updatePosition(GeoPoint geoPoint) {
		super.updatePosition(geoPoint);
		log.trace("GetsPointLoader.updatePosition {}", geoPoint);

		currentPosition = Utils.copyGeoPoint(geoPoint);

		if (lastPosition == null
				|| lastPosition.distanceTo(geoPoint) > radius / 3
				|| System.currentTimeMillis() - lastTime > POINT_UPDATE_TIME) {
			log.trace("Updating position");
			needUpdate = true;
		} else {
			log.trace("Skipping update");
			needUpdate = false;
		}
	}

	@Override
	public boolean needUpdate() {
		return needUpdate;
	}

	public void setRadius(int radiusM) {
		this.radius = radiusM;
		needUpdate = true;
	}
}
