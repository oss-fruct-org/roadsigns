package org.fruct.oss.ikm.test;

import android.content.Context;
import android.test.AndroidTestCase;

import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.gets.CategoriesList;
import org.fruct.oss.ikm.poi.gets.Kml;
import org.fruct.oss.ikm.poi.gets.Response;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.fruct.oss.ikm.poi.gets.CategoriesList.Category;
import static org.fruct.oss.ikm.poi.gets.Kml.Document;
import static org.fruct.oss.ikm.poi.gets.Kml.Placemark;

public class GetsTest extends AndroidTestCase {
	private Context testContext;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		if (testContext == null) {
			testContext = getContext().createPackageContext(
					"org.fruct.oss.ikm.test", Context.CONTEXT_IGNORE_SECURITY);
		}
	}

	public void testCategoryXml() throws Exception {
		InputStream stream = null;
		try {
			stream = testContext.getAssets().open("categories.xml");
			Serializer serializer = new Persister();

			Response response = serializer.read(Response.class, Utils.inputStreamToString(stream));
			assertNotNull(response);
			assertNotNull(response.getContent());
			assertTrue(response.getContent() instanceof CategoriesList);
			assertEquals("success", response.getMessage());
			assertEquals(1, response.getCode());


			CategoriesList list = (CategoriesList) response.getContent();

			List<Category> categories = list.getCategories();

			assertEquals("shops", categories.get(0).getName());
		} finally {
			if (stream != null)
				stream.close();
		}
	}

	public void testKml() throws Exception {
		InputStream stream = null;
		try {
			stream = testContext.getAssets().open("testKml.xml");

			Serializer serializer = new Persister();
			Kml kml = serializer.read(Kml.class, stream);

			assertNotNull(kml);

			Document doc = kml.getDocument();
			assertEquals("tests", doc.getName());
			assertEquals("description", doc.getDescription());
			assertEquals(1, doc.getOpen());

			assertEquals(2, doc.getPlacemarks().size());

			Placemark mark1 = doc.getPlacemarks().get(0);
			assertEquals("test place 1", mark1.getName());
			assertEquals("test description 1", mark1.getDescription());
			assertEquals(12.0, mark1.getLatitude(), 0.01);
			assertEquals(34.0, mark1.getLongitude(), 0.01);

			Placemark mark2 = doc.getPlacemarks().get(1);
			assertEquals("test place 2", mark2.getName());
			assertEquals("test description 2", mark2.getDescription());
			assertEquals(34.1, mark2.getLatitude(), 0.01);
			assertEquals(56.0, mark2.getLongitude(), 0.01);
		} finally {
			if (stream != null)
				stream.close();
		}
	}
}
