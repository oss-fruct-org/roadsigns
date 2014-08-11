package org.fruct.oss.ikm.storage2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NetworkStorage implements ContentStorage {
	private static final Logger log = LoggerFactory.getLogger(NetworkStorage.class);
	private static final int MAX_RECURSION = 10;

	private final String[] rootUrls;
	private List<ContentItem> items;

	public NetworkStorage(String[] rootUrls) {
		this.rootUrls = rootUrls;
	}

	@Override
	public void updateContentList() throws IOException {
		for (String contentUrl : rootUrls) {
			try {
				List<String> singleContentUrl = new ArrayList<String>(1);
				singleContentUrl.add(contentUrl);
				items = getContentList(singleContentUrl, new HashSet<String>());
				log.warn("Content root url {} successfully downloaded", contentUrl);

				break;
			} catch (IOException ex) {
				log.warn("Content root url {} unavailable", contentUrl);
			}
		}

		if (items == null) {
			throw new IOException("No one of remote content roots are available");
		}
	}

	private List<ContentItem> getContentList(List<String> contentUrls, Set<String> visited) throws IOException {
		ArrayList<ContentItem> ret = new ArrayList<ContentItem>();

		int countSuccessful = 0;
		for (String url : contentUrls) {
			if (visited.contains(url)) {
				countSuccessful++;
				continue;
			}

			visited.add(url);
			ContentConnection conn = null;
			try {
				conn = loadContentItem(url);
				Content content = Content.createFromStream(conn.getStream());

				List<NetworkContentItem> items = content.getContent();
				for (NetworkContentItem item : items) {
					item.setNetworkStorage(this);
					ret.add(item);
				}

				countSuccessful++;

				ret.addAll(getContentList(content.getIncludes(), visited));
			} catch (IOException e) {
				log.warn("Content link " + url + " broken: ", e);
			} finally {
				if (conn != null)
					conn.close();
			}
		}

		if (countSuccessful == 0 && contentUrls.size() > 0)
			throw new IOException("No one of remote content roots are available");

		return ret;
	}

	public ContentConnection loadContentItem(String urlStr) throws IOException {
		final HttpURLConnection conn = getConnection(urlStr);

		return new ContentConnection() {
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
					if (++recursionDepth > MAX_RECURSION)
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



	@Override
	public List<ContentItem> getContentList() {
		return items;
	}
}
