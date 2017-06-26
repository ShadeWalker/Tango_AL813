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

import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.BearerData.CodingException;
import com.android.internal.telephony.cdma.sms.UserData;
import com.android.internal.util.BitwiseOutputStream;

/**
 * Interface for CDMA SMS Telephony framework.
 * @hide
 */
public class ViaSmsInterfacesAdapter extends DefaultSmsInterfaces {

    private static final String LOG_TAG = "ViaSmsInterfacesAdapter";

    @Override
    public byte[] convertSubmitpduToPdu(byte[] pdu) {
        log("convertSubmitpduToPdu pdu = " + pdu);
        return RuimSmsInterfaces.convertSubmitpduToPdu(pdu);
    }

    @Override
    public byte[] makeCDMASmsRecordData(int status, byte[] pdu) {
        log("makeCDMASmsRecordData status = " + status);
        log("makeCDMASmsRecordData pdu = " + pdu);
        return RuimSmsInterfaces.makeCDMASmsRecordData(status, pdu);
    }

    @Override
    public TextEncodingDetails calcTextEncodingDetails(CharSequence msg,
            boolean force7BitEncoding) {
        log("calcTextEncodingDetails msg = " + msg);
        log("calcTextEncodingDetails force7BitEncoding = " + force7BitEncoding);
        return BearerDataInterfaces.calcTextEncodingDetails(msg, force7BitEncoding);
    }

    @Override
    public void encodeUserDataPayload(UserData uData) throws CodingException {
        log("encodeUserDataPayload");
        BearerDataInterfaces.encodeUserDataPayload(uData);
    }

    @Override
    public void encodeTimeStamp(BearerData bData, BitwiseOutputStream outStream)
            throws BitwiseOutputStream.AccessException {
        log("encodeTimeStamp outStream = " + outStream);
        BearerDataInterfaces.encodeTimeStamp(bData, outStream);
    }

    @Override
    public String decode7bitAscii(byte[] data, int offset, int numFields) throws CodingException {
        log("decode7bitAscii data = " + data);
        log("decode7bitAscii offset = " + offset);
        log("decode7bitAscii numFields = " + numFields);
        return BearerDataInterfaces.decode7bitAscii(data, offset, numFields);
    }


    @Override
    public String decodeUtf16(byte[] data, int offset, int numFields) throws CodingException {
        log("decodeUtf16 data = " + data);
        log("decodeUtf16 offset = " + offset);
        log("decodeUtf16 numFields = " + numFields);
        return BearerDataInterfaces.decodeUtf16(data, offset, numFields);
    }

    private static void log(String string) {
        if (DBG) {
            Rlog.d(LOG_TAG, string);
        }
    }
}
