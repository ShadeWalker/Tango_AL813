/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
* Copyright (C) 2013 Samsung System LSI
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
package com.android.bluetooth.map;

import android.content.Context;

import android.location.Country;
import android.location.CountryDetector;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.android.i18n.phonenumbers.AsYouTypeFormatter;
import com.android.i18n.phonenumbers.PhoneNumberUtil;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;


/**
 * Various utility methods and generic defines that can be used throughout MAPS
 */
public class BluetoothMapUtils {

    private static final String TAG = "[MAP]MapUtils";
    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;
    /* We use the upper 4 bits for the type mask.
     * TODO: When more types are needed, consider just using a number
     *       in stead of a bit to indicate the message type. Then 4
     *       bit can be use for 16 different message types.
     */
    private static final long HANDLE_TYPE_MASK            = (((long)0xf)<<56);
    private static final long HANDLE_TYPE_MMS_MASK        = (((long)0x1)<<56);
    private static final long HANDLE_TYPE_EMAIL_MASK      = (((long)0x2)<<56);
    private static final long HANDLE_TYPE_SMS_GSM_MASK    = (((long)0x4)<<56);
    private static final long HANDLE_TYPE_SMS_CDMA_MASK   = (((long)0x8)<<56);

    /**
     * This enum is used to convert from the bMessage type property to a type safe
     * type. Hence do not change the names of the enum values.
     */
    public enum TYPE{
        EMAIL,
        SMS_GSM,
        SMS_CDMA,
        MMS
    }

    public static String getLongAsString(long v) {
        char[] result = new char[16];
        int v1 = (int) (v & 0xffffffff);
        int v2 = (int) ((v>>32) & 0xffffffff);
        int c;
        for (int i = 0; i < 8; i++) {
            c = v2 & 0x0f;
            c += (c < 10) ? '0' : ('A'-10);
            result[7 - i] = (char) c;
            v2 >>= 4;
            c = v1 & 0x0f;
            c += (c < 10) ? '0' : ('A'-10);
            result[15 - i] = (char)c;
            v1 >>= 4;
        }
        return new String(result);
    }

    /**
     * Convert a Content Provider handle and a Messagetype into a unique handle
     * @param cpHandle content provider handle
     * @param messageType message type (TYPE_MMS/TYPE_SMS_GSM/TYPE_SMS_CDMA/TYPE_EMAIL)
     * @return String Formatted Map Handle
     */
    public static String getMapHandle(long cpHandle, TYPE messageType){
        String mapHandle = "-1";
        switch(messageType)
        {

            case MMS:
                mapHandle = getLongAsString(cpHandle | HANDLE_TYPE_MMS_MASK);
                break;
            case SMS_GSM:
                mapHandle = getLongAsString(cpHandle | HANDLE_TYPE_SMS_GSM_MASK);
                break;
            case SMS_CDMA:
                mapHandle = getLongAsString(cpHandle | HANDLE_TYPE_SMS_CDMA_MASK);
                break;
            case EMAIL:
                mapHandle = getLongAsString(cpHandle | HANDLE_TYPE_EMAIL_MASK);
                break;
                default:
                    throw new IllegalArgumentException("Message type not supported");
        }
        return mapHandle;

    }

    /**
     * Convert a handle string the the raw long representation, including the type bit.
     * @param mapHandle the handle string
     * @return the handle value
     */
    static public long getMsgHandleAsLong(String mapHandle){
        return Long.parseLong(mapHandle, 16);
    }
    /**
     * Convert a Map Handle into a content provider Handle
     * @param mapHandle handle to convert from
     * @return content provider handle without message type mask
     */
    static public long getCpHandle(String mapHandle)
    {
        long cpHandle = getMsgHandleAsLong(mapHandle);
        if(D)Log.d(TAG,"-> MAP handle:"+mapHandle);
        /* remove masks as the call should already know what type of message this handle is for */
        cpHandle &= ~HANDLE_TYPE_MASK;
        if(D)Log.d(TAG,"->CP handle:"+cpHandle);

        return cpHandle;
    }

    /**
     * Extract the message type from the handle.
     * @param mapHandle
     * @return
     */
    static public TYPE getMsgTypeFromHandle(String mapHandle) {
        long cpHandle = getMsgHandleAsLong(mapHandle);

        if((cpHandle & HANDLE_TYPE_MMS_MASK) != 0)
            return TYPE.MMS;
        if((cpHandle & HANDLE_TYPE_EMAIL_MASK) != 0)
            return TYPE.EMAIL;
        if((cpHandle & HANDLE_TYPE_SMS_GSM_MASK) != 0)
            return TYPE.SMS_GSM;
        if((cpHandle & HANDLE_TYPE_SMS_CDMA_MASK) != 0)
            return TYPE.SMS_CDMA;

        throw new IllegalArgumentException("Message type not found in handle string.");
    }

    /// M: Map Gemini Feature
    public static String detectCountry(Context context) {
        try {
            CountryDetector detector =
                (CountryDetector) context.getSystemService(Context.COUNTRY_DETECTOR);
            final Country country = detector.detectCountry();
            if (country != null) {
                return country.getCountryIso();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String formatNumber(String number, Context context) {
        String countryCode = detectCountry(context);
        AsYouTypeFormatter mFormatter = PhoneNumberUtil.getInstance().getAsYouTypeFormatter(countryCode);
        char [] cha = number.toCharArray();
        int ii = cha.length;
        for (int num = 0; num < ii; num++) {
            number = mFormatter.inputDigit(cha[num]);
        }
        return number;
    }
    /// @}

    /// M: fix bug ALPS01427672, check and remove invalid characters for XmlSerializer
    public static boolean isLegalArgument(String str) {
        if (TextUtils.isEmpty(str)) {
            return true;
        }

        StringWriter sw = new StringWriter();
        XmlSerializer xmlMsgElement = Xml.newSerializer();
        try {
            xmlMsgElement.setOutput(sw);
            xmlMsgElement.startDocument(null, null);
            xmlMsgElement.startTag("", "test");
            xmlMsgElement.attribute("", "str", str);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "[islegalArgument] IllegalArgumentException: " + str);
            return false;
        } catch (IOException e) {
            Log.w(TAG, "[islegalArgument] IOException: " + str);
            return false;
        }
        return true;
    }

    public static String removeInvalidChar(String str) {
        Log.d(TAG, "[removeInvalidChar] begin: " + str);
        if (TextUtils.isEmpty(str)) {
            return str;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            /// M: quote from KXmlSerializer writeEscaped()
            boolean valid = (c >= 0x20 && c <= 0xd7ff) || (c >= 0xe000 && c <= 0xfffd);
            if (valid) {
                builder.append(c);
            }
        }
        String newStr = builder.toString();
        Log.d(TAG, "[removeInvalidChar] end: " + newStr);
        return newStr;
    }
    /// @}
}

