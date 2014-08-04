package org.fruct.oss.ikm.poi;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows to store points in local storage
 */
public class PointsStorage {
	private static final int VERSION = 1;
	private static final String[] QUERY_COLUMNS = {
			"lat", "lon", "name", "category", "desc", "timestamp"
	};

	private final Context context;
	private PointsDBHelper helper;
	private SQLiteDatabase db;

	public PointsStorage(Context context) {
		this.context = context;
		helper = new PointsDBHelper(context, "points-of-interest");
		db = helper.getWritableDatabase();
	}

	public void close() {
		if (helper != null)
			helper.close();
	}

	public void insertPoint(PointDesc point, String sourcePointLoader) {
		ContentValues values = new ContentValues();
		values.put("lat", point.toPoint().getLatitudeE6());
		values.put("lon", point.toPoint().getLongitudeE6());
		values.put("name", point.getName());
		values.put("category", point.getCategory());
		values.put("desc", point.getDescription());
		values.put("timestamp", System.currentTimeMillis());
		values.put("source", sourcePointLoader);

		db.insert("points", null, values);
	}

	public void insertPoints(List<PointDesc> points, String sourcePointLoader) {
		db.beginTransaction();

		try {
			db.delete("points", "source=?", new String[] {sourcePointLoader});
			for (PointDesc point : points) {
				insertPoint(point, sourcePointLoader);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void deleteOlderThan(long timestamp) {
		db.delete("points", "timestamp < ?", new String[] {String.valueOf(timestamp)});
	}

	private PointDesc createFromCursor(Cursor cursor) {
		PointDesc point = new PointDesc(cursor.getString(2), cursor.getInt(0), cursor.getInt(1));
		point.setCategory(cursor.getString(3));
		point.setDescription(cursor.getString(4));
		return point;
	}

	public List<PointDesc> loadAll(String sourcePointLoader) {
		List<PointDesc> ret = new ArrayList<PointDesc>();
		Cursor cursor = db.query("points", QUERY_COLUMNS, "source=?", new String[]{ sourcePointLoader }, null, null, null);
		if (!cursor.moveToFirst())
			return ret;

		do {
			ret.add(createFromCursor(cursor));
		} while (cursor.moveToNext());
		return ret;
	}

	private class PointsDBHelper extends SQLiteOpenHelper {
		public PointsDBHelper(Context context, String dbName) {
			super(context, dbName, null, VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE points (" +
					"id INTEGER PRIMARY KEY AUTOINCREMENT," +
					"lat INTEGER," +
					"lon INTEGER, " +
					"name TEXT NOT NULL," +
					"category TEXT," +
					"desc TEXT," +
					"timestamp INTEGER," +
					"source TEXT);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int old, int ne) {
			if (old == ne)
				return;
		}
	}

}
