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

import android.content.ComponentName;
/// M: @{
import android.content.Context;
/// @}
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemProperties;
import android.provider.Settings;
/// M: CC027: Proprietary scheme to build Connection Capabilities @{
import android.telecom.Conference;
/// @}
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
/// M: @{
import android.widget.Toast;
/// @}

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.cdma.CDMAPhone;
/// M: @{
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
import com.android.phone.common.R;
/// @}
import com.android.phone.MMIDialogActivity;

/// M: @{
import com.android.phone.PhoneUtils;
/// @}
import com.android.ims.ImsManager;
import com.mediatek.ims.WfcReasonInfo;
/// M: CC022: Error message due to VoLTE SS checking @{
import com.mediatek.telecom.TelecomManagerEx;
/// @}

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Service for making GSM and CDMA connections.
 */
public class TelephonyConnectionService extends ConnectionService {
    /// M: CC029: DSDA conference @{
    // Create a TelephonyConferenceController array list for multi-Phone
    private final List<TelephonyConferenceController> mTelephonyConferenceControllers =
            new ArrayList<>();
    //private final TelephonyConferenceController mTelephonyConferenceController =
    //         new TelephonyConferenceController(this);
    /// @}
    private final CdmaConferenceController mCdmaConferenceController =
            new CdmaConferenceController(this);
    private final ImsConferenceController mImsConferenceController =
            new ImsConferenceController(this);
    private ComponentName mExpectedComponentName = null;
    private EmergencyCallHelper mEmergencyCallHelper;
    private EmergencyTonePlayer mEmergencyTonePlayer;

    /**
     * A listener to actionable events specific to the TelephonyConnection.
     */
    private final TelephonyConnection.TelephonyConnectionListener mTelephonyConnectionListener =
            new TelephonyConnection.TelephonyConnectionListener() {
        @Override
        public void onOriginalConnectionConfigured(TelephonyConnection c) {
            addConnectionToConferenceController(c);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mExpectedComponentName = new ComponentName(this, this.getClass());
        mEmergencyTonePlayer = new EmergencyTonePlayer(this);
        TelecomAccountRegistry.getInstance(this).setTelephonyConnectionService(this);
        /// M: CC023: Use TelephonyConnectionServiceUtil @{
        TelephonyConnectionServiceUtil.getInstance().setService(this);
        /// @}
    }

    /// M: CC023: Use TelephonyConnectionServiceUtil @{
    @Override
    public void onDestroy() {
        TelephonyConnectionServiceUtil.getInstance().unsetService();
        super.onDestroy();
    }
    /// @}

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            final ConnectionRequest request) {
        //Log.i(this, "onCreateOutgoingConnection, request: " + request);

        Uri handle = request.getAddress();
        if (handle == null) {
            Log.d(this, "onCreateOutgoingConnection, handle is null");
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.NO_PHONE_NUMBER_SUPPLIED,
                            "No phone number supplied"));
        }

        String scheme = handle.getScheme();
        final String number;
        if (PhoneAccount.SCHEME_VOICEMAIL.equals(scheme)) {
            // TODO: We don't check for SecurityException here (requires
            // CALL_PRIVILEGED permission).
            final Phone phone = getPhoneForAccount(request.getAccountHandle(), false);
            if (phone == null) {
                Log.d(this, "onCreateOutgoingConnection, phone is null");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.OUT_OF_SERVICE,
                                "Phone is null"));
            }
            number = phone.getVoiceMailNumber();
            if (TextUtils.isEmpty(number)) {
                Log.d(this, "onCreateOutgoingConnection, no voicemail number set.");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.VOICEMAIL_NUMBER_MISSING,
                                "Voicemail scheme provided but no voicemail number set."));
            }

            // Convert voicemail: to tel:
            handle = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
        } else {
            if (!PhoneAccount.SCHEME_TEL.equals(scheme) && !PhoneAccount.SCHEME_SIP.equals(scheme)) {
                Log.d(this, "onCreateOutgoingConnection, Handle %s is not type tel or sip", scheme);
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.INVALID_NUMBER,
                                "Handle scheme is not type tel or sip"));
            }

            number = handle.getSchemeSpecificPart();
            if (TextUtils.isEmpty(number)) {
                Log.d(this, "onCreateOutgoingConnection, unable to parse number");
                return Connection.createFailedConnection(
                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                android.telephony.DisconnectCause.INVALID_NUMBER,
                                "Unable to parse number"));
            }
        }

        boolean isEmergencyNumber = PhoneNumberUtils.isPotentialEmergencyNumber(number);

        /// M: GSM+CDMA Ecc special handle. @{
        Phone phone = null;
        if (isEmergencyNumber) {
            phone = TelephonyConnectionServiceUtil.getInstance()
                    .selectPhoneBySpecialEccRule(request, number);
        }
        /// @}

        // Get the right phone object from the account data passed in.
        if (phone == null) {
            phone = getPhoneForAccount(request.getAccountHandle(), isEmergencyNumber);
        }

        if (phone == null) {
            Log.d(this, "onCreateOutgoingConnection, phone is null");
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUT_OF_SERVICE, "Phone is null"));
        }

        ///M: add for plug in.@{
        if (TelephonyConnectionServiceUtil.getInstance().
                isDataOnlyMode(phone)) {
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUTGOING_CANCELED, null));
        }
        /// @}

        // Check both voice & data RAT to enable normal CS call,
        // when voice RAT is OOS but Data RAT is present.
        int state = phone.getServiceState().getState();
        if (state == ServiceState.STATE_OUT_OF_SERVICE) {
            state = phone.getServiceState().getDataRegState();
        }
       
        ///M: WFC @{

        boolean isWifiOnly = false;
        boolean isAeroplaneModeOn = false; 

        if (SystemProperties.get("ro.mtk_wfc_support").equals("1")) {
            int wfcStatusCode = ImsManager.getInstance(phone.getContext(), phone.getPhoneId()).getWfcStatusCode();
            if (wfcStatusCode == WfcReasonInfo.CODE_WFC_SUCCESS) {
                state  = ServiceState.STATE_IN_SERVICE;
            }
            if (TelephonyManager.WifiCallingPreferences.WIFI_ONLY == Settings.System.getInt(phone.getContext().getContentResolver(),
                    Settings.System.SELECTED_WFC_PREFERRENCE,
                    TelephonyManager.WifiCallingPreferences.CELLULAR_ONLY)) {
                isWifiOnly = true;
            }
            if (Settings.Global.getInt(phone.getContext().getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) > 0){
                isAeroplaneModeOn = true;
            }
        }
        /// @}  
       
        Log.d(this, " Service state " + state);
        boolean useEmergencyCallHelper = false;

        if (isEmergencyNumber) {
            if ((state == ServiceState.STATE_POWER_OFF) || 
                    (state == ServiceState.STATE_IN_SERVICE && (isWifiOnly || isAeroplaneModeOn ))) {
                useEmergencyCallHelper = true;
                Log.d(this, "useEmergencyCallHelper set true");
            }
        } else {
            /// M: CC022: Error message due to VoLTE SS checking @{
	//	 hq_hxb modified by  for  HQ01308688 begin
		if(SystemProperties.get("ro.config.emergency_call_taiwan").equals("1")){
		String mMccMncOp=PhoneNumberUtils.getOperatorMccmnc();
		String sim1_State = PhoneNumberUtils.getSimStatusForSlot(0);
		String sim2_State = PhoneNumberUtils.getSimStatusForSlot(1);
		String sim1_MccMnc = PhoneNumberUtils.getSimMccMnc(0);
		String sim2_MccMnc = PhoneNumberUtils.getSimMccMnc(1);

		boolean ariplanestatus=PhoneNumberUtils.getStatesAirplaneMode(phone.getContext());
		boolean isChangeStatesEmcyHelp=false;

		Log.d("hxb TCS, sim1_State:",  sim1_State);
		Log.d("hxb TCS, sim2_State:", sim2_State);
		Log.d("hxb TCS, mMccMncOp:", mMccMncOp);
		Log.d("hxb TCS, sim1_MccMnc:", sim1_MccMnc);
		Log.d("hxb TCS, sim2_MccMnc:", sim2_MccMnc);
		if(ariplanestatus)
		Log.d("hxb TCS, ariplanestatus:", "true");
		else
		Log.d("hxb TCS, ariplanestatus:", "false");
		if(ariplanestatus&&((sim1_State.equals("READY")&&sim1_MccMnc.startsWith("466"))
			||(sim2_State.equals("READY")&&sim2_MccMnc.startsWith("466")))
			&&(number!=null&&(!number.equals(""))&&("110".equals(number)||"119".equals(number)))){
			isChangeStatesEmcyHelp=true;
		}
		if(isChangeStatesEmcyHelp)
		Log.d("hxb TCS, isChangeStatesEmcyHelp:","true");
		else
		Log.d("hxb TCS, isChangeStatesEmcyHelp:","false");

		
		if(isChangeStatesEmcyHelp){
                useEmergencyCallHelper = true;	 	
		}else{
		            if (TelephonyConnectionServiceUtil.getInstance().
		                    shouldOpenDataConnection(number, phone)) {
		                Log.d(this, "onCreateOutgoingConnection, shouldOpenDataConnection() check fail");
		                return Connection.createFailedConnection(
		                        DisconnectCauseUtil.toTelecomDisconnectCause(
		                                android.telephony.DisconnectCause.VOLTE_SS_DATA_OFF,
		                                TelecomManagerEx.DISCONNECT_REASON_VOLTE_SS_DATA_OFF));
		            }
		            /// @}

		            /// M: CC021: Error message due to CellConnMgr checking @{
		            if (TelephonyConnectionServiceUtil.getInstance().
		                    cellConnMgrShowAlerting(phone.getSubId())) {
		                Log.d(this, "onCreateOutgoingConnection, cellConnMgrShowAlerting() check fail");
		                return Connection.createFailedConnection(
		                        DisconnectCauseUtil.toTelecomDisconnectCause(
		                                android.telephony.DisconnectCause.OUTGOING_CANCELED_BY_SERVICE,
		                                "cellConnMgrShowAlerting() check fail"));
		            }
		            /// @}

		            switch (state) {
		                case ServiceState.STATE_IN_SERVICE:
		                case ServiceState.STATE_EMERGENCY_ONLY:
		                    break;
		                case ServiceState.STATE_OUT_OF_SERVICE:
		                    return Connection.createFailedConnection(
		                            DisconnectCauseUtil.toTelecomDisconnectCause(
		                                    android.telephony.DisconnectCause.OUT_OF_SERVICE,
		                                    "ServiceState.STATE_OUT_OF_SERVICE"));
		                case ServiceState.STATE_POWER_OFF:
		                    return Connection.createFailedConnection(
		                            DisconnectCauseUtil.toTelecomDisconnectCause(
		                                    android.telephony.DisconnectCause.POWER_OFF,
		                                    "ServiceState.STATE_POWER_OFF"));
		                default:
		                    Log.d(this, "onCreateOutgoingConnection, unknown service state: %d", state);
		                    return Connection.createFailedConnection(
		                            DisconnectCauseUtil.toTelecomDisconnectCause(
		                                    android.telephony.DisconnectCause.OUTGOING_FAILURE,
		                                    "Unknown service state " + state));
		            }

		            /// M: CC027: Proprietary scheme to build Connection Capabilities @{
		            if (!canDial(request.getAccountHandle(), number)) {
		                Log.d(this, "onCreateOutgoingConnection, canDial() check fail");
		                return Connection.createFailedConnection(
		                        DisconnectCauseUtil.toTelecomDisconnectCause(
		                                android.telephony.DisconnectCause.OUTGOING_FAILURE,
		                                "canDial() check fail"));
		            }
		            /// @}
	 		}
		}else{
	            if (TelephonyConnectionServiceUtil.getInstance().
	                    shouldOpenDataConnection(number, phone)) {
	                Log.d(this, "onCreateOutgoingConnection, shouldOpenDataConnection() check fail");
	                return Connection.createFailedConnection(
	                        DisconnectCauseUtil.toTelecomDisconnectCause(
	                                android.telephony.DisconnectCause.VOLTE_SS_DATA_OFF,
	                                TelecomManagerEx.DISCONNECT_REASON_VOLTE_SS_DATA_OFF));
	            }
	            /// @}

	            /// M: CC021: Error message due to CellConnMgr checking @{
	            if (TelephonyConnectionServiceUtil.getInstance().
	                    cellConnMgrShowAlerting(phone.getSubId())) {
	                Log.d(this, "onCreateOutgoingConnection, cellConnMgrShowAlerting() check fail");
	                return Connection.createFailedConnection(
	                        DisconnectCauseUtil.toTelecomDisconnectCause(
	                                android.telephony.DisconnectCause.OUTGOING_CANCELED_BY_SERVICE,
	                                "cellConnMgrShowAlerting() check fail"));
	            }
	            /// @}

	            switch (state) {
	                case ServiceState.STATE_IN_SERVICE:
	                case ServiceState.STATE_EMERGENCY_ONLY:
	                    break;
	                case ServiceState.STATE_OUT_OF_SERVICE:
	                    return Connection.createFailedConnection(
	                            DisconnectCauseUtil.toTelecomDisconnectCause(
	                                    android.telephony.DisconnectCause.OUT_OF_SERVICE,
	                                    "ServiceState.STATE_OUT_OF_SERVICE"));
	                case ServiceState.STATE_POWER_OFF:
	                    return Connection.createFailedConnection(
	                            DisconnectCauseUtil.toTelecomDisconnectCause(
	                                    android.telephony.DisconnectCause.POWER_OFF,
	                                    "ServiceState.STATE_POWER_OFF"));
	                default:
	                    Log.d(this, "onCreateOutgoingConnection, unknown service state: %d", state);
	                    return Connection.createFailedConnection(
	                            DisconnectCauseUtil.toTelecomDisconnectCause(
	                                    android.telephony.DisconnectCause.OUTGOING_FAILURE,
	                                    "Unknown service state " + state));
	            }

	            /// M: CC027: Proprietary scheme to build Connection Capabilities @{
	            if (!canDial(request.getAccountHandle(), number)) {
	                Log.d(this, "onCreateOutgoingConnection, canDial() check fail");
	                return Connection.createFailedConnection(
	                        DisconnectCauseUtil.toTelecomDisconnectCause(
	                                android.telephony.DisconnectCause.OUTGOING_FAILURE,
	                                "canDial() check fail"));
	            }
	            /// @}
		}
        }

        final TelephonyConnection connection =
                createConnectionFor(phone, null, true /* isOutgoing */);
        if (connection == null) {
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.OUTGOING_FAILURE,
                            "Invalid phone type"));
        }

        /// M: CC036: [ALPS01794357] Set PhoneAccountHandle for ECC @{
        if (isEmergencyNumber) {
            final PhoneAccountHandle phoneAccountHandle =
                new PhoneAccountHandle(mExpectedComponentName, String.valueOf(phone.getSubId()));
            connection.setAccountHandle(phoneAccountHandle);
        }
        /// @}

        connection.setAddress(handle, PhoneConstants.PRESENTATION_ALLOWED);
        connection.setInitializing();
        connection.setVideoState(request.getVideoState());

        if (useEmergencyCallHelper) {
            if (mEmergencyCallHelper == null) {
                mEmergencyCallHelper = new EmergencyCallHelper(this);
            }
            final Phone eccPhone = phone;
            mEmergencyCallHelper.startTurnOnRadioSequence(eccPhone,
                    new EmergencyCallHelper.Callback() {
                        @Override
                        public void onComplete(boolean isRadioReady) {
                            if (connection.getState() == Connection.STATE_DISCONNECTED) {
                                // If the connection has already been disconnected, do nothing.
                            } else if (isRadioReady) {
                                connection.setInitialized();
                                placeOutgoingConnection(connection, eccPhone, request);
                            } else {
                                Log.d(this, "onCreateOutgoingConnection, failed to turn on radio");
                                connection.setDisconnected(
                                        DisconnectCauseUtil.toTelecomDisconnectCause(
                                                android.telephony.DisconnectCause.POWER_OFF,
                                                "Failed to turn on radio."));
                                connection.destroy();
                            }
                        }
                    });

        } else {
            placeOutgoingConnection(connection, phone, request);
        }

        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(this, "onCreateIncomingConnection, request: " + request);

        Phone phone = getPhoneForAccount(request.getAccountHandle(), false);
        if (phone == null) {
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.ERROR_UNSPECIFIED));
        }

        Call call = phone.getRingingCall();
        if (!call.getState().isRinging()) {
            Log.i(this, "onCreateIncomingConnection, no ringing call");
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.INCOMING_MISSED,
                            "Found no ringing call"));
        }

        com.android.internal.telephony.Connection originalConnection =
                call.getState() == Call.State.WAITING ?
                    call.getLatestConnection() : call.getEarliestConnection();
        if (isOriginalConnectionKnown(originalConnection)) {
            Log.i(this, "onCreateIncomingConnection, original connection already registered");
            return Connection.createCanceledConnection();
        }

        Connection connection =
                createConnectionFor(phone, originalConnection, false /* isOutgoing */);
        if (connection == null) {
            connection = Connection.createCanceledConnection();
            return Connection.createCanceledConnection();
        } else {
            return connection;
        }
    }

    @Override
    public Connection onCreateUnknownConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(this, "onCreateUnknownConnection, request: " + request);

        Phone phone = getPhoneForAccount(request.getAccountHandle(), false);
        if (phone == null) {
            return Connection.createFailedConnection(
                    DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.ERROR_UNSPECIFIED));
        }

        final List<com.android.internal.telephony.Connection> allConnections = new ArrayList<>();
        final Call ringingCall = phone.getRingingCall();
        if (ringingCall.hasConnections()) {
            allConnections.addAll(ringingCall.getConnections());
        }
        final Call foregroundCall = phone.getForegroundCall();
        if (foregroundCall.hasConnections()) {
            allConnections.addAll(foregroundCall.getConnections());
        }
        final Call backgroundCall = phone.getBackgroundCall();
        if (backgroundCall.hasConnections()) {
            allConnections.addAll(phone.getBackgroundCall().getConnections());
        }

        com.android.internal.telephony.Connection unknownConnection = null;
        for (com.android.internal.telephony.Connection telephonyConnection : allConnections) {
            if (!isOriginalConnectionKnown(telephonyConnection)) {
                unknownConnection = telephonyConnection;
                break;
            }
        }

        if (unknownConnection == null) {
            Log.i(this, "onCreateUnknownConnection, did not find previously unknown connection.");
            return Connection.createCanceledConnection();
        }

        TelephonyConnection connection =
                createConnectionFor(phone, unknownConnection,
                        !unknownConnection.isIncoming() /* isOutgoing */);

        if (connection == null) {
            return Connection.createCanceledConnection();
        } else {
            connection.updateState();
            return connection;
        }
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        if (connection1 instanceof TelephonyConnection &&
                connection2 instanceof TelephonyConnection) {
            ((TelephonyConnection) connection1).performConference(
                (TelephonyConnection) connection2);
        }

    }

    private void placeOutgoingConnection(
            TelephonyConnection connection, Phone phone, ConnectionRequest request) {
        String number = connection.getAddress().getSchemeSpecificPart();

        com.android.internal.telephony.Connection originalConnection;
        try {
            originalConnection = phone.dial(number, request.getVideoState());
        } catch (CallStateException e) {
            Log.e(this, e, "placeOutgoingConnection, phone.dial exception: " + e);
            /// M: Since ussd is through 3G protocol  it will cause ims call is disconnected. @{
            if (ImsPhone.USSD_DURING_IMS_INCALL.equals(e.getMessage())) {
                Context context = phone.getContext();
                Toast.makeText(context,
                    context.getString(R.string.incall_error_call_failed), Toast.LENGTH_SHORT)
                        .show();
            }
            /// @}
            connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                    android.telephony.DisconnectCause.OUTGOING_FAILURE,
                    e.getMessage()));
            return;
        }

        if (originalConnection == null) {
            int telephonyDisconnectCause = android.telephony.DisconnectCause.OUTGOING_FAILURE;
            // On GSM phones, null connection means that we dialed an MMI code
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                Log.d(this, "dialed MMI code");
                telephonyDisconnectCause = android.telephony.DisconnectCause.DIALED_MMI;
                final Intent intent = new Intent(this, MMIDialogActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                /// M: CC037: Pass phoneID via intent to MMIDialog @{
                intent.putExtra("ID", phone.getPhoneId());
                /// @}
                startActivity(intent);
            }
            Log.d(this, "placeOutgoingConnection, phone.dial returned null");
            connection.setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                    telephonyDisconnectCause, "Connection is null"));
        } else {
            connection.setOriginalConnection(originalConnection);
        }
    }

    /// M: CC029: DSDA conference @{
    private boolean telephonyConferenceControllerAdd(Phone phone, GsmConnection connection) {
        /**
         * 1. Add the connection into corresponding TelephonyConferenceControllers
         *    if the GsmPhone is same.
         * 2. Add the connection into the TelephonyConferenceControllers
         *    if mTelephonyConferenceControllers is exist but no Gsmconnection is exist.
         * 3. Create new TelephonyConferenceControllers and put the connection into it
         *    if there is no TelephonyConferenceControllers exist.
         */
        boolean isfound = false;
        for (TelephonyConferenceController conferenceController : mTelephonyConferenceControllers) {
            if (conferenceController.getConnectionPhone() != null &&
                conferenceController.getConnectionPhone().getPhoneId() == phone.getPhoneId()) {
                conferenceController.add(connection);
                Log.d(this, "telephonyConferenceControllerAdd: found match controller");
                isfound = true;
                break;
            }
        }
        if (isfound == false) {
            for (TelephonyConferenceController conferenceController : mTelephonyConferenceControllers) {
                if (conferenceController.getConnectionPhone() == null) {
                    conferenceController.add(connection);
                    Log.d(this, "telephonyConferenceControllerAdd: found empty controller");
                    isfound = true;
                    break;
                }
            }
            if (isfound == false) {
                Log.d(this, "telephonyConferenceControllerAdd: new controller");
                TelephonyConferenceController telephonyConferenceController =
                    new TelephonyConferenceController(this);
                telephonyConferenceController.add(connection);
                mTelephonyConferenceControllers.add(telephonyConferenceController);
            }
        }
        return isfound;
    }

    // DSDA : add conference version
    private boolean telephonyConferenceControllerAddConf(
            Phone phone,
            TelephonyConference conference) {

        for (TelephonyConferenceController conferenceController :
               mTelephonyConferenceControllers) {
            if (conferenceController.getConnectionPhone() != null &&
                    conferenceController.getConnectionPhone().getPhoneId() == phone.getPhoneId()) {
                conferenceController.setHandoveredConference(conference);
                Log.d(this, "telephonyConferenceControllerAddConf: found match controller");
                return true;
            }
        }

        for (TelephonyConferenceController conferenceController :
                mTelephonyConferenceControllers) {
            if (conferenceController.getConnectionPhone() == null) {
                conferenceController.setHandoveredConference(conference);
                Log.d(this, "telephonyConferenceControllerAddConf: found empty controller");
                return true;
            }
        }

        Log.d(this, "telephonyConferenceControllerAddConf: new controller");
        TelephonyConferenceController telephonyConferenceController =
            new TelephonyConferenceController(this);
        telephonyConferenceController.setHandoveredConference(conference);
        mTelephonyConferenceControllers.add(telephonyConferenceController);
        return false;
    }
    /// @}

    private TelephonyConnection createConnectionFor(
            Phone phone,
            com.android.internal.telephony.Connection originalConnection,
            boolean isOutgoing) {
        TelephonyConnection returnConnection = null;
        int phoneType = phone.getPhoneType();
        if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
            returnConnection = new GsmConnection(originalConnection);
        } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
            boolean allowMute = allowMute(phone);
            returnConnection = new CdmaConnection(
                    originalConnection, mEmergencyTonePlayer, allowMute, isOutgoing);
        }
        if (returnConnection != null) {
            // Listen to Telephony specific callbacks from the connection
            returnConnection.addTelephonyConnectionListener(mTelephonyConnectionListener);
        }
        return returnConnection;
    }

    private boolean isOriginalConnectionKnown(
            com.android.internal.telephony.Connection originalConnection) {
        for (Connection connection : getAllConnections()) {
            if (connection instanceof TelephonyConnection) {
                TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
                if (telephonyConnection.getOriginalConnection() == originalConnection) {
                    return true;
                }
            }
        }
        return false;
    }

    private Phone getPhoneForAccount(PhoneAccountHandle accountHandle, boolean isEmergency) {

        if (Objects.equals(mExpectedComponentName, accountHandle.getComponentName())) {
            if (accountHandle.getId() != null) {
                try {
                    int phoneId = SubscriptionController.getInstance().getPhoneId(
                            Integer.parseInt(accountHandle.getId()));
                    return PhoneFactory.getPhone(phoneId);
                } catch (NumberFormatException e) {
                    Log.w(this, "Could not get subId from account: " + accountHandle.getId());
                }
            }
        }

        if (isEmergency) {
            // If this is an emergency number and we've been asked to dial it using a PhoneAccount
            // which does not exist, then default to whatever subscription is available currently.
            return getFirstPhoneForEmergencyCall();
        }

        return null;
    }

    private Phone getFirstPhoneForEmergencyCall() {
        Phone selectPhone = null;
        for (int i = 0; i < TelephonyManager.getDefault().getSimCount(); i++) {
            int[] subIds = SubscriptionController.getInstance().getSubIdUsingSlotId(i);
            if (subIds.length == 0)
                continue;

            int phoneId = SubscriptionController.getInstance().getPhoneId(subIds[0]);
            Phone phone = PhoneFactory.getPhone(phoneId);
            if (phone == null)
                continue;

            if (ServiceState.STATE_IN_SERVICE == phone.getServiceState().getState()) {
                // the slot is radio on & state is in service
                Log.d(this, "pickBestPhoneForEmergencyCall, radio on & in service, slotId:" + i);
                return phone;
            } else if (ServiceState.STATE_POWER_OFF != phone.getServiceState().getState()) {
                // the slot is radio on & with SIM card inserted.
                if (TelephonyManager.getDefault().hasIccCard(i)) {
                    Log.d(this, "pickBestPhoneForEmergencyCall," +
                            "radio on and SIM card inserted, slotId:" + i);
                    selectPhone = phone;
                } else if (selectPhone == null) {
                    Log.d(this, "pickBestPhoneForEmergencyCall, radio on, slotId:" + i);
                    selectPhone = phone;
                }
            }
        }

        if (selectPhone == null) {
            Log.d(this, "pickBestPhoneForEmergencyCall, return default phone");
            selectPhone = PhoneFactory.getDefaultPhone();
        }

        return selectPhone;
    }

    /**
     * Determines if the connection should allow mute.
     *
     * @param phone The current phone.
     * @return {@code True} if the connection should allow mute.
     */
    private boolean allowMute(Phone phone) {
        // For CDMA phones, check if we are in Emergency Callback Mode (ECM).  Mute is disallowed
        // in ECM mode.
        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            PhoneProxy phoneProxy = (PhoneProxy)phone;
            CDMAPhone cdmaPhone = (CDMAPhone)phoneProxy.getActivePhone();
            if (cdmaPhone != null) {
                if (cdmaPhone.isInEcm()) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void removeConnection(Connection connection) {
        super.removeConnection(connection);
        if (connection instanceof TelephonyConnection) {
            TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
            telephonyConnection.removeTelephonyConnectionListener(mTelephonyConnectionListener);
        }
    }

    /**
     * When a {@link TelephonyConnection} has its underlying original connection configured,
     * we need to add it to the correct conference controller.
     *
     * @param connection The connection to be added to the controller
     */
    public void addConnectionToConferenceController(TelephonyConnection connection) {
        // TODO: Do we need to handle the case of the original connection changing
        // and triggering this callback multiple times for the same connection?
        // If that is the case, we might want to remove this connection from all
        // conference controllers first before re-adding it.
        if (connection.isImsConnection()) {
            Log.d(this, "Adding IMS connection to conference controller: " + connection);
            mImsConferenceController.add(connection);
        } else {
            int phoneType = connection.getCall().getPhone().getPhoneType();
            if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                //Log.d(this, "Adding GSM connection to conference controller: " + connection);
                /// M: CC029: DSDA conference @{
                /* Use the phone to decide which conferece controller we need to put GsmConnection
                 * because mOriginalConnection does not exist in GsmConnection if MO call
                 */
                Phone phone = connection.getCall().getPhone();
                telephonyConferenceControllerAdd(phone, (GsmConnection) connection);
                //mTelephonyConferenceController.add(connection);
                /// @}
            } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA &&
                    connection instanceof CdmaConnection) {
                if (Build.TYPE.equals("eng")) {
                    Log.d(this, "Adding CDMA connection to conference controller: " + connection);
                }
                mCdmaConferenceController.add((CdmaConnection) connection);
            }
        }
    }

    /// M: CC027: Proprietary scheme to build Connection Capabilities @{
    protected TelephonyConnection getFgConnection() {

        for (Connection c : getAllConnections()) {

            if (!(c instanceof TelephonyConnection)) {
                // the connection may be ConferenceParticipantConnection.
                continue;
            }

            TelephonyConnection tc = (TelephonyConnection) c;

            if (tc.getCall() == null) {
                continue;
            }

            Call.State s = tc.getCall().getState();

            // it assume that only one Fg call at the same time
            if (s == Call.State.ACTIVE || s == Call.State.DIALING || s == Call.State.ALERTING) {
                return tc;
            }
        }
        return null;
    }

    protected List<TelephonyConnection> getBgConnection() {

        ArrayList<TelephonyConnection> connectionList = new ArrayList<TelephonyConnection>();

        for (Connection c : getAllConnections()) {

            if (!(c instanceof TelephonyConnection)) {
                // the connection may be ConferenceParticipantConnection.
                continue;
            }

            TelephonyConnection tc = (TelephonyConnection) c;

            if (tc.getCall() == null) {
                continue;
            }

            Call.State s = tc.getCall().getState();

            // it assume the ringing call won't have more than one connection
            if (s == Call.State.HOLDING) {
                connectionList.add(tc);
            }
        }
        return connectionList;
    }

    protected List<TelephonyConnection> getRingingConnection() {

        ArrayList<TelephonyConnection> connectionList = new ArrayList<TelephonyConnection>();

        for (Connection c : getAllConnections()) {

            if (!(c instanceof TelephonyConnection)) {
                // the connection may be ConferenceParticipantConnection.
                continue;
            }

            TelephonyConnection tc = (TelephonyConnection) c;

            if (tc.getCall() == null) {
                continue;
            }

            // it assume the ringing call won't have more than one connection
            if (tc.getCall().getState().isRinging()) {
                connectionList.add(tc);
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
        boolean hasActiveCall = (getFgCallCount() > 0);
        boolean bIsInCallMmiCommands = isInCallMmiCommands(dialString);
        Call.State fgCallState = Call.State.IDLE;

        Phone pphone = getPhoneForAccount(accountHandle, false);
        Phone phone = ((PhoneProxy) pphone).getActivePhone();

        /* bIsInCallMmiCommands == true only when dialphone == activephone */
        if (bIsInCallMmiCommands && hasActiveCall) {
            bIsInCallMmiCommands = (phone == getFgConnection().getPhone());
        }

        TelephonyConnection fConnection = getFgConnection();
        if (fConnection != null) {
            Call fCall = fConnection.getCall();
            if (fCall != null) {
                fgCallState = fCall.getState();
            }
        }

        boolean isECCExists = TelephonyConnectionServiceUtil.getInstance().isECCExists();
        boolean result = (!isECCExists
                && !(hasRingingCall && !bIsInCallMmiCommands)
                && ((fgCallState == Call.State.ACTIVE)
                || (fgCallState == Call.State.IDLE)
                || (fgCallState == Call.State.DISCONNECTED)
                || (fgCallState == Call.State.ALERTING)));

        if (result == false) {
            Log.d(this, "canDial"
                    + " hasRingingCall=" + hasRingingCall
                    + " hasActiveCall=" + hasActiveCall
                    + " fgCallState=" + fgCallState
                    + " getFgConnection=" + fConnection
                    + " getRingingConnection=" + getRingingConnection()
                    + " bECCExists=" + isECCExists);
        }
        return result;
    }

    @Override
    public boolean canAnswer(Connection ringingConnection) {

        if (ringingConnection == null) {
            Log.d(this, "canAnswer: connection is null");
            return false;
        }

        if (!(ringingConnection instanceof TelephonyConnection)) {
            // the connection may be ConferenceParticipantConnection.
            Log.d(this, "canAnswer: the connection isn't telephonyConnection");
            return false;
        }

        TelephonyConnection rConnection = (TelephonyConnection) ringingConnection;

        if (rConnection.isValidRingingCall() || getFgCallCount() == 0) {
            if (Build.TYPE.equals("eng")) {
                Log.d(this, "canAnswer ringingConnection=" + ringingConnection);
            }
            return true;
        } else {
            Log.d(this, "canAnswer"
                    + " ringingConnection.isValidRingingCall() =" + rConnection.isValidRingingCall()
                    + " getFgCallCount =" + getFgCallCount());
            return false;
        }
    }

    @Override
    public boolean canHold(Object obj) {

        if (obj == null) {
            Log.d(this, "canHold: connection is null");
            return false;
        }

        if (obj instanceof ImsConference) {
            return TelephonyConnectionServiceUtil.getInstance()
                .canHoldImsConference((ImsConference) obj);
        } else if (obj instanceof Conference) {
            Conference fConference = (Conference) obj;

            /// M: handle Cdma conference call capability.
            if (obj instanceof CdmaConference) {
                CdmaConference ccon = (CdmaConference) obj;
                if (Connection.STATE_ACTIVE == ccon.getState()) {
                    Log.d(this, "canHold: CdmaConference case, return true");
                    return true;
                } else {
                    Log.d(this, "canHold: CdmaConference case, return false");
                    return false;
                }
            }

            if ((fConference.getConnections().size() > 0) &&
                !canHold(fConference.getConnections().get(0))) {
                return false;
            } else {
                return true;
            }
        }

        if (!(obj instanceof TelephonyConnection)) {
            // the connection may be ConferenceParticipantConnection.
            Log.d(this, "canHold: the connection isn't telephonyConnection");
            return false;
        }

        TelephonyConnection fConnection = (TelephonyConnection) obj;
        if (fConnection.getCall() == null) {
            Log.d(this, "canHold: connection.getCall() is null");
            return false;
        }

        Call.State state = fConnection.getCall().getState();

        boolean isEccConnection = false;
        if (fConnection.getCall().getEarliestConnection() != null) {
            String address = fConnection.getCall().getEarliestConnection().getAddress();
            isEccConnection = PhoneNumberUtils.isEmergencyNumber(address);
        }

        if (!isEccConnection
                && (state == Call.State.ACTIVE)
                && (fConnection.getPhone().getBackgroundCall().isIdle())) {
            if (Build.TYPE.equals("eng")) {
                Log.d(this, "canHold fConnection=" + fConnection);
            }
            /** M: Bug Fix for ALPS01938951 @{ */
            if (hasDialingCDMAConn(fConnection)) {
                return false;
            }
            /** @} */
            return true;
        } else {
            Log.d(this, "canHold"
                    + " state=" + state
                    + " BgCall is Idle = " + fConnection.getPhone().getBackgroundCall().isIdle()
                    + " isECC = " + isEccConnection);
            return false;
        }
    }

    @Override
    public boolean canUnHold(Object obj) {

        if (obj == null) {
            Log.d(this, "canUnHold: connection is null");
            return false;
        }

        if (obj instanceof ImsConference) {
            return TelephonyConnectionServiceUtil.getInstance()
                .canUnHoldImsConference((ImsConference) obj);
        } else if (obj instanceof Conference) {
            Conference bConference = (Conference) obj;
            /// M: handle Cdma conference call capability.
            if (obj instanceof CdmaConference) {
                CdmaConference ccon = (CdmaConference) obj;
                if (Connection.STATE_HOLDING == ccon.getState()) {
                    Log.d(this, "canUnHold: CdmaConference case, return true");
                    return true;
                } else {
                    Log.d(this, "canUnHold: CdmaConference case, return false");
                    return false;
                }
            }

            if ((bConference.getConnections().size() > 0) &&
                !canUnHold(bConference.getConnections().get(0))) {
                return false;
            } else {
                return true;
            }
        }

        if (!(obj instanceof TelephonyConnection)) {
            // the connection may be ConferenceParticipantConnection.
            Log.d(this, "canUnHold: the connection isn't telephonyConnection");
            return false;
        }

        TelephonyConnection bConnection = (TelephonyConnection) obj;
        Call.State state = bConnection.getCall().getState();

        ///M: fake cdma call
        if (state == Call.State.ACTIVE
                && bConnection.getState() == Connection.STATE_HOLDING
                && bConnection.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            Log.d(this, "for cdma connection, always think it can be unhold");
            return true;
        }

        if ((state == Call.State.HOLDING)
                && (bConnection.getPhone().getForegroundCall().isIdle())) {
            if (Build.TYPE.equals("eng")) {
                Log.d(this, "canUnHold bConnection=" + bConnection);
            }
            return true;

        } else {

            Log.d(this, "canUnHold"
                    + " state=" + state
                    + " FgCall is Idle = " + bConnection.getPhone().getForegroundCall().isIdle());

            return false;
        }
    }

    @Override
    public boolean canSwap(Connection fgConnection) {

        if (fgConnection == null) {
            Log.d(this, "canSwap: connection is null");
            return false;
        }

        if (!(fgConnection instanceof TelephonyConnection)) {
            // the connection may be ConferenceParticipantConnection.
            Log.d(this, "canSwap: the connection isn't telephonyConnection");
            return false;
        }

        TelephonyConnection fConnection = (TelephonyConnection) fgConnection;
        Call.State state = fConnection.getCall().getState();

        if (((state == Call.State.ACTIVE) && !(fConnection.getPhone().getBackgroundCall().isIdle()))
                || ((state == Call.State.HOLDING) && !(fConnection.getPhone().getForegroundCall().isIdle()))) {
            Log.d(this, "canSwap fgConnection=" + fgConnection);
            return true;
        } else {
            Log.d(this, "canSwap"
                    + " state=" + state
                    + " fgCall is Idle = " + fConnection.getPhone().getForegroundCall().isIdle()
                    + " bgCall is Idle = " + fConnection.getPhone().getBackgroundCall().isIdle());
            return false;
        }
    }

    @Override
    public boolean canConference(Connection cConnection) {

        if (cConnection == null) {
            Log.d(this, "canConference: connection is null");
            return false;
        }

        if (!(cConnection instanceof TelephonyConnection)) {
            // the connection may be ConferenceParticipantConnection.
            Log.d(this, "canConference: the connection isn't telephonyConnection");
            return false;
        }

        /// M: CC029: DSDA conference @{
        /* Find correct TelephonyConferenceControllers for connection depend on the GsmPhone. */
        Phone conferencePhone = null;
        TelephonyConferenceController telephonyConferenceController = null;
        TelephonyConnection tConnection = (TelephonyConnection) cConnection;

        if (mTelephonyConferenceControllers.size() > 0) {
            for (TelephonyConferenceController conferenceController : mTelephonyConferenceControllers) {
                if (conferenceController.getConnectionPhone() != null &&
                    conferenceController.getConnectionPhone().equals(tConnection.getPhone())) {
                    telephonyConferenceController = conferenceController;
                    conferencePhone = conferenceController.getConferencePhone();
                    break;
                }
            }
        }

        Call.State state = tConnection.getCall().getState();

        if (((TelephonyConnection) cConnection).isImsConnection()
                && mImsConferenceController != null) {
            if (TelephonyConnectionServiceUtil.getInstance().isVoLTEConferenceFull(
                    mImsConferenceController)) {
                Log.d(this, "canConference, conference has reach max size");
                return false;
            } else {
                Log.d(this, "canConference tConnection=" + tConnection);
                return true;
            }
        } else if (conferencePhone != null) {
            if (telephonyConferenceController.getConferenceSize() < 5) {
                // already has conference, only can merge whne the same phone
                if (conferencePhone == tConnection.getPhone()) {
                    Log.d(this, "canConference tConnection=" + tConnection);
                    return true;
                } else {
                    Log.d(this, "canConference, phone != conference phone");
                    return false;
                }
            } else {
                Log.d(this, "canConference, conference has reach max size");
                return false;
            }
        } else {
            if (mTelephonyConferenceControllers.size() == 0) {
                Log.d(this, "canConference, ConferenceController does't exist");
                return false;
            }

            // if no conference exist, the logic is similar with canSwap
            if (((state == Call.State.ACTIVE)
                    && !(tConnection.getPhone().getBackgroundCall().isIdle()))
                    || ((state == Call.State.HOLDING)
                    && !(tConnection.getPhone().getForegroundCall().isIdle()))) {
                Log.d(this, "canConference tConnection=" + tConnection);
                return true;
            } else {
                Log.d(this, "canConference"
                        + " state=" + state
                        + " fgCall is Idle = " + tConnection.getPhone().getForegroundCall().isIdle()
                        + " bgCall is Idle = " + tConnection.getPhone().getBackgroundCall().isIdle());
                return false;
            }
        }
        /// @}
    }

    @Override
    public boolean canAdd(Connection cConnection) {

        if (cConnection == null) {
            Log.d(this, "canAdd: connection is null");
            return false;
        }

        if (!(cConnection instanceof TelephonyConnection)) {
            // the connection may be ConferenceParticipantConnection.
            Log.d(this, "canAdd: the connection isn't telephonyConnection");
            return false;
        }

        TelephonyConnection tConnection = (TelephonyConnection) cConnection;
        Call.State state = tConnection.getCall().getState();

        if ((state.isRinging()) || (state.isDialing())) {
            Log.d(this, "canAdd"
                    + " state=" + state);
            return false;

        }

        if ((state == Call.State.ACTIVE) && (!tConnection.getPhone().getBackgroundCall().isIdle())) {
            Log.d(this, "canAdd"
                    + " state=" + state
                    + " BgCall is Idle = " + tConnection.getPhone().getBackgroundCall().isIdle());
            return false;
        }
        if (Build.TYPE.equals("eng")) {
            Log.d(this, "canAdd cConnection=" + cConnection);
        }
        return true;
    }

    @Override
    public boolean canTransfer(Connection bgConnection) {

        if (bgConnection == null) {
            Log.d(this, "canTransfer: connection is null");
            return false;
        }

        if (!(bgConnection instanceof TelephonyConnection)) {
            // the connection may be ConferenceParticipantConnection.
            Log.d(this, "canTransfer: the connection isn't telephonyConnection");
            return false;
        } else if (((TelephonyConnection) bgConnection).isImsConnection()) {
            // We still don't support transfer on VoLTE.
            Log.d(this, "canTransfer: the connection is an IMS connection");
            return false;
        }

        TelephonyConnection bConnection = (TelephonyConnection) bgConnection;

        Phone activePhone = null;
        Phone heldPhone = null;

        TelephonyConnection fConnection = getFgConnection();
        if (fConnection != null) {
            activePhone = fConnection.getPhone();
        }

        if (bgConnection != null) {
            heldPhone = bConnection.getPhone();
        }

        return (heldPhone == activePhone && activePhone.canTransfer());
    }

    @Override
    public boolean canSeparate(Connection cConnection) {

        if (cConnection == null) {
            Log.d(this, "canSeparate: connection is null");
            return false;
        }

        if (!(cConnection instanceof TelephonyConnection)) {
            // the connection may be ConferenceParticipantConnection.
            Log.d(this, "canSeparate: the connection isn't telephonyConnection");
            return false;
        }

        TelephonyConnection tConnection = (TelephonyConnection) cConnection;
        /* If the connection is one of conference, and
         * there is only one foreground or backgrpound call in this phone.
         */
        if (mTelephonyConferenceControllers.size() > 0) {
            for (TelephonyConferenceController conferenceController : mTelephonyConferenceControllers) {
                if (conferenceController.isConference(tConnection) == true) {
                    if (tConnection.getPhone().getForegroundCall().isIdle() ||
                        tConnection.getPhone().getBackgroundCall().isIdle()) {
                        if (!(tConnection.getOriginalConnection() instanceof ImsPhoneConnection)) {
                        return true;
                    } else {
                            Log.d(this, "canSeparate: ImsPhoneConnection doesn't support separate");
                        }
                    } else {
                        Log.d(this, "canSeparate"
                                + " ForegroundCall is idle="
                                + tConnection.getPhone().getForegroundCall().isIdle()
                                + " BackgroundCall is idle="
                                + tConnection.getPhone().getBackgroundCall().isIdle());
                    }
                } else {
                    Log.d(this, "canSeparate"
                        + " is Conference=" + conferenceController.isConference(tConnection));
                }
            }
        } else {
            Log.d(this, "canSeparate: ConferenceController does't exist");
        }
        return false;
    }

    private boolean isInCallMmiCommands(String dialString) {
        boolean result = false;
        char ch = dialString.charAt(0);

        switch (ch) {
            case '0':
            case '3':
            case '4':
            case '5':
                if (dialString.length() == 1) {
                    result = true;
                }
                break;

            case '1':
            case '2':
                if (dialString.length() == 1 || dialString.length() == 2) {
                    result = true;
                }
                break;

            default:
                break;
        }

        return result;
    }
    /// @}


    /// M: CC030: CRSS notification @{
    @Override
    protected void forceSuppMessageUpdate(Connection conn) {
        TelephonyConnectionServiceUtil.getInstance().forceSuppMessageUpdate(
                (TelephonyConnection) conn);
    }
    /// @}

    /// M: For VoLTE enhanced conference call. @{
    /**
     * This can be used by telecom to either create a new outgoing conference call or
     * attach to an existing incoming conference call.
     */
    @Override
    protected Conference onCreateConference(
            final PhoneAccountHandle callManagerAccount,
            final String conferenceCallId,
            final ConnectionRequest request,
            final List<String> numbers,
            boolean isIncoming) {
        if (!isIncoming) {
            // For MO case, we need to do some check.
            if (numbers == null || numbers.size() == 0) {
                Log.d(this, "onCreateConference(), invalid numbers");
                return TelephonyConnectionServiceUtil.getInstance().createFailedConference(
                    android.telephony.DisconnectCause.OUTGOING_FAILURE,
                    "invalid numbers");
            }

            if (!canDial(request.getAccountHandle(), numbers.get(0))) {
                Log.d(this, "onCreateConference(), canDail check failed");
                return TelephonyConnectionServiceUtil.getInstance().createFailedConference(
                    android.telephony.DisconnectCause.OUTGOING_FAILURE,
                    "canDail check failed");
            }
        }

        Phone phone = getPhoneForAccount(request.getAccountHandle(), false);

        return TelephonyConnectionServiceUtil.getInstance().createConference(
            mImsConferenceController,
            phone,
            request,
            numbers,
            isIncoming);
    }
    /// @}

    /// M: For VoLTE conference SRVCC. @{
    /**
     * perform Ims Conference SRVCC.
     * @param imsConf the ims conference.
     * @param radioConnections the new created radioConnection
     * @hide
     */
    void performImsConferenceSRVCC(
            Conference imsConf,
            ArrayList<com.android.internal.telephony.Connection> radioConnections) {
        if (imsConf == null) {
            Log.e(this, new CallStateException(),
                "performImsConferenceSRVCC(): abnormal case, imsConf is null");
        }

        if (radioConnections == null || radioConnections.size() < 2) {
            Log.e(this, new CallStateException(),
                "performImsConferenceSRVCC(): abnormal case, newConnections is null");
        }

        if (radioConnections.get(0) == null || radioConnections.get(0).getCall() == null ||
                radioConnections.get(0).getCall().getPhone() == null) {
            Log.e(this, new CallStateException(),
                "performImsConferenceSRVCC(): abnormal case, can't get phone instance");
        }

        Phone phone = radioConnections.get(0).getCall().getPhone();
        TelephonyConference newConf = new TelephonyConference(null);

        replaceConference(imsConf, (Conference) newConf);
        telephonyConferenceControllerAddConf(phone, newConf);

        // we need to follow the order below:
        // 1. new empty GsmConnection
        // 2. addExistingConnection (and it will be added to TelephonyConferenceController)
        // 3. config originalConnection.
        // Then UI will not flash the participant calls during SRVCC.
        PhoneAccountHandle handle = PhoneUtils.makePstnPhoneAccountHandle(phone);
        ArrayList<GsmConnection> newGsmConnections = new ArrayList<GsmConnection>();
        for (com.android.internal.telephony.Connection radioConn : radioConnections) {
            GsmConnection connection = new GsmConnection(null);
            newGsmConnections.add(connection);

            addExistingConnection(handle, connection);
            connection.addTelephonyConnectionListener(mTelephonyConnectionListener);
        }

        for (int i = 0; i < newGsmConnections.size(); i++) {
            newGsmConnections.get(i).setOriginalConnection(radioConnections.get(i));
        }
    }
    /// @}

    /// M: Add for cdma call handle
    private boolean mIsEnableHPF = false;

    Phone getPhoneByType(int phoneType) {
        Phone[] phones = PhoneFactory.getPhones();
        Phone phone = null;

        for (Phone p : phones) {
            if (p.getPhoneType() == phoneType) {
                phone = p;
                break;
            }
        }
        return phone;
    }

    void handleSwitchHPF() {
        Log.d(this, "enter handleSwitchHPF");
        Phone gsmPhone = getPhoneByType(TelephonyManager.PHONE_TYPE_GSM);
        Phone cdmaPhone = getPhoneByType(TelephonyManager.PHONE_TYPE_CDMA);
        if (gsmPhone == null || cdmaPhone == null) {
            return;
        }

        PhoneConstants.State state = gsmPhone.getState();
        Log.d(this, "enter handleSwitchHPF current state = " + state);

        if (PhoneConstants.State.IDLE == state) {
            if (mIsEnableHPF) {
                Log.d(this, "disable HPF!");
                cdmaPhone.requestSwitchHPF(false, null);
                mIsEnableHPF = false;
            }
        } else {
            if (!mIsEnableHPF) {
                Log.d(this, "enable HPF!");
                cdmaPhone.requestSwitchHPF(true, null);
                mIsEnableHPF = true;
            }
        }
    }

    private boolean hasDialingCDMAConn(TelephonyConnection fc) {
        /** M: Bug Fix for ALPS01938951 @{ */
        // CDMA network is different with other network, The MO will
        // auto set ACTIVE without answer the call.It will fail in CTA 6.2.2.3.5.
        // We need set workaround here set CDMA dialing MO connection can't HOLD.
        // If MO connection can't HOLD and there has another MT call,
        // system will auto disconnect MO connection. So in this function,
        // it will return turn when there has a Dialing CDMA connection, else return false

        ConnectionService cs = fc.getConnectionService();
        Log.i(this, "[hasDialingCDMAConn]cs : " + cs);
        if (null != cs && fc instanceof CdmaConnection) {
            Collection<Connection> cnns = cs.getAllConnections();
            for (Connection one : cnns) {
                if (null != one
                        && one instanceof CdmaConnection) {
                    CdmaConnection cOne = (CdmaConnection) one;
                    boolean isOutgoing = cOne.isOutgoing();
                    if (Build.TYPE.equals("eng")) {
                        Log.i(this, "[hasDialingCDMAConn] isOutgoing : " + isOutgoing
                                + " | cOne : " + cOne);
                    }
                    com.android.internal.telephony.cdma.CdmaConnection cc =
                        (com.android.internal.telephony.cdma.CdmaConnection) cOne
                            .getOriginalConnection();
                    if (!cc.isRealConnected() && isOutgoing) {
                        Log.i(this, "[hasDialingCDMAConn] return is true");
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
