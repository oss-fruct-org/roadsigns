package org.fruct.oss.ikm.poi.gets;

public class GetsException extends Exception {
	public GetsException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public GetsException(String detailMessage) {
		super(detailMessage);
	}

	public GetsException(Throwable throwable) {
		super(throwable);
	}
}
