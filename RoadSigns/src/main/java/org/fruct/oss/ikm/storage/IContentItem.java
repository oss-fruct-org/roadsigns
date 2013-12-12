package org.fruct.oss.ikm.storage;

public interface IContentItem {
	String getName();
	String getType();
	String getDescription();

	int getSize();
	String getUrl();
	String getHash();
}
