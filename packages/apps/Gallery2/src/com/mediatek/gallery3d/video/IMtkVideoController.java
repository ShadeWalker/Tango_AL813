/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.gallery3d.video;

import java.util.Map;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnTimedTextListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.media.MediaPlayer.TrackInfo;
import android.net.Uri;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;

public interface IMtkVideoController {
    /**
     * Bad file, decoder cannot decode this file.
     */
    public static final int MEDIA_ERROR_BAD_FILE = 260;
    /**
     * Can not connect to server for network failure.
     */
    public static final int MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER = 261;
    /**
     * Current media type does not supported.
     */
    public static final int MEDIA_ERROR_TYPE_NOT_SUPPORTED = 262;
    /**
     * DRM files not supported.
     */
    public static final int MEDIA_ERROR_DRM_NOT_SUPPORTED = 263;
    /**
     * Invalid link for phone to connect. (403 Forbidden)
     */
    public static final int MEDIA_ERROR_INVALID_CONNECTION = 264;
    /**
     * Status constant indicating asynchronous pause or play succeed.(Add for
     * RTSP play/pause asynchronous processing feature)
     */
    public static final int PAUSE_PLAY_SUCCEED = 0;

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l);

    public void setOnCompletionListener(OnCompletionListener l);

    public void setOnErrorListener(OnErrorListener l);

    public void start();

    public void pause();

    public boolean isInPlaybackState();

    public boolean canSeekForward();

    public boolean canPause();

    public boolean isPlaying();

    public void setSystemUiVisibility(int visibility);

    public void setVisibility(int visibility);

    public void stopPlayback();

    public boolean canSeekBackward();

    public void setOnSystemUiVisibilityChangeListener(OnSystemUiVisibilityChangeListener l);

    public int getAudioSessionId();

    public boolean postDelayed(Runnable action, long delayMillis);

    public boolean removeCallbacks(Runnable action);

    public void setOnTouchListener(View.OnTouchListener l);

    /*************************************** mtk add **********************************/
    public void setDuration(final int duration);

    public void seekTo(final int msec);

    public void setSlowMotionSpeed(int speed);

    public void enableSlowMotionSpeed();

    public void disableSlowMotionSpeed();

    public boolean isCurrentPlaying();

    public int getCurrentPosition();

    public int getDuration();

    public void setResumed(final boolean resume);

    public void clearDuration();

    public void clearSeek();

    public int selectTrack(int index);

    public void deselectTrack(int index);

    public TrackInfo[] getTrackInfo();

    public void dump();

    public void dismissAllowingStateLoss();

    public void setVideoURI(final Uri uri, final Map<String, String> headers);

    public boolean isTargetPlaying();
    // FOR MTK_SUBTITLE_SUPPORT
    public void setOnTimedTextListener(final OnTimedTextListener l);

    public void setOnVideoSizeChangedListener(final OnVideoSizeChangedListener l);

    public void setOnBufferingUpdateListener(final OnBufferingUpdateListener l);

    public void setOnInfoListener(final OnInfoListener l);

    public void setScreenModeManager(final ScreenModeManager manager);

    public String getStringParameter(int key);

    public boolean setParameter(int key, String value);

    public void setSlowMotionSection(String section);
    
    public boolean isInFilmMode();
}
