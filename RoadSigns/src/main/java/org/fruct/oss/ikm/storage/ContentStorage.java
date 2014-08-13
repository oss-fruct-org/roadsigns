package org.fruct.oss.ikm.storage;

import java.io.IOException;
import java.util.List;

interface ContentStorage {
	void updateContentList() throws IOException;
	List<ContentItem> getContentList();
}
