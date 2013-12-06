package org.fruct.oss.ikm.test;

import android.content.Context;
import android.test.AndroidTestCase;

import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.gets.CategoriesResponse;
import org.fruct.oss.ikm.poi.gets.CategoriesResponse.Category;
import org.fruct.oss.ikm.poi.gets.CategoriesResponse.Content;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

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
			CategoriesResponse response = CategoriesResponse.createFromXml(Utils.inputStreamToString(stream));

			List<Category> categories = response.getContent().getCategories();

			assertEquals("shops", categories.get(0).getName());
		} finally {
			if (stream != null)
				stream.close();
		}
	}
}
