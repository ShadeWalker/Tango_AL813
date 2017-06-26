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
import android.app.Instrumentation;
import android.telecom.PhoneCapabilities;
import android.telephony.PreciseCallState;

public class SwapCommand extends BlockingCommand {

    @Override
    protected int beforeExecute() {
        super.beforeExecute();
        int retval = ICommand.RESULT_ABORT;
        Call activeCall = CallsManager.getInstance().getActiveCall();
        Call bgCall = CallsManager.getInstance().getHeldCall();
        if (activeCall != null && bgCall != null) {
            retval = ICommand.RESULT_OK;
        } else {
            log("!!!beforeExecute not ok to swap!!!");
        }
        return retval;
    }

    @Override
    protected int executeInner(String parameters) {
        log("executeInner");
        final Instrumentation instrumentation = AutotestEngine.getInstance().getInstrumentation();
        // Swap the fg and bg calls.
        // In the future we may provides some way for user to choose among
        // multiple background calls, for now, always act on the first background call.
        Call activeCall = CallsManager.getInstance().getActiveCall();
        if(activeCall.can(PhoneCapabilities.SWAP_CONFERENCE)){
            activeCall.swapConference();
        }
        instrumentation.waitForIdleSync();
        return ICommand.RESULT_OK;
    }

    @Override
    public void onPhoneStateChanged(PreciseCallState preciseCallState) {
        final int foregroundCallState = preciseCallState.getForegroundCallState();
        final int backgroundCallState = preciseCallState.getBackgroundCallState();
        log("fg call state : " + foregroundCallState + " bg call state : " + backgroundCallState);
        if (foregroundCallState == PreciseCallState.PRECISE_CALL_STATE_ACTIVE && backgroundCallState == PreciseCallState.PRECISE_CALL_STATE_HOLDING) {
            log("swap ok");
            notify(ICommand.RESULT_OK);
        }
    }

}
