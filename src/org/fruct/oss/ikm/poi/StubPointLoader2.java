package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StubPointLoader2 implements PointLoader {
	private ArrayList<PointDesc> stubPoints = new ArrayList<PointDesc>();
	{
		stubPoints.add(new PointDesc("Petrozavodsk", 61781090, 34362360)
				.setCategory("culture").setDescription("Petrozavodsk"));

		stubPoints.add(new PointDesc("Kivach", 62267979, 33980602)
				.setCategory("culture").setDescription("Kivach"));

		stubPoints.add(new PointDesc("Kondopoga", 62203961, 34269420)
				.setCategory("culture").setDescription("Kondopoga"));
		
		stubPoints.add(new PointDesc("Medvezhegorsk", 62911189, 34464274)
				.setCategory("culture").setDescription("Medvezhegorsk"));

	}

	@Override
	public List<PointDesc> getPoints() {
		return stubPoints;
	}
}
