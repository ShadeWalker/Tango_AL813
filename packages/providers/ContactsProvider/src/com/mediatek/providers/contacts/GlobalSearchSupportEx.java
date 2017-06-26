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

import android.app.SearchManager;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.SearchSnippets;
import android.provider.ContactsContract.StatusUpdates;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.providers.contacts.ContactsProvider2;
import com.android.providers.contacts.ContactsDatabaseHelper.AggregatedPresenceColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.ContactsColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.Tables;
import com.android.providers.contacts.ContactsDatabaseHelper.Views;
import com.android.providers.contacts.R;
import com.mediatek.providers.contacts.LogUtils;
import com.mediatek.providers.contacts.ContactsProviderUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * M: Support for global search integration for Contacts.
 */
public class GlobalSearchSupportEx {
    private static final String TAG = "GlobalSearchSupportEx";
    private GlobalSearchSupportEx() {
    }

    public static Cursor processCursor(Cursor c,String[] projection,String lookupKey,String[] search_suggestions_columns) {
        if (c != null && c.getCount() == 1) {
            c.moveToFirst();
            int index = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_SHORTCUT_ID);
            if (index >= 0) {
                String lookup = c.getString(index);
                LogUtils.d(TAG,"[handleSearchShortcutRefresh]new lookupKey:" + lookup
                        + "||It is NE old:" + (lookup != null && !lookup.equals(lookupKey)));
                if (lookup != null && !lookup.equals(lookupKey)) {
                    c.close();
                    return new MatrixCursor(
                            projection == null ? search_suggestions_columns
                                    : projection);
                }
            }
            c.moveToPosition(-1);
        }
        return c;
    }
}