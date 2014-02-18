package org.fruct.oss.ikm.poi;

import org.fruct.oss.ikm.Utils;
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
	public static final int POINT_UPDATE_DISTANCE = 10000;

	private Gets gets;
	private GeoPoint lastPosition;
	private int radius = 5000;

	public GetsPointLoader(String url) {
		gets = new Gets(url);
	}

	@Override
	public void loadPoints() throws IOException, LoginException {
		log.trace("GetsPointLoader.loadPoints");

		if (lastPosition == null) {
			notifyPointsReady(new ArrayList<PointDesc>());
		} else {
			List<PointDesc> points = new ArrayList<PointDesc>();
			List<CategoriesList.Category> categories = gets.getCategories();

			for (CategoriesList.Category cat : categories) {
				log.debug("Category: {}", cat.getDescription());
				try {
					points.addAll(gets.getPoints(cat, lastPosition, radius));
				} catch (IOException ex) {

				}
			}
			notifyPointsReady(points);
		}
	}

	@Override
	public boolean updatePosition(GeoPoint geoPoint) {
		super.updatePosition(geoPoint);
		log.trace("GetsPointLoader.updatePosition {}", geoPoint);

		if (lastPosition == null || lastPosition.distanceTo(geoPoint) > POINT_UPDATE_DISTANCE) {
			log.trace("Updating position");
			lastPosition = Utils.copyGeoPoint(geoPoint);
			return true;
		} else {
			log.trace("Point too near to last position");
			return false;
		}
	}

	public void setRadius(int radiusM) {
		this.radius = radiusM;
	}
}
