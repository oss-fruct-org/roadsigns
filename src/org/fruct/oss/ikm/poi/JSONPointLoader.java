package org.fruct.oss.ikm.poi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.Utils;
import org.json.*;

public class JSONPointLoader implements PointLoader {
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
	
	private void load(String content) {
		try {
			JSONObject root = new JSONObject(content);
			JSONObject objects = root.getJSONObject("response")
					.getJSONObject("ymaps")
					.getJSONObject("GeoObjectCollection");
			
			JSONArray arr = objects.getJSONArray("featureMembers");
			
			for (int i = 0; i < arr.length(); i++) {
				JSONObject obj = arr.getJSONObject(i).getJSONObject("GeoObject");
				loadObject(obj);
			}
			
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		
	}
	
	private void loadObject(JSONObject obj) throws JSONException {
		String name = obj.getString("name");
		name = name.replace("&quot;", "\"");
		
		JSONArray point = obj.getJSONArray("Point");
		double lat = point.getDouble(0);
		double lon = point.getDouble(1);
		
		PointDesc poi = new PointDesc(name, (int) (lon * 1e6), (int) (lat * 1e6));
		poi.setCategory("culture");
		list.add(poi);
	}
	
	@Override
	public List<PointDesc> getPoints() {
		Utils.log("" + list.size());
		return list;
	}

}
