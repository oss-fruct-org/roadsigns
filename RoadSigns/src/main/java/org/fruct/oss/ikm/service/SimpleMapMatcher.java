package org.fruct.oss.ikm.service;

import android.annotation.TargetApi;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;

import org.osmdroid.util.GeoPoint;

public class SimpleMapMatcher implements IMapMatcher {
	private static final String PROVIDER = "org.fruct.oss.ikm.SIMPLE_MAP_MATCHER";
	private final GHRouting routing;

	private Location lastLocation;
	private Location lastMatchedLocation;
	private int lastMatchedNode;

	public SimpleMapMatcher(GHRouting routing) {
		this.routing = routing;
	}

	@Override
	public boolean updateLocation(Location location) {
		lastLocation = location;
		
		GeoPoint tmpPoint = new GeoPoint(location);
		lastMatchedNode = routing.getPointIndex(tmpPoint, false);
		if (lastMatchedNode != -1) {
			routing.getPoint(lastMatchedNode, tmpPoint);
			lastMatchedLocation = createLocation(location, tmpPoint.getLatitude(), tmpPoint.getLongitude());
			return true;
		} else {
			return false;
		}
	}

	@Override
	public Location getMatchedLocation() {
		return lastLocation;
	}

	@Override
	public int getMatchedNode() {
		return lastMatchedNode;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private Location createLocation(Location prototype, double lat, double lon) {
		Location location = new Location(prototype);
		location.setProvider(PROVIDER);

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
			location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
		}

		location.setLatitude(lat);
		location.setLongitude(lon);

		return location;
	}
}
