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

import android.app.Activity;
import android.graphics.Point;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.android.gallery3d.app.MovieControllerOverlay;

import com.mediatek.gallery3d.ext.DefaultActivityHooker;
import com.mediatek.gallery3d.video.VideoHookerCtrlImpl;
import com.mediatek.galleryfeature.platform.PlatformHelper;
import com.mediatek.galleryfeature.video.SlideVideoUtils;
import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.gl.MGLView;
import com.mediatek.galleryframework.util.MtkLog;

/**
 * 
 * The UI controller interact with player
 * 
 */
public class SlideVideoLayer extends Layer implements IVideoController.ControllerHideListener {
    private final static String TAG = "MtkGallery2/SlideVideoLayer";
    private IVideoController mController;
    private SlideVideoPlayer mPlayer;
    private IVideoHookerCtl mHookerCtl;
    // Whether video has been paused by scroll,when video has already been
    // paused,do not call pause again
    private Menu mMenu;
    private boolean mIsInFilmMode = false;
    private boolean mActivityHasPaused = false;
    private static int mScreenValue ;
    private Activity mActivity;
    
    @Override
    public void onActivityResume() {
        MtkLog.d(TAG, "<onActivityResume>");
        mActivityHasPaused = false;
        super.onActivityResume();
    }
    
    @Override
    public void onActivityPause() {
        MtkLog.d(TAG, "<onActivityPause>");
        mActivityHasPaused = true;
        super.onActivityPause();
    }
    
    @Override
    public boolean onSingleTapUp(float x, float y) {
        MtkLog.d(TAG, "<onSingleTapUp>");
        return super.onSingleTapUp(x, y);
    }
    
    @Override
    public boolean onDoubleTap(float x, float y) {
        MtkLog.d(TAG, "<onDoubleTap>");
        return super.onDoubleTap(x, y);
    }
    
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        MtkLog.d(TAG, "<onFling>");
        return super.onFling(e1, e2, velocityX, velocityY);
    }
    
    @Override
    public boolean onScaleBegin(float focusX, float focusY) {
        MtkLog.d(TAG, "<onScaleBegin>");
        return super.onScaleBegin(focusX, focusY);
    }
    
    @Override
    public boolean onScale(float focusX, float focusY, float scale) {
        MtkLog.d(TAG, "<onScale>");
        return super.onScale(focusX, focusY, scale);
    }
    
    @Override
    public void onScaleEnd() {
        MtkLog.d(TAG, "<onScaleEnd>");
        super.onScaleEnd();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MtkLog.d(TAG, "<onCreateOptionsMenu>");
        mMenu = menu;
        boolean result = mHookerCtl.onCreateOptionsMenu(menu);
        mMenu.setGroupVisible(DefaultActivityHooker.MENU_HOOKER_GROUP_ID, false);
        return result;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MtkLog.d(TAG, "<onPrepareOptionsMenu>");
        mMenu.setGroupVisible(DefaultActivityHooker.MENU_HOOKER_GROUP_ID, true);
        return mHookerCtl.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        MtkLog.d(TAG, "<onOptionsItemSelected>");
        return mHookerCtl.onOptionsItemSelected(item);
    }
    
    @Override
    public void onKeyEvent(KeyEvent event) {
        MtkLog.d(TAG, "<onKeyEvent>");
        super.onKeyEvent(event);
    }
    
    @Override
    public boolean onActionBarVisibilityChange(boolean newVisibility) {
        MtkLog.d(TAG, "<onActionBarVisibilityChange> newVisibility is " + newVisibility);
        if (newVisibility && !mIsInFilmMode) {
            mController.showController();
            return true;
        } else {
            mController.hideController();
            return false;
        }
    }
    
    @Override
    public void onChange(Player player, int what, int arg, Object obj) {
        MtkLog.d(TAG, "<onChange>");
    }
    
    @Override
    public void onCreate(Activity activity, ViewGroup root) {
        MtkLog.d(TAG, "<onCreate>");
        mActivity = activity;
        Point screenSize = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(screenSize);
        MtkLog.v(TAG, "onCreate mScreenValue " + mScreenValue + " screenSize " + screenSize);
        // when screen size change,controller and hooker should be
        // recreated(modify for smart book).
        if (mScreenValue != screenSize.x * screenSize.y) {
            MovieControllerOverlay.destroyMovieController();
            VideoHookerCtrlImpl.destoryHooker();
            mScreenValue = screenSize.x * screenSize.y;
        }
        mController = PlatformHelper.createController(activity);
        mHookerCtl = PlatformHelper.createHooker(activity, mController.getRewindAndForwardHooker());
        mController.setControllerHideListener(this);
    }
    
    @Override
    public void onResume(boolean isFilmMode) {
        MtkLog.d(TAG, "<onResume>");
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        onFilmModeChange(isFilmMode);
    }
    
    @Override
    public void onPause() {
        MtkLog.d(TAG, "<onPause>");
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mMenu != null) {
            mMenu.setGroupVisible(DefaultActivityHooker.MENU_HOOKER_GROUP_ID, false);
        }
        if (mController != null) {
            mController.hideController();
        }
        if (mPlayer != null) {
            // setPlayState will save loop and play position for slide video
            // when play again the video will keep loop setting and play at last
            // position,these setting will clear at onDestory clearRestoreDatas
            mPlayer.setPlayState(mActivityHasPaused);
            mPlayer.clearMovieControllerListener();
        }
    }
    
    @Override
    public void onDestroy() {
        SlideVideoUtils.clearRestoreDatas();
        MtkLog.d(TAG, "<onDestroy>");
    }
    
    @Override
    public void setData(MediaData data) {
        mHookerCtl.setData(data);
        mController.setData(data);
        MtkLog.d(TAG, "<setData> data is " + data);
    }
    
    @Override
    public void setPlayer(Player player) {
        MtkLog.d(TAG, "<setPlayer>");
        mPlayer = (SlideVideoPlayer) player;
        onFilmModeChange(mIsInFilmMode);
    }
    
    @Override
    public View getView() {
        MtkLog.d(TAG, "<getView>");
        View view = (View) mController;
        view.setVisibility(View.GONE);
        return view;
    }
    
    @Override
    public MGLView getMGLView() {
        return null;
    }
    
    @Override
    public void onControllerVisibilityChanged(boolean visibility) {
        MtkLog.d(TAG, "<onControllerVisibilityChanged> " + visibility);
        if (!visibility) {
            if (!mIsInFilmMode) {
                mBackwardContoller.toggleBars(false);
            }
        } else {
            mBackwardContoller.toggleBars(true);
        }
    }
    
    @Override
    public void onFilmModeChange(boolean isFilmMode) {
        MtkLog.v(TAG, "onFilmModeChange isFilmMode = " + isFilmMode);
        mIsInFilmMode = isFilmMode;
        if (mPlayer != null) {
            mPlayer.onFilmModeChange(isFilmMode);
        }
        if (mIsInFilmMode) {
            mController.hideController();
        } else {
            mController.showController();
        }
        mController.hideAudioOnlyIcon(mIsInFilmMode);
    }
}
