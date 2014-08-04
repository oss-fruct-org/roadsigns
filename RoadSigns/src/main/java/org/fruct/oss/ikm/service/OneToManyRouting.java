package org.fruct.oss.ikm.service;

import android.util.Pair;

import com.graphhopper.coll.IntDoubleBinHeap;
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.SoftReference;
import java.util.Arrays;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.stack.TIntStack;
import gnu.trove.stack.array.TIntArrayStack;
import org.fruct.oss.ikm.utils.Timer;

public class OneToManyRouting extends GHRouting {
	private static Logger log = LoggerFactory.getLogger(OneToManyRouting.class);

	private int fromId;
	private GeoPoint fromGeoPoint;

	private String encoderString = "CAR";

	private volatile SoftReference<DijkstraOneToMany> algoRef;

	private static Timer timer = new Timer();

	public OneToManyRouting(String filePath, LocationIndexCache li) {
		super(filePath, li);
		log.debug("OneToManyRouting created");
	}

	private DijkstraOneToMany getAlgo() {
		DijkstraOneToMany hardRef = (algoRef == null ? null : algoRef.get());

		if (hardRef == null) {
			log.debug("Creating DijkstraOneToMany with {} encoder...", encoderString);
			EncodingManager encodingManager = new EncodingManager(encoderString);
			FlagEncoder encoder = encodingManager.getEncoder(encoderString);
			Weighting weightCalc = new FastestWeighting(encoder);
			//Weighting weightCalc = new ShortestWeighting();

			Graph graph = hopper.getGraph();

			App.clearBitmapPool();
			System.gc();

			hardRef = new DijkstraOneToMany(graph, encoder, weightCalc);
			algoRef = new SoftReference<DijkstraOneToMany>(hardRef);

			log.debug("Created DijkstraOneToMany with {} encoder", encoderString);
		}

		return hardRef;
	}

	@Override
	public void prepare(GeoPoint from) {
		if (!ensureInitialized())
			return;

		fromGeoPoint = from;
		fromId = getPointIndex(from, false);
		if (algoRef != null)
			algoRef.clear();
	}

	@Override
	public synchronized PointList route(GeoPoint to) {
		if (!ensureInitialized())
			return null;

		DijkstraOneToMany algo = getAlgo();

		if (algo == null)
			return null;

		int toId = getPointIndex(to, true);

		Path path = algo.calcPath(fromId, toId);

		return path.calcPoints();
	}

	@Override
	public void route(PointDesc[] targetPoints, float radius, RoutingCallback callback) {
		TIntObjectMap<PointDesc> targetNodes = new TIntObjectHashMap<PointDesc>(targetPoints.length);

		for (PointDesc point : targetPoints) {
			int nodeId = getPointIndex(point.toPoint(), true);
			if (nodeId >= 0) {
				targetNodes.put(nodeId, point);
			} else {
				log.warn("Location index {} not found for {}", nodeId, point.getName());
			}
		}

		EncodingManager encodingManager = new EncodingManager(encoderString);
		FlagEncoder encoder = encodingManager.getEncoder(encoderString);
		Weighting weightCalc = new FastestWeighting(encoder);

		Graph graph = hopper.getGraph();

		EdgeExplorer outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
		EdgeExplorer inEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, false));

		int[] parents = new int[graph.getNodes()];
		Arrays.fill(parents, -1);

		TIntStack tmpPath = new TIntArrayStack();

		float[] costs = new float[graph.getNodes()];
		boolean[] closed = new boolean[graph.getNodes()];
		//boolean[] opened = new boolean[graph.getNodes()];
		IntDoubleBinHeap heap = new IntDoubleBinHeap();

		costs[fromId] = 0;
		heap.insert(0f, fromId);

		while (!heap.isEmpty() && targetNodes.size() > 0) {
			final int node = heap.peek_element();
			final double cost = heap.peek_key();
			heap.poll_element();
			closed[node] = true;

			if (targetNodes.containsKey(node)) {
				// Build direction for found target point
				PointDesc pointDesc = targetNodes.remove(node);

				tmpPath.clear();
				int fnode = node;

				while (fnode != -1) {
					tmpPath.push(fnode);
					fnode = parents[fnode];
				}

				assert tmpPath.peek() == fromId;

				Pair<GeoPoint, GeoPoint> dirPair = findDirection(tmpPath, radius);
				if (dirPair != null) {
					callback.pointReady(dirPair.first, dirPair.second, pointDesc);
				}
			}

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
}
