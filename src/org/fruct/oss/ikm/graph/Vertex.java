package org.fruct.oss.ikm.graph;

import java.util.ArrayList;
import java.util.List;

public class Vertex {	
	Vertex from;
	int g, f, h;
	
	List<Edge> neig = new ArrayList<Edge>();
	int id;
	
	int closedTime = 0, openTime = 0;
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Vertex other = (Vertex) obj;
		if (id != other.id)
			return false;
		return true;
	}
	
	public int getId() {
		return id;
	}
	
	public int h(Vertex target) {
		return 0;
	}
	
	public final boolean isClosed(int currentTime) {
		return currentTime == closedTime;
	}
	
	public final boolean isOpen(int currentTime) {
		return currentTime == openTime;
	}
	
	public final void close(int currentTime) {
		closedTime = currentTime;
	}
	
	public final void open(int currentTime) {
		openTime = currentTime;
	}
}