/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.app.CommonControllerOverlay.State;
import com.mediatek.gallery3d.ext.DefaultMovieItem;
import com.mediatek.gallery3d.ext.IActivityHooker;
import com.mediatek.gallery3d.ext.IMovieItem;
import com.mediatek.gallery3d.ext.IRewindAndForwardExtension;
import com.mediatek.gallery3d.ext.MovieUtils;
import com.mediatek.gallery3d.video.ExtensionHelper;
import com.mediatek.gallery3d.video.IContrllerOverlayExt;
import com.mediatek.gallery3d.video.IMtkVideoController;
import com.mediatek.gallery3d.video.MtkVideoFeature;
import com.mediatek.gallery3d.video.ScreenModeManager;
import com.mediatek.gallery3d.video.ScreenModeManager.ScreenModeListener;
import com.mediatek.gallery3d.video.SlowMotionBar;
import com.mediatek.gallery3d.video.SlowMotionController;
import com.mediatek.galleryfeature.SlideVideo.IVideoController;
import com.mediatek.galleryfeature.SlideVideo.IVideoController.ControllerHideListener;
import com.mediatek.galleryframework.base.MediaData;

/**
 * The playback controller for the Movie Player.
 */
public class MovieControllerOverlay extends CommonControllerOverlay implements
        AnimationListener, IVideoController {

    private boolean hidden;

    private final Handler handler;
    private final Runnable startHidingRunnable;
    private final Animation hideAnimation;

    /// M: @{
    private static final String TAG = "Gallery2/MovieControllerOverlay";
    private static final boolean LOG = true;
    private Context mContext;
    /// M: for different display
    private IRewindAndForwardExtension mControllerRewindAndForwardExt;
    private ScreenModeExt mScreenModeExt;
    private OverlayExtension mOverlayExt;
    // / M: mtk extension for overlay
    private ScreenModeManager mScreenModeManager;


    // / M: View used to show logo picture from metadata
    private ImageView mLogoView;
    private LogoViewExt mLogoViewExt = new LogoViewExt();
    // / @}

    // /M: @{
    // for slow motion bar
    private SlowMotionController mSlowMotionController;

    private static View mRootView;

    // / @}

    // / M: [FEATURE.ADD] SlideVideo@{
    private static MovieControllerOverlay mMovieController;
    private static Context mCurrentContext;
    public static MovieControllerOverlay getMovieController(Context context) {
        Log.i(TAG,"getMovieController context " + context);
        if (mMovieController == null) {
            mMovieController = new MovieControllerOverlay(context);
        } else if(!context.equals(mCurrentContext)){
            mMovieController.onContextChange(context);
        }
        mCurrentContext = context;
        return mMovieController;
    }
    
    private void onContextChange(Context context) {
        if (mSlowMotionController != null) {
            mSlowMotionController.onContextChange(context);
        }
    }
    public static void destroyMovieController() {
        mMovieController = null;
        mCurrentContext = null;
    }
    // / @}

    public MovieControllerOverlay(Context context) {
        super(context);
        mContext = context;
        handler = new Handler();
        startHidingRunnable = new Runnable() {
            @Override
            public void run() {
                if (mListener != null && mListener.wfdNeedShowController()) {
                    hide();
                } else {
                    startHiding();
                }
            }
        };

        hideAnimation = AnimationUtils
                .loadAnimation(context, R.anim.player_out);
        hideAnimation.setAnimationListener(this);

        mControllerRewindAndForwardExt = ExtensionHelper.getRewindAndForwardExtension(context);
        // add for slow motion controller
        if (MtkVideoFeature.isSlowMotionSupport()) {
            mSlowMotionController = new SlowMotionController(context, new SlowMotionBar.Listener() {
                @Override
                public void onScrubbingStart(int currentTime, int totalTime) {
                    // TODO Auto-generated method stub
                    onSlowMotionScrubbingStart(currentTime, totalTime);
                }

                @Override
                public void onScrubbingMove(int currentTime, int totalTime) {
                    // TODO Auto-generated method stub
                    onSlowMotionScrubbingMove(currentTime, totalTime);
                }

                @Override
                public void onScrubbingEnd(int currentTime, int totalTime) {
                    // TODO Auto-generated method stub
                    onSlowMotionScrubbingEnd(currentTime, totalTime);
                }

            });
            LayoutParams matchParent = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            addView(mSlowMotionController, matchParent);
            // for slow motion controller
        }
        addRewindAndForwardView();
        mScreenModeExt = new ScreenModeExt(context);
        mOverlayExt = new OverlayExtension();

        mLogoViewExt.init(context);
        hide();
    }

    public Animation getHideAnimation() {
        return hideAnimation;
    }

    public boolean isPlayPauseEanbled() {
        return mPlayPauseReplayView.isEnabled();
    }
    public boolean isTimeBarEnabled() {
        return mTimeBar.getScrubbing();
    }
    public IRewindAndForwardExtension getRewindAndForwardExtension() {
        return mControllerRewindAndForwardExt;
    }
    
    // / M: when RewindAndForwardView was removed, should call
    // addRewindAndForwardView again, or RewindAndForwardView will not be show@{
    public void addRewindAndForwardView() {
        if (mControllerRewindAndForwardExt != null
                && mControllerRewindAndForwardExt.getView() != null) {
            LinearLayout.LayoutParams wrapContent = new LinearLayout.LayoutParams(
                    mControllerRewindAndForwardExt.getAddedRightPadding(),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            final ViewParent parent = mControllerRewindAndForwardExt.getView()
                    .getParent();
            if (parent != null) {
                ((ViewGroup) parent).removeView(mControllerRewindAndForwardExt
                        .getView());
            }
            addView(mControllerRewindAndForwardExt.getView(), wrapContent);
        }
    }
    
    // /@}
    public void showPlaying() {
        if (!mOverlayExt.handleShowPlaying()) {
            mState = State.PLAYING;
            showMainView(mPlayPauseReplayView);
        }
        if (LOG) {
            Log.v(TAG, "showPlaying() state=" + mState);
        }
    }

    public void showPaused() {
        if (!mOverlayExt.handleShowPaused()) {
            mState = State.PAUSED;
            showMainView(mPlayPauseReplayView);
        }
        if (LOG) {
            Log.v(TAG, "showPaused() state=" + mState);
        }
    }

    public void showEnded() {
        mOverlayExt.onShowEnded();
        mState = State.ENDED;
        showMainView(mPlayPauseReplayView);
        if (LOG) {
            Log.v(TAG, "showEnded() state=" + mState);
        }
    }

    /**
     * Show loading icon.
     *
     * @param isHttp Whether the video is a http video or not.
     */
    public void showLoading(boolean isHttp) {
        mOverlayExt.onShowLoading(isHttp);
        mState = State.LOADING;
        showMainView(mLoadingView);
        if (LOG) {
            Log.v(TAG, "showLoading() state=" + mState);
        }
    }

    public void showErrorMessage(String message) {
        mOverlayExt.onShowErrorMessage(message);
        mState = State.ERROR;
        int padding = (int) (getMeasuredWidth() * ERROR_MESSAGE_RELATIVE_PADDING);
        mErrorView.setPadding(padding, mErrorView.getPaddingTop(), padding,
                mErrorView.getPaddingBottom());
        mErrorView.setText(message);
        showMainView(mErrorView);
    }

    @Override
    protected void createTimeBar(Context context) {
        mTimeBar = new TimeBar(context, this);
        /// M: set timebar id for test case @{
        int mTimeBarId = 8;
        mTimeBar.setId(mTimeBarId);
        /// @}
    }

    @Override
    public void setTimes(int currentTime, int totalTime, int trimStartTime, int trimEndTime) {
        mTimeBar.setTime(currentTime, totalTime, trimStartTime, trimEndTime);
    }

    public void setTimes(int currentTime, int totalTime) {
        if (mSlowMotionController != null) {
            mSlowMotionController.setTime(currentTime, totalTime);
        }
    }

    @Override
    public void hide() {
        boolean wasHidden = hidden;
        hidden = true;
        if (mListener == null
                || (mListener != null && !mListener.wfdNeedShowController())) {
            mPlayPauseReplayView.setVisibility(View.INVISIBLE);
            mLoadingView.setVisibility(View.INVISIBLE);
            // /M:pure video only show background
            if (!mOverlayExt.handleHide()) {
                setVisibility(View.INVISIBLE);
            }
            mBackground.setVisibility(View.INVISIBLE);
            mTimeBar.setVisibility(View.INVISIBLE);
            mScreenModeExt.onHide();
            if (mControllerRewindAndForwardExt != null
                    && mControllerRewindAndForwardExt.getView() != null) {
                mControllerRewindAndForwardExt.onHide();
            }

            if (mSlowMotionController != null) {
                mSlowMotionController.setVisibility(View.INVISIBLE);
            }
        }
        // /@}
        setFocusable(true);
        requestFocus();
        if (mListener != null && wasHidden != hidden) {
            mListener.onHidden();
            // / M: [FEATURE.ADD] SlideVideo@{
            // Gallery action bar should hide when controller hide
            if (mControllerHideListener != null) {
                mControllerHideListener.onControllerVisibilityChanged(false);
            }
            // / @}
        }

        if (LOG) {
            Log.v(TAG, "hide() wasHidden=" + wasHidden + ", hidden="
                    + hidden);
        }
    }

    private void showMainView(View view) {
        mMainView = view;
        mErrorView.setVisibility(mMainView == mErrorView ? View.VISIBLE
                : View.INVISIBLE);
        mLoadingView.setVisibility(mMainView == mLoadingView ? View.VISIBLE
                : View.INVISIBLE);
        mPlayPauseReplayView
                .setVisibility(mMainView == mPlayPauseReplayView ? View.VISIBLE
                        : View.INVISIBLE);
        mOverlayExt.onShowMainView();
        show();
    }

    @Override
    public void show() {
        if (mListener != null) {
        boolean wasHidden = hidden;
        hidden = false;
        updateViews();
        setVisibility(View.VISIBLE);
        setFocusable(false);
        if (mListener != null && wasHidden != hidden) {
            mListener.onShown();
            // / M: [FEATURE.ADD] SlideVideo@{
            if (mControllerHideListener != null) {
                 mControllerHideListener.onControllerVisibilityChanged(true);
            }
            // / @}
        }
        maybeStartHiding();
        if (LOG) {
            Log.v(TAG, "show() wasHidden=" + wasHidden + ", hidden="
                    + hidden + ", listener=" + mListener);
        }
    }
    }

    private void maybeStartHiding() {
        cancelHiding();
        if (mState == State.PLAYING) {
            handler.postDelayed(startHidingRunnable, 3000);
        }
        if (LOG) {
            Log.v(TAG, "maybeStartHiding() state=" + mState);
        }
    }

    private void startHiding() {
        startHideAnimation(mBackground);
        startHideAnimation(mTimeBar);
        mScreenModeExt.onStartHiding();
        if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
            mControllerRewindAndForwardExt.onStartHiding();
        }
        startHideAnimation(mPlayPauseReplayView);
    }

    private void startHideAnimation(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            view.startAnimation(hideAnimation);
        }
    }

    private void cancelHiding() {
        handler.removeCallbacks(startHidingRunnable);
        mBackground.setAnimation(null);
        mTimeBar.setAnimation(null);
        mScreenModeExt.onCancelHiding();
        if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
            mControllerRewindAndForwardExt.onCancelHiding();
        }
        mPlayPauseReplayView.setAnimation(null);
    }

    @Override
    public void onAnimationStart(Animation animation) {
        // Do nothing.
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
        // Do nothing.
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        hide();
    }

    public void onClick(View view) {
        if (LOG) {
            Log.v(TAG, "onClick(" + view + ") listener=" + mListener
                    + ", state=" + mState + ", canReplay=" + mCanReplay);
        }
        if (mListener != null) {
            if (view == mPlayPauseReplayView) {
                /// M: when state is retry connecting error, user can replay video
                if (mState == State.ENDED || mState == State.RETRY_CONNECTING_ERROR) {
                        mListener.onReplay();
                } else if (mState == State.PAUSED || mState == State.PLAYING) {
                    mListener.onPlayPause();
                    //set view disabled (play/pause asynchronous processing)
                    setViewEnabled(false);
                }
            }
        } else {
            mScreenModeExt.onClick(view);
            if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
                mControllerRewindAndForwardExt.onClick(view);
            }
        }
    }

  /*
   * set view enable
   * (non-Javadoc)
   * @see com.android.gallery3d.app.ControllerOverlay#setViewEnabled(boolean)
   */
  @Override
  public void setViewEnabled(boolean isEnabled) {
        if (mListener != null && mListener.onIsRTSP()) {
          Log.v(TAG, "setViewEnabled is " + isEnabled);
          mOverlayExt.setCanScrubbing(isEnabled);
          mPlayPauseReplayView.setEnabled(isEnabled);
          if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
              mControllerRewindAndForwardExt.setViewEnabled(isEnabled);
          }
      }
  }

  /*
   * set play pause button from disable to normal
   * (non-Javadoc)
   * @see com.android.gallery3d.app.ControllerOverlay#setViewEnabled(boolean)
   */
  @Override
  public void setPlayPauseReplayResume() {
      if (mListener != null && mListener.onIsRTSP()) {
          Log.v(TAG, "setPlayPauseReplayResume is enabled is true");
          mPlayPauseReplayView.setEnabled(true);
      }
    }

    /**
     * Get time bar enable status
     * @return true is enabled
     * false is otherwise
     */
    public boolean getTimeBarEnabled() {
        return mTimeBar.getScrubbing();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (hidden) {
            show();
        }
        return super.onKeyDown(keyCode, event);
    }

    ///M: remove it  for Video zoom feature.
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        if (super.onTouchEvent(event)) {
//            return true;
//        }
//        if (hidden) {
//            show();
//            return true;
//        }
//        switch (event.getAction()) {
//        case MotionEvent.ACTION_DOWN:
//            cancelHiding();
//            // you can click play or pause when view is resumed
//            // play/pause asynchronous processing
//        if ((mState == State.PLAYING || mState == State.PAUSED) && mOverlayExt.mEnableScrubbing) {
//            mListener.onPlayPause();
//            }
//            break;
//        case MotionEvent.ACTION_UP:
//            maybeStartHiding();
//            break;
//        }
//        return true;
//    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = ((Activity) mContext).getWindowManager().getDefaultDisplay().getWidth();
        Rect insets = mWindowInsets;
        int pl = insets.left; // the left paddings
        int pr = insets.right;
        int pt = insets.top;
        int pb = insets.bottom;

        int h = bottom - top;
        int w = right - left;
        boolean error = mErrorView.getVisibility() == View.VISIBLE;

        int y = h - pb;
        // Put both TimeBar and Background just above the bottom system
        // component.
        // But extend the background to the width of the screen, since we don't
        // care if it will be covered by a system component and it looks better.

        // Needed, otherwise the framework will not re-layout in case only the
        // padding is changed
        if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
            mBackground.layout(0, y - mTimeBar.getPreferredHeight() - mControllerRewindAndForwardExt.getHeight(), w, y);
            mTimeBar.layout(pl + pr, y - mTimeBar.getPreferredHeight() - mControllerRewindAndForwardExt.getHeight(), w - pr, y - mControllerRewindAndForwardExt.getHeight());
            mControllerRewindAndForwardExt.onLayout(pr, width, y, pr);
            if (mSlowMotionController != null) {
                mSlowMotionController.onLayout(insets, w, h);
            }
        } else {
            mBackground.layout(0, y - mTimeBar.getPreferredHeight(), w, y);
            mTimeBar.layout(pl, y - mTimeBar.getPreferredHeight(), w - pr - mScreenModeExt.getAddedRightPadding(), y);
            if (mSlowMotionController != null) {
                mSlowMotionController.onLayout(insets, w, h);
            }
        }
        mScreenModeExt.onLayout(w, pr, y);
        // Put the play/pause/next/ previous button in the center of the screen
        layoutCenteredView(mPlayPauseReplayView, 0, 0, w, h);
        layoutCenteredView(mAudioOnlyView, 0, 0, w, h);
        if (mMainView != null) {
            layoutCenteredView(mMainView, 0, 0, w, h);
        }
    }

    protected void updateViews() {
        if (hidden) {
            return;
        }
        mBackground.setVisibility(View.VISIBLE);
        mTimeBar.setVisibility(View.VISIBLE);
        mPlayPauseReplayView.setImageResource(
                mState == State.PAUSED ? R.drawable.videoplayer_play :
                    mState == State.PLAYING ? R.drawable.videoplayer_pause :
                        R.drawable.videoplayer_reload);
        mScreenModeExt.onShow();
        if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
            mControllerRewindAndForwardExt.onShow();
        }
        if (!mOverlayExt.handleUpdateViews()) {
            mPlayPauseReplayView.setVisibility(
                    (mState != State.LOADING && mState != State.ERROR &&
                    !(mState == State.ENDED && !mCanReplay))
                    ? View.VISIBLE : View.GONE);
        }
        if (mSlowMotionController != null) {
            mSlowMotionController.setVisibility(View.VISIBLE);
        }
        requestLayout();
        if (LOG) {
            Log.v(TAG, "updateViews() state=" + mState + ", canReplay="
                    + mCanReplay);
        }
    }

    // TimeBar listener

    @Override
    public void onScrubbingStart() {
        if (mSlowMotionController != null) {
            mSlowMotionController.setScrubbing(false);
        }
        cancelHiding();
        super.onScrubbingStart();
    }

    @Override
    public void onScrubbingMove(int time) {
        cancelHiding();
        super.onScrubbingMove(time);
    }

    @Override
    public void onScrubbingEnd(int time, int trimStartTime, int trimEndTime) {
        if (mSlowMotionController != null) {
            mSlowMotionController.setScrubbing(true);
        }
        maybeStartHiding();
        super.onScrubbingEnd(time, trimStartTime, trimEndTime);
    }

    void onSlowMotionScrubbingStart(int currentTime, int totalTime) {
        mTimeBar.setSlowMotionBarStatus(SlowMotionBar.SCRUBBERING_START);
        cancelHiding();
        super.onScrubbingStart();
    }

    void onSlowMotionScrubbingMove(int currentTime, int totalTime) {
        cancelHiding();
        super.onScrubbingMove(currentTime);
        mTimeBar.setTime(currentTime, totalTime, 0, 0);
    }

    void onSlowMotionScrubbingEnd(int currentTime, int totalTime) {
        mTimeBar.setSlowMotionBarStatus(SlowMotionBar.SCRUBBERING_END);
        maybeStartHiding();
        super.onScrubbingEnd(currentTime, 0, 0);
        mTimeBar.setTime(currentTime, totalTime, 0, 0);
    }

    public void refreshMovieInfo(IMovieItem info) {
        if (mSlowMotionController != null) {
            mSlowMotionController.refreshMovieInfo(info);
        }
    }

    public void setSlideVideoTexture(IMtkVideoController texture) {
        if (mSlowMotionController != null) {
            mSlowMotionController.setSlideVideoTexture(texture);
        }
    }

    public void setScreenModeManager(ScreenModeManager manager) {
        mScreenModeManager = manager;
        if (mScreenModeManager != null) {
            mScreenModeManager.addListener(mScreenModeExt);
        }
        if (LOG) {
            Log.v(TAG, "setScreenModeManager(" + manager + ")");
        }
    }

    public IContrllerOverlayExt getOverlayExt() {
        return mOverlayExt;
    }

    private class OverlayExtension implements IContrllerOverlayExt {
        private State mLastState = State.PLAYING;
        private String mPlayingInfo;
        // The logo picture from metadata
        private Drawable mLogoPic;

        public void showBuffering(boolean fullBuffer, int percent) {
            if (LOG) {
                Log.v(TAG, "showBuffering(" + fullBuffer + ", " + percent
                        + ") " + "lastState=" + mLastState + ", state=" + mState);
            }
            if (fullBuffer) {
                // do not show text and loading
                mTimeBar.setSecondaryProgress(percent);
                return;
            }
            if (mState == State.PAUSED || mState == State.PLAYING) {
                mLastState = mState;
            }
            if (percent >= 0 && percent < 100) { // valid value
                mState = State.BUFFERING;
                int msgId = com.mediatek.R.string.media_controller_buffering;
                String text = String.format(getResources().getString(msgId),
                        percent);
                mTimeBar.setInfo(text);
                showMainView(mLoadingView);
            } else if (percent == 100) {
                mState = mLastState;
                mTimeBar.setInfo(null);
                showMainView(mPlayPauseReplayView); // restore play pause state
            } else { // here to restore old state
                mState = mLastState;
                mTimeBar.setInfo(null);
            }
        }

        // set buffer percent to unknown value

        public void clearBuffering() {
            if (LOG) {
                Log.v(TAG, "clearBuffering()");
            }
            mTimeBar.setSecondaryProgress(TimeBar.UNKNOWN);
            showBuffering(false, TimeBar.UNKNOWN);
        }
        
        public void onCancelHiding() {
            cancelHiding();
        }
        
        public void showReconnecting(int times) {
            clearBuffering();
            mState = State.RETRY_CONNECTING;
            int msgId = R.string.VideoView_error_text_cannot_connect_retry;
            String text = getResources().getString(msgId, times);
            mTimeBar.setInfo(text);
            showMainView(mLoadingView);
            if (LOG) {
                Log.v(TAG, "showReconnecting(" + times + ")");
            }
        }

        public void showReconnectingError() {
            clearBuffering();
            mState = State.RETRY_CONNECTING_ERROR;
            int msgId = com.mediatek.R.string.VideoView_error_text_cannot_connect_to_server;
            String text = getResources().getString(msgId);
            mTimeBar.setInfo(text);
            showMainView(mPlayPauseReplayView);
            if (LOG) {
                Log.v(TAG, "showReconnectingError()");
            }
        }

        public void setPlayingInfo(boolean liveStreaming) {
            int msgId;
            if (liveStreaming) {
                msgId = com.mediatek.R.string.media_controller_live;
            } else {
                msgId = com.mediatek.R.string.media_controller_playing;
            }
            mPlayingInfo = getResources().getString(msgId);
            if (LOG) {
                Log.v(TAG, "setPlayingInfo(" + liveStreaming
                        + ") playingInfo=" + mPlayingInfo);
            }
        }

        // for pause feature
        private boolean mCanPause = true;
        private boolean mEnableScrubbing = false;

        public void setCanPause(boolean canPause) {
            this.mCanPause = canPause;
            if (LOG) {
                Log.v(TAG, "setCanPause(" + canPause + ")");
            }
        }

        public void setCanScrubbing(boolean enable) {
            mEnableScrubbing = enable;
            mTimeBar.setScrubbing(enable);
            if (mSlowMotionController != null) {
                mSlowMotionController.setScrubbing(enable);
            }
            if (LOG) {
                Log.v(TAG, "setCanScrubbing(" + enable + ")");
            }
        }
        ///M:for only audio feature.
        private boolean mAlwaysShowBottom;
        public void setBottomPanel(boolean alwaysShow, boolean foreShow) {
            mAlwaysShowBottom = alwaysShow;
            if (!alwaysShow) { // clear background
                mAudioOnlyView.setVisibility(View.INVISIBLE);
                setBackgroundColor(Color.TRANSPARENT);
                // Do not show mLogoView when change from audio-only video to
                // A/V video.
                if (mLogoPic != null) {
                    Log.v(TAG, "setBottomPanel() dissmiss orange logo picuture");
                    mLogoPic = null;
                    mLogoView.setImageDrawable(null);
                    mLogoView.setBackgroundColor(Color.TRANSPARENT);
                    mLogoView.setVisibility(View.GONE);
                }
            } else {
                // Don't set the background again when there is a logo picture
                // of the audio-only video
                if (mLogoPic != null) {
                    mAudioOnlyView.setVisibility(View.INVISIBLE);
                    mLogoView.setImageDrawable(mLogoPic);
                } else {
                    // / M: [FEATURE.ADD] SlideVideo@{
                    // audio only video picture should not be shown when in film
                    // mode
                    if (!mIsFilmMode) {
                        setBackgroundColor(Color.BLACK);
                        mAudioOnlyView.setVisibility(View.VISIBLE);
                    } else {
                        setBackgroundColor(Color.TRANSPARENT);
                        mAudioOnlyView.setVisibility(View.INVISIBLE);
                    }
                    // / @}
                }
                if (foreShow) {
                    setVisibility(View.VISIBLE);
                    // show();//show the panel
                    // hide();//hide it for jelly bean doesn't show control when
                    // enter the video.
                }
            }
            if (LOG) {
                Log.v(TAG, "setBottomPanel(" + alwaysShow + ", " + foreShow
                        + ")");
            }
        }

        public boolean handleHide() {
            if (LOG) {
                Log.v(TAG, "handleHide() mAlwaysShowBottom" + mAlwaysShowBottom);
            }
            return mAlwaysShowBottom;
        }


        /**
         * Set the picture which get from metadata.
         * @param byteArray The picture in byteArray.
         */
        public void setLogoPic(byte[] byteArray) {
            Drawable backgound = MovieUtils.bytesToDrawable(byteArray);
            setBackgroundDrawable(null);
            mLogoView.setBackgroundColor(Color.BLACK);
            mLogoView.setImageDrawable(backgound);
            mLogoView.setVisibility(View.VISIBLE);
            mLogoPic = backgound;
        }

        public boolean isPlayingEnd() {
            if (LOG) {
                Log.v(TAG, "isPlayingEnd() state=" + mState);
            }
            boolean end = false;
            if (State.ENDED == mState || State.ERROR == mState
                    || State.RETRY_CONNECTING_ERROR == mState) {
                end = true;
            }
            return end;
        }

        /**
         * Show playing information will be ignored when there is buffering
         * information updated.
         *
         * @return True if mState is changed from PLAYING to BUFFERING during
         *         showPlaying is called.
         */
        public boolean handleShowPlaying() {
            if (mState == State.BUFFERING) {
                mLastState = State.PLAYING;
                return true;
            }
            return false;
        }

        public boolean handleShowPaused() {
            mTimeBar.setInfo(null);
            if (mState == State.BUFFERING) {
                mLastState = State.PAUSED;
                return true;
            }
            return false;
        }

        /**
         * Show a information when loading or seeking
         *
         * @param isHttp Whether the video is a http video or not.
         */
        public void onShowLoading(boolean isHttp) {
            int msgId;
            if (isHttp) {
                msgId = R.string.VideoView_info_buffering;
            } else {
                msgId = com.mediatek.R.string.media_controller_connecting;
            }
            String text = getResources().getString(msgId);
            mTimeBar.setInfo(text);
        }

        public void onShowEnded() {
            clearBuffering();
            mTimeBar.setInfo(null);
        }

        public void onShowErrorMessage(String message) {
            clearBuffering();
        }

        public boolean handleUpdateViews() {
            mPlayPauseReplayView
                    .setVisibility((mState != State.LOADING
                            && mState != State.ERROR
                            &&
                            // !(state == State.ENDED && !canReplay) && //show
                            // end when user stopped it.
                            mState != State.BUFFERING
                            && mState != State.RETRY_CONNECTING && !(mState != State.ENDED
                            && mState != State.RETRY_CONNECTING_ERROR && !mCanPause))
                    // for live streaming
                    ? View.VISIBLE
                            : View.GONE);

            if (mPlayingInfo != null && mState == State.PLAYING) {
                mTimeBar.setInfo(mPlayingInfo);
            }
            return true;
        }

        public void onShowMainView() {
            if (LOG) {
                Log.v(TAG, "onShowMainView() enableScrubbing=" + mEnableScrubbing + ", state="
                        + mState);
            }
            if (mEnableScrubbing
                    && (mState == State.PAUSED || mState == State.PLAYING)) {
                mTimeBar.setScrubbing(true);
                if (mSlowMotionController != null) {
                    mSlowMotionController.setScrubbing(true);
                }
            } else {
                mTimeBar.setScrubbing(false);
                if (mSlowMotionController != null) {
                    mSlowMotionController.setScrubbing(false);
                }
            }
        }
    }

    class ScreenModeExt implements View.OnClickListener, ScreenModeListener {
        // for screen mode feature
        private ImageView mScreenView;
        private int mScreenPadding;
        private int mScreenWidth;

        private static final int MARGIN = 10; // dip
        private ViewGroup mParent;
        private ImageView mSeprator;


        public ScreenModeExt(Context context) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();

            LayoutParams wrapContent =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            //add screenView
            mScreenView = new ImageView(context);
            mScreenView.setImageResource(R.drawable.m_ic_media_fullscreen); //default next screen mode
            mScreenView.setScaleType(ScaleType.CENTER);
            mScreenView.setFocusable(true);
            mScreenView.setClickable(true);
            mScreenView.setOnClickListener(this);
            addView(mScreenView, wrapContent);

            if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
                Log.v(TAG, "ScreenModeExt enableRewindAndForward");
                mSeprator = new ImageView(context);
                mSeprator.setImageResource(R.drawable.m_ic_separator_line); //default next screen mode
                mSeprator.setScaleType(ScaleType.CENTER);
                mSeprator.setFocusable(true);
                mSeprator.setClickable(true);
                mSeprator.setOnClickListener(this);
                addView(mSeprator, wrapContent);

            } else {
                Log.v(TAG, "ScreenModeExt disableRewindAndForward");
            }

            //for screen layout
            Bitmap screenButton = BitmapFactory.decodeResource(context.getResources(), R.drawable.m_ic_media_bigscreen);
            mScreenWidth = screenButton.getWidth();
            mScreenPadding = (int) (metrics.density * MARGIN);
            screenButton.recycle();
        }

        private void updateScreenModeDrawable() {
            int screenMode = mScreenModeManager.getNextScreenMode();
            if (screenMode == ScreenModeManager.SCREENMODE_BIGSCREEN) {
                mScreenView.setImageResource(R.drawable.m_ic_media_bigscreen);
            } else if (screenMode == ScreenModeManager.SCREENMODE_FULLSCREEN) {
                mScreenView.setImageResource(R.drawable.m_ic_media_fullscreen);
            } else {
                mScreenView.setImageResource(R.drawable.m_ic_media_cropscreen);
            }
        }

        @Override
        public void onClick(View v) {
            if (v == mScreenView && mScreenModeManager != null) {
                mScreenModeManager.setScreenMode(mScreenModeManager
                        .getNextScreenMode());
                show(); // show it?
            }
        }

        public void onStartHiding() {
            startHideAnimation(mScreenView);
        }

        public void onCancelHiding() {
            mScreenView.setAnimation(null);
        }

        public void onHide() {
            mScreenView.setVisibility(View.INVISIBLE);
            if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
                mSeprator.setVisibility(View.INVISIBLE);
            }
        }
        public void onShow() {
            mScreenView.setVisibility(View.VISIBLE);
            if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
                mSeprator.setVisibility(View.VISIBLE);
            }
        }
        public void onLayout(int width, int paddingRight, int yPosition) {
            // layout screen view position
            int sw = getAddedRightPadding();
            int sepratorWidth = 2;
            if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
                mScreenView.layout(width - paddingRight - sw, yPosition
                        - mControllerRewindAndForwardExt.getHeight(), width - paddingRight,
                        yPosition);
            } else {
               mScreenView.layout(width - paddingRight - sw, yPosition
                    - mTimeBar.getBarHeight(), width - paddingRight,
                    yPosition);
            }
            if (mControllerRewindAndForwardExt != null && mControllerRewindAndForwardExt.getView() != null) {
                int controllerButtonPosition = mControllerRewindAndForwardExt.getControllerButtonPosition();
                int sepratorPosition = (width - paddingRight - sw - controllerButtonPosition) / 2 + controllerButtonPosition;
                mSeprator.layout(sepratorPosition , yPosition
                        - mControllerRewindAndForwardExt.getHeight(), sepratorPosition + sepratorWidth,
                        yPosition);
            }
        }

        public int getAddedRightPadding() {
            return mScreenPadding * 2 + mScreenWidth;
        }

        @Override
        public void onScreenModeChanged(int newMode) {
            updateScreenModeDrawable();
        }
    }

    // / @}

    // /M:Add LogoView for audio-only video.
    class LogoViewExt {
        private void init(Context context) {
            if (context instanceof MovieActivity) {
            // Add logo picture
            RelativeLayout movieView =
                    (RelativeLayout) ((MovieActivity) mContext).findViewById(R.id.movie_view_root);
            FrameLayout.LayoutParams matchParent =
                    new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT, Gravity.CENTER);
            mLogoView = new ImageView(mContext);
            mLogoView.setAdjustViewBounds(true);
            mLogoView.setMaxWidth(((MovieActivity) mContext).getWindowManager().getDefaultDisplay()
                    .getWidth());
            mLogoView.setMaxHeight(((MovieActivity) mContext).getWindowManager()
                    .getDefaultDisplay().getHeight());
            movieView.addView(mLogoView, matchParent);
            mLogoView.setVisibility(View.GONE);
            }

        }
    }
    // / @}

    // / M: [FEATURE.ADD] SlideVideo@{
    private boolean mIsFilmMode;
    @Override
    public void showController() {
        show();
    }

    @Override
    public void hideController() {
        hide();
    }

    @Override
    public void hideAudioOnlyIcon(boolean isFilmMode) {
        mIsFilmMode = isFilmMode;
        if (isFilmMode) {
            mOverlayExt.setBottomPanel(mOverlayExt.mAlwaysShowBottom, false);
        } else {
            mOverlayExt.setBottomPanel(mOverlayExt.mAlwaysShowBottom, true);
        }
    }
    
    @Override
    public void setData(MediaData data) {
        if (data != null) {
            String uri = "file://" + data.filePath;
            IMovieItem item = new DefaultMovieItem(uri, data.mimeType, data.caption);
            refreshMovieInfo(item);
        }
    }
    private ControllerHideListener mControllerHideListener;
    
    public void setControllerHideListener(ControllerHideListener listener) {
        mControllerHideListener = listener;
    }
    
    public IActivityHooker getRewindAndForwardHooker() {
        return (IActivityHooker)getRewindAndForwardExtension();
    }
    
    // / @}

}
