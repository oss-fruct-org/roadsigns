package org.fruct.oss.ikm;

import java.util.ArrayDeque;
import java.util.Iterator;

public class MovingAverage {
	private class Item {
		Item(float value, long time) {
			this.value = value;
			this.time = time;
		}
		
		float value;
		long time;
	}
	
	private ArrayDeque<Item> history = new ArrayDeque<Item>();
	private long windowMs;
	private float average = 0;
	
	public MovingAverage(long windowMs) {
		this.windowMs = windowMs;
	}
	
	public void insert(float value, long time) {
		history.addLast(new Item(value, time));
		
		update(time);
	}
	
	public float average() {
		// XXX: return possibly obsoleted data
		return average;
	}
	
	// TODO: this should be integral to simply sum/count
	private void update(long time) {
		if (history.isEmpty()) {
			average = 0;
			return;
		}
		
		Iterator<Item> iter = history.iterator();
		long minTime = time - windowMs;
		
		double sum = 0.0;
		
		while (iter.hasNext()) {
			Item item = iter.next();
			if (item.time < minTime) {
				iter.remove();
			} else {
				sum += item.value;
			}
		}
		
		average = (float) (sum / history.size());
	}
}
