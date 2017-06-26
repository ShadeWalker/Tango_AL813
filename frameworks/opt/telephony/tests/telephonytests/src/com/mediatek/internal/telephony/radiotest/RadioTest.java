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
import android.telephony.Rlog;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.CommandsInterface;
import com.mediatek.internal.telephony.RadioManager;

public class RadioTest extends AndroidTestCase {

    protected static String LOG_TAG = "RadioTest";
    protected int mPhoneCount;
    protected int mTestPhoneCount;
    protected Phone[] mProxyPhones;
	protected Phone[] mTestPhones;
    protected CommandsInterface[] mCi;
    protected CommandsInterface[] mTestCi;	
    protected RadioTestHelper radioTestHelper;
    protected Context mContext;
    
    protected static int BASIC_WAIT_INTERVAL = 10;
	protected static int MEDIUM_WAIT_INTERVAL = 30;
	protected static int LONG_WAIT_INTERVAL = 60;
    
    protected static final boolean MODEM_POWER_ON = true;
    protected static final boolean MODEM_POWER_OFF = false;
    protected static final boolean RADIO_POWER_ON = true;
    protected static final boolean RADIO_POWER_OFF = false;
    protected static final String STRING_NO_SIM_INSERTED = "N/A";

    protected String[] PROPERTY_ICCID_SIM = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4",
    };
        
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LOG_TAG = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        SystemProperties.set("radio.testmode", "1");
        mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
        mProxyPhones = PhoneFactory.getPhones();		
        mCi = new CommandsInterface[mPhoneCount];
        for (int i = 0; i < mPhoneCount; i++) {            
            PhoneProxy proxyPhone = (PhoneProxy)mProxyPhones[i];
            mCi[i] = ((PhoneBase)proxyPhone.getActivePhone()).mCi;         
        }
        getRealPhoneCount();
        initialTestPhoneAndCi();
        mContext = getContext();
        RadioTestHelper.initialTestCondition(mContext, mTestCi, mTestPhones);
    }
	
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        SystemProperties.set("radio.testmode", "0");
    }
    
	protected int convertPhoneCountIntoBitmap(){
	    int ret = 0;
		ret = (1 << (mPhoneCount + 1)) - 1;
		return ret;
	}
	
    private String readIccIdUsingPhoneId(int phoneId) {
        String ret = SystemProperties.get(PROPERTY_ICCID_SIM[phoneId]);
        log("ICCID for phone " + phoneId + " is " + ret);
        return ret;
    }

    protected boolean isAllTestRadioOn() {
        boolean result = true;
        for (int i = 0; i < mTestPhoneCount; i++) {
            if (!mTestCi[i].getRadioState().isOn()) {
                result =  false;
            }
        }
        return result;
    }
    
    protected boolean isAllTestRadioOff() {
        boolean result = true;
        for (int i = 0; i < mTestPhoneCount; i++) {
            if (mTestCi[i].getRadioState().isOn()) {
                result =  false;
            }
        }
        return result;
    }
    
    protected int findMainCapabilityPhoneId() {
        int result = 0;
        int switchStatus = Integer.valueOf(
                SystemProperties.get(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, "1"));
        result = switchStatus - 1;
        if (result < 0 || result >= mPhoneCount) {
            return 0;
        } else {
            return result;
        }
    }
    
    private void getRealPhoneCount() {
        int realPhoneCount = 0;
        for (int i = 0; i < mPhoneCount; i++) {
            String iccid = readIccIdUsingPhoneId(i);
            if (iccid == null || "".equals(iccid) || STRING_NO_SIM_INSERTED.equals(iccid)) {
                log("phone " + i + " has no SIM!");
            } else {
                realPhoneCount++;           
            }            
        }
        mTestPhoneCount = realPhoneCount;
    }
    
    private void initialTestPhoneAndCi() {
        int realPhoneCount = 0;
        mTestPhones = new Phone[mTestPhoneCount];
        mTestCi = new CommandsInterface[mTestPhoneCount];
        for (int i = 0; i < mPhoneCount; i++) {            
            String iccid = readIccIdUsingPhoneId(i);
            if (iccid == null || "".equals(iccid) || STRING_NO_SIM_INSERTED.equals(iccid)) {
                log("phone " + i + " has no SIM!");
            } else {
                mTestPhones[realPhoneCount] = mProxyPhones[i];
                mTestCi[realPhoneCount] = mCi[i];
                realPhoneCount++;           
            }            
        }
    }
    
    protected void putThreadIntoSleep(long mSec){
        try {
            Thread.sleep(mSec);
        } catch (InterruptedException e) { 
             // Restore the interrupted status
             Thread.currentThread().interrupt();
        }
    }
    
    protected void putThreadIntoSleepForASec(){
        putThreadIntoSleep(1000);
    }
    
    protected static boolean isBspPackage() {
        if(SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            return true;
        } else {
            return false;
        }
    }
    
    protected static void log(String s) {
        Rlog.d(LOG_TAG, "[RadioTest] " + s);
    }
    
}

