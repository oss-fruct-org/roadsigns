package org.fruct.oss.ikm.poi.gets;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import static org.fruct.oss.ikm.poi.gets.CategoriesList.Category;

public class Gets implements IGets {
	public static final String GET_CATEGORIES_REQUEST =
			"<request><params>" +
				"<auth_token>%s</auth_token>" +
			"</params></request>";
	public static final String LOGIN_REQUEST =
			"<request><params>" +
				"<login>%s</login>" +
				"<password>%s</password>" +
			"</params></request>";

	private String token = null;
	private String getsServerUrl;
	private Context context;
	private Logger log =  LoggerFactory.getLogger(Gets.class);

	public Gets(String getsServerUrl) {
		if (getsServerUrl.endsWith("/")) {
			this.getsServerUrl = getsServerUrl;
		} else {
			this.getsServerUrl = getsServerUrl + "/";
		}
		context = App.getContext();
	}

	/**
	 * Login in GeTS to receive auth token
	 * @param username Username
	 * @param password Password
	 * @return auth token or null if login incorrect
	 * @throws IOException
	 */
	@Override
	public String login(String username, String password) throws IOException {
		try {
			String responseStr = downloadUrl(getsServerUrl + "login.php", String.format(LOGIN_REQUEST, username, password));
			Response resp = processResponse(responseStr);

			if (resp.getCode() != 0) {
				log.warn("login returned with code {} message '{}'", resp.getCode(), resp.getMessage());
				return null;
			}

			AuthToken authToken = (AuthToken) resp.getContent();
			return authToken.getToken();
		} catch (RuntimeException ex) {
			// Simple XMl throws Exception on any error
			throw ex;
		} catch (Exception e) {
			throw new IOException("Incorrect answer from server");
		}
	}

	/**
	 * Receive list of categories
	 * @return list of categories
	 * @throws IOException
	 */
	@Override
	public List<Category> getCategories() throws IOException {
		try {
			String responseStr = downloadUrl(getsServerUrl + "getCategories.php", String.format(GET_CATEGORIES_REQUEST, token));
			Response resp = processResponse(responseStr);

			if (resp.getCode() != 0) {
				log.warn("getCategories returned with code {} message '{}'", resp.getCode(), resp.getMessage());
				throw new LoginException("Server return error");
			}

			CategoriesList categories = (CategoriesList) resp.getContent();
			return categories.getCategories();
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception e) {
			throw new IOException("Incorrect answer from server");
		}
	}

	@Override
	public List<PointDesc> getPoints(final String category) throws IOException, LoginException {
		try {
			StringBuilder requestBuilder = new StringBuilder();
			requestBuilder.append("<request><params>");

			if (token != null)
				requestBuilder.append("<auth_token>").append(token).append("</auth_token>");

			if (category != null) {
				requestBuilder.append("<category_name>").append(category).append("</category_name>");
			} else {
				requestBuilder.append("<latitude>").append(61).append("</latitude>");
				requestBuilder.append("<longitude>").append(34).append("</longitude>");
				requestBuilder.append("<radius>").append(10000).append("</radius>");
			}

			requestBuilder.append("</params></request>");

			String responseStr = downloadUrl(getsServerUrl + "loadPoints.php",requestBuilder.toString());
			Response resp = processResponse(responseStr);
			if (resp.getCode() != 0) {
				log.warn("getCategories returned with code {} message '{}'", resp.getCode(), resp.getMessage());
				throw new LoginException("Server return error");
			}

			Kml kml = (Kml) resp.getContent();
			Kml.Document document = kml.getDocument();
			List<PointDesc> pointList = Utils.map(document.getPlacemarks(), new Utils.Function<PointDesc, Kml.Placemark>() {
				@Override
				public PointDesc apply(Kml.Placemark placemark) {
					return placemark.toPointDesc().setCategory(category == null ? "Unclassified" : category);
				}
			});
			return pointList;
		} catch (RuntimeException ex) {
			// simple-xml throws too generic Exception
			throw ex;
		} catch (Exception e) {
			log.warn("Incorrect answer from server", e);
			throw new IOException("Incorrect answer from server");
		}
	}

	public String downloadUrl(String urlString, String postQuery) throws IOException {
		HttpURLConnection conn = null;
		InputStream responseStream = null;

		try {
			URL url = new URL(urlString);
			conn = (HttpURLConnection) url.openConnection();
			conn.setReadTimeout(10000);
			conn.setConnectTimeout(10000);
			conn.setRequestMethod("POST");
			conn.setDoInput(true);
			conn.setDoOutput(true);

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

	public Response processResponse(String responseStr) throws Exception {
		Serializer serializer = new Persister();
		return serializer.read(Response.class, responseStr);
	}
}
