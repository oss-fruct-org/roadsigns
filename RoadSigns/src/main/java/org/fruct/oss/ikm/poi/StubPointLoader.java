package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StubPointLoader extends PointLoader {
	private ArrayList<PointDesc> stubPoints = new ArrayList<PointDesc>();
	{
		stubPoints.add(new PointDesc("Voenkomat", 61781090, 34362360)
						.setCategory("health")
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
		
		stubPoints.add(new PointDesc("Tank", 61799888,34315730)
						.setCategory("culture")
						.setDescription("Tank T-34"));
		
		stubPoints.add(new PointDesc("Theater", 61787470,34383893)
						.setCategory("culture")
						.setDescription("Music theater"));

		stubPoints.add(new PointDesc("Forest", 63845966,33973846)
				.setCategory("health")
				.setDescription("Forest"));
	}

	@Override
	public void loadPoints() {
		notifyPointsReady(stubPoints);
	}

	@Override
	public String getName() {
		return "StubPointLoader";
	}
}
