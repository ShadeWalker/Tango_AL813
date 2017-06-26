/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.AudioState;
import android.telecom.CallState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.telecom.ParcelableConference;
import android.telecom.ParcelableConnection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.TextUtils;

import com.android.internal.os.SomeArgs;
import com.android.internal.telecom.IConnectionService;
import com.android.internal.telecom.IConnectionServiceAdapter;
import com.android.internal.telecom.IVideoProvider;
import com.android.internal.telecom.RemoteServiceCallback;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper for {@link IConnectionService}s, handles binding to {@link IConnectionService} and keeps
 * track of when the object can safely be unbound. Other classes should not use
 * {@link IConnectionService} directly and instead should use this class to invoke methods of
 * {@link IConnectionService}.
 */
final class ConnectionServiceWrapper extends ServiceBinder<IConnectionService> {
    private static final int MSG_HANDLE_CREATE_CONNECTION_COMPLETE = 1;
    private static final int MSG_SET_ACTIVE = 2;
    private static final int MSG_SET_RINGING = 3;
    private static final int MSG_SET_DIALING = 4;
    private static final int MSG_SET_DISCONNECTED = 5;
    private static final int MSG_SET_ON_HOLD = 6;
    private static final int MSG_SET_RINGBACK_REQUESTED = 7;
    private static final int MSG_SET_CONNECTION_CAPABILITIES = 8;
    private static final int MSG_SET_IS_CONFERENCED = 9;
    private static final int MSG_ADD_CONFERENCE_CALL = 10;
    private static final int MSG_REMOVE_CALL = 11;
    private static final int MSG_ON_POST_DIAL_WAIT = 12;
    private static final int MSG_QUERY_REMOTE_CALL_SERVICES = 13;
    private static final int MSG_SET_VIDEO_PROVIDER = 14;
    private static final int MSG_SET_IS_VOIP_AUDIO_MODE = 15;
    private static final int MSG_SET_STATUS_HINTS = 16;
    private static final int MSG_SET_ADDRESS = 17;
    private static final int MSG_SET_CALLER_DISPLAY_NAME = 18;
    private static final int MSG_SET_VIDEO_STATE = 19;
    private static final int MSG_SET_CONFERENCEABLE_CONNECTIONS = 20;
    private static final int MSG_ADD_EXISTING_CONNECTION = 21;
    private static final int MSG_ON_POST_DIAL_CHAR = 22;

    /* M: CC part start */
    private static final int MTK_MSG_BASE = 1000;
    private static final int MSG_NOTIFY_CONNECTION_LOST = MTK_MSG_BASE;
    private static final int MSG_NOTIFY_ACTION_FAILED = MTK_MSG_BASE + 1;
    private static final int MSG_NOTIFY_SS_TOAST = MTK_MSG_BASE + 2;
    private static final int MSG_NOTIFY_NUMBER_UPDATE = MTK_MSG_BASE + 3;
    private static final int MSG_NOTIFY_INCOMING_INFO_UPDATE = MTK_MSG_BASE + 4;
    private static final int MSG_NOTIFY_CDMA_CALL_ACCEPTED = MTK_MSG_BASE + 5;
    /* M: CC part end */

    /// M: For volte @{
    private static final int MSG_UPDATE_EXTRAS = 180;
    private static final int MSG_HANDLE_CREATE_CONFERENCE_COMPLETE = 182;
    /// @}

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            Call call;
            switch (msg.what) {
                case MSG_HANDLE_CREATE_CONNECTION_COMPLETE: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        String callId = (String) args.arg1;
                        ConnectionRequest request = (ConnectionRequest) args.arg2;
                        ParcelableConnection connection = (ParcelableConnection) args.arg3;
                        handleCreateConnectionComplete(callId, request, connection);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_ACTIVE:
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        mCallsManager.markCallAsActive(call);
                    } else {
                        //Log.w(this, "setActive, unknown call id: %s", msg.obj);
                    }
                    break;
                case MSG_SET_RINGING:
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        mCallsManager.markCallAsRinging(call);
                    } else {
                        //Log.w(this, "setRinging, unknown call id: %s", msg.obj);
                    }
                    break;
                case MSG_SET_DIALING:
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        mCallsManager.markCallAsDialing(call);
                    } else {
                        //Log.w(this, "setDialing, unknown call id: %s", msg.obj);
                    }
                    break;
                case MSG_SET_DISCONNECTED: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        call = mCallIdMapper.getCall(args.arg1);
                        DisconnectCause disconnectCause = (DisconnectCause) args.arg2;
                        //Log.d(this, "disconnect call %s %s", disconnectCause, call);
                        if (call != null) {
                            mCallsManager.markCallAsDisconnected(call, disconnectCause);
                        } else {
                            //Log.w(this, "setDisconnected, unknown call id: %s", args.arg1);
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_ON_HOLD:
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        mCallsManager.markCallAsOnHold(call);
                    } else {
                        //Log.w(this, "setOnHold, unknown call id: %s", msg.obj);
                    }
                    break;
                case MSG_SET_RINGBACK_REQUESTED: {
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        call.setRingbackRequested(msg.arg1 == 1);
                    } else {
                        //Log.w(this, "setRingback, unknown call id: %s", args.arg1);
                    }
                    break;
                }
                case MSG_SET_CONNECTION_CAPABILITIES: {
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        /// M: When capabilities changed, also need to update other calls
                        // capabilities, such as HOLD capability and ANSWER capability. @{
                        // Keep original capabilities from connection service.
                        call.setCallCapabilitiesFromConnection(msg.arg1);

                        // For ringing call, should not update capability to InCallUI before
                        // building ANSWER capability, otherwise will show wrong indication.
                        if (call.getState() == CallState.RINGING) {
                            call.buildConnectionCapabilities(msg.arg1, false);
                        } else {
                            call.setConnectionCapabilities(msg.arg1);
                        }
                        mCallsManager.updateCallCapabilities();
                        /// @}
                    } else {
                        //Log.w(ConnectionServiceWrapper.this,
                        //      "setConnectionCapabilities, unknown call id: %s", msg.obj);
                    }
                    break;
                }
                case MSG_SET_IS_CONFERENCED: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        Call childCall = mCallIdMapper.getCall(args.arg1);
                        Log.d(this, "SET_IS_CONFERENCE: %s %s", args.arg1, args.arg2);
                        if (childCall != null) {
                            String conferenceCallId = (String) args.arg2;
                            if (conferenceCallId == null) {
                                Log.d(this, "unsetting parent: %s", args.arg1);
                                childCall.setParentCall(null);
                            } else {
                                Call conferenceCall = mCallIdMapper.getCall(conferenceCallId);
                                childCall.setParentCall(conferenceCall);
                            }
                        } else {
                            //Log.w(this, "setIsConferenced, unknown call id: %s", args.arg1);
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_ADD_CONFERENCE_CALL: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        String id = (String) args.arg1;
                        if (mCallIdMapper.getCall(id) != null) {
                            Log.w(this, "Attempting to add a conference call using an existing " +
                                    "call id %s", id);
                            break;
                        }
                        ParcelableConference parcelableConference =
                                (ParcelableConference) args.arg2;

                        // Make sure that there's at least one valid call. For remote connections
                        // we'll get a add conference msg from both the remote connection service
                        // and from the real connection service.
                        boolean hasValidCalls = false;
                        for (String callId : parcelableConference.getConnectionIds()) {
                            if (mCallIdMapper.getCall(callId) != null) {
                                hasValidCalls = true;
                            }
                        }
                        // But don't bail out if the connection count is 0, because that is a valid
                        // IMS conference state.
                        if (!hasValidCalls && parcelableConference.getConnectionIds().size() > 0) {
                            Log.d(this, "Attempting to add a conference with no valid calls");
                            break;
                        }

                        // need to create a new Call
                        PhoneAccountHandle phAcc = null;
                        if (parcelableConference != null &&
                                parcelableConference.getPhoneAccount() != null) {
                            phAcc = parcelableConference.getPhoneAccount();
                        }
                        Call conferenceCall = mCallsManager.createConferenceCall(
                                phAcc, parcelableConference);
                        mCallIdMapper.addCall(conferenceCall, id);
                        conferenceCall.setConnectionService(ConnectionServiceWrapper.this);

                        Log.d(this, "adding children to conference %s phAcc %s",
                                parcelableConference.getConnectionIds(), phAcc);
                        for (String callId : parcelableConference.getConnectionIds()) {
                            Call childCall = mCallIdMapper.getCall(callId);
                            Log.d(this, "found child: %s", callId);
                            if (childCall != null) {
                                childCall.setParentCall(conferenceCall);
                                if (conferenceCall.getTargetPhoneAccount() == null) {
                                    conferenceCall.setTargetPhoneAccount(
                                            childCall.getTargetPhoneAccount());
                                }
                            }
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_REMOVE_CALL: {
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        if (call.isAlive()) {
                            mCallsManager.markCallAsDisconnected(
                                    call, new DisconnectCause(DisconnectCause.REMOTE));
                        } else {
                            mCallsManager.markCallAsRemoved(call);
                        }
                    }
                    break;
                }
                case MSG_ON_POST_DIAL_WAIT: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        call = mCallIdMapper.getCall(args.arg1);
                        if (call != null) {
                            String remaining = (String) args.arg2;
                            call.onPostDialWait(remaining);
                        } else {
                            //Log.w(this, "onPostDialWait, unknown call id: %s", args.arg1);
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_ON_POST_DIAL_CHAR: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        call = mCallIdMapper.getCall(args.arg1);
                        if (call != null) {
                            char nextChar = (char) args.argi1;
                            call.onPostDialChar(nextChar);
                        } else {
                            //Log.w(this, "onPostDialChar, unknown call id: %s", args.arg1);
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_QUERY_REMOTE_CALL_SERVICES: {
                    queryRemoteConnectionServices((RemoteServiceCallback) msg.obj);
                    break;
                }
                case MSG_SET_VIDEO_PROVIDER: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        call = mCallIdMapper.getCall(args.arg1);
                        IVideoProvider videoProvider = (IVideoProvider) args.arg2;
                        if (call != null) {
                            call.setVideoProvider(videoProvider);
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_IS_VOIP_AUDIO_MODE: {
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        call.setIsVoipAudioMode(msg.arg1 == 1);
                    }
                    break;
                }
                case MSG_SET_STATUS_HINTS: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        call = mCallIdMapper.getCall(args.arg1);
                        StatusHints statusHints = (StatusHints) args.arg2;
                        if (call != null) {
                            call.setStatusHints(statusHints);
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_ADDRESS: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        call = mCallIdMapper.getCall(args.arg1);
                        if (call != null) {
                            call.setHandle((Uri) args.arg2, args.argi1);
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_CALLER_DISPLAY_NAME: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        call = mCallIdMapper.getCall(args.arg1);
                        if (call != null) {
                            call.setCallerDisplayName((String) args.arg2, args.argi1);
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_VIDEO_STATE: {
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        call.setVideoState(msg.arg1);
                    }
                    break;
                }
                case MSG_SET_CONFERENCEABLE_CONNECTIONS: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        call = mCallIdMapper.getCall(args.arg1);
                        if (call != null ){
                            @SuppressWarnings("unchecked")
                            List<String> conferenceableIds = (List<String>) args.arg2;
                            List<Call> conferenceableCalls =
                                    new ArrayList<>(conferenceableIds.size());
                            for (String otherId : (List<String>) args.arg2) {
                                Call otherCall = mCallIdMapper.getCall(otherId);
                                if (otherCall != null && otherCall != call) {
                                    conferenceableCalls.add(otherCall);
                                }
                            }
                            call.setConferenceableCalls(conferenceableCalls);
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_ADD_EXISTING_CONNECTION: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        String callId = (String)args.arg1;
                        ParcelableConnection connection = (ParcelableConnection)args.arg2;
                        Call existingCall = mCallsManager.createCallForExistingConnection(callId,
                                connection);
                        mCallIdMapper.addCall(existingCall, callId);
                        existingCall.setConnectionService(ConnectionServiceWrapper.this);
                    } finally {
                        args.recycle();
                    }
                }

                /* M: CC part start */
                case MSG_NOTIFY_CONNECTION_LOST: {
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        mCallsManager.notifyConnectionLost(call);
                    } else {
                        Log.w(this, "notifyConnectionLost, unknown call id: %s", msg.obj);
                    }
                    break;
                }
                case MSG_NOTIFY_ACTION_FAILED: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    call = mCallIdMapper.getCall(args.arg1);
                    if (call != null) {
                        mCallsManager.notifyActionFailed(call, (int)args.arg2);
                    } else {
                        Log.w(this, "notifyActionFailed, unknown call id: %s", args.arg1);
                    }
                    break;
                }
                case MSG_NOTIFY_SS_TOAST: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    call = mCallIdMapper.getCall(args.arg1);
                    if (call != null) {
                        mCallsManager.notifySSNotificationToast(
                                call, (int)args.arg2, (int)args.arg3, (int)args.arg4, (String)args.arg5, (int)args.arg6);
                    } else {
                        Log.w(this, "notifyActionFailed, unknown call id: %s", args.arg1);
                    }
                    break;
                }
                case MSG_NOTIFY_NUMBER_UPDATE: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    call = mCallIdMapper.getCall(args.arg1);
                    if (call != null) {
                        mCallsManager.notifyNumberUpdate(call, (String)args.arg2);
                    } else {
                        Log.w(this, "notifyNumberUpdate, unknown call id: %s", args.arg1);
                    }
                    break;
                }
                case MSG_NOTIFY_INCOMING_INFO_UPDATE: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    call = mCallIdMapper.getCall(args.arg1);
                    if (call != null) {
                        mCallsManager.notifyIncomingInfoUpdate(call, (int)args.arg2, (String)args.arg3, (int)args.arg4);
                    } else {
                        Log.w(this, "notifyIncomingInfoUpdate, unknown call id: %s", args.arg1);
                    }
                    break;
                }
                case MSG_NOTIFY_CDMA_CALL_ACCEPTED: {
                    call = mCallIdMapper.getCall(msg.obj);
                    if (call != null) {
                        mCallsManager.notifyCdmaCallAccepted(call);
                    } else {
                        Log.w(this, "notifyCdmaCallAccepted, unknown call id: %s", msg.obj);
                    }
                    break;
                }
                /* M: CC part end */

                /// M: For VoLTE @{
                case MSG_UPDATE_EXTRAS: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        String callId = (String) args.arg1;
                        Bundle bundle = (Bundle) args.arg2;
                        call = mCallIdMapper.getCall(callId);
                        if (call != null) {
                            call.updateExtras(bundle);
                        }
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_HANDLE_CREATE_CONFERENCE_COMPLETE: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        String conferenceId = (String) args.arg1;
                        ConnectionRequest request = (ConnectionRequest) args.arg2;
                        ParcelableConference conference = (ParcelableConference) args.arg3;
                        handleCreateConferenceComplete(conferenceId, request, conference);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                /// @}
            }
        }
    };

    private final class Adapter extends IConnectionServiceAdapter.Stub {

        @Override
        public void handleCreateConnectionComplete(
                String callId,
                ConnectionRequest request,
                ParcelableConnection connection) {
            logIncoming("handleCreateConnectionComplete %s", request);
            if (mCallIdMapper.isValidCallId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = request;
                args.arg3 = connection;
                mHandler.obtainMessage(MSG_HANDLE_CREATE_CONNECTION_COMPLETE, args)
                        .sendToTarget();
            }
        }

        @Override
        public void setActive(String callId) {
            logIncoming("setActive %s", callId);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                mHandler.obtainMessage(MSG_SET_ACTIVE, callId).sendToTarget();
            }
        }

        @Override
        public void setRinging(String callId) {
            logIncoming("setRinging %s", callId);
            if (mCallIdMapper.isValidCallId(callId)) {
                mHandler.obtainMessage(MSG_SET_RINGING, callId).sendToTarget();
            }
        }

        @Override
        public void setVideoProvider(String callId, IVideoProvider videoProvider) {
            logIncoming("setVideoProvider %s", callId);
            if (mCallIdMapper.isValidCallId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = videoProvider;
                mHandler.obtainMessage(MSG_SET_VIDEO_PROVIDER, args).sendToTarget();
            }
        }

        @Override
        public void setDialing(String callId) {
            logIncoming("setDialing %s", callId);
            if (mCallIdMapper.isValidCallId(callId)) {
                mHandler.obtainMessage(MSG_SET_DIALING, callId).sendToTarget();
            }
        }

        @Override
        public void setDisconnected(String callId, DisconnectCause disconnectCause) {
            logIncoming("setDisconnected %s %s", callId, disconnectCause);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                //Log.d(this, "disconnect call %s", callId);
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = disconnectCause;
                mHandler.obtainMessage(MSG_SET_DISCONNECTED, args).sendToTarget();
            }
        }

        @Override
        public void setOnHold(String callId) {
            logIncoming("setOnHold %s", callId);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                mHandler.obtainMessage(MSG_SET_ON_HOLD, callId).sendToTarget();
            }
        }

        @Override
        public void setRingbackRequested(String callId, boolean ringback) {
            logIncoming("setRingbackRequested %s %b", callId, ringback);
            if (mCallIdMapper.isValidCallId(callId)) {
                mHandler.obtainMessage(MSG_SET_RINGBACK_REQUESTED, ringback ? 1 : 0, 0, callId)
                        .sendToTarget();
            }
        }

        @Override
        public void removeCall(String callId) {
            logIncoming("removeCall %s", callId);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                mHandler.obtainMessage(MSG_REMOVE_CALL, callId).sendToTarget();
            }
        }

        @Override
        public void setConnectionCapabilities(String callId, int connectionCapabilities) {
            logIncoming("setConnectionCapabilities %s %d", callId, connectionCapabilities);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                mHandler.obtainMessage(MSG_SET_CONNECTION_CAPABILITIES, connectionCapabilities, 0, callId)
                        .sendToTarget();
            } else {
                Log.w(this, "ID not valid for setCallCapabilities");
            }
        }

        @Override
        public void setIsConferenced(String callId, String conferenceCallId) {
            logIncoming("setIsConferenced %s %s", callId, conferenceCallId);
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = conferenceCallId;
            mHandler.obtainMessage(MSG_SET_IS_CONFERENCED, args).sendToTarget();
        }

        @Override
        public void addConferenceCall(String callId, ParcelableConference parcelableConference) {
            logIncoming("addConferenceCall %s %s", callId, parcelableConference);
            // We do not check call Ids here because we do not yet know the call ID for new
            // conference calls.
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = parcelableConference;
            mHandler.obtainMessage(MSG_ADD_CONFERENCE_CALL, args).sendToTarget();
        }

        @Override
        public void onPostDialWait(String callId, String remaining) throws RemoteException {
            logIncoming("onPostDialWait %s %s", callId, remaining);
            if (mCallIdMapper.isValidCallId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = remaining;
                mHandler.obtainMessage(MSG_ON_POST_DIAL_WAIT, args).sendToTarget();
            }
        }

        @Override
        public void onPostDialChar(String callId, char nextChar) throws RemoteException {
            logIncoming("onPostDialChar %s %s", callId, nextChar);
            if (mCallIdMapper.isValidCallId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.argi1 = nextChar;
                mHandler.obtainMessage(MSG_ON_POST_DIAL_CHAR, args).sendToTarget();
            }
        }

        @Override
        public void queryRemoteConnectionServices(RemoteServiceCallback callback) {
            logIncoming("queryRemoteCSs");
            mHandler.obtainMessage(MSG_QUERY_REMOTE_CALL_SERVICES, callback).sendToTarget();
        }

        @Override
        public void setVideoState(String callId, int videoState) {
            logIncoming("setVideoState %s %d", callId, videoState);
            if (mCallIdMapper.isValidCallId(callId)) {
                mHandler.obtainMessage(MSG_SET_VIDEO_STATE, videoState, 0, callId).sendToTarget();
            }
        }

        @Override
        public void setIsVoipAudioMode(String callId, boolean isVoip) {
            logIncoming("setIsVoipAudioMode %s %b", callId, isVoip);
            if (mCallIdMapper.isValidCallId(callId)) {
                mHandler.obtainMessage(MSG_SET_IS_VOIP_AUDIO_MODE, isVoip ? 1 : 0, 0,
                        callId).sendToTarget();
            }
        }

        @Override
        public void setStatusHints(String callId, StatusHints statusHints) {
            logIncoming("setStatusHints %s %s", callId, statusHints);
            if (mCallIdMapper.isValidCallId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = statusHints;
                mHandler.obtainMessage(MSG_SET_STATUS_HINTS, args).sendToTarget();
            }
        }

        @Override
        public void setAddress(String callId, Uri address, int presentation) {
            logIncoming("setAddress %s %s %d", callId, address, presentation);
            /// M: For VoLTE @{
            // the call created from addExistingConnection() do not have prefix,
            // which can not pass isValidCallId() check, so need modify.
            // Original Code:
            // if (mCallIdMapper.isValidCallId(callId)) {
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
            /// @}
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = address;
                args.argi1 = presentation;
                mHandler.obtainMessage(MSG_SET_ADDRESS, args).sendToTarget();
            }
        }

        @Override
        public void setCallerDisplayName(
                String callId, String callerDisplayName, int presentation) {
            logIncoming("setCallerDisplayName %s %s %d", callId, callerDisplayName, presentation);
            if (mCallIdMapper.isValidCallId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = callerDisplayName;
                args.argi1 = presentation;
                mHandler.obtainMessage(MSG_SET_CALLER_DISPLAY_NAME, args).sendToTarget();
            }
        }

        @Override
        public void setConferenceableConnections(
                String callId, List<String> conferenceableCallIds) {
            logIncoming("setConferenceableConnections %s %s", callId, conferenceableCallIds);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = conferenceableCallIds;
                mHandler.obtainMessage(MSG_SET_CONFERENCEABLE_CONNECTIONS, args).sendToTarget();
            }
        }

        @Override
        public void addExistingConnection(String callId, ParcelableConnection connection) {
            logIncoming("addExistingConnection  %s %s", callId, connection);
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = connection;
            mHandler.obtainMessage(MSG_ADD_EXISTING_CONNECTION, args).sendToTarget();
        }

        /* M: CC part start */
        @Override
        public void notifyConnectionLost(String callId) {
            logIncoming("notifyConnectionLost %s %d", callId);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                mHandler.obtainMessage(MSG_NOTIFY_CONNECTION_LOST, callId).sendToTarget();
            }
        }

        @Override
        public void notifyActionFailed(String callId, int action) {
            logIncoming("notifyActionFailed %s | %d", callId, action);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = action;
                mHandler.obtainMessage(MSG_NOTIFY_ACTION_FAILED, args).sendToTarget();
            }
        }

        @Override
        public void notifySSNotificationToast(String callId, int notiType, int type, int code, String number, int index) {
            logIncoming("notifySSNotificationToast %s | %d |%d | %d | %s | %d", callId, notiType, type, code, number, index);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = notiType;
                args.arg3 = type;
                args.arg4 = code;
                args.arg5 = number;
                args.arg6 = index;
                mHandler.obtainMessage(MSG_NOTIFY_SS_TOAST, args).sendToTarget();
            }
        }

        @Override
        public void notifyNumberUpdate(String callId, String number) {
            logIncoming("notifySSNotificationToast %s | %s ", callId, number);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = number;
                mHandler.obtainMessage(MSG_NOTIFY_NUMBER_UPDATE, args).sendToTarget();
            }
        }

        @Override
        public void notifyIncomingInfoUpdate(String callId, int type, String alphaid, int cli_validity) {
            logIncoming("notifySSNotificationToast %s | %d | %s | %d", callId, type, alphaid, cli_validity);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = type;
                args.arg3 = alphaid;
                args.arg4 = cli_validity;
                mHandler.obtainMessage(MSG_NOTIFY_INCOMING_INFO_UPDATE, args).sendToTarget();
            }
        }

        @Override
        public void notifyCdmaCallAccepted(String callId) {
            logIncoming("notifyCdmaCallAccepted %s", callId);
            if (mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId)) {
                mHandler.obtainMessage(MSG_NOTIFY_CDMA_CALL_ACCEPTED, callId).sendToTarget();
            }
        }
        /* M: CC part end */

        /// M: For Volte @{
        @Override
        public void updateExtras(String callId, Bundle bundle) {
            logIncoming("updateExtras %s %s", callId, bundle);
            if (mCallIdMapper.isValidCallId(callId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = callId;
                args.arg2 = bundle;
                mHandler.obtainMessage(MSG_UPDATE_EXTRAS, args).sendToTarget();
            }
        }

        @Override
        public void handleCreateConferenceComplete(
                String conferenceId,
                ConnectionRequest request,
                ParcelableConference conference) {
            logIncoming("handleCreateConferenceComplete %s", request);
            if (mCallIdMapper.isValidCallId(conferenceId)) {
                SomeArgs args = SomeArgs.obtain();
                args.arg1 = conferenceId;
                args.arg2 = request;
                args.arg3 = conference;
                mHandler.obtainMessage(MSG_HANDLE_CREATE_CONFERENCE_COMPLETE, args)
                        .sendToTarget();
            }
        }
        /// @}
    }

    private final Adapter mAdapter = new Adapter();
    private final CallsManager mCallsManager = CallsManager.getInstance();
    /**
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<Call> mPendingConferenceCalls = Collections.newSetFromMap(
            new ConcurrentHashMap<Call, Boolean>(8, 0.9f, 1));
    private final CallIdMapper mCallIdMapper = new CallIdMapper("ConnectionService");
    private final Map<String, CreateConnectionResponse> mPendingResponses = new HashMap<>();

    private Binder mBinder = new Binder();
    private IConnectionService mServiceInterface;
    private final ConnectionServiceRepository mConnectionServiceRepository;
    private final PhoneAccountRegistrar mPhoneAccountRegistrar;

    /**
     * Creates a connection service.
     *
     * @param componentName The component name of the service with which to bind.
     * @param connectionServiceRepository Connection service repository.
     * @param phoneAccountRegistrar Phone account registrar
     * @param context The context.
     * @param userHandle The {@link UserHandle} to use when binding.
     */
    ConnectionServiceWrapper(
            ComponentName componentName,
            ConnectionServiceRepository connectionServiceRepository,
            PhoneAccountRegistrar phoneAccountRegistrar,
            Context context,
            UserHandle userHandle) {
        super(ConnectionService.SERVICE_INTERFACE, componentName, context, userHandle);
        mConnectionServiceRepository = connectionServiceRepository;
        phoneAccountRegistrar.addListener(new PhoneAccountRegistrar.Listener() {
            // TODO -- Upon changes to PhoneAccountRegistrar, need to re-wire connections
            // To do this, we must proxy remote ConnectionService objects
        });
        mPhoneAccountRegistrar = phoneAccountRegistrar;
    }

    /** See {@link IConnectionService#addConnectionServiceAdapter}. */
    private void addConnectionServiceAdapter(IConnectionServiceAdapter adapter) {
        if (isServiceValid("addConnectionServiceAdapter")) {
            try {
                logOutgoing("addConnectionServiceAdapter %s", adapter);
                mServiceInterface.addConnectionServiceAdapter(adapter);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Creates a new connection for a new outgoing call or to attach to an existing incoming call.
     */
    void createConnection(final Call call, final CreateConnectionResponse response) {
        //logOutgoing("createConnection(%s) via %s.", call, getComponentName());
        BindCallback callback = new BindCallback() {
            @Override
            public void onSuccess() {
                String callId = mCallIdMapper.getCallId(call);
                mPendingResponses.put(callId, response);

                GatewayInfo gatewayInfo = call.getGatewayInfo();
                Bundle extras = call.getExtras();
                if (gatewayInfo != null && gatewayInfo.getGatewayProviderPackageName() != null &&
                        gatewayInfo.getOriginalAddress() != null) {
                    extras = (Bundle) extras.clone();
                    extras.putString(
                            TelecomManager.GATEWAY_PROVIDER_PACKAGE,
                            gatewayInfo.getGatewayProviderPackageName());
                    extras.putParcelable(
                            TelecomManager.GATEWAY_ORIGINAL_ADDRESS,
                            gatewayInfo.getOriginalAddress());
                }

                try {
                    /// M: For VoLTE @{
                    boolean isConferenceDial = (call != null && call.isConferenceDial());
                    if (isConferenceDial) {
                        Log.d(this, "createConference indeed! %s.", isConferenceDial);
                        mServiceInterface.createConference(
                                call.getConnectionManagerPhoneAccount(),
                                callId,
                                new ConnectionRequest(
                                        call.getTargetPhoneAccount(),
                                        call.getHandle(),
                                        extras,
                                        call.getVideoState()),
                                call.getConferenceDialNumbers(),
                                call.isIncoming());
                    } else {
                        mServiceInterface.createConnection(
                                call.getConnectionManagerPhoneAccount(),
                                callId,
                                new ConnectionRequest(
                                        call.getTargetPhoneAccount(),
                                        call.getHandle(),
                                        extras,
                                        call.getVideoState()),
                                call.isIncoming(),
                                call.isUnknown());
                    }
                    /// @}
                } catch (RemoteException e) {
                    Log.e(this, e, "Failure to createConnection -- %s", getComponentName());
                    mPendingResponses.remove(callId).handleCreateConnectionFailure(
                            new DisconnectCause(DisconnectCause.ERROR, e.toString()));
                }
            }

            @Override
            public void onFailure() {
                Log.e(this, new Exception(), "Failure to call %s", getComponentName());
                response.handleCreateConnectionFailure(new DisconnectCause(DisconnectCause.ERROR));
            }
        };

        mBinder.bind(callback);
    }

    /** @see ConnectionService#abort(String) */
    void abort(Call call) {
        // Clear out any pending outgoing call data
        final String callId = mCallIdMapper.getCallId(call);

        // If still bound, tell the connection service to abort.
        if (callId != null && isServiceValid("abort")) {
            try {
                logOutgoing("abort %s", callId);
                mServiceInterface.abort(callId);
            } catch (RemoteException e) {
            }
        }

        removeCall(call, new DisconnectCause(DisconnectCause.LOCAL));
    }

    /** @see ConnectionService#hold(String) */
    void hold(Call call) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("hold")) {
            try {
                logOutgoing("hold %s", callId);
                mServiceInterface.hold(callId);
            } catch (RemoteException e) {
            }
        }
    }

    /** @see ConnectionService#unhold(String) */
    void unhold(Call call) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("unhold")) {
            try {
                logOutgoing("unhold %s", callId);
                mServiceInterface.unhold(callId);
            } catch (RemoteException e) {
            }
        }
    }

    /** @see ConnectionService#onAudioStateChanged(String,AudioState) */
    void onAudioStateChanged(Call activeCall, AudioState audioState) {
        final String callId = mCallIdMapper.getCallId(activeCall);
        if (callId != null && isServiceValid("onAudioStateChanged")) {
            try {
                logOutgoing("onAudioStateChanged %s %s", callId, audioState);
                mServiceInterface.onAudioStateChanged(callId, audioState);
            } catch (RemoteException e) {
            }
        }
    }

    /** @see ConnectionService#disconnect(String) */
    void disconnect(Call call) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("disconnect")) {
            try {
                logOutgoing("disconnect %s", callId);
                mServiceInterface.disconnect(callId);
            } catch (RemoteException e) {
            }
        }
    }

    /** @see ConnectionService#answer(String,int) */
    void answer(Call call, int videoState) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("answer")) {
            try {
                logOutgoing("answer %s %d", callId, videoState);
                if (videoState == VideoProfile.VideoState.AUDIO_ONLY) {
                    mServiceInterface.answer(callId);
                } else {
                    mServiceInterface.answerVideo(callId, videoState);
                }
            } catch (RemoteException e) {
            }
        }
    }

    /** @see ConnectionService#reject(String) */
    void reject(Call call) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("reject")) {
            try {
                logOutgoing("reject %s", callId);
                mServiceInterface.reject(callId);
            } catch (RemoteException e) {
            }
        }
    }

    /** @see ConnectionService#playDtmfTone(String,char) */
    void playDtmfTone(Call call, char digit) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("playDtmfTone")) {
            try {
                logOutgoing("playDtmfTone %s %c", callId, digit);
                mServiceInterface.playDtmfTone(callId, digit);
            } catch (RemoteException e) {
            }
        }
    }

    /** @see ConnectionService#stopDtmfTone(String) */
    void stopDtmfTone(Call call) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("stopDtmfTone")) {
            try {
                logOutgoing("stopDtmfTone %s",callId);
                mServiceInterface.stopDtmfTone(callId);
            } catch (RemoteException e) {
            }
        }
    }

    void addCall(Call call) {
        if (mCallIdMapper.getCallId(call) == null) {
            mCallIdMapper.addCall(call);
        }
    }

    /**
     * Associates newCall with this connection service by replacing callToReplace.
     */
    void replaceCall(Call newCall, Call callToReplace) {
        Preconditions.checkState(callToReplace.getConnectionService() == this);
        mCallIdMapper.replaceCall(newCall, callToReplace);
    }

    void removeCall(Call call) {
        removeCall(call, new DisconnectCause(DisconnectCause.ERROR));
    }

    void removeCall(String callId, DisconnectCause disconnectCause) {
        CreateConnectionResponse response = mPendingResponses.remove(callId);
        if (response != null) {
            response.handleCreateConnectionFailure(disconnectCause);
        }

        mCallIdMapper.removeCall(callId);
    }

    void removeCall(Call call, DisconnectCause disconnectCause) {
        CreateConnectionResponse response = mPendingResponses.remove(mCallIdMapper.getCallId(call));
        if (response != null) {
            response.handleCreateConnectionFailure(disconnectCause);
        }

        mCallIdMapper.removeCall(call);
    }

    void onPostDialContinue(Call call, boolean proceed) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("onPostDialContinue")) {
            try {
                logOutgoing("onPostDialContinue %s %b", callId, proceed);
                mServiceInterface.onPostDialContinue(callId, proceed);
            } catch (RemoteException ignored) {
            }
        }
    }

    void conference(final Call call, Call otherCall) {
        final String callId = mCallIdMapper.getCallId(call);
        final String otherCallId = mCallIdMapper.getCallId(otherCall);
        if (callId != null && otherCallId != null && isServiceValid("conference")) {
            try {
                logOutgoing("conference %s %s", callId, otherCallId);
                mServiceInterface.conference(callId, otherCallId);
            } catch (RemoteException ignored) {
            }
        }
    }

    void splitFromConference(Call call) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("splitFromConference")) {
            try {
                logOutgoing("splitFromConference %s", callId);
                mServiceInterface.splitFromConference(callId);
            } catch (RemoteException ignored) {
            }
        }
    }

    void mergeConference(Call call) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("mergeConference")) {
            try {
                logOutgoing("mergeConference %s", callId);
                mServiceInterface.mergeConference(callId);
            } catch (RemoteException ignored) {
            }
        }
    }

    void swapConference(Call call) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("swapConference")) {
            try {
                logOutgoing("swapConference %s", callId);
                mServiceInterface.swapConference(callId);
            } catch (RemoteException ignored) {
            }
        }
    }

    /* M: CC part start */
    void swapWithBackgroundCall(Call call) {
        if (isServiceValid("swapWithBackgroundCall")) {
            try {
                logOutgoing("swapWithBackgroundCall %s", mCallIdMapper.getCallId(call));
                mServiceInterface.swapWithBackgroundCall(mCallIdMapper.getCallId(call));
            } catch (RemoteException ignored) {
            }
        }
    }

    /** @see ConnectionService#reject(String, int) */
    void reject(Call call, int cause) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("reject")) {
            try {
                logOutgoing("reject %s withCause %d", callId, cause);
                mServiceInterface.rejectWithCause(callId, cause);
            } catch (RemoteException ignored) {
            }
        }
    }

    void hangupAll(Call call) {
        if (isServiceValid("hangupAll")) {
            try {
                logOutgoing("hangupAll %s", mCallIdMapper.getCallId(call));
                mServiceInterface.hangupAll(mCallIdMapper.getCallId(call));
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * M: For DSDA.
     * Puts the call on hold and tell connection service the pending call action, answer?
     * outgoing? or unhold.
     */
    /** @see ConnectionService#hold(String, String) */
    void hold(Call call, String pendingCallAction) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("hold")) {
            try {
                logOutgoing("hold %s, pending call action: %s", callId, pendingCallAction);
                mServiceInterface.holdWithPendingCallAction(callId, pendingCallAction);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * M: For DSDA.
     * Disconnect the call and tell connection service the pending call action, answer?
     */
    /** @see ConnectionService#disconnect(String, String) */
    void disconnect(Call call, String pendingCallAction) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("disconnect")) {
            try {
                logOutgoing("disconnect %s, pending call action: %s", callId, pendingCallAction);
                mServiceInterface.disconnectWithPendingCallAction(callId, pendingCallAction);
            } catch (RemoteException e) {
            }
        }
    }

    /// M: For volte @{
    void inviteConferenceParticipants(Call conferenceCall, List<String> numbers) {
        final String conferenceCallId = mCallIdMapper.getCallId(conferenceCall);
        if (conferenceCallId != null && isServiceValid("inviteConferenceParticipants")) {
            try {
                logOutgoing("inviteConferenceParticipants %s", conferenceCallId);
                mServiceInterface.inviteConferenceParticipants(conferenceCallId, numbers);
            } catch (RemoteException ignored) {
            }
        }
    }
    /// @}

    /** {@inheritDoc} */
    @Override
    protected void setServiceInterface(IBinder binder) {
        if (binder == null) {
            // We have lost our service connection. Notify the world that this service is done.
            // We must notify the adapter before CallsManager. The adapter will force any pending
            // outgoing calls to try the next service. This needs to happen before CallsManager
            // tries to clean up any calls still associated with this service.
            handleConnectionServiceDeath();
            CallsManager.getInstance().handleConnectionServiceDeath(this);
            mServiceInterface = null;
        } else {
            mServiceInterface = IConnectionService.Stub.asInterface(binder);
            addConnectionServiceAdapter(mAdapter);
        }
        /// M: for volte test @{
        VolteTestTrigger.getInstance().setServiceInterface(binder, this);
        /// @}
    }

    private void handleCreateConnectionComplete(
            String callId,
            ConnectionRequest request,
            ParcelableConnection connection) {
        // TODO: Note we are not using parameter "request", which is a side effect of our tacit
        // assumption that we have at most one outgoing connection attempt per ConnectionService.
        // This may not continue to be the case.
        if (connection.getState() == Connection.STATE_DISCONNECTED) {
            // A connection that begins in the DISCONNECTED state is an indication of
            // failure to connect; we handle all failures uniformly
            removeCall(callId, connection.getDisconnectCause());
        } else {
            // Successful connection
            if (mPendingResponses.containsKey(callId)) {
                mPendingResponses.remove(callId)
                        .handleCreateConnectionSuccess(mCallIdMapper, connection);
            }
        }
    }

    /// M: For VoLTE conference @{
    private void handleCreateConferenceComplete(
            String conferenceId,
            ConnectionRequest request,
            ParcelableConference conference) {
        // TODO: Note we are not using parameter "request", which is a side effect of our tacit
        // assumption that we have at most one outgoing connection attempt per ConnectionService.
        // This may not continue to be the case.
        if (conference.getState() == Connection.STATE_DISCONNECTED) {
            // A connection that begins in the DISCONNECTED state is an indication of
            // failure to connect; we handle all failures uniformly
            removeCall(conferenceId, conference.getDisconnectCause());
        } else {
            // Successful connection
            if (mPendingResponses.containsKey(conferenceId)) {
                mPendingResponses.remove(conferenceId)
                        .handleCreateConferenceSuccess(mCallIdMapper, conference);
            }
        }
    }
    /// @}

    /**
     * Called when the associated connection service dies.
     */
    private void handleConnectionServiceDeath() {
        if (!mPendingResponses.isEmpty()) {
            CreateConnectionResponse[] responses = mPendingResponses.values().toArray(
                    new CreateConnectionResponse[mPendingResponses.values().size()]);
            mPendingResponses.clear();
            for (int i = 0; i < responses.length; i++) {
                responses[i].handleCreateConnectionFailure(
                        new DisconnectCause(DisconnectCause.ERROR));
            }
        }
        mCallIdMapper.clear();
    }

    private void logIncoming(String msg, Object... params) {
        //Log.d(this, "ConnectionService -> Telecom: " + msg, params);
    }

    private void logOutgoing(String msg, Object... params) {
        //Log.d(this, "Telecom -> ConnectionService: " + msg, params);
    }

    private void queryRemoteConnectionServices(final RemoteServiceCallback callback) {
        // Only give remote connection services to this connection service if it is listed as
        // the connection manager.
        PhoneAccountHandle simCallManager = mPhoneAccountRegistrar.getSimCallManager();
        Log.d(this, "queryRemoteConnectionServices finds simCallManager = %s", simCallManager);
        if (simCallManager == null ||
                !simCallManager.getComponentName().equals(getComponentName())) {
            noRemoteServices(callback);
            return;
        }

        // Make a list of ConnectionServices that are listed as being associated with SIM accounts
        final Set<ConnectionServiceWrapper> simServices = Collections.newSetFromMap(
                new ConcurrentHashMap<ConnectionServiceWrapper, Boolean>(8, 0.9f, 1));
        for (PhoneAccountHandle handle : mPhoneAccountRegistrar.getCallCapablePhoneAccounts()) {
            PhoneAccount account = mPhoneAccountRegistrar.getPhoneAccount(handle);
            if ((account.getCapabilities() & PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) != 0) {
                ConnectionServiceWrapper service =
                        mConnectionServiceRepository.getService(handle.getComponentName(),
                                handle.getUserHandle());
                if (service != null) {
                    simServices.add(service);
                }
            }
        }

        final List<ComponentName> simServiceComponentNames = new ArrayList<>();
        final List<IBinder> simServiceBinders = new ArrayList<>();

        Log.v(this, "queryRemoteConnectionServices, simServices = %s", simServices);

        for (ConnectionServiceWrapper simService : simServices) {
            if (simService == this) {
                // Only happens in the unlikely case that a SIM service is also a SIM call manager
                continue;
            }

            final ConnectionServiceWrapper currentSimService = simService;

            currentSimService.mBinder.bind(new BindCallback() {
                @Override
                public void onSuccess() {
                    Log.d(this, "Adding simService %s", currentSimService.getComponentName());
                    simServiceComponentNames.add(currentSimService.getComponentName());
                    simServiceBinders.add(currentSimService.mServiceInterface.asBinder());
                    maybeComplete();
                }

                @Override
                public void onFailure() {
                    Log.d(this, "Failed simService %s", currentSimService.getComponentName());
                    // We know maybeComplete() will always be a no-op from now on, so go ahead and
                    // signal failure of the entire request
                    noRemoteServices(callback);
                }

                private void maybeComplete() {
                    if (simServiceComponentNames.size() == simServices.size()) {
                        setRemoteServices(callback, simServiceComponentNames, simServiceBinders);
                    }
                }
            });
        }
    }

    private void setRemoteServices(
            RemoteServiceCallback callback,
            List<ComponentName> componentNames,
            List<IBinder> binders) {
        try {
            callback.onResult(componentNames, binders);
        } catch (RemoteException e) {
            Log.e(this, e, "Contacting ConnectionService %s",
                    ConnectionServiceWrapper.this.getComponentName());
        }
    }

    private void noRemoteServices(RemoteServiceCallback callback) {
        try {
            callback.onResult(Collections.EMPTY_LIST, Collections.EMPTY_LIST);
        } catch (RemoteException e) {
            Log.e(this, e, "Contacting ConnectionService %s", this.getComponentName());
        }
    }

    /// M: for VoLTE test @{
    /**
     * This function used to trigger VoLTE related test code.
     * @param call  which call we will modify
     * @param type  which type we will trigger.
     * @param args  what we want to modify
     */
    public void triggierVolteTest(Call call, int type, Object obj) {
        // get call id.
        String callId = null;
        if (call != null) {
            callId = mCallIdMapper.getCallId(call);
        }
        if (TextUtils.isEmpty(callId) || !(mCallIdMapper.isValidCallId(callId) || mCallIdMapper.isValidConferenceId(callId))) {
            Log.d(this, "trggierVolteTest()...can not find callId, skip this request!");
            return;
        }
        // generate SomeArgs.
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = callId;
        int msg = -1;
        if ((type == VolteTestTrigger.MSG_UPDATE_EXTRAS) && (obj instanceof Bundle)) {
            args.arg2 = obj;
            msg = MSG_UPDATE_EXTRAS;
        } else {
            Log.d(this, "trggierVolteTest()...type and obj are not matched, skip this request!");
            return;
        }
        // trigger it.
        mHandler.obtainMessage(msg, args).sendToTarget();
    }
    /// @}

    void explicitCallTransfer(Call call) {
        final String callId = mCallIdMapper.getCallId(call);
        if (callId != null && isServiceValid("explicitCallTransfer")) {
            try {
                logOutgoing("explicitCallTransfer %s", callId);
                mServiceInterface.explicitCallTransfer(callId);
            } catch (RemoteException ignored) {
            }
        }
    }
}
