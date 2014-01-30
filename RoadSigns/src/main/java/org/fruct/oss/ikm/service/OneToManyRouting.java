package org.fruct.oss.ikm.service;

import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.PointList;

import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OneToManyRouting extends GHRouting {
	private static Logger log = LoggerFactory.getLogger(OneToManyRouting.class);
	private int fromId;

	private com.graphhopper.routing.DijkstraOneToMany algo;

	public OneToManyRouting(String filePath, LocationIndexCache li) {
		super(filePath, li);
		log.debug("OneToManyRouting created");
	}

	@Override
	public void prepare(GeoPoint from) {
		if (!ensureInitialized())
			return;

		EncodingManager encodingManager = new EncodingManager("CAR");
		FlagEncoder encoder = encodingManager.getEncoder("CAR");

		Graph graph = hopper.getGraph();

		Weighting weightCalc = new ShortestWeighting();

		fromId = getPointIndex(from, false);
		algo = new DijkstraOneToMany(graph, encoder, weightCalc);
	}

	@Override
	public PointList route(GeoPoint to) {
		if (!ensureInitialized())
			return null;

		int toId = getPointIndex(to, true);
		Path path = algo.calcPath(fromId, toId);

		return path.calcPoints();
	}
}
