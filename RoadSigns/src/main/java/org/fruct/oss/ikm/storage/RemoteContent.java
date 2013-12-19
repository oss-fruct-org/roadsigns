package org.fruct.oss.ikm.storage;

import android.util.Pair;

import org.fruct.oss.ikm.DigestInputStream;
import org.fruct.oss.ikm.ProgressInputStream;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class RemoteContent {
	public enum LocalContentState {
		NOT_EXISTS, NEEDS_UPDATE, UP_TO_DATE, DELETED_FROM_SERVER
	}

	public interface Listener {
		void listReady(List<StorageItem> list);
		void downloadStateUpdated(StorageItem item, int downloaded, int max);
		void downloadFinished(StorageItem item);
		void errorDownloading(StorageItem item, IOException e);
	}

	public class StorageItem {
		public StorageItem(LocalContentState state, IContentItem item) {
			this.state = state;
			this.item = item;
		}

		private LocalContentState state;
		private IContentItem item;
		private Object tag;
		private boolean isDownloading;

		public LocalContentState getState() {
			return state;
		}

		public IContentItem getItem() {
			return item;
		}

		public Object getTag() {
			return tag;
		}

		public void setTag(Object tag) {
			this.tag = tag;
		}

		public boolean isDownloading() {
			return isDownloading;
		}

		public void setDownloading(boolean isDownloading) {
			this.isDownloading = isDownloading;
		}
	}

	private static Logger log = LoggerFactory.getLogger(RemoteContent.class);
	private final IStorage storage;
	private final IProvider provider;
	private final String contentUrl;

	private Listener listener;
	private ExecutorService executor = Executors.newSingleThreadExecutor();

	// Local storage content
	private List<IContentItem> storageContent;

	// List of available storage items
	private List<StorageItem> storageItems;

	public RemoteContent(IStorage storage, IProvider provider, String contentUrl) {
		this.storage = storage;
		this.provider = provider;
		this.contentUrl = contentUrl;
	}

	private void initialize() throws IOException {
		storageContent = getContentList(storage, "content.xml");
		if (storageContent == null) {
			storageContent = new ArrayList<IContentItem>();
			createContentList(storage);
		}
	}

	private void createContentList(IStorage storage) throws IOException {
		ByteArrayInputStream input = new ByteArrayInputStream("<content/>".getBytes("UTF-8"));
		storage.storeContentItem("content.xml", input);
		input.close();
	}

	private List<IContentItem> getContentList(IProvider storage, String contentUrl) throws IOException {
		IContentConnection conn = null;
		try {
			conn = storage.loadContentItem(contentUrl);
			Content content = Content.createFromStream(conn.getStream());
			return content.getContent();
		} catch (FileNotFoundException e) {
			return null;
		} finally {
			if (conn != null)
				conn.close();
		}
	}

	private void storeContentItem(IContentItem item, IStorage storage, InputStream stream) throws IOException {
		storage.storeContentItem(item.getName(), stream);

		removeFromList(item);
		storageContent.add(item);
		updateContentRecord(storage);
	}

	private void updateContentRecord(IStorage storage) throws IOException {
		Content content = new Content(storageContent);

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		Serializer serializer = new Persister();
		try {
			serializer.write(content, buffer);
		} catch (Exception e) {
			throw new IOException(e);
		}

		ByteArrayInputStream inputBuffer = new ByteArrayInputStream(buffer.toByteArray());
		storage.storeContentItem("content.xml", inputBuffer);
	}

	private void removeFromList(IContentItem item) {
		// Remove existing item from content list
		Iterator<IContentItem> iter = storageContent.iterator();
		while (iter.hasNext()) {
			IContentItem exItem = iter.next();
			if (exItem.getName().equals(item.getName())) {
				iter.remove();
				break;
			}
		}
	}

	public LocalContentState checkLocalState(IContentItem item) {
		boolean exists = false;
		for (IContentItem existingItem : storageContent) {
			log.debug("Checking item {} against {}", existingItem.getName(), item.getName());
			log.debug("Hash {} {}", existingItem.getHash(), item.getHash());
			if (existingItem.getName().equals(item.getName())) {
				exists = true;

				if (!existingItem.getHash().equals(item.getHash())) {
					return LocalContentState.NEEDS_UPDATE;
				}
			}
		}

		return exists ? LocalContentState.UP_TO_DATE : LocalContentState.NOT_EXISTS;
	}

	public String getPath(IContentItem item) {
		if (storage instanceof FileStorage) {
			FileStorage fs = (FileStorage) storage;
			return fs.getPath(item);
		} else {
			return null;
		}
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void deleteContentItem(StorageItem item) throws IOException {
		storage.deleteItem(item.item.getName());
		removeFromList(item.item);
		item.state = checkLocalState(item.item);
		updateContentRecord(storage);

		if (listener != null) {
			listener.listReady(storageItems);
		}
	}

	// Async methods
	public void startInitialize() {
		if (storageItems != null) {
			log.info("RemoteContent already initialized");
			if (listener != null) {
				listener.listReady(storageItems);
			}
			return;
		}

		executor.execute(new Runnable() {
			@Override
			public void run() {
				doAsyncInitialize();
			}
		});
	}

	private void doAsyncInitialize() {
		try {
			initialize();

			List<IContentItem> localContent = storageContent;
			List<IContentItem> remoteContent = getContentList(provider, contentUrl);

			HashMap<String, StorageItem> allItems = new HashMap<String, StorageItem>();
			for (IContentItem item : localContent) {
				log.debug("Local item {}", item.getName());
				allItems.put(item.getName(), new StorageItem(LocalContentState.DELETED_FROM_SERVER, item));
			}

			for (IContentItem item : remoteContent) {
				log.debug("Remote item {}", item.getName());

				StorageItem sItem = allItems.get(item.getName());
				// Local item exists check it state
				if (sItem == null) {
					// No local item
					sItem = new StorageItem(LocalContentState.NOT_EXISTS, item);
					allItems.put(item.getName(), sItem);
				} else if (item.getHash().equals(sItem.item.getHash())) {
					sItem.state = LocalContentState.UP_TO_DATE;
				} else {
					sItem.item = item;
					sItem.state = LocalContentState.NEEDS_UPDATE;
				}
			}

			if (listener != null) {
				listener.listReady(storageItems = new ArrayList<StorageItem>(allItems.values()));
			}

		} catch (IOException e) {
			log.warn("Cannot download file", e);
			e.printStackTrace();
		}
	}

	public void startDownloading(final StorageItem item) {
		item.setDownloading(true);
		executor.execute(new Runnable() {
			@Override
			public void run() {
				doAsyncStartDownloading(item);
			}
		});
	}

	private void doAsyncStartDownloading(final StorageItem sItem) {
		IContentConnection conn = null;
		try {
			IContentItem item = sItem.item;
			conn = provider.loadContentItem(item.getUrl());

			InputStream inputStream = new ProgressInputStream(conn.getStream(), item.getDownloadSize(),
					80000, new ProgressInputStream.ProgressListener() {
				@Override
				public void update(int current, int max) {
					log.trace("Downloaded {}/{}", current, max);
					if (listener != null) {
						listener.downloadStateUpdated(sItem, current, max);
					}
				}
			});

			// Setup gzip compression
			if ("gzip".equals(item.getCompression())) {
				log.info("Using gzip compression");
				inputStream = new GZIPInputStream(inputStream);
			}

			// Setup content validation
			try {
				// TODO: de-hardcode hash algorithm
				inputStream = new DigestInputStream(inputStream, "sha1", item.getHash());
			} catch (NoSuchAlgorithmException e) {
				log.warn("Unsupported hash algorithm");
			}

			storeContentItem(item, storage, inputStream);
			sItem.state = LocalContentState.UP_TO_DATE;
			if (listener != null) {
				listener.downloadFinished(sItem);
				listener.listReady(storageItems);
			}
		} catch (IOException e) {
			log.warn("Error downloading", e);
			if (listener != null) {
				listener.errorDownloading(sItem, e);
			}
		} finally {
			sItem.setDownloading(false);
			if (conn != null) {
				conn.close();
			}
		}
	}

	private static Map<Pair<String, String>, RemoteContent> instances = new HashMap<Pair<String, String>, RemoteContent>();
	public static RemoteContent getInstance(String contentUrl, String localPath) {
		Pair<String, String> key = Pair.create(contentUrl, localPath);
		RemoteContent instance = instances.get(key);
		if (instance == null) {
			log.info("New instance of RemoteContent created");
			NetworkProvider provider = new NetworkProvider();
			FileStorage storage = FileStorage.createExternalStorage(localPath);
			RemoteContent ret = new RemoteContent(storage, provider, contentUrl);
			instances.put(key, ret);
			return ret;
		} else
			return instance;
	}
}
