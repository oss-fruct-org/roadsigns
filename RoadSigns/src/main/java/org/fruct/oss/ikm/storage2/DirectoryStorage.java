package org.fruct.oss.ikm.storage2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DirectoryStorage implements ContentStorage {
	private static final Logger log = LoggerFactory.getLogger(DirectoryStorage.class);

	private final String path;
	private List<ContentItem> items;

	public DirectoryStorage(String path) {
		if (path == null)
			throw new IllegalArgumentException("Path must not be null");

		this.path = path;
	}

	@Override
	public void updateContentList() throws IOException {
		File dir = new File(path);
		if (!dir.exists() || !dir.isDirectory())
			throw new FileNotFoundException("Wrong directory " + path);

		items = new ArrayList<ContentItem>();
		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				ContentItem item = createContentItem(file);
				if (item != null) {
					items.add(item);
				} else {
					log.warn("Unsupported file " + file.getName() + " found in directory " + dir.getName());
				}
			}
		}
	}

	private ContentItem createContentItem(File file) throws FileNotFoundException {
		if (file.getName().endsWith(".map")) {
			DirectoryContentItem item =  createBaseContentItem(file);
			item.setType("mapsforge-map");
			fillMapMetadata(file, item);
			return item;
		} else if (file.getName().endsWith(".ghz")) {
			DirectoryContentItem item =  createBaseContentItem(file);
			item.setType("graphhopper-map");
			fillGhzMetadata(file, item);
			return item;
		} else {
			return null;
		}
	}

	private DirectoryContentItem createBaseContentItem(File file) throws FileNotFoundException {
		return new DirectoryContentItem(this, file.getName());
	}

	private void fillGhzMetadata(File file, DirectoryContentItem item) throws FileNotFoundException {
		item.setDescription(file.getName());
	}

	private void fillMapMetadata(File file, DirectoryContentItem item) throws FileNotFoundException {
		item.setDescription(file.getName());
	}

	@Override
	public List<ContentItem> getContentList() {
		return items;
	}

	public String getPath() {
		return path;
	}

	public String getStorageName() {
		return "directory-storage";
	}
}
