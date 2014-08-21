package org.fruct.oss.ikm.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class ContentType {
	private static final Logger log = LoggerFactory.getLogger(ContentType.class);

	protected final String id;
	protected final String configKey;

	private final List<ContentItem> contentItems = new ArrayList<ContentItem>();

	private String currentItemHash;
	private ContentItem currentItem;

	protected SharedPreferences pref;

	protected ContentType(Context context, String id, String configKey) {
		this.id = id;
		this.pref = PreferenceManager.getDefaultSharedPreferences(context);
		this.configKey = configKey;

		currentItemHash = pref.getString(configKey, null);
	}

	synchronized void applyLocation(Location location) {
		for (ContentItem contentItem : contentItems) {
			if (checkLocation(location, contentItem)) {
				setCurrentItem(contentItem);
				activateItem(contentItem);
				break;
			}
		}
	}

	synchronized void prepare() {
		contentItems.clear();
	}

	synchronized boolean addContentItem(ContentItem item) {
		if (!acceptsContentItem(item)) {
			return false;
		}

		if (currentItem == null && currentItemHash != null && currentItemHash.equals(item.getHash())) {
			if (!isCurrentItemActive(item)) {
				log.warn("Current active content item don't correspond with stored item");
				currentItemHash = null;
				pref.edit().remove(configKey).apply();
			} else {
				setCurrentItem(item);
			}
		}

		contentItems.add(item);
		return true;
	}

	synchronized boolean removeContentItem(ContentItem item) {
		if (item == currentItem)
			throw new IllegalArgumentException("Can't remove active item");

		return contentItems.remove(item);
	}

	synchronized public void updateContentItem(ContentItem item) {
		for (int i = 0, contentItemsSize = contentItems.size(); i < contentItemsSize; i++) {
			ContentItem exItem = contentItems.get(i);
			if (exItem.getName().equals(item.getName())) {
				contentItems.set(i, item);
				setCurrentItem(item);
				break;
			}
		}
	}

	synchronized void commitContentItems() {
		if (currentItemHash != null && currentItem == null) {
			currentItemHash = null;
			deactivateCurrentItem();
		}
	}

	protected abstract boolean checkLocation(Location location, ContentItem contentItem);

	protected abstract void activateItem(ContentItem item);
	protected abstract void deactivateCurrentItem();
	protected abstract boolean isCurrentItemActive(ContentItem item);
	public abstract void invalidateCurrentContent();

	boolean acceptsContentItem(ContentItem contentItem) {
		return id.equals(contentItem.getType());
	}

	ContentItem getCurrentItem() {
		return currentItem;
	}

	private void setCurrentItem(ContentItem item) {
		currentItem = item;
		currentItemHash = currentItem.getHash();
		pref.edit().putString(configKey, currentItemHash).apply();
	}

}