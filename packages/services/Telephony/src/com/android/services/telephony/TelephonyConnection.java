/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

package com.android.services.telephony;

import android.net.Uri;
import android.os.AsyncResult;
/// M: IMS feature. @{
import android.os.Bundle;
/// @}
import android.os.Handler;
import android.os.Message;
import android.telecom.AudioState;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccount;
/// M: IMS feature. @{
import android.telecom.VideoProfile;
/// @}

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection.PostDialListener;
import com.android.internal.telephony.Phone;

import com.android.internal.telephony.imsphone.ImsPhoneConnection;

/// M: IMS feature. @{
import com.mediatek.telecom.TelecomManagerEx;
/// @}

import java.lang.Override;
/// M: IMS feature. @{
import java.util.ArrayList;
/// @}
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for CDMA and GSM connections.
 */
/// M: CC030: CRSS notification @{
// Declare as public for SuppMessageManager to use.
public abstract class TelephonyConnection extends Connection {
/// @}
    private static final int MSG_PRECISE_CALL_STATE_CHANGED = 1;
    private static final int MSG_RINGBACK_TONE = 2;
    private static final int MSG_HANDOVER_STATE_CHANGED = 3;
    private static final int MSG_DISCONNECT = 4;

    private static final int MTK_EVENT_BASE = 1000;
    /// M: CC031: Radio off notification @{
    private static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE       = MTK_EVENT_BASE;
    /// @}
    private static final int EVENT_CDMA_CALL_ACCEPTED               = MTK_EVENT_BASE + 1;
    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    private static final int EVENT_SPEECH_CODEC_INFO                = MTK_EVENT_BASE + 2;
    /// @}
    /// M: IMS feature. @{
    private static final int EVENT_CALL_INFO_CHANGED_NOTIFICATION   = MTK_EVENT_BASE + 4;
    /// @}

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case MSG_PRECISE_CALL_STATE_CHANGED:
                    Log.v(TelephonyConnection.this, "MSG_PRECISE_CALL_STATE_CHANGED");
                    updateState();
                    break;
                case MSG_HANDOVER_STATE_CHANGED:
                    Log.v(TelephonyConnection.this, "MSG_HANDOVER_STATE_CHANGED");
                    com.android.internal.telephony.Connection connection =
                         (com.android.internal.telephony.Connection) ar.result;

                    /// M: @{
                    if (connection == null || mOriginalConnection == null) {
                        Log.d(TelephonyConnection.this, "SRVCC: ignore since no connections");
                        return;
                    }
                    /// @}

                    /// M: fix Google bug. @{
                    // it should use mOriginalConnection.getStateBeforeHandover(). 

                    /*if ((connection.getAddress() != null &&
                            mOriginalConnection.getAddress() != null &&
                            mOriginalConnection.getAddress().contains(connection.getAddress())) ||
                            connection.getStateBeforeHandover() == mOriginalConnection.getState()) {
                            */
                    if ((connection.getAddress() != null &&
                            mOriginalConnection.getAddress() != null &&
                            mOriginalConnection.getAddress().contains(connection.getAddress())) ||
                            mOriginalConnection.getStateBeforeHandover() == connection.getState()) {
                        /// @}
                        Log.d(TelephonyConnection.this, "SettingOriginalConnection " +
                                mOriginalConnection.toString() + " with " + connection.toString());
                        setOriginalConnection(connection);
                    }
                    break;
                case MSG_RINGBACK_TONE:
                    Log.v(TelephonyConnection.this, "MSG_RINGBACK_TONE");
                    // TODO: This code assumes that there is only one connection in the foreground
                    // call, in other words, it punts on network-mediated conference calling.
                    if (getOriginalConnection() != getForegroundConnection()) {
                        Log.v(TelephonyConnection.this, "handleMessage, original connection is " +
                                "not foreground connection, skipping");
                        return;
                    }
                    setRingbackRequested((Boolean) ((AsyncResult) msg.obj).result);
                    break;
                case MSG_DISCONNECT:
                    /// M: @{
                    Log.v(TelephonyConnection.this, "handle MSG_DISCONNECT ... cause = " +
                        (mOriginalConnection == null ? "null" :
                            mOriginalConnection.getDisconnectCause()));
                    if (mOriginalConnection != null && mOriginalConnection.getDisconnectCause() ==
                            android.telephony.DisconnectCause.IMS_EMERGENCY_REREG) {
                        try {
                            com.android.internal.telephony.Connection conn =
                                getPhone().dial(mOriginalConnection.getOrigDialString(),
                                    VideoProfile.VideoState.AUDIO_ONLY);
                            setOriginalConnection(conn);
                            notifyEcc();
                        } catch (CallStateException e) {
                            Log.e(TelephonyConnection.this, e, "Fail to redial as ECC");
                        }
                    } else {
                        updateState();
                    }
                    /// @}
                    break;
                /// M: CC031: Radio off notification @{
                case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                    notifyConnectionLost();
                    break;
                /// @}
                /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
                case EVENT_SPEECH_CODEC_INFO:
                    int value = (int) ar.result;
                    Log.v(TelephonyConnection.this, "EVENT_SPEECH_CODEC_INFO : " + value);
                    if (TelephonyConnectionServiceUtil.getInstance().isHighDefAudio(value)) {
                        setAudioQuality(com.android.internal.telephony.Connection.
                                AUDIO_QUALITY_HIGH_DEFINITION);
                    } else {
                        setAudioQuality(com.android.internal.telephony.Connection.
                                AUDIO_QUALITY_STANDARD);
                    }
                    break;
                /// @}

                /// M: IMS feature. @{
                case EVENT_CALL_INFO_CHANGED_NOTIFICATION: {
                    Log.v(TelephonyConnection.this, "Receive EVENT_CALL_INFO_CHANGED_NOTIFICATION");
                    try {
                        Bundle bundle = (Bundle) ar.result;
                        int id = bundle.getInt("id");
                        int connId = 0;
                        if (mOriginalConnection != null) {
                            connId = ((com.android.internal.telephony.imsphone.ImsPhoneConnection)
                                mOriginalConnection).getCallId();
                        }
                        Log.v(TelephonyConnection.this, "id=" + id + ", connId=" + connId +
                            ", mOriginalConnection=" + mOriginalConnection);
                        if (id == connId) {
                            setCallInfo(bundle);
                        }
                    } catch (Exception e) {
                        Log.e(TelephonyConnection.this, e, "Receive EVENT_CALL_INFO_CHANGED_NOTIFICATION");
                    }
                    break;
                }
                case EVENT_CDMA_CALL_ACCEPTED: {
                    Log.v(TelephonyConnection.this, "EVENT_CDMA_CALL_ACCEPTED");
                    fireOnCdmaCallAccepted();
                    /** M: Bug Fix for ALPS01938951 @{ */
                    // update call capabilities for CDMA HOLD status
                    updateConnectionCapabilities();
                    /** @} */
                    break;
                }
                /// @}
            }
        }
    };

    /**
     * A listener/callback mechanism that is specific communication from TelephonyConnections
     * to TelephonyConnectionService (for now). It is more specific that Connection.Listener
     * because it is only exposed in Telephony.
     */
    public abstract static class TelephonyConnectionListener {
        public void onOriginalConnectionConfigured(TelephonyConnection c) {}

        /**
         * For VoLTE enhanced conference call, notify invite conf. participants completed.
         * @param isSuccess is success or not.
         */
        public void onConferenceParticipantsInvited(boolean isSuccess) {}

        /**
         * For VoLTE conference SRVCC, notify when new participant connections maded.
         * @param radioConnections new participant connections.
         */
        public void onConferenceConnectionsConfigured(
            ArrayList<com.android.internal.telephony.Connection> radioConnections) {}

    }

    private final PostDialListener mPostDialListener = new PostDialListener() {
        @Override
        public void onPostDialWait() {
            Log.v(TelephonyConnection.this, "onPostDialWait");
            if (mOriginalConnection != null) {
                setPostDialWait(mOriginalConnection.getRemainingPostDialString());
            }
        }

        @Override
        public void onPostDialChar(char c) {
            Log.v(TelephonyConnection.this, "onPostDialChar: %s", c);
            if (mOriginalConnection != null) {
                setNextPostDialWaitChar(c);
            }
        }
    };

    /**
     * Listener for listening to events in the {@link com.android.internal.telephony.Connection}.
     */
    private final com.android.internal.telephony.Connection.Listener mOriginalConnectionListener =
            new com.android.internal.telephony.Connection.ListenerBase() {
        @Override
        public void onVideoStateChanged(int videoState) {
            setVideoState(videoState);
        }

        /**
         * The {@link com.android.internal.telephony.Connection} has reported a change in local
         * video capability.
         *
         * @param capable True if capable.
         */
        @Override
        public void onLocalVideoCapabilityChanged(boolean capable) {
            setLocalVideoCapable(capable);
        }

        /**
         * The {@link com.android.internal.telephony.Connection} has reported a change in remote
         * video capability.
         *
         * @param capable True if capable.
         */
        @Override
        public void onRemoteVideoCapabilityChanged(boolean capable) {
            setRemoteVideoCapable(capable);
        }

        /**
         * The {@link com.android.internal.telephony.Connection} has reported a change in the
         * video call provider.
         *
         * @param videoProvider The video call provider.
         */
        @Override
        public void onVideoProviderChanged(VideoProvider videoProvider) {
            setVideoProvider(videoProvider);
        }

        /**
         * Used by the {@link com.android.internal.telephony.Connection} to report a change in the
         * audio quality for the current call.
         *
         * @param audioQuality The audio quality.
         */
        @Override
        public void onAudioQualityChanged(int audioQuality) {
            setAudioQuality(audioQuality);
        }

        /**
         * Handles a change in the state of conference participant(s), as reported by the
         * {@link com.android.internal.telephony.Connection}.
         *
         * @param participants The participant(s) which changed.
         */
        @Override
        public void onConferenceParticipantsChanged(List<ConferenceParticipant> participants) {
            updateConferenceParticipants(participants);
        }

        /// M: For VoLTE conference call. @{
        /**
         * For VoLTE enhanced conference call, notify invite conf. participants completed.
         * @param isSuccess is success or not.
         */
        @Override
        public void onConferenceParticipantsInvited(boolean isSuccess) {
            notifyConferenceParticipantsInvited(isSuccess);
        }

        /**
         * For VoLTE conference SRVCC, notify when new participant connections maded.
         * @param radioConnections new participant connections.
         */
        @Override
        public void onConferenceConnectionsConfigured(
                ArrayList<com.android.internal.telephony.Connection> radioConnections) {
            notifyConferenceConnectionsConfigured(radioConnections);
        }
        /// @}

        /// M: Notify Telecom to remove pending action by onActionFailed(). @{
        /**
         * Notify Telecom to remove pending action when SS action fails reported by the
         * {@link com.android.internal.telephony.Connection}.
         *
         * @param actionCode SS action.
         */
        @Override
        public void onSuppServiceFailed(int actionCode) {
            notifyActionFailed(actionCode);
        }
        /// @}

        /// M: WFC update call type. @{
        @Override
        public void onCallTypeChanged(int callType) {
            setCallType(callType);
        }
        /// @}
    };

    private com.android.internal.telephony.Connection mOriginalConnection;
    private Call.State mOriginalConnectionState = Call.State.IDLE;

    private boolean mWasImsConnection;

    /**
     * Tracks the multiparty state of the ImsCall so that changes in the bit state can be detected.
     */
    private boolean mIsMultiParty = false;

    /**
     * Determines if the {@link TelephonyConnection} has local video capabilities.
     * This is used when {@link TelephonyConnection#updateConnectionCapabilities()}} is called,
     * ensuring the appropriate capabilities are set.  Since capabilities
     * can be rebuilt at any time it is necessary to track the video capabilities between rebuild.
     * The capabilities (including video capabilities) are communicated to the telecom
     * layer.
     */
    private boolean mLocalVideoCapable;

    /**
     * Determines if the {@link TelephonyConnection} has remote video capabilities.
     * This is used when {@link TelephonyConnection#updateConnectionCapabilities()}} is called,
     * ensuring the appropriate capabilities are set.  Since capabilities can be rebuilt at any time
     * it is necessary to track the video capabilities between rebuild. The capabilities (including
     * video capabilities) are communicated to the telecom layer.
     */
    private boolean mRemoteVideoCapable;

    /// M: CC034: Stop DTMF when TelephonyConnection is disconnected @{
    protected boolean mDtmfRequestIsStarted = false;
    /// @}

    /**
     * Determines the current audio quality for the {@link TelephonyConnection}.
     * This is used when {@link TelephonyConnection#updateConnectionCapabilities}} is called to
     * indicate whether a call has the {@link Connection#CAPABILITY_HIGH_DEF_AUDIO} capability.
     */
    private int mAudioQuality;

    /// M: WFC call type update. @{
    private int mCallType;
    /// @}

    /**
     * Listeners to our TelephonyConnection specific callbacks
     */
    private final Set<TelephonyConnectionListener> mTelephonyListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<TelephonyConnectionListener, Boolean>(8, 0.9f, 1));

    protected TelephonyConnection(com.android.internal.telephony.Connection originalConnection) {
        if (originalConnection != null) {
            setOriginalConnection(originalConnection);
        }
    }

    /**
     * Creates a clone of the current {@link TelephonyConnection}.
     *
     * @return The clone.
     */
    public abstract TelephonyConnection cloneConnection();

    @Override
    public void onAudioStateChanged(AudioState audioState) {
        // TODO: update TTY mode.
        if (getPhone() != null) {
            getPhone().setEchoSuppressionEnabled();
        }
    }

    @Override
    public void onStateChanged(int state) {
        Log.v(this, "onStateChanged, state: " + Connection.stateToString(state));
    }

    @Override
    public void onDisconnect() {
        Log.v(this, "onDisconnect");
        /// M: CC040: The telephonyDisconnectCode is not used here @{
        hangup();
        //hangup(android.telephony.DisconnectCause.LOCAL);
        /// @}
    }

    /**
     * Notifies this Connection of a request to disconnect a participant of the conference managed
     * by the connection.
     *
     * @param endpoint the {@link Uri} of the participant to disconnect.
     */
    @Override
    public void onDisconnectConferenceParticipant(Uri endpoint) {
        Log.v(this, "onDisconnectConferenceParticipant %s", endpoint);

        if (mOriginalConnection == null) {
            return;
        }

        mOriginalConnection.onDisconnectConferenceParticipant(endpoint);
    }

    @Override
    public void onSeparate() {
        Log.v(this, "onSeparate");
        if (mOriginalConnection != null) {
            try {
                mOriginalConnection.separate();
            } catch (CallStateException e) {
                Log.e(this, e, "Call to Connection.separate failed with exception");
            }
        }
    }

    @Override
    public void onAbort() {
        Log.v(this, "onAbort");
        /// M: CC040: The telephonyDisconnectCode is not used here @{
        hangup();
        //hangup(android.telephony.DisconnectCause.LOCAL);
        /// @}
    }

    @Override
    public void onHold() {
        performHold();
    }

    /// M: CC078: For DSDS/DSDA Two-action operation @{
    @Override
    public void onHold(String pendingCallAction) {
        performHold(pendingCallAction);
    }
    /// @}

    @Override
    public void onUnhold() {
        performUnhold();
    }

    @Override
    public void onAnswer(int videoState) {
        Log.v(this, "onAnswer");
        if (isValidRingingCall() && getPhone() != null) {
            try {
                getPhone().acceptCall(videoState);
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to accept call.");
            }
        }
    }

    @Override
    public void onReject() {
        Log.v(this, "onReject");
        if (isValidRingingCall()) {
            /// M: CC040: The telephonyDisconnectCode is not used here @{
            hangup();
            //hangup(android.telephony.DisconnectCause.INCOMING_REJECTED);
            /// @}
        }
        super.onReject();
    }

    @Override
    public void onPostDialContinue(boolean proceed) {
        Log.v(this, "onPostDialContinue, proceed: " + proceed);
        if (mOriginalConnection != null) {
            if (proceed) {
                mOriginalConnection.proceedAfterWaitChar();
            } else {
                mOriginalConnection.cancelPostDial();
            }
        }
    }

    /// M: CC025: Interface for swap call @{
    @Override
    public void onSwapWithBackgroundCall() {
        Log.v(this, "onSwapWithBackgroundCall");
        Phone phone = mOriginalConnection.getCall().getPhone();
        try {
            phone.switchHoldingAndActive();
        } catch (CallStateException e) {
            Log.e(this, e, "Exception occurred while trying to do swap.");
        }
    }
    /// @}

    /// M: CC040: Reject call with cause for HFP @{
    @Override
    public void onReject(int cause) {
        Log.v(this, "onReject withCause %d" + cause);
        if (isValidRingingCall()) {
            hangup(cause);
        }
        super.onReject(cause);
    }
    /// @}

    /// M: CC041: Interface for ECT @{
    @Override
    public void onExplicitCallTransfer() {
        Log.v(this, "onExplicitCallTransfer");
        Phone phone = mOriginalConnection.getCall().getPhone();
        try {
            phone.explicitCallTransfer();
        } catch (CallStateException e) {
            Log.e(this, e, "Exception occurred while trying to do ECT.");
        }
    }
    /// @}

    /// M: CC026: Interface for hangup all connections @{
    @Override
    public void onHangupAll() {
        Log.v(this, "onHangupAll");
        if (mOriginalConnection != null) {
            try {
                Phone phone = getPhone();
                if (phone != null) {
                    phone.hangupAll();
                } else {
                    Log.w(this, "Attempting to hangupAll a connection without backing phone.");
                }
            } catch (CallStateException e) {
                Log.e(this, e, "Call to phone.hangupAll() failed with exception");
            }
        }
    }
    /// @}

    /// M: CC042: [ALPS01850287] TelephonyConnection destroy itself upon user hanging up @{
    void onLocalDisconnected() {
        Log.v(this, "mOriginalConnection is null, local disconnect the call");
        setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                android.telephony.DisconnectCause.LOCAL));
        close();
        updateConnectionCapabilities();
    }
    /// @}

    public void performHold() {
        Log.v(this, "performHold");
        // TODO: Can dialing calls be put on hold as well since they take up the
        // foreground call slot?
        if (Call.State.ACTIVE == mOriginalConnectionState) {
            Log.v(this, "Holding active call");
            try {
                Phone phone = mOriginalConnection.getCall().getPhone();
                Call ringingCall = phone.getRingingCall();

                // Although the method says switchHoldingAndActive, it eventually calls a RIL method
                // called switchWaitingOrHoldingAndActive. What this means is that if we try to put
                // a call on hold while a call-waiting call exists, it'll end up accepting the
                // call-waiting call, which is bad if that was not the user's intention. We are
                // cheating here and simply skipping it because we know any attempt to hold a call
                // while a call-waiting call is happening is likely a request from Telecom prior to
                // accepting the call-waiting call.
                // TODO: Investigate a better solution. It would be great here if we
                // could "fake" hold by silencing the audio and microphone streams for this call
                // instead of actually putting it on hold.
                if (ringingCall.getState() != Call.State.WAITING) {
                    phone.switchHoldingAndActive();
                }

                // TODO: Cdma calls are slightly different.
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to put call on hold.");
            }
        } else {
            Log.w(this, "Cannot put a call that is not currently active on hold.");
        }
    }

    /// M: CC078: For DSDS/DSDA Two-action operation @{
    public void performHold(String pendingCallAction) {
        Log.v(this, "performHold, pendingCallAction %s", pendingCallAction);
        // TODO: Can dialing calls be put on hold as well since they take up the
        // foreground call slot?
        if (Call.State.ACTIVE == mOriginalConnectionState) {
            Log.v(this, "Holding active call");
            try {
                Phone phone = mOriginalConnection.getCall().getPhone();
                Call ringingCall = phone.getRingingCall();

                // Although the method says switchHoldingAndActive, it eventually calls a RIL method
                // called switchWaitingOrHoldingAndActive. What this means is that if we try to put
                // a call on hold while a call-waiting call exists, it'll end up accepting the
                // call-waiting call, which is bad if that was not the user's intention. We are
                // cheating here and simply skipping it because we know any attempt to hold a call
                // while a call-waiting call is happening is likely a request from Telecom prior to
                // accepting the call-waiting call.
                // TODO: Investigate a better solution. It would be great here if we
                // could "fake" hold by silencing the audio and microphone streams for this call
                // instead of actually putting it on hold.
                /// M: CC043: [ALPS01843977] Fake hold for 1A1W @{
                if (ringingCall.getState() != Call.State.WAITING) {
                    phone.switchHoldingAndActive();
                } else {
                    // fake the hold state
                    // in the case waiting call exists, hold action means telecomm will call answer later
                    // so we just return and then answer action will do "hold and answer"
                    // FIXME: we should add more proteection here to check concurrency event
                    //        something likes moden send indication (ex MT / remote end call)
                    // 1. To fake Telecomm FW state
                    if ("answer".equals(pendingCallAction)) {
                        setOnHold();
                        // 2. To fake Legacy Connection state
                        // in case 1W disconnects during switch, 1A remains ACTIVE and unsynced
                        mOriginalConnectionState = Call.State.HOLDING;
                    }
                }
                /// @}

                // TODO: Cdma calls are slightly different.
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to put call on hold.");
            }
        } else {
            Log.w(this, "Cannot put a call that is not currently active on hold.");
        }
    }
    /// @}

    public void performUnhold() {
        Log.v(this, "performUnhold");
        if (Call.State.HOLDING == mOriginalConnectionState) {
            try {
                /// M: CC044: Not skip unhold since proprietary swap is implemented @{
                // for the case that there are more than one calls on the same phone,
                // telecomm will use swap() instead of "hold and unhold"
                mOriginalConnection.getCall().getPhone().switchHoldingAndActive();

                /*
                // Here's the deal--Telephony hold/unhold is weird because whenever there exists
                // more than one call, one of them must always be active. In other words, if you
                // have an active call and holding call, and you put the active call on hold, it
                // will automatically activate the holding call. This is weird with how Telecom
                // sends its commands. When a user opts to "unhold" a background call, telecom
                // issues hold commands to all active calls, and then the unhold command to the
                // background call. This means that we get two commands...each of which reduces to
                // switchHoldingAndActive(). The result is that they simply cancel each other out.
                // To fix this so that it works well with telecom we add a minor hack. If we
                // have one telephony call, everything works as normally expected. But if we have
                // two or more calls, we will ignore all requests to "unhold" knowing that the hold
                // requests already do what we want. If you've read up to this point, I'm very sorry
                // that we are doing this. I didn't think of a better solution that wouldn't also
                // make the Telecom APIs very ugly.

                if (!hasMultipleTopLevelCalls()) {
                    mOriginalConnection.getCall().getPhone().switchHoldingAndActive();
                } else {
                    Log.i(this, "Skipping unhold command for %s", this);
                }
                */
                    /// @}
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to release call from hold.");
            }
        } else {
            Log.w(this, "Cannot release a call that is not already on hold from hold.");
        }
    }

    public void performConference(TelephonyConnection otherConnection) {
        Log.d(this, "performConference - %s", this);
        if (getPhone() != null) {
            try {
                // We dont use the "other" connection because there is no concept of that in the
                // implementation of calls inside telephony. Basically, you can "conference" and it
                // will conference with the background call.  We know that otherConnection is the
                // background call because it would never have called setConferenceableConnections()
                // otherwise.
                getPhone().conference();
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to conference call.");
            }
        }
    }

    /**
     * Builds call capabilities common to all TelephonyConnections. Namely, apply IMS-based
     * capabilities.
     */
    protected int buildConnectionCapabilities() {
        int callCapabilities = 0;
        /// M: for 3GPP-CR C1-124200, ECC could not be held. @{
        // Put the logic into TelephonyConnectionService.canHold().
        /*
        if (isImsConnection()) {
            callCapabilities |= CAPABILITY_SUPPORT_HOLD;
            if (getState() == STATE_ACTIVE || getState() == STATE_HOLDING) {
                callCapabilities |= CAPABILITY_HOLD;
            }
        }
        */
        /// @}
        return callCapabilities;
    }

    protected final void updateConnectionCapabilities() {
        int newCapabilities = buildConnectionCapabilities();
        newCapabilities = applyVideoCapabilities(newCapabilities);
        newCapabilities = applyAudioQualityCapabilities(newCapabilities);
        newCapabilities = applyConferenceTerminationCapabilities(newCapabilities);

        /// M: WFC call type update. @{
        newCapabilities = applyCallTypeCapabilities(newCapabilities);
        /// @}

        if (getConnectionCapabilities() != newCapabilities) {
            setConnectionCapabilities(newCapabilities);
        }
    }

    protected final void updateAddress() {
        /// M: CC045: [ALPS01790186] Force updateState to sync state between Telecom & Telephony @{
        // Remove duplicate updateState() will updateConnectionCapabilities() & updateAddress()
        // updateConnectionCapabilities();
        /// @}
        if (mOriginalConnection != null) {
            Uri address = getAddressFromNumber(mOriginalConnection.getAddress());
            int presentation = mOriginalConnection.getNumberPresentation();
            if (!Objects.equals(address, getAddress()) ||
                    presentation != getAddressPresentation()) {
                Log.v(this, "updateAddress, address changed");
                setAddress(address, presentation);
            }

            String name = mOriginalConnection.getCnapName();
            int namePresentation = mOriginalConnection.getCnapNamePresentation();
            if (!Objects.equals(name, getCallerDisplayName()) ||
                    namePresentation != getCallerDisplayNamePresentation()) {
                Log.v(this, "updateAddress, caller display name changed");
                setCallerDisplayName(name, namePresentation);
            }
        }
    }

    void onRemovedFromCallService() {
        // Subclass can override this to do cleanup.
    }

    void setOriginalConnection(com.android.internal.telephony.Connection originalConnection) {
        //Log.v(this, "new TelephonyConnection, originalConnection: " + originalConnection);
        clearOriginalConnection();

        mOriginalConnection = originalConnection;
        getPhone().registerForPreciseCallStateChanged(
                mHandler, MSG_PRECISE_CALL_STATE_CHANGED, null);
        getPhone().registerForHandoverStateChanged(
                mHandler, MSG_HANDOVER_STATE_CHANGED, null);
        getPhone().registerForRingbackTone(mHandler, MSG_RINGBACK_TONE, null);
        getPhone().registerForDisconnect(mHandler, MSG_DISCONNECT, null);
        /// M: CC031: Radio off notification @{
        getPhone().registerForRadioOffOrNotAvailable(
                mHandler, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        /// @}
        getPhone().registerForCdmaCallAccepted(mHandler, EVENT_CDMA_CALL_ACCEPTED, null);
        /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
        getPhone().registerForSpeechCodecInfo(mHandler, EVENT_SPEECH_CODEC_INFO, null);
        /// @}

        /// M: IMS feature. @{
        getPhone().registerForCallInfoChangedNotification(
                mHandler, EVENT_CALL_INFO_CHANGED_NOTIFICATION, null);
        /// @}

        mOriginalConnection.addPostDialListener(mPostDialListener);
        mOriginalConnection.addListener(mOriginalConnectionListener);

        // Set video state and capabilities
        setVideoState(mOriginalConnection.getVideoState());
        setLocalVideoCapable(mOriginalConnection.isLocalVideoCapable());
        setRemoteVideoCapable(mOriginalConnection.isRemoteVideoCapable());
        setVideoProvider(mOriginalConnection.getVideoProvider());
        setAudioQuality(mOriginalConnection.getAudioQuality());

        /// M: WFC update call type. @{
        setCallType(mOriginalConnection.getCallType());
        /// @}

        if (isImsConnection()) {
            mWasImsConnection = true;
        }
        mIsMultiParty = mOriginalConnection.isMultiparty();

        fireOnOriginalConnectionConfigured();
        /// M: CC045: [ALPS01790186] Force updateState to sync state between Telecom & Telephony @{
        updateAddress();
        updateState();
        /// @}
    }

    /**
     * Un-sets the underlying radio connection.
     */
    void clearOriginalConnection() {
        if (mOriginalConnection != null) {
            getPhone().unregisterForPreciseCallStateChanged(mHandler);
            getPhone().unregisterForRingbackTone(mHandler);
            getPhone().unregisterForHandoverStateChanged(mHandler);
            getPhone().unregisterForDisconnect(mHandler);
            /// M: CC031: Radio off notification @{
            getPhone().unregisterForRadioOffOrNotAvailable(mHandler);
            /// @}
            getPhone().unregisterForCdmaCallAccepted(mHandler);
            /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
            getPhone().unregisterForSpeechCodecInfo(mHandler);
            /// @}

            /// M: IMS feature. @{
            getPhone().unregisterForCallInfoChangedNotification(mHandler);
            /// @}
            mOriginalConnection = null;
        }
    }

    /// M: CC040: The telephonyDisconnectCode is not used here @{
    protected void hangup() {
    //protected void hangup(int telephonyDisconnectCode) {
    /// @}
        if (mOriginalConnection != null) {
            try {
                // Hanging up a ringing call requires that we invoke call.hangup() as opposed to
                // connection.hangup(). Without this change, the party originating the call will not
                // get sent to voicemail if the user opts to reject the call.
                if (isValidRingingCall()) {
                    Call call = getCall();
                    if (call != null) {
                        call.hangup();
                    } else {
                        Log.w(this, "Attempting to hangup a connection without backing call.");
                    }
                } else {
                    // We still prefer to call connection.hangup() for non-ringing calls in order
                    // to support hanging-up specific calls within a conference call. If we invoked
                    // call.hangup() while in a conference, we would end up hanging up the entire
                    // conference call instead of the specific connection.
                    mOriginalConnection.hangup();
                }
            } catch (CallStateException e) {
                Log.e(this, e, "Call to Connection.hangup failed with exception");
            }
        }
        /// M: CC042: [ALPS01850287] TelephonyConnection destroy itself upon user hanging up @{
        else {
            onLocalDisconnected();
        }
        /// @}
    }

    /// M: CC040: Reject call with cause for HFP @{
    //The Disconnect Cause is passed to Telephony Framework
    protected void hangup(int cause) {
        if (mOriginalConnection != null) {
            try {
                // Hanging up a ringing call requires that we invoke call.hangup() as opposed to
                // connection.hangup(). Without this change, the party originating the call will not
                // get sent to voicemail if the user opts to reject the call.
                if (isValidRingingCall()) {
                    Call call = getCall();
                    if (call != null) {
                        call.hangup(cause);
                    } else {
                        Log.w(this, "Attempting to hangup a connection without backing call.");
                    }
                } else {
                    // We still prefer to call connection.hangup() for non-ringing calls in order
                    // to support hanging-up specific calls within a conference call. If we invoked
                    // call.hangup() while in a conference, we would end up hanging up the entire
                    // conference call instead of the specific connection.
                    mOriginalConnection.hangup(cause);
                }
            } catch (CallStateException e) {
                Log.e(this, e, "Call to Connection.hangup failed with exception");
            }
        }
        /// M: CC042: [ALPS01850287] TelephonyConnection destroy itself upon user hanging up @{
        else {
            onLocalDisconnected();
        }
        /// @}
    }
    /// @}

    /// M: CC030: CRSS notification @{
    // Declare as public for SuppMessageManager to use.
    public com.android.internal.telephony.Connection getOriginalConnection() {
    /// @}
        return mOriginalConnection;
    }

    protected Call getCall() {
        if (mOriginalConnection != null) {
            return mOriginalConnection.getCall();
        }
        return null;
    }

    Phone getPhone() {
        Call call = getCall();
        if (call != null) {
            return call.getPhone();
        }
        return null;
    }

    private boolean hasMultipleTopLevelCalls() {
        int numCalls = 0;
        Phone phone = getPhone();
        if (phone != null) {
            if (!phone.getRingingCall().isIdle()) {
                numCalls++;
            }
            if (!phone.getForegroundCall().isIdle()) {
                numCalls++;
            }
            if (!phone.getBackgroundCall().isIdle()) {
                numCalls++;
            }
        }
        return numCalls > 1;
    }

    private com.android.internal.telephony.Connection getForegroundConnection() {
        if (getPhone() != null) {
            return getPhone().getForegroundCall().getEarliestConnection();
        }
        return null;
    }

    /**
     * Checks to see the original connection corresponds to an active incoming call. Returns false
     * if there is no such actual call, or if the associated call is not incoming (See
     * {@link Call.State#isRinging}).
     */
    /// M: CC027: Proprietary scheme to build Connection Capabilities @{
    // Declare as default for TelephonyConnectionService (in same package) to use
    /* private */ boolean isValidRingingCall() {
    /// @}
        if (getPhone() == null) {
            Log.v(this, "isValidRingingCall, phone is null");
            return false;
        }

        Call ringingCall = getPhone().getRingingCall();
        if (!ringingCall.getState().isRinging()) {
            Log.v(this, "isValidRingingCall, ringing call is not in ringing state");
            return false;
        }

        if (ringingCall.getEarliestConnection() != mOriginalConnection) {
            Log.v(this, "isValidRingingCall, ringing call connection does not match");
            return false;
        }

        Log.v(this, "isValidRingingCall, returning true");
        return true;
    }

    /// M: CC046: Force updateState for Connection once its ConnectionService is set @{
    @Override
    protected void fireOnCallState() {
        updateState();
    }
    /// @}

    void updateState() {
        if (mOriginalConnection == null) {
            return;
        }

        /// M: tune the antenna for cdma when gsm call alive/idle.
        if (this instanceof GsmConnection) {
            ConnectionService cs = getConnectionService();
            if (cs instanceof TelephonyConnectionService) {
                TelephonyConnectionService tcs = (TelephonyConnectionService) cs;
                tcs.handleSwitchHPF();
            }
        }

        Call.State newState = mOriginalConnection.getState();
        //Log.v(this, "Update state from %s to %s for %s", mOriginalConnectionState, newState, this);
        if (mOriginalConnectionState != newState) {
            mOriginalConnectionState = newState;
            switch (newState) {
                case IDLE:
                    break;
                case ACTIVE:
                    setActiveInternal();
                    break;
                case HOLDING:
                    setOnHold();
                    break;
                case DIALING:
                case ALERTING:
                    setDialing();
                    break;
                case INCOMING:
                case WAITING:
                    setRinging();
                    break;
                case DISCONNECTED:
                    setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                            mOriginalConnection.getDisconnectCause()));
                    close();
                    break;
                case DISCONNECTING:
                    break;
            }
        }
        updateConnectionCapabilities();
        updateAddress();
        updateMultiparty();
    }

    /**
     * Checks for changes to the multiparty bit.  If a conference has started, informs listeners.
     */
    private void updateMultiparty() {
        if (mOriginalConnection == null) {
            return;
        }

        if (mIsMultiParty != mOriginalConnection.isMultiparty()) {
            mIsMultiParty = mOriginalConnection.isMultiparty();

            if (mIsMultiParty) {
                notifyConferenceStarted();
            }
        }
    }

    private void setActiveInternal() {
        if (getState() == STATE_ACTIVE) {
            Log.w(this, "Should not be called if this is already ACTIVE");
            return;
        }

        // When we set a call to active, we need to make sure that there are no other active
        // calls. However, the ordering of state updates to connections can be non-deterministic
        // since all connections register for state changes on the phone independently.
        // To "optimize", we check here to see if there already exists any active calls.  If so,
        // we issue an update for those calls first to make sure we only have one top-level
        // active call.
        if (getConnectionService() != null) {
            for (Connection current : getConnectionService().getAllConnections()) {
                if (current != this && current instanceof TelephonyConnection) {
                    TelephonyConnection other = (TelephonyConnection) current;
                    if (other.getState() == STATE_ACTIVE) {
                        other.updateState();
                    }
                }
            }
        }

        /// M: Cdma special handle @{
        // If the cdma connection is in FORCE DIALING status,
        // not update the state to ACTIVE, this will be done
        // after stop the FORCE DIALING
        if (isForceDialing()) {
            return;
        }
        /// @}
        setActive();
    }

    private void close() {
        Log.v(this, "close");
        if (getPhone() != null) {

            /// M: CC034: Stop DTMF when TelephonyConnection is disconnected @{
            if (mDtmfRequestIsStarted) {
                onStopDtmfTone();
                mDtmfRequestIsStarted = false;
            }
            /// @}

            getPhone().unregisterForPreciseCallStateChanged(mHandler);
            getPhone().unregisterForRingbackTone(mHandler);
            getPhone().unregisterForHandoverStateChanged(mHandler);
            /// M: CC031: Radio off notification @{
            getPhone().unregisterForRadioOffOrNotAvailable(mHandler);
            /// @}
            /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
            getPhone().unregisterForSpeechCodecInfo(mHandler);
            /// @}
        }
        mOriginalConnection = null;
        destroy();
    }

    /**
     * Applies the video capability states to the CallCapabilities bit-mask.
     *
     * @param capabilities The CallCapabilities bit-mask.
     * @return The capabilities with video capabilities applied.
     */
    private int applyVideoCapabilities(int capabilities) {
        int currentCapabilities = capabilities;
        if (mRemoteVideoCapable) {
            currentCapabilities = applyCapability(currentCapabilities,
                    CAPABILITY_SUPPORTS_VT_REMOTE);
        } else {
            currentCapabilities = removeCapability(currentCapabilities,
                    CAPABILITY_SUPPORTS_VT_REMOTE);
        }

        if (mLocalVideoCapable) {
            currentCapabilities = applyCapability(currentCapabilities,
                    CAPABILITY_SUPPORTS_VT_LOCAL);
        } else {
            currentCapabilities = removeCapability(currentCapabilities,
                    CAPABILITY_SUPPORTS_VT_LOCAL);
        }
        return currentCapabilities;
    }

    /// M: WFC update call type. @{
    private int applyCallTypeCapabilities(int capabilities) {
        int currentCapabilities = capabilities;
        if (mCallType == 1) {   /* VoLTE */
            currentCapabilities = applyCapability(currentCapabilities, CAPABILITY_VOLTE);
            currentCapabilities = removeCapability(currentCapabilities, CAPABILITY_VoWIFI);
        } else if (mCallType == 2) { /* VoWIFI */
            currentCapabilities = applyCapability(currentCapabilities, CAPABILITY_VoWIFI);
            currentCapabilities = removeCapability(currentCapabilities, CAPABILITY_VOLTE);
        }

        return currentCapabilities;
    }
    /// @}

    /**
     * Applies the audio capabilities to the {@code CallCapabilities} bit-mask.  A call with high
     * definition audio is considered to have the {@code HIGH_DEF_AUDIO} call capability.
     *
     * @param capabilities The {@code CallCapabilities} bit-mask.
     * @return The capabilities with the audio capabilities applied.
     */
    private int applyAudioQualityCapabilities(int capabilities) {
        int currentCapabilities = capabilities;

        if (mAudioQuality ==
                com.android.internal.telephony.Connection.AUDIO_QUALITY_HIGH_DEFINITION) {
            currentCapabilities = applyCapability(currentCapabilities, CAPABILITY_HIGH_DEF_AUDIO);
        } else {
            currentCapabilities = removeCapability(currentCapabilities, CAPABILITY_HIGH_DEF_AUDIO);
        }

        return currentCapabilities;
    }

    /**
     * Applies capabilities specific to conferences termination to the
     * {@code CallCapabilities} bit-mask.
     *
     * @param capabilities The {@code CallCapabilities} bit-mask.
     * @return The capabilities with the IMS conference capabilities applied.
     */
    private int applyConferenceTerminationCapabilities(int capabilities) {
        int currentCapabilities = capabilities;

        // An IMS call cannot be individually disconnected or separated from its parent conference.
        // If the call was IMS, even if it hands over to GMS, these capabilities are not supported.
        /// M: CC085 Fix Google bug. Check IMS current status for CS conference call @{
        //if (!mWasImsConnection) {
        if (!isImsConnection()) {
        /// @}
            currentCapabilities |= CAPABILITY_DISCONNECT_FROM_CONFERENCE;
            /// M: CC029: DSDA conference @{
            /**
             * Don't always update call capability CAPABILITY_SEPARATE_FROM_CONFERENCE to Telecom.
             * Telephony use canSeparate() function to decide the call capability and
             * move the capability to buildConnectionCapabilities() in GsmConnection.java.
             */
            //currentCapabilities |= CAPABILITY_SEPARATE_FROM_CONFERENCE;
            /// @}
        }

        return currentCapabilities;
    }

    /**
     * Returns the local video capability state for the connection.
     *
     * @return {@code True} if the connection has local video capabilities.
     */
    public boolean isLocalVideoCapable() {
        return mLocalVideoCapable;
    }

    /**
     * Returns the remote video capability state for the connection.
     *
     * @return {@code True} if the connection has remote video capabilities.
     */
    public boolean isRemoteVideoCapable() {
        return mRemoteVideoCapable;
    }

    /**
     * Sets whether video capability is present locally.  Used during rebuild of the
     * capabilities to set the video call capabilities.
     *
     * @param capable {@code True} if video capable.
     */
    public void setLocalVideoCapable(boolean capable) {
        mLocalVideoCapable = capable;
        updateConnectionCapabilities();
    }

    /**
     * Sets whether video capability is present remotely.  Used during rebuild of the
     * capabilities to set the video call capabilities.
     *
     * @param capable {@code True} if video capable.
     */
    public void setRemoteVideoCapable(boolean capable) {
        mRemoteVideoCapable = capable;
        updateConnectionCapabilities();
    }


    /// M: WFC update call type. @{
    /**
     * Sets CallType.  Used during rebuild of the
     * {@link PhoneCapabilities} to set the call type.
     * @param callType the call type of current call
     */
    public void setCallType(int callType) {
       mCallType = callType;
       updateConnectionCapabilities();
    }
    /// @}

    /**
     * Sets the current call audio quality.  Used during rebuild of the capabilities
     * to set or unset the {@link Connection#CAPABILITY_HIGH_DEF_AUDIO} capability.
     *
     * @param audioQuality The audio quality.
     */
    public void setAudioQuality(int audioQuality) {
        mAudioQuality = audioQuality;
        updateConnectionCapabilities();
    }

    /**
     * Obtains the current call audio quality.
     */
    public int getAudioQuality() {
        return mAudioQuality;
    }

    void resetStateForConference() {
        if (getState() == Connection.STATE_HOLDING) {
            if (mOriginalConnection.getState() == Call.State.ACTIVE) {
                setActive();
            }
        }
    }

    boolean setHoldingForConference() {
        if (getState() == Connection.STATE_ACTIVE) {
            setOnHold();
            return true;
        }
        return false;
    }

    /**
     * Whether the original connection is an IMS connection.
     * @return {@code True} if the original connection is an IMS connection, {@code false}
     *     otherwise.
     */
    protected boolean isImsConnection() {
        return getOriginalConnection() instanceof ImsPhoneConnection;
    }

    /**
     * Whether the original connection was ever an IMS connection, either before or now.
     * @return {@code True} if the original connection was ever an IMS connection, {@code false}
     *     otherwise.
     */
    public boolean wasImsConnection() {
        return mWasImsConnection;
    }

    private static Uri getAddressFromNumber(String number) {
        // Address can be null for blocked calls.
        if (number == null) {
            number = "";
        }
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
    }

    /**
     * Applies a capability to a capabilities bit-mask.
     *
     * @param capabilities The capabilities bit-mask.
     * @param capability The capability to apply.
     * @return The capabilities bit-mask with the capability applied.
     */
    private int applyCapability(int capabilities, int capability) {
        int newCapabilities = capabilities | capability;
        return newCapabilities;
    }

    /**
     * Removes a capability from a capabilities bit-mask.
     *
     * @param capabilities The capabilities bit-mask.
     * @param capability The capability to remove.
     * @return The capabilities bit-mask with the capability removed.
     */
    private int removeCapability(int capabilities, int capability) {
        int newCapabilities = capabilities & ~capability;
        return newCapabilities;
    }

    /**
     * Register a listener for {@link TelephonyConnection} specific triggers.
     * @param l The instance of the listener to add
     * @return The connection being listened to
     */
    public final TelephonyConnection addTelephonyConnectionListener(TelephonyConnectionListener l) {
        mTelephonyListeners.add(l);
        // If we already have an original connection, let's call back immediately.
        // This would be the case for incoming calls.
        if (mOriginalConnection != null) {
            fireOnOriginalConnectionConfigured();
        }
        return this;
    }

    /**
     * Remove a listener for {@link TelephonyConnection} specific triggers.
     * @param l The instance of the listener to remove
     * @return The connection being listened to
     */
    public final TelephonyConnection removeTelephonyConnectionListener(
            TelephonyConnectionListener l) {
        if (l != null) {
            mTelephonyListeners.remove(l);
        }
        return this;
    }

    /**
     * Fire a callback to the various listeners for when the original connection is
     * set in this {@link TelephonyConnection}
     */
    private final void fireOnOriginalConnectionConfigured() {
        for (TelephonyConnectionListener l : mTelephonyListeners) {
            l.onOriginalConnectionConfigured(this);
        }
    }

    /**
     * Creates a string representation of this {@link TelephonyConnection}.  Primarily intended for
     * use in log statements.
     *
     * @return String representation of the connection.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[TelephonyConnection objId:");
        sb.append(System.identityHashCode(this));
        sb.append(" type:");
        if (isImsConnection()) {
            sb.append("ims");
        } else if (this instanceof com.android.services.telephony.GsmConnection) {
            sb.append("gsm");
        } else if (this instanceof CdmaConnection) {
            sb.append("cdma");
        }
        sb.append(" state:");
        sb.append(Connection.stateToString(getState()));
        sb.append(" capabilities:");
        sb.append(capabilitiesToString(getConnectionCapabilities()));
        sb.append(" address:");
        sb.append(Log.pii(getAddress()));
        sb.append(" originalConnection:");
        sb.append(mOriginalConnection);
        sb.append(" partOfConf:");
        if (getConference() == null) {
            sb.append("N");
        } else {
            sb.append("Y");
        }
        sb.append("]");
        return sb.toString();
    }

    /// M: @{
    /**
     * Notify this connection is ECC.
     */
    public void notifyEcc() {
        Bundle bundleECC = new Bundle();
        bundleECC.putBoolean(TelecomManagerEx.EXTRA_VOLTE_MARKED_AS_EMERGENCY, true);
        setCallInfo(bundleECC);
    }
    /// @}

    /// M: For VoLTE enhanced conference call. @{
    void performInviteConferenceParticipants(List<String> numbers) {
        if (mOriginalConnection == null) {
            Log.e(this, new CallStateException(), "no orginal connection to inviteParticipants");
            return;
        }

        if (!isImsConnection()) {
            Log.e(this, new CallStateException(), "CS connection doesn't support invite!");
            return;
        }

        ((ImsPhoneConnection) mOriginalConnection).onInviteConferenceParticipants(numbers);
    }

    /**
     * This function used to notify the onInviteConferenceParticipants() operation is done.
     * @param isSuccess is success or not
     * @hide
     */
    protected void notifyConferenceParticipantsInvited(boolean isSuccess) {
        for (TelephonyConnectionListener l : mTelephonyListeners) {
            l.onConferenceParticipantsInvited(isSuccess);
        }
    }

    /**
     * For conference SRVCC.
     * @param radioConnections new participant connections
     */
    private void notifyConferenceConnectionsConfigured(
            ArrayList<com.android.internal.telephony.Connection> radioConnections) {
        for (TelephonyConnectionListener l : mTelephonyListeners) {
            l.onConferenceConnectionsConfigured(radioConnections);
        }
    }
    /// @}

    /**
     * Used for cdma special handle.
     * @return
     */
    boolean isForceDialing() {
        return false;
    }
}
