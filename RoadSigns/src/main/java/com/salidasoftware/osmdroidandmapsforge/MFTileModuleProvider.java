
package com.salidasoftware.osmdroidandmapsforge;

//Adapted from code found here : http://www.sieswerda.net/2012/08/15/upping-the-developer-friendliness/

import java.io.File;

import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.tileprovider.MapTileRequestState;
import org.osmdroid.tileprovider.modules.MapTileFileStorageProviderBase;
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.osmdroid.tileprovider.tilesource.ITileSource;

import android.graphics.drawable.Drawable;

public class MFTileModuleProvider extends MapTileFileStorageProviderBase {

	protected MFTileSource tileSource;

	/**
	 * Constructor
	 * 
	 * @param pRegisterReceiver
	 * @param file
	 * @param tileSource
	 */
	public MFTileModuleProvider(IRegisterReceiver receiverRegistrar, MFTileSource tileSource) {
		super(receiverRegistrar, NUMBER_OF_TILE_FILESYSTEM_THREADS, TILE_FILESYSTEM_MAXIMUM_QUEUE_SIZE);

		this.tileSource = tileSource;

	}

	@Override
	protected String getName() {
		return "MapsforgeTiles Provider";
	}

	@Override
	protected String getThreadGroupName() {
		return "mapsforgetilesprovider";
	}

	@Override
	protected Runnable getTileLoader() {
		return new TileLoader();
	}

	@Override
	public boolean getUsesDataConnection() {
		return false;
	}

	@Override
	public int getMinimumZoomLevel() {
		if (tileSource != null)
			return tileSource.getMinimumZoomLevel();
		else
			return MINIMUM_ZOOMLEVEL;
	}

	@Override
	public int getMaximumZoomLevel() {
		if (tileSource != null)
			return tileSource.getMaximumZoomLevel();
		else
			return MAXIMUM_ZOOMLEVEL;
	}

	@Override
	public void setTileSource(ITileSource tileSource) {
		//prevent re-assignment of tile source
		if (tileSource instanceof MFTileSource) {
			this.tileSource = (MFTileSource) tileSource;
		} else {
			this.tileSource = null;
		}
	}

	private class TileLoader extends MapTileModuleProviderBase.TileLoader {
		@Override
		public Drawable loadTile(final MapTileRequestState pState) {
			if (tileSource != null)
				return tileSource.renderTile(pState.getMapTile());
			else
				return null;
		}
	}

}
