package org.fruct.oss.ikm.fragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.FutureTask;

import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointOfInterest;
import org.fruct.oss.ikm.poi.PointProvider;
import org.fruct.oss.ikm.poi.StubPointProvider;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.DirectedLocationOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.util.PointList;

class Direction {
	GeoPoint direction;
	List<PointOfInterest> points;
}

public class MapFragment extends Fragment {
	public static final GeoPoint PTZ = new GeoPoint(61.783333, 34.350000);
	public static final int DEFAULT_ZOOM = 18;
	public static final String POI_LIST_ID = "org.fruct.oss.ikm.fragment.POI_LIST";
		
	private PointProvider pointProvider = new StubPointProvider();
	
	private List<PointDesc> pointDesc = pointProvider.getPoints(0, 0, 0);
	private List<PointOfInterest> points = new ArrayList<PointOfInterest>();
	
	private FutureTask<Void> roadGraphLoading;
	
	private List<DirectedLocationOverlay> crossDirections;
	
	private MapView mapView;
	
	private GraphHopperAPI hopper;
	
	public MapFragment() {
		for (PointDesc p : pointDesc) {
			points.add(new PointOfInterest(p));
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
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
			if (hopper == null) {
				Context context = getActivity();
				Toast toast = Toast.makeText(context, "Database not ready yet", Toast.LENGTH_SHORT);
				toast.show();
				break;
			}
			
			updateDirections();
						
			return true;
			
		case R.id.action_place:
			Intent intent = new Intent(getActivity(), PointsActivity.class);
			intent.putExtra(POI_LIST_ID, (Serializable) pointDesc);
			startActivity(intent);
			break;
			
		case R.id.action_settings:
			intent = new Intent(getActivity(), SettingsActivity.class);
			startActivity(intent);
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
		int[] out = new int[1];
		long last = System.nanoTime();
		
		GeoPoint current = Utils.copyGeoPoint(mapView.getMapCenter());
				
		Map<GeoPoint, List<PointDesc>> directions = new HashMap<GeoPoint, List<PointDesc>>();
		for (PointOfInterest point : points) {			
			GHRequest request = new GHRequest(
					current.getLatitudeE6() / 1e6,
					current.getLongitudeE6() / 1e6,
					point.getDesc().toPoint().getLatitudeE6() / 1e6,
					point.getDesc().toPoint().getLongitudeE6() / 1e6);
			//request.setAlgorithm("dijkstraOneToMany");
			
			GHResponse response = hopper.route(request);
			PointList path = response.getPoints();
			
			GeoPoint directionNode = new GeoPoint(path.getLatitude(1), path.getLongitude(1));
						
			List<PointDesc> pointsInDirection = directions.get(directionNode);
			if (pointsInDirection == null) {
				pointsInDirection = new ArrayList<PointDesc>();
				directions.put(directionNode, pointsInDirection);
			}
			
			pointsInDirection.add(point.getDesc());
		}
		
		long curr = System.nanoTime();
		Log.d("qwe", "" + (curr - last) / 1e9);
		
		updateDirectionOverlay(current, directions);
	}
	
	private void updateDirectionOverlay(GeoPoint center, Map<GeoPoint, List<PointDesc>> directions) {
		Context context = getActivity();
		if (crossDirections != null) {
			mapView.getOverlays().removeAll(crossDirections);
		}
		
		crossDirections = new ArrayList<DirectedLocationOverlay>();
		for (Entry<GeoPoint, List<PointDesc>> entry : directions.entrySet()) {
			final GeoPoint directionPoint = entry.getKey();
			final List<PointDesc> points = entry.getValue();
			
			double bearing = center.bearingTo(directionPoint);
			GeoPoint markerPosition = center.destinationPoint(50 << (DEFAULT_ZOOM - mapView.getZoomLevel()), (float) bearing); // TODO: zoom distance
			ClickableDirectedLocationOverlay overlay = new ClickableDirectedLocationOverlay(context, mapView, markerPosition, (float) bearing, points);
			
			overlay.setListener(new ClickableDirectedLocationOverlay.Listener() {
				@Override
				public void onClick() {
					for (PointDesc pointDesc : points) {
						Log.d("qwe", pointDesc.getName());
					}
					
					Intent intent = new Intent(getActivity(), PointsActivity.class);
					intent.putExtra(POI_LIST_ID, (Serializable) points);
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
	    for (PointOfInterest point : points) {
	    	items.add(new OverlayItem(point.getDesc().getName(), "", point.getDesc().toPoint()));
	    }
	    ItemizedIconOverlay<OverlayItem> overlay = new ItemizedIconOverlay<OverlayItem>(context, items, null);
	    mapView.getOverlays().add(overlay);
	}
	
	private void loadRoadGraph() {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				final GraphHopper hopper = new GraphHopper().forMobile();
				hopper.setCHShortcuts("fastest");
				boolean res = hopper.load("/sdcard/graphhopper/maps/karelia-gh");
				Log.d("qwe", "GH loaded " + res);
				
				MapFragment.this.getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						MapFragment.this.hopper = hopper;

						createPOIOverlay();
					}
				});
			}
		};
		
		roadGraphLoading = new FutureTask<Void>(runnable, null);
		Utils.executor.execute(roadGraphLoading);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		Context context = getActivity();
		
		loadRoadGraph();

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
	}
	
	@Override
	public void onDestroy() {
		if (!roadGraphLoading.isDone()) {
			roadGraphLoading.cancel(true);
		}
	
		super.onDestroy();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putInt("center-lat", mapView.getMapCenter().getLatitudeE6());
		outState.putInt("center-lon", mapView.getMapCenter().getLongitudeE6());
		outState.putInt("zoom", mapView.getZoomLevel());
	}
}
