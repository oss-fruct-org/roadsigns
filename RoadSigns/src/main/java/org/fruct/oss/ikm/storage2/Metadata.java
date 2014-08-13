package org.fruct.oss.ikm.storage2;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;

import java.util.Map;

@Root(name = "file", strict = false)
public class Metadata {
	@Element(name = "region-id")
	private String regionId;

	@Element(name = "name")
	private String name;

	@ElementMap(entry = "description", key="lang", attribute=true, inline=true)
	private Map<String, String> description;

	public String getRegionId() {
		return regionId;
	}

	public String getName() {
		return name;
	}

	public Map<String, String> getDescription() {
		return description;
	}
}
