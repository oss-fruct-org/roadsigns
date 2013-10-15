package org.fruct.oss.ikm;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

public class Utils {
	public final static Executor executor = Executors.newFixedThreadPool(1);
	public static GeoPoint copyGeoPoint(IGeoPoint p) {
		return new GeoPoint(p.getLatitudeE6(), p.getLongitudeE6());
	}
}
