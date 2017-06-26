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
import android.net.Uri;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;


public interface IMmsMessageListItemExt {

    /**
     * M: init download button resource.<br/>
     * OP09 Feature:MMS-01016 b) CancelDownload;
     * @param activity
     * @param downladBtnLayout
     * @param expireText
     */
    void initDownloadLayout(Activity activity, LinearLayout downladBtnLayout, TextView expireText);

    /**
     * M: show Sim Type Indicator<br/>
     * OP09 Feature: Android-03-021.
     * @param context the conext.
     * @param subId the subId for sim which recieve message.
     * @param textView the text view for message's status.
     */
    void showSimType(Context context, long subId, TextView textView);

    /**
     * M: show the download button for OP09;<br/>
     * OP09 Feature:MMS-01016 b) cancel download button;
     * @param messageUri the message uri.
     * @param selectedBox the selected box.
     * @param msgId the message's id.
     * @param deviceStorageIsFull ture: device is full; false: device is not full.
     * @param downloadBtnListener  download mss listener.
     * @param canceldownloadListener cancel downloading mms listener.
     */
    void showDownloadButton(Uri messageUri, CheckBox selectedBox, long msgId,
            boolean deviceStorageIsFull, OnClickListener downloadBtnListener,
            OnClickListener canceldownloadListener);

    /**
     * M: hide the downloadButton;<br/>
     * OP09 Feature:MMS-01016 b)cancel download button;
     * @param messageUri the Mms's uri.
     * @param canceldownloadListener cancel downloading mms listener.
     * @param selectedBox the selected box.
     * @param msgId the message id.
     */
    void hideDownloadButton(Uri messageUri, OnClickListener canceldownloadListener,
            CheckBox selectedBox, long msgId);

    /**
     * M: hide cancelDownload button and download label;<br/>
     * OP09 Feature:MMS-01016 b)cancelDownload;
     */
    void hideAllButton();

    /**
     * M: For received message, the sent date be used to show and the received date was used
     * to sort. For sent message, just show the sent date;<br/>
     * OP09 Feature: CDMA/GSM-01-025;
     * @param context the application context.
     * @param srcTxt the old send date text.
     * @param msgId the message's id.
     * @param msgType the message type.
     * @param smsSentDate the sms's sent date text.
     * @param boxId the message's box's type.
     * @return
     */
    String getSentDateStr(Context context, String srcTxt, long msgId, int msgType,
            long smsSentDate, int boxId);

    /**
     * M: draw status for masss text msg;<br/>
     * OP09 Feature: Android-03-043;
     *
     * @param context
     *            the application context.
     * @param statusLayout
     *            the special layout for mass text message's status.
     * @param isSms
     *            true: is sms; false: is not sms.
     * @param timestamp
     *            the id for mass messages. The timestamp is the same one
     *            for the same mass text messages.
     */
    void drawMassTextMsgStatus(Context context, LinearLayout statusLayout, boolean isSms,
            long timestamp);

    /**
     * M: click the failed mass text message ,just show the message's details;<br/>
     * OP09 Feature: Android-03-043;
     *
     * @param context
     *            the application context.
     * @param msgId
     *            the message id.
     * @param timeStamp
     *            the id for mass messages.The timestamp is the same one
     *            for the same mass text messages.
     * @return true: need edit failed text message. false: cannot edit the failed message.
     */
    boolean needEditFailedMessge(Context context, long msgId, long timeStamp);

    /**
     * OP09 Feature: format notify content.
     * @param address the message's address.
     * @param subject the message's subject.
     * @param msgSizeText the message's size.
     * @param expireText the message's expire.
     * @param expireTextView the view for show the notify content.
     * @return
     */
    void setNotifyContent(String address, String subject, String msgSizeText, String expireText,
            TextView expireTextView);

    /**
     * M: show the dual date for received message.<br/>
     * OP09 Feature: CDMA/GSM-01-025;
     * @param context the Context.
     * @param isRecievedMsg true: recieved msg. false: sent message.
     * @param subId the related sim's subId.
     * @param dateView the textView for date.
     * @param linearLayout the linearLayout.
     * @param timeDate the timeDate String.
     */
    void setDualTime(Context context, boolean isRecievedMsg, long subId, TextView dateView,
            LinearLayout linearLayout, String timeDate);

    /**
     * OP09 Feature: Show storage full toast
     * @param context  Context use to show toast
     * @return If support showing toast return true, otherwise reutrn false
     */
    boolean showStorageFullToast(Context context);

     /**
     * Hide dualTime panel.
     *
     * @param dateView
     * @param linearLayout
     */
    void hideDualTimePanel(TextView dateView, LinearLayout linearLayout);

}
