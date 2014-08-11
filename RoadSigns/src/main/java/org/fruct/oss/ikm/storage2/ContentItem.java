package org.fruct.oss.ikm.storage2;

import java.io.File;
import java.io.IOException;

public interface ContentItem {
	String getType();
	String getName();
	String getDescription();
	String getStorage();

	ContentConnection loadContentItem() throws IOException;
}
