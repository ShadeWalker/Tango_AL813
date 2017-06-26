/*
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

import android.app.AppOpsManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;
import android.provider.ChildMode;
 /**{@hide}*/ 
public class ChildModeProvider extends ContentProvider {
    private static final String TAG = "ChildModeProvider";
    private static final boolean LOCAL_LOGV = false;

    private static final String[] COLUMN_VALUE = new String[] { "value" };
    private static final int COMMON = 0;
    private static final int APP = 1;
    private static final int URL = 2;
    private static final UriMatcher sURLMatcher =
            new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURLMatcher.addURI(ChildMode.AUTHORITY,"common", COMMON);
        sURLMatcher.addURI(ChildMode.AUTHORITY, "app", APP);
        sURLMatcher.addURI(ChildMode.AUTHORITY, "url", URL);
    }

    @Override
    public boolean onCreate() {
        setAppOps(AppOpsManager.OP_NONE, AppOpsManager.OP_WRITE_SETTINGS);
        return true;
    }

    @Override
    public Cursor query(Uri url, String[] select, String where, String[] whereArgs, String sort) {
        int match = sURLMatcher.match(url);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (match) {
            case COMMON:
                qb.setTables("common");
                break;
            case APP:
                qb.setTables("app");
                break;
            case URL:
                qb.setTables("url");
                break;
        }

        DatabaseHelper dbH = DatabaseHelper.getInstance(getContext());
        SQLiteDatabase db = dbH.getReadableDatabase();
        Cursor ret = qb.query(db, select, where, whereArgs, null, null, sort);
        return ret;
    }

    @Override
    public Uri insert(Uri url, ContentValues initialValues) {

        int match = sURLMatcher.match(url);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String table = " ";
        switch (match) {
            case COMMON:
                return null;
            case APP:
                table = "app";
                break;
            case URL:
                table = "url";
                break;
        }

        DatabaseHelper dbH = DatabaseHelper.getInstance(getContext());
        SQLiteDatabase db = dbH.getReadableDatabase();
        long rowID = db.insert(table, null, initialValues);
        Uri ret;
        if (rowID > 0) {
            ret = Uri.parse("content://" + table + "/" + rowID);
            return ret;
        }else {
            Log.e(TAG,"insert: failed! " + initialValues.toString());
        }
        return null;
    
    }

    @Override
    public int delete(Uri url, String where, String[] whereArgs) {
        int match = sURLMatcher.match(url);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String table = " ";
        switch (match) {
        case COMMON:
            return 0;
        case APP:
            table = "app";
            break;
        case URL:
            table = "url";
            break;
        }
        DatabaseHelper dbH = DatabaseHelper.getInstance(getContext());
        SQLiteDatabase db = dbH.getReadableDatabase();
        int count = db.delete(table, where, whereArgs);
        return count;
    }

    @Override
    public int update(Uri url, ContentValues initialValues, String where, String[] whereArgs) {
        int match = sURLMatcher.match(url);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String table = " ";
        switch (match) {
        case COMMON:
            table = "common";
            break;
        case APP:
            table = "app";
            break;
        case URL:
            table = "url";
            break;
        }
        DatabaseHelper dbH = DatabaseHelper.getInstance(getContext());
        SQLiteDatabase db = dbH.getReadableDatabase();

        int count = db.update(table, initialValues, where, whereArgs);

        /*Modified By zhangjun to send notify(QL1701) SW00107342 2015-2-3*/
        if (count > 0) {
            getContext().getContentResolver().notifyChange(ChildMode.COMMON_CONTENT_URI, null);
        }
        return count;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }
}
