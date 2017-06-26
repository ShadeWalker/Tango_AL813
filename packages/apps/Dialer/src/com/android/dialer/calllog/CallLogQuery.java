/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllog;

import com.mediatek.dialer.util.DialerFeatureOptions;

import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;

/**
 * The query for the call log table.
 */
public final class CallLogQuery {

    public static final String[] ORIGINAL_PROJECTION = new String[] {
            Calls._ID,                          // 0
            Calls.NUMBER,                       // 1
            Calls.DATE,                         // 2
            Calls.DURATION,                     // 3
            Calls.TYPE,                         // 4
            Calls.COUNTRY_ISO,                  // 5
            Calls.VOICEMAIL_URI,                // 6
            Calls.GEOCODED_LOCATION,            // 7
            Calls.CACHED_NAME,                  // 8
            Calls.CACHED_NUMBER_TYPE,           // 9
            Calls.CACHED_NUMBER_LABEL,          // 10
            Calls.CACHED_LOOKUP_URI,            // 11
            Calls.CACHED_MATCHED_NUMBER,        // 12
            Calls.CACHED_NORMALIZED_NUMBER,     // 13
            Calls.CACHED_PHOTO_ID,              // 14
            Calls.CACHED_FORMATTED_NUMBER,      // 15
            Calls.IS_READ,                      // 16
            Calls.NUMBER_PRESENTATION,          // 17
            Calls.PHONE_ACCOUNT_COMPONENT_NAME, // 18
            Calls.PHONE_ACCOUNT_ID,             // 19
            Calls.FEATURES,                     // 20
            Calls.DATA_USAGE,                   // 21
            Calls.TRANSCRIPTION                 // 22
    };

    public static final int ID = 0;
    public static final int NUMBER = 1;
    public static final int DATE = 2;
    public static final int DURATION = 3;
    public static final int CALL_TYPE = 4;
    public static final int COUNTRY_ISO = 5;
    public static final int VOICEMAIL_URI = 6;
    public static final int GEOCODED_LOCATION = 7;
    public static final int CACHED_NAME = 8;
    public static final int CACHED_NUMBER_TYPE = 9;
    public static final int CACHED_NUMBER_LABEL = 10;
    public static final int CACHED_LOOKUP_URI = 11;
    public static final int CACHED_MATCHED_NUMBER = 12;
    public static final int CACHED_NORMALIZED_NUMBER = 13;
    public static final int CACHED_PHOTO_ID = 14;
    public static final int CACHED_FORMATTED_NUMBER = 15;

    /// M: [Union Query] for MTK CallLog query @{
    public static final int IS_READ = DialerFeatureOptions.CALL_LOG_UNION_QUERY ? 8 : 16;
    public static final int NUMBER_PRESENTATION = DialerFeatureOptions.CALL_LOG_UNION_QUERY ? 9 : 17;
    public static final int ACCOUNT_COMPONENT_NAME = DialerFeatureOptions.CALL_LOG_UNION_QUERY ? 10 : 18;
    public static final int ACCOUNT_ID = DialerFeatureOptions.CALL_LOG_UNION_QUERY ? 11 : 19;
    public static final int FEATURES = DialerFeatureOptions.CALL_LOG_UNION_QUERY ? 12 : 20;
    public static final int DATA_USAGE = DialerFeatureOptions.CALL_LOG_UNION_QUERY ? 13 : 21;
    public static final int TRANSCRIPTION = DialerFeatureOptions.CALL_LOG_UNION_QUERY ? 14 : 22;
    /// @}

    ///----------------------------------------Mediatek--------------------------------------------
    /// M: [Union Query] for MTK CallLog query @{
    // change callLog query data source from calls table to calls join data view
    // to get more information for MTK features, gemini+, VT, etc...
    //
    // Must match the definition in CallLogProvider
    public static final String CALL_NUMBER_TYPE = "calllognumbertype";
    public static final String CALL_NUMBER_TYPE_ID = "calllognumbertypeid";
    /// @}

    /// M: [Union Query] for MTK CallLog query @{
    public static final String[] UNION_PROJECTION =  new String[] {
        Calls._ID,                          // 0
        Calls.NUMBER,                       // 1
        Calls.DATE,                         // 2
        Calls.DURATION,                     // 3
        Calls.TYPE,                         // 4
        Calls.COUNTRY_ISO,                  // 5
        Calls.VOICEMAIL_URI,                // 6
        Calls.GEOCODED_LOCATION,            // 7
        Calls.IS_READ,                      // 8
        Calls.NUMBER_PRESENTATION,          // 9
        Calls.PHONE_ACCOUNT_COMPONENT_NAME, // 10
        Calls.PHONE_ACCOUNT_ID,             // 11
        Calls.FEATURES,                     // 12
        Calls.DATA_USAGE,                   // 13
        Calls.TRANSCRIPTION,                // 14

        Calls.RAW_CONTACT_ID,               // 15
        Calls.DATA_ID,                      // 16
        Contacts.DISPLAY_NAME,              // 17
        CALL_NUMBER_TYPE,                   // 18
        CALL_NUMBER_TYPE_ID,                // 19
        Data.PHOTO_ID,                      // 20
        RawContacts.INDICATE_PHONE_SIM,     // 21
        RawContacts.CONTACT_ID,             // 22
        Contacts.LOOKUP_KEY,                // 23
        Data.PHOTO_URI,                     // 24
        Calls.IP_PREFIX,                    // 25
        RawContacts.IS_SDN_CONTACT,         // 26
        /// M: For Volte conference call calllog @{
        Calls.CONFERENCE_CALL_ID,           // 27
        Calls.SORT_DATE                     // 28
        /// @}
    };
    public static final String[] _PROJECTION =
            DialerFeatureOptions.CALL_LOG_UNION_QUERY ? UNION_PROJECTION : ORIGINAL_PROJECTION;
    /// @}

    /// M: [Union Query] for MTK CallLog query @{
    public static final int CALLS_JOIN_DATA_VIEW_RAW_CONTACT_ID = 15;
    public static final int CALLS_JOIN_DATA_VIEW_DATA_ID = 16;

    public static final int CALLS_JOIN_DATA_VIEW_DISPLAY_NAME = 17;
    public static final int CALLS_JOIN_DATA_VIEW_CALL_NUMBER_TYPE = 18;
    public static final int CALLS_JOIN_DATA_VIEW_CALL_NUMBER_TYPE_ID = 19;
    public static final int CALLS_JOIN_DATA_VIEW_PHOTO_ID = 20;
    public static final int CALLS_JOIN_DATA_VIEW_INDICATE_PHONE_SIM = 21;
    public static final int CALLS_JOIN_DATA_VIEW_CONTACT_ID = 22;
    public static final int CALLS_JOIN_DATA_VIEW_LOOKUP_KEY = 23;
    public static final int CALLS_JOIN_DATA_VIEW_PHOTO_URI = 24;
    public static final int CALLS_JOIN_DATA_VIEW_IP_PREFIX = 25;
    public static final int CALLS_JOIN_DATA_VIEW_IS_SDN_CONTACT = 26;
    /// M: For Volte conference call calllog @{
    public static final int CALLS_JOIN_DATA_VIEW_CONF_CALL_ID = 27;
    public static final int CALLS_JOIN_DATA_VIEW_SORT_DATE = 28;
    /// @}
    /// @}
}
