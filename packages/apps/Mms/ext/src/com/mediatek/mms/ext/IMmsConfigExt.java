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

import android.content.Context;
import android.net.Uri;
import android.widget.TextView;
import android.view.inputmethod.EditorInfo;
import org.apache.http.params.HttpParams;
import java.util.ArrayList;
/// M: Add MmsService configure param @{
import android.os.Bundle;
/// @}

public interface IMmsConfigExt {
    /**
     * Returns the text length threshold of change to mms from sms.
     *
     * @return the text length threshold of change to mms from sms.
     */
    int getSmsToMmsTextThreshold();

    /**
     * Returns the text length threshold of change to mms from sms.
     * @param context the context.
     * @return the text length threshold of change to mms from sms.
     */
    int getSmsToMmsTextThresholdForC2K(Context context);

    /**
     * set the text length threshold of change to mms from sms according to the configuration file.

     * @param value the threshold value to be set
     *
     */
    void setSmsToMmsTextThreshold(int value);

    /**
     * Returns the max length of mms or sms text content.
     *
     * @return the max length of mms or sms text content.
     */
    int getMaxTextLimit();

    /**
     * Set the max length of mms or sms text content.
     *
     * @param value the max length of mms or sms text content to be set
     *
     */
    void setMaxTextLimit(int value);

    /**
     * Returns the max count of mms recipient.
     *
     * @return the max count of mms recipient.
     */
    int getMmsRecipientLimit();

    /**
     * Set the max count of mms recipient.
     *
     * @param value the max count of mms recipient to be set
     *
     */
    void setMmsRecipientLimit(int value);


    /// M: For new feature CMCC_Mms in ALPS00325381, MMS easy
    /// porting check in JB @{
    /**
     * Returns the socket timeout.
     *
     * @return the value of socket timeout in ms.
     */
    int getHttpSocketTimeout();

    /**
     * Set the socket timeout.
     *
     * @param socketTimeout    the value of socket timeout in ms.
     */
    void setHttpSocketTimeout(int socketTimeout);

    /// @}

    /**
     * Returns whether show SIM SMS at setting
     *
     * @return              whether show SIM SMS at setting.
     */
    boolean isEnableSIMSmsForSetting();


    /// M: Add for CMCC FT request to set send/receive related parameters
    /**
     * Returns which time of MMS transaction retry need prompt failure
     *
     * @return              Index of retry to show prompt
     */
    int getMmsRetryPromptIndex();

    /**
     * Returns Mms transaction retry scheme
     *
     * @return              Retry scheme array
     */
    int[] getMmsRetryScheme();

     /**
     * Returns Mms transaction retry scheme according to the messageType.
     * @param messageType, MESSAGE_TYPE_NOTIFICATION_IND or MESSAGE_TYPE_SEND_REQ.
     * @return Retry scheme array
     */
    int[] getMmsRetryScheme(int messageType);

    /**
     * Returns Mms socket send timeout
     *
     * @params params     http params
     * @return
     */
    void setSoSndTimeout(HttpParams params);
    /// @}

    /**
     * in Convesation.startDeleteAll, append extra query parameter.  op01 feature
     *
     * @param uri    the uri of startDeleteAll. can append any param for use. op01 append "groupDeleteParts" to "yes".
     */
    Uri appendExtraQueryParameterForConversationDeleteAll(Uri uri);

    /// M: ALPS00527989, Extend TextView URL handling @ {
    /**
     * Sets ExtendURLSpan for extended URL click handling
     *
     * @param textView    the TextView which is setted Extented url span format
     * @return
     */
    void setExtendUrlSpan(TextView textView);
    /// @}

    /**
     * judge whether the sim card support receiving cb message.
     *
     * @param context the Context.
     * @param subId
     *            the sim's subId.
     * @return true: support cb msg. false: not support cb.
     */
    boolean isSupportCBMessage(Context context, long subId);

    /**
     * M: for OP09, whether allow to request delivery report when in international roaming status.
     * @param context the Context.
     * @param subId Only CDMA card is not allowed.
     * @return true: Request delivery report allowed.
     *         false: Request delivery report not allowed.
     */
    boolean isAllowDRWhenRoaming(Context context, long subId);

    /// M: ALPS00837193, query undelivered mms with non-permanent fail ones or not @{
    /**
     * Add non-permanent fail mms parameter for undelivery mms query
     *
     * @param defaultUri. If not special uri used, just return this uri
     * @return The uri used to query undelivery mms
     *
     */
    Uri getUndeliveryMmsQueryUri(Uri defaultUri);
    /// @}

    /// M: New plugin API @{
    /**
     * Open URL to brownser. when click the http uri in textview to run this api.
     * op01 will show a dialog to notify user when open http url to browser.
     *
     * @param context     host context
     * @param url           the http url to open.
     * @ return
     */
    void openUrl(Context context, String url);
    /// @}

    /// M: ALPS00956607, not show modify button on recipients editor @{
    /**
     * Set recipients editor's outAttrs to support special requirement on this editor.
     * Op01 does not show edit button in Recipient editor when landscape.
     *
     * @param outAttrs Set required attributes to it.
     * @return
     */
    void setRecipientsEditorOutAtts(EditorInfo outAttrs);
    /// @}

    /**
     * M: extends audio content type<br/>
     *    OP09Feature:MMS-02009
     * @param audioType
     */
    void setExtendedAudioType(ArrayList<String> audioType);

    /// M: Add MmsService configure param @{
    /**
     * M: Return configOverrides for MmsService request
     *
     * @return the config bundle
     */
    Bundle getMmsServiceConfig();
    /// @}
}

