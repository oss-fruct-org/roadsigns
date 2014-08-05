package org.fruct.oss.ikm.service;


import android.annotation.TargetApi;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;

import org.osmdroid.util.GeoPoint;

public class MapMatcher implements IMapMatcher {
	public static final String PROVIDER = "org.fruct.oss.ikm.MAP_MATCHER_PROVIDER";
	private final Graph graph;
	private final GHRouting routing;

	private Location lastLocation;
	private int currentNode;
	private String encoderString;

	private final EdgeExplorer outEdgeExplorer;

	private GeoPoint tmpPoint = new GeoPoint(0, 0);

	public MapMatcher(Graph graph, GHRouting routing, String encoderString) {
		this.graph = graph;
		this.routing = routing;

		this.encoderString = encoderString;
		EncodingManager encodingManager = new EncodingManager(encoderString);
		FlagEncoder encoder = encodingManager.getEncoder(encoderString);
		outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
	}

	@Override
	public void updateLocation(Location location) {
		if (lastLocation == null) {
			setInitialLocation(location);
		} else {
			setInitialLocation(location);
		}
	}

	private void setInitialLocation(Location location) {
		lastLocation = location;
		GeoPoint point = new GeoPoint(location);
		currentNode = routing.getPointIndex(point, false);
	}

	@Override
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	public Location getMatchedLocation() {
		Location location = new Location(PROVIDER);

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
			location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
		}

		routing.getPoint(currentNode, tmpPoint);

		location.setLatitude(tmpPoint.getLatitude());
		location.setLongitude(tmpPoint.getLongitude());

		return location;
	}
}
