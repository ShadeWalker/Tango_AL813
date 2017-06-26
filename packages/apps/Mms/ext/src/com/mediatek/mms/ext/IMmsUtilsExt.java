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
import android.content.Intent;
import android.database.Cursor;
import android.telephony.SubscriptionInfo;
import android.widget.TextView;

/**
 * M: mms utils ext added for op09.
 */
public interface IMmsUtilsExt {

    int TOAST_TYPE_FOR_SAVE_DRAFT = 0;
    int TOAST_TYPE_FOR_SEND_MSG = 1;
    int TOAST_TYPE_FOR_ATTACH = 2;
    int TOAST_TYPE_FOR_DOWNLOAD_MMS = 3;

    String MASS_TEXT_MESSAGE_GROUP_ID = "mass_txt_msg_group_id";

    /**
     * This method will format the date with the system setting's format. And it will return the yesterday string if the
     * date just was yesterday and it will return the now string if the date just was now.
     * OP09 Feature: CDMA/GSM-01-025;
     * @param context
     * @param when
     * @return
     */
    String formatDateAndTimeStampString(Context context, long msgDate, long msgDateSent,
            boolean fullFormat, String formatStr);

    /**
     * M: show Sim Type Indicator.<br>
     * OP09 Feature: Android-03-021.
     * @param context the application context.
     * @param subId  the subId for the sim which recieve the message.
     * @param textView the textView for the sim Type.
     */
    void showSimTypeBySubId(Context context, int subId, TextView textView);

    /**
     * Whether allow to safe draft
     * @param activity
     * @param isNofityUser
     * @return
     */
    boolean allowSafeDraft(final Activity activity, boolean deviceStorageIsFull, boolean isNofityUser, int toastType);

    /**
     * M:Format date and time according to system setting's time display mode.<br/>
     * OP09 Feature: CDMA/GSM-01-025;
     * @param context
     * @param time
     * @param formatFlags
     * @return
     */
    String formatDateTime(Context context, long time, int formatFlags);

    /**
     * Judge the address is whether dialable or not
     * @param address
     * @return
     */
    boolean isWellFormedSmsAddress(String address);

    /**
     * M: Set Intent data for mass text message<br/>
     * OP09Feature: CDMA/GSM-01-025;
     * @param intent
     * @param groupId
     */
    void setIntentDateForMassTextMessage(Intent intent, long groupId);

    /**
     * M: Get groupId from intent for mass txt message.<br/>
     * OP09Feature: CDMA/GSM-01-025;
     * @param intent
     * @return
     */
    long getGroupIdFromIntent(Intent intent);

    /**
     * M: Get report items data for mass text message.<br/>
     * OP09Feature: CDMA/GSM-01-025;
     * @param context
     * @param projection
     * @param groupId
     * @return
     */
    Cursor getReportItemsForMassSMS(Context context, String[] projection, long groupId);

     /**
     * Judge whether the SIM card in the slotId is CDMA type.
     * @param context the application context.
     * @param subId the subId which received message.
     * @return true if the card is CDMA type.
     */
    boolean isCDMAType(Context context, int subId);

    /**
     * M: get subinfo by subId.
     * @param ctx the Context.
     * @param subId the SubID of sim.
     * @return the subInfoRecord.
     */
    SubscriptionInfo getSubInfoBySubId(Context ctx, int subId);

    /**
     * M: get first subinforRecord by slotId.
     * @param ctx the Context.
     * @param slotId the slot Id.
     * @return the subInfoRecord.
     */
    SubscriptionInfo getFirstSimInfoBySlotId(Context ctx, int slotId);

    /**
     * M: check the phone is in data only model or not.
     * @param context the context.
     * @param subId the sim's subId.
     * @return true: 4g data only ; false: not in 4g data only model.
     */
    boolean is4GDataOnly(Context context, int subId);
}
