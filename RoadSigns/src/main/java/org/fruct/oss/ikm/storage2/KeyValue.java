package org.fruct.oss.ikm.storage2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.Closeable;
import java.io.IOException;

public class KeyValue implements Closeable {
	private final Helper helper;
	private final Context context;
	private final String name;
	private final SQLiteDatabase db;

	public KeyValue(Context context, String name) {
		this.context = context;
		this.name = name;
		this.helper = new Helper(context, name);
		this.db = helper.getWritableDatabase();
	}

	@Override
	public void close() {
		helper.close();
	}

	public void put(String key, String value) {
		ContentValues cv = new ContentValues(2);
		cv.put("value", value);

		db.beginTransaction();
		if (0 == db.update(name, cv, "key=?", new String[]{key})) {
			cv.put("key", key);
			db.insert(name, null, cv);
		}
	}

	public void delete(String key) {
		db.delete(name, "key=?", new String[] { key });
	}

	public String get(String key) {
		return get(key, null);
	}

	public String get(String key, String defaultValue) {
		Cursor cursor = db.rawQuery("SELECT value FROM ? WHERE key=?;", new String[]{name, key});
		if (!cursor.moveToFirst()) {
			cursor.close();
			return defaultValue;
		}

		try {
			return cursor.getString(0);
		} finally {
			cursor.close();
		}
	}


	private class Helper extends SQLiteOpenHelper {
		private String name;

		public Helper(Context context, String name) {
			super(context, name, null, 1);

			this.name = name;
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE ? (key TEXT PRIMARY KEY, value TEXT);", new String[] { name });
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE " + name);
			onCreate(db);
		}
	}
}
