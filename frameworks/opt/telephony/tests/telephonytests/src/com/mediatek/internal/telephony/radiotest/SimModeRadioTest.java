/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */


package com.mediatek.internal.telephony.radiotest;

import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.content.Intent;
import android.content.Context;
import android.provider.Settings;

import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.CommandsInterface;

import com.mediatek.internal.telephony.radiotest.RadioTest;
import com.mediatek.internal.telephony.RadioManager;
import com.mediatek.internal.annotation.*;

public class SimModeRadioTest extends RadioTest {
    
    private int mSimMode = 0;
    private static final boolean SIM_OFF = false;
    private static final boolean SIM_ON = true;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LOG_TAG = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        mSimMode = Settings.System.getInt(mContext.getContentResolver(),
                          Settings.System.MSIM_MODE_SETTING, -1);
        log("SimModeTest: mSimMode initial" + mSimMode);
        assertTrue((mSimMode > 0));
    }
    
    @SmallTest
    public void testSimModeSwitch() {
        for (int i = 0; i < mTestPhoneCount; i++) {
            int phoneId = mTestPhones[i].getPhoneId();
            log("TEST SIM MODE RADIO: set phone " + phoneId + " radio off");
            setSimPower(phoneId, SIM_OFF);
            assertFalse("testSimSwitch(): Sim mode off fail", mTestCi[i].getRadioState().isOn());            
        }
        
        for (int i = 0; i < mTestPhoneCount; i++) {
            int phoneId = mTestPhones[i].getPhoneId();
            log("TEST SIM MODE RADIO: set phone " + phoneId + " radio on");
            setSimPower(phoneId, SIM_ON);
            assertTrue("testSimSwitch(): Sim mode on fail", mTestCi[i].getRadioState().isOn());            
        }     
    }
   
    private void setSimPower(int phoneId, boolean power) {
        int phoneBitMap = 1 << phoneId;
        if (power == SIM_OFF) {
            mSimMode -= phoneBitMap;
        } else {
            mSimMode += phoneBitMap;
        }
        Intent intent = new Intent(Intent.ACTION_MSIM_MODE_CHANGED);
        intent.putExtra(Intent.EXTRA_MSIM_MODE, mSimMode);
        mContext.sendBroadcast(intent);
        log("SimModeTest: ACTION_MSIM_MODE_CHANGED sent, mode" + mSimMode);
        for (int i = 0; i < BASIC_WAIT_INTERVAL; i++) {
            if (power == SIM_OFF && !mCi[phoneId].getRadioState().isOn()) {
                break;
            } else if (power == SIM_ON && mCi[phoneId].getRadioState().isOn()) {
                break;
            } else {
                putThreadIntoSleepForASec();
            }
        }
    }
   
    @ToolAnnotation
    public void testShowClassName() {
        log("For test tool to get class");
    } 
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
        
}