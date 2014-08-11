package org.fruct.oss.ikm.storage2;

import org.fruct.oss.ikm.storage.IContentConnection;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DirectoryContentItem implements ContentItem {
	private DirectoryStorage storage;

	private String type;
	private String name;
	private String description;

	public DirectoryContentItem(DirectoryStorage storage, String name) {
		this.storage = storage;
		this.name = name;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getStorage() {
		return storage.getStorageName();
	}

	public String getPath() {
		return storage.getPath() + "/" + getName();
	}

	@Override
	public ContentConnection loadContentItem() throws IOException {
		final InputStream stream = new FileInputStream(getPath());
		return new ContentConnection() {
			@Override
			public InputStream getStream() {
				return stream;
			}

			@Override
			public void close() {
				try {
					stream.close();
				} catch (IOException ignored) {
				}
			}
		};
	}
}
