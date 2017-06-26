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

import android.location.Location;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.android.camera.R;

import com.mediatek.camera.ICameraContext;
import com.mediatek.camera.ICameraMode.ActionType;
import com.mediatek.camera.mode.CameraMode;
import com.mediatek.camera.platform.ICameraAppUi.CommonUiType;
import com.mediatek.camera.platform.ICameraAppUi.ShutterButtonType;
import com.mediatek.camera.platform.ICameraAppUi.SpecViewType;
import com.mediatek.camera.platform.ICameraAppUi.ViewState;
import com.mediatek.camera.platform.ICameraDeviceManager.ICameraDevice.AutoFocusMvCallback;
import com.mediatek.camera.platform.ICameraDeviceManager.ICameraDevice.MavListener;
import com.mediatek.camera.platform.ICameraView;
import com.mediatek.camera.platform.IFileSaver.FILE_TYPE;
import com.mediatek.camera.platform.IFileSaver.OnFileSavedListener;
import com.mediatek.camera.util.Log;

import junit.framework.Assert;

public class MavMode extends CameraMode {
    private static final String TAG = "MavMode";

    public static final int INFO_UPDATE_PROGRESS = 1;
    public static final int INFO_IN_CAPTURING = 2;
    public static final int INFO_OUTOF_CAPTURING = 3;

    private static final int SHOW_INFO_LENGTH_LONG = 5 * 1000;
    private static final int SHOW_INFO_FOREVER = -1;
    private static final int SHOW_INFO_LENGTH_SHORT = 3 * 1000;

    private static final int NUM_MAV_CAPTURE = 25;
    private static final int MSG_SAVE_FILE = 10000;
    private static final int MSG_SET_PROGRESS = 10001;
    private static final int MSG_ORIENTATION_CHANGED = 10002;
    private static final int MSG_HIDE_VIEW = 10003;
    private static final int MSG_SHOW_VIEW = 10004;
    private static final int MSG_FILE_SAVED = 10005;
    private static final int MSG_IN_CAPTURING = 10006;
    private static final int MSG_OUTOF_CAPTURING = 10007;
    private static final int MSG_INIT = 10008;
    private static final int MSG_UNINIT = 10009;
    private static final int MSG_SHOW_INFO = 10010;
    private static final int MSG_ON_CAMERA_PARAMETERS_READY = 10011;

    public static final int GUIDE_SHUTTER = 0;
    public static final int GUIDE_START_CAPTURE = 1;
    public static final int GUIDE_CAPTURING = 2;

    private byte[] mJpegImageData;

    private int mCurrentNum = 0;
    private int mOrientation = 0;
    private long mCaptureTime = 0;

    private boolean mIsInStopProcess = false;
    private boolean mIsMerging = false;

    private Handler mMavHandler;
    private ICameraView mICameraView;
    private MediaActionSound mCameraSound;;
    private Object mLock = new Object();
    private Runnable mOnHardwareStop;
    private Thread mLoadSoundTread;

    public MavMode(ICameraContext cameraContext) {
        super(cameraContext);
        Log.i(TAG, "[MavMode]constructor...");

        mMavHandler = new MavHandler(mActivity.getMainLooper());
        mCameraSound = new MediaActionSound();
        mLoadSoundTread = new LoadSoundTread();
        mLoadSoundTread.start();
        mICameraView = mICameraAppUi.getCameraView(SpecViewType.MODE_MAV);
    }

    @Override
    public void pause() {
        super.pause();
        Log.i(TAG, "[pasue]");
        if (mMavHandler != null) {
            mMavHandler.removeMessages(MSG_HIDE_VIEW);
            mMavHandler.sendEmptyMessage(MSG_HIDE_VIEW);
        }
    }

    @Override
    public boolean close() {
        Log.i(TAG, "[close]current state  = " + getModeState());
        if (ModeState.STATE_CAPTURING == getModeState()) {
            mICameraDevice.stopMav(false);
            mICameraDevice.setMavCallback(null);
            mICameraView.hide();
        }
        if (mCameraSound != null) {
            mCameraSound.release();
            mCameraSound = null;
        }
        safeStop();
        unlockLandscape();
        mICameraView.uninit();
        mICameraAppUi.dismissProgress();
        mICameraAppUi.dismissInfo();
        mICameraAppUi.setSwipeEnabled(true);
        mICameraAppUi.restoreViewState();
        if (mMavHandler != null) {
            mMavHandler.removeMessages(MSG_SHOW_VIEW);
            mMavHandler.removeMessages(MSG_IN_CAPTURING);
            mMavHandler.removeMessages(MSG_OUTOF_CAPTURING);
        }

        return true;
    }

    @Override
    public boolean execute(ActionType type, Object... arg) {
        if (ActionType.ACTION_ORITATION_CHANGED != type) {
            Log.i(TAG, "[execute],type = " + type);
        }
        boolean returnValue = true;
        switch (type) {
        case ACTION_ON_CAMERA_OPEN:
            super.updateDevice();
            break;

        case ACTION_ON_CAMERA_CLOSE:
            stopCapture(false);
            setModeState(ModeState.STATE_CLOSED);
            break;

        case ACTION_ON_CAMERA_PARAMETERS_READY:
            super.updateDevice();
            super.updateFocusManager();
            setModeState(ModeState.STATE_IDLE);
            mMavHandler.sendEmptyMessage(MSG_ON_CAMERA_PARAMETERS_READY);
            mMavHandler.sendEmptyMessage(MSG_SHOW_VIEW);
            mICameraDevice.setAutoFocusMoveCallback(mAutoFocusMoveCallback);
            break;

        case ACTION_PHOTO_SHUTTER_BUTTON_CLICK:
            startCapture();
            break;

        case ACTION_CANCEL_BUTTON_CLICK:
            stopCapture(false);
            break;

        case ACTION_ORITATION_CHANGED:
            Assert.assertTrue(arg.length == 1);
            lockLandscape((Integer) arg[0]);
            break;

        case ACTION_ON_COMPENSATION_CHANGED:
            Assert.assertTrue(arg.length == 1);
            mOrientation = (Integer) arg[0];
            mMavHandler.removeMessages(MSG_ORIENTATION_CHANGED);
            mMavHandler.sendEmptyMessage(MSG_ORIENTATION_CHANGED);
            break;

        case ACTION_ON_BACK_KEY_PRESS:
            return onBackPressed();

        case ACTION_ON_FULL_SCREEN_CHANGED:
            Assert.assertTrue(arg.length == 1);

            Log.i(TAG, "[execute]type = " + type + ",full:" + (Boolean) arg[0]);
            if ((Boolean) arg[0]) {// true means :from Gallery go to Camera
                lockLandscape(mIModuleCtrl.getOrientation());
                mMavHandler.removeMessages(MSG_INIT);
                mMavHandler.sendEmptyMessage(MSG_INIT);
                mMavHandler.removeMessages(MSG_SHOW_VIEW);
                mMavHandler.sendEmptyMessage(MSG_SHOW_VIEW);
            } else { // false means: Camera go to Gallery
                mMavHandler.removeMessages(MSG_UNINIT);
                mMavHandler.sendEmptyMessage(MSG_UNINIT);
            }
            break;

        case ACTION_ON_PREVIEW_DISPLAY_SIZE_CHANGED:
            Assert.assertTrue(arg.length == 2);
            mMavHandler.removeMessages(MSG_UNINIT);
            mMavHandler.sendEmptyMessage(MSG_UNINIT);
            mMavHandler.removeMessages(MSG_INIT);
            mMavHandler.sendEmptyMessage(MSG_INIT);
            mMavHandler.removeMessages(MSG_SHOW_VIEW);
            mMavHandler.sendEmptyMessage(MSG_SHOW_VIEW);
            break;

        case ACTION_SHUTTER_BUTTON_LONG_PRESS:
            mMavHandler.removeMessages(MSG_SHOW_INFO);
            mMavHandler.sendEmptyMessage(MSG_SHOW_INFO);
            break;
            
        case ACTION_ON_SINGLE_TAP_UP:
            
            Log.i(TAG, "current state : " + getModeState());
            
            if (ModeState.STATE_IDLE == getModeState()) {
                //return false means this MSG need supper execute
                returnValue = false;
            }
            break;

        default:
            returnValue = false;;
        }
        
        Log.i(TAG, "[execute]type = " + type +",returnValue = " + returnValue);
        return returnValue;
    }

    private boolean startCapture() {
        Log.i(TAG, "[startCapture] modeState: " + getModeState() + ",mIsMerging = " + mIsMerging);

        if (!isEnoughSpace() || ModeState.STATE_IDLE != getModeState() || mIsMerging) {
            Log.w(TAG, "[startCapture]return,mIsCameraClosed = " + getModeState());
            return false;
        }

        if (mCameraSound != null) {
            mCameraSound.play(MediaActionSound.START_VIDEO_RECORDING);
        }

        setModeState(ModeState.STATE_CAPTURING);
        mCurrentNum = 0;
        mIModuleCtrl.lockOrientation();
        mIFileSaver.init(FILE_TYPE.MAV, 0, null, -1);
        mICameraAppUi.switchShutterType(ShutterButtonType.SHUTTER_TYPE_CANCEL);
        mICameraAppUi.setSwipeEnabled(false);
        mICameraAppUi.showRemaining();
        mICameraAppUi.setViewState(ViewState.VIEW_STATE_CONTINUOUS_CAPTURE);
        ICameraView thumbnailView = mICameraAppUi.getCameraView(CommonUiType.THUMBNAIL);
        if (thumbnailView != null) {
            thumbnailView.hide();
        }
        
        mCaptureTime = System.currentTimeMillis();
        mIFocusManager.setAwbLock(true);
        mIModuleCtrl.applyFocusParameters(false);
        mIModuleCtrl.stopFaceDetection();
        mMavHandler.removeMessages(MSG_IN_CAPTURING);
        mMavHandler.sendEmptyMessage(MSG_IN_CAPTURING);
        mICameraDevice.setMavCallback(mMavCallback);
        mICameraDevice.startMav(NUM_MAV_CAPTURE);
        mICameraDevice.setAutoFocusMoveCallback(null);
        showGuideString(GUIDE_START_CAPTURE);
        mMavHandler.postDelayed(mFalseShutterCallback, 300);
        return true;
    }

    private void stopCapture(boolean isMerge) {
        Log.i(TAG, "[stopCapture] isMerge = " + isMerge + ",current state = " + getModeState());
        if (ModeState.STATE_CAPTURING == getModeState()) {
            mMavHandler.removeMessages(MSG_OUTOF_CAPTURING);
            mMavHandler.sendEmptyMessage(MSG_OUTOF_CAPTURING);
            stop(isMerge);
        }
    }

    private boolean onBackPressed() {
        if (ModeState.STATE_CAPTURING == getModeState()) {
            stopCapture(false);
            return true;
        } else {
            return false;
        }
    }

    private class LoadSoundTread extends Thread {
        @Override
        public void run() {
            mCameraSound.load(MediaActionSound.START_VIDEO_RECORDING);
        }
    }

    private void showGuideString(int step) {
        Log.i(TAG, " [showGuideString] step = " + step);
        int guideId = 0;
        switch (step) {
        case GUIDE_SHUTTER:
            guideId = R.string.mav_guide_shutter;
            mICameraAppUi.showInfo(mActivity.getString(guideId), SHOW_INFO_LENGTH_LONG);
            break;

        case GUIDE_START_CAPTURE:
            guideId = R.string.mav_guide_move;
            mICameraAppUi.showInfo(mActivity.getString(guideId), SHOW_INFO_FOREVER);
            break;

        case GUIDE_CAPTURING:
            guideId = R.string.capturing_mav;
            mICameraAppUi.showInfo(mActivity.getString(guideId), SHOW_INFO_LENGTH_SHORT);
            break;

        default:
            break;
        }
    }

    private Runnable mFalseShutterCallback = new Runnable() {
        @Override
        public void run() {
            // simulate an onShutter event since it is not supported in this
            // mode.
            mIFocusManager.resetTouchFocus();
            mIFocusManager.updateFocusUI();
        }
    };

    private MavListener mMavCallback = new MavListener() {
        public void onFrame(byte[] jpegData) {
            onPictureTaken(jpegData);
        }
    };

    private void onPictureTaken(byte[] jpegData) {
        if (ModeState.STATE_IDLE == getModeState()) {
            Log.i(TAG, "[onPictureTaken]CurrentState is STATE_IDLE,return.");
            return;
        }
        Log.i(TAG, "[onPictureTaken]mIsMerging: " + mIsMerging + ",CurrentNum:" + mCurrentNum);
        if (mCurrentNum == NUM_MAV_CAPTURE || mIsMerging) {
            Log.d(TAG, "[onPictureTaken]mav done");
            mIsMerging = false;
            mJpegImageData = jpegData;
            onHardwareStopped(true);
        } else if (mCurrentNum >= 0 && mCurrentNum < NUM_MAV_CAPTURE) {
            if (mCurrentNum == 0) {
                showGuideString(GUIDE_CAPTURING);
            }
            mMavHandler.removeMessages(MSG_SET_PROGRESS);
            mMavHandler.sendEmptyMessage(MSG_SET_PROGRESS);
        } else {
            Log.w(TAG, "[onPictureTaken]is called in abnormal state");
        }

        mCurrentNum++;
        if (mCurrentNum == NUM_MAV_CAPTURE) {
            stop(true);
        }
    }

    private void onHardwareStopped(boolean isMerge) {
        Log.d(TAG, "[onHardwareStopped]isMerge = " + isMerge);
        if (isMerge) {
            mICameraDevice.setMavCallback(null);
        }
        if (isMerge && mJpegImageData != null) {
            mMavHandler.removeMessages(MSG_SAVE_FILE);
            mMavHandler.sendEmptyMessage(MSG_SAVE_FILE);
        } else {
            resetCapture();
        }
        setModeState(ModeState.STATE_IDLE);

    }

    private void stop(boolean isMerge) {
        Log.i(TAG, "[stop]isMerge = " + isMerge + ",mIsMerging = " + mIsMerging
                + ",crrent state = " + getModeState());

        if (ModeState.STATE_CAPTURING != getModeState()) {
            Log.i(TAG, "[stop] current mode state is not capturing,so return");
            return;
        }

        if (mIsMerging) {
            // if current is in the progress merging,means before have stopped
            // the MAV,so can directly return.
            Log.i(TAG, "[stop] current is also in merging,so cancle this time");
            return;
        } else {
            mIsMerging = isMerge;
            if (isMerge) {
                mICameraAppUi.showProgress(mActivity.getString(R.string.saving));
                mICameraAppUi.dismissInfo();
                mMavHandler.removeMessages(MSG_HIDE_VIEW);
                mMavHandler.sendEmptyMessage(MSG_HIDE_VIEW);
            } else {
                mICameraDevice.setMavCallback(null);
            }
            stopAsync(isMerge);
        }
    }

    private void resetCapture() {
        Log.d(TAG, "[resetCapture]...current mode state = " + getModeState());
        if (ModeState.STATE_CLOSED != getModeState()) {
            mIFocusManager.setAeLock(false);
            mIFocusManager.setAwbLock(false);
            mIModuleCtrl.applyFocusParameters(false);
            mICameraDevice.setAutoFocusMoveCallback(mAutoFocusMoveCallback);
        }
        mICameraAppUi.restoreViewState();
        mICameraAppUi.switchShutterType(ShutterButtonType.SHUTTER_TYPE_PHOTO_VIDEO);
        mICameraAppUi.setSwipeEnabled(true);
        showGuideString(GUIDE_SHUTTER);
    }

    private final AutoFocusMvCallback mAutoFocusMoveCallback = new AutoFocusMvCallback() {
        @Override
        public void onAutoFocusMoving(boolean moving, android.hardware.Camera camera) {
            Log.i(TAG, "[onAutoFocusMoving]moving = " + moving);
            mIFocusManager.onAutoFocusMoving(moving);
        }
    };

    private void stopAsync(final boolean isMerge) {
        Log.d(TAG, "[stopAsync]isMerge = " + isMerge + ",mIsInStopProcess: " + mIsInStopProcess);
        if (mIsInStopProcess) {
            return;
        }
        Thread stopThread = new Thread(new Runnable() {
            public void run() {
                stopMav(isMerge);
                mOnHardwareStop = new Runnable() {
                    public void run() {
                        if (!isMerge) {
                            // if isMerge is true, onHardwareStopped
                            // will be called in onCapture.
                            onHardwareStopped(false);
                        }
                    }
                };
                mMavHandler.post(mOnHardwareStop);

                synchronized (mLock) {
                    mIsInStopProcess = false;
                    mLock.notifyAll();
                }
            }
        });
        synchronized (mLock) {
            mIsInStopProcess = true;
        }
        stopThread.start();
    }

    private void stopMav(boolean isMerge) {
        Log.d(TAG, "[stopMav]isMerge " + isMerge);
        mICameraDevice.stopMav(isMerge);
    }

    private class MavHandler extends Handler {
        public MavHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            Log.i(TAG, "[handleMessage]msg.what= " + msg.what);
            switch (msg.what) {
            case MSG_SAVE_FILE:
                saveFile();
                break;

            case MSG_FILE_SAVED:
                mICameraAppUi.dismissProgress();
                mICameraAppUi.setSwipeEnabled(true);
                resetCapture();
                mICameraView.init(mActivity, mICameraAppUi, mIModuleCtrl);
                mICameraView.show();
                break;

            case MSG_SET_PROGRESS:
                mICameraView.update(INFO_UPDATE_PROGRESS, (mCurrentNum + 1) * 9 / NUM_MAV_CAPTURE);
                break;

            case MSG_HIDE_VIEW:
                mICameraView.hide();
                break;

            case MSG_SHOW_VIEW:
                mICameraView.show();
                break;

            case MSG_IN_CAPTURING:
                mICameraView.update(INFO_IN_CAPTURING);
                break;

            case MSG_OUTOF_CAPTURING:
                mICameraView.update(INFO_OUTOF_CAPTURING);
                break;

            case MSG_INIT:
                mICameraView.init(mActivity, mICameraAppUi, mIModuleCtrl);
                break;

            case MSG_UNINIT:
                mICameraView.uninit();
                break;

            case MSG_SHOW_INFO:
                String showInfoStr = mActivity.getString(R.string.mav_dialog_title)
                        + mActivity.getString(R.string.camera_continuous_not_supported);
                mICameraAppUi.showInfo(showInfoStr);
                break;

            case MSG_ORIENTATION_CHANGED:
                mICameraView.onOrientationChanged(mOrientation);
                break;
                
            case MSG_ON_CAMERA_PARAMETERS_READY:
                mICameraView.init(mActivity, mICameraAppUi, mIModuleCtrl);
                break;
                
            default:
                break;
            }
        }
    }

    private OnFileSavedListener mFileSaverListener = new OnFileSavedListener() {
        @Override
        public void onFileSaved(Uri uri) {
            Log.i(TAG, "[onFileSaved]uri= " + uri);
            mMavHandler.removeMessages(MSG_FILE_SAVED);
            mMavHandler.sendEmptyMessage(MSG_FILE_SAVED);
        }
    };

    private void saveFile() {
        Log.i(TAG, "[saveFile]...");
        Location location = mIModuleCtrl.getLocation();
        mIFileSaver.savePhotoFile(mJpegImageData, null, mCaptureTime, true, location, 0,
                mFileSaverListener);
    }

    private void safeStop() {
        Log.i(TAG, "[safeStop] mIsInStopProcess = " + mIsInStopProcess);
        while (mIsInStopProcess) {
            try {
                synchronized (mLock) {
                    mLock.wait();
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "[safeStop]InterruptedException in waitLock");
            }
        }
    }

    private void lockLandscape(int orientation) {
        if (orientation == 270 || orientation == 0) {
            mIModuleCtrl.setOrientation(true, 270);
        } else {
            mIModuleCtrl.setOrientation(true, 90);
        }
    }

    private void unlockLandscape() {
        mIModuleCtrl.unlockOrientation();
    }
}
