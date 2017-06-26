/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2014. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */
package com.mediatek.camera.mode.motiontrack;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.R;

import com.mediatek.camera.platform.ICameraAppUi;
import com.mediatek.camera.platform.IModuleCtrl;
import com.mediatek.camera.ui.CameraView;
import com.mediatek.camera.ui.ProgressIndicator;
import com.mediatek.camera.ui.UIRotateLayout;
import com.mediatek.camera.util.Log;

public class MotionTrackView extends CameraView {
    private static final String TAG = "MotionTrackView";
    
    public static final int INFO_UPDATE_PROGRESS = 0;
    public static final int INFO_UPDATE_MOVING = 1;
    
    private static final int PROGRESS_ZERO = 0;
    private static final int BLOCK_NUM = 9;
    
    private int mDisplayOrientaion;
    private int mBlockSizes[] = { 11, 11, 11, 11, 11, 11, 11, 11, 11 };
    
    private boolean mNeedInitialize = true;
    
    private View mView;
    private View mRootView;
    private View mCenterViewLayout;
    private ImageView mCenterWindow;
    private View mNaviWindow;
    private ProgressIndicator mProgressIndicator;
    private UIRotateLayout mScreenProgressLayout;
    
    private ICameraAppUi mICameraAppUi;
    private IModuleCtrl mIMoudleCtrl;
    
    public MotionTrackView(Activity activity) {
        super(activity);
        
        Log.i(TAG, "[MotionTrackView]constructor...");
    }
    
    @Override
    public void init(Activity activity, ICameraAppUi cameraAppUi, IModuleCtrl moduleCtrl) {
        super.init(activity, cameraAppUi, moduleCtrl);
        Log.i(TAG, "[init]...");
        mIMoudleCtrl = moduleCtrl;
        setOrientation(moduleCtrl.getOrientationCompensation());
        mView = inflate(R.layout.motion_track_view);
        mRootView = mView.findViewById(R.id.motion_track_frame_layout);
        mICameraAppUi = cameraAppUi;
    }
    
    @Override
    public void uninit() {
        Log.i(TAG, "[uninit]...");
        super.uninit();
        mNeedInitialize = true;
    }
    
    @Override
    public void show() {
        super.show();
        Log.i(TAG, "[show]mNeedInitialize = " + mNeedInitialize);
        mDisplayOrientaion = mIMoudleCtrl.getDisplayOrientation();
        if (mNeedInitialize) {
            initializeViewManager();
            mNeedInitialize = false;
        }
        showCaptureView();
    }
    
    @Override
    public void hide() {
        Log.i(TAG, "[hide]...");
        resetController();
        hideCaptureView();
    }
    
    @Override
    public void reset() {
        Log.i(TAG, "[reset]...");
        mDisplayOrientaion = mIMoudleCtrl.getDisplayOrientation();
        resetController();
    }
    
    @Override
    public boolean update(int type, Object... args) {
        Log.i(TAG, "[update]type = " + type);
        switch (type) {
        case INFO_UPDATE_PROGRESS:
            int tag = ((Integer) args[0]).intValue();
            if (MotionTrackMode.SHOW_PROGERSS_UI == tag) {
                showProgressIndicator();
            } else if (MotionTrackMode.UPDATE_PROGERSS_STEP == tag) {
                int num = ((Integer) args[1]).intValue();
                setProgress(num);
            }
            break;
        
        case INFO_UPDATE_MOVING:
            int xPos = ((Integer) args[0]).intValue();
            int yPos = ((Integer) args[1]).intValue();
            boolean shown = false;
            updateMovingUI(xPos, yPos, shown);
            break;
        
        default:
            break;
        }
        
        return true;
    }
    
    @Override
    protected View getView() {
        return mView;
    }
    
    @Override
    public void onOrientationChanged(int orientation) {
        super.onOrientationChanged(orientation);
        if (mScreenProgressLayout != null) {
            mScreenProgressLayout.setOrientation(orientation, true);
        }
    }
    
    private void resetController() {
        Log.d(TAG, "[resetController]...");
        if (mCenterWindow != null) {
            mCenterWindow.setVisibility(View.VISIBLE);
        }
        
        if (mNaviWindow != null) {
            mNaviWindow.setVisibility(View.INVISIBLE);
        }
        hideProgressIndicaotr();
    }
    
    private void hideCaptureView() {
        Log.d(TAG, "[hideCaptureView]mCenterWindow = " + mCenterWindow);
        if (mCenterWindow != null) {
            mCenterWindow.setVisibility(View.INVISIBLE);
        }
    }
    
    private void showProgressIndicator() {
        if (mProgressIndicator != null) {
            mProgressIndicator.setProgress(PROGRESS_ZERO);
            mProgressIndicator.setVisibility(View.VISIBLE);
        }
    }
    
    private void initializeViewManager() {
        mCenterViewLayout = mRootView.findViewById(R.id.center_view_layout);
        mScreenProgressLayout = (UIRotateLayout) mRootView
                .findViewById(R.id.motion_track_on_screen_progress);
        
        mCenterWindow = (ImageView) mRootView.findViewById(R.id.mt_center_window);
        mNaviWindow = mRootView.findViewById(R.id.mt_navi_window);
        
        mProgressIndicator = new ProgressIndicator(getContext(), BLOCK_NUM, mBlockSizes);
        mProgressIndicator.setVisibility(View.INVISIBLE);
        mScreenProgressLayout.setOrientation(getOrientation(), true);
        mProgressIndicator.setOrientation(getOrientation());
    }
    
    private void showCaptureView() {
        mCenterViewLayout.setVisibility(View.VISIBLE);
        mCenterWindow.setVisibility(View.VISIBLE);
    }
    
    private void hideProgressIndicaotr() {
        if (mProgressIndicator != null) {
            mProgressIndicator.setProgress(0);
            mProgressIndicator.setVisibility(View.INVISIBLE);
        }
    }
    
    private void setProgress(int num) {
        if (mProgressIndicator != null) {
            mProgressIndicator.setProgress(num);
        }
    }
    
    private void updateMovingUI(int xPos, int yPos, boolean shown) {
        Log.d(TAG, "[updateMovingUI]x = " + xPos + ",y = " + yPos);
        if (0 == mNaviWindow.getHeight() || 0 == mNaviWindow.getWidth()) {
            Log.w(TAG, "[updateMovingUI]mNaviWindow set invisible,return!");
            mNaviWindow.setVisibility(View.INVISIBLE);
            return;
        }
        
        short x = (short) xPos;
        short y = (short) yPos;
        int cwx = mCenterWindow.getLeft() + mCenterWindow.getPaddingLeft();
        int cwy = mCenterWindow.getTop() + mCenterWindow.getPaddingTop();
        float x_ratio = (float) mCenterViewLayout.getWidth() / (float) mICameraAppUi.getPreviewFrameWidth();
        float y_ratio = (float) mCenterViewLayout.getHeight()
                / (float) mICameraAppUi.getPreviewFrameHeight();
        
        // assume that the activity's requested orientation is same as the lcm
        // orientation.
        // if not,the following caculation would be wrong!!
        if (mDisplayOrientaion == 180) {
            x = (short) -x;
            y = (short) -y;
        } else if (mDisplayOrientaion == 90) {
            float temp = x_ratio;
            x_ratio = y_ratio;
            y_ratio = -temp;
            
            int temp2 = cwx;
            cwx = cwy;
            cwy = temp2;
        }
        
        x *= x_ratio;
        y *= y_ratio;
        
        int screenPosX = -x + cwx;
        int screenPosY = -y + cwy;
        
        int w = mNaviWindow.getWidth();
        int h = mNaviWindow.getHeight();
        if (mDisplayOrientaion == 90) {
            int temp = screenPosX;
            screenPosX = screenPosY;
            screenPosY = temp;
            
            temp = w;
            w = h;
            h = temp;
        }
        mNaviWindow.layout(screenPosX, screenPosY, screenPosX + w, screenPosY + h);
        mNaviWindow.setVisibility(View.VISIBLE);
    }
}
