/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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

package com.mediatek.internal.telephony.ltedc;

import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneBase;

/**
 * Default implementation, bypass all Request/Urc to non LtePhone.
 */
public class DefaultRilDcArbitrator implements IRilDcArbitrator {
    private static final String LOG_TAG = "PHONE";

    protected PhoneBase mNonLtePhone;
    protected PhoneBase mLtePhone;

    protected CommandsInterface mPsCi;
    protected CommandsInterface mCsCi;

    protected boolean mSuspendDataRequest;

    /**
     * Default implementation constructor.
     *
     * @param ltePhone The LTE Phone to use
     * @param nonLtePhone The Non LTE Phone to use
     */
    public DefaultRilDcArbitrator(PhoneBase ltePhone, PhoneBase nonLtePhone) {
        mLtePhone = ltePhone;
        mNonLtePhone = nonLtePhone;
    }

    @Override
    public void updatePsCi(CommandsInterface psRil) {
        log("updatePsRil: psRil = " + psRil);
        mPsCi = psRil;
    }

    @Override
    public void dial(String address, int clirMode, Message result) {
        mNonLtePhone.mCi.dial(address, clirMode, result);
    }

    @Override
    public void hangupAll(Message result) {
        mNonLtePhone.mCi.hangupAll(result);
    }

    @Override
    public void setupDataCall(String radioTechnology, String profile,
            String apn, String user, String password, String authType,
            String protocol, String interfaceId, Message result) {
        mPsCi.setupDataCall(radioTechnology, profile, apn, user, password,
                authType, protocol, interfaceId, result);
    }

    @Override
    public void deactivateDataCall(int cid, int reason, Message result) {
        mPsCi.deactivateDataCall(cid, reason, result);
    }

    @Override
    public void setDataAllowed(boolean allowed, Message result) {
        mPsCi.setDataAllowed(allowed, result);
    }

    @Override
    public void getLastDataCallFailCause(Message result) {
        mPsCi.getLastDataCallFailCause(result);
    }

    @Override
    public void requestSetPsActiveSlot(int psSlot, Message response) {
        // NOTE: Use PS RIL to set PS active slot, this API doesn't need to
        // suspend during IRAT and must call through protocal 1.
        mLtePhone.mCi.requestSetPsActiveSlot(psSlot, response);
    }

    @Override
    public void setInitialAttachApn(String apn, String protocol, int authType,
            String username, String password, String operatorNumeric,
            boolean canHandleIms, Message result) {
        mLtePhone.mCi.setInitialAttachApn(apn, protocol, authType, username,
                password, operatorNumeric, canHandleIms, result);
    }

    @Override
    public void getDataCallList(Message result) {
        mPsCi.getDataCallList(result);
    }

    @Override
    public void getDataRegistrationState(Message result) {
        mPsCi.getDataRegistrationState(result);
    }

    @Override
    public void suspendDataRilRequest() {
    }

    @Override
    public void resumeDataRilRequest() {
    }

    @Override
    public void sendTerminalResponse(String contents, Message response, int cmdType,
            int mMutliSimType) {
    }

    @Override
    public void sendEnvelope(String contents, Message response, int cmdType, int mMutliSimType) {
    }

    /**
     * Log information.
     * @param msg Information to log.
     */
    public void log(String msg) {
        Rlog.i(LOG_TAG, "[" + "RilDcArbitrator" + "] " + msg);
    }
}
