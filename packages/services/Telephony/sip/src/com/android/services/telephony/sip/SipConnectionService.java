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

package com.android.services.telephony.sip;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.DisconnectCause;
import android.util.Log;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.sip.SipPhone;
import com.android.services.telephony.DisconnectCauseUtil;

import java.util.List;
import java.util.Objects;

/// M: CC027: Proprietary scheme to build Connection Capabilities @{
import java.util.ArrayList;
import android.telecom.Conference;
import com.android.internal.telephony.Call;
/// @}

/// M: For 3G VT only @{
import android.telecom.VideoProfile;
/// @}

public final class SipConnectionService extends ConnectionService {
    private interface IProfileFinderCallback {
        void onFound(SipProfile profile);
    }

    private static final String PREFIX = "[SipConnectionService] ";
    private static final boolean VERBOSE = true; /* STOP SHIP if true */

    private SipProfileDb mSipProfileDb;
    private Handler mHandler;

    @Override
    public void onCreate() {
        mSipProfileDb = new SipProfileDb(this);
        mHandler = new Handler();
        super.onCreate();
    }

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest request) {
        if (VERBOSE) log("onCreateOutgoingConnection, request: " + request);

        /// M: For 3G VT only @{
        if (request.getVideoState() != VideoProfile.VideoState.AUDIO_ONLY) {
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    DisconnectCause.OUTGOING_FAILURE, "Video call via SIP unsupported"));
        }
        /// @}

        Bundle extras = request.getExtras();
        if (extras != null &&
                extras.getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE) != null) {
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    DisconnectCause.CALL_BARRED, "Cannot make a SIP call with a gateway number."));
        }

        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName sipComponentName = new ComponentName(this, SipConnectionService.class);
        if (!Objects.equals(accountHandle.getComponentName(), sipComponentName)) {
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    DisconnectCause.OUTGOING_FAILURE, "Did not match service connection"));
        }

        /// M: CC027: Proprietary scheme to build Connection Capabilities @{
        if (!canDial(request.getAccountHandle(), request.getAddress().getSchemeSpecificPart())) {
            log("onCreateOutgoingConnection, canDial() check fail");
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    DisconnectCause.OUTGOING_FAILURE, "Did not match service connection"));
        }
        /// @}

        final SipConnection connection = new SipConnection();
        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setInitializing();
        connection.onAddedToCallService();
        boolean attemptCall = true;

        if (!SipUtil.isVoipSupported(this)) {
            final CharSequence description = getString(R.string.no_voip);
            connection.setDisconnected(new android.telecom.DisconnectCause(
                    android.telecom.DisconnectCause.ERROR, null, description,
                    "VoIP unsupported"));
            attemptCall = false;
        }

        if (attemptCall && !isNetworkConnected()) {
            if (VERBOSE) log("start, network not connected, dropping call");
            final boolean wifiOnly = SipManager.isSipWifiOnly(this);
            final CharSequence description = getString(wifiOnly ? R.string.no_wifi_available
                    : R.string.no_internet_available);
            connection.setDisconnected(new android.telecom.DisconnectCause(
                    android.telecom.DisconnectCause.ERROR, null, description,
                    "Network not connected"));
            attemptCall = false;
        }

        if (attemptCall) {
            // The ID used for SIP-based phone account is the SIP profile Uri. Use it to find
            // the actual profile.
            String profileUri = accountHandle.getId();
            findProfile(profileUri, new IProfileFinderCallback() {
                @Override
                public void onFound(SipProfile profile) {
                    if (profile == null) {
                        connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                                DisconnectCause.OUTGOING_FAILURE, "SIP profile not found."));
                        connection.destroy();
                    } else {
                        com.android.internal.telephony.Connection chosenConnection =
                                createConnectionForProfile(profile, request);
                        if (chosenConnection == null) {
                            connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                                    DisconnectCause.OUTGOING_FAILURE, "Connection failed."));
                            connection.destroy();
                        } else {
                            if (VERBOSE) log("initializing connection");
                            connection.initialize(chosenConnection);
                        }
                    }
                }
            });
        }

        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        if (VERBOSE) log("onCreateIncomingConnection, request: " + request);

        if (request.getExtras() == null) {
            if (VERBOSE) log("onCreateIncomingConnection, no extras");
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    DisconnectCause.ERROR_UNSPECIFIED, "No extras on request."));
        }

        Intent sipIntent = (Intent) request.getExtras().getParcelable(
                SipUtil.EXTRA_INCOMING_CALL_INTENT);
        if (sipIntent == null) {
            if (VERBOSE) log("onCreateIncomingConnection, no SIP intent");
            return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                    DisconnectCause.ERROR_UNSPECIFIED, "No SIP intent."));
        }

        SipAudioCall sipAudioCall;
        try {
            sipAudioCall = SipManager.newInstance(this).takeAudioCall(sipIntent, null);
        } catch (SipException e) {
            log("onCreateIncomingConnection, takeAudioCall exception: " + e);
            return Connection.createCanceledConnection();
        }

        SipPhone phone = findPhoneForProfile(sipAudioCall.getLocalProfile());
        if (phone == null) {
            phone = createPhoneForProfile(sipAudioCall.getLocalProfile());
        }
        if (phone != null) {
            com.android.internal.telephony.Connection originalConnection = phone.takeIncomingCall(
                    sipAudioCall);
            if (VERBOSE) log("onCreateIncomingConnection, new connection: " + originalConnection);
            if (originalConnection != null) {
                SipConnection sipConnection = new SipConnection();
                sipConnection.initialize(originalConnection);
                sipConnection.onAddedToCallService();
                return sipConnection;
            } else {
                if (VERBOSE) log("onCreateIncomingConnection, takingIncomingCall failed");
                return Connection.createCanceledConnection();
            }
        }
        return Connection.createFailedConnection(DisconnectCauseUtil.toTelecomDisconnectCause(
                DisconnectCause.ERROR_UNSPECIFIED));
    }

    private com.android.internal.telephony.Connection createConnectionForProfile(
            SipProfile profile,
            ConnectionRequest request) {
        SipPhone phone = findPhoneForProfile(profile);
        if (phone == null) {
            phone = createPhoneForProfile(profile);
        }
        if (phone != null) {
            return startCallWithPhone(phone, request);
        }
        return null;
    }

    /**
     * Searched for the specified profile in the SIP profile database.  This can take a long time
     * in communicating with the database, so it is done asynchronously with a separate thread and a
     * callback interface.
     */
    private void findProfile(final String profileUri, final IProfileFinderCallback callback) {
        if (VERBOSE) log("findProfile");
        new Thread(new Runnable() {
            @Override
            public void run() {
                SipProfile profileToUse = null;
                List<SipProfile> profileList = mSipProfileDb.retrieveSipProfileList();
                if (profileList != null) {
                    for (SipProfile profile : profileList) {
                        if (Objects.equals(profileUri, profile.getUriString())) {
                            profileToUse = profile;
                            break;
                        }
                    }
                }

                final SipProfile profileFound = profileToUse;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onFound(profileFound);
                    }
                });
            }
        }).start();
    }

    private SipPhone findPhoneForProfile(SipProfile profile) {
        if (VERBOSE) log("findPhoneForProfile, profile: " + profile);
        for (Connection connection : getAllConnections()) {
            if (connection instanceof SipConnection) {
                SipPhone phone = ((SipConnection) connection).getPhone();
                if (phone != null && phone.getSipUri().equals(profile.getUriString())) {
                    if (VERBOSE) log("findPhoneForProfile, found existing phone: " + phone);
                    return phone;
                }
            }
        }
        if (VERBOSE) log("findPhoneForProfile, no phone found");
        return null;
    }

    private SipPhone createPhoneForProfile(SipProfile profile) {
        if (VERBOSE) log("createPhoneForProfile, profile: " + profile);
        return PhoneFactory.makeSipPhone(profile.getUriString());
    }

    private com.android.internal.telephony.Connection startCallWithPhone(
            SipPhone phone, ConnectionRequest request) {
        String number = request.getAddress().getSchemeSpecificPart();
        if (VERBOSE) log("startCallWithPhone, number: " + number);

        try {
            com.android.internal.telephony.Connection originalConnection =
                    phone.dial(number, request.getVideoState());
            return originalConnection;
        } catch (CallStateException e) {
            log("startCallWithPhone, exception: " + e);
            return null;
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                return ni.getType() == ConnectivityManager.TYPE_WIFI ||
                        !SipManager.isSipWifiOnly(this);
            }
        }
        return false;
    }

    /// M: CC027: Proprietary scheme to build Connection Capabilities @{
    protected SipConnection getFgConnection() {

        for (Connection c : getAllConnections()) {

            SipConnection sc = (SipConnection) c;

            if (sc.getCall() == null) {
                continue;
            }

            Call.State s = sc.getCall().getState();

            // it assume that only one Fg call at the same time
            if (s == Call.State.ACTIVE || s == Call.State.DIALING || s == Call.State.ACTIVE) {
                return sc;
            }
        }
        return null;
    }

    protected List<SipConnection> getBgConnection() {

        ArrayList<SipConnection> connectionList = new ArrayList<SipConnection>();

        for (Connection c : getAllConnections()) {

            SipConnection sc = (SipConnection) c;

            if (sc.getCall() == null) {
                continue;
            }

            Call.State s = sc.getCall().getState();

            // it assume the ringing call won't have more than one connection
            if (s == Call.State.HOLDING) {
                connectionList.add(sc);
            }
        }
        return connectionList;
    }

    protected List<SipConnection> getRingingConnection() {

        ArrayList<SipConnection> connectionList = new ArrayList<SipConnection>();

        for (Connection c : getAllConnections()) {

            SipConnection sc = (SipConnection) c;

            if (sc.getCall() == null) {
                continue;
            }

            // it assume the ringing call won't have more than one connection
            if (sc.getCall().getState().isRinging()) {
                connectionList.add(sc);
            }
        }
        return connectionList;
    }

    protected int getFgCallCount() {
        if (getFgConnection() != null) {
            return 1;
        }
        return 0;
    }

    protected int getBgCallCount() {
        return getBgConnection().size();
    }

    protected int getRingingCallCount() {
        return getRingingConnection().size();
    }

    @Override
    public boolean canDial(PhoneAccountHandle accountHandle, String dialString) {

        boolean hasRingingCall = (getRingingCallCount() > 0);
        Call.State fgCallState = Call.State.IDLE;

        SipConnection fConnection = getFgConnection();
        if (fConnection != null) {
            Call fCall = fConnection.getCall();
            if (fCall != null) {
                fgCallState = fCall.getState();
            }
        }

        boolean result = (!(hasRingingCall)
                && !(fgCallState == Call.State.ACTIVE)
                && !(fgCallState == Call.State.DIALING));

        if (result == false) {
            log("canDial"
                    + " hasRingingCall=" + hasRingingCall
                    + " fgCallState=" + fgCallState
                    + " getFgConnection=" + fConnection
                    + " getRingingConnection=" + getRingingConnection());
        }
        return result;
    }

    @Override
    public boolean canAnswer(Connection ringingConnection) {

        if (ringingConnection == null) {
            log("canAnswer: connection is null");
            return false;
        }

        SipConnection rConnection = (SipConnection) ringingConnection;

        if (rConnection.isValidRingingCall() || getFgCallCount() == 0) {
            log("canAnswer ringingConnection=" + ringingConnection);
            return true;
        } else {
            log("canAnswer"
                    + " ringingConnection.isValidRingingCall() =" + rConnection.isValidRingingCall()
                    + " getFgCallCount =" + getFgCallCount());
            return false;
        }
    }

    @Override
    public boolean canHold(Object obj) {

        if (obj == null) {
            log("canHold: connection is null");
            return false;
        }

        if (obj instanceof Conference) {
            Conference fConference = (Conference) obj;
            if ((fConference.getConnections().size() > 0) &&
                !canHold(fConference.getConnections().get(0))) {
                return false;
            } else {
                return true;
            }
        }


        SipConnection fConnection = (SipConnection) obj;
        Call.State state = fConnection.getCall().getState();

        if ((state == Call.State.ACTIVE)
                && (fConnection.getPhone().getBackgroundCall().isIdle())) {
            log("canHold fConnection=" + fConnection);
            return true;
        } else {
            log("canHold"
                    + " state=" + state
                    + " bg Call is Idle =" + fConnection.getPhone().getBackgroundCall().isIdle());
            return false;
        }
    }

    @Override
    public boolean canUnHold(Object obj) {

        if (obj == null) {
            log("canUnHold: connection is null");
            return false;
        }

        if (obj instanceof Conference) {
            Conference bConference = (Conference) obj;
            if ((bConference.getConnections().size() > 0) &&
                !canUnHold(bConference.getConnections().get(0))) {
                return false;
            } else {
                return true;
            }
        }

        SipConnection bConnection = (SipConnection) obj;
        Call.State state = bConnection.getCall().getState();

        if ((state == Call.State.HOLDING)
                && (bConnection.getPhone().getForegroundCall().isIdle())) {
            log("canUnHold bConnection=" + bConnection);
            return true;
        } else {
            log("canUnHold"
                    + " state=" + state
                    + " Fg Call is Idle = " + bConnection.getPhone().getForegroundCall().isIdle());
            return false;
        }
    }

    @Override
    public boolean canSwap(Connection fgConnection) {

        if (fgConnection == null) {
            log("canSwap: connection is null");
            return false;
        }

        SipConnection fConnection = (SipConnection) fgConnection;
        Call.State state = fConnection.getCall().getState();

        if ((state == Call.State.ACTIVE)
                && !(fConnection.getPhone().getBackgroundCall().isIdle())) {
            log("canSwap fgConnection=" + fgConnection);
            return true;
        } else {
            log("canSwap"
                    + " state=" + state
                    + " bgCall is Idle = " + fConnection.getPhone().getForegroundCall().isIdle());
            return false;
        }
    }

    @Override
    public boolean canAdd(Connection cConnection) {

        if (cConnection == null) {
            log("canAdd: connection is null");
            return false;
        }

        SipConnection sConnection = (SipConnection) cConnection;
        Call.State state = sConnection.getCall().getState();

        if ((state.isRinging()) || (state.isDialing())) {
            log("canAdd"
                    + " state=" + state);
            return false;
        }

        if ((state == Call.State.ACTIVE)
                && (!sConnection.getPhone().getBackgroundCall().isIdle())) {
            log("canAdd"
                    + " state=" + state
                    + " BgCall is Idle = " + sConnection.getPhone().getBackgroundCall().isIdle());
            return false;
        }
        log("canAdd cConnection=" + cConnection);
        return true;
    }
    /// @}

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
