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

import org.apache.http.params.HttpParams;

/// M: ALPS00440523, Print Mms memory usage @ {
import android.content.Context;
/// @}
import android.content.Intent;
import android.net.Uri;
/// M: Add MmsService configure param @{
import android.os.Bundle;
import android.os.SystemProperties;
/// M: New plugin API @{
import android.provider.Browser;
import android.telephony.SubscriptionManager;
/// @}
import android.util.Log;
/// M: ALPS00956607, not show modify button on recipients editor @{
import android.view.inputmethod.EditorInfo;
/// @}
/// M: ALPS00527989, Extend TextView URL handling @ {
import android.widget.TextView;
/// @}

import com.mediatek.telephony.TelephonyManagerEx;

import java.util.ArrayList;

public class DefaultMmsConfigExt implements IMmsConfigExt {
    private static final String TAG = "Mms/MmsConfigImpl";

    private static int sSmsToMmsTextThreshold = 4;
    private static int sMaxTextLimit = 999999;  // modify by Hugo
    private static int sMmsRecipientLimit = 20;                 // default value
/// M:Code analyze 01,For new feature CMCC_Mms in ALPS00325381, MMS easy porting check in JB @{
    private static int sHttpSocketTimeout = 60 * 1000;
/// @}
    /// M: For common case, default retry scheme not change
    private static final int[] DEFAULTRETRYSCHEME = {
        0, 1 * 60 * 1000, 5 * 60 * 1000, 10 * 60 * 1000, 30 * 60 * 1000};
    /// @}

    private static int sSmsToMmsTextThresholdForCT = 7;
    private static final boolean MTK_C2K_SUPPORT = SystemProperties.get("ro.mtk_c2k_support")
    .equals("1");

    private static final boolean MTK_CT6M_SUPPORT = SystemProperties.get("ro.ct6m_support")
    .equals("1");
    
    public int getSmsToMmsTextThreshold() {
        Log.d(TAG, "get SmsToMmsTextThreshold: " + sSmsToMmsTextThreshold);
        if (MTK_C2K_SUPPORT) {
            Log.d(TAG, "get SmsToMmsTextThreshold For OP09: " + sSmsToMmsTextThresholdForCT);
            return sSmsToMmsTextThresholdForCT;
        }
        return sSmsToMmsTextThreshold;
    }

    @Override
    public int getSmsToMmsTextThresholdForC2K(Context context) {
        Log.d(TAG, "get getSmsToMmsTextThresholdForC2K: " + sSmsToMmsTextThreshold);
        if (MTK_C2K_SUPPORT && hasUSIMInserted(context)) {
            Log.d(TAG, "get getSmsToMmsTextThresholdForC2K" + sSmsToMmsTextThresholdForCT);
            return sSmsToMmsTextThresholdForCT;
        }
        return sSmsToMmsTextThreshold;
    }

    /**
     * M: For EVDO: check the sim is whether UIM or not.
     * @param subId the sim's sub id.
     * @return true: UIM; false: not UIM.
     */
    private boolean isUSimType(int subId) {
        String phoneType = TelephonyManagerEx.getDefault().getIccCardType(subId);
        if (phoneType == null) {
            Log.d(TAG, "[isUIMType]: phoneType = null");
            return false;
        }
        Log.d(TAG, "[isUIMType]: phoneType = " + phoneType);
        return phoneType.equalsIgnoreCase("CSIM") || phoneType.equalsIgnoreCase("UIM")
            || phoneType.equalsIgnoreCase("RUIM");
    }

    /**
     * M: For OM Version: check whethor has usim card.
     * @param context the context.
     * @return ture: has usim; false: not.
     */
    private boolean hasUSIMInserted(Context context) {
        if (context == null) {
            return false;
        }
        int[] ids = SubscriptionManager.from(context).getActiveSubscriptionIdList();
        if (ids != null && ids.length > 0) {
            for (int subId : ids) {
                if (isUSimType(subId)) {
                    Log.d(TAG, "[hasUSIMInserted]: true");
                    return true;
                }
            }
        }
        Log.d(TAG, "[hasUSIMInserted]: false");
        return false;
    }

    public void setSmsToMmsTextThreshold(int value) {
        if (value > -1) {
            sSmsToMmsTextThreshold = value;
        }
        Log.d(TAG, "set SmsToMmsTextThreshold: " + sSmsToMmsTextThreshold);
    }

    public int getMaxTextLimit() {
        Log.d(TAG, "get MaxTextLimit: " + sMaxTextLimit);
        return sMaxTextLimit;
    }

    public void setMaxTextLimit(int value) {
        if (value > -1) {
            sMaxTextLimit = value;
        }

        Log.d(TAG, "set MaxTextLimit: " + sMaxTextLimit);
    }

    public int getMmsRecipientLimit() {
        Log.d(TAG, "RecipientLimit: " + sMmsRecipientLimit);
        return sMmsRecipientLimit;
    }

    public void setMmsRecipientLimit(int value) {
        if (value > -1) {
            sMmsRecipientLimit = value;
        }

        Log.d(TAG, "set RecipientLimit: " + sMmsRecipientLimit);
    }

/// M:Code analyze 02,For new feature CMCC_Mms in ALPS00325381, MMS easy porting check in JB @{
    public int getHttpSocketTimeout() {
        Log.d(TAG, "get default socket timeout: " + sHttpSocketTimeout);
        return sHttpSocketTimeout;
    }

    public void setHttpSocketTimeout(int socketTimeout) {
        Log.d(TAG, "set default socket timeout: " + socketTimeout);
        sHttpSocketTimeout = socketTimeout;
    }
/// @}

    public boolean isEnableSIMSmsForSetting() {
        Log.d(TAG, "Enable display storage status ");
        return true;
    }

    public int getMmsRetryPromptIndex() {
        Log.d(TAG, "getMmsRetryPromptIndex");
        return 1;
    }

    public int[] getMmsRetryScheme() {
        Log.d(TAG, "getMmsRetryScheme");
        return DEFAULTRETRYSCHEME;
    }

    public int[] getMmsRetryScheme(int messageType) {
        return getMmsRetryScheme();
    }

    public void setSoSndTimeout(HttpParams params) {
        Log.d(TAG, "setSoSndTimeout");
        return;
    }

    public Uri appendExtraQueryParameterForConversationDeleteAll(Uri uri) {
        Log.d(TAG, "appendExtraQueryParameterForConversationDeleteAll; null ");
        return uri;
    }

    /// M: ALPS00527989, Extend TextView URL handling @ {
    public void setExtendUrlSpan(TextView textView) {
        Log.d(TAG, "setExtendUrlSpan");
    }
    /// @}

    @Override
    public boolean isSupportCBMessage(Context context, long subId) {
        return true;
    }

    @Override
    public boolean isAllowDRWhenRoaming(Context context, long subId) {
        Log.d(TAG, "DefaultMmsConfigExt, isAllowDRWhenRoaming() subId = " + subId);
        if (!MTK_CT6M_SUPPORT) {
            Log.d(TAG, "DefaultMmsConfigExt, CT6M NOT SUPPORT");
            return true;
        }
        if (!isUSimType((int) subId)) {
            return true;
        }
        int simCount = SubscriptionManager.from(context).getActiveSubscriptionInfoCount();
        if (simCount <= 0) {
            Log.e(TAG, "isAllowDRWhenRoaming(): Wrong subId!");
            return false;
        }
        TelephonyManagerEx telephonyManagerEx = TelephonyManagerEx.getDefault();
        boolean isRoaming = false;
        isRoaming = telephonyManagerEx.isNetworkRoaming(SubscriptionManager.getSlotId((int) subId));
        Log.d(TAG, "isAllowDRWhenRoaming() isRoaming: " + isRoaming);
        return !isRoaming;
    }

    /// M: ALPS00837193, query undelivered mms with non-permanent fail ones or not @{
    public Uri getUndeliveryMmsQueryUri(Uri defaultUri) {
        Log.d(TAG, "appendExtraQueryParameterForUndeliveryMms");
        return defaultUri;
    }
    /// @}

    /// M: New plugin API @{
    public void openUrl(Context context, String url) {
        Log.d(TAG, "openUrl, url=" + url);
        Uri theUri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, theUri);
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        context.startActivity(intent);
    }
    /// @}

    /// M: ALPS00956607, not show modify button on recipients editor @{
    public void setRecipientsEditorOutAtts(EditorInfo outAttrs) {
        Log.d(TAG, "setRecipientsEditorOutAtts");
    }
    /// @}

    @Override
    public void setExtendedAudioType(ArrayList<String> audioType) {
    }

    /// M: Add MmsService configure param @{
    public Bundle getMmsServiceConfig() {
    	return null;
    }
    /// @}
}

