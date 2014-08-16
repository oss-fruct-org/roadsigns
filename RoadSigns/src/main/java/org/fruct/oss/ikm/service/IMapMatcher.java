package org.fruct.oss.ikm.service;

import android.annotation.TargetApi;
import android.location.Location;
import android.os.Build;

public interface IMapMatcher {
	boolean updateLocation(Location location);
	Location getMatchedLocation();
	int getMatchedNode();
}
