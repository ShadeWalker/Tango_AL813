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

import android.telephony.PreciseCallState;

public class EndCommand extends BlockingCommand {

    public static final int END_ACTIVE = 1;
    public static final int END_HOLDING = 2;
    public static final int END_ALL = 3;

    protected int mEndType = END_ACTIVE;
    protected boolean mDisconnected;
    @Override
    protected int beforeExecute() {
        super.beforeExecute();
        return ICommand.RESULT_OK;
    }

    @Override
    protected int executeInner(String content) {
        String[] args = content.split(" ");
        mEndType = Integer.parseInt(args[0]);
        log("executeInner mEndType = " + mEndType);
        switch (mEndType) {
            case END_ACTIVE:
                endActive();
                break;
            case END_HOLDING:
                endHolding();
                break;
            case END_ALL:
                endAll();
            default:
                break;
        }
        AutotestEngine.getInstance().getInstrumentation().waitForIdleSync();
        return ICommand.RESULT_OK;
    }

    @Override
    public void onPhoneStateChanged(PreciseCallState preciseCallState) {
        final int IDEL_STATE = PreciseCallState.PRECISE_CALL_STATE_IDLE;
        final int ringCallState = preciseCallState.getRingingCallState();
        final int foregroundCallState = preciseCallState.getForegroundCallState();
        final int backgroundCallState = preciseCallState.getBackgroundCallState();
        log("ringCall State" + ringCallState + "fgCall State = " + foregroundCallState
                + " bgCall State = " + backgroundCallState);
        switch (mEndType) {
            case END_ACTIVE:
                if (backgroundCallState == IDEL_STATE) {
                    notify(ICommand.RESULT_OK);
                }
                break;
            case END_HOLDING:
                if (backgroundCallState == IDEL_STATE) {
                    notify(ICommand.RESULT_OK);
                }
                break;
            case END_ALL:
                if (backgroundCallState == IDEL_STATE && foregroundCallState == IDEL_STATE) {
                    notify(ICommand.RESULT_OK);
                }
                break;
            default:
                break;
        }
    }

    protected void endActive() {
        log("endActive");
        Call fgCall = CallsManager.getInstance().getActiveCall();
        if (fgCall != null) {
            fgCall.disconnect();
        }
    }

    protected void endHolding() {
        log("endHolding");
        Call bgCall = CallsManager.getInstance().getHeldCall();
        if (bgCall != null) {
            bgCall.disconnect();
        }
    }

    private void endAll() {
        Call fgCall = CallsManager.getInstance().getActiveCall();
        Call bgCall = CallsManager.getInstance().getHeldCall();
        log("endAll" + " fgCall " + fgCall + " bgCall " + bgCall);
        if (fgCall != null) {
            fgCall.disconnect();
        }
        if (bgCall != null) {
            bgCall.disconnect();
        }
    }

}
