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

package com.android.server.telecom;
import android.provider.ChildMode;
import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Trace;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.CallLog.Calls;
import android.telecom.AudioState;
import android.telecom.CallState;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.telecom.ParcelableConference;
import android.telecom.ParcelableConnection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.MultiSimVariants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.util.IndentingPrintWriter;
import android.database.ContentObserver;
import java.util.Collection;
//M: add for OP09 plug-in
import com.mediatek.telecom.ext.ExtensionManager;
import android.widget.Toast;
import com.mediatek.telecom.TelecomOverlay;
import com.mediatek.telecom.TelecomUtils;
import com.mediatek.telecom.recording.PhoneRecorderHandler;
import com.mediatek.telecom.volte.TelecomVolteUtils;
import com.mediatek.telephony.TelephonyManagerEx;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import android.widget.Toast;

import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.PhoneNumberUtils;
import android.content.Intent;
import android.os.SystemProperties;

/**
 * Singleton.
 *
 * NOTE: by design most APIs are package private, use the relevant adapter/s to allow
 * access from other packages specifically refraining from passing the CallsManager instance
 * beyond the com.android.server.telecom package boundary.
 */
public final class CallsManager extends Call.ListenerBase {

    // TODO: Consider renaming this CallsManagerPlugin.
    interface CallsManagerListener {
        void onCallAdded(Call call);
        void onCallRemoved(Call call);
        void onCallStateChanged(Call call, int oldState, int newState);
        void onConnectionServiceChanged(
                Call call,
                ConnectionServiceWrapper oldService,
                ConnectionServiceWrapper newService);
        void onIncomingCallAnswered(Call call);
        void onIncomingCallRejected(Call call, boolean rejectWithMessage, String textMessage);
        void onForegroundCallChanged(Call oldForegroundCall, Call newForegroundCall);
        void onAudioStateChanged(AudioState oldAudioState, AudioState newAudioState);
        void onRingbackRequested(Call call, boolean ringback);
        void onIsConferencedChanged(Call call);
        void onIsVoipAudioModeChanged(Call call);
        void onVideoStateChanged(Call call);
        void onCanAddCallChanged(boolean canAddCall);
        /* M: CC part start */
        void onConnectionLost(Call call);
        void onCdmaCallAccepted(Call call);
        /* M: CC part end */
        /**
         * M: The all background calls will be sorted according to the time
         * the call be held, e.g. the first hold call will be first item in
         * the list.
         */
        void onBackgroundCallListChanged(List<Call> newList);

        /**
         * M: The all incoming calls will be sorted according to user's action,
         * since there are more than 1 incoming call exist user may touch to switch
         * any incoming call to the primary screen, the sequence of the incoming call
         * will be changed.
         */
        void onInComingCallListChanged(List<Call> newList);
    }

    /**
     * Singleton instance of the {@link CallsManager}, initialized from {@link TelecomService}.
     */
    private static CallsManager sInstance = null;

    private static final String TAG = "CallsManager";

    private static final int MAXIMUM_LIVE_CALLS = 1;
    private static final int MAXIMUM_HOLD_CALLS = 2;
    private static final int MAXIMUM_RINGING_CALLS = 2;
    private static final int MAXIMUM_OUTGOING_CALLS = 1;
    private static final int MAXIMUM_TOP_LEVEL_CALLS = 2;
    /// M: for auto answer
    private static final int DELAY_AUTO_ANSWER = 125;

    private static final int[] OUTGOING_CALL_STATES =
            {CallState.CONNECTING, CallState.PRE_DIAL_WAIT, CallState.DIALING};

    private static final int[] LIVE_CALL_STATES =
            {CallState.CONNECTING, CallState.PRE_DIAL_WAIT, CallState.DIALING, CallState.ACTIVE};

    /**
     * The main call repository. Keeps an instance of all live calls. New incoming and outgoing
     * calls are added to the map and removed when the calls move to the disconnected state.
     *
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<Call> mCalls = Collections.newSetFromMap(
            new ConcurrentHashMap<Call, Boolean>(8, 0.9f, 1));

    private final ConnectionServiceRepository mConnectionServiceRepository;
    private final DtmfLocalTonePlayer mDtmfLocalTonePlayer;
    private final InCallController mInCallController;
    private final CallAudioManager mCallAudioManager;
    private final Ringer mRinger;
    private final InCallWakeLockController mInCallWakeLockController;
    // For this set initial table size to 16 because we add 13 listeners in
    // the CallsManager constructor.
    private final Set<CallsManagerListener> mListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<CallsManagerListener, Boolean>(16, 0.9f, 1));
    private final HeadsetMediaButton mHeadsetMediaButton;
    private final WiredHeadsetManager mWiredHeadsetManager;
    private final TtyManager mTtyManager;
    private final ProximitySensorManager mProximitySensorManager;
    private final PhoneStateBroadcaster mPhoneStateBroadcaster;
    private final CallLogManager mCallLogManager;
    private final Context mContext;
    private final PhoneAccountRegistrar mPhoneAccountRegistrar;
    private final MissedCallNotifier mMissedCallNotifier;
    private final Set<Call> mLocallyDisconnectingCalls = new HashSet<>();
    private final Set<Call> mPendingCallsToDisconnect = new HashSet<>();
    /* Handler tied to thread in which CallManager was initialized. */
    private final Handler mHandler = new Handler();

    private boolean mCanAddCall = true;
    /// M: Added for update screen wake state.@{
    private TelecomUtils mTelecomUtils;
    /// @}

    /// M: Dsda call control. @{
    private final Map<Call, PendingCallAction> mPendingCallActions = new HashMap<>();
    private final List<Call> mSortedInComingCallList = new ArrayList<Call>();
    private final List<Call> mSortedHoldCallList = new ArrayList<Call>();
    /// @}

    /**
     * The call the user is currently interacting with. This is the call that should have audio
     * focus and be visible in the in-call UI.
     */
    private Call mForegroundCall;

    private Runnable mStopTone;

    /** Add for ECC */
    private Call mPendingECCCall;
    private boolean mHasPendingECC;

    /* added for CDMA India optr*/
    private static final String ACTION_ESN_MO_CALL = "com.android.server.telecom.ESN_OUTGOING_CALL_PLACED";

    /** Singleton accessor. */
    static CallsManager getInstance() {
        return sInstance;
    }

    /**
     * Sets the static singleton instance.
     *
     * @param instance The instance to set.
     */
    static void initialize(CallsManager instance) {
        sInstance = instance;
    }

    /**
     * Initializes the required Telecom components.
     */
     CallsManager(Context context, MissedCallNotifier missedCallNotifier,
             PhoneAccountRegistrar phoneAccountRegistrar) {
        mContext = context;
        mPhoneAccountRegistrar = phoneAccountRegistrar;
        mMissedCallNotifier = missedCallNotifier;
        StatusBarNotifier statusBarNotifier = new StatusBarNotifier(context, this);
        mWiredHeadsetManager = new WiredHeadsetManager(context);
        mCallAudioManager = new CallAudioManager(context, statusBarNotifier, mWiredHeadsetManager);
        InCallTonePlayer.Factory playerFactory = new InCallTonePlayer.Factory(mCallAudioManager);
        mRinger = new Ringer(mCallAudioManager, this, playerFactory, context);
        mHeadsetMediaButton = new HeadsetMediaButton(context, this);
        mTtyManager = new TtyManager(context, mWiredHeadsetManager);
        mProximitySensorManager = new ProximitySensorManager(context);
        mPhoneStateBroadcaster = new PhoneStateBroadcaster();
        mCallLogManager = new CallLogManager(context);
        mInCallController = new InCallController(context);
        mDtmfLocalTonePlayer = new DtmfLocalTonePlayer(context);
        mConnectionServiceRepository = new ConnectionServiceRepository(mPhoneAccountRegistrar,
                context);

        mInCallWakeLockController = new InCallWakeLockController(context, this);

        /// M: Added for update screen wake state.@{
        mTelecomUtils = new TelecomUtils(context);
        /// @}
        mListeners.add(statusBarNotifier);
        mListeners.add(mCallLogManager);
        mListeners.add(mPhoneStateBroadcaster);
        mListeners.add(mInCallController);
        mListeners.add(mRinger);
        mListeners.add(new RingbackPlayer(this, playerFactory));
        mListeners.add(new InCallToneMonitor(playerFactory, this));
        mListeners.add(mCallAudioManager);
        mListeners.add(missedCallNotifier);
        mListeners.add(mDtmfLocalTonePlayer);
        mListeners.add(mHeadsetMediaButton);
        mListeners.add(RespondViaSmsManager.getInstance());
        mListeners.add(mProximitySensorManager);
    }

    @Override
    public void onSuccessfulOutgoingCall(Call call, int callState) {
        //Log.v(this, "onSuccessfulOutgoingCall, %s", call);

        setCallState(call, callState);
        if (!mCalls.contains(call)) {
            // Call was not added previously in startOutgoingCall due to it being a potential MMI
            // code, so add it now.
            addCall(call);
        }

        // The call's ConnectionService has been updated.
        for (CallsManagerListener listener : mListeners) {
            listener.onConnectionServiceChanged(call, null, call.getConnectionService());
        }

        //ALPS01781841, do not mark Ecc call as dialing state this time point
        //Ecc call is marked as dialing state only when FWK call state event(MSG_SET_DIALING) post to ConnectionServiceWrapper.Adapter
        if (!call.isEmergencyCall()) {
            markCallAsDialing(call);
        }
    }

    @Override
    public void onFailedOutgoingCall(Call call, DisconnectCause disconnectCause) {
        // / Annotated by guofeiyao
        //Log.v(this, "onFailedOutgoingCall, call: %s", call);
        // / End

        markCallAsRemoved(call);
    }

    @Override
    public void onSuccessfulIncomingCall(Call incomingCall) {
        Log.d(this, "onSuccessfulIncomingCall");
        /// M: Dsda call control: default set incoming call have ANSWER capability. @{
        int capabilities = incomingCall.getConnectionCapabilities();
        incomingCall.setConnectionCapabilities(capabilities | Connection.CAPABILITY_ANSWER);
        /// @}
        setCallState(incomingCall, CallState.RINGING);

        /// M: [ALPS01833793]when ECC in progress, should reject incoming call, like sip call.
        if (hasEmergencyCall() || hasMaximumRingingCalls()) {
            incomingCall.reject(false, null);
            // since the call was not added to the list of calls, we have to call the missed
            // call notifier and the call logger manually.
            mMissedCallNotifier.showMissedCallNotification(incomingCall);
            mCallLogManager.logCall(incomingCall, Calls.MISSED_TYPE);
        } else {
            addCall(incomingCall);
            /// M: Dsda call control. @{
            updateRingingCallAnswerCapability();
            /// @}
            /// M: for Auto Answer @{
            mAutoAnswerHandler.sendMessageDelayed(mAutoAnswerHandler.obtainMessage(DELAY_AUTO_ANSWER, incomingCall), 3000);
            /// @}
        }
    }

    @Override
    public void onFailedIncomingCall(Call call) {
        setCallState(call, CallState.DISCONNECTED);
        call.removeListener(this);
    }

    @Override
    public void onSuccessfulUnknownCall(Call call, int callState) {
        setCallState(call, callState);
        Log.i(this, "onSuccessfulUnknownCall for call %s", call);
        addCall(call);
    }

    @Override
    public void onFailedUnknownCall(Call call) {
        Log.i(this, "onFailedUnknownCall for call %s", call);
        setCallState(call, CallState.DISCONNECTED);
        call.removeListener(this);
    }

    @Override
    public void onRingbackRequested(Call call, boolean ringback) {
        for (CallsManagerListener listener : mListeners) {
            listener.onRingbackRequested(call, ringback);
        }
    }

    @Override
    public void onPostDialWait(Call call, String remaining) {
        mInCallController.onPostDialWait(call, remaining);
    }

    @Override
    public void onPostDialChar(final Call call, char nextChar) {
        if (PhoneNumberUtils.is12Key(nextChar)) {
            // Play tone if it is one of the dialpad digits, canceling out the previously queued
            // up stopTone runnable since playing a new tone automatically stops the previous tone.
            if (mStopTone != null) {
                mHandler.removeCallbacks(mStopTone);
            }

            mDtmfLocalTonePlayer.playTone(call, nextChar);

            mStopTone = new Runnable() {
                @Override
                public void run() {
                    // Set a timeout to stop the tone in case there isn't another tone to follow.
                    mDtmfLocalTonePlayer.stopTone(call);
                }
            };
            mHandler.postDelayed(
                    mStopTone,
                    Timeouts.getDelayBetweenDtmfTonesMillis(mContext.getContentResolver()));
        } else if (nextChar == 0 || nextChar == TelecomManager.DTMF_CHARACTER_WAIT ||
                nextChar == TelecomManager.DTMF_CHARACTER_PAUSE) {
            // Stop the tone if a tone is playing, removing any other stopTone callbacks since
            // the previous tone is being stopped anyway.
            if (mStopTone != null) {
                mHandler.removeCallbacks(mStopTone);
            }
            mDtmfLocalTonePlayer.stopTone(call);
        } else {
            Log.w(this, "onPostDialChar: invalid value %d", nextChar);
        }
    }

    @Override
    public void onParentChanged(Call call) {
        // parent-child relationship affects which call should be foreground, so do an update.
        updateCallsManagerState();
        for (CallsManagerListener listener : mListeners) {
            listener.onIsConferencedChanged(call);
        }
    }

    @Override
    public void onChildrenChanged(Call call) {
        // parent-child relationship affects which call should be foreground, so do an update.
        updateCallsManagerState();
        /// M: for ALPS01771880 @{
        // stop record if conf-call members changed
        if(mForegroundCall == call) {
            PhoneRecorderHandler.getInstance().stopRecording();
        }
        ///@}
        for (CallsManagerListener listener : mListeners) {
            listener.onIsConferencedChanged(call);
        }
    }

    @Override
    public void onIsVoipAudioModeChanged(Call call) {
        for (CallsManagerListener listener : mListeners) {
            listener.onIsVoipAudioModeChanged(call);
        }
    }

    @Override
    public void onVideoStateChanged(Call call) {
        for (CallsManagerListener listener : mListeners) {
            listener.onVideoStateChanged(call);
        }
    }

    @Override
    public boolean onCanceledViaNewOutgoingCallBroadcast(final Call call) {
        mPendingCallsToDisconnect.add(call);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mPendingCallsToDisconnect.remove(call)) {
                    Log.i(this, "Delayed disconnection of call: %s", call);
                    call.disconnect();
                }
            }
        }, Timeouts.getNewOutgoingCallCancelMillis(mContext.getContentResolver()));

        return true;
    }

    Collection<Call> getCalls() {
        return Collections.unmodifiableCollection(mCalls);
    }

    Call getForegroundCall() {
        return mForegroundCall;
    }

    Ringer getRinger() {
        return mRinger;
    }

    InCallController getInCallController() {
        return mInCallController;
    }

    boolean hasEmergencyCall() {
        for (Call call : mCalls) {
            if (call.isEmergencyCall()) {
                return true;
            }
        }
        return false;
    }

    boolean hasVideoCall() {
        for (Call call : mCalls) {
            if (call.getVideoState() != VideoProfile.VideoState.AUDIO_ONLY) {
                return true;
            }
        }
        return false;
    }

    AudioState getAudioState() {
        return mCallAudioManager.getAudioState();
    }

    boolean isTtySupported() {
        return mTtyManager.isTtySupported();
    }

    int getCurrentTtyMode() {
        return mTtyManager.getCurrentTtyMode();
    }

    void addListener(CallsManagerListener listener) {
        mListeners.add(listener);
    }

    void removeListener(CallsManagerListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Starts the process to attach the call to a connection service.
     *
     * @param phoneAccountHandle The phone account which contains the component name of the
     *        connection service to use for this call.
     * @param extras The optional extras Bundle passed with the intent used for the incoming call.
     */
    void processIncomingCallIntent(PhoneAccountHandle phoneAccountHandle, Bundle extras) {
        Log.d(this, "processIncomingCallIntent");
        Call call = new Call(
                mContext,
                mConnectionServiceRepository,
                null /* handle */,
                null /* gatewayInfo */,
                null /* connectionManagerPhoneAccount */,
                phoneAccountHandle,
                true /* isIncoming */,
                false /* isConference */);

        /// M: For VoLTE @{
        if (TelecomVolteUtils.isConferenceInvite(extras)) {
            call.setIsConferenceInvite(true);
        }
        /// @}

        call.setExtras(extras);
        // TODO: Move this to be a part of addCall()
        call.addListener(this);
        call.startCreateConnection(mPhoneAccountRegistrar);
    }

    void addNewUnknownCall(PhoneAccountHandle phoneAccountHandle, Bundle extras) {
        Uri handle = extras.getParcelable(TelecomManager.EXTRA_UNKNOWN_CALL_HANDLE);
        Log.i(this, "addNewUnknownCall with handle: %s", Log.pii(handle));
        Call call = new Call(
                mContext,
                mConnectionServiceRepository,
                handle,
                null /* gatewayInfo */,
                null /* connectionManagerPhoneAccount */,
                phoneAccountHandle,
                // Use onCreateIncomingConnection in TelephonyConnectionService, so that we attach
                // to the existing connection instead of trying to create a new one.
                true /* isIncoming */,
                false /* isConference */);
        call.setIsUnknown(true);
        call.setExtras(extras);
        call.addListener(this);
        call.startCreateConnection(mPhoneAccountRegistrar);
    }

    private Call getNewOutgoingCall(Uri handle) {
        // First check to see if we can reuse any of the calls that are waiting to disconnect.
        // See {@link Call#abort} and {@link #onCanceledViaNewOutgoingCall} for more information.
        Call reusedCall = null;
        for (Call pendingCall : mPendingCallsToDisconnect) {
            if (reusedCall == null && Objects.equals(pendingCall.getHandle(), handle)) {
                mPendingCallsToDisconnect.remove(pendingCall);
                Log.i(this, "Reusing disconnected call %s", pendingCall);
                reusedCall = pendingCall;
            } else {
                pendingCall.disconnect();
            }
        }
        if (reusedCall != null) {
            return reusedCall;
        }

        // Create a call with original handle. The handle may be changed when the call is attached
        // to a connection service, but in most cases will remain the same.
        Log.d(this, "start outgoing call ....");
        return new Call(
                mContext,
                mConnectionServiceRepository,
                handle,
                null /* gatewayInfo */,
                null /* connectionManagerPhoneAccount */,
                null /* phoneAccountHandle */,
                false /* isIncoming */,
                false /* isConference */);
    }
//add by lihaizhou for ProhibitCall in ChildMode by begin at 2015-08-10
    private boolean isChildModeOn() {
		        String isOn = ChildMode.getString(mContext.getContentResolver(),
		       		ChildMode.CHILD_MODE_ON);
		        if(isOn != null && "1".equals(isOn)){
		       	 return true;
		        }else {
		       	 return false;
				}
		       	 
		   }
     private boolean isProhibitCall() {
		     String isOn = ChildMode.getString(mContext.getContentResolver(),
		    		ChildMode.FORBID_CALL );
		     if(isOn != null && "1".equals(isOn)&& isChildModeOn()){
		    	 return true;
		     }else {
		    	 return false;
			}	 
		}
  //add by lihaizhou for ProhibitCall in ChildMode by end at 2015-08-10  
    /**
     * Kicks off the first steps to creating an outgoing call so that InCallUI can launch.
     *
     * @param handle Handle to connect the call with.
     * @param phoneAccountHandle The phone account which contains the component name of the
     *        connection service to use for this call.
     * @param extras The optional extras Bundle passed with the intent used for the incoming call.
     */
    Call startOutgoingCall(Uri handle, PhoneAccountHandle phoneAccountHandle, Bundle extras) {
      
        Call call = getNewOutgoingCall(handle);
        boolean isEmergencyCall = TelephonyUtil.shouldProcessAsEmergency(mContext,
                call.getHandle());
         //add by lihaizhou for ProhibitCall in ChildMode by begin at 2015-08-10  
        if(isProhibitCall()&&!isEmergencyCall)
        {
         Toast.makeText(mContext, R.string.wifi_dont_use_wifi_childmode_on,Toast.LENGTH_SHORT).show(); 
         return null;
        }
       //add by lihaizhou for ProhibitCall in ChildMode by end at 2015-08-10  
        List<PhoneAccountHandle> accounts =
                mPhoneAccountRegistrar.getCallCapablePhoneAccounts(handle.getScheme());

        Log.v(this, "startOutgoingCall found accounts = " + accounts);

        /// M: Comment out these code and will redesign this part. @{
        /** Google code:
        if (mForegroundCall != null && mForegroundCall.getTargetPhoneAccount() != null) {
            // If there is an ongoing call, use the same phone account to place this new call.
            phoneAccountHandle = mForegroundCall.getTargetPhoneAccount();
        }
        */
        /// @}

        // Only dial with the requested phoneAccount if it is still valid. Otherwise treat this call
        // as if a phoneAccount was not specified (does the default behavior instead).
        // Note: We will not attempt to dial with a requested phoneAccount if it is disabled.
        if (phoneAccountHandle != null) {
            if (!accounts.contains(phoneAccountHandle)) {
                phoneAccountHandle = null;
            }
        }




        if (phoneAccountHandle == null) {
            // No preset account, check if default exists that supports the URI scheme for the
            // handle.
            PhoneAccountHandle defaultAccountHandle =
                    mPhoneAccountRegistrar.getDefaultOutgoingPhoneAccount(
                            handle.getScheme());
            /// M: CTA CDMA+GSM Ecc requirement @{
            // For ecc ignore the default account, because if both CDMA and
            // GSM are in-service, need to ask user to select.
            // ExtensionManager. add for OP09 plug in
            if (defaultAccountHandle != null && !isEmergencyCall && ExtensionManager.getPhoneAccountExt()
                    .shouldSetDefaultOutgoingAccountAsPhoneAccount(mPhoneAccountRegistrar
                            .getCallCapablePhoneAccounts())) {
            /// @}
                phoneAccountHandle = defaultAccountHandle;
            }
        }

        /// M: Added for suggesting phone account feature. @{
        // When suggested PhoneAccountHandle is not same with defaultAccountHandle, need let user
        // do pick.
        if (!isEmergencyCall && mTelecomUtils.shouldShowAccountSuggestion(
                extras, accounts, phoneAccountHandle)) {
            phoneAccountHandle = null;
        }
        /// @}

        call.setTargetPhoneAccount(phoneAccountHandle);

        boolean isPotentialInCallMMICode = isPotentialInCallMMICode(handle);

        /// M: Added for MMI & IP dial. @{
        // For IP dial.
        if (TelecomUtils.MTK_IP_PREFIX_SUPPORT
                && extras.getBoolean(Constants.EXTRA_IS_IP_DIAL, false)
                && !isEmergencyCall
                && handle != null && !PhoneAccount.SCHEME_SIP.equals(handle.getScheme())
                && !TelecomUtils.isAirPlaneModeOn(mContext)) {
                call.setIsIpCall(true);
           // Log.d(this, "is ip call ...  number = " + call.getHandle());
        }

        // Selecting accounts for MMI & IP Dial.
        //TODO:ALPS02191624 GTS failed, remove the MMI handling, have to consider MMI case later.
        if (call.isIpCall()) {
            accounts = mTelecomUtils.getSubscriptionPhoneAccounts();
            if (!accounts.contains(phoneAccountHandle)) {
                phoneAccountHandle = accounts.size() == 1 ? accounts.get(0) : null;
                call.setTargetPhoneAccount(phoneAccountHandle);
            }
        }
        /// @}

        /// M: ALPS01830649: for Dsda call control. @{
        /// M: Added for setting a ip prefix. @{
        if(TelecomUtils.startIpPrefixSetting(mContext, phoneAccountHandle, call.isIpCall())) {
            return null;
        }
        call.setIpPrefix();
        /// @}

        /// M: ALPS01830649 @{
        // if there is a default account, we have to check if it has the capability to
        // start new call on anther account or not, if cannot, the call will be cancelled.
        if (!canStartOutgoingCallWithPhoneAccount(call)) {
            call.disconnect();
            Toast.makeText(mContext,
                    mContext.getResources().getString(
                            R.string.outgoing_call_error_limit_exceeded),
                            Toast.LENGTH_SHORT).show();
            Log.d(this, "Rejecting outgoing call on another account.");
            return null;
        }
        /// @}

        boolean needsAccountSelection = phoneAccountHandle == null && accounts.size() > 1 &&
                !isEmergencyCall;

        /// M: CTA CDMA+GSM Ecc requirement @{
        // If all accounts in service, pop the account selector.
        if (isEmergencyCall
                && TelecomUtils.needSelectAccountForEcc(mContext)
                && phoneAccountHandle == null) {
            needsAccountSelection = true;
        }
        /// @}

        if (needsAccountSelection) {
            // This is the state where the user is expected to select an account
            call.setState(CallState.PRE_DIAL_WAIT);
            extras.putParcelableList(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS, accounts);
        } else {
            call.setState(CallState.CONNECTING);
        }

        call.setExtras(extras);

        // Do not add the call if it is a potential MMI code.
        if ((isPotentialMMICode(handle) || isPotentialInCallMMICode) && !needsAccountSelection ) {
            Log.d(this, "is potential mmi code ....");
            call.addListener(this);
            /// M: If no account for MMI Code, show a dialog with "No SIM or SIM error" message. @{
            if (phoneAccountHandle == null) {
                Intent intent = new Intent();
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setClass(mContext, ErrorDialogActivity.class);
                intent.putExtra(ErrorDialogActivity.ERROR_MESSAGE_ID_EXTRA,
                        R.string.callFailed_simError);
                disconnectCall(call);
                mContext.startActivity(intent);
                return null;
            }
            /// @}
        } else if (!mCalls.contains(call)) {
            // We check if mCalls already contains the call because we could potentially be reusing
            // a call which was previously added (See {@link #getNewOutgoingCall}).
            addCall(call);
        }

        return call;
    }

    /**
     * Check if ok to dial ECC, the phone state should be idle, no call exsit.
     */
    boolean isOkForECC() {
        if (mCalls.size() == 0) {
            return true;
        }
        return false;
    }

    /**
     * Attempts to issue/connect the specified call.
     *
     * @param handle Handle to connect the call with.
     * @param gatewayInfo Optional gateway information that can be used to route the call to the
     *        actual dialed handle via a gateway provider. May be null.
     * @param speakerphoneOn Whether or not to turn the speakerphone on once the call connects.
     * @param videoState The desired video state for the outgoing call.
     */
    void placeOutgoingCall(Call call, Uri handle, GatewayInfo gatewayInfo, boolean speakerphoneOn,
            int videoState) {
        if (call == null) {
            // don't do anything if the call no longer exists
            Log.i(this, "Canceling unknown call.");
            return;
        }
        /* added for CDMA India optr*/
		broadcastCallPlacedIntent(call);
        /*end for india CDMA optr*/

        final Uri uriHandle = (gatewayInfo == null) ? handle : gatewayInfo.getGatewayAddress();

        /*if (gatewayInfo == null) {
            		Log.i(this, "Creating a new outgoing call with handle: %s", Log.piiHandle(uriHandle));
        		} else {
            		Log.i(this, "Creating a new outgoing call with gateway handle: %s, original handle: %s",
                    	Log.pii(uriHandle), Log.pii(handle));
        	}*/

        call.setHandle(uriHandle);
        call.setGatewayInfo(gatewayInfo);
        if (neededForceSpeakerOn()) {
            call.setStartWithSpeakerphoneOn(true);
        } else {
            call.setStartWithSpeakerphoneOn(speakerphoneOn);
        }
        call.setVideoState(videoState);

        boolean isEmergencyCall = TelephonyUtil.shouldProcessAsEmergency(mContext,
                call.getHandle());
        Log.i(this, "placeOutgoingCall isEmergencyCall = " + isEmergencyCall);

        /// M: CTA CDMA+GSM Ecc requirement. @{
        // For ecc call, check if need to wait for the account select.
        // a) Don't need to select the phone account, for example, there is one sim insert;
        // b) Need to select sim, but user gave an account, for example, CT project
        // has two call button, use can select one by click it.
        if (isEmergencyCall && (!TelecomUtils.needSelectAccountForEcc(mContext)
                || call.getTargetPhoneAccount() != null)) {
        /// @}
            // Emergency -- CreateConnectionProcessor will choose accounts automatically
            /// M: CTA CDMA+GSM Ecc requirement. @{
            // Only set the account to null when needn't select but give an account.
            if (!TelecomUtils.needSelectAccountForEcc(mContext)) {
            /// @}
                call.setTargetPhoneAccount(null);
            }
            mCalls.remove(call);
            if (!isOkForECC()) {
                Log.i(this, "placeOutgoingCall now is not ok for ECC, waiting ......");
                mPendingECCCall = call;
                mHasPendingECC = true;
                disconnectAllCalls();
                mCalls.add(call);
                return;
            }
            mCalls.add(call);
        }

        /// M: CTA CDMA+GSM Ecc requirement. @{
        // If both CDMA and GSM are in service, waiting for user to select
        // the account for ecc.
        if (call.getTargetPhoneAccount() != null || (isEmergencyCall
                && !TelecomUtils.needSelectAccountForEcc(mContext))) {
        /// @}
            // If the account has been set, proceed to place the outgoing call.
            // Otherwise the connection will be initiated when the account is set by the user.
            /// M: Dsda call control. @{
            // If already have an active call, need to hold active call first.
            Call activeCall = getFirstCallWithState(CallState.ACTIVE);
            if (activeCall == null 
                    || (isPotentialMMICode(call.getHandle())
                        && TelecomUtils.isSupportMMICode(mContext, call.getTargetPhoneAccount()))
                    || isPotentialInCallMMICode(call.getHandle())) {
               // Log.i(this, "Active call is null, start outgoing call %s.", call);
                call.startCreateConnection(mPhoneAccountRegistrar);
            } else if (activeCall.can(Connection.CAPABILITY_HOLD)) {
               // Log.i(this, "Holding active call %s before start outgoing call %s.",
               //         activeCall, call);
                mPendingCallActions.put(activeCall, new PendingCallAction(call,
                        PendingCallAction.PENDING_ACTION_OUTGOING,
                        VideoProfile.VideoState.AUDIO_ONLY));
                activeCall.hold(PendingCallAction.PENDING_ACTION_OUTGOING);
            } else if (isEmergencyCall) {
                mPendingCallActions.put(activeCall, new PendingCallAction(call,
                        PendingCallAction.PENDING_ACTION_OUTGOING,
                        VideoProfile.VideoState.AUDIO_ONLY));
                activeCall.disconnect();
            } else {
               // Log.w(this, "Active call not support hold: %s.", activeCall);
                call.disconnect();
            }
            /// @}
        }
    }

    /**
     * Attempts to start a conference call for the specified call.
     *
     * @param call The call to conference.
     * @param otherCall The other call to conference with.
     */
    void conference(Call call, Call otherCall) {
        call.conferenceWith(otherCall);
    }

    /**
     * Instructs Telecom to answer the specified call. Intended to be invoked by the in-call
     * app through {@link InCallAdapter} after Telecom notifies it of an incoming call followed by
     * the user opting to answer said call.
     *
     * @param call The call to answer.
     * @param videoState The video state in which to answer the call.
     */
    void answerCall(Call call, int videoState) {
        if (!mCalls.contains(call)) {
            Log.i(this, "Request to answer a non-existent call %s", call);
        } else {
            // If the foreground call is not the ringing call and it is currently isActive() or
            // STATE_DIALING, put it on hold before answering the call.
            if (mForegroundCall != null && mForegroundCall != call &&
                    (mForegroundCall.isActive() ||
                     mForegroundCall.getState() == CallState.DIALING)) {
                /// M: Dsda call control. @{
                mPendingCallActions.put(mForegroundCall, new PendingCallAction(call,
                        PendingCallAction.PENDING_ACTION_ANSWER, videoState));
                if (!call.can(Connection.CAPABILITY_ANSWER)) {
                    // This call does not support hold.  If it is from a different connection
                    // service, then disconnect it, otherwise allow the connection service to
                    // figure out the right states.
                    mForegroundCall.disconnect(PendingCallAction.PENDING_ACTION_ANSWER);
                } else {
                    /* removed google code, not support 2H.
                    Call heldCall = getHeldCall();
                    if (heldCall != null) {
                        Log.v(this, "Disconnecting held call %s before holding active call.",
                                heldCall);
                        heldCall.disconnect();
                    } */
                    Log.v(this, "Holding active/dialing call %s before answering incoming call %s.",
                            mForegroundCall, call);
                    mForegroundCall.hold(PendingCallAction.PENDING_ACTION_ANSWER);
                }
                // TODO: Wait until we get confirmation of the active call being
                // on-hold before answering the new call.
                // TODO: Import logic from CallManager.acceptCall()
            } else {
                for (CallsManagerListener listener : mListeners) {
                    listener.onIncomingCallAnswered(call);
                }

                // We do not update the UI until we get confirmation of the answer() through
                // {@link #markCallAsActive}.
                if (neededForceSpeakerOn()) {
                    call.setStartWithSpeakerphoneOn(true);
                }
                call.answer(videoState);
            }
            /// @}
        }
    }

    /**
     * Instructs Telecom to reject the specified call. Intended to be invoked by the in-call
     * app through {@link InCallAdapter} after Telecom notifies it of an incoming call followed by
     * the user opting to reject said call.
     */
    void rejectCall(Call call, boolean rejectWithMessage, String textMessage) {
        if (!mCalls.contains(call)) {
            Log.i(this, "Request to reject a non-existent call %s", call);
        } else {
            for (CallsManagerListener listener : mListeners) {
                listener.onIncomingCallRejected(call, rejectWithMessage, textMessage);
            }
            call.reject(rejectWithMessage, textMessage);
        }
    }

    /**
     * Instructs Telecom to play the specified DTMF tone within the specified call.
     *
     * @param digit The DTMF digit to play.
     */
    void playDtmfTone(Call call, char digit) {
        if (!mCalls.contains(call)) {
            Log.i(this, "Request to play DTMF in a non-existent call %s", call);
        } else {
            call.playDtmfTone(digit);
            mDtmfLocalTonePlayer.playTone(call, digit);
        }
    }

    /**
     * Instructs Telecom to stop the currently playing DTMF tone, if any.
     */
    void stopDtmfTone(Call call) {
        if (!mCalls.contains(call)) {
            Log.i(this, "Request to stop DTMF in a non-existent call %s", call);
        } else {
            call.stopDtmfTone();
            mDtmfLocalTonePlayer.stopTone(call);
        }
    }

    /**
     * Instructs Telecom to continue (or not) the current post-dial DTMF string, if any.
     */
    void postDialContinue(Call call, boolean proceed) {
        //ALPS01833456 call maybe null
        if (call != null) {
            if (!mCalls.contains(call)) {
                Log.i(this, "Request to continue post-dial string in a non-existent call %s", call);
            } else {
                call.postDialContinue(proceed);
            }
        }
    }

    /**
     * Instructs Telecom to disconnect the specified call. Intended to be invoked by the
     * in-call app through {@link InCallAdapter} for an ongoing call. This is usually triggered by
     * the user hitting the end-call button.
     */
    void disconnectCall(Call call) {
        //Log.v(this, "disconnectCall %s", call);

        if (!mCalls.contains(call)) {
            Log.w(this, "Unknown call (%s) asked to disconnect", call);
        } else {
            mLocallyDisconnectingCalls.add(call);
            /// M: CC start. @{
            // All childCalls whithin a conference call should not be updated as foreground
            for (Call childCall : call.getChildCalls()) {
                mLocallyDisconnectingCalls.add(childCall);
            }
            /// @}
            call.disconnect();
        }
    }

    /**
     * Instructs Telecom to disconnect all calls.
     */
    void disconnectAllCalls() {
        Log.v(this, "disconnectAllCalls");

        for (Call call : mCalls) {
            /// M: only disconnect top level calls. @{
            if (call.getParentCall() != null) {
                continue;
            }
            /// @}
            disconnectCall(call);
        }
    }

    /**
     * ALPS01765149, Instructs Telecom to disconnect all calls when flight mode turns on
     */
    void disconnectAllCallsWhenFlightModeOn() {
        Log.v(this, "disconnectAllCallsWhenFlightModeOn");
        disconnectAllCalls();
        mCallAudioManager.setNormalMode();
    }

    /**
     * Instructs Telecom to put the specified call on hold. Intended to be invoked by the
     * in-call app through {@link InCallAdapter} for an ongoing call. This is usually triggered by
     * the user hitting the hold button during an active call.
     */
    void holdCall(Call call) {
        if (!mCalls.contains(call)) {
            Log.w(this, "Unknown call (%s) asked to be put on hold", call);
        } else {
            if (Build.TYPE.equals("eng")) {
                Log.d(this, "Putting call on hold: (%s)", call);
            }
            call.hold();
        }
    }

    /**
     * Instructs Telecom to release the specified call from hold. Intended to be invoked by
     * the in-call app through {@link InCallAdapter} for an ongoing call. This is usually triggered
     * by the user hitting the hold button during a held call.
     */
    void unholdCall(Call call) {
        if (!mCalls.contains(call)) {
            Log.w(this, "Unknown call (%s) asked to be removed from hold", call);
        } else {
            if (Build.TYPE.equals("eng")) {
                Log.d(this, "unholding call: (%s)", call);
            }
            /// M: Dsda call control. @{
            /* If exist an active call in the same phone account, swap with background call.
             * or if exist active call in another phone account, hold the active call first.
             */
            Call activeCall = getFirstCallWithState(CallState.ACTIVE);
            if (activeCall != null) {
                if (isInSamePhoneAccount(call, activeCall)) {
                    activeCall.swapWithBackgroundCall();
                } else {
                    mPendingCallActions.put(activeCall, new PendingCallAction(call,
                            PendingCallAction.PENDING_ACTION_UNHOLD,
                            VideoProfile.VideoState.AUDIO_ONLY));
                    if (canSwap(activeCall)) {
                        activeCall.hold(PendingCallAction.PENDING_ACTION_UNHOLD);
                    } else {
                        Log.d(this, "Active call can not hold, disconnect it! %s.", activeCall);
                        activeCall.disconnect();
                    }
                }
            } else {
                if (getDialingCall() == null) {
                    call.unhold();
                }
            }
            /// @}
        }
    }

    /** Called by the in-call UI to change the mute state. */
    void mute(boolean shouldMute) {
        mCallAudioManager.mute(shouldMute);
    }

    /**
      * Called by the in-call UI to change the audio route, for example to change from earpiece to
      * speaker phone.
      */
    void setAudioRoute(int route) {
        mCallAudioManager.setAudioRoute(route);
    }

    /** Called by the in-call UI to turn the proximity sensor on. */
    void turnOnProximitySensor() {
        mProximitySensorManager.turnOn();
    }

    /**
     * Called by the in-call UI to turn the proximity sensor off.
     * @param screenOnImmediately If true, the screen will be turned on immediately. Otherwise,
     *        the screen will be kept off until the proximity sensor goes negative.
     */
    void turnOffProximitySensor(boolean screenOnImmediately) {
        mProximitySensorManager.turnOff(screenOnImmediately);
    }

    void phoneAccountSelected(Call call, PhoneAccountHandle account, boolean setDefault) {
        if (setDefault) {
            mPhoneAccountRegistrar.setUserSelectedOutgoingPhoneAccount(account);
        }
        if (!mCalls.contains(call) && !isPotentialMMICode(call.getHandle())
                && !isPotentialInCallMMICode(call.getHandle())) {
            Log.i(this, "Attempted to add account to unknown call %s", call);
        } else {
            // TODO: There is an odd race condition here. Since NewOutgoingCallIntentBroadcaster and
            // the PRE_DIAL_WAIT sequence run in parallel, if the user selects an account before the
            // NEW_OUTGOING_CALL sequence finishes, we'll start the call immediately without
            // respecting a rewritten number or a canceled number. This is unlikely since
            // NEW_OUTGOING_CALL sequence, in practice, runs a lot faster than the user selecting
            // a phone account from the in-call UI.
            call.setTargetPhoneAccount(account);
            /// M: CTA CDMA+GSM Ecc requirement. @{
            // After user select the account, then check is OK for ecc.
            if (TelephonyUtil.shouldProcessAsEmergency(mContext,
                    call.getHandle())) {
                mCalls.remove(call);
                if (!isOkForECC()) {
                    Log.i(this, "placeOutgoingCall now is not ok for ECC, waiting ......");
                    mPendingECCCall = call;
                    mHasPendingECC = true;
                    disconnectAllCalls();
                    mCalls.add(call);
                    return;
                }
                mCalls.add(call);
            }
            /// @}


            ///M: add ip prefix for ip dial @{
            call.setIpPrefix();
            ///  @}
            /// M: ALPS01769884: try to prevent showing incall screen. @{
            if (!canStartOutgoingCallWithPhoneAccount(call)) {
                call.disconnect();
                Toast.makeText(mContext,
                        mContext.getResources().getString(R.string.outgoing_call_error_limit_exceeded),
                        Toast.LENGTH_SHORT).show();
                Log.d(this, "Rejecting outgoing call.");
                return;
            }
            /// @}
            /// M: for ip dial, if no ip prefix then navigate to setting page @{
            if (TelecomUtils.startIpPrefixSetting(
                    mContext, account, call.isIpCall())) {
                disconnectCall(call);
                return;
            }
            /// @}
            /// M: Dsda call control. @{
            // If it already has an active call, need  to hold active call first.
            Call activeCall = getFirstCallWithState(CallState.ACTIVE);
            if (activeCall == null || isPotentialInCallMMICode(call.getHandle())
                    || (account != null
                        && TelecomUtils.isSupportMMICode(mContext, account)
                        && isPotentialMMICode(call.getHandle()))) {
                call.startCreateConnection(mPhoneAccountRegistrar);
            } else if (activeCall.can(Connection.CAPABILITY_HOLD)) {
                Log.d(this, "Holding active call %s before start outgoing call %s.",
                        activeCall, call);
                mPendingCallActions.put(activeCall, new PendingCallAction(call,
                        PendingCallAction.PENDING_ACTION_OUTGOING,
                        VideoProfile.VideoState.AUDIO_ONLY));

                activeCall.hold(PendingCallAction.PENDING_ACTION_OUTGOING);
            } else {
                Log.w(this, "Active call not support hold: %s.", activeCall);
                call.disconnect();
            }
            /// @}
            broadcastCallPlacedIntent(call);
        }
    }

    private void broadcastCallPlacedIntent(Call call) {
    	if(SystemProperties.get("persist.sys.esn_track_switch").equals("1")) {
        	PhoneAccountHandle phoneAccount = call.getTargetPhoneAccount(); 
        	if(phoneAccount != null) {	 	
            	String subId = phoneAccount.getId();	   
            	if(subId !=null && subId.length()>0) {	   
                	try {
						int subIdInt = Integer.parseInt(subId);	   
                		Log.d(this, "phoneAccountSelected cdma subIdInt= "+subIdInt);
						mContext.sendBroadcast(new Intent(ACTION_ESN_MO_CALL).putExtra(PhoneConstants.SUBSCRIPTION_KEY, subIdInt));	    
					 } catch (NumberFormatException nfe) {
						Log.d(this, "NumberFormatException occured");
						return ;
  					}		
                }    
        	}
    	}
    }

    /** Called when the audio state changes. */
    void onAudioStateChanged(AudioState oldAudioState, AudioState newAudioState) {
        Log.v(this, "onAudioStateChanged, audioState: %s -> %s", oldAudioState, newAudioState);
        for (CallsManagerListener listener : mListeners) {
            listener.onAudioStateChanged(oldAudioState, newAudioState);
        }
    }

    void markCallAsRinging(Call call) {
        setCallState(call, CallState.RINGING);
    }

    void markCallAsDialing(Call call) {
        setCallState(call, CallState.DIALING);
    }

    void markCallAsActive(Call call) {
        setCallState(call, CallState.ACTIVE);

        if (call.getStartWithSpeakerphoneOn()) {
            setAudioRoute(AudioState.ROUTE_SPEAKER);
        }
    }

    void markCallAsOnHold(Call call) {
        setCallState(call, CallState.ON_HOLD);
    }

    /**
     * Marks the specified call as STATE_DISCONNECTED and notifies the in-call app. If this was the
     * last live call, then also disconnect from the in-call controller.
     *
     * @param disconnectCause The disconnect cause, see {@link android.telecomm.DisconnectCause}.
     */
    void markCallAsDisconnected(Call call, DisconnectCause disconnectCause) {
        
        /// M: for volte @{
        // TODO: if disconnectCause is IMS_EMERGENCY_REREG, redial it and do not notify disconnect.
//        placeOutgoingCall(call, handle, gatewayInfo, speakerphoneOn, videoState);
        /// @}

        call.setDisconnectCause(disconnectCause);
        setCallState(call, CallState.DISCONNECTED);
    }

    /**
     * Removes an existing disconnected call, and notifies the in-call app.
     */
    void markCallAsRemoved(Call call) {
    	//Edit for ALPS02324806
        if (Build.TYPE.equals("eng")) {
            Log.i(this, "markCallAsDisconnected mCalls size = " + mCalls.size() + "call is :" + call);
        }
    	//END 
        removeCall(call);
        if (mLocallyDisconnectingCalls.contains(call)) {
            mLocallyDisconnectingCalls.remove(call);
            if (mForegroundCall != null && mForegroundCall.getState() == CallState.ON_HOLD
                    //// M: ALPS01765683 Disconnect a member in conference (in HOLD status),
                    // the conference should still in hold status. @{
                    && !(call.getChildCalls().size() > 0
                    && call.getChildCalls().contains(mForegroundCall))) {
                    //// @}
                mForegroundCall.unhold();
            }
		// Edit for ALPS02324806
        } else if (hasHoldingCall()) {
			Log.i(this,
					"markCallAsDisconnected hasHoldingCall, unhold it, mForegroundCall is:"
							+ mForegroundCall);
			if (mForegroundCall != null
					&& mForegroundCall.getState() == CallState.ON_HOLD
					// // M: ALPS01765683 Disconnect a member in conference (in
					// HOLD status),
					// the conference should still in hold status. @{
					&& !(call.getChildCalls().size() > 0 && call
							.getChildCalls().contains(mForegroundCall))) {
				// // @}

				Log.i(this, "markCallAsDisconnected , mForegroundCall.unhold()");
				mForegroundCall.unhold();
			}
		}
		// END
        
        /// M: after disconnect all calls, if there is a pending ecc, call immediately @{
        if (mHasPendingECC) {
            mCalls.remove(mPendingECCCall);
            Log.i(this, "markCallAsDisconnected mCalls size = " + mCalls.size());
            if (mCalls.size() == 0) {
                Log.i(this, "markCallAsDisconnected re-dial ECC!");
                mPendingECCCall.startCreateConnection(mPhoneAccountRegistrar);
                mHasPendingECC = false;
            }
            mCalls.add(mPendingECCCall);
        }
        /// @}
    }

    /**
     * Cleans up any calls currently associated with the specified connection service when the
     * service binder disconnects unexpectedly.
     *
     * @param service The connection service that disconnected.
     */
    void handleConnectionServiceDeath(ConnectionServiceWrapper service) {
        if (service != null) {
            for (Call call : mCalls) {
                if (call.getConnectionService() == service) {
                    if (call.getState() != CallState.DISCONNECTED) {
                        markCallAsDisconnected(call, new DisconnectCause(DisconnectCause.ERROR));
                    }
                    markCallAsRemoved(call);
                }
            }
        }
    }

    boolean hasAnyCalls() {
        return !mCalls.isEmpty();
    }

    boolean hasActiveOrHoldingCall() {
        return getFirstCallWithState(CallState.ACTIVE, CallState.ON_HOLD) != null;
    }

    boolean hasRingingCall() {
        return getFirstCallWithState(CallState.RINGING) != null;
    }

    boolean onMediaButton(int type) {
        if (hasAnyCalls()) {
            if (HeadsetMediaButton.SHORT_PRESS == type) {
                Call ringingCall = getFirstCallWithState(CallState.RINGING);
			   //modify by zhangjinqiang for short_press mute call--start
                if (ringingCall == null) {
					mCallAudioManager.toggleMute();
				//modify by zhangjinqiang for short_press mute call--end
                    return true;
                } else {
                    //ringingCall.answer(ringingCall.getVideoState());
                    answerCall(ringingCall, ringingCall.getVideoState());
                    return true;
                }
            } else if (HeadsetMediaButton.LONG_PRESS == type) {
                Log.d(this, "handleHeadsetHook: longpress -> hangup");
                Call callToHangup = getFirstCallWithState(
                        CallState.RINGING, CallState.DIALING, CallState.ACTIVE, CallState.ON_HOLD);
                if (callToHangup != null) {
                    /// M: ALPS01790323. disconnect call through CallsManager
                    /*
                     * original code
                     * callToHangup.disconnect();
                     */
                    disconnectCall(callToHangup);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if telecom supports adding another top-level call.
     */
    boolean canAddCall() {

        /// M: Dsda call control. Need to support more than 2 top level calls. @{
        if (hasEmergencyCall()) {
            return false;
        }

        if (hasMaximumLiveCalls()) {
            Call liveCall = getFirstCallWithState(LIVE_CALL_STATES);
            if (!liveCall.can(Connection.CAPABILITY_HOLD)) {
                //Log.i(this, "can not add a new call, live call: %s don't have hold" +
                //        "capability", liveCall);
                return false;
            }
        }
        /* removed google original code:
        if (getFirstCallWithState(OUTGOING_CALL_STATES) != null) {
            return false;
        }

        int count = 0;
        for (Call call : mCalls) {
            if (call.isEmergencyCall()) {
                // We never support add call if one of the calls is an emergency call.
                return false;
            } else if (call.getParentCall() == null) {
                count++;
            }

            // We do not check states for canAddCall. We treat disconnected calls the same
            // and wait until they are removed instead. If we didn't count disconnected calls,
            // we could put InCallServices into a state where they are showing two calls but
            // also support add-call. Technically it's right, but overall looks better (UI-wise)
            // and acts better if we wait until the call is removed.
            if (count >= MAXIMUM_TOP_LEVEL_CALLS) {
                return false;
            }
        }*/
        /// @}
        return true;
    }

    Call getRingingCall() {
        return getFirstCallWithState(CallState.RINGING);
    }

    Call getActiveCall() {
        return getFirstCallWithState(CallState.ACTIVE);
    }

    Call getDialingCall() {
        return getFirstCallWithState(CallState.DIALING);
    }

    Call getHeldCall() {
        return getFirstCallWithState(CallState.ON_HOLD);
    }

    int getNumHeldCalls() {
        int count = 0;
        for (Call call : mCalls) {
            if (call.getParentCall() == null && call.getState() == CallState.ON_HOLD) {
                count++;
            }
        }
        return count;
    }

    Call getFirstCallWithState(int... states) {
        return getFirstCallWithState(null, states);
    }

    /**
     * Returns the first call that it finds with the given states. The states are treated as having
     * priority order so that any call with the first state will be returned before any call with
     * states listed later in the parameter list.
     *
     * @param callToSkip Call that this method should skip while searching
     */
    Call getFirstCallWithState(Call callToSkip, int... states) {
        for (int currentState : states) {
            // check the foreground first
            if (mForegroundCall != null && mForegroundCall.getState() == currentState) {
                return mForegroundCall;
            }

            for (Call call : mCalls) {
                if (Objects.equals(callToSkip, call)) {
                    continue;
                }

                // Only operate on top-level calls
                if (call.getParentCall() != null) {
                    continue;
                }

                if (currentState == call.getState()) {
                    return call;
                }
            }
        }
        return null;
    }

    Call createConferenceCall(
            PhoneAccountHandle phoneAccount,
            ParcelableConference parcelableConference) {

        // If the parceled conference specifies a connect time, use it; otherwise default to 0,
        // which is the default value for new Calls.
        long connectTime =
                parcelableConference.getConnectTimeMillis() ==
                        Conference.CONNECT_TIME_NOT_SPECIFIED ? 0 :
                        parcelableConference.getConnectTimeMillis();

        Call call = new Call(
                mContext,
                mConnectionServiceRepository,
                null /* handle */,
                null /* gatewayInfo */,
                null /* connectionManagerPhoneAccount */,
                phoneAccount,
                false /* isIncoming */,
                true /* isConference */,
                connectTime);

        setCallState(call, Call.getStateFromConnectionState(parcelableConference.getState()));
        call.setConnectionCapabilities(parcelableConference.getConnectionCapabilities());
        /// M: Add for dual talk @{
        call.setCallCapabilitiesFromConnection(parcelableConference.getConnectionCapabilities());
        /// @}

        // TODO: Move this to be a part of addCall()
        call.addListener(this);
        addCall(call);
        return call;
    }

    /**
     * @return the call state currently tracked by {@link PhoneStateBroadcaster}
     */
    int getCallState() {
        return mPhoneStateBroadcaster.getCallState();
    }

    /**
     * Retrieves the {@link PhoneAccountRegistrar}.
     *
     * @return The {@link PhoneAccountRegistrar}.
     */
    PhoneAccountRegistrar getPhoneAccountRegistrar() {
        return mPhoneAccountRegistrar;
    }

    /**
     * Retrieves the {@link MissedCallNotifier}
     * @return The {@link MissedCallNotifier}.
     */
    MissedCallNotifier getMissedCallNotifier() {
        return mMissedCallNotifier;
    }

    /**
     * Adds the specified call to the main list of live calls.
     *
     * @param call The call to add.
     */
    private void addCall(Call call) {
        Trace.beginSection("addCall");
        //Log.v(this, "addCall(%s)", call);
        call.addListener(this);
        mCalls.add(call);

        // TODO: Update mForegroundCall prior to invoking
        // onCallAdded for calls which immediately take the foreground (like the first call).
        for (CallsManagerListener listener : mListeners) {
            if (Log.SYSTRACE_DEBUG) {
                Trace.beginSection(listener.getClass().toString() + " addCall");
            }
            listener.onCallAdded(call);
            if (Log.SYSTRACE_DEBUG) {
                Trace.endSection();
            }
        }
        updateCallsManagerState();
        Trace.endSection();
    }

    private void removeCall(Call call) {
        Trace.beginSection("removeCall");
        //Log.v(this, "removeCall(%s)", call);

        call.setParentCall(null);  // need to clean up parent relationship before destroying.
        call.removeListener(this);
        call.clearConnectionService();

        boolean shouldNotify = false;
        if (mCalls.contains(call)) {
            mCalls.remove(call);
            shouldNotify = true;
        }

        // Only broadcast changes for calls that are being tracked.
        if (shouldNotify) {
            for (CallsManagerListener listener : mListeners) {
                if (Log.SYSTRACE_DEBUG) {
                    Trace.beginSection(listener.getClass().toString() + " onCallRemoved");
                }
                listener.onCallRemoved(call);
                if (Log.SYSTRACE_DEBUG) {
                    Trace.endSection();
                }
            }
            updateCallsManagerState();
        }
        Trace.endSection();
    }

    /**
     * Sets the specified state on the specified call.
     *
     * @param call The call.
     * @param newState The new state of the call.
     */
    private void setCallState(Call call, int newState) {
        if (call == null) {
            return;
        }
        int oldState = call.getState();
        //Log.i(this, "setCallState %s -> %s, call: %s", CallState.toString(oldState),
        //        CallState.toString(newState), call);
        if (newState != oldState) {
            // Unfortunately, in the telephony world the radio is king. So if the call notifies
            // us that the call is in a particular state, we allow it even if it doesn't make
            // sense (e.g., STATE_ACTIVE -> STATE_RINGING).
            // TODO: Consider putting a stop to the above and turning CallState
            // into a well-defined state machine.
            // TODO: Define expected state transitions here, and log when an
            // unexpected transition occurs.
            call.setState(newState);
            /// M: Dsda call control, here need to update ringing or active call's capability. @{
            updateCallCapabilities();
            /// @}

            /// M: Set the voice recording capability
            int capabilities = call.getConnectionCapabilities();
            boolean hasRecordCap = (capabilities & Connection.CAPABILITY_VOICE_RECORD) == 0
                    ? false : true;
            boolean okToRecord = okToRecordVoice(call);
            if (okToRecord && !hasRecordCap) {
                call.setConnectionCapabilities(capabilities
                        | Connection.CAPABILITY_VOICE_RECORD);
            }
            if (!okToRecord && hasRecordCap) {
                PhoneRecorderHandler.getInstance().stopRecording();
                call.setConnectionCapabilities(capabilities
                      & ~Connection.CAPABILITY_VOICE_RECORD);
            }

            Trace.beginSection("onCallStateChanged");
            // Only broadcast state change for calls that are being tracked.
            if (mCalls.contains(call)) {
                for (CallsManagerListener listener : mListeners) {
                    if (Log.SYSTRACE_DEBUG) {
                        Trace.beginSection(listener.getClass().toString() + " onCallStateChanged");
                    }
                    listener.onCallStateChanged(call, oldState, newState);
                    if (Log.SYSTRACE_DEBUG) {
                        Trace.endSection();
                    }
                }
                updateCallsManagerState();
            }
            /// M: Dsda call control, first call action finished. @{
            handleActionProcessComplete(call);
            /// @}
            Trace.endSection();
        }
    }

    /**
     * Checks which call should be visible to the user and have audio focus.
     */
    private void updateForegroundCall() {
        Trace.beginSection("updateForegroundCall");
        Call newForegroundCall = null;
        for (Call call : mCalls) {
            // TODO: Foreground-ness needs to be explicitly set. No call, regardless
            // of its state will be foreground by default and instead the connection service should
            // be notified when its calls enter and exit foreground state. Foreground will mean that
            // the call should play audio and listen to microphone if it wants.

            // Only top-level calls can be in foreground
            if (call.getParentCall() != null) {
                continue;
            }
            /// M: CC start. @{
            // All childCalls whithin a conference call should not be updated as foreground
            if (mLocallyDisconnectingCalls.contains(call)) {
                continue;
            }
            /// @}

            /// M:ALPS01833814 can not disconnect outgoing call when answer the ringing call. @{
            // Active and outgoing calls have priority.
            if (call.isActive() || call.getState() == CallState.DIALING
                    || call.getState() == CallState.CONNECTING) {
                newForegroundCall = call;
                break;
            }
            /// @}

            /**
             * M: [ALPS01752136]if there is no Active call, the Ringing one should have a
             * higher priority than hold ones, so as to play Incoming Ringtone.
             * google original code:
             *
            if (call.isAlive() || call.getState() == CallState.RINGING) {
                newForegroundCall = call;
                // Don't break in case there's an active call that has priority.
            }
             */
        }

        /// M: [ALPS01752136]if no Active call, Ringing > Holding @{
        newForegroundCall = pickForegroundCallEx(newForegroundCall);
        /// @}

        if (newForegroundCall != mForegroundCall) {
            /// M: Need to stop recording when foregroundcall changed, e.g. when merge calls
            PhoneRecorderHandler.getInstance().stopRecording();
            /// M: ALPS01750786. If new forground call and old forground call state are same, 
            /// do not change.
            if (mForegroundCall == null || newForegroundCall == null
                    || !(newForegroundCall.getState() == mForegroundCall.getState()
                        && mForegroundCall.getParentCall() != newForegroundCall)) {
                //Log.v(this, "Updating foreground call, %s -> %s.", mForegroundCall, newForegroundCall);
                Call oldForegroundCall = mForegroundCall;
                mForegroundCall = newForegroundCall;

                /* ALPS01778496 & ALPS01762509, follow L default flow, play ringtone when background call is hold
                /// M: ALPS01762509. Do not notify the change to avoid play ringtone instead of call waiting tone
                if ((oldForegroundCall != null && oldForegroundCall.getState() == CallState.ON_HOLD)
                        && (mForegroundCall != null && mForegroundCall.getState() == CallState.RINGING)) {
                    return;
                }
                */

                for (CallsManagerListener listener : mListeners) {
                    if (Log.SYSTRACE_DEBUG) {
                        Trace.beginSection(listener.getClass().toString() + " updateForegroundCall");
                    }
                    listener.onForegroundCallChanged(oldForegroundCall, mForegroundCall);
                    if (Log.SYSTRACE_DEBUG) {
                        Trace.endSection();
                    }
                }
            }
        }
    }

    private void updateCanAddCall() {
        boolean newCanAddCall = canAddCall();
        if (newCanAddCall != mCanAddCall) {
            mCanAddCall = newCanAddCall;
            for (CallsManagerListener listener : mListeners) {
                if (Log.SYSTRACE_DEBUG) {
                    Trace.beginSection(listener.getClass().toString() + " updateCanAddCall");
                }
                listener.onCanAddCallChanged(mCanAddCall);
                if (Log.SYSTRACE_DEBUG) {
                    Trace.endSection();
                }
            }
        }
    }

    private void updateCallsManagerState() {
        updateForegroundCall();
        updateCanAddCall();
    }

    private boolean isPotentialMMICode(Uri handle) {
//add by huangshuo for #911 emergency number can not dial for 2015/10/16
	if(SystemProperties.get("ro.hq.custom.ecc").equals("1")){
	   //Call call = getNewOutgoingCall(handle);
	   boolean isEmergencyCall = TelephonyUtil.shouldProcessAsEmergency(mContext,handle);
	   Log.i("huangshuo","isPotentialMMICode:isEmergencyCall"+isEmergencyCall);
	  if( !isEmergencyCall){
	      if (SystemProperties.get("ro.hq.phone.movistar.ussd").equals("1")) {//HQ_hushunli 2015-12-08 add for HQ01508499
	          if (handle != null && handle.getSchemeSpecificPart() != null) {
	              String dialNumber = handle.getSchemeSpecificPart();
	              if (dialNumber.equals("*1") || dialNumber.equals("*5") || dialNumber.equals("*9")) {
	                  Log.d(this, "isPotentialMMICode: movistar dial is *1/*5/*9 , return true");
	                  return true;
	              }
	          }
	      }
            return (handle != null && handle.getSchemeSpecificPart() != null
                && handle.getSchemeSpecificPart().contains("#"));
	  }else{
	      return false;
	  }
	}else{
	     return (handle != null && handle.getSchemeSpecificPart() != null
                && handle.getSchemeSpecificPart().contains("#"));
	}
    }
	//end by huangshuo for  #911 emergency number can not dial for 2015/10/16
	

    /**
     * Determines if a dialed number is potentially an In-Call MMI code.  In-Call MMI codes are
     * MMI codes which can be dialed when one or more calls are in progress.
     * <P>
     * Checks for numbers formatted similar to the MMI codes defined in:
     * {@link com.android.internal.telephony.gsm.GSMPhone#handleInCallMmiCommands(String)}
     * and
     * {@link com.android.internal.telephony.imsphone.ImsPhone#handleInCallMmiCommands(String)}
     *
     * @param handle The URI to call.
     * @return {@code True} if the URI represents a number which could be an in-call MMI code.
     */

    private boolean isPotentialInCallMMICode(Uri handle) {
        Log.i("huangshuo","[isPotentialInCallMMICode]");
        if (handle != null && handle.getSchemeSpecificPart() != null &&
                handle.getScheme().equals(PhoneAccount.SCHEME_TEL)) {
           //add by huangshuo for 15,16,18...is emergency call number at 2015/10/19
            if(SystemProperties.get("ro.hq.custom.ecc").equals("1")){
                  //Call call = getNewOutgoingCall(handle);
	          boolean isEmergencyCall = TelephonyUtil.shouldProcessAsEmergency(mContext,handle);
		    if(isEmergencyCall){
				Log.i("huangshuo","[isEmergencyCall]");
				return false;
	            }else{
                          String dialedNumber = handle.getSchemeSpecificPart();
                          return  isPotentialInCallMMINumber(dialedNumber);
                   }
             }else{
                        String dialedNumber = handle.getSchemeSpecificPart();
                         return isPotentialInCallMMINumber(dialedNumber);
                     }
        	}
		     return false;
	//end by huangshuo for 15,16,18...is emergency call number at 2015/10/19
         }

	
	//add by huangshuo for 15,16,18...is emergency call number at 2015/10/19
    private  boolean isPotentialInCallMMINumber(String dialedNumber){
	    if(dialedNumber==null){
			return false;
	     }else{
		 return (dialedNumber.equals("0") ||
                                       (dialedNumber.startsWith("1") && dialedNumber.length() <= 2) ||
                                       (dialedNumber.startsWith("2") && dialedNumber.length() <= 2) ||
                                         dialedNumber.equals("3") ||
                                         dialedNumber.equals("4") ||
                                         dialedNumber.equals("5"));
	     }
    }
	//end by huangshuo for 15,16,18...is emergency call number at 2015/10/19

	
    private int getNumCallsWithState(int... states) {
        int count = 0;
        for (int state : states) {
            for (Call call : mCalls) {
                if (call.getParentCall() == null && call.getState() == state) {
                // Only top-level calls will be counted.
                    count++;
                }
            }
        }
        return count;
    }

    private boolean hasMaximumLiveCalls() {
        return MAXIMUM_LIVE_CALLS <= getNumCallsWithState(LIVE_CALL_STATES);
    }

    private boolean hasMaximumHoldingCalls() {
        return MAXIMUM_HOLD_CALLS <= getNumCallsWithState(CallState.ON_HOLD);
    }

    private boolean hasMaximumRingingCalls() {
        return MAXIMUM_RINGING_CALLS <= getNumCallsWithState(CallState.RINGING);
    }

    private boolean hasMaximumOutgoingCalls() {
        return MAXIMUM_OUTGOING_CALLS <= getNumCallsWithState(OUTGOING_CALL_STATES);
    }

    private boolean makeRoomForOutgoingCall(Call call, boolean isEmergency) {
        if (hasMaximumLiveCalls()) {
            // NOTE: If the amount of live calls changes beyond 1, this logic will probably
            // have to change.
            Call liveCall = getFirstCallWithState(call, LIVE_CALL_STATES);
            Log.i(this, "makeRoomForOutgoingCall call = " + call + " livecall = " +
                   liveCall);

            if (call == liveCall) {
                // If the call is already the foreground call, then we are golden.
                // This can happen after the user selects an account in the PRE_DIAL_WAIT
                // state since the call was already populated into the list.
                return true;
            }
            Log.d(this, "has maximum outgoing calls = " + hasMaximumOutgoingCalls());
            if (hasMaximumOutgoingCalls()) {
                // Disconnect the current outgoing call if it's not an emergency call. If the user
                // tries to make two outgoing calls to different emergency call numbers, we will try
                // to connect the first outgoing call.
                if (isEmergency) {
                    Call outgoingCall = getFirstCallWithState(OUTGOING_CALL_STATES);
                    if (!outgoingCall.isEmergencyCall()) {
                        outgoingCall.disconnect();
                        return true;
                    }
                }
                return false;
            }
            Log.d(this, "has maximum holding calls = " + hasMaximumHoldingCalls());
            if (hasMaximumHoldingCalls()) {
                // There is no more room for any more calls, unless it's an emergency.
                if (isEmergency) {
                    // Kill the current active call, this is easier then trying to disconnect a
                    // holding call and hold an active call.
                    liveCall.disconnect();
                    return true;
                }
                return false;  // No more room!
            }

            // We have room for at least one more holding call at this point.

            // First thing, if we are trying to make a call with the same phone account as the live
            // call, then allow it so that the connection service can make its own decision about
            // how to handle the new call relative to the current one.
            if (Objects.equals(liveCall.getTargetPhoneAccount(), call.getTargetPhoneAccount())) {
                return true;
            } else if (call.getTargetPhoneAccount() == null) {
                // Without a phone account, we can't say reliably that the call will fail.
                // If the user chooses the same phone account as the live call, then it's
                // still possible that the call can be made (like with CDMA calls not supporting
                // hold but they still support adding a call by going immediately into conference
                // mode). Return true here and we'll run this code again after user chooses an
                // account.
                return true;
            }

            // Try to hold the live call before attempting the new outgoing call.
            if (liveCall.can(Connection.CAPABILITY_HOLD)) {
                liveCall.hold();
                return true;
            }

            // The live call cannot be held so we're out of luck here.  There's no room.
            return false;
        }
        return true;
    }

    /**
     * Creates a new call for an existing connection.
     *
     * @param callId The id of the new call.
     * @param connection The connection information.
     * @return The new call.
     */
    Call createCallForExistingConnection(String callId, ParcelableConnection connection) {
        Call call = new Call(
                mContext,
                mConnectionServiceRepository,
                connection.getHandle() /* handle */,
                null /* gatewayInfo */,
                null /* connectionManagerPhoneAccount */,
                connection.getPhoneAccount(), /* targetPhoneAccountHandle */
                false /* isIncoming */,
                false /* isConference */);

        setCallState(call, Call.getStateFromConnectionState(connection.getState()));
        call.setConnectionCapabilities(connection.getConnectionCapabilities());
        /// M: Add for dual talk @{
        call.setCallCapabilitiesFromConnection(connection.getConnectionCapabilities());
        /// @}
        call.setCallerDisplayName(connection.getCallerDisplayName(),
                connection.getCallerDisplayNamePresentation());

        call.addListener(this);
        addCall(call);

        return call;
    }

    /**
     * Dumps the state of the {@link CallsManager}.
     *
     * @param pw The {@code IndentingPrintWriter} to write the state to.
     */
    public void dump(IndentingPrintWriter pw) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, TAG);
        if (mCalls != null) {
            pw.println("mCalls: ");
            pw.increaseIndent();
            for (Call call : mCalls) {
                pw.println(call);
            }
            pw.decreaseIndent();
        }
        pw.println("mForegroundCall: " + (mForegroundCall == null ? "none" : mForegroundCall));

        if (mCallAudioManager != null) {
            pw.println("mCallAudioManager:");
            pw.increaseIndent();
            mCallAudioManager.dump(pw);
            pw.decreaseIndent();
        }

        if (mTtyManager != null) {
            pw.println("mTtyManager:");
            pw.increaseIndent();
            mTtyManager.dump(pw);
            pw.decreaseIndent();
        }

        if (mInCallController != null) {
            pw.println("mInCallController:");
            pw.increaseIndent();
            mInCallController.dump(pw);
            pw.decreaseIndent();
        }

        if (mConnectionServiceRepository != null) {
            pw.println("mConnectionServiceRepository:");
            pw.increaseIndent();
            mConnectionServiceRepository.dump(pw);
            pw.decreaseIndent();
        }
    }

    /// M: Dsda call control. @{
    /**
     * Returns all the calls that it finds with the given states.
     *
     * @param states
     * @return Call List
     */
    public List<Call> getCallsWithStates(int... states) {
        List<Call> callList = new ArrayList<Call>();
        for (int currentState : states) {
            for (Call call : mCalls) {
                if (currentState == call.getState()) {
                    // Only find the top-level calls, skip a conference child call.
                    if (call.getParentCall() == null) {
                        callList.add(call);
                    }
                }
            }
        }
        return callList;
    }

    /**
     * Check if could start a new outgoing call. Only for non-emergency call.
     * @return
     */
    public boolean canStartOutgoingCall(Uri handle) {
        if (hasEmergencyCall()) {
            return false;
        }
        if (TelephonyUtil.shouldProcessAsEmergency(mContext, handle)
                || isPotentialMMICode(handle) || isPotentialInCallMMICode(handle)) {
            return true;
        }

        /// M: ALPS02107498 @{
        // The parameter Call is used to skip the new outgoing call itself when check calls status.
        // But here, the new outgoing call has not been created, so pass null.
        return canStartOutgoingCallInternal(null);
        /// @}
    }

    /**
     * Check if meet the maxnum live calls case.
     * @param call the call will be dialed.
     * @return
     */
    private boolean canStartOutgoingCallInternal(Call call) {
        // If have ringing call, can not start a new outgoing call.
        if (hasRingingCall()) {
            Log.i(this, "can not start outgoing call, have ringing call.");
            return false;
        }

        // NOTE: If the amount of live calls changes beyond 1, this logic will probably
        // have to change.
        // if already exist a pre_dial_wait call, we disconnect this call, then dial a
        // new MO call.
        if (hasMaximumLiveCalls()) {
            /// M: ALPS02107498 @{
            // In some special case, for example, CDMA doesn't support mmicode, so the
            // mmicode is handled as voice call, if current exist an active cdma
            // call, and then setup an call with *21*xxxx#, after selected the cdma account,
            // we will get here, in order to avoid disconnect itself, skip itself at here.
            Call preDialWaitCall = getFirstCallWithState(call, CallState.PRE_DIAL_WAIT);
            /// @}
            if (preDialWaitCall != null && !Objects.equals(preDialWaitCall, call)) {
                preDialWaitCall.disconnect();
            }

            /// M: ALPS02107498 Skip the call will be dialed. @{
            Call liveCall = getFirstCallWithState(call, LIVE_CALL_STATES);
            /// @}
            // We have room for at least one more holding call at this point.
            // If liveCall can't be hold, still can't start a new outgoing call.
            if (liveCall != null && !liveCall.can(Connection.CAPABILITY_HOLD)
                    && !Objects.equals(liveCall, call)) {
                //Log.i(this, "can not start outgoing call, live call: %s don't have hold" +
                //        "capability", liveCall);
                return false;
            }
        }
        Log.i(this, "can start outgoing call.");
        return true;
    }

    /**
     * Update ANSWER capability for ringing calls. ANSWER is false means need to hang up the
     * current active call when answer the ringing call. InCallUI need to check ringing call's
     * ANSWER capability to decide whether to show alerting info or not.
     */
    private void updateRingingCallAnswerCapability() {
        if (getNumCallsWithState(CallState.RINGING) == 0) {
            Log.i(this, "no ringing call exist");
            return;
        }
        boolean canAnswer = true;

        // For PRE_DIAL_WAIT call, when ringing call came, sim select dialog will dismiss and
        // PRE_DIAL_WAIT call will be disconnected by InCallUI. So we always think ringing call
        // can answer when we have PRE_DIAL_WAIT call.
        if (hasMaximumLiveCalls() && getFirstCallWithState(CallState.PRE_DIAL_WAIT) == null) {
            if (hasMaximumHoldingCalls()) {
                // There is no more room for any more calls.
                canAnswer = false;  // No more room!
            }

            Call liveCall = getFirstCallWithState(LIVE_CALL_STATES);

            if (!liveCall.can(Connection.CAPABILITY_HOLD)) {
             // If live call can't be held, means ringing call can't be answered directly.
                canAnswer = false;
            }
        }

        Log.d(this, "updateRingCallAnswerCapability, canAnswer: %s",canAnswer);

        List<Call> ringCallList = getCallsWithStates(CallState.RINGING);
        for (Call ringCall : ringCallList) {
            int capabilities = ringCall.getConnectionCapabilities();

            if (canAnswer && !hasActiveAndWaitingCallInAnotherAccount(ringCall)) {
                capabilities |= Connection.CAPABILITY_ANSWER;
            } else {
                capabilities &= ~Connection.CAPABILITY_ANSWER;
            }
            ringCall.setConnectionCapabilities(capabilities);
        }
    }

    /**
     * Update active call's HOLD capability.
     * Telecom need to consider: hold calls in different connectionService.
     *
     */
    private void updateActiveCallHoldCapability() {
        Call activeCall = getFirstCallWithState(CallState.ACTIVE);
        if (activeCall == null) {
            Log.d(this, "no active call exist");
            return;
        }

        // Query hold capability from connection service.
        boolean canHold = activeCall.canConnectionSupportCapability(Connection.CAPABILITY_HOLD);

        //Log.d(this, "updateActiveCallHoldCapability, canHold: %s, call: %s:", canHold, activeCall);
        if (canHold) {
            /**
             * Check hold calls number, if full, reset active call's HOLD capability.
             * And not allow two hold calls when bt connected.
             */
            if (hasMaximumHoldingCalls()) {
                Log.d(this, "no room for hold calls, canHold change to false");
                canHold = false;
            } else if (mCallAudioManager.isBluetoothAudioOn()) {
                if (getHeldCall() != null) {
                    Log.d(this, "BT connected, already have hold call, canHold change to false");
                    canHold = false;
                }
            }
        }

        int capabilities = activeCall.getConnectionCapabilities();
        if (!canHold) {
            capabilities &= ~Connection.CAPABILITY_HOLD;
        } else {
            capabilities |= Connection.CAPABILITY_HOLD;
        }
        activeCall.setConnectionCapabilities(capabilities);
    }

    /**
     * Update Separate capability for child calls in a conference. InCallUI need to check this
     * capability to show separate icon. When already have max active and hold calls,can't
     * separate for child in a conference.
     */
    public void updateConferenceChildrenSeprateCapability() {
        boolean activeAndHoldNotFull = !(hasMaximumLiveCalls() && hasMaximumHoldingCalls());
        Log.d(this, "updateConferenceChildrenSeprateCapability, activeAndHoldNotFull: %s",activeAndHoldNotFull);
        for (Call call : mCalls) {
            if (!call.isConference()) {
                continue;
            }
            for (Call child : call.getChildCalls()) {
                // check capability from connection service and call full status.
                boolean canSeparate = child.canConnectionSupportCapability(
                        Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE);
                canSeparate &= activeAndHoldNotFull;
                if (!canSeparate) {
                    child.setConnectionCapabilities(child.getConnectionCapabilities()
                                & ~Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE);
                } else {
                    child.setConnectionCapabilities(child.getConnectionCapabilities()
                            | Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE);
                }
            }
        }
    }

    /**
     * Update active and ringing calls' capabilities when call state or capabilities change.
     */
    public void updateCallCapabilities() {
        updateActiveCallHoldCapability();
        updateCanAddCall();
        updateRingingCallAnswerCapability();
        updateConferenceChildrenSeprateCapability();
    }

    /**
     * If sub1 have a call, could not start another call from sub2 if modem does not support dsda.
     * @param call
     * @return
     */
    boolean canStartOutgoingCallWithPhoneAccount(Call call) {
        /// M: CDMA+GSM MMI handle. @{
        // CDMA doesn't support MMI code, so consider all MMI code as
        // setup a normal call, need check the other account's call status.
        if (TelephonyUtil.shouldProcessAsEmergency(mContext, call.getHandle())
                /*|| isPotentialMMICode(call.getHandle())*/) {
        /// @}
            return true;
        }

        PhoneAccountHandle account = call.getTargetPhoneAccount();

        /// M: CDMA+GSM MMI handle. @{
        // 1) no account, allow the call;
        // 2) the account support MMI code, allow this operation;
        // 3) the account doesn't support MMI code, check if has max
        // calls exist.
        if (isPotentialMMICode(call.getHandle())) {
            if (account != null
                    && !TelecomUtils.isSupportMMICode(mContext, account)) {
                /// ALPS02107498: Check whether the call(with mmicode number)
                // can be started in current state.
                return canStartOutgoingCallInternal(call);
            }
            return true;
        }
        /// @}
        if (account != null && !isDualtalk()) {
            if (!TelephonyUtil.isPstnComponentName(account.getComponentName())) {
                return true;
            }

            for (Call otherCall : mCalls) {
                PhoneAccountHandle otherAccount = otherCall.getTargetPhoneAccount();
                if (otherAccount == null ||
                        !TelephonyUtil.isPstnComponentName(otherAccount.getComponentName())) {
                    continue;
                } else {
                    if (!isInSamePhoneAccount(call, otherCall)) {
                        Log.d(this, "canStartOutgoingCallWithPhoneAccount: NO.");
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * To check whether have 1A1W in another account for case: 1A1W(Account1) + 1I(Account2).
     * In this case, the incoming call in account2 don't have ANSWER capability, when answer it,
     * need to hang up the active call first.
     * @return
     */
    private boolean hasActiveAndWaitingCallInAnotherAccount(Call ringCall) {
        Call activeCall = getActiveCall();
        if (activeCall != null && !isInSamePhoneAccount(activeCall, ringCall)) {
            List<Call> ringCallList = getCallsWithStates(CallState.RINGING);
            for (Call otherRingCall : ringCallList) {
                if(otherRingCall != ringCall && isInSamePhoneAccount(activeCall, otherRingCall)) {
                    Log.d(this, "hasActiveAndWaitingCallInAnotherAccount for ringCall: %s", ringCall);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if two calls are in a same phone account.
     * @param firstCall
     * @param secondCall
     * @return
     */
    public boolean isInSamePhoneAccount(Call firstCall, Call secondCall) {
        if (firstCall == null || secondCall == null) {
            return false;
        }
        PhoneAccountHandle firstPhoneAccount = firstCall.getTargetPhoneAccount();
        PhoneAccountHandle secondPhoneAccount = secondCall.getTargetPhoneAccount();

        if (firstPhoneAccount != null && firstPhoneAccount.equals(secondPhoneAccount)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isDualtalk() {
        /** M: Bug Fix for ALPS02039491 @{ */
        boolean result = TelephonyManagerEx.getDefault().isInDsdaMode();
        Log.i(this, " [isDualtalk] result : " + result);
        return result;
        /** @} */
    }

    /**
     * Separate one command to two actions. After process the first action, according to the
     * result, continue to handle or cancel the secondary action here.
     *
     * @param call: the first action related call.
     */
    public void handleActionProcessComplete(Call call) {
        //Log.d(this, "have pending call actions: %s", mPendingCallActions.containsKey(call));
        if (mPendingCallActions.containsKey(call) && (call.getState() == CallState.ON_HOLD
                || call.getState() == CallState.DISCONNECTED)) {
            PendingCallAction pendingAction = (PendingCallAction) mPendingCallActions.remove(call);

            pendingAction.handleActionProcessSuccessful();
        }
    }

    /**
     * Keep the info of the secondary pending action of a command.
     */
    public class PendingCallAction {

        public static final String PENDING_ACTION_ANSWER      =  "answer";
        public static final String PENDING_ACTION_UNHOLD      =  "unhold";
        public static final String PENDING_ACTION_OUTGOING    =  "outgoing";

        private Call mCall;
        private String mAction;
        private int mVideoState;

        public PendingCallAction(Call call, String action, int videoState) {
            mCall = call;
            mAction = action;
            mVideoState = videoState;
        }

        /**
         * Try to handle the pending call action.
         */
        public void handleActionProcessSuccessful() {
            //Log.d(this, "second action = %s, call= %s", mAction, mCall);

            if (!mCalls.contains(mCall) 
                    && !isPotentialMMICode(mCall.getHandle())
                    && !isPotentialInCallMMICode(mCall.getHandle())) {
                Log.i(this, "handleActionProcessSuccessful()- call not exist any more!");
                return;
            }

            if (mAction.equals(PENDING_ACTION_ANSWER)) {
                for (CallsManagerListener listener : mListeners) {
                    listener.onIncomingCallAnswered(mCall);
                }

                // We do not update the UI until we get confirmation of the answer() through
                // {@link #markCallAsActive}.
                if (mCall.getState() == CallState.RINGING) {
                    mCall.answer(mVideoState);
                }
            } else if (mAction.equals(PENDING_ACTION_UNHOLD)) {
                if (mCall.getState() == CallState.ON_HOLD) {
                    mCall.unhold();
                }
            } else if (mAction.equals(PENDING_ACTION_OUTGOING)) {
                mCall.startCreateConnection(mPhoneAccountRegistrar);
            }
        }

        public void handleActionProcessFailed() {
            //Log.d(this, "handleActionProcessFailed, call= %s", mCall);

            if (!mCalls.contains(mCall) 
                    && !isPotentialMMICode(mCall.getHandle())
                    && !isPotentialInCallMMICode(mCall.getHandle())) {
                Log.i(this, "handleActionProcessFailed()- call not exist any more!");
                return;
            }

            if (mAction.equals(PENDING_ACTION_OUTGOING)) {
                mCall.disconnect();
            }
        }
    }

    /**
     * M: The all background calls will be sorted according to the time
     * the call be held, e.g. the first hold call will be first item in
     * the list.
     */
    void setSortedBackgroundCallList(List<Call> list) {
        if (list != null) {
            mSortedHoldCallList.clear();
            for (Call call: list) {
                mSortedHoldCallList.add(call);
            }
        }
        // Foreground call need to consider multiple hold call case. The first hold call's priority
        // will be higher than the second hold call.
        updateForegroundCall();
        for (CallsManagerListener listener : mListeners) {
            listener.onBackgroundCallListChanged(list);
        }
    }

    /**
     * M: The all incoming calls will be sorted according to user's action,
     * since there are more than 1 incoming call exist user may touch to switch
     * any incoming call to the primary screen, the sequence of the incoming call
     * will be changed.
     */
    void setSortedIncomingCallList(List<Call> list) {
        if (list != null) {
            mSortedInComingCallList.clear();
            for (Call call: list) {
                mSortedInComingCallList.add(call);
            }
        }
        for (CallsManagerListener listener : mListeners) {
            listener.onInComingCallListChanged(list);
        }
    }
    /// @}

    /**
     * Broadcast the connection lost of the call.
     *
     * @param call: the related call.
     */
    void notifyConnectionLost(Call call) {
        //Log.d(this, "notifyConnectionLost, call:%s", call);
        for (CallsManagerListener listener : mListeners) {
            listener.onConnectionLost(call);
        }
    }

    /**
     * Clear the pending call action if the first action failed.
     *
     * @param call: the related call.
     */
    void notifyActionFailed(Call call, int action) {
        //Log.d(this, "notifyActionFailed, call:%s", call);
        if (mPendingCallActions.containsKey(call)) {
            Log.i(this, "notifyActionFailed, remove pending action");
            PendingCallAction pendingAction = mPendingCallActions.remove(call);
            pendingAction.handleActionProcessFailed();
        }
        SuppMessageHelper suppMessageHelper = new SuppMessageHelper();
        Toast.makeText(mContext, mContext.getResources()
                .getString(suppMessageHelper.getActionFailedMessageId(action)),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * show SS notification.
     *
     * @param call: the related call.
     */
    void notifySSNotificationToast(Call call, int notiType, int type, int code, String number, int index) {
        //Log.d(this, "notifySSNotificationToast, call:%s", call);
        String msg = "";
        SuppMessageHelper suppMessageHelper = new SuppMessageHelper();
        if (notiType == 0) {
            msg = suppMessageHelper.getSuppServiceMOString(code, index);
        } else if (notiType == 1) {
            String str = "";
            msg = suppMessageHelper.getSuppServiceMTString(code, index);
            if (type == 0x91) {
                if (number != null && number.length() != 0) {
                    str = " +" + number;
                }
            }
            msg = msg + str;
        }
    	//add by huangshuo for HQ01266365
        if(SystemProperties.get("ro.hq.mena.phone.waiting").equals("1") && code==SuppMessageHelper.MO_CODE_CALL_IS_WAITING && notiType == 0){
	       msg=mContext.getResources().getString(R.string.call_waiting_indication_east);
               Toast.makeText(mContext, msg, 8000).show();
        }else{
          Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
        }
   //end by huangshuo
    }

    /**
     * show SS notification.
     *
     * @param call: the related call.
     */
    void notifyNumberUpdate(Call call, String number) {
        //Log.d(this, "notifyNumberUpdate, call:%s", call);
        if (number != null && number.length() != 0) {
            Uri handle = Uri.fromParts(PhoneNumberUtils.isUriNumber(number) ?
                    PhoneAccount.SCHEME_SIP : PhoneAccount.SCHEME_TEL, number, null);
            call.setHandle(handle);
        }
    }

    /**
     * update incoming call info..
     *
     * @param call: the related call.
     */
    void notifyIncomingInfoUpdate(Call call, int type, String alphaid, int cli_validity) {
        //Log.d(this, "notifyIncomingInfoUpdate, call:%s", call);
        // The definition of "0 / 1 / 2" is in SuppCrssNotification.java
        int handlePresentation = -1;
        switch (cli_validity) {
            case 0:
                handlePresentation = TelecomManager.PRESENTATION_ALLOWED;
                break;
            case 1:
                handlePresentation = TelecomManager.PRESENTATION_RESTRICTED;
                break;
            case 2:
                handlePresentation = TelecomManager.PRESENTATION_UNKNOWN;
                break;
            default:
                break;
        }
        // TODO: For I'm not sure what is stand for handle, SuppCrssNotification.number, or SuppCrssNotification.alphaid?
        // So I do not update handle here. Need confirm with framework, and re-check this part.
        if (handlePresentation != -1 && call != null) {
            call.setHandle(call.getHandle(), handlePresentation);
        }
    }

    void notifyCdmaCallAccepted(Call call) {
        call.setConnectTimeMillis(SystemClock.elapsedRealtime());//HQ_wuruijun add for HQ01622569
        //Log.d(this, "notifyCdmaCallAccepted, call:%s", call);
        for (CallsManagerListener listener : mListeners) {
            listener.onCdmaCallAccepted(call);
        }
    }

    public class SuppMessageHelper {
        //action code
        private static final int ACTION_UNKNOWN = 0;
        private static final int ACTION_SWITCH = 1;
        private static final int ACTION_SEPARATE = 2;
        private static final int ACTION_TRANSFER = 3;
        private static final int ACTION_CONFERENCE = 4;
        private static final int ACTION_REJECT = 5;
        private static final int ACTION_HANGUP = 6;

        //MO code
        private static final int MO_CODE_UNCONDITIONAL_CF_ACTIVE = 0;
        private static final int MO_CODE_SOME_CF_ACTIVE = 1;
        private static final int MO_CODE_CALL_FORWARDED = 2;
        private static final int MO_CODE_CALL_IS_WAITING = 3;
        private static final int MO_CODE_CUG_CALL = 4;
        private static final int MO_CODE_OUTGOING_CALLS_BARRED = 5;
        private static final int MO_CODE_INCOMING_CALLS_BARRED = 6;
        private static final int MO_CODE_CLIR_SUPPRESSION_REJECTED = 7;
        private static final int MO_CODE_CALL_DEFLECTED = 8;
        private static final int MO_CODE_CALL_FORWARDED_TO = 9;

        //MT code
        private static final int MT_CODE_FORWARDED_CALL = 0;
        private static final int MT_CODE_CUG_CALL = 1;
        private static final int MT_CODE_CALL_ON_HOLD = 2;
        private static final int MT_CODE_CALL_RETRIEVED = 3;
        private static final int MT_CODE_MULTI_PARTY_CALL = 4;
        private static final int MT_CODE_ON_HOLD_CALL_RELEASED = 5;
        private static final int MT_CODE_FORWARD_CHECK_RECEIVED = 6;
        private static final int MT_CODE_CALL_CONNECTING_ECT = 7;
        private static final int MT_CODE_CALL_CONNECTED_ECT = 8;
        private static final int MT_CODE_DEFLECTED_CALL = 9;
        private static final int MT_CODE_ADDITIONAL_CALL_FORWARDED = 10;
        private static final int MT_CODE_FORWARDED_CF = 11;
        private static final int MT_CODE_FORWARDED_CF_UNCOND = 12;
        private static final int MT_CODE_FORWARDED_CF_COND = 13;
        private static final int MT_CODE_FORWARDED_CF_BUSY = 14;
        private static final int MT_CODE_FORWARDED_CF_NO_REPLY = 15;
        private static final int MT_CODE_FORWARDED_CF_NOT_REACHABLE = 16;

        public int getActionFailedMessageId(int action) {
            int errMsgId = -1;
            switch (action) {
            case ACTION_SWITCH:
                errMsgId = R.string.incall_error_supp_service_switch;
                break;
            case ACTION_SEPARATE:
                errMsgId = R.string.incall_error_supp_service_separate;
                break;
            case ACTION_TRANSFER:
                errMsgId = R.string.incall_error_supp_service_transfer;
                break;
            case ACTION_CONFERENCE:
                errMsgId = R.string.incall_error_supp_service_conference;
                break;
            case ACTION_REJECT:
                errMsgId = R.string.incall_error_supp_service_reject;
                break;
            case ACTION_HANGUP:
                errMsgId = R.string.incall_error_supp_service_hangup;
                break;
            case ACTION_UNKNOWN:
            default:
                errMsgId = R.string.incall_error_supp_service_unknown;
                break;
            }
            return errMsgId;
        }

        public String getSuppServiceMOString(int code, int index) {
	    Log.i("huangshuo","[getSuppServiceMOString]:code:"+code+",index:"+index);
            String moStr = "";
            switch (code) {
            case MO_CODE_UNCONDITIONAL_CF_ACTIVE:
                moStr = mContext.getResources()
                        .getString(R.string.mo_code_unconditional_cf_active);
                break;
            case MO_CODE_SOME_CF_ACTIVE:
                moStr = mContext.getResources().getString(R.string.mo_code_some_cf_active);
                break;
            case MO_CODE_CALL_FORWARDED:
                moStr = mContext.getResources().getString(R.string.mo_code_call_forwarded);
                break;
            case MO_CODE_CALL_IS_WAITING:
                moStr = mContext.getResources().getString(R.string.call_waiting_indication);
                break;
            case MO_CODE_CUG_CALL:
		if(SystemProperties.get("ro.hq.phone.cug").equals("1")){
		     moStr = mContext.getResources().getString(R.string.mo_code_no_cug_call);
		}else{
                    moStr = mContext.getResources().getString(R.string.mo_code_cug_call);
                    moStr = moStr + " " + index;
		 }
                break;
            case MO_CODE_OUTGOING_CALLS_BARRED:
                moStr = mContext.getResources().getString(R.string.mo_code_outgoing_calls_barred);
                break;
            case MO_CODE_INCOMING_CALLS_BARRED:
                moStr = mContext.getResources().getString(R.string.mo_code_incoming_calls_barred);
                break;
            case MO_CODE_CLIR_SUPPRESSION_REJECTED:
                moStr = mContext.getResources().getString(
                        R.string.mo_code_clir_suppression_rejected);
                break;
            case MO_CODE_CALL_DEFLECTED:
                moStr = mContext.getResources().getString(R.string.mo_code_call_deflected);
                break;
            case MO_CODE_CALL_FORWARDED_TO:
                // here we just show "call forwarding...",
                // and number will be updated via pau later if needed.
                moStr = mContext.getResources().getString(R.string.mo_code_call_forwarding);
                break;
            default:
                // Attempt to use a service we don't recognize or support
                // ("Unsupported service" or "Selected service failed")
                moStr = mContext.getResources().getString(
                        R.string.incall_error_supp_service_unknown);
                break;
            }
            return moStr;
        }

        public String getSuppServiceMTString(int code, int index) {
            String mtStr = "";
	     Log.i("huangshuo","[getSuppServiceMTString]:code:"+code+",index:"+index);
            switch (code) {
            case MT_CODE_FORWARDED_CALL:
                mtStr = mContext.getResources().getString(R.string.mt_code_forwarded_call);
                break;
            case MT_CODE_CUG_CALL:
		if(SystemProperties.get("ro.hq.phone.cug").equals("1")){
		   mtStr = mContext.getResources().getString(R.string.mt_code_no_cug_call);
		}else{
                  mtStr = mContext.getResources().getString(R.string.mt_code_cug_call);
                  mtStr = mtStr + " " + index;
		}
                break;
            case MT_CODE_CALL_ON_HOLD:
                mtStr = mContext.getResources().getString(R.string.mt_code_call_on_hold);
                break;
            case MT_CODE_CALL_RETRIEVED:
                mtStr = mContext.getResources().getString(R.string.mt_code_call_retrieved);
                break;
            case MT_CODE_MULTI_PARTY_CALL:
                mtStr = mContext.getResources().getString(R.string.mt_code_multi_party_call);
                break;
            case MT_CODE_ON_HOLD_CALL_RELEASED:
                mtStr = mContext.getResources().getString(R.string.mt_code_on_hold_call_released);
                break;
            case MT_CODE_FORWARD_CHECK_RECEIVED:
                mtStr = mContext.getResources().getString(R.string.mt_code_forward_check_received);
                break;
            case MT_CODE_CALL_CONNECTING_ECT:
                mtStr = mContext.getResources().getString(R.string.mt_code_call_connecting_ect);
                break;
            case MT_CODE_CALL_CONNECTED_ECT:
                mtStr = mContext.getResources().getString(R.string.mt_code_call_connected_ect);
                break;
            case MT_CODE_DEFLECTED_CALL:
                mtStr = mContext.getResources().getString(R.string.mt_code_deflected_call);
                break;
            case MT_CODE_ADDITIONAL_CALL_FORWARDED:
                mtStr = mContext.getResources().getString(
                        R.string.mt_code_additional_call_forwarded);
                break;
            case MT_CODE_FORWARDED_CF:
                mtStr = mContext.getResources().getString(R.string.mt_code_forwarded_call) + "("
                        + mContext.getResources().getString(R.string.mt_code_forwarded_cf) + ")";
                break;
            case MT_CODE_FORWARDED_CF_UNCOND:
                mtStr = mContext.getResources().getString(R.string.mt_code_forwarded_call) + "("
                        + mContext.getResources().getString(R.string.mt_code_forwarded_cf_uncond)
                        + ")";
                break;
            case MT_CODE_FORWARDED_CF_COND:
                mtStr = mContext.getResources().getString(R.string.mt_code_forwarded_call) + "("
                        + mContext.getResources().getString(R.string.mt_code_forwarded_cf_cond)
                        + ")";
                break;
            case MT_CODE_FORWARDED_CF_BUSY:
                mtStr = mContext.getResources().getString(R.string.mt_code_forwarded_call) + "("
                        + mContext.getResources().getString(R.string.mt_code_forwarded_cf_busy)
                        + ")";
                break;
            case MT_CODE_FORWARDED_CF_NO_REPLY:
                mtStr = mContext.getResources().getString(R.string.mt_code_forwarded_call) + "("
                        + mContext.getResources().getString(R.string.mt_code_forwarded_cf_no_reply)
                        + ")";
                break;
            case MT_CODE_FORWARDED_CF_NOT_REACHABLE:
                mtStr = mContext.getResources().getString(R.string.mt_code_forwarded_call)
                        + "("
                        + mContext.getResources().getString(
                                R.string.mt_code_forwarded_cf_not_reachable) + ")";
                break;
            default:
                // Attempt to use a service we don't recognize or support
                // ("Unsupported service" or "Selected service failed")
                mtStr = mContext.getResources().getString(
                        R.string.incall_error_supp_service_unknown);
                break;
            }
            return mtStr;
        }
    }
    /**
     * M: Whether can record voice for a call
     * @return true if it can, false for not
     */
    public boolean okToRecordVoice(Call call) {
        // Use System.getProperties()
//        if (!FeatureOption.MTK_PHONE_VOICE_RECORDING) {
//            //For dualtalk solution, because of audio's limitation, don't support voice record
//            return retval;
//        }
        Log.v(this, "okToRecordVoice start");

        if (call.getState() != CallState.ACTIVE) {
            return false;
        }
        Log.v(this, "okToRecordVoice call is active");

        PhoneAccountHandle accountHandle = call.getTargetPhoneAccount();
        if (accountHandle != null) {
            Log.v(this, "okToRecordVoice accountHandle is not null");
            ComponentName name = accountHandle.getComponentName();
            Log.v(this, "okToRecordVoice name=" + name);
            if (TelephonyUtil.isPstnComponentName(name)) {
                Log.v(this, "okToRecordVoice isPstnComponentName");
                return true;
            }
        }

        return false;
    }

    /**
     * M: Start voice recording
     */
    void startVoiceRecording() {
        PhoneRecorderHandler.getInstance().startVoiceRecord(
                PhoneRecorderHandler.PHONE_RECORDING_VOICE_CALL_CUSTOM_VALUE);
    }

    /**
     * M: Stop voice recording
     */
    void stopVoiceRecording() {
        PhoneRecorderHandler.getInstance().stopRecording();
    }

    /**
     * M: Power on/off device when connecting to smart book
     */
    void updatePowerForSmartBook(boolean onOff) {
        TelecomOverlay.updatePowerForSmartBook(mContext, onOff);
    }

    boolean neededForceSpeakerOn() {
        boolean result = false;
        Log.i(TAG, "neededForceSpeakerOn");
        if (android.os.SystemProperties.get("ro.mtk_tb_call_speaker_on").equals("1")) {
            Log.i(TAG, "neededForceSpeakerOn, ro.mtk_tb_call_speaker_on == 1");
            if (!mWiredHeadsetManager.isPluggedIn()
                    && !mCallAudioManager.isBluetoothDeviceAvailable()) {
                Log.i(TAG, "neededForceSpeakerOn, ro.mtk_tb_call_speaker_on == 1 && no bt!");
                if (mCallAudioManager.getAudioState().route != AudioState.ROUTE_SPEAKER) {
                    result = true;
                    Log.i(TAG, "neededForceSpeakerOn, set route to speaker");
                }
            }
        }
        return result;
    }

    ///M: add for auto answer @{
    private Handler mAutoAnswerHandler = new Handler(){
        public void handleMessage(Message msg){
            if(DELAY_AUTO_ANSWER == msg.what){
                applyAutoAnswerCall((Call)msg.obj);
            }
        }
    };

    private void applyAutoAnswerCall (Call incomingCall) {
        Log.d(this, "applyAutoAnswerCall~~");
        try {
            Context friendContext = mContext.createPackageContext("com.mediatek.engineermode",
                    Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences sh = friendContext.getSharedPreferences("AutoAnswer",
                    Context.MODE_WORLD_READABLE);

            if (sh.getBoolean("flag", false)) {
                if (null != incomingCall) {
                    //Use this API to keep auto answer behavior is the same as manual answer call
                    answerCall(incomingCall, VideoProfile.VideoState.BIDIRECTIONAL);
                }
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }
    /// @}

    /**
     * M: [ALPS01752136]set the foreground priority for non-active Calls.
     * @param previousCall the Active Call or null.
     * @return new foreground call or null.
     */
    private Call pickForegroundCallEx(Call previousCall) {
        if (previousCall != null) {
            return previousCall;
        }

        Call ringingCall = null;
        Call aliveCall = null;
        for (Call call : mCalls) {
            if (call.getParentCall() != null) {
                continue;
            }

            if (call.getState() == CallState.RINGING) {
                /// FIXME: M: we can't distinguish multiple ringing calls
                ringingCall = call;
                break;
            }
            if (call.isAlive()) {
                /// FIXME: M: we can't distinguish multiple alive calls
                aliveCall = call;
            }
        }
        // use sorted hold call to distinguish multiple hold calls.
        if (aliveCall != null && aliveCall.getState() == CallState.ON_HOLD
                && mSortedHoldCallList.size() >= 2) {
            aliveCall = mSortedHoldCallList.get(0);
        }
        return ringingCall != null ? ringingCall : aliveCall;
    }

    /**
     * M: Handle explicit call transfer.
     */
    void explicitCallTransfer(Call call) {
        if (call != null) {
            final ConnectionServiceWrapper service = call.getConnectionService();
            service.explicitCallTransfer(call);
        } else {
            Log.w(this, "explicitCallTransfer failed, call is null");
        }
    }

    /**
     * M: Instructs Telecom to hang up all calls.
     */
    public void hangupAll() {
        Log.v(this, "hangupAll");

        for (Call call : mCalls) {
            if (call.getParentCall() != null) {
                continue;
            }
            call.hangupAll();
        }
    }

    /**
     * M: Instructs Telecom to disconnect all ON_HOLD calls.
     */
    public void hangupAllHoldCalls() {
        Log.v(this, "hangupAllHoldCalls");

        for (Call call : mCalls) {
            if (call.getParentCall() != null) {
                continue;
            }
            if (call.getState() == CallState.ON_HOLD) {
                disconnectCall(call);
            }
        }
    }

    /**
     * M: Instructs Telecom to disconnect active call and answer waiting call.
     */
    public void hangupActiveAndAnswerWaiting() {
        Log.v(this, "hangupActiveAndAnswerWaiting");
        Call ringingCall = mSortedInComingCallList.get(0);
        if (!mCalls.contains(ringingCall)) {
            Log.i(this, "Request to answer a non-existent call %s", ringingCall);
            return;
        }
        if (mForegroundCall != null && mForegroundCall.isActive()) {
            mPendingCallActions.put(mForegroundCall, new PendingCallAction(ringingCall,
                    PendingCallAction.PENDING_ACTION_ANSWER, VideoProfile.VideoState.AUDIO_ONLY));

            mForegroundCall.disconnect(PendingCallAction.PENDING_ACTION_ANSWER);
        }
    }

    /** 
     * M: [ALPS01798317]: judge whether all calls are ringing call
     * @return true: all calls are ringing.
     */
    public boolean isAllCallRinging() {
        for (Call call : mCalls) {
            if (call.getState() != CallState.RINGING) {
                return false;
            }
        }

        return true;
    }

    /**
     * M: Help to check whether have pending ecc.
     * @return
     */
    public boolean hasPendingEcc() {
        return mHasPendingECC;
    }

    /**
     * M: Check if a call can swap.
     */
    private boolean canSwap(Call call) {
        /// M: If telephony say this call can be hold, that means this call can swap with
        // another hold call.
        if (call != null && call.canConnectionSupportCapability(Connection.CAPABILITY_HOLD)) {
            return true;
        }

        return false;
    }

    /// M: For VoLTE @{
    public Call placeOutgoingConferenceCall(PhoneAccountHandle phoneAccount, List<String> numbers) {
        Call call = new Call(
                mContext,
                mConnectionServiceRepository,
                null /* handle */,
                null /* gatewayInfo */,
                null /* connectionManagerPhoneAccount */,
                phoneAccount,
                false /* isIncoming */,
                true /* isConference */);
        Log.d(this, "placeOutgoingConferenceCall()...");
        call.setState(CallState.CONNECTING);
        call.addListener(this);
        call.setIsConferenceDial(true);
        call.setConferenceDialNumbers(numbers);
        addCall(call);
        call.startCreateConnection(mPhoneAccountRegistrar);
        return call;
    }
    /// @}

    /// M: Update voice record capability. ALPS02026591 @{
    // For conference call, at first time, the Account will be null,
    // so check the CAPABILITY_VOICE_RECORD when the account changed.
    @Override
    public void onTargetPhoneAccountChanged(Call call) {
        Log.d(this, "onTargetPhoneAccountChanged()...");
        int capability = call.getConnectionCapabilities();
        if (okToRecordVoice(call) && !call.can(Connection.CAPABILITY_VOICE_RECORD)) {
            call.setConnectionCapabilities(capability | Connection.CAPABILITY_VOICE_RECORD);
        }
    }
    /// @}
    
	// Add For synchronize ringer and UI
	public void playIncomingCallRingtone() {
		Log.v(this, "playIncomingCallRingtone()");
		mRinger.startRingingOrCallWaiting();// private -> public
	}
	
	// Edit for ALPS02324806, Add function
	boolean hasHoldingCall() {
		return getFirstCallWithState(CallState.ON_HOLD) != null;
	}
	// END
}
