package org.fruct.oss.ikm.storage;

import java.io.IOException;

public interface IProvider {
	IContentConnection loadContentItem(String url) throws IOException;
}
