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
import android.content.AsyncQueryHandler;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.util.Log;
import java.util.ArrayList;

public class DefaultMmsComposeExt extends ContextWrapper implements IMmsComposeExt {
    private static final String TAG = "Mms/MmsComposeImpl";

    public DefaultMmsComposeExt(Context context) {
        super(context);
    }

    public void addSplitMessageContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo, Activity activity,
            long messageGroupId, int messagesCount) {
    }

    public void addSplitThreadOptionMenu(Menu menu, Activity activity, long threadId) {
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }

    public void configSubjectEditor(EditText subjectEditor) {
        return;
    }

    public String getNumberLocation(Context context, String number) {
        return number;
    }

    public Uri getConverationUri(Uri uriSrc, long threadId) {
        return uriSrc;
    }

    public boolean deleteMassTextMsg(AsyncQueryHandler backQueryHandler, long msgId, long timeStamp) {
        return false;
    }

    public boolean lockMassTextMsg(Context context, long msgId, long timeStamp, boolean lock) {
        return false;
    }

    public boolean showMassTextMsgDetail(Context context, long timeStamp) {
        return false;
    }

    public String getNumbersFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        boolean usingColon = intent.getBooleanExtra(USING_COLON, false);
        String selectContactsNumbers = intent.getStringExtra(SELECTION_CONTACT_RESULT);
        if (usingColon) {
            if (selectContactsNumbers == null || selectContactsNumbers.length() < 1) {
                return null;
            }
            String[] numberArray = selectContactsNumbers.split(NUMBERS_SEPARATOR_COLON);
            String numberTempl = "";
            int simcolonIndex = -1;
            int colonIndex = -1;
            int separatorIndex = -1;
            for (int index = 0; index < numberArray.length; index++) {
                numberTempl = numberArray[index];
                simcolonIndex = numberTempl.indexOf(NUMBERS_SEPARATOR_SIMCOLON);
                colonIndex = numberTempl.indexOf(NUMBERS_SEPARATOR_COMMA);
                if (simcolonIndex > 0) {
                    if (colonIndex < 0) {
                        separatorIndex = simcolonIndex;
                    } else if (simcolonIndex < colonIndex) {
                        separatorIndex = simcolonIndex;
                    } else if (colonIndex > 0) {
                        separatorIndex = colonIndex;
                    }
                } else {
                    if (colonIndex > 0) {
                        separatorIndex = colonIndex;
                    }
                }
                if (separatorIndex > 0) {
                    numberArray[index] = numberTempl.substring(0, separatorIndex);
                }
                simcolonIndex = -1;
                colonIndex = -1;
                separatorIndex = -1;
            }
            return TextUtils.join(NUMBERS_SEPARATOR_SIMCOLON, numberArray);
        }
        return selectContactsNumbers;
    }

    @Override
    public void showDisableDRDialog(final Activity activity, final int subId) {
        return;
    }

    @Override
    public void enableDRWarningDialog(Context context, boolean isEnable, int subId) {
        return;
    }

    @Override
    public boolean needConfirmMmsToSms() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setConfirmMmsToSms(boolean needConfirm) {
        // TODO Auto-generated method stub
    }

    /// M: New plugin API @{
    public void addMmsUrlToBookMark(Context context, ContextMenu menu, int menuId, ArrayList<String> urls) {
        Log.d(TAG, "addMmsUrlToBookMark");
        return;
    }
    /// @}

    @Override
    public int getSmsMessageAndSaveToSim(String[] numbers, String scAddress, ArrayList<String> messages, int smsStatus,
            long timeStamp, int subId, int srcResult) {
        // TODO Auto-generated method stub
        return srcResult;
    }

    /// M: --------------------OP09 Plug-in Re-factory--------------------
    @Override
    public void initDualSendButtonLayout(Activity activity, LinearLayout buttonWithCounter, TextView newTextCounter) {

    }

    @Override
    public void initDualSendBtnForAttachment(Context context, LinearLayout buttonWithCounter, Button sendButton,
            ViewOnClickListener listener) {

    }

    @Override
    public void initDualSendBtnForDialogMode(Context context, LinearLayout buttonWithCounter, TextView newTextCounter) {

    }

    @Override
    public void hideDualButtonPanel() {
        // TODO Auto-generated method stub

    }

    @Override
    public void showDualButtonPanel() {
        // TODO Auto-generated method stub

    }

    @Override
    public void hideDualButtonAndShowSrcButton() {

    }

    @Override
    public void setDualSendButtonType(ViewOnClickListener btnListener) {

    }

    @Override
    public void updateDualSendButtonStatue(boolean enable, boolean isMms) {

    }

    //fixme
    @Override
    public void updateNewTextCounter(int textLineCount, boolean isMms, int remainingInCurrentMessage, int msgCount) {

    }

    @Override
    public void setIsShowDualButtonPanel(boolean ifShowDualButtonPanel) {

    }

    @Override
    public void hideOrShowSubjectForCC(boolean screenIsFull, EditText subjectTextEditor) {

    }

    @Override
    public boolean getIsSubjectHide() {
        return false;
    }
}
