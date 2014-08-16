package org.fruct.oss.ikm;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.osmdroid.tileprovider.BitmapPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.WeakHashMap;

public class App extends Application {
	public interface Clearable {
		void clear();
	}

	private static Logger log = LoggerFactory.getLogger(App.class);

	private static Context context;
	private static App app;

	private static WeakHashMap<Clearable, Object> clearables = new WeakHashMap<Clearable, Object>();

	@Override
	public void onCreate() {
		super.onCreate();
	
		App.context = getApplicationContext();
		App.app = this;
		PreferenceManager.setDefaultValues(App.context, R.xml.preferences, false);
		AndroidGraphicFactory.createInstance(this);
	}
	
	public static Context getContext() {
		if (context == null)
			throw new IllegalStateException("Application not initialized yet");
		
		return context;
	}

	public static App getInstance() {
		if (app == null)
			throw new IllegalStateException("Application not initialized yet");

		return App.app;
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		log.info("onLowMemory called");
		for (Clearable clearable : clearables.keySet()) {
			clearable.clear();
		}
	}

	public static void addClearable(Clearable clearable) {
		clearables.put(clearable, log /* dummy object */);
	}

	public static void clearBitmapPool() {
		// FIXME: use method clearBitmapPool() when it will be available in maven repository
		BitmapPool pool = BitmapPool.getInstance();

		Field[] fields = BitmapPool.class.getDeclaredFields();
		for (Field field : fields) {
			if (field.getName().equals("mPool")) {
				field.setAccessible(true);
				try {
					LinkedList<Bitmap> mPool = (LinkedList) field.get(pool);
					log.debug("Pool size = " + mPool.size());
					synchronized (mPool) {
						while (!mPool.isEmpty()) {
							Bitmap bitmap = mPool.remove();
							bitmap.recycle();
						}
					}
				} catch (IllegalAccessException e) {
					e.printStackTrace();
					return;
				}

				break;
			}
		}
	}
}
