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

package com.android.server.telecom;

import android.content.Context;
import android.media.AudioManager;
import android.telecom.AudioState;
import android.telecom.CallState;

import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.Preconditions;

import java.util.Objects;

/* M: CC part start */
import java.io.FileWriter;
import android.os.SystemProperties;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.PhoneConstants;
/* M: CC part end */

/**
 * This class manages audio modes, streams and other properties.
 */
final class CallAudioManager extends CallsManagerListenerBase
        implements WiredHeadsetManager.Listener {
    private static final int STREAM_NONE = -1;

    private final StatusBarNotifier mStatusBarNotifier;
    private final AudioManager mAudioManager;
    private final BluetoothManager mBluetoothManager;
    private final WiredHeadsetManager mWiredHeadsetManager;

    private AudioState mAudioState;
    private int mAudioFocusStreamType;
    private boolean mIsRinging;
    private boolean mIsTonePlaying;
    private boolean mWasSpeakerOn;
    private int mMostRecentlyUsedMode = AudioManager.MODE_IN_CALL;

    /* M: CC part start */
    private static int sFirstMD = -1;
    private static int sTelephonyMode = -1;
    private static final int MODE_5_WGNTG_DUALTALK = 5;
    private static final int MODE_6_TGNG_DUALTALK = 6;
    private static final int MODE_7_WGNG_DUALTALK = 7;
    private static final int MODE_8_GNG_DUALTALK = 8;
    private TelephonyManager mTelephonyManager = TelephonyManager.getDefault();
    /* M: CC part end */

    //ALPS01765149, deal with audio mode differently if the value is true
    private boolean mFlightModeOn = false;

    CallAudioManager(Context context, StatusBarNotifier statusBarNotifier,
            WiredHeadsetManager wiredHeadsetManager) {
        mStatusBarNotifier = statusBarNotifier;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mBluetoothManager = new BluetoothManager(context, this);
        mWiredHeadsetManager = wiredHeadsetManager;
        mWiredHeadsetManager.addListener(this);

        saveAudioState(getInitialAudioState(null));
        mAudioFocusStreamType = STREAM_NONE;
    }

    AudioState getAudioState() {
        return mAudioState;
    }

    @Override
    public void onCallAdded(Call call) {
        onCallUpdated(call);

        if (hasFocus() && getForegroundCall() == call) {
            if (!call.isIncoming()) {
                // Unmute new outgoing call.
                setSystemAudioState(false, mAudioState.getRoute(),
                        mAudioState.getSupportedRouteMask());
            }
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        // If we didn't already have focus, there's nothing to do.
        if (hasFocus()) {
            if (CallsManager.getInstance().getCalls().isEmpty()
                || CallsManager.getInstance().isAllCallRinging()) {
                Log.v(this, "all calls removed, reseting system audio to default state;" +
                      "or all active call removed, only ringing calls exist");
                mWasSpeakerOn = false;
                setInitialAudioState(null, false /* force */);
            ///M: ALPS01960510 reset audio route as initial state
            // when there has ECC @{
            } else if (CallsManager.getInstance().hasEmergencyCall()) {
                Log.v(this, "reset audio route when exist ECC call");
                AudioState audioState = getInitialAudioState(null);
                setSystemAudioState(false, mAudioState.isMuted(), audioState.getRoute(), audioState.getSupportedRouteMask());
            }
            /// @}
            updateAudioStreamAndMode();
        }
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        onCallUpdated(call);
    }

    @Override
    public void onIncomingCallAnswered(Call call) {
        int route = mAudioState.getRoute();

        // We do two things:
        // (1) If this is the first call, then we can to turn on bluetooth if available.
        // (2) Unmute the audio for the new incoming call.
        boolean isOnlyCall = CallsManager.getInstance().getCalls().size() == 1;
        boolean isAllCallRinging = CallsManager.getInstance().isAllCallRinging();
        Log.i(this, "CallAudioManager.onIncomingCallAnswered(), isOnlyCall = " + isOnlyCall + " isAllCallRinging = " + isAllCallRinging);
      /// M: ALPS01900842/ALPS01900825, not change audio route to BT
        // if BT audio is not connected, to avoid AP request to connect SCO @{
        if ((isOnlyCall || isAllCallRinging) && mBluetoothManager.isBluetoothAudioConnected()) {
            route = AudioState.ROUTE_BLUETOOTH;
        }
      /// @}

        setSystemAudioState(false /* isMute */, route, mAudioState.getSupportedRouteMask());
    }

    @Override
    public void onForegroundCallChanged(Call oldForegroundCall, Call newForegroundCall) {
        onCallUpdated(newForegroundCall);
        // Ensure that the foreground call knows about the latest audio state.
        updateAudioForForegroundCall();
    }

    @Override
    public void onIsVoipAudioModeChanged(Call call) {
        updateAudioStreamAndMode();
    }

    /**
      * Updates the audio route when the headset plugged in state changes. For example, if audio is
      * being routed over speakerphone and a headset is plugged in then switch to wired headset.
      */
    @Override
    public void onWiredHeadsetPluggedInChanged(boolean oldIsPluggedIn, boolean newIsPluggedIn) {
     // This can happen even when there are no calls and we don't have focus.
        if (!hasFocus()) {
            return;
        }

        boolean isCurrentlyWiredHeadset = mAudioState.getRoute() == AudioState.ROUTE_WIRED_HEADSET;
        int newRoute = mAudioState.getRoute();  // start out with existing route
        //ALPS01767155 since BT audio is connected, do not change audio route at here
        if (!mBluetoothManager.isBluetoothAudioConnected() && mAudioState.getRoute() != AudioState.ROUTE_BLUETOOTH) {
            if (newIsPluggedIn) {
                newRoute = AudioState.ROUTE_WIRED_HEADSET;
            } else if (isCurrentlyWiredHeadset) {
                Call call = getForegroundCall();
                boolean hasLiveCall = call != null && call.isAlive();

                if (hasLiveCall) {
                    // In order of preference when a wireless headset is unplugged.
                    if (mWasSpeakerOn) {
                        newRoute = AudioState.ROUTE_SPEAKER;
                    } else {
                        newRoute = AudioState.ROUTE_EARPIECE;
                    }
                    // We don't automatically connect to bluetooth when user unplugs their wired headset
                    // and they were previously using the wired. Wired and earpiece are effectively the
                    // same choice in that they replace each other as an option when wired headsets
                    // are plugged in and out. This means that keeping it earpiece is a bit more
                    // consistent with the status quo.  Bluetooth also has more danger associated with
                    // choosing it in the wrong curcumstance because bluetooth devices can be
                    // semi-public (like in a very-occupied car) where earpiece doesn't carry that risk.
                }
            }
        }

        // We need to call this every time even if we do not change the route because the supported
        // routes changed either to include or not include WIRED_HEADSET.
        setSystemAudioState(mAudioState.isMuted(), newRoute, calculateSupportedRoutes());
    }

    /* M: CC part start */
    @Override
    public void onConnectionLost(Call call) {
        // Need to abandon audio focus first after connection lost (ex. modem reset)
        // to avoid hearing noise sound before call is disconnected.
        abandonAudioFocus();
    }
    /* M: CC part start */

    void toggleMute() {
        mute(!mAudioState.isMuted());
    }

    void mute(boolean shouldMute) {
        if (!hasFocus()) {
            return;
        }

        Log.v(this, "mute, shouldMute: %b", shouldMute);
        // add by mtk 20150928
		RuntimeException e = new RuntimeException("perform mute");
		e.fillInStackTrace();
		android.util.Log.d("CallAudioManager", "who mute!", e);

        // Don't mute if there are any emergency calls.
        if (CallsManager.getInstance().hasEmergencyCall()) {
            shouldMute = false;
            Log.v(this, "ignoring mute for emergency call");
        }

        if (mAudioState.isMuted() != shouldMute) {
            setSystemAudioState(shouldMute, mAudioState.getRoute(),
                    mAudioState.getSupportedRouteMask());
        }
    }

    /**
     * Changed the audio route, for example from earpiece to speaker phone.
     *
     * @param route The new audio route to use. See {@link AudioState}.
     */
    void setAudioRoute(int route) {
        // This can happen even when there are no calls and we don't have focus.
        if (!hasFocus()) {
            return;
        }

        Log.v(this, "setAudioRoute, route: %s", AudioState.audioRouteToString(route));

        // Change ROUTE_WIRED_OR_EARPIECE to a single entry.
        int newRoute = selectWiredOrEarpiece(route, mAudioState.getSupportedRouteMask());

        // If route is unsupported, do nothing.
        if ((mAudioState.getSupportedRouteMask() | newRoute) == 0) {
            Log.wtf(this, "Asking to set to a route that is unsupported: %d", newRoute);
            return;
        }

        if (mAudioState.getRoute() != newRoute) {
            // Remember the new speaker state so it can be restored when the user plugs and unplugs
            // a headset.
            mWasSpeakerOn = newRoute == AudioState.ROUTE_SPEAKER;
            setSystemAudioState(mAudioState.isMuted(), newRoute,
                    mAudioState.getSupportedRouteMask());
        }
    }

    void setIsRinging(boolean isRinging) {
        if (mIsRinging != isRinging) {
            Log.v(this, "setIsRinging %b -> %b", mIsRinging, isRinging);
            mIsRinging = isRinging;
            updateAudioStreamAndMode();
        }
    }

    /**
     * Sets the tone playing status. Some tones can play even when there are no live calls and this
     * status indicates that we should keep audio focus even for tones that play beyond the life of
     * calls.
     *
     * @param isPlayingNew The status to set.
     */
    void setIsTonePlaying(boolean isPlayingNew) {
        ThreadUtil.checkOnMainThread();

        if (mIsTonePlaying != isPlayingNew) {
            Log.v(this, "mIsTonePlaying %b -> %b.", mIsTonePlaying, isPlayingNew);
            mIsTonePlaying = isPlayingNew;
            updateAudioStreamAndMode();
        }
    }

    /**
     * Updates the audio routing according to the bluetooth state.
     */
    void onBluetoothStateChange(BluetoothManager bluetoothManager) {
        // This can happen even when there are no calls and we don't have focus.
        if (!hasFocus()) {
            return;
        }

        int supportedRoutes = calculateSupportedRoutes();
        int newRoute = mAudioState.getRoute();
        if (bluetoothManager.isBluetoothAudioConnectedOrPending()) {
            newRoute = AudioState.ROUTE_BLUETOOTH;
        } else if (mAudioState.getRoute() == AudioState.ROUTE_BLUETOOTH) {
            newRoute = selectWiredOrEarpiece(AudioState.ROUTE_WIRED_OR_EARPIECE, supportedRoutes);
            // Do not switch to speaker when bluetooth disconnects.
            mWasSpeakerOn = false;
        }

        setSystemAudioState(mAudioState.isMuted(), newRoute, supportedRoutes);
    }

    boolean isBluetoothAudioOn() {
        return mBluetoothManager.isBluetoothAudioConnected();
    }

    boolean isBluetoothDeviceAvailable() {
        return mBluetoothManager.isBluetoothAvailable();
    }

    private void saveAudioState(AudioState audioState) {
        mAudioState = audioState;
        mStatusBarNotifier.notifyMute(mAudioState.isMuted());
        mStatusBarNotifier.notifySpeakerphone(mAudioState.getRoute() == AudioState.ROUTE_SPEAKER);
    }

    private void onCallUpdated(Call call) {
        boolean wasNotVoiceCall = mAudioFocusStreamType != AudioManager.STREAM_VOICE_CALL;
        updateAudioStreamAndMode();

        // If we transition from not voice call to voice call, we need to set an initial state.
        if (wasNotVoiceCall && mAudioFocusStreamType == AudioManager.STREAM_VOICE_CALL) {
            setInitialAudioState(call, true /* force */);
        }
    }

    private void setSystemAudioState(boolean isMuted, int route, int supportedRouteMask) {
        setSystemAudioState(false /* force */, isMuted, route, supportedRouteMask);
    }

    private void setSystemAudioState(
            boolean force, boolean isMuted, int route, int supportedRouteMask) {
        if (!hasFocus()) {
            return;
        }

        AudioState oldAudioState = mAudioState;
        saveAudioState(new AudioState(isMuted, route, supportedRouteMask));
        if (!force && Objects.equals(oldAudioState, mAudioState)) {
            return;
        }
        Log.i(this, "changing audio state from %s to %s", oldAudioState, mAudioState);

        // Mute.
        if (mAudioState.isMuted() != mAudioManager.isMicrophoneMute()) {
            Log.i(this, "changing microphone mute state to: %b", mAudioState.isMuted());
            mAudioManager.setMicrophoneMute(mAudioState.isMuted());
        }

        // Audio route.
        if (mAudioState.getRoute() == AudioState.ROUTE_BLUETOOTH) {
            turnOnSpeaker(false);
            turnOnBluetooth(true);
        } else if (mAudioState.getRoute() == AudioState.ROUTE_SPEAKER) {
            turnOnBluetooth(false);
            turnOnSpeaker(true);
        } else if (mAudioState.getRoute() == AudioState.ROUTE_EARPIECE ||
                mAudioState.getRoute() == AudioState.ROUTE_WIRED_HEADSET) {
            /// M: ALPS02009089 
            // if initial audio route is EARPIECE, BT audio is connected during this time
            // then BT audio will be disconnected
            // only the old and new route is different, below operation is executed @{
            if (oldAudioState.route != mAudioState.route) {
                Log.i(this, "oldAudioState.route != mAudioState.route");
            /// @}
                turnOnBluetooth(false);
            }
			 turnOnSpeaker(false);//add by zhangjinqiang for  HQ01614751 2016-01-04
        }

        if (!oldAudioState.equals(mAudioState)) {
            CallsManager.getInstance().onAudioStateChanged(oldAudioState, mAudioState);
            updateAudioForForegroundCall();
        }
    }

    private void turnOnSpeaker(boolean on) {
        // Wired headset and earpiece work the same way
        if (mAudioManager.isSpeakerphoneOn() != on) {
            Log.i(this, "turning speaker phone %s", on);
            mAudioManager.setSpeakerphoneOn(on);
        }
    }

    private void turnOnBluetooth(boolean on) {
        if (mBluetoothManager.isBluetoothAvailable()) {
            boolean isAlreadyOn = mBluetoothManager.isBluetoothAudioConnectedOrPending();
            if (on != isAlreadyOn) {
                Log.i(this, "connecting bluetooth %s", on);
                if (on) {
                    mBluetoothManager.connectBluetoothAudio();
                } else {
                    mBluetoothManager.disconnectBluetoothAudio();
                }
            }
        }
    }

    private void updateAudioStreamAndMode() {
        Log.i(this, "updateAudioStreamAndMode, mIsRinging: %b, mIsTonePlaying: %b", mIsRinging,
                mIsTonePlaying);
        Log.d(this, "CallsManager.getInstance().hasActiveOrHoldingCall(): "
                + CallsManager.getInstance().hasActiveOrHoldingCall());
        Log.d(this, "hasRingingForegroundCall(): " + hasRingingForegroundCall());
        if (mIsRinging) {
            requestAudioFocusAndSetMode(AudioManager.STREAM_RING, AudioManager.MODE_RINGTONE);
        } else {
            Call foregroundCall = getForegroundCall();
            //Log.d(this, "updateAudioStreamAndMode, foregroundCall:" + foregroundCall);
            ///M: ALPS01931695 do not abandon audio focus if has PRE_DIAL_WAIT call
            // instead of postpone to set audio using call state to judge @{
            /*
            Call waitingForAccountSelectionCall =
                    CallsManager.getInstance().getFirstCallWithState(CallState.PRE_DIAL_WAIT);
            if (foregroundCall != null && waitingForAccountSelectionCall == null) {
                // In the case where there is a call that is waiting for account selection,
                // this will fall back to abandonAudioFocus() below, which temporarily exits
                // the in-call audio mode. This is to allow TalkBack to speak the "Call with"
                // dialog information at media volume as opposed to through the earpiece.
                // Once exiting the "Call with" dialog, the audio focus will return to an in-call
                // audio mode when this method (updateAudioStreamAndMode) is called again.
             */
            /// @}
            if (foregroundCall != null) {
                if (foregroundCall.isAlive()) {
                    int mode = foregroundCall.getIsVoipAudioMode() ?
                            AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_IN_CALL;
                    /// M: ALPS02064397 reset as google default behaviour
                    // set voip call as mode_in_communication when onIsVoipAudioModeChanged(Call)
                    /*
                    if (foregroundCall.getHandle() != null && foregroundCall.getHandle().getScheme().equals("sip")) {
                        mode = AudioManager.MODE_IN_COMMUNICATION;
                    }
                    */
                    /// @}

                    ///M: ALPS01765149, change audio mode to normal @{
                    if (mFlightModeOn) {
                        mode = AudioManager.MODE_NORMAL;
                    }
                    /// @}

                    //ALPS01781841, not set Ecc audio mode when the state is CONNECTING
                    //ALPS01884889, postpone set audio mode to improve launch performance
                    //ALPS01922723, postpone set audio mode when call needs to select phone account
                    if (foregroundCall.getState() == CallState.PRE_DIAL_WAIT || foregroundCall.getState() == CallState.CONNECTING) {
                        return;
                    }

                    if (isC2kSupported() && foregroundCall.getState() == CallState.ON_HOLD) {
                        mode = AudioManager.MODE_NORMAL;
                    }

                    requestAudioFocusAndSetMode(AudioManager.STREAM_VOICE_CALL, mode);
                    if (isC2kSupported() && foregroundCall.getState() == CallState.ACTIVE
                        && mAudioState.isMuted()) {
                        Log.d(this, "updateAudioStreamAndMode, restore mute state!");
                        mAudioManager.setMicrophoneMute(mAudioState.isMuted());
                    }
                }
            } else if (mIsTonePlaying) {
                // There is no call, however, we are still playing a tone, so keep focus.
                // Since there is no call from which to determine the mode, use the most
                // recently used mode instead.
              ///M: ALPS01765149, change audio mode to normal @{
                if (mFlightModeOn) {
                    mMostRecentlyUsedMode = AudioManager.MODE_NORMAL;
                }
                /// @}

                requestAudioFocusAndSetMode(
                        AudioManager.STREAM_VOICE_CALL, mMostRecentlyUsedMode);
            } else if (!hasRingingForegroundCall() 
                       && !CallsManager.getInstance().hasActiveOrHoldingCall()) {
                //ALPS02007006,one participant disconnects from conference call
                //foreground call maybe null during a short time
                //so audio system/mode will reset to initial state
                //add more condition to avoid this case happen
                abandonAudioFocus();
            } else {
                // mIsRinging is false, but there is a foreground ringing call present. Don't
                // abandon audio focus immediately to prevent audio focus from getting lost between
                // the time it takes for the foreground call to transition from RINGING to ACTIVE/
                // DISCONNECTED. When the call eventually transitions to the next state, audio
                // focus will be correctly abandoned by the if clause above.
            }
        }
    }

    private void requestAudioFocusAndSetMode(int stream, int mode) {
        Log.i(this, "requestAudioFocusAndSetMode, stream: %d -> %d, mode: %d",
                mAudioFocusStreamType, stream, mode);
        Preconditions.checkState(stream != STREAM_NONE);

        // Even if we already have focus, if the stream is different we update audio manager to give
        // it a hint about the purpose of our focus.
        if (mAudioFocusStreamType != stream) {
            Log.v(this, "requesting audio focus for stream: %d", stream);
            mAudioManager.requestAudioFocusForCall(stream,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        mAudioFocusStreamType = stream;

        setMode(mode);
    }

    private void abandonAudioFocus() {
        if (hasFocus()) {
            /// M: reset audio system to before abandon audio focus
            // some case there is no tone to play when disconnect call 
            // so abandonAudioFocus() will execute before onCallRemoved()
            // the reset audio system in onCallRemoved() will not execute under these cases
            // because audio focus has abandoned in advance @{
            /// M: ALPS01931170 it should reset audio system before audio mode set as MODE_NORMAL
            // such as mute/unmute can not operate successfully under MODE_NORMAL
            Log.i(this, "reset audio system before abandonAudioFocus");
            mWasSpeakerOn = false;
            setInitialAudioState(null, false);
            /// @}
            setMode(AudioManager.MODE_NORMAL);
            Log.v(this, "abandoning audio focus");
            mAudioManager.abandonAudioFocusForCall();
            mAudioFocusStreamType = STREAM_NONE;

          //ALPS01765149, reset mFlightModeOn as false when abandon audio focus
            mFlightModeOn = false;
            Log.i(this, "after abandoning audio focus, mFlightModeOn = " + mFlightModeOn);
        }
    }

    /**
     * Sets the audio mode.
     *
     * @param newMode Mode constant from AudioManager.MODE_*.
     */
    private void setMode(int newMode) {
        Preconditions.checkState(hasFocus());
        int oldMode = mAudioManager.getMode();
        int mode = audioModeUpdateDualModem(newMode);
        Log.v(this, "Request to change audio mode from %d to %d", oldMode, mode);

        if (oldMode != mode) {
            /* M: CC part start */
            audioModeCusUpdate(newMode);
            newMode = mode;
            /* M: CC part end */

            if (oldMode == AudioManager.MODE_IN_CALL && newMode == AudioManager.MODE_RINGTONE) {
                Log.i(this, "Transition from IN_CALL -> RINGTONE. Resetting to NORMAL first.");
                mAudioManager.setMode(AudioManager.MODE_NORMAL);
            }
            mAudioManager.setMode(newMode);
            mMostRecentlyUsedMode = newMode;
        }
    }

    private int selectWiredOrEarpiece(int route, int supportedRouteMask) {
        // Since they are mutually exclusive and one is ALWAYS valid, we allow a special input of
        // ROUTE_WIRED_OR_EARPIECE so that callers dont have to make a call to check which is
        // supported before calling setAudioRoute.
        if (route == AudioState.ROUTE_WIRED_OR_EARPIECE) {
            route = AudioState.ROUTE_WIRED_OR_EARPIECE & supportedRouteMask;
            if (route == 0) {
                Log.wtf(this, "One of wired headset or earpiece should always be valid.");
                // assume earpiece in this case.
                route = AudioState.ROUTE_EARPIECE;
            }
        }
        return route;
    }

    private int calculateSupportedRoutes() {
        int routeMask = AudioState.ROUTE_SPEAKER;

        if (mWiredHeadsetManager.isPluggedIn()) {
            routeMask |= AudioState.ROUTE_WIRED_HEADSET;
        } else {
            routeMask |= AudioState.ROUTE_EARPIECE;
        }

        if (mBluetoothManager.isBluetoothAvailable()) {
            routeMask |=  AudioState.ROUTE_BLUETOOTH;
        }

        return routeMask;
    }

    private AudioState getInitialAudioState(Call call) {
        int supportedRouteMask = calculateSupportedRoutes();
        int route = selectWiredOrEarpiece(
                AudioState.ROUTE_WIRED_OR_EARPIECE, supportedRouteMask);

        // We want the UI to indicate that "bluetooth is in use" in two slightly different cases:
        // (a) The obvious case: if a bluetooth headset is currently in use for an ongoing call.
        // (b) The not-so-obvious case: if an incoming call is ringing, and we expect that audio
        //     *will* be routed to a bluetooth headset once the call is answered. In this case, just
        //     check if the headset is available. Note this only applies when we are dealing with
        //     the first call.
        /// M: ALPS01900842/ALPS01900825, not change audio route to BT
        // if BT audio is not connected, to avoid AP request to connect SCO
        if (call != null && mBluetoothManager.isBluetoothAudioConnected()) {
            switch(call.getState()) {
                case CallState.ACTIVE:
                case CallState.ON_HOLD:
                case CallState.DIALING:
                case CallState.CONNECTING:
                case CallState.RINGING:
                ///M: ALPS01883896 
                // to avoid audio router is not BT
                // when call state is pre_dial_wait and BT connected @{
                case CallState.PRE_DIAL_WAIT:
                /// @}
                    route = AudioState.ROUTE_BLUETOOTH;
                    break;
                default:
                    break;
            }
        }

        return new AudioState(false, route, supportedRouteMask);
    }

    private void setInitialAudioState(Call call, boolean force) {
        AudioState audioState = getInitialAudioState(call);
       // Log.v(this, "setInitialAudioState %s, %s", audioState, call);
        setSystemAudioState(
                force, audioState.isMuted(), audioState.getRoute(),
                audioState.getSupportedRouteMask());
    }

    private void updateAudioForForegroundCall() {
        Call call = CallsManager.getInstance().getForegroundCall();
        if (call != null && call.getConnectionService() != null) {
            call.getConnectionService().onAudioStateChanged(call, mAudioState);
        }
    }

    /**
     * Returns the current foreground call in order to properly set the audio mode.
     */
    private Call getForegroundCall() {
        Call call = CallsManager.getInstance().getForegroundCall();

        // We ignore any foreground call that is in the ringing state because we deal with ringing
        // calls exclusively through the mIsRinging variable set by {@link Ringer}.
        if (call != null && call.getState() == CallState.RINGING) {
            return null;
        }

        return call;
    }

    private boolean hasRingingForegroundCall() {
        Call call = CallsManager.getInstance().getForegroundCall();
        return call != null && call.getState() == CallState.RINGING;
    }

    private boolean hasFocus() {
        return mAudioFocusStreamType != STREAM_NONE;
    }

    /**
     * Dumps the state of the {@link CallAudioManager}.
     *
     * @param pw The {@code IndentingPrintWriter} to write the state to.
     */
    public void dump(IndentingPrintWriter pw) {
        pw.println("mAudioState: " + mAudioState);
        pw.println("mBluetoothManager:");
        pw.increaseIndent();
        mBluetoothManager.dump(pw);
        pw.decreaseIndent();
        if (mWiredHeadsetManager != null) {
            pw.println("mWiredHeadsetManager:");
            pw.increaseIndent();
            mWiredHeadsetManager.dump(pw);
            pw.decreaseIndent();
        } else {
            pw.println("mWiredHeadsetManager: null");
        }
        pw.println("mAudioFocusStreamType: " + mAudioFocusStreamType);
        pw.println("mIsRinging: " + mIsRinging);
        pw.println("mIsTonePlaying: " + mIsTonePlaying);
        pw.println("mWasSpeakerOn: " + mWasSpeakerOn);
        pw.println("mMostRecentlyUsedMode: " + mMostRecentlyUsedMode);
    }

    /* M: CC part start */
    // =======================================================================================================
    // Audio Mode enhancement
    // =======================================================================================================
    private int getPhoneType(int phoneId) {
        int subId = SubscriptionManager.getSubIdUsingPhoneId(phoneId);
        int type = mTelephonyManager.getCurrentPhoneType(subId);
        Log.d(this, "getPhoneType, phoneId:" + phoneId + ", subId:" + subId
                + ", phone type:" + type);
        return type;
    }

    private boolean isDualtalk() {
        return SystemProperties.get("ro.mtk_dt_support").equals("1");
    }

    private boolean isC2kSupported() {
        return (SystemProperties.get("ro.mtk_c2k_support").equals("1"));
    }

    private boolean isEvdoDualtalk() {
        return (SystemProperties.get("ro.evdo_dt_support").equals("1"));
    }

    private boolean isEnableMD1() {
        return (SystemProperties.get("ro.mtk_enable_md1").equals("1"));
    }

    private boolean isEnableMD2() {
        return (SystemProperties.get("ro.mtk_enable_md2").equals("1"));
    }

    private boolean isEnableMD5() {
        return (SystemProperties.get("ro.mtk_enable_md5").equals("1"));
    }

    private int getExternalModemSlot() {
        if (SystemProperties.getInt("ril.external.md", 0) == 1) {
            return PhoneConstants.SIM_ID_1;
        } else if (SystemProperties.getInt("ril.external.md", 0) == 2) {
            return PhoneConstants.SIM_ID_2;
        } else {
            return -1;
        }
    }

    private int getTelephonyMode() {
        if (sTelephonyMode < 0)
            sTelephonyMode = SystemProperties.getInt("ril.telephony.mode", 0);
        return sTelephonyMode;
    }

    private int getFirstMD() {
        int telephonyMode = getTelephonyMode();
        switch (telephonyMode) {
            case MODE_5_WGNTG_DUALTALK:
              if (sFirstMD < 0) {
                  sFirstMD = SystemProperties.getInt("ril.first.md", 0);
              }
              break;

            case MODE_7_WGNG_DUALTALK:
            case MODE_8_GNG_DUALTALK:
              sFirstMD = 1;
              break;

            case MODE_6_TGNG_DUALTALK:
              sFirstMD = 2;
              break;
        }

        return sFirstMD;
    }

    private int getPhoneId(PhoneAccountHandle handle) {
        Log.d(this, "getPhoneId, handle:" + handle);
        if (handle == null) {
            return SubscriptionManager.INVALID_PHONE_INDEX;
        }
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        try {
            //M for EVDO world phone.@{
            String id = handle.getId();
            if (id != null && id.startsWith("E")) {
                id = id.substring(1);
            }
            //M for EVDO world phone.@}
            subId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            Log.d(this, "NumberFormatException, handle.getId():" + handle.getId());
        }
        return SubscriptionManager.getPhoneId(subId);
    }

    private int audioModeUpdateDualModem(int mode) {
        Log.d(this, "audioModeUpdateDualModem, mode:" + mode + ", isDualtalk:" + isDualtalk());
        int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        if (isDualtalk() || isEvdoDualtalk() || isC2kSupported()) {
            Call call = getForegroundCall();
            /* Do not need to switch in call mode if the foregound call is not alive */
            //Log.d(this, "getForegroundCall, call:" + call);
            if (call != null && call.isAlive() && mode == AudioManager.MODE_IN_CALL) {
                phoneId = getPhoneId(call.getTargetPhoneAccount());
                if (phoneId == SubscriptionManager.INVALID_PHONE_INDEX) {
                    Log.d(this, "Can't get the exactly phone id now.");
                    return AudioManager.MODE_INVALID;
                }
                if (isDualtalk()) {
                    if (phoneId == PhoneConstants.SIM_ID_2) {
                        mode = AudioManager.MODE_IN_CALL_EXTERNAL;
                        if (getExternalModemSlot() == -1) { // Dualtalk with two internal modems
                            mode = AudioManager.MODE_IN_CALL_2;
                        }
                    } else {
                        mode = AudioManager.MODE_IN_CALL;
                    }
                }
            }
        }

        int oldMode = mode;
        int newMode = oldMode;

        Log.d(this, "getExternalModemSlot:" + getExternalModemSlot() + ", mode:" + mode);

        /*
         * FDD modem: MODE_IN_CALL
         * TDD modem: MODE_IN_CALL_2
         * External modem: MODE_IN_CALL_EXTERNAL
         */

        /* If TDD modem is create as Phone1, FDD modem is created as Phone2, in call mode needs to be exchanged */
        if (isDualtalk() && getForegroundCall() != null && getForegroundCall().isAlive() &&
            (oldMode == AudioManager.MODE_IN_CALL || oldMode == AudioManager.MODE_IN_CALL_2
                || oldMode == AudioManager.MODE_IN_CALL_EXTERNAL)) {
            if ((getFirstMD() == 2) && (getExternalModemSlot() == -1)) {
                //Dualtalk with two internal modems
                newMode = (oldMode == AudioManager.MODE_IN_CALL)
                            ? AudioManager.MODE_IN_CALL_2 : AudioManager.MODE_IN_CALL;
            } else if (getExternalModemSlot() == PhoneConstants.SIM_ID_1) {
                newMode = (oldMode == AudioManager.MODE_IN_CALL)
                            ? AudioManager.MODE_IN_CALL_EXTERNAL : AudioManager.MODE_IN_CALL;
            }
        }

        /* If CDMA is created as Phone 1, switch IN_CALL and IN_CALL_EXTERNAL */
        if ((isEvdoDualtalk() || isC2kSupported())
            && getForegroundCall() != null
            && getForegroundCall().isAlive()
            && (oldMode == AudioManager.MODE_IN_CALL || oldMode == AudioManager.MODE_IN_CALL_2
                || oldMode == AudioManager.MODE_IN_CALL_EXTERNAL)) {
            if (getPhoneType(phoneId) != PhoneConstants.PHONE_TYPE_GSM) {
                newMode = AudioManager.MODE_IN_CALL_EXTERNAL;
            } else {
                newMode = AudioManager.MODE_IN_CALL;
            }
        }

        /* If only modem2 is enabled, replace MODE_IN_CALL with MODE_IN_CALL_2 */
        if (!isEnableMD1() && isEnableMD2() && (newMode == AudioManager.MODE_IN_CALL)) {
            newMode = AudioManager.MODE_IN_CALL_2;
        }

        if (!isDualtalk() && isEnableMD5() && (newMode == AudioManager.MODE_IN_CALL)) {
            newMode = AudioManager.MODE_IN_CALL_EXTERNAL;
        }

        Log.d(this, "audioModeUpdateDualModem, oldMode:" + oldMode + ", newMode:" + newMode);
        return newMode;
    }

    private void audioModeCusUpdate(int mode) {

        // Notify driver that call state changed
        // they may need to do something
        final int value = (mode > AudioManager.MODE_RINGTONE) ? 2 : mode;

        new Thread(new Runnable() {
            public void run() {
                // Owner : yucong Xiong
                // Set kpd as wake up source
                // so that kpd can wak up Sysytem by Vol. key when phone suspend when talking
                String callStateFilePath2 = String.format("/sys/bus/platform/mtk-kpd/driver/kpd_call_state");
                try {
                    String state2 = String.valueOf(value);
                    FileWriter fw2 = new FileWriter(callStateFilePath2);
                    fw2.write(state2);
                    fw2.close();
                    Log.v(this, "Call state for kpd is  %s" + state2);
                } catch (Exception e) {
                    Log.v(this, "" , e);
                }
            }
        }).start();
    }

    /**
     * ALPS01765149, set audio mode to normal as early as possible
     */
    void setNormalMode() {
        Log.i(this, "set audio mode as normal when flight mode turns on");
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
        mFlightModeOn = true;
        Log.i(this, "setNormalMode(), mFlightMode = " + mFlightModeOn);
    }
    /* M: CC part end */
}
