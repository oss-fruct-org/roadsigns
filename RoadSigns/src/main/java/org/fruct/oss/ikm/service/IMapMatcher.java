package org.fruct.oss.ikm.service;

import android.annotation.TargetApi;
import android.location.Location;
import android.os.Build;

public interface IMapMatcher {
	void updateLocation(Location location);

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	Location getMatchedLocation();
}
