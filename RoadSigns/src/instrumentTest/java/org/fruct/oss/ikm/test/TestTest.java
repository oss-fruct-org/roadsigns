package org.fruct.oss.ikm.test;

import android.test.AndroidTestCase;

import org.fruct.oss.ikm.storage.FileStorage;
import org.fruct.oss.ikm.storage.IContentConnection;
import org.fruct.oss.ikm.storage.IContentItem;
import org.fruct.oss.ikm.storage.NetworkProvider;
import org.fruct.oss.ikm.storage.RemoteContent;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Root(strict = false)
class Entity {
	@Element
	public String a;

	@Element
	public String b;
}

public class TestTest extends AndroidTestCase {
	private static Logger log = LoggerFactory.getLogger(TestTest.class);

	public void testXml() throws Exception {
		String xml =
				"" +
				"<entity>" +
				"<a>Test</a>" +
				"<b>Test2</b>" +
				"</entity>" +
				"";

		Serializer serializer = new Persister();
		Entity ent = serializer.read(Entity.class, xml);

		assertEquals("Test", ent.a);
		assertEquals("Test2", ent.b);
	}

	public void testGets() throws Exception {
		/*IGets gets = new Gets();
		List<Category> categories = gets.getCategories();

		for (Category cat : categories) {
			log.debug(cat.getName());
		}

		gets.login("user", "password");*/
	}

	public void testContent() throws Exception {
		/*NetworkProvider provider = new NetworkProvider();
		IContentConnection conn = null;

		try {
			conn = provider.loadContentItem("https://dl.dropboxusercontent.com/sh/x3qzpqcrqd7ftys/qNDPelAPa_/content.xml");
			InputStream in = conn.getStream();

			System.out.println(Utils.inputStreamToString(in));
		} finally {
			if (conn != null) {
				conn.close();
			}
		}*/
	}

	public void testFileStorage() throws Exception {
		/*FileStorage storage = FileStorage.createExternalStorage("teststorage");
		storage.getContent();

		NetworkProvider provider = new NetworkProvider();
		IContentConnection conn = provider.loadContentItem("https://dl.dropboxusercontent.com/sh/x3qzpqcrqd7ftys/qNDPelAPa_/content.xml");
		Content content = Content.createFromStream(conn.getStream());
		conn.close();

		log.debug("Size {}", content.getContent().size());
		for (IContentItem item : content.getContent()) {
			log.debug("Process item");
			IContentConnection dataConn = provider.loadContentItem(item.getUrl());
			storage.storeContentItem(item, dataConn.getStream());
			dataConn.close();
		}*/
	}

	public void testRemoteStorage() throws Exception {
		/*NetworkProvider provider = new NetworkProvider();
		FileStorage storage = FileStorage.createExternalStorage("teststorage");
		RemoteContent rem = new RemoteContent(storage);
		rem.initialize();

		for (IContentItem item : rem.getContentList(provider,
				"https://dl.dropboxusercontent.com/sh/x3qzpqcrqd7ftys/qNDPelAPa_/content.xml")) {
			IContentConnection conn = provider.loadContentItem(item.getUrl());
			rem.storeContentItem(item, storage, conn.getStream());
			conn.close();
		}*/
	}
}
