/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2009 The Android Open Source Project
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
 * limitations under the License
 */

package com.mediatek.providers.contacts;

import static com.android.providers.contacts.util.DbQueryUtils.checkForSupportedColumns;
import android.content.Context;
import static com.android.providers.contacts.util.DbQueryUtils.getEqualityClause;
import static com.android.providers.contacts.util.DbQueryUtils.getInequalityClause;
import android.app.AppOpsManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Process;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.CallLog.ConferenceCalls;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.ImsCall;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.app.SearchManager;

import com.android.providers.contacts.Constants;
import com.android.providers.contacts.ContactsDatabaseHelper;
import com.android.providers.contacts.DatabaseModifier;
import com.android.providers.contacts.DbModifierWithNotification;
import com.android.providers.contacts.NameNormalizer;
import com.android.providers.contacts.VoicemailPermissions;
import com.android.providers.contacts.ContactsDatabaseHelper.DbProperties;
import com.android.providers.contacts.ContactsDatabaseHelper.Tables;
import com.android.providers.contacts.util.SelectionBuilder;
import com.android.providers.contacts.util.UserUtils;
import com.android.providers.contacts.ContactsDatabaseHelper.Views;
import com.android.providers.contacts.ContactsDatabaseHelper.PhoneLookupColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.SearchIndexColumns;
import com.mediatek.providers.contacts.DialerSearchSupport.DialerSearchLookupColumns;
import com.mediatek.providers.contacts.DialerSearchSupport.DialerSearchLookupType;
import com.mediatek.providers.contacts.LogUtils;
import com.mediatek.providers.contacts.ContactsProviderUtils;
import com.mediatek.providers.contacts.CallLogSearchSupport;
import com.android.providers.contacts.CallLogProvider;
import com.mediatek.providers.contacts.DialerSearchUtils;
import com.mediatek.providers.contacts.ConstantsUtils;
import com.mediatek.common.mom.SubPermissions;
import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.List;
import com.android.providers.contacts.ContactsProvider2;

/**
 * Call log content provider.
 */
public class CallLogProviderEx {
    private static final String TAG = CallLogProviderEx.class.getSimpleName();

    private final Context mContext;
    public CallLogProviderEx(Context context) {
        mContext = context;
    }
    private ContactsDatabaseHelper mDbHelper;
    private DatabaseUtils.InsertHelper mCallsInserter;
    private static final int CALLS_SEARCH_FILTER = 4;
    private static final int CALLS_JION_DATA_VIEW = 5;
    private static final int CALLS_JION_DATA_VIEW_ID = 6;
    private static final int CONFERENCE_CALLS = 7;
    private static final int CONFERENCE_CALLS_ID = 8;
    private static final int SEARCH_SUGGESTIONS = 10001;
    private static final int SEARCH_SHORTCUT = 10002;
    private CallLogSearchSupport mCallLogSearchSupport;
    private CallLogProvider mCallLogProvider;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {

        sURIMatcher.addURI(CallLog.AUTHORITY, "calls/search_filter/*", CALLS_SEARCH_FILTER);
        sURIMatcher.addURI(CallLog.AUTHORITY, "callsjoindataview", CALLS_JION_DATA_VIEW);
        sURIMatcher.addURI(CallLog.AUTHORITY, "callsjoindataview/#", CALLS_JION_DATA_VIEW_ID);
        sURIMatcher.addURI(CallLog.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGESTIONS);
        sURIMatcher.addURI(CallLog.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGESTIONS);
        sURIMatcher.addURI(CallLog.AUTHORITY, SearchManager.SUGGEST_URI_PATH_SHORTCUT + "/*", SEARCH_SHORTCUT);
        sURIMatcher.addURI(CallLog.AUTHORITY, "conference_calls", CONFERENCE_CALLS);
        sURIMatcher.addURI(CallLog.AUTHORITY, "conference_calls/#", CONFERENCE_CALLS_ID);
    }

    private static final String sStableCallsJoinData = Tables.CALLS 
            + " LEFT JOIN "
            + Tables.CONFERENCE_CALLS + " ON " + Calls.CONFERENCE_CALL_ID + "=" + Tables.CONFERENCE_CALLS + "." + ConferenceCalls._ID
            + " LEFT JOIN "
            + " (SELECT * FROM " +  Views.DATA + " WHERE " + Data._ID + " IN "
            + "(SELECT " +  Calls.DATA_ID + " FROM " + Tables.CALLS + ")) AS " + Views.DATA
                    + " ON(" + Tables.CALLS + "." + Calls.DATA_ID + " = " + Views.DATA + "." + Data._ID + ")";

    // Must match the definition in CallLogQuery - begin.
    private static final String CALL_NUMBER_TYPE = "calllognumbertype";
    private static final String CALL_NUMBER_TYPE_ID = "calllognumbertypeid";
    // Must match the definition in CallLogQuery - end.

    private static final HashMap<String, String> sCallsJoinDataViewProjectionMap;
    static {
        // Calls Join view_data projection map
        sCallsJoinDataViewProjectionMap = new HashMap<String, String>();
        sCallsJoinDataViewProjectionMap.put(Calls._ID, Tables.CALLS + "._id as " + Calls._ID);
        sCallsJoinDataViewProjectionMap.put(Calls.NUMBER, Calls.NUMBER);
        sCallsJoinDataViewProjectionMap.put(Calls.NUMBER_PRESENTATION, Calls.NUMBER_PRESENTATION);
        sCallsJoinDataViewProjectionMap.put(Calls.DATE, Calls.DATE);
        sCallsJoinDataViewProjectionMap.put(Calls.DURATION, Calls.DURATION);
        sCallsJoinDataViewProjectionMap.put(Calls.DATA_USAGE, Calls.DATA_USAGE);
        sCallsJoinDataViewProjectionMap.put(Calls.TYPE, Calls.TYPE);
        sCallsJoinDataViewProjectionMap.put(Calls.FEATURES, Calls.FEATURES);
        sCallsJoinDataViewProjectionMap.put(Calls.PHONE_ACCOUNT_COMPONENT_NAME, Calls.PHONE_ACCOUNT_COMPONENT_NAME);
        sCallsJoinDataViewProjectionMap.put(Calls.PHONE_ACCOUNT_ID, Calls.PHONE_ACCOUNT_ID);
        sCallsJoinDataViewProjectionMap.put(Calls.NEW, Calls.NEW);
        sCallsJoinDataViewProjectionMap.put(Calls.VOICEMAIL_URI, Calls.VOICEMAIL_URI);
        sCallsJoinDataViewProjectionMap.put(Calls.TRANSCRIPTION, Calls.TRANSCRIPTION);
        sCallsJoinDataViewProjectionMap.put(Calls.IS_READ, Calls.IS_READ);
        sCallsJoinDataViewProjectionMap.put(Calls.COUNTRY_ISO, Calls.COUNTRY_ISO);
        sCallsJoinDataViewProjectionMap.put(Calls.GEOCODED_LOCATION, Calls.GEOCODED_LOCATION);
        sCallsJoinDataViewProjectionMap.put(Calls.RAW_CONTACT_ID, Tables.CALLS + "."
                + Calls.RAW_CONTACT_ID + " AS " + Calls.RAW_CONTACT_ID);
        sCallsJoinDataViewProjectionMap.put(Calls.DATA_ID, Calls.DATA_ID);

        sCallsJoinDataViewProjectionMap.put(Contacts.DISPLAY_NAME,
                Views.DATA + "." + Contacts.DISPLAY_NAME + " AS " + Contacts.DISPLAY_NAME);
        sCallsJoinDataViewProjectionMap.put(CALL_NUMBER_TYPE_ID,
                Views.DATA + "." + Data.DATA2 + " AS " + CALL_NUMBER_TYPE_ID);
        sCallsJoinDataViewProjectionMap.put(CALL_NUMBER_TYPE,
                Views.DATA + "." + Data.DATA3 + " AS " + CALL_NUMBER_TYPE);
        sCallsJoinDataViewProjectionMap.put(Data.PHOTO_ID, Views.DATA + "." + Data.PHOTO_ID + " AS " + Data.PHOTO_ID);
        sCallsJoinDataViewProjectionMap.put(RawContacts.INDICATE_PHONE_SIM, RawContacts.INDICATE_PHONE_SIM);
        sCallsJoinDataViewProjectionMap.put(RawContacts.IS_SDN_CONTACT, RawContacts.IS_SDN_CONTACT);  // add by MTK
        sCallsJoinDataViewProjectionMap.put(RawContacts.CONTACT_ID, RawContacts.CONTACT_ID);
        sCallsJoinDataViewProjectionMap.put(Contacts.LOOKUP_KEY, Views.DATA + "."
                + Contacts.LOOKUP_KEY + " AS " + Contacts.LOOKUP_KEY);
        sCallsJoinDataViewProjectionMap.put(Data.PHOTO_URI, Views.DATA + "." + Data.PHOTO_URI + " AS " + Data.PHOTO_URI);
        sCallsJoinDataViewProjectionMap.put(Calls.IP_PREFIX, Calls.IP_PREFIX);
        sCallsJoinDataViewProjectionMap.put(Calls.CONFERENCE_CALL_ID, Calls.CONFERENCE_CALL_ID);
        sCallsJoinDataViewProjectionMap.put(Calls.SORT_DATE, "(CASE WHEN " + Calls.CONFERENCE_CALL_ID + ">0  THEN "
                        + ConferenceCalls.CONFERENCE_DATE + " ELSE " + Calls.DATE + " END) AS " + Calls.SORT_DATE);
    }

    private VoicemailPermissions mVoicemailPermissions;
    private DialerSearchSupport mDialerSearchSupport;

    public boolean onCreate() {
        mVoicemailPermissions = new VoicemailPermissions(mContext);
        mCallLogSearchSupport = new CallLogSearchSupport(mContext);
        mDbHelper = getDatabaseHelper(mContext);
        mDialerSearchSupport = DialerSearchSupport.getInstance(mContext, mDbHelper);
        return true;
    }

    public SQLiteQueryBuilder queryCallLog(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder, int match, SQLiteQueryBuilder qb, SelectionBuilder selectionBuilder, Long parseCallId) {
        String groupBy = null;
        switch (match) {
        case CALLS_SEARCH_FILTER: {
            String query = uri.getPathSegments().get(2);
            String nomalizeName = NameNormalizer.normalize(query);
            final String SNIPPET_CONTACT_ID = "snippet_contact_id";
            String table = Tables.CALLS
                    + " LEFT JOIN " + Tables.CONFERENCE_CALLS + " ON "
                    + Calls.CONFERENCE_CALL_ID + "=" + Tables.CONFERENCE_CALLS + "." + ConferenceCalls._ID
                    + " LEFT JOIN " + Views.DATA + " ON (" + Views.DATA + "." + Data._ID + "="
                    + Tables.CALLS + "." + Calls.DATA_ID + ")" + " LEFT JOIN (SELECT " + SearchIndexColumns.CONTACT_ID
                    + " AS " + SNIPPET_CONTACT_ID + " FROM " + Tables.SEARCH_INDEX
                    // M: fix Cr:ALPS01790297,modify match to glob to ensure
                    // query call log contacts name in quickSearchBox right.
                    + " WHERE " + SearchIndexColumns.NAME + " GLOB '*" + nomalizeName + "*') " + " ON ("
                    + SNIPPET_CONTACT_ID + "=" + Views.DATA + "." + Data.CONTACT_ID + ")";

            qb.setTables(table);
            qb.setProjectionMap(sCallsJoinDataViewProjectionMap);

            StringBuilder sb = new StringBuilder();
            sb.append(Tables.CALLS + "." + Calls.NUMBER + " GLOB '*");
            sb.append(query);
            sb.append("*' OR (" + SNIPPET_CONTACT_ID + ">0 AND " + Tables.CALLS + "." + Calls.RAW_CONTACT_ID + ">0) ");
            qb.appendWhere(sb);
            groupBy = Tables.CALLS + "." + Calls._ID;

            LogUtils.d(TAG, " CallLogProvider.CALLS_SEARCH_FILTER, table=" + table + ", query=" + query + ", sb="
                    + sb.toString());
            break;
        }

        case CALLS_JION_DATA_VIEW: {
            qb.setTables(sStableCallsJoinData);
            qb.setProjectionMap(sCallsJoinDataViewProjectionMap);
            qb.setStrict(true);
            break;
        }

        case CALLS_JION_DATA_VIEW_ID: {
            qb.setTables(sStableCallsJoinData);
            qb.setProjectionMap(sCallsJoinDataViewProjectionMap);
            qb.setStrict(true);
            selectionBuilder.addClause(getEqualityClause(Tables.CALLS + "." + Calls._ID, parseCallId));
            break;
        }

        case CONFERENCE_CALLS_ID: {
            LogUtils.d(TAG, "CallLogProvider.CONFERENCE_CALLS_ID. Uri:" + uri);
            qb.setTables(sStableCallsJoinData);
            qb.setProjectionMap(sCallsJoinDataViewProjectionMap);
            long confCallId = ContentUris.parseId(uri);
            qb.appendWhere(Calls.CONFERENCE_CALL_ID + "=" + confCallId);
            break;
        }

        }
        return qb;
    }

    public Cursor queryGlobalSearch(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, int match) {
        final SQLiteDatabase db = mDbHelper.getReadableDatabase();
        Cursor c = null;
        switch (match) {
        case SEARCH_SUGGESTIONS: {
            LogUtils.d(TAG, "CallLogProvider.SEARCH_SUGGESTIONS");
            c = mCallLogSearchSupport.handleSearchSuggestionsQuery(db, uri, getLimit(uri));
            break;
        }

        case SEARCH_SHORTCUT: {
            LogUtils.d(TAG, "CallLogProvider.SEARCH_SHORTCUT. Uri:" + uri);
            String callId = uri.getLastPathSegment();
            String filter = uri.getQueryParameter(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA);
            c = mCallLogSearchSupport.handleSearchShortcutRefresh(db, projection, callId, filter);
            break;
        }
        }
        return c;
    }

    public Uri insertConferenceCall(Uri uri, ContentValues values) {
        if (CONFERENCE_CALLS == sURIMatcher.match(uri)) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            final long confCallId = db.insert(Tables.CONFERENCE_CALLS, ConferenceCalls.GROUP_ID, values);
    
            if (confCallId < 0) {
                LogUtils.w(TAG, "Insert Conference Call Failed, Uri:" + uri);
                return null;
            }
            return ContentUris.withAppendedId(uri, confCallId);
        }

        return null;
    }

    public Uri insert(Uri uri, ContentValues values) {
        LogUtils.d(TAG, "[insert]uri: " + uri);
        SQLiteDatabase db = null;
        try {
            db = mDbHelper.getWritableDatabase();
        } catch (SQLiteDiskIOException err) {
            err.printStackTrace();
            LogUtils.d(TAG, "insert()- 1 SQLiteDiskIOException");
            return null;
        }

        String strInsNumber = values.getAsString(Calls.NUMBER);
        //LogUtils.d(TAG, "[insert] get default insert number:" + strInsNumber);

        if (mCallsInserter == null) {
            mCallsInserter = new DatabaseUtils.InsertHelper(db, Tables.CALLS);
        }

        try {
            db.beginTransaction();

            boolean bIsUriNumber = PhoneNumberUtils.isUriNumber(strInsNumber);

            // Get all same call log id from calls table
            Cursor allCallLogCursorOfSameNum = null;
            if (bIsUriNumber) {
                allCallLogCursorOfSameNum = db.query(Tables.CALLS,
                        new String[] { Calls._ID, Calls.DATE },
                        Calls.NUMBER + "='" + strInsNumber + "'",
                        null, null, null, "_id DESC", null);
            } else {
                allCallLogCursorOfSameNum = db.query(Tables.CALLS,
                        new String[] { Calls._ID, Calls.DATE },
                       // "PHONE_NUMBERS_EQUAL(" + Calls.NUMBER + ", '" + strInsNumber + "')",
                       Calls.NUMBER + "='" + strInsNumber+"'",
                        null, null, null, "_id DESC", null);
            }

            long updateRowID = -1;
            long latestRowID = -1;
            StringBuilder noNamebuilder = new StringBuilder();
            if (allCallLogCursorOfSameNum != null) {
                if (allCallLogCursorOfSameNum.moveToFirst()) {
                    latestRowID = allCallLogCursorOfSameNum.getLong(0);
                    noNamebuilder.append(latestRowID);
                }
                while (allCallLogCursorOfSameNum.moveToNext()) {
                    noNamebuilder.append(",");
                    noNamebuilder.append(allCallLogCursorOfSameNum.getInt(0));
                }
                allCallLogCursorOfSameNum.close();
                allCallLogCursorOfSameNum = null;
            }

            // Get data_id and raw_contact_id information about contacts
            Cursor nameCursor = null;
            String normalizedNumber = strInsNumber;
            boolean numberCheckFlag = false;
            long dataId = -1;
            long rawContactId = -1;
            /*
             * TODO add number presentation logic to support special number type
             *
            boolean bSpecialNumber = (strInsNumber.equals(CallerInfo.UNKNOWN_NUMBER)
                    || strInsNumber.equals(CallerInfo.PRIVATE_NUMBER)
                    || strInsNumber.equals(CallerInfo.PAYPHONE_NUMBER));
            */
            boolean bSpecialNumber = false;
            LogUtils.d(TAG, "bIsUriNumber:" + bIsUriNumber + "|bSpecialNumber:" + bSpecialNumber);
            if (bIsUriNumber) {
                // Get internet call number contact information
                nameCursor = db.query(Views.DATA, new String[] {
                        Data._ID,
                        Data.RAW_CONTACT_ID
                }, Data.DATA1 + "='" + strInsNumber
                    + "' AND (" + Data.MIMETYPE + "='" + SipAddress.CONTENT_ITEM_TYPE + "' OR "
                    + Data.MIMETYPE + "='" + ImsCall.CONTENT_ITEM_TYPE + "')",
                        null, null, null, null);
            } else {
                // Get non-internet call number contact information
                //Do not strip the special number. Otherwise, UI would not get the right value.
                if (!bSpecialNumber) {
                    normalizedNumber = DialerSearchUtils.stripSpecialCharInNumberForDialerSearch(strInsNumber);
                }
                /*
                 * Use key "lookup" to get right data_id and raw_contact_id.
                 * The former one which uses "normalizedNumber" to search
                 * phone_lookup table would cause to get the dirty data.
                 *
                 * The previous code is:
                 *   nameCursor = getContext().getContentResolver().query(
                 *           Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(strInsNumber)),
                 *           new String[] {PhoneLookupColumns.DATA_ID, PhoneLookupColumns.RAW_CONTACT_ID},
                 *           null, null, null);
                 */
                nameCursor = DialerSearchUtils.queryPhoneLookupByNumber(db, mDbHelper,
                        strInsNumber, new String[] {
                                PhoneLookupColumns.DATA_ID, PhoneLookupColumns.RAW_CONTACT_ID, Phone.NUMBER
                        }, null, null, null, null, null, null);
            }
            if ((!bSpecialNumber) && (null != nameCursor) && (nameCursor.moveToFirst())) {
                numberCheckFlag = true;
                dataId = nameCursor.getLong(0);
                rawContactId = nameCursor.getLong(1);
                // Put the data_id and raw_contact_id into copiedValues to insert
                values.put(Calls.DATA_ID, dataId);
                values.put(Calls.RAW_CONTACT_ID, rawContactId);
            }
            if (null != nameCursor) {
                nameCursor.close();
            }

            // rowId is new callLog ID, and latestRowID is old callLog ID for the same number.
            LogUtils.d(TAG, "insert into calls table");
            long rowId = getDatabaseModifier(mCallsInserter).insert(values);
            LogUtils.d(TAG, "inserted into calls table. new rowId:" + rowId + "|dataId:"
                    + dataId + "|rawContactId" + rawContactId);

            if (ContactsProviderUtils.isSearchDbSupport()) {
                if (updateRowID == -1) {
                    updateRowID = rowId;
                }
                LogUtils.d(TAG, "[insert] insert updateRowID:" + updateRowID + " latestRowID:" + latestRowID + " rowId:" + rowId);
            }
            if (rowId > 0 && ContactsProviderUtils.isSearchDbSupport()) {
                ContentValues updateNameLookupValues = new ContentValues();
                updateNameLookupValues.put(DialerSearchLookupColumns.CALL_LOG_ID, rowId);
                if (numberCheckFlag) {
                    /*
                     * update old NO Name CallLog records that share the same
                     * number with the new inserted one, if exist.
                     *     String updateNoNameCallLogStmt = Calls.DATA_ID + " IS NULL " +
                     *         " AND PHONE_NUMBERS_EQUAL(" + Calls.NUMBER + ",'" + number + "') ";
                     *
                     * update All CallLog records that share the same number with the new inserted
                     * one, if exist.
                     */
                    if (noNamebuilder != null && noNamebuilder.length() > 0) {
                        // update NO Name CallLog records of the inserted CallLog
                        if (Build.TYPE.equals("eng")) {
                            LogUtils.d(TAG, "[insert]updated calls record. number:" + strInsNumber + " data_id:"
                                    + dataId + " raw_contact_id:" + rawContactId);
                        }
                        ContentValues updateNoNameCallLogValues = new ContentValues();
                        updateNoNameCallLogValues.put(Calls.RAW_CONTACT_ID, rawContactId);
                        updateNoNameCallLogValues.put(Calls.DATA_ID, dataId);
                        int updateNoNameCallLogCount = db.update(Tables.CALLS, updateNoNameCallLogValues,
                                Calls._ID + " IN (" + noNamebuilder.toString() + ")", null);
                        LogUtils.d(TAG, "[insert]updated NO Name CallLog records of the inserted CallLog. Count:"
                                + updateNoNameCallLogCount);

                        // delete No Name CallLog records in dialer search table, if exists.
                        LogUtils.d(TAG, "[insert] delete No Name CallLog records:" + noNamebuilder.toString()
                                + " Except:" + latestRowID);
                        String deleteNoNameCallLogInDs = "("
                                + DialerSearchLookupColumns.CALL_LOG_ID + " IN (" + noNamebuilder.toString() + ") "
                                + "AND "
                                + DialerSearchLookupColumns.NAME_TYPE + " = " + DialerSearchLookupType.NO_NAME_CALL_LOG
                                + " AND "
                                + DialerSearchLookupColumns.RAW_CONTACT_ID + " < 0 "
                                + " AND "
                                + DialerSearchLookupColumns.DATA_ID + " < 0 )";
                        int deleteNoNameCallLogCount = db.delete(Tables.DIALER_SEARCH,
                                deleteNoNameCallLogInDs, null);
                        LogUtils.d(TAG, "[insert] deleted No Name CallLog records in dialer search table. Count:"
                                + deleteNoNameCallLogCount);
                    }

                    //update dialer search table.
                    LogUtils.d(TAG, "[insert]query dialer_search. ");
                    String updateNameCallLogStmt = "(" + DialerSearchLookupColumns.RAW_CONTACT_ID + " = " + rawContactId
                            + " AND " + DialerSearchLookupColumns.NAME_TYPE + " = 11)"
                            + " OR (" + DialerSearchLookupColumns.DATA_ID + " = " + dataId
                            + " AND " + DialerSearchLookupColumns.NAME_TYPE + " = 8)";
                    int updateDialerSearchCount = db.update(Tables.DIALER_SEARCH,
                            updateNameLookupValues, updateNameCallLogStmt, null);
                    LogUtils.d(TAG, "[insert]update dialer_search table. updateDialerSearchCount:"
                            + updateDialerSearchCount);

                    // if the new a call log with new contact id but the same number
                    // change the original call_log_id as
                    updateNameLookupValues.put(DialerSearchLookupColumns.CALL_LOG_ID, 0);
                    int upDialCount = db.update(Tables.DIALER_SEARCH, updateNameLookupValues,
                            DialerSearchLookupColumns.CALL_LOG_ID + " = " + latestRowID, null);
                    LogUtils.d(TAG, "[insert]update dialer_search table. updateDialerSearchCount:" + upDialCount);
                } else {
                    LogUtils.d(TAG, "[insert]cursor nameCursor donot have data.");
                    if (latestRowID != -1) {
                        LogUtils.d(TAG, "[insert] update NO NAME RECORD.");
                        updateNameLookupValues.put(DialerSearchLookupColumns.DATA_ID, -updateRowID);
                        updateNameLookupValues.put(DialerSearchLookupColumns.RAW_CONTACT_ID, -updateRowID);
                        updateNameLookupValues.put(DialerSearchLookupColumns.NORMALIZED_NAME, normalizedNumber);
                        updateNameLookupValues.put(DialerSearchLookupColumns.NORMALIZED_NAME_ALTERNATIVE, normalizedNumber);
                        int updateDialerSearchCount = db.update(Tables.DIALER_SEARCH, updateNameLookupValues, DialerSearchLookupColumns.CALL_LOG_ID + " = " + latestRowID, null);
                        LogUtils.d(TAG, "[insert]update dialer_search table. updateDialerSearchCount:" + updateDialerSearchCount);
                        if (updateDialerSearchCount == 0) {
                            Log.w(TAG, "[insert]database has old calllog, but did not insert to dialersearch");
                            long insertDialerSearch = insertNoNameDialerSearch(db, updateRowID, normalizedNumber);
                            LogUtils.d(TAG, "[insert]insert dialer_search table. insertDialerSearch:" + insertDialerSearch);
                        }
                    } else {
                        LogUtils.d(TAG, "[insert]**nameLookupCursor is null");
                        long insertDialerSearch = insertNoNameDialerSearch(db, updateRowID, normalizedNumber);
                        LogUtils.d(TAG, "[insert]insert dialer_search table. insertDialerSearch:" + insertDialerSearch);
                    }
                }
            }
            if (rowId > 0) {
                notifyDialerSearchChange();
                mDialerSearchSupport.notifyDialerSearchChange();
                uri = ContentUris.withAppendedId(uri, rowId);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return uri;
    }

    public int delete(Uri uri, String selection, String[] selectionArgs, SelectionBuilder selectionBuilder) {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();
            int count = 0;
            if (ContactsProviderUtils.isSearchDbSupport()) {
                /*
                 * update name_lookup for usage of dialer search:
                 */
                if (selection == null) {    // delete all call logs
                    LogUtils.d(TAG, "[delete] Selection is null, delete all Call logs.");
                    int deleteCount = db.delete(Tables.DIALER_SEARCH,
                            DialerSearchLookupColumns.CALL_LOG_ID + " > 0 AND "
                            + DialerSearchLookupColumns.RAW_CONTACT_ID + " <=0 " , null);
                    LogUtils.d(TAG, "[delete] delete from Dialer_Search Count: " + deleteCount);
                    ContentValues updateNameLookupValue = new ContentValues();
                    updateNameLookupValue.put(DialerSearchLookupColumns.CALL_LOG_ID, 0);
                    int updateCount = db.update(Tables.DIALER_SEARCH, updateNameLookupValue,
                            DialerSearchLookupColumns.CALL_LOG_ID + " > 0 AND "
                            + DialerSearchLookupColumns.RAW_CONTACT_ID + " >0 ", null);
                    LogUtils.d(TAG, "[delete] update from Dialer_Search Count: " + updateCount);
                    count = getDatabaseModifier(db).delete(Tables.CALLS,
                            selectionBuilder.build(), selectionArgs);
                } else {
                    mDbHelper.getWritableDatabase().beginTransaction();
                    try {
                        LogUtils.d(TAG, "[delete] delete calls selection: " + selection);
                        Cursor delCursor = db.query(true, Tables.CALLS,
                                new String[] { Calls._ID, Calls.NUMBER, Calls.RAW_CONTACT_ID, Calls.DATA_ID },
                                selection, selectionArgs, "data_id, _id", null, null, null);
                        Cursor allCallLogs = db.query(true,
                                Tables.CALLS, new String[] { Calls._ID, Calls.DATA_ID},
                                null, null, null, null, null, null);
                        int allCount = allCallLogs == null ? 0 : allCallLogs.getCount();
                        if (delCursor != null && delCursor.getCount() == allCount) {
                            int deleteCount = db.delete(Tables.DIALER_SEARCH,
                                    DialerSearchLookupColumns.CALL_LOG_ID + " > 0 AND "
                                    + DialerSearchLookupColumns.RAW_CONTACT_ID + " <=0 " , null);
                            LogUtils.d(TAG, "[delete] delete from Dialer_Search Count: " + deleteCount);
                            ContentValues updateNameLookupValue = new ContentValues();
                            updateNameLookupValue.put(DialerSearchLookupColumns.CALL_LOG_ID, 0);
                            int updateCount = db.update(Tables.DIALER_SEARCH, updateNameLookupValue,
                                    DialerSearchLookupColumns.CALL_LOG_ID + " > 0 AND "
                                    + DialerSearchLookupColumns.RAW_CONTACT_ID + " >0 ", null);
                            LogUtils.d(TAG, "[delete] update from Dialer_Search Count: " + updateCount);
                            count = getDatabaseModifier(db).delete(Tables.CALLS,
                                    selectionBuilder.build(), selectionArgs);
                        } else if (delCursor != null && delCursor.getCount() > 0) {

                            db.execSQL("DROP TABLE IF EXISTS delCallLog");
                            if (selectionArgs != null && selectionArgs.length > 0) {
                                db.execSQL(" CREATE TEMP TABLE delCallLog AS SELECT " +
                                        "_id, number, data_id, raw_contact_id" +
                                        " FROM calls WHERE " + selection, selectionArgs);
                            } else {
                                db.execSQL(" CREATE TEMP TABLE delCallLog AS SELECT " +
                                        "_id, number, data_id, raw_contact_id" +
                                        " FROM calls WHERE " + selection);
                            }
                            count = getDatabaseModifier(db).delete(Tables.CALLS,
                                    selectionBuilder.build(), selectionArgs);

                            String queryStr = "SELECT " +
                            "delCallLog._id as _id, " +
                            "delCallLog.number as delNumber, " +
                            "delCallLog.data_id as delDataId, " +
                            "delCallLog.raw_contact_id as delRawId, " +
                            "calls._id as newId, " +
                            "calls.number as newNumber, " +
                            "calls.data_id as newDataId, " +
                            "calls.raw_contact_id as newRawId " +
                            " FROM delCallLog " +
                            " LEFT JOIN calls " +
                            " on case when delCallLog.data_id is null then PHONE_NUMBERS_EQUAL(delCallLog.number, calls.number) " +
                            " else delCallLog.data_id = calls.data_id " +
                            " end and delCallLog._id != calls._id GROUP BY delCallLog._id";
                            Cursor updateCursor = db.rawQuery(queryStr, null);
                            if (updateCursor != null) {
                                while (updateCursor.moveToNext()) {
                                    long delCallId = updateCursor.getLong(0);
                                    long delDataId = updateCursor.getLong(2);
                                    long newCallId = updateCursor.getLong(4);
                                    if (delDataId > 0) {
                                        if (mUpdateForCallLogUpdated == null) {
                                            mUpdateForCallLogUpdated = db.compileStatement(
                                                    " UPDATE " + Tables.DIALER_SEARCH +
                                                    " SET " + DialerSearchLookupColumns.CALL_LOG_ID + "=? " +
                                                    " WHERE " + DialerSearchLookupColumns.CALL_LOG_ID + "=? ");
                                        }
                                        // named call log
                                        if (newCallId != delCallId && newCallId > 0) {
                                            mUpdateForCallLogUpdated.bindLong(1, newCallId);
                                            mUpdateForCallLogUpdated.bindLong(2, delCallId);
                                        } else if (newCallId <= 0) {
                                            mUpdateForCallLogUpdated.bindLong(1, 0);
                                            mUpdateForCallLogUpdated.bindLong(2, delCallId);
                                        }
                                        mUpdateForCallLogUpdated.execute();
                                    } else {
                                        // no name call log
                                        if (newCallId > 0) {
                                            //update new call log
                                            if (newCallId != delCallId) {
                                                if (mUpdateForNoNameCallLogDeleted == null) {
                                                    mUpdateForNoNameCallLogDeleted = db.compileStatement(
                                                            " UPDATE " + Tables.DIALER_SEARCH +
                                                            " SET " + DialerSearchLookupColumns.DATA_ID + "=?, " +
                                                            DialerSearchLookupColumns.RAW_CONTACT_ID + "=?, " +
                                                            DialerSearchLookupColumns.CALL_LOG_ID + "=? " +
                                                            " WHERE " + DialerSearchLookupColumns.CALL_LOG_ID + "=? ");
                                                }
                                                mUpdateForNoNameCallLogDeleted.bindLong(1, -newCallId);
                                                mUpdateForNoNameCallLogDeleted.bindLong(2, -newCallId);
                                                mUpdateForNoNameCallLogDeleted.bindLong(3, newCallId);
                                                mUpdateForNoNameCallLogDeleted.bindLong(4, delCallId);
                                                mUpdateForNoNameCallLogDeleted.execute();
                                            }
                                        } else {
                                            if (mDeleteForCallLogDeleted == null) {
                                                mDeleteForCallLogDeleted = db.compileStatement(
                                                        "DELETE FROM " + Tables.DIALER_SEARCH +
                                                        " WHERE " + DialerSearchLookupColumns.CALL_LOG_ID + " =? " +
                                                        " AND " + DialerSearchLookupColumns.NAME_TYPE + " = " + DialerSearchLookupType.PHONE_EXACT);
                                            }
                                            //delete from dialer search table
                                            mDeleteForCallLogDeleted.bindLong(1, delCallId);
                                            mDeleteForCallLogDeleted.execute();
                                        }
                                    }
                                }
                                updateCursor.close();
                            }
                            db.execSQL("DROP TABLE IF EXISTS delCallLog");
                        }
                        if (delCursor != null) {
                            delCursor.close();
                        }
                        if (allCallLogs != null)
                            allCallLogs.close();
                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                }
            } else {
                count = getDatabaseModifier(db).delete(Tables.CALLS,
                    selectionBuilder.build(), selectionArgs);
            }
            LogUtils.d(TAG, "[delete] delete Calls. count: " + count);
            if (count > 0) {
                notifyDialerSearchChange();
                mDialerSearchSupport.notifyDialerSearchChange();
            }
            return count;
        }

    private static final boolean DBG_DIALER_SEARCH = ContactsProviderUtils.DBG_DIALER_SEARCH;

    private SQLiteStatement mUpdateForCallLogUpdated;
    private SQLiteStatement mUpdateForNoNameCallLogDeleted;
    private SQLiteStatement mDeleteForCallLogDeleted;

    private void notifyDialerSearchChange() {
        mContext.getContentResolver().notifyChange(
                ContactsContract.AUTHORITY_URI.buildUpon().appendPath("dialer_search")
                        .appendPath("call_log").build(), null, false);
    }

    // send new Calls broadcast to launcher to update unread icon 
    public static final void notifyNewCallsCount(Context context) {
        SQLiteDatabase db = null; 
        Cursor c = null;
        int newCallsCount = 0;
        try {
            db = getDatabaseHelper(context).getReadableDatabase();

            if (db == null || context == null) {            
                LogUtils.w(TAG, "[notifyNewCallsCount] Cannot notify with null db or context.");
                return;
            }

            c = db.rawQuery("SELECT count(*) FROM " + Tables.CALLS
                    + " WHERE " + Calls.TYPE + " in (" + Calls.MISSED_TYPE + "," + Calls.VOICEMAIL_TYPE
                    + ") AND " + Calls.NEW + "=1", null);

            if (c != null && c.moveToFirst()) {
                newCallsCount = c.getInt(0);
            }
        } catch (SQLiteException e) {
            LogUtils.w(TAG, "[notifyNewCallsCount] SQLiteException:" + e);
            return;
        } finally {
            if (c != null) {
                c.close();
            }
        }

        LogUtils.i(TAG, "[notifyNewCallsCount] newCallsCount = " + newCallsCount);
        //send count=0 to clear the unread icon
        if (newCallsCount >= 0) {
            Intent newIntent = new Intent(Intent.ACTION_UNREAD_CHANGED);
            newIntent.putExtra(Intent.EXTRA_UNREAD_NUMBER, newCallsCount);
            newIntent.putExtra(Intent.EXTRA_UNREAD_COMPONENT, new ComponentName(ConstantsUtils.CONTACTS_PACKAGE,
                    ConstantsUtils.CONTACTS_DIALTACTS_ACTIVITY));
            context.sendBroadcast(newIntent);
            android.provider.Settings.System.putInt(context.getContentResolver(), ConstantsUtils.CONTACTS_UNREAD_KEY, Integer
                    .valueOf(newCallsCount));
        }
    }

    // insert dialerSearch with number not stored in contacts.
    private long insertNoNameDialerSearch(SQLiteDatabase db, long updateRowID, String normalizedNumber) {
        ContentValues insertNameLookupValues = new ContentValues();
        insertNameLookupValues.put(DialerSearchLookupColumns.CALL_LOG_ID, updateRowID);
        insertNameLookupValues.put(DialerSearchLookupColumns.NAME_TYPE, DialerSearchLookupType.NO_NAME_CALL_LOG);
        insertNameLookupValues.put(DialerSearchLookupColumns.DATA_ID, -updateRowID);
        insertNameLookupValues.put(DialerSearchLookupColumns.RAW_CONTACT_ID, -updateRowID);
        insertNameLookupValues.put(DialerSearchLookupColumns.NORMALIZED_NAME, normalizedNumber);
        insertNameLookupValues.put(DialerSearchLookupColumns.NORMALIZED_NAME_ALTERNATIVE, normalizedNumber);
        long insertDialerSearchCnt = db.insert(Tables.DIALER_SEARCH, null, insertNameLookupValues);
        return insertDialerSearchCnt;
    }

    protected static ContactsDatabaseHelper getDatabaseHelper(final Context context) {
        return ContactsDatabaseHelper.getInstance(context);
    }

    /**
     * Returns a {@link DatabaseModifier} that takes care of sending necessary notifications
     * after the operation is performed.
     */
    private DatabaseModifier getDatabaseModifier(SQLiteDatabase db) {
        return new DbModifierWithNotification(Tables.CALLS, db, mContext);
    }

    /**
     * Same as {@link #getDatabaseModifier(SQLiteDatabase)} but used for insert helper operations
     * only.
     */
    private DatabaseModifier getDatabaseModifier(DatabaseUtils.InsertHelper insertHelper) {
        return new DbModifierWithNotification(Tables.CALLS, insertHelper, mContext);
    }

    /**
     * Gets the value of the "limit" URI query parameter.
     *
     * @return A string containing a non-negative integer, or <code>null</code> if
     *         the parameter is not set, or is set to an invalid value.
     */
    public String getLimit(Uri uri) {
        String limitParam = uri.getQueryParameter("limit");
        if (limitParam == null) {
            return null;
        }
        // make sure that the limit is a non-negative integer
        try {
            int l = Integer.parseInt(limitParam);
            if (l < 0) {
                Log.w(TAG, "Invalid limit parameter: " + limitParam);
                return null;
            }
            return String.valueOf(l);
        } catch (NumberFormatException ex) {
            Log.w(TAG, "Invalid limit parameter: " + limitParam);
            return null;
        }
    }

}
