package org.fruct.oss.ikm;

import org.fruct.oss.ikm.storage.FileStorage;
import org.fruct.oss.ikm.storage.NetworkProvider;
import org.fruct.oss.ikm.storage.RemoteContent;

public class MapLoader {
	private final String remoteContentPath;
	private NetworkProvider provider;
	private FileStorage storage;
	private RemoteContent remoteContent;

	public MapLoader(String remoteContentPath) {
		this.remoteContentPath = remoteContentPath;

		provider = new NetworkProvider();
		storage = FileStorage.createExternalStorage("roadsigns-maps");
		remoteContent = new RemoteContent(storage);
	}

}
