/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.internal.telephony.imsphone;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.os.UserHandle;

import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import com.android.ims.ImsCallForwardInfo;
/// M: SS OP01 Ut
import com.android.ims.ImsCallForwardInfoEx;
import com.android.ims.ImsCallProfile;
import com.android.ims.ImsEcbm;
import com.android.ims.ImsEcbmStateListener;
import com.android.ims.ImsException;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsSsInfo;
import com.android.ims.ImsUtInterface;

import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOIC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAOICxH;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAIC;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BAICr;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_ALL;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_MO;
import static com.android.internal.telephony.CommandsInterface.CB_FACILITY_BA_MT;

import static com.android.internal.telephony.CommandsInterface.CF_ACTION_DISABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ERASURE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_REGISTRATION;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL_CONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NO_REPLY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NOT_REACHABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_BUSY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VOICE;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_NONE;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VIDEO;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
/// M: SS OP01 Ut
import com.android.internal.telephony.CallForwardInfoEx;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.MMTelSSUtils;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.SuppSrvRequest;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.uicc.IccRecords;

/// M: ALPS01953873. @{
import java.util.Arrays;
/// @}
import java.util.ArrayList;
import java.util.List;

/**
 * {@hide}
 */
public class ImsPhone extends ImsPhoneBase {
    private static final String LOG_TAG = "ImsPhone";
    private static final boolean DBG = true;
    private static final boolean VDBG = false; // STOPSHIP if true

    protected static final int EVENT_SET_CALL_BARRING_DONE          = EVENT_LAST + 1;
    protected static final int EVENT_GET_CALL_BARRING_DONE          = EVENT_LAST + 2;
    protected static final int EVENT_SET_CALL_WAITING_DONE          = EVENT_LAST + 3;
    protected static final int EVENT_GET_CALL_WAITING_DONE          = EVENT_LAST + 4;
    protected static final int EVENT_SET_CLIR_DONE                  = EVENT_LAST + 5;
    protected static final int EVENT_GET_CLIR_DONE                  = EVENT_LAST + 6;

    public static final String CS_FALLBACK = "cs_fallback";
    public static final String UT_BUNDLE_KEY_CLIR = "queryClir";

    /// M: @{
    /**
     * Used as the message in CallStateException.
     * We don't support dialing a USSD number while there is an existing IMS call.
     */
    public static final String USSD_DURING_IMS_INCALL = "ussd_during_ims_incall";
    /// @}

    static final int RESTART_ECM_TIMER = 0; // restart Ecm timer
    static final int CANCEL_ECM_TIMER = 1; // cancel Ecm timer

    // Default Emergency Callback Mode exit timer
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;

    // Instance Variables
    PhoneBase mDefaultPhone;
    ImsPhoneCallTracker mCT;
    ArrayList <ImsPhoneMmiCode> mPendingMMIs = new ArrayList<ImsPhoneMmiCode>();

    Registrant mPostDialHandler;
    ServiceState mSS = new ServiceState();

    // To redial silently through GSM or CDMA when dialing through IMS fails
    private String mLastDialString;

    WakeLock mWakeLock;
    protected boolean mIsPhoneInEcmState;

    // mEcmExitRespRegistrant is informed after the phone has been exited the emergency
    // callback mode keep track of if phone is in emergency callback mode
    private Registrant mEcmExitRespRegistrant;

    private final RegistrantList mSilentRedialRegistrants = new RegistrantList();

    private boolean mImsRegistered = false;

    private String mDialString;

    // A runnable which is used to automatically exit from Ecm after a period of time.
    private Runnable mExitEcmRunnable = new Runnable() {
        @Override
        public void run() {
            exitEmergencyCallbackMode();
        }
    };

    // Create Cf (Call forward) so that dialling number &
    // mIsCfu (true if reason is call forward unconditional)
    // mOnComplete (Message object passed by client) can be packed &
    // given as a single Cf object as user data to UtInterface.
    private static class Cf {
        final String mSetCfNumber;
        final Message mOnComplete;
        final boolean mIsCfu;

        Cf(String cfNumber, boolean isCfu, Message onComplete) {
            mSetCfNumber = cfNumber;
            mIsCfu = isCfu;
            mOnComplete = onComplete;
        }
    }

    // Constructors

    ImsPhone(Context context, PhoneNotifier notifier, Phone defaultPhone) {
        super("ImsPhone", context, notifier);

        mDefaultPhone = (PhoneBase) defaultPhone;
        mCT = new ImsPhoneCallTracker(this);
        mSS.setStateOff();

        mPhoneId = mDefaultPhone.getPhoneId();

        // This is needed to handle phone process crashes
        // Same property is used for both CDMA & IMS phone.
        mIsPhoneInEcmState = SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_INECM_MODE, false);

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOG_TAG);
        mWakeLock.setReferenceCounted(false);
    }

    public void updateParentPhone(PhoneBase parentPhone) {
        // synchronization is managed at the PhoneBase scope (which calls this function)
        mDefaultPhone = parentPhone;
        mPhoneId = mDefaultPhone.getPhoneId();
    }

    @Override
    public void dispose() {
        Rlog.d(LOG_TAG, "dispose");

        // Nothing to dispose in PhoneBase
        //super.dispose();
        mPendingMMIs.clear();
        mCT.dispose();

        //Force all referenced classes to unregister their former registered events
    }

    @Override
    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        super.removeReferences();

        mCT = null;
        mSS = null;
    }

    @Override
    public ServiceState
    getServiceState() {
        return mSS;
    }

    /* package */ void setServiceState(int state) {
        mSS.setState(state);
    }

    @Override
    public CallTracker getCallTracker() {
        return mCT;
    }

    @Override
    public List<? extends ImsPhoneMmiCode>
    getPendingMmiCodes() {
        return mPendingMMIs;
    }


    @Override
    public void
    acceptCall(int videoState) throws CallStateException {
        mCT.acceptCall(videoState);
    }

    @Override
    public void
    rejectCall() throws CallStateException {
        mCT.rejectCall();
    }

    @Override
    public void
    switchHoldingAndActive() throws CallStateException {
        mCT.switchWaitingOrHoldingAndActive();
    }

    @Override
    public boolean canConference() {
        return mCT.canConference();
    }

    public boolean canDial() {
        return mCT.canDial();
    }

    @Override
    public void conference() {
        mCT.conference();
    }

    @Override
    public void clearDisconnected() {
        mCT.clearDisconnected();
    }

    @Override
    public boolean canTransfer() {
        return mCT.canTransfer();
    }

    @Override
    public void explicitCallTransfer() {
        mCT.explicitCallTransfer();
    }

    @Override
    public ImsPhoneCall
    getForegroundCall() {
        return mCT.mForegroundCall;
    }

    @Override
    public ImsPhoneCall
    getBackgroundCall() {
        return mCT.mBackgroundCall;
    }

    @Override
    public ImsPhoneCall
    getRingingCall() {
        return mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (getRingingCall().getState() != ImsPhoneCall.State.IDLE) {
            if (DBG) Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                mCT.rejectCall();
            } catch (CallStateException e) {
                if (DBG) Rlog.d(LOG_TAG, "reject failed", e);
                notifySuppServiceFailed(Phone.SuppService.REJECT);
            }
        } else if (getBackgroundCall().getState() != ImsPhoneCall.State.IDLE) {
            if (DBG) Rlog.d(LOG_TAG, "MmiCode 0: hangupWaitingOrBackground");
            try {
                mCT.hangup(getBackgroundCall());
            } catch (CallStateException e) {
                if (DBG) Rlog.d(LOG_TAG, "hangup failed", e);
            }
        }

        return true;
    }


    private boolean handleCallWaitingIncallSupplementaryService(
            String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        ImsPhoneCall call = getForegroundCall();

        try {
            if (len > 1) {
                if (DBG) Rlog.d(LOG_TAG, "not support 1X SEND");
                notifySuppServiceFailed(Phone.SuppService.HANGUP);
            } else {
                if (call.getState() != ImsPhoneCall.State.IDLE) {
                    if (DBG) Rlog.d(LOG_TAG, "MmiCode 1: hangup foreground");
                    mCT.hangup(call);
                } else {
                    if (DBG) Rlog.d(LOG_TAG, "MmiCode 1: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            }
        } catch (CallStateException e) {
            if (DBG) Rlog.d(LOG_TAG, "hangup failed", e);
            notifySuppServiceFailed(Phone.SuppService.HANGUP);
        }

        return true;
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        ImsPhoneCall call = getForegroundCall();

        if (len > 1) {
            if (DBG) Rlog.d(LOG_TAG, "separate not supported");
            notifySuppServiceFailed(Phone.SuppService.SEPARATE);
        } else {
            try {
                if (getRingingCall().getState() != ImsPhoneCall.State.IDLE) {
                    if (DBG) Rlog.d(LOG_TAG, "MmiCode 2: accept ringing call");
                    mCT.acceptCall(ImsCallProfile.CALL_TYPE_VOICE);
                } else {
                    if (DBG) Rlog.d(LOG_TAG, "MmiCode 2: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            } catch (CallStateException e) {
                if (DBG) Rlog.d(LOG_TAG, "switch failed", e);
                notifySuppServiceFailed(Phone.SuppService.SWITCH);
            }
        }

        return true;
    }

    private boolean handleMultipartyIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (DBG) Rlog.d(LOG_TAG, "MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean handleEctIncallSupplementaryService(String dialString) {

        int len = dialString.length();

        if (len != 1) {
            return false;
        }

        if (DBG) Rlog.d(LOG_TAG, "MmiCode 4: not support explicit call transfer");
        notifySuppServiceFailed(Phone.SuppService.TRANSFER);
        return true;
    }

    private boolean handleCcbsIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        Rlog.i(LOG_TAG, "MmiCode 5: CCBS not supported!");
        // Treat it as an "unknown" service.
        notifySuppServiceFailed(Phone.SuppService.UNKNOWN);
        return true;
    }

    @Override
    public boolean handleInCallMmiCommands(String dialString) {
        if (!isInCall()) {
            return false;
        }

        if (TextUtils.isEmpty(dialString)) {
            return false;
        }

        boolean result = false;
        char ch = dialString.charAt(0);
        switch (ch) {
            case '0':
                result = handleCallDeflectionIncallSupplementaryService(
                        dialString);
                break;
            case '1':
                result = handleCallWaitingIncallSupplementaryService(
                        dialString);
                break;
            case '2':
                result = handleCallHoldIncallSupplementaryService(dialString);
                break;
            case '3':
                result = handleMultipartyIncallSupplementaryService(dialString);
                break;
            case '4':
                result = handleEctIncallSupplementaryService(dialString);
                break;
            case '5':
                result = handleCcbsIncallSupplementaryService(dialString);
                break;
            default:
                break;
        }

        return result;
    }

    /// M: for USSD over IMS workaround. @{
    private boolean isUssdDuringInCall(ImsPhoneMmiCode mmi) {
        if (mmi == null || !mmi.isUssdNumber()) {
            return false;
        }

        return isInCall();
    }
    /// @}

    boolean isInCall() {
        ImsPhoneCall.State foregroundCallState = getForegroundCall().getState();
        ImsPhoneCall.State backgroundCallState = getBackgroundCall().getState();
        ImsPhoneCall.State ringingCallState = getRingingCall().getState();

       return (foregroundCallState.isAlive() ||
               backgroundCallState.isAlive() ||
               ringingCallState.isAlive());
    }

    void notifyNewRingingConnection(Connection c) {
        mDefaultPhone.notifyNewRingingConnectionP(c);
    }


    @Override
    public Connection
    dial(String dialString, int videoState) throws CallStateException {
        return dialInternal(dialString, videoState);
    }

    protected Connection dialInternal(String dialString, int videoState)
            throws CallStateException {
        /// M: Ignore stripping for VoLTE SIP uri. @{
        String newDialString = dialString;
        if (!PhoneNumberUtils.isUriNumber(dialString)) {
            // Need to make sure dialString gets parsed properly
            newDialString = PhoneNumberUtils.stripSeparators(dialString);
        }
        /// @}

        // handle in-call MMI first if applicable
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }

        if (mDefaultPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            return mCT.dial(dialString, videoState);
        }

        /// M: Ignore extracting for VoLTE SIP uri. @{
        String networkPortion = dialString;
        if (!PhoneNumberUtils.isUriNumber(dialString)) {
            // Only look at the Network portion for mmi
            networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        }
        /// @}
        ImsPhoneMmiCode mmi =
                ImsPhoneMmiCode.newFromDialString(networkPortion, this);
        if (DBG) Rlog.d(LOG_TAG,
                "dialing w/ mmi '" + mmi + "'...");

        /// M: If there is an existing call, CS_FB will cause call end. So just ignore it. @{
        if (isUssdDuringInCall(mmi)) {
            if (DBG) {
                Rlog.d(LOG_TAG, "USSD during in-call, ignore this operation!");
            }
            throw new CallStateException(USSD_DURING_IMS_INCALL);
        }
        /// @}

        mDialString = dialString;
        if (mmi == null) {
            return mCT.dial(dialString, videoState);
        } else if (mmi.isTemporaryModeCLIR()) {
            return mCT.dial(mmi.getDialingNumber(), mmi.getCLIRMode(), videoState);
        } else if (!mmi.isSupportedOverImsPhone()) {
            if (DBG) {
                Rlog.d(LOG_TAG, "MMI not supported over ImsPhone()");
            }
            // If the mmi is not supported by IMS service,
            // try to initiate dialing with default phone
            throw new CallStateException(CS_FALLBACK);
        } else {
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();

            return null;
        }
    }

    @Override
    public void
    sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG,
                    "sendDtmf called with invalid character '" + c + "'");
        } else {
            if (mCT.mState ==  PhoneConstants.State.OFFHOOK) {
                mCT.sendDtmf(c, null);
            }
        }
    }

    @Override
    public void
    startDtmf(char c) {
        if (!(PhoneNumberUtils.is12Key(c) || (c >= 'A' && c <= 'D'))) {
            Rlog.e(LOG_TAG,
                    "startDtmf called with invalid character '" + c + "'");
        } else {
            mCT.startDtmf(c);
        }
    }

    @Override
    public void
    stopDtmf() {
        mCT.stopDtmf();
    }

    @Override
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        mPostDialHandler = new Registrant(h, what, obj);
    }

    /*package*/ void notifyIncomingRing() {
        if (DBG) Rlog.d(LOG_TAG, "notifyIncomingRing");
        AsyncResult ar = new AsyncResult(null, null, null);
        sendMessage(obtainMessage(EVENT_CALL_RING, ar));
    }

    @Override
    public void setMute(boolean muted) {
        mCT.setMute(muted);
    }

    @Override
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
        mCT.setUiTTYMode(uiTtyMode, onComplete);
    }

    @Override
    public boolean getMute() {
        return mCT.getMute();
    }

    @Override
    public PhoneConstants.State getState() {
        return mCT.mState;
    }

    private boolean isValidCommandInterfaceCFReason (int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
        case CF_REASON_UNCONDITIONAL:
        case CF_REASON_BUSY:
        case CF_REASON_NO_REPLY:
        case CF_REASON_NOT_REACHABLE:
        case CF_REASON_ALL:
        case CF_REASON_ALL_CONDITIONAL:
            return true;
        default:
            return false;
        }
    }

    private boolean isValidCommandInterfaceCFAction (int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
        case CF_ACTION_DISABLE:
        case CF_ACTION_ENABLE:
        case CF_ACTION_REGISTRATION:
        case CF_ACTION_ERASURE:
            return true;
        default:
            return false;
        }
    }

    private  boolean isCfEnable(int action) {
        return (action == CF_ACTION_ENABLE) || (action == CF_ACTION_REGISTRATION);
    }

    private int getConditionFromCFReason(int reason) {
        switch(reason) {
            case CF_REASON_UNCONDITIONAL: return ImsUtInterface.CDIV_CF_UNCONDITIONAL;
            case CF_REASON_BUSY: return ImsUtInterface.CDIV_CF_BUSY;
            case CF_REASON_NO_REPLY: return ImsUtInterface.CDIV_CF_NO_REPLY;
            case CF_REASON_NOT_REACHABLE: return ImsUtInterface.CDIV_CF_NOT_REACHABLE;
            case CF_REASON_ALL: return ImsUtInterface.CDIV_CF_ALL;
            case CF_REASON_ALL_CONDITIONAL: return ImsUtInterface.CDIV_CF_ALL_CONDITIONAL;
            default:
                break;
        }

        return ImsUtInterface.INVALID;
    }

    private int getCFReasonFromCondition(int condition) {
        switch(condition) {
            case ImsUtInterface.CDIV_CF_UNCONDITIONAL: return CF_REASON_UNCONDITIONAL;
            case ImsUtInterface.CDIV_CF_BUSY: return CF_REASON_BUSY;
            case ImsUtInterface.CDIV_CF_NO_REPLY: return CF_REASON_NO_REPLY;
            case ImsUtInterface.CDIV_CF_NOT_REACHABLE: return CF_REASON_NOT_REACHABLE;
            case ImsUtInterface.CDIV_CF_ALL: return CF_REASON_ALL;
            case ImsUtInterface.CDIV_CF_ALL_CONDITIONAL: return CF_REASON_ALL_CONDITIONAL;
            default:
                break;
        }

        return CF_REASON_NOT_REACHABLE;
    }

    private int getActionFromCFAction(int action) {
        switch(action) {
            case CF_ACTION_DISABLE: return ImsUtInterface.ACTION_DEACTIVATION;
            case CF_ACTION_ENABLE: return ImsUtInterface.ACTION_ACTIVATION;
            case CF_ACTION_ERASURE: return ImsUtInterface.ACTION_ERASURE;
            case CF_ACTION_REGISTRATION: return ImsUtInterface.ACTION_REGISTRATION;
            default:
                break;
        }

        return ImsUtInterface.INVALID;
    }

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason,
            Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "getCallForwardingOption reason=" + commandInterfaceCFReason);
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (DBG) Rlog.d(LOG_TAG, "requesting call forwarding query.");
            Message resp;
            resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);

            try {
                ImsUtInterface ut = mCT.getUtInterface();
                ut.queryCallForward(getConditionFromCFReason(commandInterfaceCFReason),null,resp);
            } catch (ImsException e) {
                sendErrorResponse(onComplete, e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    @Override
    public void setCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "setCallForwardingOption action=" + commandInterfaceCFAction
                + ", reason=" + commandInterfaceCFReason);
        if ((isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {
            Message resp;
            Cf cf = new Cf(dialingNumber,
                    (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL ? true : false),
                    onComplete);
            resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
                    isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, cf);

            try {
                ImsUtInterface ut = mCT.getUtInterface();
                ut.updateCallForward(getActionFromCFAction(commandInterfaceCFAction),
                        getConditionFromCFReason(commandInterfaceCFReason),
                        dialingNumber,
                        timerSeconds,
                        resp);
             } catch (ImsException e) {
                sendErrorResponse(onComplete, e);
             }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    @Override
    public void getCallWaiting(Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "getCallWaiting");
        Message resp;
        resp = obtainMessage(EVENT_GET_CALL_WAITING_DONE, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.queryCallWaiting(resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    @Override
    public void setCallWaiting(boolean enable, Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "setCallWaiting enable=" + enable);
        Message resp;
        resp = obtainMessage(EVENT_SET_CALL_WAITING_DONE, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.updateCallWaiting(enable, resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    private int getCBTypeFromFacility(String facility) {
        if (CB_FACILITY_BAOC.equals(facility)) {
            return ImsUtInterface.CB_BAOC;
        } else if (CB_FACILITY_BAOIC.equals(facility)) {
            return ImsUtInterface.CB_BOIC;
        } else if (CB_FACILITY_BAOICxH.equals(facility)) {
            return ImsUtInterface.CB_BOIC_EXHC;
        } else if (CB_FACILITY_BAIC.equals(facility)) {
            return ImsUtInterface.CB_BAIC;
        } else if (CB_FACILITY_BAICr.equals(facility)) {
            return ImsUtInterface.CB_BIC_WR;
        } else if (CB_FACILITY_BA_ALL.equals(facility)) {
            return ImsUtInterface.CB_BA_ALL;
        } else if (CB_FACILITY_BA_MO.equals(facility)) {
            return ImsUtInterface.CB_BA_MO;
        } else if (CB_FACILITY_BA_MT.equals(facility)) {
            return ImsUtInterface.CB_BA_MT;
        }

        return 0;
    }

    /**
     * Get Call Barring State.
     * @param facility the call barring method
     * @param onComplete message callback
     */
    /* package */ public void getCallBarring(String facility, Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "getCallBarring facility=" + facility);
        Message resp;
        resp = obtainMessage(EVENT_GET_CALL_BARRING_DONE, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.queryCallBarring(getCBTypeFromFacility(facility), resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    /**
     * Set Call Barring State.
     * @param facility the call barring method
     * @param lockState activation flag
     * @param password password
     * @param onComplete message callback
     */
    /* package */ public void setCallBarring(String facility, boolean lockState, String password,
            Message onComplete) {
        if (DBG) Rlog.d(LOG_TAG, "setCallBarring facility=" + facility
                + ", lockState=" + lockState);
        Message resp;
        resp = obtainMessage(EVENT_SET_CALL_BARRING_DONE, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            // password is not required with Ut interface
            ut.updateCallBarring(getCBTypeFromFacility(facility), lockState, resp, null);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    @Override
    public void sendUssdResponse(String ussdMessge) {
        Rlog.d(LOG_TAG, "sendUssdResponse");
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newFromUssdUserInput(ussdMessge, this);
        mPendingMMIs.add(mmi);
        mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.sendUssd(ussdMessge);
    }

    /* package */
    void sendUSSD (String ussdString, Message response) {
        mCT.sendUSSD(ussdString, response);
    }

    /* package */
    void cancelUSSD() {
        mCT.cancelUSSD();
    }

    /* package */
    void sendErrorResponse(Message onComplete) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null,
                    new CommandException(CommandException.Error.GENERIC_FAILURE));
            onComplete.sendToTarget();
        }
    }

    /* package */
    void sendErrorResponse(Message onComplete, Throwable e) {
        Rlog.d(LOG_TAG, "sendErrorResponse");
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null, getCommandException(e));
            onComplete.sendToTarget();
        }
    }

    /* package */
    void sendErrorResponse(Message onComplete, ImsReasonInfo reasonInfo) {
        Rlog.d(LOG_TAG, "sendErrorResponse reasonCode=" + reasonInfo.getCode());
        if (onComplete != null) {
            AsyncResult.forMessage(onComplete, null, getCommandException(reasonInfo.getCode()));
            onComplete.sendToTarget();
        }
    }

    /* package */
    CommandException getCommandException(int code) {
        Rlog.d(LOG_TAG, "getCommandException code=" + code);
        CommandException.Error error = CommandException.Error.GENERIC_FAILURE;

        switch(code) {
            case ImsReasonInfo.CODE_UT_NOT_SUPPORTED:
                error = CommandException.Error.REQUEST_NOT_SUPPORTED;
                break;
            case ImsReasonInfo.CODE_UT_CB_PASSWORD_MISMATCH:
                error = CommandException.Error.PASSWORD_INCORRECT;
                break;
            case ImsReasonInfo.CODE_UT_XCAP_403_FORBIDDEN:
                error = CommandException.Error.UT_XCAP_403_FORBIDDEN;
                break;
            case ImsReasonInfo.CODE_UT_UNKNOWN_HOST:
                error = CommandException.Error.UT_UNKNOWN_HOST;
                break;
            case ImsReasonInfo.CODE_UT_XCAP_409_CONFLICT:
                if (MMTelSSUtils.isEnableXcapHttpResponse409(mPhoneId)) {
                    Rlog.d(LOG_TAG, "getCommandException UT_XCAP_409_CONFLICT");
                    error = CommandException.Error.UT_XCAP_409_CONFLICT;
                }
                break;
            default:
                break;
        }

        return new CommandException(error);
    }

    /* package */
    CommandException getCommandException(Throwable e) {
        CommandException ex = null;

        if (e instanceof ImsException) {
            ex = getCommandException(((ImsException)e).getCode());
        } else {
            Rlog.d(LOG_TAG, "getCommandException generic failure");
            ex = new CommandException(CommandException.Error.GENERIC_FAILURE);
        }
        return ex;
    }

    private void
    onNetworkInitiatedUssd(ImsPhoneMmiCode mmi) {
        Rlog.d(LOG_TAG, "onNetworkInitiatedUssd");
        mMmiCompleteRegistrants.notifyRegistrants(
            new AsyncResult(null, mmi, null));
    }

    /* package */
    void onIncomingUSSD (int ussdMode, String ussdMessage) {
        if (DBG) Rlog.d(LOG_TAG, "onIncomingUSSD ussdMode=" + ussdMode);

        boolean isUssdError;
        boolean isUssdRequest;

        isUssdRequest
            = (ussdMode == CommandsInterface.USSD_MODE_REQUEST);

        isUssdError
            = (ussdMode != CommandsInterface.USSD_MODE_NOTIFY
                && ussdMode != CommandsInterface.USSD_MODE_REQUEST);
      /*HQ_sunli HQ01544260 20151211 begin*/
		if(isUssdError &&(SystemProperties.get("ro.config.remove_mmiError").equals("true"))){
					ussdMessage = null;
				}
      /*HQ_sunli HQ01544260 20151211 end*/
        ImsPhoneMmiCode found = null;
        for (int i = 0, s = mPendingMMIs.size() ; i < s; i++) {
            if(mPendingMMIs.get(i).isPendingUSSD()) {
                found = mPendingMMIs.get(i);
                break;
            }
        }

        if (found != null) {
            // Complete pending USSD
            if (isUssdError) {
                found.onUssdFinishedError();
            } else {
                found.onUssdFinished(ussdMessage, isUssdRequest);
            }
        } else { // pending USSD not found
            // The network may initiate its own USSD request

            // ignore everything that isnt a Notify or a Request
            // also, discard if there is no message to present
            if (!isUssdError && ussdMessage != null) {
                ImsPhoneMmiCode mmi;
                mmi = ImsPhoneMmiCode.newNetworkInitiatedUssd(ussdMessage,
                        isUssdRequest,
                        ImsPhone.this);
                onNetworkInitiatedUssd(mmi);
            }
        }
    }

    /**
     * Removes the given MMI from the pending list and notifies
     * registrants that it is complete.
     * @param mmi MMI that is done
     */
    /*package*/ void
    onMMIDone(ImsPhoneMmiCode mmi) {
        /* Only notify complete if it's on the pending list.
         * Otherwise, it's already been handled (eg, previously canceled).
         * The exception is cancellation of an incoming USSD-REQUEST, which is
         * not on the list.
         */
        if (mPendingMMIs.remove(mmi) || mmi.isUssdRequest()) {
            mMmiCompleteRegistrants.notifyRegistrants(
                    new AsyncResult(null, mmi, null));
        }
    }

    public ArrayList<Connection> getHandoverConnection() {
        ArrayList<Connection> connList = new ArrayList<Connection>();
        // Add all foreground call connections
        connList.addAll(getForegroundCall().mConnections);
        // Add all background call connections
        connList.addAll(getBackgroundCall().mConnections);
        // Add all background call connections
        connList.addAll(getRingingCall().mConnections);
        if (connList.size() > 0) {
            return connList;
        } else {
            return null;
        }
    }

    public void notifySrvccState(Call.SrvccState state) {
        mCT.notifySrvccState(state);
    }

    /* package */ void
    initiateSilentRedial() {
        String result = mLastDialString;
        AsyncResult ar = new AsyncResult(null, result, null);
        if (ar != null) {
            mSilentRedialRegistrants.notifyRegistrants(ar);
        }
    }

    public void registerForSilentRedial(Handler h, int what, Object obj) {
        mSilentRedialRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSilentRedial(Handler h) {
        mSilentRedialRegistrants.remove(h);
    }

    @Override
    public int getSubId() {
        return mDefaultPhone.getSubId();
    }

    @Override
    public int getPhoneId() {
        return mDefaultPhone.getPhoneId();
    }

    private IccRecords getIccRecords() {
        return mDefaultPhone.mIccRecords.get();
    }

    private CallForwardInfo getCallForwardInfo(ImsCallForwardInfo info) {
        CallForwardInfo cfInfo = new CallForwardInfo();
        cfInfo.status = info.mStatus;
        cfInfo.reason = getCFReasonFromCondition(info.mCondition);
        cfInfo.serviceClass = SERVICE_CLASS_VOICE;
        cfInfo.toa = info.mToA;
        cfInfo.number = info.mNumber;
        cfInfo.timeSeconds = info.mTimeSeconds;
        return cfInfo;
    }

    private CallForwardInfo[] handleCfQueryResult(ImsCallForwardInfo[] infos) {
        CallForwardInfo[] cfInfos = null;

        if (infos != null && infos.length != 0) {
            cfInfos = new CallForwardInfo[infos.length];
        }

        IccRecords r = getIccRecords();
        if (infos == null || infos.length == 0) {
            if (r != null) {
                // Assume the default is not active
                // Set unconditional CFF in SIM to false
                r.setVoiceCallForwardingFlag(1, false, null);
            }
        } else {
            for (int i = 0, s = infos.length; i < s; i++) {
                if (infos[i].mCondition == ImsUtInterface.CDIV_CF_UNCONDITIONAL) {
                    if (r != null) {
                        r.setVoiceCallForwardingFlag(1, (infos[i].mStatus == 1),
                            infos[i].mNumber);
                    }
                }
                cfInfos[i] = getCallForwardInfo(infos[i]);
            }
        }

        return cfInfos;
    }

    private int[] handleCbQueryResult(ImsSsInfo[] infos) {
        int[] cbInfos = new int[1];
        cbInfos[0] = infos[0].mStatus;

        return cbInfos;
    }

    private int[] handleCwQueryResult(ImsSsInfo[] infos) {
        int[] cwInfos = new int[2];
        cwInfos[0] = 0;

        if (infos[0].mStatus == 1) {
            cwInfos[0] = 1;
            cwInfos[1] = SERVICE_CLASS_VOICE;
        }

        return cwInfos;
    }

    private void
    sendResponse(Message onComplete, Object result, Throwable e) {
        if (onComplete != null) {
            CommandException ex = null;
            ImsException imsEx = null;
            if (e != null) {
                if (e instanceof ImsException) {
                    imsEx = (ImsException) e;
                    AsyncResult.forMessage(onComplete, result, imsEx);
                } else {
                    ex = getCommandException(e);
                    AsyncResult.forMessage(onComplete, result, ex);
                }
            } else {
                AsyncResult.forMessage(onComplete, result, null);
            }
            onComplete.sendToTarget();
        }
    }

    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        Message onComplete;

        if (DBG) Rlog.d(LOG_TAG, "handleMessage what=" + msg.what);
        switch (msg.what) {
            case EVENT_SET_CALL_FORWARD_DONE: {
                IccRecords r = getIccRecords();
                Cf cf = (Cf) ar.userObj;

                CallForwardInfo[] cfInfos = null;

                if (cf.mIsCfu && ar.exception == null && r != null) {
                    if (MMTelSSUtils.isOp05IccCard(mPhoneId)) {
                        if (ar.result == null) {
                            Rlog.i(LOG_TAG, "arResult is null.");
                        } else {
                            Rlog.d(LOG_TAG, "[EVENT_SET_CALL_FORWARD_DONE check cfinfo.");
                            cfInfos = handleCfQueryResult((ImsCallForwardInfo[]) ar.result);
                        }
                    } else {
                        r.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cf.mSetCfNumber);
                    }
                }
                sendResponse(cf.mOnComplete, cfInfos, ar.exception);
                break;
            }    

            case EVENT_GET_CALL_FORWARD_DONE: {
                CallForwardInfo[] cfInfos = null;
                if (ar.exception == null) {
                    cfInfos = handleCfQueryResult((ImsCallForwardInfo[])ar.result);
                }
                sendResponse((Message) ar.userObj, cfInfos, ar.exception);
                break;
            }
             case EVENT_GET_CALL_BARRING_DONE:
                // Transfer CODE_UT_XCAP_404_NOT_FOUND to UT_XCAP_404_NOT_FOUND. 
                // Only for op15 and CB.
                // If not op15 and CB, it would transfer to GENERIC_FAILURE in getCommandException.
                if (MMTelSSUtils.isOp05IccCard(mPhoneId) &&
                    (ar.exception != null) && (ar.exception instanceof ImsException)) {
                     ImsException imsException = (ImsException) ar.exception;
                     if ((imsException != null) && (imsException.getCode() ==
                            ImsReasonInfo.CODE_UT_XCAP_404_NOT_FOUND)) {
                         Message resp = (Message) ar.userObj;
                         if (resp != null) {
                            AsyncResult.forMessage(resp, null, new CommandException(
                                    CommandException.Error.UT_XCAP_404_NOT_FOUND));
                            resp.sendToTarget();
                            return;
                        }
                    }
                }

             case EVENT_GET_CALL_WAITING_DONE:
                int[] ssInfos = null;
                if (ar.exception == null) {
                    if (msg.what == EVENT_GET_CALL_BARRING_DONE) {
                        ssInfos = handleCbQueryResult((ImsSsInfo[])ar.result);
                    } else if (msg.what == EVENT_GET_CALL_WAITING_DONE) {
                        ssInfos = handleCwQueryResult((ImsSsInfo[])ar.result);
                    }
                }
                sendResponse((Message) ar.userObj, ssInfos, ar.exception);
                break;

             case EVENT_SET_CALL_BARRING_DONE:
                // Transfer CODE_UT_XCAP_404_NOT_FOUND to UT_XCAP_404_NOT_FOUND. 
                // Only for op15 and CB.
                // If not op15 and CB, it would transfer to GENERIC_FAILURE in getCommandException.
                if (MMTelSSUtils.isOp05IccCard(mPhoneId) &&
                    (ar.exception != null) && (ar.exception instanceof ImsException)) {
                     ImsException imsException = (ImsException) ar.exception;
                     if ((imsException != null) && (imsException.getCode() ==
                            ImsReasonInfo.CODE_UT_XCAP_404_NOT_FOUND)) {
                         Message resp = (Message) ar.userObj;
                         if (resp != null) {
                            AsyncResult.forMessage(resp, null, new CommandException(
                                    CommandException.Error.UT_XCAP_404_NOT_FOUND));
                            resp.sendToTarget();
                            return;
                        }
                     }
                }
             case EVENT_SET_CALL_WAITING_DONE:
                sendResponse((Message) ar.userObj, null, ar.exception);
                break;

             case EVENT_SET_CLIR_DONE:
                 if (ar.exception == null) {
                     if (mDefaultPhone instanceof GSMPhone) {
                         GSMPhone gsmPhone = (GSMPhone) mDefaultPhone;
                         gsmPhone.saveClirSetting(msg.arg1);
                     }
                 }
                 sendResponse((Message) ar.userObj, ar.result, ar.exception);
                 break;

             case EVENT_GET_CLIR_DONE:
                 int[] clirInfo = null;
                 if (ar.exception == null) {
                     Bundle ssInfo = (Bundle) ar.result;
                     clirInfo = ssInfo.getIntArray(UT_BUNDLE_KEY_CLIR);
                     // clirInfo[0] = The 'n' parameter from TS 27.007 7.7
                     // clirInfo[1] = The 'm' parameter from TS 27.007 7.7
                     Rlog.d(LOG_TAG, "EVENT_GET_CLIR_DONE: CLIR param n=" + clirInfo[0]
                             + " m=" + clirInfo[1]);
                 }
                 sendResponse((Message) ar.userObj, clirInfo, ar.exception);
                 break;

             /// M: SS OP01 Ut @{
             case EVENT_GET_CALL_FORWARD_TIME_SLOT_DONE:
                 CallForwardInfoEx[] cfInfosEx = null;
                 if (ar.exception == null) {
                     cfInfosEx = handleCfInTimeSlotQueryResult(
                            (ImsCallForwardInfoEx[]) ar.result);
                 }

                 if ((ar.exception != null) && (ar.exception instanceof ImsException)) {
                     ImsException imsException = (ImsException) ar.exception;
                     if ((imsException != null) && (imsException.getCode() ==
                            ImsReasonInfo.CODE_UT_XCAP_403_FORBIDDEN)) {
                         mDefaultPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                         Message resp = (Message) ar.userObj;
                         if (resp != null) {
                            AsyncResult.forMessage(resp, cfInfosEx, new CommandException(
                                    CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED));
                            resp.sendToTarget();
                            return;
                        }
                     }
                 }
                 sendResponse((Message) ar.userObj, cfInfosEx, ar.exception);
                 break;

             case EVENT_SET_CALL_FORWARD_TIME_SLOT_DONE:
                 IccRecords records = getIccRecords();
                 CfEx cfEx = (CfEx) ar.userObj;
                 if (cfEx.mIsCfu && ar.exception == null && records != null) {
                     records.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cfEx.mSetCfNumber);
                     saveTimeSlot(cfEx.mSetTimeSlot);
                 }

                 if ((ar.exception != null) && (ar.exception instanceof ImsException)) {
                     ImsException imsException = (ImsException) ar.exception;
                     if ((imsException != null) && (imsException.getCode() ==
                            ImsReasonInfo.CODE_UT_XCAP_403_FORBIDDEN)) {
                         mDefaultPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
                         Message resp = cfEx.mOnComplete;
                         if (resp != null) {
                            AsyncResult.forMessage(resp, null, new CommandException(
                                    CommandException.Error.SPECAIL_UT_COMMAND_NOT_SUPPORTED));
                            resp.sendToTarget();
                            return;
                        }
                     }
                 }
                 sendResponse(cfEx.mOnComplete, null, ar.exception);
                 break;
             /// @}

             default:
                 super.handleMessage(msg);
                 break;
        }
    }

    /**
     * Listen to the IMS ECBM state change
     */
    ImsEcbmStateListener mImsEcbmStateListener =
            new ImsEcbmStateListener() {
                @Override
                public void onECBMEntered() {
                    if (DBG) Rlog.d(LOG_TAG, "onECBMEntered");
                    handleEnterEmergencyCallbackMode();
                }

                @Override
                public void onECBMExited() {
                    if (DBG) Rlog.d(LOG_TAG, "onECBMExited");
                    handleExitEmergencyCallbackMode();
                }
            };

    public boolean isInEmergencyCall() {
        return mCT.isInEmergencyCall();
    }

    public boolean isInEcm() {
        return mIsPhoneInEcmState;
    }

    void sendEmergencyCallbackModeChange() {
        // Send an Intent
        Intent intent = new Intent(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        intent.putExtra(PhoneConstants.PHONE_IN_ECM_STATE, mIsPhoneInEcmState);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
        if (DBG) Rlog.d(LOG_TAG, "sendEmergencyCallbackModeChange");
    }

    @Override
    public void exitEmergencyCallbackMode() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (DBG) Rlog.d(LOG_TAG, "exitEmergencyCallbackMode()");

        // Send a message which will invoke handleExitEmergencyCallbackMode
        ImsEcbm ecbm;
        try {
            ecbm = mCT.getEcbmInterface();
            ecbm.exitEmergencyCallbackMode();
        } catch (ImsException e) {
            e.printStackTrace();
        }
    }

    private void handleEnterEmergencyCallbackMode() {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleEnterEmergencyCallbackMode,mIsPhoneInEcmState= "
                    + mIsPhoneInEcmState);
        }
        // if phone is not in Ecm mode, and it's changed to Ecm mode
        if (mIsPhoneInEcmState == false) {
            mIsPhoneInEcmState = true;
            // notify change
            sendEmergencyCallbackModeChange();
            setSystemProperty(TelephonyProperties.PROPERTY_INECM_MODE, "true");

            // Post this runnable so we will automatically exit
            // if no one invokes exitEmergencyCallbackMode() directly.
            long delayInMillis = SystemProperties.getLong(
                    TelephonyProperties.PROPERTY_ECM_EXIT_TIMER, DEFAULT_ECM_EXIT_TIMER_VALUE);
            postDelayed(mExitEcmRunnable, delayInMillis);
            // We don't want to go to sleep while in Ecm
            mWakeLock.acquire();
        }
    }

    private void handleExitEmergencyCallbackMode() {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleExitEmergencyCallbackMode: mIsPhoneInEcmState = "
                    + mIsPhoneInEcmState);
        }
        // Remove pending exit Ecm runnable, if any
        removeCallbacks(mExitEcmRunnable);

        if (mEcmExitRespRegistrant != null) {
            mEcmExitRespRegistrant.notifyResult(Boolean.TRUE);
        }
            if (mIsPhoneInEcmState) {
                mIsPhoneInEcmState = false;
                setSystemProperty(TelephonyProperties.PROPERTY_INECM_MODE, "false");
            }
            // send an Intent
            sendEmergencyCallbackModeChange();
    }

    /**
     * Handle to cancel or restart Ecm timer in emergency call back mode if action is
     * CANCEL_ECM_TIMER, cancel Ecm timer and notify apps the timer is canceled; otherwise, restart
     * Ecm timer and notify apps the timer is restarted.
     */
    void handleTimerInEmergencyCallbackMode(int action) {
        switch (action) {
            case CANCEL_ECM_TIMER:
                removeCallbacks(mExitEcmRunnable);
                if (mDefaultPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                    ((GSMPhone) mDefaultPhone).notifyEcbmTimerReset(Boolean.TRUE);
                } else { // Should be CDMA - also go here by default
                    ((CDMAPhone) mDefaultPhone).notifyEcbmTimerReset(Boolean.TRUE);
                }
                break;
            case RESTART_ECM_TIMER:
                long delayInMillis = SystemProperties.getLong(
                        TelephonyProperties.PROPERTY_ECM_EXIT_TIMER, DEFAULT_ECM_EXIT_TIMER_VALUE);
                postDelayed(mExitEcmRunnable, delayInMillis);
                if (mDefaultPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                    ((GSMPhone) mDefaultPhone).notifyEcbmTimerReset(Boolean.FALSE);
                } else { // Should be CDMA - also go here by default
                    ((CDMAPhone) mDefaultPhone).notifyEcbmTimerReset(Boolean.FALSE);
                }
                break;
            default:
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + action);
        }
    }

    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        mEcmExitRespRegistrant = new Registrant(h, what, obj);
    }

    public void unsetOnEcbModeExitResponse(Handler h) {
        mEcmExitRespRegistrant.clear();
    }

    public boolean isVolteEnabled() {
        return mCT.isVolteEnabled();
    }

    public boolean isVtEnabled() {
        return mCT.isVtEnabled();
    }

    public Phone getDefaultPhone() {
        return mDefaultPhone;
    }

    public boolean isImsRegistered() {
        return mImsRegistered;
    }

    public void setImsRegistered(boolean value) {
        mImsRegistered = value;
    }

    public void callEndCleanupHandOverCallIfAny() {
        mCT.callEndCleanupHandOverCallIfAny();
    }

    @Override
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        if (DBG) {
            Rlog.d(LOG_TAG, "getOutgoingCallerIdDisplay");
        }
        Message resp;
        resp = obtainMessage(EVENT_GET_CLIR_DONE, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.queryCLIR(resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    @Override
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode,
            Message onComplete) {
        if (DBG) {
            Rlog.d(LOG_TAG, "setOutgoingCallerIdDisplay: commandInterfaceCLIRMode="
                    + commandInterfaceCLIRMode);
        }
        Message resp;
        resp = obtainMessage(EVENT_SET_CLIR_DONE, commandInterfaceCLIRMode, 0, onComplete);

        try {
            ImsUtInterface ut = mCT.getUtInterface();
            ut.updateCLIR(commandInterfaceCLIRMode, resp);
        } catch (ImsException e) {
            sendErrorResponse(onComplete, e);
        }
    }

    /**
     * CS Fall back to GSMPhone for MMI code.
     *
     *@param reason the reason for CS fallback
     */
    public void handleMmiCodeCsfb(int reason) {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleMmiCodeCsfb: reason = " + reason + ", mDialString = "
                    + mDialString);
        }
        if (reason == ImsReasonInfo.CODE_UT_XCAP_403_FORBIDDEN) {
            mDefaultPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_UNTIL_NEXT_BOOT);
        } else if (reason == ImsReasonInfo.CODE_UT_UNKNOWN_HOST) {
            mDefaultPhone.setCsFallbackStatus(PhoneConstants.UT_CSFB_ONCE);
        }
        SuppSrvRequest ss = SuppSrvRequest.obtain(SuppSrvRequest.SUPP_SRV_REQ_MMI_CODE, null);
        ss.mParcel.writeString(mDialString);
        Message msgCSFB = mDefaultPhone.obtainMessage(EVENT_IMS_UT_CSFB, ss);

        mDefaultPhone.sendMessage(msgCSFB);
    }

    /// M: SS OP01 Ut @{
    private static class CfEx {
        final String mSetCfNumber;
        final long[] mSetTimeSlot;
        final Message mOnComplete;
        final boolean mIsCfu;

        CfEx(String cfNumber, long[] cfTimeSlot, boolean isCfu, Message onComplete) {
            mSetCfNumber = cfNumber;
            mSetTimeSlot = cfTimeSlot;
            mIsCfu = isCfu;
            mOnComplete = onComplete;
        }
    }

    @Override
    public void getCallForwardInTimeSlot(int commandInterfaceCFReason,
            Message onComplete) {
        if (DBG) {
            Rlog.d(LOG_TAG, "getCallForwardInTimeSlot reason = " + commandInterfaceCFReason);
        }
        if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
            if (DBG) {
                Rlog.d(LOG_TAG, "requesting call forwarding in a time slot query.");
            }
            Message resp;
            resp = obtainMessage(EVENT_GET_CALL_FORWARD_TIME_SLOT_DONE, onComplete);

            try {
                ImsUtInterface ut = mCT.getUtInterface();
                ut.queryCallForwardInTimeSlot(
                        getConditionFromCFReason(commandInterfaceCFReason),
                        resp);
            } catch (ImsException e) {
                sendErrorResponse(onComplete, e);
            }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    @Override
    public void setCallForwardInTimeSlot(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            long[] timeSlot,
            Message onComplete) {
        if (DBG) {
            Rlog.d(LOG_TAG, "setCallForwardInTimeSlot action = " + commandInterfaceCFAction
                    + ", reason = " + commandInterfaceCFReason);
        }
        if ((isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL)) {
            Message resp;
            CfEx cfEx = new CfEx(dialingNumber, timeSlot,
                    (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL ? true : false),
                    onComplete);
            resp = obtainMessage(EVENT_SET_CALL_FORWARD_TIME_SLOT_DONE,
                    isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, cfEx);

            try {
                ImsUtInterface ut = mCT.getUtInterface();
                ut.updateCallForwardInTimeSlot(getActionFromCFAction(commandInterfaceCFAction),
                        getConditionFromCFReason(commandInterfaceCFReason),
                        dialingNumber,
                        timerSeconds,
                        timeSlot,
                        resp);
             } catch (ImsException e) {
                sendErrorResponse(onComplete, e);
             }
        } else if (onComplete != null) {
            sendErrorResponse(onComplete);
        }
    }

    private CallForwardInfoEx[] handleCfInTimeSlotQueryResult(ImsCallForwardInfoEx[] infos) {
        CallForwardInfoEx[] cfInfos = null;

        if (infos != null && infos.length != 0) {
            cfInfos = new CallForwardInfoEx[infos.length];
        }

        IccRecords r = getIccRecords();
        if (infos == null || infos.length == 0) {
            if (r != null) {
                // Assume the default is not active
                // Set unconditional CFF in SIM to false
                r.setVoiceCallForwardingFlag(1, false, null);
            }
        } else {
            for (int i = 0, s = infos.length; i < s; i++) {
                if (infos[i].mCondition == ImsUtInterface.CDIV_CF_UNCONDITIONAL) {
                    if (r != null) {
                        r.setVoiceCallForwardingFlag(1, (infos[i].mStatus == 1),
                                infos[i].mNumber);
                        saveTimeSlot(infos[i].mTimeSlot);
                    }
                }
                cfInfos[i] = getCallForwardInfoEx(infos[i]);
            }
        }

        return cfInfos;
    }

    private CallForwardInfoEx getCallForwardInfoEx(ImsCallForwardInfoEx info) {
        CallForwardInfoEx cfInfo = new CallForwardInfoEx();
        cfInfo.status = info.mStatus;
        cfInfo.reason = getCFReasonFromCondition(info.mCondition);
        cfInfo.serviceClass = info.mServiceClass;
        cfInfo.toa = info.mToA;
        cfInfo.number = info.mNumber;
        cfInfo.timeSeconds = info.mTimeSeconds;
        cfInfo.timeSlot = info.mTimeSlot;
        return cfInfo;
    }

    @Override
    public void hangupAll() throws CallStateException {
        if (DBG) {
            Rlog.d(LOG_TAG, "hangupAll");
        }
        mCT.hangupAll();
    }
    /// @}

    /// M: Add the new API to get the CommandsInterface @{
    //@Override
    public CommandsInterface getCommandsInterface() {
        return mDefaultPhone.mCi;
    }
    /// @}

    /// M: For VoLTE enhanced conference call. @{
    @Override
    public Connection dial(List<String> numbers, int videoState)
            throws CallStateException {
        return mCT.dial(numbers, videoState);
    }
    /// @}

    /// M: ALPS01953873. @{
    /**
     * Query if currently this phone support the specific feature.
     * @param feature defineded in Phone.java
     * @return true if supporting.
     * @hide
     */
    @Override
    public boolean isFeatureSupported(FeatureType feature) {
        List<String> featureSupportedMccMncList = null;
        String featureName = null;

        if (feature == FeatureType.VOLTE_ENHANCED_CONFERENCE) {
            featureSupportedMccMncList = Arrays.asList(
                    // 1. CMCC:
                    "46000", "46002", "46007", "46008", "46011");
            featureName = "VOLTE_ENHANCED_CONFERENCE";
        } else if (feature == FeatureType.VOLTE_CONF_REMOVE_MEMBER) {
            // Always support remove member.
            return true;
        }

        if (featureSupportedMccMncList == null) {
            Rlog.d(LOG_TAG, "isFeatureSupported: unknown feature: " + feature);
            return false;
        } else if (isCurrentIccInList(featureSupportedMccMncList)) {
            Rlog.d(LOG_TAG, "isFeatureSupported(" + featureName + "): ret=true");
            return true;
        } else {
            Rlog.d(LOG_TAG, "isFeatureSupported(" + featureName + "): ret=false");
            return false;
        }
    }

    /**
     * Given a MCC/MNC list, check if the MCC/MNC of current SIM is in the specific list.
     *
     * @param mccMncList the given MCC/MNC list
     * @return true if current SIM is in the specific list.
     */
    private boolean isCurrentIccInList(List<String> mccMncList) {
        if (mccMncList == null) {
            return false;
        }

        IccRecords iccRecords = getIccRecords();
        if (iccRecords == null) {
            Rlog.d(LOG_TAG, "no Icc records");
            return false;
        }

        String mccMnc = iccRecords.getOperatorNumeric();
        Rlog.d(LOG_TAG, "current mccMnc = " + mccMnc);
        return mccMncList.contains(mccMnc);
    }
    /// @}
}
