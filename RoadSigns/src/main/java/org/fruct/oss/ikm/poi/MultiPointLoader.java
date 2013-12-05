package org.fruct.oss.ikm.poi;

import java.util.ArrayList;
import java.util.List;

public class MultiPointLoader extends PointLoader {
	private ArrayList<PointLoader> loaders = new ArrayList<PointLoader>();

	public void addLoader(PointLoader loader) {
		this.loaders.add(loader);
	}

	@Override
	public void loadPoints() {
	}
}
