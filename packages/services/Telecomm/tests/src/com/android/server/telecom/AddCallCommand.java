/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

package com.android.server.telecom;

import android.content.Intent;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.PhoneCapabilities;
import android.telephony.PreciseCallState;

public class AddCallCommand extends BlockingCommand {

    // Use some public call to test, these calls can be connected and disconnect auto.
    // Tester should control the time of test case.
    // Experience: 10010 and 10011 can keep about 50s between Active and Disconnect.
    public static String FIRST_CALL_USING_SUB1 = "AddCall 10010 1";
    public static String FIRST_CALL_USING_SUB2 = "AddCall 10010 2";
    public static String SECOND_CALL_USING_SUB1 = "AddCall 10011 1";
    public static String SECOND_CALL_USING_SUB2 = "AddCall 10011 2";
    // Experience: 10086 some times can just keep 20s between Active and Disconnect.
    // Should be careful about the time when use 10086 to test.
    public static String THIRD_CALL_USING_SUB1 = "AddCall 10086 1";
    public static String THIRD_CALL_USING_SUB2 = "AddCall 10011 2";

    @Override
    protected int beforeExecute() {
        super.beforeExecute();
        boolean airOn = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
        log("beforeExecute airOn: " + airOn);
        if (airOn) {
            log("!!!beforeExecute  ariplane on!!!");
            return ICommand.RESULT_ABORT;
        }
        if (CallsManager.getInstance().getForegroundCall() != null) {
            if (CallsManager.getInstance().getForegroundCall().can(PhoneCapabilities.ADD_CALL)) {
                return ICommand.RESULT_OK;
            }
        } else {
            return ICommand.RESULT_OK;
        }
        log("beforeExecute can not add call");
        return ICommand.RESULT_ABORT;
    }

    @Override
    protected int executeInner(String content) {
        log("executeInner parameters: " + content);
        final String[] args = content.split(" ");
        final String phoneAccountId = args[1];
        PhoneAccountHandle handle = AutotestEngineUtils.getAccountById(mContext, phoneAccountId);
        log("phoneAccountId: " + phoneAccountId + " handle: " + handle);
        if(handle == null) {
            return ICommand.RESULT_ABORT;
        }
        final Intent intent = AutotestEngineUtils.getCallIntent(args[0], handle);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        return ICommand.RESULT_OK;
    }

    @Override
    public void onPhoneStateChanged(PreciseCallState preciseCallState) {
        int callState = preciseCallState.getForegroundCallState();
        if (callState == PreciseCallState.PRECISE_CALL_STATE_DIALING) {
            log("dialing...");
        } else if (callState == PreciseCallState.PRECISE_CALL_STATE_ALERTING) {
            log("alerting...");
        } else if (callState == PreciseCallState.PRECISE_CALL_STATE_ACTIVE) {
            log("active...");
            notify(ICommand.RESULT_OK);
        }
    }
}
