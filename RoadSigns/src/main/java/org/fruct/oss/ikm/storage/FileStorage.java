package org.fruct.oss.ikm.storage;

import android.content.Context;
import android.os.Environment;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileStorage implements IStorage, IProvider {
	public static final int BUFFER_SIZE = 4096;

	private final String storagePath;
	private List<IContentItem> contentItems;

	private volatile boolean interrupt = false;

	public FileStorage(String storagePath) {
		this.storagePath = storagePath;
	}

	@Override
	public void interrupt() {
		interrupt = true;
	}

	@Override
	public void storeContentItem(String url, InputStream input) throws IOException {
		interrupt = false;
		OutputStream output = null;
		String fileStr = storagePath + "/" + url;
		File outputFile = new File(fileStr + ".roadsignsdownload");
		File targetFile = new File(fileStr);

		try {
			output = new FileOutputStream(outputFile);

			// Write file
			int readed;
			byte[] buf = new byte[BUFFER_SIZE];
			while ((readed = input.read(buf)) > 0) {
				output.write(buf, 0, readed);
				if (interrupt) {
					throw new InterruptedIOException();
				}
			}

			outputFile.renameTo(targetFile);
		} catch (IOException e) {
			outputFile.delete();
			throw e;
		} finally {
			if (output != null) {
				output.close();
			}
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
				if (stream != null) {
					try {
						stream.close();
					} catch (IOException e) {
					}
				}
			}
		};
	}

	public static FileStorage createInternalStorage(Context context, String path) {
		String storagePath = context.getFilesDir() + "/" + path;
		File storageDirFile = new File(storagePath);
		if (!storageDirFile.exists())
			storageDirFile.mkdirs();

		return new FileStorage(storagePath);
	}

	public static FileStorage createExternalStorage(String path) {
		String storagePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path;
		File storageDirFile = new File(storagePath);
		if (!storageDirFile.exists())
			storageDirFile.mkdirs();

		return new FileStorage(storagePath);
	}
}
