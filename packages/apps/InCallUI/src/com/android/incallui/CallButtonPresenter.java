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

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.AudioState;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.PhoneRecorderListener;

import java.util.Objects;
import com.mediatek.incallui.InCallUtils;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener, IncomingCallListener,
        InCallDetailsListener, CanAddCallListener, PhoneRecorderListener {

    private static final String KEY_AUTOMATICALLY_MUTED = "incall_key_automatically_muted";
    private static final String KEY_PREVIOUS_MUTE_STATE = "incall_key_previous_mute_state";

    private Call mCall;
    private Call mPrimary;
    private Call mSecondary;
    private boolean mIsSecondaryCallExist = false;
    private boolean mAutomaticallyMuted = false;
    private boolean mPreviousMuteState = false;
    private CallTimer mCallTimer;//add by zhangjinqiang
    private static final long RECORD_TIME_UPDATE_INTERVAL_MS = 1000;//ADD by zjq

    public CallButtonPresenter() {
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);

        AudioModeProvider.getInstance().addListener(this);

        // register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addCanAddCallListener(this);
        InCallPresenter.getInstance().addPhoneRecorderListener(this);
        ui.configRecordingMenuItemTitle();
        /* begin:add by donghongjing for HQ01432460 */
        final CallList calls = CallList.getInstance();
        if (calls != null) {
            InCallState state = InCallPresenter.getPotentialStateFromCallList(calls);
            onStateChange(state, state, calls);
        }
        /* end:add by donghongjing for HQ01432460 */
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        AudioModeProvider.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        /// M: ALPS01828853. @{
        // should remove listener when ui unready.
        InCallPresenter.getInstance().removePhoneRecorderListener(this);
        /// @}
    }

    /* begin:add by donghongjing for HQ01432460 */
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
    /* end:add by donghongjing for HQ01432460 */

    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        CallButtonUi ui = getUi();

        /* begin:add by donghongjing for HQ01432460 */
        Call primary = null;
        Call secondary = null;

        if (newState == InCallState.INCOMING) {
            primary = callList.getIncomingCall();
            secondary = callList.getSecondaryIncomingCall();
        } else if (newState == InCallState.PENDING_OUTGOING || newState == InCallState.OUTGOING) {
            primary = callList.getOutgoingCall();
            if (primary == null) {
                primary = callList.getPendingOutgoingCall();
            }

            // getCallToDisplay doesn't go through outgoing or incoming calls. It will return the
            // highest priority call to display as the secondary call.
            secondary = getCallToDisplay(callList, null, true);
        } else if (newState == InCallState.INCALL) {
            primary = getCallToDisplay(callList, null, false);
            secondary = getCallToDisplay(callList, primary, true);
        }

        Log.d(this, "Primary call: " + primary);
        Log.d(this, "Secondary call: " + secondary);

        mSecondary = secondary;
        mPrimary = primary;
        if (mPrimary != null && mSecondary != null) {
            mIsSecondaryCallExist = true;
        } else {
            mIsSecondaryCallExist = false;
        }
        /* end:add by donghongjing for HQ01432460 */

        if (newState == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
            /// M: For ALPS01940714, force set mute false if emergency call. @{
            if (isEmergencyCall(mCall)) {
                /* begin:change by donghongjing for HQ01450439 */
                fireMuteClicked(false);
                /* end:change by donghongjing for HQ01450439 */
            }
            /// @}
        } else if (newState == InCallState.INCALL) {
            mCall = callList.getActiveOrBackgroundCall();

            // When connected to voice mail, automatically shows the dialpad.
            // (On previous releases we showed it when in-call shows up, before waiting for
            // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
            // the dialpad too.)
            if (ui != null) {
                if (oldState == InCallState.OUTGOING && mCall != null) {
                    if (CallerInfoUtils.isVoiceMailNumber(ui.getContext(), mCall)) {
                        ui.displayDialpad(false /* show */, true /* animate */);
                    }
                }
            }
        } else if (newState == InCallState.INCOMING) {
            if (ui != null) {
                ui.displayDialpad(false /* show */, true /* animate */);
            }
            mCall = null;
        } else {
            mCall = null;
        }
        // / M: When a incoming call is disconnected by remote and popup menu is
        // shown, we need dismiss the popup menu. @{
        if (oldState == InCallState.INCOMING && oldState != newState && ui != null) {
            ui.dismissPopupMenu();
        }
        /// @}
        updateUi(newState, mCall);

        /// M: Plug-in. @{
        ExtensionManager.getRCSeCallButtonExt().onStateChange(mCall != null ? mCall.getTelecommCall() : null,
                callList.getCallMap());
        /// @}
        
		//add by zhangjinqiang for hide record time--start
		updateVoiceCallRecordState();
		//add by zjq end 
    }

    /**
     * Updates the user interface in response to a change in the details of a call.
     * Currently handles changes to the call buttons in response to a change in the details for a
     * call.  This is important to ensure changes to the active call are reflected in the available
     * buttons.
     *
     * @param call The active call.
     * @param details The call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        if (getUi() != null && Objects.equals(call, mCall)) {
            updateCallButtons(call, getUi().getContext());
        }
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        /// M: for ALPS01749269 @{
        // dismiss all pop up menu when a new call incoming
        getUi().dismissPopupMenu();
        /// @}

        onStateChange(oldState, newState, CallList.getInstance());
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
        if (getUi() != null && mCall != null) {
            updateCallButtons(mCall, getUi().getContext());
        }
    }

    @Override
    public void onAudioMode(int mode) {
        if (getUi() != null) {
            getUi().setAudio(mode);
        }
    }

    @Override
    public void onSupportedAudioMode(int mask) {
        if (getUi() != null) {
            getUi().setSupportedAudio(mask);
        }
    }

    @Override
    public void onMute(boolean muted) {
        if (getUi() != null && !mAutomaticallyMuted) {
            getUi().setMute(muted);
        }
    }

    public int getAudioMode() {
        return AudioModeProvider.getInstance().getAudioMode();
    }

    public int getSupportedAudio() {
        return AudioModeProvider.getInstance().getSupportedModes();
    }

    public void setAudioMode(int mode) {

        // TODO: Set a intermediate state in this presenter until we get
        // an update for onAudioMode().  This will make UI response immediate
        // if it turns out to be slow

        Log.d(this, "Sending new Audio Mode: " + AudioState.audioRouteToString(mode));
        TelecomAdapter.getInstance().setAudioRoute(mode);
    }

    /**
     * Function assumes that bluetooth is not supported.
     */
    public void toggleSpeakerphone() {
        // this function should not be called if bluetooth is available
        if (0 != (AudioState.ROUTE_BLUETOOTH & getSupportedAudio())) {

            // It's clear the UI is wrong, so update the supported mode once again.
            Log.e(this, "toggling speakerphone not allowed when bluetooth supported.");
            if(null != getUi()) {
            	getUi().setSupportedAudio(getSupportedAudio());
            }
            return;
        }

        int newMode = AudioState.ROUTE_SPEAKER;

        // if speakerphone is already on, change to wired/earpiece
        if (getAudioMode() == AudioState.ROUTE_SPEAKER) {
            newMode = AudioState.ROUTE_WIRED_OR_EARPIECE;
            if(null != getUi()) {
            	getUi().setAudioModel(false);
            }
        }else{
        	if(null != getUi()) {
				getUi().setAudioModel(true);
        	}
		}

        setAudioMode(newMode);
    }

    /* begin:change by donghongjing for HQ01450439 */
    public void fireMuteClicked(boolean checked) {
        Log.d(this, "fireMuteClicked turning on mute: " + checked);
        TelecomAdapter.getInstance().mute(checked);
    }

    public void muteClicked(boolean checked) {
        Log.d(this, "muteClicked turning on mute: " + checked);
        TelecomAdapter.getInstance().mute(checked);
        if (checked) {
            AudioModeProvider.getInstance().addMutedCall((mCall == null) ? null : mCall.getId());
        } else {
            AudioModeProvider.getInstance().removeMutedCall((mCall == null) ? null : mCall.getId());
        }

    }
    /* end:change by donghongjing for HQ01450439 */

    public void holdClicked(boolean checked) {
        if (mCall == null) {
            return;
        }
        if (checked) {
            Log.i(this, "Putting the call on hold: " + mCall);
            TelecomAdapter.getInstance().holdCall(mCall.getId());
        } else {
            Log.i(this, "Removing the call from hold: " + mCall);
            TelecomAdapter.getInstance().unholdCall(mCall.getId());
        }
    }

    /* begin:add by donghongjing for HQ01432460 */
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
        }
    }
    /* end:add by donghongjing for HQ01432460 */

    public void swapClicked() {
        if (mCall == null) {
            return;
        }

        /* begin:add by donghongjing for HQ01432460 */
        if (mIsSecondaryCallExist) {
            secondaryInfoClicked();
            return;
        }
        /* end:add by donghongjing for HQ01432460 */
        Log.i(this, "Swapping the call: " + mCall);
        TelecomAdapter.getInstance().swap(mCall.getId());
    }

    public void mergeClicked() {
        TelecomAdapter.getInstance().merge(mCall.getId());
    }

	//add by zhangjinqiang for al812 start
	public void contactsClicked() {
		TelecomAdapter.getInstance().addContacts();
	}
	//add by zhangjinqiang for al812 end
	
    public void addCallClicked() {
        // Automatically mute the current call
        mAutomaticallyMuted = true;
        mPreviousMuteState = AudioModeProvider.getInstance().getMute();
        // Simulate a click on the mute button
        /* begin:change by donghongjing for HQ01450439 */
        fireMuteClicked(true);
        /* end:change by donghongjing for HQ01450439 */
        TelecomAdapter.getInstance().addCall();
    }

    public void changeToVoiceClicked() {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        VideoProfile videoProfile = new VideoProfile(
                VideoProfile.VideoState.AUDIO_ONLY, VideoProfile.QUALITY_DEFAULT);
        videoCall.sendSessionModifyRequest(videoProfile);
    }

    public void showDialpadClicked(boolean checked) {
        Log.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked /* show */, true /* animate */);
    }

    public void changeToVideoClicked() {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        VideoProfile videoProfile =
                new VideoProfile(VideoProfile.VideoState.BIDIRECTIONAL);
        videoCall.sendSessionModifyRequest(videoProfile);

        mCall.setSessionModificationState(Call.SessionModificationState.REQUEST_FAILED);
    }

    /**
     * Switches the camera between the front-facing and back-facing camera.
     * @param useFrontFacingCamera True if we should switch to using the front-facing camera, or
     *     false if we should switch to using the back-facing camera.
     */
    public void switchCameraClicked(boolean useFrontFacingCamera) {
        InCallCameraManager cameraManager = InCallPresenter.getInstance().getInCallCameraManager();
        cameraManager.setUseFrontFacingCamera(useFrontFacingCamera);

        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        String cameraId = cameraManager.getActiveCameraId();
        if (cameraId != null) {
            videoCall.setCamera(cameraId);
            videoCall.requestCameraCapabilities();
        }
        getUi().setSwitchCameraButton(!useFrontFacingCamera);
    }

    /**
     * Stop or start client's video transmission.
     * @param pause True if pausing the local user's video, or false if starting the local user's
     *    video.
     */
    public void pauseVideoClicked(boolean pause) {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        if (pause) {
            videoCall.setCamera(null);
            VideoProfile videoProfile = new VideoProfile(
                    mCall.getVideoState() | VideoProfile.VideoState.PAUSED);
            videoCall.sendSessionModifyRequest(videoProfile);
        } else {
            InCallCameraManager cameraManager = InCallPresenter.getInstance().
                    getInCallCameraManager();
            videoCall.setCamera(cameraManager.getActiveCameraId());
            VideoProfile videoProfile = new VideoProfile(
                    mCall.getVideoState() & ~VideoProfile.VideoState.PAUSED);
            videoCall.sendSessionModifyRequest(videoProfile);
        }
        getUi().setPauseVideoButton(pause);
    }

    private void updateUi(InCallState state, Call call) {
        Log.d(this, "Updating call UI for call: ", call);
        /// M: DMLock @{
        if (InCallUtils.isDMLocked()) {
            updateInCallControlsDuringDMLocked(call);
            return;
        }
        /// @}

        final CallButtonUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isEnabled =
                state.isConnectingOrConnected() &&!state.isIncoming() && call != null;
        ui.setEnabled(isEnabled);

        /// M: Added for FTA case: When there has one active call and a
        // incoming call which can be answered, user can select hangup this call
        // using related menu which shown in the overflow menu. @{
        updateOverflowButtonForIncoming(state);
        /// @}

        /// M: for ALPS01945830. Redraw callbuttons. @{
        ui.updateColors();
        /// @}

        if (!isEnabled) {
            return;
        }

        updateCallButtons(call, ui.getContext());

        ui.enableMute(call.can(android.telecom.Call.Details.CAPABILITY_MUTE));
    }

    /**
     * Updates the buttons applicable for the UI.
     *
     * @param call The active call.
     * @param context The context.
     */
    private void updateCallButtons(Call call, Context context) {
        if (call.isVideoCall(context)) {
            updateVideoCallButtons(call);
        } else {
            updateVoiceCallButtons(call);
        }
    }

    private void updateVideoCallButtons(Call call) {
        Log.v(this, "Showing buttons for video call.");
        final CallButtonUi ui = getUi();

        // Hide all voice-call-related buttons.
        ui.showAudioButton(false);
        ui.showDialpadButton(false);
        ui.showHoldButton(false);
        ui.showSwapButton(false);
        ui.showChangeToVideoButton(false);
        ui.showAddCallButton(false);
        ui.showMergeButton(false);
        ui.showOverflowButton(false);

        // Show all video-call-related buttons.
        ui.showChangeToVoiceButton(true);
        ui.showSwitchCameraButton(true);
        ui.showPauseVideoButton(true);

        final boolean supportHold = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD);
        final boolean enableHoldOption = call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
        ui.showHoldButton(supportHold);
        ui.enableHold(enableHoldOption);
        ui.setHold(call.getState() == Call.State.ONHOLD);
    }

    private void updateVoiceCallButtons(Call call) {
        Log.v(this, "Showing buttons for voice call.");
        final CallButtonUi ui = getUi();

        // Hide all video-call-related buttons.
        ui.showChangeToVoiceButton(false);
        ui.showSwitchCameraButton(false);
        ui.showPauseVideoButton(false);

        // Show all voice-call-related buttons.
        ui.showAudioButton(true);
        ui.showDialpadButton(true);

        Log.v(this, "Show hold ", call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD));
        Log.v(this, "Enable hold", call.can(android.telecom.Call.Details.CAPABILITY_HOLD));
        Log.v(this, "Show merge ", call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE));
        Log.v(this, "Show swap ", call.can(
                android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE));
        Log.v(this, "Show add call ", TelecomAdapter.getInstance().canAddCall());
        Log.v(this, "Show mute ", call.can(android.telecom.Call.Details.CAPABILITY_MUTE));

        final boolean canAdd = TelecomAdapter.getInstance().canAddCall();
        /// M: Enable hold button when call support HOLD or UNHOLD.
        /* Google code:
        final boolean enableHoldOption = call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
        */
        final boolean enableHoldOption = call.can(android.telecom.Call.Details.CAPABILITY_HOLD)
                || call.can(android.telecom.Call.Details.CAPABILITY_UNHOLD);
        /// @}
        final boolean supportHold = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD);
        final boolean isCallOnHold = call.getState() == Call.State.ONHOLD;

        boolean canVideoCall = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL)
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE);
        ui.showChangeToVideoButton(canVideoCall);
        ui.enableChangeToVideoButton(!isCallOnHold);

        final boolean showMergeOption = call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
        final boolean showAddCallOption = canAdd;

        // Show either HOLD or SWAP, but not both. If neither HOLD or SWAP is available:
        //     (1) If the device normally can hold, show HOLD in a disabled state.
        //     (2) If the device doesn't have the concept of hold/swap, remove the button.
        /* begin:change by donghongjing for HQ01432460 add mIsSecondaryCallExist */
        final boolean showSwapOption = mIsSecondaryCallExist || call.can(
                android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);
        /* end:change by donghongjing for HQ01432460 */
        final boolean showHoldOption = !showSwapOption && (enableHoldOption || supportHold);
        final boolean showVoiceRecordOption = call.can(android.telecom.Call.Details.CAPABILITY_VOICE_RECORD);

        ui.setHold(isCallOnHold);
        // If we show video upgrade and add/merge and hold/swap, the overflow menu is needed.
        final boolean isVideoOverflowScenario = canVideoCall
                && (showAddCallOption || showMergeOption) && (showHoldOption || showSwapOption);
        // If we show hold/swap, add, and merge simultaneously, the overflow menu is needed.
        final boolean isOverflowScenario =
                (showHoldOption || showSwapOption) && showMergeOption && showAddCallOption;

        if (isVideoOverflowScenario) {
            ui.showHoldButton(false);
            ui.showSwapButton(false);
            ui.showAddCallButton(false);
            ui.showMergeButton(false);

            ui.configureOverflowMenu(
                    showMergeOption,
                    showAddCallOption /* showAddMenuOption */,
                    showHoldOption && enableHoldOption /* showHoldMenuOption */,
                    showSwapOption,
                    showVoiceRecordOption); /* MTK add this for recording feature.*/
            ui.showOverflowButton(true);
        } else {
            /// M: Modify this for adding recording button. @{
            /* Unuse Google code:
            if (isOverflowScenario) {
                ui.showAddCallButton(false);
                ui.showMergeButton(false);
                ui.configureOverflowMenu(
                        showMergeOption,
                        showAddCallOption,
                        false,
                        false);
            } else {
                ui.showMergeButton(showMergeOption);
                ui.showAddCallButton(showAddCallOption);
            }
            ui.showOverflowButton(isOverflowScenario);
            */
            //ui.showAddCallButton(false);
	   //ui.showHoldButton(false);
            //ui.showMergeButton(false);
            ui.showOverflowButton(true);
            ui.configureOverflowMenu(
                    showMergeOption,
                    showAddCallOption /* showAddMenuOption */,
                    false /* showHoldMenuOption */,
                    false /* showSwapMenuOption */,
                    showVoiceRecordOption);
            /// @}
	   ui.showAddCallButton(showAddCallOption);
	   ui.showRecordButton(showVoiceRecordOption);
	   ui.showMuteButton(showVoiceRecordOption);
			
            ui.showHoldButton(enableHoldOption);
            ui.enableHold(enableHoldOption);
            ui.showSwapButton(showSwapOption);


			
	   ui.showMergeButton(showMergeOption);
        /* begin:add by donghongjing for HQ01450439 */
        refreshMuteState();
        /* begin:add by donghongjing for HQ01450439 */
        }
    }

    /* begin:change by donghongjing for HQ01450439 */
    public void refreshMuteState() {
        // Restore the previous mute state
        /*if (mAutomaticallyMuted &&
                AudioModeProvider.getInstance().getMute() != mPreviousMuteState) {
            if (getUi() == null) {
                return;
            }
            fireMuteClicked(mPreviousMuteState);
        }*/
        /* mPreviousMuteState may has been set for other call */
        boolean isMuted = AudioModeProvider.getInstance().isCallMuted((mCall == null) ? null : mCall.getId());
        if (AudioModeProvider.getInstance().getMute() != isMuted) {
            if (getUi() == null) {
                return;
            }
            fireMuteClicked(isMuted);
        }

        mAutomaticallyMuted = false;
    }
    /* end:change by donghongjing for HQ01450439 */

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_AUTOMATICALLY_MUTED, mAutomaticallyMuted);
        outState.putBoolean(KEY_PREVIOUS_MUTE_STATE, mPreviousMuteState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        mAutomaticallyMuted =
                savedInstanceState.getBoolean(KEY_AUTOMATICALLY_MUTED, mAutomaticallyMuted);
        mPreviousMuteState =
                savedInstanceState.getBoolean(KEY_PREVIOUS_MUTE_STATE, mPreviousMuteState);
        super.onRestoreInstanceState(savedInstanceState);
    }

    public interface CallButtonUi extends Ui {
		//add by zhangjinqiang for al812 start
		//for recordButton 
	void showRecordButton(boolean show);
	void setRecord(boolean on);
	void enableRecord(boolean enabled);

		//for muteButton;
	void showMuteButton(boolean show);
	//add by zhangjinqiang end
        void setEnabled(boolean on);
        void setMute(boolean on);
        void enableMute(boolean enabled);
        void showAudioButton(boolean show);
        void showChangeToVoiceButton(boolean show);
        void showDialpadButton(boolean show);
        void setHold(boolean on);
        void showHoldButton(boolean show);
        void enableHold(boolean enabled);
        void showSwapButton(boolean show);
        void showChangeToVideoButton(boolean show);
        void enableChangeToVideoButton(boolean enable);
        void showSwitchCameraButton(boolean show);
        void setSwitchCameraButton(boolean isBackFacingCamera);
        void showAddCallButton(boolean show);
        void showMergeButton(boolean show);
        void showPauseVideoButton(boolean show);
        void setPauseVideoButton(boolean isPaused);
        void showOverflowButton(boolean show);
        void displayDialpad(boolean on, boolean animate);
        boolean isDialpadVisible();
        void setAudio(int mode);
        void setSupportedAudio(int mask);
        void configureOverflowMenu(boolean showMergeMenuOption, boolean showAddMenuOption,
                boolean showHoldMenuOption, boolean showSwapMenuOption,
                        boolean showVoiceRecordOption);
        /// M: Voice recording
        void configRecordingMenuItemTitle();
        Context getContext();
        /// M: DM Lock should disable add call button
        void enableAddCall(boolean enabled);

        /// M: for ALPS01749269 @{
        // dismiss all pop up menu when a new call incoming
        void dismissPopupMenu();
        /// @}
        /// M: for ALPS01945830. Redraw callbuttons. @{
        void updateColors();
	    void setAudioModel(boolean flag);
		void setRecordTime(boolean isShow,long duration);
        /// @}
    }

    void updateInCallControlsDuringDMLocked(Call call) {
        final CallButtonUi ui = getUi();
        if (ui == null) {
            Log.d(this, "just return ui:" + ui);
            return;
        }
        Context context = ui.getContext();
        if (context == null) {
            Log.d(this, "just return context:" + context);
            return;
        }
        if (call == null) {
            Log.d(this, "just return call:" + call);
            return;
        }
        ui.setEnabled(false);
        ui.showMergeButton(false);
        ui.showAddCallButton(true);
        ui.enableAddCall(false);
        final boolean canHold = call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
        ui.displayDialpad(getUi().isDialpadVisible(), true);
        ui.showHoldButton(canHold);
    }

    //---------------------------------------Mediatek-----------------------------------

    public void voiceRecordClicked() {
    		//add by zhangjinqiang for HQ01396987--start
    		final CallButtonUi ui = getUi();
    		final long recordStart = System.currentTimeMillis();
    		mCallTimer = new CallTimer(new Runnable() {
            @Override
            public void run() {
                  long duration = System.currentTimeMillis() - recordStart;
				ui.setRecordTime(true,duration);		 
            }
        });
		 mCallTimer.start(RECORD_TIME_UPDATE_INTERVAL_MS);
		//add by zjq end
		
        TelecomAdapter.getInstance().startVoiceRecording();
    }

	//add by zhangjinqiang for hide record time--start
	private void updateVoiceCallRecordState() {
        Log.d(this, "[updateVoiceCallRecordState]...");
        final CallButtonUi ui = getUi();
        if (ui == null) {
            return;
        }
        Call ringCall = null;
        int ringCallState = -1;
        ringCall = CallList.getInstance().getIncomingCall();
        if (null != ringCall) {
            ringCallState = ringCall.getState();
        }
       if ((!InCallPresenter.getInstance().isRecording())
                || (ringCallState == Call.State.CALL_WAITING)) {
            stopRecordClicked();
        }
    }
	//add by zjq end

    public void stopRecordClicked() {
		final CallButtonUi ui = getUi();
		if(mCallTimer !=null){
			mCallTimer.cancel();
		}
		ui.setRecord(false);
		ui.setRecordTime(false,0);
        TelecomAdapter.getInstance().stopVoiceRecording();
    }

    @Override
    public void onUpdateRecordState(int state, int customValue) {
        if (FeatureOptionWrapper.isSupportPhoneVoiceRecording()) {
            final CallButtonUi ui = getUi();
            if (ui != null) {
                ui.configRecordingMenuItemTitle();
                if (!InCallPresenter.getInstance().isRecording()) {
                	stopRecordClicked();
                }
            }
        }
    }

    /**
     * Instructs Telecom to disconnect all the calls.
     */
    public void hangupAllClicked() {
        Log.d(this, "Hangup all calls");
        TelecomAdapter.getInstance().hangupAll();
    }

    /**
     * Instructs Telecom to disconnect all the HOLDING calls.
     */
    public void hangupAllHoldCallsClicked() {
        Log.d(this, "Hangup all hold calls");
        TelecomAdapter.getInstance().hangupAllHoldCalls();
    }

    /**
     * Instructs Telecom to disconnect active call and answer waiting call.
     */
    public void hangupActiveAndAnswerWaitingClicked() {
        Log.d(this, "Hangup all hold calls");
        TelecomAdapter.getInstance().hangupActiveAndAnswerWaiting();
    }

    /**
     * When there is incoming call, need update overflow menu as below:
     * 1. When there has one active call and the incoming call which can be answered,
     * need show and enable it.
     * 2. If there has no active or hold calls, need't show overflow button.
     * @param state current state
     */
    private void updateOverflowButtonForIncoming(InCallState state) {
        final CallButtonUi ui = getUi();
        if (ui == null || !state.isIncoming()) {
            return;
        }

        if (state.isIncoming() && CallList.getInstance().getActiveAndHoldCallsCount() == 0) {
            ui.showOverflowButton(false);
        } else {
            ui.showOverflowButton(true);
            ui.configureOverflowMenu(false, false, false, false, false);
        }
    }

    /**
     * M: Check whether the call is ECC.
     * @param call current call
     * @return true if is ECC call
     */
    private boolean isEmergencyCall(Call call) {
        if (call != null) {
            Uri handle = call.getHandle();
            if (handle != null) {
                String number = handle.getSchemeSpecificPart();
                if (!TextUtils.isEmpty(number)) {
                    return PhoneNumberUtils.isEmergencyNumber(number);
                }
            }
        }
        return false;
    }
}
