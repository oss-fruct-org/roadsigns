package org.fruct.oss.ikm.storage;

import java.io.IOException;
import java.util.List;

public class RemoteContentListenerAdapter implements RemoteContentService.Listener {
	@Override
	public void localListReady(List<ContentItem> list) {

	}

	@Override
	public void remoteListReady(List<ContentItem> list) {

	}

	@Override
	public void downloadStateUpdated(ContentItem item, int downloaded, int max) {

	}

	@Override
	public void downloadFinished(ContentItem localItem, ContentItem remoteItem) {

	}

	@Override
	public void errorDownloading(ContentItem item, IOException e) {

	}

	@Override
	public void errorInitializing(IOException e) {

	}

	@Override
	public void downloadInterrupted(ContentItem item) {

	}
}
