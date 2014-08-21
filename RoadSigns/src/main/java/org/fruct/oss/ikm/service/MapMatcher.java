package org.fruct.oss.ikm.service;


import android.annotation.TargetApi;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.fragment.TestLinesOverlay;
import org.osmdroid.util.GeoPoint;

import java.util.HashSet;
import java.util.Set;

import gnu.trove.list.array.TDoubleArrayList;

import static java.lang.Math.cos;
import static java.lang.Math.toRadians;

public class MapMatcher implements IMapMatcher {
	public static final String PROVIDER = "org.fruct.oss.ikm.MAP_MATCHER_PROVIDER";

	public static final double MAX_DISTANCE = 40f;
	public static final double MAX_INITIAL_DISTANCE = 4000f;

	public static final int MAX_RECURSION = 10;

	private final GHRouting routing;

	private Location lastLocation;
	private Location matchedLocation;
	private int matchedNode;

	private Set<Edge> activeEdges = new HashSet<Edge>();

	private final Graph graph;

	private final EdgeExplorer outEdgeExplorer;
	private final EdgeExplorer allEdgeExplorer;

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
		allEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, true));

		distanceCalc = new DistanceCalcEarth();
		weightCalc = new ShortestWeighting();
	}

	@Override
	public boolean updateLocation(Location location) {
		routing.throwIfClosed();

		if (activeEdges.isEmpty() || (matchedLocation != null && location.distanceTo(matchedLocation) > MAX_DISTANCE)) {
			activeEdges.clear();
			if (!setInitialLocation(location)) {
				return false;
			}
		}

		setLocation(location);

		lastLocation = location;
		return true;
	}

	private boolean setInitialLocation(Location location) {
		routing.throwIfClosed();

		matchedNode = -1;
		matchedLocation = null;

		GeoPoint locationPoint = new GeoPoint(location);

		QueryResult result = routing.getQueryResult(locationPoint);

		if (result == null)
			return false;

		if (result.getQueryDistance() > MAX_INITIAL_DISTANCE) {
			return false;
		}

		EdgeIteratorState closestEdge = result.getClosestEdge();
		activateNodeEdges(closestEdge.getAdjNode(), closestEdge.getEdge(), allEdgeExplorer);
		activateNodeEdges(closestEdge.getBaseNode(), -1, allEdgeExplorer);
		return true;
	}

	private int activateNodeEdges(int nodeId, int exclude, EdgeExplorer explorer) {
		int added = 0;
		EdgeIterator iterator = explorer.setBaseNode(nodeId);
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
		routing.throwIfClosed();

		final double rLat = location.getLatitude();
		final double rLon= location.getLongitude();

		for (int i = 0; i < MAX_RECURSION; i++) {
			EvalResult bestEvalResult = null;
			double maxValue = -Double.MAX_VALUE;

			/*initLines();
			for (Edge edge : activeEdges) {
				addLine(edge);
			}
			sendLines();*/

			for (Edge edge : activeEdges) {
				EvalResult evalResult = evalEdge(edge, rLat, rLon);
				double value = evalResult.value;

				if (value > maxValue) {
					maxValue = value;
					bestEvalResult = evalResult;
				}
			}

			assert bestEvalResult != null;
			// TODO: can throw NPE if near road too long
			EdgeIteratorState edgeProps = graph.getEdgeProps(bestEvalResult.edge.edgeId, bestEvalResult.edge.baseNodeId);

			activeEdges.clear();
			int addedBase = activateNodeEdges(edgeProps.getBaseNode(), bestEvalResult.edge.edgeId, outEdgeExplorer);
			int addedAdj = activateNodeEdges(edgeProps.getAdjNode(), bestEvalResult.edge.edgeId, outEdgeExplorer);
			activeEdges.add(bestEvalResult.edge);

			if (bestEvalResult.node != -1) {
				if (addedBase > 0 && bestEvalResult.node == edgeProps.getBaseNode()) {
					continue;
				} else if (addedAdj > 0 && bestEvalResult.node == edgeProps.getAdjNode()) {
					continue;
				} else {
					// Dead end
					matchedLocation = createLocation(location, bestEvalResult.cLat, bestEvalResult.cLon);
					matchedNode = bestEvalResult.node;
					return;
				}
			} else {
				matchedLocation = createLocation(location, bestEvalResult.cLat, bestEvalResult.cLon);
				matchedNode = routing.getPointIndex(new GeoPoint(matchedLocation), false);
				return;
			}
		}

		activeEdges.clear();
		if (setInitialLocation(location))
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

	@Override
	public Location getMatchedLocation() {
		return matchedLocation;
	}

	@Override
	public int getMatchedNode() {
		return matchedNode;
	}


	// Debug broadcasts
	private TDoubleArrayList lines = new TDoubleArrayList();

	private void initLines() {
		lines.clear();
	}

	private void addLine(int node1, int node2) {
		routing.getPoint(node1, tmpPoint);
		lines.add(tmpPoint.getLatitude());
		lines.add(tmpPoint.getLongitude());
		routing.getPoint(node2, tmpPoint);
		lines.add(tmpPoint.getLatitude());
		lines.add(tmpPoint.getLongitude());
	}

	private void addLine(Edge edge) {
		EdgeIteratorState edgeProps = graph.getEdgeProps(edge.edgeId, edge.baseNodeId);
		addLine(edgeProps.getBaseNode(), edgeProps.getAdjNode());
	}

	private void sendLines() {
		Intent intent = new Intent(TestLinesOverlay.BROADCAST);
		intent.putExtra("lines", lines.toArray());
		LocalBroadcastManager.getInstance(App.getContext()).sendBroadcast(intent);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
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
