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

import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.content.Intent;
import android.content.Context;
import android.telephony.Rlog;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.CommandsInterface;
import com.mediatek.internal.telephony.radiotest.RadioTest;
import com.mediatek.internal.telephony.RadioManager;

public class RadioTestHelper { 

    protected static final String LOG_TAG = "RadioTestHelper";

    protected static final boolean MODEM_POWER_ON = true;
    protected static final boolean MODEM_POWER_OFF = false;
    protected static final boolean RADIO_POWER_ON = true;
    protected static final boolean RADIO_POWER_OFF = false;
    protected static final String STRING_NO_SIM_INSERTED = "N/A";

    private static boolean isIntializationDone = false;
    
    protected static void initialTestCondition(Context context, CommandsInterface[] ci, Phone[] phone) {
        if (!isIntializationDone) {
            log("Initial radio condition");
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", false);
            context.sendBroadcast(intent);
            log("ACTION_AIRPLANE_MODE_CHANGED sent");
            putThreadIntoSleep(20000);
            for (int i = 0; i < phone.length; i++) {
                int phoneId = phone[i].getPhoneId();
                log("Set radio power on for phone " + phoneId);
                RadioManager.getInstance().forceSetRadioPower(RADIO_POWER_ON, phoneId);
            }
            putThreadIntoSleep(5000);
            isIntializationDone = true;
        } else {
            log("Initial already done, not do it again");
        }
    }
    
    protected static void putThreadIntoSleep(long mSec){
        try {
            Thread.sleep(mSec);
        } catch (InterruptedException e) { 
            // Restore the interrupted status
            Thread.currentThread().interrupt();
        }
    }
    
    protected static void log(String s) {
        Rlog.d(LOG_TAG, "[RadioTestHelper] " + s);
    }
}

