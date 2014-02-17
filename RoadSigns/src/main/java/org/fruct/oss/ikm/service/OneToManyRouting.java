package org.fruct.oss.ikm.service;

import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.PointList;

import org.fruct.oss.ikm.App;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.SoftReference;

public class OneToManyRouting extends GHRouting {
	private static Logger log = LoggerFactory.getLogger(OneToManyRouting.class);
	private int fromId;
	private String encoderString = "CAR";

	private volatile SoftReference<DijkstraOneToMany> algoRef;

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
	public void setEncoder(String encoding) {
		encoderString = encoding;
	}
}
