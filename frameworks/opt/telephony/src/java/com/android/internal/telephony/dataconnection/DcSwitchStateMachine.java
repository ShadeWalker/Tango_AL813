/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.dataconnection;

import com.android.internal.util.AsyncChannel;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.internal.telephony.dataconnection.DcSwitchAsyncChannel.RequestInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SubscriptionController;

import android.os.Message;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;

import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvltePhoneProxy;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;

public class DcSwitchStateMachine extends StateMachine {
    private static final boolean DBG = true;
    private static final boolean VDBG = false;
    private static final String LOG_TAG = "DcSwitchSM";

    // ***** Event codes for driving the state machine
    private static final int BASE = Protocol.BASE_DATA_CONNECTION_TRACKER + 0x00001000;
    private static final int EVENT_CONNECTED = BASE + 0;
    private static final int EVENT_DATA_DETACH_DONE = BASE + 1;

    private int mId;
    private Phone mPhone;
    private AsyncChannel mAc;

    private IdleState mIdleState = new IdleState();
    private AttachingState mAttachingState = new AttachingState();
    private AttachedState mAttachedState = new AttachedState();
    private DetachingState mDetachingState = new DetachingState();
    private PreDetachCheckState mPreDetachCheckState = new PreDetachCheckState();
    private DefaultState mDefaultState = new DefaultState();

    //MTK START
    private boolean mNeedAttach = true;
    private boolean mIsAttached = false;
    private String mReason = "";
    public static final String DCSTATE_IDLE = "idle";
    public static final String DCSTATE_ATTACHING = "attaching";
    public static final String DCSTATE_ATTACHED = "attached";
    public static final String DCSTATE_PREDETACH_CHECK = "predetachcheck";
    public static final String DCSTATE_DETACHING = "detaching";

    //[C2K] SvLte DSDS
    private static final int SIM_ID_NONE = -1;

    //MTK END

    protected DcSwitchStateMachine(Phone phone, String name, int id) {
        super(name);
        if (DBG) log("DcSwitchState constructor E");
        mPhone = phone;
        mId = id;
        mIsAttached = false;

        addState(mDefaultState);
        addState(mIdleState, mDefaultState);
        addState(mAttachingState, mDefaultState);
        addState(mAttachedState, mDefaultState);
        addState(mPreDetachCheckState, mDefaultState);
        addState(mDetachingState, mDefaultState);
        setInitialState(mIdleState);
        if (DBG) log("DcSwitchState constructor X");
    }

//    public void notifyDataConnection(int phoneId, String state, String reason,
//            String apnName, String apnType, boolean unavailable) {
//        if (phoneId == mId &&
//                TextUtils.equals(state, PhoneConstants.DataState.CONNECTED.toString())) {
//            sendMessage(obtainMessage(EVENT_CONNECTED));
//        }
//    }

    private class IdleState extends State {
        @Override
        public void enter() {
            if (DBG) log("IdleState: enter");

            try {
                DctController.getInstance().processRequests();
                DctController.getInstance().notifyDcSwitchStateChange(DCSTATE_IDLE, mId, mReason);
            } catch (RuntimeException e) {
                if (DBG) loge("DctController is not ready");
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            boolean retVal;

            switch (msg.what) {
                case DcSwitchAsyncChannel.REQ_CONNECT: {
                    RequestInfo apnRequest = null;

                    if (msg.obj != null) {
                        apnRequest = (RequestInfo) msg.obj;

                        // M: EIMS excute when PS unregister or without SIM
                        if (isRequestEIMS(apnRequest)) {
                            log("IdleState: special case for EIMS");
                            DctController.getInstance().executeRequest(apnRequest);
                        }
                    }
                    if (DBG) {
                        log("IdleState: REQ_CONNECT, apnRequest=" + apnRequest);
                    }

                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_CONNECT,
                            PhoneConstants.APN_REQUEST_STARTED);

                    /// M: [C2K][SVLTE] Set PS active slot when data detached.
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                        log("REQ_CONNECT : mId = " + mId);
                        setPsActiveSim();
                    }
                    transitionTo(mAttachingState);
                    retVal = HANDLED;
                    break;
                }

/*M: Fix google issue
                case DcSwitchAsyncChannel.EVENT_DATA_ATTACHED:
                    if (DBG) {
                        log("AttachingState: EVENT_DATA_ATTACHED");
                    }
                    transitionTo(mAttachedState);
                    retVal = HANDLED;
                    break;
*/
                case DcSwitchAsyncChannel.EVENT_DATA_ATTACHED: {
                    if (DBG) {
                        log("IdleState: EVENT_DATA_ATTACHED");
                    }
                    mIsAttached = true;
                    retVal = HANDLED;
                    break;
                }
                case EVENT_CONNECTED: {
                    if (DBG) {
                        log("IdleState: Receive invalid event EVENT_CONNECTED!");
                    }
                    retVal = HANDLED;
                    break;
                }
                case DcSwitchAsyncChannel.REQ_DISCONNECT_ALL: {
                    if (DBG) {
                        log("IdleState: REQ_DISCONNECT_ALL, shouldn't in this state," +
                            "but regards it had disconnected");
                    }
                    mAc.replyToMessage(msg,
                            DcSwitchAsyncChannel.REQ_DISCONNECT_ALL,
                            PhoneConstants.APN_ALREADY_INACTIVE);
                    retVal = HANDLED;
                    break;
                }
                default:
                    if (VDBG) {
                        log("IdleState: nothandled msg.what=0x" +
                                Integer.toHexString(msg.what));
                    }
                    retVal = NOT_HANDLED;
                    break;
            }
            return retVal;
        }
    }

    private class AttachingState extends State {
        boolean mTriggeredWithoutSIM = false;
        @Override
        public void enter() {
            log("AttachingState: enter mNeedAttach = " + mNeedAttach);
            if (mNeedAttach) {
                // M: [C2K][IRAT] Change the format of RIL request
                setDataAllowed(true, null);
            } else {
                log("AttachingState: caused by lost connection, don't attach again");
                mNeedAttach = true;
                mReason = "Lost Connection";
            }
            DctController.getInstance().notifyDcSwitchStateChange(DCSTATE_ATTACHING, mId, mReason);            
            mReason = "";
        }

        @Override
        public boolean processMessage(Message msg) {
            boolean retVal;

            switch (msg.what) {
                case DcSwitchAsyncChannel.REQ_CONNECT: {
                    if (DBG) {
                        log("AttachingState: REQ_CONNECT");
                    }

                    /// M: [C2K][IRAT] Set PS active slot..
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                        log("REQ_CONNECT : mId = " + mId);
                        setPsActiveSim();
                    }

                    // M: [C2K][IRAT] Change the format of RIL request
                    setDataAllowed(true, null);

                    RequestInfo apnRequest = null;

                    if (msg.obj != null) {
                        apnRequest = (RequestInfo) msg.obj;
                        // M: EIMS excute when PS unregister or without SIM
                        if (isRequestEIMS(apnRequest)) {
                            log("AttachingState: special case for EIMS");
                            DctController.getInstance().executeRequest(apnRequest);
                        }
                    }

                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_CONNECT,
                            PhoneConstants.APN_REQUEST_STARTED);

                    if (mIsAttached) {
                        if (DBG) {
                            log("AttachingState: already attached, transfer to AttachedState");
                        }
                        transitionTo(mAttachedState);
                    }

                    retVal = HANDLED;
                    break;
                }

                case DcSwitchAsyncChannel.EVENT_DATA_ATTACHED:
                    if (DBG) {
                        log("AttachingState: EVENT_DATA_ATTACHED");
                    }
                    mIsAttached = true;
                    transitionTo(mAttachedState);
                    retVal = HANDLED;
                    break;

                case DcSwitchAsyncChannel.REQ_DISCONNECT_ALL: {
                    if (DBG) {
                        log("AttachingState: REQ_DISCONNECT_ALL");
                    }

                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_DISCONNECT_ALL,
                            PhoneConstants.APN_REQUEST_STARTED);

                    transitionTo(mPreDetachCheckState);
                    retVal = HANDLED;
                    break;
                }

                default:
                    if (VDBG) {
                        log("AttachingState: nothandled msg.what=0x" +
                                Integer.toHexString(msg.what));
                    }
                    retVal = NOT_HANDLED;
                    break;
            }
            return retVal;
        }
    }

    private class AttachedState extends State {
        @Override
        public void enter() {
            if (DBG) log("AttachedState: enter");
            //When enter attached state, we need exeute all requests.
            DctController.getInstance().executeAllRequests(mId);
            DctController.getInstance().notifyDcSwitchStateChange(DCSTATE_ATTACHED, mId, mReason);
        }

        @Override
        public boolean processMessage(Message msg) {
            boolean retVal;

            switch (msg.what) {
                case DcSwitchAsyncChannel.REQ_CONNECT: {
                    RequestInfo apnRequest = null;

                    if (msg.obj != null) {
                        apnRequest = (RequestInfo) msg.obj;
                        DctController.getInstance().executeRequest(apnRequest);
                    }

                    if (DBG) {
                        log("AttachedState: REQ_CONNECT, apnRequest=" + apnRequest);
                    }

                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_CONNECT,
                            PhoneConstants.APN_REQUEST_STARTED);
                    retVal = HANDLED;
                    break;
                }
                case DcSwitchAsyncChannel.REQ_DISCONNECT: {
                    RequestInfo apnRequest = (RequestInfo) msg.obj;
                    if (DBG) {
                        log("AttachedState: REQ_DISCONNECT apnRequest=" + apnRequest);
                    }

                    DctController.getInstance().releaseRequest(apnRequest);
                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_DISCONNECT,
                            PhoneConstants.APN_REQUEST_STARTED);

                    retVal = HANDLED;
                    break;
                }

                case DcSwitchAsyncChannel.REQ_DISCONNECT_ALL: {
                    if (DBG) {
                        log("AttachedState: REQ_DISCONNECT_ALL");
                    }

                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_DISCONNECT_ALL,
                            PhoneConstants.APN_REQUEST_STARTED);

                    transitionTo(mPreDetachCheckState);
                    retVal = HANDLED;
                    break;
                }

                case DcSwitchAsyncChannel.EVENT_DATA_DETACHED: {
                    if (DBG) {
                        log("AttachedState: EVENT_DATA_DETACHED");
                    }
                    mNeedAttach = false;
                    mIsAttached = false;
                    transitionTo(mAttachingState);
                    retVal = HANDLED;
                    break;
                }

                default:
                    if (VDBG) {
                        log("AttachedState: nothandled msg.what=0x" +
                                Integer.toHexString(msg.what));
                    }
                    retVal = NOT_HANDLED;
                    break;
            }
            return retVal;
        }
    }

    private class PreDetachCheckState extends State {
        int mUserCnt;
        int mTransactionId;

        @Override
        public void enter() {
            if (DBG) {
                log("PreDetachCheckState: enter");
            }
            DctController.getInstance().notifyDcSwitchStateChange(DCSTATE_PREDETACH_CHECK, mId,
                mReason);
        }

        @Override
        public boolean processMessage(Message msg) {
            boolean retVal;

            switch (msg.what) {
                case DcSwitchAsyncChannel.REQ_CONFIRM_PREDETACH:
                    if (DBG) {
                        log("PreDetachCheckState: REQ_CONFIRM_PREDETACH");
                    }

                    DctController.getInstance().releaseAllRequests(mId);
                    transitionTo(mDetachingState);
                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_CONFIRM_PREDETACH,
                            PhoneConstants.APN_REQUEST_STARTED);
                    retVal = HANDLED;
                    break;
                case DcSwitchAsyncChannel.REQ_CONNECT: {
                    if (DBG) {
                        log("PreDetachCheckState: REQ_CONNECT, return fail");
                    }
                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_CONNECT,
                            PhoneConstants.APN_REQUEST_FAILED);
                    retVal = HANDLED;
                    break;
                }
                case DcSwitchAsyncChannel.REQ_DISCONNECT: {
                    RequestInfo apnRequest = (RequestInfo) msg.obj;
                    if (DBG) {
                        log("PreDetachCheckState: REQ_DISCONNECT apnRequest=" + apnRequest);
                    }

                    DctController.getInstance().releaseRequest(apnRequest);
                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_DISCONNECT,
                            PhoneConstants.APN_REQUEST_STARTED);

                    retVal = HANDLED;
                    break;
                }
                case DcSwitchAsyncChannel.REQ_DISCONNECT_ALL: {
                    if (DBG) {
                        log("PreDetachCheckState: REQ_DISCONNECT_ALL, return fail.");
                    }
                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_DISCONNECT_ALL,
                            PhoneConstants.APN_REQUEST_FAILED);
                    retVal = HANDLED;
                    break;
                }

                default:
                    if (VDBG) {
                        log("PreDetachCheckState: nothandled msg.what=0x" +
                                Integer.toHexString(msg.what));
                    }
                    retVal = NOT_HANDLED;
                    break;
            }
            return retVal;
        }
    }

    private class DetachingState extends State {
        @Override
        public void enter() {
            if (DBG) log("DetachingState: enter");
            // M: [C2K][IRAT] Change the format of RIL request
            setDataAllowed(false, obtainMessage(EVENT_DATA_DETACH_DONE));
            DctController.getInstance().notifyDcSwitchStateChange(DCSTATE_DETACHING, mId, mReason);
        }

        @Override
        public boolean processMessage(Message msg) {
            boolean retVal;

            switch (msg.what) {
                case DcSwitchAsyncChannel.REQ_CONNECT: {
                    if (DBG) {
                        log("DetachingState: REQ_CONNECT, return fail");
                    }
                    // M: [C2K][IRAT] Change the format of RIL request
                    setDataAllowed(false, obtainMessage(EVENT_DATA_DETACH_DONE));
                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_CONNECT,
                            PhoneConstants.APN_REQUEST_FAILED);
                    retVal = HANDLED;
                    break;
                }
                case EVENT_DATA_DETACH_DONE: {
                    if (DBG) {
                        log("DeattachingState: EVENT_DATA_DETACH_DONE");
                    }
                    transitionTo(mIdleState);
                    retVal = HANDLED;
                    break;
                }
                case DcSwitchAsyncChannel.REQ_DISCONNECT_ALL: {
                    if (DBG) {
                        log("DetachingState: REQ_DISCONNECT_ALL, already detaching" );
                    }
                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_DISCONNECT_ALL,
                            PhoneConstants.APN_REQUEST_STARTED);
                    retVal = HANDLED;
                    break;
                }
                default:
                    if (VDBG) {
                        log("DetachingState: nothandled msg.what=0x" +
                                Integer.toHexString(msg.what));
                    }
                    retVal = NOT_HANDLED;
                    break;
            }
            return retVal;
        }
    }

    private class DefaultState extends State {
        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_FULL_CONNECTION: {
                    if (mAc != null) {
                        if (VDBG) log("Disconnecting to previous connection mAc=" + mAc);
                        mAc.replyToMessage(msg, AsyncChannel.CMD_CHANNEL_FULLY_CONNECTED,
                                AsyncChannel.STATUS_FULL_CONNECTION_REFUSED_ALREADY_CONNECTED);
                    } else {
                        mAc = new AsyncChannel();
                        mAc.connected(null, getHandler(), msg.replyTo);
                        if (VDBG) log("DcDefaultState: FULL_CONNECTION reply connected");
                        mAc.replyToMessage(msg, AsyncChannel.CMD_CHANNEL_FULLY_CONNECTED,
                                AsyncChannel.STATUS_SUCCESSFUL, mId, "hi");
                    }
                    break;
                }
                case AsyncChannel.CMD_CHANNEL_DISCONNECT: {
                    if (VDBG) log("CMD_CHANNEL_DISCONNECT");
                    mAc.disconnect();
                    break;
                }
                case AsyncChannel.CMD_CHANNEL_DISCONNECTED: {
                    if (VDBG) log("CMD_CHANNEL_DISCONNECTED");
                    mAc = null;
                    break;
                }
                case DcSwitchAsyncChannel.REQ_IS_IDLE_STATE: {
                    boolean val = getCurrentState() == mIdleState;
                    if (VDBG) log("REQ_IS_IDLE_STATE  isIdle=" + val);
                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_IS_IDLE_STATE, val ? 1 : 0);
                    break;
                }
                case DcSwitchAsyncChannel.REQ_IS_IDLE_OR_DETACHING_STATE: {
                    boolean val = (getCurrentState() == mIdleState ||
                            getCurrentState() == mDetachingState);
                    if (VDBG) log("REQ_IS_IDLE_OR_DETACHING_STATE  isIdleDetaching=" + val);
                    mAc.replyToMessage(msg,
                            DcSwitchAsyncChannel.RSP_IS_IDLE_OR_DETACHING_STATE, val ? 1 : 0);
                    break;
                }
                case DcSwitchAsyncChannel.REQ_CONFIRM_PREDETACH:
                    if (DBG) {
                        log("DefaultState: unhandle REQ_PRECHECK_DONE");
                    }
                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_CONFIRM_PREDETACH,
                            PhoneConstants.APN_REQUEST_FAILED);
                    break;
                case DcSwitchAsyncChannel.REQ_DC_SWITCH_STATE:
                    if (DBG) {
                        log("DefaultState: get DC switch state");
                    }
                    mAc.replyToMessage(msg, DcSwitchAsyncChannel.RSP_DC_SWITCH_STATE,
                           (Object) convertState(getCurrentState()));
                    break;
                case DcSwitchAsyncChannel.EVENT_DATA_DETACHED:
                    if (DBG) {
                        log("DefaultState: EVENT_DATA_DETACHED");
                    }
                    mIsAttached = false;
                    break;
                default:
                    if (DBG) {
                        log("DefaultState: shouldn't happen but ignore msg.what=0x" +
                                Integer.toHexString(msg.what));
                    }
                    break;
            }
            return HANDLED;
        }
    }

    @Override
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[" + getName() + "] " + s);
    }

    private String convertState(Object state) {
        String ret = "";
        if (state instanceof IdleState) {
            ret = DCSTATE_IDLE;
        } else if (state instanceof AttachingState) {
            ret = DCSTATE_ATTACHING;
        } else if (state instanceof AttachedState) {
            ret = DCSTATE_ATTACHED;
        } else if (state instanceof PreDetachCheckState) {
            ret = DCSTATE_PREDETACH_CHECK;
        } else if (state instanceof DetachingState) {
            ret = DCSTATE_DETACHING;
        }
        return ret;
    }

    /// M: [C2K][IRAT] code start {@
    private void setPsActiveSim() {
        //Should setPsActiveSim in SVLTE project, not just only for ACTIVE SVLTE slot
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            ((SvltePhoneProxy) mPhone).getRilDcArbitrator().requestSetPsActiveSlot(
                    mId + 1, null);
        }
    }

    private void setDataAllowed(boolean allowed, Message result) {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && SvlteUtils.isActiveSvlteMode(mId)) {
            ((SvltePhoneProxy) mPhone).getIratDataSwitchHelper()
                    .setDataAllowed(allowed, result);
        } else {
            PhoneBase phone = (PhoneBase) ((PhoneProxy) mPhone)
                    .getActivePhone();
            phone.mCi.setDataAllowed(allowed, result);
        }
    }
    /// M: [C2K][IRAT] code end @}

    /// M: VOLTE code start @}
    private boolean isRequestEIMS(RequestInfo apnRequest) {
        boolean bRet = false;
        boolean bhasEIMSCap = apnRequest.request.networkCapabilities.hasCapability(
                                                NetworkCapabilities.NET_CAPABILITY_EIMS);
        String specifier = apnRequest.request.networkCapabilities.getNetworkSpecifier();
        log("bHasEIMSCap: " + bhasEIMSCap + ", specifier: " + specifier);

        if (bhasEIMSCap && (specifier != null && !specifier.equals("") )) {
            bRet = true;
        }
        log("isRequestEIMSWithoutSIM ret: " + bRet);
        return bRet;
    }
    /// M: VOLTE code end @}
}
