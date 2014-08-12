package org.fruct.oss.ikm.storage2;

import org.apache.commons.io.IOUtils;
import org.fruct.oss.ikm.storage.IStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DirectoryStorage implements ContentStorage {
	private static final Logger log = LoggerFactory.getLogger(DirectoryStorage.class);

	private String path;
	private KeyValue digestCache;
	private List<ContentItem> items;

	public DirectoryStorage(KeyValue digestCache, String path) {
		this.digestCache = digestCache;
		if (path == null)
			throw new IllegalArgumentException("Path must not be null");

		this.path = path;
	}

	@Override
	public void updateContentList() throws IOException {
		File dir = new File(path);
		if (!dir.exists() || !dir.isDirectory())
			throw new FileNotFoundException("Wrong directory " + path);

		items = new ArrayList<ContentItem>();
		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				ContentItem item = createContentItem(file);
				if (item != null) {
					items.add(item);
				} else {
					log.warn("Unsupported file " + file.getName() + " found in directory " + dir.getName());
				}
			}
		}
	}

	public ContentItem storeContentItem(ContentItem remoteContentItem, InputStream input) throws IOException {
		OutputStream output = null;

		String fileStr = path + "/" + remoteContentItem.getName();

		File outputFile = new File(fileStr + ".roadsignsdownload");
		File targetFile = new File(fileStr);

		try {
			output = new FileOutputStream(outputFile);

			IOUtils.copy(input, output);

			if (!outputFile.renameTo(targetFile))
				throw new IOException("Can't replace original file with loaded file");

			DirectoryContentItem localItem = new DirectoryContentItem(this, digestCache, remoteContentItem.getName());
			localItem.setDescription(remoteContentItem.getDescription());
			localItem.setType(remoteContentItem.getType());
			localItem.setHash(remoteContentItem.getHash());
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

	private ContentItem createContentItem(File file) throws FileNotFoundException {
		if (file.getName().endsWith(".map")) {
			DirectoryContentItem item =  createBaseContentItem(file);
			item.setType("mapsforge-map");
			fillMapMetadata(file, item);
			return item;
		} else if (file.getName().endsWith(".ghz")) {
			DirectoryContentItem item =  createBaseContentItem(file);
			item.setType("graphhopper-map");
			fillGhzMetadata(file, item);
			return item;
		} else {
			return null;
		}
	}

	private DirectoryContentItem createBaseContentItem(File file) throws FileNotFoundException {
		return new DirectoryContentItem(this, digestCache, file.getName());
	}

	private void fillGhzMetadata(File file, DirectoryContentItem item) throws FileNotFoundException {
		item.setDescription(file.getName());
	}

	private void fillMapMetadata(File file, DirectoryContentItem item) throws FileNotFoundException {
		item.setDescription(file.getName());
	}

	@Override
	public List<ContentItem> getContentList() {
		return items;
	}

	public String getPath() {
		return path;
	}

	public String getStorageName() {
		return "directory-storage";
	}

	public void migrate(String newPath, MigrationListener listener) throws IOException {
		String oldPath = path;

		if (oldPath.equals(newPath))
			return;

		File newDir = new File(newPath);
		if (newDir.exists() && !newDir.isDirectory())
			throw new IOException("Target directory already exists and actually not an directory");

		// First, enumerate all files in old directory
		File oldDir = new File(oldPath);
		File[] oldFiles = oldDir.listFiles();

		boolean canRename = false;
		// Check that files in directories can be simply renamed
		File tmpFile = new File(oldDir, ".roadsignstemporary" + System.currentTimeMillis());
		if (tmpFile.createNewFile()) {
			File tmpNewFile = new File(newDir, ".roadsignstemporary" + System.currentTimeMillis());
			canRename = tmpFile.renameTo(tmpNewFile);
			tmpFile.delete();
			tmpNewFile.delete();
		}

		if (!newDir.mkdirs() && !newDir.isDirectory()) {
			throw new IOException("Cannot create migration target directory " + newDir);
		}

		List<File> copiedFiles = new ArrayList<File>();

		try {
			for (int i = 0, oldFilesLength = oldFiles.length; i < oldFilesLength; i++) {
				File file = oldFiles[i];
				if (file.isDirectory()) {
					log.warn("Content directory contains subdirectory {}", file);
					continue;
				}

				String name = file.getName();
				File newFile = new File(newDir, name);

				if (canRename) {
					if (!file.renameTo(newFile)) {
						log.warn("Can't rename file but previous test show that renaming is possible. Fallback to copying");
						canRename = false;
					}
				}

				if (!canRename) {
					listener.fileCopying(name, i, oldFilesLength);
					FileInputStream inputStream = new FileInputStream(file);
					FileOutputStream outputStream = new FileOutputStream(newFile);

					IOUtils.copy(inputStream, outputStream);

					IOUtils.closeQuietly(inputStream);
					IOUtils.closeQuietly(outputStream);

					copiedFiles.add(newFile);
				}
			}
		} catch (IOException e) {
			// Clean up target directory before exit
			for (File file : copiedFiles) {
				file.delete();
			}

			newDir.delete();
			throw e;
		}

		// Delete old directory
		for (File file : oldFiles) {
			file.delete();
		}

		oldDir.delete();
		path = newPath;
	}

	static interface MigrationListener {
		void fileCopying(String name, int n, int max);
	}
}
