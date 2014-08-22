package org.fruct.oss.ikm.storage.contenttypes;

import android.content.Context;
import android.location.Location;

import com.graphhopper.util.Unzipper;

import org.fruct.oss.ikm.DataService;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.storage.ContentItem;
import org.fruct.oss.ikm.storage.ContentType;
import org.fruct.oss.ikm.storage.DirectoryContentItem;
import org.fruct.oss.ikm.storage.RemoteContentService;
import org.fruct.oss.ikm.utils.Region;
import org.fruct.oss.ikm.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GraphhopperMapType extends ContentType {
	private static final Logger log = LoggerFactory.getLogger(GraphhopperMapType.class);

	private final DataService dataService;
	private Map<String, Region> regions;

	public GraphhopperMapType(Context context, DataService dataService, Map<String, Region> regions) {
		super(context, RemoteContentService.GRAPHHOPPER_MAP, "graphhopper-map-current-hash");
		this.dataService = dataService;
		this.regions = regions;
	}

	@Override
	protected void onItemAdded(ContentItem item) {
		try {
			regions.put(item.getRegionId(), createRegion(item));
		} catch (IOException e) {
			log.error("Can't create region from content item {}", item.getName());
		}
	}

	@Override
	protected boolean checkLocation(Location location, ContentItem contentItem) {
		Region region = regions.get(contentItem.getRegionId());
		if (region == null) {
			return false;
		} else {
			return region.testHit(location.getLatitude(), location.getLongitude());
		}
	}
	private Region createRegion(ContentItem item) throws IOException {
		File file = new File(((DirectoryContentItem) item).getPath());

		ZipFile zipFile = new ZipFile(file, ZipFile.OPEN_READ);
		ZipEntry entry = zipFile.getEntry("polygon.poly");

		return new Region(zipFile.getInputStream(entry));
	}

	@Override
	protected void activateItem(ContentItem item) {
		DirectoryContentItem dItem = (DirectoryContentItem) item;

		String dataPath = dataService.getDataPath();
		String ghPath = dataPath + "/graphhopper";

		String regionName = "gh-" + item.getHash();
		String regionPath = ghPath + "/" + regionName;

		log.info("Extracting graphhopper archive {}", regionPath);

		try {
			new Unzipper().unzip(dItem.getPath(), regionPath, false);
			pref.edit().putString(SettingsActivity.NAVIGATION_DATA, regionName).apply();
			log.info("Graphhopper archive successfully extracted");
		} catch (IOException e) {
			log.error("Can't extract archive {}", dItem.getPath());
			// TODO: handle this error
		}
	}

	@Override
	protected boolean isCurrentItemActive(ContentItem item) {
		String regionName = "gh-" + item.getHash();
		return regionName.equals(pref.getString(SettingsActivity.NAVIGATION_DATA, null));
	}

	@Override
	public void invalidateCurrentContent() {
		String navigationPath = pref.getString(SettingsActivity.NAVIGATION_DATA, null);
		if (navigationPath != null) {
			Utils.deleteDir(new File(navigationPath));
		}

		currentItem = null;
		pref.edit().remove(SettingsActivity.NAVIGATION_DATA)
				.remove(configKey)
				.apply();
	}

	@Override
	protected void deactivateCurrentItem() {
		pref.edit().remove(SettingsActivity.NAVIGATION_DATA)
				.remove(configKey)
				.apply();
		super.deactivateCurrentItem();
	}
}
