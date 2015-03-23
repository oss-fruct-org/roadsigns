package org.fruct.oss.ikm.service;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocationReceiver implements LocationListener {
	private static Logger log = LoggerFactory.getLogger(LocationReceiver.class);

	public static final String MOCK_PROVIDER = "org.fruct.oss.socialnavigator.MockProvider";

	private String vehicle = "CAR";
	private LocationManager locationManager;
	private Location oldLocation;
	private Listener listener;

	private boolean isDisableRealLocation = false;
	private boolean isStarted = false;

	private String lastReason = "";

	public LocationReceiver(Context context) {
		this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void mockLocation(Location location) {
		newLocation(location);
	}

	public void start() {
		isStarted = true;

		setupUpdates();
	}

	private void setupUpdates() {
		try {
			if (!isDisableRealLocation) {
				Criteria criteria = new Criteria();
				criteria.setAccuracy(Criteria.ACCURACY_FINE);

				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, getMillsFreq(vehicle), getMeterFreq(vehicle), this, Looper.getMainLooper());
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, getMillsFreq(vehicle), getMeterFreq(vehicle), this, Looper.getMainLooper());
			}
		} catch (Exception ex) {
			log.error("Can't setup location providers", ex);
		}
	}

	public void stop() {
		locationManager.removeUpdates(this);

		isStarted = false;
	}

	private int getMeterFreq(String v) {
		if ("car".equalsIgnoreCase(v)) {
			return 10;
		} else {
			return 2;
		}
	}

	private int getMillsFreq(String v) {
		if ("car".equalsIgnoreCase(v)) {
			return 1000;
		} else {
			return 1000;
		}
	}

	public Location getLastKnownLocation() {
		Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

		Location locationToSend = null;

		if (gpsLocation != null && networkLocation != null) {
			if (isBetterLocation(networkLocation, gpsLocation))
				locationToSend = networkLocation;
			else
				locationToSend = gpsLocation;
		} else if (gpsLocation != null)
			locationToSend = gpsLocation;
		else if (networkLocation != null)
			locationToSend = networkLocation;

		return locationToSend;
	}

	public Location getOldLocation() {
		if (oldLocation == null)
			return getLastKnownLocation();
		else
			return oldLocation;
	}

	/**
	 * Retrieves last location from LocationManager and sends it to listener
	 */
	public void sendLastLocation() {
		if (listener == null)
			return;

		Location locationToSend = getLastKnownLocation();

		if (locationToSend != null) {
			oldLocation = locationToSend;
			listener.newLocation(locationToSend);
			log.debug("LocationReceiver send last location");
		}
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

			if (listener != null && isStarted())
				listener.newLocation(location);

			log.info(dbg + " accepted. Reason = " + lastReason);
		} else {
			log.info(dbg + " dropped. Reason = " + lastReason);
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

	public void setVehicle(String vehicle) {
		if (this.vehicle.equals(vehicle))
			return;

		this.vehicle = vehicle;
		if (isStarted) {
			locationManager.removeUpdates(this);
			setupUpdates();
		}
	}

	public interface Listener {
		void newLocation(Location location);
	}
}