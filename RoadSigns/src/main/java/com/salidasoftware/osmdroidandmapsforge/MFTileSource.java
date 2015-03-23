package com.salidasoftware.osmdroidandmapsforge;

//Adapted from code found here : http://www.sieswerda.net/2012/08/15/upping-the-developer-friendliness/

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidTileBitmap;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.cache.InMemoryTileCache;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapDatabase;
import org.mapsforge.map.reader.header.FileOpenResult;
import org.mapsforge.map.reader.header.MapFileInfo;
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback;
import org.osmdroid.ResourceProxy;
import org.osmdroid.ResourceProxy.string;
import org.osmdroid.tileprovider.LRUMapTileCache;
import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.core.model.Tile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.util.LruCache;

public class MFTileSource extends BitmapTileSourceBase {
	protected File mapFile;

	// Reasonable defaults ..
	public static final int minZoom = 8;
	public static final int maxZoom = 18;
	public static final int tileSizePixels = 256;

	private DatabaseRenderer renderer;
	private MapDatabase mapDatabase;

	// Required for the superclass
	public static final string resourceId = ResourceProxy.string.offline_mode;
	private DisplayModel displayModel;

	private TileCache tileCache;

	private Bitmap badTileBitmap;

	/**
	 * The reason this constructor is protected is because all parameters,
	 * except file should be determined from the archive file. Therefore a
	 * factory method is necessary.
	 *
	 * @param minZoom
	 * @param maxZoom
	 * @param tileSizePixels
	 * @param file
	 */
	protected MFTileSource(int minZoom, int maxZoom, int tileSizePixels, File file) {
		super("MFTiles", resourceId, minZoom, maxZoom, tileSizePixels, ".png");

		setFile(file);
	}

	/**
	 * Creates a new MFTileSource from file.
	 *
	 * Parameters minZoom and maxZoom are obtained from the
	 * database. If they cannot be obtained from the DB, the default values as
	 * defined by this class are used.
	 *
	 * @param file
	 * @return
	 */
	public static MFTileSource createFromFile(File file) {
		//TODO - set these based on .map file info
		int minZoomLevel = 8;
		int maxZoomLevel = 18;
		int tileSizePixels = 256;

		return new MFTileSource(minZoomLevel, maxZoomLevel, tileSizePixels, file);
	}

	//The synchronized here is VERY important.  If missing, the mapDatbase read gets corrupted by multiple threads reading the file at once.
	public synchronized Drawable renderTile(MapTile pTile) {
		Tile tile = new Tile(pTile.getX(), pTile.getY(), (byte)pTile.getZoomLevel(), 256);

		byte[] compressed = null;

		try {
			//Draw the tile
			RendererJob rendererJob = new RendererJob(tile, mapFile, theme, displayModel, 1.0f, false, false);
			TileBitmap tileBitmap = renderer.executeJob(rendererJob);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			tileBitmap.compress(out);
			compressed = out.toByteArray();
			tileBitmap.decrementRefCount();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		Bitmap bitmap;
		if (compressed == null) {
			bitmap = badTileBitmap;
		} else {
			bitmap = BitmapFactory.decodeByteArray(compressed, 0, compressed.length);
		}

		return new BitmapDrawable(bitmap);
	}

	public synchronized void setFile(File file) {
		if (mapDatabase != null && mapDatabase.hasOpenFile()) {
			mapDatabase.closeFile();
		}

		mapDatabase = new MapDatabase();

		//Make sure the database can open the file
		FileOpenResult fileOpenResult = this.mapDatabase.openFile(file);
		if (fileOpenResult.isSuccess()) {
			mapFile = file;
		}
		else{
			mapFile = null;
		}

		displayModel = new DisplayModel();
		displayModel.setFixedTileSize(tileSizePixels);
		badTileBitmap = Bitmap.createBitmap(tileSizePixels, tileSizePixels, Bitmap.Config.RGB_565);
		badTileBitmap.eraseColor(Color.YELLOW);

		tileCache = new InMemoryTileCache(100);


		renderer = new DatabaseRenderer(mapDatabase, AndroidGraphicFactory.INSTANCE, tileCache);
	}

	private XmlRenderTheme theme = new XmlRenderTheme() {
		@Override
		public XmlRenderThemeMenuCallback getMenuCallback() {
			return null;
		}

		@Override
		public String getRelativePathPrefix() {
			return "/osmarender/";
		}

		@Override
		public InputStream getRenderThemeAsStream() throws FileNotFoundException {
			return this.getClass().getResourceAsStream("/osmarender/osmarender.xml");
		}
	};
}
