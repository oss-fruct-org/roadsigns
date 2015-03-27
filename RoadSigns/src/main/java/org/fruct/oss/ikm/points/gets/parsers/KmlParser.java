package org.fruct.oss.ikm.points.gets.parsers;

import org.fruct.oss.ikm.points.Point;
import org.fruct.oss.ikm.points.gets.ContentParser;
import org.fruct.oss.ikm.points.gets.XmlUtil;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class KmlParser implements ContentParser<Kml> {
	@Override
	public Kml parse(XmlPullParser parser) throws IOException, XmlPullParserException {
		Kml kml = new Kml();
		ArrayList<Point> points = new ArrayList<>();

		parser.require(XmlPullParser.START_TAG, null, "content");
		parser.nextTag();
		parser.require(XmlPullParser.START_TAG, null, "kml");
		parser.nextTag();
		parser.require(XmlPullParser.START_TAG, null, "Document");

		while (parser.next() != XmlPullParser.END_TAG) {
			if (parser.getEventType() != XmlPullParser.START_TAG)
				continue;

			String tagName = parser.getName();
			switch (tagName) {
			case "Placemark":
				points.add(parsePoint(parser));
				parser.require(XmlPullParser.END_TAG, null, "Placemark");
				break;
			default:
				XmlUtil.skip(parser);
				break;
			}
		}

		parser.require(XmlPullParser.END_TAG, null, "Document");
		parser.nextTag();
		parser.require(XmlPullParser.END_TAG, null, "kml");
		parser.nextTag();
		parser.require(XmlPullParser.END_TAG, null, "content");

		kml.points = points;
		return kml;
	}

	public static Point parsePoint(XmlPullParser parser) throws IOException, XmlPullParserException {
		parser.require(XmlPullParser.START_TAG, null, "Placemark");

		String name = null;
		String description = null;
		String kmlCoordinates = null;
		ExtendedData extendedData = null;

		while (parser.next() != XmlPullParser.END_TAG) {
			if (parser.getEventType() != XmlPullParser.START_TAG)
				continue;

			String tagName = parser.getName();
			switch (tagName) {
			case "name":
				name = XmlUtil.readText(parser);
				parser.require(XmlPullParser.END_TAG, null, "name");
				break;

			case "description":
				description = XmlUtil.readText(parser);
				parser.require(XmlPullParser.END_TAG, null, "description");
				break;

			case "Point":
				parser.nextTag();
				parser.require(XmlPullParser.START_TAG, null, "coordinates");

				kmlCoordinates = XmlUtil.readText(parser);

				parser.nextTag();
				parser.require(XmlPullParser.END_TAG, null, "Point");
				break;

			case "ExtendedData":
				extendedData = readExtendedData(parser);
				parser.require(XmlPullParser.END_TAG, null, "ExtendedData");
				break;

			default:
				XmlUtil.skip(parser);
				break;
			}
		}

		if (name == null || kmlCoordinates == null) {
			throw new XmlPullParserException("No name or coordinates");
		}

		StringTokenizer tok = new StringTokenizer(kmlCoordinates, ",", false);
		double longitude = Double.parseDouble(tok.nextToken());
		double latitude = Double.parseDouble(tok.nextToken());

		Point point = new Point(name, (int) (latitude * 1e6), (int) (longitude * 1e6));
		if (description != null) {
			point.setDescription(description);
		}

		if (extendedData != null) {
			point.setUuid(extendedData.uuid);
		}

		return point;
	}

	private static ExtendedData readExtendedData(XmlPullParser parser) throws IOException, XmlPullParserException {
		ExtendedData extendedData = new ExtendedData();

		parser.require(XmlPullParser.START_TAG, null, "ExtendedData");
		while (parser.next() != XmlPullParser.END_TAG) {
			if (parser.getEventType() != XmlPullParser.START_TAG)
				continue;

			String tagName = parser.getName();
			if (tagName.equals("Data")) {
				String key = parser.getAttributeValue(null, "name");
				if (key == null)
					throw new XmlPullParserException("Data tag have to have attribute 'name'");

				parser.nextTag();
				parser.require(XmlPullParser.START_TAG, null, "value");
				String value = XmlUtil.readText(parser);

				switch (key) {
				case "uuid":
					extendedData.uuid = value;
					break;
				}
				parser.nextTag();
				parser.require(XmlPullParser.END_TAG, null, "Data");
			} else {
				XmlUtil.skip(parser);
			}
		}

		return extendedData;
	}

	private static class ExtendedData {
		String uuid;
	}
}
