package org.fruct.oss.ikm.service;

import org.osmdroid.util.GeoPoint;

import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.util.PointList;

public class OneToManyRouting extends GHRouting {
	private int fromId;
	private EncodingManager encodingManager;
	private FlagEncoder encoder;
	private EdgeFilter edgeFilter;
	private WeightCalculation weightCalc;
	private Graph graph;
	
	private Location2IDIndex index;
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
		edgeFilter = new DefaultEdgeFilter(encoder);

		graph = hopper.getGraph();
		
		index = hopper.getLocationIndex();
		
		weightCalc = new ShortestCalc();
		
		fromId = index.findClosest(from.getLatitudeE6() / 1e6, from.getLongitudeE6() / 1e6, edgeFilter).getClosestNode();
		algo = new DijkstraOneToMany(graph, encoder, weightCalc);
	}

	@Override
	public PointList route(GeoPoint to) {
		if (!ensureInitialized())
			return null;

		int toId = index.findClosest(to.getLatitudeE6() / 1e6, to.getLongitudeE6() / 1e6, edgeFilter).getClosestNode();
		
		if (toId < 0 || toId == fromId)
			return null;
		
		Path path = algo.calcPath(fromId, toId);
		
		return path.calcPoints();
	}

}
