package org.fruct.oss.ikm.test;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointLoader;

public class TestPointsLoader extends PointLoader {
	@Override
	public void loadPoints() {
		ArrayList<PointDesc> ret = new ArrayList<PointDesc>();
		ret.add(new PointDesc("stadium", 61786314, 34356570).setCategory("sport"));
		ret.add(new PointDesc("trainstation", 61784364,34344790).setCategory("culture"));
		ret.add(new PointDesc("stadium", 61787309,34338836).setCategory("culture"));
		ret.add(new PointDesc("forest", 61780720,34289472).setCategory("sport"));

		notifyPointsReady(ret);
	}

	@Override
	public String getName() {
		return "TestPointsLoader";
	}
}