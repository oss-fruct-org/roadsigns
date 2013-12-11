package org.fruct.oss.ikm.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface IStorage {
	/**
	 * Return all available content in storage
	 * @return content
	 */
	List<IContentItem> getContent() throws IOException;

	/**
	 * Store content item in storage
	 * @param item
	 * @param stream
	 */
	void storeContentItem(IContentItem item, InputStream stream) throws IOException;

	/**
	 *
	 * @param name Filename
	 * @return Content of file
	 * @throws IOException
	 */
	IContentConnection loadContentItem(String name) throws IOException;
}
