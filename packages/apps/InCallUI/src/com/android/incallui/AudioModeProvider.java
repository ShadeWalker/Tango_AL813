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

import com.google.common.collect.Lists;

import android.telecom.AudioState;
import android.telecom.Phone;

import java.util.List;

/**
 * Proxy class for getting and setting the audio mode.
 */
/* package */ class AudioModeProvider implements InCallPhoneListener {

    static final int AUDIO_MODE_INVALID = 0;

    private static AudioModeProvider sAudioModeProvider = new AudioModeProvider();
    private int mAudioMode = AudioState.ROUTE_EARPIECE;
    private boolean mMuted = false;
    private int mSupportedModes = AudioState.ROUTE_ALL;
    private final List<AudioModeListener> mListeners = Lists.newArrayList();
    private Phone mPhone;
    private List<String> mMutedCallList = Lists.newArrayList();

    private Phone.Listener mPhoneListener = new Phone.Listener() {
        @Override
        public void onAudioStateChanged(Phone phone, AudioState audioState) {
            onAudioModeChange(audioState.getRoute(), audioState.isMuted());
            onSupportedAudioModeChange(audioState.getSupportedRouteMask());
        }
    };

    public static AudioModeProvider getInstance() {
        return sAudioModeProvider;
    }

    public void addMutedCall (String call) {
        if (call == null) {
           return;
        }
        mMutedCallList.add(call);
    }

    public void removeMutedCall (String call) {
        if (call == null) {
           return;
        }
        mMutedCallList.remove(call);
    }

    public boolean isCallMuted (String call) {
        if (call == null) {
           return false;
        }
        for (String c : mMutedCallList) {
            if (c.equals(call)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void setPhone(Phone phone) {
        mPhone = phone;
        mPhone.addListener(mPhoneListener);
    }

    @Override
    public void clearPhone() {
        mPhone.removeListener(mPhoneListener);
        mPhone = null;
    }

    public void onAudioModeChange(int newMode, boolean muted) {
        if (mAudioMode != newMode) {
            mAudioMode = newMode;
            for (AudioModeListener l : mListeners) {
                l.onAudioMode(mAudioMode);
            }
        }

        if (mMuted != muted) {
            mMuted = muted;
            for (AudioModeListener l : mListeners) {
                l.onMute(mMuted);
            }
        }
    }

    public void onSupportedAudioModeChange(int newModeMask) {
        /// M: For ALPS01825524 @{
        // when mSupportedModes is really changed, then do update
        if (mSupportedModes != newModeMask) {
        /// @}
            mSupportedModes = newModeMask;

            for (AudioModeListener l : mListeners) {
                l.onSupportedAudioMode(mSupportedModes);
            }
        }
    }

    public void addListener(AudioModeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
            listener.onSupportedAudioMode(mSupportedModes);
            listener.onAudioMode(mAudioMode);
            listener.onMute(mMuted);
        }
    }

    public void removeListener(AudioModeListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    public int getSupportedModes() {
        return mSupportedModes;
    }

    public int getAudioMode() {
        return mAudioMode;
    }

    public boolean getMute() {
        return mMuted;
    }

    /* package */ interface AudioModeListener {
        void onAudioMode(int newMode);
        void onMute(boolean muted);
        void onSupportedAudioMode(int modeMask);
    }
}
