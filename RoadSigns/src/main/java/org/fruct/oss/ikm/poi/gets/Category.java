package org.fruct.oss.ikm.poi.gets;

public class Category {
	private final long id;
	private final String name;
	private final String description;
	private final String url;

	public Category(String url,
					String name,
					String description,
					long id) {
		this.url = url;
		this.name = name;
		this.description = description;
		this.id = id;
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getUrl() {
		return url;
	}
}
