package com.salidasoftware.osmdroidandmapsforge;

import java.io.File;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import android.content.Context;
import android.location.Location;
import android.os.Environment;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class HybridMap extends FrameLayout {

	public MapView mv;
	private ResourceProxy resourceProxy;

	public HybridMap(Context context, AttributeSet attrs) 
	{
		super(context, attrs);

		resourceProxy = new DefaultResourceProxyImpl(this.getContext());
		useMapsforgeTiles(Environment.getExternalStorageDirectory().getPath() + File.separator + "iceland.map");
		mv.getController().setZoom(14);
		center(39.73465,-104.981446);
	}

	public void useMapsforgeTiles(String mapFilePath){

		if(mv != null){
			this.removeView(mv);
			mv = null;
		}

		File mapFile = new File(mapFilePath);
		MFTileProvider provider = new MFTileProvider((IRegisterReceiver)this.getContext(), mapFile);

		mv = new MapView(this.getContext(), provider.getTileSource().getTileSizePixels(), resourceProxy, provider);
		mv.setBuiltInZoomControls(true);
		mv.setMultiTouchControls(true);

		this.addView(mv);

	}

	public void center(double latitude, double longitude){
		GeoPoint p = new GeoPoint(latitude, longitude);
		mv.getController().setCenter(p);
	}

}
