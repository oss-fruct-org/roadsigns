package org.fruct.oss.ikm.storage;

import org.fruct.oss.ikm.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DirectoryContentItem implements ContentItem {
	private static final Logger log = LoggerFactory.getLogger(DirectoryStorage.class);

	private final KeyValue digestCache;
	private final DirectoryStorage storage;

	private String type;
	private String name;
	private String description;
	private String regionId;

	private String hash;

	public DirectoryContentItem(DirectoryStorage storage, KeyValue digestCache, String name) {
		this.storage = storage;
		this.name = name;
		this.digestCache = digestCache;
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

	@Override
	public String getHash() {
		if (hash != null) {
			return hash;
		}

		hash = digestCache.get(name);

		if (hash == null) {
			log.trace("Sha1 of {}", getPath());

			try {
				FileInputStream input = new FileInputStream(getPath());
				hash = Utils.hashStream(input, "sha1");
				digestCache.put(name, hash);
			} catch (IOException e) {
				throw new RuntimeException("Can't get hash of file " + name);
			}
		}

		return hash;
	}

	@Override
	public String getRegionId() {
		return regionId;
	}

	@Override
	public boolean isDownloadable() {
		return false;
	}

	@Override
	public boolean isReadonly() {
		return !(storage instanceof WritableDirectoryStorage);
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

	public void setHash(String hash) {
		this.hash = hash;
	}

	public void setRegionId(String regionId) {
		this.regionId = regionId;
	}

}
