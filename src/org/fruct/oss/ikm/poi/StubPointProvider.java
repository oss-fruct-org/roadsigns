package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;

public class StubPointProvider implements PointProvider {
	private ArrayList<PointDesc> stubPoints = new ArrayList<PointDesc>();
	{
		stubPoints.add(new PointDesc("Voenkomat", 61781090, 34362360));
		stubPoints.add(new PointDesc("Bsmp", 61796590, 34365400));
		stubPoints.add(new PointDesc("Physic corpus", 61773060, 34283250));
		stubPoints.add(new PointDesc("TV", 61775372, 34328494));
	}
	
	@Override
	public List<PointDesc> getPoints(int latE6, int lonE6, int radius) {
		return stubPoints;
	}
}
