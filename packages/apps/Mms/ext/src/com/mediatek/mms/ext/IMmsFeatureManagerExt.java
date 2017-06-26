/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
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


public interface IMmsFeatureManagerExt {
    //define featue name index for Op01, index from 1 to 100.

    /**
     * Add OP01 base index.
     */
    public static final int OP01_BASE_INDEX = 0;

    /**
     * Add comment for this feature.
     */
    public static final int MMS_ATTACH_ENHANCE                     = OP01_BASE_INDEX + 1;
    public static final int SMS_APPEND_SENDER                      = OP01_BASE_INDEX + 2;
    public static final int MMS_ENABLE_REPORT_ALLOWED              = OP01_BASE_INDEX + 3;
    public static final int MMS_ENABLE_FOLDER_MODE                 = OP01_BASE_INDEX + 4;
    public static final int SMS_ENABLE_FORWORD_WITH_SENDER         = OP01_BASE_INDEX + 5;
    public static final int DISPLAY_STORAGE_STATUS                 = OP01_BASE_INDEX + 6;
    public static final int ENABLE_ADJUST_FONT_SIZE                = OP01_BASE_INDEX + 7;
    public static final int SMS_ENABLE_VALIDITY_PERIOD             = OP01_BASE_INDEX + 8;
    public static final int MMS_RETRY_FOR_PERMANENTFAIL            = OP01_BASE_INDEX + 9;
    public static final int MMS_RETAIN_RETRY_INDEX_WHEN_INCALL     = OP01_BASE_INDEX + 10;
    public static final int MMS_SEND_EXPIRED_RES_IF_NOTIFY_EXPIRED = OP01_BASE_INDEX + 11;
    public static final int EXIT_COMPOSER_AFTER_FORWARD            = OP01_BASE_INDEX + 12;
    public static final int SUPPORT_AUTO_SELECT_SIMID              = OP01_BASE_INDEX + 13;
    public static final int SUPPORT_ASYNC_UPDATE_WALLPAPER         = OP01_BASE_INDEX + 14;
    public static final int SUPPORT_MESSAGING_NOTIFICATION_PROXY   = OP01_BASE_INDEX + 15;
    public static final int MMS_ENABLE_SYNC_START_PDP              = OP01_BASE_INDEX + 16;
    public static final int MMS_ENABLE_RESTART_PENDINGS            = OP01_BASE_INDEX + 17;
    public static final int MMS_ENABLE_GEMINI_MULTI_TRANSACTION    = OP01_BASE_INDEX + 18;
    public static final int SMS_ENABLE_CONCATENATE_LONG_SIM_SMS    = OP01_BASE_INDEX + 19;
    public static final int MMS_ENABLE_ADD_TOP_BOTTOM_SLIDE        = OP01_BASE_INDEX + 20;


    //define featue name index for Op02 origin, index from 201 to 300.

    /**
     * Add OP02 base index.
     */
    public static final int OP02_BASE_INDEX = 200;

    /**
     * this feature switch used to control whether show sim sms entry in the sms settings.
     * by default the value is true, that is in the Mms seetings, sms part, there is an entry
     * to check sms saved in sim card.
     * OP02 request not show this entry in the settings, show it in the conversationist menu.
     * currently this is the only operator to disable this feature.
     */
    public static final int SHOW_SIM_SMS_ENTRY_IN_SETTINGS = OP02_BASE_INDEX + 1;

    //define featue name index for Op03 origin, index from 301 to 400.
    /**
     * Add OP03 base index.
     */
    public static final int OP03_BASE_INDEX = 300;
    /**
     * Support retrieve status error RETRIEVE_STATUS_ERROR_PERMANENT_MESSAGE_NOT_FOUND check
     */
    public static final int MMS_ENABLE_RETRIEVE_STATUS_ERROR_CHECK = OP03_BASE_INDEX + 1;

    /**
     * Is conversation split supported
     */
    public static final int CONVERSATION_SPLIT_SUPPORTED = OP03_BASE_INDEX + 2;

    /**
     * Support sms encoding type
     */
    public static final int ENABLED_SMS_ENCODING_TYPE = OP03_BASE_INDEX + 3;

    //define featue name index for Op09, index from 901 to 1000.

    /**
     * Add OP09 base index.
     */
    public static final int OP09_BASE_INDEX = 900;

    /**
     * Add comment for this feature.
     */
    public static final int STRING_REPLACE_MANAGEMENT = OP09_BASE_INDEX + 1;
    /// M: OP09Feature: for show dual time for received message item.
    public static final int SHOW_DUAL_TIME_FOR_MESSAGE_ITEM = OP09_BASE_INDEX + 2;
    /// M: OP09Feature: for show tab setting for MMS setting.
    public static final int MMS_TAB_SETTING = OP09_BASE_INDEX + 3;
    /// M: OP09Feature: for show dual send button in compose.
    public static final int MMS_DUAL_SEND_BUTTON = OP09_BASE_INDEX + 4;
    /// M: OP09Feature: for preview VCard in MMS compose.
    public static final int MMS_VCARD_PREVIEW = OP09_BASE_INDEX + 5;
    /// M: OP09Feature: change the legnthRequired MMS to SMS;
    public static final int CHANGE_LENGTH_REQUIRED_MMS_TO_SMS = OP09_BASE_INDEX + 6;
    /// M: OP09Feature: mass text msg: there is only one message item which be show , when send a text msg to more than
    /// one recipient in one conversation.
    public static final int MASS_TEXT_MSG =  OP09_BASE_INDEX + 7;
    /// M: OP09Feature: It is allowed to multiCOmpose exist .
    public static final int MMS_MULTI_COMPOSE = OP09_BASE_INDEX + 8;
    /// M: OP09Feature: can cancel the downloading MMS which has already start download.
    public static final int MMS_CANCEL_DOWNLOAD = OP09_BASE_INDEX + 9;
    /// M: OP09Feature: splice missed sms which is a long sms which contains more than one short messages.
    public static final int SPLICE_MISSED_SMS = OP09_BASE_INDEX + 10;
    /// M: OP09Feature: new class_zero model: when user received more than one class_zero msg. the device should always
    /// show the latest class_zero msg.
    public static final int CLASS_ZERO_NEW_MODEL_SHOW_LATEST = OP09_BASE_INDEX + 11;
    /// M: OP09Feature: when device memory has low than 5% memory, device show show Notification for user.
    public static final int MMS_LOW_MEMORY = OP09_BASE_INDEX + 12;
    /// M: OP09Feature: wake up screen when the device has inserted headSet when receive new msg.
    public static final int WAKE_UP_SCREEN_WHEN_RECEIVE_MSG = OP09_BASE_INDEX + 13;
    /// M: OP09Feature: AdvanceSearchView: add time search condition.
    public static final int ADVANCE_SEARCH_VIEW =  OP09_BASE_INDEX + 14;
    /// M: OP09Feature: When the device is at roaming status, MMS cannot allow to set delivery report.
    public static final int DELIEVEEY_REPORT_IN_ROAMING = OP09_BASE_INDEX + 15;
    /// M: OP09Feature: show number location
    public static final int MMS_NUMBER_LOCATION = OP09_BASE_INDEX + 16;
    /// M: OP09Feature: format date and time stamp for op09;
    public static final int FORMAT_DATE_AND_TIME = OP09_BASE_INDEX + 17;
    /// M: OP09Feature: format notification content for adding expire date.
    public static final int FORMAT_NOTIFICATION_CONTENT = OP09_BASE_INDEX + 18;
    /// M: OP09Feature: read SMS from dual model UIM;
    public static final int READ_SMS_FROM_DUAL_MODEL_UIM = OP09_BASE_INDEX + 19;
    /// M: OP09Feature: show sent date and sorted by received date
    public static final int SHOW_DATE_MANAGEMENT = OP09_BASE_INDEX + 20;
    /// M: OP09Feature: there is more strict validation for SMS address.
    public static final int MORE_STRICT_VALIDATION_FOR_SMS_ADDRESS = OP09_BASE_INDEX + 21;
    /// M: OP09Feature: show preview for recipient.
    public static final int SHOW_PREVIEW_FOR_RECIPIENT = OP09_BASE_INDEX + 22;
    /// M: OP09Feature: when receive SI message, show the dialog to user.
    public static final int SHOW_DIALOG_FOR_NEW_SI_MSG = OP09_BASE_INDEX + 23;
    /// M: OP09Feature: When MMS transaction failed, show toast to notify user the specific reason.
    public static final int MMS_TRANSACTION_FAILED_NOTIFY = OP09_BASE_INDEX + 24;
    /// M: OP09Feature: Modify MMS retry time interval.
    public static final int MMS_RETRY_SCHEDULER = OP09_BASE_INDEX + 25;
    /// M: OP09Feature: can add cc recipients into mms.
    public static final int MMS_CC_RECIPIENTS = OP09_BASE_INDEX + 26;
    /// M: OP09Feature: Set priority when sending SMS.
    public static final int SMS_PRIORITY = OP09_BASE_INDEX + 27;
    /// M: OP09Feature: turn page after fling screen left or right;
    public static final int MMS_PLAY_FILING_TURNPAGE = OP09_BASE_INDEX + 28;
    /// M: OP09Feature: unsupported files;
    public static final int MMS_UNSUPPORTED_FILES = OP09_BASE_INDEX + 29;

    public boolean isFeatureEnabled(int featureNameIndex);
}

