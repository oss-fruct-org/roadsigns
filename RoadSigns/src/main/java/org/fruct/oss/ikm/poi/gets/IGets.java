package org.fruct.oss.ikm.poi.gets;

import org.fruct.oss.ikm.poi.PointDesc;

import java.io.IOException;
import java.util.List;

/**
 * Interface for GeTS service
 */
public interface IGets {
	boolean checkAvailability();

	String login(String username, String password);
	List<CategoriesList.Category> getCategories();
	List<PointDesc> getPoints();
}
