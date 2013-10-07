package org.fruct.oss.ikm.fragment;

import java.util.ArrayList;
import java.util.List;

import org.fruct.oss.ikm.R;
import org.fruct.oss.ikm.graph.MapVertex;
import org.fruct.oss.ikm.graph.Road;
import org.fruct.oss.ikm.graph.RoadGraph;
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

			
			MapVertex cross = roadGraph.nearestCrossroad(mapView.getMapCenter(), out);
			for (Road road : cross.getRoads()) {
				for (PointOfInterest point : road.getPointsOfInterest()) {
					Log.d("qwe", point.getName());
				}
			}

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
