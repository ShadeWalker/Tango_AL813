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

import java.io.IOException;
import java.util.Map;

//mark for build error.
import com.android.gallery3d.R;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.gallery3d.video.ScreenModeManager.ScreenModeListener;
import com.mediatek.galleryfeature.SlideVideo.IVideoTexture;
import com.mediatek.galleryfeature.SlideVideo.IVideoTexture.Listener;
import com.mediatek.galleryfeature.video.SlideVideoUtils;
import com.mediatek.galleryframework.gl.GLIdleExecuter;
import com.mediatek.galleryframework.gl.MExtTexture;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MStringTexture;
import com.mediatek.galleryframework.gl.GLIdleExecuter.GLIdleCmd;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.res.Resources;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.os.Handler;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnTimedTextListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.media.MediaPlayer.TrackInfo;
import android.media.Metadata;
import android.net.Uri;
import android.widget.Toast;
import android.view.Surface;
import android.view.WindowManager;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;

public class MtkVideoTexture extends VideoSurfaceTexture implements ScreenModeListener,
        IMtkVideoController, IVideoTexture {
    private static final String TAG = "Gallery2/VideoPlayer/MtkVideoTexture";
    private static final int UNKNOWN = -1;
    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;
    private float[] mTransformForCropingCenter = new float[16];
    private float[] mTransformFromSurfaceTexture = new float[16];
    private float[] mTransformFinal = new float[16];
    private float[] mTransform = new float[16];
    private static final int TEXTURE_HEIGHT = 1024;
    private static final int TEXTURE_WIDTH = 1024;
    private volatile boolean mIsFrameAvailable;
    private boolean mIsAudioOnly;
    private final Object mLockMediaPlayerRelease = new Object();

    // mCurrentState is a VideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;
    private int mVideoWidth;
    private int mVideoHeight;
    private boolean mIsIllegalState;

    private MediaPlayer mMediaPlayer = null;
    private OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private OnErrorListener mOnErrorListener;
    private OnInfoListener mOnInfoListener;
    private OnErrorListener mErrorListener;
    private OnVideoSizeChangedListener mVideoSizeListener;
    private OnVideoSizeChangedListener mSizeChangedListener;

    private Context mContext;
    private int mSeekWhenPrepared; // recording the seek position while
    // preparing
    private boolean mCanPause;
    private boolean mCanSeekBack;
    private boolean mCanSeekForward;
    // for slow motion.
    private int mSlowMotionSpeed = 0;
    private String mSlowMotionSection;
    private boolean mEnableSlowMotionSpeed = false;
    private static int KEY_SLOW_MOTION_SPEED = 1800;
    private static int KEY_SLOW_MOTION_SECTION = 1900;

    private static int MEDIA_ERROR_BASE = -1000;
    private static int ERROR_BUFFER_DEQUEUE_FAIL = MEDIA_ERROR_BASE - 100 - 6;
    private Map<String, String> mHeaders;
    private int mDuration;
    private Uri mUri;

    private SurfaceTexture mSurfaceTexture;
    private Surface surface;
    private volatile boolean mNeedSaveFrame = false;
    private int messageId;
    private int mPosition = 0;
    // Whether the start() method has been called
    private boolean mStartRun = false;
    private final Object releaseLock = new Object();
    
    private boolean mIsInFilmMode = false;
    private MStringTexture mAudioOnlyText;

    private int mVideoScreenMode = ScreenModeManager.SCREENMODE_BIGSCREEN;

    protected MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                    if (mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(mMediaPlayer);
                    }
                }
            };

    private final MediaPlayer.OnInfoListener mInfoListener = new MediaPlayer.OnInfoListener() {

        public boolean onInfo(final MediaPlayer mp, final int what, final int extra) {
            MtkLog.v(TAG, "onInfo() what:" + what + " extra:" + extra + "mStartRun = " + mStartRun);
            // video and audio not support will tell ap layer from onInfo,so monitor this error at here
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_NOT_SUPPORTED) {
                messageId = com.android.gallery3d.R.string.VideoView_info_text_video_not_supported;
            } else if (what == MediaPlayer.MEDIA_INFO_AUDIO_NOT_SUPPORTED) {
                messageId = com.android.gallery3d.R.string.audio_not_supported;
            }
            if (!mStartRun) {
                return true;
            }
            if (mOnInfoListener != null && mOnInfoListener.onInfo(mp, what, extra)) {
                return true;
            }
            return false;
        }

    };
    private Handler mHandler;

    public MtkVideoTexture(final Context context) {
        mContext = context;
        mHandler = new Handler(mContext.getMainLooper());
        mAudioOnlyText = MStringTexture.newInstance(mContext.getString(R.string.audio_only_video),
                mContext.getResources().getDimensionPixelSize(R.dimen.albumset_title_font_size),
                Color.WHITE);
        initialize();
    }

    /**
     * Register a callback to be invoked when the media file is loaded and ready
     * to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file has been
     * reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs during playback or
     * setup. If no listener is specified, or if the listener returned false,
     * VideoView will inform the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(OnErrorListener l) {
        mOnErrorListener = l;
    }

    public boolean isInPlaybackState() {
        MtkLog.v(TAG, "isInPlaybackState mMediaPlayer " + mMediaPlayer + " mCurrentState "
                + mCurrentState);
        return (mMediaPlayer != null && mCurrentState != STATE_ERROR && mCurrentState != STATE_IDLE && mCurrentState != STATE_PREPARING);
    }

    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    public boolean canPause() {
        return mCanPause;
    }

    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    public void setSystemUiVisibility(int visibility) {
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            MtkLog.i(TAG, "stopPlayback  mMediaPlayer != null ");
            synchronized (mLockMediaPlayerRelease) {
                if (mMediaPlayer != null) {
                    mMediaPlayer.reset();
                    mMediaPlayer.release();
                    mMediaPlayer = null;
                }
            }
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
        }
    }

    public void setOnSystemUiVisibilityChangeListener(OnSystemUiVisibilityChangeListener l) {

    }

    public void setDuration(final int duration) {
        MtkLog.v(TAG, "setDuration(" + duration + ")");
        mDuration = (duration > 0 ? -duration : duration);
    }

    public void seekTo(final int msec) {
        MtkLog.v(TAG, "seekTo(" + msec + ") isInPlaybackState()=" + isInPlaybackState());
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    public void setSlowMotionSpeed(int speed) {
        MtkLog.v(TAG, "setSlowMotionSpeed(" + speed + ") mEnableSlowMotionSpeed = "
                + mEnableSlowMotionSpeed);
        if (mMediaPlayer != null && mEnableSlowMotionSpeed && speed != 0) {
            mMediaPlayer.setParameter(KEY_SLOW_MOTION_SPEED, speed);
        }
        mSlowMotionSpeed = speed;
    }

    public void setSlowMotionSection(String section) {
        MtkLog.v(TAG, "setSlowMotionSection(" + section + ")");
        if (mMediaPlayer != null) {
            mMediaPlayer.setParameter(KEY_SLOW_MOTION_SECTION, section);
        } else {
            mSlowMotionSection = section;
        }
    }

    public void enableSlowMotionSpeed() {
        MtkLog.v(TAG, "enableSlowMotionSpeed mEnableSlowMotionSpeed " + mEnableSlowMotionSpeed);
        if (!mEnableSlowMotionSpeed) {
            mEnableSlowMotionSpeed = true;
            setSlowMotionSpeed(mSlowMotionSpeed);
        }
    }

    public void disableSlowMotionSpeed() {
        MtkLog.v(TAG, "disableSlowMotionSpeed mEnableSlowMotionSpeed " + mEnableSlowMotionSpeed);
        if (mEnableSlowMotionSpeed) {
            if (mMediaPlayer != null) {
                mMediaPlayer.setParameter(KEY_SLOW_MOTION_SPEED, 1);
            }
            mEnableSlowMotionSpeed = false;
        }
    }

    public boolean isCurrentPlaying() {
        MtkLog.v(TAG, "isCurrentPlaying() mCurrentState=" + mCurrentState);
        return mCurrentState == STATE_PLAYING;
    }

    public int getCurrentPosition() {
        int position = 0;
        if (isInPlaybackState()) {
            position = mMediaPlayer.getCurrentPosition();
        } else if (mSeekWhenPrepared > 0) {
            position = mSeekWhenPrepared;
        }
        MtkLog.v(TAG, "getCurrentPosition() return " + position + ", mSeekWhenPrepared="
                + mSeekWhenPrepared);
        return position;
    }

    public int getDuration() {
        final boolean inPlaybackState = isInPlaybackState();
        MtkLog.v(TAG, "getDuration() mDuration=" + mDuration + ", inPlaybackState="
                + inPlaybackState);
        if (inPlaybackState) {
            if (mDuration > 0) {
                return mDuration;
            }
            mDuration = mMediaPlayer.getDuration();
            MtkLog.v(TAG, "getDuration() when mDuration<0, mMediaPlayer.getDuration() is "
                    + mDuration);
            return mDuration;
        }
        return mDuration;
    }

    public void setResumed(final boolean resume) {
        // do nothing
    }

    public void clearDuration() {
        MtkLog.v(TAG, "clearDuration() mDuration=" + mDuration);
        mDuration = -1;
    }

    /**
     * clear the seek position any way,this will effect the case: stop video
     * before it's seek completed.
     */
    public void clearSeek() {
        MtkLog.v(TAG, "clearSeek() mSeekWhenPrepared=" + mSeekWhenPrepared);
        mSeekWhenPrepared = 0;
    }

    public int selectTrack(int index) {
        return 0;
    }

    public void deselectTrack(int index) {

    }

    public TrackInfo[] getTrackInfo() {
        return null;
    }

    public void dump() {
        MtkLog
                .v(TAG, "dump() mUri=" + mUri + ", mTargetState=" + mTargetState
                        + ", mCurrentState=" + mCurrentState + ", mSeekWhenPrepared="
                        + mSeekWhenPrepared + ", mVideoWidth=" + mVideoWidth + ", mVideoHeight="
                        + mVideoHeight + ", mMediaPlayer=" + mMediaPlayer);
    }

    private final String errorDialogTag = "ERROR_DIALOG_TAG";
    private FragmentManager fragmentManager;

    public void dismissAllowingStateLoss() {
        if (fragmentManager == null) {
            fragmentManager = ((Activity) mContext).getFragmentManager();
        }
        DialogFragment oldFragment =
                (DialogFragment) fragmentManager.findFragmentByTag(errorDialogTag);
        if (null != oldFragment) {
            oldFragment.dismissAllowingStateLoss();
        }
    }

    @Override
    public void setVisibility(int visibility) {

    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public boolean postDelayed(Runnable action, long delayMillis) {
        return false;
    }

    @Override
    public boolean removeCallbacks(Runnable action) {
        return false;
    }

    @Override
    public void setVideoURI(Uri uri, Map<String, String> headers) {
        MtkLog.v(TAG, "setVideoURI(" + uri + ", " + headers + ")");
        mDuration = -1;
        setResumed(true);
        mUri = uri;
        mHeaders = headers;
        mSeekWhenPrepared = 0;
        openVideo();
    }

    @Override
    public void setOnTouchListener(OnTouchListener l) {
    }

    @Override
    public boolean isTargetPlaying() {
        return false;
    }

    @Override
    public void setOnTimedTextListener(OnTimedTextListener l) {
    }

    @Override
    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener l) {
        mVideoSizeListener = l;
        MtkLog.i(TAG, "setOnVideoSizeChangedListener(" + l + ")");
    }

    @Override
    public void setOnBufferingUpdateListener(OnBufferingUpdateListener l) {

    }

    @Override
    public void setOnInfoListener(OnInfoListener l) {
        mOnInfoListener = l;
        MtkLog.v(TAG, "setInfoListener(" + l + ")");
    }

    private ScreenModeManager mScreenModeManager;

    @Override
    public void setScreenModeManager(ScreenModeManager manager) {
        mScreenModeManager = manager;
        MtkLog.v(TAG, "setScreenModeManager(" + manager + ")");
        if (mScreenModeManager != null) {
            mScreenModeManager.addListener(this);
        }
    }

    @Override
    public void onScreenModeChanged(final int newMode) {
        MtkLog.v(TAG, "setScreenModeManager(" + newMode + ")");
        mVideoScreenMode = newMode;
        mListener.onFrameAvailable();
    }

    private void clearVideoInfo() {
        MtkLog.v(TAG, "clearVideoInfo()");
    }

    protected void release(boolean cleartargetstate) {
        if (mMediaPlayer != null) {
            MtkLog.i(TAG, "release  mMediaPlayer != null ");
            mCurrentState = STATE_IDLE;
            synchronized (mLockMediaPlayerRelease) {
                if (mMediaPlayer != null) {
                    mMediaPlayer.reset();
                    mMediaPlayer.release();
                    mMediaPlayer = null;
                }
            }
            if (cleartargetstate) {
                mTargetState = STATE_IDLE;
                if (mNeedSaveFrame) {
                    /*
                     * SlideVideoUtils.saveSurfaceTexture(mSurfaceTexture,
                     * mExtTexture, mVideoWidth, mVideoHeight, null, null);
                     */
                }
                try {
                    synchronized (releaseLock) {
                        releaseSurfaceTexture(true);
                    }
                } catch (Exception e) {
                    // catch all the exception,when abnormal behavior happen,use
                    // this exception to analyze.
                    MtkLog.e(TAG, "release mExtTexture is " + mExtTexture);
                }
            }
        }
    }

    protected void openVideo() {
        MtkLog.v(TAG, "openVideo() mUri=" + mUri + ", mSeekWhenPrepared=" + mSeekWhenPrepared
                + ", mMediaPlayer=" + mMediaPlayer );
        clearVideoInfo();
        if (mUri == null) {
            // not ready for playback just yet, will try again later
            return;
        }
        release(false);
        if ("".equalsIgnoreCase(String.valueOf(mUri))) {
            MtkLog.w(TAG, "Unable to open content: " + mUri);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        }
        try {
            mMediaPlayer = new MediaPlayer();
            mIsIllegalState = false;
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setDataSource(mContext, mUri, mHeaders);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            if (mEnableSlowMotionSpeed && mSlowMotionSpeed != 0) {
                MtkLog.i(TAG, "set slow motion speed when open video " + mSlowMotionSpeed);
                mMediaPlayer.setParameter(KEY_SLOW_MOTION_SPEED, mSlowMotionSpeed);
            }
            if (mSlowMotionSection != null) {
                MtkLog.i(TAG, "set slow motion section when open video " + mSlowMotionSection);
                mMediaPlayer.setParameter(KEY_SLOW_MOTION_SECTION, mSlowMotionSection);
            }
            mCurrentState = STATE_PREPARING;
        } catch (final IOException ex) {
            mIsIllegalState = true;
            MtkLog.w(TAG, "IOException, Unable to open content: " + mUri, ex);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (final IllegalArgumentException ex) {
            mIsIllegalState = true;
            MtkLog.w(TAG, "IllegalArgumentException, Unable to open content: " + mUri, ex);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (final SQLiteException ex) {
            mIsIllegalState = true;
            MtkLog.w(TAG, "SQLiteException, Unable to open content: " + mUri, ex);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (final IllegalStateException ex) {
            mIsIllegalState = true;
            MtkLog.w(TAG, "IllegalStateException, Unable to open content: " + mUri, ex);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        }
        MtkLog.v(TAG, "openVideo() mUri=" + mUri + ", mSeekWhenPrepared=" + mSeekWhenPrepared
                + ", mMediaPlayer=" + mMediaPlayer);
    }

    private final Object theLockObject = new Object();

    private void doPrepared(final MediaPlayer mp) {
        mCurrentState = STATE_PREPARED;
        if (mOnPreparedListener != null) {
            mOnPreparedListener.onPrepared(mMediaPlayer);
        }
        mVideoWidth = mp.getVideoWidth();
        mVideoHeight = mp.getVideoHeight();
        if (mVideoWidth != 0 && mVideoHeight != 0) {
            setSize(mVideoWidth, mVideoHeight);
        }
        getDuration();
        final int seekToPosition = mSeekWhenPrepared;
        if (seekToPosition != 0) {
            seekTo(seekToPosition);
        }
        MtkLog.v(TAG, "doPrepared() end video size: " + mVideoWidth + "," + mVideoHeight
                + ", mTargetState=" + mTargetState + ", mCurrentState=" + mCurrentState);
    }

    private void initialize() {
        mErrorListener = new MediaPlayer.OnErrorListener() {
            public boolean onError(final MediaPlayer mp, final int frameworkErr, final int implErr) {
                MtkLog.d(TAG, "Error: " + frameworkErr + "," + implErr);
                if (mCurrentState == STATE_ERROR) {
                    MtkLog.w(TAG, "Duplicate error message,the message has been sent! " + "error=("
                            + frameworkErr + "," + implErr + ")");
                    return true;
                }

                mSeekWhenPrepared = getCurrentPosition();
                mDuration = Math.abs(mDuration);
                mCurrentState = STATE_ERROR;
                mTargetState = STATE_ERROR;
                final Resources r = mContext.getResources();
                if (frameworkErr == MEDIA_ERROR_BAD_FILE) {
                    if (implErr == ERROR_BUFFER_DEQUEUE_FAIL) {
                        return true;
                    }
                    messageId = com.mediatek.R.string.VideoView_error_text_bad_file;
                } else if (frameworkErr == MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER) {
                    messageId = com.mediatek.R.string.VideoView_error_text_cannot_connect_to_server;
                } else if (frameworkErr == MEDIA_ERROR_TYPE_NOT_SUPPORTED) {
                    messageId = com.mediatek.R.string.VideoView_error_text_type_not_supported;
                } else if (frameworkErr == MEDIA_ERROR_DRM_NOT_SUPPORTED) {
                    messageId = com.mediatek.R.string.VideoView_error_text_drm_not_supported;
                } else if (frameworkErr == MEDIA_ERROR_INVALID_CONNECTION) {
                    messageId = com.mediatek.internal.R.string.VideoView_error_text_invalid_connection;
                } else if (frameworkErr == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                    messageId = com.android.internal.R.string.VideoView_error_text_invalid_progressive_playback;
                } else {
                    messageId = com.android.internal.R.string.VideoView_error_text_unknown;
                }
                return true;
            }
        };

        mSizeChangedListener = new MediaPlayer.OnVideoSizeChangedListener() {
            public void onVideoSizeChanged(final MediaPlayer mp, final int width, final int height) {
                MtkLog.v(TAG, "onVideoSizeChanged() mCurrentState=" + mCurrentState
                        + "mStartRun = " + mStartRun);
                if (!mStartRun || !isInPlaybackState()) {
                    return;
                }
                mVideoWidth = mp.getVideoWidth();
                mVideoHeight = mp.getVideoHeight();
                MtkLog.v(TAG, "onVideoSizeChanged(" + width + "," + height + ")");
                MtkLog.v(TAG, "onVideoSizeChanged(" + mVideoWidth + "," + mVideoHeight + ")");
                if (width != 0 && height != 0) {
                    mIsAudioOnly = false;
                } else {
                    mIsAudioOnly = true;
                    mListener.onFrameAvailable();
                }
                if (mVideoSizeListener != null) {
                    mVideoSizeListener.onVideoSizeChanged(mp, width, height);
                }
            }
        };
    }

    public void onPrepared() {
        MtkLog.v(TAG, "onPrepared(" + mMediaPlayer + ")");
        Metadata data = mMediaPlayer.getMetadata(MediaPlayer.METADATA_ALL,
                MediaPlayer.BYPASS_METADATA_FILTER);
        if (data != null) {
            mCanPause = !data.has(Metadata.PAUSE_AVAILABLE)
                    || data.getBoolean(Metadata.PAUSE_AVAILABLE);
            mCanSeekBack = !data.has(Metadata.SEEK_BACKWARD_AVAILABLE)
                    || data.getBoolean(Metadata.SEEK_BACKWARD_AVAILABLE);
            mCanSeekForward = !data.has(Metadata.SEEK_FORWARD_AVAILABLE)
                    || data.getBoolean(Metadata.SEEK_FORWARD_AVAILABLE);
        } else {
            mCanPause = true;
            mCanSeekBack = true;
            mCanSeekForward = true;
            MtkLog.w(TAG, "data is null!");
        }
        MtkLog.v(TAG, "onPrepared() mCanPause=" + mCanPause);
        doPrepared(mMediaPlayer);
    }
    @Override
    public String getStringParameter(int key) {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getStringParameter(key);
        }
        return null;
    }
    
    @Override
    public boolean setParameter(int key, String value) {
        if(mMediaPlayer != null) {
            return mMediaPlayer.setParameter(key,value);
        }
        return false;
    }

    public boolean draw(MGLCanvas canvas, int x, int y, int width, int height) {
        // width and height is the video width and height ,but width is scale to
        // the screen width.
        // No image text will be shown when play an audio only video in film mode
        if (mIsInFilmMode && mIsAudioOnly) {
            canvas.fillRect(x, y, width, height,
                    mContext.getResources().getColor(R.color.photo_placeholder));
            mAudioOnlyText.draw(canvas, width / 2 - mAudioOnlyText.getWidth() / 2, height / 2
                    - mAudioOnlyText.getHeight() / 2);
            return true;
        }
        try {
            synchronized (releaseLock) {
                if (mIsFrameAvailable && mSurfaceTexture != null) {
                    mSurfaceTexture.updateTexImage();
                } else {
                    return false;
                }
            }
        } catch (IllegalStateException e) {
            MtkLog.e(TAG, "onFrameAvailable GL cotext has been destroyed");
            return false;
        }
        synchronized (this) {
            if (mSurfaceTexture == null || !mIsFrameAvailable) {
                MtkLog.w(TAG, "draw mSurfaceTexture is " + mSurfaceTexture
                        + " mIsFrameAvailable is" + mIsFrameAvailable);
            }
            mSurfaceTexture.getTransformMatrix(mTransform);
            canvas.save(MGLCanvas.SAVE_FLAG_MATRIX);
            int cx = width / 2;
            int cy = height / 2;
            canvas.translate(cx, cy);
            int screenWidth = canvas.getWidth();
            int screenHeight = canvas.getHeight();
            if (!mIsInFilmMode) {
                // get draw width and height by screen mode
                switch (mVideoScreenMode) {
                case ScreenModeManager.SCREENMODE_BIGSCREEN:
                    if (width <= screenWidth && height <= screenHeight) {
                        if (mVideoWidth * screenHeight > screenWidth
                                * mVideoHeight) {
                            height = screenWidth * mVideoHeight / mVideoWidth;
                            width = screenWidth;
                        } else if (mVideoWidth * screenHeight < screenWidth
                                * mVideoHeight) {
                            width = screenHeight * mVideoWidth / mVideoHeight;
                            height = screenHeight;
                        }
                    }
                    break;
                case ScreenModeManager.SCREENMODE_FULLSCREEN:
                    width = screenWidth;
                    height = screenHeight;
                    break;
                case ScreenModeManager.SCREENMODE_CROPSCREEN:
                    if (mVideoWidth * screenHeight > screenWidth * mVideoHeight) {
                        // extend width to be cropped
                        width = screenHeight * mVideoWidth / mVideoHeight;
                        height = screenHeight;
                    } else if (mVideoWidth * screenHeight < screenWidth
                            * mVideoHeight) {
                        // extend height to be cropped
                        height = screenWidth * mVideoHeight / mVideoWidth;
                        width = screenWidth;
                    }
                    break;
                }
            }
            canvas.scale(1, -1, 1);
            cx = width / 2;
            cy = height / 2;
            canvas.translate(-cx, -cy);
            canvas.drawTexture(mExtTexture, mTransform, 0, 0, width, height);
            canvas.restore();
            mNeedSaveFrame = true;
        }
        return true;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        MtkLog.v(TAG, "onFrameAvailable entry");
        mIsFrameAvailable = true;
        mListener.onFrameAvailable();
    }

    @Override
    public void start() {
        MtkLog.v(TAG, "start() mUri = " + mUri);
        startVideo();
        //Set screen on flag when start to play video.
        ((Activity) mContext).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((Activity) mContext).getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    @Override
    public void onStart() {
        MtkLog.v(TAG, "onStart() messageId = " + messageId + " mUri = " + mUri);
        if (messageId != 0) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mVideoSizeListener.onVideoSizeChanged(mMediaPlayer, 1, 1);
                    String message = mContext.getString(messageId);
                    Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
        startVideo();
    }

    @Override
    public void prepare() {
        MtkLog.v(TAG, "prepare player start");
        if (mMediaPlayer == null || mIsIllegalState) {
            MtkLog.v(TAG, "mediaPlayer is null and mIsIllegalState is " + mIsIllegalState
                    + " cancle prepare");
            return;
        }
        if (mSurfaceTexture == null) {
            setSize(UNKNOWN, UNKNOWN);
            if (mGLIdleExecuter != null) {
                mGLIdleExecuter.addOnGLIdleCmd(new GLIdleCmd() {
                    public boolean onGLIdle(MGLCanvas canvas) {
                        MtkLog.v(TAG, "prepare onGLIdle");
                        acquireSurfaceTexture(canvas);
                        mSurfaceTexture = getSurfaceTexture();
                        surface = new Surface(mSurfaceTexture);
                        synchronized (theLockObject) {
                            theLockObject.notifyAll();
                        }
                        return false;
                    }
                });
            }
        }
        synchronized (theLockObject) {
            while (surface == null) {
                try {
                    theLockObject.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
            }
        }
        mMediaPlayer.setSurface(surface);
        try {
            // mSeekWhenPrepared = 0 means start play from begin
            // mSeekWhenPrepared so should set
            // messageId = 0,messageId = 0 means idle
            // when replay a bad file should use this
            if (mSeekWhenPrepared == 0) {
                messageId = 0;
            }
            mMediaPlayer.prepare();
            if (mSeekWhenPrepared >= 0) {
                mPosition = mSeekWhenPrepared;
            }
            mCurrentState = STATE_PREPARED;
            onPrepared();
        } catch (final IOException ex) {
            MtkLog.e(TAG, "IOException unable to open content: " + mUri, ex);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (final IllegalArgumentException ex) {
            MtkLog.e(TAG, "IllegalArgumentException unable to open content: " + mUri, ex);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (final SQLiteException ex) {
            MtkLog.e(TAG, "SQLiteException u to open content: " + mUri, ex);
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        }
    }

    @Override
    public void onPause() {
        pauseVideo();
    }

    @Override
    public void pause() {
        pauseVideo();
        //Clear screen on flag when pause a video.
        ((Activity) mContext).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((Activity) mContext).getWindow().clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    @Override
    public int getPlayPosition(boolean isActivityPaused) {
        MtkLog.v(TAG, "getPlayPosition mCurrentState  = " + mCurrentState
                + " ,isActivityPaused is " + isActivityPaused);
        // When gallery activity paused called at the video's complete
        // state(such as press home key),the position should be the completion
        // position so that the video can keep paused at the last frame.
        if (mCurrentState == STATE_PLAYBACK_COMPLETED && !isActivityPaused) {
            return 0;
        } else {
            return getCurrentPosition();
        }
    }
    
    @Override
    public void setPlayPosition(int position) {
        seekTo(position);
    }
    
    @Override
    public void release() {
        release(true);
    }

    @Override
    public MExtTexture getTexture(MGLCanvas canvas) {
        synchronized (releaseLock) {
            // mExtTexture should be return so that gallery will use this
            // mExtTexture to draw audio only text.
            if (mIsInFilmMode && mIsAudioOnly) {
                return mExtTexture;
            }
            if (mIsFrameAvailable && mSurfaceTexture != null) {
                return mExtTexture;
            } else {
                return null;
            }
        }
    }

    private Listener mListener;
    private GLIdleExecuter mGLIdleExecuter;

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void setGLIdleExecuter(GLIdleExecuter ext) {
        mGLIdleExecuter = ext;
    }
    
    @Override
    public void onFilmModeChange(boolean isFilmMode) {
        MtkLog.v(TAG, "onFilmModeChange isFilmMode = " + isFilmMode);
        mIsInFilmMode = isFilmMode;
    }

    @Override
    public boolean isInFilmMode() {
        return mIsInFilmMode;
    }
    
    private void startVideo() {
        if (mVideoSizeListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mVideoSizeListener.onVideoSizeChanged(mMediaPlayer, mVideoWidth, mVideoHeight);
                }
            });
        }
        // stopPlayback will release media Player and set mCurrentState =
        // STATE_IDLE
        // so if want to play this video again should call openVideo() and
        // prepare() before mMediaPlayer.start()
        if (mCurrentState == STATE_IDLE) {
            openVideo();
            prepare();
        }
        if (isInPlaybackState()) {
            mStartRun = true;
            mMediaPlayer.start();
            if (mPosition > 0) {
                mMediaPlayer.seekTo(mPosition);
                mPosition = 0;
            }
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }

    private void pauseVideo() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
    }
}
