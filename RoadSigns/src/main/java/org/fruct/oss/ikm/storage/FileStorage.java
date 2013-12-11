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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileStorage implements IStorage {
	public static final int BUFFER_SIZE = 4096;

	private final String storagePath;
	private List<IContentItem> contentItems;

	public FileStorage(String storagePath) {
		this.storagePath = storagePath;
	}

	@Override
	public List<IContentItem> getContent() throws IOException {
		InputStream stream = null;
		String contentFileStr = storagePath + "/" + "content.xml";
		try {
			stream = new FileInputStream(contentFileStr);

			Content content = Content.createFromStream(stream);
			return contentItems = content.getContent();
		} catch (FileNotFoundException e) {
			FileWriter writer = new FileWriter(contentFileStr);
			writer.write("<content></content>");
			writer.close();

			return contentItems = new ArrayList<IContentItem>();
		} finally {
			if (stream != null) {
				stream.close();
			}
		}
	}

	@Override
	public void storeContentItem(IContentItem item, InputStream input) throws IOException {
		OutputStream output;
		String fileStr = storagePath + "/" + item.getName();

		try {
			output = new FileOutputStream(fileStr);

			// Write file
			int readed;
			byte[] buf = new byte[BUFFER_SIZE];
			while ((readed = input.read(buf)) > 0) {
				output.write(buf, 0, readed);
			}

			// Update content record
			contentItems.add(item);
			Content content = new Content(contentItems);

			Serializer serializer = new Persister();
			try {
				serializer.write(content, new File(storagePath + "/" + "content.xml"));
			} catch (Exception e) {
				throw new IOException(e);
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	@Override
	public IContentConnection loadContentItem(final String name) throws IOException {
		return new IContentConnection() {
			private InputStream stream;

			@Override
			public InputStream getStream() throws IOException {
				return stream = new FileInputStream(storagePath + "/" + name);
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
