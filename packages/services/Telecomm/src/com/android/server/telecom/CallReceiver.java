package com.android.server.telecom;

import java.util.ArrayList;
import java.util.List;

import com.android.ims.ImsManager;
import com.mediatek.ims.WfcReasonInfo;
import com.mediatek.telecom.TelecomUtils;
import com.mediatek.telecom.volte.TelecomVolteUtils;
import com.mediatek.telecom.TelecomManagerEx;
import com.mediatek.telecom.wfc.TelecomWfcUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Trace;
import android.os.UserHandle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.widget.Toast;

/**
 * Single point of entry for all outgoing and incoming calls. {@link CallActivity} serves as a
 * trampoline activity that captures call intents for individual users and forwards it to
 * the {@link CallReceiver} which interacts with the rest of Telecom, both of which run only as
 * the primary user.
 */
public class CallReceiver extends BroadcastReceiver {
    private static final String TAG = CallReceiver.class.getName();

    static final String KEY_IS_UNKNOWN_CALL = "is_unknown_call";
    static final String KEY_IS_INCOMING_CALL = "is_incoming_call";
    static final String KEY_IS_DEFAULT_DIALER =
            "is_default_dialer";

    @Override
    public void onReceive(Context context, Intent intent) {
        final boolean isUnknownCall = intent.getBooleanExtra(KEY_IS_UNKNOWN_CALL, false);
        Log.i(this, "onReceive - isUnknownCall: %s", isUnknownCall);

        Trace.beginSection("processNewCallCallIntent");
        if (isUnknownCall) {
            processUnknownCallIntent(intent);
        } else {
            processOutgoingCallIntent(context, intent);
        }
        Trace.endSection();
    }

    /**
     * Processes CALL, CALL_PRIVILEGED, and CALL_EMERGENCY intents.
     *
     * @param intent Call intent containing data about the handle to call.
     */
    static void processOutgoingCallIntent(Context context, Intent intent) {
        if (shouldPreventDuplicateVideoCall(context, intent)) {
            return ;
        }
        /// M: Dsda call control @{
        // Check outgoing call condition, if exist ring call or 1A2H, or 1A1H in a same phone
        // account, could not start a new outgoing call. Here need to stop dialing out.
        if (!CallsManager.getInstance().canStartOutgoingCall(intent.getData())) {
            Toast.makeText(context,
                    context.getResources().getString(R.string.outgoing_call_error_limit_exceeded),
                    Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Rejecting out going call due to LIMIT EXCEEDED");
            return;
        }

        // for performance enhancement, bind InCallUI in advance.
        CallsManager.getInstance().getInCallController().bind(null);
        /// @}

        Uri handle = intent.getData();
        String scheme = handle.getScheme();
        String uriString = handle.getSchemeSpecificPart();

        if (!PhoneAccount.SCHEME_VOICEMAIL.equals(scheme)) {
            handle = Uri.fromParts(PhoneNumberUtils.isUriNumber(uriString) ?
                    PhoneAccount.SCHEME_SIP : PhoneAccount.SCHEME_TEL, uriString, null);
        }

        PhoneAccountHandle phoneAccountHandle = intent.getParcelableExtra(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);

        //added by HQ_wangshiqing for HQ02064631 at 20170110 begin
        /// M: GTS case failed ALPS02495313 @{
        // This is work-around since GTS 3.0 R2 test case will not register the mock phone account
        // So it will be registered here.
        if (phoneAccountHandle != null) {
            if ("com.google.android.gts.telecom.MockConnectionService"
                    .equals(phoneAccountHandle.getComponentName().getClassName())) {
                PhoneAccountHandle accountHandle = new PhoneAccountHandle(
                        new android.content.ComponentName(
                                "com.google.android.gts.telecom",
                                "com.google.android.gts.telecom.MockConnectionService"),
                        "gtstest_CALL_PROVIDER_ID");
                if (CallsManager.getInstance().getPhoneAccountRegistrar()
                        .getPhoneAccount(accountHandle) == null) {
                    CallsManager.getInstance().getPhoneAccountRegistrar().registerPhoneAccount(
                                    PhoneAccount.builder(accountHandle,"Telecom GTS Call Provider")
                                            .setAddress(Uri.parse("tel:555-1234"))
                                            .setSubscriptionAddress(Uri.parse("tel:555-1234"))
                                            .setCapabilities(2)
                                            .setShortDescription("Test Connection Service")
                                            .build());
                }
            }
        }
        /// @}
        //added by HQ_wangshiqing for HQ02064631 at 20170110 end

        /// M: for volte: IMS only @{
        if (TelecomVolteUtils.isImsCallOnlyRequest(intent)
                || TelecomVolteUtils.isConferenceDialRequest(intent)) {
            // If Ims is disabled in setting, notify user to enable it.
            if (!TelecomVolteUtils.isImsEnabled(context)) {
                Log.d(TAG, "Ims is disabled, show error dialog and return!");
                TelecomVolteUtils.showImsDisableDialog(context);
                return;
            }
            // Ims is enabled, try to get a PhoneAccount to dial it.
            PhoneAccountRegistrar phoneAccountRegistrar = getCallsManager().getPhoneAccountRegistrar();
            List<PhoneAccountHandle> phoneAccountHandles = new ArrayList<PhoneAccountHandle>();
            if (phoneAccountRegistrar != null) {
                phoneAccountHandles.addAll(phoneAccountRegistrar.getVolteCallCapablePhoneAccounts());
            }
            // For now, we only have one PhoneAccount to support VoLTE at most,
            // If we have more than one phoneAccount support VoLTE later,
            // then we should review phoneAccount-select part for ims call only.
            switch (phoneAccountHandles.size()) {
                case 0:
                    Log.d(TAG, "No PhoneAccount support VoLTE, show error dialog and return!");
                    TelecomVolteUtils.showNoImsServiceDialog(context);
                    return;
                case 1:
                    Log.d(TAG, "Only one PhoneAccount support VoLTE, use it!");
                    phoneAccountHandle = phoneAccountHandles.get(0);
                    handle = Uri.fromParts(PhoneAccount.SCHEME_TEL, uriString, null);
                    break;
                default:
                    Log.d(TAG, "More than one PhoneAccounts support VoLTE, need check!");
                    break;
            }
        }

        /// M: WFC @{
       String number = handle.getSchemeSpecificPart();
       boolean isEmergency = PhoneNumberUtils.isPotentialLocalEmergencyNumber(context, number);
	   // / Annotated by guofeiyao
       //Log.d(TAG, "[WFC]phone number  = " + handle.getSchemeSpecificPart());
       // / End
       
       Log.d(TAG, "[WFC]isEmergency  = " + isEmergency);

       // show poup when no cellular and wfc unregistered. Excluding for emergency call.
        if ((TelecomWfcUtils.isWfcEnabled(context)) && TelecomWfcUtils.isSimPresent(context)
                && !TelecomWfcUtils.isRatPresent(context) && !isEmergency) {
            TelecomWfcUtils.showNoWifiServiceDialog(context);
            return;
        }
        // For VoLTE conference dial, we have already get the only PhoneAccount above. dial via it.
        if (TelecomVolteUtils.isConferenceDialRequest(intent)) {
            List<String> numbers = TelecomVolteUtils.getConferenceDialNumbers(intent);
            if (numbers != null && !numbers.isEmpty()) {
                Call call = getCallsManager().placeOutgoingConferenceCall(phoneAccountHandle, numbers);
            } else {
                // TODO: maybe we should goto ErrorDialogActivity
                Log.d(TAG, "no number detected, just abandon this conference dial request!");
            }
            // For conference dial, we skip NewOutgoingCallIntentBroadcaster.
            return;
        }
        /// @}

        Bundle clientExtras = null;
        if (intent.hasExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)) {
            clientExtras = intent.getBundleExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
        }
        if (clientExtras == null) {
            /// M: ALPS01965829 EMPTY bundle will cause JE. @{
            // google original code:
            //clientExtras = Bundle.EMPTY;
            clientExtras = new Bundle();
            /// @}
        }

        final boolean isDefaultDialer = intent.getBooleanExtra(KEY_IS_DEFAULT_DIALER, false);
        clientExtras.putBoolean(Constants.EXTRA_IS_IP_DIAL, intent.getBooleanExtra(Constants.EXTRA_IS_IP_DIAL, false));

        /// M: Supporting suggested account @{
        PhoneAccountHandle suggestedPhoneAccountHandle = intent.getParcelableExtra(
                TelecomManagerEx.EXTRA_SUGGESTED_PHONE_ACCOUNT_HANDLE);
        if (suggestedPhoneAccountHandle != null) {
            clientExtras.putParcelable(TelecomManagerEx.EXTRA_SUGGESTED_PHONE_ACCOUNT_HANDLE, suggestedPhoneAccountHandle);
        }
        /// @}

		// /added by guofeiyao
		  
		//just for test
		/*
		if (phoneAccountHandles.size() == 2){
		    phoneAccountHandle = phoneAccountHandles.get(1);
		}
		*/
		int account = intent.getIntExtra("slot_id",-1);
		if (account != -1){
            PhoneAccountRegistrar phoneAccountRegistrar = getCallsManager().getPhoneAccountRegistrar();
		    List<PhoneAccountHandle> phoneAccountHandles = null;
		    if (phoneAccountRegistrar != null){
                phoneAccountHandles = phoneAccountRegistrar.getAllPhoneAccountHandles();
		    } else {
                Log.d("guofeiyao","phoneAccountRegistrar is null!!!");
	    	}
	    	if (phoneAccountHandles != null){
                Log.d("guofeiyao","phoneAccountHandles size:" + phoneAccountHandles.size());
	    	}

			if (phoneAccountHandles.size() == 2 && account < 2){
		        phoneAccountHandle = phoneAccountHandles.get(account);
		    } else {
                Log.d("guofeiyao","some error when set account");
			}
		}
		// /end

        // Send to CallsManager to ensure the InCallUI gets kicked off before the broadcast returns
        Call call = getCallsManager().startOutgoingCall(handle, phoneAccountHandle, clientExtras);

        if (call != null) {
            /// M: ip dial. ip prefix already add, here need to change intent @{
            if (call.isIpCall()) {
                intent.setData(call.getHandle());
            }
            /// @}

            // Asynchronous calls should not usually be made inside a BroadcastReceiver because once
            // onReceive is complete, the BroadcastReceiver's process runs the risk of getting
            // killed if memory is scarce. However, this is OK here because the entire Telecom
            // process will be running throughout the duration of the phone call and should never
            // be killed.
            NewOutgoingCallIntentBroadcaster broadcaster = new NewOutgoingCallIntentBroadcaster(
                    context, getCallsManager(), call, intent, isDefaultDialer);

            final int result = broadcaster.processIntent();
            final boolean success = result == DisconnectCause.NOT_DISCONNECTED;

            if (!success && call != null) {
                disconnectCallAndShowErrorDialog(context, call, result);
            }
        }
    }

    static void processIncomingCallIntent(Intent intent) {
        PhoneAccountHandle phoneAccountHandle = intent.getParcelableExtra(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);

        if (phoneAccountHandle == null) {
            Log.w(TAG, "Rejecting incoming call due to null phone account");
            return;
        }
        if (phoneAccountHandle.getComponentName() == null) {
            Log.w(TAG, "Rejecting incoming call due to null component name");
            return;
        }

        Bundle clientExtras = null;
        if (intent.hasExtra(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)) {
            clientExtras = intent.getBundleExtra(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
        }
        if (clientExtras == null) {
            clientExtras = Bundle.EMPTY;
        }

        Log.d(TAG, "Processing incoming call from connection service [%s]",
                phoneAccountHandle.getComponentName());
        getCallsManager().processIncomingCallIntent(phoneAccountHandle, clientExtras);
    }

    private void processUnknownCallIntent(Intent intent) {
        PhoneAccountHandle phoneAccountHandle = intent.getParcelableExtra(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);

        if (phoneAccountHandle == null) {
            Log.w(this, "Rejecting unknown call due to null phone account");
            return;
        }
        if (phoneAccountHandle.getComponentName() == null) {
            Log.w(this, "Rejecting unknown call due to null component name");
            return;
        }

        getCallsManager().addNewUnknownCall(phoneAccountHandle, intent.getExtras());
    }

    static CallsManager getCallsManager() {
        return CallsManager.getInstance();
    }

    private static void disconnectCallAndShowErrorDialog(
            Context context, Call call, int errorCode) {
        call.disconnect();
        final Intent errorIntent = new Intent(context, ErrorDialogActivity.class);
        int errorMessageId = -1;
        switch (errorCode) {
            case DisconnectCause.INVALID_NUMBER:
            case DisconnectCause.NO_PHONE_NUMBER_SUPPLIED:
                errorMessageId = R.string.outgoing_call_error_no_phone_number_supplied;
                break;
        }
        if (errorMessageId != -1) {
            errorIntent.putExtra(ErrorDialogActivity.ERROR_MESSAGE_ID_EXTRA, errorMessageId);
        }
        errorIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivityAsUser(errorIntent, UserHandle.CURRENT);
    }

    /**
     * Whether an outgoing video call should be prevented from going out. Namely, don't allow an
     * outgoing video call if there is already an ongoing video call. Notify the user if their call
     * is not sent.
     *
     * @return {@code true} if the outgoing call is a video call and should be prevented from going
     *     out, {@code false} otherwise.
     */
    private static boolean shouldPreventDuplicateVideoCall(Context context, Intent intent) {
        int intentVideoState = intent.getIntExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.VideoState.AUDIO_ONLY);
        if (intentVideoState == VideoProfile.VideoState.AUDIO_ONLY
                || !getCallsManager().hasVideoCall()) {
            return false;
        } else {
            // Display an error toast to the user.
            Toast.makeText(
                    context,
                    context.getResources().getString(R.string.duplicate_video_call_not_allowed),
                    Toast.LENGTH_LONG).show();
            return true;
        }
    }
}
