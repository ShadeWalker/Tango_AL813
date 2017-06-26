/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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
package com.mediatek.camera.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.os.SystemProperties;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.android.camera.R;

import java.io.IOException;

public class CameraAppGuide implements IAppGuide {
    public static final String GUIDE_TYPE_CAMERA = "CAMERA";
    
    private static final String TAG = "CameraAppGuide";
    private static final String SHARED_PREFERENCE_NAME = "application_guide";
    private static final String KEY_CAMERA_GUIDE = "camera_guide";
    private static final String KEY_CAMERA_MAV_GUIDE = "camera_mav_guide";
    private static final String MAV_TYPE = "CAMERA_MAV";
    private static final String CAMERA_TYPE = "CAMERA";
    
    private Activity mActivity;
    private AppGuideDialog mAppGuideDialog;
    private SharedPreferences mSharedPrefs;
    
    public CameraAppGuide(Activity context) {
        mActivity = (Activity) context;
        Log.d(TAG, "[CameraAppGuide] construct");
    }
    
    /**
     * Called when the APP want to show camera guide
     * 
     * @param type
     *            : The APP type, such as "PHONE/CONTACTS/MMS/CAMERA"
     */
    public void showCameraGuide(String type, final OnGuideFinishListener onFinishListener) {
        Log.d(TAG, "showCameraGuide() begin, type = " + type);
        if (mActivity.isDestroyed()) {
            Log.d(TAG, "Activity already destroyed, return");
            return;
        }
        
        mSharedPrefs = mActivity.getSharedPreferences(SHARED_PREFERENCE_NAME,
                Context.MODE_WORLD_WRITEABLE);
        if (isGmoROM()
                || (CAMERA_TYPE.equals(type) && mSharedPrefs.getBoolean(KEY_CAMERA_GUIDE, false))
                || (MAV_TYPE.equals(type) && mSharedPrefs.getBoolean(KEY_CAMERA_MAV_GUIDE, false))) {
            Log.d(TAG, "already hava showen camera guide, return");
            onFinishListener.onGuideFinish();
            return;
        }
        
        mAppGuideDialog = new AppGuideDialog(mActivity, type);
        mAppGuideDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mAppGuideDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mAppGuideDialog.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                onFinishListener.onGuideFinish();
            }
        });
        mAppGuideDialog.show();
        Log.d(TAG, "showCameraGuide() end, type = " + type);
    }
    
    /**
     * add for tablet rotation not in time Called when the APP orientation
     * changed
     */
    public void configurationChanged() {
        Log.d(TAG, "[configurationChanged]");
        if (mAppGuideDialog != null
                && (mAppGuideDialog.mCurrentStep == mAppGuideDialog.SCROLL_IN_CAMERA || mAppGuideDialog.mCurrentStep == mAppGuideDialog.MAV_IN_CAMERA)
                && !mAppGuideDialog.mIsPrepareVideo) {
            mAppGuideDialog.prepareVideo(mAppGuideDialog.mCurrentStep);
        }
    }
    
    public void dismiss() {
        Log.d(TAG, "dismiss " + mAppGuideDialog);
        if (mAppGuideDialog != null) {
            mAppGuideDialog.dismiss();
            mAppGuideDialog = null;
        }
    }
    
    private boolean isGmoROM() {
        boolean enable = SystemProperties.getInt("ro.mtk_gmo_rom_optimize", 0) == 1;
        Log.d(TAG, "isGmoROM() return " + enable);
        return enable;
    }
    
    protected class AppGuideDialog extends Dialog implements OnCompletionListener,
            OnPreparedListener, SurfaceHolder.Callback {
        
        public static final int SCROLL_IN_CAMERA = 0;
        public static final int ZOOM_IN_CAMERA = 1;
        public static final int MAV_IN_CAMERA = 2;
        
        boolean mIsPrepareVideo = false;
        boolean mIsFinished = false;
        int mCurrentStep;
        
        // Key and value for enable ClearMotion
        private static final int CLEAR_MOTION_KEY = 1700;
        private static final int CLEAR_MOTION_DISABLE = 1;
        private final String[] mVideoArray = new String[] { "scroll_left_bar.mp4",
                "zoom_in_and_out.mp4", "camera_mav.mp4" };
        
        private boolean mSetScreenSize = false;
        private boolean mIsSmbPlugged;
        private int mOrientation = 0;
        
        private Activity mActivity;
        private Button mRightBtn;
        private MediaPlayer mMediaPlayer;
        private String mGuideType;
        private SurfaceView mPreview;
        private SurfaceHolder mHolder;
        private TextView mTitle;
        private View mView;
        
        public AppGuideDialog(Activity activity, String type) {
            super(activity, R.style.dialog_fullscreen);
            mActivity = activity;
            mGuideType = type;
        }
        
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            DisplayManager displayManager = (DisplayManager) mActivity
                    .getSystemService(Context.DISPLAY_SERVICE);
            mIsSmbPlugged = displayManager.isSmartBookPluggedIn();
            mOrientation = mActivity.getRequestedOrientation();
            // when invoke play video, need full screen
            if ((mActivity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != WindowManager.LayoutParams.FLAG_FULLSCREEN) {
                Log.d(TAG, "set to full screen");
                mActivity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            LayoutInflater inflater = LayoutInflater.from(mActivity);
            mView = inflater.inflate(R.layout.video_view, null);
            mView.setBackgroundColor(Color.BLACK);
            mRightBtn = (Button) mView.findViewById(R.id.right_btn);
            mRightBtn.setText(android.R.string.ok);
            mRightBtn.setVisibility(View.GONE);
            mTitle = (TextView) mView.findViewById(R.id.guide_title);
            
            if (MAV_TYPE.equals(mGuideType)) {
                mCurrentStep = MAV_IN_CAMERA;
            } else if (CAMERA_TYPE.equals(mGuideType)) {
                mCurrentStep = SCROLL_IN_CAMERA;
            } else {
                Log.d(TAG, "it's not MAV or common Camera MODE guide");
            }
            
            Log.i(TAG, "[onCreate]mCurrentStep = " + mCurrentStep +",mIsSmbPlugged = " + mIsSmbPlugged);
            mPreview = (SurfaceView) mView.findViewById(R.id.surface_view);
            mHolder = mPreview.getHolder();
            mHolder.addCallback(this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            mPreview.setBackgroundColor(Color.BLACK);
            setContentView(mView);
            // add for remove dialog animation
            getWindow().getAttributes().windowAnimations = 0;
        }
        
        @Override
        public void onCompletion(MediaPlayer arg0) {
            Log.d(TAG, "[onCompletion] mCurrentStep = " + mCurrentStep);
            
            mRightBtn.setVisibility(View.VISIBLE);
            if (mCurrentStep == SCROLL_IN_CAMERA) {
                mTitle.setText(R.string.scroll_left_bar_title);
                mRightBtn.setOnClickListener(mNextListener);
            } else if (mCurrentStep == ZOOM_IN_CAMERA) {
                mTitle.setText(R.string.zoome_title);
                mRightBtn.setOnClickListener(mOkListener);
            } else if (mCurrentStep == MAV_IN_CAMERA) {
                // camera mav guild, we should change title and textview top
                // position
                int cameraMavTitleTop = (int) mActivity.getResources().getDimension(
                        R.dimen.camera_mav_title_margin_top);
                Log.d(TAG, "[onCompletion] cameraMavTitleTop = " + cameraMavTitleTop);
                mTitle.setY(cameraMavTitleTop);
                mTitle.setText(R.string.camera_mav_title);
                mRightBtn.setOnClickListener(mOkListener);
            }
        }
        
        @Override
        public void onPrepared(MediaPlayer mediaplayer) {
            Log.d(TAG, "[onPrepared]");
            mPreview.setBackgroundColor(android.R.color.transparent);
            mMediaPlayer.start();
        }
        
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            int orientation = mActivity.getRequestedOrientation();
            Log.i(TAG, "[surfaceCreated] mOrientation is " + mOrientation + " orientation = "
                    + orientation + ",mIsSmbPlugged = " + mIsSmbPlugged + ",mGuideType = "
                    + mGuideType);

            // add for surface create again
            if (MAV_TYPE.equals(mGuideType)) {
                mCurrentStep = MAV_IN_CAMERA;
                if (!mIsSmbPlugged && orientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        && orientation != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                    Log.d(TAG, "[surfaceCreated] set acitivty to LANDSCAPE");
                    mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
            } else if (CAMERA_TYPE.equals(mGuideType)) {
                mCurrentStep = SCROLL_IN_CAMERA;
                if (!mIsSmbPlugged && orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        && orientation != ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                    Log.d(TAG, "[surfaceCreated] set acitivty to PORTRAIT");
                    mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            } else {
                Log.d(TAG, "it's not MAV or common Camera MODE guide");
            }
            
            this.mHolder = holder;
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setDisplay(this.mHolder);
            mTitle.setText("");
            mRightBtn.setVisibility(View.GONE);
            
            if (MAV_TYPE.equals(mGuideType)) {
                if (mIsSmbPlugged || orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        || orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                    Log.d(TAG, "start to prepare vedio for MAV camera on Phone");
                    prepareVideo(mCurrentStep);
                }
            } else if (CAMERA_TYPE.equals(mGuideType)) {
                if (mIsSmbPlugged || orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        || orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                    Log.d(TAG, "start to prepare vedio for common camera on Phone");
                    prepareVideo(mCurrentStep);
                }
            }
            
        }
        
        @Override
        public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
            Log.d(TAG, "surfaceChanged called");
        }
        
        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceholder) {
            Log.d(TAG, "surfaceDestroyed called, mOrientation:" + mOrientation);
            if (surfaceholder != mHolder) {
                Log.d(TAG, "surfaceholder != mHolder, return");
                return;
            }
            if (mIsFinished) {
                if (MAV_TYPE.equals(mGuideType)) {
                    if (!mIsSmbPlugged && mOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            && mOrientation != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                        mActivity.setRequestedOrientation(mOrientation);
                    }
                } else if (CAMERA_TYPE.equals(mGuideType)) {
                    if (!mIsSmbPlugged && mOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            && mOrientation != ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                        mActivity.setRequestedOrientation(mOrientation);
                    }
                }
            }
            
            if (mMediaPlayer != null) {
                mMediaPlayer.pause();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            mIsPrepareVideo = false;
        }
        
        public void onBackPressed() {
            mIsFinished = true;
            super.onBackPressed();
        }
        
        /**
         * next button listener, show next video.
         */
        private View.OnClickListener mNextListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "play next video");
                mTitle.setText("");
                mRightBtn.setVisibility(View.GONE);
                mCurrentStep++;
                if (mCurrentStep <= ZOOM_IN_CAMERA) {
                    prepareVideo(mCurrentStep);
                }
            }
        };
        /**
         * OK button listener, finish APP guide.
         */
        private View.OnClickListener mOkListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "click ok, finish app guide");
                if (CAMERA_TYPE.equals(mGuideType)) {
                    mSharedPrefs.edit().putBoolean(KEY_CAMERA_GUIDE, true).commit();
                } else if (MAV_TYPE.equals(mGuideType)) {
                    mSharedPrefs.edit().putBoolean(KEY_CAMERA_MAV_GUIDE, true).commit();
                }
                
                releaseMediaPlayer();
            }
        };
        /**
         * error listener, stop play video.
         */
        private OnErrorListener mErrorListener = new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.d(TAG, "play error: " + what);
                releaseMediaPlayer();
                return false;
            }
        };
        
        private void prepareVideo(int step) {
            Log.d(TAG, "prepareVideo step = " + step);
            // add for tablet rotation not in time
            mIsPrepareVideo = true;
            try {
                if (mMediaPlayer != null) {
                    mMediaPlayer.reset();
                    AssetFileDescriptor afd = mActivity.getAssets().openFd(mVideoArray[step]);
                    Log.d(TAG, "video path = " + afd.getFileDescriptor());
                    mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                            afd.getLength());
                    afd.close();
                    // Disable ClearMotion
                    mMediaPlayer.setParameter(CLEAR_MOTION_KEY, CLEAR_MOTION_DISABLE);
                    
                    mMediaPlayer.prepare();
                    resizeSurfaceView();
                    Log.d(TAG, "mMediaPlayer prepare()");
                }
            } catch (IOException e) {
                Log.e(TAG, "unable to open file; error: " + e.getMessage(), e);
                releaseMediaPlayer();
            } catch (IllegalStateException e) {
                Log.e(TAG, "media player is in illegal state; error: " + e.getMessage(), e);
                releaseMediaPlayer();
            }
        }
        
        private void releaseMediaPlayer() {
            Log.d(TAG, "releaseMediaPlayer");
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            
            onBackPressed();
        }
        
        private void resizeSurfaceView() {
            Log.d(TAG, "resizeSurfaceView()");
            if (mSetScreenSize) {
                return;
            } else {
                mSetScreenSize = true;
            }
            
            int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            Log.d(TAG, "resizeSurfaceView  Display.getRotation() is " + rotation);
            
            int videoW = mMediaPlayer.getVideoWidth();
            int videoH = mMediaPlayer.getVideoHeight();
            int screenW = mActivity.getWindowManager().getDefaultDisplay().getWidth();
            int screenH = mActivity.getWindowManager().getDefaultDisplay().getHeight();
            Log.d(TAG,
                    "mActivity.getWindowManager().getDefaultDisplay().getHeight() ----  screenW = "
                            + screenW + " ,screenH = " + screenH);
            Log.i(TAG, "videoW = " + videoW + " ,videoH = " + videoH);
            Log.i(TAG, "screenW = " + screenW + " ,screenH = " + screenH);
            
            android.view.ViewGroup.LayoutParams lp = mPreview.getLayoutParams();
            
            if (!mIsSmbPlugged) {
                if (MAV_TYPE.equals(mGuideType)) {
                    if (screenW < screenH) {
                        int temp = screenH;
                        screenH = screenW;
                        screenW = temp;
                        Log.d(TAG, "exchange screenW and screenH");
                    }
                } else if (CAMERA_TYPE.equals(mGuideType)) {
                    if (screenW > screenH) {
                        int temp = screenH;
                        screenH = screenW;
                        screenW = temp;
                        Log.d(TAG, "exchange screenW and screenH");
                    }
                }
            }

            float videoScale = (float) videoH / (float) videoW;
            float screenScale = (float) screenH / (float) screenW;
            if (screenScale > videoScale) {
                lp.width = screenW;
                lp.height = (int) (videoScale * (float) screenW) + 1;
                Log.d(TAG, "screenScale > videoScale");
            } else {
                lp.height = screenH;
                lp.width = (int) ((float) screenH / videoScale) + 1;
                Log.d(TAG, "screenScale < videoScale");
            }
            mPreview.setLayoutParams(lp);
            Log.d(TAG, "lp.height = " + lp.height + " ,lp.width = " + lp.width);
        }
    }
}
