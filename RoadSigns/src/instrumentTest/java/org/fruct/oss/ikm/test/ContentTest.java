package org.fruct.oss.ikm.test;

import android.content.Context;
import android.test.AndroidTestCase;

import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.gets.CategoriesList;
import org.fruct.oss.ikm.poi.gets.Response;
import org.fruct.oss.ikm.storage.Content;
import org.fruct.oss.ikm.storage.ContentItem;
import org.fruct.oss.ikm.storage.IContentItem;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ContentTest extends AndroidTestCase {
	private Context testContext;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		if (testContext == null) {
			testContext = getContext().createPackageContext(
					"org.fruct.oss.ikm.test", Context.CONTEXT_IGNORE_SECURITY);
		}
	}

	public void testContentXml() throws Exception{
		Serializer serializer = new Persister();
		InputStream stream = null;

		try {
			stream = testContext.getAssets().open("content.xml");
			Content content = serializer.read(Content.class, stream);

			List<IContentItem> contentList = content.getContent();
			assertEquals(1, contentList.size());
			IContentItem item = contentList.get(0);

			assertEquals("karelia.map", item.getName());
			assertEquals("http://www.dropbox.com/sh/p92qawz4omqnfxd/9WTLh0z-su/karelia.map", item.getUrl());
			assertEquals(34482377, item.getSize());
			assertEquals("mapsforge-map", item.getType());
			assertEquals("77b9ecd66b2153f211de887c02fc157b", item.getHash());
		} finally {
			if (stream != null)
				stream.close();
		}
	}
}
