package org.fruct.oss.ikm.fragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointProvider;
import org.fruct.oss.ikm.poi.StubPointProvider;
import org.fruct.oss.ikm.service.DirectionService;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.DirectedLocationOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

public class MapFragment extends Fragment {
	public static final GeoPoint PTZ = new GeoPoint(61.783333, 34.350000);
	public static final int DEFAULT_ZOOM = 18;
	
	public static final String POINTS = "org.fruct.oss.ikm.fragment.POI_LIST";
	public static final String MAP_CENTER = "org.fruct.oss.ikm.fragment.MAP_CENTER";
	
	private PointProvider pointProvider = new StubPointProvider();
	
	private List<PointDesc> points = pointProvider.getPoints(0, 0, 0);
		
	private List<DirectedLocationOverlay> crossDirections;
	
	private MapView mapView;
	
	private BroadcastReceiver directionsReceiver;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(directionsReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				GeoPoint geoPoint = intent.getParcelableExtra(DirectionService.CENTER);
				mapView.getController().animateTo(geoPoint);
				
				@SuppressWarnings("unchecked")
				HashMap<GeoPoint, ArrayList<PointDesc>> directions = 
						(HashMap<GeoPoint, ArrayList<PointDesc>>) intent.getSerializableExtra(DirectionService.GET_DIRECTIONS_RESULT);
				updateDirectionOverlay(geoPoint, directions);
			}
		}, new IntentFilter(DirectionService.GET_DIRECTIONS_READY));
	}
	
	@Override
	public void onPause() {
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(directionsReceiver);
		
		super.onPause();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.map_fragment, container, false);
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.main, menu);
	    
	    //mapView.getOverlayManager().onCreateOptionsMenu(menu, 4, mapView);
	    
	    super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_search:			
			//updateDirections();	
			
			Intent intent = new Intent(getActivity(), DirectionService.class);
			intent.setAction(DirectionService.FAKE_LOCATION);
			intent.putExtra(DirectionService.CENTER, (Parcelable) Utils.copyGeoPoint(mapView.getMapCenter()));
			getActivity().startService(intent);
			break;
			
		case R.id.action_place:
			intent = new Intent(getActivity(), PointsActivity.class);
			intent.putExtra(POINTS, (Serializable) points);
			startActivity(intent);
			break;
			
		case R.id.action_settings:
			intent = new Intent(getActivity(), SettingsActivity.class);
			startActivity(intent);
			break;
			
		case R.id.action_track:
			Log.d("qwe", "action_track");
			intent = new Intent(getActivity(), DirectionService.class);
			intent.setAction(DirectionService.START_FOLLOWING);
			intent.putParcelableArrayListExtra(DirectionService.POINTS, new ArrayList<PointDesc>(points));
			getActivity().startService(intent);
			break;
			
		default:
			//mapView.getOverlayManager().onOptionsItemSelected(item, 4, mapView);
			return super.onOptionsItemSelected(item);
		}

		return true;
	}
		
	/**
	 * Update point-of-interest's directions and display them on screen
	 */
	private void updateDirections() {
		Intent intent = new Intent(getActivity(), DirectionService.class);
		intent.setAction(DirectionService.GET_DIRECTIONS);
		intent.putExtra(DirectionService.CENTER,
				(Parcelable) Utils.copyGeoPoint(mapView.getMapCenter()));
		intent.putParcelableArrayListExtra(DirectionService.POINTS,
				new ArrayList<PointDesc>(points));

		getActivity().startService(intent);
	}
	
	private void updateDirectionOverlay(GeoPoint center, HashMap<GeoPoint, ArrayList<PointDesc>> directions) {
		Context context = getActivity();
		if (crossDirections != null) {
			mapView.getOverlays().removeAll(crossDirections);
		}
		
		crossDirections = new ArrayList<DirectedLocationOverlay>();
		for (Entry<GeoPoint, ArrayList<PointDesc>> entry : directions.entrySet()) {
			final GeoPoint directionPoint = entry.getKey();
			final List<PointDesc> points = entry.getValue();
			
			double bearing = center.bearingTo(directionPoint);
			GeoPoint markerPosition = center.destinationPoint(50 << (DEFAULT_ZOOM - mapView.getZoomLevel()), (float) bearing);
			ClickableDirectedLocationOverlay overlay = new ClickableDirectedLocationOverlay(context, mapView, markerPosition, (float) bearing, points);
			
			overlay.setListener(new ClickableDirectedLocationOverlay.Listener() {
				@Override
				public void onClick() {
					for (PointDesc pointDesc : points) {
						Log.d("qwe", pointDesc.getName());
					}
					
					Intent intent = new Intent(getActivity(), PointsActivity.class);
					intent.putExtra(POINTS, (Serializable) points);
					startActivity(intent);
				}
			});
			
			mapView.getOverlays().add(overlay);
			crossDirections.add(overlay);
		}
		
		mapView.invalidate();
	}
	
	private void createPOIOverlay() {
		Context context = getActivity();
    	ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();
	    for (PointDesc point : points) {
	    	items.add(new OverlayItem(point.getName(), "", point.toPoint()));
	    }
	    
	    ItemizedIconOverlay<OverlayItem> overlay = new ItemizedIconOverlay<OverlayItem>(context, items, null);
	    mapView.getOverlays().add(overlay);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Context context = getActivity();
		
		//loadRoadGraph();

		mapView = (MapView) getView().findViewById(R.id.map_view);
	    mapView.setBuiltInZoomControls(true);
	    
	    if (savedInstanceState == null) {
	    	mapView.getController().setZoom(DEFAULT_ZOOM);
	    	mapView.getController().setCenter(PTZ);
	    } else {
	    	mapView.getController().setZoom(savedInstanceState.getInt("zoom"));
	    	GeoPoint center = new GeoPoint(savedInstanceState.getInt("center-lat"),
	    									savedInstanceState.getInt("center-lon"));
	    	mapView.getController().setCenter(center);
	    }
	    
	    //GpsMyLocationProvider provider = new GpsMyLocationProvider(context);
	    //IMyLocationProvider provider2 = new StubMyLocationProvider(mapView);
	    
	    /*MyLocationNewOverlay myLocationOverlay = new MyLocationNewOverlay(context, provider2, mapView);
	    mapView.getOverlays().add(myLocationOverlay);
	    myLocationOverlay.enableMyLocation(provider2);*/
	    
	    Overlay overlay = new Overlay(context) {
	    	Paint paint = new Paint();
	    	{
	    		paint.setColor(Color.GRAY);
	    		paint.setStrokeWidth(2);
	    		paint.setStyle(Style.FILL);
	    	}
	    	
	    	@Override
	    	protected void draw(Canvas canvas, MapView mapView, boolean shadow) {
	    		Projection proj = mapView.getProjection();
	    		Point p = proj.toMapPixels(mapView.getMapCenter(), null);
	    		canvas.drawRect(p.x - 5, p.y - 5, p.x + 5, p.y + 5, paint);
	    	}
		};
		
		mapView.getOverlays().add(overlay);
		createPOIOverlay();
	}
	
	@Override
	public void onDestroy() {
		getActivity().stopService(new Intent(getActivity(), DirectionService.class));
		
		super.onDestroy();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putInt("center-lat", mapView.getMapCenter().getLatitudeE6());
		outState.putInt("center-lon", mapView.getMapCenter().getLongitudeE6());
		outState.putInt("zoom", mapView.getZoomLevel());
	}
	
	public void setCenter(IGeoPoint geoPoint) {
		mapView.getController().setZoom(DEFAULT_ZOOM);
		mapView.getController().animateTo(geoPoint);
	}
}
