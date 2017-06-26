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

/**
 * The Telephony PlusCode Utility interface.
 * @hide
 */
public interface IPlusCodeUtils {

    static final String PROPERTY_OPERATOR_MCC = "cdma.operator.mcc";
    static final String PROPERTY_OPERATOR_SID = "cdma.operator.sid";
    static final String PROPERTY_TIME_LTMOFFSET = "cdma.operator.ltmoffset";
    static final String PROPERTY_ICC_CDMA_OPERATOR_MCC = "cdma.icc.operator.mcc";

    /**
     * Check mcc by sid ltm off.
     * @param mccMnc the MCCMNC
     * @return the MCCMNC
     */
    String checkMccBySidLtmOff(String mccMnc);

    /**
     * @return if can convert plus code to IddNdd.
     */
    boolean canFormatPlusToIddNdd();

    /**
     * @return if can format plus code for sms.
     */
    boolean canFormatPlusCodeForSms();

    /**
     * Replace plus code with IDD or NDD input: the number input by the user.
     * @param number the number value
     * @return the number after deal with plus code
     */
    String replacePlusCodeWithIddNdd(String number);

    /**
     * Replace puls code, the phone number for MT or sender of sms or mms.
     * @param number the number value
     * @return the number after deal with plus code
     */
    String replacePlusCodeForSms(String number);

    /**
     * Replace puls code with IDD or NDD input: the phone number for MT or
     * sender of sms or mms.
     * @param number the number value
     * @return the number after deal with plus code
     */
    String removeIddNddAddPlusCodeForSms(String number);

    /**
     * Replace puls code with IDD or NDD input: the phone number for MT or
     * sender of sms or mms.
     * @param number the number value
     * @return the number after deal with plus code
     */
    String removeIddNddAddPlusCode(String number);
}
