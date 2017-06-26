/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.mediatek.dialer.dialersearch;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.CallUtil;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;

import com.mediatek.dialer.util.LogUtils;


/**
 * List adapter to display the SmartDial search results.
 */
public class SmartDialNumberListAdapterEx extends DialerPhoneNumberListAdapterEx {

    private static final String TAG = "SmartDialNumberListAdapterEx";

    private SmartDialNameMatcher mNameMatcher;

    public SmartDialNumberListAdapterEx(Context context) {
        super(context);
        mNameMatcher = new SmartDialNameMatcher("", SmartDialPrefix.getMap());
    }

    public void configureLoader(DialerSearchCursorLoader loader) {

        //LogUtils.d(TAG, "MTK-DialerSearch, configureLoader, getQueryString: " + getQueryString() + " ,loader: " + loader);

        if (getQueryString() == null) {
            loader.configureQuery("", true);
            mNameMatcher.setQuery("");
        } else {
            loader.configureQuery(getQueryString(), true);
            mNameMatcher.setQuery(PhoneNumberUtils.normalizeNumber(getQueryString()));
        }
    }

    @Override
    public Uri getDataUri(int position) {
        Cursor cursor = ((Cursor) getItem(position));
        if (cursor != null) {
            long id = cursor.getLong(DATA_ID_INDEX);
            //LogUtils.d(TAG, "SmartDialNumberListAdatper: DataId:" + id);

            if (id < 0) {
                return null;
            } else {
                return ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id);
            }
        } else {
            Log.w(TAG, "Cursor was null in getDataUri() call. Returning null instead.");
            return null;
        }
    }

    @Override
    public void setQueryString(String queryString) {
        /** M: fix for ALPS01759137, set mFormattedQueryString in advance @{ */
        super.setQueryString(queryString);
        /** @} */
        final boolean showNumberShortcuts = !TextUtils.isEmpty(getFormattedQueryString());
        boolean changed = false;
        changed |= setShortcutEnabled(SHORTCUT_ADD_NUMBER_TO_CONTACTS, showNumberShortcuts);
        changed |= setShortcutEnabled(SHORTCUT_MAKE_VIDEO_CALL,
                showNumberShortcuts && CallUtil.isVideoEnabled(getContext()));
        if (changed) {
            notifyDataSetChanged();
        }
    }

    @Override
    public String getPhoneNumber(int position) {
        Cursor cursor = ((Cursor) getItem(position));
        if (cursor != null) {
            String phoneNumber = cursor.getString(SEARCH_PHONE_NUMBER_INDEX);
            //LogUtils.d(TAG, "SmartDialNumberListAdatper: phoneNumber:" + phoneNumber);

            return phoneNumber;
        } else {
            Log.w(TAG, "Cursor was null in getPhoneNumber() call. Returning null instead.");
            return null;
        }
    }
}
