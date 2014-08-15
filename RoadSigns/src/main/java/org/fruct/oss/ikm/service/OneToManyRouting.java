package org.fruct.oss.ikm.service;

import android.util.Pair;

import com.graphhopper.coll.IntDoubleBinHeap;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.stack.TIntStack;
import gnu.trove.stack.array.TIntArrayStack;
import org.fruct.oss.ikm.utils.Timer;

public class OneToManyRouting extends GHRouting {
	private static Logger log = LoggerFactory.getLogger(OneToManyRouting.class);

	private String encoderString = "CAR";

	private static Timer timer = new Timer();

	// Dijkstra routing arrays
	private int[] parents;
	private float[] costs;
	private boolean[] closed;
	private IntDoubleBinHeap heap;

	private EdgeExplorer outEdgeExplorer;
	private Weighting weightCalc;

	private transient TIntStack tmpPath;
	private transient GeoPoint tmpPoint = new GeoPoint(0, 0);

	public OneToManyRouting(String filePath, LocationIndexCache li) {
		super(filePath, li);
		log.debug("OneToManyRouting created");
	}

	@Override
	public void prepare(GeoPoint from) {
		if (!ensureInitialized())
			return;

		Graph graph = hopper.getGraph();
		tmpPath = new TIntArrayStack();

		EncodingManager encodingManager = new EncodingManager(encoderString);
		FlagEncoder encoder = encodingManager.getEncoder(encoderString);
		weightCalc = new FastestWeighting(encoder);

		outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));

		int fromId = getPointIndex(from, false);

		parents = new int[graph.getNodes()];
		Arrays.fill(parents, -1);

		costs = new float[graph.getNodes()];
		closed = new boolean[graph.getNodes()];
		heap = new IntDoubleBinHeap();

		costs[fromId] = 0;
		heap.insert(0f, fromId);
	}

	@Override
	public synchronized PointList route(GeoPoint to) {
		if (!ensureInitialized())
			return null;

		int toId = getPointIndex(to, true);
		int node = toId;

		while (!heap.isEmpty() && !closed[toId]) {
			node = findNextNode();
		}

		if (node != -1 && closed[node]) {
			PointList pointList = new PointList();

			while (node != -1) {
				GeoPoint nodePoint = getPoint(node, tmpPoint);
				pointList.add(nodePoint.getLatitude(), nodePoint.getLongitude());
				node = parents[node];
			}

			return pointList;
		} else {
			return null;
		}
	}

	@Override
	public void route(PointDesc[] targetPoints, float radius, RoutingCallback callback) {
		// TODO: possibly use more efficient multi-map
		TIntObjectMap<List<PointDesc>> targetNodes = new TIntObjectHashMap<List<PointDesc>>(targetPoints.length);

		// Prepare location index
		for (PointDesc point : targetPoints) {
			int nodeId = getPointIndex(point.toPoint(), true);
			if (nodeId >= 0) {
				if (closed[nodeId]) {
					ArrayList<PointDesc> points = new ArrayList<PointDesc>(1);
					points.add(point);
					sendPointDirection(nodeId, points, radius, callback);
				} else {
					List<PointDesc> nodePoints = targetNodes.get(nodeId);
					if (nodePoints == null) {
						nodePoints = new ArrayList<PointDesc>();
						targetNodes.put(nodeId, nodePoints);
					}

					nodePoints.add(point);
				}
			} else {
				log.warn("Location index {} not found for {}", nodeId, point.getName());
			}

			if (Thread.interrupted()) {
				return;
			}
		}

		while (!heap.isEmpty() && targetNodes.size() > 0) {
			int node = findNextNode();
			if (targetNodes.containsKey(node)) {
				List<PointDesc> nodePoints = targetNodes.remove(node);
				sendPointDirection(node, nodePoints, radius, callback);
			}

			if (Thread.interrupted()) {
				return;
			}
		}
	}

	private void sendPointDirection(int node, List<PointDesc> targetPoints, float radius, RoutingCallback callback) {
		// Build direction for found target point
		buildPath(node, tmpPath);

		Pair<GeoPoint, GeoPoint> dirPair = findDirection(tmpPath, radius);
		if (dirPair != null) {
			for (PointDesc pointDesc : targetPoints) {
				callback.pointReady(dirPair.first, dirPair.second, pointDesc);
			}
		}
	}

	private int findNextNode() {
		final int node = heap.peek_element();
		final double cost = heap.peek_key();
		heap.poll_element();
		closed[node] = true;

		EdgeIterator iter = outEdgeExplorer.setBaseNode(node);
		while (iter.next()) {
			int adjNode = iter.getAdjNode();
			if (closed[adjNode])
				continue;

			float tcost = (float) (cost + weightCalc.calcWeight(iter, false));
			if (parents[adjNode] == -1) {
				parents[adjNode] = node;
				costs[adjNode] = tcost;
				heap.insert(tcost, adjNode);
			} else if (tcost < costs[adjNode]) {
				parents[adjNode] = node;
				costs[adjNode] = tcost;
				heap.update(tcost, adjNode);
			}
		}

		return node;
	}

	private void buildPath(int node, TIntStack outPath) {
		outPath.clear();

		while (node != -1) {
			outPath.push(node);
			node = parents[node];
		}
	}

	private Pair<GeoPoint, GeoPoint> findDirection(TIntStack path, final float radius) {
		// TODO: handle this case specially
		if (path.size() < 2)
			return null;

		final GeoPoint current = new GeoPoint(0, 0);
		GeoPoint point = new GeoPoint(0, 0);
		getPoint(path.pop(), current);
		GeoPoint prev = Utils.copyGeoPoint(current);

		while (path.size() > 0) {
			int node = path.pop();
			getPoint(node, point);

			final int dist = current.distanceTo(point);
			if (dist > radius) {
				final GeoPoint a = prev;
				// TODO: remove magic number '2'
				final double d = a.distanceTo(point) + 2;
				final float bearing = (float) a.bearingTo(point);

				// TODO: catch exceptions
				double sol = Utils.solve(0, d, 0.1, new Utils.FunctionDouble() {
					@Override
					public double apply(double x) {
						GeoPoint mid = a.destinationPoint(x, bearing);
						double distFromCenter = current.distanceTo(mid);
						return distFromCenter - (radius + 2);
					}
				});

				GeoPoint target = a.destinationPoint(sol, bearing);

				return Pair.create(a, target);
			}
			prev = Utils.copyGeoPoint(point);
		}

		return null;
	}

	@Override
	public void setEncoder(String encoding) {
		encoderString = encoding;
	}

	@Override
	public IMapMatcher createMapMatcher() {
		ensureInitialized();

		return new MapMatcher(hopper.getGraph(), this, encoderString);
	}
}
