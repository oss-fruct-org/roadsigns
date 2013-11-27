package org.fruct.oss.ikm;

public class Smoother {
	private long prevTime = 0;
	private float prevValue = 0;
	
	private float tau;
	
	public Smoother(float tau) {
		this.tau = tau;
	}
	
	public void insert(float value, long time) {
		if (prevTime == 0) {
			prevTime = time;
			prevValue = value;
		} else {
			long deltaTime = (time - prevTime);
			float alpha = (float) (1 - Math.exp(-deltaTime / tau));
			prevTime = time;
			prevValue += alpha * (value - prevValue);
		}
	}
	
	public float average() {
		return prevValue;
	}
}
