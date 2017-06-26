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
import android.net.Uri;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.util.Log;

public class DefaultMmsMessageListItemExt extends ContextWrapper implements IMmsMessageListItemExt {
    private static final String TAG = "Mms/MmsMessageListItemImpl";

    public DefaultMmsMessageListItemExt(Context context) {
        super(context);
    }

    public void initDownloadLayout(Activity activity, LinearLayout downladBtnLayout, TextView expireText) {

    }

    @Override
    public void showSimType(Context context, long subId, TextView textView) {
        Log.d(TAG, "showSimType default");
    }

    @Override
    public void showDownloadButton(Uri messageUri, CheckBox selectedBox, long msgId, boolean deviceStorageIsFull,
            OnClickListener downloadBtnListener, OnClickListener canceldownloadListener) {
        Log.d(TAG, "showDownloadButton");
    }

    //fixme
    public void hideDownloadButton(Uri messageUri, OnClickListener canceldownloadListener, CheckBox selectedBox, long msgId) {

    }

    @Override
    public void hideAllButton() {
    }

    public String getSentDateStr(Context context, String srcTxt, long msgId, int msgType,
            long smsSentDate, int boxId) {
        return srcTxt;
    }

    public void drawMassTextMsgStatus(Context context,  LinearLayout statusLayout, boolean isSms, long timestamp) {
    }

    //fixme
    public boolean needEditFailedMessge(Context context, long msgId, long timeStamp) {
        return true;
    }

    @Override
    public void setNotifyContent(String address, String subject, String msgSizeText,
            String expireText, TextView expireTextView) {
    }

    @Override
    public void setDualTime(Context context, boolean isRecievedMsg, long subId, TextView dateView,
            LinearLayout linearLayout, String timeDate) {

    }

    public boolean showStorageFullToast(Context context) {
        return false;
    }

    public void hideDualTimePanel(TextView dateView, LinearLayout linearLayout) {
    }
}
