package org.fruct.oss.ikm.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.R;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
	private static Logger log = LoggerFactory.getLogger(Utils.class);

	private Utils() {
	}

	public static interface Predicate<T> {
		public boolean apply(T t);
	}
	
	@SuppressWarnings("hiding")
	public static interface Function<R, T> {
		public R apply(T t);
	}

	public static interface Function2<R1, R2, T> {
		public Pair<R1, R2> apply(T t);
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

	/*public static String toHex(byte[] arr) {
		StringBuilder builder = new StringBuilder();
		for (byte b : arr) {
			builder.append(Integer.toHexString((b & 0xff) + 0x100).substring(1));
		}
		return builder.toString();
	}*/

	private static final char[] hexDigits = "0123456789abcdef".toCharArray();
	public static String toHex(byte[] arr) {
		final char[] str = new char[arr.length * 2];

		for (int i = 0; i < arr.length; i++) {
			final int v = arr[i] & 0xff;
			str[2 * i] = hexDigits[v >>> 4];
			str[2 * i + 1] = hexDigits[v & 0x0f];
		}

		return new String(str);
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

	public static String inputStreamToString(InputStream stream) throws IOException {
		InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
		return readerToString(reader);
	}

	public static String readerToString(Reader reader) throws IOException {
		StringBuilder builder = new StringBuilder();
		int bufferSize = 4096;
		char[] buf = new char[bufferSize];

		int readed;
		while ((readed = reader.read(buf)) > 0) {
			builder.append(buf, 0, readed);
		}

		return builder.toString();
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

	public static <R1, R2, T> Pair<List<R1>, List<R2>> map2(List<T> source, Function2<R1, R2, T> fun) {
		List<R1> ret1 = new ArrayList<R1>(source.size());
		List<R2> ret2 = new ArrayList<R2>(source.size());

		for (T t : source) {
			Pair<R1, R2> ret = fun.apply(t);
			if (ret != null) {
				ret1.add(ret.first);
				ret2.add(ret.second);
			}
		}

		return new Pair<List<R1>, List<R2>>(ret1, ret2);
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

	public static boolean checkNetworkAvailability(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = connManager.getActiveNetworkInfo();
		return info != null && info.isConnected();
	}

	public static <T, R extends T> R safeCast(T t, Class<? extends R> cls) {
		if (t == null || !cls.isInstance(t))
			return null;
		else
			return cls.cast(t);
	}

	public static int getDialogTheme() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			return android.R.style.Theme_Dialog;
		} else {
			return android.R.style.Theme_Holo_Light_Dialog;
		}
	}

	public static void deleteDir(File dir) {
		if (!dir.exists() && !dir.isDirectory())
			return;

		File[] listFiles = dir.listFiles();
		for (File file : listFiles) {
			if (!file.isDirectory()) {
				file.delete();
			}
		}

		dir.delete();
	}

	public static int geoCoordMin(double coord) {
		return (int) ((geoCoordDeg(coord) - Math.abs(coord)) * 60);
	}

	public static int geoCoordDeg(double coord) {
		return (int) (Math.abs(coord));
	}

	public static String[] getPrivateStorageDirs(Context context) {
		List<String> ret = new ArrayList<String>();

		// Internal storage
		ret.add(context.getDir("storage", 0).getPath());

		// External storage
		File externalDir = context.getExternalFilesDir(null);
		if (externalDir != null)
			ret.add(externalDir.getPath());

		// Secondary external storage
		String secondaryStorageString = System.getenv("SECONDARY_STORAGE");
		if (secondaryStorageString != null && !secondaryStorageString.trim().isEmpty()) {
			for (String secondaryStoragePath : secondaryStorageString.split(":")) {
				ret.add(secondaryStoragePath + "/Android/data/" + context.getPackageName());
			}
		}

		return ret.toArray(new String[ret.size()]);
	}
}
