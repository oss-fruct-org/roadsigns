package org.fruct.oss.ikm;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

import android.content.Context;
import android.util.Log;

public class Utils {
	public static interface Predicate<T> {
		public boolean apply(T t);
	}
	
	public final static Executor executor = Executors.newFixedThreadPool(1);
	public static GeoPoint copyGeoPoint(IGeoPoint p) {
		return new GeoPoint(p.getLatitudeE6(), p.getLongitudeE6());
	}
	
	public static void log(String str) {
		Log.d("roadsigns", str);
	}
	
	public static void log(String format, Object... args) {
		log(String.format(format, args));
	}
	
	public static float normalizeAngle(float degree) {
		return (float) (StrictMath.IEEEremainder(degree, 360));
	}
	
	public static String copyToInternalStorage(Context context, InputStream inputStream,
			String pathInStorage, String fileInStorage) throws IOException {
		if (!pathInStorage.startsWith("/"))
			pathInStorage = "/" + pathInStorage;

		final int bufferSize = 4096;
		final String storageFolder = context.getFilesDir().getPath() + "/" + pathInStorage;
		final String storageFile = storageFolder + "/" + fileInStorage;
		
		File targetFile = new File(storageFile);
		if (targetFile.exists())
			return storageFile;
		
		File targetDirectory = new File(storageFolder);
		targetDirectory.mkdirs();
		
		InputStream input = new BufferedInputStream(inputStream);
		OutputStream output = new BufferedOutputStream(new FileOutputStream(targetFile));
		
		int length;
		byte[] buffer = new byte[bufferSize];
		while ((length = input.read(buffer)) > 0) {
			output.write(buffer, 0, length);
		}
		
		output.flush();
		output.close();
		
		return storageFile;
	}
	

	public static <T> void select(Collection<T> source, Collection<T> target, Predicate<T> pred) {
		for (T t : source) {
			if (pred.apply(t))
				target.add(t);
		}
	}
	
	public static <T> T find (Collection<T> source, Predicate<T> pred) {
		for (T t : source) {
			if (pred.apply(t))
				return t;
		}
		
		return null;
	}
}
