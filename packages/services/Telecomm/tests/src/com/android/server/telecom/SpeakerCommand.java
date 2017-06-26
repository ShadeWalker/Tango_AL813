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
import android.telecom.AudioState;


public class SpeakerCommand extends DefaultCommand {

    protected int mNewMode = AudioState.ROUTE_SPEAKER;

    @Override
    protected int beforeExecute() {
        int retval = ICommand.RESULT_OK;
        if (!CallsManager.getInstance().hasActiveOrHoldingCall()) {
            retval = ICommand.RESULT_ABORT;
        }
        // if speaker is already on, change to wired/earpiece
        if (CallsManager.getInstance().getAudioState().route == AudioState.ROUTE_SPEAKER) {
            mNewMode = AudioState.ROUTE_WIRED_OR_EARPIECE;
        }
        log("isSpeakerOn " + (mNewMode == AudioState.ROUTE_SPEAKER) + "mNewMode = " + mNewMode);
        return retval;
    }

    @Override
    protected int executeInner(String content) {
        log("executeInner");
        final Instrumentation instrumentation = AutotestEngine.getInstance().getInstrumentation();
        CallsManager.getInstance().setAudioRoute(mNewMode);
        log("toggleSpeaker(): newSpeakerState = " + mNewMode);
        instrumentation.waitForIdleSync();
        return ICommand.RESULT_OK;
    }

    @Override
    protected int afterExecute() {
        int retval = ICommand.RESULT_FAIL;
        final int mode = CallsManager.getInstance().getAudioState().route;
        if (mNewMode == mode) {
            log("!!!afterExecute switch speaker failed!!!");
            retval = ICommand.RESULT_OK;
        }
        log("isSpeakerOn() " + (mNewMode == AudioState.ROUTE_SPEAKER));
        return retval;
    }

}
