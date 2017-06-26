/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

import android.app.AppOpsManager;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Telephony;
import android.provider.Telephony.CanonicalAddressesColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.TextBasedSmsColumns;
import android.provider.Telephony.ThreadsColumns;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.TelephonyIntents;
import com.google.android.mms.pdu.PduHeaders;
import com.mediatek.common.mom.SubPermissions;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;

public class SmsProvider extends ContentProvider {
    private static final Uri NOTIFICATION_URI = Uri.parse("content://sms");
    private static final Uri ICC_URI = Uri.parse("content://sms/icc");
//    private static final Uri ICC_URI_GEMINI = Uri.parse("content://sms/icc2");
//    private static final Uri ICC_URI_THREE = Uri.parse("content://sms/icc3");
//    private static final Uri ICC_URI_FOUR = Uri.parse("content://sms/icc4");
    /// M: New Feature The international card.
    private static final Uri ICC_URI_INTERNATIONAL = Uri.parse("content://sms/icc_international");
    private static final Uri ICC_URI_GEMINI_INTERNATIONAL = Uri.parse("content://sms/icc2_international");
    private boolean mIsInternationalCardNotActivate = false;
    static final String TABLE_SMS = "sms";
    static final String TABLE_RAW = "raw";
    private static final String TABLE_SR_PENDING = "sr_pending";
    private static final String TABLE_WORDS = "words";
    /// M: Code analyze 002, fix bug ALPS00046358, improve multi-delete speed by use batch
    /// processing. reference from page http://www.erpgear.com/show.php?contentid=1111.
    private static final String FOR_MULTIDELETE = "ForMultiDelete";
    private static final Integer ONE = Integer.valueOf(1);
    private static final int PERSON_ID_COLUMN = 0;
    /// M: Code analyze 005, fix bug ALPS00245352, it cost long time to restore messages.
    /// remove useless operation and add transaction while import sms. @{
    private static final int NORMAL_NUMBER_MAX_LENGTH = 15;
    private static final String[] CANONICAL_ADDRESSES_COLUMNS_2 =
    new String[] { CanonicalAddressesColumns._ID, CanonicalAddressesColumns.ADDRESS };
    /// @}
    /// M: Code analyze 006, fix bug ALPS00252799, it cost long time to restore messages.
    /// support batch processing while restore messages. @{
    /**
     * Maximum number of operations allowed in a batch
     */
    private static final int MAX_OPERATIONS_PER_PATCH = 50;
    /// @}
    /**
     * These are the columns that are available when reading SMS
     * messages from the ICC.  Columns whose names begin with "is_"
     * have either "true" or "false" as their values.
     */
    private final static String[] ICC_COLUMNS = new String[] {
        // N.B.: These columns must appear in the same order as the
        // calls to add appear in convertIccToSms.
        "service_center_address",       // getServiceCenterAddress
        "address",                      // getDisplayOriginatingAddress
        "message_class",                // getMessageClass
        "body",                         // getDisplayMessageBody
        "date",                         // getTimestampMillis
        "status",                       // getStatusOnIcc
        "index_on_icc",                 // getIndexOnIcc
        "is_status_report",             // isStatusReportMessage
        "transport_type",               // Always "sms".
        "type",                         // Always MESSAGE_TYPE_ALL.
        "locked",                       // Always 0 (false).
        "error_code",                   // Always 0
        "_id",
        /// M: Code analyze 007, fix bug ALPS00042403, should show the sender's number
        /// in manage SIM card. show concatenation sms in one bubble, set incoming sms
        /// on left and sent sms on right, display sender information for every sms.
        "sub_id"                        // sim id
    };

    @Override
    public boolean onCreate() {
        // M: MoMS for controling database access ability
        setMoMSPermission(SubPermissions.QUERY_SMS, SubPermissions.MODIFY_SMS);

        setAppOps(AppOpsManager.OP_READ_SMS, AppOpsManager.OP_WRITE_SMS);
        mOpenHelper = MmsSmsDatabaseHelper.getInstance(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projectionIn, String selection,
            String[] selectionArgs, String sort) {
        //add by wanghui for al812
        //Xlog.d(TAG, "query begin, uri = " + uri + ", selection = " + selection);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        // Generate the body of the query.
        int match = sURLMatcher.match(uri);
        switch (match) {
            case SMS_ALL:
                constructQueryForBox(qb, Sms.MESSAGE_TYPE_ALL);
                break;

            case SMS_UNDELIVERED:
                constructQueryForUndelivered(qb);
                break;

            case SMS_FAILED:
                constructQueryForBox(qb, Sms.MESSAGE_TYPE_FAILED);
                break;

            case SMS_QUEUED:
                constructQueryForBox(qb, Sms.MESSAGE_TYPE_QUEUED);
                break;

            case SMS_INBOX:
                constructQueryForBox(qb, Sms.MESSAGE_TYPE_INBOX);
                break;

            case SMS_SENT:
                constructQueryForBox(qb, Sms.MESSAGE_TYPE_SENT);
                break;

            case SMS_DRAFT:
                constructQueryForBox(qb, Sms.MESSAGE_TYPE_DRAFT);
                break;

            case SMS_OUTBOX:
                constructQueryForBox(qb, Sms.MESSAGE_TYPE_OUTBOX);
                break;

            case SMS_ALL_ID:
                qb.setTables(TABLE_SMS);
                qb.appendWhere("(_id = " + uri.getPathSegments().get(0) + ")");
                break;

            case SMS_INBOX_ID:
            case SMS_FAILED_ID:
            case SMS_SENT_ID:
            case SMS_DRAFT_ID:
            case SMS_OUTBOX_ID:
                qb.setTables(TABLE_SMS);
                qb.appendWhere("(_id = " + uri.getPathSegments().get(1) + ")");
                break;

            case SMS_CONVERSATIONS_ID:
                int threadID;

                try {
                    threadID = Integer.parseInt(uri.getPathSegments().get(1));
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.d(TAG, "query conversations: threadID=" + threadID);
                    }
                }
                catch (Exception ex) {
                    Log.e(TAG,
                          "Bad conversation thread id: "
                          + uri.getPathSegments().get(1));
                    return null;
                }

                qb.setTables(TABLE_SMS);
                qb.appendWhere("thread_id = " + threadID);
                break;

            case SMS_CONVERSATIONS:
                qb.setTables("sms, (SELECT thread_id AS group_thread_id, MAX(date)AS group_date,"
                       + "COUNT(*) AS msg_count FROM sms GROUP BY thread_id) AS groups");
                qb.appendWhere("sms.thread_id = groups.group_thread_id AND sms.date ="
                       + "groups.group_date");
                qb.setProjectionMap(sConversationProjectionMap);
                break;

            case SMS_RAW_MESSAGE:
                qb.setTables("raw");
                break;

            case SMS_STATUS_PENDING:
                qb.setTables("sr_pending");
                break;

            case SMS_ATTACHMENT:
                qb.setTables("attachments");
                break;

            case SMS_ATTACHMENT_ID:
                qb.setTables("attachments");
                qb.appendWhere(
                        "(sms_id = " + uri.getPathSegments().get(1) + ")");
                break;

            case SMS_QUERY_THREAD_ID:
                qb.setTables("canonical_addresses");
                if (projectionIn == null) {
                    projectionIn = sIDProjection;
                }
                break;

            case SMS_STATUS_ID:
                qb.setTables(TABLE_SMS);
                qb.appendWhere("(_id = " + uri.getPathSegments().get(1) + ")");
                break;

            case SMS_ALL_ICC:
                return getAllMessagesFromIcc(uri, getSubIdFromUri(uri));
            case SMS_ICC:
                String messageIndexString = uri.getPathSegments().get(1);

                return getSingleMessageFromIcc(messageIndexString, getSubIdFromUri(uri));
            /// M: Code analyze 011, fix bug ALPS00282321, ANR while delete old messages.
            /// use new process of delete. @{
            case SMS_ALL_THREADID:
                /// M: return all the distinct threadid from sms table
                return getAllSmsThreadIds(selection, selectionArgs);
            /// M: New Feature The international card.
            case SMS_ALL_ICC_INTERNATIONAL:
                return getAllMessagesFromIccInternational(uri, 0);
            /// @}
            case URI_THREAD_ID:
                String recipient = uri.getQueryParameter("recipient");
                SQLiteDatabase db = mOpenHelper.getWritableDatabase();
                return getThreadIdWithoutInsert(recipient, db);
            default:
                Log.e(TAG, "Invalid request: " + uri);
                return null;
        }

        String orderBy = null;

        if (!TextUtils.isEmpty(sort)) {
            orderBy = sort;
        } else if (qb.getTables().equals(TABLE_SMS)) {
            orderBy = Sms.DEFAULT_SORT_ORDER;
        }
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor ret = qb.query(db, projectionIn, selection, selectionArgs,
                              null, null, orderBy);
        // TODO: Since the URLs are a mess, always use content://sms
        ret.setNotificationUri(getContext().getContentResolver(),
                NOTIFICATION_URI);
        Xlog.d(TAG, "query end");
        return ret;
    }
    /// M: Code analyze 007, fix bug ALPS00042403, should show the sender's number
    /// in manage SIM card. show concatenation sms in one bubble, set incoming sms
    /// on left and sent sms on right, display sender information for every sms. @{
    private Object[] convertIccToSms(SmsMessage message,
            ArrayList<String> concatSmsIndexAndBody, int id,
            int subId) {
        // N.B.: These calls must appear in the same order as the
        // columns appear in ICC_COLUMNS.
        Object[] row = new Object[14];
        row[0] = message.getServiceCenterAddress();

        // check message status and set address
        if ((message.getStatusOnIcc() == SmsManager.STATUS_ON_ICC_READ) ||
               (message.getStatusOnIcc() == SmsManager.STATUS_ON_ICC_UNREAD)) {
            row[1] = message.getDisplayOriginatingAddress();
        } else {
            row[1] = message.getDestinationAddress();
        }

        String concatSmsIndex = null;
        String concatSmsBody = null;
        if (null != concatSmsIndexAndBody) {
            concatSmsIndex = concatSmsIndexAndBody.get(0);
            concatSmsBody = concatSmsIndexAndBody.get(1);
        }

        row[2] = String.valueOf(message.getMessageClass());
        row[3] = concatSmsBody == null ? message.getDisplayMessageBody() : concatSmsBody;
        row[4] = message.getTimestampMillis();
        row[5] = message.getStatusOnIcc();
        if (mIsInternationalCardNotActivate) {
            if (concatSmsIndex == null) {
                try {
                    concatSmsIndex = String.valueOf(message.getIndexOnIcc() ^ (0x01 << 10));
                    row[6] = concatSmsIndex;
                } catch (NumberFormatException e) {
                    Xlog.e(TAG, "concatSmsIndex bad number");
                }
            } else {
                row[6] = concatSmsIndex;
            }
        } else {
            row[6] = concatSmsIndex == null ? message.getIndexOnIcc() : concatSmsIndex;
        }
        Xlog.d(TAG, "convertIccToSms; contactSmsIndex:" + row[6]);
        row[7] = message.isStatusReportMessage();
        row[8] = "sms";
        row[9] = TextBasedSmsColumns.MESSAGE_TYPE_ALL;
        row[10] = 0;      // locked
        row[11] = 0;      // error_code
        row[12] = id;
        row[13] = subId;
        return row;
    }
    /// @}
    /**
     * Return a Cursor containing just one message from the ICC.
     */

    private Cursor getSingleMessageFromIcc(String messageIndexString, int subId) {
         try {
            int messageIndex = Integer.parseInt(messageIndexString);
            ArrayList<SmsMessage> messages;

            // use phone app permissions to avoid UID mismatch in AppOpsManager.noteOp() call
            long token = Binder.clearCallingIdentity();
            try {
                messages = SmsManager.getSmsManagerForSubscriptionId(subId).getAllMessagesFromIcc();
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            /// M: Code analyze 012, unknown, check if "messages" is valid. @{
            if (messages == null || messages.isEmpty()) {
                Xlog.e(TAG, "getSingleMessageFromIcc messages is null");
                return null;
            }
            /// @}
            SmsMessage message = messages.get(messageIndex);
            if (message == null) {
                throw new IllegalArgumentException(
                        "Message not retrieved. ID: " + messageIndexString);
            }
            MatrixCursor cursor = new MatrixCursor(ICC_COLUMNS, 1);
            cursor.addRow(convertIccToSms(message, 0, subId));
            return withIccNotificationUri(cursor);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Bad SMS ICC ID: " + messageIndexString);
        }
    }

    /**
     * Return a Cursor listing all the messages stored on the ICC.
     */
    /// M: Code analyze 007, fix bug ALPS00042403, should show the sender's number
    /// in manage SIM card. show concatenation sms in one bubble, set incoming sms
    /// on left and sent sms on right, display sender information for every sms. @{
    private Cursor getAllMessagesFromIcc(Uri uri, int subId) {
        ArrayList<SmsMessage> messages;

        // use phone app permissions to avoid UID mismatch in AppOpsManager.noteOp() call
        long token = Binder.clearCallingIdentity();
        try {
            messages = SmsManager.getSmsManagerForSubscriptionId(subId).getAllMessagesFromIcc();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        /// M: Code analyze 012, unknown, check if "messages" is valid. @{
        if (messages == null || messages.isEmpty()) {
            Xlog.e(TAG, "getAllMessagesFromIcc messages is null");
            return null;
        }
        /// @}
        final int count = messages.size();
        MatrixCursor cursor = new MatrixCursor(ICC_COLUMNS, count);
        ArrayList<String> concatSmsIndexAndBody = null;
        /// M: Code analyze 009, use a flag "showInOne" indicate show long sms in one bubble or not.
        boolean showInOne = "1".equals(uri.getQueryParameter("showInOne"));

        for (int i = 0; i < count; i++) {
            concatSmsIndexAndBody = null;
            SmsMessage message = messages.get(i);
            if (message != null && !message.isStatusReportMessage()) {
                if (showInOne) {
                    SmsHeader smsHeader = message.getUserDataHeader();
                    if (null != smsHeader && null != smsHeader.concatRef) {
                        concatSmsIndexAndBody = getConcatSmsIndexAndBody(messages, i);
                    }
                }
               cursor.addRow(convertIccToSms(message, concatSmsIndexAndBody, i, subId));
            }
        }
        return withIccNotificationUri(cursor);
    }
    /// @}
    private Cursor withIccNotificationUri(Cursor cursor) {
        cursor.setNotificationUri(getContext().getContentResolver(), ICC_URI);
        return cursor;
    }

    private void constructQueryForBox(SQLiteQueryBuilder qb, int type) {
        qb.setTables(TABLE_SMS);

        if (type != Sms.MESSAGE_TYPE_ALL) {
            qb.appendWhere("type=" + type);
        }
    }

    private void constructQueryForUndelivered(SQLiteQueryBuilder qb) {
        qb.setTables(TABLE_SMS);

        qb.appendWhere("(type=" + Sms.MESSAGE_TYPE_OUTBOX +
                       " OR type=" + Sms.MESSAGE_TYPE_FAILED +
                       " OR type=" + Sms.MESSAGE_TYPE_QUEUED + ")");
    }

    @Override
    public String getType(Uri url) {
        switch (url.getPathSegments().size()) {
        case 0:
            return VND_ANDROID_DIR_SMS;
            case 1:
                try {
                    Integer.parseInt(url.getPathSegments().get(0));
                    return VND_ANDROID_SMS;
                } catch (NumberFormatException ex) {
                    return VND_ANDROID_DIR_SMS;
                }
            case 2:
                // TODO: What about "threadID"?
                if (url.getPathSegments().get(0).equals("conversations")) {
                    return VND_ANDROID_SMSCHAT;
                } else {
                    return VND_ANDROID_SMS;
                }
        }
        return null;
    }

    @Override
    public Uri insert(Uri url, ContentValues initialValues) {
        final int callerUid = Binder.getCallingUid();
        long token = Binder.clearCallingIdentity();
        try {
            return insertInner(url, initialValues, callerUid);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private Uri insertInner(Uri url, ContentValues initialValues, int callerUid) {
        ContentValues values;
        long rowID = 0;
        int type = Sms.MESSAGE_TYPE_ALL;
        /// M: Code analyze 005, fix bug ALPS00245352, it cost long time to restore messages.
        /// remove useless operation and add transaction while import sms. @{
        /// M: for import sms only
        boolean importSms = false;
        /// @}
        int match = sURLMatcher.match(url);
        String table = TABLE_SMS;

        switch (match) {
            case SMS_ALL:
                Integer typeObj = initialValues.getAsInteger(Sms.TYPE);
                if (typeObj != null) {
                    type = typeObj.intValue();
                } else {
                    // default to inbox
                    type = Sms.MESSAGE_TYPE_INBOX;
                }
                break;

            case SMS_INBOX:
                type = Sms.MESSAGE_TYPE_INBOX;
                break;

            case SMS_FAILED:
                type = Sms.MESSAGE_TYPE_FAILED;
                break;

            case SMS_QUEUED:
                type = Sms.MESSAGE_TYPE_QUEUED;
                break;

            case SMS_SENT:
                type = Sms.MESSAGE_TYPE_SENT;
                break;

            case SMS_DRAFT:
                type = Sms.MESSAGE_TYPE_DRAFT;
                break;

            case SMS_OUTBOX:
                type = Sms.MESSAGE_TYPE_OUTBOX;
                break;

            case SMS_RAW_MESSAGE:
                table = "raw";
                break;

            case SMS_STATUS_PENDING:
                table = "sr_pending";
                break;

            case SMS_ATTACHMENT:
                table = "attachments";
                break;

            case SMS_NEW_THREAD_ID:
                table = "canonical_addresses";
                break;

            default:
                Log.e(TAG, "Invalid request: " + url);
                return null;
        }
        Xlog.d(TAG, "insertInner match url end");
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            if (table.equals(TABLE_SMS)) {
                boolean addDate = false;
                boolean addType = false;

                // Make sure that the date and type are set
                if (initialValues == null) {
                    values = new ContentValues(1);
                    addDate = true;
                    addType = true;
                } else {
                    values = new ContentValues(initialValues);

                    if (!initialValues.containsKey(Sms.DATE)) {
                        addDate = true;
                    }

                    if (!initialValues.containsKey(Sms.TYPE)) {
                        addType = true;
                    }
                    /// M: Code analyze 005, fix bug ALPS00245352, it cost long time to restore messages.
                    /// remove useless operation and add transaction while import sms. @{
                    if (initialValues.containsKey("import_sms")) {
                        importSms = true;
                        values.remove("import_sms");
                    }
                    /// @}
                }

                if (addDate) {
                    values.put(Sms.DATE, new Long(System.currentTimeMillis()));
                /// M: Code analyze 014, fix bug ALPS00114870, messages' time were abnormal after restored.
                /// set the date as the right value when import.
                } else {
                    Long date = values.getAsLong(Sms.DATE);
                    values.put(Sms.DATE, date);
                    Xlog.d(TAG, "insert sms date " + date);
                /// @}
                }

                if (addType && (type != Sms.MESSAGE_TYPE_ALL)) {
                    values.put(Sms.TYPE, Integer.valueOf(type));
                }

                // thread_id
                Long threadId = values.getAsLong(Sms.THREAD_ID);
                String address = values.getAsString(Sms.ADDRESS);

                if (((threadId == null) || (threadId == 0)) && (!TextUtils.isEmpty(address))) {
                    /// M: Code analyze 005, fix bug ALPS00245352, it cost long time to restore messages.
                    /// remove useless operation and add transaction while import sms. @{
                    Xlog.d(TAG, "insert sms getThreadId start");
                    long id = 0;
                    if (importSms){
                        id = getThreadIdInternal(address, db, true);
                    } else {
                        id = getThreadIdInternal(address, db, false);
                    }
                    values.put(Sms.THREAD_ID, id);
                    Xlog.d(TAG, "insert getContentResolver getOrCreateThreadId end id = " + id);
                    /// @}
                }

                // If this message is going in as a draft, it should replace any
                // other draft messages in the thread.  Just delete all draft
                // messages with this thread ID.  We could add an OR REPLACE to
                // the insert below, but we'd have to query to find the old _id
                // to produce a conflict anyway.
                if (values.getAsInteger(Sms.TYPE) == Sms.MESSAGE_TYPE_DRAFT) {
                    db.delete(TABLE_SMS, "thread_id=? AND type=?",
                            new String[] { values.getAsString(Sms.THREAD_ID),
                                           Integer.toString(Sms.MESSAGE_TYPE_DRAFT) });
                }
                /// M: Code analyze 006, fix bug ALPS00252799, it cost long time to restore messages.
                /// support batch processing while restore messages. @{
                if (type != Sms.MESSAGE_TYPE_INBOX) {
                    values.put(Sms.READ, ONE);
                }
                if (!values.containsKey(Sms.PERSON)) {
                    values.put(Sms.PERSON, 0);
                }
                /// @}
                if (ProviderUtil.shouldSetCreator(values, callerUid)) {
                    // Only SYSTEM or PHONE can set CREATOR
                    // If caller is not SYSTEM or PHONE, or SYSTEM or PHONE does not set CREATOR
                    // set CREATOR using the truth on caller.
                    // Note: Inferring package name from UID may include unrelated package names
                    values.put(Sms.CREATOR, ProviderUtil.getPackageNamesByUid(getContext(), callerUid));
                }
            } else {
                if (initialValues == null) {
                    values = new ContentValues(1);
                } else {
                    values = initialValues;
                }
            }

            rowID = db.insert(table, "body", values);
            /// M: Code analyze 005, fix bug ALPS00245352, it cost long time to restore messages.
            /// remove useless operation and add transaction while import sms. @{
            Xlog.d(TAG, "insert table body end");
            // if (!importSms){
                /// M: Code analyze 015, fix bug ALPS00234074, do not delete the thread
                /// when it is in writiing status.
                setThreadStatus(db, values, 0);
            // }
            /// @}
            // Don't use a trigger for updating the words table because of a bug
            // in FTS3.  The bug is such that the call to get the last inserted
            // row is incorrect.
            if (table == TABLE_SMS) {
                // Update the words table with a corresponding row.  The words table
                // allows us to search for words quickly, without scanning the whole
                // table;
                Xlog.d(TAG, "insert TABLE_WORDS begin");
                ContentValues cv = new ContentValues();
                cv.put(Telephony.MmsSms.WordsTable.ID, rowID);
                cv.put(Telephony.MmsSms.WordsTable.INDEXED_TEXT, values.getAsString("body"));
                cv.put(Telephony.MmsSms.WordsTable.SOURCE_ROW_ID, rowID);
                cv.put(Telephony.MmsSms.WordsTable.TABLE_ID, 1);
                db.insert(TABLE_WORDS, Telephony.MmsSms.WordsTable.INDEXED_TEXT, cv);
                Xlog.d(TAG, "insert TABLE_WORDS end");

            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            Xlog.d(TAG, "insert sms transacton end");
        }
        /// M: Code analyze 002, fix bug ALPS00262044, not show out unread message
        /// icon after restore messages. notify mms application about unread messages
        /// number after insert operation. @{
        if (rowID > 0) {
            Uri uri = Uri.parse("content://" + table + "/" + rowID);

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.d(TAG, "insertInner " + uri + " succeeded");
            }
            //now notify the launcher to show unread message.
            notifyChange(uri, true);

            Xlog.d(TAG, "insertInner succeed, uri = " + uri);
            return uri;
        } else {
            Log.e(TAG, "insertInner: failed! " + values.toString());
        }
        /// @}
        return null;
    }

    @Override
    public int delete(Uri url, String where, String[] whereArgs) {
        Xlog.d(TAG, "delete begin, uri = " + url + ", selection = " + where);

        int count = 0;
        int match = sURLMatcher.match(url);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        switch (match) {
            case SMS_ALL:
                Xlog.d(TAG, "Call delete case SMS_ALL");
                if (where != null && where.equals(FOR_MULTIDELETE)) {
                    Xlog.d(TAG, "delete FOR_MULTIDELETE");
                    String selectids = getSmsIdsFromArgs(whereArgs);
                    String threadQuery = String.format("SELECT DISTINCT thread_id FROM sms WHERE _id IN %s", selectids);
                    Cursor cursor = db.rawQuery(threadQuery, null);
                    /// M: fix ALPS01263429, consider cursor as view, we should read cursor before delete related records.
                    long[] deletedThreads = null;
                    try {
                        deletedThreads = new long[cursor.getCount()];
                        int i = 0;
                        while (cursor.moveToNext()) {
                            deletedThreads[i++] = cursor.getLong(0);
                        }
                    } finally {
                        cursor.close();
                    }
                    String finalSelection = String.format(" _id IN %s", selectids);
                    count = deleteMessages(db, finalSelection, null);
                    if (count != 0) {
                        MmsSmsDatabaseHelper.updateMultiThreads(db, deletedThreads);
                    }
                    Xlog.d(TAG, "delete FOR_MULTIDELETE count = " + count);
                } else {
                        Xlog.d(TAG, "SMS_ALL: where = " + where);
                        Cursor cursor = db.query(TABLE_SMS, new String[]{"distinct thread_id"}, where, whereArgs,
                                               null, null, null);
                        Xlog.d(TAG, "SMS_ALL: cursor = " + cursor);

                        long[] deletedThreads = null;
                        try {
                            deletedThreads = new long[cursor.getCount()];
                            int i = 0;
                            while (cursor.moveToNext()) {
                                deletedThreads[i++] = cursor.getLong(0);
                            }
                        } finally {
                            cursor.close();
                        }

                    count = db.delete(TABLE_SMS, where, whereArgs);
                         Xlog.d(TAG, "SMS_ALL: delete count = " + count);

                    if (count != 0) {
                             MmsSmsDatabaseHelper.updateMultiThreads(db, deletedThreads);
                    }
                }
                break;

            case SMS_ALL_ID:
                try {
                    int message_id = Integer.parseInt(url.getPathSegments().get(0));
                    count = MmsSmsDatabaseHelper.deleteOneSms(db, message_id);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                        "Bad message id: " + url.getPathSegments().get(0));
                }
                break;

            case SMS_CONVERSATIONS_ID:
                int threadID;

                try {
                    threadID = Integer.parseInt(url.getPathSegments().get(1));
                } catch (Exception ex) {
                    throw new IllegalArgumentException(
                            "Bad conversation thread id: "
                            + url.getPathSegments().get(1));
                }

                // delete the messages from the sms table
                where = DatabaseUtils.concatenateWhere("thread_id=" + threadID, where);
                count = db.delete(TABLE_SMS, where, whereArgs);
                MmsSmsDatabaseHelper.updateThread(db, threadID);
                break;
            /// M: Code analyze 011, fix bug ALPS00282321, ANR while delete old messages.
            /// use new process of delete. @{
            case SMS_AUTO_DELETE:

                try {
                    threadID = Integer.parseInt(url.getPathSegments().get(1));
                } catch (Exception ex) {
                    throw new IllegalArgumentException(
                            "Bad conversation thread id: "
                            + url.getPathSegments().get(1));
                }

                where = DatabaseUtils.concatenateWhere("thread_id=" + threadID, where);
                /// M: delete the messages from the sms table
                if (whereArgs != null) {
                    String selectids = getSmsIdsFromArgs(whereArgs);
                  //  Log.d(TAG, "selectids = "  + selectids);
                    db.execSQL("delete from words where table_to_use=1 and source_id in " + selectids);
                    Xlog.d(TAG, "delete words end");
                    for (int i = 0; i < whereArgs.length; ) {
                        if (i % 100 == 0) {
                            Xlog.d(TAG, "delete sms1 beginTransaction i = " + i);
                          // db.beginTransaction();
                        }
                        where = "_id=" + whereArgs[i];
                        count += db.delete(TABLE_SMS, where, null);
                        i++;
//                        if (i%100 == 0 || i == whereArgs.length){
//                            Xlog.d(TAG, "delete sms1 endTransaction i = " + i);
//                            db.endTransaction();
//                        }
                    }
                } else {
                    if (where != null) {
                        int id = 0;
                        String[] args = where.split("_id<");
                        if (args.length > 1) {
                            String finalid = args[1].replace(")", "");
                            Xlog.d(TAG, "SMS_CONVERSATIONS_ID args[1] = " + args[1]);
                            id = Integer.parseInt(finalid);
                            Xlog.d(TAG, "SMS_CONVERSATIONS_ID id = " + id);

                            for (int i = 1; i < id; i++) {
                                if (i % 30 == 0 || i == id - 1) {
                                    Xlog.d(TAG, "delete sms2 beginTransaction i = " + i);
                                    where = "locked=0 AND type<>3 AND ipmsg_id<=0 AND _id>" + (i - 30) + " AND _id<=" + i;
                                    where = DatabaseUtils.concatenateWhere("thread_id=" + threadID, where);
                                    count += db.delete(TABLE_SMS, where, null);
                                    Xlog.d(TAG, "delete sms2 endTransaction i = " + i + " count=" + count);
                                }
                            }
                        }
                    }
                }
                MmsSmsDatabaseHelper.updateThread(db, threadID);
                break;
            /// @}
            case SMS_RAW_MESSAGE:
                count = db.delete("raw", where, whereArgs);
                break;

            case SMS_STATUS_PENDING:
                count = db.delete("sr_pending", where, whereArgs);
                break;
            case SMS_ICC:
                String messageIndexString = url.getPathSegments().get(1);
                return deleteMessageFromIcc(messageIndexString, getSubIdFromUri(url));
            case SMS_ALL_ICC:
                int subId = getSubIdFromUri(url);
                Log.i(TAG, "Delete messages in subId = " + subId);
                if (where != null && where.equals(FOR_MULTIDELETE)) {
                    Xlog.d(TAG, "delete FOR_MULTIDELETE");
                    String message_id = "";
                    for (int i = 0; i < whereArgs.length; i++) {
                        if (whereArgs[i] != null) {
                            message_id = whereArgs[i];
                                Log.i(TAG, "Delete Sub" + (subId + 1) + " SMS id: " + message_id);
                                count += deleteMessageFromIcc(message_id, subId);
                        }
                    }
                } else {
                    count = deleteMessageFromIcc("-1", subId);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URL");
        }

        if (count > 0) {
            notifyChange(url, false);
        }
        Xlog.d(TAG, "delete end, count = " + count);
        return count;
    }

    protected static String getSmsIdsFromArgs(String[] selectionArgs) {
        StringBuffer content = new StringBuffer("(");
        String res = "";
        if (selectionArgs == null || selectionArgs.length < 1) {
            return "()";
        }
        for (int i = 0; i < selectionArgs.length - 1; i++) {
            if (selectionArgs[i] == null) {
                break;
            }
            content.append(selectionArgs[i]);
            content.append(",");
        }
        if (selectionArgs[selectionArgs.length - 1] != null) {
           content.append(selectionArgs[selectionArgs.length - 1]);
        }
        res = content.toString();
        if (res.endsWith(",")) {
            res = res.substring(0, res.lastIndexOf(","));
        }
        res += ")";
        return res;
    }
    /**
     * Delete the message at index from ICC.  Return true iff
     * successful.
     */

    private int deleteMessageFromIcc(String messageIndexString, int subId) {
        long token = Binder.clearCallingIdentity();
        try {
            return SmsManager.getSmsManagerForSubscriptionId(subId).deleteMessageFromIcc(
                    Integer.parseInt(messageIndexString))
                    ? 1 : 0;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Bad SMS ICC ID: " + messageIndexString);
        } finally {
            Binder.restoreCallingIdentity(token);

            ContentResolver cr = getContext().getContentResolver();

            cr.notifyChange(ICC_URI, null);
        }
    }

    @Override
    public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
        final int callerUid = Binder.getCallingUid();
        Xlog.d(TAG, "update begin, uri = " + url + ", values = " + values + ", selection = " + where);
        int count = 0;
        String table = TABLE_SMS;
        String extraWhere = null;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        switch (sURLMatcher.match(url)) {
            case SMS_RAW_MESSAGE:
                table = TABLE_RAW;
                break;

            case SMS_STATUS_PENDING:
                table = TABLE_SR_PENDING;
                break;

            case SMS_ALL:
            case SMS_FAILED:
            case SMS_QUEUED:
            case SMS_INBOX:
            case SMS_SENT:
            case SMS_DRAFT:
            case SMS_OUTBOX:
            case SMS_CONVERSATIONS:
                break;

            case SMS_ALL_ID:
                extraWhere = "_id=" + url.getPathSegments().get(0);
                break;

            case SMS_INBOX_ID:
            case SMS_FAILED_ID:
            case SMS_SENT_ID:
            case SMS_DRAFT_ID:
            case SMS_OUTBOX_ID:
                extraWhere = "_id=" + url.getPathSegments().get(1);
                break;

            case SMS_CONVERSATIONS_ID: {
                String threadId = url.getPathSegments().get(1);

                try {
                    Integer.parseInt(threadId);
                } catch (Exception ex) {
                    Log.e(TAG, "Bad conversation thread id: " + threadId);
                    break;
                }

                extraWhere = "thread_id=" + threadId;
                break;
            }

            case SMS_STATUS_ID:
                extraWhere = "_id=" + url.getPathSegments().get(1);
                break;

            default:
                throw new UnsupportedOperationException(
                        "URI " + url + " not supported");
        }

        if (table.equals(TABLE_SMS) && ProviderUtil.shouldRemoveCreator(values, callerUid)) {
            // CREATOR should not be changed by non-SYSTEM/PHONE apps
            Log.w(TAG, ProviderUtil.getPackageNamesByUid(getContext(), callerUid) +
                    " tries to update CREATOR");
            values.remove(Sms.CREATOR);
        }

        where = DatabaseUtils.concatenateWhere(where, extraWhere);
        count = db.update(table, values, where, whereArgs);

        if (count > 0) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.d(TAG, "update " + url + " succeeded");
            }
            Boolean notify = values.containsKey(Sms.READ);
            notifyChange(url, notify);
        }
        Xlog.d(TAG, "update end, affectedRows = " + count);
        return count;
    }

    private void notifyChange(Uri uri, boolean notify) {
        ContentResolver cr = getContext().getContentResolver();

        cr.notifyChange(uri, null, true, UserHandle.USER_ALL);
        cr.notifyChange(MmsSms.CONTENT_URI, null, true, UserHandle.USER_ALL);
        cr.notifyChange(Uri.parse("content://mms-sms/conversations/"), null, true,
                UserHandle.USER_ALL);
        if (notify) {
            Xlog.d(TAG, "notifyChange, notify unread change");
            MmsSmsProvider.notifyUnreadMessageNumberChanged(getContext());
        }
    }

    private SQLiteOpenHelper mOpenHelper;

    private final static String TAG = "Mms/Provider/Sms";
    private final static String VND_ANDROID_SMS = "vnd.android.cursor.item/sms";
    private final static String VND_ANDROID_SMSCHAT =
            "vnd.android.cursor.item/sms-chat";
    private final static String VND_ANDROID_DIR_SMS =
            "vnd.android.cursor.dir/sms";

    private static final HashMap<String, String> sConversationProjectionMap =
            new HashMap<String, String>();
    private static final String[] sIDProjection = new String[] { "_id" };

    private static final int SMS_ALL = 0;
    private static final int SMS_ALL_ID = 1;
    private static final int SMS_INBOX = 2;
    private static final int SMS_INBOX_ID = 3;
    private static final int SMS_SENT = 4;
    private static final int SMS_SENT_ID = 5;
    private static final int SMS_DRAFT = 6;
    private static final int SMS_DRAFT_ID = 7;
    private static final int SMS_OUTBOX = 8;
    private static final int SMS_OUTBOX_ID = 9;
    private static final int SMS_CONVERSATIONS = 10;
    private static final int SMS_CONVERSATIONS_ID = 11;
    private static final int SMS_RAW_MESSAGE = 15;
    private static final int SMS_ATTACHMENT = 16;
    private static final int SMS_ATTACHMENT_ID = 17;
    private static final int SMS_NEW_THREAD_ID = 18;
    private static final int SMS_QUERY_THREAD_ID = 19;
    private static final int SMS_STATUS_ID = 20;
    private static final int SMS_STATUS_PENDING = 21;
    private static final int SMS_ALL_ICC = 22;
    private static final int SMS_ICC = 23;
    private static final int SMS_FAILED = 24;
    private static final int SMS_FAILED_ID = 25;
    private static final int SMS_QUEUED = 26;
    private static final int SMS_UNDELIVERED = 27;
    /// M: Code analyze 010, new feature, support for gemini. @{
//    private static final int SMS_ALL_ICC_GEMINI = 28;
//    private static final int SMS_ALL_ICC_THREE = 29;
//    private static final int SMS_ALL_ICC_FOUR = 30;
    /// @}
    /// M: Code analyze 011, fix bug ALPS00282321, ANR while delete old messages.
    /// use new process of delete. @{
    private static final int SMS_ALL_THREADID = 34;
    private static final int SMS_AUTO_DELETE  = 35;
    /// @}

    /// M: New Feature The international card.
    private static final int SMS_ALL_ICC_INTERNATIONAL = 36;
    private static final int SMS_ICC_INTERNATIONAL = 37;
    private static final int SMS_ALL_ICC_GEMINI_INTERNATIONAL = 38;
    private static final int SMS_ICC_GEMINI_INTERNATIONAL = 39;

    private static final int URI_THREAD_ID = 40;
    private static final UriMatcher sURLMatcher =
            new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sURLMatcher.addURI("sms", null, SMS_ALL);
        sURLMatcher.addURI("sms", "#", SMS_ALL_ID);
        sURLMatcher.addURI("sms", "inbox", SMS_INBOX);
        sURLMatcher.addURI("sms", "inbox/#", SMS_INBOX_ID);
        sURLMatcher.addURI("sms", "sent", SMS_SENT);
        sURLMatcher.addURI("sms", "sent/#", SMS_SENT_ID);
        sURLMatcher.addURI("sms", "draft", SMS_DRAFT);
        sURLMatcher.addURI("sms", "draft/#", SMS_DRAFT_ID);
        sURLMatcher.addURI("sms", "outbox", SMS_OUTBOX);
        sURLMatcher.addURI("sms", "outbox/#", SMS_OUTBOX_ID);
        sURLMatcher.addURI("sms", "undelivered", SMS_UNDELIVERED);
        sURLMatcher.addURI("sms", "failed", SMS_FAILED);
        sURLMatcher.addURI("sms", "failed/#", SMS_FAILED_ID);
        sURLMatcher.addURI("sms", "queued", SMS_QUEUED);
        sURLMatcher.addURI("sms", "conversations", SMS_CONVERSATIONS);
        sURLMatcher.addURI("sms", "conversations/*", SMS_CONVERSATIONS_ID);
        sURLMatcher.addURI("sms", "raw", SMS_RAW_MESSAGE);
        sURLMatcher.addURI("sms", "attachments", SMS_ATTACHMENT);
        sURLMatcher.addURI("sms", "attachments/#", SMS_ATTACHMENT_ID);
        sURLMatcher.addURI("sms", "threadID", SMS_NEW_THREAD_ID);
        sURLMatcher.addURI("sms", "threadID/*", SMS_QUERY_THREAD_ID);
        sURLMatcher.addURI("sms", "status/#", SMS_STATUS_ID);
        sURLMatcher.addURI("sms", "sr_pending", SMS_STATUS_PENDING);
        sURLMatcher.addURI("sms", "icc", SMS_ALL_ICC);
        sURLMatcher.addURI("sms", "icc/#", SMS_ICC);
        //we keep these for not breaking old applications
        sURLMatcher.addURI("sms", "sim", SMS_ALL_ICC);
        sURLMatcher.addURI("sms", "sim/#", SMS_ICC);
//        /// M: Code analyze 010, new feature, support for gemini. @{
//        sURLMatcher.addURI("sms", "icc2", SMS_ALL_ICC_GEMINI);
//        /// M: we keep these for not breaking old applications
//        sURLMatcher.addURI("sms", "sim2", SMS_ALL_ICC_GEMINI);
//        sURLMatcher.addURI("sms", "icc3", SMS_ALL_ICC_THREE);
//        sURLMatcher.addURI("sms", "sim3", SMS_ALL_ICC_THREE);
//        sURLMatcher.addURI("sms", "icc4", SMS_ALL_ICC_FOUR);
//        sURLMatcher.addURI("sms", "sim4", SMS_ALL_ICC_FOUR);
        /// @}
        /// M: Code analyze 011, fix bug ALPS00282321, ANR while delete old messages.
        /// use new process of delete. @{
        sURLMatcher.addURI("sms", "all_threadid", SMS_ALL_THREADID);
        sURLMatcher.addURI("sms", "auto_delete/#", SMS_AUTO_DELETE);
        /// M: New Feature The international card.
        sURLMatcher.addURI("sms", "icc_international", SMS_ALL_ICC_INTERNATIONAL);
        sURLMatcher.addURI("sms", "icc_international/#", SMS_ICC_INTERNATIONAL);
        sURLMatcher.addURI("sms", "icc2_international", SMS_ALL_ICC_GEMINI_INTERNATIONAL);
        sURLMatcher.addURI("sms", "icc2_international/#", SMS_ICC_GEMINI_INTERNATIONAL);
        /// @}
        sURLMatcher.addURI("sms", "thread_id", URI_THREAD_ID);
        sConversationProjectionMap.put(Sms.Conversations.SNIPPET,
            "sms.body AS snippet");
        sConversationProjectionMap.put(Sms.Conversations.THREAD_ID,
            "sms.thread_id AS thread_id");
        sConversationProjectionMap.put(Sms.Conversations.MESSAGE_COUNT,
            "groups.msg_count AS msg_count");
        sConversationProjectionMap.put("delta", null);
    }
    /// M: Code analyze 007, fix bug ALPS00042403, should show the sender's number
    /// in manage SIM card. show concatenation sms in one bubble, set incoming sms
    /// on left and sent sms on right, display sender information for every sms. @{
    private Object[] convertIccToSms(SmsMessage message, int id, int subId) {
        return convertIccToSms(message, null, id, subId);
    }
    /// @}

    /// M: New Feature The international card.
    private Cursor getAllMessagesFromIccInternational(Uri url, int slotId) {
        long token = Binder.clearCallingIdentity();
        int[] subIds = SubscriptionManager.getSubIdUsingSlotId(slotId);
        if (subIds == null || subIds.length == 0) {
            Xlog.e(TAG, "getAllMessagesFromIccInternational subIds is null or length = 0");
            return null;
        }
        MatrixCursor mc = null;
        try {
            SmsManager smsManager =
                    SmsManager.getSmsManagerForSubscriptionId(subIds[0]);
            if (isInternationalCard(slotId)) {
                int activateMode = TelephonyManagerEx.getDefault().getPhoneType(0);
                ArrayList<SmsMessage> messagesActivate = null;
                ArrayList<SmsMessage> messagesNotActivate = null;
                if (activateMode == TelephonyManager.PHONE_TYPE_GSM) {
                    messagesActivate = smsManager.getAllMessagesFromIccEfByMode(
                            TelephonyManager.PHONE_TYPE_GSM);
                    messagesNotActivate = smsManager.getAllMessagesFromIccEfByMode(
                            TelephonyManager.PHONE_TYPE_CDMA);
                } else {
                    messagesActivate = smsManager.getAllMessagesFromIccEfByMode(
                            TelephonyManager.PHONE_TYPE_CDMA);
                    messagesNotActivate = smsManager.getAllMessagesFromIccEfByMode(
                            TelephonyManager.PHONE_TYPE_GSM);
                }
                mc = getAllMessagesForInternationalCardMatrix(slotId,
                         url,
                         messagesActivate,
                         messagesNotActivate);
                mIsInternationalCardNotActivate = false;
                if (mc != null) {
                    mc.setNotificationUri(getContext().getContentResolver(),
                            ICC_URI_INTERNATIONAL);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
            return mc;
        }
    }

    private boolean isInternationalCard(int slotId) {
        IntentFilter intentFilter =
                new IntentFilter(TelephonyIntents.ACTION_CDMA_CARD_TYPE);
        Intent intent = getContext().registerReceiver(null, intentFilter);
        if (intent == null) {
            Xlog.d(TAG, "[isInternationalCard]:failed. intent == null;");
            return false;
        }
        Bundle bundle = intent.getExtras();
        IccCardConstants.CardType cardType = (IccCardConstants.CardType) bundle
                .get(TelephonyIntents.INTENT_KEY_CDMA_CARD_TYPE);
        boolean isDualSim = cardType == IccCardConstants.CardType.CT_UIM_SIM_CARD;
        Xlog.d(TAG, "[isInternationalCard]:" + isDualSim);
        return isDualSim;
    }

    private MatrixCursor getAllMessagesForInternationalCardMatrix(int subId,
            Uri url,
            ArrayList<SmsMessage> messagesActivate,
            ArrayList<SmsMessage> messagesNotActivate) {
        if (messagesActivate == null || messagesActivate.isEmpty()) {
            Xlog.e(TAG, "getAllMessagesFromIccInternational messagesActivate is null");
            if (messagesNotActivate == null ||
                    messagesNotActivate.isEmpty()) {
                return null;
            } else {
                mIsInternationalCardNotActivate = true;
                return getAllMessagesForInternationalCard(
                        subId,
                        url,
                        messagesNotActivate);
            }
        } else {
            if (messagesNotActivate == null || messagesNotActivate.isEmpty()) {
                mIsInternationalCardNotActivate = false;
                return getAllMessagesForInternationalCard(
                        subId,
                        url,
                        messagesActivate);
            } else {
                ArrayList<String> concatSmsIndexAndBody = null;
                int countActivate = messagesActivate.size();
                int countNotActivate = messagesNotActivate.size();
                MatrixCursor cursor = new MatrixCursor(
                        ICC_COLUMNS,
                        countActivate + countNotActivate);
                boolean showInOne = "1".equals(url.getQueryParameter("showInOne"));
                mIsInternationalCardNotActivate = true;
                for (int i = 0; i < countNotActivate; i++) {
                    concatSmsIndexAndBody = null;
                    SmsMessage message = messagesNotActivate.get(i);
                    if (message != null) {
                        if (showInOne) {
                            SmsHeader smsHeader = message.getUserDataHeader();
                            if (null != smsHeader && null != smsHeader.concatRef) {
                                concatSmsIndexAndBody =
                                    getConcatSmsIndexAndBody(messagesNotActivate, i);
                            }
                        }
                        cursor.addRow(convertIccToSms(message,
                                 concatSmsIndexAndBody,
                                 i,
                                 subId));
                    }
                }
                mIsInternationalCardNotActivate = false;
                for (int i = 0; i < countActivate; i++) {
                    concatSmsIndexAndBody = null;
                    SmsMessage message = messagesActivate.get(i);
                    if (message != null) {
                        if (showInOne) {
                            SmsHeader smsHeader = message.getUserDataHeader();
                            if (null != smsHeader && null != smsHeader.concatRef) {
                                concatSmsIndexAndBody =
                                    getConcatSmsIndexAndBody(messagesActivate, i);
                            }
                        }
                        cursor.addRow(convertIccToSms(message,
                                 concatSmsIndexAndBody,
                                 i + countNotActivate,
                                 subId));
                    }
                }
                return cursor;
            }
        }
    }

    private MatrixCursor getAllMessagesForInternationalCard(int subId,
            Uri url,
            ArrayList<SmsMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            Xlog.e(TAG, "getAllMessagesFromIccInternational messages is null");
            return null;
        }
        ArrayList<String> concatSmsIndexAndBody = null;
        int count = messages.size();
        MatrixCursor cursor = new MatrixCursor(ICC_COLUMNS, count);
        boolean showInOne = "1".equals(url.getQueryParameter("showInOne"));

        for (int i = 0; i < count; i++) {
            concatSmsIndexAndBody = null;
            SmsMessage message = messages.get(i);
            if (message != null) {
                if (showInOne) {
                    SmsHeader smsHeader = message.getUserDataHeader();
                    if (null != smsHeader && null != smsHeader.concatRef) {
                        concatSmsIndexAndBody = getConcatSmsIndexAndBody(messages, i);
                    }
                }
                cursor.addRow(convertIccToSms(message,
                        concatSmsIndexAndBody,
                        i,
                        subId));
            }
        }
        return cursor;
    }

    /// @}
    /// M: Code analyze 010, new feature, support for gemini.
    /// fix bug ALPS00042403, show the sender's number in manage SIM card. @{
    private Cursor withIccNotificationUri(Cursor cursor, long subId) {
     // @yanlin will fix later
//        if(subId == PhoneConstants.SIM_ID_1) {
//            cursor.setNotificationUri(getContext().getContentResolver(), ICC_URI);
//        }
//        else {
            cursor.setNotificationUri(getContext().getContentResolver(), Uri.parse("content://sms/" + subId + "/icc"));
//        }
        return cursor;
    }
    /// @}
    /// M: Code analyze 007, fix bug ALPS00042403, should show the sender's number
    /// in manage SIM card. show concatenation sms in one bubble, set incoming sms
    /// on left and sent sms on right, display sender information for every sms. @{
    private ArrayList<String> getConcatSmsIndexAndBody(ArrayList<SmsMessage> messages, int index) {
        int totalCount = messages.size();
        int refNumber = 0;
        int msgCount = 0;
        ArrayList<String> indexAndBody = new ArrayList<String>();
        StringBuilder smsIndex = new StringBuilder();
        StringBuilder smsBody = new StringBuilder();
        ArrayList<SmsMessage> concatMsg = null;
        SmsMessage message = messages.get(index);
        if (message != null) {
            SmsHeader smsHeader = message.getUserDataHeader();
            if (null != smsHeader && null != smsHeader.concatRef) {
                msgCount = smsHeader.concatRef.msgCount;
                refNumber = smsHeader.concatRef.refNumber;
            }
        }

        concatMsg = new ArrayList<SmsMessage>();
        concatMsg.add(message);

        for (int i = index + 1; i < totalCount; i++) {
            SmsMessage sms = messages.get(i);
            if (sms != null) {
                SmsHeader smsHeader = sms.getUserDataHeader();
                if (null != smsHeader && null != smsHeader.concatRef && refNumber == smsHeader.concatRef.refNumber) {
                    concatMsg.add(sms);
                    messages.set(i, null);
                    if (msgCount == concatMsg.size()) {
                        break;
                    }
                }
            }
        }

        int concatCount = concatMsg.size();
        for (int k = 0; k < msgCount; k++) {
            for (int j = 0; j < concatCount; j++) {
                SmsMessage sms = concatMsg.get(j);
                SmsHeader smsHeader = sms.getUserDataHeader();
                if (k == smsHeader.concatRef.seqNumber - 1) {
                    if (mIsInternationalCardNotActivate) {
                        try {
                            smsIndex.append((message.getIndexOnIcc() ^ (0x01 << 10)) + "");
                        } catch (NumberFormatException e) {
                            Xlog.e(TAG, "concatSmsIndex bad number");
                        }
                    } else {
                        smsIndex.append(sms.getIndexOnIcc());
                    }
                    smsIndex.append(";");
                    smsBody.append(sms.getDisplayMessageBody());
                    break;
                }
            }
        }

        Xlog.d(TAG, "concatenation sms index:" + smsIndex.toString());
        Xlog.d(TAG, "concatenation sms body:" + smsBody.toString());
        indexAndBody.add(smsIndex.toString());
        indexAndBody.add(smsBody.toString());

        return indexAndBody;
    }
    /// @}

    /// M: Code analyze 006, fix bug ALPS00252799, it cost long time to restore messages.
    /// support batch processing while restore messages. @{
    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        int ypCount = 0;
        int opCount = 0;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        /// M: Fix ALPS00288517, not use transaction again to avoid ANR because of order of locking db
        db.beginTransaction();
        /// @}
        try {
            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            for (int i = 0; i < numOperations; i++) {
                if (++opCount > MAX_OPERATIONS_PER_PATCH) {
                    throw new OperationApplicationException(
                            "Too many content provider operations between yield points. "
                                    + "The maximum number of operations per yield point is "
                                    + MAX_OPERATIONS_PER_PATCH, ypCount);
                }
                final ContentProviderOperation operation = operations.get(i);
                results[i] = operation.apply(this, results, i);
            }
            /// M: Fix ALPS00288517, not use transaction again to avoid ANR because of order of locking db
            db.setTransactionSuccessful();
            /// @}
            return results;
        } finally {
            /// M: Fix ALPS00288517, not use transaction again to avoid ANR because of order of locking db
            db.endTransaction();
            /// @}
        }
    }
    /// @}
    /// M: Code analyze 015, fix bug ALPS00234074, do not delete the thread
    /// when it is in writiing status. @{
    private void setThreadStatus(SQLiteDatabase db, ContentValues values, int value) {
        ContentValues statusContentValues = new ContentValues(1);
        statusContentValues.put(Telephony.Threads.STATUS, value);
        db.update("threads", statusContentValues, "_id=" + values.getAsLong(Sms.THREAD_ID), null);
    }
    /// @}
    /// M: Code analyze 011, fix bug ALPS00282321, ANR while delete old messages.
    /// use new process of delete. @{
    private Cursor getAllSmsThreadIds(String selection, String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        return db.query("sms",  new String[] {"distinct thread_id"},
                selection, selectionArgs, null, null, null);
    }
    /// @}
    /// M: Code analyze 005, fix bug ALPS00245352, it cost long time to restore messages.
    /// remove useless operation and add transaction while import sms. @{
    private long getThreadIdInternal(String recipient, SQLiteDatabase db, boolean importSms) {
        String THREAD_QUERY;
        if (SystemProperties.get("ro.mtk_wappush_support").equals("1") == true) {
            THREAD_QUERY = "SELECT _id FROM threads " + "WHERE type<>"
                    + Telephony.Threads.WAPPUSH_THREAD + " AND type<>"
                    + Telephony.Threads.CELL_BROADCAST_THREAD + " AND recipient_ids=?";
        } else {
            THREAD_QUERY = "SELECT _id FROM threads " + "WHERE type<>"
                    + Telephony.Threads.CELL_BROADCAST_THREAD + " AND recipient_ids=?";
        }
        long recipientId = 0;
        if (importSms) {
            recipientId = fastGetRecipientId(recipient, db);
        } else {
            recipientId = getRecipientId(recipient, db);
        }
        Xlog.d(TAG, "sms insert, getThreadIdInternal, recipientId = " + recipientId);
        String[] selectionArgs = new String[] { String.valueOf(recipientId) };
        Cursor cursor = db.rawQuery(THREAD_QUERY, selectionArgs);
        try {
              if (cursor != null && cursor.getCount() == 0) {
                   if (Build.TYPE.equals("eng")) {
                       Log.d(TAG, "getThreadId: create new thread_id for recipients " + recipient);
                   }
                   return insertThread(recipientId, db);
               } else if (cursor.getCount() == 1) {
                      if (cursor.moveToFirst()) {
                       return cursor.getLong(0);
                   }
               } else {
                   Log.w(TAG, "getThreadId: why is cursorCount=" + cursor.getCount());
               }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return 0;
    }

    private Cursor getThreadIdWithoutInsert(String recipient, SQLiteDatabase db) {
        String THREAD_QUERY;
        if (SystemProperties.get("ro.mtk_wappush_support").equals("1") == true) {
            THREAD_QUERY = "SELECT _id FROM threads " + "WHERE type<>"
                    + Telephony.Threads.WAPPUSH_THREAD + " AND type<>"
                    + Telephony.Threads.CELL_BROADCAST_THREAD + " AND recipient_ids=?";
        } else {
            THREAD_QUERY = "SELECT _id FROM threads " + "WHERE type<>"
                    + Telephony.Threads.CELL_BROADCAST_THREAD + " AND recipient_ids=?";
        }
        long recipientId = getSingleAddressId(recipient, db, false);
        Xlog.d(TAG, "getThreadIdWithoutInsert, recipientId = " + recipientId);
        if (recipientId != -1L) {
            String[] selectionArgs = new String[] { String.valueOf(recipientId) };
            return db.rawQuery(THREAD_QUERY, selectionArgs);
        }
        return null;
    }

    /**
     * Insert a record for a new thread.
     */
    private long insertThread(long recipientIds, SQLiteDatabase db) {
        ContentValues values = new ContentValues(4);

        long date = System.currentTimeMillis();
        values.put(ThreadsColumns.DATE, date - date % 1000);
        values.put(ThreadsColumns.RECIPIENT_IDS, recipientIds);
        values.put(ThreadsColumns.MESSAGE_COUNT, 0);
        return db.insert("threads", null, values);
    }
    private long getRecipientId(String address, SQLiteDatabase db) {
         if (!address.equals(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR)) {
             long id = getSingleAddressId(address, db, true);
             if (id != -1L) {
                 return id;
             } else {
                 Log.e(TAG, "getAddressIds: address ID not found for " + address);
             }
         }
         return 0;
    }

    private long fastGetRecipientId(String address, SQLiteDatabase db) {
        if (!address.equals(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR)) {
            long id = -1L;
            String escapedAddress = DatabaseUtils.sqlEscapeString(address);
            boolean useStrictPhoneNumberComparation =
                    getContext().getResources().getBoolean(
                            com.android.internal.R.bool.config_use_strict_phone_number_comparation);
            String selection = "(address=" + escapedAddress + " OR PHONE_NUMBERS_EQUAL(address, " +
                    escapedAddress + (useStrictPhoneNumberComparation ? ", 1))" : ", 0))");
            Cursor cursor = db.query(
                    "canonical_addresses", CANONICAL_ADDRESSES_COLUMNS_2,
                    selection, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    id = cursor.getLong(cursor.getColumnIndex("_id"));
                    Log.d(TAG, "fastGetRecipientId, id=" + id);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            if (id != -1L) {
                return id;
            } else {
                Log.e(TAG, "fastGetRecipientId: address ID not found for " + address);
                return insertCanonicalAddresses(db, address);
            }
        }
        return 0;
    }
    /**
     * Return the canonical address ID for this address.
     */
    private long getSingleAddressId(String address, SQLiteDatabase db, boolean needInsert) {
        long retVal = -1L;
        HashMap<String, Long> addressesMap = new HashMap<String, Long>();
        HashMap<String, ArrayList<String>> addressKeyMap = new HashMap<String, ArrayList<String>>();
        String key = "";
        ArrayList<String> candidates = null;
        Cursor cursor = null;
        try {
            cursor = db.query(
                    "canonical_addresses", CANONICAL_ADDRESSES_COLUMNS_2,
                    null, null, null, null, null);

            if (cursor != null) {
                long id;
                String number = "";
                while (cursor.moveToNext()) {
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(CanonicalAddressesColumns._ID));
                    number = cursor.getString(cursor.getColumnIndexOrThrow(CanonicalAddressesColumns.ADDRESS));
                    CharBuffer keyBuffer = CharBuffer.allocate(MmsSmsProvider.STATIC_KEY_BUFFER_MAXIMUM_LENGTH);
                    /// M: for ALPS01840116, ignore char case if it is email address. @{
                    if (Mms.isEmailAddress(number)) {
                        number = number.toLowerCase();
                    }
                    /// @}
                    key = MmsSmsProvider.key(number, keyBuffer);
                    candidates = addressKeyMap.get(key);
                    if (candidates == null) {
                        candidates = new ArrayList<String>();
                        addressKeyMap.put(key, candidates);
                    }
                    candidates.add(number);
                    addressesMap.put(number, id);
                }
            }

            boolean isEmail = Mms.isEmailAddress(address);
            boolean isPhoneNumber = Mms.isPhoneNumber(address);
            String refinedAddress = isEmail ? address.toLowerCase() : address;
            CharBuffer keyBuffer = CharBuffer.allocate(MmsSmsProvider.STATIC_KEY_BUFFER_MAXIMUM_LENGTH);
            key = MmsSmsProvider.key(refinedAddress, keyBuffer);
            candidates = addressKeyMap.get(key);
            String addressValue = "";
            if (candidates != null) {
                for (int i = 0; i < candidates.size(); i++) {
                    addressValue = candidates.get(i);
                    if (addressValue.equals(refinedAddress)) {
                        retVal = addressesMap.get(addressValue);
                        break;
                    }
                    if (isPhoneNumber && (refinedAddress != null && refinedAddress.length() <= NORMAL_NUMBER_MAX_LENGTH)
                            && (addressValue != null && addressValue.length() <= NORMAL_NUMBER_MAX_LENGTH)) {
                        boolean useStrictPhoneNumberComparation = getContext().getResources().getBoolean(
                                com.android.internal.R.bool.config_use_strict_phone_number_comparation);

                        if (PhoneNumberUtils.compare(refinedAddress, addressValue,
                                useStrictPhoneNumberComparation)) {
                            retVal = addressesMap.get(addressValue);
                            break;
                        }  //add by wanghui russian b
                        else{
                             if(SystemProperties.get("ro.hq.russian.number.combin").equals("1")){
                                 String temp_refinedAddress = null;
                                 String temp_addressValue = null;
                                 //MmsLog.d(TAG, "refinedAddress2:"+refinedAddress);
                                 //MmsLog.d(TAG, "addressValue2:"+addressValue);
                                 if((refinedAddress.startsWith("+7"))&&(addressValue.startsWith("8"))&&(refinedAddress.length()== (addressValue.length()+1))){
                                       temp_refinedAddress = refinedAddress.replace("+7", "8");
                                       temp_addressValue = addressValue;
                                 } else if((addressValue.startsWith("+7"))&&(refinedAddress.startsWith("8"))&&(addressValue.length()== (refinedAddress.length()+1))){
                                             temp_refinedAddress = refinedAddress;
                                             temp_addressValue = addressValue.replace("+7", "8");
                                   } else if((addressValue.startsWith("+375"))&&(refinedAddress.startsWith("80"))&&(addressValue.length()== (refinedAddress.length()+2))){
                                                temp_refinedAddress = refinedAddress;
                                                temp_addressValue = addressValue.replace("+375", "80");
                                     } else if((refinedAddress.startsWith("+375"))&&(addressValue.startsWith("80"))&&(refinedAddress.length()== (addressValue.length()+2))){
                                                temp_refinedAddress = refinedAddress.replace("+375", "80");
                                                temp_addressValue = addressValue;
                                       }
                                        //MmsLog.d(TAG, "temp_refinedAddress2:"+temp_refinedAddress);
                                       // MmsLog.d(TAG, "temp_addressValue2:"+temp_addressValue);
                                        if (temp_refinedAddress != null&&temp_addressValue != null){
                                            if (PhoneNumberUtils.compare(temp_refinedAddress, temp_addressValue, useStrictPhoneNumberComparation)){
                                                 //MmsLog.d(TAG, "PhoneNumberUtils.compare return true");
                                                 retVal = addressesMap.get(addressValue);
                                                 //Log.d(TAG, "retVal:"+retVal);
                                                  break;
                                             }
                                        }
                              }
                          }////add by wanghui russian e
                    }
                }
            }
            if (!needInsert) {
                return retVal;
            }
            if (retVal == -1L) {
                retVal = insertCanonicalAddresses(db, address);
                if (Build.TYPE.equals("eng")) {
                    Xlog.d(TAG, "getSingleAddressId: insert new canonical_address for " +
                            /*address*/ "xxxxxx" + ", addressess = " + address.toString());
                }
            } else {
                if (Build.TYPE.equals("eng")) {
                    Xlog.d(TAG, "getSingleAddressId: get exist id=" + retVal + ", address="
                            + address + ", currentNumber=" + addressValue);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return retVal;
    }

    private long insertCanonicalAddresses(SQLiteDatabase db, String refinedAddress) {
        if (Build.TYPE.equals("eng")) {
            Xlog.d(TAG, "sms insert insertCanonicalAddresses for address = " + refinedAddress);
        }
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(CanonicalAddressesColumns.ADDRESS, refinedAddress);
        return db.insert("canonical_addresses", CanonicalAddressesColumns.ADDRESS, contentValues);
    }
    /// @}

    /// M: Code analyze 003, fix bug ALPS00239521, ALPS00244682, improve multi-delete speed
    /// in folder mode. @{
    /// M: get the select id from sms to delete words
    private String getWordIds(SQLiteDatabase db, String boxType, String selectionArgs) {
        StringBuffer content = new StringBuffer("(");
        String res = "";
        String rawQuery = String.format("select _id from sms where _id NOT IN (select _id from sms where type IN %s AND _id NOT IN %s)", boxType, selectionArgs);
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(rawQuery, null);
            if (cursor == null || cursor.getCount() == 0) {
                return "()";
            }
            if (cursor.moveToFirst()) {
                do {
                    content.append(cursor.getInt(0));
                    content.append(",");
                } while (cursor.moveToNext());
                res = content.toString();
                if (!TextUtils.isEmpty(content) && res.endsWith(",")) {
                    res = res.substring(0, res.lastIndexOf(","));
                }
                res += ")";
            }
            Xlog.d(TAG, "getWordIds cursor content = " + res + " COUNT " + cursor.getCount());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return res;
    }
    /// @}

    /// M: because of triggers on sms and pdu, delete a large number of sms/pdu through an
    /// atomic operation will cost too much time. To avoid blocking other database operation,
    /// remove trigger sms_update_thread_on_delete, and set a limit to each delete operation. @{
    private static final int DELETE_LIMIT = 100;

    static int deleteMessages(SQLiteDatabase db,
            String selection, String[] selectionArgs) {
        Xlog.d(TAG, "deleteMessages, start");
        int deleteCount = DELETE_LIMIT;
        if (TextUtils.isEmpty(selection)) {
            selection = "_id in (select _id from sms limit " + DELETE_LIMIT + ")";
        } else {
            selection = "_id in (select _id from sms where " + selection + " limit " + DELETE_LIMIT + ")";
        }
        int count = 0;
        while (deleteCount > 0) {
            deleteCount = db.delete(TABLE_SMS, selection, selectionArgs);
            count += deleteCount;
            Xlog.d(TAG, "deleteMessages, delete " + deleteCount + " sms");
        }
        Log.d(TAG, "deleteMessages, delete sms end");
        return count;
    }
    /// @}

    public static int getSubIdFromUri(Uri uri) {
        String subIdStr = uri.getQueryParameter(PhoneConstants.SUBSCRIPTION_KEY);
        int subId = SubscriptionManager.getDefaultSubId();
        try {
            subId = Integer.valueOf(subIdStr);
        } catch (NumberFormatException e) {
            Log.d(TAG, "getSubIdFromUri : " + e);
        }
        return subId;
    }
}
