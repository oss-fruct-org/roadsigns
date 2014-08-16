package org.fruct.oss.ikm.storage;

import android.os.AsyncTask;

import org.fruct.oss.ikm.utils.Region;
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
		HashMap<String, Region> regions = new HashMap<String, Region>();

		for (ContentItem dir : arg.dirs) {
			if (dir.getType().equals("graphhopper-map") && dir instanceof DirectoryContentItem) {
				ZipFile zipFile = null;
				try {
					File file = new File(((DirectoryContentItem) dir).getPath());

					zipFile = new ZipFile(file, ZipFile.OPEN_READ);
					ZipEntry entry = zipFile.getEntry("polygon.poly");

					regions.put(dir.getRegionId(), new Region(zipFile.getInputStream(entry)));
				} catch (Exception e) {
					log.warn("Can't read polygon file from content item " + dir.getName(), e);
				} finally {
					if (zipFile != null) {
						try {
							zipFile.close();
						} catch (IOException ignored) {
						}
					}
				}
			}

			if (Thread.interrupted())
				return null;
		}

		for (Map.Entry<String, Region> entry : regions.entrySet()) {
			if (entry.getValue().testHit(arg.lat, arg.lon)) {
				return entry.getKey();
			}

			if (Thread.interrupted())
				return null;
		}

		return null;
	}

}
