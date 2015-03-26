package org.fruct.oss.ikm.poi.gets;

import android.content.Context;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.poi.gets.parsers.CategoriesContent;
import org.fruct.oss.ikm.poi.gets.parsers.CategoriesParser;
import org.fruct.oss.ikm.poi.gets.parsers.Kml;
import org.fruct.oss.ikm.poi.gets.parsers.KmlParser;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class Gets {
	public static final String GET_CATEGORIES_REQUEST =
			"<request><params/></request>";

	private String getsServerUrl;
	private Context context;
	private static Logger log =  LoggerFactory.getLogger(Gets.class);

	public Gets(String getsServerUrl) {
		if (getsServerUrl.endsWith("/")) {
			this.getsServerUrl = getsServerUrl;
		} else {
			this.getsServerUrl = getsServerUrl + "/";
		}
		context = App.getContext();
	}

	/**
	 * Receive list of categories
	 * @return list of categories
	 * @throws IOException
	 */
	public List<Category> getCategories() throws IOException {
		try {
			String responseStr = downloadUrl(getsServerUrl + "getCategories.php", String.format(GET_CATEGORIES_REQUEST));
			GetsResponse<CategoriesContent> resp = GetsResponse.parse(responseStr, new CategoriesParser());

			if (resp.getCode() != 0) {
				log.warn("getCategories returned with code {} message '{}'", resp.getCode(), resp.getMessage());
				throw new GetsException("Server return error");
			}

			CategoriesContent categories = resp.getContent();
			return categories.getCategories();
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception e) {
			throw new IOException("Incorrect answer from server");
		}
	}

	public List<PointDesc> getPoints(Category category, GeoPoint position, int radius) throws IOException, GetsException {
		try {
			StringBuilder requestBuilder = new StringBuilder();
			requestBuilder.append("<request><params>");

			requestBuilder.append("<latitude>").append(position.getLatitude()).append("</latitude>");
			requestBuilder.append("<longitude>").append(position.getLongitude()).append("</longitude>");
			requestBuilder.append("<radius>").append(radius / 1000.0).append("</radius>");

			if (category != null) {
				requestBuilder.append("<category_id>").append(category.getId()).append("</category_id>");
			}

			requestBuilder.append("</params></request>");

			String responseStr = downloadUrl(getsServerUrl + "loadPoints.php",requestBuilder.toString());
			log.trace("Req {}", requestBuilder.toString());
			GetsResponse<Kml> kmlGetsResponse = GetsResponse.parse(responseStr, new KmlParser());
			if (kmlGetsResponse.getCode() != 0) {
				log.warn("getCategories returned with code {} message '{}'", kmlGetsResponse.getCode(),
						kmlGetsResponse.getMessage());
				throw new GetsException("Server return error");
			}

			Kml kml = kmlGetsResponse.getContent();

			for (PointDesc pointDesc : kml.getPoints()) {
				pointDesc.setCategory(category == null ? "Unclassified" : category.getName());
			}

			return kml.getPoints();
		} catch (RuntimeException ex) {
			// simple-xml throws too generic Exception
			throw ex;
		} catch (Exception e) {
			log.warn("Incorrect answer from server", e);
			throw new IOException("Incorrect answer from server");
		}
	}

	// TODO: Move this method to Utils
	public static String downloadUrl(String urlString, String postQuery) throws IOException {
		HttpURLConnection conn = null;
		InputStream responseStream = null;

		try {
			URL url = new URL(urlString);
			conn = (HttpURLConnection) url.openConnection();
			conn.setReadTimeout(10000);
			conn.setConnectTimeout(10000);
			conn.setRequestMethod(postQuery == null ? "GET" : "POST");
			conn.setDoInput(true);
			conn.setDoOutput(postQuery != null);
			conn.setRequestProperty("User-Agent", "RoadSigns/0.4 (http://oss.fruct.org/projects/roadsigns/)");

			if (postQuery != null) {
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), "UTF-8"));
				writer.write(postQuery);
				writer.flush();
				writer.close();
			}

			conn.connect();

			int responseCode = conn.getResponseCode();
			responseStream = conn.getInputStream();
			String response = Utils.inputStreamToString(responseStream);

			log.trace("Request url {} data {}", urlString, postQuery);
			log.trace("Response code {}, response {}", responseCode, response);

			return response;
		} finally {
			if (conn != null)
				conn.disconnect();

			if (responseStream != null)
				responseStream.close();
		}
	}
}
