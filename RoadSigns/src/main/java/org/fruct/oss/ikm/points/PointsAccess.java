package org.fruct.oss.ikm.points;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.fruct.oss.ikm.points.gets.Category;
import org.fruct.oss.mapcontent.content.utils.Utils;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows to store points in local storage
 */
public class PointsAccess {
	private static final int VERSION = 3;

	private static String POINT_SELECT = " point.name, point.desc, point.lat, point.lon," +
			" point.uuid, point.photosJson, point.id," +
			" point.regionId4, point.regionId6, point.regionUpdateTime ";
	private static String CATEGORY_SELECT = " category.id, category.name, category.description, category.url, category.active ";

	private final Context context;
	private PointsDBHelper helper;
	private SQLiteDatabase db;

	public PointsAccess(Context context) {
		this.context = context.getApplicationContext();
		helper = new PointsDBHelper(context, "points-of-interest");
		db = helper.getWritableDatabase();
	}

	public void close() {
		if (helper != null) {
			helper.close();
			helper = null;
		}
	}

	public void insertCategory(Category category) {
		ContentValues cv = new ContentValues();
		cv.put("name", category.getName());
		cv.put("description", category.getDescription());
		cv.put("url", category.getUrl());

		int updated = db.update("category", cv, "id=?", new String[] {String.valueOf(category.getId())});
		if (updated == 0) {
			cv.put("id", category.getId());
			db.insert("category", null, cv);
		}
	}

	public void insertCategories(List<Category> categories) {
		db.beginTransaction();
		try {
			for (Category category : categories) {
				insertCategory(category);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void insertPoint(Point point) {
		ContentValues values = new ContentValues();
		values.put("lat", point.toPoint().getLatitudeE6());
		values.put("lon", point.toPoint().getLongitudeE6());
		values.put("name", point.getName());
		values.put("categoryId", point.getCategory().getId());
		values.put("desc", point.getDescription());
		values.put("timestamp", System.currentTimeMillis());
		values.put("photosJson", Utils.serializeStringList(point.getPhotos()));

		String region4 = point.getRegionId(4);
		if (region4 != null)
			values.put("regionId4", region4);

		String region6 = point.getRegionId(6);
		if (region6 != null)
			values.put("regionId6", region6);

		long regionUpdateTime = point.getRegionUpdateTime();
		if (regionUpdateTime > 0)
			values.put("regionUpdateTime", regionUpdateTime);

		int updated = db.update("point", values, "uuid=?", new String[] { point.getUuid() });
		if (updated == 0) {
			values.put("uuid", point.getUuid());
			db.insert("point", null, values);

			for (String photoUrl : point.getPhotos()) {
				ContentValues photoCv = new ContentValues(2);
				photoCv.put("url", photoUrl);
			}
		}
	}

	public void insertPoints(List<Point> points) {
		db.beginTransaction();
		try {
			for (Point point : points) {
				insertPoint(point);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void setCategoryState(Category category, boolean isActive) {
		ContentValues cv = new ContentValues(1);
		cv.put("active", isActive);
		db.update("category", cv, "category.id=?", new String[]{"" + category.getId()});
	}

	public List<Point> loadActive() {
		Cursor cursor = db.rawQuery("SELECT " + POINT_SELECT + ',' + CATEGORY_SELECT +
				" FROM point JOIN category ON point.categoryId = category.id " +
				" WHERE category.active=1;", null);

		try {
			List<Point> points = new ArrayList<>(cursor.getCount());
			while (cursor.moveToNext()) {
				points.add(toPoint(cursor, 0));
			}
			points.size();
			return points;
		} finally {
			cursor.close();
		}
	}

	public List<Point> loadAll() {
		Cursor cursor = db.rawQuery("SELECT " + POINT_SELECT + ',' + CATEGORY_SELECT +
				" FROM point JOIN category ON point.categoryId = category.id;", null);

		try {
			List<Point> points = new ArrayList<>(cursor.getCount());
			while (cursor.moveToNext()) {
				points.add(toPoint(cursor, 0));
			}
			return points;
		} finally {
			cursor.close();
		}
	}

	public List<Point> loadByCategory(Category category) {
		Cursor cursor = db.rawQuery("SELECT " + POINT_SELECT + ',' + CATEGORY_SELECT +
				" FROM point JOIN category ON point.categoryId = category.id" +
				"WHERE category.id=?;", new String[] {"" + category.getId() });

		try {
			List<Point> points = new ArrayList<>(cursor.getCount());
			while (cursor.moveToNext()) {
				points.add(toPoint(cursor, 0));
			}
			return points;
		} finally {
			cursor.close();
		}
	}

	public List<Category> loadCategories() {
		Cursor cursor = db.rawQuery("SELECT " + CATEGORY_SELECT + " FROM category;", null);
		try {
			List<Category> categories = new ArrayList<>(cursor.getCount());
			while (cursor.moveToNext()) {
				categories.add(toCategory(cursor, 0));
			}
			return categories;
		} finally {
			cursor.close();
		}
	}

	public List<Category> loadActiveCategories() {
		Cursor cursor = db.rawQuery("SELECT " + CATEGORY_SELECT + " FROM category WHERE category.active=1;", null);
		try {
			List<Category> categories = new ArrayList<>(cursor.getCount());
			while (cursor.moveToNext()) {
				categories.add(toCategory(cursor, 0));
			}
			return categories;
		} finally {
			cursor.close();
		}
	}


	public void deleteOlderThan(long timestamp) {
		db.delete("point", "timestamp < ?", new String[] {String.valueOf(timestamp)});
	}

	public List<Point> loadPointsWithoutRegion(long sinceTime) {
		Cursor cursor = db.rawQuery("SELECT " + POINT_SELECT + ',' + CATEGORY_SELECT +
				" FROM point JOIN category ON point.categoryId = category.id " +
				" WHERE (point.regionId4 IS NULL OR point.regionId6 IS NULL)" +
						" AND point.regionUpdateTime <= ?;",
				new String[] {String.valueOf(sinceTime)});

		try {
			List<Point> points = new ArrayList<>(cursor.getCount());
			while (cursor.moveToNext()) {
				points.add(toPoint(cursor, 0));
			}
			points.size();
			return points;
		} finally {
			cursor.close();
		}

	}

	private Point toPoint(Cursor cursor, int offset) {
		return new Point(cursor.getString(offset), cursor.getInt(offset + 2), cursor.getInt(offset + 3))
				.setDescription(cursor.getString(offset + 1))
				.setCategory(toCategory(cursor, offset + 10))
				.setUuid(cursor.getString(offset + 4))
				.setPhotos(Utils.deserializeStringList(cursor.getString(offset + 5)))
				.setDbId(cursor.getLong(6))
				.setRegionId(cursor.getString(7), 4)
				.setRegionId(cursor.getString(8), 6)
				.setRegionUpdateTime(9);
	}

	private Category toCategory(Cursor cursor, int offset) {
		Category category = new Category(cursor.getLong(offset),
				cursor.getString(offset + 1),
				cursor.getString(offset + 2),
				cursor.getString(offset + 3));
		category.setActive(cursor.getInt(offset + 4) != 0);
		return category;
	}

	private class PointsDBHelper extends SQLiteOpenHelper {
		public PointsDBHelper(Context context, String dbName) {
			super(context, dbName, null, VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			onUpgrade(db, 0, VERSION);
		}

		@Override
		public void onOpen(SQLiteDatabase db) {
			super.onOpen(db);

			db.execSQL("PRAGMA foreign_keys = ON;");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int old, int ne) {
			switch (old) {
			case 0:
				db.execSQL("DROP TABLE IF EXISTS point;");
				db.execSQL("DROP TABLE IF EXISTS category;");

				db.execSQL("CREATE TABLE point (" +
						"id INTEGER PRIMARY KEY AUTOINCREMENT," +
						"uuid TEXT UNIQUE," +
						"lat INTEGER," +
						"lon INTEGER, " +
						"name TEXT NOT NULL," +
						"categoryId INTEGER," +
						"desc TEXT," +
						"timestamp INTEGER," +
						"photosJson TEXT," +
						"regionId4 TEXT," + // Currently there saved only region name, not id
						"regionId6 TEXT," + // Currently there saved only region name, not id
						"regionUpdateTime INTEGER DEFAULT 0," +

						"FOREIGN KEY (categoryId) REFERENCES category(id) ON DELETE CASCADE);");

				db.execSQL("CREATE TABLE category (" +
						"id INTEGER PRIMARY KEY," +
						"name TEXT," +
						"description TEXT," +
						"url TEXT," +
						"active INTEGER DEFAULT 1);");
				break;
			}
		}
	}
}
