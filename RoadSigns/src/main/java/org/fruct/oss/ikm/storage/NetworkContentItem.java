package org.fruct.oss.ikm.storage;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;

import java.io.IOException;

@Root(name = "file", strict = false)
public class NetworkContentItem implements ContentItem {
	@Element(name = "name")
	private String name;

	@Element(name = "type")
	private String type;

	@Element(name = "size")
	private int size;

	@Element(name = "url")
	private Url url;

	@Element(name = "hash")
	private String hash;

	@Element(name = "description", required = false)
	private String description;

	@Element(name = "region-id")
	private String regionId;

	private NetworkStorage storage;

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getType() {
		return type;
	}

	public int getSize() {
		return size;
	}

	public int getDownloadSize() {
		return url.size == -1 ? size : url.size;
	}

	public String getUrl() {
		return url.url;
	}

	public String getHash() {
		return hash;
	}

	@Override
	public String getRegionId() {
		return regionId;
	}

	@Override
	public boolean isDownloadable() {
		return true;
	}

	@Override
	public boolean isReadonly() {
		return true;
	}

	public String getDescription() {
		return description;
	}

	public String getCompression() {
		return url.compression;
	}

	@Override
	public String getStorage() {
		return getClass().getName();
	}

	@Root(name = "url")
	private static class Url {
		@Attribute(name = "compression", required = false)
		String compression;

		@Attribute(name = "size", required = false)
		int size = -1;

		@Text
		String url;
	}

	void setNetworkStorage(NetworkStorage storage) {
		this.storage = storage;
	}

	@Override
	public ContentConnection loadContentItem() throws IOException {
		return storage.loadContentItem(getUrl());
	}

}