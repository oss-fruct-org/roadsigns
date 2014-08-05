package org.fruct.oss.ikm.service;


import android.annotation.TargetApi;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Edge;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.shapes.GHPoint;

import org.osmdroid.util.GeoPoint;

import static java.lang.Math.cos;
import static java.lang.Math.toRadians;

public class MapMatcher implements IMapMatcher {
	public static final String PROVIDER = "org.fruct.oss.ikm.MAP_MATCHER_PROVIDER";
	private final Graph graph;
	private final GHRouting routing;

	private Location lastLocation;
	private Location matchedLocation;

	private int currentNode;

	private final EdgeExplorer outEdgeExplorer;
	private final DistanceCalcEarth distanceCalc;

	private GeoPoint tmpPoint = new GeoPoint(0, 0);
	private double[] tmpCoord = new double[2];
	private int[] tmpOut = new int[1];

	public MapMatcher(Graph graph, GHRouting routing, String encoderString) {
		this.graph = graph;
		this.routing = routing;

		EncodingManager encodingManager = new EncodingManager(encoderString);
		FlagEncoder encoder = encodingManager.getEncoder(encoderString);

		outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
		distanceCalc = new DistanceCalcEarth();
	}

	@Override
	public void updateLocation(Location location) {
		if (lastLocation == null) {
			setInitialLocation(location);
		}

		findBestMatch(location);
		lastLocation = location;
	}

	private void setInitialLocation(Location location) {
		GeoPoint point = new GeoPoint(location);
		currentNode = routing.getPointIndex(point, false);
	}

	private void findBestMatch(Location location) {
		EdgeIterator iter = outEdgeExplorer.setBaseNode(currentNode);
		GeoPoint basePoint = routing.getPoint(iter.getBaseNode(), tmpPoint);

		final double rLat = location.getLatitude();
		final double rLon = location.getLongitude();
		final double baseLat = basePoint.getLatitude();
		final double baseLon = basePoint.getLongitude();

		double minDist = Double.MAX_VALUE;
		GeoPoint minPoint = new GeoPoint(0, 0);
		int minNode = -1;

		while (iter.next()) {
			int adjNode = iter.getAdjNode();
			GeoPoint adjPoint = routing.getPoint(adjNode, tmpPoint);
			final double adjLat = adjPoint.getLatitude();
			final double adjLon = adjPoint.getLongitude();

			double d = calcDist(rLat, rLon, baseLat, baseLon, adjLat, adjLon, tmpOut, tmpCoord);
			if (d < minDist) {
				minDist = d;
				minPoint.setCoordsE6((int) (tmpCoord[0] * 1e6), (int) (tmpCoord[1] * 1e6));

				if (tmpOut[0] == 1) {
					minNode = currentNode;
				} else if (tmpOut[0] == 2) {
					minNode = adjNode;
				}
			}
		}

		if (minDist != Double.MAX_VALUE) {
			matchedLocation = createLocation(minPoint.getLatitude(), minPoint.getLongitude());
		}

		if (minNode != -1) {
			setInitialLocation(matchedLocation);
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private Location createLocation(double lat, double lon) {
		Location location = new Location(PROVIDER);

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
			location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
		}

		location.setLatitude(lat);
		location.setLongitude(lon);

		return location;
	}

	@Override
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public Location getMatchedLocation() {
		if (matchedLocation == null)
			return lastLocation;
		else
			return matchedLocation;
	}

	// Copied and modified from graphhopper's DistanceCalcEarth.java
	public double calcDist(double r_lat_deg, double r_lon_deg,
						   double a_lat_deg, double a_lon_deg,
						   double b_lat_deg, double b_lon_deg, int[] type, double[] outCoord) {
		type[0] = 0;
		double shrink_factor = cos((toRadians(a_lat_deg) + toRadians(b_lat_deg)) / 2);
		double a_lat = a_lat_deg;
		double a_lon = a_lon_deg * shrink_factor;

		double b_lat = b_lat_deg;
		double b_lon = b_lon_deg * shrink_factor;

		double r_lat = r_lat_deg;
		double r_lon = r_lon_deg * shrink_factor;

		double delta_lon = b_lon - a_lon;
		double delta_lat = b_lat - a_lat;

		if (delta_lat == 0) {
			// special case: horizontal edge
			outCoord[0] = a_lat_deg;
			outCoord[1] = r_lon_deg;
			return distanceCalc.calcDist(a_lat_deg, r_lon_deg, r_lat_deg, r_lon_deg);
		}
		if (delta_lon == 0) {
			// special case: vertical edge
			outCoord[0] = r_lat_deg;
			outCoord[1] = a_lon_deg;
			return distanceCalc.calcDist(r_lat_deg, a_lon_deg, r_lat_deg, r_lon_deg);
		}

		double norm = delta_lon * delta_lon + delta_lat * delta_lat;
		double factor = ((r_lon - a_lon) * delta_lon + (r_lat - a_lat) * delta_lat) / norm;

		if (factor > 1) {
			type[0] = 1;
			factor = 1;
		} else if (factor < 0) {
			type[0] = 2;
			factor = 0;
		}

		// x,y is projection of r onto segment a-b
		double c_lon = a_lon + factor * delta_lon;
		double c_lat = a_lat + factor * delta_lat;

		outCoord[0] = c_lat;
		outCoord[1] = c_lon / shrink_factor;

		return distanceCalc.calcDist(c_lat, c_lon / shrink_factor, r_lat_deg, r_lon_deg);
	}
}
