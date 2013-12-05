package org.fruct.oss.ikm.poi;

/**
 * Interface for filtering POI data
 *
 */
public interface Filter {
	/**
	 * Check whether point pass filter
	 * @param point
	 * @return true if filter accepts point false otherwise
	 */
	boolean accepts(PointDesc point);
	
	/**
	 * String representation of filter
	 * @return string
	 */
	String getString();
	
	boolean isActive();
	void setActive(boolean active);
}
