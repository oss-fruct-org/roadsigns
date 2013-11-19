package org.fruct.oss.ikm.poi;

/**
 * Filter that accepts any Point
 *
 */
public class AllFilter implements Filter {
	private boolean isActive = true;
	
	@Override
	public boolean accepts(PointDesc point) {
		return true;
	}

	@Override
	public String getString() {
		return "All";
	}

	@Override
	public boolean isActive() {
		return isActive;
	}

	@Override
	public void setActive(boolean active) {
		isActive = active;
	}

}
