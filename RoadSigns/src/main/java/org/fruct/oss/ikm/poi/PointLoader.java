package org.fruct.oss.ikm.poi;

import java.util.List;

public abstract class PointLoader {
	private List<PointDesc> points;

	/**
	 * Perform points loading
	 * This method can block
	 */
	public abstract void loadPoints();

	/**
	 * Should be called by implementation
	 * @param points loaded points
	 */
	protected void notifyPointsReady(List<PointDesc> points) {
		this.points = points;
	}

	/**
	 * Return loaded points
	 * @return loaded points or null of points not loaded yet
	 */
	public final List<PointDesc> getPoints() {
		return points;
	}
}