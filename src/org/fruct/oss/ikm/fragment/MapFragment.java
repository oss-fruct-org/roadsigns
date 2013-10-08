package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.Utils;
import org.fruct.oss.ikm.db.RoadOpenHelper;
import org.fruct.oss.ikm.graph.MapVertex;
import org.fruct.oss.ikm.graph.Road;
import org.fruct.oss.ikm.graph.RoadGraph;
import org.fruct.oss.ikm.graph.Vertex;
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
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

public class MapFragment extends Fragment {
	public static final GeoPoint PTZ = new GeoPoint(61.783333, 34.350000);
	
	private PointProvider pointProvider = new StubPointProvider();
	private List<PointOfInterest> points = pointProvider.getPoints(0, 0, 0);
	private RoadGraph roadGraph;
	private RoadOpenHelper roadOpenHelper; 
	private SQLiteDatabase sqlite;
	private FutureTask roadGraphLoading;
	
	private List<DirectedLocationOverlay> crossDirections;
	
	private MapView mapView;
	
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
	    super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_search:
			if (roadGraph == null) {
				Context context = getActivity();
				Toast toast = Toast.makeText(context, "Database not ready yet", Toast.LENGTH_SHORT);
				toast.show();
				break;
			}
			
			updateDirections();
						
			return true;
			
		default:
			return super.onOptionsItemSelected(item);
		}
		
		return true;
	}
	
	private void updateDirections() {
		int[] out = new int[1];
		long last = System.nanoTime();
		MapVertex cross = roadGraph.nearestCrossroad(mapView.getMapCenter(), out);
		if (cross == null || out[0] > 20)
			return;
		
		List<GeoPoint> directions = new ArrayList<GeoPoint>();
		for (PointOfInterest point : points) {
			List<Vertex> path = roadGraph.findPath(cross, point.getRoadVertex());
			
			Road road;
			if (path.size() == 1) {
				road = point.getRoad();
				directions.add(point.getRoadVertex().getNode());
			} else {
				MapVertex v1 = (MapVertex) path.get(0);
				MapVertex v2 = (MapVertex) path.get(1);
				road = roadGraph.roadBetweenVertex(v1, v2);
				directions.add(v2.getNode());
			}
			Log.d("qwe", point.getName() + " " + road.getName());
		}
		long curr = System.nanoTime();
		Log.d("qwe", "" + (curr - last) / 1e9);

		updateDirectionOverlay(cross.getNode(), directions);
	}
	
	private void updateDirectionOverlay(GeoPoint center, List<GeoPoint> directions) {
		Context context = getActivity();
		if (crossDirections != null) {
			Log.d("qwe", "qweqweqwe");
			mapView.getOverlays().removeAll(crossDirections);
		}
		
		crossDirections = new ArrayList<DirectedLocationOverlay>();
		for (GeoPoint directionPoint : directions) {
			DirectedLocationOverlay overlay = new DirectedLocationOverlay(context);
			crossDirections.add(overlay);
			
			double bearing = center.bearingTo(directionPoint);
			GeoPoint markerPosition = center.destinationPoint(50, (float) bearing); // TODO: zoom distance
			overlay.setLocation(markerPosition);
			overlay.setBearing((float) bearing);
			mapView.getOverlays().add(overlay);
		}
		
		mapView.invalidate();
	}
	
	private void createPOIOverlay() {
		Context context = getActivity();
    	ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();
	    for (PointOfInterest point : points) {
	    	items.add(new OverlayItem(point.getName(), "", point.toPoint()));
	    	roadGraph.addPointOfInterest(point);
	    }
	    ItemizedIconOverlay<OverlayItem> overlay = new ItemizedIconOverlay<OverlayItem>(context, items, null);
	    mapView.getOverlays().add(overlay);
	}
	
	private void loadRoadGraph() {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				Log.d("qwe", "Running thread");
				final RoadGraph roadGraph = RoadGraph.loadFromDatabase(sqlite);
				MapFragment.this.getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						MapFragment.this.roadGraph = roadGraph;
						createPOIOverlay();
					    sqlite.close();
					    sqlite = null;
					}
				});
				Log.d("qwe", "Finished thread");
			}
		};
		
		roadGraphLoading = new FutureTask<Void>(runnable, null);
		Utils.executor.execute(roadGraphLoading);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		Context context = getActivity();
		
		RoadOpenHelper.initialize(context.getApplicationContext());
		roadOpenHelper = new RoadOpenHelper(context, null, 1);
		sqlite = roadOpenHelper.getReadableDatabase();
		loadRoadGraph();

		mapView = (MapView) getView().findViewById(R.id.map_view);
	    mapView.setBuiltInZoomControls(true);
	    
	    mapView.getController().setZoom(18);
	    mapView.getController().setCenter(PTZ);
	   
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
}
