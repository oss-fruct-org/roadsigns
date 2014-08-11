package org.fruct.oss.ikm;

import java.io.File;
import java.util.HashSet;

import org.fruct.oss.ikm.storage.RemoteContent;
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
	private MFTileSource mfSource;
	private OnlineTileSourceBase webSource;
	private boolean isOnline;
	private final RemoteContent remoteContent;

	public TileProviderManager(Context context) {
    	SimpleRegisterReceiver register = new SimpleRegisterReceiver(context.getApplicationContext());		
    	
    	SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
    	String mapName = pref.getString(SettingsActivity.OFFLINE_MAP, null);
		String contentPath = pref.getString(SettingsActivity.STORAGE_PATH, null);

		remoteContent = RemoteContent.getInstance(contentPath);
		String mapPath = remoteContent.getPath(mapName);
		if (mapPath == null) {
			mapPath = "";
		}

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
		
		provider = new MapTileProviderArray(mfSource, register,
				new MapTileModuleProviderBase[] { fileSystemProvider, mfProvider, webProvider });
		App.addClearable(this);

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
	
	public void setFile(String name) {
		String path = remoteContent.getPath(name);
		File file = (path == null ? null : new File(path));

		if (file == null || path.isEmpty() || !file.exists()) {
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

	@Override
	public void clear() {
		provider.clearTileCache();
	}
}
