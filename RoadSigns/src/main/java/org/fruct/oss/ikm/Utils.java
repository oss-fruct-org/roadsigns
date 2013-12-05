package org.fruct.oss.ikm;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
	private static Logger log = LoggerFactory.getLogger(Utils.class);

	public static interface Predicate<T> {
		public boolean apply(T t);
	}
	
	@SuppressWarnings("hiding")
	public static interface Function<R, T> {
		public R apply(T t);
	}
	
	public static interface FunctionDouble {
		public double apply(double x);
	}
	
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

	public static String toHex(byte[] arr) {
		StringBuilder builder = new StringBuilder();
		for (byte b : arr) {
			builder.append(Integer.toHexString((b & 0xff) + 0x100).substring(1));
		}
		return builder.toString();
	}
	
	public static String hashString(String input) {
		MessageDigest md5;
		try {
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return String.valueOf(input.hashCode());
		}
		md5.update(input.getBytes());
		byte[] hash = md5.digest();
		return toHex(hash);
	}
	
	public static String hashStream(InputStream in) throws IOException {
		MessageDigest md5 = null;
		try {
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		int bsize = 1024;
		byte[] buffer = new byte[bsize];
		int length;
		
		while ((length = in.read(buffer, 0, bsize)) > 0) {
			md5.update(buffer, 0, length);
		}
		
		return toHex(md5.digest());
	}
	
	private static boolean checkFileNeedsUpdate(String path, InputStream in) throws IOException {
		if (!in.markSupported()) {
			log.warn("copyToInternalStorage in.markSupported == false");
			return true;
		}
		
		String fileId = hashString(path);
		
		SharedPreferences pref = App.getContext().getSharedPreferences("stored-files", Context.MODE_PRIVATE);
		String oldFileHash = pref.getString(fileId, null);
		
		log.info("copyToInternalStorage oldFileHash = " + oldFileHash);
		
		// XXX: handle any file size
		in.mark(50 * 1024 * 1024);
		String fileHash = hashStream(in);
		in.reset();
		
		log.info("copyToInternalStorage fileHash = " + fileHash);
		
		boolean needUpdate = !fileHash.equals(oldFileHash);
		
		if (needUpdate)
			pref.edit().putString(fileId, fileHash).commit();
		
		return needUpdate;
	}
	
	/**
	 * Copies or updates data from stream to internal storage
	 * 
	 * @param context Android context
	 * @param input data stream
	 * @param pathInStorage
	 * @param fileInStorage
	 * @return path to file
	 * @throws IOException
	 */
	public static String copyToInternalStorage(Context context, InputStream input,
			String pathInStorage, String fileInStorage) throws IOException {
		if (!pathInStorage.startsWith("/"))
			pathInStorage = "/" + pathInStorage;

		final int bufferSize = 4096;
		final String storageFolder = context.getFilesDir().getPath() + "/" + pathInStorage;
		final String storageFile = storageFolder + "/" + fileInStorage;
		
		File targetFile = new File(storageFile);
		if (targetFile.exists() || !checkFileNeedsUpdate(storageFile, input))
			return storageFile;
		
		log.info("copyToInternalStorage copying file " + storageFile);
	
		File targetDirectory = new File(storageFolder);
		// XXX: check return value
		targetDirectory.mkdirs();
		
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
	
	@SuppressWarnings("hiding")
	public static <R,T> List<R> map(List<T> source, Function<R, T> fun) {
		List<R> list = new ArrayList<R>(source.size());
		for (T t : source) {
			list.add(fun.apply(t));
		}
		
		return list;
	}
	
	public static int getDP(int px) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px,
				App.getContext().getResources().getDisplayMetrics());
	}

	public static float getSP(int px) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, px,
				App.getContext().getResources().getDisplayMetrics());
	}
		
	public static String stringMeters(int meters) {
		String str;
		
		if (meters > 1000) {
			double v = (meters / 100) / 10.0;
			str = tr(R.plurals.distance_kilometers, (int) (v * 10), v);
		} else {
			str = tr(R.plurals.distance_meters, meters, meters);
		}
		
		return str;
	}
	
	public static String tr(int resId) {
		return App.getContext().getResources().getString(resId);
	}
	
	public static String tr(int resId, int count, Object... args) {
		return App.getContext().getResources().getQuantityString(resId, count, args);
	}
	
	/*public static double solve(double a, double b, double delta, FunctionDouble fun) {
		{
			double fa = fun.apply(a);
			double fb = fun.apply(b);

			if (fa > 0 && fb > 0 || fa < 0 && fb < 0 || a >= b)
				throw new IllegalArgumentException();
		}
		
		final int MAX_ITERS = 100;
		for (int i = 0; i < MAX_ITERS; i++) {
			double c = (a + b) / 2;
			double fc = fun.apply(c);
			
			if (Math.abs(fc) < delta || (b - a) / 2 < delta) {
				return c;
			}
			
			double fa = fun.apply(a);
			if (fa < 0 && fc < 0 || fa > 0 && fc > 0) {
				a = c;
			} else {
				b = c;
			}
		}
		
		throw new IllegalArgumentException();
	}*/
	
	/*public static double solve(double a, double b, double delta, FunctionDouble fun) {
		{
			double fa = fun.apply(a);
			double fb = fun.apply(b);

			if (fa > 0 && fb > 0 || fa < 0 && fb < 0 || a >= b)
				throw new IllegalArgumentException();
		}
		
		final int MAX_ITERS = 100;
		for (int i = 0; i < MAX_ITERS; i++) {
			if (Math.abs(a - b) <= delta)
				return (a + b) / 2;
			
			final double fa = fun.apply(a);
			final double fb = fun.apply(b);

	        a = b - (b - a) * fb / (fb - fa);
	        b = a - (a - b) * fa / (fa - fb);
		}
		
		throw new IllegalArgumentException();
	}*/
	
	// False position method
	public static double solve(double a, double b, double delta, FunctionDouble fun) {
		double fa = fun.apply(a);
		double fb = fun.apply(b);
		double fr, r = 0;
		int side = 0;
		
		if (fa > 0 && fb > 0 || fa < 0 && fb < 0 || a >= b)
			throw new IllegalArgumentException();
		
		final int MAX_ITERS = 100;
		for (int i = 0; i < MAX_ITERS; i++) {
			r = (fa * b - fb * a) / (fa - fb);
			if (Math.abs(a - b) < delta * Math.abs(a + b))
				break;

			fr = fun.apply(r);
			if (fr * fb > 0) {
				b = r;
				fb = fr;
				if (side == -1)
					fa /= 2;
				side = -1;
			} else if (fa * fr > 0) {
				a = r;
				fa = fr;
				if (side == 1)
					fb /= 2;
				side = 1;
			} else {
				break;
			}
		}
		
		return r;
	}
}