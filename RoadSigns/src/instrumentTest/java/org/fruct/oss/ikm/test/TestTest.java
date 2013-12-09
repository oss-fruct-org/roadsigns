package org.fruct.oss.ikm.test;

import android.test.AndroidTestCase;

import org.fruct.oss.ikm.poi.gets.CategoriesList;
import org.fruct.oss.ikm.poi.gets.Gets;
import org.fruct.oss.ikm.poi.gets.IGets;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
}
