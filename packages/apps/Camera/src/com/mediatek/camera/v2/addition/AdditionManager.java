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

package com.mediatek.camera.v2.addition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import junit.framework.Assert;

import com.mediatek.camera.v2.addition.asd.AsdAddition;
import com.mediatek.camera.v2.addition.asd.AsdDeviceImpl;
import com.mediatek.camera.v2.addition.asd.IAsdAdditionCallback;
import com.mediatek.camera.v2.addition.asd.IAsdDevice;
import com.mediatek.camera.v2.addition.facedetection.FdAddition;
import com.mediatek.camera.v2.addition.facedetection.FdDeviceImpl;
import com.mediatek.camera.v2.addition.facedetection.FdViewManager;
import com.mediatek.camera.v2.addition.facedetection.IFdDevice;
import com.mediatek.camera.v2.addition.gesturedetection.GestureDetection;
import com.mediatek.camera.v2.addition.gesturedetection.GestureDetectionDeviceImpl;
import com.mediatek.camera.v2.addition.gesturedetection.GestureDetectionView;
import com.mediatek.camera.v2.addition.gesturedetection.IGestureDetectionDevice;
import com.mediatek.camera.v2.addition.smiledetection.ISmileDetectionDevice;
import com.mediatek.camera.v2.addition.smiledetection.SmileDetection;
import com.mediatek.camera.v2.addition.smiledetection.SmileDetectionDeviceImpl;
import com.mediatek.camera.v2.addition.smiledetection.SmileDetectionView;
import com.mediatek.camera.v2.module.ModuleListener;
import com.mediatek.camera.v2.module.ModuleListener.CaptureType;
import com.mediatek.camera.v2.module.ModuleListener.RequestType;
import com.mediatek.camera.v2.platform.app.AppController;
import com.mediatek.camera.v2.platform.app.AppUi;
import com.mediatek.camera.v2.setting.ISettingServant;
import com.mediatek.camera.v2.setting.SettingCtrl;
import com.mediatek.camera.v2.setting.ISettingServant.ISettingChangedListener;
import com.mediatek.camera.v2.util.SettingKeys;

import android.app.Activity;
import android.graphics.RectF;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Builder;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Log;
import android.view.ViewGroup;

/**
 * A manager used to manage additions.
 * It will broadcast events from modules to additions.
 */
public class AdditionManager implements IAdditionManager, ISettingChangedListener, 
            IAsdAdditionCallback, IAdditionManager.IAdditionListener {
    private static final String         TAG = AdditionManager.class.getSimpleName();
    private final AppController         mAppController;
    private final AppUi                 mAppUi;
    private final ISettingServant       mSettingServant;
    private final ModuleListener        mModuleListener;
    private final CopyOnWriteArrayList<IAdditionCaptureObserver> 
                                        mCaptureObservers     = new CopyOnWriteArrayList<IAdditionCaptureObserver>();
    
    private ArrayList<String>           mCaredSettingChangedKeys = new ArrayList<String>();
    
    // addition feature
    private SmileDetection              mSmileDetection;
    private SmileDetectionView          mSmileDetectionView;
    private ISmileDetectionDevice       mSmileDetectionDeviceImpl;
    
    private GestureDetection            mGestureDetection;
    private GestureDetectionView        mGestureDetectionView;
    private IGestureDetectionDevice     mGestureDetectionDeviceImpl;
    
    private AsdAddition                 mAsdAddition;
    private IAsdDevice                  mAsdDeviceImpl;
    
    private FdAddition                  mFdAddition;
    private FdViewManager               mFdViewManager;
    private IFdDevice                   mFdDeviceImpl;
    
    private volatile RequestType        mCurrentRepeatingRequestType = RequestType.PREVIEW;
    private String                      mInitializedCameraId;
    
    public AdditionManager(AppController app, ModuleListener moduleListener, String cameraId) {
        Assert.assertNotNull(app);
        Assert.assertNotNull(moduleListener);
        mAppController         = app;
        mInitializedCameraId   = cameraId;
        mAppUi                 = mAppController.getCameraAppUi();
        mModuleListener        = moduleListener;
        mSettingServant        = mAppController.getServices().getSettingController().getSettingServant(cameraId);

        // switch camera will not be notified,so we need initiative to listener
        // the switch camera occur.
        addCaredSettingChangedKeys(SettingKeys.KEY_CAMERA_ID);

        // initialize special feature
        mSmileDetectionDeviceImpl   = new SmileDetectionDeviceImpl(this);
        mSmileDetection             = new SmileDetection(mSmileDetectionDeviceImpl);
        addCaredSettingChangedKeys(SettingKeys.KEY_SMILE_SHOT);
        
        mGestureDetectionDeviceImpl = new GestureDetectionDeviceImpl(this);
        mGestureDetection           = new GestureDetection(mGestureDetectionDeviceImpl);
        addCaredSettingChangedKeys(SettingKeys.KEY_GESTURE_SHOT);
        
        mAsdDeviceImpl         = new AsdDeviceImpl(this, mSettingServant, this);
        mAsdAddition           = new AsdAddition(mAsdDeviceImpl);
        addCaredSettingChangedKeys(SettingKeys.KEY_ASD);
        
        mFdDeviceImpl          = new FdDeviceImpl(this);
        mFdAddition            = new FdAddition(mAppController.getActivity(), mFdDeviceImpl);
        addCaredSettingChangedKeys(SettingKeys.KEY_CAMERA_FACE_DETECT);
    }

    @Override
    public void onSettingChanged(Map<String, String> result) {
        
        boolean smileDetectionEnable = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_SMILE_SHOT));
        boolean gestureDetectionEnable = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_GESTURE_SHOT));
        boolean asdEnable = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_ASD));
        boolean fdEnable = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_CAMERA_FACE_DETECT));
        Log.i(TAG, "onSettingChanged smileDetectionEnable = " + smileDetectionEnable + 
                    " gestureDetectionEnable = " + gestureDetectionEnable +
                    " asdEnable = " + asdEnable + 
                    " fdEnable = " + fdEnable);

        if (result.get(SettingKeys.KEY_CAMERA_ID) != null) {
            updateAsdState();
            updateGesureDetectionState();
            updateSmileDetectionState();
            updateFdState();
        }

        String asdChanged = result.get(SettingKeys.KEY_ASD);
        if (asdChanged != null) {
            updateAsdState();
        }
        String gsChanged = result.get(SettingKeys.KEY_GESTURE_SHOT);
        if (gsChanged != null) {
            updateGesureDetectionState();
        }
        String ssChanged = result.get(SettingKeys.KEY_SMILE_SHOT);
        if (ssChanged != null) {
            updateSmileDetectionState();
        } 
        String fdChanged = result.get(SettingKeys.KEY_CAMERA_FACE_DETECT);
        if (fdChanged != null) {
            updateFdState();
        }
    }
    
    @Override
    public void open(Activity activity, ViewGroup parentView, boolean isCaptureIntent) {
        mSettingServant.registerSettingChangedListener(this, mCaredSettingChangedKeys, 
                ISettingChangedListener.MIDDLE_PRIORITY);
        
        mSmileDetectionView = new SmileDetectionView(activity, mSmileDetection, mAppUi);
        mGestureDetectionView = new GestureDetectionView(activity, parentView, 
                mGestureDetection, mAppController, mSettingServant);
        mFdViewManager = new FdViewManager(mFdAddition, mSettingServant);
        mFdViewManager.open(activity, parentView);
    }

    @Override
    public void close() {
        if (mFdViewManager != null) {
            mFdViewManager.close();
        }
        mSettingServant.unRegisterSettingChangedListener(this);
    }

    @Override
    public void resume() {
        Log.i(TAG, "resume");
        
        boolean smileDetectionEnable = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_SMILE_SHOT));
        boolean gestureDetectionEnable = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_GESTURE_SHOT));
        boolean asdEnable = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_ASD));
        boolean fdEnable = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_CAMERA_FACE_DETECT));
        Log.i(TAG, "smileDetectionEnable = " + smileDetectionEnable + 
                    " gestureDetectionEnable = " + gestureDetectionEnable +
                    " asdEnable = " + asdEnable + 
                    " fdEnable = " + fdEnable);
        
        boolean asdsettingOpened = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_ASD));
        if (asdsettingOpened) {
            registerCaptureObserver(mAsdDeviceImpl.getCaptureObserver());
            mAsdAddition.startAsd(false);
        }
        boolean fdsettingOpened = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_CAMERA_FACE_DETECT));
        if (fdsettingOpened) {
            registerCaptureObserver(mFdDeviceImpl.getCaptureObserver());
            mFdAddition.startFaceDetection();
        }
        boolean gssettingOpened = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_GESTURE_SHOT));
        if (gssettingOpened) {
            registerCaptureObserver(mGestureDetectionDeviceImpl.getCaptureObserver());
            mGestureDetection.startGestureDetection();
        }
        boolean sssettingOpened = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_SMILE_SHOT));
        if (sssettingOpened) {
            registerCaptureObserver(mSmileDetectionDeviceImpl.getCaptureObserver());
            mSmileDetection.startSmileDetection();
        }
    }

    @Override
    public void pause() {
        Log.i(TAG, "pause");
    }

    @Override
    public void onSingleTapUp(float x, float y) {
        
    }

    @Override
    public void onLongPressed(float x, float y) {
        
    }

    @Override
    public void onPreviewAreaChanged(RectF previewArea) {
        Log.i(TAG, "onPreviewAreaChanged mGestureDetectionView:" + mGestureDetectionView + 
                " mFdViewManager:" + mFdViewManager);
        if (mGestureDetectionView != null) {
            mGestureDetectionView.onPreviewAreaChanged(previewArea);
        }
        if (mFdViewManager != null) {
            mFdViewManager.onPreviewAreaChanged(previewArea);
        }
    }

    @Override
    public void onOrientationChanged(int orientation) {
        if (mFdViewManager != null) {
            mFdViewManager.onOrientationChanged(orientation);
        }
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        
    }

    @Override
    public void configuringSessionRequests(
            Map<RequestType, Builder> requestBuilders, CaptureType captureType) {
        Set<RequestType> keySet = requestBuilders.keySet();
        Iterator<RequestType> iterator = keySet.iterator();
        while (iterator.hasNext()) {
            RequestType requestType = iterator.next();
            CaptureRequest.Builder requestBuilder = requestBuilders.get(requestType);
            updateRepeatingRequest(requestType);
            for (IAdditionCaptureObserver observer : mCaptureObservers) {
                observer.configuringRequests(requestBuilder, requestType);
            }
        }
    }

    @Override
    public void onCaptureStarted(CaptureRequest request, long timestamp,
            long frameNumber) {
        for (IAdditionCaptureObserver observer : mCaptureObservers) {
            observer.onCaptureStarted(request, timestamp, frameNumber);
        }
    }

    @Override
    public void onCaptureCompleted(CaptureRequest request,
            TotalCaptureResult result) {
        Log.i(TAG, "onCaptureCompleted camera id:" + mSettingServant.getCameraId());
        for (IAdditionCaptureObserver observer : mCaptureObservers) {
            observer.onCaptureCompleted(request, result);
        }
    }
    
    //FIXME should not put here, remove from addition manager
    @Override
    public void onAsdDetectedScene(String scene) {
        mAppUi.updateAsdDetectedScene(scene);
    }

    @Override
    public void onAsdClosed() {
        mAppUi.updateAsdDetectedScene(null);
    }
    
    @Override
    public RequestType getRepeatingRequestType() {
        return mCurrentRepeatingRequestType;
    }
    
    @Override
    public void requestChangeCaptureRequets(boolean sync, RequestType requestType,
            CaptureType captureType) {
        if (mInitializedCameraId == null) {
            mModuleListener.requestChangeCaptureRequets(sync,requestType, captureType);
        } else if (SettingCtrl.BACK_CAMERA.equals(mInitializedCameraId)) {
            mModuleListener.requestChangeCaptureRequets(true, sync, requestType, captureType);
        } else if (SettingCtrl.FRONT_CAMERA.equals(mInitializedCameraId)) {
            mModuleListener.requestChangeCaptureRequets(false, sync,requestType, captureType);
        }
    }
    
    private void addCaredSettingChangedKeys(String key) {
        if (key != null && !mCaredSettingChangedKeys.contains(key)) {
            mCaredSettingChangedKeys.add(key);
        }
    }

    private void registerCaptureObserver(IAdditionCaptureObserver observer) {
        if (observer != null && !mCaptureObservers.contains(observer)) {
            mCaptureObservers.add(observer);
        }
    }

    private void unregisterCaptureObserver(IAdditionCaptureObserver observer) {
        if (observer != null && mCaptureObservers.contains(observer)) {
            mCaptureObservers.remove(observer);
        }
    }

    private void updateSmileDetectionState() {
        boolean settingOpened = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_SMILE_SHOT));
        if (settingOpened) {
            registerCaptureObserver(mSmileDetectionDeviceImpl.getCaptureObserver());
            mSmileDetection.startSmileDetection();
        } else {
            unregisterCaptureObserver(mSmileDetectionDeviceImpl.getCaptureObserver());
            mSmileDetection.stopSmileDetection();
        }
    }
    
    private void updateGesureDetectionState() {
        boolean settingOpened = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_GESTURE_SHOT));
        if (settingOpened) {
            registerCaptureObserver(mGestureDetectionDeviceImpl.getCaptureObserver());
            mGestureDetection.startGestureDetection();
        } else {
            unregisterCaptureObserver(mGestureDetectionDeviceImpl.getCaptureObserver());
            mGestureDetection.stopGestureDetection();
        }
    }

    private void updateAsdState() {
        boolean settingOpened = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_ASD));
        if (settingOpened) {
            registerCaptureObserver(mAsdDeviceImpl.getCaptureObserver());
            mAsdAddition.startAsd(false);
        } else {
            unregisterCaptureObserver(mAsdDeviceImpl.getCaptureObserver());
            mAsdAddition.stopAsd();
        }
    }

    private void updateFdState() {
        boolean fdOpened = "on".equals(mSettingServant.getSettingValue(SettingKeys.KEY_CAMERA_FACE_DETECT));
        Log.i(TAG, "updateFdState camera id:" + mSettingServant.getCameraId() + " status:" + fdOpened);
        if (fdOpened) {
            registerCaptureObserver(mFdDeviceImpl.getCaptureObserver());
            mFdAddition.startFaceDetection();
        } else {
            unregisterCaptureObserver(mFdDeviceImpl.getCaptureObserver());
            mFdAddition.stopFaceDetection();
        }
    }
    
    private void updateRepeatingRequest(RequestType requestType) {
        if (requestType == RequestType.PREVIEW || requestType == RequestType.RECORDING) {
            mCurrentRepeatingRequestType = requestType;
        }
    }
}
