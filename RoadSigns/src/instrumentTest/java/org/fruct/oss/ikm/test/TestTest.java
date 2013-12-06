package org.fruct.oss.ikm.test;

import android.test.AndroidTestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

@Root(strict = false)
class Entity {
	@Element
	public String a;

	@Element
	public String b;
}

public class TestTest extends AndroidTestCase {
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
}
