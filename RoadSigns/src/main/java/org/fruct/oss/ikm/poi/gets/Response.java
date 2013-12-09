package org.fruct.oss.ikm.poi.gets;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;

@Root(name = "response", strict = false)
public class Response {
	@Element(name = "code")
	@Path("status")
	private int code;

	@Element(name = "message")
	@Path("status")
	private String message;

	@Path("content")
	@ElementUnion({
		@Element(name = "kml", type = Kml.class),
		@Element(name = "categories", type = CategoriesList.class),
		@Element(name = "auth_token", type = AuthToken.class)
	})
	private IContent content;

	public IContent getContent() {
		return content;
	}

	public String getMessage() {
		return message;
	}

	public int getCode() {
		return code;
	}
}
