package org.fruct.oss.ikm.db;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class RoadOpenHelper extends SQLiteOpenHelper {
    private static String DB_NAME = "test.db";
    private static String DB_FOLDER;
    private static String DB_PATH;
    private static String DB_ASSETS_PATH;
    private static String LOG_TAG = "RoadOpenHelper";

    private static Context appContext;
    
	public RoadOpenHelper(Context context, CursorFactory factory,
			int version) {
		super(context, DB_PATH, factory, version);
	}
	
    private static boolean isInitialized() {
        SQLiteDatabase checkDB = null;

        try {
            checkDB = SQLiteDatabase.openDatabase(DB_PATH, null, SQLiteDatabase.OPEN_READONLY);
        } catch (SQLiteException e) {
        } finally {
            if (checkDB != null)
                checkDB.close();
        }
        return checkDB != null;
    }
    
    public static void initialize(Context appContext) {
		DB_FOLDER = "/data/data/" + appContext.getPackageName() + "/databases/";
		DB_PATH = DB_FOLDER + DB_NAME;
		DB_ASSETS_PATH = DB_NAME;
		
		Log.d(LOG_TAG, "DB_PATH=" + DB_PATH);
    	
    	RoadOpenHelper.appContext = appContext;
    	if (!isInitialized())
    		copyInialDBfromAssets();
    }
	
    private static void copyInialDBfromAssets() {
    	Log.d(LOG_TAG, "Initializing");
    	
        InputStream inStream = null;
        OutputStream outStream = null;
        try {
            inStream = new BufferedInputStream(appContext.getAssets().open(
                    DB_ASSETS_PATH), 4096);
            File dbDir = new File(DB_FOLDER);
            if (dbDir.exists() == false)
                dbDir.mkdir();
            outStream = new BufferedOutputStream(new FileOutputStream(DB_PATH), 4096);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }
            outStream.flush();
            outStream.close();
            inStream.close();
        } catch (IOException ex) {
        	Log.w("RoadOpenHelper", "Can't open database");
        }
    }
    
	@Override
	public void onCreate(SQLiteDatabase arg0) {
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}
}
