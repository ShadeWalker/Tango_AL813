/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony.gsm;

import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.EventLog;
import android.telephony.Rlog;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.gsm.CallFailCause;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.GsmCall;
import com.android.internal.telephony.gsm.GsmConnection;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;

/// M: for SRVCC @{
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
/// @}

/// M: For 3G VT only @{
import android.os.RemoteException;

import com.mediatek.internal.telephony.gsm.GsmVTProvider;
import com.mediatek.internal.telephony.gsm.GsmVideoCallProviderWrapper;
import com.mediatek.internal.telephony.gsm.IGsmVideoCallProvider;
import android.telecom.VideoProfile;
/// @}

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
import java.util.ArrayList;

/**
 * {@hide}
 */
public final class GsmCallTracker extends CallTracker {
    static final String LOG_TAG = "GsmCallTracker";
    private static final boolean REPEAT_POLLING = false;

    private static final boolean DBG_POLL = Build.TYPE.equals("eng");/* HQ_guomiao 2015-10-27 modified for HQ01444267 */

    //***** Constants

    static final int MAX_CONNECTIONS = 7;   // only 7 connections allowed in GSM
    static final int MAX_CONNECTIONS_PER_CALL = 5; // only 5 connections allowed per call

    //***** Instance Variables
    GsmConnection mConnections[] = new GsmConnection[MAX_CONNECTIONS];
    RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();
    /// M: CC010: Add RIL interface @{
    RegistrantList mVoiceCallIncomingIndicationRegistrants = new RegistrantList();
    /// @}

    // connections dropped during last poll
    ArrayList<GsmConnection> mDroppedDuringPoll
        = new ArrayList<GsmConnection>(MAX_CONNECTIONS);

    GsmCall mRingingCall = new GsmCall(this);
            // A call that is ringing or (call) waiting
    GsmCall mForegroundCall = new GsmCall(this);
    GsmCall mBackgroundCall = new GsmCall(this);

    GsmConnection mPendingMO;
    boolean mHangupPendingMO;

    GSMPhone mPhone;

    boolean mDesiredMute = false;    // false = mute off

    /// M: CC011: Use GsmCallTrackerHelper @{
    // Declare as public for GsmCallTrackerHelper to use
    public PhoneConstants.State mState = PhoneConstants.State.IDLE;
    /// @}

    Call.SrvccState mSrvccState = Call.SrvccState.NONE;

    /// M: CC011: Use GsmCallTrackerHelper @{
    GsmCallTrackerHelper mHelper;
    /// @}

    /// M: For 3G VT only @{
    /* voice&video waiting */
    boolean hasPendingReplaceRequest = false;
    /// @}

    /// M: CC015: CRSS special handling @{
    boolean mHasPendingSwapRequest = false;
    WaitingForHold mWaitingForHoldRequest = new WaitingForHold();
    /// @}

    /// M: CC010: Add RIL interface @{
    private String mPendingCnap = null;
    /// @}
    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    private static final String STR_PROPERTY_HD_VOICE = "persist.radio.hd.voice";
    private int mSpeechCodecType = 0;
    /// @}

    ///M: IMS conference call feature. @{
    private ArrayList<Connection> mImsConfParticipants = new ArrayList<Connection>();

    // for SRVCC purpose, put conference connection Ids temporarily
    private int[] mEconfSrvccConnectionIds = null;
    /// @}

    /// M: CC015: CRSS special handling @{
    class WaitingForHold {

        private boolean mWaiting = false;
        private String mDialString = null;
        private int mClirMode = 0;
        private UUSInfo mUUSInfo = null;

        WaitingForHold() {
            reset();
        }

        boolean isWaiting() {
            return mWaiting;
        }

        void set() {
            mWaiting = true;
        }

        public void set(String dialSting, int clir, UUSInfo uusinfo) {
            mWaiting = true;
            mDialString = dialSting;
            mClirMode = clir;
            mUUSInfo = uusinfo;
        }

        public void reset() {

            Rlog.d(LOG_TAG, "Reset WaitingForHoldRequest variables");

            mWaiting = false;
            mDialString = null;
            mClirMode = 0;
            mUUSInfo = null;
        }

        /**
         * Check if there is another action need to be performed after holding request is done.
         *
         * @return Return true if there exists action need to be perform, else return false.
         */
        private boolean handleOperation() {
            Rlog.d(LOG_TAG, "handleWaitingOperation begin");

            if (mWaiting) {
                mCi.dial(mDialString, mClirMode, mUUSInfo, obtainCompleteMessage());

                /// M: For 3G VT only @{
                if (SystemProperties.get("ro.mtk_vt3g324m_support").equals("1")) {
                    //MO:new VT service
                    mForegroundCall.mVTProvider = new GsmVTProvider();
                    Rlog.d(LOG_TAG, "handleOperation new GsmVTProvider");
                    try {
                        IGsmVideoCallProvider gsmVideoCallProvider =
                                mForegroundCall.mVTProvider.getInterface();
                        if (gsmVideoCallProvider != null) {
                            GsmVideoCallProviderWrapper gsmVideoCallProviderWrapper =
                                    new GsmVideoCallProviderWrapper(gsmVideoCallProvider);
                            Rlog.d(LOG_TAG, "handleOperation new GsmVideoCallProviderWrapper");
                            mPendingMO.setVideoProvider(gsmVideoCallProviderWrapper);
                        }
                    } catch (RemoteException e) {
                        Rlog.d(LOG_TAG, "handleOperation new GsmVideoCallProviderWrapper failed");
                    }
                }
                /// @}

                reset();
                Rlog.d(LOG_TAG, "handleWaitingOperation end");
                return true;
            }
            return false;
        }
    }
    /// @}

    //***** Events

    //***** Constructors

    GsmCallTracker (GSMPhone phone) {
        this.mPhone = phone;
        mCi = phone.mCi;

        mCi.registerForCallStateChanged(this, EVENT_CALL_STATE_CHANGE, null);

        mCi.registerForOn(this, EVENT_RADIO_AVAILABLE, null);
        mCi.registerForNotAvailable(this, EVENT_RADIO_NOT_AVAILABLE, null);

        /// M: CC010: Add RIL interface @{
        mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mCi.setOnIncomingCallIndication(this, EVENT_INCOMING_CALL_INDICATION, null);
        mCi.setCnapNotify(this, EVENT_CNAP_INDICATION, null);
        /// @}
        /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
        mCi.setOnSpeechCodecInfo(this, EVENT_SPEECH_CODEC_INFO, null);
        /// @}
        /// M: CC011: Use GsmCallTrackerHelper @{
        mHelper = new GsmCallTrackerHelper(phone.getContext(), this);
        /// @}
        ///M: IMS conference call feature. @{
        mCi.registerForEconfSrvcc(this, EVENT_ECONF_SRVCC_INDICATION, null);
        /// @}
    }

    public void dispose() {
        Rlog.d(LOG_TAG, "GsmCallTracker dispose");
        //Unregister for all events
        mCi.unregisterForCallStateChanged(this);
        mCi.unregisterForOn(this);
        mCi.unregisterForNotAvailable(this);

        /// M: CC010: Add RIL interface @{
        mCi.unregisterForOffOrNotAvailable(this);
        mCi.unsetOnIncomingCallIndication(this);
        mCi.unSetCnapNotify(this);
        /// @}
        /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
        mCi.unSetOnSpeechCodecInfo(this);
        /// @}

        clearDisconnected();
    }

    @Override
    protected void finalize() {
        Rlog.d(LOG_TAG, "GsmCallTracker finalized");
    }

    //***** Instance Methods

    //***** Public Methods
    @Override
    public void registerForVoiceCallStarted(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mVoiceCallStartedRegistrants.add(r);
    }

    @Override
    public void unregisterForVoiceCallStarted(Handler h) {
        mVoiceCallStartedRegistrants.remove(h);
    }

    /// M: CC010: Add RIL interface @{
    public void registerForVoiceCallIncomingIndication(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mVoiceCallIncomingIndicationRegistrants.add(r);
    }

    public void unregisterForVoiceCallIncomingIndication(Handler h) {
        mVoiceCallIncomingIndicationRegistrants.remove(h);
    }
    /// @}

    @Override
    public void registerForVoiceCallEnded(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mVoiceCallEndedRegistrants.add(r);
    }

    @Override
    public void unregisterForVoiceCallEnded(Handler h) {
        mVoiceCallEndedRegistrants.remove(h);
    }

    private void
    fakeHoldForegroundBeforeDial() {
        List<Connection> connCopy;

        // We need to make a copy here, since fakeHoldBeforeDial()
        // modifies the lists, and we don't want to reverse the order
        connCopy = (List<Connection>) mForegroundCall.mConnections.clone();

        for (int i = 0, s = connCopy.size() ; i < s ; i++) {
            GsmConnection conn = (GsmConnection)connCopy.get(i);

            conn.fakeHoldBeforeDial();
        }
    }

    /// M: CC015: CRSS special handling @{
    private void resumeBackgroundAfterDialFailed() {
        // We need to make a copy here, since fakeHoldBeforeDial()
        // modifies the lists, and we don't want to reverse the order
        List<Connection> connCopy = (List<Connection>) mBackgroundCall.mConnections.clone();

        for (int i = 0, s = connCopy.size() ; i < s ; i++) {
            GsmConnection conn = (GsmConnection) connCopy.get(i);

            conn.resumeHoldAfterDialFailed();
        }
    }
    /// @}

    /// M: For 3G VT only @{
    /**
     * clirMode is one of the CLIR_ constants.
     */
    synchronized Connection
    vtDial(String dialString, int clirMode, UUSInfo uusInfo) throws CallStateException {
        // note that this triggers call state changed notif
        clearDisconnected();

        if (!canDial()) {
            throw new CallStateException("cannot vtDial in current state");
        }

        if (!canVtDial()) {
            throw new CallStateException("cannot vtDial in current state");
        }

        String origNumber = dialString;
        dialString = convertNumberIfNecessary(mPhone, dialString);

        if (mForegroundCall.getState() != GsmCall.State.IDLE) {
            //we should have failed in !canDial() above before we get here
            throw new CallStateException("cannot vtDial in current state");
        }

        mPendingMO = new GsmConnection(mPhone.getContext(), checkForTestEmergencyNumber(dialString),
                this, mForegroundCall);
        mHangupPendingMO = false;
        mPendingMO.mIsVideo = true;

        if (mPendingMO.getAddress() == null || mPendingMO.getAddress().length() == 0
                || mPendingMO.getAddress().indexOf(PhoneNumberUtils.WILD) >= 0
        ) {
            // Phone number is invalid
            mPendingMO.mCause = DisconnectCause.INVALID_NUMBER;

            // handlePollCalls() will notice this call not present
            // and will mark it as dropped.
            pollCallsWhenSafe();
        } else {
            // Always unmute when initiating a new call
            setMute(false);

            mCi.vtDial(mPendingMO.getAddress(), clirMode, uusInfo,
                    obtainCompleteMessage(EVENT_DIAL_CALL_RESULT));

            mPendingMO.setVideoState(VideoProfile.VideoState.BIDIRECTIONAL);

            //MO:new VT service
            mForegroundCall.mVTProvider = new GsmVTProvider();
            Rlog.d(LOG_TAG, "vtDial new GsmVTProvider");
            try {
                IGsmVideoCallProvider gsmVideoCallProvider =
                        mForegroundCall.mVTProvider.getInterface();
                if (gsmVideoCallProvider != null) {
                    GsmVideoCallProviderWrapper gsmVideoCallProviderWrapper =
                            new GsmVideoCallProviderWrapper(gsmVideoCallProvider);
                    Rlog.d(LOG_TAG, "vtDial new GsmVideoCallProviderWrapper");
                    mPendingMO.setVideoProvider(gsmVideoCallProviderWrapper);
                }
            } catch (RemoteException e) {
                Rlog.d(LOG_TAG, "vtDial new GsmVideoCallProviderWrapper failed");
            }
        }

        if (mNumberConverted) {
            mPendingMO.setConverted(origNumber);
            mNumberConverted = false;
        }

        updatePhoneState();
        mPhone.notifyPreciseCallStateChanged();

        return mPendingMO;
    }

    Connection
    vtDial(String dialString, UUSInfo uusInfo) throws CallStateException {
        return vtDial(dialString, CommandsInterface.CLIR_DEFAULT, uusInfo);
    }
    /// @}

    /**
     * clirMode is one of the CLIR_ constants
     */
    synchronized Connection
    dial (String dialString, int clirMode, UUSInfo uusInfo) throws CallStateException {
        // note that this triggers call state changed notif
        clearDisconnected();

        if (!canDial()) {
            throw new CallStateException("cannot dial in current state");
        }

        String origNumber = dialString;
        dialString = convertNumberIfNecessary(mPhone, dialString);

        // The new call must be assigned to the foreground call.
        // That call must be idle, so place anything that's
        // there on hold
        if (mForegroundCall.getState() == GsmCall.State.ACTIVE) {
            // this will probably be done by the radio anyway
            // but the dial might fail before this happens
            // and we need to make sure the foreground call is clear
            // for the newly dialed connection

            /// M: CC015: CRSS special handling @{
            mWaitingForHoldRequest.set();
            /// @}

            switchWaitingOrHoldingAndActive();

            // Fake local state so that
            // a) foregroundCall is empty for the newly dialed connection
            // b) hasNonHangupStateChanged remains false in the
            // next poll, so that we don't clear a failed dialing call
            fakeHoldForegroundBeforeDial();
        }

        if (mForegroundCall.getState() != GsmCall.State.IDLE) {
            //we should have failed in !canDial() above before we get here
            throw new CallStateException("cannot dial in current state");
        }

        mPendingMO = new GsmConnection(mPhone.getContext(), checkForTestEmergencyNumber(dialString),
                this, mForegroundCall);
        mHangupPendingMO = false;

        if ( mPendingMO.getAddress() == null || mPendingMO.getAddress().length() == 0
                || mPendingMO.getAddress().indexOf(PhoneNumberUtils.WILD) >= 0
        ) {
            // Phone number is invalid
            mPendingMO.mCause = DisconnectCause.INVALID_NUMBER;

            /// M: CC015: CRSS special handling @{
            mWaitingForHoldRequest.reset();
            /// @}

            // handlePollCalls() will notice this call not present
            // and will mark it as dropped.
            pollCallsWhenSafe();
        } else {
            // Always unmute when initiating a new call
            setMute(false);

            /// M: CC010: Add RIL interface @{
			// hhq fix for ecc bug start 
            /*if (PhoneNumberUtils.isEmergencyNumber(dialString)  && !PhoneNumberUtils.isSpecialEmergencyNumber(dialString)) {*/
			if (/*PhoneNumberUtils.isEmergencyNumber(dialString)*/PhoneNumberUtils.isEmergencyNumberExt(dialString, PhoneConstants.PHONE_TYPE_GSM)
				&& !PhoneNumberUtils.isSpecialEmergencyNumber(dialString)) {
			// hhq end

                int serviceCategory = PhoneNumberUtils.getServiceCategoryFromEcc(dialString);
                mCi.setEccServiceCategory(serviceCategory);
                mCi.emergencyDial(mPendingMO.getAddress(), clirMode, uusInfo, obtainCompleteMessage(EVENT_DIAL_CALL_RESULT));
            /// @}
            /// M: CC015: CRSS special handling @{
            } else {
                if (!mWaitingForHoldRequest.isWaiting()) {
                    mCi.dial(mPendingMO.getAddress(), clirMode, uusInfo, obtainCompleteMessage(EVENT_DIAL_CALL_RESULT));
                } else {
                    mWaitingForHoldRequest.set(mPendingMO.getAddress(), clirMode, uusInfo);
                }
            }
            /// @}
        }

        if (mNumberConverted) {
            mPendingMO.setConverted(origNumber);
            mNumberConverted = false;
        }

        updatePhoneState();
        mPhone.notifyPreciseCallStateChanged();

        return mPendingMO;
    }

    Connection
    dial(String dialString) throws CallStateException {
        return dial(dialString, CommandsInterface.CLIR_DEFAULT, null);
    }

    Connection
    dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        return dial(dialString, CommandsInterface.CLIR_DEFAULT, uusInfo);
    }

    Connection
    dial(String dialString, int clirMode) throws CallStateException {
        return dial(dialString, clirMode, null);
    }

    /// M: For 3G VT only @{
    void
    acceptCall(int videoState) throws CallStateException {
        // FIXME if SWITCH fails, should retry with ANSWER
        // in case the active/holding call disappeared and this
        // is no longer call waiting

        if (mRingingCall.getState() == GsmCall.State.INCOMING) {
            Rlog.i("phone", "acceptCall: incoming...");
            // Always unmute when answering a new call
            setMute(false);
            //if (SystemProperties.get("ro.mtk_vt3g324m_support").equals("1")) {
                GsmConnection cn = (GsmConnection) mRingingCall.mConnections.get(0);
                if (cn.isVideo()) {
                    if (videoState == VideoProfile.VideoState.AUDIO_ONLY) {
                        mCi.acceptVtCallWithVoiceOnly(cn.getGSMIndex(), obtainCompleteMessage());
                        cn.setVideoState(VideoProfile.VideoState.AUDIO_ONLY);
                        return;
                    }
                }
            //}
            mCi.acceptCall(obtainCompleteMessage());
        } else if (mRingingCall.getState() == GsmCall.State.WAITING) {
            setMute(false);
            //if (SystemProperties.get("ro.mtk_vt3g324m_support").equals("1")) {
                GsmConnection cn = (GsmConnection) mRingingCall.mConnections.get(0);
                if (cn.isVideo()) {
                    GsmConnection fgCn = (GsmConnection) mForegroundCall.mConnections.get(0);
                    if (fgCn != null && fgCn.isVideo()) {
                        hasPendingReplaceRequest = true;
                        mCi.replaceVtCall(fgCn.mIndex + 1, obtainCompleteMessage());
                        fgCn.onHangupLocal();
                        return;
                    }
                }
            //}
            switchWaitingOrHoldingAndActive();
        } else {
            throw new CallStateException("phone not ringing");
        }
    }
    /// @}

    void
    acceptCall () throws CallStateException {
        // FIXME if SWITCH fails, should retry with ANSWER
        // in case the active/holding call disappeared and this
        // is no longer call waiting

        if (mRingingCall.getState() == GsmCall.State.INCOMING) {
            Rlog.i("phone", "acceptCall: incoming...");
            // Always unmute when answering a new call
            setMute(false);
            mCi.acceptCall(obtainCompleteMessage());
        } else if (mRingingCall.getState() == GsmCall.State.WAITING) {
            setMute(false);
            switchWaitingOrHoldingAndActive();
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    void
    rejectCall () throws CallStateException {
        // AT+CHLD=0 means "release held or UDUB"
        // so if the phone isn't ringing, this could hang up held
        if (mRingingCall.getState().isRinging()) {
            mCi.rejectCall(obtainCompleteMessage());
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    void
    switchWaitingOrHoldingAndActive() throws CallStateException {
        // Should we bother with this check?
        if (mRingingCall.getState() == GsmCall.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        } else {
            /// M: CC015: CRSS special handling @{
            if (!mHasPendingSwapRequest) {
                mCi.switchWaitingOrHoldingAndActive(
                        obtainCompleteMessage(EVENT_SWITCH_RESULT));
                mHasPendingSwapRequest = true;
            }
            /// @}
        }
    }

    void
    conference() {
        mCi.conference(obtainCompleteMessage(EVENT_CONFERENCE_RESULT));
    }

    void
    explicitCallTransfer() {
        mCi.explicitCallTransfer(obtainCompleteMessage(EVENT_ECT_RESULT));
    }

    void
    clearDisconnected() {
        internalClearDisconnected();

        updatePhoneState();
        mPhone.notifyPreciseCallStateChanged();
    }

    boolean
    canConference() {
        return mForegroundCall.getState() == GsmCall.State.ACTIVE
                && mBackgroundCall.getState() == GsmCall.State.HOLDING
                && !mBackgroundCall.isFull()
                && !mForegroundCall.isFull();
    }

    boolean
    canDial() {
        boolean ret;
        int serviceState = mPhone.getServiceState().getState();
        String disableCall = SystemProperties.get(
                TelephonyProperties.PROPERTY_DISABLE_CALL, "false");

        ret = (serviceState != ServiceState.STATE_POWER_OFF)
                && mPendingMO == null
                && !mRingingCall.isRinging()
                && !disableCall.equals("true")
                && (!mForegroundCall.getState().isAlive()
                    || !mBackgroundCall.getState().isAlive());

        return ret;
    }

    /// M: For 3G VT only @{
    boolean
    canVtDial() {
        boolean ret;
        int networkType = mPhone.getServiceState().getNetworkType();
        Rlog.d(LOG_TAG, "networkType=" + TelephonyManager.getNetworkTypeName(networkType));

        ret = (networkType == TelephonyManager.NETWORK_TYPE_UMTS ||
                networkType == TelephonyManager.NETWORK_TYPE_HSDPA ||
                networkType == TelephonyManager.NETWORK_TYPE_HSUPA ||
                networkType == TelephonyManager.NETWORK_TYPE_HSPA ||
                networkType == TelephonyManager.NETWORK_TYPE_HSPAP ||
                networkType == TelephonyManager.NETWORK_TYPE_LTE);

        return ret;
    }
    /// @}

    boolean
    canTransfer() {
        return (mForegroundCall.getState() == GsmCall.State.ACTIVE
                || mForegroundCall.getState() == GsmCall.State.ALERTING
                || mForegroundCall.getState() == GsmCall.State.DIALING)
            && mBackgroundCall.getState() == GsmCall.State.HOLDING;
    }

    //***** Private Instance Methods

    private void
    internalClearDisconnected() {
        mRingingCall.clearDisconnected();
        mForegroundCall.clearDisconnected();
        mBackgroundCall.clearDisconnected();
    }

    /**
     * Obtain a message to use for signalling "invoke getCurrentCalls() when
     * this operation and all other pending operations are complete
     */
    /// M: CC011: Use GsmCallTrackerHelper @{
    // Declare as protected (not priviate) for GsmCallTrackerHelper to use
    protected Message
    /// @}
    obtainCompleteMessage() {
        return obtainCompleteMessage(EVENT_OPERATION_COMPLETE);
    }

    /**
     * Obtain a message to use for signalling "invoke getCurrentCalls() when
     * this operation and all other pending operations are complete
     */
    /// M: CC011: Use GsmCallTrackerHelper @{
    // Declare as protected (not priviate) for GsmCallTrackerHelper to use
    protected Message
    /// @}
    obtainCompleteMessage(int what) {
        mPendingOperations++;
        mLastRelevantPoll = null;
        mNeedsPoll = true;

        if (DBG_POLL) log("obtainCompleteMessage: pendingOperations=" +
                mPendingOperations + ", needsPoll=" + mNeedsPoll);

        return obtainMessage(what);
    }

    private void
    operationComplete() {
        mPendingOperations--;

        if (DBG_POLL) log("operationComplete: pendingOperations=" +
                mPendingOperations + ", needsPoll=" + mNeedsPoll);

        if (mPendingOperations == 0 && mNeedsPoll) {
            mLastRelevantPoll = obtainMessage(EVENT_POLL_CALLS_RESULT);
            mCi.getCurrentCalls(mLastRelevantPoll);
        } else if (mPendingOperations < 0) {
            // this should never happen
            Rlog.e(LOG_TAG,"GsmCallTracker.pendingOperations < 0");
            mPendingOperations = 0;
        }
    }

    private void
    updatePhoneState() {
        PhoneConstants.State oldState = mState;
        if (mRingingCall.isRinging()) {
            mState = PhoneConstants.State.RINGING;
        } else if (mPendingMO != null ||
                !(mForegroundCall.isIdle() && mBackgroundCall.isIdle())) {
            mState = PhoneConstants.State.OFFHOOK;
        } else {
            ImsPhone imsPhone = (ImsPhone)mPhone.getImsPhone();
            /// M: ALPS02192901. @{
            // If the call is disconnected after CIREPH=1, before +CLCC, the original state is
            // idle and new state is still idle, so callEndCleanupHandOverCallIfAny isn't called.
            // Related CR: ALPS02015368, ALPS02161020, ALPS02192901.
            //if ( mState == PhoneConstants.State.OFFHOOK && (imsPhone != null)){
            if (imsPhone != null) {
                /// @}
                imsPhone.callEndCleanupHandOverCallIfAny();
            }
            mState = PhoneConstants.State.IDLE;
        }

        if (mState == PhoneConstants.State.IDLE && oldState != mState) {
            mVoiceCallEndedRegistrants.notifyRegistrants(
                new AsyncResult(null, null, null));
        } else if (oldState == PhoneConstants.State.IDLE && oldState != mState) {
            mVoiceCallStartedRegistrants.notifyRegistrants (
                    new AsyncResult(null, null, null));
        }
        log("updatePhoneState: old: " + oldState + " , new: " + mState);
        if (mState != oldState) {
            mPhone.notifyPhoneStateChanged();
        }
    }

    @Override
    protected synchronized void
    handlePollCalls(AsyncResult ar) {
        List polledCalls;

        if (ar.exception == null) {
            polledCalls = (List)ar.result;
        } else if (isCommandExceptionRadioNotAvailable(ar.exception)) {
            // just a dummy empty ArrayList to cause the loop
            // to hang up all the calls
            polledCalls = new ArrayList();
        } else if (mNeedWaitImsEConfSrvcc) {
            /// M: ALPS02019630. @{
            log("SRVCC: +ECONFSRVCC is still not arrival, skip this poll call.");
            return;
            /// @}
        } else {
            // Radio probably wasn't ready--try again in a bit
            // But don't keep polling if the channel is closed
            pollCallsAfterDelay();
            return;
        }

        Connection newRinging = null; //or waiting
        Connection newUnknown = null;
        boolean hasNonHangupStateChanged = false;   // Any change besides
                                                    // a dropped connection
        boolean hasAnyCallDisconnected = false;
        boolean needsPollDelay = false;
        boolean unknownConnectionAppeared = false;

        for (int i = 0, curDC = 0, dcSize = polledCalls.size()
                ; i < mConnections.length; i++) {
            GsmConnection conn = mConnections[i];
            DriverCall dc = null;

            // polledCall list is sparse
            if (curDC < dcSize) {
                dc = (DriverCall) polledCalls.get(curDC);

                if (dc.index == i+1) {
                    curDC++;
                } else {
                    dc = null;
                }
            }

            if (DBG_POLL) log("poll: conn[i=" + i + "]=" +
                    conn+", dc=" + dc);

            if (conn == null && dc != null) {
                /* M: CC part start */
                if (DBG_POLL) log("case 1 : new Call appear");

                /// M: CC010: Add RIL interface @{
                // give CLIP ALLOW default value, it will be changed on CLIP URC
                dc.numberPresentation = PhoneConstants.PRESENTATION_ALLOWED;
                /// @}
                /* M: CC part end */

                // Connection appeared in CLCC response that we don't know about
                if (mPendingMO != null && mPendingMO.compareTo(dc)) {

                    if (DBG_POLL) log("poll: pendingMO=" + mPendingMO);

                    /// M: For 3G VT only @{
                    //MO:set id to VT service
                    if (SystemProperties.get("ro.mtk_vt3g324m_support").equals("1")) {
                        if ((mForegroundCall.mVTProvider != null) && dc.isVideo) {
                            mForegroundCall.mVTProvider.setId(i + 1);
                        }
                    }
                    /// @}

                    // It's our pending mobile originating call
                    mConnections[i] = mPendingMO;
                    mPendingMO.mIndex = i;
                    mPendingMO.update(dc);
                    mPendingMO = null;

                    // Someone has already asked to hangup this call
                    if (mHangupPendingMO) {
                        mHangupPendingMO = false;
                        try {
                            if (Phone.DEBUG_PHONE) log(
                                    "poll: hangupPendingMO, hangup conn " + i);
                            hangup(mConnections[i]);
                        } catch (CallStateException ex) {
                            Rlog.e(LOG_TAG, "unexpected error on hangup");
                        }

                        // Do not continue processing this poll
                        // Wait for hangup and repoll
                        return;
                    }
                } else {

                    /* M: CC part start */
                    /// M: CC050: Remove handling for MO/MT conflict, not hangup MT @{
                    if (mPendingMO != null && !mPendingMO.compareTo(dc)) {
                        log("MO/MT conflict! MO should be hangup by MD");
                    }
                    /// @}

                    mConnections[i] = new GsmConnection(mPhone.getContext(), dc, this, i);

                    /// M: CC010: Add RIL interface @{
                    if (mPendingCnap != null) {
                       mConnections[i].setCnapName(mPendingCnap);
                       mPendingCnap = null;
                    }
                    /// @}

                    /// M: CC052: DM&PPL @{
                    boolean checkFlag = mHelper.DmCheckIfCallCanComing(mConnections[i]);
                    /// @}

                    /// M: CC017: Forwarding number via EAIC @{
                    //To store forwarding address to connection object.
                    mHelper.setForwardingAddressToConnection(i, mConnections[i]);
                    /// @}

                    Connection hoConnection = getHoConnection(dc);
                    if (checkFlag && (hoConnection != null)) {
                        // Single Radio Voice Call Continuity (SRVCC) completed
                        //mConnections[i].migrateFrom(hoConnection);
                        //if (!hoConnection.isMultiparty()) {
                            // Remove only if it is not multiparty
                            //mHandoverConnections.remove(hoConnection);
                        //}
                        //mPhone.notifyHandoverStateChanged(mConnections[i]);

                        /// M: modified to fulfill conference SRVCC. @{
                        if (hoConnection.isMultipartyBeforeHandover() && !hoConnection.getStateBeforeHandover().isRinging()) {
                            Rlog.i(LOG_TAG, "SRVCC: goes to conference case.");
                            mConnections[i].mOrigConnection = hoConnection;
                            mImsConfParticipants.add(mConnections[i]);
                            // For conference participant, do not disconnect it later, otherwise the inCallUI will disappear.
                            if (hoConnection.isIncoming()) {
                                mHandoverConnections.remove(hoConnection);
                            }
                        } else {
                            Rlog.i(LOG_TAG, "SRVCC: goes to normal call case.");
                            mConnections[i].migrateFrom(hoConnection);
                            mHandoverConnections.remove(hoConnection);
                            mPhone.notifyHandoverStateChanged(mConnections[i]);
                        }
                        /// @}
                    } else if (checkFlag && (mConnections[i].getCall() == mRingingCall)) { // it's a ringing call
                        newRinging = mConnections[i];
                    } else if (checkFlag) {
                        // Something strange happened: a call appeared
                        // which is neither a ringing call or one we created.
                        // Either we've crashed and re-attached to an existing
                        // call, or something else (eg, SIM) initiated the call.

                        Rlog.i(LOG_TAG,"Phantom call appeared " + dc);

                        // If it's a connected call, set the connect time so that
                        // it's non-zero.  It may not be accurate, but at least
                        // it won't appear as a Missed Call.
                        if (dc.state != DriverCall.State.ALERTING
                                && dc.state != DriverCall.State.DIALING) {
                            mConnections[i].onConnectedInOrOut();
                            if (dc.state == DriverCall.State.HOLDING) {
                                // We've transitioned into HOLDING
                                mConnections[i].onStartedHolding();
                            }
                        }

                        newUnknown = mConnections[i];

                        unknownConnectionAppeared = true;
                    }
                    /* M: CC part end */
                }
                hasNonHangupStateChanged = true;
            } else if (conn != null && dc == null) {

                /* M: CC part start */
                if (DBG_POLL) log("case 2 : old Call disappear");

                /// M: CC019: Convert state from WAITING to INCOMING @{
                if (((conn.getCall() == mForegroundCall && mForegroundCall.mConnections.size() == 1 && mBackgroundCall.isIdle()) ||
                     (conn.getCall() == mBackgroundCall && mBackgroundCall.mConnections.size() == 1 && mForegroundCall.isIdle())) &&
                    mRingingCall.getState() == GsmCall.State.WAITING) {
                    mRingingCall.mState = GsmCall.State.INCOMING;
                }
                /// @}

                // Connection missing in CLCC response that we were tracking.
                mDroppedDuringPoll.add(conn);
                // Dropped connections are removed from the CallTracker
                // list but kept in the GsmCall list
                mConnections[i] = null;

                /// M: CC010: Add RIL interface @{
                mHelper.CallIndicationEnd();
                /// @}

                /// M: CC017: Forwarding number via EAIC @{
                //To clear forwarding address if needed
                mHelper.clearForwardingAddressVariables(i);
                /// @}
                /* M: CC part end */

            } else if (conn != null && dc != null && !conn.compareTo(dc)) {

                /* M: CC part start */
                if (DBG_POLL) log("case 3 : old Call replaced");

                // Connection in CLCC response does not match what
                // we were tracking. Assume dropped call and new call

                mDroppedDuringPoll.add(conn);

                /// M: CC010: Add RIL interface @{
                // give CLIP ALLOW default value, it will be changed on CLIP URC
                dc.numberPresentation = PhoneConstants.PRESENTATION_ALLOWED;
                /// @}
                /* M: CC part end */

                mConnections[i] = new GsmConnection (mPhone.getContext(), dc, this, i);

                if (mConnections[i].getCall() == mRingingCall) {
                    newRinging = mConnections[i];
                } // else something strange happened
                hasNonHangupStateChanged = true;
            } else if (conn != null && dc != null) { /* implicit conn.compareTo(dc) */

                /* M: CC part start */
                if (DBG_POLL) log("case 4 : old Call update");

                /// M: CC010: Add RIL interface @{
                // dc's CLIP value should use conn's, because it may has been updated
                dc.numberPresentation = conn.getNumberPresentation();
                /// @}
                /* M: CC part end */

                boolean changed;
                changed = conn.update(dc);
                hasNonHangupStateChanged = hasNonHangupStateChanged || changed;
            }

            if (REPEAT_POLLING) {
                if (dc != null) {
                    // FIXME with RIL, we should not need this anymore
                    if ((dc.state == DriverCall.State.DIALING
                            /*&& cm.getOption(cm.OPTION_POLL_DIALING)*/)
                        || (dc.state == DriverCall.State.ALERTING
                            /*&& cm.getOption(cm.OPTION_POLL_ALERTING)*/)
                        || (dc.state == DriverCall.State.INCOMING
                            /*&& cm.getOption(cm.OPTION_POLL_INCOMING)*/)
                        || (dc.state == DriverCall.State.WAITING
                            /*&& cm.getOption(cm.OPTION_POLL_WAITING)*/)
                    ) {
                        // Sometimes there's no unsolicited notification
                        // for state transitions
                        needsPollDelay = true;
                    }
                }
            }
        }

        // This is the first poll after an ATD.
        // We expect the pending call to appear in the list
        // If it does not, we land here
        if (mPendingMO != null) {
            Rlog.d(LOG_TAG,"Pending MO dropped before poll fg state:"
                            + mForegroundCall.getState());

            mDroppedDuringPoll.add(mPendingMO);
            mPendingMO = null;
            mHangupPendingMO = false;
        }

        if (newRinging != null) {
            if (DBG_POLL) log("notifyNewRingingConnection");
            mPhone.notifyNewRingingConnection(newRinging);

            /// M: For 3G VT only @{
            //MT:new VT service
            if (SystemProperties.get("ro.mtk_vt3g324m_support").equals("1")) {
                if (newRinging.isVideo()) {
                    newRinging.setVideoState(VideoProfile.VideoState.BIDIRECTIONAL);
                    try {
                        mRingingCall.mVTProvider =
                                new GsmVTProvider(((GsmConnection) newRinging).getGSMIndex());
                        Rlog.d(LOG_TAG, "handlePollCalls new GsmVTProvider");
                        IGsmVideoCallProvider gsmVideoCallProvider =
                                mRingingCall.mVTProvider.getInterface();
                        if (gsmVideoCallProvider != null) {
                            GsmVideoCallProviderWrapper gsmVideoCallProviderWrapper =
                                    new GsmVideoCallProviderWrapper(gsmVideoCallProvider);
                            Rlog.d(LOG_TAG, "handlePollCalls new GsmVideoCallProviderWrapper");
                            newRinging.setVideoProvider(gsmVideoCallProviderWrapper);
                        }
                    } catch (CallStateException ex) {
                    } catch (ClassCastException e) {
                        log("cast to GsmConnection fail for newRinging " + e);
                    } catch (RemoteException e) {
                        Rlog.d(LOG_TAG, "handlePollCalls new GsmVideoCallProviderWrapper failed");
                    }
                }
            }
            /// @}

        }

        // clear the "local hangup" and "missed/rejected call"
        // cases from the "dropped during poll" list
        // These cases need no "last call fail" reason
        log("dropped during poll size = " + mDroppedDuringPoll.size());
        for (int i = mDroppedDuringPoll.size() - 1; i >= 0 ; i--) {
            GsmConnection conn = mDroppedDuringPoll.get(i);

            /// M: CC012: Set as DisconnectCause.LOCAL if conn is disconnected due to Radio Off @{
            if (isCommandExceptionRadioNotAvailable(ar.exception)) {
                conn.onHangupLocal();
            }
            /// @}

            if (conn.isIncoming() && conn.getConnectTime() == 0) {
                // Missed or rejected call
                int cause;
                if (conn.mCause == DisconnectCause.LOCAL) {
                    cause = DisconnectCause.INCOMING_REJECTED;
                } else {
                    cause = DisconnectCause.INCOMING_MISSED;
                }

                if (Phone.DEBUG_PHONE) {
                    log("missed/rejected call, conn.cause=" + conn.mCause);
                    log("setting cause to " + cause);
                }
                mDroppedDuringPoll.remove(i);
                hasAnyCallDisconnected |= conn.onDisconnect(cause);
            } else if (conn.mCause == DisconnectCause.LOCAL
                    || conn.mCause == DisconnectCause.INVALID_NUMBER) {

                log("local hangup or invalid number");
                mDroppedDuringPoll.remove(i);
                hasAnyCallDisconnected |= conn.onDisconnect(conn.mCause);
            }
        }

        /// M: added method to fulfill conference SRVCC. @{
        if (mImsConfHostConnection != null) {
            ImsPhoneConnection hostConn = (ImsPhoneConnection) mImsConfHostConnection;
            if (mImsConfParticipants.size() >= 2) {
                // participants >= 2, means this is conference host side.
                // apply MTK SRVCC solution.

                // try to restore participants' address, we don't sure if +ECONFSRVCC is arrival.
                restoreConferenceParticipantAddress();

                log("srvcc: notify new participant connections");
                hostConn.notifyConferenceConnectionsConfigured(mImsConfParticipants);

            } else if (mImsConfParticipants.size() == 1) {
                // participants = 1, can't be a conference, so apply Google SRVCC solution.
                GsmConnection participant = (GsmConnection) mImsConfParticipants.get(0);

                if (participant.isIncoming()) {
                    // Case 1: Conference participant side case.
                    log("srvcc: conference participant case.");
                } else {
                    // Case 2: Conference host side case, with only participant case. 
                    // Due to modem's limitation, we still need to restore the address.
                    String address = hostConn.getConferenceParticipantAddress(0);
                    log("srvcc: restore participant connection with address: " + address);
                    participant.updateConferenceParticipantAddress(address);
                }

                log("srvcc: only one connection, consider it as a normal call SRVCC");
                mPhone.notifyHandoverStateChanged(participant);
            } else {
                Rlog.e(LOG_TAG, "SRVCC: abnormal case, no participant connections.");
            }
            mImsConfParticipants.clear();
            mImsConfHostConnection = null;
            mEconfSrvccConnectionIds = null;
        }
        /// @}

        /* Disconnect any pending Handover connections */
        for (Connection hoConnection : mHandoverConnections) {
            log("handlePollCalls - disconnect hoConn= " + hoConnection.toString());
            ((ImsPhoneConnection)hoConnection).onDisconnect(DisconnectCause.NOT_VALID);
            mHandoverConnections.remove(hoConnection);
        }

        // Any non-local disconnects: determine cause
        if (mDroppedDuringPoll.size() > 0 &&
                /// M: For 3G VT only @{
                !hasPendingReplaceRequest) {
                /// @}
            mCi.getLastCallFailCause(
                obtainNoPollCompleteMessage(EVENT_GET_LAST_CALL_FAIL_CAUSE));
        }

        if (needsPollDelay) {
            pollCallsAfterDelay();
        }

        // Cases when we can no longer keep disconnected Connection's
        // with their previous calls
        // 1) the phone has started to ring
        // 2) A Call/Connection object has changed state...
        //    we may have switched or held or answered (but not hung up)
        if (newRinging != null || hasNonHangupStateChanged || hasAnyCallDisconnected) {
            internalClearDisconnected();
        }

        updatePhoneState();

        if (unknownConnectionAppeared) {
            if (DBG_POLL) log("notifyUnknownConnection");
            mPhone.notifyUnknownConnection(newUnknown);
        }

        if ((hasNonHangupStateChanged || newRinging != null || hasAnyCallDisconnected)
            /// M: CC015: CRSS special handling @{
            && !mHasPendingSwapRequest) {
            /// @}
            if (DBG_POLL) log("notifyPreciseCallStateChanged");
            mPhone.notifyPreciseCallStateChanged();
        }

        /// M: CC019: [ALPS00401290] Convert state from WAITING to INCOMING @{
        if ((mHelper.getCurrentTotalConnections() == 1) &&
            (mRingingCall.getState() == GsmCall.State.WAITING)) {
           mRingingCall.mState = GsmCall.State.INCOMING;
        }
        /// @}

        dumpState();
    }

    private void
    handleRadioNotAvailable() {
        // handlePollCalls will clear out its
        // call list when it gets the CommandException
        // error result from this
        pollCallsWhenSafe();
    }

    private void
    dumpState() {
        List l;

        Rlog.i(LOG_TAG,"Phone State:" + mState);

        Rlog.i(LOG_TAG,"Ringing call: " + mRingingCall.toString());

        l = mRingingCall.getConnections();
        for (int i = 0, s = l.size(); i < s; i++) {
            Rlog.i(LOG_TAG,l.get(i).toString());
        }

        Rlog.i(LOG_TAG,"Foreground call: " + mForegroundCall.toString());

        l = mForegroundCall.getConnections();
        for (int i = 0, s = l.size(); i < s; i++) {
            Rlog.i(LOG_TAG,l.get(i).toString());
        }

        Rlog.i(LOG_TAG,"Background call: " + mBackgroundCall.toString());

        l = mBackgroundCall.getConnections();
        for (int i = 0, s = l.size(); i < s; i++) {
            Rlog.i(LOG_TAG,l.get(i).toString());
        }

        /// M: CC011: Use GsmCallTrackerHelper @{
        mHelper.LogState();
        // @}
    }

    //***** Called from GsmConnection

    /*package*/ void
    hangup (GsmConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException ("GsmConnection " + conn
                                    + "does not belong to GsmCallTracker " + this);
        }

        if (conn == mPendingMO) {
            // We're hanging up an outgoing call that doesn't have it's
            // GSM index assigned yet

            if (Phone.DEBUG_PHONE) log("hangup: set hangupPendingMO to true");
            mHangupPendingMO = true;
            /// M: CC013: Hangup special handling @{
            mHelper.PendingHangupRequestReset();
            /// @}
        } else {
            try {
                /// M: CC013: Hangup special handling @{
                mCi.hangupConnection(conn.getGSMIndex(), obtainCompleteMessage(EVENT_HANG_UP_RESULT));
                /// @}
            } catch (CallStateException ex) {
                // Ignore "connection not found"
                // Call may have hung up already
                /// M: CC013: Hangup special handling @{
                mHelper.PendingHangupRequestReset();
                /// @}
                Rlog.w(LOG_TAG,"GsmCallTracker WARN: hangup() on absent connection "
                                + conn);
            }
        }

        conn.onHangupLocal();
    }

    /*package*/ void
    separate (GsmConnection conn) throws CallStateException {
        if (conn.mOwner != this) {
            throw new CallStateException ("GsmConnection " + conn
                                    + "does not belong to GsmCallTracker " + this);
        }
        try {
            mCi.separateConnection (conn.getGSMIndex(),
                obtainCompleteMessage(EVENT_SEPARATE_RESULT));
        } catch (CallStateException ex) {
            // Ignore "connection not found"
            // Call may have hung up already
            Rlog.w(LOG_TAG,"GsmCallTracker WARN: separate() on absent connection "
                          + conn);
        }
    }

    //***** Called from GSMPhone

    /*package*/ void
    setMute(boolean mute) {
        mDesiredMute = mute;
        mCi.setMute(mDesiredMute, null);
    }

    /*package*/ boolean
    getMute() {
        return mDesiredMute;
    }


    //***** Called from GsmCall

    /* package */ void
    hangup (GsmCall call) throws CallStateException {
        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections in call");
        }

        /// M: CC013: Hangup special handling @{
        if (mHelper.hasPendingHangupRequest()) {
            Rlog.d(LOG_TAG, "hangup(GsmCall) hasPendingHangupRequest = true");
            if (mHelper.ForceReleaseAllConnection(call)) {
                return;
            }
        }
        /// @}

        if (call == mRingingCall) {
            /// M: CC013: Hangup special handling @{
            mHelper.PendingHangupRequestInc();
            if (Phone.DEBUG_PHONE) log("(ringing) hangup waiting or background");
            hangup((GsmConnection) (call.getConnections().get(0)));
            /// @}
        } else if (call == mForegroundCall) {
            /// M: CC013: Hangup special handling @{
            mHelper.PendingHangupRequestInc();
            /// @}
            if (call.isDialingOrAlerting()) {
                if (Phone.DEBUG_PHONE) {
                    log("(foregnd) hangup dialing or alerting...");
                }
                hangup((GsmConnection)(call.getConnections().get(0)));
            /// M: CC089: Use 1+SEND MMI to release active calls & accept held or waiting call @{
            // 3GPP 22.030 6.5.5
            // "Releases all active calls (if any exist) and accepts
            //  the other (held or waiting) call."
            /*
            } else if (mRingingCall.isRinging()) {
                // Do not auto-answer ringing on CHUP, instead just end active calls
                log("hangup all conns in active/background call, without affecting ringing call");
                hangupAllConnections(call);
            */
            /// @}
            } else {
                /// M: CC013: Hangup special handling @{
                if (Phone.DEBUG_PHONE) log("(foregnd) hangup active");

                /* For solving [ALPS01431282][ALPS.KK1.MP2.V2.4 Regression Test][Case Fail][Call] Can not end the ECC call when enable SIM PIN lock. */
                GsmConnection cn = (GsmConnection) call.getConnections().get(0);
                String address = cn.getAddress();
                if (PhoneNumberUtils.isEmergencyNumber(address) && !PhoneNumberUtils.isSpecialEmergencyNumber(address)) {
                   log("(foregnd) hangup active Emergency call by connection index");
                   hangup((GsmConnection) (call.getConnections().get(0)));
                } else {
                /// @}
                   hangupForegroundResumeBackground();
                }
            }
        } else if (call == mBackgroundCall) {
            if (mRingingCall.isRinging()) {
                if (Phone.DEBUG_PHONE) {
                    log("hangup all conns in background call");
                }
                hangupAllConnections(call);
            } else {
                /// M: CC013: Hangup special handling @{
                mHelper.PendingHangupRequestInc();
                if (Phone.DEBUG_PHONE) log("(backgnd) hangup waiting/background");
                /// @}
                hangupWaitingOrBackground();
            }
        } else {
            throw new RuntimeException ("GsmCall " + call +
                    "does not belong to GsmCallTracker " + this);
        }

        call.onHangupLocal();
        mPhone.notifyPreciseCallStateChanged();
    }

    /* package */
    void hangupWaitingOrBackground() {
        if (Phone.DEBUG_PHONE) log("hangupWaitingOrBackground");
        /// M: CC013: Hangup special handling @{
        mCi.hangupWaitingOrBackground(obtainCompleteMessage(EVENT_HANG_UP_RESULT));
        /// @}
    }

    /* package */
    void hangupForegroundResumeBackground() {
        if (Phone.DEBUG_PHONE) log("hangupForegroundResumeBackground");
        /// M: CC013: Hangup special handling @{
        mCi.hangupForegroundResumeBackground(obtainCompleteMessage(EVENT_HANG_UP_RESULT));
        /// @}
    }

    void hangupConnectionByIndex(GsmCall call, int index)
            throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            GsmConnection cn = (GsmConnection)call.mConnections.get(i);
            if (cn.getGSMIndex() == index) {
                mCi.hangupConnection(index, obtainCompleteMessage());
                return;
            }
        }
        /// M: CC013: Hangup special handling @{
        if (mHelper.hangupBgConnectionByIndex(index))
            return;
        if (mHelper.hangupRingingConnectionByIndex(index))
            return;
        /// @}

        throw new CallStateException("no gsm index found");
    }

    void hangupAllConnections(GsmCall call) {
        try {
            int count = call.mConnections.size();
            for (int i = 0; i < count; i++) {
                GsmConnection cn = (GsmConnection)call.mConnections.get(i);
                mCi.hangupConnection(cn.getGSMIndex(), obtainCompleteMessage());
            }
        } catch (CallStateException ex) {
            Rlog.e(LOG_TAG, "hangupConnectionByIndex caught " + ex);
        }
    }

    /* package */
    GsmConnection getConnectionByIndex(GsmCall call, int index)
            throws CallStateException {
        int count = call.mConnections.size();
        for (int i = 0; i < count; i++) {
            GsmConnection cn = (GsmConnection)call.mConnections.get(i);
            if (cn.getGSMIndex() == index) {
                return cn;
            }
        }

        return null;
    }

    private Phone.SuppService getFailedService(int what) {
        switch (what) {
            case EVENT_SWITCH_RESULT:
                return Phone.SuppService.SWITCH;
            case EVENT_CONFERENCE_RESULT:
                return Phone.SuppService.CONFERENCE;
            case EVENT_SEPARATE_RESULT:
                return Phone.SuppService.SEPARATE;
            case EVENT_ECT_RESULT:
                return Phone.SuppService.TRANSFER;
        }
        return Phone.SuppService.UNKNOWN;
    }

    //****** Overridden from Handler

    @Override
    public void
    handleMessage (Message msg) {
        AsyncResult ar;

        if (!mPhone.mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        /// M: CC011: Use GsmCallTrackerHelper @{
        mHelper.LogerMessage(msg.what);
        /// @}

        switch (msg.what) {
            case EVENT_POLL_CALLS_RESULT:
                ar = (AsyncResult)msg.obj;

                if (msg == mLastRelevantPoll) {
                    if (DBG_POLL) log(
                            "handle EVENT_POLL_CALLS_RESULT: set needsPoll=F");
                    mNeedsPoll = false;
                    mLastRelevantPoll = null;
                    handlePollCalls((AsyncResult)msg.obj);
                }
            break;

            case EVENT_OPERATION_COMPLETE:
                ar = (AsyncResult)msg.obj;
                operationComplete();
                /// M: For 3G VT only @{
                if (hasPendingReplaceRequest) {
                    hasPendingReplaceRequest = false;
                }
                /// @}
            break;

            case EVENT_SWITCH_RESULT:
                /// M: CC015: CRSS special handling @{
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    if (mWaitingForHoldRequest.isWaiting()) {

                        mPendingMO.mCause = DisconnectCause.LOCAL;
                        mPendingMO.onDisconnect(DisconnectCause.LOCAL);
                        mPendingMO = null;
                        mHangupPendingMO = false;
                        updatePhoneState();

                        resumeBackgroundAfterDialFailed();
                        mWaitingForHoldRequest.reset();
                    }

                    mPhone.notifySuppServiceFailed(getFailedService(msg.what));
                } else {
                    if (mWaitingForHoldRequest.isWaiting()) {
                        Rlog.i(LOG_TAG, "Switch success, and then dial");
                        mWaitingForHoldRequest.handleOperation();
                    }
                }
                mHasPendingSwapRequest = false;
                operationComplete();
            break;
            /// @}
            case EVENT_CONFERENCE_RESULT:
            case EVENT_SEPARATE_RESULT:
            case EVENT_ECT_RESULT:
                ar = (AsyncResult)msg.obj;
                if (ar.exception != null) {
                    /// M: CC015: CRSS special handling @{
                    mHelper.PendingHangupRequestUpdate();
                    /// @}
                    mPhone.notifySuppServiceFailed(getFailedService(msg.what));
                }
                operationComplete();
            break;

            case EVENT_GET_LAST_CALL_FAIL_CAUSE:
                int causeCode;
                ar = (AsyncResult)msg.obj;

                operationComplete();

                if (ar.exception != null) {
                    // An exception occurred...just treat the disconnect
                    // cause as "normal"
                    causeCode = CallFailCause.NORMAL_CLEARING;
                    Rlog.i(LOG_TAG,
                            "Exception during getLastCallFailCause, assuming normal disconnect");
                } else {
                    causeCode = ((int[])ar.result)[0];
                }
                // Log the causeCode if its not normal
                if (causeCode == CallFailCause.NO_CIRCUIT_AVAIL ||
                    causeCode == CallFailCause.TEMPORARY_FAILURE ||
                    causeCode == CallFailCause.SWITCHING_CONGESTION ||
                    causeCode == CallFailCause.CHANNEL_NOT_AVAIL ||
                    causeCode == CallFailCause.QOS_NOT_AVAIL ||
                    causeCode == CallFailCause.BEARER_NOT_AVAIL ||
                    causeCode == CallFailCause.ERROR_UNSPECIFIED) {
                    GsmCellLocation loc = ((GsmCellLocation)mPhone.getCellLocation());
                    EventLog.writeEvent(EventLogTags.CALL_DROP,
                            causeCode, loc != null ? loc.getCid() : -1,
                            /// M: CC054: getNetworkType with subId for GsmCellLocation @{
                            TelephonyManager.getDefault().getNetworkType(mPhone.getSubId()));
                            /// @}
                }

                for (int i = 0, s =  mDroppedDuringPoll.size()
                        ; i < s ; i++
                ) {
                    GsmConnection conn = mDroppedDuringPoll.get(i);

                    conn.onRemoteDisconnect(causeCode);
                }

                updatePhoneState();

                mPhone.notifyPreciseCallStateChanged();
                mDroppedDuringPoll.clear();
            break;

            case EVENT_REPOLL_AFTER_DELAY:
            case EVENT_CALL_STATE_CHANGE:
                pollCallsWhenSafe();
            break;

            case EVENT_RADIO_AVAILABLE:
                handleRadioAvailable();
                /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
                int hdVoiceEnabled = Integer.parseInt(
                        SystemProperties.get(STR_PROPERTY_HD_VOICE, "0"));
                log("persist.radio.hd.voice = " + hdVoiceEnabled);
                if (hdVoiceEnabled == 1) {
                    mCi.setSpeechCodecInfo(true, null);
                } else if (hdVoiceEnabled == 0) {
                    mCi.setSpeechCodecInfo(false, null);
                }
                ///@}
            break;

            case EVENT_RADIO_NOT_AVAILABLE:
                handleRadioNotAvailable();
            break;

            /// M: CC010: Add RIL interface @{
            case EVENT_HANG_UP_RESULT:
                mHelper.PendingHangupRequestDec();
                operationComplete();
            break;

            case EVENT_DIAL_CALL_RESULT:
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    log("dial call failed!!");
                    mHelper.PendingHangupRequestUpdate();
                }
                operationComplete();
            break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                handleRadioNotAvailable();
            break;

            case EVENT_INCOMING_CALL_INDICATION:
                mHelper.CallIndicationProcess((AsyncResult) msg.obj);
            break;

            case EVENT_CNAP_INDICATION:
                    ar = (AsyncResult) msg.obj;

                    String[] cnapResult = (String[]) ar.result;

                    log("handle EVENT_CNAP_INDICATION : " + cnapResult[0] + ", " + cnapResult[1]);

                    log("ringingCall.isIdle() : " + mRingingCall.isIdle());

                    if (!mRingingCall.isIdle()) {
                        GsmConnection cn = (GsmConnection) mRingingCall.mConnections.get(0);

                        cn.setCnapName(cnapResult[0]);
                    } else {  // queue the CNAP
                        mPendingCnap = new String(cnapResult[0]);
                    }
            break;
            /// @}

            /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
            case EVENT_SPEECH_CODEC_INFO:
                /* FIXME: If any suppression is needed */		
                ar = (AsyncResult) msg.obj;
                mSpeechCodecType = ((int[]) ar.result)[0];
                log("handle EVENT_SPEECH_CODEC_INFO : " + mSpeechCodecType);
                mPhone.notifySpeechCodecInfo(mSpeechCodecType);
            break;
            /// @}

            ///M: IMS conference call feature. @{
            case EVENT_ECONF_SRVCC_INDICATION:
                log("Receives EVENT_ECONF_SRVCC_INDICATION");
                ar = (AsyncResult) msg.obj;
                mEconfSrvccConnectionIds = (int[]) ar.result;

                // try to restore participants' address, we don't sure if CIREPH=1 is arrival.
                //if (restoreConferenceParticipantAddress()) {
                //    log("notifyPreciseCallStateChanged");
                //    mPhone.notifyPreciseCallStateChanged();
                //}
                mNeedWaitImsEConfSrvcc = false;
                pollCallsWhenSafe();
            break;
            /// @}

            default:
                break;
        }
    }

    @Override
    protected void log(String msg) {
        if (Build.TYPE.equals("eng"))/* HQ_guomiao 2015-10-27 modified for HQ01444267 */
        Rlog.d(LOG_TAG, "[GsmCallTracker][Phone" + (mPhone.getPhoneId()) + "] " + msg);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (!Build.TYPE.equals("eng")) return;/* HQ_guomiao 2015-10-27 modified for HQ01444267 */
        pw.println("GsmCallTracker extends:");
        super.dump(fd, pw, args);
        pw.println("mConnections: length=" + mConnections.length);
        for(int i=0; i < mConnections.length; i++) {
            pw.printf("  mConnections[%d]=%s\n", i, mConnections[i]);
        }
        pw.println(" mVoiceCallEndedRegistrants=" + mVoiceCallEndedRegistrants);
        pw.println(" mVoiceCallStartedRegistrants=" + mVoiceCallStartedRegistrants);
        pw.println(" mDroppedDuringPoll: size=" + mDroppedDuringPoll.size());
        for(int i = 0; i < mDroppedDuringPoll.size(); i++) {
            pw.printf( "  mDroppedDuringPoll[%d]=%s\n", i, mDroppedDuringPoll.get(i));
        }
        pw.println(" mRingingCall=" + mRingingCall);
        pw.println(" mForegroundCall=" + mForegroundCall);
        pw.println(" mBackgroundCall=" + mBackgroundCall);
        pw.println(" mPendingMO=" + mPendingMO);
        pw.println(" mHangupPendingMO=" + mHangupPendingMO);
        pw.println(" mPhone=" + mPhone);
        pw.println(" mDesiredMute=" + mDesiredMute);
        pw.println(" mState=" + mState);
    }
    
    @Override
    public PhoneConstants.State getState() {
        return mState;
    }

    /**
     * clirMode is one of the CLIR_ constants
     */
    /// M: CC040: Reject call with cause for HFP @{
    /* [ALPS00475147] Add by mtk01411 to provide disc only ringingCall with specific cause instead of INCOMMING_REJECTED */
    /* package */ void
    hangup(GsmCall call, int discRingingCallCause) throws CallStateException {
        /// M: [mtk04070][111118][ALPS00093395]MTK modified. @{
        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections in call");
        }

        if (mHelper.hasPendingHangupRequest()) {
            Rlog.d(LOG_TAG, "hangup(GsmCall) hasPendingHangupRequest = true");
            if (mHelper.ForceReleaseNotRingingConnection(call)) {
                return;
            }
        }

        if (call == mRingingCall) {
            if (Phone.DEBUG_PHONE) log("(ringing) hangup waiting or background");
            /* Solve [ALPS00303482][GCF][51.010-1][26.8.1.3.5.3], mtk04070, 20120628 */
            log("Hang up waiting or background call by connection index.");
            GsmConnection conn = (GsmConnection) (call.getConnections().get(0));
            mCi.hangupConnection(conn.getGSMIndex(), obtainCompleteMessage());

        } else {
            throw new RuntimeException("GsmCall " + call +
                    "does not belong to GsmCallTracker " + this);
        }

        call.onHangupLocal();
        /* Add by mtk01411: Change call's state as DISCONNECTING in call.onHangupLocal()
               *  --> cn.onHangupLocal(): set cn's cause as DisconnectionCause.LOCAL
         */
        if (call == mRingingCall) {
            GsmConnection ringingConn = (GsmConnection) (call.getConnections().get(0));
            ringingConn.mCause = discRingingCallCause;
        }
        mPhone.notifyPreciseCallStateChanged();
        /// @}
    }
    /// @}

    /// M: CC010: Add RIL interface @{
    void hangupAll() {
        if (Phone.DEBUG_PHONE) log("hangupAll");
        mCi.hangupAll(obtainCompleteMessage());

        if (!mRingingCall.isIdle()) {
            mRingingCall.onHangupLocal();
        }
        if (!mForegroundCall.isIdle()) {
            mForegroundCall.onHangupLocal();
        }
        if (!mBackgroundCall.isIdle()) {
            mBackgroundCall.onHangupLocal();
        }
    }

    public void setIncomingCallIndicationResponse(boolean accept) {
        mHelper.CallIndicationResponse(accept);
    }
    ///@}

    /// M: CC053: MoMS [Mobile Managerment] @{
    // 2. MT Phone Call Interception
    /**
     * To know if the incoming call is rejected by Mobile Manager Service.
     * @return Return true if it is rejected by Moms, else return false.
     */
    public boolean isRejectedByMoms() {
       return mHelper.MobileManagermentGetIsBlocking();
    }
    /// @}

    /* M: CC part start */
    private DriverCall.State getDriverCallStateFromCallState(Call.State state) {
        switch (state) {
        case HOLDING:
            return DriverCall.State.HOLDING;
        case DIALING:
            return DriverCall.State.DIALING;
        case ALERTING:
            return DriverCall.State.ALERTING;
        case INCOMING:
            return DriverCall.State.INCOMING;
        case WAITING:
            return DriverCall.State.WAITING;
        default:
            return DriverCall.State.ACTIVE;
        }
    }

    private String callStateToString(Call.State state) {
        switch (state) {
        case HOLDING:
            return "HOLDING";
        case DIALING:
            return "DIALING";
        case ALERTING:
            return "ALERTING";
        case INCOMING:
            return "INCOMING";
        case WAITING:
            return "WAITING";
        default:
            return "ACTIVE";
        }
    }
    /* M: CC part end */

    /// M: for Ims Conference SRVCC. @{
    /**
     * For conference participants, the call number will be empty after SRVCC.
     * So at handlePollCalls(), it will get new connections without address. We use +ECONFSRVCC
     * and conference XML to restore all addresses.
     *
     * @return true if connections are restored.
     */
    private synchronized boolean restoreConferenceParticipantAddress() {
        if (mEconfSrvccConnectionIds == null) {
            log("SRVCC: restoreConferenceParticipantAddress():" +
                    "ignore because mEconfSrvccConnectionIds is empty");
            return false;
        }

        boolean finishRestore = false;

        // int[] mEconfSrvccConnectionIds = { size, call-ID-1, call-ID-2, call-ID-3, ...}
        int numOfParticipants = mEconfSrvccConnectionIds[0];
        for (int index = 1; index <= numOfParticipants; index++) {

            int participantCallId = mEconfSrvccConnectionIds[index];
            GsmConnection participantConnection = mConnections[participantCallId - 1];

            if (participantConnection != null) {
                log("SRVCC: found conference connections!");

                ImsPhoneConnection hostConnection = null;
                if (participantConnection.mOrigConnection instanceof ImsPhoneConnection) {
                    hostConnection = (ImsPhoneConnection) participantConnection.mOrigConnection;
                } else {
                    log("SRVCC: host is abnormal, ignore connection: " + participantConnection);
                    continue;
                }

                if (hostConnection == null) {
                    log("SRVCC: no host, ignore connection: " + participantConnection);
                    continue;
                }

                String address = hostConnection.getConferenceParticipantAddress(index - 1);
                participantConnection.updateConferenceParticipantAddress(address);
                finishRestore = true;

                log("SRVCC: restore Connection=" + participantConnection +
                        " with address:" + address);
            }
        }

        return finishRestore;
    }

    @Override
    protected Connection getHoConnection(DriverCall dc) {
        if (mEconfSrvccConnectionIds != null && dc != null) {
            int numOfParticipants = mEconfSrvccConnectionIds[0];
            for (int index = 1; index <= numOfParticipants; index++) {
                if (dc.index == mEconfSrvccConnectionIds[index]) {
                    log("SRVCC: getHoConnection for call-id:"
                            + dc.index + " in a conference is found!");
                    if (mImsConfHostConnection == null) {
                        log("SRVCC: but mImsConfHostConnection is null, try to find by callState");
                        break;
                    } else {
                        log("SRVCC: ret= " + mImsConfHostConnection);
                        return mImsConfHostConnection;
                    }
                }
            }
        }

        return super.getHoConnection(dc);
    }
    /// @}
}
