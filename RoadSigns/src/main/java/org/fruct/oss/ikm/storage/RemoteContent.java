package org.fruct.oss.ikm.storage;

import android.support.v4.widget.ListViewAutoScrollHelper;
import android.util.Pair;

import org.fruct.oss.ikm.DigestInputStream;
import org.fruct.oss.ikm.OnlineContentPreference;
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
import java.io.InterruptedIOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
		void errorInitializing(IOException e);
		void downloadInterrupted(StorageItem sItem);
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
	private final List<String> contentUrls;

	private ArrayList<Listener> listeners = new ArrayList<Listener>();
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private volatile boolean initializationStarted = false;

	private List<Future<?>> currentTasks = new ArrayList<Future<?>>();

	// Local storage content
	private List<IContentItem> storageContent;

	// List of available storage items
	private List<StorageItem> storageItems;

	public RemoteContent(IStorage storage, IProvider provider, List<String> contentUrls) {
		this.storage = storage;
		this.provider = provider;
		this.contentUrls = contentUrls;
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

	private List<IContentItem> getContentList(IProvider storage, List<String> contentUrls, Set<String> visited) throws IOException {
		ArrayList<IContentItem> ret = new ArrayList<IContentItem>();

		int countSuccessful = 0;
		for (String url : contentUrls) {
			if (visited.contains(url)) {
				countSuccessful++;
				continue;
			}

			visited.add(url);
			IContentConnection conn = null;
			try {
				conn = storage.loadContentItem(url);
				Content content = Content.createFromStream(conn.getStream());

				List<IContentItem> items = content.getContent();
				for (IContentItem item : items) {
					ret.add(item);
				}

				countSuccessful++;

				ret.addAll(getContentList(storage, content.getIncludes(), visited));
			} catch (IOException e) {
				log.warn("Content link " + url + " broken: ", e);
			} finally {
				if (conn != null)
					conn.close();
			}
		}

		if (countSuccessful == 0 && contentUrls.size() > 0)
			throw new IOException("No one of remote content roots are available");

		return ret;
	}

	private List<IContentItem> getContentList(IProvider storage, List<String> contentUrls) throws IOException {
		return getContentList(storage, contentUrls, new HashSet<String>());
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

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	public void deleteContentItem(StorageItem item) throws IOException {
		storage.deleteItem(item.item.getName());
		removeFromList(item.item);
		item.state = checkLocalState(item.item);
		updateContentRecord(storage);

		for (Listener listener : listeners)
			listener.listReady(storageItems);
	}

	private synchronized void startTask(Runnable run) {
		Future<?> future = executor.submit(run);
		currentTasks.add(future);

		// Delete old tasks
		for (Iterator<Future<?>> iterator = currentTasks.iterator(); iterator.hasNext(); ) {
			Future<?> task = iterator.next();
			if (task.isDone())
				iterator.remove();
		}
	}

	// Async methods
	public void startInitialize(boolean forceInitialization) {
		if (initializationStarted)
			return;

		if (storageItems != null && !forceInitialization) {
			log.info("RemoteContent already initialized");
			for (Listener listener : listeners)
				listener.listReady(storageItems);

			return;
		}

		startTask(new Runnable() {
			@Override
			public void run() {
				initializationStarted = true;
				doAsyncInitialize();
				initializationStarted = false;
			}
		});
	}

	private void doAsyncInitialize() {
		List<IContentItem> remoteContent = null;
		List<IContentItem> localContent = null;
		try {
			initialize();
			localContent = storageContent;

			remoteContent = getContentList(provider, contentUrls);
		} catch (IOException e) {
			log.warn("Cannot download file", e);
			for (Listener listener : listeners)
				listener.errorInitializing(e);

			e.printStackTrace();
		}

		HashMap<String, StorageItem> allItems = new HashMap<String, StorageItem>();
		for (IContentItem item : localContent) {
			log.debug("Local item {}", item.getName());
			allItems.put(item.getName(), new StorageItem(LocalContentState.DELETED_FROM_SERVER, item));
		}

		if (remoteContent != null) {
			for (IContentItem item : remoteContent) {
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
				log.debug("Remote item {} state {}", item.getName(), sItem.state.name());
			}
		}

		for (Listener listener : listeners)
			listener.listReady(storageItems = new ArrayList<StorageItem>(allItems.values()));

	}

	public void startDownloading(final StorageItem item) {
		item.setDownloading(true);
		startTask(new Runnable() {
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
					for (Listener listener : listeners)
						listener.downloadStateUpdated(sItem, current, max);
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
			for (Listener listener : listeners) {
				listener.downloadFinished(sItem);
				listener.listReady(storageItems);
			}
		} catch (InterruptedIOException ex) {
			log.info("Downloading interrupted");
			for (Listener listener : listeners) {
				listener.downloadInterrupted(sItem);
			}
		} catch (IOException e) {
			log.warn("Error downloading", e);
			for (Listener listener : listeners) {
				listener.errorDownloading(sItem, e);
			}
		} finally {
			sItem.setDownloading(false);
			if (conn != null) {
				conn.close();
			}
		}
	}

	public void stopAll() {
		for (Future future : currentTasks)
			future.cancel(true);
	}

	private static Map<Pair<ArrayList<String>, String>, RemoteContent> instances = new HashMap<Pair<ArrayList<String>, String>, RemoteContent>();
	public static RemoteContent getInstance(String[] contentUrl, String localPath) {
		ArrayList<String> contentUrlsList = new ArrayList<String>(Arrays.asList(contentUrl));

		Pair<ArrayList<String>, String> key = Pair.create(contentUrlsList, localPath);
		RemoteContent instance = instances.get(key);
		if (instance == null) {
			log.info("New instance of RemoteContent created");
			NetworkProvider provider = new NetworkProvider();
			FileStorage storage = FileStorage.createExternalStorage(localPath);
			RemoteContent ret = new RemoteContent(storage, provider, contentUrlsList);
			instances.put(key, ret);
			return ret;
		} else
			return instance;
	}
}
