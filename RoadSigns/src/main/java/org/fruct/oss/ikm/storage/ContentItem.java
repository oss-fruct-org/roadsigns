package org.fruct.oss.ikm.storage;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root(name = "file")
public class ContentItem implements IContentItem {
	@Element(name = "name")
	private String name;

	@Element(name = "type")
	private String type;

	@Element(name = "size")
	private int size;

	@Element(name = "url")
	private String url;

	@Element(name = "hash")
	private String hash;

	public ContentItem(@Element(name = "name") String name,
					   @Element(name = "type") String type,
					   @Element(name = "size") int size,
					   @Element(name = "url")  String url,
					   @Element(name = "hash") String hash) {
		this.name = name;
		this.type = type;
		this.size = size;
		this.url = url;
		this.hash = hash;
	}

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
	public String getUrl() {
		return url;
	}

	@Override
	public String getHash() {
		return hash;
	}
}
