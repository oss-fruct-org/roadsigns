package org.fruct.oss.ikm.storage;

import org.apache.commons.io.IOUtils;
import org.fruct.oss.ikm.utils.Utils;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

		File file = new File(path);
		if (!file.mkdirs() && !file.isDirectory()) {
			log.warn("Can't mkdirs new path");
		}
	}

	@Override
	public void updateContentList() throws IOException {
		File dir = new File(path);
		if (!dir.exists() || !dir.isDirectory())
			throw new FileNotFoundException("Wrong directory " + path);

		items = new ArrayList<ContentItem>();
		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				ContentItem item = null;
				try {
					item = createContentItem(file);
				} catch (IOException ignored) {
				}

				if (item != null) {
					items.add(item);
				} else {
					log.warn("Unsupported file " + file.getName() + " found in directory " + dir.getName());
				}
			}
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

	private ContentItem createContentItem(File file) throws IOException {
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

	private void fillFromMetadata(Metadata metadata, DirectoryContentItem item) {
		Locale locale = Locale.getDefault();
		String lang = locale.getLanguage();

		item.setRegionId(metadata.getRegionId());
		if (metadata.getDescription().containsKey(lang)) {
			item.setDescription(metadata.getDescription().get(lang));
		} else if (metadata.getDescription().containsKey("en")) {
			item.setDescription(metadata.getDescription().get("en"));
		} else {
			item.setDescription(metadata.getDescription().values().iterator().next());
		}
	}

	private void fillGhzMetadata(File file, DirectoryContentItem item) throws IOException {
		ZipFile zipFile = new ZipFile(file, ZipFile.OPEN_READ);
		ZipEntry entry = zipFile.getEntry("description.txt");
		if (entry != null) {
			InputStream input = zipFile.getInputStream(entry);
			Serializer serializer = new Persister();
			try {
				Metadata metadata = serializer.read(Metadata.class, input);
				fillFromMetadata(metadata, item);
			} catch (Exception ex) {
				throw new IOException("Incorrect xml file");
			} finally {
				input.close();
			}
		}
		zipFile.close();
	}

	private void fillMapMetadata(File file, DirectoryContentItem item) throws IOException {
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(file, "r");

			raf.skipBytes(20 + 4 + 4 + 8 + 8 + 16 + 2);
			int projLength = readMapVbeu(raf);
			raf.skipBytes(projLength);

			int skipOptional = 0;

			byte b = raf.readByte();

			if ((b & (1 << 3)) == 0) {
				// TODO: no comment
				return;
			}

			if ((b & (1 << 6)) != 0) {
				skipOptional += 8;
			}

			if ((b & (1 << 5)) != 0) {
				skipOptional += 1;
			}

			if ((b & (1 << 4)) != 0) {
				// Skip language
				skipOptional += readMapVbeu(raf);
			}

			if (skipOptional != 0) {
				raf.skipBytes(skipOptional);
			}

			int commentLength = readMapVbeu(raf);
			byte[] commentBytes = new byte[commentLength];
			raf.read(commentBytes);

			String comment = new String(commentBytes, "UTF-8");
			Serializer serializer = new Persister();
			try {
				Metadata metadata = serializer.read(Metadata.class, comment);
				fillFromMetadata(metadata, item);
			} catch (Exception e) {
				throw new IOException("Incorrect xml file");
			}
		} finally {
			if (raf != null) {
				raf.close();
			}
		}
	}

	private int readMapVbeu(RandomAccessFile raf) throws IOException {
		int res = 0;
		int offset = 0;
		byte b;
		do {
			b = raf.readByte();
			int d = b & 0x7f;
			res ^= (d << offset);
			offset += 7;
		} while ((b & 0x80) != 0);
		return res;
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

	public void migrate(String newPath) {
		path = newPath;
		File file = new File(path);
		if (!file.mkdirs() && !file.isDirectory()) {
			log.warn("Can't mkdirs new path");
		}
	}
}
