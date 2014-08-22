package org.fruct.oss.ikm.service;

import android.support.v4.util.LruCache;
import android.util.Pair;

import com.graphhopper.coll.IntDoubleBinHeap;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.queue.TIntQueue;
import gnu.trove.stack.TIntStack;
import gnu.trove.stack.array.TIntArrayStack;
import org.fruct.oss.ikm.utils.Timer;

public class OneToManyRouting extends GHRouting {
	private static final int MAX_TARGET_PATH_SEARCH = 10;
	private static final int MAX_RADIUS = 10;
	private static Logger log = LoggerFactory.getLogger(OneToManyRouting.class);
	private static final boolean ENABLE_CACHING = true;

	private static Timer timer = new Timer();

	// Dijkstra routing arrays
	private int[] parents;
	private float[] costs;
	private boolean[] closed;
	private int[] distances;

	private IntDoubleBinHeap heap;

	private EdgeExplorer outEdgeExplorer;

	private transient TIntList tmpPath;
	private transient GeoPoint tmpPoint = new GeoPoint(0, 0);

	private int fromId;

	private int targetNode = -1;
	private TIntList targetPath = new TIntArrayList();

	private TargetPointCache targetPointCache = new TargetPointCache(1024 * 1024);

	public OneToManyRouting(String filePath, LocationIndexCache li) {
		super(filePath, li);
		log.debug("OneToManyRouting created");

	}

	@Override
	public void prepare(int fromId) {
		if (!ensureInitialized())
			return;

		if (fromId == this.fromId) {
			return;
		}

		this.fromId = fromId;

		Graph graph = hopper.getGraph();
		tmpPath = new TIntArrayList();

		EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder, false, true);
		outEdgeExplorer = graph.createEdgeExplorer(edgeFilter);

		parents = new int[graph.getNodes()];
		Arrays.fill(parents, -1);

		distances = new int[graph.getNodes()];
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

		// New point. Reset cached path
		targetNode = getPointIndex(to, true);

		if (targetNode == -1) {
			return null;
		}

		return routeTarget();
	}

	private PointList routeTarget() {
		targetPath.clear();

		// Do dijkstra steps until target point found
		int node = targetNode;
		while (!heap.isEmpty() && !closed[targetNode]) {
			node = findNextNode();
		}

		if (node != -1 && closed[node]) {
			targetPath.clear();

			// Build new cached path
			while (node != -1) {
				targetPath.add(node);
				node = parents[node];
			}

			return createPointList(targetPath);
		} else {
			return null;
		}
	}

	private PointList createPointList(TIntList nodes) {
		PointList pointList = new PointList();

		for (int i = 0, length = nodes.size(); i < length; i++) {
			int node = nodes.get(i);
			GeoPoint nodePoint = getPoint(node, tmpPoint);
			pointList.add(nodePoint.getLatitude(), nodePoint.getLongitude());
		}

		return pointList;
	}

	@Override
	public void route(PointDesc[] targetPoints, float radius, RoutingCallback callback) {
		// TODO: possibly use more efficient multi-map
		TIntObjectMap<List<PointDesc>> targetNodes = new TIntObjectHashMap<List<PointDesc>>(targetPoints.length);
		TIntObjectMap<TargetPoint> nearCachedPoints = new TIntObjectHashMap<TargetPoint>();

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

					if (ENABLE_CACHING) {
						// Check if exists any cached path for current point
						TargetPoint targetPoint = targetPointCache.get(nodeId);
						if (targetPointCache != null) {
							// Add first MAX_RADIUS points in cached path to look-up list
							for (int i = 0, length = Math.min(targetPoint.currentPath.size(), MAX_RADIUS);
								 i < length; i++) {
								int lookupNode = targetPoint.currentPath.get(targetPoint.currentPath.size() - 1 - i);
								nearCachedPoints.put(lookupNode, targetPoint);
							}
						}
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

		// Target path update
		if (targetNode != -1) {
			boolean found = false;
			for (int i = 0; i < MAX_TARGET_PATH_SEARCH && !targetPath.isEmpty(); i++) {
				if (targetPath.get(targetPath.size() - 1) == fromId) {
					found = true;
					break;
				}
				targetPath.removeAt(targetPath.size() - 1);
			}

			if (found) {
				log.trace("Target path: using cached ");
				callback.pathUpdated(createPointList(targetPath));
			} else {
				log.trace("Target path: recalculated");
				callback.pathUpdated(routeTarget());
			}
		}
	}

	private void sendPointDirection(int node, List<PointDesc> targetPoints, float radius, RoutingCallback callback) {
		TIntList tmp;

		// Build direction for found target point
		if (ENABLE_CACHING) {
			tmp = new TIntArrayList();
			targetPointCache.put(node, new TargetPoint(node, targetPoints));
		} else	{
			tmp = tmpPath;
		}

		buildPath(node, tmp);

		int dist = distances[node];

		Pair<GeoPoint, GeoPoint> dirPair = findDirection(tmp, radius);
		if (dirPair != null) {
			for (PointDesc pointDesc : targetPoints) {
				pointDesc.setDistance(dist);
				callback.pointReady(dirPair.first, dirPair.second, pointDesc);
			}
		}
	}

	private int findNextNode() {
		final int node = heap.peek_element();
		final double cost = heap.peek_key();
		final int dist = distances[node];
		heap.poll_element();
		closed[node] = true;

		EdgeIterator iter = outEdgeExplorer.setBaseNode(node);
		while (iter.next()) {
			int adjNode = iter.getAdjNode();
			if (closed[adjNode])
				continue;

			int tdist = dist + (int) iter.getDistance();
			float tcost = (float) (cost + weightCalc.calcWeight(iter, false));
			if (parents[adjNode] == -1) {
				parents[adjNode] = node;
				costs[adjNode] = tcost;
				distances[adjNode] = tdist;
				heap.insert(tcost, adjNode);
			} else if (tcost < costs[adjNode]) {
				parents[adjNode] = node;
				costs[adjNode] = tcost;
				distances[adjNode] = tdist;
				heap.update(tcost, adjNode);
			}
		}

		return node;
	}

	private void buildPath(int node, TIntList outPath) {
		outPath.clear();

		while (node != -1) {
			outPath.add(node);
			node = parents[node];
		}
	}

	private Pair<GeoPoint, GeoPoint> findDirection(TIntList path, final float radius) {
		// TODO: handle this case specially
		if (path.size() < 2)
			return null;

		final GeoPoint current = new GeoPoint(0, 0);
		GeoPoint point = new GeoPoint(0, 0);
		getPoint(path.removeAt(path.size() - 1), current);
		GeoPoint prev = Utils.copyGeoPoint(current);

		while (path.size() > 0) {
			int node = path.removeAt(path.size() - 1);
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
	public IMapMatcher createMapMatcher() {
		ensureInitialized();

		return new MapMatcher(hopper.getGraph(), this, encodingString);
	}

	@Override
	public IMapMatcher createSimpleMapMatcher() {
		ensureInitialized();

		return new SimpleMapMatcher(this);
	}

	private static class TargetPoint {
		private TargetPoint(int node, List<PointDesc> points) {
			this.node = node;
			this.points = points;
		}

		int node;
		TIntList currentPath;
		List<PointDesc> points;
	}

	private static class TargetPointCache extends LruCache<Integer, TargetPoint> {
		public TargetPointCache(int maxSize) {
			super(maxSize);
		}

		@Override
		protected int sizeOf(Integer key, TargetPoint value) {
			return 4 + 4 + value.currentPath.size() * 4;
		}
	}
}
