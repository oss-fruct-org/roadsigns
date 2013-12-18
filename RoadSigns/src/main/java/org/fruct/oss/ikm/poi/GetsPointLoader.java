package org.fruct.oss.ikm.poi;

import org.fruct.oss.ikm.poi.gets.Gets;
import org.fruct.oss.ikm.poi.gets.LoginException;

import java.io.IOException;
import java.util.List;

public class GetsPointLoader extends PointLoader {
	private Gets gets;

	public GetsPointLoader(String url) {
		gets = new Gets(url);
	}

	@Override
	public void loadPoints() throws IOException, LoginException {
		final List<PointDesc> points = gets.getPoints(null);
		notifyPointsReady(points);
	}
}
