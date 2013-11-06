package org.fruct.oss.ikm;

import android.app.Application;
import android.content.Context;

public class App extends Application {
	private static Context context;
	
	@Override
	public void onCreate() {
		super.onCreate();
	
		App.context = getApplicationContext();
	}
	
	public static Context getContext() {
		if (context == null)
			throw new IllegalStateException("Application not initialized yet");
		
		return context;
	}
}
