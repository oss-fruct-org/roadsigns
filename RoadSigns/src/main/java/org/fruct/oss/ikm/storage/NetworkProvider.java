package org.fruct.oss.ikm.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkProvider implements IProvider {
	private static Logger log = LoggerFactory.getLogger(NetworkProvider.class);

	@Override
	public IContentConnection loadContentItem(String urlStr) throws IOException {
		final HttpURLConnection conn = getConnection(urlStr);

		return new IContentConnection() {
			private InputStream stream;

			@Override
			public InputStream getStream() throws IOException {
				return stream = new BufferedInputStream(conn.getInputStream());
			}

			@Override
			public void close() {
				if (stream != null) {
					try {
						stream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				conn.disconnect();
			}
		};
	}

	private int recursionDepth = 0;
	public HttpURLConnection getConnection(String urlStr) throws IOException {
		log.info("Downloading {}", urlStr);
		URL url = new URL(urlStr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setReadTimeout(10000);
		conn.setConnectTimeout(10000);

		conn.setRequestMethod("GET");
		conn.setDoInput(true);

		conn.connect();
		int code = conn.getResponseCode();
		log.info("Code {}", code);

		// TODO: not tested
		if (code != HttpURLConnection.HTTP_ACCEPTED) {
			if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP) {
				try {
					if (++recursionDepth > 10)
						throw new IOException("Too many redirects");

					String newLocation = conn.getHeaderField("Location");
					log.info("Redirecting to {}", newLocation);

					conn.disconnect();
					return getConnection(newLocation);
				} finally {
					recursionDepth--;
				}
			}
		}

		return conn;
	}
}
