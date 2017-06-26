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
import android.content.Intent;
import android.net.Uri;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

public interface IMmsComposeExt {

    int MSG_LIST_SHOW_MSGITEM_DETAIL = 3600;

    String USING_COLON = "USE_COLON";
    String SELECTION_CONTACT_RESULT = "contactId";
    String NUMBERS_SEPARATOR_COLON = ":";
    String NUMBERS_SEPARATOR_SIMCOLON = ";";
    String NUMBERS_SEPARATOR_COMMA = ",";

    /**
     * for OP01; config subject editor to allow max 14 alpha number.
     *
     * @param subjectEditor the control of subject editor
     * @return
     */
    void configSubjectEditor(EditText subjectEditor);

    /**
     * M: For OP09; Add Context Menu for split message apart
     * @param menu
     * @param v
     * @param menuInfo
     * @param context
     * @param messageGroupId
     */
    void addSplitMessageContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo, Activity activity,
            long messageGroupId, int messagesCount);

    /**
     * M: For OP09; Add option menu for split thread apart
     * @param menu
     * @param activity
     * @param threadId
     */
    void addSplitThreadOptionMenu(Menu menu, Activity activity, long threadId);

    /**
     * M:For OP09;
     * @param item
     * @return
     */
    boolean onOptionsItemSelected(MenuItem item);

    /**
     * M: get the location of number<br/>
     * OP09Feature: Android-03-033;
     * @param context
     * @param number
     * @return
     */
    String getNumberLocation(Context context, String number);

    /**
     * M:get the conversation uri <br/>
     *  OP09Feature: Android-03-043;
     * @param uriSrc
     * @param threadId
     * @return
     */
    Uri getConverationUri(Uri uriSrc, long threadId);

    /**
     * M: delete mass text msg<br/>
     * OP09Feature: Android-03-043;
     * @param msgId
     * @param timeStamp
     * @return
     */
    boolean deleteMassTextMsg(AsyncQueryHandler backQueryHandler, long msgId, long timeStamp);

    /**
     * M: lock mass text msg.<br/>
     * OP09Feature: Android-03-043;
     * @param context
     * @param msgId
     * @param timeStamp
     * @param lock
     * @return true: compose need continue to lock msg with the old lock process.
     */
    boolean lockMassTextMsg(Context context, long msgId, long timeStamp, boolean lock);

    /**
     * M: show mass text msg details<br/>
     * OP09Feature: Android-03-043;
     * @param context
     * @param timeStamp
     * @return
     */
    boolean showMassTextMsgDetail(Context context, long timeStamp);

    /**
     * M: Get the numbers from the contact's intent.<br/>
     * OP09Feature: Android-03-038;
     * @return
     */
    String getNumbersFromIntent(Intent intent);

    /**
     * M: show the warning dialog to user.<br/>
     * OP09Feature: CDMA/GSM-01-009 3.b;
     * @param activity the activity which call the dialog.
     * @param subId the sim's subId.
     */
    void showDisableDRDialog(final Activity activity, final int subId);

    /**
     * M: set the dr waring dialog show every time or just once.<br/>
     * op09Feature: CDMA/GSM-01-009 3.b;
     * @param context the Context.
     * @param isEnable true: show warning dialog.
     * @param subId the sim's SubId.
     */
    void enableDRWarningDialog(Context context, boolean isEnable, int subId);

    /**
     * Add menu to add uri bookmark for Mms
     * @param context    Context use to call browser
     * @param menu       Menu object
     * @param menuId    Menu item id
     * @param urls          Urls to add
     * @return void
     */
    void addMmsUrlToBookMark(Context context, ContextMenu menu,
                                    int menuId, ArrayList<String> urls);

    /**
     * M: whether be allowed to translate the long sms to Mms.<br/>
     * op09Feature: CDMA/GSM-01-009 2);
     * @return
     */
    boolean needConfirmMmsToSms();

    /**
     * M: Set the sign for message<br/>
     * op09Feature: CDMA/GSM-01-009 2);
     * @param needConfirm
     */
    void setConfirmMmsToSms(boolean needConfirm);

    /**
     * M: Copy message to SIM Card with the first validate number of numbers;<br/>
     * OP09Feature: Android-03-043;
     * @param numbers  the message's number.
     * @param scAddress the service center.
     * @param messages the message content.
     * @param smsStatus the message's status.
     * @param timeStamp the message's date.
     * @param subId the sim's sub-Id.
     * @param srcResult the result for receiving message.
     * @return the save status.
     */
    int getSmsMessageAndSaveToSim(String[] numbers, String scAddress, ArrayList<String> messages, int smsStatus,
            long timeStamp, int subId, final int srcResult);

    /**
     * M: initial resource for dual sendButton.<br/>
     * OP09 Feature: CDMA/GSM-01-019 DualSendButton;
     * @param activity
     * @param buttonWithCounter : which contains the dual send button layout.
     * @param newTextCounter : which is the new text counter for dual send button which has been added in compose_message_activity.xml
     */
    void initDualSendButtonLayout(Activity activity, LinearLayout buttonWithCounter, TextView newTextCounter);

    /**
     * M: initial resources for dual send button for attachment view.<br/>
     * OP09 Feature: CDMA/GSM-01-019 DualSendButton;
     * @param context
     * @param buttonWithCounter, which contains the dual send button layout.
     * @param sendButton, which is the old send button.
     */
    void initDualSendBtnForAttachment(Context context, LinearLayout buttonWithCounter, Button sendButton,
            ViewOnClickListener listener);

    /**
     * M: initial resources for dual send button for mms dialog mode.<br/>
     * op09 Feature: CDMA/GSM-01-019 DualSendButton;
     * @param context
     * @param buttonWithCounter,  which contains the dual send button layout.
     * @param newTextCounter, which is the old original send button.
     */
    void initDualSendBtnForDialogMode(Context context, LinearLayout buttonWithCounter, TextView newTextCounter);

    /**
     * M: Hide the dual button from compose.<br/>
     * OP09Feature: CDMA/GSM-01-019 DualSendButton;
     */
    void hideDualButtonPanel();

    /**
     * M: Show the dual button for compose.<br/>
     * OP09Feature: CDMA/GSM-01-019 DualSendButton;
     */
    void showDualButtonPanel();

    /**
     * M: hide the dual send button and show the old button.<br/>
     * OP09Feature: CDMA/GSM-01-019 DualSendButton;
     */
    void hideDualButtonAndShowSrcButton();

    /**
     * M: set the draw picture for dual send button.<br/>
     * OP09Feature: CDMA/GSM-01-019 DualSendButton
     * @param dualBtnListener
     */
    void setDualSendButtonType(ViewOnClickListener dualBtnListener);

    /**
     * M: update the dual send button's status.<br/>
     * OP09Feature: CDMA/GSM-01-019 DualSendButton;
     * @param enable
     * @param isMms
     */
    void updateDualSendButtonStatue(boolean enable, boolean isMms);

    /**
     * M: update the text counter's content.<br/>
     * OP09Feature: CDMA/GSM-01-019 DualSendButton;
     * @param textLineCount , which is the content's line number.
     * @param isMms, which is mms or not.
     * @param remainingInCurrentMessage, which is the remaining number for the latest msg.
     * @param msgCount, which is the all count for the text content.
     */
    void updateNewTextCounter(int textLineCount, boolean isMms, int remainingInCurrentMessage, int msgCount);

    /**
    * M: set if show dual button panel.<br/>
    * @param ifShowDualButtonPanel show panel or not.
    */
    void setIsShowDualButtonPanel(boolean ifShowDualButtonPanel);

    /**
    * M: hide subject to show sharepanel when landscape and show CC texteditor.<br/>
    * @param screenIsFull  the situation to hide subject.
    * @param subjectTextEditor the subject to hide.
    */
    public void hideOrShowSubjectForCC(boolean screenIsFull, EditText subjectTextEditor);

    /**
    * M: set if show dual button panel.<br/>
    * @return if the subject hide.
    */
    public boolean getIsSubjectHide();
}

