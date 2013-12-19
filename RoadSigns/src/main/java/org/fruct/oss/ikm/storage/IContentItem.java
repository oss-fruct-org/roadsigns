package org.fruct.oss.ikm.storage;

public interface IContentItem {
	String getName();
	String getType();
	String getDescription();

	int getSize();

	int getDownloadSize();

	String getUrl();
	String getHash();

	String getCompression();
}
