package org.fruct.oss.ikm.service;

import org.osmdroid.util.GeoPoint;

import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;

public class OneToManyRouting extends GHRouting {
	private int fromId;
	private EncodingManager encodingManager;
	private FlagEncoder encoder;
	private EdgeFilter edgeFilter;
	private Weighting weightCalc;
	private Graph graph;
	
	private LocationIndex index;
	private com.graphhopper.routing.DijkstraOneToMany algo;

	public OneToManyRouting(String filePath) {
		super(filePath);
	}

	@Override
	public void prepare(GeoPoint from) {
		if (!ensureInitialized())
			return;

		encodingManager = new EncodingManager("CAR");
		encoder = encodingManager.getEncoder("CAR");
		edgeFilter = EdgeFilter.ALL_EDGES;

		graph = hopper.getGraph();
		
		index = hopper.getLocationIndex();
		
		weightCalc = new ShortestWeighting();

		QueryResult queryResult = index.findClosest(from.getLatitudeE6() / 1e6, from.getLongitudeE6() / 1e6, edgeFilter);
		fromId = queryResult.getClosestNode();
		algo = new DijkstraOneToMany(graph, encoder, weightCalc);
	}

	StopWatch sw = new StopWatch("Routing");

	@Override
	public PointList route(GeoPoint to) {
		if (!ensureInitialized())
			return null;

		QueryResult queryResult = index.findClosest(to.getLatitudeE6() / 1e6, to.getLongitudeE6() / 1e6, edgeFilter);

		if (!queryResult.isValid()) {
			assert queryResult.getClosestNode() == -1;
			assert queryResult.getClosestEdge() == null;
			log.debug("Can't find target road not for point {}", to);
		} else {
			assert queryResult.getClosestNode() != -1;
			assert queryResult.getClosestEdge() != null;
		}

		int toId = queryResult.getClosestNode();
		if (toId < 0 || toId == fromId)
			return null;
		
		Path path = algo.calcPath(fromId, toId);

		return path.calcPoints();
	}
}
