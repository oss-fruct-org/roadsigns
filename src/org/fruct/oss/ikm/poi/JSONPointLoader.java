package org.fruct.oss.ikm.poi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.Utils;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSONPointLoader implements PointLoader {
	private static Logger log = LoggerFactory.getLogger(JSONPointLoader.class);
	private ArrayList<PointDesc> list = new ArrayList<PointDesc>();
	

	public JSONPointLoader(InputStream in) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		StringBuilder builder = new StringBuilder();
		
		String line = null;
		
		while ((line = reader.readLine()) != null) {
			builder.append(line).append('\n');
		}
		
		String content = builder.toString();
		load(content);

		reader.close();
	}
	
	private void load(String content) throws IOException {
		try {
			JSONObject root = new JSONObject(content);
			JSONObject objects = root.getJSONObject("response")
					.getJSONObject("ymaps")
					.getJSONObject("GeoObjectCollection");

			String collectionName = objects.getString("name");
			JSONArray arr = objects.getJSONArray("featureMembers");
			
			for (int i = 0; i < arr.length(); i++) {
				JSONObject obj = arr.getJSONObject(i).getJSONObject("GeoObject");
				loadObject(obj, collectionName);
			}
			
		} catch (JSONException e) {
			e.printStackTrace();
			throw new IOException();
		}
	}
	
	private void loadObject(JSONObject obj, String collectionName) throws JSONException {
		String name = obj.getString("name");
		name = name.replace("&quot;", "\"");
		
		JSONArray point = obj.getJSONArray("Point");
		double lat = point.getDouble(0);
		double lon = point.getDouble(1);
		
		PointDesc poi = new PointDesc(name, (int) (lon * 1e6), (int) (lat * 1e6));
		poi.setCategory(collectionName);
		list.add(poi);
	}
	
	@Override
	public List<PointDesc> getPoints() {
		Utils.log("" + list.size());
		return list;
	}

	public static JSONPointLoader createForAsset(String assetFile) throws IOException {
		InputStream in = App.getContext().getAssets().open(assetFile);
		return new JSONPointLoader(in);
	}
}
