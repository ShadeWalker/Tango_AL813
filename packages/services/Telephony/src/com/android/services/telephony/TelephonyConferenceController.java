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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.net.Uri;
import android.telecom.Conference;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;

import com.android.internal.telephony.Call;
/// M: for SRVCC. @{
import com.android.internal.telephony.CallStateException;
/// @}
import com.android.internal.telephony.gsm.GsmConnection;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;

/// M: CC029: DSDA conference @{
import com.android.internal.telephony.Phone;
/// @}

/**
 * Maintains a list of all the known TelephonyConnections connections and controls GSM and
 * default IMS conference call behavior. This functionality is characterized by the support of
 * two top-level calls, in contrast to a CDMA conference call which automatically starts a
 * conference when there are two calls.
 */
final class TelephonyConferenceController {
    private static final int TELEPHONY_CONFERENCE_MAX_SIZE = 5;

    private final Connection.Listener mConnectionListener = new Connection.Listener() {
        @Override
        public void onStateChanged(Connection c, int state) {
            recalculate();
        }

        /** ${inheritDoc} */
        @Override
        public void onDisconnected(Connection c, DisconnectCause disconnectCause) {
            recalculate();
        }

        @Override
        public void onDestroyed(Connection connection) {
            remove(connection);
        }
    };

    /** The known connections. */
    private final List<TelephonyConnection> mTelephonyConnections = new ArrayList<>();

    private final TelephonyConnectionService mConnectionService;

    public TelephonyConferenceController(TelephonyConnectionService connectionService) {
        mConnectionService = connectionService;
    }

    /** The TelephonyConference connection object. */
    private TelephonyConference mTelephonyConference;

    /// M: for Ims Conference SRVCC @{
    private TelephonyConference mHandoverTelephonyConference = null;
    /// @}

    void add(TelephonyConnection connection) {
        mTelephonyConnections.add(connection);
        connection.addConnectionListener(mConnectionListener);
        recalculate();
    }

    void remove(Connection connection) {
        connection.removeConnectionListener(mConnectionListener);
        mTelephonyConnections.remove(connection);

        recalculate();
    }

    private void recalculate() {
        /**
               *  mTelephonyConference is the list of all conferenced/nonconferenced connections.
               *  mTelephonyConference is created upon conference is activated.
               *
               *  recalculateConferece() functions as below:
               *  Update ConferencedConnections in mTelephonyConference first, based on call state
               *  of all connections in mTelephonyConference.
               *  For the first "merge", it will create a new mTelephonyConference obj.
               *  If mTelephonyConference already exists,
               *  update latest connections within mTelephonyConference.
               *
               *  recalculateConferenceable() functions as below:
               *  (1) Update ConferenceableConnections for each connection in GsmConnections, based
               *       on its call state.
               *  (2) Update ConferenceableConnections of mTelephonyConference based on conferenced-
               *       Connections i.e. nonConferencedConnections
               *
               *  Since mTelephonyConference needs to be updated before ConferenceableConnections,
               *  re-arrange the sequence of methods. Otherwise the ConferenceableConnetions in
               *  mTelephonyConference will be INCORRECT.
               */
        recalculateConference();
        recalculateConferenceable();
    }

    private boolean isFullConference(Conference conference) {
        return conference.getConnections().size() >= TELEPHONY_CONFERENCE_MAX_SIZE;
    }

    private boolean participatesInFullConference(Connection connection) {
        return connection.getConference() != null &&
                isFullConference(connection.getConference());
    }

    /**
     * Calculates the conference-capable state of all GSM connections in this connection service.
     */
    private void recalculateConferenceable() {
        Log.v(this, "recalculateConferenceable : %d", mTelephonyConnections.size());

        List<Connection> activeConnections = new ArrayList<>(mTelephonyConnections.size());
        List<Connection> backgroundConnections = new ArrayList<>(mTelephonyConnections.size());

        // Loop through and collect all calls which are active or holding
        for (Connection connection : mTelephonyConnections) {
            //Log.d(this, "recalc - %s %s", connection.getState(), connection);

            if (!participatesInFullConference(connection)) {
                switch (connection.getState()) {
                    case Connection.STATE_ACTIVE:
                        activeConnections.add(connection);
                        continue;
                    case Connection.STATE_HOLDING:
                        backgroundConnections.add(connection);
                        continue;
                    default:
                        break;
                }
            }

            connection.setConferenceableConnections(Collections.<Connection>emptyList());
        }

        Log.v(this, "active: %d, holding: %d",
                activeConnections.size(), backgroundConnections.size());

        // Go through all the active connections and set the background connections as
        // conferenceable.
        for (Connection connection : activeConnections) {
            connection.setConferenceableConnections(backgroundConnections);
        }

        // Go through all the background connections and set the active connections as
        // conferenceable.
        for (Connection connection : backgroundConnections) {
            connection.setConferenceableConnections(activeConnections);
        }

        // Set the conference as conferenceable with all the connections
        /// M: CC028: [ALPS01781919] Always update nonConferencedConnections to Telecom @{
        /**
        *  TelephonyConferenceController should always update nonConferencedConnections to Telecom
        *  even if the connections of mTelephonyConference is more than 5.
        *  If connection of mTelephonyConference is more than 5,
        *  it needs to update a empty nonConferencedConnections to Telecom.
        */
        if (mTelephonyConference != null) {
            List<Connection> nonConferencedConnections =
                    new ArrayList<>(mTelephonyConnections.size());
            for (TelephonyConnection c : mTelephonyConnections) {
                if (c.getConference() == null && !isFullConference(mTelephonyConference)) {
                    nonConferencedConnections.add(c);
                }
            }
            Log.v(this, "conference conferenceable: %s", nonConferencedConnections);
            mTelephonyConference.setConferenceableConnections(nonConferencedConnections);
        }
        /// @}

        // TODO: Do not allow conferencing of already conferenced connections.
    }

    private void recalculateConference() {
        Set<Connection> conferencedConnections = new HashSet<>();
        int numGsmConnections = 0;

        for (TelephonyConnection connection : mTelephonyConnections) {
            com.android.internal.telephony.Connection radioConnection =
                connection.getOriginalConnection();

            if (radioConnection != null) {
                Call.State state = radioConnection.getState();
                Call call = radioConnection.getCall();
                if ((state == Call.State.ACTIVE || state == Call.State.HOLDING) &&
                        (call != null && call.isMultiparty())) {

                    numGsmConnections++;
                    conferencedConnections.add(connection);
                }
            }
        }

        Log.d(this, "Recalculate conference calls %s %s.",
                mTelephonyConference, conferencedConnections);

        /// M: for Ims Conference SRVCC. @{
        if (mTelephonyConference != null && mHandoverTelephonyConference != null) {
            Log.e(this, new CallStateException(), "SRVCC: abnormal case!");
        }
        /// @}

        // If this is a GSM conference and the number of connections drops below 2, we will
        // terminate the conference.
        if (numGsmConnections < 2) {
            Log.d(this, "not enough connections to be a conference!");

            // No more connections are conferenced, destroy any existing conference.
            if (mTelephonyConference != null) {
                Log.d(this, "with a conference to destroy!");
                mTelephonyConference.destroy();
                mTelephonyConference = null;
            }
        } else {
            if (mTelephonyConference != null) {
                List<Connection> existingConnections = mTelephonyConference.getConnections();
                // Remove any that no longer exist
                for (Connection connection : existingConnections) {
                    if (connection instanceof TelephonyConnection &&
                            !conferencedConnections.contains(connection)) {
                        mTelephonyConference.removeConnection(connection);
                    }
                }

                // Add any new ones
                for (Connection connection : conferencedConnections) {
                    if (!existingConnections.contains(connection)) {
                        mTelephonyConference.addConnection(connection);
                        /// M: CC029: DSDA conference @{
                        /**
                         * Update call capability after put connection in GsmConference list
                         * to avoid update wrong capability to miss separate icon.
                         */
                        if (connection instanceof TelephonyConnection) {
                            TelephonyConnection tcs = (TelephonyConnection) connection;
                            tcs.fireOnCallState();
                        }
                        /// @}
                    }
                }
            } else {
                /// M: for Ims Conference SRVCC @{
                boolean hasHandoverConference = false;
                if (mHandoverTelephonyConference != null) {
                    Log.d(this, "SRVCC: assign handover conference to telephonyConference");
                    mTelephonyConference = mHandoverTelephonyConference;
                    mHandoverTelephonyConference = null;
                    hasHandoverConference = true;
                } else {
                    /// @}
                    mTelephonyConference = new TelephonyConference(null);
                }

                for (Connection connection : conferencedConnections) {
                    Log.d(this, "Adding a connection to a conference call: %s %s",
                            mTelephonyConference, connection);
                    mTelephonyConference.addConnection(connection);
                    /// M: CC029: DSDA conference @{
                    /**
                     * Update call capability after put connection in GsmConference list
                     * to avoid update wrong capability to miss separate icon.
                     */
                    if (connection instanceof TelephonyConnection) {
                        TelephonyConnection tcs = (TelephonyConnection) connection;
                        tcs.fireOnCallState();
                    }
                    /// @}
                }

                /// M: for Ims Conference SRVCC @{
                if (hasHandoverConference) {
                    // just reset the handover conference here,
                    // since it is configured in telephonyConnectionService.
                    Log.d(this, "SRVCC: skip adding conference to connectionService");
                } else {
                    /// @}
                    mConnectionService.addConference(mTelephonyConference);
                }
            }

            // Set the conference state to the same state as its child connections.
            Connection conferencedConnection = mTelephonyConference.getPrimaryConnection();
            if (conferencedConnection != null) {
                switch (conferencedConnection.getState()) {
                    case Connection.STATE_ACTIVE:
                    /// M: CC027: Proprietary scheme to build Connection Capabilities @{
                    mTelephonyConference.updateConnectionCapabilities();
                    /// @}
                        mTelephonyConference.setActive();
                        break;
                    case Connection.STATE_HOLDING:
                    /// M: CC027: Proprietary scheme to build Connection Capabilities @{
                    mTelephonyConference.updateConnectionCapabilities();
                    /// @}
                        mTelephonyConference.setOnHold();
                        break;
                }
            }
        }
    }

    /// M: CC029: DSDA conference @{
    /** @hide */
    private boolean hasConference() {
        if (mTelephonyConference != null) {
            return true;
        }
        return false;
    }

    /** @hide */
    public Phone getConferencePhone() {
        if (hasConference()) {
            return mTelephonyConference.getConferencePhone();
        }
        return null;
    }

    /** @hide */
    public Phone getConnectionPhone() {
        if (mTelephonyConnections.size() != 0) {
            return mTelephonyConnections.get(0).getPhone();
        }
        return null;
    }

    /** @hide */
    public boolean isConference(Connection cConnection) {
        if (hasConference()) {
            List<Connection> existingConnections = mTelephonyConference.getConnections();
            if (existingConnections.contains(cConnection)) {
                return true;
            }
        }
        return false;
    }

    /** @hide */
    public int getConferenceSize() {
        if (hasConference()) {
            return mTelephonyConference.getConnections().size();
        }
        return 0;
    }
    /// @}


    /// M: For VoLTE conference SRVCC. @{
    /**
     * When VoLTE conference SRVCC, it will be switched to TelephonyConference.
     * Telecomm should be unware of this.
     * @param conference the new replaced TelephonyConference.
     * @hide
     */
    void setHandoveredConference(TelephonyConference conference) {
        Log.d(this, "config the handover conference!");
        mHandoverTelephonyConference = conference;
    }
    /// @}
}
