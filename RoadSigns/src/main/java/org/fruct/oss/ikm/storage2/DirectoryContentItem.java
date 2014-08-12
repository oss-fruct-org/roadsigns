package org.fruct.oss.ikm.storage2;

import org.fruct.oss.ikm.storage.IContentConnection;
import org.fruct.oss.ikm.utils.Utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class DirectoryContentItem implements ContentItem {
	private final KeyValue digestCache;
	private final DirectoryStorage storage;

	private String type;
	private String name;
	private String description;

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
			try {
				FileInputStream input = new FileInputStream(getPath());
				hash = Utils.hashStream(input);
				digestCache.put(name, hash);
			} catch (IOException e) {
				throw new RuntimeException("Can't get hash of file " + name);

			}
		}

		return hash;
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
}
