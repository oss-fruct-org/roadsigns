package org.fruct.oss.ikm.storage;

import android.content.Context;

import java.io.IOException;
import java.util.List;

public class RemoteContent {
	private final String contentUrl;
	private final IProvider provider;
	private final IStorage storage;
	private final Context context;
	private Content content;

	public RemoteContent(Context context, IProvider provider, IStorage storage, String contentUrl) {
		this.contentUrl = contentUrl;
		this.provider = provider;
		this.storage = storage;
		this.context = context;
	}

	public void initialize() throws IOException {
		IContentConnection conn = null;
		try {
			conn = provider.loadContentItem(contentUrl);
			content = Content.createFromStream(conn.getStream());
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		} finally {
			if (conn != null) {
				conn.close();
			}
		}
	}
}
