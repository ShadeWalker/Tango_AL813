/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.contacts.simcontact;

import java.io.File;
import java.util.HashMap;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.provider.ContactsContract.Groups;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import android.os.SystemProperties;
import com.mediatek.contacts.ContactsSystemProperties;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simservice.SIMProcessorService;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.util.LogUtils;
import com.android.contacts.R;

import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

public class BootCmpReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCmpReceiver";
    //private static HashMap<Integer, Boolean> sImportRecord = new HashMap<Integer, Boolean>();
    private static HashMap sImportRecord = new HashMap();
    // add for ALPS01950279, receive phb before boot complete.
    //private static boolean sRemoveInvalidSimContact = false;
    private SharedPreferences huaweiPreference;

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if(action == null) return; //add by majian for null pointer fatal error
        LogUtils.d(TAG, "onReceive, action is " + action);// +
            //" sRemoveInvalidSimContact: " + sRemoveInvalidSimContact);
        // add for multi-user ALPS01964765, whether the current user is running.
        // if not , will do nothing.
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        boolean isRunning = userManager.isUserRunning(new UserHandle(UserHandle.myUserId()));
        LogUtils.d(TAG, "the current user is: " + UserHandle.myUserId()
                + " isRunning: " + isRunning);
        if (!isRunning) {
            return;
        }

        if (action.equals(TelephonyIntents.ACTION_PHB_STATE_CHANGED)) {			
            //if (!sRemoveInvalidSimContact) {
            //    sRemoveInvalidSimContact = true;
            //    processBootComplete(context);
            //}
            processPhoneBookChanged(context, intent);
        } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
//        	startSimService(context, -1, SIMServiceUtils.SERVICE_WORK_IMPORT_SDN_CONTACTS);
//            家人、朋友、同事、同学 
            SharedPreferences defaultGroupPreference=context.getSharedPreferences("defaultGroup", context.MODE_PRIVATE);
            SharedPreferences.Editor defaultGroupPreferenceEditor=defaultGroupPreference.edit();
            
            if(!defaultGroupPreference.getBoolean("added", false)){//已经添加，不再添加，出厂设置之后需要reset！
            	ContentValues values = new ContentValues();  
            	String[]  groupNames=context.getResources().getStringArray(R.array.groupName);
            	ContentResolver contentResolver = context.getContentResolver();
            	for (String groupName : groupNames) {
            		values.put(Groups.ACCOUNT_TYPE, "com.android.huawei.phone");
            		values.put(Groups.ACCOUNT_NAME, "Phone");
            		values.put(Groups.TITLE, groupName);
            		contentResolver.insert(Groups.CONTENT_URI, values);
            	}
            	defaultGroupPreferenceEditor.putBoolean("added", true);
            	defaultGroupPreferenceEditor.commit();
            }
            
            
            
            // fix ALPS01003520,when boot complete,remove the contacts if the
            // card of a slot has been removed
            //if (!sRemoveInvalidSimContact) {
            //    sRemoveInvalidSimContact = true;
            //    processBootComplete(context);
            //}
            boolean isPhbReady = isPhbReady();
            LogUtils.d(TAG, "ACTION_BOOT_COMPLETED isPhbReady = " + isPhbReady);
            if (!isPhbReady) {
                
                processBootComplete(context);
            }
        }

        /** M: Bug Fix for CR ALPS01328816: when other owner, do not show sms when share contact @{ */
        if (action.equals("android.intent.action.USER_SWITCHED_FOR_MULTIUSER_APP")
                && ContactsSystemProperties.MTK_OWNER_SIM_SUPPORT) {
            if (UserHandle.myUserId() == UserHandle.USER_OWNER) {
                context.getPackageManager().setComponentEnabledSetting(
                        new ComponentName("com.android.contacts",
                            "com.mediatek.contacts.ShareContactViaSMSActivity"),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            } else {
                context.getPackageManager().setComponentEnabledSetting(
                        new ComponentName("com.android.contacts",
                            "com.mediatek.contacts.ShareContactViaSMSActivity"),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }
        /** @} */
    }

    private boolean isPhbReady() {
        final int simCount = TelephonyManager.getDefault().getSimCount();
        LogUtils.d(TAG, "isPhbReady simCount: " + simCount);
        for (int slotId = 0; slotId < simCount; slotId++) {
            int[] subId = SubscriptionManager.getSubId(slotId);
            if (subId != null && subId.length > 0 && SimCardUtils.isPhoneBookReady(subId[0])) {
                return true;
            }
        }
        return false;
    }

    private void startSimService(Context context, int subId, int workType) {
        Intent intent = null;
        intent = new Intent(context, SIMProcessorService.class);
        intent.putExtra(SIMServiceUtils.SERVICE_SUBSCRIPTION_KEY, subId);
        intent.putExtra(SIMServiceUtils.SERVICE_WORK_TYPE, workType);
        LogUtils.d(TAG, "[startSimService]subId:" + subId + "|workType:" + workType);
        context.startService(intent);
    }

    private void processPhoneBookChanged(Context context, Intent intent) {
        LogUtils.d(TAG, "processPhoneBookChanged");
        boolean phbReady = intent.getBooleanExtra("ready", false);
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -10);
        LogUtils.d(TAG, "[processPhoneBookChanged]phbReady:" + phbReady + "|subId:" + subId);
        //if the PHB state has been changed,reset the phone book info
        //Only update the info when first used to avoid ANR in onReceiver when Boot Complete
        SlotUtils.refreshActiveUsimPhbInfos();
        if (phbReady && subId > 0) {
            sImportRecord.put(subId, true);
            startSimService(context, subId, SIMServiceUtils.SERVICE_WORK_IMPORT);
        } else if (subId > 0 && !phbReady) {
            startSimService(context, subId, SIMServiceUtils.SERVICE_WORK_REMOVE);
        }
    }

    /**
     * fix for [PHB Status Refatoring] ALPS01003520
     * when boot complete,remove the contacts if the card of a slot had been removed
     */
    private void processBootComplete(Context context) {
        LogUtils.d(TAG, "processBootComplete");
        startSimService(context, SIMServiceUtils.SERVICE_FORCE_REMOVE_SUB_ID,
            SIMServiceUtils.SERVICE_WORK_REMOVE);
    }
}

