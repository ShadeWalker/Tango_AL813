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
 * MediaTek Inc. (C) 2015. All rights reserved.
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

package com.mediatek.camera.v2.addition.facedetection;

import java.util.ArrayList;
import java.util.Map;

import android.app.Activity;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera.Face;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.mediatek.camera.v2.setting.ISettingServant;
import com.mediatek.camera.v2.setting.SettingCtrl;
import com.mediatek.camera.v2.setting.ISettingServant.ISettingChangedListener;
import com.mediatek.camera.v2.util.SettingKeys;

import com.android.camera.R;

public class FdViewManager implements ISettingChangedListener {
    private static final String   TAG = FdViewManager.class.getSimpleName();
    private final FdAddition      mFdAddition;
    private FdAdditionStatuslImpl     mIFaceDetecionModeCallback;
    
    private final ISettingServant mISettingServant;
    private ArrayList<String>     mCaredSettingChangedKeys = new ArrayList<String>();
    
    private Activity              mActivity;
    private FdView                mFdView;
    private boolean               mIsFbEnabled = false;
    private boolean               mMirror = false;
    
    public FdViewManager(FdAddition addition, ISettingServant settingServant) {
        mFdAddition = addition;
        mISettingServant = settingServant;
    }

    public void open(Activity activity, ViewGroup parentViewGroup) {
        Log.i(TAG, "[open]+");
        mActivity = activity;
        mIFaceDetecionModeCallback = new FdAdditionStatuslImpl(new Handler(mActivity.getMainLooper()));
        activity.getLayoutInflater().inflate(
                R.layout.facedetection_view, parentViewGroup, true);
        mFdView = (FdView)parentViewGroup.findViewById(R.id.face_detection_view);
        mFdView.setVisibility(View.VISIBLE);
        mFdAddition.setFdAdditionStatusCallback(mIFaceDetecionModeCallback);
        
        addCaredSettingChangedKeys(SettingKeys.KEY_FACE_BEAUTY);
        addCaredSettingChangedKeys(SettingKeys.KEY_CAMERA_ID);
        mISettingServant.registerSettingChangedListener(this, mCaredSettingChangedKeys, 
                ISettingChangedListener.MIDDLE_PRIORITY);
        Log.i(TAG, "[open]- ");
    }
    
    public void close() {
        mISettingServant.unRegisterSettingChangedListener(this);
    }
    
    @Override
    public void onSettingChanged(Map<String, String> result) {
        String fbChanged = result.get(SettingKeys.KEY_FACE_BEAUTY);
        String cameraId = result.get(SettingKeys.KEY_CAMERA_ID);
        if (fbChanged != null || cameraId != null) {
            updateFbAndMirrorStatus();
            if (mActivity != null) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mIFaceDetecionModeCallback != null) {
                            mIFaceDetecionModeCallback.removeFdRunnables();
                        }
                        if (mFdView != null) {
                            mFdView.clear();
                            mFdView.setMirror(mMirror);
                            mFdView.setFbEnabled(mIsFbEnabled);
                        }
                    }
                });
            }
        }
    }
    
    public void onOrientationChanged(int orientation) {
        if (mFdView != null) {
            mFdView.onOrientationChanged(orientation);
        }
    }
    
    public void onPreviewAreaChanged(RectF previewRect) {
        if (mFdView != null) {
            mFdView.onPreviewAreaChanged(previewRect);
        }
    }
    
    private class FdAdditionStatuslImpl implements IFdAdditionStatus {
        private final Handler mHandler;
        
        public FdAdditionStatuslImpl(Handler handler) {
            mHandler = handler;
        }
        
        public void removeFdRunnables() {
            mHandler.removeCallbacksAndMessages(null);
        }
        
        @Override
        public void onFdStarted() {
            updateFbAndMirrorStatus();
            if (mActivity != null) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mFdView != null) {
                            mFdView.clear();
                            mFdView.setMirror(mMirror);
                            mFdView.setFbEnabled(mIsFbEnabled);
                        }
                    }
                });
            }
        }

        @Override
        public void onFdStoped() {
            if (mFdView != null) {
                mFdView.clear();
            }
        }

        @Override
        public void onFaceDetected(final int[] ids, final int[] landmarks, final Rect[] rectangles,
                final byte[] scores, final Point[][] pointsInfo, final Rect cropRegion) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mFdView != null) {
                        mFdView.setFaces(ids, landmarks, rectangles, scores, pointsInfo, cropRegion);
                    }
                }
            });
        }
    }
    
    private void addCaredSettingChangedKeys(String key) {
        if (key != null && !mCaredSettingChangedKeys.contains(key)) {
            mCaredSettingChangedKeys.add(key);
        }
    }
    
    private void updateFbAndMirrorStatus() {
        mIsFbEnabled = "on".equalsIgnoreCase(
                mISettingServant.getSettingValue(SettingKeys.KEY_FACE_BEAUTY));
        if (mISettingServant.getCameraId() == SettingCtrl.BACK_CAMERA) {
            mMirror = false;
        } else {
            mMirror = true;
        }
        Log.i(TAG, "updateFbAndMirrorStatus mIsFbEnable:" + mIsFbEnabled + 
                " mMirror:" + mMirror);
    }
}