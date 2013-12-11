package org.fruct.oss.ikm.storage;

public interface IContentItem {
	String getName();
	String getType();
	int getSize();
	String getUrl();
	String getHash();
}
