package org.fruct.oss.ikm.poi.gets;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.util.List;

@Root(name = "content", strict = false)
public class CategoriesList implements IContent{
	@ElementList(name = "categories", inline = true, entry = "category")
	private List<Category> categories;

	public List<Category> getCategories() {
		return categories;
	}

	@Root(name = "category", strict = false)
	public static class Category {
		private final int id;
		private final String name;
		private final String description;
		private final String url;

		public Category(@Element(name="url") String url,
						@Element(name="name") String name,
						@Element(name="description") String description,
						@Element(name="id") int id) {
			this.url = url;
			this.name = name;
			this.description = description;
			this.id = id;
		}

		@Element(name="id")
		public int getId() {
			return id;
		}

		@Element(name="name")
		public String getName() {
			return name;
		}

		@Element(name="description")
		public String getDescription() {
			return description;
		}

		@Element(name="url")
		public String getUrl() {
			return url;
		}

		@Override
		public String toString() {
			return "Category{" +
					"id=" + id +
					", name='" + name + '\'' +
					", description='" + description + '\'' +
					", url='" + url + '\'' +
					'}';
		}
	}
}
