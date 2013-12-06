package org.fruct.oss.ikm.poi.gets;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import static org.fruct.oss.ikm.poi.gets.CategoriesResponse.Category;

public class Gets {
	private String getsServerUrl;
	private Context context;
	private Logger log =  LoggerFactory.getLogger(Gets.class);

	public Gets(String getsServerUrl) {
		this.getsServerUrl = getsServerUrl;
		context = App.getContext();
	}

	public boolean checkAvailability() {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = connManager.getActiveNetworkInfo();
		if (info != null && info.isConnected()) {
			Toast.makeText(context, "Yes network", Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(context, "No network", Toast.LENGTH_LONG).show();
			return false;
		}

		List<Category> categories = getCategories();

		for (Category cat : categories) {
			log.debug("Category {}", cat);
		}

		return true;
	}

	private List<Category> getCategories() {
		String response;

		try {
			response = downloadUrl(getsServerUrl + "getCategories.php", "<request><params><auth_token>qweasdzxc</auth_token></params></request>");
			return CategoriesResponse.createFromXml(response).getContent().getCategories();
		} catch (Exception e) {
			log.warn("Error: ", e);
			return null;
		}
	}

	private String downloadUrl(String urlString, String postQuery) throws IOException {
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
}
