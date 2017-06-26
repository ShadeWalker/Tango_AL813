/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.gallery3d.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.TrackInfo;
import android.media.TimedText;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Virtualizer;

import android.media.Metadata;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.view.animation.Animation;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.ImageView;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.BlobCache;
import com.android.gallery3d.util.CacheManager;
import com.android.gallery3d.util.GalleryUtils;
import com.mediatek.gallery3d.ext.DefaultMovieItem;
import com.mediatek.gallery3d.ext.IActivityHooker;
import com.mediatek.gallery3d.ext.IMovieItem;
import com.mediatek.gallery3d.ext.IRewindAndForwardExtension;
import com.mediatek.gallery3d.ext.IServerTimeoutExtension;
import com.mediatek.gallery3d.ext.MovieUtils;
import com.mediatek.gallery3d.video.ActivityHookerGroup;
import com.mediatek.gallery3d.video.BookmarkEnhance;
import com.mediatek.gallery3d.video.DetailDialog;
import com.mediatek.gallery3d.video.ErrorDialogFragment;
import com.mediatek.gallery3d.video.ExtensionHelper;
import com.mediatek.gallery3d.video.IMtkVideoController;
import com.mediatek.gallery3d.video.IContrllerOverlayExt;
import com.mediatek.gallery3d.video.IMovieDrmExtension;
import com.mediatek.gallery3d.video.IMovieDrmExtension.IMovieDrmCallback;
import com.mediatek.gallery3d.video.IMoviePlayer;
import com.mediatek.gallery3d.video.MTKVideoView;
import com.mediatek.gallery3d.video.MtkVideoFeature;
import com.mediatek.gallery3d.video.MtkVideoTexture;
import com.mediatek.gallery3d.video.ScreenModeManager;
import com.mediatek.gallery3d.video.SlowMotionItem;
import com.mediatek.gallery3d.video.ScreenModeManager.ScreenModeListener;
import com.mediatek.gallery3d.video.VideoZoomController;
import com.mediatek.gallery3d.video.WfdPowerSaving;
import com.mediatek.gallery3d.video.VideoHookerCtrlImpl;

// M: FOR MTK_SUBTITLE_SUPPORT
import com.mediatek.gallery3d.video.SubTitleView;
import com.mediatek.gallery3d.video.SubtitleSettingDialog;
//@}
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryfeature.SlideVideo.IVideoPlayer;
import com.mediatek.galleryfeature.SlideVideo.IVideoTexture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class MoviePlayer implements
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener,
        ControllerOverlay.Listener,
        MediaPlayer.OnBufferingUpdateListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnInfoListener,
        MediaPlayer.OnVideoSizeChangedListener,
        MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnTimedTextListener {   //M: ADD FOR MTK_SUBTITLE_SUPPORT
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/MoviePlayer";
    private static final String TEST_CASE_TAG = "Gallery2PerformanceTestCase2";

    private static final String KEY_VIDEO_POSITION = "video-position";
    private static final String KEY_RESUMEABLE_TIME = "resumeable-timeout";

    ///M: MTK_AUDIO_CHANGE_SUPPORT & MTK_SUBTITLE_SUPPORT
    ///@{
    private static final String KEY_SELECT_AUDIO_INDEX = "select-audio-index";
    private static final String KEY_SELECT_AUDIO_TRACK_INDEX = "select-audio-track-index";
    private static final String KEY_SELECT_SUBTITLE_INDEX = "select-subtitle-index";
    private static final String KEY_SELECT_SUBTITLE_TRACK_INDEX = "select-subtitle-track-index";
    ///@}

    // Copied from MediaPlaybackService in the Music Player app.
    private static final String SERVICECMD = "com.android.music.musicservicecommand";
    private static final String CMDNAME = "command";
    private static final String CMDPAUSE = "pause";
    private static final String ASYNC_PAUSE_PLAY = "MTK-ASYNC-RTSP-PAUSE-PLAY";
    /// M: for more detail in been killed case @{
    private static final String KEY_CONSUMED_DRM_RIGHT = "consumed_drm_right";
    private static final String KEY_POSITION_WHEN_PAUSED = "video_position_when_paused";
    private static final String KEY_VIDEO_CAN_SEEK = "video_can_seek";
    private static final String KEY_VIDEO_CAN_PAUSE = "video_can_pause";
    private static final String KEY_VIDEO_LAST_DURATION = "video_last_duration";
    private static final String KEY_VIDEO_LAST_DISCONNECT_TIME = "last_disconnect_time";
    private static final String KEY_VIDEO_STATE = "video_state";
    private static final String KEY_VIDEO_STREAMING_TYPE = "video_streaming_type";
    private static final String KEY_VIDEO_CURRENT_URI = "video_current_uri";

    private static final int RETURN_ERROR = -1;
    private static final int NONE_TRACK_INFO = -1;
    private static final int TYPE_TRACK_INFO_BOTH = -1;
    private static final int ERROR_CANNOT_CONNECT = -1003;
    private static final int ERROR_FORBIDDEN = -1100;
    private static final int ERROR_INVALID_OPERATION = -38;
    private static final int ERROR_ALREADY_EXISTS = -17;
    // These are constants in KeyEvent, appearing on API level 11.
    private static final int KEYCODE_MEDIA_PLAY = 126;
    private static final int KEYCODE_MEDIA_PAUSE = 127;
    private static final long BLACK_TIMEOUT = 500;
    // /M:try to connecting the server 1500ms later
    private static final long RETRY_TIME = 1500;
    // If we resume the acitivty with in RESUMEABLE_TIMEOUT, we will keep playing.
    // Otherwise, we pause the player.
    private static final long RESUMEABLE_TIMEOUT = 3 * 60 * 1000; // 3 mins
    private long mResumeableTime = Long.MAX_VALUE;

    /// M: for log flag, if set this false, will improve run speed.
    private static final boolean LOG = true;
    private Activity mActivityContext; //for dialog and toast context
    /// M: add for streaming cookie
    private String mCookie = null;
    private Context mContext;
    private final IMtkVideoController mVideoView;
    private final View mRootView;
    private final View mVideoRoot;
    private final Bookmarker mBookmarker;
    //private Uri mUri;
    private final Handler mHandler;
    private final AudioBecomingNoisyReceiver mAudioBecomingNoisyReceiver;
    private WfdConnectReceiver mWfdConnectReceiver;
    private MovieControllerOverlay mController;

    private int mVideoPosition = 0;
    private int mLastSystemUiVis = 0;
    //M: MTK_AUDIO_CHANGE_SUPPORT & MTK_SUBTITLE_SUPPORT//@{
    private int mSelectAudioIndex = 0; //single choice index in dialog
    private int mSelcetAudioTrackIdx = 0; //index in all track info
    private int mSelectSubTitleIndex = 0; //single choice index in dialog
    private int mSelectSubTitleTrackIdx = 0;  //index in all track info
    private AlertDialog mSubtitleSetDialog;
    private SubTitleView mSubTitleView;
    private ImageView mSubTitleViewBm;
    //@}

    ///M: Add for Video Zoom @{
    private VideoZoomController mZoomController;
    private final Runnable mHideSystemUIRunnable;
    private final static int HIDE_SYSTEM_UI_DELAY = 3500; //ms
    ///@}

    // /M: mHideActionBarRunnable is for action bar not disappear
    // after press power key quickly.
    private final Runnable mHideActionBarRunnable;
    // /@}

    private boolean mIsLivePhoto = false;
    private int mVideoLastDuration; //for duration displayed in init state
    // /M: Add for pre-sanity test case @{
    public static boolean mIsVideoPlaying = false;
    // /@}
    private PlayPauseProcessExt mPlayPauseProcessExt = new PlayPauseProcessExt();
    private boolean mFirstBePlayed = false; //for toast more info
    private boolean mVideoCanPause = false;
    private boolean mVideoCanSeek = false;
    private boolean mCanReplay;
    private boolean mHasPaused = false;
    private boolean mError = false;
    // If the time bar is being dragged.
    private boolean mDragging;
    // If the time bar is visible.
    private boolean mShowing;
    /// M: add for control Action bar first open show 500ms @{
    private boolean mIsDelayFinish = false;
    private static final long SHOW_ACTIONBAR_TIME = 500;
    /// @}
    // M: the type of the video
    private int mVideoType = MovieUtils.VIDEO_TYPE_LOCAL;

    ///M: for wfd power saving.
    private static WfdPowerSaving mWfdPowerSaving;
    ///M: for hotKnot
    private final static int HOTKNOT_POSITION_SEEK = 100;
    private boolean mHotKnotIntent = false;

    private TState mTState = TState.PLAYING;
    private IMovieItem mMovieItem;
    private RetryExtension mRetryExt = new RetryExtension();
    private ScreenModeExt mScreenModeExt = new ScreenModeExt();
    private IServerTimeoutExtension mServerTimeoutExt = ExtensionHelper.getServerTimeout();
    public MoviePlayerExtension mPlayerExt = new MoviePlayerExtension();
    /// M: [FEATURE.ADD] SlideVideo@{
    public SlideVideoExt mSlideVideoExt =  new SlideVideoExt();
    /// @}
    private IContrllerOverlayExt mOverlayExt;
    private IRewindAndForwardExtension mControllerRewindAndForwardExt;
    private static final String VIRTUALIZE_EXTRA = "virtualize";
    private static final String HOTKNOT_EXTRA = "hotknot";
    private Virtualizer mVirtualizer;
    // /M:Whether streaming video is buffering or not
    private boolean mIsBuffering = false;
    // /M: the position which seek move to
    private int mSeekMovePosition;
    // /M:The media details dialog is shown or not.To avoid MediaPlayerService
    // may pause fail when play RTSP,should pause video in the time buffering
    // end.
    private boolean mIsDialogShow = false;
    private View mBlackCover = null;
    private SlowMotionItem mSlowMotionItem;

    private final Runnable mPlayingChecker = new Runnable() {
        @Override
        public void run() {
            boolean isplaying = mVideoView.isPlaying();
            if (LOG) {
                Log.v(TAG, "mPlayingChecker.run() isplaying=" + isplaying + " mIsBuffering is "
                        + mIsBuffering);
            }
            // /M:Only when start command has performed as well as video is not
            // buffering should the playing information can be shown@{
            if (isplaying && !mIsBuffering) {
                // live streaming can't pause ,but showPlaying() will set right string for live streaming 
                if (mIsDialogShow && !MovieUtils.isLiveStreaming(mVideoType)) {
                    Log.v(TAG, "mPlayingChecker.run() mIsDialogShow = true");
                    mPlayerExt.pauseIfNeed();
                } else {
                    mController.showPlaying();
                }
            } else {
                mHandler.postDelayed(mPlayingChecker, 250);
            }
        }
    };

//    private final Runnable mRemoveBackground = new Runnable() {
//        @Override
//        public void run() {
//            if (LOG) {
//                Log.v(TAG, "mRemoveBackground.run()");
//            }
//            mRootView.setBackground(null);
//        }
//    };

    private final Runnable mProgressChecker = new Runnable() {
        @Override
        public void run() {
            if (mSlowMotionItem.isSlowMotionVideo()) {
                setProgress();
                mHandler.postDelayed(mProgressChecker, 1000);
            } else {
                int pos = setProgress();
                mHandler.postDelayed(mProgressChecker, 1000 - (pos % 1000));
            }
        }
    };
    
    // / M: [FEATURE.ADD] SlowMotion@{
    private final Runnable mSlowMotionChecker = new Runnable() {
        @Override
        public void run() {
            int position = mVideoView.getCurrentPosition();
            int duration = mVideoView.getDuration();
            mController.setTimes(position, duration);
            mHandler.postDelayed(mSlowMotionChecker, 100);
        }
    };
    
    private void startSlowMotionChecker() {
        if (mSlowMotionItem.isSlowMotionVideo()) {
            mHandler.removeCallbacks(mSlowMotionChecker);
            mHandler.post(mSlowMotionChecker);
        }
    }
    
    private void stopSlowMotionChecker() {
        if (mSlowMotionItem.isSlowMotionVideo()) {
            mHandler.removeCallbacks(mSlowMotionChecker);
        }
    }
    // / @}


    public MoviePlayer(View rootView, final MovieActivity movieActivity, IMovieItem info,
            Bundle savedInstance, boolean canReplay, String cookie) {
        mActivityContext = movieActivity;
        mContext = movieActivity.getApplicationContext();
        mHandler = new Handler();
        mRootView = rootView;
        mVideoRoot = rootView.findViewById(R.id.video_root);
        mVideoView = (MTKVideoView) mVideoRoot.findViewById(R.id.surface_view);

        if (MtkVideoFeature.isEnableMultiWindow()) {
            mBlackCover = rootView.findViewById(R.id.black_cover);
            mBlackCover.setVisibility(View.VISIBLE);
        }

        mBookmarker = new Bookmarker(movieActivity);
        //FOR MTK_SUBTITLE_SUPPORT
        ///@{
        if (MtkVideoFeature.isSubTitleSupport()) {
            mSubTitleView = (SubTitleView) rootView.findViewById(R.id.subtitle_view);
            mSubTitleViewBm = (ImageView) rootView.findViewById(R.id.subtitle_bitmap);
            mSubTitleView.InitFirst(mContext, mSubTitleViewBm);
        }
        ///@}
        mCookie = cookie;
        //mUri = videoUri;
        mController = new MovieControllerOverlay(movieActivity);
        movieActivity.setMovieHookerParameter(null, mPlayerExt);
        ((ViewGroup)rootView).addView(mController.getView());
        mController.setListener(this);
        mController.setCanReplay(canReplay);
        getRewindAndForwardHooker().setParameter(null, mPlayerExt);
        init(movieActivity, info, canReplay);

        mVideoView.setOnErrorListener(this);
        mVideoView.setOnCompletionListener(this);
        //we move this behavior to startVideo()
        //mVideoView.setVideoURI(mUri, null);

        mHideSystemUIRunnable = new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "hideSystemUIRunnable run()");
                showSystemUi(false, false);
                if (mWfdPowerSaving != null && !mHasPaused && mWfdPowerSaving.isPowerSavingEnable()) {
                    mWfdPowerSaving.startCountDown();
                }
            }
        };
        ///@}
        // /M: mHideActionBarRunnable is for action bar not diasppear
        // after press power key quickly.
        mHideActionBarRunnable = new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "hideActionBarRunnable run()");
                showSystemUi(false, false);
            }
        };
        // /@}

        Intent ai = movieActivity.getIntent();
        boolean virtualize = ai.getBooleanExtra(VIRTUALIZE_EXTRA, false);
        if (virtualize) {
            int session = mVideoView.getAudioSessionId();
            if (session != 0) {
                mVirtualizer = new Virtualizer(0, session);
                mVirtualizer.setEnabled(true);
            } else {
                Log.w(TAG, "no audio session to virtualize");
            }
        }
        ///M: remove it  for video zoom feature.
//        mVideoView.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
////                    mController.show();
//                return false;
//            }
//        });

        /// M: remove it for seekable is handled in onPrepred. @{
        /*mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer player) {
                if (!mVideoView.canSeekForward() || !mVideoView.canSeekBackward()) {
                    mController.setSeekable(false);
                } else {
                    mController.setSeekable(true);
                }
                setProgress();
            }
        });*/
        /// @}

        // The SurfaceView is transparent before drawing the first frame.
        // This makes the UI flashing when open a video. (black -> old screen
        // -> video) However, we have no way to know the timing of the first
        // frame. So, we hide the VideoView for a while to make sure the
        // video has been drawn on it.
        /// M: remove it for performance issue. @{
        /*mVideoView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mVideoView.setVisibility(View.VISIBLE);
            }
        }, BLACK_TIMEOUT);*/
        /// @}
        setOnSystemUiVisibilityChangeListener();
        
        enableWfdPowerSavingIfNeed();
        ///M: Add for video zoom @{
        mZoomController = new VideoZoomController(movieActivity, mVideoRoot,
                mVideoView, mController) {
            //add for wfd power saving, do the same thing as the onSystemUiVisibilityChange 
            //in case the callback not come.
            @Override
            public void onDownEvent() {
                Log.v(TAG, "onDownEvent() mShowing = " + mShowing);
                if (isPlaying() && !mShowing) {
                    if (mWfdPowerSaving != null
                            && mWfdPowerSaving.isPowerSavingEnable()) {
                        mWfdPowerSaving.cancelCountDown();
                    }
                    mHandler.removeCallbacks(mHideSystemUIRunnable);
                    mHandler.postDelayed(mHideSystemUIRunnable,
                            HIDE_SYSTEM_UI_DELAY);
                }
            }
            
            @Override
            public boolean isInWfdExtension() {
                return mWfdPowerSaving != null && mWfdPowerSaving.isInExtensionMode();
            }
        };
        //}@

        
        mAudioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver();
        mAudioBecomingNoisyReceiver.register();

        if (savedInstance != null) { // this is a resumed activity
            /// M: add for action bar don't dismiss
            mIsDelayFinish = true;
            mVideoPosition = savedInstance.getInt(KEY_VIDEO_POSITION, 0);
            mResumeableTime = savedInstance.getLong(KEY_RESUMEABLE_TIME, Long.MAX_VALUE);
            onRestoreInstanceState(savedInstance);
            mHasPaused = true;
        } else {
            // /M: for video hotKnot spec:
            //  the video should paused in first frame when completed transfer.@{
            Intent intent = mActivityContext.getIntent();
            mHotKnotIntent = intent.getBooleanExtra(HOTKNOT_EXTRA, false);
            mIsDelayFinish = true;
            if (mHotKnotIntent) {
                Log.v(TAG, "hotKnot receiver video pause");
                doStartVideo(true, HOTKNOT_POSITION_SEEK, 0, false);
                pauseVideo();
            } else {
                // Hide system UI by default
                /// M:first open need to show action bar 500ms.
                if (!mSlowMotionItem.isSlowMotionVideo()) {
                    showSystemUi(false, true); //Slow motion video will show UI for a while.
                }
                mTState = TState.PLAYING;
                mFirstBePlayed = true;
                final BookmarkerInfo bookmark = mBookmarker.getBookmark(mMovieItem.getUri());
                if (bookmark != null) {
                    showResumeDialog(movieActivity, bookmark);
                } else {
                    doStartVideoCareDrm(false, 0, 0);
                    if (mSlowMotionItem.isSlowMotionVideo()) {
                        mController.show(); //Slow motion video will show UI for a while.
                        // /M: Add for surfaceview transparent problem before 1st frame arrived.  @{
                        //TODO: maybe change to common flow.
                        mBlackCover = rootView.findViewById(R.id.black_cover);
                        mBlackCover.setVisibility(View.VISIBLE);
                        // / @}
                    }
                }
            }
        }
        mScreenModeExt.setScreenMode();
    }

    // / M: [FEATURE.ADD] SlideVideo@{
    public MoviePlayer(Context context, MediaData data) {
        mContext = context;
        mActivityContext = (Activity) context;
        mRootView = new View(context);
        mVideoRoot = new View(context);
        mVideoView = new MtkVideoTexture(context);
        mBookmarker = new Bookmarker(context);
        mHandler = new Handler(mContext.getMainLooper());
        mActivityContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mController = MovieControllerOverlay.getMovieController(mContext);
                mController.setCanReplay(false);
                
            }
        });
        mAudioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver();
        mHideSystemUIRunnable = new Runnable() {
            @Override
            public void run() {
            }
        };
        mHideActionBarRunnable = new Runnable() {
            @Override
            public void run() {
            }
        };
        
        String uri = "file://" + data.filePath;
        mMovieItem = new DefaultMovieItem(uri, data.mimeType, data.caption);
        init(null, mMovieItem, false);
        mIsLivePhoto = MovieUtils.isLivePhoto(mActivityContext, mMovieItem.getUri());
        mVideoView.setOnErrorListener(this);
        mVideoView.setOnCompletionListener(this);
        mTState = TState.PLAYING;
        mFirstBePlayed = true;
        mVideoView.setVideoURI(mMovieItem.getUri(), null);
    }
    // / @}
    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setOnSystemUiVisibilityChangeListener() {
        if (!ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_HIDE_NAVIGATION) return;

        // When the user touches the screen or uses some hard key, the framework
        // will change system ui visibility from invisible to visible. We show
        // the media control and enable system UI (e.g. ActionBar) to be visible at this point
        mRootView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                boolean finish = (mActivityContext == null ? true : mActivityContext.isFinishing());
                int diff = mLastSystemUiVis ^ visibility;
                mLastSystemUiVis = visibility;
                if ((diff & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0
                        && (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
                        || (diff & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0
                        && (visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0) {
                    if(!mHasPaused) {
                        mController.show();
                        mRootView.setBackgroundColor(Color.BLACK);
                    }
                }
                if (LOG) {
                    Log.v(TAG, "onSystemUiVisibilityChange(" + visibility + ") finishing()=" + finish);
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void showSystemUi(boolean visible, boolean isFirstOpen) {
        /// M:isFirstOpen mark for first open
        Log.v(TAG, "showSystemUi() visible " + visible + " isFirstOpen " + isFirstOpen);
        if (!ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_LAYOUT_STABLE) return;
        int flag = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                ///M: Add for video zoom, KK new method for full screen.
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        // / M: [PERF.ADD] Power Test@{
        boolean isPowerTest = SystemProperties.get("persist.power.auto.test").equals("1");
        if (isPowerTest) {
            Log.v(TAG, "showSystemUi in power test");
            flag = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        }
        // / @}
        if (!visible) {
            //We used the deprecated "STATUS_BAR_HIDDEN" for unbundling
            /// M: if first open, need to show action bar 500ms @{
            final int flagx = flag | View.STATUS_BAR_HIDDEN | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            flag = flagx;
            // /M:Delay hidden the action bar when play a streaming video
            boolean isLocalFile =
                    MovieUtils.isLocalFile(mMovieItem.getUri(), mMovieItem.getMimeType());
            if (!isLocalFile) {
                mIsDelayFinish = true;
            }
            if (isFirstOpen && isLocalFile) {
                mRootView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mRootView.setSystemUiVisibility(flagx);
                        mIsDelayFinish = true;
                        if (mWfdPowerSaving != null) {
                            mWfdPowerSaving.setSystemUiVisibility(flagx);
                        }
                    }
                }, SHOW_ACTIONBAR_TIME);
                Log.v(TAG, "first open showSystemUi() flag = " + flagx);
                return;
            }
            mRootView.setSystemUiVisibility(flag);
            if (mWfdPowerSaving != null) {
                mWfdPowerSaving.setSystemUiVisibility(flag);
            }
            Log.v(TAG, "not first open showSystemUi() flag = " + flag);
            return;
            /// @}
        }
        mRootView.setSystemUiVisibility(flag);
        if (mWfdPowerSaving != null) {
            mWfdPowerSaving.setSystemUiVisibility(flag);
        }
        Log.v(TAG, "visiable showSystemUi() flag = " + flag);
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_VIDEO_POSITION, mVideoPosition);
        outState.putLong(KEY_RESUMEABLE_TIME, mResumeableTime);
        onSaveInstanceStateMore(outState);
    }

    private void showResumeDialog(Context context, final BookmarkerInfo bookmark) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.resume_playing_title);
        builder.setMessage(String.format(
                context.getString(R.string.resume_playing_message),
                GalleryUtils.formatDuration(context, bookmark.mBookmark / 1000)));
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                onCompletion();
                mIsShowResumingDialog = false;
            }
        });
        builder.setPositiveButton(
                R.string.resume_playing_resume, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //here try to seek for bookmark
                //Note: if current video can not be sought, it will not has any bookmark.
                ///M: MTK_AUDIO_CHANGE_SUPPORT & MTK_SUBTITLE_SUPPORT
                ///@{
                  if (MtkVideoFeature.isAudioChangeSupport()) {
                      mSelectAudioIndex = bookmark.mSelectAudioIndexBmk;
                      mSelcetAudioTrackIdx = bookmark.mSelcetAudioTrackIdxBmk;
                  }
                  if (MtkVideoFeature.isSubTitleSupport()) {
                      mSelectSubTitleIndex = bookmark.mSelectSubTitleIndexBmk;
                      mSelectSubTitleTrackIdx = bookmark.mSelectSubTitleTrackIdxBmk;
                  }
                  ///@}
                mVideoCanSeek = true;
                doStartVideoCareDrm(true, bookmark.mBookmark, bookmark.mDuration);
                if (mSlowMotionItem.isSlowMotionVideo()) {
                    mController.show(); //Slow motion video will show UI for a while.
                }
                mIsShowResumingDialog = false;
                mHandler.removeCallbacks(mProgressChecker);
                mHandler.post(mProgressChecker);
                startSlowMotionChecker();
            }
        });
        builder.setNegativeButton(
                R.string.resume_playing_restart, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                doStartVideoCareDrm(true, 0, bookmark.mDuration);
                mIsShowResumingDialog = false;
                mHandler.removeCallbacks(mProgressChecker);
                mHandler.post(mProgressChecker);
                startSlowMotionChecker();
            }
        });
        builder.show();
        mIsShowResumingDialog = true;
    }

    public boolean onPause() {
        if (LOG) {
            Log.v(TAG, "onPause()");
         }

        if (mWfdPowerSaving != null) {
            mWfdPowerSaving.cancelCountDown();
            mWfdPowerSaving.unregisterReceiver();
        }
        ///M: Unregister wfd connect receiver.@{
        if (mWfdConnectReceiver != null) {
            mWfdConnectReceiver.unregister();
            mWfdConnectReceiver = null;
        }
        ///M: @}
        doOnPause();
        return true;
    }

    //we should stop video anyway after this function called.
    public void onStop() {
        if (LOG) {
            Log.v(TAG, "onStop() mHasPaused=" + mHasPaused);
        }
        if (!mHasPaused) {
            doOnPause();
        }
    }

    private void doOnPause() {
        long start = System.currentTimeMillis();
        mHasPaused = true;
        mHandler.removeCallbacksAndMessages(null);
        // /M:Cancel hiding controller when video stop play.
        mOverlayExt.onCancelHiding();
        ///M: set background black here for avoid screen maybe flash when exit MovieActivity
        mHandler.removeCallbacks(mRemoveBackground);
        mRootView.setBackgroundColor(Color.BLACK);
        int position = mVideoView.getCurrentPosition();
        mVideoPosition = position >= 0 ? position : mVideoPosition;
        Log.v(TAG, "mVideoPosition is " + mVideoPosition);
        int duration = mVideoView.getDuration();
        mVideoLastDuration = duration > 0 ? duration : mVideoLastDuration;
        ///M: MTK_SUBTITLE_SUPPORT & MTK_AUDIO_CHANGE_SUPPORT
        ///@{
        if (MtkVideoFeature.isAudioChangeSupport() || MtkVideoFeature.isSubTitleSupport()) {
            mBookmarker.setBookmark(mMovieItem.getUri(), mVideoPosition, mVideoLastDuration,
            mSelcetAudioTrackIdx, mSelectSubTitleTrackIdx, mSelectAudioIndex, mSelectSubTitleIndex);
            if (MtkVideoFeature.isSubTitleSupport()) {
            if (null != mSubTitleView) {
                    updateSubtitleView(null);
                mSubTitleView.saveSettingPara();
            }
            }
        //@}
        } else {
            mBookmarker.setBookmark(mMovieItem.getUri(), mVideoPosition, mVideoLastDuration);
        }

        long end1 = System.currentTimeMillis();
        mVideoView.stopPlayback(); //change suspend to release for sync paused and killed case
        mIsBuffering = false;
        mResumeableTime = System.currentTimeMillis() + RESUMEABLE_TIMEOUT;
        mVideoView.setResumed(false); //avoid start after surface created
        ///M: when stop play set enable in case  the pause complete callback not come.
        mController.setPlayPauseReplayResume();
        /// if activity will be finished, will not set movie view invisible @{
        if (!mActivityContext.isFinishing()) {
            mVideoView.setVisibility(View.INVISIBLE); //Workaround for last-seek frame difference
            /// M: [BUG.ADD] @{
            // set controller background drawable to null to avoid screen flash when
            // play an audio only video
            mOverlayExt.setBottomPanel(false, false);
            // / @}
        }
        /// @}

        long end2 = System.currentTimeMillis();
        mOverlayExt.clearBuffering(); //to end buffer state
        mServerTimeoutExt.recordDisconnectTime();
        if (LOG) {
            Log.v(TAG, "doOnPause() save video info consume:" + (end1 - start));
            Log.v(TAG, "doOnPause() suspend video consume:" + (end2 - end1));
            Log.v(TAG, "doOnPause() mVideoPosition=" + mVideoPosition + ", mResumeableTime=" + mResumeableTime
                + ", mVideoLastDuration=" + mVideoLastDuration + ", mIsShowResumingDialog="
                + mIsShowResumingDialog);
        }
    }
    // / M: [FEATURE.ADD] RewindAndForward @{
    public IActivityHooker getRewindAndForwardHooker() {
        return (IActivityHooker)mController.getRewindAndForwardExtension();
    }
    // / @}
    public void onResume() {
        dump();
        mDragging = false; //clear drag info
        // / M: [DEBUG.ADD] @{
        // Toast info of video or audio is supported should be shown when video
        // resume to play.
        mFirstBePlayed = true;
        // / @}
        if (mHasPaused) {
            /// M: same as launch case to delay transparent. @{
            mVideoView.removeCallbacks(mDelayVideoRunnable);
            mVideoView.postDelayed(mDelayVideoRunnable, BLACK_TIMEOUT);
            /// @}
            // /M: reset notification related variable. @{
            mPlayPauseProcessExt.mNeedCheckPauseSuccess = false;
            mPlayPauseProcessExt.mPauseSuccess = false;
            mPlayPauseProcessExt.mPlayVideoWhenPaused = false;
            ///@}
            if (mServerTimeoutExt.handleOnResume() || mIsShowResumingDialog) {
                mHasPaused = false;
                return;
            }
            enableWfdPowerSavingIfNeed();
            switch(mTState) {
            case RETRY_ERROR:
                mRetryExt.showRetry();
                break;
            case STOPED:
                mController.showEnded();
                break;
            case COMPELTED:
                ///slidevideo
                mActivityContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mController.showEnded();
                    }
                });
                if (mVideoCanSeek || mVideoView.canSeekForward()) {
                    mVideoView.seekTo(mVideoPosition);
                }
                mVideoView.setDuration(mVideoLastDuration);
                break;
            case PAUSED:
                //if video was paused, so it should be started.
                    doStartVideo(true, mVideoPosition, mVideoLastDuration,
                            false);
                pauseVideo();
                break;
            default:
                if (mConsumedDrmRight) {
                    doStartVideo(true, mVideoPosition, mVideoLastDuration);
                } else {
                        doStartVideoCareDrm(true, mVideoPosition,
                                mVideoLastDuration);
                }
                pauseVideoMoreThanThreeMinutes();
                break;
            }
            mVideoView.dump();
            mHasPaused = false;
        }
        mHandler.post(mProgressChecker);
        startSlowMotionChecker();
    }

  ///M: enable wfd power saving mode when wfd is connected.
    private void enableWfdPowerSavingIfNeed() {
        if (MovieUtils.isWfdEnabled(mContext)) {
            if (mWfdPowerSaving != null) {
                if (mActivityContext.equals(mWfdPowerSaving.getCurrentActivity())) {
                    mWfdPowerSaving.refreshPowerSavingPara();
                } else {
                    mWfdPowerSaving.dismissPresentaion();
                    mWfdPowerSaving = new WfdPowerSaving(mRootView, mActivityContext, mVideoView, mHandler) {
                        @Override
                        public void showController() {
                            mController.show();
                            mRootView.setBackgroundColor(Color.BLACK);
                        }
                        @Override
                        public void restoreSystemUiListener() {
                            setOnSystemUiVisibilityChangeListener();
                        }
                    };
                mWfdPowerSaving.registerReceiver();
                }
            } else {
                mWfdPowerSaving = new WfdPowerSaving(mRootView, mActivityContext, mVideoView, mHandler) {
                    @Override
                    public void showController() {
                        mController.show();
                        mRootView.setBackgroundColor(Color.BLACK);
                    }
                    @Override
                    public void restoreSystemUiListener() {
                        setOnSystemUiVisibilityChangeListener();
                    }
                };
            }
            mWfdPowerSaving.registerReceiver();
        } else {
            mWfdPowerSaving = null;
            if (mActivityContext instanceof MovieActivity) {
                // register wfd receiver
                mWfdConnectReceiver = new WfdConnectReceiver();
                mWfdConnectReceiver.register();
            }
        }
        
        if(mZoomController != null) {
            mZoomController.updateWfdStatus();
        }
    }
  /// @}

    private void pauseVideoMoreThanThreeMinutes() {
        // If we have slept for too long, pause the play
        // If is live streaming, do not pause it too
        long now = System.currentTimeMillis();
        if (now > mResumeableTime && !MovieUtils.isLiveStreaming(mVideoType)
                && ExtensionHelper.shouldEnableCheckLongSleep(mActivityContext)) {
            if (mVideoCanPause || mVideoView.canPause()) {
                pauseVideo();
            }
        }
        if (LOG) {
            Log.v(TAG, "pauseVideoMoreThanThreeMinutes() now=" + now);
        }
    }

    public void onDestroy() {
        if (mVirtualizer != null) {
            mVirtualizer.release();
            mVirtualizer = null;
        }
        if (mWfdPowerSaving != null) {
            mWfdPowerSaving = null;
        }

        mVideoView.stopPlayback();
        mAudioBecomingNoisyReceiver.unregister();
        mServerTimeoutExt.clearTimeoutDialog();
    }

    // This updates the time bar display (if necessary). It is called every
    // second by mProgressChecker and also from places where the time bar needs
    // to be updated immediately.
    private int setProgress() {
        if (LOG) {
            Log.v(TAG, "setProgress() mDragging=" + mDragging + ", mShowing=" + mShowing
                + ", mIsOnlyAudio=" + mIsOnlyAudio);
        }
        /// M: [FEATURE.ADD] WFD Power Saving @{
        boolean needShow = false;
        if (mWfdPowerSaving != null && mWfdPowerSaving.needShowController()) {
          /// M: In WFD Extension mode, Time will always update.
            needShow = true;
        }
        /// @}
        
        if (mDragging
                || (!mShowing && !needShow) && !mIsOnlyAudio) {
            return 0;
        }
        
        int position = mVideoView.getCurrentPosition();
        int duration = mVideoView.getDuration();
        
        mController.setTimes(position, duration, 0, 0);
        if (mControllerRewindAndForwardExt != null && mController.isPlayPauseEanbled()) {
            mControllerRewindAndForwardExt.updateRewindAndForwardUI();
        }
        return position;
    }

    private void doStartVideo(final boolean enableFasten, final int position, final int duration, boolean start) {
        if (LOG) {
            Log.v(TAG, "doStartVideo(" + enableFasten + ", " + position + ", " + duration + ", " + start + ")");
        }
        ///M:dismiss some error dialog and if error still it will show again
        mVideoView.dismissAllowingStateLoss();
        Uri uri = mMovieItem.getUri();
        String mimeType = mMovieItem.getMimeType();
        if (!MovieUtils.isLocalFile(uri, mimeType)) {
            Map<String, String> header = new HashMap<String, String>(2);
            mActivityContext.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mController.showLoading(false);
                    mOverlayExt.setPlayingInfo(MovieUtils.isLiveStreaming(mVideoType));
                }
            });
            mHandler.removeCallbacks(mPlayingChecker);
            mHandler.postDelayed(mPlayingChecker, 250);
            Log.v(TAG, "doStartVideo() mCookie is " + mCookie);
            // / M: add play/pause asynchronous processing @{
            if (onIsRTSP()) {
                header.put(ASYNC_PAUSE_PLAY, String.valueOf(true));
                // /M: add for streaming cookie
                if (mCookie != null) {
                    header.put(MovieActivity.COOKIE, mCookie);
                }
                mVideoView.setVideoURI(mMovieItem.getUri(), header);
                // / @}
            } else {
                if (mCookie != null) {
                    // /M: add for streaming cookie
                    header.put(MovieActivity.COOKIE, mCookie);
                    mVideoView.setVideoURI(mMovieItem.getUri(), header);
                } else {
                    mVideoView.setVideoURI(mMovieItem.getUri(), null);
                }
            }
        } else {
            if (mWfdPowerSaving != null) {
                ///slidevideo
                mActivityContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mWfdPowerSaving.disableWfdPowerSaving();
                        mController.showPlaying();
                        mController.hide();
                        mWfdPowerSaving.enableWfdPowerSaving();
                    }
                });
            } else {
                ///slidevideo
                mActivityContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mController.showPlaying();
                        // / M:slidevideo debug
                        mController.hide();
                    }
                });
            }
            ///M: set background to null to avoid lower power after start playing,
            // because GPU is always runing, if not set null.
            //can not set too early, for onShown() will set backgound black.
            mHandler.removeCallbacks(mRemoveBackground);
            mHandler.postDelayed(mRemoveBackground, 2 * BLACK_TIMEOUT);
            mVideoView.setVideoURI(mMovieItem.getUri(), null);
        }
        if (start) {
            mVideoView.start();
        }
        /// @}

        ///slidevideo
        mActivityContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //we may start video from stopVideo,
                //this case, we should reset canReplay flag according canReplay and loop
                if (mController != null) {
                    boolean loop = mPlayerExt.getLoop();
                    boolean canReplay = loop ? loop : mCanReplay;
                    mController.setCanReplay(canReplay);
                } else {
                    Log.v(TAG, "doStartVideo post runnable: mController = " + mController);
                }
            }
        });

        if (position > 0 && (mVideoCanSeek || mVideoView.canSeekForward()) || mHotKnotIntent) {
            mVideoView.seekTo(position);
        }
        if (enableFasten) {
            mVideoView.setDuration(duration);
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                setProgress();
            }
        });
    }

    private void doStartVideo(boolean enableFasten, int position, int duration) {
        if (mActivityContext instanceof MovieActivity) {
            // / M: [FEATURE.MODIFY] DRM@{
            // Audio focus should be requested when the video really
            // start.Notice:The audio focus is request at MoviePlayer
            // onStart(For
            // DRM modify) and release at MovieActivity onStop(follow Google
            // default).
            ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE)).requestAudioFocus(
                    null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            doStartVideo(enableFasten, position, duration, true);
        } else {
              doStartVideo(enableFasten, position, duration, false);
        }
    }

    private void playVideo() {
        if (LOG) {
            Log.v(TAG, "playVideo()");
        }
        /// M: resume mPauseBuffering to false for show buffering info to user.
        mPlayerExt.mPauseBuffering = false;
        mTState = TState.PLAYING;
        mVideoView.start();
        ///slidevideo
        mActivityContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mController.showPlaying();
            }
        });
        setProgress();
    }

    private void pauseVideo() {
        if (LOG) {
            Log.v(TAG, "pauseVideo()");
        }
        mTState = TState.PAUSED;
        mVideoView.pause();
        // / M: [FEATURE.ADD] SlideVideo@{
        mActivityContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mController.showPaused();
                if (mVideoView.isInFilmMode()) {
                    mController.hideController();
                }
            }
        });
        // / @}
        setProgress();
        ///M: Add for Video zoom new full screen method.@{
        mHandler.removeCallbacks(mHideSystemUIRunnable);
        //}@
    }

    public void pauseWithoutStateChange() {
        mVideoView.pause();
    }

    public int getCurrentPosition() {
        return mVideoView.getCurrentPosition();
    }
    // Below are notifications from VideoView
    @Override
    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        if (LOG) {
            Log.v(TAG, "onError(" + player + ", " + arg1 + ", " + arg2 + ")");
        }
        mError = true;
        if (mServerTimeoutExt != null && mServerTimeoutExt.onError(player, arg1, arg2)) {
            return true;
        }
        if (mRetryExt.onError(player, arg1, arg2)) {
            return true;
        }
        mHandler.removeCallbacksAndMessages(null);
        // /M:Add for slideVideo,mController maybe null when onError
        // occurred,new it to avoid mController JE.
        if (mController == null && FeatureConfig.supportSlideVideoPlay) {
            mActivityContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mController = MovieControllerOverlay.getMovieController(mActivityContext);
                }
            });
        }
        // / @}
        mHandler.post(mProgressChecker); // always show progress
        // /slidevideo
        mActivityContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // / M:resume controller,note that VideoView will show an error
                // dialog if we return false, so no need to show more message.
                mController.setViewEnabled(true);
                mController.showErrorMessage("");
            }
        });

        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (LOG) {
            Log.v(TAG, "onCompletion() mCanReplay=" + mCanReplay);
        }
        // SetProgress when receive EOS to avoid that sometimes the progress
        // bar is not right because the position got from media player is
        // not in time.Notice that even if the position got again when receive
        // EOS,the progress bar may not exactly right for native may return
        // an inaccurate position.
        setProgress();
        if (mError) {
            Log.w(TAG, "error occured, exit the video player!");
            mActivityContext.finish();
            return;
        }
        
        if (mPlayerExt.getLoop() || mIsLivePhoto) {
            onReplay();
        } else { //original logic
            mTState = TState.COMPELTED;
            if (mCanReplay) {
                mActivityContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mController.showEnded();
                    }
                });
            }
            // / M: [FEATURE.ADD] SlideVideo@{
            // paused at the last frame when slide video enabled
            if (!(mActivityContext instanceof MovieActivity)) {
                mActivityContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mController.showPaused();
                        if (mVideoView.isInFilmMode()) {
                            mController.hideController();
                        }
                        // /M:clear screen on flag when video is completed.
                        mActivityContext.getWindow().clearFlags(
                              WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                });
            }
            // / @}
            onCompletion();
        }
    }

    public void onCompletion() {
    }

    // Below are notifications from ControllerOverlay
    @Override
    public void onPlayPause() {
        if (mVideoView.isPlaying()) {
            if (mVideoView.canPause()) {
                pauseVideo();
                //set view disabled(play/pause asynchronous processing)
                mController.setViewEnabled(false);
            }
        } else {
            playVideo();
            //set view disabled(play/pause asynchronous processing)
            mController.setViewEnabled(false);
        }
    }

    public boolean isPlaying() {
        return mVideoView.isPlaying();
    }

    @Override
    public void onSeekStart() {
        if (LOG) {
            Log.v(TAG, "onSeekStart() mDragging=" + mDragging);
        }
        mSeekMovePosition = -1;
        mDragging = true;
        // /M: When slow motion is supported and a slow motion video is
        // playing,pause it first when seek start. @{
        if (MtkVideoFeature.isSlowMotionSupport() && mSlowMotionItem.isSlowMotionVideo()) {
            if (mTState == TState.PLAYING) {
                mVideoView.pause();
            }
        }
    }

    @Override
    public void onSeekMove(int time) {
        if (LOG) {
            Log.v(TAG, "onSeekMove(" + time + ") mDragging=" + mDragging);
        }
        // /M:When slow motion is supported and user drag a slow motion
        // video,the video view should be updated dynamically @{
        if (!mDragging || MtkVideoFeature.isSlowMotionSupport()
                && mSlowMotionItem.isSlowMotionVideo()) {
            mVideoView.seekTo(time);
            mSeekMovePosition = time;
        }
    }

    @Override
    public void onSeekEnd(int time, int start, int end) {
        if (LOG) {
            Log.v(TAG, "onSeekEnd(" + time + ") mDragging=" + mDragging);
        }
        mDragging = false;
        // /M:No need to seek to the same position twice
        if (mSeekMovePosition != time) {
            // / @}
            mVideoView.seekTo(time);
        }
        // setProgress();

        // /M:When slow motion is supported and user drag a slow motion
        // video,the video view should be continue to play when drag end. @{
        if (MtkVideoFeature.isSlowMotionSupport() && mSlowMotionItem.isSlowMotionVideo()) {
            if (mTState == TState.PLAYING) {
                mVideoView.start();
            }
        }
    }

    @Override
    public void onShown() {
        if (LOG) {
            Log.v(TAG, "onShown");
        }
        mHandler.removeCallbacks(mRemoveBackground);
        ///M: Add for Video zoom new full screen method.@{
        mHandler.removeCallbacks(mHideSystemUIRunnable);
        //}@
        // /M: mHideActionBarRunnable is for action bar not disappear
        // after press power key quickly.
        mHandler.removeCallbacks(mHideActionBarRunnable);
        // /}@
        mRootView.setBackgroundColor(Color.BLACK);
        mShowing = true;
        setProgress();
        /// M:if it isn't first open, no need to show action bar 500ms.
        if (mActivityContext instanceof MovieActivity) {
            showSystemUi(true, false);
        }
        if (mWfdPowerSaving != null && mWfdPowerSaving.isPowerSavingEnable()) {
           mWfdPowerSaving.cancelCountDown();
        }
        }
    @Override
    public void onHidden() {
        if (LOG) {
            Log.v(TAG, "onHidden");
        }
            mShowing = false;
        /// M: if show action bar is not finish, can not to hidden it. @{
        if (mIsDelayFinish) {
            Log.v(TAG, "mIsDelayFinish " + mIsDelayFinish);
            if (mActivityContext instanceof MovieActivity) {
                showSystemUi(false, false);
            }
            
            // /M: mHideActionBarRunnable is for action bar not disappear
            // after press power key quickly.
            mHandler.removeCallbacks(mHideActionBarRunnable);
            mHandler.postDelayed(mHideActionBarRunnable, BLACK_TIMEOUT);
            ///M: set background to null avoid lower power,
            // because GPU is always running, if not set null.
            //delay 1000ms is to avoid ghost image when action bar do slide animation,
            mHandler.removeCallbacks(mRemoveBackground);
            mHandler.postDelayed(mRemoveBackground, 3 * BLACK_TIMEOUT);
            // /}@
            if (mWfdPowerSaving != null && !mHasPaused && mWfdPowerSaving.isPowerSavingEnable()
                    && mVideoView != null && mVideoView.isPlaying()) {
                mWfdPowerSaving.startCountDown();
            }
        }
        /// @}
    }
    @Override
    public boolean onIsRTSP() {
        return MovieUtils.isRTSP(mVideoType);
    }

    @Override
    public boolean wfdNeedShowController() {
      if (mWfdPowerSaving != null) {
          return  mWfdPowerSaving.needShowController();
      } else {
          return false;
      }
    }


    @Override
    public void onReplay() {
        if (LOG) {
            Log.v(TAG, "onReplay()");
        }
        mFirstBePlayed = true;
        if (mRetryExt.handleOnReplay()) {
            return;
        }
        //M: FOR MTK_SUBTITLE_SUPPORT//@{
        if (MtkVideoFeature.isSubTitleSupport()) {
            if (null != mSubTitleView) {
                updateSubtitleView(null);
        }
        }
        //@}
        doStartVideoCareDrm(false, 0, 0);
        // / M: [FEATURE.ADD] SlideVideo@{
        if (mVideoView instanceof MtkVideoTexture) {
            ((MtkVideoTexture) mVideoView).prepare();
            ((MtkVideoTexture) mVideoView).start();
        }
        // / @}
    }

    // Below are key events passed from MovieActivity.
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LOG) {
            Log.v(TAG, "onKeyDown keyCode = " + keyCode);
        }
        // Some headsets will fire off 7-10 events on a single click
        if (event.getRepeatCount() > 0) {
            return isMediaKey(keyCode);
        }

        if (!mController.getTimeBarEnabled()) {
            Log.v(TAG, "onKeyDown, can not play or pause");
            return isMediaKey(keyCode);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (mVideoView.isPlaying() && mVideoView.canPause()) {
                    pauseVideo();
                } else {
                    playVideo();
                }
                //set view disabled(play/pause asynchronous processing)
                mController.setViewEnabled(false);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (mVideoView.isPlaying() && mVideoView.canPause()) {
                    pauseVideo();
                    //set view disabled(play/pause asynchronous processing)
                    mController.setViewEnabled(false);
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (!mVideoView.isPlaying()) {
                    playVideo();
                    //set view disabled(play/pause asynchronous processing)
                    mController.setViewEnabled(false);
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                // /M:modify for slidevideo
                if (!(mActivityContext instanceof MovieActivity)) {
                    return false;
                }
                if (((MovieActivity) mActivityContext).mMovieList == null) {
                    return false;
                }
                mPlayerExt.startNextVideo(((MovieActivity) mActivityContext).mMovieList
                        .getPrevious(mMovieItem));
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                // /M:modify for slidevideo
                if (!(mActivityContext instanceof MovieActivity)) {
                    return false;
                }
                if (((MovieActivity) mActivityContext).mMovieList == null) {
                    return false;
                }
                mPlayerExt.startNextVideo(((MovieActivity) mActivityContext).mMovieList
                        .getNext(mMovieItem));
                return true;
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return isMediaKey(keyCode);
    }

    private static boolean isMediaKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE;
    }

    // We want to pause when the headset is unplugged.
    private class AudioBecomingNoisyReceiver extends BroadcastReceiver {

        public void register() {
            mContext.registerReceiver(this,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "AudioBecomingNoisyReceiver onReceive");
            if (!mController.getTimeBarEnabled()) {
                Log.v(TAG, "AudioBecomingNoisyReceiver, can not play or pause");
                return;
            }
            if (mVideoView.isPlaying() && mVideoView.canPause()) {
                pauseVideo();
                //set view disabled(play/pause asynchronous processing)
                mController.setViewEnabled(false);
            }
        }
    }

    ///M:Register WFD connect receiver.@{
    private class WfdConnectReceiver extends BroadcastReceiver {

        public void register() {
            mContext.registerReceiver(this,
                    new IntentFilter(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED));
            Log.v(TAG, "register this: " + this);
        }

        public void unregister() {
            Log.v(TAG, "unregister this: " + this);
            mContext.unregisterReceiver(this);

        }
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction().equals(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED)) {
                WifiDisplayStatus status = (WifiDisplayStatus) intent.getParcelableExtra(
                        DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
                Log.v(TAG, "mWfdReceiver onReceive wfd status: " + status);
                if (status != null) {
                    if (status.getActiveDisplay() == null) {
                        // WFD disconnected
                    } else {
                        // WFD connected
                        Toast.makeText(mContext.getApplicationContext(),
                                mContext.getString(R.string.wfd_connected), Toast.LENGTH_LONG).show();
                        mActivityContext.finish();
                    }
                }
            }
        }

    }
    /// M @}



    private void init(final Activity movieActivity, IMovieItem info, boolean canReplay) {
        mCanReplay = canReplay;
        mMovieItem = info;
        mSlowMotionItem = new SlowMotionItem(mContext, mMovieItem.getUri());
        mVideoType = MovieUtils.judgeStreamingType(info.getUri(), info.getMimeType());
        //for toast more info and live streaming
        mVideoView.setOnInfoListener(this);
        mVideoView.setOnPreparedListener(this);
        mVideoView.setOnBufferingUpdateListener(this);
        mVideoView.setOnVideoSizeChangedListener(this);
        // M: FOR MTK_SUBTITLE_SUPPORT
        //@{
        if (MtkVideoFeature.isSubTitleSupport()) {
            mVideoView.setOnTimedTextListener(this);
        }
        //@}

        ///M: The listener will be define in VideoZoomController.
//        mRootView.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//               if(!mHideController) {
//                    mController.show();
//                }
//                return true;
//            }
//        });

        mActivityContext.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mOverlayExt = mController.getOverlayExt();
                mControllerRewindAndForwardExt = mController.getRewindAndForwardExtension();
            }
        });
     }


    public IMtkVideoController getVideoSurface() {
        return mVideoView;
    }

    private void onSaveInstanceStateMore(Bundle outState) {
        //for more details
        mServerTimeoutExt.onSaveInstanceState(outState);
        outState.putInt(KEY_VIDEO_LAST_DURATION, mVideoLastDuration);
        outState.putBoolean(KEY_VIDEO_CAN_PAUSE, mVideoView.canPause());
        /// M: add this for deal with change language or other case which cause activity destory but not save right state
        /// @{
        if (mVideoCanSeek || mVideoView.canSeekForward()) {
            outState.putBoolean(KEY_VIDEO_CAN_SEEK, true);
        } else {
            outState.putBoolean(KEY_VIDEO_CAN_SEEK, false);
        }
        /// @}
        outState.putBoolean(KEY_CONSUMED_DRM_RIGHT, mConsumedDrmRight);
        outState.putInt(KEY_VIDEO_STREAMING_TYPE, mVideoType);
        outState.putString(KEY_VIDEO_STATE, String.valueOf(mTState));
        outState.putString(KEY_VIDEO_CURRENT_URI, mMovieItem.getUri().toString());
        mScreenModeExt.onSaveInstanceState(outState);
        mRetryExt.onSaveInstanceState(outState);
        mPlayerExt.onSaveInstanceState(outState);

        ///M: MTK_AUDIO_CHANGE_SUPPORT & MTK_SUBTITLE_SUPPORT
        ///@{
        if (MtkVideoFeature.isAudioChangeSupport()) {
            outState.putInt(KEY_SELECT_AUDIO_INDEX, mSelectAudioIndex);
            outState.putInt(KEY_SELECT_AUDIO_TRACK_INDEX, mSelcetAudioTrackIdx);
            Log.d(TAG, "Save audio track index: " + mSelectSubTitleIndex +
                       ", track index: " + mSelectSubTitleTrackIdx);
        }
        if (MtkVideoFeature.isSubTitleSupport()) {
            outState.putInt(KEY_SELECT_SUBTITLE_INDEX, mSelectSubTitleIndex);
            outState.putInt(KEY_SELECT_SUBTITLE_TRACK_INDEX, mSelectSubTitleTrackIdx);
            Log.d(TAG, "Save subtitle index: " + mSelectSubTitleIndex +
                       ", track index: " + mSelectSubTitleTrackIdx);
        }
        ///@}

        if (LOG) {
            Log.v(TAG, "onSaveInstanceState(" + outState + ")");
        }
    }

    private void onRestoreInstanceState(Bundle icicle) {
        mVideoLastDuration = icicle.getInt(KEY_VIDEO_LAST_DURATION);
        mVideoCanPause = icicle.getBoolean(KEY_VIDEO_CAN_PAUSE);
        mVideoCanSeek = icicle.getBoolean(KEY_VIDEO_CAN_SEEK);
        mConsumedDrmRight = icicle.getBoolean(KEY_CONSUMED_DRM_RIGHT);
        mVideoType = icicle.getInt(KEY_VIDEO_STREAMING_TYPE);
        mTState = TState.valueOf(icicle.getString(KEY_VIDEO_STATE));
        mMovieItem.setUri(Uri.parse(icicle.getString(KEY_VIDEO_CURRENT_URI)));
        mScreenModeExt.onRestoreInstanceState(icicle);
        mServerTimeoutExt.onRestoreInstanceState(icicle);
        mRetryExt.onRestoreInstanceState(icicle);
        mPlayerExt.onRestoreInstanceState(icicle);

        ///M: MTK_AUDIO_CHANGE_SUPPORT & MTK_SUBTITLE_SUPPORT
        ///@{
        if (MtkVideoFeature.isAudioChangeSupport()) {
          mSelectAudioIndex = icicle.getInt(KEY_SELECT_AUDIO_INDEX, 0);
          mSelcetAudioTrackIdx = icicle.getInt(KEY_SELECT_AUDIO_TRACK_INDEX, 0);
          Log.d(TAG, "Restore audio track index: " + mSelectSubTitleIndex +
                     ", track index: " + mSelectSubTitleTrackIdx);
        }
        if (MtkVideoFeature.isSubTitleSupport()) {
          mSelectSubTitleIndex = icicle.getInt(KEY_SELECT_SUBTITLE_INDEX, 0);
          mSelectSubTitleTrackIdx = icicle.getInt(KEY_SELECT_SUBTITLE_TRACK_INDEX, 0);
          Log.d(TAG, "Restore subtitle index: " + mSelectSubTitleIndex +
                     ", track index: " + mSelectSubTitleTrackIdx);
        }
        ///@}

        if (LOG) {
            Log.v(TAG, "onRestoreInstanceState(" + icicle + ")");
        }
    }
    /// @}

    private void clearVideoInfo() {
        mVideoPosition = 0;
        mVideoLastDuration = 0;
        mIsOnlyAudio = false;
        mConsumedDrmRight = false;
        mIsBuffering = false;
        mIsLivePhoto = false;
        if (mServerTimeoutExt != null) {
            mServerTimeoutExt.clearServerInfo();
        }

        if (mRetryExt != null) {
            mRetryExt.removeRetryRunnable();
        }
    }

    private void getVideoInfo(MediaPlayer mp) {
        Uri uri = mMovieItem.getUri();
        String mimeType = mMovieItem.getMimeType();
        if (!MovieUtils.isLocalFile(uri, mimeType)) {
            Metadata data = mp.getMetadata(MediaPlayer.METADATA_ALL,
                    MediaPlayer.BYPASS_METADATA_FILTER);
            if (data != null) {
                mServerTimeoutExt.setVideoInfo(data);
                mPlayerExt.setVideoInfo(data);
            } else {
                Log.w(TAG, "Metadata is null!");
            }
            int duration = mp.getDuration();
            // /M:For http streaming does not has live streaming,so do not set a
            // live streaming type to a http streaming whether its duration is
            // bigger or smaller than 0 or not @{
            if (duration <= 0 && !MovieUtils.isHttpStreaming(uri, mimeType)) {
                mVideoType = MovieUtils.VIDEO_TYPE_SDP; // correct it
            } else {
                //correct sdp to rtsp
                if (mVideoType == MovieUtils.VIDEO_TYPE_SDP) {
                    mVideoType = MovieUtils.VIDEO_TYPE_RTSP;
                }
            }

            if (LOG) {
                Log.v(TAG, "getVideoInfo() duration=" + duration);
            }
        }
    }

    /// M: FOR MTK_SUBTITLE_SUPPORT @{
    @Override
    public void onTimedText(MediaPlayer mp, TimedText text) {
        if (LOG) {
            Log.v(TAG, " AudioAndSubtitle mOnTimedTextListener.onTimedTextListener("
                            + mp + ")" + " text = " + text);
        }
        if (text != null) {
            updateSubtitleView(text);
        } else {
            updateSubtitleView(null);
        }
    }
    ///@}

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (LOG) {
            Log.v(TAG, "onPrepared(" + mp + ")");
        }
        getVideoInfo(mp);
        final boolean canPause = mVideoView.canPause();
        final boolean canSeek = mVideoView.canSeekBackward() && mVideoView.canSeekForward();

        mActivityContext.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (mVideoType != MovieUtils.VIDEO_TYPE_LOCAL) {
                    // Here we get the correct streaming type
                    mOverlayExt.setPlayingInfo(MovieUtils.isLiveStreaming(mVideoType));
                }
                mOverlayExt.setCanPause(canPause);
                mOverlayExt.setCanScrubbing(canSeek);
                // resume play pause button (play/pause asynchronous processing)
                mController.setPlayPauseReplayResume();
            }
        });

        if (!canPause && !mVideoView.isTargetPlaying()) {
            mVideoView.start();
        }
        if (mActivityContext instanceof MovieActivity) {
            mControllerRewindAndForwardExt.updateRewindAndForwardUI();
        }
        if (LOG) {
            Log.v(TAG, "onPrepared() canPause=" + canPause + ", canSeek=" + canSeek);
        }
        //FOR MTK_SUBTITLE_SUPPORT
        //@{ TODO set menu visible or invisible
        final int minTrackNumber = 1;
        if (MtkVideoFeature.isSubTitleSupport()) {
            addExternalSubtitle();
            if (getTrackNumFromVideoView(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) < minTrackNumber) {
                ((MovieActivity) mActivityContext).setSubtitleMenuItemVisible(false);
            } else {
                ((MovieActivity) mActivityContext).setSubtitleMenuItemVisible(true);
            }
        }
        //@}
        //FOR MTK_AUDIO_CHANGE_SUPPORT
        //@{ TODO set menu visible or invisible
        if (MtkVideoFeature.isAudioChangeSupport() && mVideoType == MovieUtils.VIDEO_TYPE_LOCAL) {
            if (getTrackNumFromVideoView(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) < minTrackNumber) {
                ((MovieActivity) mActivityContext).setAudioMenuItemVisible(false);
            } else {
                ((MovieActivity) mActivityContext).setAudioMenuItemVisible(true);
            }
            if (mSelcetAudioTrackIdx != 0) {
                mVideoView.selectTrack(mSelcetAudioTrackIdx);
            }
        }
        ///@}
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (LOG) {
            Log.v(TAG, "onInfo() what:" + what + " extra:" + extra);
        }

        if (MtkVideoFeature.isEnableMultiWindow() && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            mBlackCover.setVisibility(View.GONE);
        }
        mRetryExt.onInfo(mp, what, extra);
        if (mPlayPauseProcessExt.onInfo(mp, what, extra)) {
            return true;
        }
        isPlaySupported(what, extra);
        // / M: add log for performance auto test @{
        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            // /M: Add for surfaceview transparent problem before 1st frame arrived.  @{
            //TODO: maybe change to common flow.
            if (mSlowMotionItem.isSlowMotionVideo() && mBlackCover != null) {
                mBlackCover.setVisibility(View.GONE);
            }
            // /@}
            // /M: Add for pre-sanity test case @{
            mIsVideoPlaying = mVideoView.isPlaying();
            // /@}
            long endTime = System.currentTimeMillis();
            Log.i(TEST_CASE_TAG,
                    "[Performance Auto Test][VideoPlayback] The duration of open a video end ["
                            + endTime + "]");
            Log.i(TAG, "[CMCC Performance test][Gallery2][Video Playback] open mp4 file end ["
                    + endTime + "]");

            if (mWfdPowerSaving != null&& mWfdPowerSaving.isPowerSavingEnable()
                    && mTState == TState.PLAYING
                    && MovieUtils.isLocalFile(mMovieItem.getUri(), mMovieItem.getMimeType())) {
                mWfdPowerSaving.startCountDown();
            }
        }
        // / @}

        // /M:For http streaming, show spinner while seek to a new
        // position.
        handleBuffering(what, extra);

        // /M:HLS_audio-only_02 The mediaplayer shall support metadata
        // embedded in the MPEG2-TS file
        if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
            handleMetadataUpdate(mp, what, extra);
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        if (!mPlayerExt.pauseBuffering()) {
            mOverlayExt.showBuffering(!MovieUtils.isRtspOrSdp(mVideoType), percent);
        }
        if (LOG) {
            Log.v(TAG, "onBufferingUpdate(" + percent + ") pauseBuffering=" + mPlayerExt.pauseBuffering());
        }
    }

    // / M: Check whether video or audio is supported @{
    private boolean isPlaySupported(int what, int extra) {
        if (mFirstBePlayed) {
            int messageId = 0;
            if (extra == ERROR_CANNOT_CONNECT || extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED
                    || extra == ERROR_FORBIDDEN) {
                messageId = R.string.VideoView_info_text_network_interrupt;
            } else {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_NOT_SUPPORTED) {
                    messageId = R.string.VideoView_info_text_video_not_supported;
                } else if (what == MediaPlayer.MEDIA_INFO_AUDIO_NOT_SUPPORTED) {
                    messageId = R.string.audio_not_supported;
                }
            }
            if (messageId != 0) {
                String message = mActivityContext.getString(messageId);
                Toast.makeText(mActivityContext, message, Toast.LENGTH_SHORT).show();
                mFirstBePlayed = false;
                return true;
            }
        }
        return false;
    }
    // / @}

    // /M:HLS_audio-only_02 The mediaplayer shall support metadata
    // embedded in the MPEG2-TS file @{
    private void handleMetadataUpdate(MediaPlayer mp, int what, int extra) {
        Log.v(TAG, "handleMetadataUpdate entry");
        Metadata data =
                mp.getMetadata(MediaPlayer.METADATA_ALL, MediaPlayer.BYPASS_METADATA_FILTER);
        Log.v(TAG, "handleMetadataUpdate data is " + data);
        if (data == null) {
            return;
        }
        String mimeType = new String();
        byte[] album = null;
        if (data.has(Metadata.MIME_TYPE)) {
            mimeType = data.getString(Metadata.MIME_TYPE);
            Log.v(TAG, "handleMetadataUpdate mimeType is " + mimeType);
        }
        if (data.has(Metadata.ALBUM_ART)) {
            album = data.getByteArray(Metadata.ALBUM_ART);
            if (album != null) {
                mOverlayExt.setLogoPic(album);
                Log.v(TAG, "handleMetadataUpdate album size is " + album.length);
            } else {
                mOverlayExt.setBottomPanel(true, true);
                Log.v(TAG, "handleMetadataUpdate album is null");
            }
        }
    }// / @}

    // /M:For http streaming, show spinner while seek to a new position.
    private void handleBuffering(int what, int extra) {
        Log.v(TAG, "handleBuffering what is " + what + " mIsDialogShow is " + mIsDialogShow);
        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            mIsBuffering = true;
            if (MovieUtils.isHttpStreaming(mMovieItem.getUri(), mMovieItem.getMimeType())
                    || MovieUtils
                            .isHttpLiveStreaming(mMovieItem.getUri(), mMovieItem.getMimeType())) {
                mController.showLoading(true);
            }
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            // /M: The video should restore to its previous state after
            // buffering end.
            Log.v(TAG, "handleBuffering mTState is " + mTState);
            mIsBuffering = false;
            if (mIsDialogShow) {
                mPlayerExt.pauseIfNeed();
            }
            if (MovieUtils.isHttpStreaming(mMovieItem.getUri(), mMovieItem.getMimeType())
                    || MovieUtils
                            .isHttpLiveStreaming(mMovieItem.getUri(), mMovieItem.getMimeType())) {
                if (mTState == TState.PAUSED) {
                    mController.showPaused();
                } else {
                    mController.showPlaying();
                }
            }
        }
    } // / @}


    /// @}

    /// M: for drm feature @{
    private boolean mConsumedDrmRight = false;
    private IMovieDrmExtension mDrmExt = ExtensionHelper.getMovieDrmExtension(mActivityContext);
    private void doStartVideoCareDrm(final boolean enableFasten, final int position, final int duration) {
        if (LOG) {
            Log.v(TAG, "doStartVideoCareDrm(" + enableFasten + ", " + position + ", " + duration + ")");
        }
        /// M: [FEATURE.ADD] Live photo@{
        mIsLivePhoto = MovieUtils.isLivePhoto(mActivityContext, mMovieItem.getUri());
        /// @}
        mTState = TState.PLAYING;
        if (!mDrmExt.handleDrmFile(mActivityContext, mMovieItem, new IMovieDrmCallback() {
            @Override
            public void onContinue() {
                doStartVideo(enableFasten, position, duration);
                mConsumedDrmRight = true;
            }
            @Override
            public void onStop() {
                mPlayerExt.setLoop(false);
                onCompletion(null);
            }
        })) {
            doStartVideo(enableFasten, position, duration);
        }
    }

    /// M: for dynamic change video size(http live streaming) @{
    private boolean mIsOnlyAudio = false;
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        //reget the audio type
        if (width != 0 && height != 0) {
            mIsOnlyAudio = false;
        } else {
            mIsOnlyAudio = true;
        }
        mOverlayExt.setBottomPanel(mIsOnlyAudio, true);
        if (LOG) {
            Log.v(TAG, "onVideoSizeChanged(" + width + ", " + height + ") mIsOnlyAudio=" + mIsOnlyAudio);
        }
    }
    /// @}

    private void dump() {
        if (LOG) {
            Log.v(TAG, "dump() mHasPaused=" + mHasPaused
                + ", mVideoPosition=" + mVideoPosition + ", mResumeableTime=" + mResumeableTime
                + ", mVideoLastDuration=" + mVideoLastDuration + ", mDragging=" + mDragging
                + ", mConsumedDrmRight=" + mConsumedDrmRight + ", mVideoCanSeek=" + mVideoCanSeek
                + ", mVideoCanPause=" + mVideoCanPause + ", mTState=" + mTState
                + ", mIsShowResumingDialog=" + mIsShowResumingDialog);
        }
    }

    //for more killed case, same as videoview's state and controller's state.
    //will use it to sync player's state.
    //here doesn't use videoview's state and controller's state for that
    //videoview's state doesn't have reconnecting state and controller's state has temporary state.
    private enum TState {
        PLAYING,
        PAUSED,
        STOPED,
        COMPELTED,
        RETRY_ERROR
    }


    interface Restorable {
        void onRestoreInstanceState(Bundle icicle);
        void onSaveInstanceState(Bundle outState);
    }

    private class RetryExtension implements Restorable, MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener {
        private static final String KEY_VIDEO_RETRY_COUNT = "video_retry_count";
        private int mRetryDuration;
        private int mRetryPosition;
        private int mRetryCount;

        private final Runnable mRetryRunnable = new Runnable() {
            @Override
            public void run() {
                if (LOG) {
                    Log.v(TAG, "mRetryRunnable.run()");
                }
                retry();
            }
        };

        public void removeRetryRunnable() {
            mHandler.removeCallbacks(mRetryRunnable);
        }

        public void retry() {
            doStartVideoCareDrm(true, mRetryPosition, mRetryDuration);
            if (LOG) {
                Log.v(TAG, "retry() mRetryCount=" + mRetryCount + ", mRetryPosition=" + mRetryPosition);
            }
        }

        public void clearRetry() {
            if (LOG) {
                Log.v(TAG, "clearRetry() mRetryCount=" + mRetryCount);
            }
            mRetryCount = 0;
        }

        public boolean reachRetryCount() {
            if (LOG) {
                Log.v(TAG, "reachRetryCount() mRetryCount=" + mRetryCount);
            }
            if (mRetryCount > 3) {
                return true;
            }
            return false;
        }

        public int getRetryCount() {
            if (LOG) {
                Log.v(TAG, "getRetryCount() return " + mRetryCount);
            }
            return mRetryCount;
        }

        public boolean isRetrying() {
            boolean retry = false;
            if (mRetryCount > 0) {
                retry = true;
            }
            if (LOG) {
                Log.v(TAG, "isRetrying() mRetryCount=" + mRetryCount);
            }
            return retry;
        }

        @Override
        public void onRestoreInstanceState(Bundle icicle) {
            mRetryCount = icicle.getInt(KEY_VIDEO_RETRY_COUNT);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putInt(KEY_VIDEO_RETRY_COUNT, mRetryCount);
        }

        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            if (what == IMtkVideoController.MEDIA_ERROR_CANNOT_CONNECT_TO_SERVER) {
                //get the last position for retry
                mRetryPosition = mVideoView.getCurrentPosition();
                mRetryDuration = mVideoView.getDuration();
                mRetryCount++;
                mTState = TState.RETRY_ERROR;
                if (reachRetryCount()) {
                    mOverlayExt.showReconnectingError();
                    /// M: set replay is true for user can reload video when
                    //media error can not connect to server
                    mController.setCanReplay(true);
                    // When it reach retry count and streaming can not connect
                    // to server,the rewind and forward button should be
                    // disabled
                    if (mVideoView.canPause()) {
                        mOverlayExt.setCanScrubbing(false);
                        if (mControllerRewindAndForwardExt != null
                                && mControllerRewindAndForwardExt.getView() != null) {
                            mControllerRewindAndForwardExt.setViewEnabled(false);
                        }
                    }
                } else {
                    mOverlayExt.showReconnecting(mRetryCount);
                    mHandler.postDelayed(mRetryRunnable, RETRY_TIME);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                clearRetry();
                return true;
            }
            return false;
        }

        public boolean handleOnReplay() {
            if (isRetrying()) { //from connecting error
                clearRetry();
                int errorPosition = mVideoView.getCurrentPosition();
                int errorDuration = mVideoView.getDuration();
                doStartVideoCareDrm(errorPosition > 0, errorPosition, errorDuration);
                if (LOG) {
                    Log.v(TAG, "onReplay() errorPosition=" + errorPosition + ", errorDuration=" + errorDuration);
                }
                return true;
            }
            return false;
        }

        public void showRetry() {
            mOverlayExt.showReconnectingError();
            if (mVideoCanSeek || mVideoView.canSeekForward()) {
                mVideoView.seekTo(mVideoPosition);
            }
            mVideoView.setDuration(mVideoLastDuration);
            mRetryPosition = mVideoPosition;
            mRetryDuration = mVideoLastDuration;
        }
    }

    private class ScreenModeExt implements Restorable, ScreenModeListener {
        private static final String KEY_VIDEO_SCREEN_MODE = "video_screen_mode";
        private int mScreenMode = ScreenModeManager.SCREENMODE_BIGSCREEN;
        private ScreenModeManager mScreenModeManager = new ScreenModeManager();

        public void setScreenMode() {
            mVideoView.setScreenModeManager(mScreenModeManager);
            mController.setScreenModeManager(mScreenModeManager);
            mScreenModeManager.addListener(this);
            mScreenModeManager.setScreenMode(mScreenMode); //notify all listener to change screen mode
            if (LOG) {
                Log.v(TAG, "setScreenMode() mScreenMode=" + mScreenMode);
            }
        }

        @Override
        public void onScreenModeChanged(int newMode) {
            mScreenMode = newMode; //changed from controller
            if (LOG) {
                Log.v(TAG, "OnScreenModeClicked(" + newMode + ")");
            }
        }

        @Override
        public void onRestoreInstanceState(Bundle icicle) {
            mScreenMode = icicle.getInt(KEY_VIDEO_SCREEN_MODE, ScreenModeManager.SCREENMODE_BIGSCREEN);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putInt(KEY_VIDEO_SCREEN_MODE, mScreenMode);
        }
    }

    /// M: FOR MTK_AUDIO_CHANGE_SUPPORT & MTK_SUBTITLE_SUPPORT
    /// @{
    /** get the track number
    * @param type can be MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO
     *                  or
     *                  MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT
     *                  or
     *                  TYPE_TRACK_INFO_BOTH for both type
    */

    public int getTrackNumFromVideoView(int type) {
        TrackInfo trackInfo[] = mVideoView.getTrackInfo();
        int AudioNum = 0;
        int SubtilteNum = 0;
        if (trackInfo == null) {
            Log.v(TAG,
                    "---AudioAndSubtitle getTrackInfoFromVideoView: NULL ");
            return NONE_TRACK_INFO;
        }
        int trackLength = trackInfo.length;
        for (int i = 0; i < trackLength; i++) {
            if (trackInfo[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                AudioNum++;
                continue;
            } else if (trackInfo[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                SubtilteNum++;
                continue;
            }
        }
        Log.v(TAG,
                "---AudioAndSubtitle getTrackNumFromVideoView: trackInfo.length = "
                        + trackLength + ", AudioNum= " + AudioNum
                        + ", SubtilteNum=" + SubtilteNum);
        if (type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
            return AudioNum;
        } else if (type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
            return SubtilteNum;
        } else {
            return trackLength;
        }

    }

    private void addExternalSubtitle() {
        File[] externalSubTitlefiles = ((MovieActivity) mActivityContext).listExtSubTitleFileNameWithPath();
        if (null != externalSubTitlefiles) {

            for (File file : externalSubTitlefiles) {
                String filePath = file.getPath().toLowerCase();
                if (filePath.endsWith(MovieUtils.SUBTITLE_SUPPORT_WITH_SUFFIX_SRT.
                           toLowerCase())) {
                    ((MTKVideoView) mVideoView).addExtTimedTextSource(file.getPath(), MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBRIP);
                    continue;
                } else if (filePath.endsWith(MovieUtils.SUBTITLE_SUPPORT_WITH_SUFFIX_SMI.
                           toLowerCase())) {
                    ((MTKVideoView) mVideoView).addExtTimedTextSource(file.getPath(), MovieUtils.MEDIA_MIMETYPE_TEXT_SUBSMI);
                    continue;
                } else if (filePath.endsWith(MovieUtils.SUBTITLE_SUPPORT_WITH_SUFFIX_SUB.
                           toLowerCase())) {
                    ((MTKVideoView) mVideoView).addExtTimedTextSource(file.getPath(), MovieUtils.MEDIA_MIMETYPE_TEXT_SUB);
                    continue;
                } else if (filePath.endsWith(MovieUtils.SUBTITLE_SUPPORT_WITH_SUFFIX_IDX.
                           toLowerCase())) {
                    ((MTKVideoView) mVideoView).addExtTimedTextSource(file.getPath(), MovieUtils.MEDIA_MIMETYPE_TEXT_IDX);
                    continue;
                } else if (filePath.endsWith(MovieUtils.SUBTITLE_SUPPORT_WITH_SUFFIX_TXT.
                           toLowerCase())) {
                    ((MTKVideoView) mVideoView).addExtTimedTextSource(file.getPath(), MovieUtils.MEDIA_MIMETYPE_TEXT_SUBTXT);
                    continue;
                } else if (filePath.endsWith(MovieUtils.SUBTITLE_SUPPORT_WITH_SUFFIX_ASS.
                           toLowerCase())) {
                    ((MTKVideoView) mVideoView).addExtTimedTextSource(file.getPath(), MovieUtils.MEDIA_MIMETYPE_TEXT_SUBASS);
                    continue;
                } else if (filePath.endsWith(MovieUtils.SUBTITLE_SUPPORT_WITH_SUFFIX_SSA.
                           toLowerCase())) {
                    ((MTKVideoView) mVideoView).addExtTimedTextSource(file.getPath(), MovieUtils.MEDIA_MIMETYPE_TEXT_SUBSSA);
                    continue;
                } else if (filePath.endsWith(MovieUtils.SUBTITLE_SUPPORT_WITH_SUFFIX_MPL.
                          toLowerCase())) {
                    ((MTKVideoView) mVideoView).addExtTimedTextSource(file.getPath(), MovieUtils.MEDIA_MIMETYPE_TEXT_SUBMPL);
                    continue;
                }
            }
        }
        //selsect the bookMark saved track. if mSelectSubTitleTrackIdx > hasTrackNum do not call this
        if (mSelectSubTitleTrackIdx != 0
                && (mSelectSubTitleTrackIdx < getTrackNumFromVideoView(TYPE_TRACK_INFO_BOTH))) {
             mVideoView.selectTrack(mSelectSubTitleTrackIdx);
        }
    }

    /**
     * show the dialog for user to select audio track or subtitle
     * @param TrackType can be MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO
     *                  or
     *                  MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT
     */
    private static final int MINI_TRACK_INFO_NUM = 1;
    public void showDialogForTrack(int TrackType) {
        TrackInfo trackInfo[] = mVideoView.getTrackInfo();
        if (trackInfo == null || trackInfo.length < 1) {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    mActivityContext);
            builder.setTitle(R.string.noAvailableeTrack);
            builder.show();
            return;
        }
        int trackInfoNum = 0;
        trackInfoNum = trackInfo.length;

        if (MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO == TrackType) {
            ArrayList<String> ListAudio = new ArrayList();
            ArrayList<Integer> ListAudioIdx = new ArrayList();
            int idx = 1;
            for (int i = 0; i < trackInfoNum; i++) {
                String at = null;
                if (trackInfo[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                    /*
                     * at = "Language-" +trackInfo[i].getLanguage() + "-" +
                     * mContext.getString(R.string.audioTrack)+ "# " + idx;
                     */
                    at = mContext.getString(R.string.audioTrack) + "# " + idx;
                    idx++;
                } else {
                    continue;
                }
                ListAudio.add(at);
                ListAudioIdx.add(i);
            }
            Log.v(TAG,
                    "---AudioAndSubtitle showDialogForTrack: Audio.TrackInfo.size = "
                            + ListAudio.size());
            if (ListAudio.size() < MINI_TRACK_INFO_NUM) {
                ListAudio.add(mContext
                        .getString(R.string.noAvailableeAudioTrack));
                ListAudioIdx.add(0);
            }
            String[] at1 = new String[ListAudio.size()];
            ListAudio.toArray(at1);
            Integer[] AudioIdx = new Integer[ListAudioIdx.size()];
            ListAudioIdx.toArray(AudioIdx);
            showDialog2Disp(at1, mSelectAudioIndex, AudioIdx,
                    MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO);
        } else if (MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT == TrackType) {
            ArrayList<String> ListSubTitle = new ArrayList();
            ArrayList<Integer> ListSubTitleIdx = new ArrayList();
            ListSubTitle.add(mContext.getString(R.string.closeSubtitle));
            ListSubTitleIdx.add(0);
            int idx = 1;
            for (int i = 0; i < trackInfoNum; i++) {
                String at = null;
                if (trackInfo[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                    /*
                     * at = "Language-" +trackInfo[i].getLanguage() + "-" +
                     * mContext.getString(R.string.SubtitleSetting)+ "# " + idx;
                     */
                    at = mContext.getString(R.string.SubtitleSetting) + "# "
                            + idx;
                    idx++;
                } else {
                    continue;
                }
                ListSubTitle.add(at);
                ListSubTitleIdx.add(i);
            }
            Log.v(TAG, "---AudioAndSubtitle showDialogForTrack: list.size ="
                    + ListSubTitle.size());
            String[] at1 = new String[ListSubTitle.size()];
            ListSubTitle.toArray(at1);
            Integer[] SubTitleIdx = new Integer[ListSubTitleIdx.size()];
            ListSubTitleIdx.toArray(SubTitleIdx);
            if (mSelectSubTitleIndex >= ListSubTitleIdx.size()) {
                // if external subtitle has been deleted
                mSelectSubTitleIndex = 0;
            }
            showDialog2Disp(at1, mSelectSubTitleIndex, SubTitleIdx,
                    MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT);
        }
    }

        private AlertDialog mAudioOrSubtitleDialog = null;


    private void showDialog2Disp(String[] Track, int index,
            final Integer[] TrackIdx, final int TrackType) {

        Log.i(TAG, "AudioAndSubTitleChange showDialog2Disp: showSeclectDialog ");
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivityContext);
        if (MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO == TrackType) {
            builder.setTitle(R.string.audioTrackChange);
            builder.setSingleChoiceItems(Track, index,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            // TODO
                        Log.v(TAG, "AudioAndSubTitleChange  onClick whichButton = " + whichButton);
                            if (mSelectAudioIndex == whichButton) {
                                Log.v(TAG, "AudioAndSubTitleChange  onClick whichButton SameChoice");
                                dialog.dismiss();
                                return;
                            }
                            if (RETURN_ERROR == mVideoView.selectTrack(TrackIdx[whichButton].intValue())) {
                                if (mTState == TState.PAUSED) {
                                    Log.v(TAG, "AudioAndSubTitleChange --- onClick if has error after selectTrack");
                                    playVideo();
                                    mVideoView.selectTrack(TrackIdx[whichButton].intValue());
                                    pauseVideo();
                                }
                            }
                            mSelectAudioIndex = whichButton;
                            mSelcetAudioTrackIdx = TrackIdx[whichButton]
                                    .intValue();
                            dialog.dismiss();
                        }
                    });
            builder.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {

                }
            });
        } else if (MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT == TrackType) {
            builder.setTitle(R.string.subtitleTrackChange);
            builder.setSingleChoiceItems(Track, index,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            // TODO
                            Log.v(TAG,
                                    "AudioAndSubTitleChange --- onClick whichButton = "
                                            + whichButton);
                            if (mSelectSubTitleIndex == whichButton) {
                                Log.v(TAG, "AudioAndSubTitleChange --- onClick whichButton SameChoice");
                                dialog.dismiss();
                            } else {
                                if (whichButton == 0) {
                                    mVideoView.deselectTrack(TrackIdx[mSelectSubTitleIndex].intValue());
                                    updateSubtitleView(null);
                                    mSelectSubTitleIndex = whichButton;
                                    mSelectSubTitleTrackIdx = TrackIdx[whichButton].intValue();
                                    dialog.dismiss();
                                } else {
                                    if (mSelectSubTitleIndex != 0) {
                                        mVideoView.deselectTrack(TrackIdx[mSelectSubTitleIndex].intValue());
                                        updateSubtitleView(null);
                                    }
                                    mVideoView.selectTrack(TrackIdx[whichButton].intValue());
                                    mSelectSubTitleIndex = whichButton;
                                    mSelectSubTitleTrackIdx = TrackIdx[whichButton].intValue();
                            dialog.dismiss();
                        }
        }
                        }
                    });
            builder.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {

                }
            });
        }

        if (mAudioOrSubtitleDialog != null) {
            mAudioOrSubtitleDialog.dismiss();
            mAudioOrSubtitleDialog = null;
        }

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface arg0) {

                //mPlayerExt.pauseIfNeed(); mark for  no pause audio change
            }
        });
        dialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface arg0) {

                mPlayerExt.resumeIfNeed();
            }
        });
        dialog.show();
        mAudioOrSubtitleDialog = dialog;
   }

    private void updateSubtitleView(TimedText text) {
        mSubTitleView.setTextOrBitmap(text);
    }
   ///@}

    public int getStepOptionValue() {
        final String slectedStepOption = "selected_step_option";
        final String videoPlayerData = "video_player_data";
        final int stepBase = 3000;
        final String stepOptionThreeSeconds = "0";
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return (Integer.parseInt(mPrefs.getString(slectedStepOption, stepOptionThreeSeconds)) + 1)
                * stepBase;
    }

    public class MoviePlayerExtension implements IMoviePlayer, Restorable {
        private static final String KEY_VIDEO_IS_LOOP = "video_is_loop";

        private BookmarkEnhance mBookmark; //for bookmark
        private String mAuthor; //for detail
        private String mTitle; //for detail
        private String mCopyRight; //for detail
        private boolean mIsLoop;
        private boolean mLastPlaying;
        private boolean mLastCanPaused;
        private boolean mPauseBuffering;
        private boolean mResumeNeed = false;

        @Override
        public void stopVideo() {
            if (LOG) {
                Log.v(TAG, "stopVideo()");
            }
            mTState = TState.STOPED;
            mVideoView.clearSeek();
            mVideoView.clearDuration();
            mVideoView.stopPlayback();
            mVideoView.setResumed(false);
            mVideoView.setVisibility(View.INVISIBLE);
            mVideoView.setVisibility(View.VISIBLE);
            if (MtkVideoFeature.isEnableMultiWindow()) {
                mBlackCover.setVisibility(View.VISIBLE);
            }
            clearVideoInfo();
            mFirstBePlayed = false;
            mController.setCanReplay(true);
            ///slidevideo
            mActivityContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mController.showEnded();
                    if (mVideoView.isInFilmMode()) {
                        mController.hideController();
                    }
                }
            });
            mController.setViewEnabled(true);
            //FOR MTK_AUDIO_CHANGE_SUPPORT
            //@{
            if (MtkVideoFeature.isAudioChangeSupport()) {
                ((MovieActivity) mActivityContext).setAudioMenuItemVisible(false);
            }
            ///@}
            // FOR MTK_SUBTITLE_SUPPORT
            if (MtkVideoFeature.isSubTitleSupport()) {
                if (null != mSubTitleView) { // M: FOR MTK_SUBTITLE_CHANGE_SUPPORT
                    updateSubtitleView(null);
                }
                ((MovieActivity) mActivityContext).setSubtitleMenuItemVisible(false);
            }
            //@}
            setProgress();
        }

        @Override
        public boolean canStop() {
            boolean stopped = false;
            if (mController != null && mOverlayExt != null) {
                stopped = mOverlayExt.isPlayingEnd();
            }
            if (LOG) {
                Log.v(TAG, "canStop() stopped=" + stopped);
            }
            return !stopped;
        }

        @Override
        public void addBookmark() {
            if (mBookmark == null) {
                mBookmark = new BookmarkEnhance(mActivityContext);
            }
            String uri = String.valueOf(mMovieItem.getUri());
            if (mBookmark.exists(uri)) {
                Toast.makeText(mActivityContext, R.string.bookmark_exist, Toast.LENGTH_SHORT).show();
            } else {
                mBookmark.insert(mTitle, uri, mMovieItem.getMimeType(), 0);
                Toast.makeText(mActivityContext, R.string.bookmark_add_success, Toast.LENGTH_SHORT).show();
            }
            if (LOG) {
                Log.v(TAG, "addBookmark() mTitle=" + mTitle + ", mUri=" + mMovieItem.getUri());
            }
        }

        @Override
        public boolean getLoop() {
            if (LOG) {
                Log.v(TAG, "getLoop() return " + mIsLoop);
            }
            return mIsLoop;
        }
        /// M: FOR  MTK_SUBTITLE_SUPPORT
        /// @{
        @Override
        public void showSubtitleViewSetDialog() {

            if (mSubtitleSetDialog != null) {
                mSubtitleSetDialog.dismiss();
            }
            mSubtitleSetDialog = new SubtitleSettingDialog(mActivityContext, mSubTitleView);
            mSubtitleSetDialog.setOnShowListener(new OnShowListener() {

                @Override
                public void onShow(DialogInterface dialog) {
                    if (LOG) {
                        Log.v(TAG, "mSubtitleSetDialog.onShow()");
                    }
                    mIsDialogShow = true;
                    pauseIfNeed();
                }
            });
            mSubtitleSetDialog.setOnDismissListener(new OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (LOG) {
                        Log.v(TAG, "mSubtitleSetDialog.onDismiss()");
                    }
                    mIsDialogShow = false;
                    // For rtsp streaming mIsBuffering is true when suspend and
                    // wake up.Only 701 is received.So we does not check
                    // mIsBuffering value when resume.
                    if (!MovieUtils.isLiveStreaming(mVideoType) && !isPlaying()
                            && mTState != TState.RETRY_ERROR) {
                        resumeIfNeed();
                    }
                }
            });
            mSubtitleSetDialog.show();
        }
        ///@}
        @Override
        public void setLoop(boolean loop) {
            if (LOG) {
                Log.v(TAG, "setLoop(" + loop + ") mIsLoop=" + mIsLoop);
            }
            if (mVideoType == MovieUtils.VIDEO_TYPE_LOCAL) {
                mIsLoop = loop;
                if (mTState != TState.STOPED)
                {
                    mActivityContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mController.setCanReplay(mIsLoop);
                        }
                    });
                }
            }
        }

        @Override
        public void showDetail() {
            DetailDialog detailDialog =
                    new DetailDialog(mActivityContext, mTitle, mAuthor, mCopyRight);
            detailDialog.setTitle(R.string.media_detail);
            detailDialog.setOnShowListener(new OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    if (LOG) {
                        Log.v(TAG, "showDetail onShow() mIsBuffering is " + mIsBuffering
                                + " playing is " + isPlaying());
                    }
                    mIsDialogShow = true;
                    pauseIfNeed();
                }
            });
            detailDialog.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (LOG) {
                        Log.v(TAG, "showDetail onDismiss() mIsBuffering is " + mIsBuffering
                                + " playing is " + isPlaying());
                    }
                    mIsDialogShow = false;
                    // For rtsp streaming mIsBuffering is true when suspend and
                    // wake up.Only 701 is received.So we does not check
                    // mIsBuffering value when resume.
                    if (!MovieUtils.isLiveStreaming(mVideoType) && !isPlaying()
                            && mTState != TState.RETRY_ERROR) {
                        resumeIfNeed();
                    }
                }
            });
            detailDialog.show();
        }

        @Override
        public void startNextVideo(IMovieItem item) {
            IMovieItem next = item;
            if (next != null && next != mMovieItem) {
                int position = mVideoView.getCurrentPosition();
                int duration = mVideoView.getDuration();
                if (MtkVideoFeature.isAudioChangeSupport() || MtkVideoFeature.isSubTitleSupport()) {
                    mBookmarker.setBookmark(mMovieItem.getUri(), position, duration,
                    mSelcetAudioTrackIdx, mSelectSubTitleTrackIdx, mSelectAudioIndex, mSelectSubTitleIndex);
                } else {
                    mBookmarker.setBookmark(mMovieItem.getUri(), position, duration);
                }
                //mBookmarker.setBookmark(mMovieItem.getUri(), position, duration);
                mVideoView.stopPlayback();
                mVideoView.setVisibility(View.INVISIBLE);
                clearVideoInfo();
                mMovieItem = next;
                ((MovieActivity) mActivityContext).refreshMovieInfo(mMovieItem);
                stopSlowMotionChecker();
                mSlowMotionItem.updateItemUri(mMovieItem.getUri());
                startSlowMotionChecker();
                mController.refreshMovieInfo(mMovieItem);
                mFirstBePlayed = true;
                doStartVideoCareDrm(false, 0, 0);
                if (mSlowMotionItem.isSlowMotionVideo()) {
                    mController.show(); // Slow motion video will show UI for a while.
                }
                if (mZoomController != null) {
                    mZoomController.reset();
                }
                mVideoView.setVisibility(View.VISIBLE);
            } else {
                Log.e(TAG, "Cannot play the next video! " + item);
            }
            mActivityContext.closeOptionsMenu();
        }

        @Override
        public void onRestoreInstanceState(Bundle icicle) {
            mIsLoop = icicle.getBoolean(KEY_VIDEO_IS_LOOP, false);
            if (mIsLoop) {
                mController.setCanReplay(true);
            } // else  will get can replay from intent.
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putBoolean(KEY_VIDEO_IS_LOOP, mIsLoop);
        }

        private void pauseIfNeed() {
            mLastCanPaused = canStop() && mVideoView.canPause();
            if (mLastCanPaused) {
                Log.v(TAG, "pauseIfNeed mTState= " + mTState);
                mLastPlaying = (mTState == TState.PLAYING);
                ///M: Reset flag , we don't want use the last result.
                mPlayPauseProcessExt.mPlayVideoWhenPaused = false;
                if (mVideoView.isCurrentPlaying() && onIsRTSP()) {
                   mPlayPauseProcessExt.mPauseSuccess = false;
                   mPlayPauseProcessExt.mNeedCheckPauseSuccess = true;
                }
                if (!MovieUtils.isLiveStreaming(mVideoType) && isPlaying() && !mIsBuffering) {
                    mPauseBuffering = true;
                    mOverlayExt.clearBuffering();
                    pauseVideo();
                }
            }
            if (LOG) {
                Log.v(TAG, "pauseIfNeed() mLastPlaying=" + mLastPlaying + ", mLastCanPaused=" + mLastCanPaused
                    + ", mPauseBuffering= " + mPauseBuffering + " mTState=" + mTState);
            }
        }

        private void resumeIfNeed() {
            if (mLastCanPaused) {
                if (mLastPlaying) {
                    mPauseBuffering = false;
                    ///M: restore mTsate firstly. Because playvideo() maybe happened in onInfo().
                    mTState = TState.PLAYING;
                    mPlayPauseProcessExt.CheckPauseSuccess();
                }
            }
            if (LOG) {
                Log.v(TAG, "resumeIfNeed() mLastPlaying=" + mLastPlaying + ", mLastCanPaused=" + mLastCanPaused
                    + ", mPauseBuffering=" + mPauseBuffering);
            }
        }



        public boolean pauseBuffering() {
            return mPauseBuffering;
        }

        public void setVideoInfo(Metadata data) {
            if (data.has(Metadata.TITLE)) {
                mTitle = data.getString(Metadata.TITLE);
            }
            if (data.has(Metadata.AUTHOR)) {
                mAuthor = data.getString(Metadata.AUTHOR);
            }
            if (data.has(Metadata.COPYRIGHT)) {
                mCopyRight = data.getString(Metadata.COPYRIGHT);
            }
        }

        @Override
        public int getVideoType() {
            return mVideoType;
        }
        @Override
        public int getVideoPosition() {
            return mVideoPosition;
        }
        @Override
        public int getVideoLastDuration() {
            return mVideoLastDuration;
        }
        @Override
        public void startVideo(final boolean enableFasten, final int position, final int duration) {
            doStartVideoCareDrm(enableFasten, position, duration);
        }
        @Override
        public void notifyCompletion() {
            onCompletion();
        }
        public SurfaceView getVideoSurface() {
            return getVideoSurface();
        }
        public boolean canSeekForward() {
            return mVideoView.canSeekForward();
        }
        public boolean canSeekBackward() {
            return mVideoView.canSeekBackward();
        }
        public boolean isVideoCanSeek() {
            return mVideoCanSeek;
        }
        public void seekTo(int msec) {
            mVideoView.seekTo(msec);
        }
        public void setDuration(int duration) {
            mVideoView.setDuration(duration);
        }
        public int getCurrentPosition() {
            return mVideoView.getCurrentPosition();
        }
        public int getDuration() {
            return mVideoView.getDuration();
        }
        public Animation getHideAnimation() {
            return mController.getHideAnimation();
        }
        public boolean isTimeBarEnabled() {
            // /M:Add for slideVideo,mController maybe null when slide to next.
            if (mController == null && FeatureConfig.supportSlideVideoPlay) {
                mController = MovieControllerOverlay.getMovieController(mActivityContext);
            }
            return mController.isTimeBarEnabled();
        }
        public void updateProgressBar() {
            mHandler.post(mProgressChecker);
        }
        public void showEnded() {
            mController.showEnded();
        }

        /// M: when need show movie controller. @{
        /**
         * Add for plugin to show controller.
         */
        public void showMovieController() {
            mActivityContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mController.show();
                }
            });
        }
        /// @}
    }

    // / M: [FEATURE.ADD] SlideVideo@{
    public class SlideVideoExt implements IVideoPlayer {
        private IActivityHooker hooker = VideoHookerCtrlImpl.getHooker();

        @Override
        public IVideoTexture getVideoSurface() {
            return (IVideoTexture) mVideoView;
        }
        @Override
        public boolean getLoop() {
            return mPlayerExt.getLoop();
        }
        
        @Override
        public void setLoop(boolean loop) {
            mPlayerExt.setLoop(loop);
        }
        
        @Override
        public void updateHooker() {
            hooker.setParameter(null, getVideoSurface());
            hooker.setParameter(null, MoviePlayer.this);
            hooker.setParameter(null, mPlayerExt);
        }

        @Override
        public void setMovieControllerListener() {
            Log.v(TAG, "setMovieControllerListener()");
            mAudioBecomingNoisyReceiver.register();
            /// M: [FEATURE.ADD] add for slow motion, do not movie these codes to runOnUiThread().@{
            if (mController == null) {
                mController = MovieControllerOverlay.getMovieController(mContext);
            }
            mController.setSlideVideoTexture(mVideoView);
            startSlowMotionChecker();
            /// @}
            
            mActivityContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mController == null) {
                        mController = MovieControllerOverlay.getMovieController(mContext);
                    }
                    mController.setListener(MoviePlayer.this);
                    mController.showPlaying();
                    if (!mSlowMotionItem.isSlowMotionVideo() || mVideoView.isInFilmMode()) {
                        mController.hide(); //slow motion video will show UI for a while.
                    }
                    mScreenModeExt.setScreenMode();
                }
            });

            mHandler.post(mProgressChecker);

            if (mWfdConnectReceiver != null) {
                mWfdConnectReceiver.register();
            } else if (mWfdPowerSaving == null) {
                // register wfd receiver
                mWfdConnectReceiver = new WfdConnectReceiver();
                mWfdConnectReceiver.register();
            }
        }

        @Override
        public void clearMovieControllerListener() {
            try {
                mAudioBecomingNoisyReceiver.unregister();
            } catch (IllegalArgumentException exception) {
                Log.w(TAG, "clearMovieControllerListener error" + exception.getMessage());
            }
            mActivityContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mController != null && mOverlayExt != null) {
                        mController.showPaused();
                        // reset bottom panel to avoid mPlayPauseReplayView is
                        // always
                        // show when slide from a audio only video to a picture
                        mOverlayExt.setBottomPanel(false, false);
                        mController.hide();
                        mController.setListener(null);
                    }
                }
            });
            mHandler.removeCallbacks(mProgressChecker);
            stopSlowMotionChecker();
            if (mWfdConnectReceiver != null) {
                mWfdConnectReceiver.unregister();
                mWfdConnectReceiver = null;
            }
        }
    }

    // / @}

    /**
     * Play/pause asynchronous processing.
     */
    private class PlayPauseProcessExt implements MediaPlayer.OnInfoListener {
        public boolean mPauseSuccess = false;
        public boolean mNeedCheckPauseSuccess = false;
        public boolean mPlayVideoWhenPaused = false;

        /**
         * Check Pause is success or not. if success, it will start play, or
         * will start play when success is come in onInfo().
         */
        private void CheckPauseSuccess() {
            Log.v(TAG, "CheckPauseSuccess() mNeedCheckPauseSuccess=" + mNeedCheckPauseSuccess
                    + ", mPauseSuccess=" + mPauseSuccess);
            if (mNeedCheckPauseSuccess == true) {
                if (mPauseSuccess == true) {
                    playVideo();
                    mPauseSuccess = false;
                    mNeedCheckPauseSuccess = false;
                } else {
                    mPlayVideoWhenPaused = true;
                    mController.setViewEnabled(false);
                }
            } else {
                playVideo();
            }
        }

        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            if (what == MediaPlayer.MEDIA_INFO_PAUSE_COMPLETED
                    || what == MediaPlayer.MEDIA_INFO_PLAY_COMPLETED) {
                Log.v(TAG, "onInfo is PAUSE PLAY COMPLETED");
                if (extra == IMtkVideoController.PAUSE_PLAY_SUCCEED) {
                    if (what == MediaPlayer.MEDIA_INFO_PAUSE_COMPLETED) {
                        handlePauseComplete();
                    }
                } else {
                    if (extra != ERROR_INVALID_OPERATION && extra != ERROR_ALREADY_EXISTS) {
                        showNetWorkErrorDialog();
                    }
                }
                if (mVideoView.canPause()) {
                    // When it reach retry count and streaming can not connect
                    // to server,the rewind and forward button should be
                    // disabled,play/pause complete information maybe notified
                    // after retry error occurred,so we double check retry error
                    // and disable the UI again
                    if (mTState == TState.RETRY_ERROR && mRetryExt.reachRetryCount()) {
                        mOverlayExt.setCanScrubbing(false);
                        if (mControllerRewindAndForwardExt != null
                                && mControllerRewindAndForwardExt.getView() != null) {
                            mControllerRewindAndForwardExt.setViewEnabled(false);
                        }
                    } else {
                        mController.setViewEnabled(true);
                        if (mControllerRewindAndForwardExt != null) {
                            mControllerRewindAndForwardExt.updateRewindAndForwardUI();
                        }
                    }
                }
                return true;
            }
            return false;
        }

        /**
         * Judge if need play video in onInfo.
         */
        private void handlePauseComplete() {
            Log.v(TAG, "handlePauseComplete() mNeedCheckPauseSuccess=" + mNeedCheckPauseSuccess
                    + ", mPlayVideoWhenPaused=" + mPlayVideoWhenPaused);
            if (mNeedCheckPauseSuccess == true) {
                mPauseSuccess = true;
            }
            if (mPlayVideoWhenPaused == true) {
                mVideoView.start();
                ///slidevideo
                mActivityContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mController.showPlaying();
                    }
                });
                mPauseSuccess = false;
                mNeedCheckPauseSuccess = false;
                mPlayVideoWhenPaused = false;
            }
        }

        /**
         * Show dialog to user if play/pause is failed.Notify that only socket
         * error(except invalid operation and already exists error) will cause
         * network connection failed and should show the dialog.
         */
        private void showNetWorkErrorDialog() {
            final String errorDialogTag = "ERROR_DIALOG_TAG";
            FragmentManager fragmentManager = ((Activity) mActivityContext).getFragmentManager();
            DialogFragment fragment =
                    ErrorDialogFragment
                            .newInstance(R.string.VideoView_error_text_connection_failed);
            fragment.show(fragmentManager, errorDialogTag);
            fragmentManager.executePendingTransactions();
        }
    }



    private final Runnable mRemoveBackground = new Runnable() {
        @Override
        public void run() {
            if (LOG) {
                Log.v(TAG, "mRemoveBackground.run()");
            }
            if (mWfdPowerSaving != null) {
                /// M: In WFD Extension mode, UI and Background will always show.
                if (!mWfdPowerSaving.needShowController() && !MtkVideoFeature.isEnableMultiWindow()) {
                    mRootView.setBackground(null);
                }
            } else if (!MtkVideoFeature.isEnableMultiWindow()) {
                mRootView.setBackground(null);
            }
        }
    };

    /// M: same as launch case to delay transparent. @{
    private Runnable mDelayVideoRunnable = new Runnable() {
        @Override
        public void run() {
            if (LOG) {
                Log.v(TAG, "mDelayVideoRunnable.run()");
            }
            mVideoView.setVisibility(View.VISIBLE);
        }
    };
    /// @}

    // / M: when show resuming dialog, suspend->wake up, will play video. @{
    private boolean mIsShowResumingDialog;
    // / @}

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        setProgress();
    }

    /// @}
}

class Bookmarker {
    private static final String TAG = "Gallery2/Bookmarker";

    private static final String BOOKMARK_CACHE_FILE = "bookmark";
    private static final int BOOKMARK_CACHE_MAX_ENTRIES = 100;
    private static final int BOOKMARK_CACHE_MAX_BYTES = 10 * 1024;
    private static final int BOOKMARK_CACHE_VERSION = 1;

    private static final int HALF_MINUTE = 30 * 1000;
    private static final int TWO_MINUTES = 4 * HALF_MINUTE;

    private final Context mContext;

    public Bookmarker(Context context) {
        mContext = context;
    }

    public void setBookmark(Uri uri, int bookmark, int duration) {
        if (LOG) {
            Log.v(TAG, "setBookmark(" + uri + ", " + bookmark + ", " + duration + ")");
        }
        try {
            //do not record or override bookmark if duration is not valid.
            if (duration <= 0) {
                return;
            }
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeUTF(uri.toString());
            dos.writeInt(bookmark);
            dos.writeInt(Math.abs(duration));
            dos.flush();
            cache.insert(uri.hashCode(), bos.toByteArray());
        } catch (Throwable t) {
            Log.w(TAG, "setBookmark failed", t);
        }
    }
    /// M: FOR MTK_AUDIO_CHANGE_SUPPORT & MTK_SUBTITLE_SUPPORT
    ///@{
    public void setBookmark(Uri uri, int bookmark, int duration,
                            int audioIdx, int subtitleIdx, int audioDlgListIdx, int  subtitleDlgListIdx) {
        if (LOG) {
            Log.v(TAG, "setBookmark(" + uri + ", " + bookmark + ", " + duration + ", "
                    + audioIdx + ", " + subtitleIdx + ", " + audioDlgListIdx + ", " + subtitleDlgListIdx + ")");
        }
        try {
            //do not record or override bookmark if duration is not valid.
            if (duration <= 0) {
                return;
            }
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeUTF(uri.toString());
            dos.writeInt(bookmark);
            dos.writeInt(Math.abs(duration));
            dos.writeInt(audioIdx);
            dos.writeInt(subtitleIdx);
            dos.writeInt(audioDlgListIdx);
            dos.writeInt(subtitleDlgListIdx);
            dos.flush();
            cache.insert(uri.hashCode(), bos.toByteArray());
        } catch (Throwable t) {
            Log.w(TAG, "setBookmark failed", t);
        }
    }
    ///@}

    public BookmarkerInfo getBookmark(Uri uri) {
        try {
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);

            byte[] data = cache.lookup(uri.hashCode());
            if (data == null) {
                if (LOG) {
                    Log.v(TAG, "getBookmark(" + uri + ") data=null. uri.hashCode()=" + uri.hashCode());
                }
                return null;
            }

            DataInputStream dis = new DataInputStream(
                    new ByteArrayInputStream(data));

            String uriString = DataInputStream.readUTF(dis);
            int bookmark = dis.readInt();
            int duration = dis.readInt();
            if (LOG) {
                Log.v(TAG, "getBookmark(" + uri + ") uriString=" + uriString + ", bookmark=" + bookmark
                        + ", duration=" + duration);
            }
            if (!uriString.equals(uri.toString())) {
                return null;
            }

            if ((bookmark < HALF_MINUTE) || (duration < TWO_MINUTES)
                    || (bookmark > (duration - HALF_MINUTE))) {
                return null;
            }
            if (MtkVideoFeature.isAudioChangeSupport() || MtkVideoFeature.isSubTitleSupport()) {
                int audioIdx = dis.readInt();
                int subtitleIdx = dis.readInt();
                int audioDlgListIdx = dis.readInt();
                int subtitleDlgListIdx = dis.readInt();
                return new BookmarkerInfo(bookmark, duration, audioIdx, subtitleIdx, audioDlgListIdx, subtitleDlgListIdx);
            }
            else {
                return new BookmarkerInfo(bookmark, duration);
            }

        } catch (Throwable t) {
            Log.w(TAG, "getBookmark failed", t);
        }
        return null;
    }

    private static final boolean LOG = true;
}

class BookmarkerInfo {
    public final int mBookmark;
    public final int mDuration;

    /// M: FOR MTK_AUDIO_CHANGE_SUPPORT & MTK_SUBTITLE_SUPPORT
    ///@{
    public int mSelectAudioIndexBmk;
    public int mSelcetAudioTrackIdxBmk;
    public int mSelectSubTitleIndexBmk;
    public int mSelectSubTitleTrackIdxBmk;
    ///@}
    public BookmarkerInfo(int bookmark, int duration) {
        this.mBookmark = bookmark;
        this.mDuration = duration;
    }
    /// M: FOR MTK_AUDIO_CHANGE_SUPPORT & MTK_SUBTITLE_SUPPORT
    ///@{
    public BookmarkerInfo(int bookmark, int duration,
            int audioIdx, int subtitleIdx, int audioDlgListIdx, int  subtitleDlgListIdx) {
        this.mBookmark = bookmark;
        this.mDuration = duration;
        this.mSelcetAudioTrackIdxBmk = audioIdx;
        this.mSelectAudioIndexBmk = audioDlgListIdx;
        this.mSelectSubTitleTrackIdxBmk = subtitleIdx;
        this.mSelectSubTitleIndexBmk = subtitleDlgListIdx;
    }
    ///@}

    @Override
    public String toString() {
        return new StringBuilder()
        .append("BookmarkInfo(bookmark=")
        .append(mBookmark)
        .append(", duration=")
        .append(mDuration)
        .append(")")
        .toString();
    }
}
