package org.fruct.oss.ikm.storage;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

public class FileStorage implements IStorage, IProvider {
	public static final int BUFFER_SIZE = 512 * 1024 * 1;

	private final String storagePath;

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

	public String getPath(IContentItem item) {
		return storagePath + "/" + item.getName();
	}
}
