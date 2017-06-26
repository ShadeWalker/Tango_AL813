/*
 * Copyright (c) 2013 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.child.provider.ChildMode;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.media.AudioManager;
import android.media.AudioService;
import android.net.ConnectivityManager;
import android.os.Environment;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
//import android.telephony.MSimTelephonyManager;//delete by lihaizhou at 2015-07-15
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.provider.ChildMode;


import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;


/**
 * Database helper class for {@link ChildModeProvider}.
 * Mostly just has a bit {@link #onCreate} to initialize the database.
 */
 /**{@hide}*/ 
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "ChildModeProvider";
    private static final String DATABASE_NAME = "childmode.db";
    private static final int DATABASE_VERSION = 1;

    private Context mContext;
    private static DatabaseHelper sInstance = null;

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }
    static synchronized DatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DatabaseHelper(context);////modified by lihaizhou for clear database 
        }
        return sInstance;
    }

    private void createCommonTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE common (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT UNIQUE ON CONFLICT REPLACE," +
                "value TEXT" +
                ");");
    }

    private void createAPPTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE app (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "package_name TEXT UNIQUE ON CONFLICT REPLACE" +
                ");");
    }
    private void createUrlTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE url (" +
            "_id INTEGER PRIMARY KEY," +
            "name TEXT," +
            "url TEXT" +
            ");");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createCommonTable(db);
        createAPPTable(db);
        createUrlTable(db);
        loadChildMode(db);
    }
    /*Modified By zhangjun set default values(QL1701) SW00107342 2015-2-10*/
    private void loadChildMode(SQLiteDatabase db) {
        SQLiteStatement stmt = null;
        try {
            stmt = db.compileStatement("INSERT OR IGNORE INTO common(name,value)"
                    + " VALUES(?,?);");
            loadSetting(stmt, ChildMode.CHILD_MODE_ON,ChildMode.OFF);
            loadSetting(stmt, ChildMode.APP_BlACK_LIST_ON,ChildMode.OFF);
            loadSetting(stmt, ChildMode.URL_WHITE_LIST_ON,ChildMode.OFF);
            loadSetting(stmt, ChildMode.INTERNET_TIME_RESTRICTION_ON,ChildMode.OFF);
            loadSetting(stmt, ChildMode.INTERNET_TIME_RESTRICTION,"60");
            loadSetting(stmt, ChildMode.INTERNET_TRAFFIC_RESTRICTION_ON,ChildMode.OFF);
            loadSetting(stmt, ChildMode.INTERNET_TRAFFIC_RESTRICTION,"1");
            loadSetting(stmt, ChildMode.FORBID_SEND_MESSAGE_ON,ChildMode.ON);
            loadSetting(stmt, ChildMode.FORBID_CALL,ChildMode.ON);
            loadSetting(stmt, ChildMode.FORBID_WLAN,ChildMode.OFF);
            loadSetting(stmt, ChildMode.FORBID_DATA,ChildMode.ON);
            loadSetting(stmt, ChildMode.FORBID_DELETE_MESSAGE,ChildMode.ON);
            loadSetting(stmt, ChildMode.FORBID_INSTALL_APP,ChildMode.OFF);
            loadSetting(stmt, ChildMode.FORBID_UNINSTALL_APP,ChildMode.ON);
            //add by lihaizhou for disable MobileData for ChildMode by begin
            loadSetting(stmt, ChildMode.INTERNET_LIMIT_TRAFFIC_UP,"0");
            loadSetting(stmt, ChildMode.INTERNET_LIMIT_TIME_UP,"0");
            //add by lihaizhou for disable MobileData for ChildMode by end        
            loadSetting(stmt, ChildMode.FORBID_UNINSTALL_APP,ChildMode.ON);
            /*Modified By zhangjun add key-values for lack and internet(QL1701) SW00107342 2015-2-10*/
            loadSetting(stmt, "child_mode_lock_type", "-1");
            loadSetting(stmt, "child_mode_pin", "");
            loadSetting(stmt, "child_mode_pattern", "");
            loadSetting(stmt, "child_mode_password_tip", "");
            loadSetting(stmt, "internet_traffic_restriction_limit", "0");
            loadSetting(stmt, "internet_time_restriction_limit", "0");
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    private void loadSetting(SQLiteStatement stmt, String key, Object value) {
        stmt.bindString(1, key);
        stmt.bindString(2, value.toString());
        stmt.execute();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO Auto-generated method stub
    }
}
