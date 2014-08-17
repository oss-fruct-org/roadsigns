package org.fruct.oss.ikm.storage;

import java.io.IOException;

public interface ContentItem {
	String getType();
	String getName();
	String getDescription();
	String getStorage();
	String getHash();
	String getRegionId();

	boolean isDownloadable();
	boolean isReadonly();

	ContentConnection loadContentItem() throws IOException;
}
