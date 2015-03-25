package org.fruct.oss.ikm.events;

import android.location.Location;
import android.support.annotation.NonNull;

public class LocationEvent {
	@NonNull
	private Location location;

	private int matchedNode;

	public LocationEvent(@NonNull Location location, int matchedNode) {
		this.location = location;
		this.matchedNode = matchedNode;
	}

	public LocationEvent(@NonNull Location location) {
		this.location = location;
		matchedNode = -1;
	}

	@NonNull
	public Location getLocation() {
		return location;
	}

	public int getMatchedNode() {
		return matchedNode;
	}
}
