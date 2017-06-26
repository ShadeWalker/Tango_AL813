/*
* This software/firmware and related documentation ("MediaTek Software") are
* protected under relevant copyright laws. The information contained herein
* is confidential and proprietary to MediaTek Inc. and/or its licensors.
* Without the prior written permission of MediaTek inc. and/or its licensors,
* any reproduction, modification, use or disclosure of MediaTek Software,
* and information contained herein, in whole or in part, shall be strictly prohibited.
*/
/* MediaTek Inc. (C) 2011. All rights reserved.
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

import java.util.List;

import com.mediatek.contacts.GlobalEnv;

import android.content.ContentUris;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

public class SubInfoUtils {
    private static final String TAG = "SubInfoUtils";

    public static final String PHONE_BOOK_SERVICE_NAME = "simphonebook";

    public static final String ICC_PROVIDER_SDN_URI = "content://icc/sdn/subId";
    public static final String ICC_PROVIDER_ADN_URI = "content://icc/adn/subId";
    public static final String ICC_PROVIDER_PBR_URI = "content://icc/pbr/subId";

    //private static ArrayList<Bitmap> mIcons = Lists.newArrayList();

    public static List<SubscriptionInfo> getActivatedSubInfoList() {
        return SubscriptionManager.from(GlobalEnv.getApplicationContext())
                .getActiveSubscriptionInfoList();
    }

    public static int getActivatedSubInfoCount() {
        return SubscriptionManager.from(GlobalEnv.getApplicationContext())
                .getActiveSubscriptionInfoCount();
    }

    public static SubscriptionInfo getSubInfoUsingSubId(int subId) {
        if (!checkSubscriber(subId)) {
            return null;
        }
        List<SubscriptionInfo> subscriptionInfoList = getActivatedSubInfoList();
        if (subscriptionInfoList != null && subscriptionInfoList.size() > 0) {
            for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                if (subscriptionInfo.getSubscriptionId() == subId) {
                    return subscriptionInfo;
                }
            }
        }
        return null;
    }

    public static int[] getActiveSubscriptionIdList() {
        return SubscriptionManager.from(GlobalEnv.getApplicationContext())
            .getActiveSubscriptionIdList();
    }

    public static boolean iconTintChange(int iconTint, int subId) {
        Log.d(TAG, "iconTintChange iconTint = " + iconTint + " subId = " + subId);
        boolean isChanged = true;
        List<SubscriptionInfo> activeList = getActivatedSubInfoList();
        if (activeList == null) {
            isChanged = false;
            return isChanged;
        }
        //TODO:: Check here,it may cause performance poor than L0
        for (SubscriptionInfo subInfo : activeList) {
            if (subInfo.getSubscriptionId() == subId &&
                iconTint == subInfo.getIconTint()) {
                isChanged = false;
                break;
            }
        }
        return isChanged;
    }

    public static int getSlotIdUsingSubId(int subId) {
        if (!checkSubscriber(subId)) {
            return -1;
        }
        SubscriptionInfo subscriptionInfo = getSubInfoUsingSubId(subId);
        return subscriptionInfo == null ? getInvalidSlotId() : subscriptionInfo.getSimSlotIndex();
    }

    public static String getDisplaynameUsingSubId(int subId) {
        if (!checkSubscriber(subId)) {
            return null;
        }
        SubscriptionInfo subscriptionInfo = getSubInfoUsingSubId(subId);
        if (subscriptionInfo == null) {
            return null;
        }
        CharSequence displayName = subscriptionInfo.getDisplayName();
        return displayName == null ? null : displayName.toString();
    }

    public static int getColorUsingSubId(int subId) {
        if (!checkSubscriber(subId)) {
            return -1;
        }
        SubscriptionInfo subscriptionInfo = getSubInfoUsingSubId(subId);
        return subscriptionInfo == null ? -1 : subscriptionInfo.getIconTint();
    }

    public static Uri getIccProviderUri(int subId) {
        if (!checkSubscriber(subId)) {
            return null;
        }
        if (SimCardUtils.isSimUsimType(subId)) {
            return ContentUris.withAppendedId(Uri.parse(ICC_PROVIDER_PBR_URI), subId);
        } else {
            return ContentUris.withAppendedId(Uri.parse(ICC_PROVIDER_ADN_URI), subId);
        }
    }

    public static Uri getIccProviderSdnUri(int subId) {
        if (!checkSubscriber(subId)) {
            return null;
        }
        return ContentUris.withAppendedId(Uri.parse(ICC_PROVIDER_SDN_URI), subId);
    }

    public static boolean checkSubscriber(int subId) {
        if (subId < 1) {
            Log.d(TAG, "Warning, invalid subId: " + subId);
            return false;
        }
        return true;
    }

    public static boolean isActiveForSubscriber(int subId) {
        SubscriptionInfo subscriptionInfo = getSubInfoUsingSubId(subId);
        if (subscriptionInfo != null) {
            return true;
        } else {
            return false;
        }
    }

    public static String getPhoneBookServiceName() {
        return PHONE_BOOK_SERVICE_NAME;
    }

    public static int getInvalidSlotId() {
        return SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    }

    public static int getInvalidSubId() {
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }


    public static Bitmap getIconBitmap(int subId) {
        if (!checkSubscriber(subId)) {
            return null;
        }
        SubscriptionInfo subscriptionInfo = getSubInfoUsingSubId(subId);
        return subscriptionInfo == null ? null :
            subscriptionInfo.createIconBitmap(GlobalEnv.getApplicationContext());
    }

    public static Drawable getIconDrawable(int subId) {
        Bitmap iconBitmap = getIconBitmap(subId);
        return iconBitmap == null ? null : new BitmapDrawable(iconBitmap);
    }
}
