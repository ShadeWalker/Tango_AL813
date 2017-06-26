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
import android.database.Cursor;
import android.telephony.SubscriptionInfo;;
import android.text.format.DateUtils;
import android.widget.TextView;

import com.mediatek.common.MPlugin;
import com.mediatek.common.PluginImpl;
import com.mediatek.common.telephony.ILteDataOnlyController;
import android.util.Log;
/**
 * M: default implemention for mms utils.
 */
public class DefaultMmsUtilsExt extends ContextWrapper implements IMmsUtilsExt {

    public DefaultMmsUtilsExt(Context context) {
        super(context);
    }

    @Override
    public String formatDateAndTimeStampString(Context context, long msgDate, long msgDateSent,
            boolean fullFormat, String formatStr) {
        return formatStr;
    }

    @Override
    public void showSimTypeBySubId(Context context, int subId, TextView textView){
    }

    public boolean allowSafeDraft(final Activity activity, boolean deviceStorageIsFull, boolean isNofityUser,
            int toastType) {
        return true;
    }

    public String formatDateTime(Context context, long time, int formatFlags) {
        return DateUtils.formatDateTime(context, time, formatFlags);
    }

    public boolean isWellFormedSmsAddress(String address) {
        return true;
    }

    public void setIntentDateForMassTextMessage(Intent intent, long groupId) {
        return;
    }

    public long getGroupIdFromIntent(Intent intent) {
        return -1;
    }

    public Cursor getReportItemsForMassSMS(Context context, String[] projection, long groupId) {
        return null;
    }

    @Override
    public boolean isCDMAType(Context context, int subId) {
        return false;
    }

    @Override
    public SubscriptionInfo getSubInfoBySubId(Context ctx, int subId) {
        return null;
    }

    @Override
    public SubscriptionInfo getFirstSimInfoBySlotId(Context ctx, int slotId) {
        return null;
    }

    @Override
    public boolean is4GDataOnly(Context context, int subId) {
        Log.d("DefaultMmsUtilsExt", "[is4GDataOnly]");
        if (context == null) {
        return false;
    }
        boolean result = false;
        ILteDataOnlyController ldoc = (ILteDataOnlyController) MPlugin.createInstance(
            ILteDataOnlyController.class.getName(), context);
        if (ldoc == null) {
            result = false;
        }
        if (ldoc.checkPermission(subId)) {
            result = false;
        } else {
            result = true;
        }
        Log.d("DefaultMmsUtilsExt", "[is4GDataOnly],result:" + result);
        return result;
    }
}
