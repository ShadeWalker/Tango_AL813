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

package com.mediatek.galleryfeature.SlideVideo;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;

import com.mediatek.galleryfeature.platform.PlatformHelper;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.gl.GLIdleExecuter;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MTexture;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryfeature.video.SlideVideoUtils;
import com.mediatek.galleryfeature.video.SlideVideoUtils.RestoreData;

/**
 * Player with slide video feature
 *
 */
public class SlideVideoPlayer extends Player implements IVideoTexture.Listener {

    @Override
    public MTexture getTexture(MGLCanvas canvas) {
        if (mTexture != null) {
            return mTexture.getTexture(canvas);
        }
        return null;
    }

    private static final String TAG = "MtkGallery2/SlideVideoPlayer";
    private IVideoPlayer mPlayer;
    private IVideoTexture mTexture;
    private Context mContext;
    private GLIdleExecuter mGLIdleExecuter;
    private RestoreData mRestoreData = new RestoreData();
    private Uri mUri ;
    private boolean mIsLoop = false;
    private int mPlayPosition = 0;
    private MediaData mData;
    private boolean mIsInFilmMode = false;

    public SlideVideoPlayer(Context context, MediaData data, OutputType outputType,
            ThumbType thumbType) {
        super(context, data, outputType);
        mContext = context;
        mData = data;
        mIsLoop = false;
        String uri = "file://" + data.filePath;
        mUri = Uri.parse(uri);
    }

    @Override
    protected boolean onPrepare() {
        MtkLog.d(TAG, "<onPrepare> mUri is " + mUri);
        getPlayState();
        mPlayer = PlatformHelper.createSVExtension(mContext, mMediaData);
        mPlayer.setLoop(mIsLoop);
        mTexture = mPlayer.getVideoSurface();
        mTexture.setListener(this);
        mTexture.setGLIdleExecuter(mGLIdleExecuter);
        if (mPlayPosition != 0) {
            mTexture.setPlayPosition(mPlayPosition);
        }
        mTexture.prepare();
        return true;
    }

    @Override
    protected void sendFrameAvailable() {
        super.sendFrameAvailable();
    }

    @Override
    protected void onRelease() {
        MtkLog.d(TAG, "<onRelease> mUri is " + mUri);
        clearMovieControllerListener();
        mTexture.release();
    }

    @Override
    protected boolean onStart() {
        MtkLog.d(TAG, "<onStart> mUri is " + mUri);
        ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE)).requestAudioFocus(null,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        onFilmModeChange(mIsInFilmMode);
        mPlayer.setLoop(mRestoreData.mIsLoop);
        mPlayer.updateHooker();
        if (!mData.isLivePhoto) {
            mPlayer.setMovieControllerListener();
        }
        mTexture.onStart();
        return true;
    }
    
    @Override
    protected boolean onStop() {
        MtkLog.d(TAG, "<onStop>");
        // always return true so that the State of Player can be set to PREPARED
        return true;
    }

    @Override
    protected boolean onPause() {
        MtkLog.d(TAG, "<onPause> mUri is " + mUri);
        mTexture.onFilmModeChange(true);
        mTexture.onPause();
        return true;
    }

    @Override
    public void onFrameAvailable() {
        sendFrameAvailable();
    }

    public void setGLIdleExecuter(GLIdleExecuter exe) {
        mGLIdleExecuter = exe;
    }
    
    public void getPlayState() {
        MtkLog.v(TAG, "<getPlayState> mUri = " + mUri);
        if (SlideVideoUtils.getRestoreData(mUri) != null) {
            mIsLoop = SlideVideoUtils.getRestoreData(mUri).mIsLoop;
            mPlayPosition = SlideVideoUtils.getRestoreData(mUri).mPosition;
            MtkLog.v(TAG, "<getPlayState> mIsLoop" + mIsLoop + " mPlayPosition = " + mPlayPosition);
        }
    }
    
    public void setPlayState(boolean isActivityPaused) {
        MtkLog.v(TAG, "<setPlayState> mUri = " + mUri);
        if (mPlayer != null) {
            mRestoreData.mIsLoop = mPlayer.getLoop();
            mRestoreData.mPosition = mTexture.getPlayPosition(isActivityPaused);
            SlideVideoUtils.setRestoreData(mUri, mRestoreData);
        } else {
            MtkLog.w(TAG,
                    "<setPlayState> mPlayer is null,maybe the info of the video does not save");
        }
        MtkLog.d(TAG, "<setPlayState> mRestoreData.mIsLoop = " + mRestoreData.mIsLoop
                + " mRestoreData.mPosition = " + mRestoreData.mPosition);
    }
    
    public void clearMovieControllerListener() {
        if (!mData.isLivePhoto && mPlayer != null) {
            mPlayer.clearMovieControllerListener();
        }
    }
    
    public void onFilmModeChange(boolean isFilmMode) {
        MtkLog.v(TAG, "onFilmModeChange isFilmMode = " + isFilmMode);
        mIsInFilmMode = isFilmMode;
        if (mTexture != null) {
            mTexture.onFilmModeChange(isFilmMode);
        }
    }
}
