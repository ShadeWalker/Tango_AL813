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

package com.mediatek.audioprofile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.Settings;
import android.provider.Settings.System;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.mediatek.settings.ext.IAudioProfileExt;
import com.mediatek.settings.UtilsExt;
import com.mediatek.xlog.Xlog;

/**
 * Turns a {@link SeekBar} into a volume control.
 * @hide
 */
public class SeekBarVolumizer implements OnSeekBarChangeListener, Handler.Callback {
    private static final String TAG = "AudioProfile_SeekBarVolumizer";

    public interface Callback {
        void onSampleStarting(SeekBarVolumizer sbv);
    }

    private final Context mContext;
    private final Handler mHandler;
    private final H mUiHandler = new H();
    private final Callback mCallback;
    private final Uri mDefaultUri;
    private final AudioManager mAudioManager;
    private final AudioProfileManager mProfileManager;
    private final int mStreamType;
    private final int mMaxStreamVolume;
    private int mSystemVolume = -1;
    private final Receiver mReceiver = new Receiver();
    private final Observer mVolumeObserver;
    private String mKey;
    private boolean mProfileIsActive = false;

    private int mOriginalStreamVolume;
    private Ringtone mRingtone;
    private int mLastProgress = -1;
    private SeekBar mSeekBar;
    private int mVolumeBeforeMute = -1;

    private static final int MSG_SET_STREAM_VOLUME = 0;
    private static final int MSG_START_SAMPLE = 1;
    private static final int MSG_STOP_SAMPLE = 2;
    private static final int MSG_INIT_SAMPLE = 3;
    private static final int CHECK_RINGTONE_PLAYBACK_DELAY_MS = 1000;

    public IAudioProfileExt mExt;

    public SeekBarVolumizer(Context context, int streamType, Uri defaultUri,
            Callback callback, String profileKey) {
        mContext = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mProfileManager = (AudioProfileManager) context
                .getSystemService(context.AUDIO_PROFILE_SERVICE);

        mStreamType = streamType;
        mKey = profileKey;
        //mMaxStreamVolume = mAudioManager.getStreamMaxVolume(mStreamType);
        mMaxStreamVolume = mProfileManager.getStreamMaxVolume(mStreamType);
        mSystemVolume = mAudioManager.getStreamVolume(mStreamType);
        Xlog.d(TAG, "" + mStreamType + " get Original SYSTEM Volume: "
                + mSystemVolume);

        //mOriginalStreamVolume = mAudioManager.getStreamVolume(mStreamType);
        mOriginalStreamVolume = mProfileManager.getStreamVolume(mKey, mStreamType);
        Xlog.d(TAG, "Profile keys: " + mKey + " " + mStreamType + " get Original Volume: "
                + mOriginalStreamVolume);
        mProfileIsActive = mProfileManager.isActiveProfile(mKey);
        // if the volume is changed to 1 for ringer mode changed and we
        // can't receive the
        // broadcast to adjust the volume, sync the profile volume with the
        // system
        if (mProfileIsActive) {
            if (mSystemVolume != mOriginalStreamVolume) {
                Xlog.d(TAG, " sync " + mStreamType + " original Volume to"
                        + mSystemVolume);
                mOriginalStreamVolume = mSystemVolume;
            }
        }

        HandlerThread thread = new HandlerThread(TAG + ".CallbackHandler");
        thread.start();
        mHandler = new Handler(thread.getLooper(), this);
        mCallback = callback;


        mVolumeObserver = new Observer(mHandler);
        mContext.getContentResolver().registerContentObserver(
                System.getUriFor(System.VOLUME_SETTINGS[mStreamType]),
                false, mVolumeObserver);
        mReceiver.setListening(true);
        if (defaultUri == null) {
            if (mStreamType == AudioProfileManager.STREAM_RING) {
                defaultUri = mProfileManager.getRingtoneUri(mKey,
                        AudioProfileManager.TYPE_RINGTONE);
            } else if (mStreamType == AudioProfileManager.STREAM_NOTIFICATION) {
                defaultUri = mProfileManager.getRingtoneUri(mKey,
                        AudioProfileManager.TYPE_NOTIFICATION);
            } else {
                defaultUri = Settings.System.DEFAULT_ALARM_ALERT_URI;
            }
        }
        mDefaultUri = defaultUri;
        mHandler.sendEmptyMessage(MSG_INIT_SAMPLE);

        mExt = UtilsExt.getAudioProfilePlgin(context);
    }

    public void setSeekBar(SeekBar seekBar) {
        if (mSeekBar != null) {
            mSeekBar.setOnSeekBarChangeListener(null);
        }
        mSeekBar = seekBar;
        mSeekBar.setOnSeekBarChangeListener(null);
        mSeekBar.setMax(mMaxStreamVolume);
        mSeekBar.setProgress(mLastProgress > -1 ? mLastProgress : mOriginalStreamVolume);
        mSeekBar.setOnSeekBarChangeListener(this);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_SET_STREAM_VOLUME:
                saveVolume();
                break;
            case MSG_START_SAMPLE:
                onStartSample();
                break;
            case MSG_STOP_SAMPLE:
                onStopSample();
                break;
            case MSG_INIT_SAMPLE:
                onInitSample();
                break;
            default:
                Log.e(TAG, "invalid SeekBarVolumizer message: " + msg.what);
        }
        return true;
    }

    private void onInitSample() {
        mRingtone = RingtoneManager.getRingtone(mContext, mDefaultUri);
        if (mRingtone != null) {
            mRingtone.setStreamType(mStreamType);
        }
    }

    private void postStartSample() {
        mHandler.removeMessages(MSG_START_SAMPLE);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_START_SAMPLE),
                isSamplePlaying() ? CHECK_RINGTONE_PLAYBACK_DELAY_MS : 0);
    }

    private void onStartSample() {
        if (!isSamplePlaying()) {
            if (mCallback != null) {
            	Log.v(TAG, "Start sample.");
                mCallback.onSampleStarting(this);
            }
            if (mRingtone != null) {
                try {
                    mRingtone.play();
                } catch (Throwable e) {
                    Log.w(TAG, "Error playing ringtone, stream " + mStreamType, e);
                }
            }
        }
    }

    void postStopSample() {
        // remove pending delayed start messages
        mHandler.removeMessages(MSG_START_SAMPLE);
        mHandler.removeMessages(MSG_STOP_SAMPLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_SAMPLE));
    }

    private void onStopSample() {
        if (mRingtone != null) {
            mRingtone.stop();
        }
    }

    public void stop() {
        postStopSample();
        mContext.getContentResolver().unregisterContentObserver(mVolumeObserver);
        mSeekBar.setOnSeekBarChangeListener(null);
        mReceiver.setListening(false);
        mHandler.getLooper().quitSafely();
    }

    public void revertVolume() {
        Xlog.d(TAG, "" + mStreamType + " revert Last Volume "
                + mOriginalStreamVolume);
        //mAudioManager.setStreamVolume(mStreamType, mOriginalStreamVolume, 0);
        mProfileManager.setStreamVolume(mKey, mStreamType, mOriginalStreamVolume);
        if (mStreamType == AudioProfileManager.STREAM_RING) {
            mProfileManager.setStreamVolume(mKey,
                    AudioProfileManager.STREAM_NOTIFICATION,
                    mOriginalStreamVolume);
        }

        if (mProfileManager.isActiveProfile(mKey)) {
            Xlog.d(TAG, "" + mStreamType + " Active, Revert system Volume "
                    + mOriginalStreamVolume);
            setVolume(mStreamType, mOriginalStreamVolume, false);
        } else {
            if (!isSilentProfileActive()) {
                Xlog.d(TAG, "revertVolume: " + mStreamType
                        + " not Active, Revert system Volume "
                        + mSystemVolume);
                //setVolume(mStreamType, mSystemVolume, false);
            }
        }
    }

    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromTouch) {
        if (!fromTouch) {
            return;
        }

        postSetVolume(progress);
    }

    void postSetVolume(int progress) {
        // Do the volume changing separately to give responsive UI
        mLastProgress = progress;
        mHandler.removeMessages(MSG_SET_STREAM_VOLUME);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_STREAM_VOLUME));
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        postStartSample();
    }

    public boolean isSamplePlaying() {
        return mRingtone != null && mRingtone.isPlaying();
    }

    public void startSample() {
        postStartSample();
    }

    public void stopSample() {
        postStopSample();
    }

    public SeekBar getSeekBar() {
        return mSeekBar;
    }

    public void changeVolumeBy(int amount) {
        mSeekBar.incrementProgressBy(amount);
        postSetVolume(mSeekBar.getProgress());
        postStartSample();
        mVolumeBeforeMute = -1;
    }

    public void muteVolume() {
        if (mVolumeBeforeMute != -1) {
            mSeekBar.setProgress(mVolumeBeforeMute);
            postSetVolume(mVolumeBeforeMute);
            postStartSample();
            mVolumeBeforeMute = -1;
        } else {
            mVolumeBeforeMute = mSeekBar.getProgress();
            mSeekBar.setProgress(0);
            postStopSample();
            postSetVolume(0);
        }
    }

    public void onSaveInstanceState(VolumeStore volumeStore) {
        if (mLastProgress >= 0) {
            volumeStore.mVolume = mLastProgress;
            volumeStore.mOriginalVolume = mOriginalStreamVolume;
            volumeStore.mSystemVolume = mSystemVolume;
        }
    }

    public void onRestoreInstanceState(VolumeStore volumeStore) {
        if (volumeStore.mVolume != -1) {
            mOriginalStreamVolume = volumeStore.mOriginalVolume;
            mLastProgress = volumeStore.mVolume;
            mSystemVolume = volumeStore.mSystemVolume;
            postSetVolume(mLastProgress);
        }
    }

    /**
     * bind the preference with the profile
     *
     * @param key
     *            the profile key
     */
    public void setProfile(String key) {
        mKey = key;
    }

    /**
     * Get whether the current ringermode is normal
     *
     * @return true, the current ringer mode is VIBRATE or Silent,
     *         corresponding to the MEETING or Silent profile
     */
    private boolean isSilentProfileActive() {
        return mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
    }

    /**
     * According to the streamType, volume ,flag, set the volume to the
     * system
     *
     * @param streamType
     *            The StreamType of the volume which will be set
     * @param volume
     *            the volume value which will be set
     * @param flag
     *            true, set the volume by calling
     *            AudioManager.setAudioProfileStreamVolume, in this API,
     *            even though the volume is set to 0, the ringer Mode will
     *            not change, it is useful because in the general profile of
     *            common load, set the volume to 0 and not save, in this
     *            case we need not to change the ringermode, we change the
     *            ringermode if click the "ok" button.The same case is about
     *            the CMCC load, in CMCC, we need not to change the ringer
     *            mode no matter the volume we set is 0 even though we click
     *            "ok" button.
     */
    private void setVolume(int streamType, int volume, boolean flag) {
        if (streamType == AudioProfileManager.STREAM_RING) {

            if (flag) {
                mAudioManager.setAudioProfileStreamVolume(mStreamType,
                        volume, 0);
                mAudioManager.setAudioProfileStreamVolume(
                        AudioProfileManager.STREAM_NOTIFICATION, volume, 0);
            } else {
                mExt.setRingerVolume(mAudioManager, volume);
            }

        } else {
            if (flag) {
                mAudioManager.setAudioProfileStreamVolume(streamType,
                        volume, 0);
            } else {
                mExt.setVolume(mAudioManager, streamType, volume);
            }
        }
    }

    /**
     * When click the "Ok" button, set the volume to system
     */
    public void saveVolume() {
        Xlog.d(TAG, "" + mStreamType + " Save Last Volume " + mLastProgress);

        mProfileManager.setStreamVolume(mKey, mStreamType, mLastProgress);
        if (mStreamType == AudioProfileManager.STREAM_RING) {
            mProfileManager.setStreamVolume(mKey,
                    AudioProfileManager.STREAM_NOTIFICATION, mLastProgress);
        }

        if (mProfileManager.isActiveProfile(mKey)) {
            Xlog.d(TAG, "" + mStreamType + " Active, save system Volume "
                    + mLastProgress);
            setVolume(mStreamType, mLastProgress, false);
        } else {
            if (!isSilentProfileActive()) {
                Xlog.d(TAG, "saveVolume: " + mStreamType
                        + " not Active, Revert system Volume "
                        + mSystemVolume);
                //setVolume(mStreamType, mSystemVolume, false);
            }
        }

    }
    
    public void ringtoneChanged() {
    	Xlog.d(TAG, "Ringtone changed.");
    	Uri newRingtoneUri = null;
        if (mStreamType == AudioProfileManager.STREAM_RING) {
        	newRingtoneUri = mProfileManager.getRingtoneUri(mKey,
                    AudioProfileManager.TYPE_RINGTONE);
        } else if (mStreamType == AudioProfileManager.STREAM_NOTIFICATION) {
        	newRingtoneUri = mProfileManager.getRingtoneUri(mKey,
                    AudioProfileManager.TYPE_NOTIFICATION);
        }
        
        if (newRingtoneUri != null) {
            mRingtone = RingtoneManager.getRingtone(mContext, newRingtoneUri);
            if (mRingtone != null) {
                mRingtone.setStreamType(mStreamType);
            }        	
        }
    }

    private final class H extends Handler {
        private static final int UPDATE_SLIDER = 1;

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == UPDATE_SLIDER) {
                if (mSeekBar != null) {
                    mSeekBar.setProgress(msg.arg1);
                    mLastProgress = mSeekBar.getProgress();
                }
            }
        }

        public void postUpdateSlider(int volume) {
            obtainMessage(UPDATE_SLIDER, volume, 0).sendToTarget();
        }
    }

    private final class Observer extends ContentObserver {
        public Observer(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (mSeekBar != null && mAudioManager != null) {
                final int volume = mAudioManager.getStreamVolume(mStreamType);
                mUiHandler.postUpdateSlider(volume);
            }
        }
    }

    public static class VolumeStore {
        public int mVolume = -1;
        public int mOriginalVolume = -1;
        public int mSystemVolume = -1;
    }

    private final class Receiver extends BroadcastReceiver {
        private boolean mListening;

        public void setListening(boolean listening) {
            if (mListening == listening) return;
            mListening = listening;
            if (listening) {
                final IntentFilter filter = new IntentFilter(AudioManager.VOLUME_CHANGED_ACTION);
                mContext.registerReceiver(this, filter);
            } else {
                mContext.unregisterReceiver(this);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!AudioManager.VOLUME_CHANGED_ACTION.equals(intent.getAction())) return;
            final int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
            final int streamValue = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, -1);
            if (mSeekBar != null && streamType == mStreamType && streamValue != -1) {
                mUiHandler.postUpdateSlider(streamValue);
            }
        }
    }
}

