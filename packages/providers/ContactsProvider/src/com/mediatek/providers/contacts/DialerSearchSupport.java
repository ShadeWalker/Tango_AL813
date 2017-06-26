package com.mediatek.providers.contacts;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;

import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DialerSearch;
import android.provider.ContactsContract.Preferences;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.ImsCall;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import com.android.providers.contacts.ContactsDatabaseHelper;
import com.android.providers.contacts.ContactsDatabaseHelper.RawContactsColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.SearchIndexColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.Tables;
import com.android.providers.contacts.ContactsDatabaseHelper.Views;
import com.android.providers.contacts.util.UserUtils;
import com.android.providers.contacts.ContactsProvider2;
import com.android.providers.contacts.HanziToPinyin;
import com.mediatek.providers.contacts.LogUtils;
import com.mediatek.providers.contacts.ContactsProviderUtils;
import com.mediatek.providers.contacts.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import android.os.Build;


public class DialerSearchSupport {

    private static final String TAG = "DialerSearchSupport";
    private static final boolean DS_DBG = ContactsProviderUtils.DBG_DIALER_SEARCH;
    private static final String DATA_READY_FLAG = "isDataReady";
    private boolean mIsDataInit = false;

    public interface DialerSearchLookupColumns {
        public static final String _ID = BaseColumns._ID;
        public static final String RAW_CONTACT_ID = "raw_contact_id";
        public static final String DATA_ID = "data_id";
        public static final String NORMALIZED_NAME = "normalized_name";
        public static final String NAME_TYPE = "name_type";
        public static final String CALL_LOG_ID = "call_log_id";
        public static final String NUMBER_COUNT = "number_count";
        public static final String SEARCH_DATA_OFFSETS = "search_data_offsets";
        public static final String NORMALIZED_NAME_ALTERNATIVE = "normalized_name_alternative";
        public static final String SEARCH_DATA_OFFSETS_ALTERNATIVE = "search_data_offsets_alternative";
        public static final String IS_VISIABLE = "is_visiable";
        public static final String SORT_KEY = "sort_key";
        public static final String TIMES_USED = "times_used";
    }

    public final static class DialerSearchLookupType {
        public static final int PHONE_EXACT = 8;
        public static final int NO_NAME_CALL_LOG = 8;
        public static final int NAME_EXACT = 11;
    }

    public interface DialerSearchQuery {
        String TABLE = Tables.DIALER_SEARCH;
        String[] COLUMNS = new String[] {
                DialerSearch.NAME_LOOKUP_ID,
                DialerSearch.CONTACT_ID,
                DialerSearchLookupColumns.DATA_ID,
                DialerSearch.CALL_DATE,
                DialerSearch.CALL_LOG_ID,
                DialerSearch.CALL_TYPE,
                DialerSearch.CALL_GEOCODED_LOCATION,
                DialerSearch.PHONE_ACCOUNT_ID,
                DialerSearch.PHONE_ACCOUNT_COMPONENT_NAME,
                DialerSearch.NUMBER_PRESENTATION,
                DialerSearch.INDICATE_PHONE_SIM,
                DialerSearch.CONTACT_STARRED,
                DialerSearch.PHOTO_ID,
                DialerSearch.SEARCH_PHONE_TYPE,
                DialerSearch.SEARCH_PHONE_LABEL,
                DialerSearch.NAME,
                DialerSearch.SEARCH_PHONE_NUMBER,
                DialerSearch.CONTACT_NAME_LOOKUP,
                DialerSearch.IS_SDN_CONTACT,
                DialerSearch.MATCHED_DATA_OFFSET,
                DialerSearch.MATCHED_NAME_OFFSET
        };

        /// M: fix CR:ALPS01563203,SDN icon not show lock icon in Dialer.
        public static final int NAME_LOOKUP_ID_INDEX = 0;
        public static final int CONTACT_ID_INDEX = 1;
        public static final int DATA_ID_INDEX = 2;
        public static final int CALL_LOG_DATE_INDEX = 3;
        public static final int CALL_LOG_ID_INDEX = 4;
        public static final int CALL_TYPE_INDEX = 5;
        public static final int CALL_GEOCODED_LOCATION_INDEX = 6;
        public static final int PHONE_ACCOUNT_ID = 7;
        public static final int PHONE_ACCOUNT_COMPONENT_NAME = 8;
        public static final int PRESENTATION = 9;
        public static final int INDICATE_PHONE_SIM_INDEX = 10;
        public static final int CONTACT_STARRED_INDEX = 11;
        public static final int PHOTO_ID_INDEX = 12;
        public static final int SEARCH_PHONE_TYPE_INDEX = 13;
        public static final int NUMBER_LABEL = 14;
        public static final int NAME_INDEX = 15;
        public static final int SEARCH_PHONE_NUMBER_INDEX = 16;
        public static final int CONTACT_NAME_LOOKUP_INDEX = 17;
        public static final int IS_SDN_CONTACT = 18;
        public static final int DS_MATCHED_DATA_OFFSETS = 19;
        public static final int DS_MATCHED_NAME_OFFSETS = 20;
    }

    // The initial TEMP_DIALER_SEARCH_VIEW without display name column and sort
    // key column
    private static final String DS_INIT_VIEW_COLUMNS = DialerSearch.NAME_LOOKUP_ID + ","
                        + DialerSearch.CONTACT_ID + ","
                        + DialerSearch.RAW_CONTACT_ID + ","
                        + DialerSearch.NAME_ID + ","
                        + DialerSearch.CALL_DATE + ","
                        + DialerSearch.CALL_LOG_ID + ","
                        + DialerSearch.CALL_TYPE + ","
                        + DialerSearch.CALL_GEOCODED_LOCATION + ","
                        + DialerSearch.PHONE_ACCOUNT_ID + ","
                        + DialerSearch.PHONE_ACCOUNT_COMPONENT_NAME + ","
                        + DialerSearch.NUMBER_PRESENTATION + ","
                        + DialerSearch.SEARCH_PHONE_NUMBER + ","
                        + DialerSearch.SEARCH_PHONE_TYPE + ","
                        + DialerSearch.SEARCH_PHONE_LABEL + ","
                        + DialerSearch.CONTACT_NAME_LOOKUP + ","
                        + DialerSearch.PHOTO_ID + ","
                        + DialerSearch.CONTACT_STARRED + ","
                        + DialerSearch.INDICATE_PHONE_SIM + ","
                        + DialerSearch.IS_SDN_CONTACT + ","// add by MTK
                        // M: fix CR:ALPS01712363,add data_id fields to ensure query right data_id
                        + DialerSearchLookupColumns.DATA_ID; // add by MTK

    private static final String TEMP_DIALER_SEARCH_TABLE = "temp_dialer_search_table";
    private ArrayList<String> mFilterCache = new ArrayList<String>();
    private ArrayList<Object[][]> mResultCache = new ArrayList<Object[][]>();

    private static final int BACKGROUND_TASK_CREATE_CACHE = 0;
    private static final int BACKGROUND_TASK_REMOVE_CACHE = 1;
    private ContactsDatabaseHelper mDbHelper;
    private Context mContext;
    private boolean mIsCached = false;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private static DialerSearchSupport sDialerSearchSupport;
    private static final int CACHE_TASK_DELAY_MILLIS = 10000;
    private static final String sCachedOffsetsTempTableHead = "ds_offsets_temp_table_";

    private boolean mUseStrictPhoneNumberComparation;
    private HashMap<Long, ContactData> mContactMap;
    private int mNumberCount = 0;
    private int mDisplayOrder = -1;
    private int mSortOrder = -1;
    private int mPrevSearchNumberLen = 0;

    private DialerSearchSupport(Context context, ContactsDatabaseHelper helper) {
        mDbHelper = helper;
        mContext = context;

        mBackgroundThread = new HandlerThread("DialerSearchWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                performBackgroundTask(msg.what);
            }
        };
    }

    public void initialize() {
        scheduleBackgroundTask(BACKGROUND_TASK_CREATE_CACHE);
    }

    private void performBackgroundTask(int task) {
        LogUtils.d(TAG, "performBackgroundTask," +
                    " mIsCached always should be: " + mIsCached + " | task: " + task);
        if (task == BACKGROUND_TASK_CREATE_CACHE) {
            mIsCached = true;
            createDsTempTableZero2Nine(mDbHelper.getWritableDatabase());
        } else if (task == BACKGROUND_TASK_REMOVE_CACHE) {
            removeDsTempTableZero2Nine(mDbHelper.getWritableDatabase());
        } 
    }

    public static String computeNormalizedNumber(String number) {
        String normalizedNumber = null;
        if (number != null) {
            normalizedNumber = PhoneNumberUtils.getStrippedReversed(number);
        }
        return normalizedNumber;
    }

    public static String stripSpecialCharInNumberForDialerSearch(String number) {
        if (number == null)
            return null;
        int len = number.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char c = number.charAt(i);
            if (PhoneNumberUtils.isNonSeparator(c)) {
                sb.append(c);
            } else if (c == ' ' || c == '-' || c == '(' || c == ')') {
                // strip blank and hyphen
            } else {
                continue;
            }
        }
        return sb.toString();
    }

    public static void createDialerSearchTable(SQLiteDatabase db) {
        if (ContactsProviderUtils.isSearchDbSupport()) {
            db.execSQL("CREATE TABLE " + Tables.DIALER_SEARCH + " ("
                    + DialerSearchLookupColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + DialerSearchLookupColumns.DATA_ID
                        + " INTEGER REFERENCES data(_id) NOT NULL,"
                    + DialerSearchLookupColumns.RAW_CONTACT_ID
                        + " INTEGER REFERENCES raw_contacts(_id) NOT NULL,"
                    + DialerSearchLookupColumns.NAME_TYPE + " INTEGER NOT NULL,"
                    + DialerSearchLookupColumns.CALL_LOG_ID + " INTEGER DEFAULT 0,"
                    + DialerSearchLookupColumns.NUMBER_COUNT + " INTEGER NOT NULL DEFAULT 0, "
                    + DialerSearchLookupColumns.IS_VISIABLE + " INTEGER NOT NULL DEFAULT 1, "
                    + DialerSearchLookupColumns.NORMALIZED_NAME + " VARCHAR DEFAULT NULL,"
                    + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + " VARCHAR DEFAULT NULL,"
                    + DialerSearchLookupColumns.NORMALIZED_NAME_ALTERNATIVE
                        + " VARCHAR DEFAULT NULL,"
                    + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS_ALTERNATIVE
                        + " VARCHAR DEFAULT NULL " + ");");
            db.execSQL("CREATE INDEX dialer_data_id_index ON "
                    + Tables.DIALER_SEARCH + " ("
                    + DialerSearchLookupColumns.DATA_ID + ");");
            db.execSQL("CREATE INDEX dialer_search_raw_contact_id_index ON "
                    + Tables.DIALER_SEARCH + " ("
                    + DialerSearchLookupColumns.RAW_CONTACT_ID + ","
                    + DialerSearchLookupColumns.NAME_TYPE + ");");
            db.execSQL("CREATE INDEX dialer_search_call_log_id_index ON "
                    + Tables.DIALER_SEARCH + " ("
                    + DialerSearchLookupColumns.CALL_LOG_ID + ");");
        }
    }

    public static void createDialerSearchView(SQLiteDatabase db) {
        if (ContactsProviderUtils.isSearchDbSupport()) {
            db.execSQL("DROP VIEW IF EXISTS " + Views.DIALER_SEARCH_VIEW + ";");
            String mDSNameTable = "dialer_search_name";
            String mDSNumberTable = "dialer_search_number";
            // M: fix CR:ALPS01712363,add data_id fields to ensure query right data_id
            String mDSViewSelect = " SELECT "
                + mDSNumberTable + "." + DialerSearchLookupColumns.DATA_ID
                + " AS " + DialerSearchLookupColumns.DATA_ID + ","
                + mDSNumberTable + "." + DialerSearchLookupColumns._ID
                + " AS " + DialerSearch.NAME_LOOKUP_ID + ","
                + Tables.CONTACTS + "." + Contacts._ID
                + " AS " + DialerSearch.CONTACT_ID + ","
                + mDSNumberTable + "." + DialerSearchLookupColumns.RAW_CONTACT_ID
                + " AS " + DialerSearch.RAW_CONTACT_ID + ","
                + Tables.RAW_CONTACTS + "." + RawContacts.DISPLAY_NAME_PRIMARY
                + " AS " + DialerSearch.NAME + ","
                + Tables.RAW_CONTACTS + "." + RawContacts.DISPLAY_NAME_ALTERNATIVE
                + " AS " + DialerSearch.NAME_ALTERNATIVE + ","
                + Tables.CALLS + "." + Calls.DATE
                + " AS " + DialerSearch.CALL_DATE + ","
                + mDSNumberTable + "." + DialerSearchLookupColumns.CALL_LOG_ID
                + " AS " + DialerSearch.CALL_LOG_ID + ","
                + Tables.CALLS + "." + Calls.TYPE + " AS " + DialerSearch.CALL_TYPE + ","
                + Tables.CALLS + "." + Calls.PHONE_ACCOUNT_ID + " AS " + DialerSearch.PHONE_ACCOUNT_ID + ","
                + Tables.CALLS + "." + Calls.PHONE_ACCOUNT_COMPONENT_NAME + " AS " + DialerSearch.PHONE_ACCOUNT_COMPONENT_NAME + ","
                + Tables.CALLS + "." + Calls.NUMBER_PRESENTATION + " AS " + DialerSearch.NUMBER_PRESENTATION + ","
                + Tables.CALLS + "." + Calls.GEOCODED_LOCATION
                + " AS " + DialerSearch.CALL_GEOCODED_LOCATION + ","
//                + " (CASE "
//                    + " WHEN " + mDSNumberTable + "." + DialerSearchLookupColumns.CALL_LOG_ID + " > 0 "
//                        + " THEN " + Tables.CALLS + "." + Calls.NUMBER
//                    + " ELSE " + mDSNumberTable + "." + DialerSearchLookupColumns.NORMALIZED_NAME
//                + " END) AS " + DialerSearch.SEARCH_PHONE_NUMBER + ","
                + mDSNumberTable + "." + DialerSearchLookupColumns.NORMALIZED_NAME
                + " AS " + DialerSearch.SEARCH_PHONE_NUMBER + ","
                + Tables.DATA + "." + Data.DATA2 + " AS " + DialerSearch.SEARCH_PHONE_TYPE + ","
                + Tables.DATA + "." + Data.DATA3 + " AS " + DialerSearch.SEARCH_PHONE_LABEL + ","
                + Tables.CONTACTS + "." + Contacts.LOOKUP_KEY
                + " AS " + DialerSearch.CONTACT_NAME_LOOKUP + ","
                + Tables.CONTACTS + "." + Contacts.PHOTO_ID + " AS " + DialerSearch.PHOTO_ID + ","
                + Tables.CONTACTS + "." + Contacts.STARRED + " AS " + DialerSearch.CONTACT_STARRED + ","
                + Tables.CONTACTS + "." + Contacts.INDICATE_PHONE_SIM
                + " AS " + DialerSearch.INDICATE_PHONE_SIM + ","
                + Tables.CONTACTS + "." + Contacts.IS_SDN_CONTACT
                + " AS " + DialerSearch.IS_SDN_CONTACT + ","  // add by MTK
                + Tables.RAW_CONTACTS + "." + RawContacts.SORT_KEY_PRIMARY
                + " AS " + DialerSearch.SORT_KEY_PRIMARY + ","
                + Tables.RAW_CONTACTS + "." + RawContacts.SORT_KEY_ALTERNATIVE
                + " AS " + DialerSearch.SORT_KEY_ALTERNATIVE + ","
                + mDSNameTable + "." + DialerSearchLookupColumns._ID + " AS " + DialerSearch.NAME_ID
                + " FROM (SELECT * FROM " + Tables.DIALER_SEARCH
                        + " WHERE " + DialerSearchLookupColumns.NAME_TYPE
                        + " = " + DialerSearchLookupType.PHONE_EXACT + ") AS " + mDSNumberTable
                + " LEFT JOIN " + Tables.RAW_CONTACTS
                    + " ON " + Tables.RAW_CONTACTS + "." + RawContacts._ID
                    + " = " + mDSNumberTable + "." + DialerSearchLookupColumns.RAW_CONTACT_ID
                + " LEFT JOIN " + Tables.CONTACTS
                    + " ON " + Tables.CONTACTS + "." + Contacts._ID
                    + " = " + Tables.RAW_CONTACTS + "." + RawContacts.CONTACT_ID
                + " LEFT JOIN " + Tables.CALLS
                    + " ON " + Tables.CALLS + "." + Calls._ID
                    + " = " + mDSNumberTable + "." + DialerSearchLookupColumns.CALL_LOG_ID
                + " LEFT JOIN " + Tables.DATA
                    + " ON " + Tables.DATA + "." + Data._ID
                    + " = " + mDSNumberTable + "." + DialerSearchLookupColumns.DATA_ID
                + " LEFT JOIN " + Tables.DIALER_SEARCH + " AS " + mDSNameTable
                    + " ON " + mDSNameTable + "." + DialerSearchLookupColumns.RAW_CONTACT_ID
                    + " = " + mDSNumberTable + "." + DialerSearchLookupColumns.RAW_CONTACT_ID
                    + " AND " + mDSNameTable + "." + DialerSearchLookupColumns.NAME_TYPE
                    + " = " + DialerSearchLookupType.NAME_EXACT
                + " WHERE " + Tables.CONTACTS + "." + Contacts._ID + " >0 OR "
                    + mDSNumberTable + "." + DialerSearchLookupColumns.CALL_LOG_ID + ">0";
            db.execSQL("CREATE VIEW " + Views.DIALER_SEARCH_VIEW + " AS " + mDSViewSelect);
        }
    }

    private static String DIALER_SEARCH_TEMP_TABLE = "dialer_search_temp_table";
    private static void createDialerSearchTempTable(SQLiteDatabase db, String contactsSelect, String rawContactsSelect, String calllogSelect) {
        if (ContactsProviderUtils.isSearchDbSupport()) {
            db.execSQL("DROP TABLE IF EXISTS " + DIALER_SEARCH_TEMP_TABLE + ";");
            String dsNumberTableName = "t_ds_number";
            String dsCallsTableName = "t_ds_calls";
            String dsDataViewName = "v_ds_data";

            String dsCallsTable = " (SELECT "
                    + Calls._ID + ", "
                    + Calls.DATE + ", "
                    + Calls.TYPE + ", "
                    + Calls.PHONE_ACCOUNT_ID + ", "
                    + Calls.PHONE_ACCOUNT_COMPONENT_NAME + ", "
                    + Calls.NUMBER_PRESENTATION + ", "
                    + Calls.GEOCODED_LOCATION
                    + " FROM " + Tables.CALLS 
                    + " WHERE (" + calllogSelect + ")"
                    + " ) AS " + dsCallsTableName;

            String dsDataView = " (SELECT "
                    + Data._ID + ", "
                    + Data.RAW_CONTACT_ID + ", "
                    + RawContacts.CONTACT_ID + ", "
                    + RawContacts.DISPLAY_NAME_PRIMARY + ", "
                    + RawContacts.DISPLAY_NAME_ALTERNATIVE + ", "
                    + Data.DATA2 + ", "
                    + Data.DATA3 + ", "
                    + Contacts.LOOKUP_KEY + ", "
                    + Contacts.PHOTO_ID + ", "
                    + Contacts.STARRED + ", "
                    + Contacts.INDICATE_PHONE_SIM + ", "
                    + Contacts.TIMES_CONTACTED + ", "
                    + Contacts.IS_SDN_CONTACT + ", "
                    + RawContacts.SORT_KEY_PRIMARY + ", "
                    + RawContacts.SORT_KEY_ALTERNATIVE
                    + " FROM " + Views.DATA 
                    + " WHERE (" + contactsSelect + ")"
                    + " ) AS " + dsDataViewName;

            String dsViewSelect = " SELECT "
                + dsNumberTableName + "." + DialerSearchLookupColumns._ID + " AS " + DialerSearch.NAME_LOOKUP_ID + ","
                + dsNumberTableName + "." + DialerSearchLookupColumns.DATA_ID + " AS " + DialerSearchLookupColumns.DATA_ID + ","
                + dsNumberTableName + "." + DialerSearchLookupColumns.RAW_CONTACT_ID + " AS " + DialerSearch.RAW_CONTACT_ID + ","
                + dsNumberTableName + "." + DialerSearchLookupColumns.CALL_LOG_ID + " AS " + DialerSearch.CALL_LOG_ID + ","
                + dsNumberTableName + "." + DialerSearchLookupColumns.NORMALIZED_NAME + " AS " + DialerSearch.SEARCH_PHONE_NUMBER + ","
                + dsNumberTableName + "." + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + ","  //Add to calculate offset
                + dsNumberTableName + "." + DialerSearchLookupColumns.NAME_TYPE + "," //Add to calculate offset

                + dsCallsTableName + "." + Calls.DATE + " AS " + DialerSearch.CALL_DATE + ","
                + dsCallsTableName + "." + Calls.TYPE + " AS " + DialerSearch.CALL_TYPE + ","
                + dsCallsTableName + "." + Calls.PHONE_ACCOUNT_ID + " AS " + DialerSearch.PHONE_ACCOUNT_ID + ","
                + dsCallsTableName + "." + Calls.PHONE_ACCOUNT_COMPONENT_NAME + " AS " + DialerSearch.PHONE_ACCOUNT_COMPONENT_NAME + ","
                + dsCallsTableName + "." + Calls.NUMBER_PRESENTATION + " AS " + DialerSearch.NUMBER_PRESENTATION + ","
                + dsCallsTableName + "." + Calls.GEOCODED_LOCATION + " AS " + DialerSearch.CALL_GEOCODED_LOCATION + ","

                + dsDataViewName + "." + RawContacts.CONTACT_ID + " AS " + DialerSearch.CONTACT_ID + ","
                + dsDataViewName + "." + RawContacts.DISPLAY_NAME_PRIMARY + " AS " + DialerSearch.NAME + ","
                + dsDataViewName + "." + RawContacts.DISPLAY_NAME_ALTERNATIVE + " AS " + DialerSearch.NAME_ALTERNATIVE + ","
                + dsDataViewName + "." + Data.DATA2 + " AS " + DialerSearch.SEARCH_PHONE_TYPE + ","
                + dsDataViewName + "." + Data.DATA3 + " AS " + DialerSearch.SEARCH_PHONE_LABEL + ","
                + dsDataViewName + "." + Contacts.LOOKUP_KEY + " AS " + DialerSearch.CONTACT_NAME_LOOKUP + ","
                + dsDataViewName + "." + Contacts.PHOTO_ID + " AS " + DialerSearch.PHOTO_ID + ","
                + dsDataViewName + "." + Contacts.STARRED + " AS " + DialerSearch.CONTACT_STARRED + ","
                + dsDataViewName + "." + Contacts.TIMES_CONTACTED + " AS " + DialerSearchLookupColumns.TIMES_USED + ","
                + dsDataViewName + "." + Contacts.INDICATE_PHONE_SIM + " AS " + DialerSearch.INDICATE_PHONE_SIM + ","
                + dsDataViewName + "." + Contacts.IS_SDN_CONTACT + " AS " + DialerSearch.IS_SDN_CONTACT + ","
                + dsDataViewName + "." + RawContacts.SORT_KEY_PRIMARY + " AS " + DialerSearch.SORT_KEY_PRIMARY + ","
                + dsDataViewName + "." + RawContacts.SORT_KEY_ALTERNATIVE + " AS " + DialerSearch.SORT_KEY_ALTERNATIVE

                + " FROM (SELECT * FROM " + Tables.DIALER_SEARCH
                        + " WHERE " + DialerSearchLookupColumns.NAME_TYPE + " = " + DialerSearchLookupType.PHONE_EXACT
                        + " AND (" + rawContactsSelect + ")"
                        + " ) AS " + dsNumberTableName
                + " LEFT JOIN " + dsCallsTable + " ON "
                        + dsCallsTableName + "." + Calls._ID + " = " + dsNumberTableName + "." + DialerSearchLookupColumns.CALL_LOG_ID
                + " LEFT JOIN " + dsDataView + " ON " 
                        + dsDataViewName + "." + Data._ID + " = " + dsNumberTableName + "." + DialerSearchLookupColumns.DATA_ID;

            db.execSQL("CREATE TEMP TABLE " + DIALER_SEARCH_TEMP_TABLE + " AS " + dsViewSelect);
        }
    }

    public static void createContactsTriggersForDialerSearch(SQLiteDatabase db) {
        /*
         * For dialer search, update dialer search table and calls table when
         * a raw contact is deleted.
         */

        // It used to SYNC dialer_search table and calls table when a contact was deleted.
        db.execSQL("DROP TRIGGER IF EXISTS " + Tables.AGGREGATION_EXCEPTIONS + "_splite_contacts");
        db.execSQL("CREATE TRIGGER " + Tables.AGGREGATION_EXCEPTIONS
                + "_splite_contacts AFTER INSERT ON " + Tables.AGGREGATION_EXCEPTIONS
                + " BEGIN "
                + "   UPDATE " + Tables.DIALER_SEARCH
                + "     SET " + DialerSearchLookupColumns.RAW_CONTACT_ID + "="
                                + "(SELECT " + DialerSearchLookupColumns.RAW_CONTACT_ID
                                + " FROM " + Tables.DATA +
                                " WHERE " + Tables.DATA + "." + Data._ID
                                + "=" + Tables.DIALER_SEARCH
                                    + "." + DialerSearchLookupColumns.DATA_ID + ")"
                + "   WHERE " + DialerSearchLookupColumns.RAW_CONTACT_ID
                                + " IN (" + "NEW." + AggregationExceptions.RAW_CONTACT_ID1
                                    + ",NEW." + AggregationExceptions.RAW_CONTACT_ID2 + ")"
                                + " AND " + DialerSearchLookupColumns.IS_VISIABLE + "=1"
                                + " AND " + DialerSearchLookupColumns.NAME_TYPE
                                    + "=" + DialerSearchLookupType.PHONE_EXACT
                                + " AND " + "NEW." + AggregationExceptions.TYPE + "=2;"
                + "   UPDATE " + Tables.DIALER_SEARCH
                + "     SET " + DialerSearchLookupColumns.IS_VISIABLE + "=1"
                + "   WHERE " + DialerSearchLookupColumns.RAW_CONTACT_ID
                                + " IN (" + "NEW." + AggregationExceptions.RAW_CONTACT_ID1
                                    + ",NEW." + AggregationExceptions.RAW_CONTACT_ID2 + ")"
                                + " AND " + DialerSearchLookupColumns.IS_VISIABLE + "=0"
                                + " AND " + DialerSearchLookupColumns.NAME_TYPE
                                    + "=" + DialerSearchLookupType.NAME_EXACT
                                + " AND " + "NEW." + AggregationExceptions.TYPE + "=2"
                                + ";"
                + " END");
    }

    public static void setNameForDialerSearch(SQLiteStatement dialerSearchNameUpdate,
            SQLiteDatabase db, long rawContactId, String displayNamePrimary,
            String displayNameAlternative) {
        if (dialerSearchNameUpdate == null) {
            dialerSearchNameUpdate = db.compileStatement("UPDATE "
                    + Tables.DIALER_SEARCH + " SET "
                    + DialerSearchLookupColumns.NORMALIZED_NAME + "=?,"
                    + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + "=?,"
                    + DialerSearchLookupColumns.NORMALIZED_NAME_ALTERNATIVE + "=?,"
                    + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS_ALTERNATIVE + "=?"
                    + " WHERE " + DialerSearchLookupColumns.RAW_CONTACT_ID + "=? AND "
                    + DialerSearchLookupColumns.NAME_TYPE + "="
                    + DialerSearchLookupType.NAME_EXACT);
        }

        StringBuilder mSearchNameOffsets = new StringBuilder();
        String mSearchName = HanziToPinyin.getInstance()
                .getTokensForDialerSearch(displayNamePrimary, mSearchNameOffsets);
        StringBuilder mSearchNameOffsetsAlt = new StringBuilder();
        String mSearchNameAlt = HanziToPinyin.getInstance()
                .getTokensForDialerSearch(displayNameAlternative, mSearchNameOffsetsAlt);

        setBind(dialerSearchNameUpdate, mSearchName, 1);
        setBind(dialerSearchNameUpdate, mSearchNameOffsets.toString(), 2);
        setBind(dialerSearchNameUpdate, mSearchNameAlt, 3);
        setBind(dialerSearchNameUpdate, mSearchNameOffsetsAlt.toString(), 4);
        dialerSearchNameUpdate.bindLong(5, rawContactId);
        dialerSearchNameUpdate.execute();
    }

    private static void setBind(SQLiteStatement stmt, String value, int index) {
        if (TextUtils.isEmpty(value)) {
            stmt.bindNull(index);
            return;
        }
        stmt.bindString(index, value);
    }

    private String getDialerSearchViewColumns(int displayOrder, int sortOrder) {
        StringBuilder sb = new StringBuilder(DS_INIT_VIEW_COLUMNS);
        if (displayOrder == ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE) {
            sb.append("," + DialerSearch.NAME_ALTERNATIVE + " AS " + DialerSearch.NAME);
        } else {
            sb.append("," + DialerSearch.NAME);
        }
        if (sortOrder == ContactsContract.Preferences.SORT_ORDER_ALTERNATIVE) {
            sb.append("," + DialerSearch.SORT_KEY_ALTERNATIVE + " AS "
                    + DialerSearch.SORT_KEY_PRIMARY);
        } else {
            sb.append("," + DialerSearch.SORT_KEY_PRIMARY);
        }
        return sb.toString();
    }

    public int updateDialerSearchDataForMultiDelete(SQLiteDatabase db, String selection,
            String[] selectionArgs) {
        /**
         * Original Android code:
         * Cursor cursor = db.rawQuery("SELECT _id FROM raw_contacts WHERE " + selection, selectionArgs);
         *
         * M: [ALPS00884503]After reboot phone, sim contact will remove then import again.
         * SEARCH_INDEX table not delete old data of sim contact, so the performance will degradation @{
         */
        Cursor cursor = db.rawQuery("SELECT _id, contact_id FROM raw_contacts WHERE " + selection, selectionArgs);
        ArrayList<Long> contactIdArray = new ArrayList<Long>();
        /**
         * [ALPS00884503] @}
         */
        ArrayList<Long> rawIdArray = new ArrayList<Long>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                rawIdArray.add(cursor.getLong(cursor.getColumnIndex("_id")));
                /**
                 * M: [ALPS00884503]After reboot phone, sim contact will remove then import again.
                 * SEARCH_INDEX table not delete old data of sim contact, so the performance will degradation @{
                 */
                contactIdArray.add(cursor.getLong(cursor.getColumnIndex("contact_id")));
                /**
                 * [ALPS00884503] @}
                 */
            }
            cursor.close();
        }
        int count = db.delete(Tables.RAW_CONTACTS, selection, selectionArgs);
        for (long rawContactId : rawIdArray) {
            LogUtils.d(TAG,"[updateDialerSearchDataForMultiDelete]rawContactId:" + rawContactId);
            updateDialerSearchDataForDelete(db, rawContactId);
        }
        /**
         * M: [ALPS00884503] After reboot phone, sim contact will remove then import again.
         * SEARCH_INDEX table not delete old data of sim contact, so the performance will degradation @{
         */
        for (long contactId: contactIdArray) {
            String contactIdAsString = String.valueOf(contactId);
            db.execSQL("DELETE FROM " + Tables.SEARCH_INDEX + " WHERE " + SearchIndexColumns.CONTACT_ID + "=CAST(? AS int)",
                    new String[] { contactIdAsString });
        }
        /**
         * [ALPS00884503] @}
         */
        return count;
    }

    public void updateDialerSearchDataForDelete(SQLiteDatabase db, long rawContactId) {
        
        Cursor c = null;
        try{
            c = db.rawQuery(
                    "SELECT _id,number,data_id FROM calls WHERE raw_contact_id = "
                            + rawContactId + " GROUP BY data_id;", null);
            if (c != null) {
                LogUtils.d(TAG,"[updateDialerSearchDataForDelete]calls count:" + c.getCount());
                while (c.moveToNext()) {
                    long callId = c.getLong(c.getColumnIndex("_id"));
                    String number = c.getString(c.getColumnIndex("number"));
                    long dataId = c.getLong(c.getColumnIndex("data_id"));
					if(Build.TYPE.equals("eng")){
                     LogUtils.d(TAG,"[updateDialerSearchDataForDelete]callId:" + callId
                            + "|number:" + number + "|dataId:" + dataId);
					}
                    String UseStrict = mUseStrictPhoneNumberComparation ? "1" : "0";
                    Cursor dataCursor = null;
                    try{
                        if (PhoneNumberUtils.isUriNumber(number)) {
                            // M: fix CR:ALPS01763175,substitute mimetype values for mimetype_id to
                            // ensure mimetype_id change query still right.
                            dataCursor = db.rawQuery(
                                            "SELECT _id,raw_contact_id,contact_id FROM view_data "
                                                    + " WHERE data1 =?" + " AND (mimetype= '" + SipAddress.CONTENT_ITEM_TYPE + "'"
                                                    +" OR mimetype= '" + ImsCall.CONTENT_ITEM_TYPE + "')"
                                                    + " AND raw_contact_id !=? LIMIT 1",
                                            new String[] {
                                                    number, String.valueOf(rawContactId)
                                            });
                            // M: fix CR:ALPS01763175,substitute mimetype values for mimetype_id to
                            // ensure mimetype_id change query still right.
                        } else {
                            dataCursor = db.rawQuery(
                                      "SELECT _id,raw_contact_id,contact_id FROM view_data "
                                              + " WHERE PHONE_NUMBERS_EQUAL(data1, '" + number + "' , "
                                              + UseStrict + " )"
                                              + " AND mimetype= '" + Phone.CONTENT_ITEM_TYPE + "'" + " AND raw_contact_id !=? LIMIT 1",
                                       new String[] {
                                           String.valueOf(rawContactId)
                                       });
                        }
                        if (dataCursor != null && dataCursor.moveToFirst()) {
                            long newDataId = dataCursor.getLong(dataCursor.getColumnIndex("_id"));
                            long newRawId = dataCursor.getLong(dataCursor.getColumnIndex("raw_contact_id"));
                            LogUtils.d(TAG,"[updateDialerSearchDataForDelete]newDataId:" + newDataId
                                        + "|newRawId:" + newRawId);
                            db.execSQL("UPDATE calls SET data_id=?, raw_contact_id=? "
                                                + " WHERE data_id=?", new String[] {
                                                String.valueOf(newDataId),
                                                String.valueOf(newRawId),
                                                String.valueOf(dataId)
                                        });
                            db.execSQL("UPDATE dialer_search SET call_log_id=? "
                                        + " WHERE data_id=?", new String[] {
                                        String.valueOf(callId),
                                        String.valueOf(newDataId)
                            });
                        } else {
                            LogUtils.d(TAG,"[updateDialerSearchDataForDelete]update call log null.");
                            db.execSQL("UPDATE calls SET data_id=null, raw_contact_id=null "
                                            + "WHERE data_id=?", new String[] {
                                            String.valueOf(dataId)
                                        });
                            db.execSQL("UPDATE dialer_search "
                                    + "SET data_id=-call_log_id, "
                                    + " raw_contact_id=-call_log_id, "
                                    + " normalized_name=?, "
                                    + " normalized_name_alternative=? "
                                    + " WHERE data_id =?",
                                    new String[] {
                                            number, number, String.valueOf(dataId)
                                    });

                        }
                    }finally{
                        if (dataCursor != null) {
                            dataCursor.close();
                        }
                    }
                }
//                c.close();
            }
            String delStr = "DELETE FROM dialer_search WHERE raw_contact_id=" + rawContactId;
            LogUtils.d(TAG,"[updateDialerSearchDataForDelete]delStr:" + delStr);
            db.execSQL(delStr);
        }finally{
            if(c != null)
                c.close();
        }
    }

    public static Cursor queryPhoneLookupByNumber(SQLiteDatabase db, ContactsDatabaseHelper dbHelper,
            String number, String[] projection, String selection, String[] selectionArgs,
            String groupBy, String having, String sortOrder, String limit) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String numberE164 = PhoneNumberUtils.formatNumberToE164(number,
                dbHelper.getCurrentCountryIso());
        String normalizedNumber = PhoneNumberUtils.normalizeNumber(number);
        dbHelper.buildPhoneLookupAndContactQuery(qb, normalizedNumber, numberE164);
        qb.setStrict(true);
        boolean foundResult = false;
		//add by zhaizhanfeng for calllog number match at 151015 start 
		if (TextUtils.isEmpty(sortOrder)) {
		// Default the sort order to something reasonable so we get consistent
		// results when callers don't request an ordering
		sortOrder = " length(lookup.normalized_number) DESC";
		}
		//add by zhaizhanfeng for calllog number match at 151015 end
        Cursor c = qb.query(db, projection, selection, selectionArgs, groupBy, having,
                sortOrder, limit);
        try {
            if (c.getCount() > 0) {
                foundResult = true;
                return c;
            } else {
                qb = new SQLiteQueryBuilder();
                dbHelper.buildFallbackPhoneLookupAndContactQuery(qb, normalizedNumber);
                qb.setStrict(true);
            }
        } finally {
            if (!foundResult) {
                // We'll be returning a different cursor, so close this one.
                c.close();
            }
        }
        return qb.query(db, projection, selection, selectionArgs, groupBy, having,
                sortOrder, limit);
    }

    /// M: Remove abandoned dialer search entry @{
    /**
    public Cursor handleEmptyQuery(SQLiteDatabase db, Uri uri) {
        return queryDialerSearchInit(db, uri);
    }

    public Cursor handleSimpleQuery(SQLiteDatabase db, Uri uri) {
        return queryDialerSearchSimple(db, uri);
    }

    public Cursor handleIncrementQuery(SQLiteDatabase db, Uri uri) {
        return queryDialerSearchIncrement(db, uri);
    }
    */
    /// M: @}

    public Cursor handleDialerSearchQuery(SQLiteDatabase db, Uri uri) {
        return queryDialerSearch(db, uri);
    }

    public static void createDsTempTableZero2Nine(SQLiteDatabase db) {
        long begin = System.currentTimeMillis();

        try {
            for (int i = 0; i <= 9; i++) {
                String filterNum = String.valueOf(i);
                String cachedOffsetsTempTable = sCachedOffsetsTempTableHead + filterNum;
                String offsetsSelect = createCacheOffsetsTableSelect(filterNum);

                db.execSQL("CREATE TEMP TABLE IF NOT EXISTS " + cachedOffsetsTempTable + " AS " + offsetsSelect);
            }
        } catch (SQLiteException e) {
            LogUtils.w(TAG, "createDsTempTableZero2Nine SQLiteException:" + e);
        }

        long end = System.currentTimeMillis();
        LogUtils.d(TAG, "[createDsTempTableZero2Nine] create cache table cost:" + (end - begin));
    }

    public static void removeDsTempTableZero2Nine(SQLiteDatabase db) {
        long begin = System.currentTimeMillis();

        try {
            for (int i = 0; i <= 9; i++) {
                String filterNum = String.valueOf(i);
                String cachedOffsetsTempTable = sCachedOffsetsTempTableHead + filterNum;
                db.execSQL("DROP TABLE IF EXISTS " + cachedOffsetsTempTable + ";");
            }
        } catch (SQLiteException e) {
            LogUtils.w(TAG, "removeDsTempTableZero2Nine SQLiteException:" + e);
        }

        long end = System.currentTimeMillis();
        LogUtils.d(TAG, "[removeDsTempTableZero2Nine] remove cache table cost:" + (end - begin));
    }

    private static String createCacheOffsetsTableSelect(String filterNum) {
        String offsetsSelect = " SELECT "
                + getOffsetsTempTableColumns(ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY, filterNum)
                + "," + RawContacts.CONTACT_ID
                + " FROM " + Tables.DIALER_SEARCH

                + " LEFT JOIN ("
                + "SELECT _id as raw_id, contact_id," + RawContacts.SORT_KEY_PRIMARY
                + " FROM " + Tables.RAW_CONTACTS + ") AS raw_contact_info ON "
                + "raw_contact_info.raw_id=" + DialerSearchLookupColumns.RAW_CONTACT_ID

                + " WHERE " + DialerSearchLookupColumns.IS_VISIABLE + " = 1"
                + " AND "
                + "DIALER_SEARCH_MATCH_FILTER("
                + DialerSearchLookupColumns.NORMALIZED_NAME + ","
                + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + ","
                + DialerSearchLookupColumns.NAME_TYPE + ",'"
                + filterNum + "'" + ")"

                + " ORDER BY offset COLLATE MATCHTYPE DESC, "
                + DialerSearchLookupColumns.SORT_KEY + " COLLATE PHONEBOOK,"
                + DialerSearchLookupColumns.CALL_LOG_ID + " DESC ";
        return offsetsSelect;
    }

    public Cursor handleDialerSearchQueryEx(SQLiteDatabase db, Uri uri) {
        String filterParam = uri.getLastPathSegment();
        if (filterParam.isEmpty()) {
            LogUtils.d(TAG, "DialerSearch Uri with empty filter: " + uri);
            return new MatrixCursor(DialerSearchQuery.COLUMNS);
        }

        String displayOrder = uri.getQueryParameter(Preferences.DISPLAY_ORDER);
        String sortOrder = uri.getQueryParameter(Preferences.SORT_ORDER);
        if (!TextUtils.isEmpty(displayOrder) && !TextUtils.isEmpty(sortOrder)) {
            mDisplayOrder = Integer.parseInt(displayOrder);
            mSortOrder = Integer.parseInt(sortOrder);
        }
        //LogUtils.d(TAG, "MTK-DialerSearch, handleDialerSearchExQuery begin. filterParam:" + filterParam);

        long begin = System.currentTimeMillis();
        Cursor cursor = null;

        String offsetsSelectedTable = "selected_offsets_temp_table";
        int firstNum = -1;

        try {
            firstNum = Integer.parseInt(String.valueOf(filterParam.charAt(0)));
        } catch (NumberFormatException e) {
            LogUtils.d(TAG, "MTK-DialerSearch, Cannot Parse as Int:" + filterParam);
        }

        try {
            db.beginTransaction();

            LogUtils.d(TAG, "handleDialerSearchQueryEx, mIsCached:" + mIsCached);

            String currentRawContactsSelect = DialerSearchLookupColumns.RAW_CONTACT_ID
                    + " in (SELECT _id FROM " + Tables.RAW_CONTACTS + ")";
            String currentCallsSelect = DialerSearchLookupColumns.CALL_LOG_ID
                    + " in (SELECT _id FROM " + Tables.CALLS + ")";
            String currentSelect = "("+ currentCallsSelect + ") OR (" + currentRawContactsSelect + ")";

            // Only when first char is number and contacts preference is primary,
            // can using cached table to enhance dialer search performance.
            if (firstNum >= 0 && firstNum <= 9 && mIsCached
                    && mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY
                    && mSortOrder == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
                String cachedOffsetsTable = sCachedOffsetsTempTableHead + firstNum;
                String offsetsSelect = null;

                // CREATE TEMP TABLE called in transaction, Cannot be used next time.
                db.execSQL("CREATE TEMP TABLE IF NOT EXISTS " + cachedOffsetsTable
                        + " AS " + createCacheOffsetsTableSelect(String.valueOf(firstNum)));

                // If length of user input(e.g: 1) is 1, then select the first 150 data from cached table directly;
                // Otherwise choose the data matched the user input(e.g: 123) base on the cached table.
                if (filterParam.length() == 1) {
                    offsetsSelect = "SELECT * FROM " + cachedOffsetsTable + " WHERE " + currentSelect + " LIMIT 150 ";
                } else {
                    offsetsSelect = " SELECT "
                        + getOffsetsTempTableColumns(ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY, filterParam)
                        + "," + RawContacts.CONTACT_ID
                        + " FROM " + cachedOffsetsTable
                        + " JOIN " + "(select "
                        + DialerSearchLookupColumns._ID + ","
                        + DialerSearchLookupColumns.NORMALIZED_NAME + ","
                        + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + " from "
                        + Tables.DIALER_SEARCH + ") AS ds_info " + " ON (_id = ds_id)"

                        + " WHERE (" + currentSelect + ") AND "
                        + "DIALER_SEARCH_MATCH_FILTER("
                        + DialerSearchLookupColumns.NORMALIZED_NAME + ","
                        + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + ","
                        + DialerSearchLookupColumns.NAME_TYPE + ",'"
                        + filterParam + "'" + ")"

                        + " ORDER BY offset COLLATE MATCHTYPE DESC, "
                        + DialerSearchLookupColumns.SORT_KEY + " COLLATE PHONEBOOK,"
                        + DialerSearchLookupColumns.CALL_LOG_ID + " DESC "
                        + " LIMIT 150";
                }
                db.execSQL("DROP TABLE IF EXISTS " + offsetsSelectedTable + ";");
                db.execSQL("CREATE TEMP TABLE " + offsetsSelectedTable + " AS " + offsetsSelect);
                long createTempTable1End = System.currentTimeMillis();
                LogUtils.d(TAG, "[handleDialerSearchQueryEx] create TEMP TABLE1 Cost:" + (createTempTable1End - begin));
            } else {
                String offsetsTable = "ds_offsets_temp_table";
                String dsOffsetsTable = " SELECT "
                        + getOffsetsTempTableColumns(mDisplayOrder, filterParam)
                        + "," + RawContacts.CONTACT_ID
                        + " FROM " + Tables.DIALER_SEARCH

                        + " LEFT JOIN ("
                        + "SELECT _id as raw_id, contact_id,"
                        + ((mSortOrder == ContactsContract.Preferences.SORT_ORDER_ALTERNATIVE)
                                ? RawContacts.SORT_KEY_ALTERNATIVE + " AS " + RawContacts.SORT_KEY_PRIMARY : RawContacts.SORT_KEY_PRIMARY)
                        + " FROM " + Tables.RAW_CONTACTS + ") AS raw_contact_info ON " 
                        + "raw_contact_info.raw_id=" + DialerSearchLookupColumns.RAW_CONTACT_ID

                        + " WHERE " + DialerSearchLookupColumns.IS_VISIABLE + " = 1"
                        + " AND (" + currentSelect + ")"
                        + " AND DIALER_SEARCH_MATCH_FILTER("
                        + DialerSearchLookupColumns.NORMALIZED_NAME + ","
                        + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + ","
                        + DialerSearchLookupColumns.NAME_TYPE + ",'"
                        + filterParam + "'" + ")";

                db.execSQL("DROP TABLE IF EXISTS " + offsetsTable + ";");
                db.execSQL("CREATE TEMP TABLE " + offsetsTable + " AS " + dsOffsetsTable);
                long createTempTable1End = System.currentTimeMillis();
                LogUtils.d(TAG, "[handleDialerSearchQueryEx] create TEMP TABLE1 Cost:" + (createTempTable1End - begin));

                String offsetsSelect = "SELECT * FROM " + offsetsTable

                        + " ORDER BY offset COLLATE MATCHTYPE DESC, "
                        + DialerSearchLookupColumns.SORT_KEY + " COLLATE PHONEBOOK,"
                        + DialerSearchLookupColumns.CALL_LOG_ID + " DESC "
                        + " LIMIT 150 ";

                db.execSQL("DROP TABLE IF EXISTS " + offsetsSelectedTable + ";");
                db.execSQL("CREATE TEMP TABLE " + offsetsSelectedTable + " AS " + offsetsSelect);
                long createTempTable2End = System.currentTimeMillis();
                LogUtils.d(TAG, "[handleDialerSearchQueryEx] create TEMP TABLE2 Cost:" + (createTempTable2End - createTempTable1End));
            }

            String contactsSelect = "contact_id IN (SELECT contact_id FROM " + offsetsSelectedTable + ")";
            String rawContactsSelect = "raw_contact_id IN (SELECT raw_contact_id FROM " + offsetsSelectedTable + ")";
            String calllogSelect = Calls._ID + " IN (SELECT call_log_id FROM " + offsetsSelectedTable + ")";
            createDialerSearchTempTable(db, contactsSelect, rawContactsSelect, calllogSelect);

            long createTempTableEnd = System.currentTimeMillis();
            LogUtils.d(TAG, "[handleDialerSearchQueryEx] create TEMP TABLE Cost:" + (createTempTableEnd - begin));

            String nameOffsets = "SELECT raw_contact_id as name_raw_id, offset as name_offset FROM "
                                    + offsetsSelectedTable + " WHERE name_type=" + DialerSearchLookupType.NAME_EXACT;

            String joinedOffsetTable = "SELECT"
                    + " ds_id, raw_contact_id, offset as offset_order, name_raw_id, name_type, " 
                    + " (CASE WHEN " + DialerSearchLookupColumns.NAME_TYPE + " = " + DialerSearchLookupType.PHONE_EXACT
                    + " THEN offset ELSE NULL END) AS " + DialerSearch.MATCHED_DATA_OFFSET + ", "
                    + " (CASE WHEN " + DialerSearchLookupColumns.NAME_TYPE + " = " + DialerSearchLookupType.NAME_EXACT
                    + " THEN offset ELSE name_offset END) AS " +   DialerSearch.MATCHED_NAME_OFFSET

                    + " FROM "
                    + offsetsSelectedTable
                    + " LEFT JOIN (" + nameOffsets + ") AS name_offsets" 
                           + " ON (" + offsetsSelectedTable + ".name_type=" + DialerSearchLookupType.PHONE_EXACT 
                           + " AND " + offsetsSelectedTable + ".raw_contact_id=name_offsets.name_raw_id)";

            cursor = db.rawQuery("SELECT " 
                  + getDialerSearchResultColumns(mDisplayOrder, mSortOrder)
                  + ", offset_order"
                  + ", " + DialerSearchLookupColumns.TIMES_USED

                  + " FROM "
                  + " (" + joinedOffsetTable + " ) AS offset_table" 
                  + " JOIN "
                  + DIALER_SEARCH_TEMP_TABLE
                  + " ON (" + DIALER_SEARCH_TEMP_TABLE + "." + DialerSearch._ID + "=offset_table.ds_id)"
                      + " OR ( offset_table.name_type=" + DialerSearchLookupType.NAME_EXACT + " AND "
                      + DIALER_SEARCH_TEMP_TABLE + "." + DialerSearch.RAW_CONTACT_ID + "=offset_table.raw_contact_id )" 

                  + " WHERE NOT" + "( offset_table.name_type=" + DialerSearchLookupType.NAME_EXACT
                  + " AND " + "_id IN (SELECT ds_id as _id FROM "
                  + offsetsSelectedTable + " WHERE name_type=" + DialerSearchLookupType.PHONE_EXACT + ") )"

                  + " ORDER BY " + DialerSearch.MATCHED_NAME_OFFSET + " COLLATE MATCHTYPE DESC,"
                  + DialerSearch.MATCHED_DATA_OFFSET + " COLLATE MATCHTYPE DESC,"
                  + DialerSearchLookupColumns.TIMES_USED + " DESC,"
                  + DialerSearch.SORT_KEY_PRIMARY + " COLLATE PHONEBOOK,"
                  + DialerSearch.CALL_LOG_ID + " DESC ", null);

            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            LogUtils.w(TAG, "handleDialerSearchQueryEx SQLiteException:" + e);
        } finally {
            db.endTransaction();
        }

        if (cursor == null) {
            LogUtils.d(TAG, "DialerSearch Cusor is null, Uri: " + uri);
            cursor = new MatrixCursor(DialerSearchQuery.COLUMNS);
        }

        return cursor;
    }

    private String getDialerSearchResultColumns(int displayOrder, int sortOrder) {
        StringBuilder sb = new StringBuilder();
        sb.append(DialerSearch.NAME_LOOKUP_ID + ","
                  + DialerSearch.CONTACT_ID + ","
                  + DialerSearchLookupColumns.DATA_ID + ","
                  + DialerSearch.CALL_DATE + ","
                  + DialerSearch.CALL_LOG_ID + ","
                  + DialerSearch.CALL_TYPE + ","
                  + DialerSearch.CALL_GEOCODED_LOCATION + ","
                  + DialerSearch.PHONE_ACCOUNT_ID + ","
                  + DialerSearch.PHONE_ACCOUNT_COMPONENT_NAME + ","
                  + DialerSearch.NUMBER_PRESENTATION + ","
                  + DialerSearch.INDICATE_PHONE_SIM + ","
                  + DialerSearch.CONTACT_STARRED + ","
                  + DialerSearch.PHOTO_ID + ","
                  + DialerSearch.SEARCH_PHONE_TYPE + ","
                  + DialerSearch.SEARCH_PHONE_LABEL + ","
                  + ((displayOrder == ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE)
                          ? DialerSearch.NAME_ALTERNATIVE + " AS " + DialerSearch.NAME : DialerSearch.NAME) + ","
                  + DialerSearch.SEARCH_PHONE_NUMBER + ","
                  + DialerSearch.CONTACT_NAME_LOOKUP + ","
                  + DialerSearch.IS_SDN_CONTACT + ","
                  + DialerSearch.MATCHED_DATA_OFFSET + ","
                  + DialerSearch.MATCHED_NAME_OFFSET);

        sb.append(",").append((sortOrder == ContactsContract.Preferences.SORT_ORDER_ALTERNATIVE)
                  ? DialerSearch.SORT_KEY_ALTERNATIVE + " AS " + DialerSearch.SORT_KEY_PRIMARY : DialerSearch.SORT_KEY_PRIMARY);

        return sb.toString();
    }

    public void handleDialerSearchQueryInit(SQLiteDatabase db, Uri uri) {
        // Here may happen "cursor leak" detail refer to ALPS01476419.
        // But if we add synchronized, may lead to ANR, like ALPS01583861
        // TODO: 1. We may need review the function queryDialerSearchInit  and
        //       last migration is reasonable or not.
        //       2. We may shouldn't every time reproduce every thing, when
        //       dates have been updated.
        //       3. cursor leak issue may happen again, though dialer have reduce
        //       query times in ALPS01529545
        queryDialerSearchInit(db, uri);
    }

    private void queryDialerSearchInit(SQLiteDatabase db, Uri uri) {
        LogUtils.d(TAG, "MTK-DialerSearch, queryDialerSearchInit, begin. uri: " + uri);

        String displayOrder = uri.getQueryParameter(Preferences.DISPLAY_ORDER);
        String sortOrder = uri.getQueryParameter(Preferences.SORT_ORDER);
        LogUtils.d(TAG, "MTK-DialerSearch, queryDialerSearchInit, displayOrder: " + displayOrder + " ,sortOrder: "
                + sortOrder);
        // M: fix CR:ALPS01660816,dialer search query fail problem,add transaction to ensure can't
        // release connection before finish filling contactMap.
        db.beginTransaction();
        try {
            if (!TextUtils.isEmpty(displayOrder) && !TextUtils.isEmpty(sortOrder)) {
                mDisplayOrder = Integer.parseInt(displayOrder);
                mSortOrder = Integer.parseInt(sortOrder);
                db.execSQL("DROP TABLE IF EXISTS " + TEMP_DIALER_SEARCH_TABLE);
                db.execSQL("CREATE TEMP TABLE  " + TEMP_DIALER_SEARCH_TABLE + " AS SELECT " + Tables.DIALER_SEARCH + "."
                        + DialerSearchLookupColumns._ID + " AS " + DialerSearchLookupColumns._ID + " ,"
                        + Tables.DIALER_SEARCH + "." + DialerSearchLookupColumns.DATA_ID + " AS "
                        + DialerSearchLookupColumns.DATA_ID + "," + Tables.DIALER_SEARCH + "."
                        + DialerSearchLookupColumns.RAW_CONTACT_ID + " AS " + DialerSearchLookupColumns.RAW_CONTACT_ID + ","
                        + Tables.DIALER_SEARCH + "." + DialerSearchLookupColumns.NAME_TYPE + " AS "
                        + DialerSearchLookupColumns.NAME_TYPE + "," + Tables.DIALER_SEARCH + "."
                        + DialerSearchLookupColumns.CALL_LOG_ID + " AS " + DialerSearchLookupColumns.CALL_LOG_ID + ","
                        + Tables.DIALER_SEARCH + "." + DialerSearchLookupColumns.NUMBER_COUNT + " AS "
                        + DialerSearchLookupColumns.NUMBER_COUNT + "," + Tables.DIALER_SEARCH + "."
                        + DialerSearchLookupColumns.IS_VISIABLE + " AS " + DialerSearchLookupColumns.IS_VISIABLE + ","
                        + Tables.DIALER_SEARCH + "." + DialerSearchLookupColumns.NORMALIZED_NAME + " AS "
                        + DialerSearchLookupColumns.NORMALIZED_NAME + "," + Tables.DIALER_SEARCH + "."
                        + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + " AS "
                        + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + "," + Tables.DIALER_SEARCH + "."
                        + DialerSearchLookupColumns.NORMALIZED_NAME_ALTERNATIVE + " AS "
                        + DialerSearchLookupColumns.NORMALIZED_NAME_ALTERNATIVE + "," + Tables.DIALER_SEARCH + "."
                        + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS_ALTERNATIVE + " AS "
                        + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS_ALTERNATIVE + "," + Tables.RAW_CONTACTS + "."
                        + RawContacts.SORT_KEY_PRIMARY + " AS " + DialerSearchLookupColumns.SORT_KEY + ","
                        + Tables.RAW_CONTACTS + "." + RawContacts.TIMES_CONTACTED + " AS "
                        + DialerSearchLookupColumns.TIMES_USED + " FROM " + Tables.DIALER_SEARCH + " LEFT JOIN "
                        + Tables.RAW_CONTACTS + " ON " + RawContactsColumns.CONCRETE_ID + "=" + Tables.DIALER_SEARCH + "."
                        + DialerSearchLookupColumns.RAW_CONTACT_ID + " WHERE " + DialerSearchLookupColumns.IS_VISIABLE
                        + " = 1");

                String viewColumns = getDialerSearchViewColumns(mDisplayOrder, mSortOrder);
                mContactMap = new HashMap<Long, ContactData>();
                long rawId = 0;
                ContactData contactData;
                Cursor c = db.rawQuery("SELECT " + viewColumns + " FROM " + Views.DIALER_SEARCH_VIEW + " ORDER BY "
                        + DialerSearch.RAW_CONTACT_ID, null);
                if (c != null) {
                    try {
                        mNumberCount = c.getCount();
                        LogUtils.d(TAG, "MTK-DialerSearch, DialerSearch View Count: " + mNumberCount);

                        while (c.moveToNext()) {
                            long tmpRawId = c.getLong(c.getColumnIndex(DialerSearch.RAW_CONTACT_ID));
                            long dataId = c.getLong(c.getColumnIndex(DialerSearchLookupColumns.DATA_ID));
                            if (rawId != tmpRawId) {
                                rawId = tmpRawId;
                                contactData = new ContactData(rawId, c.getLong(c.getColumnIndex(DialerSearch.CONTACT_ID)),
                                        c.getString(c.getColumnIndex(DialerSearch.NAME)), c.getInt(c
                                        .getColumnIndex(DialerSearch.INDICATE_PHONE_SIM)), c.getLong(c
                                        .getColumnIndex(DialerSearch.PHOTO_ID)), c.getString(c
                                        .getColumnIndex(DialerSearch.CONTACT_NAME_LOOKUP)), c.getInt(c
                                        .getColumnIndex(DialerSearch.IS_SDN_CONTACT)));
                                mContactMap.put(rawId, contactData);
                            }
                            ContactData refData = mContactMap.get(rawId);
                            if (refData == null) {
                                continue;
                            }
                            long id = c.getLong(c.getColumnIndex(DialerSearch.NAME_LOOKUP_ID));
                            int type = c.getInt(c.getColumnIndex(DialerSearch.SEARCH_PHONE_TYPE));
                            String label = c.getString(c.getColumnIndex(DialerSearch.SEARCH_PHONE_LABEL));
                            long callLogId = c.getLong(c.getColumnIndex(DialerSearch.CALL_LOG_ID));
                            String callDate = c.getString(c.getColumnIndex(DialerSearch.CALL_DATE));
                            int callType = c.getInt(c.getColumnIndex(DialerSearch.CALL_TYPE));
                            String geo = c.getString(c.getColumnIndex(DialerSearch.CALL_GEOCODED_LOCATION));

                            String accountId = c.getString(c.getColumnIndex(DialerSearch.PHONE_ACCOUNT_ID));
                            String accountComponentName = c.getString(c.getColumnIndex(DialerSearch.PHONE_ACCOUNT_COMPONENT_NAME));
                            int presentation = c.getInt(c.getColumnIndex(DialerSearch.NUMBER_PRESENTATION));

                            String number = c.getString(c.getColumnIndex(DialerSearch.SEARCH_PHONE_NUMBER));
                            refData.mNumberMap.add(new PhoneNumber(id, type, label, callLogId, callDate, callType, geo,
                                                                   accountId, accountComponentName, presentation, number, dataId));
                        }
                    } finally {
                        c.close();
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        // TODO: Review these variables' usage before
        mFilterCache = new ArrayList<String>();
        mPrevSearchNumberLen = 0;
        mIsDataInit = true;
        LogUtils.d(TAG, "MTK-DialerSearch, queryDialerSearchInit, end.");
    }

    private Cursor queryDialerSearch(SQLiteDatabase db, Uri uri) {
        LogUtils.d(TAG, "MTK-DialerSearch, queryDialerSearch, begin. uri: " + uri);

        String isDataReadyParam = uri.getQueryParameter(DATA_READY_FLAG);
        String filterParam = uri.getLastPathSegment();
        LogUtils.d(TAG, "MTK-DialerSearch, queryDialerSearch ,filterParam: "
                + filterParam + ", mIsDataInit = " + mIsDataInit);

        // Here may happen "cursor leak" detail refer to ALPS01476419.
        // But if we add synchronized, may lead to ANR, like ALPS01583861
        // TODO: 1. We may need review the function queryDialerSearchInit  and
        //       last migration is reasonable or not.
        //       2. We may shouldn't every time reproduce every thing, when
        //       dates have been updated.
        //       3. cursor leak issue may happen again, though dialer have reduce
        //       query times in ALPS01529545
        if (!mIsDataInit) {
            queryDialerSearchInit(db, uri);
        }

        Object[][] cursorValues = null;

        cursorValues = queryDialerSearchInternal(db, filterParam, null, null);
        Cursor c = buildCursor(cursorValues);
        LogUtils.d(TAG, "MTK-DialerSearch, queryDialerSearch, end. ResultCount: " + c.getCount());

        return c;
    }

    /// M: Remove abandoned dialer search entry @{
    /**
    private Cursor queryDialerSearchSimple(SQLiteDatabase db, Uri uri) {
        log("DIALER_SEARCH_SIMPLE begin. uri:" + uri);
        String filterParam = uri.getLastPathSegment();
        if (TextUtils.isEmpty(filterParam)) {
            return null;
        }
        mFilterCache = new ArrayList<String>();
        mPrevSearchNumberLen = 0;

        Object[][] cursorValues = queryDialerSearchInternal(db, filterParam, null, null);

        log("DIALER_SEARCH_SIMPLE end.");
        return buildCursor(cursorValues);
    }

    private Cursor queryDialerSearchIncrement(SQLiteDatabase db, Uri uri) {
        log("DIALER_SEARCH_INCREMENT begin. uri:" + uri);
        String filterParam = uri.getLastPathSegment();

        Object[][] cursorValues = null;
        // Check Input OR Delete
        int numberCount = filterParam.length();
        if (mPrevSearchNumberLen > numberCount) {
            // current operation is delete number to search
            mPrevSearchNumberLen = numberCount;
            if (mFilterCache.size() > 0) {
                mFilterCache.remove(mFilterCache.size() - 1);
                mResultCache.remove(mResultCache.size() - 1);
                if (mResultCache.size() > 0) {
                    cursorValues = mResultCache.get(mResultCache.size() - 1);
                }
            }
        } else if (mPrevSearchNumberLen == numberCount) {
            // current operation is delete number to search
            if (mResultCache.size() > 0) {
                cursorValues = mResultCache.get(mResultCache.size() - 1);
            }
        } else {
            mPrevSearchNumberLen = numberCount;
            String selection = mFilterCache.size() == 0 ? null :
                mFilterCache.get(mFilterCache.size() - 1);
            ResultCallBack result = new ResultCallBack();
            cursorValues = queryDialerSearchInternal(db, filterParam, selection, result);
            mFilterCache.add(result.mFilter);
            mResultCache.add(cursorValues);
        }
        Cursor c = buildCursor(cursorValues);
        log("DIALER_SEARCH_INCREMENT end");
        return c;
    }
    */
    /// M: @}

    private Object[][] queryDialerSearchInternal(SQLiteDatabase db, String filterParam,
            String selection, ResultCallBack callBack) {
        LogUtils.d(TAG, "MTK-DialerSearch, queryDialerSearchInternal begin. filterParam:" + filterParam
                   + "|selection:" + selection);

        Object[][] objectMap = null;
        StringBuilder selectedIds = new StringBuilder();
        int cursorPos = 0;
        db.beginTransaction();
        Cursor rawCursor = null;
        try {
            String mTableColumns = getDialerSearchNameTableColumns(mDisplayOrder, filterParam);
            rawCursor = db.rawQuery("SELECT "
                    + mTableColumns
                    + " FROM "
                    + TEMP_DIALER_SEARCH_TABLE
                    + " WHERE "
                    + (TextUtils.isEmpty(selection) ? "" :
                        (DialerSearchLookupColumns._ID + " IN (" + selection + ") AND "))
                    + " DIALER_SEARCH_MATCH_FILTER("
                    + DialerSearchLookupColumns.NORMALIZED_NAME + ","
                    + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + ","
                    + DialerSearchLookupColumns.NAME_TYPE + ",'"
                    + filterParam + "'" + ")"
                    + " ORDER BY " + DialerSearch.MATCHED_DATA_OFFSET + " COLLATE MATCHTYPE DESC,"
                    + DialerSearchLookupColumns.TIMES_USED + " DESC,"
                    + DialerSearchLookupColumns.SORT_KEY + " COLLATE PHONEBOOK,"
                    + DialerSearchLookupColumns.CALL_LOG_ID + " DESC " + " limit 500", null);
            if (rawCursor == null) {
                LogUtils.e(TAG, "--- rawCursor is null ---");
                return null;
            }
            objectMap = new Object [mNumberCount][];
            ArrayList<Object[]> callLogPartitionList = new ArrayList<Object[]>(256);
            HashMap<Long, Integer> matchPosMap = new HashMap<Long, Integer>();
            LogUtils.d(TAG, "MTK-DialerSearch, Cursor from temp dialer table, Count: " + rawCursor.getCount());

            while (rawCursor.moveToNext()) {
                long searchId = rawCursor.getLong(rawCursor.getColumnIndex(DialerSearchLookupColumns._ID));
                selectedIds.append(searchId).append(",");
                int nameType = rawCursor.getInt(rawCursor.getColumnIndex(DialerSearchLookupColumns.NAME_TYPE));

                String matchOffset = rawCursor.getString(rawCursor.getColumnIndex(DialerSearch.MATCHED_DATA_OFFSET));
                if (TextUtils.isEmpty(matchOffset))
                    break;
                long rawId = rawCursor.getLong(rawCursor.getColumnIndex(DialerSearchLookupColumns.RAW_CONTACT_ID));
                ContactData contactData = mContactMap.get(rawId);
                // if contactData is null, it mean the contacts data has not been cached,
                // and so the result will not show this persion. However, we do not remove
                // this result, it will show after the cache finish its update.
                if (contactData == null || contactData.mNumberMap == null) {
                    continue;
                }

                if (nameType == DialerSearchLookupType.NAME_EXACT) {
                    for (PhoneNumber number : contactData.mNumberMap) {
                        if (matchPosMap.containsKey(number.mId)) {
                            int pos = matchPosMap.get(number.mId);
                            objectMap[pos][DialerSearchQuery.DS_MATCHED_NAME_OFFSETS] = matchOffset;
                        } else {
                            matchPosMap.put(number.mId, cursorPos);
                            /// M: For ALPS01539080, if the amount of a contact's phone number were bigger than 2,
                             // the first phone number record would be associated with the incorrect data id which
                             // had been rewritten by the second phone number's PHONE_EXACT logic.
                             // Workaround this issue that just to set data id to -1 in this scenario. Dialer would
                             // get the correct phone number to make call.
                             // TODO: correlate PhoneNumber with DataId. Add "data_id" field in VIEW_DIALER_SEARCH
                            long numberDataId = contactData.mNumberMap.size() > 1 ? -1 : number.mDataId;
                            /// M: fix CR:ALPS01563203,SDN icon not show lock icon in Dialer
                            objectMap[cursorPos++] = buildCursorRecord(number.mId,
                                    contactData.mContactId, numberDataId, null, 0, 0, null, number.mPhoneAccountId, number.mPhoneAccountComponentName,
                                    number.mPresentation, contactData.mSimIndicate, 0, contactData.mPhotoId,
                                    number.mNumberType, number.mNumberLabel, contactData.mDisplayName, number.mNumber,
                                    contactData.mLookup, contactData.mIsSdn, null, matchOffset);
                        }
                    }
                } else if (nameType == DialerSearchLookupType.PHONE_EXACT) {

                    // two numbers
                    PhoneNumber number = null;
                    for (PhoneNumber n : contactData.mNumberMap) {
                        if (n.mId == searchId) {
                            number = n;
                            break;
                        }
                    }
                    if (number == null) {
                        continue;
                    }
                    if (rawId > 0) {
                        if (matchPosMap.containsKey(number.mId)) {
                            int pos = matchPosMap.get(number.mId);
                            objectMap[pos][DialerSearchQuery.DS_MATCHED_DATA_OFFSETS] = matchOffset;
                        } else {
                            matchPosMap.put(number.mId, cursorPos);
                            /// M: fix CR:ALPS01563203,SDN icon not show lock icon in Dialer.
                            objectMap[cursorPos++] = buildCursorRecord(number.mId,
                                    contactData.mContactId, number.mDataId, null, 0, 0, null, number.mPhoneAccountId, number.mPhoneAccountComponentName,
                                    number.mPresentation, contactData.mSimIndicate, 0, contactData.mPhotoId,
                                    number.mNumberType, number.mNumberLabel, contactData.mDisplayName, number.mNumber,
                                    contactData.mLookup, contactData.mIsSdn, matchOffset, null);
                        }
                    } else {
                        /// M: fix CR:ALPS01563203,SDN icon not show lock icon in Dialer.
                        callLogPartitionList.add(buildCursorRecord(number.mId,
                                contactData.mContactId, number.mDataId, number.mCallDate, number.mCallLogId,
                                number.mCallType, number.mGeoLocation, number.mPhoneAccountId, number.mPhoneAccountComponentName,
                                number.mPresentation, -1, 0, 0, 0, number.mNumberLabel, null, number.mNumber, null, contactData.mIsSdn,
                                matchOffset, null));
                    }
                }
            }
            if (selectedIds.length() > 0) {
                selectedIds.deleteCharAt(selectedIds.length() - 1);
            }
            rawCursor.close();
            if (callLogPartitionList != null && callLogPartitionList.size() > 0) {
                for (Object[] item:callLogPartitionList) {
                    objectMap[cursorPos++] = item;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            if (rawCursor != null) {
                rawCursor.close();
                rawCursor = null;
            }
            db.endTransaction();
        }
        if (callBack != null) {
            callBack.mCursorCount = cursorPos + 1;
            callBack.mFilter = selectedIds.toString();
        }
        LogUtils.d(TAG, "MTK-DialerSearch, queryDialerSearchInternal end. objectCount: " + cursorPos);

        return objectMap;
    }

    private String getDialerSearchNameTableColumns(int displayOrder, String filterParam) {
        String columns = DialerSearchLookupColumns._ID + " ,"
                + DialerSearchLookupColumns.RAW_CONTACT_ID + ","
                + DialerSearchLookupColumns.NAME_TYPE + ","
                + DialerSearchLookupColumns.DATA_ID;
        String searchParamList = "";
        if (displayOrder == ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE) {
            searchParamList = DialerSearchLookupColumns.NORMALIZED_NAME_ALTERNATIVE
                    + "," + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS_ALTERNATIVE
                    + "," + DialerSearchLookupColumns.NAME_TYPE
                    + ",'" + filterParam + "'";
        } else {
            searchParamList = DialerSearchLookupColumns.NORMALIZED_NAME + ","
                                    + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS + ","
                                    + DialerSearchLookupColumns.NAME_TYPE + ",'"
                                    + filterParam + "'";
        }
        return columns + ","
                + "DIALER_SEARCH_MATCH(" + searchParamList + ") AS "
                + DialerSearch.MATCHED_DATA_OFFSET;
    }

    private static String getOffsetsTempTableColumns(int displayOrder, String filterParam) {
        StringBuilder builder = new StringBuilder();

        builder.append(DialerSearchLookupColumns._ID + " AS ds_id");
        builder.append(", ");
        builder.append(DialerSearchLookupColumns.RAW_CONTACT_ID);
        builder.append(", ");
        builder.append(DialerSearchLookupColumns.CALL_LOG_ID);
        builder.append(", ");
        builder.append(DialerSearchLookupColumns.NAME_TYPE);
        builder.append(", ");
        builder.append(RawContacts.SORT_KEY_PRIMARY + " AS " + DialerSearchLookupColumns.SORT_KEY);
        builder.append(", ");
        builder.append(getOffsetColumn(displayOrder, filterParam) + " AS " + "offset");

        return builder.toString();
    }

    private static String getOffsetColumn(int displayOrder, String filterParam) {
        String searchParamList = "";
        if (displayOrder == ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE) {
            searchParamList = DialerSearchLookupColumns.NORMALIZED_NAME_ALTERNATIVE
                    + "," + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS_ALTERNATIVE
                    + "," + DialerSearchLookupColumns.NAME_TYPE
                    + ",'" + filterParam + "'";
        } else {
            searchParamList = DialerSearchLookupColumns.NORMALIZED_NAME
                    + "," + DialerSearchLookupColumns.SEARCH_DATA_OFFSETS
                    + "," + DialerSearchLookupColumns.NAME_TYPE 
                    + ",'" + filterParam + "'";
        }
        return "DIALER_SEARCH_MATCH(" + searchParamList + ")";
    }

    private Cursor buildCursor(Object[][] cursorValues) {
        MatrixCursor c = new MatrixCursor(DialerSearchQuery.COLUMNS);
        if (cursorValues != null) {
            for (Object[] record : cursorValues) {
                if (record == null) {
                    break;
                }
                c.addRow(record);
            }
        }
        return c;
    }

    private Object[] buildCursorRecord(long id, long contactId, long dataId, String callData, long callLogId,
            int callType, String geo, String phoneAccountId, String phoneAccountComponentName, int presentation, int simIndicator, int starred,
            long photoId, int numberType, String numberLabel, String name, String number, String lookup, int isSdn,
            String phoneOffset, String nameOffset) {
        Object[] record = new Object[] {
                id, contactId, dataId, callData, callLogId,
                callType, geo, phoneAccountId, phoneAccountComponentName, presentation, simIndicator, starred,
                photoId, numberType, numberLabel, name, number, lookup, isSdn,
                phoneOffset, nameOffset
        };
        return record;
    }

    public static class PhoneNumber {
        long mId;
        int mNumberType;
        String mNumberLabel;
        long mCallLogId;
        String mCallDate;
        int mCallType;
        String mGeoLocation;
        String mPhoneAccountId;
        String mPhoneAccountComponentName;
        int mPresentation;
        String mNumber;
        // M: fix CR:ALPS01712363,add data_id fields to ensure query right data_id
        long mDataId;
        PhoneNumber(long phoneId, int numberType, String numberLabel, long callLogId, String callDate, int callType,
                String geo, String phoneAccountId, String phoneAccountComponentName, int presentation, String phoneNumber, long dataId) {
            mId = phoneId;
            mNumberType = numberType;
            mNumberLabel = numberLabel;
            mCallLogId = callLogId;
            mCallDate = callDate;
            mCallType = callType;
            mGeoLocation = geo;
            mPhoneAccountId = phoneAccountId;
            mPhoneAccountComponentName = phoneAccountComponentName;
            mPresentation = presentation;
            mNumber = phoneNumber;
            mDataId = dataId;
        }

        @Override
        public String toString() {
            return mId + "," + mNumber + "," + mDataId;
        }
    }

    public static class ContactData {
        long mRawId;
        long mContactId;
        String mDisplayName;
        int mSimIndicate;
        long mPhotoId;
        String mLookup;
        int mIsSdn;
        ArrayList<PhoneNumber> mNumberMap;

        ContactData(long rId, long rContactId, String rName, int rIndicate, long rPhotoId,
                String rLookup, int rIsSdn) {
            mRawId = rId;
            mContactId = rContactId;
            mDisplayName = rName;
            mSimIndicate = rIndicate;
            mPhotoId = rPhotoId;
            mLookup = rLookup;
            mIsSdn = rIsSdn;
            mNumberMap = new ArrayList<PhoneNumber>();
        }

        @Override
        public String toString() {
            return mRawId + "," + mContactId + "," + mDisplayName + "||" + mNumberMap.toString();
        }
    }

    class ResultCallBack {
        int mCursorCount;
        String mFilter;
    }

    public static synchronized DialerSearchSupport getInstance(Context context, ContactsDatabaseHelper helper) {
        if (sDialerSearchSupport == null) {
            sDialerSearchSupport = new DialerSearchSupport(context, helper);
            sDialerSearchSupport.initialize();
        }
        return sDialerSearchSupport;
    }

    private void scheduleBackgroundTask(int task) {
        mBackgroundHandler.sendEmptyMessage(task);
    }

    private void scheduleBackgroundTask(int task, long delayMillus) {
        mBackgroundHandler.sendEmptyMessageDelayed(task, delayMillus);
    }

    public void notifyDialerSearchChange() {
        mBackgroundHandler.removeMessages(BACKGROUND_TASK_CREATE_CACHE);
        LogUtils.d(TAG, "notifyDialerSearchChange, mIsCached:" + mIsCached);
        if (mIsCached) {
            mIsCached = false; //disable cache before caches removed when data changed.
            scheduleBackgroundTask(BACKGROUND_TASK_REMOVE_CACHE);
        }
        scheduleBackgroundTask(BACKGROUND_TASK_CREATE_CACHE, CACHE_TASK_DELAY_MILLIS);
    }
}
