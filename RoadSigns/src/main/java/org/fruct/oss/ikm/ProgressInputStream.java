package org.fruct.oss.ikm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProgressInputStream extends FilterInputStream {
	private int max;
	private int reportInterval;
	private ProgressListener listener;

	private int current = 0;
	private int oldReportIntervals = 0;

	public interface ProgressListener {
		void update(int current, int max);
	}

	public ProgressInputStream(InputStream in, int max, int reportInterval, ProgressListener listener) {
		super(in);
		this.max = max;
		this.reportInterval = reportInterval;
		this.listener = listener;
	}

	private void increaseCurrent(int delta) {
		current += delta;

		int newReportIntervals = current / reportInterval;
		if (newReportIntervals > oldReportIntervals) {
			oldReportIntervals = newReportIntervals;
			if (listener != null) {
				listener.update(current, max);
			}
		}
	}

	@Override
	public int read() throws IOException {
		final int c = super.read();
		if (c != -1)
			increaseCurrent(1);

		return c;
	}

	@Override
	public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
		final int l = super.read(buffer, byteOffset, byteCount);

		if (l > 0)
			increaseCurrent(l);

		return l;
	}
}
