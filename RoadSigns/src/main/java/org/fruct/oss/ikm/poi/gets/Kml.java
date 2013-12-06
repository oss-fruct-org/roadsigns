package org.fruct.oss.ikm.poi.gets;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;

import java.util.List;
import java.util.StringTokenizer;

@Root(name = "kml", strict = false)
public class Kml {
	public Document getDocument() {
		return document;
	}

	@Element(name = "Document")
	private Document document;

	@Root(strict = false)
	public static class Document {
		public static String getQwe() {
			return "qwe";
		}

		public List<Placemark> getPlacemarks() {
			return placemarks;
		}

		public String getName() {
			return name;
		}

		public int getOpen() {
			return open;
		}

		public String getDescription() {
			return description;
		}

		@Element(name = "name")
		private String name;

		@Element(name = "open")
		private int open;

		@Element(name = "description", data=true)
		private String description;

		@ElementList(inline = true, entry = "Placemark")
		private List<Placemark> placemarks;
	}

	@Root(strict = false)
	public static class Placemark {
		@Element(name = "name")
		private String name;

		@Element(name = "description", data=true)
		private String description;

		private double latitude;
		private double longitude;

		@Element(name="coordinates")
		@Path("Point")
		public void setCoordinates(String coordinates) {
			StringTokenizer tok = new StringTokenizer(coordinates, ",", false);

			latitude = Double.parseDouble(tok.nextToken());
			longitude = Double.parseDouble(tok.nextToken());
		}

		@Element(name="coordinates")
		@Path("Point")
		public String getCoordinates() {
			return latitude + ", " + longitude + ", 0.0";
		}

		public String getName() {
			return name;
		}

		public String getDescription() {
			return description;
		}

		public double getLatitude() {
			return latitude;
		}

		public double getLongitude() {
			return longitude;
		}
	}
}
