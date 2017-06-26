/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.telephony.cdma.utk;

import android.os.Handler;
import android.os.Message;

import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;


/**
 * Class used for queuing raw ril messages, decoding them into CommanParams
 * objects and sending the result back to the UTK Service.
 */
class RilMessageDecoder extends StateMachine {

    // constants
    private static final int CMD_START = 1;
    private static final int CMD_PARAMS_READY = 2;

    // members
    private static RilMessageDecoder sInstanceSim1 = null;
    private static RilMessageDecoder sInstanceSim2 = null;
    private CommandParamsFactory mCmdParamsFactory = null;
    private RilMessage mCurrentRilMessage = null;
    private Handler mCaller = null;
    private int mPhoneId;

    // States
    private StateStart mStateStart = new StateStart();
    private StateCmdParamsReady mStateCmdParamsReady = new StateCmdParamsReady();

    /**
     * Get the singleton instance, constructing if necessary.
     *
     * @param caller
     * @param fh
     * @param phoneId
     * @return RilMesssageDecoder
     */
    public static synchronized RilMessageDecoder getInstance(Handler caller, IccFileHandler fh, int phoneId) {
        if (PhoneConstants.SIM_ID_1 == phoneId) {
            if (sInstanceSim1 == null) {
                UtkLog.d("RilMessageDecoder", "Create RilMessageDecoder instance" + phoneId);
                sInstanceSim1 = new RilMessageDecoder(caller, fh, phoneId);
                sInstanceSim1.start();
            }
            return sInstanceSim1;
        } else if (PhoneConstants.SIM_ID_2 == phoneId) {
            if (sInstanceSim2 == null) {
                UtkLog.d("RilMessageDecoder", "Create RilMessageDecoder instance" + phoneId);
                sInstanceSim2 = new RilMessageDecoder(caller, fh, phoneId);
                sInstanceSim2.start();
            }
            return sInstanceSim2;
        } else {
            UtkLog.d("RilMessageDecoder", "Invalid phone Id and just return null");
            return null;
        }
    }

    /**
     * Start decoding the message parameters,
     * when complete MSG_ID_RIL_MSG_DECODED will be returned to caller.
     *
     * @param rilMsg
     */
    public void sendStartDecodingMessageParams(RilMessage rilMsg) {
        Message msg = obtainMessage(CMD_START);
        msg.obj = rilMsg;
        sendMessage(msg);
    }

    /**
     * The command parameters have been decoded.
     *
     * @param resCode
     * @param cmdParams
     */
    public void sendMsgParamsDecoded(ResultCode resCode, CommandParams cmdParams) {
        Message msg = obtainMessage(RilMessageDecoder.CMD_PARAMS_READY);
        msg.arg1 = resCode.value();
        msg.obj = cmdParams;
        sendMessage(msg);
    }

    private void sendCmdForExecution(RilMessage rilMsg) {
        Message msg = mCaller.obtainMessage(UtkService.MSG_ID_RIL_MSG_DECODED,
                new RilMessage(rilMsg));
        msg.sendToTarget();
    }

    private RilMessageDecoder(Handler caller, IccFileHandler fh, int phoneId) {
        super("RilMessageDecoder");

        addState(mStateStart);
        addState(mStateCmdParamsReady);
        setInitialState(mStateStart);

        mCaller = caller;
        mPhoneId = phoneId;
        mCmdParamsFactory = CommandParamsFactory.getInstance(this, fh,((UtkService)mCaller)
                                                                                     .getContext());
    }
    
    public int getPhoneId() {
        return mPhoneId;
    }

    /**
     * Class for starting to decode Rilmessage.
     *
     * {@hide}
     */
    private class StateStart extends State {
        @Override
        public boolean processMessage(Message msg) {
            if (msg.what == CMD_START) {
                if (decodeMessageParams((RilMessage) msg.obj)) {
                    transitionTo(mStateCmdParamsReady);
                }
            } else {
                UtkLog.d(this, "StateStart unexpected expecting START=" +
                         CMD_START + " got " + msg.what);
            }
            return true;
        }
    }

    /**
     * Class for decoding  rilmessage ready.
     *
     * {@hide}
     */
    private class StateCmdParamsReady extends State {
        @Override
        public boolean processMessage(Message msg) {
            if (msg.what == CMD_PARAMS_READY) {
                mCurrentRilMessage.mResCode = ResultCode.fromInt(msg.arg1);
                mCurrentRilMessage.mData = msg.obj;
                sendCmdForExecution(mCurrentRilMessage);
                transitionTo(mStateStart);
            } else {
                UtkLog.d(this, "StateCmdParamsReady expecting CMD_PARAMS_READY="
                         + CMD_PARAMS_READY + " got " + msg.what);
                deferMessage(msg);
            }
            return true;
        }
    }

    private boolean decodeMessageParams(RilMessage rilMsg) {
        boolean decodingStarted;

        mCurrentRilMessage = rilMsg;
        switch(rilMsg.mId) {
        case UtkService.MSG_ID_SESSION_END:
        case UtkService.MSG_ID_CALL_SETUP:
            mCurrentRilMessage.mResCode = ResultCode.OK;
            sendCmdForExecution(mCurrentRilMessage);
            decodingStarted = false;
            break;
        case UtkService.MSG_ID_PROACTIVE_COMMAND:
        case UtkService.MSG_ID_EVENT_NOTIFY:
        case UtkService.MSG_ID_REFRESH:
        //Add for UTK IR case
        case UtkService.MSG_ID_MENU_INFO:
            byte[] rawData = null;
            try {
                rawData = IccUtils.hexStringToBytes((String) rilMsg.mData);
            } catch (Exception e) {
                // zombie messages are dropped
                UtkLog.d(this, "decodeMessageParams dropping zombie messages");
                decodingStarted = false;
                break;
            }
            try {
                //Temp test IR case
                UtkLog.d(this, "rawData = " + rawData);
                if (rawData == null) {
                    UtkLog.d(this, "rawData is null");
                }
                // Start asynch parsing of the command parameters.
                mCmdParamsFactory.make(BerTlv.decode(rawData));
                decodingStarted = true;
            } catch (ResultException e) {
                // send to Service for proper RIL communication.
                UtkLog.d(this, "decodeMessageParams: caught ResultException e=" + e);
                mCurrentRilMessage.mResCode = e.result();
                sendCmdForExecution(mCurrentRilMessage);
                decodingStarted = false;
            }
            break;
        default:
            decodingStarted = false;
            break;
        }
        return decodingStarted;
    }

    public void dispose(int phoneId) {
        UtkLog.d(this, "decodeMessageParams: dispose obj" + phoneId);
        if (PhoneConstants.SIM_ID_1 == phoneId) {
            if (sInstanceSim1 != null) {
                sInstanceSim1.quit();
                sInstanceSim1 = null;
            }
        } else {
            if (sInstanceSim2 != null) {
                sInstanceSim2.quit();
                sInstanceSim2 = null;
            }

        }
    }
}
