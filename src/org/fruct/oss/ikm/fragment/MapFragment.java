package org.fruct.oss.ikm.fragment;

import static org.fruct.oss.ikm.Utils.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;

import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.Direction.RelativeDirection;
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

import android.annotation.TargetApi;
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
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
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
import android.widget.ListView;

import com.graphhopper.util.PointList;

class MapState implements Parcelable {
	GeoPoint center;
	int zoomLevel;
	List<GeoPoint> currentPath = Collections.emptyList();
	List<Direction> directions = Collections.emptyList();
	boolean isTracking;
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeParcelable(center, flags);
		dest.writeInt(zoomLevel);
		dest.writeTypedList(currentPath);
		dest.writeTypedList(directions);
		dest.writeValue(isTracking);
	}
	
	public static final Parcelable.Creator<MapState> CREATOR = new Parcelable.Creator<MapState>() {
		@Override
		public MapState createFromParcel(Parcel source) {
			MapState ret = new MapState();
			ClassLoader loader = getClass().getClassLoader();
			
			ret.center = source.readParcelable(loader);
			ret.zoomLevel = source.readInt();
			
			ret.currentPath = new ArrayList<GeoPoint>();
			source.readTypedList(ret.currentPath, GeoPoint.CREATOR);
			
			ret.directions = new ArrayList<Direction>();
			source.readTypedList(ret.directions, Direction.CREATOR);
			
			ret.isTracking = (Boolean) source.readValue(loader);
			
			return ret;
		}
		
		@Override
		public MapState[] newArray(int size) {
			return new MapState[size];
		}
	};
}

public class MapFragment extends Fragment {
	static enum State {
		NO_CREATED(0), CREATED(1), DS_CREATED(2), DS_RECEIVED(3), SIZE(4);
		
		State(int idx) {
			this.idx = idx;
		}
		
		public int getIdx() {
			return idx;
		}
		
		private int idx;
	}
	
	
	public static final GeoPoint PTZ = new GeoPoint(61.783333, 34.350000);
	public static final int DEFAULT_ZOOM = 18;
	
	public static final String POINTS = "org.fruct.oss.ikm.fragment.POI_LIST";
	public static final String MAP_CENTER = "org.fruct.oss.ikm.fragment.MAP_CENTER";
				
	private List<DirectedLocationOverlay> crossDirections;
	private PathOverlay pathOverlay;
	private MyPositionOverlay myPositionOverlay;
	
	private Menu menu;
	private MapView mapView;
	
	private DirectionsListAdapter leftPanelAdapter;
	private DirectionsListAdapter rightPanelAdapter;
	
	private BroadcastReceiver directionsReceiver;
	private BroadcastReceiver locationReceiver;
	
	private Location myLocation;
	
	// Camera follow updates from DirectionService
	private boolean isTracking = false;
	
	private DirectionService directionService;
	
	private State state = State.NO_CREATED;
	private EnumMap<State, List<Runnable>> pendingTasks = new EnumMap<MapFragment.State, List<Runnable>>(
			State.class);
	
	// Current map state used to restore map view when rotating screen
	private MapState mapState = new MapState();
	
	public MapFragment() {
		pendingTasks.put(State.NO_CREATED, new ArrayList<Runnable>());
		pendingTasks.put(State.CREATED, new ArrayList<Runnable>());
		pendingTasks.put(State.DS_CREATED, new ArrayList<Runnable>());
		pendingTasks.put(State.DS_RECEIVED, new ArrayList<Runnable>());
	}
	
	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			directionService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			log("MapFragment.onServiceConnected");
			directionService = ((DirectionService.DirectionBinder) service).getService();
			
			directionService.startTracking();
			
			state = State.DS_CREATED;
			stateUpdated(state);
		}
	};
	private TestOverlay panelOverlay;

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
				updateDirectionOverlay(directions);
				
				state = State.DS_RECEIVED;
				stateUpdated(state);
			}
		}, new IntentFilter(DirectionService.DIRECTIONS_READY));
		
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(locationReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Location location = intent.getParcelableExtra(DirectionService.LOCATION);
				log("location bearing = " + location.getBearing());
				myLocation = location;
				
				myPositionOverlay.setLocation(myLocation);

				if (isTracking) {
					mapView.getController().animateTo(new GeoPoint(myLocation));
					mapView.setMapOrientation(-location.getBearing());
				}
			}
		}, new IntentFilter(DirectionService.LOCATION_CHANGED));
		
		log("MapFragment.onCreate EXIT");
	}
	
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setHardwareAccelerationOff()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Context context = getActivity();

		// Initialize map
		mapView = (MapView) getView().findViewById(R.id.map_view);
		mapView.setBuiltInZoomControls(true);
		mapView.setMultiTouchControls(true);
		setHardwareAccelerationOff();

		// Initialize views
		leftPanelAdapter = new DirectionsListAdapter(getActivity(), RelativeDirection.LEFT);
		rightPanelAdapter = new DirectionsListAdapter(getActivity(), RelativeDirection.RIGHT);
		
		ListView leftView = (ListView) getView().findViewById(R.id.left_map_panel);
		leftView.setAdapter(leftPanelAdapter);
		
		ListView rightView = (ListView) getView().findViewById(R.id.right_map_panel);
		rightView.setAdapter(rightPanelAdapter);
		
		
		// Process MAP_CENTER parameter
		Intent intent = getActivity().getIntent();
		GeoPoint center = intent.getParcelableExtra(MAP_CENTER);
		if (center != null && savedInstanceState == null) {
			log("MapFragment.onActivityCreated setCenter = " + center);
			setCenter(center);
		}

		// Process SHOW_PATH action
		if (MainActivity.SHOW_PATH
				.equals(getActivity().getIntent().getAction())
				&& savedInstanceState == null) {
			log("MapFragment.onActivityCreated SHOW_PATH");
			GeoPoint target = (GeoPoint) getActivity().getIntent()
					.getParcelableExtra(MainActivity.SHOW_PATH_TARGET);
			showPath(target);
		}
		
		// Restore saved instance state
		if (savedInstanceState == null) {
			mapView.getController().setZoom(DEFAULT_ZOOM);
			mapView.getController().setCenter(PTZ);
		} else {
			log("Restore mapCenter = " + mapState.center);
			
			MapState mapState = savedInstanceState.getParcelable("map-state");
			mapView.getController().setZoom(mapState.zoomLevel);
			mapView.getController().setCenter(mapState.center);

			if (!mapState.directions.isEmpty())
				updateDirectionOverlay(mapState.directions);
			
			if (!mapState.currentPath.isEmpty())
				showPath(mapState.currentPath);
			
			if (mapState.isTracking)
				startTracking();
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
				if (shadow == true)
					return;
					
				Projection proj = mapView.getProjection();
				Point mapCenter = proj.toMapPixels(mapView.getMapCenter(), null);
				canvas.drawRect(mapCenter.x - 5, mapCenter.y - 5,
						mapCenter.x + 5, mapCenter.y + 5, paint);
				
				canvas.drawRect(0, 0, 64, 64, paint);
			}
		};
		
		myPositionOverlay = new MyPositionOverlay(getActivity(), mapView);
		mapView.getOverlays().add(myPositionOverlay);
		
		
		//panelOverlay = new TestOverlay(getActivity(), mapView);
		//mapView.getOverlays().add(panelOverlay);
		

		mapView.getOverlays().add(overlay);
		createPOIOverlay();

		state = State.CREATED;
		stateUpdated(state);
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
		// mapView.getOverlayManager().onCreateOptionsMenu(menu, 4, mapView);

		super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		this.menu = menu;
		
		menu.findItem(R.id.action_track).setIcon(
				isTracking ? R.drawable.ic_action_location_searching
						: R.drawable.ic_action_location_found);
		super.onPrepareOptionsMenu(menu);
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

	private void updateDirectionOverlay(final List<Direction> directions) {
		Context context = getActivity();
		if (crossDirections != null) {
			mapView.getOverlays().removeAll(crossDirections);
		}
				
		crossDirections = new ArrayList<DirectedLocationOverlay>();
		for (Direction direction : directions) {
			final GeoPoint directionPoint = direction.getDirection();
			final GeoPoint centerPoint = direction.getCenter();
			
			final List<PointDesc> points = direction.getPoints();
			
			double bearing = centerPoint.bearingTo(directionPoint);
			GeoPoint markerPosition = centerPoint.destinationPoint(50 << (DEFAULT_ZOOM - mapView.getZoomLevel()), (float) bearing);
			
			
			//markerPosition = directionPoint;
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
		
		mapState.directions = directions;
		
		// Update panelOverlay after fragment loaded
		addPendingTask(new Runnable() {
			@Override
			public void run() {
				//panelOverlay.setDirections(directions, myLocation != null ? myLocation.getBearing() : 0);
				leftPanelAdapter.setPoints(directions, myLocation.getBearing());
				rightPanelAdapter.setPoints(directions, myLocation.getBearing());
			}
		},  State.CREATED);
		
		mapView.invalidate();

	}
	
	private void createPOIOverlay() {
		log("MapFragment.createPOIOverlay");
		Context context = getActivity();
		ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();

		List<PointDesc> points = PointsManager.getInstance().getFilteredPoints();

		for (PointDesc point : points) {
			items.add(new OverlayItem(point.getName(), "", point.toPoint()));
		}

		ItemizedIconOverlay<OverlayItem> overlay = new ItemizedIconOverlay<OverlayItem>(
				context, items, null);
		mapView.getOverlays().add(overlay);
		log("MapFragment.createPOIOverlay EXIT");
	}
	
	public void startTracking() {
		isTracking = true;
		
		if (menu != null)
			menu.findItem(R.id.action_track).setIcon(R.drawable.ic_action_location_searching);
		
		if (state.idx >= State.DS_CREATED.idx)
			directionService.startTracking();
	}
	
	public void stopTracking() {
		isTracking = false;
		
		if (menu != null)
			menu.findItem(R.id.action_track).setIcon(R.drawable.ic_action_location_found);
		
		mapView.setMapOrientation(0);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		mapState.center = Utils.copyGeoPoint(mapView.getMapCenter());
		mapState.zoomLevel = mapView.getZoomLevel();
		mapState.isTracking = isTracking;
		//mapState.mapOrientation = mapView.getMapOrientation();
		outState.putParcelable("map-state", mapState);
	}
	
	public void setCenter(IGeoPoint geoPoint) {
		mapView.getController().setZoom(DEFAULT_ZOOM);
		mapView.getController().animateTo(geoPoint);
		stopTracking();
	}
	
	public void showPath(final GeoPoint target) {
		Runnable task = new Runnable() {
			@Override
			public void run() {
				log("MapFragment.showPath task start");

				// Get last known location from DirectionService
				Location lastLocation = directionService.getLastLocation();
				if (lastLocation == null) {
					Log.w("roadsigns", "MapFragment.showPath: myLocation == null");
					return;
				}
				myLocation = lastLocation;
				
				GeoPoint current = new GeoPoint(myLocation);
				
				// Find path from current location to target location
				PointList list = directionService.findPath(current, target);
				if (list.getSize() == 0)
					return;
				
				ArrayList<GeoPoint> pathArray = new ArrayList<GeoPoint>();
				for (int i = 0; i < list.getSize(); i++)
					pathArray.add(new GeoPoint(list.getLatitude(i), list.getLongitude(i)));
				
				showPath(pathArray);
				mapView.getController().setCenter(new GeoPoint(myLocation));
			}
		};
		
		addPendingTask(task, State.DS_RECEIVED);
	}
	
	private void showPath(List<GeoPoint> path) {
		// Remove existing path overlay
		if (pathOverlay != null) {
			mapView.getOverlays().remove(pathOverlay);
		}
		
		pathOverlay = new PathOverlay(Color.BLUE, getActivity());
		pathOverlay.setAlpha(127);
		
		for (GeoPoint geoPoint : path) {
			log("path " + geoPoint);
			pathOverlay.addPoint(geoPoint);
		}
		
		mapView.getOverlays().add(pathOverlay);
		mapView.invalidate();
		
		mapState.currentPath = path;
	}
	
	private void addPendingTask(Runnable runnable, State state) {
		pendingTasks.get(state).add(runnable);
		
		// Execute all task for current state
		stateUpdated(this.state);
	}
	
	private void stateUpdated(State newState) {
		for (Entry<State, List<Runnable>> entry : pendingTasks.entrySet()) {
			State state = entry.getKey();
			List<Runnable> tasks = entry.getValue();
			
			if (state.idx <= newState.idx) {
				for (Runnable runnable : tasks) {
					runnable.run();
				}
				tasks.clear();
			}
		}
	}
}
