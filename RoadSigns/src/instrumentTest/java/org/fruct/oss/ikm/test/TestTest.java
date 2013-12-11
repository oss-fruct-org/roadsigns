package org.fruct.oss.ikm.test;

import android.test.AndroidTestCase;

import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.gets.CategoriesList;
import org.fruct.oss.ikm.poi.gets.Gets;
import org.fruct.oss.ikm.poi.gets.IGets;
import org.fruct.oss.ikm.storage.Content;
import org.fruct.oss.ikm.storage.FileStorage;
import org.fruct.oss.ikm.storage.IContentConnection;
import org.fruct.oss.ikm.storage.IContentItem;
import org.fruct.oss.ikm.storage.NetworkProvider;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

import static org.fruct.oss.ikm.poi.gets.CategoriesList.Category;

@Root(strict = false)
class Entity {
	@Element
	public String a;

	@Element
	public String b;
}

public class TestTest extends AndroidTestCase {
	private Logger log = LoggerFactory.getLogger(TestTest.class);

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
}
