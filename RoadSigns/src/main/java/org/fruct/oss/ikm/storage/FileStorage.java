package org.fruct.oss.ikm.storage;

import android.content.Context;
import android.os.Environment;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileStorage implements IStorage, IProvider {
	private static final Logger log = LoggerFactory.getLogger(FileStorage.class);
	public static final int BUFFER_SIZE = 512 * 1024 * 1;

	private String storagePath;

	public FileStorage(String storagePath) {
		this.storagePath = storagePath;
	}

	@Override
	public void storeContentItem(String url, InputStream input) throws IOException {
		OutputStream output = null;
		String fileStr = storagePath + "/" + url;
		File outputFile = new File(fileStr + ".roadsignsdownload");
		File targetFile = new File(fileStr);

		try {
			output = new FileOutputStream(outputFile);

			// Write file
			int readed;
			byte[] buf = new byte[BUFFER_SIZE];
			while ((readed = input.read(buf)) != -1) {
				output.write(buf, 0, readed);
				if (Thread.interrupted()) {
					throw new InterruptedIOException();
				}
			}

			if (!outputFile.renameTo(targetFile))
				throw new IOException("Can't replace original file with loaded file");
		} catch (IOException e) {
			outputFile.delete();
			throw e;
		} finally {
			if (output != null)
				output.close();
		}
	}

	@Override
	public void deleteItem(String url) {
		String fileStr = storagePath + "/" + url;
		new File(fileStr).delete();
	}

	@Override
	public IContentConnection loadContentItem(final String name) throws IOException {
		final InputStream stream = new FileInputStream(storagePath + "/" + name);
		return new IContentConnection() {
			@Override
			public InputStream getStream() {
				return stream;
			}

			@Override
			public void close() {
				try {
					stream.close();
				} catch (IOException ignored) {
				}
			}
		};
	}

	@Deprecated
	public static FileStorage createInternalStorage(Context context, String path) {
		String storagePath = context.getFilesDir() + "/" + path;
		File storageDirFile = new File(storagePath);
		if (!storageDirFile.exists())
			storageDirFile.mkdirs();

		return new FileStorage(storagePath);
	}

	@Deprecated
	public static FileStorage createExternalStorage(String path) {
		String storagePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path;
		File storageDirFile = new File(storagePath);
		if (!storageDirFile.exists())
			storageDirFile.mkdirs();

		return new FileStorage(storagePath);
	}

	@Override
	public void migrate(String targetPath, MigrationListener listener) throws IOException {
		String oldPath = storagePath;
		String newPath = targetPath;

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
		storagePath = newPath;
	}

	public String getPath(IContentItem item) {
		return storagePath + "/" + item.getName();
	}
}
