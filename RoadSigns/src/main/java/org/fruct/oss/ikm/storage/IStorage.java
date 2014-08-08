package org.fruct.oss.ikm.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface IStorage extends IProvider {
	static interface MigrationListener {
		void fileCopying(String name, int n, int max);
	}

	/**
	 * Function migrates storage to new location
	 * @param targetPath new location
	 * @param listener progress callback
	 */
	void migrate(String targetPath, MigrationListener listener) throws IOException;
	void storeContentItem(String url, InputStream stream) throws IOException;
	void deleteItem(String url);
}
