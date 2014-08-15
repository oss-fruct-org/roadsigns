package org.fruct.oss.ikm.storage;

import android.os.AsyncTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class RegionsTask extends AsyncTask<RegionsTask.RegionTasksArg, Integer, String> {
	private static final double DELTA = 0.001;

	static class RegionTasksArg {
		RegionTasksArg(List<ContentItem> dirs, double lat, double lon) {
			this.dirs = dirs;
			this.lat = lat;
			this.lon = lon;
		}

		List<ContentItem> dirs;
		double lat;
		double lon;
	}

	private static final Logger log = LoggerFactory.getLogger(RegionsTask.class);

	@Override
	protected String doInBackground(RegionTasksArg... args) {
		RegionTasksArg arg = args[0];
		HashMap<String, List<Region>> regions = new HashMap<String, List<Region>>();

		for (ContentItem dir : arg.dirs) {
			if (dir.getType().equals("graphhopper-map") && dir instanceof DirectoryContentItem) {
				try {
					regions.put(dir.getRegionId(), Region.fromContentItem((DirectoryContentItem) dir));
				} catch (Exception e) {
					log.warn("Can't read polygon file from content item " + dir.getName(), e);
				}
			}

			if (Thread.interrupted())
				return null;
		}

		for (Map.Entry<String, List<Region>> entry : regions.entrySet()) {
			if (testHit(arg.lat, arg.lon, entry.getValue())) {
				return entry.getKey();
			}

			if (Thread.interrupted())
				return null;
		}

		return null;
	}

	private boolean testHit(double rLat, double rLon, List<Region> regions) {
		// Test AABB
		boolean aabbHit = false;
		for (Region region : regions) {
			if (region.testHitAABB(rLat, rLon)) {
				aabbHit = true;
				break;
			}
		}

		if (!aabbHit)
			return false;

		int intersections = 0;
		for (Region region : regions) {
			for (int i = 0; i < region.lats.length - 1; i++) {
				final double aLat = region.lats[i];
				final double aLon = region.lons[i];
				final double bLat = region.lats[i + 1];
				final double bLon = region.lons[i + 1];

				if (testHitLine(rLat, region.aLon, rLon, aLat, aLon, bLat, bLon)) {
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

	private static class Region {
		private String regionId;
		private double[] lats;
		private double[] lons;

		private double aLat, aLon, bLat, bLon;

		private Region(String regionId, double[] lats, double[] lons) {
			this.lats = lats;
			this.lons = lons;
			this.regionId = regionId;
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

		private boolean testHitAABB(double rLat, double rLon) {
			return aLat <= rLat && aLon <= rLon && bLat >= rLat && bLon >= rLon;
		}

		private static List<Region> fromContentItem(DirectoryContentItem directoryContentItem) throws IOException {

			List<Region> ret = new ArrayList<Region>();

			String path = directoryContentItem.getPath();
			File file = new File(path);

			ZipFile zipFile = new ZipFile(file, ZipFile.OPEN_READ);
			ZipEntry entry = zipFile.getEntry("polygon.poly");

			BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));

			// Skip name
			reader.readLine();

			// Skip first section name
			reader.readLine();

			String line;
			do {
				Builder builder = new Builder(directoryContentItem.getRegionId());

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

		private static class Builder {
			private final String regionId;
			private List<Double> lats = new ArrayList<Double>();
			private List<Double> lons = new ArrayList<Double>();

			private Builder(String regionId) {
				this.regionId = regionId;
			}

			private void add(double lat, double lon) {
				lats.add(lat);
				lons.add(lon);
			}

			private Region build() {
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

				return new Region(regionId, lats, lons);
			}
		}
	}
}
