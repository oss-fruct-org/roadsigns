package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;

public class StubPointProvider implements PointProvider {
	private ArrayList<PointOfInterest> stubPoints = new ArrayList<PointOfInterest>();
	{
		stubPoints.add(new PointOfInterest("Voenkomat", 61781090, 34362360));
		stubPoints.add(new PointOfInterest("Bsmp", 61796590, 34365400));
		stubPoints.add(new PointOfInterest("Physic corpus", 61773060, 34283250));
		stubPoints.add(new PointOfInterest("TV", 61775372, 34328494));
	}
	
	@Override
	public List<PointOfInterest> getPoints(int latE6, int lonE6, int radius) {
		return stubPoints;
	}
}
