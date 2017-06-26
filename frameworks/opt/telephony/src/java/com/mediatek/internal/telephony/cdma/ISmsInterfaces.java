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

import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.BearerData.CodingException;
import com.android.internal.telephony.cdma.sms.UserData;

import com.android.internal.util.BitwiseOutputStream;

/**
 * Interface for CDMA SMS Telephony framework.
 *
 * {@hide}
 */
public interface ISmsInterfaces {

    /**
     * convert it from one submitpdu to a normal android pdu.
     * @param pdu the submit pdu.
     *
     * @return byte array of normal android pdu.
     */
    byte[] convertSubmitpduToPdu(byte[] pdu);

    /**
     * Generate an CDMA EF_SMS record from status and raw PDU.
     * @param status Message status.
     * @param pdu Raw message PDU.
     *
     * @return byte array of a record.
     */
    byte[] makeCDMASmsRecordData(int status, byte[] pdu);

    /**
     * Calculate message encoding length.
     *
     * @param msg message text.
     * @param force7BitEncoding ignore illegal characters if true.
     *
     * @return septet count, -1 for fail.
     */
    TextEncodingDetails calcTextEncodingDetails(CharSequence msg, boolean force7BitEncoding);

    /**
     * encode User Data Payload.
     *
     * @param uData User Data.
     *
     * @throws CodingException Thrown if fail.
     */
    void encodeUserDataPayload(UserData uData) throws CodingException;

    /**
     * encode User Data Payload.
     *
     * @param bData Bearer Data.
     * @param outStream output stream.
     *
     * @throws BitwiseOutputStream.AccessException Thrown if fail.
     */
    void encodeTimeStamp(BearerData bData, BitwiseOutputStream outStream)
            throws BitwiseOutputStream.AccessException;

    /**
     * Decode 7-bit ascii userdata.
     *
     *
     * @param data the whole userdata data and
     *               the userdata header if existed in bytes of PDU.
     * @param offset userdata date offset, 0 for no header.
     * @param numFields fields number.
     *
     * @throws CodingException Thrown if fail.
     * @return decoded string text.
     */
    String decode7bitAscii(byte[] data, int offset, int numFields) throws CodingException;

    /**
     * Decode 16-bit userdata.
     *
     * @param data the whole userdata data and
     *               the userdata header if existed in bytes of PDU.
     * @param offset userdata date offset, 0 for no header.
     * @param numFields fields number.
     *
     * @throws CodingException Thrown if fail.
     * @return decoded string text.
     */
    String decodeUtf16(byte[] data, int offset, int numFields) throws CodingException;
}
