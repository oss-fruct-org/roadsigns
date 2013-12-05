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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

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

		return true;
	}

	private void getCategories() {
		String response = null;

		try {
			response = downloadUrl(getsServerUrl, "<request><params><auth_token>qweasdzxc</auth_token></params></request>");
		} catch (IOException e) {
			e.printStackTrace();
			log.warn("Error");
			return;
		}
	}

	private String downloadUrl(String urlString, String postQuery) throws IOException {
		InputStream input = null;

		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setReadTimeout(10000);
		conn.setConnectTimeout(10000);
		conn.setRequestMethod("POST");
		conn.setDoInput(true);
		conn.setDoOutput(true);

		if (postQuery != null) {
			OutputStream out = conn.getOutputStream();

			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), "UTF-8"));
			writer.write(postQuery);
			writer.flush();
			writer.close();
		}

		conn.connect();

		int responseCode = conn.getResponseCode();
		InputStream responseStream = conn.getInputStream();
		String response = Utils.inputStreamToString(responseStream);

		log.debug("Response code {}, response {}", responseCode, response);

		return null;
	}
}
