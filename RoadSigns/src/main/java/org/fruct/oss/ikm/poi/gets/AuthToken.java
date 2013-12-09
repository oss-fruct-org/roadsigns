package org.fruct.oss.ikm.poi.gets;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;

@Root(name = "auth_token", strict = false)
public class AuthToken implements IContent {
	@Text
	private String token;

	public String getToken() {
		return token;
	}
}
