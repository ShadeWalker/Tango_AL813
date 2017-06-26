package com.speeddial.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

class SpeedDialDatabaseHelper extends SQLiteOpenHelper{

    private final static String TAG = "SpeedDialDatabaseHelper";
    private static final int DATABASE_VERSION = 1;
    // / Modified by guofeiyao
    private static final String DATABASE_NAME = "hq_speeddial.db";
    // / End
    private static SpeedDialDatabaseHelper sMe;
    public interface Tables {
        static final String SPEEDDIAL = "speeddials";
    }


    public static synchronized SpeedDialDatabaseHelper getInstance(Context context) {
        if (sMe == null) {
            sMe = new SpeedDialDatabaseHelper(context, DATABASE_NAME, true);
        }
        return sMe;        
    }

    protected SpeedDialDatabaseHelper(Context context, String databaseName,
                                            boolean optimizationEnabled) {
        super(context, databaseName, null, DATABASE_VERSION);
    }

    @Override 
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + Tables.SPEEDDIAL + " (" +
            SpeedDial.Numbers._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            SpeedDial.Numbers.NUMBER + " TEXT" +
            ");");
        initSpeedDialTable(db);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    private void initSpeedDialTable(SQLiteDatabase db) {
        for (int i = 0; i < 10; i++) {
            db.execSQL("INSERT INTO " + Tables.SPEEDDIAL + " (" +
                SpeedDial.Numbers.NUMBER + ") " +
                "VALUES('" + "" + "'" +
                ");");
        }
    }
    
}

