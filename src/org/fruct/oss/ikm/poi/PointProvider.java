package org.fruct.oss.ikm.poi;

import java.util.List;

public interface PointProvider {
	List<PointDesc> getPoints(int latE6, int lonE6, int radius);
}
