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
 * limitations under the License
 */

package com.android.incallui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.ContactInfoCache.ContactInfoUpdatedListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallEventListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallPresenter.PhoneRecorderListener;
import com.android.incalluibind.ObjectFactory;

import java.lang.ref.WeakReference;

import com.google.common.base.Preconditions;

import com.mediatek.incallui.InCallTrace;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;
import com.mediatek.incallui.CallDetailChangeHandler;
import com.mediatek.incallui.CallDetailChangeHandler.CallDetailChangeListener;
import com.mediatek.incallui.volte.InCallUIVolteUtils;
import com.cootek.smartdialer_plugin_oem.CooTekSmartdialerOemModule;
import com.cootek.smartdialer.aidl.CallerIdResult;
import com.cootek.smartdialer_plugin_oem.CooTekSmartdialerOemModule.CallerIdStrategy;
import android.provider.Settings;
import android.telephony.SubscriptionManager;

/**
 * Presenter for the Call Card Fragment.
 * <p>
 * This class listens for changes to InCallState and passes it along to the fragment.
 */
public class CallCardPresenter extends Presenter<CallCardPresenter.CallCardUi>
        implements InCallStateListener, IncomingCallListener, InCallDetailsListener,
        InCallEventListener, PhoneRecorderListener {

    private static final String TAG = CallCardPresenter.class.getSimpleName();
    private static final long CALL_TIME_UPDATE_INTERVAL_MS = 1000;
    //maheling HQ01312058 2015.11.5
    public static String number;

	//add by zhangjinqiang for al812--start
	private  CooTekSmartdialerOemModule csom;
	private String recordName;
	//add by zjq end

    // Use to mark which contact info and view needs to be updated.
    private enum CallEnum {
        PRIMARY,
        SECONDARY,
        THIRD,
        NULL;
    }

    private Call mPrimary;
    private Call mSecondary;
    private Call mThird;
    private ContactCacheEntry mPrimaryContactInfo;
    private ContactCacheEntry mSecondaryContactInfo;
    private ContactCacheEntry mThirdContactInfo;
    private CallTimer mCallTimer;
    private Context mContext;

    public static class ContactLookupCallback implements ContactInfoCacheCallback {
        private final WeakReference<CallCardPresenter> mCallCardPresenter;
        private final CallEnum mType;

        public ContactLookupCallback(CallCardPresenter callCardPresenter, CallEnum type) {
            mCallCardPresenter = new WeakReference<CallCardPresenter>(callCardPresenter);
            mType = type;
        }

        @Override
        public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
            CallCardPresenter presenter = mCallCardPresenter.get();
            if (presenter != null) {
                presenter.onContactInfoComplete(callId, entry, mType);
            }
        }

        @Override
        public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
            CallCardPresenter presenter = mCallCardPresenter.get();
            if (presenter != null) {
                presenter.onImageLoadComplete(callId, entry);
            }
        }

    }

    public CallCardPresenter() {
        // create the call timer
        mCallTimer = new CallTimer(new Runnable() {
            @Override
            public void run() {
                updateCallTime();
            }
        });
    }

    public void init(Context context, Call call) {
        mContext = Preconditions.checkNotNull(context);

        /// M: For volte @{
        // Here we will use "mContext", so need add here, instead of "onUiReady()"
        ContactInfoCache.getInstance(mContext).addContactInfoUpdatedListener(mContactInfoUpdatedListener);
        /// @}

        // Call may be null if disconnect happened already.
        if (call != null) {
            mPrimary = call;

            // start processing lookups right away.
            if (!call.isConferenceCall()) {
                startContactInfoSearch(call, CallEnum.PRIMARY, call.getState() == Call.State.INCOMING);
            } else {
                /// M: Modified this for MTK DSDA feature. @{
                /* Google Code:
                updateContactEntry(null, true);
                */
                updateContactEntry(null, CallEnum.PRIMARY, true);
                /// @}
            }
        }
    }

    @Override
    public void onUiReady(CallCardUi ui) {
        super.onUiReady(ui);

        // Contact search may have completed before ui is ready.
        if (mPrimaryContactInfo != null) {
            updatePrimaryDisplayInfo();
        }

        // Register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addInCallEventListener(this);
        /// M: Add for recording. @{
        InCallPresenter.getInstance().addPhoneRecorderListener(this);
        updateVoiceCallRecordState();
        /// @}
    }

    @Override
    public void onUiUnready(CallCardUi ui) {
        super.onUiUnready(ui);

        // stop getting call state changes
        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeInCallEventListener(this);

        /// M: ALPS01828853. @{
        // should remove listener when ui unready.
        InCallPresenter.getInstance().removePhoneRecorderListener(this);
        /// @}

        /// M: For volte @{
        ContactInfoCache.getInstance(mContext).removeContactInfoUpdatedListener(mContactInfoUpdatedListener);
        /// @}

        mPrimary = null;
        mPrimaryContactInfo = null;
        mSecondaryContactInfo = null;
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        // same logic should happen as with onStateChange()
        onStateChange(oldState, newState, CallList.getInstance());
    }

    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        Log.d(this, "onStateChange() " + newState);
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        Call primary = null;
        Call secondary = null;
        Call third = null;

        if (newState == InCallState.INCOMING) {
            primary = callList.getIncomingCall();
            secondary = callList.getSecondaryIncomingCall();
		   ui.showCircle();
        } else if (newState == InCallState.PENDING_OUTGOING || newState == InCallState.OUTGOING) {
            primary = callList.getOutgoingCall();
            if (primary == null) {
                primary = callList.getPendingOutgoingCall();
            }

            // getCallToDisplay doesn't go through outgoing or incoming calls. It will return the
            // highest priority call to display as the secondary call.
            secondary = getCallToDisplay(callList, null, true);
            third = getThirdCallToDisplay(callList, primary, secondary);
        } else if (newState == InCallState.INCALL) {
            primary = getCallToDisplay(callList, null, false);
            secondary = getCallToDisplay(callList, primary, true);
            third = getThirdCallToDisplay(callList, primary, secondary);
        }

        Log.d(this, "Primary call: " + primary);
        Log.d(this, "Secondary call: " + secondary);
        Log.d(this, "Third call: " + third);

        final boolean primaryChanged = !Call.areSame(mPrimary, primary);
        final boolean secondaryChanged = !Call.areSame(mSecondary, secondary);
        final boolean thirdChanged = !Call.areSame(mThird, third);

        mSecondary = secondary;
        mPrimary = primary;
        mThird = third;

        // Refresh primary call information if either:
        // 1. Primary call changed.
        // 2. The call's ability to manage conference has changed.
        if (mPrimary != null && (primaryChanged ||
                /// M: [VoLTE Conference] volte conference incoming call @{
                needUpdatePrimaryForVolte(oldState, newState, mPrimary) ||
                /// @}
                ui.isManageConferenceVisible() != shouldShowManageConference())) {
            // primary call has changed
            mPrimaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext, mPrimary,
                    mPrimary.getState() == Call.State.INCOMING);
            updatePrimaryDisplayInfo();
            maybeStartSearch(mPrimary, CallEnum.PRIMARY);
            mPrimary.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
        }

        if (mSecondary == null) {
            // Secondary call may have ended.  Update the ui.
            mSecondaryContactInfo = null;
            updateSecondaryDisplayInfo();
        } else if (secondaryChanged) {
            // secondary call has changed
            mSecondaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext, mSecondary,
                    Call.State.isIncoming(mSecondary.getState()));
            updateSecondaryDisplayInfo();
            maybeStartSearch(mSecondary, CallEnum.SECONDARY);
            mSecondary.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
        }

        if (mThird == null) {
            // Third call may have ended.  Update the ui.
            mThirdContactInfo = null;
            updateThirdDisplayInfo(false);
        } else if (thirdChanged) {
            // Third call has changed.
            mThirdContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext, mThird,
                    Call.State.isIncoming(mThird.getState()));
            updateThirdDisplayInfo(mThird.isConferenceCall());
            maybeStartSearch(mThird, CallEnum.THIRD);
            mThird.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
        }

        // Start/stop timers.
        if (mPrimary != null && mPrimary.getState() == Call.State.ACTIVE) {
            Log.d(this, "Starting the calltime timer");
		   if(mSecondary!=null){
				ui.showCallTime();
		   }
            mCallTimer.start(CALL_TIME_UPDATE_INTERVAL_MS);
		   //add by zhangjinqiang for get recordName --start
		   Log.d("recordName",recordName);
		   Settings.System.putString(mContext.getContentResolver(), "record_Name", recordName);
		   //add by zjq end
        } else {
            Log.d(this, "Canceling the calltime timer");
            mCallTimer.cancel();
            ui.setPrimaryCallElapsedTime(false, 0);
        }

        // Set the call state
        int callState = Call.State.IDLE;
        if (mPrimary != null) {
            callState = mPrimary.getState();
            updatePrimaryCallState();
        } else {
            getUi().setCallState(
                    callState,
                    VideoProfile.VideoState.AUDIO_ONLY,
                    Call.SessionModificationState.NO_REQUEST,
                    new DisconnectCause(DisconnectCause.UNKNOWN),
                    null,
                    null,//add by liruihong for HQ01440479
                    null,
                    null);
        }

        // Hide/show the contact photo based on the video state.
        // If the primary call is a video call on hold, still show the contact photo.
        // If the primary call is an active video call, hide the contact photo.
        if (mPrimary != null) {
            getUi().setPhotoVisible(!(mPrimary.isVideoCall(mContext) &&
                    callState != Call.State.ONHOLD));
        }

        maybeShowManageConferenceCallButton();

        final boolean enableEndCallButton = Call.State.isConnectingOrConnected(callState) &&
                callState != Call.State.INCOMING && mPrimary != null;
        // Hide the end call button instantly if we're receiving an incoming call.
        getUi().setEndCallButtonEnabled(
                enableEndCallButton, callState != Call.State.INCOMING /* animate */);

        /// M: for ALPS01774241
        // update record icon when state change
        updateVoiceCallRecordState();

        /// M: for ALPS01945830, update primarycall and callbutton background color. @{
        ui.updateColors();
        /// @}
    }

    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        updatePrimaryCallState();

        if (call.can(android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE) !=
                android.telecom.Call.Details.can(
                        details.getCallCapabilities(),
                        android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE)) {
            maybeShowManageConferenceCallButton();
        }
    }

    private String getSubscriptionNumber() {
        // If it's an emergency call, and they're not populating the callback number,
        // then try to fall back to the phone sub info (to hopefully get the SIM's
        // number directly from the telephony layer).
        PhoneAccountHandle accountHandle = mPrimary.getAccountHandle();
        if (accountHandle != null) {
            TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
            PhoneAccount account = mgr.getPhoneAccount(accountHandle);
            if (account != null) {
                return getNumberFromHandle(account.getSubscriptionAddress());
            }
        }
        return null;
    }

    private void updatePrimaryCallState() {
        InCallTrace.begin("callcard_updateprimarystate");
	 Log.i("liruihong","updatePrimaryCallState:getShortDescription = "+getShortDescription());
        if (getUi() != null && mPrimary != null) {
            getUi().setCallState(
                    mPrimary.getState(),
                    mPrimary.getVideoState(),
                    mPrimary.getSessionModificationState(),
                    mPrimary.getDisconnectCause(),
                    getConnectionLabel(),
                    getShortDescription(), //add by liruihog
                    getCallStateIcon(),
                    getGatewayNumber());
            setCallbackNumber();

            //add for Plug-in. @{
            ExtensionManager.getCallCardExt().onStateChange(mPrimary.getTelecommCall());
            ExtensionManager.getRCSeCallCardExt().onStateChange(mPrimary.getTelecommCall());
            //add for Plug-in. @}
        }
        InCallTrace.end("callcard_updateprimarystate");
    }

    /**
     * Only show the conference call button if we can manage the conference.
     */
    private void maybeShowManageConferenceCallButton() {
        getUi().showManageConferenceCallButton(shouldShowManageConference());
    }

    /**
     * Determines if the manage conference button should be visible, based on the current primary
     * call.
     *
     * @return {@code True} if the manage conference button should be visible.
     */
    private boolean shouldShowManageConference() {
        if (mPrimary == null) {
            return false;
        }

        return mPrimary.can(android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE);
    }

    private void setCallbackNumber() {
        String callbackNumber = null;

        boolean isEmergencyCall = PhoneNumberUtils.isEmergencyNumber(
                getNumberFromHandle(mPrimary.getHandle()));
        if (isEmergencyCall) {
            callbackNumber = getSubscriptionNumber();
        } else {
            StatusHints statusHints = mPrimary.getTelecommCall().getDetails().getStatusHints();
            if (statusHints != null) {
                Bundle extras = statusHints.getExtras();
                if (extras != null) {
                    callbackNumber = extras.getString(TelecomManager.EXTRA_CALL_BACK_NUMBER);
                }
            }
        }

        TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
        String simNumber = mgr.getLine1Number(mPrimary.getAccountHandle());
        if (PhoneNumberUtils.compare(callbackNumber, simNumber)) {
            Log.d(this, "Numbers are the same; not showing the callback number");
            callbackNumber = null;
        }

        getUi().setCallbackNumber(callbackNumber, isEmergencyCall);
    }

    public void updateCallTime() {
        final CallCardUi ui = getUi();

        if (ui == null || mPrimary == null || mPrimary.getState() != Call.State.ACTIVE) {
            if (ui != null) {
                ui.setPrimaryCallElapsedTime(false, 0);
            }
            mCallTimer.cancel();
        } else {
            final long callStart = mPrimary.getConnectTimeMillis();
            final long duration = SystemClock.elapsedRealtime() - callStart;//HQ_wuruijun add for HQ01622569
            ui.setPrimaryCallElapsedTime(true, duration);
        }
    }

    public void onCallStateButtonTouched() {
        Intent broadcastIntent = ObjectFactory.getCallStateButtonBroadcastIntent(mContext);
        if (broadcastIntent != null) {
            Log.d(this, "Sending call state button broadcast: ", broadcastIntent);
            mContext.sendBroadcast(broadcastIntent, Manifest.permission.READ_PHONE_STATE);
        }
    }

    private void maybeStartSearch(Call call, CallEnum type) {
        // no need to start search for conference calls which show generic info.
        /**
         * M: [VoLTE conference] incoming call still need to search.
         * google original code:
        if (call != null && !call.isConferenceCall()) {
         * @{
         */
        if ((call != null && !call.isConferenceCall()) || isIncomingVolteConference(call)) {
        /** @} */
            startContactInfoSearch(call, type, call.getState() == Call.State.INCOMING);
        }
    }

    /**
     * Starts a query for more contact data for the save primary and secondary calls.
     */
    private void startContactInfoSearch(final Call call, CallEnum type,
            boolean isIncoming) {
        final ContactInfoCache cache = ContactInfoCache.getInstance(mContext);

        cache.findInfo(call, isIncoming, new ContactLookupCallback(this, type));
    }

    /// M: Modify this for DSDA feature. @{
    /* Google code:
    private void onContactInfoComplete(String callId, ContactCacheEntry entry, boolean isPrimary) {
        updateContactEntry(entry, isPrimary);
        if (entry.name != null) {
            Log.d(TAG, "Contact found: " + entry);
        }
        if (entry.contactUri != null) {
            CallerInfoUtils.sendViewNotification(mContext, entry.contactUri);
        }
    }
    */

    private void onContactInfoComplete(String callId, ContactCacheEntry entry, CallEnum type) {
        /// M: Here need to check the finished querying callId is what kind of call@{
        CallEnum newType = reCalculateContactInfoType(callId);
        if (newType != CallEnum.NULL) {
            updateContactEntry(entry, newType, false);
            if (entry.name != null) {
                Log.d(TAG, "Contact found: " + entry);
            }
            if (entry.contactUri != null) {
                CallerInfoUtils.sendViewNotification(mContext, entry.contactUri);
            }
        }
    }
    /// @}

    private void onImageLoadComplete(String callId, ContactCacheEntry entry) {
        if (getUi() == null) {
            return;
        }

        if (entry.photo != null) {
            if (mPrimary != null && callId.equals(mPrimary.getId())) {
                getUi().setPrimaryImage(entry.photo);
            }
        }
    }

    private void updateContactEntry(ContactCacheEntry entry, boolean isPrimary) {
        if (isPrimary) {
            mPrimaryContactInfo = entry;
            updatePrimaryDisplayInfo();
        } else {
            mSecondaryContactInfo = entry;
            updateSecondaryDisplayInfo();
        }
    }

    /**
     * Update the contact entry and view with specified view type.
     *
     * @param entry
     * @param type Includes the following three types: PRIMARY/SECONDARY/THIRD.
     * @param isConference
     */
    private void updateContactEntry(ContactCacheEntry entry, CallEnum type, boolean isConference) {
        Log.d(this, "updateContactEntry, type = " + type + "; entry = " + entry);
        switch (type) {
            case PRIMARY:
                mPrimaryContactInfo = entry;
                updatePrimaryDisplayInfo();
                break;
            case SECONDARY:
                mSecondaryContactInfo = entry;
                updateSecondaryDisplayInfo();
                break;
            case THIRD:
                mThirdContactInfo = entry;
                updateThirdDisplayInfo(isConference);
                break;
            default:
                break;
        }
    }

    /**
     * Get the highest priority call to display.
     * Goes through the calls and chooses which to return based on priority of which type of call
     * to display to the user. Callers can use the "ignore" feature to get the second best call
     * by passing a previously found primary call as ignore.
     *
     * @param ignore A call to ignore if found.
     */
    private Call getCallToDisplay(CallList callList, Call ignore, boolean skipDisconnected) {

        // Active calls come second.  An active call always gets precedent.
        Call retval = callList.getActiveCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Disconnected calls get primary position if there are no active calls
        // to let user know quickly what call has disconnected. Disconnected
        // calls are very short lived.
        if (!skipDisconnected) {
            retval = callList.getDisconnectingCall();
            if (retval != null && retval != ignore) {
                return retval;
            }

            /// M: ALPS02217975 previously disconnected call screen for cdma is shown again@{
            retval = getDisconnectedCdmaConfCall(callList);
            if (retval != null && retval != ignore) {
                return retval;
            }
            /// @}

            retval = callList.getDisconnectedCall();
            if (retval != null && retval != ignore) {
                return retval;
            }
        }

        // Then we go to background call (calls on hold)
        retval = callList.getBackgroundCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Lastly, we go to a second background call.
        retval = callList.getSecondBackgroundCall();

        return retval;
    }

    private void updatePrimaryDisplayInfo() {
        final CallCardUi ui = getUi();
        if (ui == null) {
            // TODO: May also occur if search result comes back after ui is destroyed. Look into
            // removing that case completely.
            Log.d(TAG, "updatePrimaryDisplayInfo called but ui is null!");
            return;
        }

        InCallTrace.begin("callcard_updateprimaryinfo");
        if (mPrimary == null) {
            // Clear the primary display info.
            ui.setPrimary(null, null, false, null, null, false);
            return;
        }

        if (mPrimary.isConferenceCall()) {
            Log.d(TAG, "Update primary display info for conference call.");

            /// M: [VoLTE conference]show caller info for incoming volte conference @{
            if (isIncomingVolteConference(mPrimary)) {
                setPrimaryForIncomingVolteConference();
            } else {
            /// @}
            ui.setPrimary(
                    null /* number */,
                    getConferenceString(mPrimary),
                    false /* nameIsNumber */,
                    null /* label */,
                    getConferencePhoto(mPrimary),
                    false /* isSipCall */);
            }
        } else if (mPrimaryContactInfo != null) {
            Log.d(TAG, "Update primary display info for " + mPrimaryContactInfo);

            String name = getNameForCallHW(mPrimaryContactInfo);
            number = getNumberForCall(mPrimaryContactInfo);

			// / Modified by guofeiyao 2015/12/07
			// Restore original code
		    //String location = getLocationForCallHW(mPrimaryContactInfo);
		    String location = getLocationForCall(mPrimaryContactInfo);
			//Log.e("duanze", location);
            // / End
			
            boolean nameIsNumber = name != null && name.equals(mPrimaryContactInfo.number);
			//Log.d(TAG,"name==:"+name);
			//Log.d(TAG,"number==:"+number);
			//Log.d(TAG,"nameIsNumber==:"+nameIsNumber);
            ui.setPrimary(
                    number,
                    name,
                    nameIsNumber,
                    location,
                    mPrimaryContactInfo.photo,
                    mPrimaryContactInfo.isSipCall);
				  int slotId = SubscriptionManager.getSlotId(mPrimary.getSubId());
				  Settings.System.putInt(mContext.getContentResolver(), "slot_id", slotId);
				   //add by zhangjinqiang for get recordName--start
				   recordName = name+"_"+number;
				    //add by zjq end
        } else {
            // Clear the primary display info.
            ui.setPrimary(null, null, false, null, null, false);
        }

        // / M: Add for plugin. @{
        if (mPrimary != null) {
            ExtensionManager.getCallCardExt().updatePrimaryDisplayInfo(mPrimary.getTelecommCall());
        }
        InCallTrace.end("callcard_updateprimaryinfo");
    }

    private void updateSecondaryDisplayInfo() {
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        if (mSecondary == null) {
            // Clear the secondary display info.
            ui.setSecondary(false, null, false, null, null, false /* isConference */,
                    false /* For MTK DSDA feature */);
            return;
        }

        /// M: Added for DSDA feature. @{
        final boolean isIncoming = Call.State.isIncoming(mSecondary.getState());
        /// @}
        
        // M: add for OP09 plug in @{
        ExtensionManager.getCallCardExt().setPhoneAccountForSecondCall(getAccountForCall(mSecondary));
        // add for OP09 plug in @}
        if (mSecondary.isConferenceCall()) {
            ui.setSecondary(
                    true /* show */,
                    getConferenceString(mSecondary),
                    false /* nameIsNumber */,
                    null /* label */,
                    getCallProviderLabel(mSecondary),
                    true /* isConference */,
                    isIncoming /* For MTK DSDA feature */);
        } else if (mSecondaryContactInfo != null) {
            Log.d(TAG, "updateSecondaryDisplayInfo() " + mSecondaryContactInfo);
            String name = getNameForCallHW(mSecondaryContactInfo);
			Log.d(TAG,"mSecondaryContactInfo--"+name);
			String number = getNumberForCall(mSecondaryContactInfo);
            boolean nameIsNumber = name != null && name.equals(mSecondaryContactInfo.number);
            ui.setSecondary(
                    true /* show */,
                    name,
                    nameIsNumber,
                    /*mSecondaryContactInfo.label*/number,
                    getCallProviderLabel(mSecondary),
                    false /* isConference */,
                    isIncoming /* For MTK DSDA feature */);

            /// Fix ALPS01768230. @{
            ui.setSecondaryEnabled(true);
        } else {
            ui.setSecondaryEnabled(false);
            /// @}

            // Clear the secondary display info.
            ui.setSecondary(false, null, false, null, null, false /* isConference */,
                    isIncoming /* For MTK DSDA feature */);
        }
    }

    private void updateThirdDisplayInfo(boolean isConference) {

        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }
        if (mThird == null) {
            // Clear the third display info.
            ui.setThird(false, null, false, null, null, false /* isConference */);
            return;
        }
        
        // M:add for op09 plug in @{
        ExtensionManager.getCallCardExt().setPhoneAccountForThirdCall(getAccountForCall(mThird));
        // add for op09 plug in @}

        if (isConference) {
            ui.setThird(true /* show */, 
                    getConferenceString(mThird), 
                    false /* nameIsNumber */,
                    null /* label */, 
                    getCallProviderLabel(mThird),
                    true /* isConference */);
        } else if (mThirdContactInfo != null) {
            Log.d(TAG, "updateThirdDisplayInfo() " + mThirdContactInfo);
            final String nameForCall = getNameForCallHW(mThirdContactInfo);

            final boolean nameIsNumber = nameForCall != null
                    && nameForCall.equals(mThirdContactInfo.number);
			//add by zhangjinqiang for HQ01340645--start
			final String number = getNumberForCall(mThirdContactInfo);
			//add by zjq end
            ui.setThird(true /* show */, nameForCall, nameIsNumber, number/*mThirdContactInfo.label*/,
                    getCallProviderLabel(mThird), false/* isConference */);
            /// Fix ALPS01768230. @{
            ui.setThirdEnabled(true);
        } else {
            ui.setThirdEnabled(false);
            /// @}
            // reset to nothing so that it starts off blank next time we use it.
            ui.setThird(false, null, false, null, null, false/* isConference */);
        }
    }

    /**
     * Gets the phone account to display for a call.
     */
    private PhoneAccount getAccountForCall(Call call) {
    	   if(call==null){
			return null;
	   }
        PhoneAccountHandle accountHandle = call.getAccountHandle();
        if (accountHandle == null) {
			//modify by zjq
			if(call.getTelecommCall().getChildren().size()>1){
				android.telecom.Call child = call.getTelecommCall().getChildren().get(0);
				if(child.getDetails()!=null){
                		accountHandle = child.getDetails().getAccountHandle();
				}else{
					return null;
				}
			}else{
				return null;
			}
			//end
        }
		return InCallPresenter.getInstance().getTelecomManager().getPhoneAccount(accountHandle);
    }

    /**
     * Returns the gateway number for any existing outgoing call.
     */
    private String getGatewayNumber() {
        if (hasOutgoingGatewayCall()) {
            return getNumberFromHandle(mPrimary.getGatewayInfo().getGatewayAddress());
        }
        return null;
    }

    /**
     * Return the string label to represent the call provider
     */
    private String getCallProviderLabel(Call call) {
        PhoneAccount account = getAccountForCall(call);
        TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
        if (account != null && !TextUtils.isEmpty(account.getLabel())
                && mgr.hasMultipleCallCapableAccounts()) {
            return account.getLabel().toString();
        }
        return null;
    }

/**add by liruihong for HQ01440479*/
    private String getShortDescription() {
        PhoneAccount account = getAccountForCall(mPrimary);
        TelecomManager mgr = InCallPresenter.getInstance().getTelecomManager();
        if (account != null && !TextUtils.isEmpty(account.getShortDescription())
                && mgr.hasMultipleCallCapableAccounts()) {
            return account.getShortDescription().toString();
        }
        return null;
    }


    /**
     * Returns the label (line of text above the number/name) for any given call.
     * For example, "calling via [Account/Google Voice]" for outgoing calls.
     */
    private String getConnectionLabel() {
        // M: add for OP09 plug in. @{
        String label = ExtensionManager.getCallCardExt().getCallProviderLabel(mContext, 
                getAccountForCall(mPrimary));
        if (label != null) {
            return label;
        }
        // @}
        StatusHints statusHints = mPrimary.getTelecommCall().getDetails().getStatusHints();
        if (statusHints != null && !TextUtils.isEmpty(statusHints.getLabel())) {
            return statusHints.getLabel().toString();
        }

        if (hasOutgoingGatewayCall() && getUi() != null) {
            // Return the label for the gateway app on outgoing calls.
            final PackageManager pm = mContext.getPackageManager();
            try {
                ApplicationInfo info = pm.getApplicationInfo(
                        mPrimary.getGatewayInfo().getGatewayProviderPackageName(), 0);
                return pm.getApplicationLabel(info).toString();
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(this, "Gateway Application Not Found.", e);
                return null;
            }
        }
        return getCallProviderLabel(mPrimary);
    }

    private Drawable getCallStateIcon() {
        // M: add for OP09 Plug in.@{
        Drawable iconEx = ExtensionManager.getCallCardExt().getCallProviderIcon(mContext, 
                getAccountForCall(mPrimary));
        if (iconEx != null) {
            return iconEx;
        }
        // @}
        // Return connection icon if one exists.
        StatusHints statusHints = mPrimary.getTelecommCall().getDetails().getStatusHints();
        if (statusHints != null && statusHints.getIconResId() != 0) {
            Drawable icon = statusHints.getIcon(mContext);
            if (icon != null) {
                return icon;
            }
        }

        // Return high definition audio icon if the capability is indicated.
        if ((mPrimary.getTelecommCall().getDetails().can(
                android.telecom.Call.Details.CAPABILITY_HIGH_DEF_AUDIO)
                && mPrimary.getState() == Call.State.ACTIVE)
                /// M: [VoLTE] show HD icon when VoLTE @{
                || mPrimary.getTelecommCall().getDetails().can(
                        android.telecom.Call.Details.CAPABILITY_VOLTE)
                /// M : WFC <Show hd icon for WFC call also>@{        
                || mPrimary.getTelecommCall().getDetails().can(
                        android.telecom.Call.Details.CAPABILITY_VoWIFI)) {
                /// @}
            return mContext.getResources().getDrawable(R.drawable.ic_hd_audio);
        }

        return null;
    }

    private boolean hasOutgoingGatewayCall() {
        // We only display the gateway information while STATE_DIALING so return false for any othe
        // call state.
        // TODO: mPrimary can be null because this is called from updatePrimaryDisplayInfo which
        // is also called after a contact search completes (call is not present yet).  Split the
        // UI update so it can receive independent updates.
        if (mPrimary == null) {
            return false;
        }
        return Call.State.isDialing(mPrimary.getState()) && mPrimary.getGatewayInfo() != null &&
                !mPrimary.getGatewayInfo().isEmpty();
    }

    /**
     * Gets the name to display for the call.
     */
    private static String getNameForCall(ContactCacheEntry contactInfo) {
    		/*modify by zhangjinqiang 
        if (TextUtils.isEmpty(contactInfo.name)) {
            return contactInfo.number;
        }
        */
        return contactInfo.name;
    }

	//add by zhangjinqiang for al812--start
    private  String getNameForCallHW(ContactCacheEntry contactInfo) {
        if(!TextUtils.isEmpty(contactInfo.name)){
			return contactInfo.name;
		}
				
		InCallActivity inCallActivity = (InCallActivity)mContext;
		csom = inCallActivity.getCooTekSDK();
        CallerIdResult  mCallResult = csom.getCallerIdResult(contactInfo.number,CallerIdStrategy.OFFLINE_ONLY);
		if(mCallResult!=null&&mCallResult.getName()!=null){
			return mCallResult.getName();
		}else{
			return null;
		}
    }
	//add by zjq end

	//add by zhangjinqiang for phone location  start
	private static String getLocationForCall(ContactCacheEntry contactInfo){
		//Log.d("zjqContactCacheEntry",contactInfo.location);
		return contactInfo.location;
	}

	private  String getLocationForCallHW(ContactCacheEntry contactInfo){
		//add by zhangjinqiang for al812--start
		InCallActivity inCallActivity = (InCallActivity)mContext;
		csom = inCallActivity.getCooTekSDK();
		//add by zjq end
		//Log.d("zjqContactCacheEntry",contactInfo.number);
		return csom.getPhoneAttribute(contactInfo.number);
	}
	//add by zhangjinqiang end

    /**
     * Gets the number to display for a call.
     */
    private static String getNumberForCall(ContactCacheEntry contactInfo) {
        // If the name is empty, we use the number for the name...so dont show a second
        // number in the number field
        /*modify by zhangjinqiang
        if (TextUtils.isEmpty(contactInfo.name)) {
            return contactInfo.location;
        }*/
        return contactInfo.number;
    }

    public void secondaryInfoClicked() {
        if (mSecondary == null) {
            Log.w(this, "Secondary info clicked but no secondary call.");
            return;
        }

        Log.i(this, "Swapping call to foreground: " + mSecondary);
        if (Call.State.isIncoming(mSecondary.getState())) {
            CallList.getInstance().switchIncomingCalls();
        } else {
            TelecomAdapter.getInstance().unholdCall(mSecondary.getId());
			final CallCardUi ui = getUi();
			int slotId = SubscriptionManager.getSlotId(mSecondary.getSubId());
			ui.updateSimLable(slotId);
        }
    }

    public void thirdInfoClicked() {
        if (mThird == null) {
            Log.wtf(this, "Third info clicked but no third call.");
            return;
        }

        Log.i(this, "Swapping call to foreground: " + mThird);
        TelecomAdapter.getInstance().unholdCall(mThird.getId());
	    final CallCardUi ui = getUi();
		int slotId = SubscriptionManager.getSlotId(mThird.getSubId());
		ui.updateSimLable(slotId);
    }

    public void endCallClicked() {
        if (mPrimary == null) {
            return;
        }

        Log.i(this, "Disconnecting call: " + mPrimary);
        mPrimary.setState(Call.State.DISCONNECTING);
        /// M: For ALPS01965644, fix mPrimary maybe modified between
        // CallList.onUpdate() and TelecomAdapter.disconnectCall(id) @{
        /*
         * Google code:
         * TelecomAdapter.getInstance().disconnectCall(mPrimary.getId());
         */
        String id = mPrimary.getId();
        CallList.getInstance().onUpdate(mPrimary);
        TelecomAdapter.getInstance().disconnectCall(id);
        /// @}
    }

    private String getNumberFromHandle(Uri handle) {
        return handle == null ? "" : handle.getSchemeSpecificPart();
    }

    /**
     * Handles a change to the full screen video state.
     *
     * @param isFullScreenVideo {@code True} if the application is entering full screen video mode.
     */
    @Override
    public void onFullScreenVideoStateChanged(boolean isFullScreenVideo) {
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }
        ui.setCallCardVisible(!isFullScreenVideo);
    }

    private String getConferenceString(Call call) {
        boolean isGenericConference = call.can(
                android.telecom.Call.Details.CAPABILITY_GENERIC_CONFERENCE);
        Log.v(this, "getConferenceString: " + isGenericConference);
		//modify by zhangjinqiang for HQ01379143 --start
        final int resId = isGenericConference
                ? R.string.card_title_conf_call:R.string.card_title_in_call ;
		//modify by zjq end 
        return mContext.getResources().getString(resId);
    }

    private Drawable getConferencePhoto(Call call) {
        boolean isGenericConference = call.can(
                android.telecom.Call.Details.CAPABILITY_GENERIC_CONFERENCE);
        Log.v(this, "getConferencePhoto: " + isGenericConference);

        final int resId = isGenericConference
                ? R.drawable.img_phone : R.drawable.img_conference;
        Drawable photo = mContext.getResources().getDrawable(resId);
        photo.setAutoMirrored(true);
        return photo;
    }

    public interface CallCardUi extends Ui {
        void setVisible(boolean on);
        void setCallCardVisible(boolean visible);
        void setPrimary(String number, String name, boolean nameIsNumber, String label,
                Drawable photo, boolean isSipCall);
        /// M: Modified for MTK DSDA feature. @{
        /* Google code:
        void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
                String providerLabel, boolean isConference);
        */
        void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
                String providerLabel, boolean isConference, boolean isIncoming);
        void setThird(boolean show, String name, boolean nameIsNumber, String label,
                String providerLabel, boolean isConference);
        /// @}
        void setCallState(int state, int videoState, int sessionModificationState,
                DisconnectCause disconnectCause, String connectionLabel,String shortDescription,
                Drawable connectionIcon, String gatewayNumber);//modify by liruihong for HQ01440479
        void setPrimaryCallElapsedTime(boolean show, long duration);
        void setPrimaryName(String name, boolean nameIsNumber);
        void setPrimaryImage(Drawable image);
        void setPrimaryPhoneNumber(String phoneNumber);
        void setPrimaryLabel(String label);
        void setEndCallButtonEnabled(boolean enabled, boolean animate);
        void setCallbackNumber(String number, boolean isEmergencyCalls);
        void setPhotoVisible(boolean isVisible);
        void setProgressSpinnerVisible(boolean visible);
        void showManageConferenceCallButton(boolean visible);
        boolean isManageConferenceVisible();
        /// M: @{
        // Add for recording
        void updateVoiceRecordIcon(boolean show);
        // ALPS01759672.
        void setSecondaryEnabled(boolean enable);
        void setThirdEnabled(boolean enable);
        /// @}
        /// M: for ALPS01945830, update primarycall and callbutton background color.@{
        void updateColors();
		void showCallTime();
		void showCircle();
		void updateSimLable(int num);
        /// @}
    }

    // -----------------------------Medaitek---------------------------------------

    @Override
    public void onUpdateRecordState(int state, int customValue) {
        if (FeatureOptionWrapper.isSupportPhoneVoiceRecording()) {
            updateVoiceCallRecordState();
        }
    }

    private void updateVoiceCallRecordState() {
        Log.d(this, "[updateVoiceCallRecordState]...");
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }
        Call ringCall = null;
        int ringCallState = -1;
        ringCall = CallList.getInstance().getIncomingCall();
        if (null != ringCall) {
            ringCallState = ringCall.getState();
        }
        if ((InCallPresenter.getInstance().isRecording()) && (ringCallState != Call.State.INCOMING)
                && (ringCallState != Call.State.CALL_WAITING)) {
            ui.updateVoiceRecordIcon(true);
        } else if ((!InCallPresenter.getInstance().isRecording())
                || (ringCallState == Call.State.INCOMING)
                || (ringCallState == Call.State.CALL_WAITING)) {
            ui.updateVoiceRecordIcon(false);
        }
    }

    /**
     * M: Maybe after querying complete, primary call or secondary call has be changed.
     * So, check the finished querying callId is what kind of call.
     * @param callId
     * @return
     */
    private CallEnum reCalculateContactInfoType(String callId) {
        CallEnum callEnum;
        if(mPrimary != null && mPrimary.getId() == callId) {
            callEnum = CallEnum.PRIMARY;
        } else if(mSecondary != null && mSecondary.getId() == callId) {
            callEnum = CallEnum.SECONDARY;
        } else if(mThird != null && mThird.getId() == callId) {
            callEnum = CallEnum.THIRD;
        } else {
            callEnum = CallEnum.NULL;
        }
        Log.d(this, "reCalculateContactInfoType... callId =, callType = " + callId, callEnum);
        return callEnum;
    }

    private Call getThirdCallToDisplay(CallList callList, Call ignore1, Call ignore2) {

        // Then we go to background call (calls on hold)
        Call retval = callList.getBackgroundCall();
        if (retval != null && retval != ignore1 && retval != ignore2) {
            return retval;
        }

        retval = callList.getSecondBackgroundCall();
        if (retval != ignore1 && retval != ignore2) {
            return retval;
        }

        return null;
    }

    /// M: For volte @{
    /**
     * listner onContactInfoUpdated(),
     * will be notified when ContactInfoCache finish re-query, triggered by some call's number change.
     */
    private final ContactInfoUpdatedListener mContactInfoUpdatedListener = new ContactInfoUpdatedListener() {
        public void onContactInfoUpdated(String callId) {
            handleContactInfoUpdated(callId);
        }
    };

    /**
     * trigger UI update for call when the call changes to call waiting state.
     * @param call
     */
    private void handleIsCallWaitingChanged(Call call) {
        Log.d(this, "handleIsCallWaitingChanged()... call = " + call);
        // only trigger refresh UI when the primary call changed to be call waiting,
        // when mPrimaryContactInfo == null, skip; when mPrimaryContactInfo becomes non-null, will trigger it.
        if (call != null && mPrimary != null && call.getId() == mPrimary.getId()) {
            if (mPrimaryContactInfo != null) {
                Log.d(this, "handleIsCallWaitingChanged()... trigger UI refresh.");
                // TODO: maybe we can re-use google default follow,
                // onDetailsChanged() in in this class will trigger below function also.
                updatePrimaryCallState();
            }
        }
    }

    /**
     * ask for new ContactInfo to update UI when re-query complete by ContactInfoCache.
     */
    private void handleContactInfoUpdated(String callId) {
        Log.d(this, "handleContactInfoUpdated()... callId = " + callId);
        Call call = null;
        CallEnum callEnum;
        if(mPrimary != null && mPrimary.getId() == callId) {
            callEnum = CallEnum.PRIMARY;
            call = mPrimary;
        } else if(mSecondary != null && mSecondary.getId() == callId) {
            callEnum = CallEnum.SECONDARY;
            call = mSecondary;
        } else if(mThird != null && mThird.getId() == callId) {
            callEnum = CallEnum.THIRD;
            call = mThird;
        }
        if(call != null) {
            startContactInfoSearch(call, CallEnum.PRIMARY, call.getState() == Call.State.INCOMING);
        }
    }
    /// @}

    /// M: For second/third call color @{
    public int getSecondCallColor() {
        return InCallPresenter.getInstance().getPrimaryColorFromCall(mSecondary);
    }

    public int getThirdCallColor() {
        return InCallPresenter.getInstance().getPrimaryColorFromCall(mThird);
    }
    /// @}

    private boolean isIncomingVolteConference(Call call) {
        return call != null
                && Call.State.isIncoming(call.getState())
                && call.isConferenceCall()
                && call.can(android.telecom.Call.Details.CAPABILITY_VOLTE);
    }

    private void setPrimaryForIncomingVolteConference() {
        if (mPrimaryContactInfo == null) {
            Log.d(this, "[setPrimaryForIncomingVolteConference]no contact info");
            getUi().setPrimary(null, null, false, null, null, false);
            return;
        }
        String name = getNameForCall(mPrimaryContactInfo);
        String number = getNumberForCall(mPrimaryContactInfo);

            // Restore original code
		    //String location = getLocationForCallHW(mPrimaryContactInfo);
		    String location = getLocationForCall(mPrimaryContactInfo);
			//Log.e("duanze", location);
            // / End
		
        boolean nameIsNumber = name != null && name.equals(mPrimaryContactInfo.number);
        getUi().setPrimary(
                number,
                name,
                nameIsNumber,
                //mPrimaryContactInfo.label,
                location,
                getConferencePhoto(mPrimary),
                false/*isSip*/);
    }

    private boolean needUpdatePrimaryForVolte(
            InCallState oldState, InCallState newState, Call call) {
        return call != null &&
                call.isConferenceCall() &&
                oldState == InCallState.INCOMING &&
                newState != InCallState.INCOMING;
    }

    /// M: ALPS02217975 previously disconnected call screen for cdma is shown again@{
    /*
     * get cdma conference call from callist.
     * @param callList
     */
    private Call getDisconnectedCdmaConfCall(CallList callList) {
        Call cdmaConfCall = callList.getCdmaConfCall();
        if (cdmaConfCall != null  && cdmaConfCall.getState() == Call.State.DISCONNECTED) {
            return cdmaConfCall;
        }
        return null;
    }
    /// @}
}
