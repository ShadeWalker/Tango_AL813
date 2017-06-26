/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom;

/**
 * App-wide constants for the phone app.
 *
 * Any constants that need to be shared between two or more classes within
 * the com.android.phone package should be defined here.  (Constants that
 * are private to only one class can go in that class's .java file.)
 */
public class Constants {
    //
    // URI schemes
    //

    public static final String SCHEME_SMSTO = "smsto";

    public static final String EXTRA_IS_IP_DIAL = "com.android.phone.extra.ip";
    public static final String PHONE_PACKAGE = "com.android.phone";
    public static final String IP_PREFIX_SETTING_CLASS_NAME = "com.mediatek.settings.IpPrefixPreference";
    public static final String EXTRA_ACTUAL_NUMBER_TO_DIAL = "android.phone.extra.ACTUAL_NUMBER_TO_DIAL";
}
