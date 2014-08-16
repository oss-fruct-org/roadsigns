package org.fruct.oss.ikm.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Region {
	private static final double DELTA = 0.0001;
	private final List<Polygon> polygons;

	public Region(InputStream input) throws IOException {
		this.polygons = Polygon.fromPath(input);
	}

	public boolean testHit(double rLat, double rLon) {
		// Test AABB
		boolean aabbHit = false;
		for (Polygon polygon : polygons) {
			if (polygon.testHitAABB(rLat, rLon)) {
				aabbHit = true;
				break;
			}
		}

		if (!aabbHit)
			return false;

		int intersections = 0;
		for (Polygon polygon : polygons) {
			for (int i = 0; i < polygon.lats.length - 1; i++) {
				final double aLat = polygon.lats[i];
				final double aLon = polygon.lons[i];
				final double bLat = polygon.lats[i + 1];
				final double bLon = polygon.lons[i + 1];

				if (testHitLine(rLat, polygon.aLon, rLon, aLat, aLon, bLat, bLon)) {
					intersections++;
				}
			}
		}

		return intersections % 2 != 0;
	}

	private boolean testHitLine(double rLat, double rLon0, double rLon1, double aLat, double aLon, double bLat, double bLon) {
		if (aLat < rLat && bLat < rLat || aLat > rLat && bLat > rLat)
			return false;

		if (rLon0 >= rLon1) {
			return false;
		}

		if (aLon < rLon0 && bLon < rLon0 || aLon > rLon1 && bLon > rLon1)
			return false;

		final double dy = bLat - aLat;
		if (Math.abs(dy) < DELTA) {
			return false;
		}

		final double r = ((bLon - aLon) * (rLat - aLat) + dy * (aLon - rLon0)) / dy;

		return (r > 0 && r < rLon1 - rLon0);
	}

	public static class Polygon {
		public double[] lats;
		public double[] lons;

		public double aLat, aLon, bLat, bLon;

		private Polygon(double[] lats, double[] lons) {
			this.lats = lats;
			this.lons = lons;
			// Calculate AABB
			if (lats.length > 0) {
				aLat = bLat = lats[0];
				aLon = bLon = lons[0];

				for (int i = 1; i < lats.length; i++) {
					if (lats[i] < aLat) {
						aLat = lats[i];
					}

					if (lats[i] > bLat) {
						bLat = lats[i];
					}
				}

				for (int i = 1; i < lons.length; i++) {
					if (lons[i] < aLon) {
						aLon = lons[i];
					}

					if (lons[i] > bLon) {
						bLon = lons[i];
					}
				}
			}
		}

		public boolean testHitAABB(double rLat, double rLon) {
			return aLat <= rLat && aLon <= rLon && bLat >= rLat && bLon >= rLon;
		}

		public static List<Polygon> fromPath(InputStream input) throws IOException {
			List<Polygon> ret = new ArrayList<Polygon>();
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));

			// Skip name
			reader.readLine();

			// Skip first section name
			reader.readLine();

			String line;
			do {
				Builder builder = new Builder();

				line = reader.readLine();
				while (!line.startsWith("END")) {
					StringTokenizer tok = new StringTokenizer(line);
					double lon = Double.parseDouble(tok.nextToken());
					double lat = Double.parseDouble(tok.nextToken());
					builder.add(lat, lon);
					line = reader.readLine();
				}

				ret.add(builder.build());
				reader.readLine();
			} while (!line.startsWith("END"));

			return ret;
		}

		public static class Builder {
			private List<Double> lats = new ArrayList<Double>();
			private List<Double> lons = new ArrayList<Double>();

			private void add(double lat, double lon) {
				lats.add(lat);
				lons.add(lon);
			}

			private Polygon build() {
				int size = this.lats.size();
				if (!this.lats.get(0).equals(this.lats.get(size - 1)) || !this.lons.get(0).equals(this.lons.get(size - 1))) {
					this.lats.add(this.lats.get(size - 1));
					this.lons.add(this.lons.get(size - 1));
				}

				double[] lats = new double[this.lats.size()/* + 1*/];
				double[] lons = new double[this.lons.size()/* + 1*/];

				int c = 0;
				for (double d : this.lats) {
					lats[c++] = d;
				}

				int c1 = 0;
				for (double d : this.lons) {
					lons[c1++] = d;
				}

				//lats[lats.length - 1] = this.lats.get(0);
				//lons[lons.length - 1] = this.lons.get(0);

				return new Polygon(lats, lons);
			}
		}
	}
}
