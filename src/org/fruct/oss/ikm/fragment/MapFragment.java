package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.R;
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
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

public class MapFragment extends Fragment {
	public static final GeoPoint PTZ = new GeoPoint(61.783333, 34.350000);
	
	private PointProvider pointProvider = new StubPointProvider();
	private List<PointOfInterest> points = pointProvider.getPoints(0, 0, 0);
	private RoadGraph roadGraph = RoadGraph.createSampleGraph();
	private RoadOpenHelper roadOpenHelper; 
	private SQLiteDatabase sqlite;
	
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
			int[] out = new int[1];
			long last = System.nanoTime();
			MapVertex cross = roadGraph.nearestCrossroad(mapView.getMapCenter(), out);
			for (PointOfInterest point : points) {
				List<Vertex> path = roadGraph.findPath(cross, point.getRoadVertex());
				
				Road road;
				if (path.size() == 1) {
					road = point.getRoad();
				} else {
					MapVertex v1 = (MapVertex) path.get(0);
					MapVertex v2 = (MapVertex) path.get(1);
					road = roadGraph.roadBetweenVertex(v1, v2);
				}
				
				//Log.d("qwe", point.getName() + " " + path.size());
				Log.d("qwe", point.getName() + " " + road.getName());
			}
			long curr = System.nanoTime();
			Log.d("qwe", "" + (curr - last) / 1e9);

			
			//Cursor cursor = sqlite.rawQuery("select * from nodeways where nodeId=247347562", null);
			//Log.d("qwe", "" + cursor.getColumnNames()[0]);
			
			/*Context context = getActivity();
			Toast toast = Toast.makeText(context, text + " " + dist, Toast.LENGTH_SHORT);
			toast.show();*/
			return true;
			
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		Context context = getActivity();
		
		RoadOpenHelper.initialize(context.getApplicationContext());
		roadOpenHelper = new RoadOpenHelper(context, null, 1);
		sqlite = roadOpenHelper.getReadableDatabase();
		roadGraph = RoadGraph.loadFromDatabase(sqlite);

		mapView = (MapView) getView().findViewById(R.id.map_view);
	    mapView.setBuiltInZoomControls(true);
	    
	    mapView.getController().setZoom(16);
	    mapView.getController().setCenter(PTZ);
	   
	    GpsMyLocationProvider provider = new GpsMyLocationProvider(context);
	    IMyLocationProvider provider2 = new StubMyLocationProvider(mapView);
	    
	    MyLocationNewOverlay myLocationOverlay = new MyLocationNewOverlay(context, provider2, mapView);
	    mapView.getOverlays().add(myLocationOverlay);
	    myLocationOverlay.enableMyLocation(provider2);
	    
    	ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();
	    for (PointOfInterest point : points) {
	    	items.add(new OverlayItem(point.getName(), "", point.toPoint()));
	    	roadGraph.addPointOfInterest(point);
	    }
	    ItemizedIconOverlay<OverlayItem> overlay = new ItemizedIconOverlay<OverlayItem>(context, items, null);
	    mapView.getOverlays().add(overlay);
	}
}
