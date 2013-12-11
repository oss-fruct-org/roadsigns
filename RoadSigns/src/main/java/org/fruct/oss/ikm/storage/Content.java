package org.fruct.oss.ikm.storage;

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
@Root(name = "content")
public class Content {
	@ElementList(inline = true, entry = "file", type = ContentItem.class, empty = false, required = false)
	private List<IContentItem> content;

	public Content(@ElementList(inline = true, entry = "file", type = ContentItem.class, empty = false, required = false)
				   List<IContentItem> content_) {
		this.content = (content_ == null ? new ArrayList<IContentItem>() : content_);
	}

	public List<IContentItem> getContent() {
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
}
