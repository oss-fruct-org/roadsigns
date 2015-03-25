package org.fruct.oss.ikm.events;

import android.location.Location;
import android.support.annotation.NonNull;

public class LocationEvent {
	@NonNull
	private Location location;

	public LocationEvent(@NonNull Location location) {
		this.location = location;
	}

	@NonNull
	public Location getLocation() {
		return location;
	}
}
