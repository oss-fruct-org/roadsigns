package org.fruct.oss.ikm.points.gets.parsers;

import org.fruct.oss.ikm.points.Point;
import org.fruct.oss.ikm.points.gets.IContent;

import java.util.List;

public class Kml implements IContent {
	List<Point> points;

	public List<Point> getPoints() {
		return points;
	}
}
