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

package com.mediatek.internal.telephony.cdma;

import android.content.Context;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.cdma.CDMAPhone;

/**
 * The default GPS process class.
 * @hide
 */
public class DefaultGpsProcess implements IGpsProcess {

    private static final String LOG_TAG = "DefaultGpsProcess";

    /**
     * @hide
     *
     * @param context context instance
     * @param phone The phone instance
     * @param ci The command interface
     */
    public DefaultGpsProcess(Context context, CDMAPhone phone, CommandsInterface ci) {
        log("DefaultGpsProcess created");
    }

    /**
     * @hide
     */
    public void start() {
        log("DefaultGpsProcess start");
    }

    /**
     * @hide
     */
    public void stop() {
        log("DefaultGpsProcess stop");
    }

    private static void log(String string) {
        Rlog.d(LOG_TAG, string);
    }
}


