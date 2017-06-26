/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2012. All rights reserved.
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

package com.mediatek.mms.ext;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import android.database.Cursor;

import com.google.android.mms.pdu.AcknowledgeInd;
import com.google.android.mms.pdu.NotifyRespInd;

import android.util.Log;

public class DefaultMmsPreferenceExt extends ContextWrapper implements IMmsPreferenceExt {
    private static final String TAG = "Mms/DefaultMmsPreferenceExt";
    private IGeneralPreferenceHost mHost = null;

    public DefaultMmsPreferenceExt(Context context) {
        super(context);
    }

    public boolean syncDataRoamingStatus(Activity activity, Preference preference, int simIndex) {
        Log.d(TAG, "syncDataRoamingStatus");
        return true;
    }

    public void modifyDataRoamingPreference(Context context, Intent intent) {
        Log.d(TAG, "modifyDataRoamingPreference");
    }

    public void configSmsPreference(Activity hostActivity, PreferenceCategory pC, int simCount) {
        return;
    }

    public void configSmsPreferenceEditorWhenRestore(Activity hostActivity, SharedPreferences.Editor editor) {
        return;
    }

    public void configMmsPreferenceEditorWhenRestore(Activity hostActivity, SharedPreferences.Editor editor) {
        return;
    }

    //fixme
    public Bundle getSmsValidityParamBundleWhenSend(Context context, int subId) {
        return null;
    }

    public void configSelectCardPreferenceTitle(Activity hostActivity) {
        return;
    }

    public boolean handleSelectCardPreferenceTreeClick(Activity hostActivity, final int subId) {
        return false;
    }

     public String formatSmsBody(Context context, String smsBody , String nameAndNumber, int boxId) {
        return smsBody;
     }

     public String formatSmsBody(Context context, String smsBody, String nameAndNumber, Cursor cursor) {
        return smsBody;
     }

    public void configGeneralPreference(Activity hostActivity, PreferenceCategory pC) {
        return;
    }

    public void setGeneralPreferenceHost(IGeneralPreferenceHost host) {
        return;
    }

    public void configMmsPreference(Activity hostActivity, PreferenceCategory pC, int simCount) {
        return;
    }

    public boolean configMmsPreferenceState(Activity hostActivity, String preference, int subId, CheckBoxPreference cp) {
        return false;
    }

    public void configMultiSimPreferenceTitle(Activity hostActivity) {
        return;
    }

    //fixme
    public boolean setMmsPreferenceState(Activity hostActivity, String preference, int subId , boolean checked) {
        return false;
    }
    //fixme
    public void setAcknowledgeDeliveryReport(Context mContext, int subId, AcknowledgeInd acknowledgeInd) {
        return;
    }
    //fixme
    public void setNotifyRespDeliveryReport(Context mContext, int subId, NotifyRespInd notifyRespInd) {
        return;
    }
}