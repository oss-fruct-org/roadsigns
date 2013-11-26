package org.fruct.oss.ikm;

import java.io.File;

import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.MapTileProviderArray;
import org.osmdroid.tileprovider.modules.MapTileDownloader;
import org.osmdroid.tileprovider.modules.MapTileFilesystemProvider;
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck;
import org.osmdroid.tileprovider.modules.TileWriter;
import org.osmdroid.tileprovider.tilesource.ITileSource;
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
	private XYTileSource webSource;
	
	public TileProviderManager(Context context) {
    	SimpleRegisterReceiver register = new SimpleRegisterReceiver(context.getApplicationContext());		
    	
    	SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
    	String mapPath = pref.getString(SettingsActivity.OFFLINE_MAP, "");
    	
		File mapFile = new File(mapPath);
			
		mfSource = MFTileSource.createFromFile(mapFile);
		webSource = new XYTileSource("Mapnik", ResourceProxy.string.mapnik, 1, 18, 256, ".png", "http://tile.openstreetmap.org/");
		
		// Setup cache
		TileWriter cacheWriter = new TileWriter();
		MapTileFilesystemProvider fileSystemProvider = new MapTileFilesystemProvider(register, webSource);
		
		// Setup providers
		NetworkAvailabliltyCheck networkAvailabliltyCheck = new NetworkAvailabliltyCheck(context);
		MFTileModuleProvider mfProvider = new MFTileModuleProvider(register, mapFile, mfSource);
		MapTileDownloader webProvider = new MapTileDownloader(webSource, cacheWriter, networkAvailabliltyCheck);
		
		provider = new MapTileProviderArray(mfSource, register,
				new MapTileModuleProviderBase[] { fileSystemProvider, mfProvider, webProvider });
		
		if (!mapPath.isEmpty() && mapFile.exists()) {
			provider.setTileSource(mfSource);
		} else {
			provider.setTileSource(webSource);
		}
	}
	
	public MapTileProviderArray getProvider() {
		return provider;
	}
	
	public void setFile(String path) {
		File file = new File(path);

		if (path == null || path.isEmpty() || !file.exists()) {
			provider.setTileSource(webSource);
			return;
		}
	
		mfSource.setFile(file);
		provider.setTileSource(mfSource);
	}
}
