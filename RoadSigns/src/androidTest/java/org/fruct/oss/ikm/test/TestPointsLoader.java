package org.fruct.oss.ikm.test;

import java.util.ArrayList;

import org.fruct.oss.ikm.points.Point;
import org.fruct.oss.ikm.points.PointLoader;

public class TestPointsLoader extends PointLoader {
	@Override
	public void loadPoints() {
		ArrayList<Point> ret = new ArrayList<Point>();
		ret.add(new Point("stadium", 61786314, 34356570).setCategory("sport"));
		ret.add(new Point("trainstation", 61784364,34344790).setCategory("culture"));
		ret.add(new Point("stadium", 61787309,34338836).setCategory("culture"));
		ret.add(new Point("forest", 61780720,34289472).setCategory("sport"));

		notifyPointsReady(ret);
	}

	@Override
	public String getName() {
		return "TestPointsLoader";
	}
}