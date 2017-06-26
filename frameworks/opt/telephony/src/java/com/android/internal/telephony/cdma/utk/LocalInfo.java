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

package com.android.internal.telephony.cdma.utk;

import java.io.ByteArrayOutputStream;

/**
 * Local Info
 *
 * {@hide}
 */
public class LocalInfo {

    public int Technology;
    public int MCC;
    public int IMSI_11_12;
    public int SID;
    public int NID;
    public int BASE_ID;
    public int BASE_LAT;
    public int BASE_LONG;

    LocalInfo() {}

    public void localInfoFormat(ByteArrayOutputStream buf) {
    if (buf == null) {
        return;
    }
    //Svlte cache the MCC and IMSI
    buf.write(ComprehensionTlvTag.LOCATION_INFORMATION.value());
    buf.write(0x0F); //length
    buf.write(MCC & 0xFF);
    buf.write(MCC >> 8);

    buf.write(IMSI_11_12);

    buf.write(SID & 0xFF);
    buf.write(SID >> 8);

    buf.write(NID & 0xFF);
    buf.write(NID >> 8);

    buf.write(BASE_ID & 0xFF);
    buf.write(BASE_ID >> 8);

    buf.write(BASE_LAT & 0xFF);
    buf.write((BASE_LAT & 0xFF00) >> 8);
    buf.write(BASE_LAT >> 16);

    buf.write(BASE_LONG & 0xFF);
    buf.write((BASE_LONG & 0xFF00) >> 8);
    buf.write(BASE_LONG >> 16);

    UtkLog.d("LocalInfo", "LocalInfoFormat MCC:" + MCC + " IMSI:" + IMSI_11_12 +" SID:" + SID +
         " NID:" + NID + " BASEID:" + BASE_ID + " BASELAT:" + BASE_LAT + " BASELONG:" + BASE_LONG);
    }
    
    public int getMccCodec(int mMcc) {
        int myMap[] = { 9, 0, 1, 2, 3, 4, 5, 6, 7, 8 }; 
        int MCC = mMcc;
        int mcc = mMcc;
        if (mcc < 1000){
            MCC = myMap[mcc/100]*100; 
            mcc %= 100;
            MCC += myMap[mcc/10]*10;
            MCC += myMap[mcc%10];
        } else {
            MCC = myMap[mcc/1000]*1000; 
            mcc%=1000;
            MCC += myMap[mcc/100]*100; 
            mcc%=100;
            MCC += myMap[mcc/10]*10;
            MCC += myMap[mcc%10];
        }
        return MCC;        
    }
    
    public int getMncCodec(int mMnc) {
        int myMap[] = { 9, 0, 1, 2, 3, 4, 5, 6, 7, 8 }; 
        int IMSI_11_12 = mMnc;
        int mnc = mMnc;
        if (mnc < 100) {
            IMSI_11_12 = myMap[mnc/10]*10;
            IMSI_11_12 += myMap[mnc%10];
        } else {
            IMSI_11_12 = myMap[mnc/100]*100; 
            mnc%=100;
            IMSI_11_12 += myMap[mnc/10]*10;
            IMSI_11_12 += myMap[mnc%10];
        }
        return IMSI_11_12;
    }
    

    public void technologyFormat(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        UtkLog.d(this, "LocalInformationResponseData technology = " + Technology);

        buf.write(0x3F);
        buf.write(0x01); //length
        buf.write(Technology);
    }

    public void copyFrom(LocalInfo other) {
        if (other == null) {
            return;
        }

        Technology = other.Technology;
        MCC = other.MCC;
        IMSI_11_12 = other.IMSI_11_12;
        SID = other.SID;
        NID = other.NID;
        BASE_ID = other.BASE_ID;
        BASE_LAT = other.BASE_LAT;
        BASE_LONG = other.BASE_LONG;
    }
}
