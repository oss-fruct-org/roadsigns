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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.WeakHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GraphhopperMapType extends ContentType {
	private static final Logger log = LoggerFactory.getLogger(GraphhopperMapType.class);

	private final DataService dataService;

	private final transient WeakHashMap<ContentItem, Region> regionsCache = new WeakHashMap<ContentItem, Region>();

	public GraphhopperMapType(Context context, DataService dataService) {
		super(context, RemoteContentService.GRAPHHOPPER_MAP, "graphhopper-map-current-hash");
		this.dataService = dataService;
	}

	@Override
	protected boolean checkLocation(Location location, ContentItem contentItem) {
		Region region = regionsCache.get(contentItem);
		if (region == null) {
			try {
				region = createRegion(contentItem);
				regionsCache.put(contentItem, region);
			} catch (IOException e) {
				log.error("Can't create region from content item {}", contentItem.getName());
				return false;
			}
		}

		return region.testHit(location.getLatitude(), location.getLongitude());
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

		try {
			new Unzipper().unzip(dItem.getPath(), regionPath, false);
			pref.edit().putString(SettingsActivity.NAVIGATION_DATA, regionName).apply();
		} catch (IOException e) {
			log.error("Can't extract archive {}", dItem.getPath());
			// TODO: handle this error
		}
	}

	@Override
	protected void deactivateCurrentItem() {

	}

	@Override
	public void invalidateCurrentContent() {
		pref.edit().remove(SettingsActivity.NAVIGATION_DATA).apply();
	}
}
