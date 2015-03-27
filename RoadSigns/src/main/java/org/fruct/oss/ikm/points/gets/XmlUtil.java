package org.fruct.oss.ikm.points.gets;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;

public class XmlUtil {
	public static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
		int depth = 1;
		if (parser.getEventType() != XmlPullParser.START_TAG) {
			throw new IllegalStateException("Parser must be on start tag");
		}
		while (depth > 0) {
			switch (parser.next()) {
			case XmlPullParser.START_TAG:
				depth++;
				break;
			case XmlPullParser.END_TAG:
				depth--;
				break;
			}
		}
	}

	public static String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
		String result = "";
		if (parser.next() == XmlPullParser.TEXT) {
			result = parser.getText();
			parser.nextTag();
		}
		return result;
	}

	public static int readNumber(XmlPullParser parser) throws IOException, XmlPullParserException {
		try {
			String text = readText(parser);
			return Integer.parseInt(text);
		} catch (NumberFormatException ex) {
			throw new XmlPullParserException("Expected number");
		}
	}
}