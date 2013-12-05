package org.fruct.oss.ikm.fragment;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.drawable.LayerDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.graphhopper.util.PointList;
import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.PointsActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.Smoother;
import org.fruct.oss.ikm.TileProviderManager;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.poi.PointDesc;
import org.fruct.oss.ikm.poi.PointsManager;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.DirectionService;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.bonuspack.overlays.DefaultInfoWindow;
import org.osmdroid.bonuspack.overlays.ExtendedOverlayItem;
import org.osmdroid.bonuspack.overlays.ItemizedOverlayWithBubble;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.ResourceProxyImpl;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.DirectedLocationOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.PathOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;

class MapState implements Parcelable {
	GeoPoint center;
	int zoomLevel;
	List<GeoPoint> currentPath = Collections.emptyList();
	List<Direction> directions = Collections.emptyList();
	boolean isTracking;
	
	boolean providerWarningShown;
	
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
		dest.writeValue(providerWarningShown);
	}
	
	public static final Parcelable.Creator<MapState> CREATOR = new Parcelable.Creator<MapState>() {
		@Override
		public MapState createFromParcel(Parcel source) {
			MapState ret = new MapState();
			ClassLoader loader = ((Object)this).getClass().getClassLoader();
			
			ret.center = source.readParcelable(loader);
			ret.zoomLevel = source.readInt();
			
			ret.currentPath = new ArrayList<GeoPoint>();
			source.readTypedList(ret.currentPath, GeoPoint.CREATOR);
			
			ret.directions = new ArrayList<Direction>();
			source.readTypedList(ret.directions, Direction.CREATOR);
			
			ret.isTracking = (Boolean) source.readValue(loader);
			ret.providerWarningShown = (Boolean) source.readValue(loader);
			
			return ret;
		}
		
		@Override
		public MapState[] newArray(int size) {
			return new MapState[size];
		}
	};
}

public class MapFragment extends Fragment implements MapListener, OnSharedPreferenceChangeListener {
	private static Logger log = LoggerFactory.getLogger(MapFragment.class);

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
	public static final GeoPoint ICELAND = new GeoPoint(64.133038, -21.898337);
	public static final int DEFAULT_ZOOM = 18;
	
	public static final String POINTS = "org.fruct.oss.ikm.fragment.POI_LIST";
	public static final String MAP_CENTER = "org.fruct.oss.ikm.fragment.MAP_CENTER";
				
	private List<DirectedLocationOverlay> crossDirections;
	private PathOverlay pathOverlay;
	private MyPositionOverlay myPositionOverlay;
	
	private Menu menu;
	private MapView mapView;
	private TestOverlay panelOverlay;

	private BroadcastReceiver directionsReceiver;
	private BroadcastReceiver locationReceiver;
	
	private Location myLocation;
	private Smoother speedAverage = new Smoother(10000);
	
	// Camera follow updates from DirectionService
	private boolean isTracking = false;
	
	private DirectionService directionService;
	
	private State state = State.NO_CREATED;
	private EnumMap<State, List<Runnable>> pendingTasks = new EnumMap<MapFragment.State, List<Runnable>>(
			State.class);
	
	// Current map state used to restore map view when rotating screen
	private MapState mapState = new MapState();

	private boolean toastShown;
	private TileProviderManager tileProviderManager;
	
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
			log.info("MapFragment.onServiceConnected");
			directionService = ((DirectionService.DirectionBinder) service).getService();
			
			directionService.startTracking();
			
			state = State.DS_CREATED;
			stateUpdated(state);
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		log.debug("MapFragment.onCreate");

		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		
		// Bind DirectionService
		Intent intent = new Intent(getActivity(), DirectionService.class);
		getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
		
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(directionsReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				log.debug("MapFragment DIRECTIONS_READY");
				//GeoPoint geoPoint = intent.getParcelableExtra(DirectionService.CENTER);
				ArrayList<Direction> directions = intent.getParcelableArrayListExtra(DirectionService.DIRECTIONS_RESULT);
				updateDirectionOverlay(directions);
				
				state = State.DS_RECEIVED;
				stateUpdated(state);
			}
		}, new IntentFilter(DirectionService.DIRECTIONS_READY));
		
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(locationReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				log.debug("MapFragment LOCATION_CHANGED");
				Location location = intent.getParcelableExtra(DirectionService.LOCATION);
				myLocation = location;

				log.debug("New location provider " + location.getProvider());
				log.debug("New location accuracy " + location.getAccuracy());
				log.debug("New location speed " + location.getSpeed());

				myPositionOverlay.setLocation(myLocation);

				if (isTracking) {
					if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(SettingsActivity.AUTOZOOM, false)) {
					speedAverage.insert(location.getSpeed(), location.getTime());
					float ave = speedAverage.average();
					
					int newZoomLevel = getZoomBySpeed(ave);
						log.debug("Speed average = " + ave + " zoom = " + newZoomLevel);

					if (newZoomLevel != mapView.getZoomLevel()) {
						mapView.getController().setZoom(newZoomLevel);
					}
					}
					mapView.getController().animateTo(new GeoPoint(myLocation));
					mapView.setMapOrientation(-location.getBearing());
				}
			}
		}, new IntentFilter(DirectionService.LOCATION_CHANGED));
		
		PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

		log.debug("MapFragment.onCreate EXIT");
	}

	private int getZoomBySpeed(float speed) {
		float speedkmh = speed * 3.6f;
		
		if (speedkmh < 10)
			return 18;
		else if (speedkmh < 30)
			return 17;
		else if (speedkmh < 50)
			return 16;
		else
			return 15;
	}
	
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setHardwareAccelerationOff()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }
    
    private void createMapView(View view) {
    	RelativeLayout layout = (RelativeLayout) view.findViewById(R.id.map_layout);
    	ResourceProxyImpl proxy = new ResourceProxyImpl(this.getActivity().getApplicationContext());
    	
    	tileProviderManager = new TileProviderManager(getActivity());
    	
		mapView = new MapView(getActivity(), 256, proxy, tileProviderManager.getProvider());
    	mapView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		mapView.setBuiltInZoomControls(true);
		mapView.setMultiTouchControls(true);
		mapView.setMapListener(this);
		layout.addView(mapView);		
		
		setHardwareAccelerationOff();
    }
    
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Context context = getActivity();

		// Initialize map		
		createMapView(getView());
		
		panelOverlay = (TestOverlay) getView().findViewById(R.id.directions_panel);
		panelOverlay.initialize(mapView);
		
		// Process MAP_CENTER parameter
		Intent intent = getActivity().getIntent();
		GeoPoint center = intent.getParcelableExtra(MAP_CENTER);
		if (center != null && savedInstanceState == null) {
			log.debug("MapFragment.onActivityCreated setCenter = " + center);
			setCenter(center);
		}

		// Process SHOW_PATH action
		if (MainActivity.SHOW_PATH
				.equals(getActivity().getIntent().getAction())
				&& savedInstanceState == null) {
			log.debug("MapFragment.onActivityCreated SHOW_PATH");
			GeoPoint target = getActivity().getIntent().getParcelableExtra(MainActivity.SHOW_PATH_TARGET);
			showPath(target);
		}
		
		// Restore saved instance state
		if (savedInstanceState == null) {
			mapView.getController().setZoom(DEFAULT_ZOOM);
			mapView.getController().setCenter(PTZ);
		} else {
			log.debug("Restore mapCenter = " + mapState.center);
			
			MapState mapState = savedInstanceState.getParcelable("map-state");
			mapView.getController().setZoom(mapState.zoomLevel);
			mapView.getController().setCenter(mapState.center);
			toastShown = mapState.providerWarningShown;

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
				paint.setAntiAlias(true);
			}

			@Override
			protected void draw(Canvas canvas, MapView mapView, boolean shadow) {
				if (shadow)
					return;
					
				Projection proj = mapView.getProjection();
				Point mapCenter = proj.toMapPixels(mapView.getMapCenter(), null);
				canvas.drawCircle(mapCenter.x, mapCenter.y, 5, paint);
			}
		};
		
		myPositionOverlay = new MyPositionOverlay(getActivity(), mapView);
		mapView.getOverlays().add(myPositionOverlay);
		
		// Apply SHOW_ACCURACY preference
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
		myPositionOverlay.setShowAccuracy(pref.getBoolean(SettingsActivity.SHOW_ACCURACY, false));
		
		mapView.getOverlays().add(overlay);
		createPOIOverlay();

		state = State.CREATED;
		stateUpdated(state);
		
		checkProvidersEnabled();
		
		// Start tracking if preference set
		if (pref.getBoolean(SettingsActivity.STORE_LOCATION, false)) {
			addPendingTask(new Runnable() {
				@Override
				public void run() {
					startTracking();
				}
			}, State.DS_RECEIVED);
		}

		// Listen for mapView shown
		ViewTreeObserver vto = mapView.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				log.debug("New size {} {}", mapView.getWidth(), mapView.getHeight());

				mapView.getController().setZoom(mapView.getZoomLevel());
				updateRadius();

				ViewTreeObserver obs = mapView.getViewTreeObserver();
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					obs.removeOnGlobalLayoutListener(this);
				} else {
					obs.removeGlobalOnLayoutListener(this);
				}
			}
		});
	}
	
	private void checkProvidersEnabled() {
		LocationManager locationManager = (LocationManager) getActivity()
				.getSystemService(Context.LOCATION_SERVICE);

		// Check if all providers disabled and show warning
		if (!toastShown
				&& !locationManager
						.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
				&& !locationManager
						.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

			SharedPreferences pref = PreferenceManager
					.getDefaultSharedPreferences(getActivity());
			if (!pref.getBoolean(SettingsActivity.WARN_PROVIDERS_DISABLED, false)) {
				ProvidersDialog dialog = new ProvidersDialog();
				dialog.show(getFragmentManager(), "providers-dialog");
			} else {
				Toast toast = Toast.makeText(getActivity(),
						R.string.warn_no_providers, Toast.LENGTH_SHORT);
				toast.show();
			}
			toastShown = true;
		}
	}
	
	@Override
	public void onDestroy() {
		log.debug("MapFragment.onDestroy");
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(directionsReceiver);
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(locationReceiver);
		
		getActivity().unbindService(serviceConnection);
		
		PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
		
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
			
		case R.id.action_filter:
			showFilterDialog();
			break;
			
		default:
			//mapView.getOverlayManager().onOptionsItemSelected(item, 4, mapView);
			return super.onOptionsItemSelected(item);
		}

		return true;
	}

	private void showFilterDialog() {
		FilterDialog dialog = new FilterDialog();
		dialog.show(getFragmentManager(), "filter-dialog");
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
			//GeoPoint markerPosition = centerPoint.destinationPoint(50 << (DEFAULT_ZOOM - mapView.getZoomLevel()), (float) bearing);
			GeoPoint markerPosition = directionPoint;
			
			//markerPosition = directionPoint;
			ClickableDirectedLocationOverlay overlay = new ClickableDirectedLocationOverlay(context, mapView, markerPosition, (float) bearing);
			
			overlay.setListener(new ClickableDirectedLocationOverlay.Listener() {
				@Override
				public void onClick() {
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
				panelOverlay.setDirections(directions, myLocation != null ? myLocation.getBearing() : 0);
			}
		},  State.CREATED);
		
		mapView.invalidate();
	}
	
	private void createPOIOverlay() {
		log.debug("MapFragment.createPOIOverlay");
		final Context context = getActivity();
		
		
		List<PointDesc> points = PointsManager.getInstance()
				.getFilteredPoints();
		List<ExtendedOverlayItem> items2 = Utils.map(points,new Utils.Function<ExtendedOverlayItem, PointDesc>() {
					public ExtendedOverlayItem apply(PointDesc point) {
						ExtendedOverlayItem item = new ExtendedOverlayItem(point.getName(), point
								.getDescription(), point.toPoint(), context);
						item.setRelatedObject(point);
						return item;
					}
				});

		final DefaultInfoWindow infoWindow = new POIInfoWindow(R.layout.bonuspack_bubble, mapView);		
		ItemizedOverlayWithBubble<ExtendedOverlayItem> overlay2 = new ItemizedOverlayWithBubble<ExtendedOverlayItem>(
				context, items2, mapView, infoWindow);
		
		mapView.getOverlays().add(overlay2);
		log.debug("MapFragment.createPOIOverlay EXIT");
	}
	
	public void startTracking() {
		isTracking = true;
		panelOverlay.setVisibility(View.VISIBLE);
		panelOverlay.setHidden(false);

		if (menu != null)
			menu.findItem(R.id.action_track).setIcon(R.drawable.ic_action_location_searching);
		
		if (state.idx >= State.DS_CREATED.idx)
			directionService.startTracking();
	}
	
	public void stopTracking() {
		isTracking = false;
		panelOverlay.setVisibility(View.GONE);
		panelOverlay.setHidden(true);
		
		if (menu != null)
			menu.findItem(R.id.action_track).setIcon(R.drawable.ic_action_location_found);
		
		mapView.setMapOrientation(0);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		mapState.center = Utils.copyGeoPoint(mapView.getMapCenter());
		mapState.zoomLevel = mapView.getZoomLevel();
		mapState.isTracking = isTracking;
		mapState.providerWarningShown = toastShown;
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
				log.debug("MapFragment.showPath task start");

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
				if (list == null || list.isEmpty())
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
			log.trace("path " + geoPoint);
			pathOverlay.addPoint(geoPoint);
		}
		
		mapView.getOverlays().add(pathOverlay);
		mapView.invalidate();
		
		mapState.currentPath = path;
	}
	
	private void addPendingTask(Runnable runnable, State state) {
		pendingTasks.get(state).add(runnable);
		
		// Execute all tasks for current state
		stateUpdated(this.state);
	}
	
	private void stateUpdated(State newState) {
		for (Entry<State, List<Runnable>> entry : pendingTasks.entrySet()) {
			State state = entry.getKey();
			
			if (state.idx <= newState.idx) {
				List<Runnable> tasks = new ArrayList<Runnable>(entry.getValue());
				entry.getValue().clear();
				
				for (Runnable runnable : tasks) {
					runnable.run();
				}
			}
		}
	}

	@Override
	public boolean onScroll(ScrollEvent event) {
		return false;
	}

	private void updateRadius() {
		Projection proj = mapView.getProjection();

		GeoPoint p1 = Utils.copyGeoPoint(proj.fromPixels(0, 0));
		IGeoPoint p2 = proj.fromPixels(mapView.getWidth(), 0);
		IGeoPoint p3 = proj.fromPixels(0, mapView.getHeight());

		final int dist = Math.min(p1.distanceTo(p2), p1.distanceTo(p3)) / 2;
		log.debug("Size {} {}", mapView.getWidth(),  mapView.getHeight());
		log.debug("Dist {} {}", p1.distanceTo(p2), p1.distanceTo(p3));
		log.debug("Zoom level {}", mapView.getZoomLevel());
		
		if (dist == 0)
			return;
		
		addPendingTask(new Runnable() {
			@Override
			public void run() {
				directionService.setRadius(dist);
			}
		}, State.DS_CREATED);
	}
	
	@Override
	public boolean onZoom(ZoomEvent event) {
		log.debug("MapFragment.onZoom");
		updateRadius();
		return false;
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(SettingsActivity.SHOW_ACCURACY)) {
			if (myPositionOverlay != null)
				myPositionOverlay.setShowAccuracy(sharedPreferences.getBoolean(SettingsActivity.SHOW_ACCURACY, false));
			mapView.invalidate();
		} else if (key.equals(SettingsActivity.OFFLINE_MAP)) {
			String value = sharedPreferences.getString(key, "");
			tileProviderManager.setFile(value);
			
			mapView.invalidate();
		}
	}
}