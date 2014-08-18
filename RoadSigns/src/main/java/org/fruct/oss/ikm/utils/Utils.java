package org.fruct.oss.ikm.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.fruct.oss.ikm.App;
import org.fruct.oss.ikm.R;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
	private static final int BUFFER_SIZE = 4096;
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

	public static void copyStream(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		int r = 0;
		Thread currentThread = Thread.currentThread();

		while ((r = input.read(buffer)) != -1) {
			output.write(buffer, 0, r);
			if (currentThread.isInterrupted())
				throw new InterruptedIOException();
		}
	}

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
	
	public static String hashStream(InputStream in, String hash) throws IOException {
		MessageDigest md5 = null;
		try {
			md5 = MessageDigest.getInstance(hash);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		int bsize = 4096;
		byte[] buffer = new byte[bsize];
		int length;
		
		while ((length = in.read(buffer, 0, bsize)) > 0) {
			md5.update(buffer, 0, length);
		}
		
		return toHex(md5.digest());
	}

	public static String hashStream(InputStream in) throws IOException {
		return hashStream(in, "MD5");
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

	public static String[] getSecondaryDirs() {
		String secondaryStorageString = System.getenv("SECONDARY_STORAGE");
		if (secondaryStorageString != null && !secondaryStorageString.trim().isEmpty()) {
			return secondaryStorageString.split(":");
		} else {
			return null;
		}
	}

	public static String[] getExternalDirs(Context context) {
		List<String> paths = new ArrayList<String>();
		String[] secondaryDirs = getSecondaryDirs();
		if (secondaryDirs != null) {
			for (String secondaryDir : secondaryDirs) {
				paths.add(secondaryDir + "/roadsigns");
			}
		}

		File externalStorageDir = Environment.getExternalStorageDirectory();
		if (externalStorageDir != null && externalStorageDir.isDirectory()) {
			paths.add(Environment.getExternalStorageDirectory().getPath() + "/roadsigns");
		}

		return paths.toArray(new String[paths.size()]);
	}

	public static StorageDirDesc[] getPrivateStorageDirs(Context context) {
		List<StorageDirDesc> ret = new ArrayList<StorageDirDesc>();

		// Secondary external storage
		String[] secondaryDirs = getSecondaryDirs();
		if (secondaryDirs != null) {
			for (String secondaryStoragePath : secondaryDirs) {
				ret.add(new StorageDirDesc(R.string.storage_path_sd_card, secondaryStoragePath + "/Android/data/" + context.getPackageName() + "/files"));
			}
		}

		// External storage
		File externalDir = context.getExternalFilesDir(null);
		if (externalDir != null)
			ret.add(new StorageDirDesc(R.string.storage_path_external, externalDir.getPath()));

		// Internal storage
		ret.add(new StorageDirDesc(R.string.storage_path_internal, context.getDir("other", 0).getPath()));

		return ret.toArray(new StorageDirDesc[ret.size()]);
	}

	public static class StorageDirDesc {
		public final int nameRes;
		public final String path;

		public StorageDirDesc(int nameRes, String path) {
			this.nameRes = nameRes;
			this.path = path;
		}
	}

	public static void atomicCopy(String oldPath, String newPath, Utils.MigrationListener listener) throws IOException {
		if (oldPath.equals(newPath))
			return;

		File newDir = new File(newPath);
		if (newDir.exists() && !newDir.isDirectory())
			throw new IOException("Target directory already exists and actually not an directory");

		// First, enumerate all files in old directory
		File oldDir = new File(oldPath);
		File[] oldFiles = oldDir.listFiles();

		boolean canRename = false;
		// Check that files in directories can be simply renamed
		File tmpFile = new File(oldDir, ".roadsignstemporary" + System.currentTimeMillis());
		if (tmpFile.createNewFile()) {
			File tmpNewFile = new File(newDir, ".roadsignstemporary" + System.currentTimeMillis());
			canRename = tmpFile.renameTo(tmpNewFile);
			tmpFile.delete();
			tmpNewFile.delete();
		}

		if (!newDir.mkdirs() && !newDir.isDirectory()) {
			throw new IOException("Cannot create migration target directory " + newDir);
		}

		List<File> copiedFiles = new ArrayList<File>();

		try {
			for (int i = 0, oldFilesLength = oldFiles.length; i < oldFilesLength; i++) {
				File file = oldFiles[i];
				if (file.isDirectory()) {
					log.warn("Content directory contains subdirectory {}", file);
					continue;
				}

				String name = file.getName();
				File newFile = new File(newDir, name);

				if (canRename) {
					if (!file.renameTo(newFile)) {
						log.warn("Can't rename file but previous test show that renaming is possible. Fallback to copying");
						canRename = false;
					}
				}

				if (!canRename) {
					listener.fileCopying(name, i, oldFilesLength);
					FileInputStream inputStream = new FileInputStream(file);
					FileOutputStream outputStream = new FileOutputStream(newFile);

					copyStream(inputStream, outputStream);

					IOUtils.closeQuietly(inputStream);
					IOUtils.closeQuietly(outputStream);

					copiedFiles.add(newFile);
				}
			}
		} catch (IOException e) {
			// Clean up target directory before exit
			for (File file : copiedFiles) {
				file.delete();
			}

			newDir.delete();
			throw e;
		}

		// Delete old directory
		for (File file : oldFiles) {
			file.delete();
		}

		oldDir.delete();
	}

	// http://stackoverflow.com/questions/3301635/change-private-static-final-field-using-java-reflection
	public static void setFinalStatic(Field field, Object newValue) throws NoSuchFieldException, IllegalAccessException {
		field.setAccessible(true);

		try {
			Field modifiersField = Field.class.getDeclaredField("modifiers");
			modifiersField.setAccessible(true);
			modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
		} catch (NoSuchFieldException ignore) {
		} catch (IllegalAccessException ignore) {
		} catch (IllegalArgumentException ignore) {
		}

		field.set(null, newValue);
	}


	public static interface MigrationListener {
		void fileCopying(String name, int n, int max);
	}
}
