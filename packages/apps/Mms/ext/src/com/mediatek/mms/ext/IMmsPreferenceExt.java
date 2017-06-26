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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import android.database.Cursor;

import com.google.android.mms.pdu.AcknowledgeInd;
import com.google.android.mms.pdu.NotifyRespInd;

public interface IMmsPreferenceExt {
    /**
     * Sync mms data roaming status with setting' data roaming's status
     * @param activity
     * @param preference
     * @param listSimInfo
     * @param simIndex
     * @return
     */
    boolean syncDataRoamingStatus(Activity activity, Preference preference, int simIndex);

    /**
     * M: Modify the auto_retrieve_during_roaming status in mms preference in accorded with setting 's
     * auto_retrieve_during_roaming status
     *
     * @param context
     * @param intent
     */
    void modifyDataRoamingPreference(Context context, Intent intent);

    /**
     * config smsPreferenceActivity's preference. can add any preferences in preferenceCategory
     *
     * @param hostActivity    Host Activity that call this api
     * @param pc                 will add preference in this PreferenceCategory
     * @param simCount       current simCount
     * @return
    */
    void configSmsPreference(Activity hostActivity, PreferenceCategory pC, int simCount);

    /**
     * config smsPreferenceActivity's preference editor for restore default value
     *
     * @param hostActivity    Host Activity that call this api
     * @param editor            from config the editor to restore default value
     * @return
    */
    void configSmsPreferenceEditorWhenRestore(Activity hostActivity, SharedPreferences.Editor editor);


    /**
     * config mmsPreferenceActivity's preference editor for restore default value
     *
     * @param hostActivity    Host Activity that call this api
     * @param editor            from config the editor to restore default value
     * @return
    */
    void configMmsPreferenceEditorWhenRestore(Activity hostActivity, SharedPreferences.Editor editor);

    /**
     * get sms validity param in bundle format.
     *
     * @param context    host context for get preference value
     * @param subId      sub id
     * @return Bundle     bundle contain sms validity value; return null if sms validity feature is close
    */
    Bundle getSmsValidityParamBundleWhenSend(Context context, int subId);

    /**
     * set SelectCardPreferenceActivity's title.
     *
     * @param hostActivity    Host Activity that call this api
     * @return
    */
    void configSelectCardPreferenceTitle(Activity hostActivity);

    /**
     * handle preference's click event in SelectCardPreferenceActivity.
     *
     * @param hostActivity              Host Activity that call this api
     * @param preferenceScreen      preferenceScreen contain the clicked preference
     * @param preference                the preference that be clicked
     * @param subId                    the sub id of the clicked preference correspondingly
     * @return
    */
    boolean handleSelectCardPreferenceTreeClick(Activity hostActivity, int subId);

    /**
     * format sms body, op01 plugin will append sms origin sender infor  with sms body
     *
     * @param context                     Host context for get preference value
     * @param smsBody                   forwarding sms's body
     * @param nameAndNumber        combine string of origin sender's name and number; number only if contact don't has this number
     * @param boxId                        the box id in storage. only receivered sms append sender infor.
     * @return  String                       append nameAndNumber with smsBody if forward received sms, or smsBody only
    */
    public String formatSmsBody(Context context, String smsBody, String nameAndNumber, int boxId);

    /**
     * format sms body, op01 plugin will append sms origin sender infor  with sms body
     *
     * @param context                     Host context for get preference value
     * @param smsBody                   forwarding sms's body
     * @param nameAndNumber        combine string of origin sender's name and number; number only if contact don't has this number
     * @param cursor                       cursor contain the origin sms
     * @return  String                      append nameAndNumber with smsBody if forward received sms, or smsBody only
    */
    public String formatSmsBody(Context context, String smsBody, String nameAndNumber, Cursor cursor);

    /**
     * config generalPreferenceActivity's preference. can add any preferences in preferenceCategory
     *
     * @param hostActivity    Host Activity that call this api
     * @param pc                 will add preference in this PreferenceCategory
     * @return
    */
    void configGeneralPreference(Activity hostActivity, PreferenceCategory pC);


    /**
     * set generalPreference host to operator
     *
     * @param host   Through which operator can call APIs definded in host
     * @return
    */
    void setGeneralPreferenceHost(IGeneralPreferenceHost host);

    /**
     * config mmsPreferenceActivity's preference. can add any preferences in preferenceCategory
     *
     * @param hostActivity    Host Activity that call this api
     * @param pc                 will add preference in this PreferenceCategory
     * @param simCount       current simCount
     * @return
    */
    void configMmsPreference(Activity hostActivity, PreferenceCategory pC, int simCount);

    /**
     * config sim selected status in MultiSimPreferenceActivity . set one SIM card is selected or not
     *
     * @param hostActivity    Host Activity that call this api
     * @param preference      the preference which will be set state
     * @param subId             the selected sim card' ID
     * @param cp                 CheckBoxPreference of the selected sim card
     * @return True: the preference was handled, False: preference no found
    */
    boolean configMmsPreferenceState(Activity hostActivity, String preference, int subId, CheckBoxPreference cp);

    /**
     * config MultiSimPreferenceActivity' title, when select a preference which is added by plugin
     *
     * @param hostActivity    Host Activity that call this api
     * @return
    */
    void configMultiSimPreferenceTitle(Activity hostActivity);


    /**
     * check sim selected status in MultiSimPreferenceActivity. and save it into share preference if the status changed
     *
     * @param hostActivity    Host Activity that call this api
     * @param preference      the preference which selected
     * @param subid            the selected sim card' ID
     * @param checked         if the CheckBoxPreference is selected or not
     * @return True: the preference was handled, False: preference no found
    */
    boolean setMmsPreferenceState(Activity hostActivity, String preference, int subId, boolean checked);

    /**
     *  set if enable send delivery report in acknowledgeInd
     *
     * @param mContext      the  Context that call this api
     * @param subId         the sub card will be set
     * @param acknowledgeInd    the data structure message server will send
     * @return
    */
    void setAcknowledgeDeliveryReport(Context mContext, int subId, AcknowledgeInd acknowledgeInd);


    /**
     *  set if enable send delivery report in notifyRespInd
     *
     * @param mContext      the  Context that call this api
     * @param subId         the sim card will be set
     * @param notifyRespInd    the data structure message server will send
     * @return
    */
    void setNotifyRespDeliveryReport(Context mContext, int subId, NotifyRespInd notifyRespInd);

}
