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
	public static final String GET_CATEGORIES_REQUEST = "<request><params><auth_token>%s</auth_token></params></request>";
	public static final String LOGIN_REQUEST = "<request><params><login>%s</login><password>%s</password></params></request>";

	private String token = "notoken";
	private String getsServerUrl;
	private Context context;
	private Logger log =  LoggerFactory.getLogger(Gets.class);

	public Gets(String getsServerUrl) {
		this.getsServerUrl = getsServerUrl;
		context = App.getContext();
	}

	@Override
	public String login(String username, String password) {
		try {
			String responseStr = downloadUrl(getsServerUrl + "login.php", String.format(LOGIN_REQUEST, username, password));
			Response resp = processResponse(responseStr);

			if (resp.getCode() != 0) {
				log.warn("login returned with code {} message '{}'", resp.getCode(), resp.getMessage());
				return null;
			}

			AuthToken authToken = (AuthToken) resp.getContent();
			return authToken.getToken();
		} catch (Exception e) {
			log.warn("Error: ", e);
			return null;
		}
	}

	@Override
	public boolean checkAvailability() {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = connManager.getActiveNetworkInfo();
		return info != null && info.isConnected();
	}

	@Override
	public List<Category> getCategories() {
		try {
			String responseStr = downloadUrl(getsServerUrl + "getCategories.php", String.format(GET_CATEGORIES_REQUEST, token));
			Response resp = processResponse(responseStr);

			if (resp.getCode() != 0) {
				log.warn("getCategories returned with code {} message '{}'", resp.getCode(), resp.getMessage());
				return null;
			}

			CategoriesList categories = (CategoriesList) resp.getContent();
			return categories.getCategories();
		} catch (Exception e) {
			log.warn("Error: ", e);
			return null;
		}
	}

	@Override
	public List<PointDesc> getPoints() {
		return null;
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
