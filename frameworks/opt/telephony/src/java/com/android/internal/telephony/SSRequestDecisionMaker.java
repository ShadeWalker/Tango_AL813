/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.android.internal.telephony;


import static com.android.internal.telephony.CommandsInterface.CF_ACTION_DISABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_REGISTRATION;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_UT_CFU_NOTIFICATION_MODE;
import static com.android.internal.telephony.TelephonyProperties.TERMINAL_BASED_CALL_WAITING_DISABLED;
import static com.android.internal.telephony.TelephonyProperties.UT_CFU_NOTIFICATION_MODE_OFF;
import static com.android.internal.telephony.TelephonyProperties.UT_CFU_NOTIFICATION_MODE_ON;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.gsm.GSMPhone;
import com.mediatek.simservs.xcap.XcapException;

import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * {@hide}
 */
public class SSRequestDecisionMaker {
    static final String LOG_TAG = "SSDecisonMaker";

    private static final int SS_REQUEST_GET_CALL_FORWARD = 1;
    private static final int SS_REQUEST_SET_CALL_FORWARD = 2;
    private static final int SS_REQUEST_GET_CALL_BARRING = 3;
    private static final int SS_REQUEST_SET_CALL_BARRING = 4;
    private static final int SS_REQUEST_GET_CALL_WAITING = 5;
    private static final int SS_REQUEST_SET_CALL_WAITING = 6;
    private static final int SS_REQUEST_GET_CLIR = 7;
    private static final int SS_REQUEST_SET_CLIR = 8;
    private static final int SS_REQUEST_GET_CLIP = 9;
    private static final int SS_REQUEST_SET_CLIP = 10;
    private static final int SS_REQUEST_GET_COLR = 11;
    private static final int SS_REQUEST_SET_COLR = 12;
    private static final int SS_REQUEST_GET_COLP = 13;
    private static final int SS_REQUEST_SET_COLP = 14;
    private static final int SS_REQUEST_GET_CALL_FORWARD_TIME_SLOT = 15;
    private static final int SS_REQUEST_SET_CALL_FORWARD_TIME_SLOT = 16;

    private static final int EVENT_SS_SEND = 1;
    private static final int EVENT_SS_RESPONSE = 2;
    private static final int EVENT_SS_CLEAR_TEMP_VOLTE_USER_FLAG = 3;

    private static final int SS_ERROR_UT_USER_UNKNOWN = 403;
    private static final int SS_ERROR_UT_NOT_FOUND = 404;
    private static final int SS_ERROR_UT_CONFLICT = 409;

    private static final int CLEAR_DELAY_TIMEOUT = 10 * 1000;

    

    private PhoneBase mPhone;
    private CommandsInterface mCi;
    private int mPhoneId;
    private Context mContext;
    private HandlerThread mSSHandlerThread;
    private SSRequestHandler mSSRequestHandler;
    private MMTelSSTransport mMMTelSSTSL;
    private boolean mIsTempVolteUser;

    public SSRequestDecisionMaker(Context context, Phone phone) {
        mContext = context;
        mPhone = (PhoneBase) phone;
        mCi = mPhone.mCi;
        mPhoneId = phone.getPhoneId();

        mSSHandlerThread = new HandlerThread("SSRequestHandler");
        mSSHandlerThread.start();
        Looper looper = mSSHandlerThread.getLooper();
        mSSRequestHandler = new SSRequestHandler(looper);

        mMMTelSSTSL = MMTelSSTransport.getInstance();
        // Sijia: Need to modify for MSIM
        mMMTelSSTSL.registerPhone(phone, context, phone.getPhoneId());
    }

    class SSRequestHandler extends Handler implements Runnable {

        public SSRequestHandler(Looper looper) {
            super(looper);
        }

        //***** Runnable implementation
        public void
            run() {
            //setup if needed
        }

        @Override
        public void handleMessage(Message msg) {
            if (!mPhone.mIsTheCurrentActivePhone) {
                Rlog.e(LOG_TAG, "SSRequestDecisionMaker-Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
                return;
            }

            switch (msg.what) {
                case EVENT_SS_SEND:
                    processSendRequest(msg.obj);
                    break;
                case EVENT_SS_RESPONSE:
                    processResponse(msg.obj);
                    break;
                case EVENT_SS_CLEAR_TEMP_VOLTE_USER_FLAG:
                    mIsTempVolteUser = false;
                    break;
                default:
                    Rlog.d(LOG_TAG, "SSRequestDecisionMaker:msg.what=" + msg.what);
            }
        }
    }

    private void processSendRequest(Object obj) {
        Message resp = null;
        ArrayList <Object> ssParmList = (ArrayList <Object>) obj;
        Integer request = (Integer) ssParmList.get(0);
        Message utResp = mSSRequestHandler.obtainMessage(EVENT_SS_RESPONSE,
                ssParmList);
        Rlog.d(LOG_TAG, "processSendRequest, request = " + request);

        switch (request.intValue()) {
            case SS_REQUEST_GET_CALL_FORWARD: {
                int cfReason = ((Integer) ssParmList.get(1)).intValue();
                int serviceClass = ((Integer) ssParmList.get(2)).intValue();
                String number = (String) ssParmList.get(3);
                resp = (Message) ssParmList.get(4);

                mMMTelSSTSL.queryCallForwardStatus(cfReason,
                        serviceClass, number, utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_SET_CALL_FORWARD: {
                int action = ((Integer) ssParmList.get(1)).intValue();
                int cfReason = ((Integer) ssParmList.get(2)).intValue();
                int serviceClass = ((Integer) ssParmList.get(3)).intValue();
                String number = (String) ssParmList.get(4);
                int timeSeconds = ((Integer) ssParmList.get(5)).intValue();
                resp = (Message) ssParmList.get(6);

                mMMTelSSTSL.setCallForward(action, cfReason, serviceClass,
                        number, timeSeconds, utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_GET_CALL_FORWARD_TIME_SLOT: {
                int cfReason = ((Integer) ssParmList.get(1)).intValue();
                int serviceClass = ((Integer) ssParmList.get(2)).intValue();
                resp = (Message) ssParmList.get(3);

                mMMTelSSTSL.queryCallForwardInTimeSlotStatus(cfReason, serviceClass, utResp,
                        mPhoneId);
                break;
            }
            case SS_REQUEST_SET_CALL_FORWARD_TIME_SLOT: {
                int action = ((Integer) ssParmList.get(1)).intValue();
                int cfReason = ((Integer) ssParmList.get(2)).intValue();
                int serviceClass = ((Integer) ssParmList.get(3)).intValue();
                String number = (String) ssParmList.get(4);
                int timeSeconds = ((Integer) ssParmList.get(5)).intValue();
                long[] timeSlot = (long[]) ssParmList.get(6);
                resp = (Message) ssParmList.get(7);

                mMMTelSSTSL.setCallForwardInTimeSlot(action, cfReason, serviceClass, number,
                        timeSeconds, timeSlot, utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_GET_CALL_BARRING: {
                String facility = (String) ssParmList.get(1);
                String password = (String) ssParmList.get(2);
                int serviceClass = ((Integer) ssParmList.get(3)).intValue();
                resp = (Message) ssParmList.get(4);

                if (MMTelSSUtils.isOp01IccCard(mPhoneId) &&
                        MMTelSSUtils.isOutgoingCallBarring(facility)) {
                    if (mIsTempVolteUser) {
                        if (resp != null) {
                            AsyncResult.forMessage(resp, null, new CommandException(
                                    CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED));
                            resp.sendToTarget();
                        }
                        return;
                    } else {
                        facility = CommandsInterface.CB_FACILITY_BAIC;
                    }
                }
                mMMTelSSTSL.queryFacilityLock(facility, password, serviceClass, utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_SET_CALL_BARRING: {
                String facility = (String) ssParmList.get(1);
                boolean lockState = ((Boolean) ssParmList.get(2)).booleanValue();
                String password = (String) ssParmList.get(3);
                int serviceClass = ((Integer) ssParmList.get(4)).intValue();
                resp = (Message) ssParmList.get(5);
                mMMTelSSTSL.setFacilityLock(facility, lockState, password, serviceClass, utResp,
                        mPhoneId);
                break;
            }
            case SS_REQUEST_GET_CALL_WAITING: {
                int serviceClass = ((Integer) ssParmList.get(1)).intValue();
                resp = (Message) ssParmList.get(2);
                mMMTelSSTSL.queryCallWaiting(serviceClass, utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_SET_CALL_WAITING: {
                boolean enable = ((Boolean) ssParmList.get(1)).booleanValue();
                int serviceClass = ((Integer) ssParmList.get(2)).intValue();
                resp = (Message) ssParmList.get(3);
                // query call waiting to determine VoLTE user
                //mMMTelSSTSL.setCallWaiting(enable, serviceClass, utResp, mPhoneId);
                mMMTelSSTSL.queryCallWaiting(serviceClass, utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_GET_CLIR: {
                resp = (Message) ssParmList.get(1);
                mMMTelSSTSL.getCLIR(utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_SET_CLIR: {
                int mode = ((Integer) ssParmList.get(1)).intValue();
                resp = (Message) ssParmList.get(2);
                mMMTelSSTSL.setCLIR(mode, utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_GET_CLIP: {
                resp = (Message) ssParmList.get(1);
                mMMTelSSTSL.queryCLIP(utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_SET_CLIP: {
                int mode = ((Integer) ssParmList.get(1)).intValue();
                resp = (Message) ssParmList.get(2);
                mMMTelSSTSL.setCLIP(mode, utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_GET_COLR: {
                resp = (Message) ssParmList.get(1);
                mMMTelSSTSL.getCOLR(utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_SET_COLR: {
                int mode = ((Integer) ssParmList.get(1)).intValue();
                resp = (Message) ssParmList.get(2);
                mMMTelSSTSL.setCOLR(mode, utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_GET_COLP: {
                resp = (Message) ssParmList.get(1);
                mMMTelSSTSL.getCOLP(utResp, mPhoneId);
                break;
            }
            case SS_REQUEST_SET_COLP: {
                int mode = ((Integer) ssParmList.get(1)).intValue();
                resp = (Message) ssParmList.get(2);
                mMMTelSSTSL.setCOLP(mode, utResp, mPhoneId);
                break;
            }
            default:
                break;
        }
    }

    private void processResponse(Object obj) {
        Message resp = null;
        AsyncResult ar = (AsyncResult) obj;
        Object arResult = ar.result;
        Throwable arException = ar.exception;
        ArrayList <Object> ssParmList = (ArrayList <Object>) (ar.userObj);
        Integer request = (Integer) ssParmList.get(0);
        Rlog.d(LOG_TAG, "processResponse, request = " + request);

        switch (request.intValue()) {
            case SS_REQUEST_GET_CALL_FORWARD: {
                int cfReason = ((Integer) ssParmList.get(1)).intValue();
                int serviceClass = ((Integer) ssParmList.get(2)).intValue();
                String number = (String) ssParmList.get(3);
                resp = (Message) ssParmList.get(4);

                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.queryCallForwardStatus(cfReason, serviceClass, number, resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.queryCallForwardStatus(cfReason, serviceClass, number, resp);
                    return;
                }
                break;
            }
            case SS_REQUEST_SET_CALL_FORWARD: {
                int action = ((Integer) ssParmList.get(1)).intValue();
                int cfReason = ((Integer) ssParmList.get(2)).intValue();
                int serviceClass = ((Integer) ssParmList.get(3)).intValue();
                String number = (String) ssParmList.get(4);
                int timeSeconds = ((Integer) ssParmList.get(5)).intValue();
                resp = (Message) ssParmList.get(6);

                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.setCallForward(action, cfReason, serviceClass,
                                number, timeSeconds, resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.setCallForward(action, cfReason, serviceClass, number, timeSeconds, resp);
                    return;
                }

                if (ar.exception == null) {

                    if (MMTelSSUtils.isOp05IccCard(mPhoneId)
                        && cfReason == CF_REASON_UNCONDITIONAL) {
                        CallForwardInfo[] cfInfo = null;
                        if (arResult == null) {
                            Rlog.i(LOG_TAG, "arResult is null.");
                        } else {
                            if (arResult instanceof CallForwardInfo[]) {
                                cfInfo = (CallForwardInfo[]) arResult;
                            }
                        }
                        if (cfInfo == null || cfInfo.length == 0) {
                            Rlog.i(LOG_TAG, "cfInfo is null or length is 0.");
                        } else {
                            for (int i = 0; i < cfInfo.length ; i++) {
                                if ((CommandsInterface.SERVICE_CLASS_VOICE
                                    & cfInfo[i].serviceClass) != 0) {
                                    if (cfInfo[i].status == 0) {
                                        Rlog.i(LOG_TAG, "Set CF_DISABLE, serviceClass: "
                                            + cfInfo[i].serviceClass);
                                        action = CF_ACTION_DISABLE;
                                    } else {
                                        Rlog.i(LOG_TAG, "Set CF_ENABLE, serviceClass: "
                                            + cfInfo[i].serviceClass);
                                        action = CF_ACTION_ENABLE;
                                    }
                                    break;
                                }
                            }
                        }
                    }


                    if (cfReason == CF_REASON_UNCONDITIONAL) {
                        if ((action == CF_ACTION_ENABLE) || (action == CF_ACTION_REGISTRATION)) {
                            mPhone.setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                    UT_CFU_NOTIFICATION_MODE_ON);
                        } else {
                            mPhone.setSystemProperty(PROPERTY_UT_CFU_NOTIFICATION_MODE,
                                    UT_CFU_NOTIFICATION_MODE_OFF);
                        }
                    }
                }
                break;
            }
            case SS_REQUEST_GET_CALL_FORWARD_TIME_SLOT: {
                resp = (Message) ssParmList.get(3);

                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        arException = new CommandException(
                                CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                        arResult = null;
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    if (resp != null) {
                        AsyncResult.forMessage(resp, null,
                                new CommandException(
                                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED));
                        resp.sendToTarget();
                    }
                    return;
                }
                break;
            }
            case SS_REQUEST_SET_CALL_FORWARD_TIME_SLOT: {
                resp = (Message) ssParmList.get(7);

                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        arException = new CommandException(
                                CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                        arResult = null;
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    if (resp != null) {
                        AsyncResult.forMessage(resp, null,
                                new CommandException(
                                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED));
                        resp.sendToTarget();
                    }
                    return;
                }
                break;
            }
            case SS_REQUEST_GET_CALL_BARRING: {
                String facility = (String) ssParmList.get(1);
                String password = (String) ssParmList.get(2);
                int serviceClass = ((Integer) ssParmList.get(3)).intValue();
                resp = (Message) ssParmList.get(4);

                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.queryFacilityLock(facility,
                                password, serviceClass, resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    } else if (xcapException.getHttpErrorCode() == SS_ERROR_UT_NOT_FOUND) {   
                        //Transfer here because it only consider for CB case.
                        if (MMTelSSUtils.isOp05IccCard(mPhoneId)) {
                            Rlog.d(LOG_TAG, "processResponse, xcapException.httpErrorCode = " +
                                ((XcapException) arException).getHttpErrorCode());
                            arException = new CommandException(CommandException.Error.UT_XCAP_404_NOT_FOUND);
                        }
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.queryFacilityLock(facility, password, serviceClass, resp);
                    return;
                }

                if (MMTelSSUtils.isOp01IccCard(mPhoneId) &&
                        MMTelSSUtils.isOutgoingCallBarring(facility)) {
                    arException = new CommandException(
                            CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                    arResult = null;
                    mIsTempVolteUser = true;
                    Message msg;
                    msg = mSSRequestHandler.obtainMessage(EVENT_SS_CLEAR_TEMP_VOLTE_USER_FLAG);
                    mSSRequestHandler.sendMessageDelayed(msg, CLEAR_DELAY_TIMEOUT);
                }
                break;
            }
            case SS_REQUEST_SET_CALL_BARRING: {
                String facility = (String) ssParmList.get(1);
                boolean lockState = ((Boolean) ssParmList.get(2)).booleanValue();
                String password = (String) ssParmList.get(3);
                int serviceClass = ((Integer) ssParmList.get(4)).intValue();
                resp = (Message) ssParmList.get(5);

                if (MMTelSSUtils.isOutgoingCallBarring(facility)) {
                    arException = new CommandException(
                            CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                    arResult = null;
                }

                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.setFacilityLock(facility, lockState,
                                password, serviceClass, resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    } else if (xcapException.getHttpErrorCode() == SS_ERROR_UT_NOT_FOUND) {   
                        //Transfer here because it only consider for CB case.
                        if (MMTelSSUtils.isOp05IccCard(mPhoneId)) {
                            Rlog.d(LOG_TAG, "processResponse, xcapException.httpErrorCode = " +
                                ((XcapException) arException).getHttpErrorCode());
                            arException = new CommandException(CommandException.Error.UT_XCAP_404_NOT_FOUND);
                        }
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.setFacilityLock(facility, lockState, password, serviceClass, resp);
                    return;
                }
                break;
            }
            case SS_REQUEST_GET_CALL_WAITING: {
                boolean queryVolteUser = false;
                if (mPhone instanceof GSMPhone) {
                    GSMPhone gsmPhone = (GSMPhone) mPhone;
                    if (gsmPhone.getTbcwMode() == GSMPhone.TBCW_UNKNOWN) {
                        queryVolteUser = true;
                    }
                }

                if (queryVolteUser) {
                    GSMPhone gsmPhone = (GSMPhone) mPhone;
                    Integer reqCode = (Integer) ssParmList.get(0);
                    int serviceClass;
                    boolean enable = false;
                    if (reqCode.intValue() == SS_REQUEST_GET_CALL_WAITING) {
                        serviceClass = ((Integer) ssParmList.get(1)).intValue();
                        resp = (Message) ssParmList.get(2);
                    } else {
                        enable = ((Boolean) ssParmList.get(1)).booleanValue();
                        serviceClass = ((Integer) ssParmList.get(2)).intValue();
                        resp = (Message) ssParmList.get(3);
                    }

                    XcapException xcapException = null;
                    if (ar.exception != null && ar.exception instanceof XcapException) {
                        xcapException = (XcapException) ar.exception;
                    }

                    if (ar.exception == null) {
                        gsmPhone.setTbcwMode(GSMPhone.TBCW_OPTBCW_VOLTE_USER);
                        gsmPhone.setTbcwToEnabledOnIfDisabled();
                        if (reqCode.intValue() == SS_REQUEST_GET_CALL_WAITING) {
                            gsmPhone.getTerminalBasedCallWaiting(resp);
                        } else {
                            gsmPhone.setTerminalBasedCallWaiting(enable, resp);
                        }
                    } else if (xcapException != null
                            && xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        gsmPhone.setTbcwMode(GSMPhone.TBCW_OPTBCW_NOT_VOLTE_USER);
                        gsmPhone.setSystemProperty(PROPERTY_TERMINAL_BASED_CALL_WAITING_MODE,
                                TERMINAL_BASED_CALL_WAITING_DISABLED);
                        if (reqCode.intValue() == SS_REQUEST_GET_CALL_WAITING) {
                            mCi.queryCallWaiting(serviceClass, resp);
                        } else {
                            mCi.setCallWaiting(enable, serviceClass, resp);
                        }
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                    } else if (ar.exception != null
                            && ar.exception instanceof UnknownHostException) {
                        if (reqCode.intValue() == SS_REQUEST_GET_CALL_WAITING) {
                            mCi.queryCallWaiting(serviceClass, resp);
                        } else {
                            mCi.setCallWaiting(enable, serviceClass, resp);
                        }
                    } else {
                        if (reqCode.intValue() == SS_REQUEST_GET_CALL_WAITING) {
                            gsmPhone.getTerminalBasedCallWaiting(resp);
                        } else {
                            gsmPhone.setTerminalBasedCallWaiting(enable, resp);
                        }
                    }
                    return;
                } else {
                    int serviceClass = ((Integer) ssParmList.get(1)).intValue();
                    resp = (Message) ssParmList.get(2);

                    if (ar.exception != null && ar.exception instanceof XcapException) {
                        XcapException xcapException = (XcapException) ar.exception;
                        if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                            mCi.queryCallWaiting(serviceClass, resp);
                            mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                            return;
                        }
                    } else if (ar.exception != null
                            && ar.exception instanceof UnknownHostException) {
                        mCi.queryCallWaiting(serviceClass, resp);
                        return;
                    }
                }
                // Todo
                break;
            }
            case SS_REQUEST_SET_CALL_WAITING: {
                boolean enable = ((Boolean) ssParmList.get(1)).booleanValue();
                int serviceClass = ((Integer) ssParmList.get(2)).intValue();
                resp = (Message) ssParmList.get(3);
                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.setCallWaiting(enable, serviceClass, resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.setCallWaiting(enable, serviceClass, resp);
                    return;
                }
                // Todo
                break;
            }
            case SS_REQUEST_GET_CLIR: {
                resp = (Message) ssParmList.get(1);

                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.getCLIR(resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.getCLIR(resp);
                    return;
                }
                arException = new CommandException(
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                arResult = null;
                break;
            }
            case SS_REQUEST_SET_CLIR: {
                int mode = ((Integer) ssParmList.get(1)).intValue();
                resp = (Message) ssParmList.get(2);

                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.setCLIR(mode, resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.setCLIR(mode, resp);
                    return;
                }
                arException = new CommandException(
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                arResult = null;
                break;
            }
            case SS_REQUEST_GET_CLIP: {
                resp = (Message) ssParmList.get(1);
                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.queryCLIP(resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.queryCLIP(resp);
                    return;
                }
                arException = new CommandException(
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                arResult = null;
                break;
            }
            case SS_REQUEST_SET_CLIP: {
                int mode = ((Integer) ssParmList.get(1)).intValue();
                boolean modeCs = (mode == 0) ? false : true;
                resp = (Message) ssParmList.get(2);
                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.setCLIP(modeCs, resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.setCLIP(modeCs, resp);
                    return;
                }
                arException = new CommandException(
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                arResult = null;
                break;
            }
            case SS_REQUEST_GET_COLR: {
                resp = (Message) ssParmList.get(1);
                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.getCOLR(resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.getCOLR(resp);
                    return;
                }
                arException = new CommandException(
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                arResult = null;
                break;
            }
            case SS_REQUEST_SET_COLR: {
                arException = new CommandException(
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                arResult = null;
                break;
            }
            case SS_REQUEST_GET_COLP: {
                resp = (Message) ssParmList.get(1);
                if (ar.exception != null && ar.exception instanceof XcapException) {
                    XcapException xcapException = (XcapException) ar.exception;
                    if (xcapException.getHttpErrorCode() == SS_ERROR_UT_USER_UNKNOWN) {
                        mCi.getCOLP(resp);
                        mPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                        return;
                    }
                } else if (ar.exception != null && ar.exception instanceof UnknownHostException) {
                    mCi.getCOLP(resp);
                    return;
                }
                arException = new CommandException(
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                arResult = null;
                break;
            }
            case SS_REQUEST_SET_COLP: {
                arException = new CommandException(
                        CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED);
                arResult = null;
                break;
            }
            default:
                break;
        }

        if (arException != null && arException instanceof XcapException) {
            Rlog.d(LOG_TAG, "processResponse, xcapException.httpErrorCode = " +
                    ((XcapException) arException).getHttpErrorCode());
            //arException = new CommandException(CommandException.Error.GENERIC_FAILURE);
            arException = getCommandException((XcapException) arException);
        }

        if (resp != null) {
            AsyncResult.forMessage(resp, arResult, arException);
            resp.sendToTarget();
        }
    }

    private CommandException getCommandException(XcapException xcapException) {
        switch(xcapException.getHttpErrorCode()) {
            case SS_ERROR_UT_CONFLICT:
                if (MMTelSSUtils.isEnableXcapHttpResponse409(mPhoneId)) {
                    Rlog.d(LOG_TAG, "getCommandException UT_XCAP_409_CONFLICT");
                    return new CommandException(CommandException.Error.UT_XCAP_409_CONFLICT);
                }
                break;
            default:
                Rlog.d(LOG_TAG, "getCommandException GENERIC_FAILURE");
                return new CommandException(CommandException.Error.GENERIC_FAILURE);
        }
        Rlog.d(LOG_TAG, "getCommandException GENERIC_FAILURE");
        return new CommandException(CommandException.Error.GENERIC_FAILURE);
    }

    public void queryCallForwardStatus(int cfReason, int serviceClass,
            String number, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_GET_CALL_FORWARD));
        ssParmList.add(new Integer(cfReason));
        ssParmList.add(new Integer(serviceClass));
        ssParmList.add(number);
        ssParmList.add(response);
        send(ssParmList);
    }

    public void setCallForward(int action, int cfReason, int serviceClass,
            String number, int timeSeconds, Message response) {

        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_SET_CALL_FORWARD));
        ssParmList.add(new Integer(action));
        ssParmList.add(new Integer(cfReason));
        ssParmList.add(new Integer(serviceClass));
        ssParmList.add(number);
        ssParmList.add(new Integer(timeSeconds));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void queryCallForwardInTimeSlotStatus(int cfReason,
            int serviceClass, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_GET_CALL_FORWARD_TIME_SLOT));
        ssParmList.add(new Integer(cfReason));
        ssParmList.add(new Integer(serviceClass));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void setCallForwardInTimeSlot(int action, int cfReason, int serviceClass,
            String number, int timeSeconds, long[] timeSlot, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_SET_CALL_FORWARD_TIME_SLOT));
        ssParmList.add(new Integer(action));
        ssParmList.add(new Integer(cfReason));
        ssParmList.add(new Integer(serviceClass));
        ssParmList.add(number);
        ssParmList.add(new Integer(timeSeconds));
        ssParmList.add(timeSlot);
        ssParmList.add(response);
        send(ssParmList);
    }

    public void queryFacilityLock(String facility, String password,
            int serviceClass, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_GET_CALL_BARRING));
        ssParmList.add(facility);
        ssParmList.add(password);
        ssParmList.add(new Integer(serviceClass));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void setFacilityLock(String facility, boolean lockState,
            String password, int serviceClass, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_SET_CALL_BARRING));
        ssParmList.add(facility);
        ssParmList.add(new Boolean(lockState));
        ssParmList.add(password);
        ssParmList.add(new Integer(serviceClass));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void queryCallWaiting(int serviceClass, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_GET_CALL_WAITING));
        ssParmList.add(new Integer(serviceClass));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void setCallWaiting(boolean enable, int serviceClass, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_SET_CALL_WAITING));
        ssParmList.add(new Boolean(enable));
        ssParmList.add(new Integer(serviceClass));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void getCLIR(Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_GET_CLIR));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void setCLIR(int clirMode, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_SET_CLIR));
        ssParmList.add(new Integer(clirMode));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void getCLIP(Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_GET_CLIP));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void setCLIP(int clipMode, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_SET_CLIP));
        ssParmList.add(new Integer(clipMode));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void getCOLR(Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_GET_COLR));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void setCOLR(int colrMode, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_SET_COLR));
        ssParmList.add(new Integer(colrMode));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void getCOLP(Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_GET_COLP));
        ssParmList.add(response);
        send(ssParmList);
    }

    public void setCOLP(int colpMode, Message response) {
        ArrayList<Object> ssParmList = new ArrayList<Object>();
        ssParmList.add(new Integer(SS_REQUEST_SET_COLP));
        ssParmList.add(new Integer(colpMode));
        ssParmList.add(response);
        send(ssParmList);
    }

    void send(ArrayList<Object> ssParmList) {
        Message msg;
        msg = mSSRequestHandler.obtainMessage(EVENT_SS_SEND, ssParmList);
        msg.sendToTarget();
    }
}

