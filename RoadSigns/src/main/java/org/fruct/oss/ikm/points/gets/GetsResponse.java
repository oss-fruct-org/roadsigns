package org.fruct.oss.ikm.points.gets;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;

public class GetsResponse<T extends IContent> {
	private int code;
	private String message;
	private T content;

	public int getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public T getContent() {
		return content;
	}

	public static <T extends IContent> GetsResponse<T> parse(String responseXml,
														  ContentParser<T> contentParser) throws GetsException {
		try {
			XmlPullParser parser = Xml.newPullParser();
			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
			parser.setInput(new StringReader(responseXml));
			parser.nextTag();
			return readGetsResponse(parser, contentParser);
		} catch (XmlPullParserException | IOException e) {
			throw new GetsException(e);
		}
	}

	private static <T extends IContent> GetsResponse<T> readGetsResponse(XmlPullParser parser,
																	  ContentParser<T> contentParser) throws IOException, XmlPullParserException {
		GetsResponse<T> resp = new GetsResponse<>();
		parser.require(XmlPullParser.START_TAG, null, "response");

		while (parser.next() != XmlPullParser.END_TAG) {
			if (parser.getEventType() != XmlPullParser.START_TAG)
				continue;
			String tagName = parser.getName();
			if (tagName.equals("status")) {
				readStatus(resp, parser);
			} else if (tagName.equals("content") && contentParser != null) {
				resp.content = contentParser.parse(parser);
			} else {
				XmlUtil.skip(parser);
			}
		}

		return resp;
	}
	private static void readStatus(GetsResponse out, XmlPullParser parser) throws IOException, XmlPullParserException {
		parser.require(XmlPullParser.START_TAG, null, "status");
		while (parser.next() != XmlPullParser.END_TAG) {
			if (parser.getEventType() != XmlPullParser.START_TAG)
				continue;
			String tagName = parser.getName();
			if (tagName.equals("code")) {
				parser.require(XmlPullParser.START_TAG, null, "code");
				out.code = Integer.parseInt(readText(parser));
				parser.require(XmlPullParser.END_TAG, null, "code");
			} else if (tagName.equals("message")) {
				parser.require(XmlPullParser.START_TAG, null, "message");
				out.message = readText(parser);
				parser.require(XmlPullParser.END_TAG, null, "message");
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
}
