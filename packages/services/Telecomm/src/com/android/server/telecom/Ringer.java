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

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.SystemVibrator;
import android.os.Vibrator;
import android.provider.Settings;
import android.telecom.CallState;
import android.telecom.PhoneAccountHandle;
import android.media.RingtoneManager;
import android.telephony.SubscriptionManager;

import com.mediatek.audioprofile.AudioProfileManager;
import com.mediatek.telecom.TelecomUtils;

import java.util.LinkedList;
import java.util.List;

import android.os.Bundle;
import com.huawei.systemmanager.preventmode.IHoldPreventService;
import android.os.IBinder;
import android.os.ServiceManager;

/**
 * Controls the ringtone player.
 */
final class Ringer extends CallsManagerListenerBase {
    private static final long[] VIBRATION_PATTERN = new long[] {
        0, // No delay before starting
        1000, // How long to vibrate
        1000, // How long to wait before vibrating again
    };

    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build();

    /** Indicate that we want the pattern to repeat at the step which turns on vibration. */
    private static final int VIBRATION_PATTERN_REPEAT = 1;

    private final AsyncRingtonePlayer mRingtonePlayer;

    /**
     * Used to keep ordering of unanswered incoming calls. There can easily exist multiple incoming
     * calls and explicit ordering is useful for maintaining the proper state of the ringer.
     */
    private final List<Call> mRingingCalls = new LinkedList<>();

    private final CallAudioManager mCallAudioManager;
    private final CallsManager mCallsManager;
    private final InCallTonePlayer.Factory mPlayerFactory;
    private final Context mContext;
    private final Vibrator mVibrator;
    private final static int KEY_MO_VIBRATE_CONFIG = 0x00000002;
    private final static long MO_CALL_VIBRATE_TIME = 200;
    private InCallTonePlayer mCallWaitingPlayer;

    /**
     * Used to track the status of {@link #mVibrator} in the case of simultaneous incoming calls.
     */
    private boolean mIsVibrating = false;
		
	//add by zhangjinqiang for HWSystemManager--start
	private static final String PREVENT_MODE_SERVICE =
	"com.huawei.systemmanager.preventmode.PreventModeService";
	private IHoldPreventService holdPreventService;
	//add by zhangjinqiang end

    /** Initializes the Ringer. */
    Ringer(
            CallAudioManager callAudioManager,
            CallsManager callsManager,
            InCallTonePlayer.Factory playerFactory,
            Context context) {

        mCallAudioManager = callAudioManager;
        mCallsManager = callsManager;
        mPlayerFactory = playerFactory;
        mContext = context;
        // We don't rely on getSystemService(Context.VIBRATOR_SERVICE) to make sure this
        // vibrator object will be isolated from others.
        mVibrator = new SystemVibrator(context);
        mRingtonePlayer = new AsyncRingtonePlayer(context);
    }

    @Override
    public void onCallAdded(final Call call) {
		//add by zhangjinqiang for HW system manager --blacklist block start
		if(call!=null&&call.getCallerInfo()!=null){
			String incomingNumber = call.getCallerInfo().phoneNumber;
			try {
					IBinder service =  ServiceManager.getService(PREVENT_MODE_SERVICE);
					holdPreventService= IHoldPreventService.Stub.asInterface(service);
					boolean isPrevent = holdPreventService.isPrevent(incomingNumber,true);
					if(isPrevent){return;}
				}catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				} 
		 }
		//add by zhangjinqiang end
        if (call.isIncoming() && call.getState() == CallState.RINGING) {
            if (mRingingCalls.contains(call)) {
                Log.wtf(this, "New ringing call is already in list of unanswered calls");
            }
            mRingingCalls.add(call);
            updateRinging();
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        removeFromUnansweredCall(call);
    }

    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        if (newState != CallState.RINGING) {
            removeFromUnansweredCall(call);
        }
        ///M: vibrate when MO call connected. @{
        if (newState == CallState.ACTIVE && (oldState == CallState.DIALING || oldState == CallState.CONNECTING)
                /// M: CDMA MO call special handling. @{
                // For cdma call, framework will vibrate when the call be 'really' answered
                // by remote side, at this point the CDMA MO call maybe not in
                // real ACTIVE state, so skip this for CDMA MO call.
                && !TelecomUtils.hasCdmaCallCapability(mContext,
                        call.getTargetPhoneAccount())) {
                /// @}
            int emSetting = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.TELEPHONY_MISC_FEATURE_CONFIG, KEY_MO_VIBRATE_CONFIG);
            boolean enabled = (emSetting & KEY_MO_VIBRATE_CONFIG) != 0;
            Log.v(this, "need to vibrate, enabled: %s.", enabled);
            if (enabled) {
                mVibrator.vibrate(MO_CALL_VIBRATE_TIME);
            }
        }
        /// @}
    }

    @Override
    public void onIncomingCallAnswered(Call call) {
        onRespondedToIncomingCall(call);
    }

    @Override
    public void onIncomingCallRejected(Call call, boolean rejectWithMessage, String textMessage) {
        onRespondedToIncomingCall(call);
    }

    @Override
    public void onForegroundCallChanged(Call oldForegroundCall, Call newForegroundCall) {
        if (mRingingCalls.contains(oldForegroundCall) ||
                mRingingCalls.contains(newForegroundCall)) {
        	// Add For synchronize ringer and UI
        	Log.v(this, "synchronize onForegroundCallChanged() ");
        	//End
        	
        	//Original code
        	//updateRinging();
        }
    }

    /**
     * Silences the ringer for any actively ringing calls.
     */
    void silence() {
        // Remove all calls from the "ringing" set and then update the ringer.
        mRingingCalls.clear();
        updateRinging();
    }

    private void onRespondedToIncomingCall(Call call) {
        // Only stop the ringer if this call is the top-most incoming call.
        if (getTopMostUnansweredCall() == call) {
            removeFromUnansweredCall(call);
        }
    }

    private Call getTopMostUnansweredCall() {
        return mRingingCalls.isEmpty() ? null : mRingingCalls.get(0);
    }

    /**
     * Removes the specified call from the list of unanswered incoming calls and updates the ringer
     * based on the new state of {@link #mRingingCalls}. Safe to call with a call that is not
     * present in the list of incoming calls.
     */
    private void removeFromUnansweredCall(Call call) {
        mRingingCalls.remove(call);
        updateRinging();
    }

    private void updateRinging() {
        if (mRingingCalls.isEmpty()) {
            stopRinging();
            stopCallWaiting();
        } else {
            startRingingOrCallWaiting();
        }
    }

    public void startRingingOrCallWaiting() {
        Call foregroundCall = mCallsManager.getForegroundCall();
       // Log.v(this, "startRingingOrCallWaiting, foregroundCall: %s.", foregroundCall);

        if (mRingingCalls.contains(foregroundCall)) {
            ///M: ALPS01778496: not change call waiting to ringtone @{
            if (mCallWaitingPlayer != null) {
                return;
            }
            /// @}

            // The foreground call is one of incoming calls so play the ringer out loud.
            stopCallWaiting();

            if (!shouldRingForContact(foregroundCall.getContactUri())) {
                ///M: ALPS01786536: to request audio focus even interruption is on @{
                mCallAudioManager.setIsRinging(true);
                /// @}
                return;
            }

            AudioManager audioManager =
                    (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager.getStreamVolume(AudioManager.STREAM_RING) > 0) {
                Log.v(this, "startRingingOrCallWaiting");
                mCallAudioManager.setIsRinging(true);
				if (SystemProperties.get("ro.mtk_multisim_ringtone").equals("1")) {
                    PhoneAccountHandle phoneAccountHandle = foregroundCall.getTargetPhoneAccount();
                    Log.v(this, "phoneAccountHandle = " + phoneAccountHandle);
                    long subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                    if(phoneAccountHandle != null) {
                        String mId = phoneAccountHandle.getId();
                        Log.v(this, "phoneAccountHandle id = " + mId);
                        if(mId != null) {
                            subId = new Long(mId).longValue();
                        }
                    }
//				    AudioProfileManager audioProfileMgr = (AudioProfileManager)mContext.getSystemService(Context.AUDIO_PROFILE_SERVICE);
//                    Uri ringtoneUri = audioProfileMgr.getRingtoneUri(audioProfileMgr.getActiveProfileKey(), AudioProfileManager.TYPE_RINGTONE, subId);
                    
                    //for al812_al if subId is 1, it represents sim card 2 and if subId is 2, it represents sim card 1
                    //for al812_cl if subId is 1, it represents sim card 1 and if subId is 2, it represents sim card 2
                    String phoneType = SystemProperties.get("ro.phoneType");
                    Uri ringtoneUri = null;
                    //HQ_hanchao 20150923 modify for ringtone HQ01364299 start
                    int slotId = SubscriptionManager.getSlotId((int)subId);
                    ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(mContext,
                            (slotId == 0) ? RingtoneManager.TYPE_RINGTONE : RingtoneManager.TYPE_RINGTONE_SIM2);
                    //HQ_hanchao 20150923 modify for ringtone end
                    if(ringtoneUri != null){   //hanchao add for HQ01501853  2015.11.13
	                    if(false==RingtoneManager.isRingtoneExist(mContext, ringtoneUri)){
	                    	ringtoneUri = null;
	                    }
	                    Log.d(this, "subscriber id: "+subId+" ringtoneUri: "+ringtoneUri);
	                    Uri contactRingtoneUri = foregroundCall.getRingtone();
	                    Log.d(this, "contactRingtoneUri = " + contactRingtoneUri);

// / Added by guofeiyao
					int tmpType = -1;
                    if (contactRingtoneUri != null) {
                     	tmpType = RingtoneManager.getDefaultType(contactRingtoneUri);
                    }

                    if (tmpType != -1) {
                        Uri tmpUri = RingtoneManager.getActualDefaultRingtoneUri(mContext, tmpType);
					    if(!RingtoneManager.isRingtoneExist(mContext, tmpUri)){
						    Log.d("Ringer_duanze", "!RingtoneManager.isRingtoneExist(mContext, tmpUri)");
                            contactRingtoneUri = null;
                        }
                    }
					// / End

	                    mRingtonePlayer.play(contactRingtoneUri == null ? ringtoneUri : contactRingtoneUri);
                    }
                 } else { //add end

                // Because we wait until a contact info query to complete before processing a
                // call (for the purposes of direct-to-voicemail), the information about custom
                // ringtones should be available by the time this code executes. We can safely
                // request the custom ringtone from the call and expect it to be current.
                    mRingtonePlayer.play(foregroundCall.getRingtone());
                }
            } else {
                Log.v(this, "startRingingOrCallWaiting, skipping because volume is 0");
		/** add by liruihong for HQ01435293 for play call waiting when in silent mode begin*/
                //modify by wangmingyue for HQ01524536 begin
                AudioManager localAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);  
                boolean isHeadset = localAudioManager.isWiredHeadsetOn();
                if(isHeadset) {
			        int ringerMode = audioManager.getRingerModeInternal();
				    if (ringerMode == AudioManager.RINGER_MODE_SILENT && mCallWaitingPlayer == null) {
		                mCallWaitingPlayer =
		                        mPlayerFactory.createPlayer(InCallTonePlayer.TONE_CALL_WAITING);
		                mCallWaitingPlayer.startTone();
		            }
                }
                //modify by wangmingyue for HQ01524536 end
		/** add by liruihong for HQ01435293 for play call waiting when in silent mode begin*/
            }


            if (shouldVibrate(mContext) && !mIsVibrating) {
                mVibrator.vibrate(VIBRATION_PATTERN, VIBRATION_PATTERN_REPEAT,
                        VIBRATION_ATTRIBUTES);
                mIsVibrating = true;
            }
        } else if (foregroundCall != null) {
            ///M: ALPS01978768
            // do not play call-waiting tone when a pre_dial_wait call exists
            // directly play ringtone when pre_dial_wait call is disconnected @{
            if (foregroundCall.getState() == CallState.PRE_DIAL_WAIT) {
                return;
            }
            /// @}

            ///M: ALPS02009942
            // do not change ringtone to call-waiting
            // when one ringing call is disconnected by remote or rejected(two incoming calls exist) 
            // this happens dsda project @{
            if ((foregroundCall.getState() == CallState.DISCONNECTED && !mRingingCalls.isEmpty())
                    || mCallsManager.isAllCallRinging()) {
                Log.v(this, "do not change ringtone to call-waiting when one ringing call is disconnected(two incoming calls exist)");
                return;
            }
            /// @}

            // The first incoming call added to Telecom is not a foreground call at this point
            // in time. If the current foreground call is null at point, don't play call-waiting
            // as the call will eventually be promoted to the foreground call and play the
            // ring tone.
            Log.v(this, "Playing call-waiting tone.");

            // All incoming calls are in background so play call waiting.
            stopRinging();

            if (mCallWaitingPlayer == null) {
                mCallWaitingPlayer =
                        mPlayerFactory.createPlayer(InCallTonePlayer.TONE_CALL_WAITING);
                mCallWaitingPlayer.startTone();
            }
        }
    }

    private boolean shouldRingForContact(Uri contactUri) {
        final NotificationManager manager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        final Bundle extras = new Bundle();
        if (contactUri != null) {
            extras.putStringArray(Notification.EXTRA_PEOPLE, new String[] {contactUri.toString()});
        }
        return manager.matchesCallFilter(extras);
    }

    private void stopRinging() {
        Log.v(this, "stopRinging");

        mRingtonePlayer.stop();

        if (mIsVibrating) {
            mVibrator.cancel();
            mIsVibrating = false;
        }

        // Even though stop is asynchronous it's ok to update the audio manager. Things like audio
        // focus are voluntary so releasing focus too early is not detrimental.
        mCallAudioManager.setIsRinging(false);
    }

    private void stopCallWaiting() {
        Log.v(this, "stop call waiting.");
        if (mCallWaitingPlayer != null) {
            mCallWaitingPlayer.stopTone();
            mCallWaitingPlayer = null;
        }
    }

    private boolean shouldVibrate(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        /// M: use MTK_AUDIO_PROFILES on non-bsp @{
        /*if (SystemProperties.get("ro.mtk_audio_profiles").equals("1")
                && !SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
            //return audioManager.shouldVibrate(AudioManager.VIBRATE_TYPE_RINGER);
            //ALPS01813593: change to use AudioProfileManager, to sync with Dial vibrate settings
            AudioProfileManager audioProfileMgr = (AudioProfileManager)context.getSystemService(Context.AUDIO_PROFILE_SERVICE);
            String profileKey = audioProfileMgr.getActiveProfileKey();
            return audioProfileMgr.isVibrationEnabled(profileKey);
        }*/
        /// @}

        int ringerMode = audioManager.getRingerModeInternal();
        if (getVibrateWhenRinging(context)) {
            return ringerMode != AudioManager.RINGER_MODE_SILENT;
        } else {
            return ringerMode == AudioManager.RINGER_MODE_VIBRATE;
        }
    }

    private boolean getVibrateWhenRinging(Context context) {
        if (!mVibrator.hasVibrator()) {
            return false;
        }
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING, 0) != 0;
    }
}
