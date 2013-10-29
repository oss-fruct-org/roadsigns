package org.fruct.oss.ikm.fragment;

import static org.fruct.oss.ikm.Utils.log;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.DirectionService;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.DirectedLocationOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.PathOverlay;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.graphhopper.util.PointList;
public class MapFragment extends Fragment {
	public static final GeoPoint PTZ = new GeoPoint(61.783333, 34.350000);
	public static final int DEFAULT_ZOOM = 18;
	
	public static final String POINTS = "org.fruct.oss.ikm.fragment.POI_LIST";
	public static final String MAP_CENTER = "org.fruct.oss.ikm.fragment.MAP_CENTER";
				
	private List<DirectedLocationOverlay> crossDirections;
	private PathOverlay pathOverlay;
	
	private Menu menu;
	private MapView mapView;
	
	private BroadcastReceiver directionsReceiver;
	private BroadcastReceiver locationReceiver;
	
	private GeoPoint myLocation;
	private boolean isTracking = false;
	
	private boolean pathShow = false; // Show path only once on first fragment creating
	
	private DirectionService directionService;
	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			directionService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			directionService = ((DirectionService.DirectionBinder) service).getService();
			
		    // Process SHOW_PATH action
			if (!pathShow && MainActivity.SHOW_PATH.equals(getActivity().getIntent().getAction())) {
				log("MapFragment.onServiceConnected SHOW_PATH");
				GeoPoint target = (GeoPoint) getActivity().getIntent().getParcelableExtra(MainActivity.SHOW_PATH_TARGET);
				showPath(target);
				pathShow = true;
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		log("MapFragment.onCreate");

		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		
		// Bind DirectionService
		Intent intent = new Intent(getActivity(), DirectionService.class);
		getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
		
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(directionsReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				GeoPoint geoPoint = intent.getParcelableExtra(DirectionService.CENTER);
				ArrayList<Direction> directions = intent.getParcelableArrayListExtra(DirectionService.DIRECTIONS_RESULT);
				updateDirectionOverlay(geoPoint, directions);
			}
		}, new IntentFilter(DirectionService.DIRECTIONS_READY));
		
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(locationReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Location location = intent.getParcelableExtra(DirectionService.LOCATION);
				log("location bearing = " + location.getBearing());

				if (isTracking) {
					myLocation = new GeoPoint(location);
					mapView.getController().animateTo(new GeoPoint(myLocation));
				}
			}
		}, new IntentFilter(DirectionService.LOCATION_CHANGED));
	}
	
	@Override
	public void onDestroy() {
		log("MapFragment.onDestroy");
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(directionsReceiver);
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(locationReceiver);
		
		getActivity().unbindService(serviceConnection);
		
		super.onDestroy();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.map_fragment, container, false);
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.main, menu);
	    this.menu = menu;
	    //mapView.getOverlayManager().onCreateOptionsMenu(menu, 4, mapView);
	    
	    super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_search:			
			directionService.fakeLocation(Utils.copyGeoPoint(mapView.getMapCenter()));
			break;
			
		case R.id.action_place:
			Intent intent = new Intent(getActivity(), PointsActivity.class);
			startActivity(intent);
			break;
			
		case R.id.action_settings:
			intent = new Intent(getActivity(), SettingsActivity.class);
			startActivity(intent);
			break;
			
		case R.id.action_track:
			if (!isTracking)
				startTracking();
			else
				stopTracking(); 
		
			break;
			
		default:
			//mapView.getOverlayManager().onOptionsItemSelected(item, 4, mapView);
			return super.onOptionsItemSelected(item);
		}

		return true;
	}

	private void updateDirectionOverlay(GeoPoint center, ArrayList<Direction> directions) {
		Context context = getActivity();
		if (crossDirections != null) {
			mapView.getOverlays().removeAll(crossDirections);
		}
		
		crossDirections = new ArrayList<DirectedLocationOverlay>();
		for (Direction direction : directions) {
			final GeoPoint directionPoint = direction.getDirection();
			final List<PointDesc> points = direction.getPoints();
			
			double bearing = center.bearingTo(directionPoint);
			GeoPoint markerPosition = center.destinationPoint(50 << (DEFAULT_ZOOM - mapView.getZoomLevel()), (float) bearing);
			ClickableDirectedLocationOverlay overlay = new ClickableDirectedLocationOverlay(context, mapView, markerPosition, (float) bearing);
			
			overlay.setListener(new ClickableDirectedLocationOverlay.Listener() {
				@Override
				public void onClick() {
					for (PointDesc pointDesc : points) {
						log(pointDesc.getName());
					}
					
					Intent intent = new Intent(getActivity(), PointsActivity.class);
					intent.putParcelableArrayListExtra(POINTS, new ArrayList<PointDesc>(points));
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
    	
    	List<PointDesc> points = PointsManager.getInstance().getFilteredPoints();
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
	    
	    // Restore saved instance state
	    if (savedInstanceState == null) {
	    	mapView.getController().setZoom(DEFAULT_ZOOM);
	    	mapView.getController().setCenter(PTZ);
	    } else {
	    	mapView.getController().setZoom(savedInstanceState.getInt("zoom"));
	    	GeoPoint center = new GeoPoint(savedInstanceState.getInt("center-lat"),
	    									savedInstanceState.getInt("center-lon"));
	    	mapView.getController().setCenter(center);
	    }
	    
	    // Process MAP_CENTER parameter
	    Intent intent = getActivity().getIntent();
	    GeoPoint center = intent.getParcelableExtra(MAP_CENTER);
	    if (center != null) {
	    	mapView.getController().setCenter(center);
	    }
	   
	    	    
	    // Setup device position overlay
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
	    		
	    		Point myLocation;
	    		Point mapCenter = proj.toMapPixels(mapView.getMapCenter(), null);
	    		
	    		if (MapFragment.this.myLocation != null) {
	    			myLocation = proj.toMapPixels(MapFragment.this.myLocation, null);
		    		canvas.drawRect(myLocation.x - 5, myLocation.y - 5, myLocation.x + 5, myLocation.y + 5, paint);

	    		}
	    		
	    		canvas.drawRect(mapCenter.x - 5, mapCenter.y - 5, mapCenter.x + 5, mapCenter.y + 5, paint);
	    	}
		};
		
		mapView.getOverlays().add(overlay);
		createPOIOverlay();
	}
	
	public void startTracking() {
		isTracking = true;
		
		menu.findItem(R.id.action_track).setIcon(R.drawable.ic_action_location_searching);
				
		directionService.startTracking();
	}
	
	public void stopTracking() {
		isTracking = false;
		
		menu.findItem(R.id.action_track).setIcon(R.drawable.ic_action_location_found);
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
	
	public void showPath(GeoPoint target) {
		log("MapFragment.showPath directionService " + directionService);
		if (myLocation == null) {
			Location lastLocation = directionService.getLastLocation();
			if (lastLocation == null) {
				Log.w("roadsigns", "MapFragment.showPath: myLocation == null");
				return;
			}
			myLocation = new GeoPoint(lastLocation);
		
		}
		
		GeoPoint current = new GeoPoint(myLocation);
		PointList list = directionService.findPath(current, target);
		if (list.getSize() == 0)
			return;
		
		
		if (pathOverlay != null) {
			mapView.getOverlays().remove(pathOverlay);
		}
		
		pathOverlay = new PathOverlay(Color.BLUE, getActivity());
		pathOverlay.setAlpha(127);
		
		for (int i = 0; i < list.getSize(); i++) {
			GeoPoint p = new GeoPoint(list.getLatitude(i), list.getLongitude(i));
			log("path " + p);
			pathOverlay.addPoint(p);
		}
		
		mapView.getOverlays().add(pathOverlay);
		mapView.getController().setCenter(myLocation);
		mapView.invalidate();
	}
}
