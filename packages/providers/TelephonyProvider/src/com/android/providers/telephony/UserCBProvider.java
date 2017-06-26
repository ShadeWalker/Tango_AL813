/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.providers.telephony;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import com.mediatek.xlog.Xlog;

import java.util.HashMap;


public class UserCBProvider extends ContentProvider {
    private static final Uri NOTIFICATION_URI = Uri.parse("content://usercb");
    private static final String TABLE_USERCB = "usercb";
    private SQLiteOpenHelper mOpenHelper;

    private final static String TAG = "UserCBProvider";

    private static final HashMap<String, String> sConversationProjectionMap =
            new HashMap<String, String>();
    private static final String[] sIDProjection = new String[] { "_id" };

    private static final int SMS_ALL = 0;

    private static final UriMatcher URI_MATCHER =
            new UriMatcher(UriMatcher.NO_MATCH);

    static {
        URI_MATCHER.addURI("usercb", null, SMS_ALL);
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = MmsSmsDatabaseHelper.getInstance(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri url, String[] projectionIn, String selection,
            String[] selectionArgs, String sort) {
        Xlog.d(TAG, "query begin uri = " + url);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        // Generate the body of the query.
        int match = URI_MATCHER.match(url);
        switch (match) {
            case SMS_ALL:
                qb.setTables(TABLE_USERCB);
                break;
        }

        String orderBy = null;

        if (!TextUtils.isEmpty(sort)) {
            orderBy = sort;
        } else if (qb.getTables().equals(TABLE_USERCB)) {
            orderBy = "_id ASC";
        }

        Xlog.d(TAG, "query getReadbleDatabase");
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Xlog.d(TAG, "query getReadbleDatabase qb.query begin");
        Cursor ret = qb.query(db, projectionIn, selection, selectionArgs,
                              null, null, orderBy);
        Xlog.d(TAG, "query getReadbleDatabase qb.query end");
        if (ret != null) {
            ret.setNotificationUri(getContext().getContentResolver(),
                    NOTIFICATION_URI);
        }
        return ret;
    }

    @Override
    public Uri insert(Uri url, ContentValues initialValues) {
        Xlog.d(TAG, "insert begin");
        ContentValues values;
        int match = URI_MATCHER.match(url);
        String table = TABLE_USERCB;
        long rowID;

        switch (match) {
            case SMS_ALL:
                break;
            default:
                break;
        }
        Xlog.d(TAG, "insert match url end");

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Xlog.d(TAG, "insert mOpenHelper.getWritableDatabase end");

        if (initialValues == null) {
            return null;
        }

        values = new ContentValues(initialValues);
        rowID = db.insert(table, "usercb-pdus", values);
        Xlog.d(TAG, "insert table body end");

        if (rowID > 0) {
            Uri uri = Uri.parse("content://" + table + "/" + rowID);

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.d(TAG, "insert " + uri + " succeeded");
            }
            return uri;
        } else {
            Log.e(TAG, "insert: failed! " + values.toString());
        }

        Xlog.d(TAG, "insert end");
        return null;
    }

    @Override
    public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
        Xlog.d(TAG, "update begin");
        int count = 0;
        String table = TABLE_USERCB;
        String extraWhere = null;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        switch (URI_MATCHER.match(url)) {
            case SMS_ALL:
                break;
            default:
                throw new UnsupportedOperationException(
                        "URI " + url + " not supported");
        }

        where = DatabaseUtils.concatenateWhere(where, extraWhere);
        count = db.update(table, values, where, whereArgs);

        if (count > 0) {
            Log.d(TAG, "update " + url + " succeeded");
        }
        Xlog.d(TAG, "update end");
        return count;
    }

    @Override
    public int delete(Uri url, String where, String[] whereArgs) {
        int deletedRows = 0;
        Uri deleteUri = null;
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        deleteOnce(url, where, whereArgs);
        return deletedRows;
    }

    @Override
    public String getType(Uri url) {
        return null;
    }

    private int deleteOnce(Uri url, String where, String[] whereArgs) {
        int count = 0;
        int match = URI_MATCHER.match(url);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Log.d(TAG, "Delete deleteOnce: " + match);
        switch (match) {
            case SMS_ALL:
                count = db.delete(TABLE_USERCB, where, whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URL");
        }
        return count;
    }
}
