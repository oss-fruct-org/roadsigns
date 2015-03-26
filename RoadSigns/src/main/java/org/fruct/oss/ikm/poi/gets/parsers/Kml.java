package org.fruct.oss.ikm.poi.gets.parsers;

import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.gets.IContent;

import java.util.List;

public class Kml implements IContent {
	List<PointDesc> points;

	public List<PointDesc> getPoints() {
		return points;
	}
}
