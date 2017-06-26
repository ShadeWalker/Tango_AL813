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

package com.mediatek.camera.mode.livephoto;

import android.graphics.Bitmap;
import android.media.MediaActionSound;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.view.View;

import com.android.camera.R;
import com.android.camera.Storage;

import com.mediatek.camera.ICameraContext;
import com.mediatek.camera.mode.VideoMode;
import com.mediatek.camera.platform.IFileSaver.FILE_TYPE;
import com.mediatek.camera.platform.IFileSaver.OnFileSavedListener;
import com.mediatek.camera.util.Log;
import com.mediatek.camera.util.Util;
import com.mediatek.effect.effects.VideoScenarioEffect;
import com.mediatek.media.MediaRecorderEx;

import junit.framework.Assert;

public class LivePhotoMode extends VideoMode {
    private static final String TAG = "LivePhotoMode";

    private static final int LIVE_PHOTO_TAG_IN_DB = 1;

    private static Object sWaitForVideoProcessing = new Object();

    private boolean mIsNeedBackGroundRecording = true;
    private boolean mIsNeedStartPreviewAgain = true;
    private boolean mIsFullScreen = true;
    private boolean mIsCanStartPreviewNow = true;
    private boolean mIsReleased = false;
    private boolean mIsSurfaceViewDisplayIdReady = false;
    private boolean mIsParameterReady = false;
    private boolean mCanRunLivePhoto = false;
    private long mDuration = -1l;

    public LivePhotoMode(ICameraContext cameraContext) {
        super(cameraContext);
        mIsSurfaceViewDisplayIdReady = false;
        Log.i(TAG, "[LivePhotoMode]constructor...");
    }

    @Override
    public boolean execute(ActionType type, Object... arg) {
        Log.i(TAG, "[execute]type = " + type);
        switch (type) {
        case ACTION_ON_CAMERA_OPEN:
            super.updateDevice();
            onCameraOpenDone();
            break;

        case ACTION_ON_CAMERA_PARAMETERS_READY:
            mIsParameterReady = true;
            doOnCameraParameterReady(((Boolean) arg[0]).booleanValue());
            break;

        case ACTION_ON_CAMERA_CLOSE:
            onCameraClose();
            break;

        case ACTION_ON_STOP_PREVIEW:
            break;

        case ACTION_NOTIFY_SURFCEVIEW_DISPLAY_IS_READY:
            notifySurfaceViewDisplayIsReady();
            break;

        case ACTION_SHUTTER_BUTTON_LONG_PRESS:
            onPhotoShutterButtonLongPressed();
            break;

        case ACTION_PHOTO_SHUTTER_BUTTON_CLICK:
            takeLivePhoto();
            break;

        case ACTION_ON_SINGLE_TAP_UP:
            Assert.assertTrue(arg.length == 3);
            if (getModeState() == ModeState.STATE_RECORDING) {
                onSingleTapUp((View) arg[0], (Integer) arg[1], (Integer) arg[2]);
            }
            break;

        case ACTION_ON_FULL_SCREEN_CHANGED:
            doOnFullScreenChanged(((Boolean) arg[0]).booleanValue());
            break;

        case ACTION_ON_BACK_KEY_PRESS:
            return onBackPressed();

        case ACTION_ON_USER_INTERACTION:
            return onUserInteraction();

        case ACTION_ON_MEDIA_EJECT:
            onMediaEject();
            break;

        default:
            return false;
        }

        return true;
    }

    @Override
    public void playSound(int soundId) {
        // Live photo don't recoding sounds, so it can play all action sounds.
        mCameraSound.play(soundId);
    }

    @Override
    public void initializeShutterStatus() {
        mICameraAppUi.setPhotoShutterEnabled(true);
    }

    @Override
    public void doOnCameraParameterReady(boolean startPreview) {
        Log.i(TAG, "[doOnCameraParameterReady]startPreview = " + startPreview
                + "mSurfaceViewDisplayIdReady " + mIsSurfaceViewDisplayIdReady
                + "isVideoProcessing  = " + isVideoProcessing() + "mIsParameterReady  =" + mIsParameterReady);
        mIsCanStartPreviewNow = mIsSurfaceViewDisplayIdReady && !isVideoProcessing();
        mIsNeedBackGroundRecording = true;
        // in this case,we should do start preview in mSurfaceViewDisplayIdReady
        if (mIsMediaRecorderRecording || !mIsParameterReady) {
            mHandler.sendEmptyMessage(INIT_SHUTTER_STATUS);
            Log.i(TAG, "mIsMediaRecorderRecording is true so not doOnCameraParameterReady");
            return;
        }
        super.updateParameters();
        mICameraDevice.enableRecordingSound("1");
        if (mIsCanStartPreviewNow) {
            setModeState(ModeState.STATE_IDLE);
            super.doOnCameraParameterReady(startPreview);
        }
        Log.i(TAG, "[doOnCameraParameterReady] end mCanStartPreviewNow = " + mIsCanStartPreviewNow);
    }

    @Override
    public void initializeNormalRecorder() {
        Log.i(TAG, "[initializeRecorder]...");
        super.initializeNormalRecorder();
        MediaRecorderEx.startLivePhotoMode(mMediaRecorder);
    }

    @Override
    public void doStartPreview() {
        Log.i(TAG, "[doStartPreview]...");
        super.doStartPreview();
        if (Storage.getLeftSpace() <= 0) {
            Log.w(TAG, "[doStartPreview]not enough space,return!");
            mICameraAppUi.setSwipeEnabled(true);
            return;
        }
        mCanRunLivePhoto = true;
        if (ModeState.STATE_CLOSED == getModeState() || !mIsFullScreen || isVideoProcessing()
                || !mIsSurfaceViewDisplayIdReady || mIsReleased) {
            Log.w(TAG, "[doStartPreview]invalid state,return! = " + ",mFullScreen = "
                    + mIsFullScreen + ",mIsReleased = " + mIsReleased);
            mICameraAppUi.setSwipeEnabled(true);
            return;
        }
        if (mIsMediaRecorderRecording) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopVideoRecordingAsync(true);
                }
            });
        } else {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mIModuleCtrl.unlockOrientation();
                    startVideoRecording();
                }
            });
            mICameraAppUi.setSwipeEnabled(true);
        }
    }

    @Override
    public boolean startRecording() {
        Log.i(TAG, "[startRecording] ..");
        return startNormalRecording();
    }

    public void notifySurfaceViewDisplayIsReady() {
        Log.i(TAG, "notifySurfaceViewDisplayIsReady" + ",mCanStartPreviewNow = "
                + mIsCanStartPreviewNow);
        mIsSurfaceViewDisplayIdReady = true;
        if (!mIsCanStartPreviewNow) {
            doOnCameraParameterReady(true);
        }
    }

    @Override
    public void initializeRecordingView() {
        // do nothing
    }

    @Override
    public void addVideoToMediaStore() {
        Log.i(TAG, "[addVideoToMediaStore] mCurrentVideoFilename = " + mCurrentVideoFilename
                + ",mNeedBackGroundRecording = " + mIsNeedBackGroundRecording);
        if (!mIsNeedBackGroundRecording) {
            releaseMediaRecorder();
            deleteCurrentVideo();
            Log.i(TAG, "[addVideoToMediaStore] deleteCurrentVideo,return.");
            return;
        }
        VideoScenarioEffect vv = new VideoScenarioEffect();
        Bitmap lastFrame = createVideoLastFramePicture();
        int rotation = 0;
        boolean result = false;
        try {
            if (mProfile != null && lastFrame != null) {
                rotation = Util.getRecordingRotation(mIModuleCtrl.getOrientation(),
                        mICameraDeviceManager.getCurrentCameraId(), mICameraDeviceManager
                                .getCameraInfo(mICameraDeviceManager.getCurrentCameraId()));
                Log.i(TAG, "[addVideoToMediaStore] MFF setScenario begin mRotation = " + rotation);
                if (vv.setScenario(
                        mActivity,
                        getScenario(rotation, mProfile.videoFrameWidth, mProfile.videoFrameHeight,
                                mDuration, mCurrentVideoFilename,
                                generateVideoFilename(mProfile.fileFormat, "livephoto")), mProfile,
                        lastFrame, lastFrame)) {
                    Log.i(TAG, "[addVideoToMediaStore] MFF Process begin ");
                    result = vv.process();
                    Log.i(TAG, "[addVideoToMediaStore] MFF Process end result = " + result);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        deleteCurrentVideo();
        mCurrentVideoFilename = mVideoFilename;
        mIFileSaver.init(
                FILE_TYPE.LIVEPHOTO,
                mProfile.fileFormat,
                Integer.toString(mProfile.videoFrameWidth) + "x"
                        + Integer.toString(mProfile.videoFrameHeight), Util.getRecordingRotation(
                        mIModuleCtrl.getOrientation(), mICameraDeviceManager.getCurrentCameraId(),
                        mICameraDeviceManager.getCameraInfo(mICameraDeviceManager
                                .getCurrentCameraId())));
        mIFileSaver.saveVideoFile(mIModuleCtrl.getLocation(), mVideoTempPath, computeDuration(),
                LIVE_PHOTO_TAG_IN_DB, mLivePhotoSavedListener);
    }
    
    @Override
    public boolean startVideoRecording(){
        if (mIsReleased) {
            Log.i(TAG,"[startVideoRecording] mIsReleased is true ");
            return false;
        }
        return super.startVideoRecording();
    }
    
    @Override
    public boolean close() {
        Log.i(TAG, "[close]...");
        if (mIsReleased) {
            return true;
        }
        mIsParameterReady = false;
        mCanRunLivePhoto = false;
        mIsReleased = true;
        mIsSurfaceViewDisplayIdReady = false;
        if (mICameraDevice != null) {
            // switch between PIP and LivePhoto
            // quickly,mICameraDevice may be is null sometimes
            mICameraDevice.enableRecordingSound("0");
        }
        stopVideoOnPause();

        return true;
    }

    @Override
    public void onCameraClose() {
        Log.i(TAG, "[onCameraClose]...");
        mIsReleased = true;
        mIsParameterReady = false;
        mCanRunLivePhoto = false;
        mIsCanStartPreviewNow = true;
        mIsSurfaceViewDisplayIdReady = false;
        super.onCameraClose();
    }

    @Override
    public void stopVideoRecordingAsync(boolean isShowSaving) {
        Log.i(TAG, "[stopVideoRecordingAsync] mMediaRecorderRecording=" + mIsMediaRecorderRecording);
        mICameraAppUi.changeZoomForQuality();
        mICameraAppUi.setVideoShutterMask(false);
        if (ModeState.STATE_SAVING == getModeState()) {
            Log.i(TAG, "[stopVideoRecordingAsync],current state is saving,return");
            return;
        }
        setModeState(ModeState.STATE_SAVING);
        if (mIsMediaRecorderRecording) {
            if (isShowSaving && mIsNeedBackGroundRecording) {
                mICameraAppUi.setVideoShutterEnabled(false);
                mICameraAppUi.showProgress(mActivity.getResources().getString(
                        R.string.saving_livephoto));
            }
            mVideoSavingTask = new SavingTask();
            mVideoSavingTask.start();
        } else {
            releaseMediaRecorder();
            if (mStoppingAction == STOP_RETURN_UNVALID) {
                mVideoModeHelper.doReturnToCaller(false, mCurrentVideoUri);
            }
        }
    }

    @Override
    public void stopVideoOnPause() {
        Log.i(TAG, "[stopVideoOnPause] mMediaRecorderRecording = " + mIsMediaRecorderRecording);
        if (ModeState.STATE_SAVING == getModeState()) {
            Log.i(TAG, "[stopVideoOnPause],current state is saving,return");
            return;
        }
        mIsNeedBackGroundRecording = false;
        super.stopVideoOnPause();
    }

    @Override
    public void stopRecording() {
        Log.i(TAG, "[stopRecording] mNeedBackGroundRecording = " + mIsNeedBackGroundRecording);
        if (mIsNeedBackGroundRecording) {
            Log.i(TAG, "[stopRecording] stopLivePhotoMode begin");
            MediaRecorderEx.stopLivePhotoMode(mMediaRecorder);
            mIModuleCtrl.lockOrientation();
            Log.i(TAG, "[stopRecording] stopLivePhotoMode end");
            playSound(MediaActionSound.SHUTTER_CLICK);
        }
        super.stopRecording();
        super.stopPreview();
    }

    @Override
    public boolean onBackPressed() {
        if (ModeState.STATE_CLOSED == getModeState() || (mICameraAppUi.isShowingProgress())
                || isVideoProcessing()) {
            return true;
        }
        return false;
    }

    @Override
    public void doAfterStopRecording(boolean fail) {
        Log.i(TAG, "[doAfterStopRecording] fail = " + fail + " mNeedBackGroundRecording = "
                + mIsNeedBackGroundRecording);
        if (mIsNeedBackGroundRecording) {
            super.doAfterStopRecording(fail);
        } else {
            Log.i(TAG, "[doAfterStopRecording] deleteCurrentVideo");
            mCurrentVideoFilename = mVideoFilename;
            deleteCurrentVideo();
            releaseMediaRecorder();
            synchronized (mVideoSavingTask) {
                Log.i(TAG, " [doAfterStopRecording] notify for releasing camera");
                mVideoSavingTask.notifyAll();
            }
            synchronized (sWaitForVideoProcessing) {
                Log.i(TAG, "[doAfterStopRecording] notify for video processing");
                mIsMediaRecorderRecording = false;
                sWaitForVideoProcessing.notifyAll();
            }
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mICameraAppUi.dismissProgress();
                }
            });
        }
    }

    private OnFileSavedListener mLivePhotoSavedListener = new OnFileSavedListener() {

        @Override
        public void onFileSaved(Uri uri) {
            Log.d(TAG, "[onFileSaved] uri = " + uri);
            mHandler.removeMessages(SHOW_SAVING_DIALOG);
        }
    };

    @Override
    public CameraModeType getCameraModeType() {
        return CameraModeType.EXT_MODE_LIVE_PHOTO;
    }

    @Override
    protected void pauseAudioPlayback() {
        // do nothing
    }

    @Override
    protected void backToLastModeIfNeed() {
        Log.i(TAG, "[backToLastModeIfNeed] mNeedBackGroundRecording = "
                + mIsNeedBackGroundRecording + " mCanStartPreviewNow = " + mIsCanStartPreviewNow
                + " mIsNeedStartPreviewAgain = " + mIsNeedStartPreviewAgain + "getModeState() = " + getModeState());
        if ((mIsNeedBackGroundRecording && ModeState.STATE_CLOSED != getModeState() && mIsNeedStartPreviewAgain)
                || !mIsCanStartPreviewNow || mIsSurfaceViewDisplayIdReady) {
            doOnCameraParameterReady(true);
        }
        mIsNeedStartPreviewAgain = true;
    }

    @Override
    protected void updateViewState(boolean hide) {
        if (!hide) {
            mICameraAppUi.restoreViewState();
        }
    }

    @Override
    protected void updateRecordingTime() {
        // do nothing
    }

    @Override
    protected void initVideoRecordingFirst() {
        super.initVideoRecordingFirst();
        updateTimeLapseStatus(false);
        mIsRecordAudio = false;
    }

    @Override
    protected void setOrientationHint(int orientation) {
        Log.i(TAG, "[setOrientationHint] mMediaRecorder = " + mMediaRecorder);
        if (mMediaRecorder != null) {
            mMediaRecorder.setOrientationHint(0);
        }
    }

    @Override
    protected void setSlowMotionVideoFileSpeed(MediaRecorder recorder, int value) {
        // do nothing
    }

    private void onCameraOpenDone() {
        mIsNeedStartPreviewAgain = !isVideoProcessing();
        mIsReleased = false;
    }

    private void doOnFullScreenChanged(boolean full) {
        Log.d(TAG, "[doOnFullScreenChanged] full = " + full + " mIsParameterReady = "
                + mIsParameterReady + " mCanRunLivePhoto = " + mCanRunLivePhoto);
        mIsFullScreen = full;
        if (!mIsParameterReady || !mCanRunLivePhoto ) {
            return;
        }
        if (isVideoProcessing() && mIsMediaRecorderRecording) {
            Log.i(TAG, "[doOnFullScreenChanged] video is processing");
            synchronized (sWaitForVideoProcessing) {
                try {
                    Log.i(TAG, "[doOnFullScreenChanged] Wait for video processing");
                    sWaitForVideoProcessing.wait();
                } catch (InterruptedException e) {
                    Log.w(TAG, "[doOnFullScreenChanged] Got notify from video processing", e);
                }
            }
        }
        if (Storage.getLeftSpace() <= 0) {
            Log.w(TAG,
                    "[doOnFullScreenChanged] when space is not enough, should not start recroding,return!");
            return;
        }
        if (full && !mIsMediaRecorderRecording) {
            Log.i(TAG, "[doOnFullScreenChanged] onFullScreenChanged start video recording.");
            mIsNeedBackGroundRecording = true;
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setModeState(ModeState.STATE_IDLE);
                    startVideoRecording();
                }
            });
        } else if (!full && mIsMediaRecorderRecording) {
            Log.i(TAG, "[doOnFullScreenChanged] onFullScreenChanged stop video recording.");
            mIsNeedBackGroundRecording = false;
            stopVideoRecordingAsync(false);
        }
    }

    private void onPhotoShutterButtonLongPressed() {
        mICameraAppUi.showInfo(mActivity.getString(R.string.livephoto_dialog_title)
                + mActivity.getString(R.string.camera_continuous_not_supported));
    }

    private void takeLivePhoto() {
        Log.i(TAG, "[takeLivePhoto] Photo.onShutterButtonClick mMediaRecorderRecording = "
                + mIsMediaRecorderRecording);
        if (!mIsMediaRecorderRecording || !mIsFullScreen || Storage.getLeftSpace() <= 0) {
            Log.w(TAG, "[takeLivePhoto]invalid state,mMediaRecorderRecording = "
                    + mIsMediaRecorderRecording + ",mFullScreen = " + mIsFullScreen);
            return;
        }
        mICameraAppUi.setSwipeEnabled(false);
        stopVideoRecordingAsync(true);

    }

    private Bitmap createVideoLastFramePicture() {
        Log.i(TAG, "[createVideoLastFramePicture]...");
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(mCurrentVideoFilename);
            mDuration = Long.valueOf(retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            bitmap = retriever.getFrameAtTime((mDuration - 200) * 1000,
                    MediaMetadataRetriever.OPTION_NEXT_SYNC);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
        }
        if (bitmap == null) {
            Log.i(TAG, "[createVideoLastFramePicture]bitmap is null,return!");
            return null;
        }

        return bitmap;
    }

    private String getScenario(int orientation, int width, int height, long duration,
            String inputPath, String outputPath) {
        String fixBitmap = "object1";
        String scenario = "<?xml version=\"1.0\"?>" + "<scenario>" + "   <size orientation= \""
                + orientation
                + "\" owidth=\""
                + width
                + "\" oheight=\""
                + height
                + "\"></size>"
                + "   <video>/system/media/video/gen30.mp4</video>"
                + // we shall has this to make sure the timestamp and to trigger
                  // the scenario
                "   <video>"
                + inputPath
                + "</video>"
                + // the second video to be transcoded
                "   <edge>/system/media/video/edge720p.png</edge>"
                + // the edge frame
                "   <outputvideo livephoto=\"1\">"
                + outputPath
                + "</outputvideo>"
                + // the output file

                "   <videoevent name=\"ve\" type=\"still\" start=\"0\" end=\"1500\">"
                + "   <background>" + fixBitmap + "</background>" + "   </videoevent>" +

                "   <videoevent name=\"ve\" type=\"overlay\" start=\"1500\" end=\"2000\">"
                + "   <showtime related_start=\"0\" length=\"500\"></showtime>"
                + "   <thumbnail move=\"1\">" + fixBitmap + "</thumbnail>"
                + "   <background still=\"1\" fade_in=\"1\">video2</background>"
                + "   </videoevent>" +

                "   <videoevent name=\"ve\" type=\"overlay\" start=\"1900\" end=\""
                + (2000 + duration) + "\">" + "   <showtime related_start=\"100\" length=\""
                + duration + "\"></showtime>" + "   <thumbnail>" + fixBitmap + "</thumbnail>"
                + "   <background>video2</background>" + "   </videoevent>" +

                "   <videoevent name=\"ve\" type=\"overlay\" start=\"" + (2000 + duration)
                + "\" end=\"" + (2300 + duration) + "\">"
                + "   <showtime related_start=\"0\" length=\"300\"></showtime>"
                + "   <thumbnail fade_out=\"1\">" + fixBitmap + "</thumbnail>"
                + "   <background still=\"1\">" + fixBitmap + "</background>" + "   </videoevent>" +

                "   <videoevent name=\"ve\" type=\"still\" start=\"" + (2300 + duration)
                + "\" end=\"" + (2301 + duration) + "\">" + "   <background>" + fixBitmap
                + "</background>" + "   </videoevent>" + "</scenario>";

        return scenario;
    }
}