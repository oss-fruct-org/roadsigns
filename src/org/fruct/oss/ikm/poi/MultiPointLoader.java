package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;

public class MultiPointLoader implements PointLoader {
	private ArrayList<PointLoader> loaders = new ArrayList<PointLoader>();

	public void addLoader(PointLoader loader) {
		this.loaders.add(loader);
	}

	@Override
	public List<PointDesc> getPoints() {
		ArrayList<PointDesc> ret = new ArrayList<PointDesc>();

		for (PointLoader loader :loaders)
			ret.addAll(loader.getPoints());

		return ret;
	}
}
