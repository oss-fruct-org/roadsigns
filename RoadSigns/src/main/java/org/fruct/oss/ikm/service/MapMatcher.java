package org.fruct.oss.ikm.service;


import android.annotation.TargetApi;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;

import com.graphhopper.coll.IntDoubleBinHeap;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Edge;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gnu.trove.map.TIntDoubleMap;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import static java.lang.Math.cos;
import static java.lang.Math.toRadians;

public class MapMatcher implements IMapMatcher {
	public static final String PROVIDER = "org.fruct.oss.ikm.MAP_MATCHER_PROVIDER";

	public static final double MAX_DISTANCE = 40f;
	public static final int MAX_RECURSION = 10;

	private final GHRouting routing;

	private Location lastLocation;
	private Location matchedLocation;

	private Set<Edge> activeEdges = new HashSet<Edge>();

	private final Graph graph;
	private final EdgeExplorer outEdgeExplorer;
	private final DistanceCalcEarth distanceCalc;
	private final Weighting weightCalc;

	private GeoPoint tmpPoint = new GeoPoint(0, 0);
	private double[] tmpCoord = new double[2];
	private int[] tmpInt = new int[1];
	private boolean[] tmpBoolean = new boolean[1];

	public MapMatcher(Graph graph, GHRouting routing, String encoderString) {
		this.graph = graph;
		this.routing = routing;

		EncodingManager encodingManager = new EncodingManager(encoderString);
		FlagEncoder encoder = encodingManager.getEncoder(encoderString);

		outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
		distanceCalc = new DistanceCalcEarth();
		weightCalc = new ShortestWeighting();
	}

	@Override
	public void updateLocation(Location location) {
		if (activeEdges.isEmpty()) {
			setInitialLocation(location);
		}

		setLocation(location);

		lastLocation = location;
	}

	private void setInitialLocation(Location location) {
		int baseNodeId = routing.getPointIndex(new GeoPoint(location), false);
		activateNodeEdges(baseNodeId, -1);
	}

	private int activateNodeEdges(int nodeId, int exclude) {
		int added = 0;
		EdgeIterator iterator = outEdgeExplorer.setBaseNode(nodeId);
		while (iterator.next()) {
			Edge edge = new Edge(iterator);
			if (edge.edgeId != exclude) {
				activeEdges.add(edge);
				added++;
			}
		}
		return added;
	}

	private void setLocation(Location location) {
		final double rLat = location.getLatitude();
		final double rLon= location.getLongitude();

		for (int i = 0; i < MAX_RECURSION; i++) {
			EvalResult bestEvalResult = null;
			double maxValue = -Double.MAX_VALUE;

			for (Edge edge : activeEdges) {
				EvalResult evalResult = evalEdge(edge, rLat, rLon);
				double value = evalResult.value;

				if (value > maxValue) {
					maxValue = value;
					bestEvalResult = evalResult;
				}
			}

			assert bestEvalResult != null;

			if (distanceCalc.calcDist(rLat, rLon, bestEvalResult.cLat, bestEvalResult.cLon) > MAX_DISTANCE) {
				break;
			}

			if (bestEvalResult.node == -1) {
				matchedLocation = createLocation(bestEvalResult.cLat, bestEvalResult.cLon);
				return;
			}

			EdgeIteratorState edgeProps = graph.getEdgeProps(bestEvalResult.edge.edgeId, bestEvalResult.edge.baseNodeId);

			activeEdges.clear();
			int addedBase = activateNodeEdges(edgeProps.getBaseNode(), bestEvalResult.edge.edgeId);
			int addedAdj = activateNodeEdges(edgeProps.getAdjNode(), bestEvalResult.edge.edgeId);
			activeEdges.add(bestEvalResult.edge);

			if (bestEvalResult.node != -1) {
				if (addedBase > 0 && bestEvalResult.node == edgeProps.getBaseNode()) {
					continue;
				} else if (addedAdj > 0 && bestEvalResult.node == edgeProps.getAdjNode()) {
					continue;
				} else {
					matchedLocation = createLocation(bestEvalResult.cLat, bestEvalResult.cLon);
					return;
				}
			}
		}

		setInitialLocation(location);
		setLocation(location);
	}

	private EvalResult evalEdge(Edge edge, final double rLat, final double rLon) {
		EvalResult result = new EvalResult();

		EdgeIteratorState edgeProps = graph.getEdgeProps(edge.edgeId, edge.baseNodeId);

		routing.getPoint(edgeProps.getBaseNode(), tmpPoint);
		final double aLat = tmpPoint.getLatitude();
		final double aLon = tmpPoint.getLongitude();

		routing.getPoint(edgeProps.getAdjNode(), tmpPoint);
		final double bLat = tmpPoint.getLatitude();
		final double bLon = tmpPoint.getLongitude();

		double dist = calcDist(rLat, rLon, aLat, aLon, bLat, bLon, tmpInt, tmpCoord);

		result.edge = edge;
		result.value = -dist;
		result.cLat = tmpCoord[0];
		result.cLon = tmpCoord[1];

		if (tmpInt[0] == 1) {
			result.node = edgeProps.getBaseNode();
		} else if (tmpInt[0] == 2) {
			result.node = edgeProps.getAdjNode();
		} else {
			result.node = -1;
		}

		return result;
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
			type[0] = 2;
			factor = 1;
		} else if (factor < 0) {
			type[0] = 1;
			factor = 0;
		}

		// x,y is projection of r onto segment a-b
		double c_lon = a_lon + factor * delta_lon;
		double c_lat = a_lat + factor * delta_lat;

		outCoord[0] = c_lat;
		outCoord[1] = c_lon / shrink_factor;

		return distanceCalc.calcDist(c_lat, c_lon / shrink_factor, r_lat_deg, r_lon_deg);
	}

	static class Edge {
		Edge(int edgeId, int baseNodeId) {
			this.edgeId = edgeId;
			this.baseNodeId = baseNodeId;
		}

		Edge(EdgeIteratorState iter) {
			this.edgeId = iter.getEdge();
			this.baseNodeId = iter.getBaseNode();
		}

		final int edgeId;
		final int baseNodeId;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Edge edge = (Edge) o;

			if (edgeId != edge.edgeId) return false;

			return true;
		}

		@Override
		public int hashCode() {
			return edgeId;
		}
	}

	static class EvalResult {
		Edge edge;
		double value;
		int node;

		double cLat;
		double cLon;
	}
}
