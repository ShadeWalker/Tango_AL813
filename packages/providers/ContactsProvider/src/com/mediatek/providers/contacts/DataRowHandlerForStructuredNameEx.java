package com.mediatek.providers.contacts;

import android.content.ContentValues;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.util.Log;
import com.android.providers.contacts.ContactsDatabaseHelper;
import com.android.providers.contacts.ContactsDatabaseHelper.Tables;
import com.android.providers.contacts.DataRowHandler.DataDeleteQuery;
import com.mediatek.providers.contacts.DialerSearchSupport.DialerSearchLookupColumns;
import com.mediatek.providers.contacts.DialerSearchSupport.DialerSearchLookupType;
import com.android.providers.contacts.aggregation.ContactAggregator;
import com.android.providers.contacts.DataRowHandlerForStructuredName;
import com.android.providers.contacts.TransactionContext;
import com.android.providers.contacts.NameSplitter;
import com.android.providers.contacts.NameLookupBuilder;
import com.mediatek.providers.contacts.ContactsProviderUtils;
import com.mediatek.providers.contacts.LogUtils;

public class DataRowHandlerForStructuredNameEx extends
        DataRowHandlerForStructuredName {
    private static final String TAG = "DataRowHandlerForStructuredNameEx";
    private static final boolean DBG = ContactsProviderUtils.DBG_DIALER_SEARCH;
    private SQLiteStatement mDialerSearchNewRecordInsert;
    private SQLiteStatement mDialerSearchDelete;

    public DataRowHandlerForStructuredNameEx(Context context, ContactsDatabaseHelper dbHelper,
            ContactAggregator aggregator, NameSplitter splitter,
            NameLookupBuilder nameLookupBuilder) {
        super(context, dbHelper, aggregator, splitter, nameLookupBuilder);
    }

    @Override
    public int delete(SQLiteDatabase db, TransactionContext txContext, Cursor c) {
        int count = super.delete(db, txContext, c);
        if (ContactsProviderUtils.isSearchDbSupport()) {
            long dataId = c.getLong(DataDeleteQuery._ID);
            deleteNameForDialerSearch(db, dataId);
        }
        return count;
    }

    @Override
    protected void insertDialerSearchName(SQLiteDatabase db, long rawContactId, long dataId,
            ContentValues values) {
        if (ContactsProviderUtils.isSearchDbSupport()) {
            String name = values.getAsString(StructuredName.DISPLAY_NAME);
            insertNameForDialerSearch(db, rawContactId, dataId, name);
        }
    }

    public void insertNameForDialerSearch(SQLiteDatabase db, long rawContactId, long dataId, String name) {
        if (mDialerSearchNewRecordInsert == null) {
            mDialerSearchNewRecordInsert = db.compileStatement(
                    "INSERT INTO " + Tables.DIALER_SEARCH + "(" +
                    DialerSearchLookupColumns.RAW_CONTACT_ID + "," +
                    DialerSearchLookupColumns.DATA_ID + "," +
                    DialerSearchLookupColumns.NORMALIZED_NAME + "," +
                    DialerSearchLookupColumns.NAME_TYPE + "," +
                    DialerSearchLookupColumns.CALL_LOG_ID + "," +
                    DialerSearchLookupColumns.NORMALIZED_NAME_ALTERNATIVE + ")" +
                    " VALUES (?,?,?,?,?,?)");
        }
        if (name == null) {
            return;
        }
        long mCallLogId = 0;
        //Do not insert name now, update it later for both name and alternative name.
        mDialerSearchNewRecordInsert.bindLong(1, rawContactId);
        mDialerSearchNewRecordInsert.bindLong(2, dataId);
        mDialerSearchNewRecordInsert.bindNull(3);
        mDialerSearchNewRecordInsert.bindLong(4, DialerSearchLookupType.NAME_EXACT);
        mDialerSearchNewRecordInsert.bindLong(5, mCallLogId);
        mDialerSearchNewRecordInsert.bindNull(6);
        mDialerSearchNewRecordInsert.executeInsert();
        LogUtils.d(TAG,"[insertNameForDialerSearch]insert name records into dialer search table.");
    }

    public void updateNameForDialerSearch(SQLiteDatabase db, long rawContactId, long dataId, String name) {
    }

    private void deleteNameForDialerSearch(SQLiteDatabase db, long dataId) {
        if (mDialerSearchDelete == null) {
            mDialerSearchDelete = db.compileStatement(
                    "DELETE FROM " + Tables.DIALER_SEARCH +
                    " WHERE " + DialerSearchLookupColumns.DATA_ID + "=?");
        }
        mDialerSearchDelete.bindLong(1, dataId);
        mDialerSearchDelete.execute();
        LogUtils.d(TAG,"[deleteNameForDialerSearch]delete name records in dialer search table");
    }

}
