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
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;

import com.android.camera.R;

import com.mediatek.camera.ICameraContext;
import com.mediatek.camera.mode.CameraMode;
import com.mediatek.camera.platform.ICameraAppUi.SpecViewType;
import com.mediatek.camera.platform.ICameraAppUi.ViewState;
import com.mediatek.camera.platform.ICameraDeviceManager.ICameraDevice.AutoFocusMvCallback;
import com.mediatek.camera.platform.ICameraDeviceManager.ICameraDevice.Listener;
import com.mediatek.camera.platform.ICameraView;
import com.mediatek.camera.platform.IFocusManager;
import com.mediatek.camera.platform.IFileSaver.FILE_TYPE;
import com.mediatek.camera.platform.IFileSaver.OnFileSavedListener;
import com.mediatek.camera.util.CaptureSound;
import com.mediatek.camera.util.Log;
import com.mediatek.camera.util.Util;

import junit.framework.Assert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MotionTrackMode extends CameraMode implements IFocusManager.FocusListener{

    protected static final int SHOW_PROGERSS_UI = 401;
    protected static final int UPDATE_PROGERSS_STEP = 402;

    private static final String TAG = "MotionTrackMode";

    private static final int SHOW_INFO_LENGTH_LONG = 5 * 1000;
    
    private static final int ON_CAMERA_PARAMTERS_READY = 1;
    private static final int GUIDE_SHUTTER = 2;
    private static final int MSG_ONFRAME_AVALIALBE = 3;
    private static final int MSG_ONCAPTURE_AVALIABLE = 4;
    private static final int MSG_RESOTRE_MOTION_TRACK_VIEW = 5;
    private static final int MSG_HIDE_ALL_VIEW = 6;
    private static final int MSG_HIDE_CENTER_MOTION_TRACK_VIEW = 7;
    // FILE NAME USED TAG --->
    private static final int ORIGINAL_IMAGE = 501;
    private static final int INTERMEDIA_IMAGE = 502;
    private static final int BLENDED_IMAGE = 503;
    private static final int MAX_MOTHION_TRACK_NUMBER = 20;
    private static final int BLOCK_NUM = 9;
    private static final String MOTION_TRACK_SUFFIX = "MT";
    private static final String TRACK_PHOTO_SUFFIX = "MTTK";
    private static final String INTERMEDIA_PHOTO_SUFFIX = "MTIT";
    private static final String MOTION_TRACK_HIDE_FOLDER_NAME = ".ConShots/";
    private static final String FOLDER_INTERMEDIA_NAME = "InterMedia/";
    private static final String IMAGE_TYPE = ".jpg";
    private static final int SHOW_SAVING_PROGRESS = 601;
    private static final int DISMISS_SAVING_PROGRESS = 602;

    private static final int CAMERA_MOTION_TRACK_DATA_OFFSET = 16;
    private static final int CAMERA_MOTION_TRACK_INTERMEDIAT_DATA_OFFSET = 4;

    private int mCurrentNum = 0;
    private int mFrameX;
    private int mFrameY;

    private long mLeftStorage = -1;

    private boolean mIsLongPressed = false;
    private boolean mIsBlendedFailed = false;
    private boolean mIsShowAlterDilogInProcess = false;
    private boolean mIsMotionTrackStopped = true;
    
    /**
     * used to tag current whether is in camera view or not, when slide to
     * gallery, this tag is false, so this time when play a video, this time
     * camera will onPause(), when video is finished,camera will onResume(),so
     * will receive the MSG parametersReady but this time also not need show MT
     * view
     */
    private boolean mIsInCameraPreview = true;
    
    /**
     * when in settings, change one setting,so MT will receive the SMG
     * parametersReady , but in this case not need show MT view,and also current
     * have the interface to get current setting state[show setting/hide
     * setting]
     */
    private boolean mIsShowSetting = false;
    
    /**
     * used tag this time focus is from capture why need this ? because when you
     * tap capture button,need first do a focus and them take picture but when
     * the msg:onshutterbuttonClick or onShutterButtonLongpressed is received
     * after the auto focus callback,so the view state will be changed to
     * Normal, when receive the take picture command, will set the view state
     * capture/continuous state,so the view will flash
     */
    private boolean mIsFocusFromCapture = false;
    
    private boolean mIsAutoFocusCallback = false;

    private String mTimeFolderName;

    private Activity mActivity;
    private Thread mWaitSavingDoneThread;
    private Thread mLoadSoundTread;

    private Handler mMotionTrackHandler;
    private SimpleDateFormat mFormat;
    private CaptureSound mCaptureSound;

    private ICameraView mICameraView;

    public MotionTrackMode(ICameraContext cameraContext) {
        super(cameraContext);
        Log.i(TAG, "[MotionTrackMode]constructor...");
        mActivity = cameraContext.getActivity();
        
        mICameraView = mICameraAppUi.getCameraView(SpecViewType.MODE_MOTION_TRACK);
        mICameraView.init(mActivity, mICameraAppUi, mIModuleCtrl);

        mMotionTrackHandler = new MotiontrackHandler(mActivity.getMainLooper());
        // Make sure the mCaptureSound != null
        mCaptureSound = new CaptureSound();
        mLoadSoundTread = new LoadSoundTread();
        mLoadSoundTread.start();
        initalizeDateForamt();
    }

    @Override
    public void pause() {
        super.pause();
        Log.i(TAG, "[pause]");
        if (mMotionTrackHandler != null) {
            mMotionTrackHandler.removeMessages(MSG_HIDE_ALL_VIEW);
            boolean value = mMotionTrackHandler.sendEmptyMessage(MSG_HIDE_ALL_VIEW);
            Log.i(TAG, "[pause()],hide msg send success : " + value);
        }
    }

    @Override
    public boolean close() {
        Log.i(TAG, "[close]");

        mICameraView.uninit();
        unlockLandscape();

        if (mCaptureSound != null) {
            mCaptureSound.release();
            mCaptureSound = null;
        }
        mIsLongPressed = false;
        if (mMotionTrackHandler != null) {
            mMotionTrackHandler.removeMessages(ON_CAMERA_PARAMTERS_READY);
        }

        return false;
    }

    @Override
    public boolean execute(ActionType type, Object... arg) {
        
        if (ActionType.ACTION_ORITATION_CHANGED != type) {
            Log.i(TAG, "[execute],type = " + type);
        }
        
        switch (type) {
        case ACTION_ON_CAMERA_OPEN:
            super.updateDevice();
            break;
        
        case ACTION_ON_CAMERA_CLOSE:
            
            // current maybe MT is running,so when user not release finger on
            // the capture button
            // so will stop MT
            stopMotinTrackCapture(false);
            // this time not need receive the callback's,such as on
            // frame/onitermediaData...
            mICameraDevice.setMotionTrackCallback(null);
            setModeState(ModeState.STATE_CLOSED);
            // in MT capture process -> press power key ->launch camera will
            // found the UI is error,so in this case,when close camera,need
            // restore the view state
            mICameraAppUi.restoreViewState();
            break;
        
        case ACTION_ON_START_PREVIEW:
            Assert.assertTrue(arg.length == 1);
            startPreview((Boolean) arg[0]);
            break;
        
        case ACTION_ON_CAMERA_PARAMETERS_READY:
            super.updateDevice();
            super.updateFocusManager();
            if (mIFocusManager != null) {
                mIFocusManager.setListener(this);
            }
            setModeState(ModeState.STATE_IDLE);
            
            mMotionTrackHandler.sendEmptyMessage(ON_CAMERA_PARAMTERS_READY);
            mICameraDevice.setAutoFocusMoveCallback(mAutoFocusMvCallback);
            break;
        
        case ACTION_PREVIEW_VISIBLE_CHANGED:
            updatePreviewVisible(((Integer) arg[0]).intValue());
            break;
        
        case ACTION_ORITATION_CHANGED:
            lockLandscape((Integer) arg[0]);
            break;
        
        case ACTION_ON_COMPENSATION_CHANGED:
            mICameraView.onOrientationChanged((Integer) arg[0]);
            break;
        
        case ACTION_ON_FULL_SCREEN_CHANGED:
            onFullScreenChange((Boolean) arg[0]);
            break;
        
        case ACTION_PHOTO_SHUTTER_BUTTON_CLICK:
            //if the space is not enough,not do focus and capture
            if (mIFocusManager != null && mIFileSaver.isEnoughSpace()) {
                mIFocusManager.focusAndCapture();
            }
            break;
        
        case ACTION_SHUTTER_BUTTON_LONG_PRESS:
            startMotionTrackCapture();
            break;
        
        case ACTION_SHUTTER_BUTTON_FOCUS:
            boolean isPressed = (Boolean) arg[0];
            Log.i(TAG, "isPressed = " + isPressed + ", mIsLongPressed = " + mIsLongPressed);
            // Auto focus just do normal capture,so if long pressed,means
            // current is MT capture,not need do AF
            if (!mIsLongPressed && mIFocusManager != null) {
                
                if (!mIFileSaver.isEnoughSpace()) {
                    Log.i(TAG, "current space is not enough,so return");
                    break;
                }
                if (isPressed) {
                    mIFocusManager.onShutterDown();
                    mIFocusManager.clearView();
                    mIsFocusFromCapture = true;
                    
                } else {
                    mIFocusManager.onShutterUp();
                }
                
            }
            
            // Follow is used when release flinger,will stop MT
            if (!isPressed && mIsLongPressed) {
                stopMotinTrackCapture(true);
            }
            break;
        
        case ACTION_ON_BACK_KEY_PRESS:
            return onBackPressed();
            
        case ACTION_ON_SETTING_BUTTON_CLICK:
            // if current user have click the settings ,show hide MT view
            onSettingClick((Boolean) arg[0]);
            break;
        
        case ACTION_ON_SINGLE_TAP_UP:
            onSinlgeTapUp((View) arg[0], (Integer) arg[1], (Integer) arg[2]);
            break;
        
        default:
            return false;
        }
        
        return true;
    }

    
    @Override
    public void autoFocus() {
        Log.i(TAG, "[autoFocus]...");
        mICameraDevice.autoFocus(mAutoFocusCallback);
        mICameraAppUi.setViewState(ViewState.VIEW_STATE_FOCUSING);
        if (ModeState.STATE_CAPTURING != getModeState()) {
            setModeState(ModeState.STATE_FOCUSING);
        }
    }

    @Override
    public void cancelAutoFocus() {
        Log.i(TAG, "[cancelAutoFocus]...current mode = " + getModeState());
        mICameraDevice.cancelAutoFocus();
        //if current in capture progress,not change the view state
        if (ModeState.STATE_CAPTURING != getModeState()) {
            mICameraAppUi.restoreViewState();
            setModeState(ModeState.STATE_IDLE);
        }
        setFocusParameters();
    }

    @Override
    public boolean capture() {
        Log.i(TAG, "[capture]...");
        startNormalCapture();
        return true;
    }

    @Override
    public void startFaceDetection() {
        Log.i(TAG, "[startFaceDetection]...");
        mIModuleCtrl.startFaceDetection();
    }

    @Override
    public void stopFaceDetection() {
        Log.i(TAG, "[stopFaceDetection]...");
        mIModuleCtrl.stopFaceDetection();
    }

    @Override
    public void setFocusParameters() {
        Log.i(TAG, "[setFocusParameters]mIsAutoFocusCallback = " + mIsAutoFocusCallback);
        mIModuleCtrl.applyFocusParameters(!mIsAutoFocusCallback);
        mIsAutoFocusCallback = false;
    }

    @Override
    public void playSound(int soundId) {
        mCameraSound.play(soundId);
    }
    
    // Follow is Normal capture M:{
    private void startNormalCapture() {
        if ((ModeState.STATE_IDLE != getModeState()) || !isEnoughSpace()) {
            Log.i(TAG, "[startNormalCapture],invalid state, return!");
            return;
        }

        Log.i(TAG, "[startNormalCapture]...");
        mICameraAppUi.setSwipeEnabled(false);
        mICameraAppUi.setViewState(ViewState.VIEW_STATE_CAPTURE);
        setModeState(ModeState.STATE_CAPTURING);
        mICameraDevice.takePicture(mShutterCallback, mRawPictureCallback, mPostViewPictureCallback,
                mJpegPictureCallback);
    }

    private boolean startMotionTrackCapture() {
        if ((ModeState.STATE_IDLE != getModeState()) || !isEnoughSpace() || !mIsMotionTrackStopped) {
            Log.i(TAG, "[startMotionTrackCapture],invalid state : " + getModeState()
                    + ",isEnoughtSpace = " + isEnoughSpace() + ",mIsMotionTrackStopped = "
                    + mIsMotionTrackStopped + " ,so return");
            return false;
        }
        Log.i(TAG, "[startMotionTrackCapture]......");
        // initialize the arguments
        mIsLongPressed = true;
        mIsBlendedFailed = false;
        mIsShowAlterDilogInProcess = false;
        mCurrentNum = 0;
        mTimeFolderName = null;
        mLeftStorage = mICameraAppUi.updateRemainStorage();
        mIFileSaver.init(FILE_TYPE.JPEG, 0, null, -1);
        mICameraAppUi.setViewState(ViewState.VIEW_STATE_CONTINUOUS_CAPTURE);
        lock3A();

        if (mICameraView != null) {
            mICameraView.update(MotionTrackView.INFO_UPDATE_PROGRESS, SHOW_PROGERSS_UI);
            doStart();
        }

        return true;
    }

    private void lock3A() {
        mIFocusManager.setAeLock(true);
        mIFocusManager.setAwbLock(true);
        mIFocusManager.overrideFocusMode(Parameters.FOCUS_MODE_AUTO);
        mIModuleCtrl.applyFocusParameters(true);
    }

    private void doStart() {
        Log.i(TAG, "[doStart] ...begin");
        setModeState(ModeState.STATE_CAPTURING);
        mICameraDevice.setMotionTrackCallback(mMotionTrackListener);
        mICameraDevice.startMotionTrack(MAX_MOTHION_TRACK_NUMBER);
        mIsMotionTrackStopped = false;
        Log.i(TAG, "[doStart] ...end");

    }

    private boolean stopMotinTrackCapture(boolean merge) {
        boolean needMerge = mCurrentNum > 1 && !mIsBlendedFailed;
        mMotionTrackHandler.removeMessages(MSG_HIDE_ALL_VIEW);
        boolean value = mMotionTrackHandler.sendEmptyMessage(MSG_HIDE_ALL_VIEW);
        Log.d(TAG, "[stopMotinTrackCapture]msg send succeess = " + value + ",mIsLongPressed = "
                + mIsLongPressed + ",needMerge = " + needMerge);
        // why need mIsLongPressed ?
        // because if you not takeMT picture and close camera,so this time not
        // need show the alterDialog
        if (!needMerge && mIsLongPressed) {
            showMotionFailedAlterDialog();
        }
        mIsLongPressed = false;
        doStop();
        unlockAeAwb();
        return true;
    }

    private void showMotionFailedAlterDialog() {
        Log.d(TAG, "[showMotionFailedAlterDialog] begin");

        if (mIsShowAlterDilogInProcess) {
            Log.i(TAG, "[showMotionFailedAlterDialog]will ignor this time");
            return;
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                captureFailed();
            }
        };

        if (mCurrentNum < 2) {
            mICameraAppUi.showAlertDialog(null,
                    mActivity.getString(R.string.motion_track_required_more),
                    mActivity.getString(android.R.string.ok), runnable, null, null);
        } else {
            mICameraAppUi.showAlertDialog(null,
                    mActivity.getString(R.string.motion_track_blended_failed),
                    mActivity.getString(android.R.string.ok), runnable, null, null);
        }

        mIsShowAlterDilogInProcess = true;
        Log.d(TAG, "[showMotionFailedAlterDialog] end");
    }

    private void doStop() {
        Log.d(TAG, "[doStop]isMerge mIsMotionTrackStopped = " + mIsMotionTrackStopped);
        if(mCaptureSound != null) {
            mCaptureSound.stop();
        }
        if (mICameraDevice != null && mIsMotionTrackStopped == false) {
            Log.i(TAG, "[doStop]stopMotionTrack begin");
            mICameraDevice.stopMotionTrack();
            Log.i(TAG, "[doStop]stopMotionTrack end");
            mIsMotionTrackStopped = true;
        }

    }

    private void captureFailed() {
        Log.d(TAG, "[captureFailed]...mIsInCameraPreview = " + mIsInCameraPreview);
        if (!mIsInCameraPreview) {
            Log.d(TAG, "[captureFailed] current is not in camera preview,so return");
            return;
        }
        if (mIFocusManager != null) {
            mIFocusManager.resetTouchFocus();
        }
        mMotionTrackHandler.removeMessages(MSG_RESOTRE_MOTION_TRACK_VIEW);
        mMotionTrackHandler.sendEmptyMessage(MSG_RESOTRE_MOTION_TRACK_VIEW);
        mIsShowAlterDilogInProcess = false;
        mICameraAppUi.setViewState(ViewState.VIEW_STATE_NORMAL);
        setModeState(ModeState.STATE_IDLE);
    }

    private void unlockAeAwb() {
        Log.d(TAG, "[unlockAeAwb]");

        if (mIFocusManager != null) {
            mIFocusManager.setAeLock(false);
            mIFocusManager.setAwbLock(false);
        }
        mIModuleCtrl.applyFocusParameters(true);
        if (mIFocusManager != null
                && Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mIFocusManager.getFocusMode())) {
            mICameraDevice.cancelAutoFocus();
        }
    }

    private void updatePreviewVisible(int visible) {
        if (View.VISIBLE == visible) {
            mICameraView.show();
        } else {
            mICameraView.hide();
        }
    }

    private class MotiontrackHandler extends Handler {
        public MotiontrackHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "[handleMessage]msg.what = " + msg.what);
            switch (msg.what) {
            case ON_CAMERA_PARAMTERS_READY:
                lockLandscape(mIModuleCtrl.getOrientation());
                // Camera ->Gallery,click the picture to Print and save,
                // this time Gallery Activity will onPause,when save the print
                // gallery will onResume,so MT will receive this MSG,
                // but need show the view
                
                //if in settings change one setting, will also receive this MSG,
                //so this time not need show the MT view
                Log.i(TAG, "[handleMessage]parametres ready , mIsInCameraPreview = "
                        + mIsInCameraPreview + ",mIsShowSetting = " + mIsShowSetting);
                if (mIsInCameraPreview && !mIsShowSetting) {
                    mICameraView.show();
                    showGuideString(GUIDE_SHUTTER);
                }
                break;

            case MSG_ONCAPTURE_AVALIABLE:
                mICameraView.update(MotionTrackView.INFO_UPDATE_PROGRESS,
                        (Object) UPDATE_PROGERSS_STEP, (mCurrentNum + 1) * BLOCK_NUM
                                / MAX_MOTHION_TRACK_NUMBER);
                break;

            case MSG_ONFRAME_AVALIALBE:
                mICameraView.update(MotionTrackView.INFO_UPDATE_MOVING, mFrameX, mFrameY);
                break;

            case MSG_RESOTRE_MOTION_TRACK_VIEW:
                mICameraView.reset();
                break;

            case MSG_HIDE_ALL_VIEW:
                mICameraView.hide();
                break;

            case MSG_HIDE_CENTER_MOTION_TRACK_VIEW:
                break;

            case SHOW_SAVING_PROGRESS:
                showSavingProcess();
                break;

            case DISMISS_SAVING_PROGRESS:
                mICameraAppUi.dismissProgress();
                mICameraAppUi.restoreViewState();
                mICameraAppUi.showRemainIfNeed();
                if (mIsInCameraPreview) {
                    mICameraView.reset();
                }
                setModeState(ModeState.STATE_IDLE);
                break;

            default:
                break;
            }
        }
    }

    private Listener mMotionTrackListener = new Listener() {

        @Override
        public void onDeviceCallback(Object... obj) {
            if (null == obj) {
                Log.w(TAG, "[onDeviceCallback]datas is null,return!");
                return;
            }
            byte[] byteArray = (byte[]) obj[0];
            IntBuffer intBuf = ByteBuffer.wrap(byteArray).order(ByteOrder.nativeOrder())
                    .asIntBuffer();
            int dataLength = byteArray.length - CAMERA_MOTION_TRACK_DATA_OFFSET;
            int intermeidaDataLength = byteArray.length
                    - CAMERA_MOTION_TRACK_INTERMEDIAT_DATA_OFFSET;
            byte[] motionTrackdata = new byte[dataLength];
            Log.i(TAG, "intBuf.get(0) =" + intBuf.get(0) + ",byteArray =  " + byteArray.toString()
                    + ",dataLength= " + dataLength);
            switch (intBuf.get(0)) {
            case 0:
                /*
                 * case 0 means current device location. data structure:
                 * -----byte 1~3 is reserved -----byte 4~7 movement in x-axis
                 * (32-bit signed integer) -----byte 8~11 is movement in y-axis
                 * (32-bit signed integer)
                 */
                if (mCurrentNum < 1 || mCurrentNum >= MAX_MOTHION_TRACK_NUMBER || !mIsLongPressed) {
                    Log.w(TAG, "will return ,not update the MovingUI");
                    return;
                }

                if (mICameraView != null) {
                    mFrameX = intBuf.get(1);
                    mFrameY = intBuf.get(2);
                    Log.v(TAG, "[onDeviceCallback]mFrameX = " + mFrameX + ",mFrameY = " + mFrameY);
                    mMotionTrackHandler.removeMessages(MSG_ONFRAME_AVALIALBE);
                    mMotionTrackHandler.sendEmptyMessage(MSG_ONFRAME_AVALIALBE);
                }
                break;

            case 1:
                /*
                 * case 1 means current have a picture callback. data structure:
                 * -----byte 1~3 is reserved -----byte 4~7 movement in x-axis
                 * (32-bit signed integer) -----byte 8~11 is movement in y-axis
                 * (32-bit signed integer)
                 */

                System.arraycopy(byteArray, CAMERA_MOTION_TRACK_DATA_OFFSET, motionTrackdata, 0,
                        dataLength);
                onCapture(motionTrackdata);
                break;

            case 2:
                /*
                 * case 2 means the blended callback. data structure: -----byte
                 * 1~3 is reserved -----byte 4~length is jpeg data
                 */

                int imageIndex = intBuf.get(1);
                int totalIndex = intBuf.get(2);
                System.arraycopy(byteArray, CAMERA_MOTION_TRACK_DATA_OFFSET, motionTrackdata, 0,
                        dataLength);
                onBlended(motionTrackdata, imageIndex, totalIndex);
                break;

            case 3:
                /*
                 * case 3 means the EIS data callback. data structure: -----byte
                 * 1~3 is reserved -----byte 4~length is EIS data
                 */

                byte[] motionTrackIntermeidaData = new byte[intermeidaDataLength];
                System.arraycopy(byteArray, CAMERA_MOTION_TRACK_INTERMEDIAT_DATA_OFFSET,
                        motionTrackIntermeidaData, 0, intermeidaDataLength);
                onIntermediate(motionTrackIntermeidaData);
                break;

            default:
                break;

            }
        }
    };

    private void onCapture(byte[] data) {
        if (data == null) {
            Log.w(TAG, "[onCapture]data is null,return!");
            return;
        }
        // when sdcard is full,need stop capture
        if (needStopCapture()) {
            Log.i(TAG, "[onCapture]needStopCapture,return!");
            stopMotinTrackCapture(true);
            return;
        }

        mCurrentNum++;
        Log.i(TAG, "[onCapture]data's length = " + data.length + ",mCurrentNum = " + mCurrentNum);
        // just first callback to start play
        if (mIsLongPressed && mCurrentNum == 1) {
            mCaptureSound.play();
        }

        // must store the name
        if (mTimeFolderName == null) {
            mTimeFolderName = createJpegName(System.currentTimeMillis());
        }

        if (mCurrentNum >= 0 && mCurrentNum <= MAX_MOTHION_TRACK_NUMBER) {
            mMotionTrackHandler.removeMessages(MSG_ONCAPTURE_AVALIABLE);
            mMotionTrackHandler.sendEmptyMessage(MSG_ONCAPTURE_AVALIABLE);
        }

        // save the data
        String title = createFileName(ORIGINAL_IMAGE, mCurrentNum);
        mIFileSaver.savePhotoFile(data, title, System.currentTimeMillis(), false,
                mIModuleCtrl.getLocation(), 0, null);

        if (mCurrentNum == MAX_MOTHION_TRACK_NUMBER) {
            stopMotinTrackCapture(true);
        }
    }

    private void onIntermediate(byte[] data) {
        Log.i(TAG, "[onIntermediate]...");

        if (data == null) {
            Log.w(TAG, "[onIntermediate]data is null, return!");
            return;
        }
        String title = createFileName(INTERMEDIA_IMAGE, -1);
        mIFileSaver.savePhotoFile(data, title, System.currentTimeMillis(), false,
                mIModuleCtrl.getLocation(), 0, null);
    }

    private void onBlended(byte[] data, int imageIndex, int totalIndex) {
        Log.d(TAG, "[onBlended] imageindex = " + imageIndex + ", totoalIndex = " + totalIndex
                + ",data.length = " + data.length);

        // totalIndex = 0 means blended failed
        if (totalIndex == 0) {
            mIsBlendedFailed = true;
            showMotionFailedAlterDialog();
            return;
        }

        //first onBlended callback show the saving dialog
        if (imageIndex == 0 && totalIndex != 0) {
            savingDoneThread();
        }
        imageIndex++;
        String title = createFileName(BLENDED_IMAGE, imageIndex);
        mIFileSaver.savePhotoFile(data, title, System.currentTimeMillis(), false,
                mIModuleCtrl.getLocation(), 0, null);
        mIsBlendedFailed = false;

        if (imageIndex == totalIndex) {
            title = createFileName(-1, -1);
            mIFileSaver.savePhotoFile(data, title, System.currentTimeMillis(), true,
                    mIModuleCtrl.getLocation(), 0, mFileSavedListener);
        }
    }

    /**
     * ************The Name Rule****************** Follow rule is discuss for
     * Gallery,here will use this rule to parse the JPEG Original Image: Folder
     * path: /DCIM/Camera/.Conshots/(IMG_ + date and time + MT)/ File name:
     * IMG_+ date and time + MT +number Blend Image: Folder path:
     * /DCIM/Camera/.Conshots/(IMG_ + date and time + MTTK) File name: IMG_+
     * date and time + MTTK+ number EIS Data: Folder
     * path:/DCIM/Camera/.Conshots/InterMedia/ File name:IMG_ + date and time +
     * MTIT Last Blend Image: Folder path: /DCIM/Camera File name: IMG_ + date
     * and time + MT
     */
    private String createFileName(int suffixIndex, int index) {
        String fileName = mTimeFolderName;
        String folderName = MOTION_TRACK_HIDE_FOLDER_NAME;

        if (suffixIndex == INTERMEDIA_IMAGE) {
            folderName += FOLDER_INTERMEDIA_NAME;
        } else if (ORIGINAL_IMAGE == suffixIndex || BLENDED_IMAGE == suffixIndex) {
            folderName += fileName;
        }

        switch (suffixIndex) {
        case ORIGINAL_IMAGE:
            folderName = folderName + MOTION_TRACK_SUFFIX + "/";
            fileName = fileName + MOTION_TRACK_SUFFIX
                    + String.format(Locale.ENGLISH, "%02d", index);
            fileName = folderName + fileName;
            break;

        case INTERMEDIA_IMAGE:
            fileName += INTERMEDIA_PHOTO_SUFFIX;
            fileName = folderName + fileName;
            break;

        case BLENDED_IMAGE:
            folderName = folderName + TRACK_PHOTO_SUFFIX + "/";
            fileName = fileName + TRACK_PHOTO_SUFFIX + String.format(Locale.ENGLISH, "%02d", index);
            fileName = folderName + fileName;
            break;

        default:
            // means need add to DB and not need do anything
            fileName += MOTION_TRACK_SUFFIX;
            break;
        }

        // at last add the image type
        if (suffixIndex != INTERMEDIA_IMAGE) {
            fileName += IMAGE_TYPE;
        }

        Log.i(TAG, "[createFileName]fileName = " + fileName + ",suffixIndex = " + suffixIndex
                + ",index = " + index);

        return fileName;
    }

    private OnFileSavedListener mFileSavedListener = new OnFileSavedListener() {

        @Override
        public void onFileSaved(Uri uri) {
            Log.i(TAG, "[onFileSaved]uri = " + uri);
            if (uri != null) {
                mICameraAppUi.setSwipeEnabled(true);
            }
        }
    };

    private void showGuideString(int step) {
        Log.d(TAG, "[showGuideString]step = " + step);
        int guideId = 0;
        switch (step) {
        case GUIDE_SHUTTER:
            guideId = R.string.motion_track_guide_shutter;
            break;

        default:
            break;
        }
        if (guideId != 0) {
            mICameraAppUi.showInfo(mActivity.getString(guideId), SHOW_INFO_LENGTH_LONG);
        }
    }

    private void initalizeDateForamt() {
        mFormat = new SimpleDateFormat(mActivity.getString(R.string.image_file_name_format));
    }

    private String createJpegName(long dateTaken) {
        Date date = new Date(dateTaken);
        return mFormat.format(date);
    }

    private void savingDoneThread() {
        mWaitSavingDoneThread = new WaitMotionTrackSavingDoneThread();
        mWaitSavingDoneThread.start();
    }

    private class WaitMotionTrackSavingDoneThread extends Thread {
        @Override
        public void run() {
            Log.i(TAG, "[WaitMotionTrackSavingDoneThread]run...");
            mMotionTrackHandler.removeMessages(SHOW_SAVING_PROGRESS);
            mMotionTrackHandler.sendEmptyMessage(SHOW_SAVING_PROGRESS);
            mIFileSaver.waitDone();
            mMotionTrackHandler.removeMessages(DISMISS_SAVING_PROGRESS);
            mMotionTrackHandler.sendEmptyMessage(DISMISS_SAVING_PROGRESS);
        }
    }

    private class LoadSoundTread extends Thread {
        @Override
        public void run() {
            mCaptureSound.load();
        }
    }

    private void showSavingProcess() {
        mICameraAppUi.showProgress(mActivity.getString(R.string.saving));
    }

    private ShutterCallback mShutterCallback = new ShutterCallback() {

        @Override
        public void onShutter() {
            Log.d(TAG, "[mShutterCallback]onShutter,time = " + System.currentTimeMillis());

        }
    };

    private PictureCallback mRawPictureCallback = new PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "[mRawPictureCallback]onPictureTaken, time = " + System.currentTimeMillis());

        }
    };

    private PictureCallback mPostViewPictureCallback = new PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG,
                    "[mPostViewPictureCallback]onPictureTaken,time = " + System.currentTimeMillis());

        }
    };

    private PictureCallback mJpegPictureCallback = new PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.i(TAG, "[mJpegPictureCallback]onPictureTaken,time = " + System.currentTimeMillis()
                    + ",data = " + data);

            if (ModeState.STATE_CLOSED == getModeState()) {
                Log.i(TAG, "[onPictureTaken] Camera is Closed");
                mICameraAppUi.restoreViewState();
                mICameraAppUi.setSwipeEnabled(true);
                return;
            }
            
            // prepare the save request
            if (data != null) {
                mIFileSaver.init(FILE_TYPE.JPEG, 0, null, -1);
                long time = System.currentTimeMillis();
                String title = Util.createNameFormat(time,
                        mActivity.getString(R.string.image_file_name_format))
                        + ".jpg";
                mIFileSaver.savePhotoFile(data, title, time, true, mIModuleCtrl.getLocation(), 0,
                        mFileSavedListener);
            }
            mIFocusManager.updateFocusUI(); // Ensure focus indicator
            //Need Restart preview ,synchronize Normal photo 
            startPreview(true);
            mICameraAppUi.restoreViewState();

        }
    };

    // Follow is Normal capture M:}

    private boolean onBackPressed() {
        Log.i(TAG, "[onBackPressed]");
        captureFailed();
        return false;
    }

    // when Camera -> Gallery,need hide all the MotionTrack view
    // Gallery -> Camera,need lock the orientation and show relative view
    private void onFullScreenChange(boolean isPreview) {
        Log.i(TAG, "[onFullScreenChange] isPreview = " + isPreview);
        mIsInCameraPreview = isPreview;
        if (isPreview) {
            lockLandscape(mIModuleCtrl.getOrientation());
            mICameraView.show();
            mMotionTrackHandler.removeMessages(MSG_RESOTRE_MOTION_TRACK_VIEW);
            mMotionTrackHandler.sendEmptyMessage(MSG_RESOTRE_MOTION_TRACK_VIEW);
        } else {
            mMotionTrackHandler.removeMessages(MSG_HIDE_ALL_VIEW);
            mMotionTrackHandler.sendEmptyMessage(MSG_HIDE_ALL_VIEW);
        }
    }

    private void onSettingClick(boolean isShowing) {
        Log.d(TAG, "[onSettingClick] isShowing = " + isShowing);
        mIsShowSetting = isShowing;
        // when settings is showing/hiding, in MT mode need change the
        // cameraview state
        // just care MT mode view,camera view state will be change by
        // SettingManager
        if (isShowing) {
            // hide the MT view
            mMotionTrackHandler.removeMessages(MSG_HIDE_ALL_VIEW);
            mMotionTrackHandler.sendEmptyMessage(MSG_HIDE_ALL_VIEW);
        } else {
            mMotionTrackHandler.removeMessages(MSG_RESOTRE_MOTION_TRACK_VIEW);
            mMotionTrackHandler.sendEmptyMessage(MSG_RESOTRE_MOTION_TRACK_VIEW);
        }
    }

    private boolean needStopCapture() {
        boolean isNeed = mLeftStorage <= 0;
        return isNeed;
    }

    private void onSinlgeTapUp(View view, int x, int y) {
        Log.i(TAG, "[onSingleTapUp] begin view = " + view + ",x = " + x + ",y = " + y);

        if (ModeState.STATE_IDLE != getModeState()) {
            Log.i(TAG, "[onSingleTapUp] current mode state is error,so return");
            return;
        }

        String focusMode = null;
        if (mIFocusManager != null) {
            focusMode = mIFocusManager.getFocusMode();
            Log.i(TAG, "[onSingleTapUp] current focusMode = " + focusMode);
        }
        if (mICameraDevice == null || focusMode == null
                || (Parameters.FOCUS_MODE_INFINITY.equals(focusMode))) {
            Log.i(TAG, "current mICameraDevice/focusMode is null or focus mode is inifinity");
            return;
        }
        if (!mIFocusManager.getFocusAreaSupported()) {
            Log.i(TAG, "this project not supported Touch AF");
            return;
        }
        mIFocusManager.onSingleTapUp(x, y);
        Log.i(TAG, "[onSingleTapUp] end ");
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

    private final AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback() {
        
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            Log.i(TAG, "[onAutoFocus] success = " + success + ",current state = " + getModeState()
                    + ",mIsFocusFromCapture = " + mIsFocusFromCapture);
            
            if (ModeState.STATE_CLOSED == getModeState()) {
                Log.i(TAG, "[onAutoFocus]camera is closed,so return");
                return;
            }
            
            // when this time the focus is from capture,so the view state not
            // need restore.
            // because when capture finished,will set the view state and camera
            // state idle
            if (ModeState.STATE_FOCUSING == getModeState() && !mIsFocusFromCapture) {
                mICameraAppUi.restoreViewState();
            }
            if (ModeState.STATE_CAPTURING != getModeState()) {
                setModeState(ModeState.STATE_IDLE);
            }
            mIFocusManager.onAutoFocus(success);
            mIsAutoFocusCallback = true;
        }
    };
    
    private final AutoFocusMvCallback mAutoFocusMvCallback = new AutoFocusMvCallback() {

        @Override
        public void onAutoFocusMoving(boolean start, Camera camera) {
            Log.i(TAG, "[onAutoFocusMoving]moving = " + start);
            mIFocusManager.onAutoFocusMoving(start);
        }
        
    };
    
    
    private void startPreview(boolean needStop) {
        
        mIsAutoFocusCallback = false;
        
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mIFocusManager.resetTouchFocus();
            }
        });
        if (needStop) {
            stopPreview();
        }
        mIFocusManager.setAeLock(false); // Unlock AE and AWB.
        mIFocusManager.setAwbLock(false);
        
        mIModuleCtrl.applyFocusParameters(false);
        Log.i(TAG, "set setFocusParameters normal");
        
        mICameraDevice.startPreview();
        mICameraDevice.setAutoFocusMoveCallback(mAutoFocusMvCallback);
        mIFocusManager.onPreviewStarted();
        setModeState(ModeState.STATE_IDLE);
    }
    
    private void stopPreview() {
        Log.i(TAG, "[stopPreview]mCurrentState = " + getModeState());
        if (ModeState.STATE_CLOSED == getModeState()) {
            Log.i(TAG, "[stopPreview]Preview is stopped.");
            return;
        }
        mICameraDevice.cancelAutoFocus(); // Reset the focus.
        mICameraDevice.stopPreview();
        if (mIFocusManager != null) {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    mIFocusManager.onPreviewStopped();
                }
            });
        }
    }
}
