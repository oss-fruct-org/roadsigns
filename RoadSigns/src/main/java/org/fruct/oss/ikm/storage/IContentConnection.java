package org.fruct.oss.ikm.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public interface IContentConnection extends Closeable {
	InputStream getStream() throws IOException;

	@Override
	void close();
}
