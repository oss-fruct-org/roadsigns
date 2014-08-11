package org.fruct.oss.ikm.storage2;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public interface ContentConnection extends Closeable {
	InputStream getStream() throws IOException;

	@Override
	void close();
}
