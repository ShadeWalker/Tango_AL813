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

import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.CommandsInterface;

import com.mediatek.internal.telephony.radiotest.RadioTest;
import com.mediatek.internal.telephony.RadioManager;
import com.mediatek.internal.annotation.*;

public class FlightModeRadioTest extends RadioTest {

    private static String flightModePowerOffModem = SystemProperties.get("ro.mtk_flight_mode_power_off_md"); 
    private static final boolean FLIGHT_MODE_ON = true;
    private static final boolean FLIGHT_MODE_OFF = false;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LOG_TAG = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        SystemProperties.set("radio.test.poweroffmd", "0");
    }
    
    @SmallTest
    public void testFlightModeSwitch() {
        log("TEST FLIGHT MODE RADIO: flight mode on");
        broadcastFlightModeIntent(FLIGHT_MODE_ON);
        for (int i = 0; i < mTestPhoneCount; i++) {
            assertFalse("testRadioPowerSwitch(): flight mode on fail", mTestCi[i].getRadioState().isOn());
        }
        log("TEST FLIGHT MODE RADIO: flight mode off");
        broadcastFlightModeIntent(FLIGHT_MODE_OFF);
        for (int i = 0; i < mTestPhoneCount; i++) {
            assertTrue("testRadioPowerSwitch(): flight mode off fail", mTestCi[i].getRadioState().isOn());
        }        
    }
   
    private void broadcastFlightModeIntent(boolean enabled) {
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enabled);
        mContext.sendBroadcast(intent);
        log("FlightModeTest: ACTION_AIRPLANE_MODE_CHANGED sent, state" + enabled);
        for (int i = 0; i < BASIC_WAIT_INTERVAL; i++) {
            if (enabled == true && isAllTestRadioOff()) {
                break;
            } else if (enabled == false && isAllTestRadioOn()) {
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
        SystemProperties.set("radio.test.poweroffmd", flightModePowerOffModem);
    }
        
}
