/*
 * Copyright (C) 2011, The Android Open Source Project
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
/*
 * Contributed by: Giesecke & Devrient GmbH.
 */

package com.mediatek.nfc.addon;

import android.nfc.NdefMessage;

import android.util.Log;
import android.content.Context;
import android.net.Uri;

import android.provider.MediaStore;
import android.database.Cursor;

public class Util {

    static final String TAG = "com.mediatek.nfc.addon.Util";

    public static byte[] mergeBytes(byte[] array1, byte[] array2) {
        byte[] data = new byte[array1.length + array2.length];
        int i = 0;
        for (; i < array1.length; i++)
            data[i] = array1[i];
        for (int j = 0; j < array2.length; j++)
            data[j + i] = array2[j];
        return data;
    }

    public static byte[] getMid(byte[] array, int start, int length) {
        byte[] data = new byte[length];
        System.arraycopy(array, start, data, 0, length);
        return data;
    }

    public static String bytesToString(byte[] bytes) {
        if (bytes == null)
            return "";
        StringBuffer sb = new StringBuffer();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b & 0xFF));
        }
        String str = sb.toString();
        if (str.length() > 0) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    public static String printNdef(NdefMessage PrintNdef) {
        String ResultStr = "";

        if (PrintNdef == null)
            return " null";


        byte[] PrintNdefByteArray = PrintNdef.toByteArray();
        ResultStr += "  Length:" + PrintNdefByteArray.length;
        ResultStr += "  Array::" + Util.bytesToString(PrintNdefByteArray);

        return ResultStr;
    }

    public static String printNdef(byte[] NdefArray) {
        String ResultStr = "";

        if (NdefArray == null)
            return " null";

        //byte[] PrintNdefByteArray = PrintNdef.toByteArray();
        ResultStr += "  Length:" + NdefArray.length;
        ResultStr += "  Array::" + Util.bytesToString(NdefArray);

        return ResultStr;
    }

    /*
    * int:0x112233 btyeCount:3
    *  array[0]: 0x33
    *  array[1]: 0x22
    *  array[2]: 0x11
    */
    public static byte[] intToByteCountArray(int i, byte btyeCount)
    {
        byte j;
        if (btyeCount > 4)
        return null;

        byte[] result = new byte[btyeCount];

        for (j = 0 ; j < btyeCount ; j++)
        {
            byte k = (byte) ((byte) j * 8);
            result[btyeCount - 1 - j] = (byte) (i >> k);
        }
        //result[0] = (byte) (i >> 24);
        //result[1] = (byte) (i >> 16);
        //result[2] = (byte) (i >> 8);
        //result[3] = (byte) (i /*>> 0*/);

        return result;
    }


    public static int byteArrayToint(byte[] btyeCount)
    {
        byte j;
        int result = 0;
        int length = btyeCount.length;
        //Log.i(TAG, " ~~  ~~  byteArrayToint " + length);
        for (j = 0 ; j < length ; j++)
        {
            byte k = (byte) ((byte) j * 8);
            //Log.i(TAG, " ~~  ~~  byteArrayToint  k:" + k +"    ele:"+ (btyeCount[j] << k));
            result += btyeCount[j] << k;
            //Log.i(TAG, " ~~  ~~  byteArrayToint  result:" + result );
        }

        return result;
    }



    /*
    *   The Input MAC Address "06:08:A0:11:CC:6D"
    *   it will convert to 6bytes Array [0]:6D [1]:CC [2]:11 [3]:A0 [4]:08 [5]:06
    *
    */
    public static byte[] addressToReverseBytes(String address) {
        Log.d(TAG, "addressToReverseBytes: " + address);
        String[] split = address.split(":");
        //Log.d(TAG, "addressToReverseBytes: " + split);
        byte[] result = new byte[split.length];

        for (int i = 0; i < split.length; i++) {
            // need to parse as int because parseByte() expects a signed byte
            result[split.length - 1 - i] = (byte) Integer.parseInt(split[i], 16);
        }

        return result;
    }

    /*
    *   The Input MAC Address "06:08:A0:11:CC:6D"
    *   it will convert to 6bytes Array [0]:06 [1]:08  [2]:A0 [3]:11 [4]:CC [5]:6D
    *
    */
    public static byte[] addressToByteArray(String address) {
        String[] split = address.split(":");
        byte[] result = new byte[split.length];

        for (int i = 0; i < split.length; i++) {
            // need to parse as int because parseByte() expects a signed byte
            result[i] = (byte) Integer.parseInt(split[i], 16);
        }

        return result;
    }


    /*
    *   The InputIP Address "192.168.1.1"
    *   it will convert to 6bytes Array [0]:01 [1]:01 [2]:A8 [3]:C0
    *
    */
    public static byte[] ipAddressToReverseBytes(String ipAddress) {
        //String ipAddress = "192.168.1.1";
        String[] ipAddressParts = ipAddress.split("\\.");

        //Log.i(TAG, " ~~  ~~ ipAddressParts.length :" + ipAddressParts.length );
        // convert int string to byte values
        byte[] ipAddressBytes = new byte[ipAddressParts.length];
        for (int i = 0; i < ipAddressParts.length; i++) {

            ipAddressBytes[ipAddressParts.length - 1 - i] = (byte) Integer.parseInt(ipAddressParts[i]);
        }
        return ipAddressBytes;
    }

    public static int byteToUnsignedInt(byte b) {
        return 0x00 << 24 | b & 0xff;
     }


    /*
     *  byte[] s2 ={0x6D,0xCC,0x11,0xA0,0x08,0x06};
     *  output "06:08:A0:11:CC:6D"
     *
     */
    public static String macBytesArrayToReverseString(byte[] ByteArray)
    {
        int separateLength = 5;

        if (ByteArray.length != 6)
            Log.e(TAG, " Mac Address length not match :" + ByteArray.length);

        StringBuilder sb = new StringBuilder(ByteArray.length + separateLength);

        for (int i = 0; i < ByteArray.length; i++) {
            if (sb.length() > 0)
                sb.append(':');
            sb.append(String.format("%02x", ByteArray[ByteArray.length - 1 - i]));
        }
            String resultString = sb.toString();
            return resultString.toUpperCase();
        //return sb.toString();

    }

    /*
     *  byte[] s2 ={0x06,0x08,0xA0,0x11,0xCC,0x6D};
     *  output "06:08:A0:11:CC:6D"
     *  without reversed string
     *
     */
    public static String macBytesArrayToString(byte[] ByteArray)
    {
        int separateLength = 5;

        if (ByteArray.length != 6)
            Log.e(TAG, " Mac Address length not match :" + ByteArray.length);

        StringBuilder sb = new StringBuilder(ByteArray.length + separateLength);

        for (int i = 0; i < ByteArray.length; i++) {
            if (sb.length() > 0)
                sb.append(':');
            sb.append(String.format("%02x", ByteArray[i]));
        }
            String resultString = sb.toString();
            return resultString.toUpperCase();
        //return sb.toString();

    }



    /*
     *      short:288 (hex: 0x120)
     *  output "0x0120"
     *
     */
    public static String addPrefixShortString(Short value)
    {
        StringBuilder sb = new StringBuilder(6); //short byte present + 0x

        sb.append("0x");
        sb.append(String.format("%04x", value));

        String resultString = sb.toString();
        return resultString; //.toUpperCase();
    }



    /*
     *  byte[] s2 ={1,1,(byte)0xa8,(byte)0xc0};
     *
     *  output 192.168.1.1
     */
    public static String ipBytesArrayToReverseString(byte[] ByteArray)
    {
        int separateLength = 3;

        if (ByteArray.length != 4)
            Log.e(TAG, " IP Address length not match :" + ByteArray.length);

        StringBuilder sb = new StringBuilder(ByteArray.length + separateLength);

        for (int i = 0; i < ByteArray.length; i++) {
            if (sb.length() > 0)
                sb.append('.');
            sb.append(String.format("%d", byteToUnsignedInt(ByteArray[ByteArray.length - 1 - i])));
        }

        return sb.toString();

    }


    /**
    *   The Input String "0x0020"
    *   it will convert to String "0020"
    *
    */
    public static String splitPrefixString(String targetString) {

        String[] targetStringParts = targetString.split("0x");


        if (targetStringParts.length != 2)
            Log.e(TAG, " splitPrefixString Error :" + targetStringParts.length);

        String outputString = targetStringParts[1];

        return outputString;
    }



    public static String getFilePathByContentUri(String tag, Context context, Uri uri) {
        Log.d(tag, "getFilePathByContentUri(), uri.toString() = " + uri.toString());
        Log.d(tag, "                           uri.getPath(): " + uri.getPath());

        Uri filePathUri = uri;
        if (uri.getScheme().toString().compareTo("content") == 0) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor.moveToFirst()) {
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA); //Instead of "MediaStore.Images.Media.DATA" can be used "_data"

                    String curString = cursor.getString(column_index);
                    Log.d(tag, " return cursor.getString : " + curString);
                    filePathUri = Uri.parse(curString);
                    Log.d(tag, "filePathUri.getPath() : " + filePathUri.getPath());
                    return curString; //filePathUri.getPath();
                }
            } catch (Exception e) {
                Log.d(tag, "exception...");
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        Log.d(tag, "getFilePathByContentUri doesn't work, try direct getPath");

        Log.d(tag, "return uri.getPath(): " + uri.getPath());
        return uri.getPath();
    }

    //convert File:// or content:// to filePath
    public static String getFilePathByString(String tag, Context context, String input) {

        Log.d(tag, " convertToFilePath() input:" + input);

        if (input.startsWith("content")) {
            Uri uri = Uri.parse(input);
            return getFilePathByContentUri(tag, context, uri);
        }
        else if (input.startsWith("file://")) {
           Log.d(tag, " return  substring(7):" + input.substring(7));
           return input.substring(7);
        }

        return input;
    }




}
