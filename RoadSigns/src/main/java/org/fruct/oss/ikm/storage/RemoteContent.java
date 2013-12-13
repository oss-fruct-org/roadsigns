package org.fruct.oss.ikm.storage;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RemoteContent {
	public enum LocalContentState {
		NOT_EXISTS, NEEDS_UPDATE, UP_TO_DATE
	}

	private static Logger log = LoggerFactory.getLogger(RemoteContent.class);
	private final IStorage storage;

	private List<IContentItem> storageContent;

	public RemoteContent(IStorage storage) {
		this.storage = storage;
	}

	public void initialize() throws IOException {
		storageContent = getContentList(storage, "content.xml");
		if (storageContent == null) {
			storageContent = new ArrayList<IContentItem>();
			createContentList(storage);
		}
	}

	public void createContentList(IStorage storage) throws IOException {
		ByteArrayInputStream input = new ByteArrayInputStream("<content/>".getBytes("UTF-8"));
		storage.storeContentItem("content.xml", input);
		input.close();
	}

	public List<IContentItem> getContentList(IProvider storage, String contentUrl) throws IOException {
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

	public void deleteContentItem(IContentItem item, IStorage storage) throws IOException {
		try {
			storage.deleteItem(item.getName());
			removeFromList(item);
		} finally {
			updateContentRecord(storage);
		}
	}

	public void storeContentItem(IContentItem item, IStorage storage, InputStream stream) throws IOException {
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

}
