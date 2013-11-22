package org.fruct.oss.ikm.service;

import org.fruct.oss.ikm.Utils;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class LocationReceiver implements LocationListener {
	public interface Listener {
		void newLocation(Location location);
	}
	
	private static final String MOCK_PROVIDER = "mock-provider";
	
	private Context context;
	private LocationManager locationManager;
	private boolean isMockProviderEnabled = false;
	private Location oldLocation;
	private Listener listener;
	private boolean isDisableRealLocation = false;
	private boolean isStarted = false;
	private String lastReason = "";
	
	public LocationReceiver(Context context) {
		this.context = context;
		this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		
	}
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	public void mockLocation(Location location) {
		if (isMockProviderEnabled) {
			locationManager.setTestProviderLocation(MOCK_PROVIDER, location);
		}
	}
	
	private void setupMockProvider() {
		try {
			locationManager.addTestProvider(MOCK_PROVIDER, false, false,
					false, false, false, false, true, 0, 5);
		} catch (IllegalArgumentException ex) {
			// All ok, provider already exists
		} catch (SecurityException ex) {
			ex.printStackTrace();
			return;
		}
		
		try {				
			locationManager.setTestProviderEnabled(MOCK_PROVIDER, true);
			locationManager.requestLocationUpdates(MOCK_PROVIDER, 1000, 5, this);
			isMockProviderEnabled = true;
		} catch (SecurityException ex) {
			ex.printStackTrace();
		}
	}
	
	public void start() {
		try {
			if (!isDisableRealLocation) {
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 20, this);
				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 20, this);
			}
		} catch (SecurityException ex) {
			ex.printStackTrace();
		}
		
		
		setupMockProvider();
		isStarted = true;
	}
	
	public void stop() {
		locationManager.removeUpdates(this);
		
		// Destroy mock provider
		if (isMockProviderEnabled) {
			locationManager.removeTestProvider(MOCK_PROVIDER);
		}
		isStarted = false;
	}
	
	public boolean isStarted() {
		return isStarted;
	}
	
	public void disableRealLocation() {
		isDisableRealLocation = true;
	}
	
	private void newLocation(Location location) {
		String dbg = "LocationReceiver new location accuracy = " + location.getAccuracy()
				+ " provider = " + location.getProvider();
		
		if (isBetterLocation(location, oldLocation)) {
			oldLocation = location;
			
			if (listener != null) {
				listener.newLocation(location);
			}
			
			Utils.log(dbg + " accepted. Reason = " + lastReason);
		} else {
			Utils.log(dbg + " dropped. Reson = " + lastReason);
		}
	}

	private boolean isBetterLocation(Location location, Location oldLocation) {
		final long TIME_LIMIT = 2 * 60 * 1000;
		final float ACC_LIMIT = 200;
		
		if (oldLocation == null) {
			lastReason = "No old location";
			return true;
		}
		
		long timeDiff = location.getTime() - oldLocation.getTime();
		if (timeDiff > TIME_LIMIT) {
			lastReason = "Significantly newer";
			return true;
		} else if (timeDiff < -TIME_LIMIT) {
			lastReason = "Significantly older";
			return false;
		}
		
		int accDiff = (int) (location.getAccuracy() - oldLocation.getAccuracy());
		
		if (accDiff < 0) {
			lastReason = "Accuracy better";
			return true;
		} else if (timeDiff > 0 && accDiff <= 0) {
			lastReason = "Newer and not worse";
			return true;
		} else if (timeDiff > 0
				&& accDiff < ACC_LIMIT
				&& (location.getProvider() != null && location.getProvider()
						.equals(oldLocation.getProvider()))) {
			lastReason = "Same provider";
			return true;
		} else {
			lastReason = "Accuracy worse";
			return false;
		}
	}
	
	@Override
	public void onLocationChanged(Location location) {
		newLocation(location);
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}
}
