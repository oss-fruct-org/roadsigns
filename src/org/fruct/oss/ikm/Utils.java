package org.fruct.oss.ikm;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

import android.util.Log;

public class Utils {
	public final static Executor executor = Executors.newFixedThreadPool(1);
	public static GeoPoint copyGeoPoint(IGeoPoint p) {
		return new GeoPoint(p.getLatitudeE6(), p.getLongitudeE6());
	}
	
	public static void log(String str) {
		Log.d("roadsigns", str);
	}
	
	public static void log(String format, Object... args) {
		log(String.format(format, args));
	}
	
	public static float normalizeAngle(float degree) {
		return (float) (Math.IEEEremainder(degree, 360));
	}
}
