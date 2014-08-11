package org.fruct.oss.ikm.storage2;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps content xml to java object
 */
@Root(name = "content", strict = false)
class Content {
	@ElementList(inline = true, entry = "file", type = NetworkContentItem.class, empty = false, required = false)
	private List<NetworkContentItem> content;

	public Content(@ElementList(inline = true, entry = "file", type = NetworkContentItem.class, empty = false, required = false)
				   List<NetworkContentItem> content_) {
		this.content = (content_ == null ? new ArrayList<NetworkContentItem>() : content_);
	}

	public List<NetworkContentItem> getContent() {
		return content;
	}

	public static Content createFromStream(InputStream stream) throws IOException {
		Serializer serializer = new Persister();
		try {
			return serializer.read(Content.class, stream);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@ElementList(inline = true, entry = "include", type = String.class, empty = false, required = false)
	private List<String> includes;

	public List<String> getIncludes() {
		return includes;
	}
}
