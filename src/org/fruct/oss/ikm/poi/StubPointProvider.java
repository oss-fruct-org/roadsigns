package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;

public class StubPointProvider implements PointProvider {
	private ArrayList<PointDesc> stubPoints = new ArrayList<PointDesc>();
	{
		stubPoints.add(new PointDesc("Voenkomat", 61781090, 34362360)
						.setCategory("education")
						.setDescription("Petrozavodsk voenkomat"));
		
		stubPoints.add(new PointDesc("Bsmp", 61796590, 34365400)
						.setCategory("health")
						.setDescription("Petrozavodsk bolnitsa"));
		
		stubPoints.add(new PointDesc("Physic corpus", 61773060, 34283250)
						.setCategory("education")
						.setDescription("Physic corpus of PetrSU"));
		
		stubPoints.add(new PointDesc("TV", 61775372, 34328494)
						.setCategory("education")
						.setDescription("Patrozavods TV Petrozavodsk TV Petrozavodsk TV"));
	}
	
	@Override
	public List<PointDesc> getPoints(int latE6, int lonE6, int radius) {
		return stubPoints;
	}
}
