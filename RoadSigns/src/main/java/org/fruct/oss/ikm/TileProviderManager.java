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

public class TileProviderManager implements App.Clearable {
	private MapTileProviderArray provider;
	private OnlineTileSourceBase webSource;
	private boolean isOnline;


	public TileProviderManager(Context context) {
    	SimpleRegisterReceiver register = new SimpleRegisterReceiver(context.getApplicationContext());

		webSource = TileSourceFactory.MAPQUESTOSM;

		// Setup cache
		TileWriter cacheWriter = new TileWriter();
		MapTileFilesystemProvider fileSystemProvider = new MapTileFilesystemProvider(register, webSource);

		// Setup providers
		NetworkAvailabliltyCheck networkAvailabliltyCheck = new NetworkAvailabliltyCheck(context);
		MFTileModuleProvider mfProvider = new MFTileModuleProvider(register, null);
		MapTileDownloader webProvider = new MapTileDownloader(webSource, cacheWriter, networkAvailabliltyCheck);
		
		provider = new MapTileProviderArray(webSource, register,
				new MapTileModuleProviderBase[] { fileSystemProvider, mfProvider, webProvider });

		App.addClearable(this);

		provider.setTileSource(webSource);
		isOnline = true;
	}
	
	public MapTileProviderArray getProvider() {
		return provider;
	}
	
	public void setFile(String path) {
		File file = (path == null ? null : new File(path));

		if (file == null || path.isEmpty() || !file.exists()) {
			provider.setTileSource(webSource);
			isOnline = true;
			return;
		}

		MFTileSource mfSource = MFTileSource.createFromFile(file);
		provider.setTileSource(mfSource);
	}

	public boolean isOnline() {
		return isOnline;
	}

	@Override
	public void clear() {
		provider.clearTileCache();
	}
}
