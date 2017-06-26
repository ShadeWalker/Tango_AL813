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

package com.android.deskclock.alarms;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.text.TextUtils;

import com.android.deskclock.LogUtils;
import com.android.deskclock.AlarmClockFragment;
import com.android.deskclock.R;
import com.android.deskclock.provider.AlarmInstance;

import com.mediatek.deskclock.ext.IAlarmControllerExt;
import com.mediatek.deskclock.extension.OPExtensionFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Manages playing ringtone and vibrating the device.
 */
public class AlarmKlaxon {
    private static final long[] VIBRATE_PATTERN = new long[] { 500, 500 };
    private static final int VIBRATE_LENGTH = 500;

    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build();

    private static boolean sStarted = false;
    private static MediaPlayer sMediaPlayer = null;
    ///M: to control the in call alarm
    private static IAlarmControllerExt sAlarmControllerExt;

    public static void stop(Context context) {
        LogUtils.v("AlarmKlaxon.stop()");

        if (sStarted) {
            sStarted = false;
            // Stop audio playing
            if (sMediaPlayer != null) {
                sMediaPlayer.stop();
                AudioManager audioManager = (AudioManager)
                        context.getSystemService(Context.AUDIO_SERVICE);
                audioManager.abandonAudioFocus(null);
                sMediaPlayer.release();
                sMediaPlayer = null;
            }

            ((Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE)).cancel();
        }
    }

    public static void start(final Context context, AlarmInstance instance,
            boolean inTelephoneCall) {
        LogUtils.v("AlarmKlaxon.start()");
        // Make sure we are stop before starting
        stop(context);

        /*
         * M: If in call state, just vibrate the phone, and don't start the alarm @{
         */
        if (inTelephoneCall) {
            sAlarmControllerExt = OPExtensionFactory.getAlarmControllerExt(context);
            if (null != sAlarmControllerExt) {
                sAlarmControllerExt.vibrate(context);
            }
            return;
        } ///@}

        if (!AlarmInstance.NO_RINGTONE_URI.equals(instance.mRingtone)) {
            Uri alarmNoise = instance.mRingtone;
            // Fall back on the default alarm if the database does not have an
            // alarm stored.
            /// M: if the alarm's uri exists but the real file is missing, roll back to default one
            if (alarmNoise == null
                   || !AlarmClockFragment.isRingtoneExisted(context, alarmNoise.toString())) {
                alarmNoise = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                LogUtils.v("Using default alarm: " + alarmNoise.toString());
            }

            // TODO: Reuse mMediaPlayer instead of creating a new one and/or use RingtoneManager.
            sMediaPlayer = new MediaPlayer();
            sMediaPlayer.setOnErrorListener(new OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    LogUtils.e("Error occurred while playing audio. Stopping AlarmKlaxon.");
                    AlarmKlaxon.stop(context);
                    return true;
                }
            });

            /// M: recoder whether exception or not
            boolean happenException = false;
            try {
                ///M: if boot from power off alarm and use external ringtone,
                //just use the backup ringtone to play @{
                if (PowerOffAlarm.bootFromPoweroffAlarm()
                        && PowerOffAlarm.getNearestAlarmWithExternalRingtone(
                                context, instance) != null) {
                    setBackupRingtoneToPlay(context, instance);
                ///@}
                } else {
                    sMediaPlayer.setDataSource(context, alarmNoise);
                }
                startAlarm(context, sMediaPlayer);
            } catch (IOException ie) {
                LogUtils.v("Failed to play the default ringtone,ie: " + ie);
                happenException = true;
            } catch (IllegalStateException se) {
                LogUtils.v("Failed to play the default ringtone,se: " + se);
                happenException = true;
            } finally {
                // The alarmNoise may be on the sd card which could be busy
                // right now. Use the fallback ringtone.
                if (happenException) {
                    try {
                        // Must reset the media player to clear the error state.
                        sMediaPlayer.reset();
                        /// M: change the fallback ringtone to defualt
                        Uri defaultRingtone = RingtoneManager
                                .getDefaultUri(RingtoneManager.TYPE_ALARM);
                        sMediaPlayer.setDataSource(context, defaultRingtone);
                        startAlarm(context, sMediaPlayer);
                    } catch (IOException ex1) {
                        // At this point we just don't play anything.
                        LogUtils.e("Failed to play fallback ringtone", ex1);
                    }
                    happenException = false;
                }
            }
        }

        if (instance.mVibrate) {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_PATTERN, 0, VIBRATION_ATTRIBUTES);
        }

        sStarted = true;
    }

    // Do the common stuff when starting the alarm.
    private static void startAlarm(Context context, MediaPlayer player) throws IOException {
        LogUtils.v("startAlarm, check StreamVolume and requestAudioFocus");
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // do not play alarms if stream volume is 0 (typically because ringer mode is silent).
        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
            player.setAudioStreamType(AudioManager.STREAM_ALARM);
            player.setLooping(true);
            player.prepare();
            audioManager.requestAudioFocus(null,
                    AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            player.start();
            LogUtils.d("Play successful, StreamVolume != 0");
        }
    }

    private static void setDataSourceFromResource(Context context, MediaPlayer player, int res)
            throws IOException {
        AssetFileDescriptor afd = context.getResources().openRawResourceFd(res);
        if (afd != null) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        }
    }

    /** M: set the backup ringtone for power off alarm to play @{ */
    private static void setBackupRingtoneToPlay(Context context, AlarmInstance instance)
            throws IOException {
        String externalRingUri =
                PowerOffAlarm.getNearestAlarmWithExternalRingtone(context, instance);
        String ringtonePath = null;
        if (!TextUtils.isEmpty(externalRingUri)) {
            ringtonePath = context.getFilesDir().getAbsolutePath() + File.separator
                    + PowerOffAlarm.getBackupFilename(context, instance.mRingtone);
        }
        LogUtils.v("setBackupRingtoneToPlay ringtone: " + ringtonePath);

        File file = null;
        FileInputStream fis = null;
        try {
            if (!TextUtils.isEmpty(ringtonePath)) {
                file = new File(ringtonePath);
                if (null != file && file.exists() && file.getTotalSpace() > 0) {
                    fis = new FileInputStream(file);
                }
            }

            LogUtils.v("setBackupRingtoneToPlay Set external ringtone success: " + (null != fis));
            if (null == fis) {
                sMediaPlayer.setDataSource(context,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            } else {
                sMediaPlayer.setDataSource(fis.getFD());
            }
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }
}
