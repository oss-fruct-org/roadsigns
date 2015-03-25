package org.fruct.oss.ikm.events;

public class TrackingModeEvent {
	private boolean isInTrackingMode;

	public TrackingModeEvent(boolean isInTrackingMode) {
		this.isInTrackingMode = isInTrackingMode;
	}

	public boolean isInTrackingMode() {
		return isInTrackingMode;
	}
}
