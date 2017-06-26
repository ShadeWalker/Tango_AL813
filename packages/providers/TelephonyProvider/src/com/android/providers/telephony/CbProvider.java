/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.android.providers.telephony;

import java.util.List;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Binder;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.mms.util.SqliteWrapper;
import android.provider.Telephony.MmsSms;
import com.mediatek.xlog.Xlog;

// Added by lei.wang@archermind.com
public class CbProvider extends ContentProvider {
    private static final String TAG = "Mms/Provider/Cb";
    private static final Boolean DEBUG = true;

    private static final String CONVERSATION_ORDER = "date ASC";

    private static final int URL_CHANNEL = 1;
    private static final int URL_MESSAGES = 2;
    private static final int URL_CONVERSATION = 3;
    private static final int URL_ADDRESS = 4;
    private static final int URL_THREAD_ID = 5;
    private static final int URL_CONVERSATIONS_MESSAGES = 6;
    private static final int URL_CHANNEL_ID = 7;
    private static final int URL_ADDRESS_ID = 8;
    private static final int URL_CONVERSATION_ID = 9;
    private static final int URL_CBRAW_MESSAGE = 10;

    private static final Uri THREAD_ID_URI = Uri
            .parse("content://cb/thread_id");
    private static String[] ID_PROJECTION = { BaseColumns._ID };
    private static final String FOR_MULTIDELETE = "ForMultiDelete";
    private static final UriMatcher URI_MATCHER = new UriMatcher(
            UriMatcher.NO_MATCH);

    static {
        URI_MATCHER.addURI("cb", "channel", URL_CHANNEL);
        URI_MATCHER.addURI("cb", "messages", URL_MESSAGES);
        URI_MATCHER.addURI("cb", "threads", URL_CONVERSATION);
        URI_MATCHER.addURI("cb", "addresses", URL_ADDRESS);
        URI_MATCHER.addURI("cb", "thread_id", URL_THREAD_ID);
        URI_MATCHER.addURI("cb", "messages/#", URL_CONVERSATIONS_MESSAGES);
        URI_MATCHER.addURI("cb", "channel/#", URL_CHANNEL_ID);
        URI_MATCHER.addURI("cb", "addresses/#", URL_ADDRESS_ID);
        URI_MATCHER.addURI("cb", "threads/#", URL_CONVERSATION_ID);
        URI_MATCHER.addURI("cb", "cbraw", URL_CBRAW_MESSAGE);
    }

    private SQLiteOpenHelper mOpenHelper;
    private SQLiteOpenHelper mMmsSmsOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new CbDatabaseHelper(getContext());
        mMmsSmsOpenHelper = MmsSmsDatabaseHelper.getInstance(getContext());
        return true;
    }

    private void notifyChange() {
        Log.i(TAG, "Notify change");
        ContentResolver cr = getContext().getContentResolver();
        cr.notifyChange(Telephony.SmsCb.CONTENT_URI, null);
        cr.notifyChange(Telephony.SmsCb.Conversations.CONTENT_URI, null);
        cr.notifyChange(MmsSms.CONTENT_URI, null);
        MmsSmsProvider.notifyUnreadMessageNumberChanged(getContext());
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        SQLiteDatabase dbmmssms = mMmsSmsOpenHelper.getReadableDatabase();
        int match = URI_MATCHER.match(url);
        Xlog.d(TAG, " query match " + match);
        switch (match) {
        case URL_MESSAGES:
            //qb.setTables(CbDatabaseHelper.CBMESSAGE_TABLE);
            qb.setTables(MmsSmsDatabaseHelper.TABLE_CELLBROADCAST);

            Cursor ret = qb.query(dbmmssms, projection, selection, selectionArgs, null,
                    null, sortOrder);
            if (ret != null)
                ret.setNotificationUri(getContext().getContentResolver(), Telephony.SmsCb.CONTENT_URI);
            return ret;
            //break;
        case URL_CHANNEL:
            qb.setTables(CbDatabaseHelper.CHANNEL_TABLE);
            //add by lipeng for Cellbroadcast
            if (selection == null || !selection.contains("sub_id")) {
            qb.appendWhere("(sub_id = " + SmsProvider.getSubIdFromUri(url) + ")");
            }//end by lipeng
            break;
        case URL_CONVERSATION:
            //qb.setTables(CbDatabaseHelper.CONVERSATION_TABLE);
            qb.setTables("threads");

            Cursor retmmssms = qb.query(dbmmssms, projection, selection, selectionArgs, null,
                    null, sortOrder);
            Log.i(TAG, "query conversation from threads");
            if (retmmssms != null)
                    retmmssms.setNotificationUri(getContext().getContentResolver(),
                           Telephony.SmsCb.CONTENT_URI);
            return retmmssms;
            //break;
        case URL_ADDRESS:
            qb.setTables(CbDatabaseHelper.ADDRESS_TABLE);
            break;
        case URL_THREAD_ID:
            List<String> recipients = url.getQueryParameters("recipient");
            if (recipients == null || recipients.size() == 0)
                return null;
            return getThreadId(recipients);
        case URL_CONVERSATIONS_MESSAGES:
            //qb.setTables(CbDatabaseHelper.CBMESSAGE_TABLE);
            qb.setTables(MmsSmsDatabaseHelper.TABLE_CELLBROADCAST);
            qb.appendWhere("(thread_id = " + url.getPathSegments().get(1)
                            + ")");
            sortOrder = buildConversationOrder(sortOrder);
            Cursor retmmssmsconv = qb.query(dbmmssms, projection, selection, selectionArgs, null,
                    null, sortOrder);
            if (retmmssmsconv != null)
                  retmmssmsconv.setNotificationUri(getContext().getContentResolver(),
                           Telephony.SmsCb.CONTENT_URI);
            return retmmssmsconv;
            //break;
        case URL_CHANNEL_ID:
            qb.setTables(CbDatabaseHelper.CHANNEL_TABLE);
            qb.appendWhere("(_id = " + url.getPathSegments().get(1) + ")");
            break;
        case URL_ADDRESS_ID:
            qb.setTables(CbDatabaseHelper.ADDRESS_TABLE);
            qb.appendWhere("(_id = " + url.getPathSegments().get(1) + ")");
            break;
        case URL_CONVERSATION_ID:
            qb.setTables(CbDatabaseHelper.CHANNEL_TABLE);
            qb.appendWhere("(_id = " + url.getPathSegments().get(1) + ")");
            break;
        case URL_CBRAW_MESSAGE:
            qb.setTables("cbraw");
            break;
        default:
            return null;
        }
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor ret = qb.query(db, projection, selection, selectionArgs, null,
                null, sortOrder);
        if (ret != null)
            ret.setNotificationUri(getContext().getContentResolver(), Telephony.SmsCb.CONTENT_URI);
        return ret;
    }

    private String buildConversationOrder(String sortOrder) {
        if (sortOrder == null) {
            return CONVERSATION_ORDER;
        }
        return sortOrder;
    }

    private Cursor getThreadMessages(String threadIdString,
            String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        try {
            Long.parseLong(threadIdString);
        } catch (NumberFormatException exception) {
            Log.e(TAG, "Thread ID must be a Long.");
            return null;
        }
        SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();
        //unionQueryBuilder.setTables(CbDatabaseHelper.CBMESSAGE_TABLE);
        unionQueryBuilder.setTables(MmsSmsDatabaseHelper.TABLE_CELLBROADCAST);
        String finalSelection = concatSelections(selection, "thread_id = "
                + threadIdString);
        String unionQuery = unionQueryBuilder.buildQuery(projection,
                finalSelection, selectionArgs, null, null, sortOrder, null);
        //return mOpenHelper.getReadableDatabase().rawQuery(unionQuery,
        //        new String[0]);
        return mMmsSmsOpenHelper.getReadableDatabase().rawQuery(unionQuery,
                new String[0]);
    }

    private static String concatSelections(String selection1, String selection2) {
        if (TextUtils.isEmpty(selection1)) {
            return selection2;
        } else if (TextUtils.isEmpty(selection2)) {
            return selection1;
        } else {
            return selection1 + " AND " + selection2;
        }
    }

    private Cursor getThreadId(List<String> recipients) {
        // if donot has the channel id, need create one
        // only get the first string.
        long id = getAddressId(recipients.get(0));
        String str_id = String.valueOf(id);
        //String THREAD_QUERY = "SELECT _id FROM threads "
        //        + "WHERE address_id = ?";
        String THREAD_QUERY = "SELECT _id FROM threads " + "WHERE type = "
                + Telephony.Threads.CELL_BROADCAST_THREAD + " AND recipient_ids = ?";
        Log.i(TAG, "getThreadId THREAD_QUERY: " + THREAD_QUERY
                + ", address_id=" + id);
        if (DEBUG) {
            Log.i(TAG, "getThreadId THREAD_QUERY: " + THREAD_QUERY
                    + ", address_id=" + id);
        }
        //SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor cursor = mMmsSmsOpenHelper.getReadableDatabase().rawQuery(THREAD_QUERY, new String[] { str_id });

        if (cursor.getCount() == 0) {
            cursor.close();
            if (DEBUG) {
                Log.i(TAG, "getThreadId cursor zero, creating new threadid");
            }
            // only insert 1
            insertThread(str_id, 1);
            //db = mOpenHelper.getReadableDatabase(); // In case insertThread
            // closed it
            //cursor = db.rawQuery(THREAD_QUERY, new String[] { str_id });
            cursor = mMmsSmsOpenHelper.getReadableDatabase().rawQuery(THREAD_QUERY, new String[] { str_id });
        }
        if (DEBUG) {
            Log.i(TAG, "getThreadId cursor count: " + cursor.getCount());
        }
        return cursor;
    }

    /**
     * Insert a record for a new thread.
     */
    private void insertThread(String recipientIds, int numberOfRecipients) {
        ContentValues values = new ContentValues(4);

        long date = System.currentTimeMillis();
        values.put(Telephony.SmsCb.DATE, date - date % 1000);
        //values.put(Telephony.SmsCb.Conversations.ADDRESS_ID, recipientIds);
        values.put("recipient_ids", recipientIds);
        values.put(Telephony.SmsCb.Conversations.MESSAGE_COUNT, 0);
        values.put("type", Telephony.Threads.CELL_BROADCAST_THREAD);

        //mOpenHelper.getWritableDatabase().insert(
        //        CbDatabaseHelper.CONVERSATION_TABLE, null, values);
        if (DEBUG) {
            Log.i(TAG, "insertThread");
        }
        mMmsSmsOpenHelper.getWritableDatabase().insert(
                  "threads", null, values);
        Xlog.d(TAG, "Notify change insertThread");
        notifyChange();
    }

    private void setThreadStatus(ContentValues values) {
        Long threadId = values.getAsLong(Telephony.SmsCb.THREAD_ID);
        ContentValues statusValues = new ContentValues();
        statusValues.put(Telephony.Threads.STATUS, 0);
        mMmsSmsOpenHelper.getWritableDatabase().update("threads", statusValues, "_id=" + threadId, null);
    }

    private long getAddressId(String address) {
        String selection = "address=?";
        String[] selectionArgs = { address };
        Cursor cursor = null;
        try {
            SQLiteDatabase db = mOpenHelper.getReadableDatabase();
            cursor = db.query(CbDatabaseHelper.ADDRESS_TABLE, ID_PROJECTION,
                    selection, selectionArgs, null, null, null);

            if (cursor.getCount() == 0) {
                ContentValues contentValues = new ContentValues(1);
                contentValues.put(
                        Telephony.SmsCb.CanonicalAddressesColumns.ADDRESS,
                        address);

                db = mOpenHelper.getWritableDatabase();
                return db.insert(CbDatabaseHelper.ADDRESS_TABLE,
                        Telephony.SmsCb.CanonicalAddressesColumns.ADDRESS,
                        contentValues);
            }

            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor
                        .getColumnIndexOrThrow(BaseColumns._ID));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    private String getOrCreateThreadId(String address) {

        //Uri.Builder uriBuilder = THREAD_ID_URI.buildUpon();
        Uri.Builder uriBuilder = (Uri.parse("content://mms-sms/threadID")).buildUpon();
        uriBuilder.appendQueryParameter("recipient", address);
        uriBuilder.appendQueryParameter("cellbroadcast", address);
        Uri uri = uriBuilder.build();
        // TODO need replace with helper interface.
        Cursor cursor = null;
        long token = Binder.clearCallingIdentity();
        try {
            cursor = SqliteWrapper.query(this.getContext(), this
                    .getContext().getContentResolver(), uri, ID_PROJECTION, null,
                   null, null);
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return String.valueOf(cursor.getLong(0));
                } else {
                    Log.e(TAG, "getOrCreateThreadId returned no rows!");
                }
            } finally {
                cursor.close();
            }
        }
        Log.d(TAG, "getOrCreateThreadId failed with address " + address);
        throw new IllegalArgumentException(
                "Unable to find or allocate a thread ID.");
    }

    private ContentValues internalInsertMessages(ContentValues initialValues) {
        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }
        Long threadId = values.getAsLong(Telephony.SmsCb.THREAD_ID);
        String address = values.getAsString(Telephony.SmsCb.CHANNEL_ID);
        if (((threadId == null) || (threadId == 0)) && (address != null)) {
            values.put(Telephony.SmsCb.THREAD_ID,
                    getOrCreateThreadId(address));
        }

        if (values.containsKey(Telephony.SmsCb.SUBSCRIPTION_ID) == false) {
            values.put(Telephony.SmsCb.SUBSCRIPTION_ID, -1);
        }
        if (values.containsKey(Telephony.SmsCb.BODY) == false) {
            values.put(Telephony.SmsCb.BODY, "");
        }
        if (values.containsKey(Telephony.SmsCb.CHANNEL_ID) == false) {
            values.put(Telephony.SmsCb.CHANNEL_ID, -1);
        }
        if (values.containsKey(Telephony.SmsCb.READ) == false) {
            values.put(Telephony.SmsCb.READ, 0);
        }
        if (values.containsKey(Telephony.SmsCb.DATE) == false) {
            values.put(Telephony.SmsCb.DATE, 0);
        }
        if (values.containsKey(Telephony.SmsCb.THREAD_ID) == false) {
            values.put(Telephony.SmsCb.THREAD_ID, 0);
        }
        return values;
    }

    @Override
    public Uri insert(Uri url, ContentValues initialValues) {
        Uri result = null;
        // TODO Check Permission
        // checkPermission();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        SQLiteDatabase dbmmssms = mMmsSmsOpenHelper.getWritableDatabase();
        int match = URI_MATCHER.match(url);
        ContentValues values;
        long rowID;
        String table = null;
        Log.d(TAG, " insert match = " + match);
        switch (match) {
        case URL_MESSAGES:
            //table = CbDatabaseHelper.CBMESSAGE_TABLE;
            table = MmsSmsDatabaseHelper.TABLE_CELLBROADCAST;

            values = internalInsertMessages(initialValues);
            rowID = dbmmssms.insert(table, null, values);
            Log.d(TAG, "insert to cellbroadcast " + values);
            if (rowID > 0) {
                result = Uri.parse("content://messages/" + rowID);
                notifyChange();
                return result;
            }
            break;
        case URL_CHANNEL:
            table = CbDatabaseHelper.CHANNEL_TABLE;
            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }
            if (values.containsKey(Telephony.SmsCb.CbChannel.NAME) == false) {
                values.put(Telephony.SmsCb.CbChannel.NAME, "");
            }
            if (values.containsKey(Telephony.SmsCb.CbChannel.NUMBER) == false) {
                values.put(Telephony.SmsCb.CbChannel.NUMBER, "");
            }
            if (values.containsKey(Telephony.SmsCb.CbChannel.ENABLE) == false) {
                values.put(Telephony.SmsCb.CbChannel.ENABLE, false);
            }
            if (values.containsKey("sub_id") == false) {
                values.put("sub_id", -1);
            }
            rowID = db.insert(table, null, values);
            if (rowID > 0) {
                Uri uri = Uri.parse("content://channel/" + rowID);
                notifyChange();
                return uri;
            }
            break;

        case URL_CONVERSATION:
            //table = CbDatabaseHelper.CONVERSATION_TABLE;
            table = "threads";
            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }
            if (values.containsKey(Telephony.SmsCb.Conversations.SNIPPET) == false) {
                values.put(Telephony.SmsCb.Conversations.SNIPPET, "");
            }
            if (values.containsKey(Telephony.SmsCb.CHANNEL_ID) == false) {
                values.put(Telephony.SmsCb.CHANNEL_ID, -1);
            }
            if (values.containsKey(Telephony.SmsCb.DATE) == false) {
                values.put(Telephony.SmsCb.DATE, 0);
            }
            if (values.containsKey(Telephony.SmsCb.Conversations.ADDRESS_ID) == false) {
                values.put(Telephony.SmsCb.Conversations.ADDRESS_ID, -1);
            }
            if (values
                    .containsKey(Telephony.SmsCb.Conversations.MESSAGE_COUNT) == false) {
                values.put(Telephony.SmsCb.Conversations.MESSAGE_COUNT, 0);
            }

            values.put("type", Telephony.Threads.CELL_BROADCAST_THREAD);
            //rowID = db.insert(table, null, values);
            rowID = dbmmssms.insert(table, null, values);
            Log.d(TAG, "insert conversation to " + table);
            if (rowID > 0) {
                Uri uri = Uri.parse("content://threads/" + rowID);
                notifyChange();
                return uri;
            }
            break;
        case URL_ADDRESS:
            table = CbDatabaseHelper.ADDRESS_TABLE;
            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }
            if (values
                    .containsKey(Telephony.SmsCb.CanonicalAddressesColumns.ADDRESS) == false) {
                values.put(Telephony.SmsCb.CanonicalAddressesColumns.ADDRESS,
                        -1);
            }
            rowID = db.insert(table, null, values);
            if (rowID > 0) {
                Uri uri = Uri.parse("content://addresses/" + rowID);
                notifyChange();
                return uri;
            }
            break;
        case URL_CBRAW_MESSAGE:

            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }
            rowID = db.insert("cbraw", null, values);

            if (rowID > 0) {
                Uri uri = Uri.parse("content://cbraw/" + rowID);

                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.d(TAG, "insert " + uri + " succeeded");
                }
                return uri;
            } else {
                Log.e(TAG, "insert: failed! " + values.toString());
            }
            break;

        default:
            return null;
        }
        return result;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where,
            String[] whereArgs) {
        int count = 0;
        // TODO check permission.
        // checkPermission();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        SQLiteDatabase dbmmssms = mMmsSmsOpenHelper.getWritableDatabase();
        String extraWhere = null;
        int match = URI_MATCHER.match(uri);
        switch (match) {
        case URL_MESSAGES:

            //count = db.update(CbDatabaseHelper.CBMESSAGE_TABLE, values, where,
            //        whereArgs);
            count = dbmmssms.update(MmsSmsDatabaseHelper.TABLE_CELLBROADCAST, values, where,
                            whereArgs);
            break;
        case URL_CHANNEL:
            count = db.update(CbDatabaseHelper.CHANNEL_TABLE, values, where,
                    whereArgs);
            break;
        case URL_CONVERSATION:
            //count = db.update(CbDatabaseHelper.CONVERSATION_TABLE, values,
            //        where, whereArgs);
            count = dbmmssms.update("threads", values, where,
                    whereArgs);
            break;
        case URL_ADDRESS:
            count = db.update(CbDatabaseHelper.ADDRESS_TABLE, values, where,
                    whereArgs);
            break;
        case URL_CONVERSATION_ID:
            String threadId = uri.getPathSegments().get(1);

            try {
                Integer.parseInt(threadId);
            } catch (Exception ex) {
                Log.e(TAG, "Bad conversation thread id: " + threadId);
                break;
            }
            extraWhere = "thread_id=" + threadId;
            where = DatabaseUtils.concatenateWhere(where, extraWhere);
            //count = db.update(CbDatabaseHelper.CBMESSAGE_TABLE, values, where,
            //        whereArgs);
            count = dbmmssms.update(MmsSmsDatabaseHelper.TABLE_CELLBROADCAST, values, where,
                    whereArgs);

            break;

        default:
            throw new UnsupportedOperationException("Cannot update that URL: "
                    + uri);
        }
        Xlog.d(TAG, "Notify change update");
        notifyChange();
        return count;
    }

    @Override
    public int delete(Uri url, String where, String[] whereArgs) {
        int deletedRows = 0;
        Uri deleteUri = null;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        if (where != null) {
            if (where.equals(FOR_MULTIDELETE)) {
                db.beginTransaction();
                int message_id = 0;
                deletedRows = 0;
                for (int i = 0; i < whereArgs.length; i++) {
                    deleteUri = null;
                    if (whereArgs[i] == null) {
                        //return deletedRows;
                    } else {
                        message_id = Integer.parseInt(whereArgs[i]);
                        deleteUri = ContentUris.withAppendedId(url, message_id);
                        Log.i(TAG, "message_id is " + message_id);
                        deletedRows += deleteOnce(deleteUri, null, null);
                    }
                }
                db.setTransactionSuccessful();
                db.endTransaction();
            } else {
                deletedRows = deleteOnce(url, where, whereArgs);
            }
        } else {
            deletedRows = deleteOnce(url, where, whereArgs);
        }
        //if (deletedRows > 0) {
        Xlog.d(TAG, "Notify change delete");
        notifyChange();
        //}
        return deletedRows;
    }
    public int deleteOnce(Uri uri, String where, String[] whereArgs) {
        int count = 0;
        // TODO check permission.
        // checkPermission();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        SQLiteDatabase dbmmssms = mMmsSmsOpenHelper.getWritableDatabase();
        int match = URI_MATCHER.match(uri);
        switch (match) {
        case URL_MESSAGES:

            //count = db.delete(CbDatabaseHelper.CBMESSAGE_TABLE, where,
            //        whereArgs);
            //if(count > 0) {
            //    CbDatabaseHelper.updateThread(db, -1);
            //}
            count = dbmmssms.delete(MmsSmsDatabaseHelper.TABLE_CELLBROADCAST, where,
                            whereArgs);
              if (count > 0) {
                  MmsSmsDatabaseHelper.updateThread(dbmmssms, -1);
            }
            break;
        case URL_CHANNEL:
            count = db.delete(CbDatabaseHelper.CHANNEL_TABLE, where, whereArgs);
            break;
        case URL_CONVERSATION:
            //count = db.delete(CbDatabaseHelper.CBMESSAGE_TABLE, where,
            //        whereArgs);
            //CbDatabaseHelper.updateThread(db, -1);
            count = dbmmssms.delete(MmsSmsDatabaseHelper.TABLE_CELLBROADCAST, where,
                    whereArgs);
            //MmsSmsDatabaseHelper.updateThread(db, -1);
            break;
        case URL_ADDRESS:
            count = db.delete(CbDatabaseHelper.ADDRESS_TABLE, where, whereArgs);
            break;
        case URL_THREAD_ID:
            // int threadID;
            //
            // try {
            // threadID = Integer.parseInt(uri.getPathSegments().get(1));
            // } catch (Exception ex) {
            // throw new IllegalArgumentException(
            // "Bad conversation thread id: "
            // + uri.getPathSegments().get(1));
            // }
            //
            // // delete the messages from the sms table
            // where = DatabaseUtils.concatenateWhere("thread_id=" + threadID,
            // where);
            // count = db.delete(CbDatabaseHelper.CBMESSAGE_TABLE, where,
            // whereArgs);
            // // TODO need add updateThread in CbDatabaseHelper.java
            // // updateThread(db, threadID);
            break;
        case URL_CONVERSATION_ID:
            int threadID;
            try {
                threadID = Integer.parseInt(uri.getPathSegments().get(1));
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        "Bad conversation thread id: "
                                + uri.getPathSegments().get(1));
            }

            // delete the messages from the sms table
            where = DatabaseUtils.concatenateWhere("thread_id=" + threadID,
                    where);
            //count = db.delete(CbDatabaseHelper.CBMESSAGE_TABLE, where,
            //        whereArgs);
            // TODO need add updateThread in CbDatabaseHelper.java
            //CbDatabaseHelper.updateThread(db, threadID);
            count = dbmmssms.delete(MmsSmsDatabaseHelper.TABLE_CELLBROADCAST, where,
                    whereArgs);
            MmsSmsDatabaseHelper.updateThread(db, threadID);
            break;
        case URL_CONVERSATIONS_MESSAGES:
            int messageId;
            try {
                messageId = Integer.parseInt(uri.getPathSegments().get(1));
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        "Bad conversation thread id: "
                                + uri.getPathSegments().get(1));
            }

            // delete the messages from the messages table
            where = DatabaseUtils.concatenateWhere("_id=" + messageId, where);
            //count = db.delete(CbDatabaseHelper.CBMESSAGE_TABLE, where,
            //        whereArgs);
            // TODO need add updateThread in CbDatabaseHelper.java
            //CbDatabaseHelper.updateThread(db, -1);
            count = dbmmssms.delete(MmsSmsDatabaseHelper.TABLE_CELLBROADCAST, where,
                    whereArgs);
            //MmsSmsDatabaseHelper.updateThread(db, -1);
            break;
        case URL_CBRAW_MESSAGE:
            count = db.delete("cbraw", where, whereArgs);
            break;
        default:
            throw new UnsupportedOperationException("Cannot delete that URL: "
                    + uri);
        }
        //notifyChange();
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
        case URL_MESSAGES:
            return "vnd.android.cursor.dir/cb-messages";
        case URL_CHANNEL:
            return "vnd.android.cursor.item/cb-channel";
        case URL_CONVERSATION:
            return "vnd.android.cursor.dir/cb-conversation";
        case URL_ADDRESS:
            return "vnd.android.cursor.item/cb-address";
        default:
            throw new IllegalArgumentException("Unknown URL " + uri);
        }
    }

}
