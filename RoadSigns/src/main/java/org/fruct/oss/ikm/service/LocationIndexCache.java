package org.fruct.oss.ikm.service;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;

import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LocationIndexCache {
	private static Logger log = LoggerFactory.getLogger(LocationIndexCache.class);

	private DBHelper helper;
	private SQLiteDatabase db;
	private SharedPreferences pref;

	public LocationIndexCache(Context context) {
		pref = PreferenceManager.getDefaultSharedPreferences(context);
		helper = new DBHelper(context);
		db = helper.getWritableDatabase();
	}

	public void close() {
		helper.close();
	}

	public void put(GeoPoint point, int index) {
		int hash = point.hashCode();

		ContentValues cv = new ContentValues(5);
		cv.put("hash", hash);
		cv.put("lat", point.getLatitudeE6());
		cv.put("lon", point.getLongitudeE6());
		cv.put("indx", index);
		cv.put("timestamp", System.currentTimeMillis());

		db.insert("cache", null, cv);
	}

	public int get(GeoPoint point) {
		Cursor cursor = db.rawQuery("select indx from cache where hash=? and lat=? and lon=?;",
				new String[]{String.valueOf(point.hashCode()),
						String.valueOf(point.getLatitudeE6()),
						String.valueOf(point.getLongitudeE6())});

		try {
			if (cursor.moveToFirst()) {
				return cursor.getInt(0);
			}
			return -1;
		} finally {
			cursor.close();
		}
	}

	public void reset(String regionId, String navigationPath) {
		log.info("Resetting LocationIndexCache");
		db.execSQL("delete from cache;");

		pref.edit().putString("pref-location-index-cache-region-id", regionId)
				.putString("pref-location-index-cache-navigationPath", navigationPath)
				.apply();
	}

	public String getRegionId() {
		return pref.getString("pref-location-index-cache-region-id", "");
	}

	public String getNavigationPath() {
		return pref.getString("pref-location-index-cache-navigationPath", null);
	}

	private static class DBHelper extends SQLiteOpenHelper {
		public DBHelper(Context context) {
			super(context, "location_index_cache", null, 1);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("create table cache (id integer primary key autoincrement," +
					"hash integer," +
					"lat integer, lon integer," +
					"indx integer," +
					"timestamp integer);");
			db.execSQL("create index cache_index on cache(hash);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (oldVersion == newVersion)
				return;

			db.execSQL("delete from cache;");
		}
	}
}
