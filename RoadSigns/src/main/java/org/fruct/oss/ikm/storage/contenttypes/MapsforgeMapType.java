package org.fruct.oss.ikm.storage.contenttypes;

import android.content.Context;
import android.location.Location;

import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.storage.ContentItem;
import org.fruct.oss.ikm.storage.ContentType;
import org.fruct.oss.ikm.storage.DirectoryContentItem;
import org.fruct.oss.ikm.storage.RemoteContentService;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.reader.MapDatabase;
import org.mapsforge.map.reader.header.FileOpenResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class MapsforgeMapType extends ContentType {
	private static final Logger log = LoggerFactory.getLogger(MapsforgeMapType.class);

	public MapsforgeMapType(Context context) {
		super(context, RemoteContentService.MAPSFORGE_MAP, "mapsforge-map-current-hash");
	}

	@Override
	protected boolean checkLocation(Location location, ContentItem contentItem) {
		boolean ret = false;

		DirectoryContentItem dItem = (DirectoryContentItem) contentItem;
		MapDatabase mapDatabase = new MapDatabase();
		FileOpenResult result = mapDatabase.openFile(new File(dItem.getPath()));
		if (!result.isSuccess()) {
			log.error("Can't read map database {}", result.getErrorMessage());
			return false;
		}

		BoundingBox bbox = mapDatabase.getMapFileInfo().boundingBox;
		if (bbox.contains(new LatLong(location.getLatitude(), location.getLongitude()))) {
			// TODO: add precise detection
			ret = true;
		}

		mapDatabase.closeFile();

		return ret;
	}

	@Override
	protected void activateItem(ContentItem item) {
		pref.edit().putString(SettingsActivity.OFFLINE_MAP, item.getName()).apply();
	}

	@Override
	protected void deactivateCurrentItem() {

	}

	@Override
	protected boolean isCurrentItemActive(ContentItem item) {
		return true;
	}

	@Override
	public void invalidateCurrentContent() {
		currentItem = null;
		pref.edit().remove(SettingsActivity.OFFLINE_MAP)
				.remove(configKey)
				.apply();
	}
}
