package org.fruct.oss.ikm;

import java.io.File;
import java.util.HashSet;

import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.BitmapPool;
import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.tileprovider.MapTileCache;
import org.osmdroid.tileprovider.MapTileProviderArray;
import org.osmdroid.tileprovider.modules.MapTileDownloader;
import org.osmdroid.tileprovider.modules.MapTileFilesystemProvider;
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck;
import org.osmdroid.tileprovider.modules.TileWriter;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.salidasoftware.osmdroidandmapsforge.MFTileModuleProvider;
import com.salidasoftware.osmdroidandmapsforge.MFTileSource;

public class TileProviderManager {
	private MapTileProviderArray provider;
	private MFTileSource mfSource;
	private OnlineTileSourceBase webSource;
	private boolean isOnline;

	public TileProviderManager(Context context) {
    	SimpleRegisterReceiver register = new SimpleRegisterReceiver(context.getApplicationContext());		
    	
    	SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
    	String mapPath = pref.getString(SettingsActivity.OFFLINE_MAP, "");
    	
		File mapFile = new File(mapPath);
			
		mfSource = MFTileSource.createFromFile(mapFile);
		webSource = TileSourceFactory.MAPQUESTOSM;
		//webSource = new XYTileSource("Mapquest", ResourceProxy.string.mapnik, 1, 18, 256, ".png", "http://tile.openstreetmap.org/");

		// Setup cache
		TileWriter cacheWriter = new TileWriter();
		MapTileFilesystemProvider fileSystemProvider = new MapTileFilesystemProvider(register, webSource);

		// Setup providers
		NetworkAvailabliltyCheck networkAvailabliltyCheck = new NetworkAvailabliltyCheck(context);
		MFTileModuleProvider mfProvider = new MFTileModuleProvider(register, mapFile, mfSource);
		MapTileDownloader webProvider = new MapTileDownloader(webSource, cacheWriter, networkAvailabliltyCheck);
		
		provider = new ClearableMapTileProviderArray(mfSource, register,
				new MapTileModuleProviderBase[] { fileSystemProvider, mfProvider, webProvider });
		
		if (!mapPath.isEmpty() && mapFile.exists()) {
			provider.setTileSource(mfSource);
			isOnline = false;
		} else {
			provider.setTileSource(webSource);
			isOnline = true;
		}
	}
	
	public MapTileProviderArray getProvider() {
		return provider;
	}
	
	public void setFile(String path) {
		File file = new File(path);

		if (path == null || path.isEmpty() || !file.exists()) {
			provider.setTileSource(webSource);
			isOnline = true;
			return;
		}

		mfSource.setFile(file);
		provider.setTileSource(mfSource);
	}

	public boolean isOnline() {
		return isOnline;
	}

	/*
	 *	MapTileProviderArray that allows application clear MapTileCache
	 */
	private class ClearableMapTileProviderArray extends MapTileProviderArray {
		public ClearableMapTileProviderArray(ITileSource pTileSource, IRegisterReceiver aRegisterReceiver, MapTileModuleProviderBase[] pTileProviderArray) {
			super(pTileSource, aRegisterReceiver, pTileProviderArray);
		}

		@Override
		public MapTileCache createTileCache() {
			ClearableTileCache tmpTileCache = new ClearableTileCache();
			App.addClearable(tmpTileCache);
			return tmpTileCache;
		}
	}

	private class ClearableTileCache extends MapTileCache implements App.Clearable {
	}
}
