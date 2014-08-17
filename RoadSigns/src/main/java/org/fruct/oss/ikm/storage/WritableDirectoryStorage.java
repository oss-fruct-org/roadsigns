package org.fruct.oss.ikm.storage;

import org.fruct.oss.ikm.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class WritableDirectoryStorage extends DirectoryStorage {
	private static final Logger log = LoggerFactory.getLogger(WritableDirectoryStorage.class);

	public WritableDirectoryStorage(KeyValue digestCache, String path) {
		super(digestCache, path);

		File file = new File(path);
		if (!file.mkdirs() && !file.isDirectory()) {
			log.warn("Can't mkdirs new path");
		}
	}

	public void deleteContentItem(ContentItem contentItem) {
		DirectoryContentItem directoryContentItem = (DirectoryContentItem) contentItem;
		File file = new File(directoryContentItem.getPath());
		file.delete();
		items.remove(contentItem);
	}

	public ContentItem storeContentItem(ContentItem remoteContentItem, InputStream input) throws IOException {
		OutputStream output = null;

		String fileStr = path + "/" + remoteContentItem.getName();

		File outputFile = new File(fileStr + ".roadsignsdownload");
		File targetFile = new File(fileStr);

		try {
			output = new FileOutputStream(outputFile);

			Utils.copyStream(input, output);

			if (!outputFile.renameTo(targetFile))
				throw new IOException("Can't replace original file with loaded file");

			DirectoryContentItem localItem = new DirectoryContentItem(this, digestCache, remoteContentItem.getName());
			localItem.setDescription(remoteContentItem.getDescription());
			localItem.setType(remoteContentItem.getType());
			localItem.setHash(remoteContentItem.getHash());
			localItem.setRegionId(remoteContentItem.getRegionId());
			items.add(localItem);

			return localItem;
		} catch (IOException e) {
			outputFile.delete();
			throw e;
		} finally {
			if (output != null)
				output.close();
		}
	}

	public void migrate(String newPath) {
		path = newPath;
		File file = new File(path);
		if (!file.mkdirs() && !file.isDirectory()) {
			log.warn("Can't mkdirs new path");
		}
	}

	public String getStorageName() {
		return "writable-directory-storage";
	}

}
