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

import android.telephony.Rlog;

/**
 * The Telephony PlusCode Utility interface.
 * @hide
 */
public class ViaPlusCodeUtils extends DefaultPlusCodeUtils {

    private static final String LOG_TAG = "ViaPlusCodeUtils";

    @Override
    public String checkMccBySidLtmOff(String mccMnc) {
        log("checkMccBySidLtmOff mccMnc=" + mccMnc);
        return PlusCodeToIddNddUtils.checkMccBySidLtmOff(mccMnc);
    }

    @Override
    public boolean canFormatPlusToIddNdd() {
        log("canFormatPlusToIddNdd");
        return PlusCodeToIddNddUtils.canFormatPlusToIddNdd();
    }

    @Override
    public boolean canFormatPlusCodeForSms() {
        log("canFormatPlusCodeForSms");
        return PlusCodeToIddNddUtils.canFormatPlusCodeForSms();
    }

    @Override
    public String replacePlusCodeWithIddNdd(String number) {
        log("replacePlusCodeWithIddNdd number=" + number);
        return PlusCodeToIddNddUtils.replacePlusCodeWithIddNdd(number);
    }

    @Override
    public String replacePlusCodeForSms(String number) {
        log("replacePlusCodeForSms number=" + number);
        return PlusCodeToIddNddUtils.replacePlusCodeForSms(number);
    }

    @Override
    public String removeIddNddAddPlusCodeForSms(String number) {
        log("removeIddNddAddPlusCodeForSms number=" + number);
        return PlusCodeToIddNddUtils.removeIddNddAddPlusCodeForSms(number);
    }

    @Override
    public String removeIddNddAddPlusCode(String number) {
        log("removeIddNddAddPlusCode number=" + number);
        return PlusCodeToIddNddUtils.removeIddNddAddPlusCode(number);
    }

    private static void log(String string) {
        if (DBG) {
            Rlog.d(LOG_TAG, string);
        }
    }
}
