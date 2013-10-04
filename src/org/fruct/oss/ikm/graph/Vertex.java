package org.fruct.oss.ikm.graph;

import java.util.ArrayList;
import java.util.List;

public class Vertex {	
	Vertex from;
	int g, f, h;
	
	List<Edge> neig = new ArrayList<Edge>();
	int id;
	
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
	
	public int h() {
		return 0;
	}
	
	public int getId() {
		return id;
	}
}