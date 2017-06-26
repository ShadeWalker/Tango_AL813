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

package com.mediatek.camera.mode.mav;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.android.camera.R;

import com.mediatek.camera.platform.ICameraAppUi;
import com.mediatek.camera.platform.IModuleCtrl;
import com.mediatek.camera.ui.CameraView;
import com.mediatek.camera.ui.UIRotateLayout;
import com.mediatek.camera.util.Log;

public class MavView extends CameraView {
    private static final String TAG = "MavView";
    
    private UIRotateLayout mScreenProgressLayout;
    private ViewGroup mRootView;
    private MavEffectView mMavEffectView;
    private MavProgressIndicator mMavProgressIndicator;
    
    private boolean mNeedInitialize = true;
    
    private ICameraAppUi mICameraAppUi;
    private IModuleCtrl mIModuleCtrl;
    
    private static final double FRAME_FACTOR = 0.8;
    
    public MavView(Activity activity) {
        super(activity);
        Log.i(TAG, "[MavView]constructor...");
    }
    
    @Override
    public void init(Activity activity, ICameraAppUi cameraAppUi, IModuleCtrl moduleCtrl) {
        super.init(activity, cameraAppUi, moduleCtrl);
        Log.i(TAG, "[init]...");
        mICameraAppUi = cameraAppUi;
        mIModuleCtrl = moduleCtrl;
        
        mIModuleCtrl.setOrientation(true, 270);
        setOrientation(mIModuleCtrl.getOrientationCompensation());
        
        int location[] = new int[2];
        mICameraAppUi.getPreviewFrameLayout().getLocationInWindow(location);
        int previewLeft = location[0];
        int previewTop = location[1];
        int previewWidth = mICameraAppUi.getPreviewFrameWidth();
        int previewHeight = mICameraAppUi.getPreviewFrameHeight();
        
        int frameWidth = (int) (Math.min(previewWidth, previewHeight) * FRAME_FACTOR);
        int frameHeight = frameWidth;
        
        mMavEffectView = new MavEffectView(activity, previewLeft, previewTop, previewWidth,
                previewHeight, frameWidth, frameHeight);
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
        if (mNeedInitialize) {
            initializeViewManager();
            mNeedInitialize = false;
        }
        showEffectView();
    }
    
    @Override
    protected void removeView() {
        Log.i(TAG, "removeView");
        super.removeView();
        if (mMavEffectView != null) {
            final ViewParent parent = mMavEffectView.getParent();
            if (parent != null) {
                ((ViewGroup) parent).removeView(mMavEffectView);
            }
        }
    }
    
    @Override
    protected View getView() {
        Log.i(TAG, "[getView]... ");
        View view = inflate(R.layout.mav_preview);
        mRootView = (ViewGroup)view.findViewById(R.id.mav_frame_layout);
        mRootView.addView(mMavEffectView);
        return view;
    }
    
    @Override
    protected void addView(View view) {
        mICameraAppUi.getBottomViewLayer().addView(view);
    }
    
    @Override
    public boolean update(int type, Object... obj) {
        Log.i(TAG, "[update]type = " + type);
        switch (type) {
        case MavMode.INFO_UPDATE_PROGRESS:
            if (obj[0] != null) {
                int num = Integer.parseInt(obj[0].toString());
                mMavProgressIndicator.setProgress(num);
            }
            break;
        
        case MavMode.INFO_IN_CAPTURING:
            showCaptureView();
            break;
        
        case MavMode.INFO_OUTOF_CAPTURING:
            showEffectView();
            break;
        
        default:
            break;
        }
        
        return true;
    }
    
    @Override
    public void onOrientationChanged(int orientation) {
        super.onOrientationChanged(orientation);
        if (mScreenProgressLayout != null) {
            mScreenProgressLayout.setOrientation(orientation, true);
        }
    }
    
    private void initializeViewManager() {
       // mMavEffectView = (MavEffectView) mRootView.findViewById(R.id.effectView);
        mScreenProgressLayout = (UIRotateLayout) mRootView.findViewById(R.id.on_screen_progress);
        
        mMavProgressIndicator = new MavProgressIndicator(mActivity);
        mMavProgressIndicator.setVisibility(View.GONE);
        mScreenProgressLayout.setOrientation(getOrientation(), true);
        mMavProgressIndicator.setOrientation(getOrientation());
    }
    
    private void showCaptureView() {
        if (mMavProgressIndicator != null) {
            mMavProgressIndicator.setProgress(0);
            mMavProgressIndicator.setVisibility(View.VISIBLE);
        }
    }
    
    private void showEffectView() {
        Log.d(TAG, "[showEffectView]...");
        mMavProgressIndicator.setVisibility(View.INVISIBLE);
        mMavEffectView.invalidate();
    }
}
