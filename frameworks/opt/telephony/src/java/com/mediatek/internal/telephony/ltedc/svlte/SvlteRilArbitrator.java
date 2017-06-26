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

package com.mediatek.internal.telephony.ltedc.svlte;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.cdma.UtkInterface;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.PhoneBase;
// MTK_SWIP_C2K_START
//import com.android.internal.telephony.cdma.utk.UtkService;
// MTK_SWIP_C2K_END
import com.mediatek.internal.telephony.ltedc.DefaultRilDcArbitrator;

/**
 * For SVLTE, manage RIL Request/URC
 */
public class SvlteRilArbitrator extends DefaultRilDcArbitrator {

    private static final String LOG_TAG = "PHONE";
    private static final String PS_REQUEST_QUEUE_THREAD_NAME = "PsRequestQueueThread";
    private static final int MSG_SUSPEND_DATA_REQUEST = 0;

    private HandlerThread mPsRequestQueueThread;
    private Handler mPsRequestQueueHandler;
    private Handler mMainHandler;
    /**
     * Public constructor, pass two phone, one for LTE, one for GSM or CDMA.
     *
     * @param ltePhone The LTE Phone
     * @param nonLtePhone The non LTE Phone
     */
    public SvlteRilArbitrator(PhoneBase ltePhone, PhoneBase nonLtePhone) {
        super(ltePhone, nonLtePhone);
        log("RilDcArbitrator: lteRil = " + mLtePhone.mCi + ", non-lteRIL = "
                + mNonLtePhone.mCi);

        mPsRequestQueueThread = new HandlerThread(PS_REQUEST_QUEUE_THREAD_NAME);
        mPsRequestQueueThread.start();

        Looper looper = mPsRequestQueueThread.getLooper();
        mPsRequestQueueHandler = new Handler(looper) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SUSPEND_DATA_REQUEST:
                        suspendDataRequestInner();
                        break;
                    default:
                        break;
                }
            }
        };

        mMainHandler = new Handler();
    }

    @Override
    public void dial(String address, int clirMode, Message result) {
        mNonLtePhone.mCi.dial(address, clirMode, result);
    }

    @Override
    public void suspendDataRilRequest() {
        log("suspendDataRequest: mPsCi = " + mPsCi);
        // Send message to handler thread, so it will block itself until
        // notified by other threads(such as main thread).
        mPsRequestQueueHandler.removeMessages(MSG_SUSPEND_DATA_REQUEST);
        mPsRequestQueueHandler.sendEmptyMessage(MSG_SUSPEND_DATA_REQUEST);
        mSuspendDataRequest = true;
    }

    private void suspendDataRequestInner() {
        log("suspendDataRequestInner E: mPsCi = " + mPsCi);
        synchronized (mPsRequestQueueThread) {
            try {
                if (!mSuspendDataRequest) {
                    // happened when resumeDataRilRequest is called before here.
                    log("mSuspendDataRequest is false, ignore suspend.");
                    return;
                }
                mPsRequestQueueThread.wait();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
        log("suspendDataRequestInner X: mPsCi = " + mPsCi);
    }

    @Override
    public void resumeDataRilRequest() {
        log("resumeDataRequest: mPsCi = " + mPsCi);
        mSuspendDataRequest = false;
        synchronized (mPsRequestQueueThread) {
            mPsRequestQueueThread.notifyAll();
        }
    }

    @Override
    public void updatePsCi(CommandsInterface psCi) {
        log("updatePsCi: mPsCi = " + mPsCi + ", psCi = " + psCi);
        mPsCi = psCi;
    }

    @Override
    public void setupDataCall(final String radioTechnology,
            final String profile, final String apn, final String user,
            final String password, final String authType,
            final String protocol, final String interfaceId,
            final Message result) {
        log("setupDataCall: mSuspendDataRequest = " + mSuspendDataRequest
                + ",mPsCi = " + mPsCi + ", apn = " + apn);
        if (mSuspendDataRequest) {
            enqueuePsRequest(new Runnable() {
                @Override
                public void run() {
                    mPsCi.setupDataCall(radioTechnology, profile, apn, user,
                            password, authType, protocol, interfaceId, result);
                }
            });
        } else {
            mPsCi.setupDataCall(radioTechnology, profile, apn, user, password,
                    authType, protocol, interfaceId, result);
        }
    }

    @Override
    public void deactivateDataCall(final int cid, final int reason,
            final Message result) {
        log("deactivateDataCall: mSuspendDataRequest = " + mSuspendDataRequest
                + ",mPsCi = " + mPsCi + ", cid = " + cid);
        if (mSuspendDataRequest) {
            enqueuePsRequest(new Runnable() {
                @Override
                public void run() {
                    mPsCi.deactivateDataCall(cid, reason, result);
                }
            });
        } else {
            mPsCi.deactivateDataCall(cid, reason, result);
        }
    }

    @Override
    public void setDataAllowed(final boolean allowed, final Message result) {
        log("setDataAllowed: mSuspendDataRequest = " + mSuspendDataRequest
                + ",mPsCi = " + mPsCi + ", allowed = " + allowed);
        if (mSuspendDataRequest) {
            enqueuePsRequest(new Runnable() {
                @Override
                public void run() {
                    mPsCi.setDataAllowed(allowed, result);
                }
            });
        } else {
            mPsCi.setDataAllowed(allowed, result);
        }
    }

    @Override
    public void getLastDataCallFailCause(final Message result) {
        log("getLastDataCallFailCause: mSuspendDataRequest = "
                + mSuspendDataRequest + ", mPsCi = " + mPsCi);
        if (mSuspendDataRequest) {
            enqueuePsRequest(new Runnable() {
                @Override
                public void run() {
                    mPsCi.getLastDataCallFailCause(result);
                }
            });
        } else {
            mPsCi.getLastDataCallFailCause(result);
        }
    }

    @Override
    public void getDataCallList(final Message result) {
        log("getDataCallList: mSuspendDataRequest = " + mSuspendDataRequest
                + ", mPsCi = " + mPsCi);
        if (mSuspendDataRequest) {
            enqueuePsRequest(new Runnable() {
                @Override
                public void run() {
                    mPsCi.getDataCallList(result);
                }
            });
        } else {
            mPsCi.getDataCallList(result);
        }
    }

    @Override
    public void getDataRegistrationState(final Message result) {
        if (mSuspendDataRequest) {
            enqueuePsRequest(new Runnable() {
                @Override
                public void run() {
                    mPsCi.getDataRegistrationState(result);
                }
            });
        } else {
            mPsCi.getDataRegistrationState(result);
        }
    }

    private void enqueuePsRequest(final Runnable r) {
        mPsRequestQueueHandler.post(new Runnable() {
            @Override
            public void run() {
                mMainHandler.post(r);
            }
        });
    }

    @Override
    public void sendTerminalResponse(String contents, Message response,
            int cmdType, int mMutliSimType) {
        if (UtkInterface.UTK_CARD_TYPE_UIM_ONLY == mMutliSimType) {
            log("send terminal response through mNonLtePhone");
            mNonLtePhone.mCi.sendTerminalResponse(contents, response);
        } else if (UtkInterface.UTK_CARD_TYPE_UIM_AND_USIM == mMutliSimType) {
            if (UtkInterface.UTK_PS_TR == cmdType) {
                log("send terminal response through mLtePhone");
                mLtePhone.mCi.sendTerminalResponse(contents, response);
            } else {
                log("send terminal response through mNonLtePhone");
                mNonLtePhone.mCi.sendTerminalResponse(contents, response);
            }
        } else {
            log("Invalid multiSimType! ");
        }
    }

    @Override
    public void sendEnvelope(String contents, Message response, int cmdType,
            int mMutliSimType) {
        if (UtkInterface.UTK_CARD_TYPE_UIM_ONLY == mMutliSimType) {
            log("send envelope command through mNonLtePhone");
            mNonLtePhone.mCi.sendEnvelope(contents, response);
        } else if (UtkInterface.UTK_CARD_TYPE_UIM_AND_USIM == mMutliSimType) {
            if (UtkInterface.UTK_PS_ENV == cmdType) {
                log("send envelope command through mLtePhone");
                mLtePhone.mCi.sendEnvelope(contents, response);
            } else {
                log("send envelope command through mNonLtePhone");
                mNonLtePhone.mCi.sendEnvelope(contents, response);
            }
        } else {
            log("Invalid multiSimType! ");
        }
    }

    /**
     * To override log format, add RilDcArbitrator prefix.
     *
     * @param msg The log to print
     */
    public void log(String msg) {
        Rlog.i(LOG_TAG, "[" + "IRAT_SvlteRilArbitrator" + "] " + msg);
    }
}
