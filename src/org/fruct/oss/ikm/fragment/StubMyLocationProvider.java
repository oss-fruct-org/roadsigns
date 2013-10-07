package org.fruct.oss.ikm.fragment;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;

import android.location.Location;

public class StubMyLocationProvider implements IMyLocationProvider, Runnable {
	private volatile boolean cont;
	private Location location = new Location("StubMyLocationProvider");
	private IMyLocationConsumer consumer;
	private MapView mapView;
	
	private int lonE6, latE6;
	
	public StubMyLocationProvider(MapView mapView) {
		this.mapView = mapView;
	}
	
	@Override
	public void run() {
		while (cont) {
			IGeoPoint geo = mapView.getMapCenter();
			int nlatE6 = geo.getLatitudeE6();
			int nlonE6 = geo.getLongitudeE6();
			
			if (nlatE6 != latE6 || nlonE6 != lonE6) {
				latE6 = nlatE6;
				lonE6 = nlonE6;
				location.setLatitude(latE6 / 1e6);
				location.setLongitude(lonE6 / 1e6);
				consumer.onLocationChanged(location, this);
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public Location getLastKnownLocation() {
		return location;
	}

	@Override
	public boolean startLocationProvider(IMyLocationConsumer consumer) {
		this.consumer = consumer;
		cont = true;
		new Thread(this).start();
		
		return true;
	}

	@Override
	public void stopLocationProvider() {
		cont = false;
	}
}
