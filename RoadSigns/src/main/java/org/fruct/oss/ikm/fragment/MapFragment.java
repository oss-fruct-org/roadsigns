package org.fruct.oss.ikm.fragment;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.MainActivity;
import org.fruct.oss.ikm.OnlineContentActivity;
import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.SettingsActivity;
import org.fruct.oss.ikm.Smoother;
import org.fruct.oss.ikm.TileProviderManager;
import org.fruct.oss.ikm.drawer.DrawerActivity;
import org.fruct.oss.ikm.drawer.MultiPanel;
import org.fruct.oss.ikm.events.DirectionsEvent;
import org.fruct.oss.ikm.events.EventReceiver;
import org.fruct.oss.ikm.events.LocationEvent;
import org.fruct.oss.ikm.events.PathEvent;
import org.fruct.oss.ikm.events.ScreenRadiusEvent;
import org.fruct.oss.ikm.events.TargetPointEvent;
import org.fruct.oss.ikm.events.TrackingModeEvent;
import org.fruct.oss.ikm.points.Point;
import org.fruct.oss.ikm.service.Direction;
import org.fruct.oss.ikm.service.DirectionService;
import org.fruct.oss.ikm.utils.Utils;
import org.fruct.oss.mapcontent.content.ContentItem;
import org.fruct.oss.mapcontent.content.ContentListenerAdapter;
import org.fruct.oss.mapcontent.content.ContentManagerImpl;
import org.fruct.oss.mapcontent.content.ContentService;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnection;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnectionListener;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.ResourceProxyImpl;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.PathOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.greenrobot.event.EventBus;

class MapState implements Parcelable {
	GeoPoint center;
	int zoomLevel;
	List<GeoPoint> currentPath = Collections.emptyList();
	List<Direction> directions = Collections.emptyList();
	boolean isTracking;
	
	boolean warningShown;
	
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
		dest.writeValue(warningShown);
	}
	
	public static final Parcelable.Creator<MapState> CREATOR = new Parcelable.Creator<MapState>() {
		@Override
		public MapState createFromParcel(Parcel source) {
			MapState ret = new MapState();
			ClassLoader loader = ((Object)this).getClass().getClassLoader();
			
			ret.center = source.readParcelable(loader);
			ret.zoomLevel = source.readInt();
			
			ret.currentPath = new ArrayList<>();
			source.readTypedList(ret.currentPath, GeoPoint.CREATOR);
			
			ret.directions = new ArrayList<>();
			source.readTypedList(ret.directions, Direction.CREATOR);
			
			ret.isTracking = (Boolean) source.readValue(loader);
			ret.warningShown = (Boolean) source.readValue(loader);
			
			return ret;
		}
		
		@Override
		public MapState[] newArray(int size) {
			return new MapState[size];
		}
	};
}

public class MapFragment extends Fragment implements MapListener,
		OnSharedPreferenceChangeListener,
		MyPositionOverlay.OnScrollListener, ContentServiceConnectionListener {
	private static Logger log = LoggerFactory.getLogger(MapFragment.class);

	private boolean networkToastShown;
	private boolean navigationDataToastShown;
	private boolean providersToastShown;

	private Overlay poiOverlay;

	public static final GeoPoint PTZ = new GeoPoint(61.783333, 34.350000);
	public static final int DEFAULT_ZOOM = 18;

	public static final String ACTION_CENTER_MAP = "org.fruct.oss.ikm.ACTION_CENTER_MAP"; // arg MapFragment.ARG_MAP_CENTER
	public static final String ACTION_SHOW_PATH = "org.fruct.oss.ikm.ACTION_SHOW_PATH";

	public static final String ARG_MAP_CENTER = "org.fruct.oss.ikm.fragment.ARG_MAP_CENTER";
	public static final String ARG_SHOW_PATH_TARGET = "org.fruct.oss.ikm.ARG_SHOW_PATH_TARGET";

	private List<ClickableDirectedLocationOverlay> crossDirections;
	private PathOverlay pathOverlay;
	private boolean pathJumped;
	private MyPositionOverlay myPositionOverlay;
	
	private Menu menu;
	private MapView mapView;
	private TestOverlay panelOverlay;

	// Zoom and map center that should be set after map ready ("on global layout")
	private GeoPoint initialMapPosition;
	private int initialZoom;

	private Location myLocation;
	private Smoother speedAverage = new Smoother(10000);
	
	// Camera follow updates from DirectionService
	private boolean isTracking = false;
	
	private DirectionService directionService;
	
	// Current map state used to restore map view when rotating screen
	private MapState mapState = new MapState();

	private ContentService remoteContent;

	private ContentServiceConnection remoteContentServiceConnection = new ContentServiceConnection(this);

	private TileProviderManager tileProviderManager;
	private ContentItem recommendedContentItem;

	private SharedPreferences pref;

	public static Fragment newInstanceGeoPoint(GeoPoint geoPoint) {
		MapFragment mapFragment = new MapFragment();

		Bundle args = new Bundle();
		args.putParcelable(ARG_MAP_CENTER, geoPoint);
		mapFragment.setArguments(args);
		return mapFragment;
	}

	public static Fragment newInstanceForPath(GeoPoint geoPoint) {
		MapFragment mapFragment = new MapFragment();

		Bundle args = new Bundle();
		args.putParcelable(ARG_SHOW_PATH_TARGET, geoPoint);
		mapFragment.setArguments(args);
		return mapFragment;
	}

	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			log.info("MapFragment.onServiceDisconnected");

			directionService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			log.info("MapFragment.onServiceConnected");
			directionService = ((DirectionService.DirectionBinder) service).getService();
			assert directionService != null;
		}
	};

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof DrawerActivity) {
			((DrawerActivity) activity).onSectionAttached(getString(R.string.app_name),
					ActionBar.NAVIGATION_MODE_STANDARD);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		log.debug("MapFragment.onCreate");

		super.onCreate(savedInstanceState);

		pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

		setHasOptionsMenu(true);
		
		// Bind DirectionService
		Intent intent = new Intent(getActivity(), DirectionService.class);
		getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

		PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

		remoteContentServiceConnection.bindService(getActivity());
	}

	@Override
	public void onStart() {
		super.onStart();

		EventBus.getDefault().registerSticky(this);

		checkProvidersEnabled();
		checkNetworkAvailable();
	}

	@Override
	public void onStop() {
		EventBus.getDefault().unregister(this);
		pref.edit()
				.putInt("last-pos-lat", mapView.getMapCenter().getLatitudeE6())
				.putInt("last-pos-lon", mapView.getMapCenter().getLongitudeE6()).apply();

		super.onStop();
	}

	@EventReceiver
	public void onEventMainThread(PathEvent pathEvent) {
		ArrayList<GeoPoint> path = pathEvent.getPathArray();
		showPath(path);

		if (!pathJumped)
			mapView.getController().setCenter(new GeoPoint(myLocation));

		pathJumped = true;
	}

	@EventReceiver
	public void onEventMainThread(LocationEvent locationEvent) {
		Location location = locationEvent.getLocation();
		myLocation = location;

		myPositionOverlay.setLocation(location);

		// Auto-zoom and animate to new location if tracking mode enabled
		if (isTracking) {
			// If auto zoom enabled
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

		mapView.invalidate();
	}

	@EventReceiver
	public void onEventMainThread(DirectionsEvent directionsEvent) {
		if (isTracking) {
			updateDirectionOverlay(directionsEvent.getDirections());
		}
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
    private void setHardwareAccelerationOff() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }
    
    private void createMapView(View view) {
    	RelativeLayout layout = (RelativeLayout) view.findViewById(R.id.map_layout);
    	ResourceProxyImpl proxy = new ResourceProxyImpl(this.getActivity().getApplicationContext());
    	
    	tileProviderManager = new TileProviderManager(getActivity());
    	
		mapView = new MapView(getActivity(), 256, proxy, tileProviderManager.getProvider());
    	mapView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		log.info("Created MapView using {} tiles", tileProviderManager.isOnline() ? "online" : "offline");

		mapView.setMultiTouchControls(true);
		mapView.setMapListener(this);

		layout.addView(mapView);		
		
		setHardwareAccelerationOff();
    }

	private void setupMapCenterOverlay() {
		// Setup map center overlay
		Overlay overlay = new Overlay(getActivity()) {
			final android.graphics.Point point = new android.graphics.Point();
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
				android.graphics.Point mapCenter = proj.toPixels(mapView.getMapCenter(), point);
				canvas.drawCircle(mapCenter.x, mapCenter.y, 5, paint);
			}
		};

		mapView.getOverlays().add(overlay);
	}

	private void setupMyPositionOverlay() {
		myPositionOverlay = new MyPositionOverlay(getActivity(), mapView);

		// Apply SHOW_ACCURACY preference
		myPositionOverlay.setShowAccuracy(pref.getBoolean(SettingsActivity.SHOW_ACCURACY, false));

		mapView.getOverlays().add(myPositionOverlay);
	}

	private void processFragmentArguments() {
		// Process ACTION_SHOW_PATH action
		GeoPoint targetPoint = getArguments() != null
				? getArguments().<GeoPoint>getParcelable(ARG_SHOW_PATH_TARGET)
				: null;

		EventBus.getDefault().postSticky(new TargetPointEvent(targetPoint));
	}

	private void loadInitialPositionFromArguments() {
		// Process ARG_MAP_CENTER parameter
		GeoPoint centerPoint = getArguments() != null
				? getArguments().<GeoPoint>getParcelable(ARG_MAP_CENTER)
				: null;

		if (centerPoint != null) {
			initialZoom = DEFAULT_ZOOM;
			initialMapPosition = centerPoint;
		}
	}

	private void loadInitialPositionFromSavedState(Bundle savedInstanceState) {
		MapState mapState = savedInstanceState.getParcelable("pref_map_state");

		initialZoom = mapState.zoomLevel;
		initialMapPosition = mapState.center;
	}

	private void processSavedState(Bundle savedInstanceState) {
		MapState mapState = savedInstanceState.getParcelable("pref_map_state");

		providersToastShown = networkToastShown = navigationDataToastShown = mapState.warningShown;

		if (!mapState.directions.isEmpty())
			updateDirectionOverlay(mapState.directions);

		if (!mapState.currentPath.isEmpty())
			showPath(mapState.currentPath);
	}

	private void loadInitialStateFromPref() {
		int lat = pref.getInt("last-pos-lat", PTZ.getLatitudeE6());
		int lon = pref.getInt("last-pos-lon", PTZ.getLongitudeE6());

		initialMapPosition = new GeoPoint(lat, lon);
		initialZoom = DEFAULT_ZOOM;

		mapState.isTracking = pref.getBoolean(SettingsActivity.START_TRACKING_MODE, false);
	}

	private void scheduleGlobalLayoutListener() {
		ViewTreeObserver vto = mapView.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@SuppressWarnings("deprecation")
			@Override
			public void onGlobalLayout() {
				mapView.getController().setZoom(initialZoom);
				mapView.getController().setCenter(initialMapPosition);
				updateRadius();

				if (mapState.isTracking) {
					startTracking();
				} else {
					stopTracking();
				}

				ViewTreeObserver obs = mapView.getViewTreeObserver();
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					obs.removeOnGlobalLayoutListener(this);
				} else {
					obs.removeGlobalOnLayoutListener(this);
				}
			}
		});
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.map_fragment, container, false);

		// Initialize map
		createMapView(view);

		// Setup directions panel overlay
		panelOverlay = (TestOverlay) view.findViewById(R.id.directions_panel);
		panelOverlay.initialize(mapView);

		setupMapCenterOverlay();
		setupMyPositionOverlay();

		updatePOIOverlay();

		if (savedInstanceState != null) {
			processSavedState(savedInstanceState);
			loadInitialPositionFromSavedState(savedInstanceState);
		} else {
			processFragmentArguments();
			loadInitialPositionFromArguments();

			// No arguments so try load position from saved state
			if (initialMapPosition == null) {
				loadInitialStateFromPref();
			}
		}

		scheduleGlobalLayoutListener();

		return view;
	}
/*
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

		// Listen for new points in PointManager

		setState(State.CREATED);

		// Start tracking if preference set
		if (pref.getBoolean(SettingsActivity.START_TRACKING_MODE, false)) {
			addPendingTask(new Runnable() {
				@Override
				public void run() {
					startTracking();
				}
			}, State.DS_RECEIVED);
		}
	}
*/
	private void checkNavigationDataAvailable() {
		if (!navigationDataToastShown && recommendedContentItem == null) {
			if (!pref.getBoolean(SettingsActivity.WARN_NAVIGATION_DATA_DISABLED, false)) {
				WarnDialog dialog = new WarnDialog(R.string.warn_no_navigation_data,
						R.string.configure_navigation_data,
						R.string.warn_providers_disable,
						SettingsActivity.WARN_NAVIGATION_DATA_DISABLED) {
					@Override
					protected void onAccept() {
						Intent intent = new Intent(getActivity(), DrawerActivity.class);
						intent.setAction(ContentFragment.ACTION_SHOW_ONLINE_CONTENT);
						startActivity(intent);
					}
				};
				dialog.show(getFragmentManager(), "navigation-data-dialog");
			} else {
				Toast toast = Toast.makeText(getActivity(),
						R.string.warn_no_navigation_data, Toast.LENGTH_SHORT);
				toast.show();

			}
			navigationDataToastShown = true;
		}
	}

	private void checkNetworkAvailable() {
		boolean networkActive = Utils.checkNetworkAvailability(getActivity());

		if (!networkToastShown && !networkActive && tileProviderManager.isOnline()) {
			if (!pref.getBoolean(SettingsActivity.WARN_NETWORK_DISABLED, false)) {
				WarnDialog dialog = new WarnDialog(R.string.warn_no_network,
						R.string.configure_use_offline_map,
						R.string.warn_providers_disable,
						SettingsActivity.WARN_NETWORK_DISABLED) {
					@Override
					protected void onAccept() {
						Intent intent = new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
						startActivity(intent);
					}
				};
				dialog.show(getFragmentManager(), "network-dialog");
			} else {
				Toast toast = Toast.makeText(getActivity(),
						R.string.warn_no_network, Toast.LENGTH_SHORT);
				toast.show();
			}

			networkToastShown = true;
		}
	}

	private void checkProvidersEnabled() {
		LocationManager locationManager = (LocationManager) getActivity()
				.getSystemService(Context.LOCATION_SERVICE);

		// Check if all providers disabled and show warning
		if (!providersToastShown
				&& !locationManager
						.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
				&& !locationManager
						.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			if (!pref.getBoolean(SettingsActivity.WARN_PROVIDERS_DISABLED, false)) {
				WarnDialog dialog = new WarnDialog(R.string.warn_no_providers,
						R.string.configure_providers,
						R.string.warn_providers_disable,
						SettingsActivity.WARN_PROVIDERS_DISABLED) {
					@Override
					protected void onAccept() {
						Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
						startActivity(intent);
					}
				};

				dialog.show(getFragmentManager(), "providers-dialog");
			} else {
				Toast toast = Toast.makeText(getActivity(),
						R.string.warn_no_providers, Toast.LENGTH_SHORT);
				toast.show();
			}
			providersToastShown = true;
		}
	}
	
	@Override
	public void onDestroy() {
		log.debug("MapFragment.onDestroy");

		mapView.getTileProvider().clearTileCache();

		getActivity().unbindService(serviceConnection);
		remoteContentServiceConnection.unbindService(getActivity());

		pref.unregisterOnSharedPreferenceChangeListener(this);

		if (remoteContent != null) {
			remoteContent.removeItemListener(remoteContentAdapter);
			remoteContent = null;
		}

		super.onDestroy();
	}


	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.main, menu);
		this.menu = menu;

		super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		this.menu = menu;
		
		menu.findItem(R.id.action_track).setIcon(
				isTracking ? R.drawable.ic_action_location_searching
						: R.drawable.ic_action_location_found);

		menu.findItem(R.id.action_remove_path).setVisible(pathOverlay != null);
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_search:			
			directionService.fakeLocation(Utils.copyGeoPoint(mapView.getMapCenter()));
			menu.findItem(R.id.action_real_location).setVisible(true);
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

		case R.id.action_remove_path:
			if (pathOverlay != null) {
				mapView.getOverlays().remove(pathOverlay);
				menu.findItem(R.id.action_remove_path).setVisible(false);
				pathOverlay = null;
			}

			EventBus.getDefault().postSticky(new TargetPointEvent(null));

			mapView.invalidate();

			break;

		case R.id.action_real_location:
			if (directionService != null) {
				directionService.realLocation();
				menu.findItem(R.id.action_real_location).setVisible(false);
			}
			break;

		default:
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
				
		crossDirections = new ArrayList<>();
		for (Direction direction : directions) {
			final GeoPoint directionPoint = direction.getDirection();
			final GeoPoint centerPoint = direction.getCenter();
			
			final List<Point> points = direction.getPoints();
			
			final double bearing = centerPoint.bearingTo(directionPoint);
			//GeoPoint markerPosition = centerPoint.destinationPoint(50 << (DEFAULT_ZOOM - mapView.getZoomLevel()), (float) bearing);
			final GeoPoint markerPosition = directionPoint;
			
			//markerPosition = directionPoint;
			ClickableDirectedLocationOverlay overlay = new ClickableDirectedLocationOverlay(context, mapView, markerPosition, (float) bearing);
			
			overlay.setListener(new ClickableDirectedLocationOverlay.Listener() {
				@Override
				public void onClick() {
					Intent intent = new Intent(PointsFragment.ACTION_SHOW_POINTS, null,
							getActivity(), DrawerActivity.class);
					intent.putParcelableArrayListExtra(PointsFragment.ARG_POINTS, new ArrayList<>(points));
					startActivity(intent);
				}
			});
			
			mapView.getOverlays().add(overlay);
			crossDirections.add(overlay);
		}
		
		mapState.directions = directions;
		panelOverlay.setDirections(directions, myLocation != null ? myLocation.getBearing() : 0);
		mapView.invalidate();
	}
	
	private void updatePOIOverlay() {
		if (poiOverlay != null)
			mapView.getOverlays().remove(poiOverlay);

		PointsOverlay pointsOverlay = new PointsOverlay(getActivity(), mapView);
		pointsOverlay.setPoints(App.getInstance().getPointsAccess().loadActive());
		mapView.getOverlays().add(pointsOverlay);
		mapView.invalidate();

		poiOverlay = pointsOverlay;
/*
		List<Point> points = PointsManager.getInstance()
				.getFilteredPoints();
		List<ExtendedOverlayItem> items2 = Utils.map(points,new Utils.Function<ExtendedOverlayItem, PointDesc>() {
					public ExtendedOverlayItem apply(PointDesc point) {
						ExtendedOverlayItem item = new ExtendedOverlayItem(point.getName(), point
								.getDescription(), point.toPoint());
						item.setRelatedObject(point);
						return item;
					}
				});

		infoWindow = new POIInfoWindow(R.layout.bonuspack_bubble, mapView);
		poiOverlay = new ItemizedOverlayWithBubble<ExtendedOverlayItem>(
				context, items2, mapView, infoWindow) {
			@Override
			public boolean onSingleTapUp(MotionEvent e, MapView mapView) {
				if (infoWindow.isOpen())
					infoWindow.close();
				return super.onSingleTapUp(e, mapView);
			}
		};

		mapView.getOverlays().add(poiOverlay);*/
	}
	
	public void startTracking() {
		isTracking = true;
		myPositionOverlay.setListener(this);
		panelOverlay.setVisibility(View.VISIBLE);
		panelOverlay.setHidden(false);

		if (menu != null)
			menu.findItem(R.id.action_track).setIcon(R.drawable.ic_action_location_searching);

		updateRadius();

		EventBus.getDefault().postSticky(new TrackingModeEvent(true));
	}
	
	public void stopTracking() {

		isTracking = false;
		myPositionOverlay.clearListener();
		panelOverlay.setVisibility(View.GONE);
		panelOverlay.setHidden(true);
		
		if (menu != null)
			menu.findItem(R.id.action_track).setIcon(R.drawable.ic_action_location_found);
		
		updateDirectionOverlay(Collections.<Direction>emptyList());
		mapView.setMapOrientation(0);
		updateRadius();

		EventBus.getDefault().postSticky(new TrackingModeEvent(false));
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		mapState.center = Utils.copyGeoPoint(mapView.getMapCenter());
		mapState.zoomLevel = mapView.getZoomLevel();
		mapState.isTracking = isTracking;
		mapState.warningShown = providersToastShown || navigationDataToastShown || networkToastShown;
		outState.putParcelable("pref_map_state", mapState);
	}
	
	public void setCenter(IGeoPoint geoPoint) {
		mapView.getController().setZoom(DEFAULT_ZOOM);
		mapView.getController().animateTo(geoPoint);

		if (isTracking)
			stopTracking();
	}

	private void showPath(List<GeoPoint> path) {
		// Remove existing path overlay
		if (pathOverlay != null) {
			mapView.getOverlays().remove(pathOverlay);
		}
		
		pathOverlay = new PathOverlay(Color.BLUE, 6f, new DefaultResourceProxyImpl(getActivity()));
		pathOverlay.setAlpha(127);

		for (GeoPoint geoPoint : path)
			pathOverlay.addPoint(geoPoint);

		mapView.getOverlays().add(pathOverlay);
		mapView.invalidate();
		
		mapState.currentPath = path;
		menu.findItem(R.id.action_remove_path).setVisible(true);
	}

	// Scroll from MapListener
	@Override
	public boolean onScroll(ScrollEvent event) {
		return false;
	}

	@Override
	public void onScroll() {
		if (isTracking)
			stopTracking();
	}

	@Override
	public void onContentServiceReady(ContentService contentService) {
		remoteContent = contentService;
		remoteContent.addItemListener(remoteContentAdapter);
		remoteContent.requestRecommendedItem();
	}

	@Override
	public void onContentServiceDisconnected() {
		log.warn("onContentServiceDisconnected");
	}

	private void setupOfflineMap() {
		log.debug("setupOfflineMap");
		if (remoteContent == null || recommendedContentItem == null)
			return;

		//remoteContent.removeListener(remoteContentAdapter);
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

		boolean useOfflineMap = pref.getBoolean(SettingsActivity.USE_OFFLINE_MAP, false);

		if (useOfflineMap) {
			String offlineMapPath = remoteContent.requestContentItemSource(recommendedContentItem);

			if (offlineMapPath != null) {
				tileProviderManager.setFile(offlineMapPath);
				mapView.invalidate();
			}
		} else {
			tileProviderManager.setFile(null);
		}
	}

	private void updateRadius() {
		Projection proj = mapView.getProjection();

		int width = mapView.getWidth() - Utils.getDP(10);
		int height = mapView.getHeight() - Utils.getDP(10);

		if (isTracking) {
			width -= Utils.getDP(160);
			height -= Utils.getDP(160);
		}

		GeoPoint p1 = Utils.copyGeoPoint(proj.fromPixels(0, 0));
		IGeoPoint p2 = proj.fromPixels(width, 0);
		IGeoPoint p3 = proj.fromPixels(0, height);

		final int dist = Math.min(p1.distanceTo(p2), p1.distanceTo(p3)) / 2;
		log.trace("Size {} {}", mapView.getWidth(), mapView.getHeight());
		log.trace("Dist {} {}", p1.distanceTo(p2), p1.distanceTo(p3));
		log.trace("Zoom level {}", mapView.getZoomLevel());

		if (dist == 0)
			return;

		EventBus.getDefault().postSticky(new ScreenRadiusEvent(dist));
	}
	
	@Override
	public boolean onZoom(ZoomEvent event) {
		log.trace("MapFragment.onZoom");
		updateRadius();
		return false;
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		log.trace("MapFragment.onSharedPreferenceChanged");

		switch (key) {
		case SettingsActivity.SHOW_ACCURACY:
			if (myPositionOverlay != null)
				myPositionOverlay.setShowAccuracy(sharedPreferences.getBoolean(SettingsActivity.SHOW_ACCURACY, false));
			mapView.invalidate();
			break;
		case SettingsActivity.USE_OFFLINE_MAP:
			setupOfflineMap();
			break;
		case SettingsActivity.GETS_ENABLE:
		case SettingsActivity.GETS_SERVER:

			// TODO: reload points from new server
			break;
		}
	}

	private ContentListenerAdapter remoteContentAdapter = new ContentListenerAdapter() {
		@Override
		public void recommendedRegionItemReady(ContentItem contentItem) {
			if (!contentItem.getType().equals(ContentManagerImpl.MAPSFORGE_MAP)) {
				return;
			}

			if (recommendedContentItem == contentItem) {
				return;
			}

			recommendedContentItem = contentItem;
			setupOfflineMap();
		}

		@Override
		public void requestContentReload() {
			if (recommendedContentItem != null) {
				setupOfflineMap();
			}
		}

		@Override
		public void recommendedRegionItemNotFound(String contentType) {
			if (!contentType.equals(ContentManagerImpl.GRAPHHOPPER_MAP)) {
				return;
			}

			Handler checkerHandler = new Handler(Looper.getMainLooper());
			checkerHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (remoteContent != null) {
						checkNavigationDataAvailable();
					}
				}
			}, 1000);
		}
	};
}
