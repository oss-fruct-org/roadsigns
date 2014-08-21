package org.fruct.oss.ikm.utils;


public final class Timer {
	private long accumulated = 0;
	private long time = -1;

	public final void start() {
		time = System.currentTimeMillis();
	}

	public final void stop() {
		if (time < 0)
			throw new IllegalStateException("Timer haven't been started");

		accumulated += System.currentTimeMillis() - time;
		time = -1;
	}

	public long getAcc() {
		return accumulated;
	}

	public void reset() {
		accumulated = 0;
		time = -1;
	}
}
