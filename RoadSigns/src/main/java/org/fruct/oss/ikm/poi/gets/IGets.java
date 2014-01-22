package org.fruct.oss.ikm.poi.gets;

import org.fruct.oss.ikm.poi.PointDesc;
import org.osmdroid.util.GeoPoint;

import java.io.IOException;
import java.util.List;

/**
 * Interface for GeTS service
 */
public interface IGets {
	String login(String username, String password) throws IOException;
	List<CategoriesList.Category> getCategories() throws IOException, LoginException;
	List<PointDesc> getPoints(String category, GeoPoint position) throws IOException, LoginException;
}
