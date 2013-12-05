package org.fruct.oss.ikm.poi;

public class CategoryFilter implements Filter {
	private String category;
	private boolean active = true;
	private String name;

	public CategoryFilter(String category, String name) {
		this.category = category;
		this.name = name;
	}
	
	@Override
	public boolean accepts(PointDesc point) {
		return category.equals(point.getCategory());
	}

	@Override
	public String getString() {
		return name;
	}
	
	@Override
	public boolean isActive() {
		return active;
	}
	
	@Override
	public void setActive(boolean active) {
		this.active = active;
	}
}
