package org.fruct.oss.ikm.log;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class TestLoggerFactory  implements ILoggerFactory {
	static final String ANONYMOUS_TAG = "null";
	static final int TAG_MAX_LENGTH = 23;

	@Override
	public Logger getLogger(String name) {
		return new TestLogger(nameToTag(name));
	}

	private String nameToTag(String name) {
		if (name == null)
			return ANONYMOUS_TAG;

		StringBuilder builder = new StringBuilder();

		if (name.startsWith("org.fruct")) {
			builder.append("RS: ");
		}

		int cutStart = 0;

		int lastDot = name.lastIndexOf(".");
		if (lastDot > 0) {
			cutStart = lastDot + 1;
		}

		builder.append(name.substring(cutStart, Math.min(name.length(), cutStart + TAG_MAX_LENGTH - builder.length())));

		return builder.toString();
	}
}
