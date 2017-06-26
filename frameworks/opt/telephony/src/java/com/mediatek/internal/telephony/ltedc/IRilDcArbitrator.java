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

/*
 *
 */

package com.mediatek.internal.telephony.ltedc;

import android.os.Message;

import com.android.internal.telephony.CommandsInterface;

/**
 * Bypass RIL Request/URC to different RIL according LTE dual connection strategy
 */
public interface IRilDcArbitrator {

    /**
     * Demo arbitrator API for reference.
     * @param address The address to dial
     * @param clirMode The clirMode
     * @param result Callback message
     */
    void dial(String address, int clirMode, Message result);

    /**
     * Hang up the call.
     * @param result Callback message
     */
    void hangupAll(Message result);

    /**
     * Bypass to PS ril, but will suspended if IRAT is ongoing.
     * @param radioTechnology Radio technology.
     * @param profile Profile
     * @param apn APN name.
     * @param user User account.
     * @param password User password.
     * @param authType Auth type.
     * @param protocol Protocol, IPv4 or IPv6.
     * @param interfaceId Interface ID.
     * @param result Response message.
     */
    void setupDataCall(String radioTechnology, String profile, String apn,
            String user, String password, String authType, String protocol,
            String interfaceId, Message result);

    /**
     * Bypass to PS ril, but will suspended if IRAT is ongoing.
     * @param cid Connection ID.
     * @param reason Reason to deactivate
     * @param result Response message.
     */
    void deactivateDataCall(int cid, int reason, Message result);

    /**
     * Tells the modem if data is allowed or not. Bypass to PS ril, but will
     * suspended if IRAT is ongoing.
     * @param allowed true = allowed, false = not alowed
     * @param result Callback message contains the information of
     *            SUCCESS/FAILURE.
     */
    void setDataAllowed(boolean allowed, Message result);

    /**
     * Bypass to PS ril, but will suspended if IRAT is ongoing.
     * @param result Response message.
     */
    void getLastDataCallFailCause(Message result);

    /**
     * Set initial attach APN, only set for LTE modem for dual connection project.
     * @param apn APN name.
     * @param protocol APN protocol.
     * @param authType APN authType.
     * @param username APN username.
     * @param password APN password.
     * @param operatorNumeric APN operatorNumeric.
     * @param canHandleIms Whether the initial attach APN can handle IMS.
     * @param result Response message.
     */
    void setInitialAttachApn(String apn, String protocol, int authType, String username,
            String password, String operatorNumeric, boolean canHandleIms, Message result);

    /**
     * Set PS active slot for Gemini LTE dual connection project, send
     * AT+EACTS=slotId and AT+CGATT=1, the request can only send by main protocol.
     * @param psSlot Slot to be used for data connection.
     * @param response A callback message with the String response in the obj field.
     */
    void requestSetPsActiveSlot(int psSlot, Message response);

    /**
     *  Bypass to PS ril, but will suspended if IRAT is ongoing.
     * @param result Response message.
     */
    void getDataCallList(Message result);

    /**
     * Bypass to PS ril, but will suspended if IRAT is ongoing.
     * @param result Response message.
     */
    void getDataRegistrationState(Message result);

    /**
     * Suspend data request when data rat is switching.
     */
    void suspendDataRilRequest();

    /**
     * Resume data request when data rat is finished.
     */
    void resumeDataRilRequest();

    /**
     * Update ril for PS request.
     * @param psRil ril for PS
     */
    void updatePsCi(CommandsInterface psRil);

    /**
     * Send TERMINAL RESPONSE to the SIM, after processing a proactive command
     * sent by the SIM.
     * @param contents The contents of response
     * @param response The callback message
     * @param cmdType The type of command
     * @param mMutliSimType Multi SIM type
     */
    void sendTerminalResponse(String contents, Message response, int cmdType, int mMutliSimType);

    /**
     * Send ENVELOPE to the SIM, after processing a proactive command sent by
     * the SIM.
     * @param contents The contents of response
     * @param response The callback message
     * @param cmdType The type of command
     * @param mMutliSimType Multi SIM type
     */
    void sendEnvelope(String contents, Message response, int cmdType, int mMutliSimType);
}
