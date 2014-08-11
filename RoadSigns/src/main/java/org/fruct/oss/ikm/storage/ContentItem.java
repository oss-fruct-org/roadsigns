package org.fruct.oss.ikm.storage;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;

@Root(name = "file", strict = false)
public class ContentItem implements IContentItem {
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

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public int getSize() {
		return size;
	}

	@Override
	public int getDownloadSize() {
		return url.size == -1 ? size : url.size;
	}

	@Override
	public String getUrl() {
		return url.url;
	}

	@Override
	public String getHash() {
		return hash;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getCompression() {
		return url.compression;
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
}
