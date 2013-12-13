package org.fruct.oss.ikm.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface IStorage extends IProvider {
	void interrupt();
	void storeContentItem(String url, InputStream stream) throws IOException;
	void deleteItem(String url);
}
