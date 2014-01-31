package org.fruct.oss.ikm;

import android.app.Application;
import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
}
