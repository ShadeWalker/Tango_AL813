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

package com.mediatek.camera.v2.mode.pip;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CaptureRequest.Builder;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.ViewGroup;

import com.android.camera.R;
import com.android.camera.v2.util.Storage;
import com.mediatek.camera.v2.exif.Exif;
import com.mediatek.camera.v2.mode.AbstractCameraMode;
import com.mediatek.camera.v2.mode.pip.combination.FdAffectedRule;
import com.mediatek.camera.v2.mode.pip.combination.PictureSizeRule;
import com.mediatek.camera.v2.mode.pip.combination.PreviewSizeRule;
import com.mediatek.camera.v2.mode.pip.combination.VideoQualityRule;
import com.mediatek.camera.v2.module.ModuleListener;
import com.mediatek.camera.v2.module.ModuleListener.CaptureType;
import com.mediatek.camera.v2.module.ModuleListener.RequestType;
import com.mediatek.camera.v2.platform.ModeChangeListener;
import com.mediatek.camera.v2.platform.app.AppController;
import com.mediatek.camera.v2.platform.app.AppUi;
import com.mediatek.camera.v2.services.SoundClips;
import com.mediatek.camera.v2.services.FileSaver.OnFileSavedListener;
import com.mediatek.camera.v2.setting.ISettingRule;
import com.mediatek.camera.v2.setting.ISettingServant;
import com.mediatek.camera.v2.setting.SettingCtrl;
import com.mediatek.camera.v2.setting.ISettingRule.MappingFinder;
import com.mediatek.camera.v2.setting.ISettingServant.ISettingChangedListener;
import com.mediatek.camera.v2.setting.SettingItem;
import com.mediatek.camera.v2.stream.StreamManager;
import com.mediatek.camera.v2.stream.CaptureStreamController.CaptureStreamCallback;
import com.mediatek.camera.v2.stream.PreviewStreamController.PreviewSurfaceCallback;
import com.mediatek.camera.v2.stream.RecordStream.RecordStreamStatus;
import com.mediatek.camera.v2.stream.pip.PipStreamController;
import com.mediatek.camera.v2.stream.pip.PipStreamView;
import com.mediatek.camera.v2.stream.pip.PipViewController;
import com.mediatek.camera.v2.stream.pip.PipStreamController.PipStreamCallback;
import com.mediatek.camera.v2.stream.pip.PipViewController.PipViewCallback;
import com.mediatek.camera.v2.stream.pip.pipwrapping.PipEGLConfigWrapper;
import com.mediatek.camera.v2.util.SettingKeys;
import com.mediatek.camera.v2.util.Utils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

public class PipMode extends AbstractCameraMode {
    private final String               TAG;
    private final String               mOriginalCameraIdStr;
    
    private PipStreamController        mPipStreamController;
    private ModePipStreamCallback      mPipCallback;
    private PictureSizeRule            mPictureSizeRule;
    private PreviewSizeRule            mPreviewSizeRule;
    private VideoQualityRule           mVideoQualityRule;
    private ISettingRule               mFaceDetectionRule;
    private ISettingRule               mGestureShotRule;
    private ISettingRule               mSmileShotRule;
    
    private PipViewController          mPipViewController;
    
    // preview
    private Surface                    mBottomPreviewSurface;
    private Surface                    mTopPreviewSurface;
    
    // capture / video snap shot
    private Surface                    mBottomCaptureSurface;
    private Surface                    mTopCaptureSurface;
    private CaptureStreamCallback      mCaptureStreamCallback;
    
    // video recording
    private boolean                    mRecording = false;
    private RecordStreamStatus         mRecordStreamCallback;
    private int                        mRecordingRotation;
    private CamcorderProfile           mCameraCamcorderProfile;
    private String                     mVideoTempPath;
    
    // app resources
    private int                         mCurrentOrientation;
    
    private ContentValues               mCapContentValues;
    private ContentValues               mVideoContentValues;
    
    public PipMode(AppController app, ModuleListener moduleListener) {
        super(app, moduleListener);
        TAG = PipMode.class.getSimpleName() + "(" + FEATURE_TAG + ")";
        mVideoQualityRule = new VideoQualityRule(mSettingCtroller);
        mPreviewSizeRule = new PreviewSizeRule(mSettingCtroller);
        mPictureSizeRule = new PictureSizeRule(mSettingCtroller);
        mFaceDetectionRule = new FdAffectedRule(mSettingCtroller, SettingKeys.KEY_CAMERA_FACE_DETECT);
        mGestureShotRule = new FdAffectedRule(mSettingCtroller, SettingKeys.KEY_GESTURE_SHOT);
        mSmileShotRule = new FdAffectedRule(mSettingCtroller, SettingKeys.KEY_SMILE_SHOT);
        
        mSettingServant  = mSettingCtroller.getSettingServant(mSettingCtroller.getCurrentCameraId());
        
        mSettingCtroller.addRule(SettingKeys.KEY_PHOTO_PIP, SettingKeys.KEY_PICTURE_SIZE, mPictureSizeRule);
        mSettingCtroller.addRule(SettingKeys.KEY_PHOTO_PIP, SettingKeys.KEY_PICTURE_RATIO, mPreviewSizeRule);
        mSettingCtroller.addRule(SettingKeys.KEY_PHOTO_PIP, SettingKeys.KEY_VIDEO_QUALITY, mVideoQualityRule);
        mSettingCtroller.addRule(SettingKeys.KEY_PHOTO_PIP, SettingKeys.KEY_CAMERA_FACE_DETECT, mFaceDetectionRule);
        mSettingCtroller.addRule(SettingKeys.KEY_PHOTO_PIP, SettingKeys.KEY_GESTURE_SHOT, mGestureShotRule);
        mSettingCtroller.addRule(SettingKeys.KEY_PHOTO_PIP, SettingKeys.KEY_SMILE_SHOT, mSmileShotRule);
        
        mOriginalCameraIdStr = mSettingServant.getCameraId();
        
        mPipCallback = new ModePipStreamCallback(new Handler(app.getActivity().getMainLooper()));
    }

    @Override
    protected void updateCaredSettingChangedKeys() {
        super.updateCaredSettingChangedKeys();
        addCaredSettingChangedKeys(SettingKeys.KEY_CAMERA_ID);
    }
    
    @Override
    public void onSettingChanged(Map<String, String> result) {
        super.onSettingChanged(result);
        // when camera id changed, update relative setting servant
        if (result.containsKey(SettingKeys.KEY_CAMERA_ID)) {
            String cameraId = result.get(SettingKeys.KEY_CAMERA_ID);
            if (cameraId != null ) {
                Log.i(TAG, "onSettingChanged cameraId new :"+ cameraId);
                mSettingServant.unRegisterSettingChangedListener(this);
                mSettingServant = mSettingCtroller.getSettingServant(cameraId);
                mSettingServant.registerSettingChangedListener(this, mCaredSettingChangedKeys, ISettingChangedListener.MIDDLE_PRIORITY);
            }
        }
    }
    
    @Override
    protected int getModeId() {
        return ModeChangeListener.MODE_PIP;
    }
    
    @Override
    public void open(StreamManager streamManager, ViewGroup parentView,
            boolean isCaptureIntent) {
        Log.i(TAG, "[open]+");
        
        super.open(streamManager, parentView, isCaptureIntent);
        mPipStreamController = streamManager.getPipStreamController();
        mPipStreamController.registerPipStreamCallback(mPipCallback);
        mPipViewController = new PipStreamView(
                mAppController,
                mPipStreamController,
                new PipViewCallbackImpl());
        
        mPipViewController.open();
        mPipStreamController.open(mAppController.getActivity());
        if (!SettingCtrl.BACK_CAMERA.equals(mSettingCtroller.getCurrentCameraId())) {
            mPipStreamController.switchingPip();
        }
        Log.i(TAG, "[open]-");
    }

    @Override
    public void close() {
        Log.i(TAG, "[close]+");
        
        mPipStreamController.close();
        mPipStreamController.unregisterPipStreamCallback(mPipCallback);
        mPipViewController.close();
        super.close();
        if (!mOriginalCameraIdStr.equals(mSettingServant.getCameraId())) {
            mAppUi.performCameraPickerBtnClick();
        }
        Log.i(TAG, "[close]-");
    }

    @Override
    public void resume() {
        Log.i(TAG, "[resume]+");
        
        mPipStreamController.resume();
        super.resume();
        
        Log.i(TAG, "[resume]-");
    }

    @Override
    public void pause() {
        Log.i(TAG, "[pause]+");
        if (mRecording) {
            onShutterClicked(true/**video**/);
        }
        mPipStreamController.pause();
        super.pause();
        mBottomCaptureSurface = null;
        mTopCaptureSurface = null;
        Log.i(TAG, "[pause]-");
    }

    @Override
    public void onOrientationChanged(int orientation) {
        super.onOrientationChanged(orientation);
        mCurrentOrientation = orientation;
        mPipStreamController.onOrientationChanged(orientation);
        mPipViewController.onOrientationChanged(orientation);
    }
    
    @Override
    public void onPreviewVisibilityChanged(int visibility) {
        super.onPreviewVisibilityChanged(visibility);
        Log.i(TAG, "onPreviewVisibilityChanged visibility:" + visibility);
        mPipViewController.onPreviewVisibleChanged(visibility == AppUi.PREVIEW_VISIBILITY_VISIBLE);
    }
    
    @Override
    public void onPreviewAreaChanged(RectF previewArea) {
        Log.i(TAG, "onPreviewAreaChanged previewArea:" + previewArea);
        super.onPreviewAreaChanged(previewArea);
        mPipStreamController.onPreviewAreaChanged(previewArea);
    }
    
    @Override
    public ModeGestureListener getModeGestureListener() {
        return mModeGestureListener;
    }
    
    @Override
    public void onShutterPressed(boolean isVideo) {
        
    }

    @Override
    public void onShutterClicked(boolean isVideo) {
        if (isVideo) {
            videoShutterButtonClicked();
        } else {
            if (mRecording) {
                videoSnapshotShutterButtonClicked();
            } else {
                photoShutterButtonClicked();
            }
        }
    }

    @Override
    public void onShutterLongPressed(boolean isVideo) {
        
    }

    @Override
    public void onShutterReleased(boolean isVideo) {
        
    }

    @Override
    public boolean onBackPressed() {
        if (mRecording) {
            onShutterClicked(true/**video shutter**/);
            return true;
        }
        if (mPipViewController != null) {
            return mPipViewController.onBackPressed();
        }
        return super.onBackPressed();
    }
    
    @Override
    public boolean switchCamera(String cameraId) {
        Log.i(TAG, "switchCamera cameraId:" + cameraId);
        mPipStreamController.switchingPip();
        return true;
    }
    
    @Override
    protected CaptureStreamCallback getCaptureStreamCallback() {
        if (mCaptureStreamCallback == null) {
            mCaptureStreamCallback = new CaptureStreamCallback() {
                @Override
                public void onCaptureCompleted(Image image) {
                    Log.i(TAG, "onCaptureCompleted width: " + image.getWidth() + " height: " + image.getHeight());
                    if (mRecording) {
                        mAppController.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mAppUi.setShutterButtonEnabled(true, false/*photo shutter*/);
                            }
                        });
                    } else {
                        mCameraServices.getSoundPlayback().play(SoundClips.SHUTTER_CLICK);
                        mBottomCaptureSurface = null;
                        mModuleListener.requestChangeSessionOutputs(true/*sync*/,true);
                        mAppController.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mAppUi.setAllCommonViewEnable(true);
                                mAppUi.setSwipeEnabled(true);
                            }
                        });
                    }
                    
                    if (image.getFormat() == PixelFormat.RGBA_8888) {
                        int width = image.getWidth();
                        int height = image.getHeight();
                        byte[] jpegData = null;
                        int orientation = Utils.getJpegRotation(mCurrentOrientation, Utils.getCameraCharacteristics(
                                mAppController.getActivity(), mSettingServant.getCameraId()));
                        try {
                            Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(),
                                    PipEGLConfigWrapper.getInstance().getBitmapConfig());
                            ByteBuffer imageBuffer = null;
                            // get byte buffer from image
                            if ((image.getPlanes()[0].getPixelStride() * image.getWidth()) != image.getPlanes()[0]
                                    .getRowStride()) {
                                Log.i(TAG, "getPixelStride = " + image.getPlanes()[0].getPixelStride()
                                        + " getRowStride = " + image.getPlanes()[0].getRowStride());
                                // buffer is not placed continuously, should remove buffer position again
                                byte[] bytes = Utils.getContinuousRGBADataFromImage(image);
                                imageBuffer = ByteBuffer.allocateDirect(bytes.length);
                                imageBuffer.put(bytes);
                                imageBuffer.rewind();
                                bytes = null;
                            } else {
                                // continuous buffer, read directly
                                imageBuffer = image.getPlanes()[0].getBuffer();
                            }
                            System.gc();
                            // copy buffer to bitmap
                            bitmap.copyPixelsFromBuffer(imageBuffer);
                            imageBuffer.clear();
                            imageBuffer = null;
                            // compress and save it
                            ByteArrayOutputStream bos = null;
                            try {
                                bos = new ByteArrayOutputStream();//new BufferedOutputStream(new FileOutputStream("/sdcard/DCIM/Camera/piptest.png"));
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos);
                                bitmap.recycle();
                                bitmap = null;
                            } finally {
                                jpegData = bos.toByteArray();
                                if (bos != null)
                                    bos.close();
                            }
                            System.gc();
                            image.close();
                            image = null;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (jpegData != null) {
                                updateCaptureContentValues(width, height, orientation);
                                mCameraServices.getMediaSaver().addImage(jpegData, mCapContentValues, 
                                    mMediaSavedListener, mAppController.getActivity().getContentResolver());
                            }
                    }
                }
            };
        }
        return mCaptureStreamCallback;
    }
    
    @Override
    protected RecordStreamStatus getRecordStreamCallback() {
        if (mRecordStreamCallback == null) {
            mRecordStreamCallback = new RecordStreamStatus() {
                @Override
                public void onRecordingStarted() {
                    
                }
                @Override
                public void onRecordingStoped() {
                    Log.i(TAG, "onRecordingStoped");
                    mSettingCtroller.doSettingChange(SettingKeys.KEY_VIDEO_PIP, "off", false);
                    updateVideoContentValues();
                    mCameraServices.getMediaSaver().addVideo(mVideoTempPath, mVideoContentValues, 
                            mMediaSavedListener, mAppController.getActivity().getContentResolver());
                    mVideoTempPath = null;
                    mCameraCamcorderProfile = null;
                }
                @Override
                public void onInfo(int what, int extra) {
                    
                }
                @Override
                public void onError(int what, int extra) {
                    
                }
            };
        }
        return mRecordStreamCallback;
    }
    
    @Override
    protected Size getPreviewSize() {
        Log.i(TAG, "[getPreviewSize]+ mRecording: " + mRecording + 
                " mCameraCamcorderProfile: " + mCameraCamcorderProfile);
        if (mRecording && mCameraCamcorderProfile != null) {
            return new Size(mCameraCamcorderProfile.videoFrameWidth,mCameraCamcorderProfile.videoFrameHeight);
        }
        Size bottomSize = mSettingServant.getPreviewSize();
        
        ISettingServant anotherServant = mSettingCtroller.getSettingServant(getAnotherCameraId());
        Size topSize = anotherServant.getPreviewSize();
        Log.i(TAG, "getPreviewSize bottomSize:" + Utils.buildSize(bottomSize) + 
                " topSize:" + Utils.buildSize(topSize));
        // pip need the same preview size,
        // this is a workaround for when setting is not ready, we get different aspect ratio size.
//        if (!Utils.isSameAspectRatio(bottomSize, topSize)) {
//            return null;
//        }
//        if (Utils.compareSize(bottomSize, topSize)) {
//            return topSize;
//        }
        return bottomSize;
    }
    
    @Override
    protected int getCaptureFormat() {
        return PixelFormat.RGBA_8888;
    }

    @Override
    protected Size getCaptureSize() {
        if (mRecording && mCameraCamcorderProfile != null) {
            return new Size(mCameraCamcorderProfile.videoFrameWidth,
                    mCameraCamcorderProfile.videoFrameHeight);
        }
        Size mainCamCaptureSize = getCameraCaptureSize(SettingCtrl.BACK_CAMERA);
        Size subCamCaptureSize = getCameraCaptureSize(SettingCtrl.FRONT_CAMERA);
        Log.i(TAG, "getCaptureSize bottomSize:" + Utils.buildSize(mainCamCaptureSize) + 
                " subCamCaptureSize:" + Utils.buildSize(subCamCaptureSize));
        if (mainCamCaptureSize == null || subCamCaptureSize == null) {
            return null;
        }
        if (Utils.compareSize(mainCamCaptureSize, subCamCaptureSize)) {
            return mainCamCaptureSize;
        }
        return subCamCaptureSize;
    }

    @Override
    protected boolean changingModePictureSize() {
        super.changingModePictureSize();
        if (mRecording) {
            mPipStreamController.setCaptureSize(getPreviewSize(), getPreviewSize());
            return false;
        } else {
            Size mainCamCaptureSize = getCameraCaptureSize(SettingCtrl.BACK_CAMERA);
            Size subCamCaptureSize = getCameraCaptureSize(SettingCtrl.FRONT_CAMERA);
            mPipStreamController.setCaptureSize(mainCamCaptureSize, subCamCaptureSize);
            return true;
        }
    }

    @Override
    public void configuringSessionOutputs(List<Surface> sessionOutputSurfaces, boolean mainCamera) {
        Log.i(TAG, "[configuringSessionOutputs]+ mainCamera = " + mainCamera);
        waitPreviewSurfaceReady();
        // pip video recording get buffer from preview, 
        // no need to configure recording stream from camera device.
        if (mainCamera) {
            if (mRecording) {
                updatePictureSize();
                updateCaptureSurfaces(true/**update main**/);
            }
            sessionOutputSurfaces.add(mBottomPreviewSurface);
            if (mBottomCaptureSurface != null) {
                sessionOutputSurfaces.add(mBottomCaptureSurface);
            }
        } else {
            updatePictureSize();
            updateCaptureSurfaces(false/**update sub**/);
            sessionOutputSurfaces.add(mTopPreviewSurface);
            if (mTopCaptureSurface != null) {
                sessionOutputSurfaces.add(mTopCaptureSurface);
            }
        }
        Log.i(TAG, "[configuringSessionOutputs]- "
                + " output surface size = " + sessionOutputSurfaces.size());
    }

    @Override
    public void configuringSessionRequests(
            Map<RequestType, Builder> requestBuilders, boolean isMainCamera) {
        Log.i(TAG, "[configuringSessionRequests]+ isMainCamera = " + isMainCamera);
        Set<RequestType> keySet = requestBuilders.keySet();
        Iterator<RequestType> iterator = keySet.iterator();
        while (iterator.hasNext()) {
            RequestType requestType = iterator.next();
            configuringPreviewRequests(requestBuilders.get(requestType), isMainCamera);
            switch (requestType) {
            case STILL_CAPTURE:
                configuringCaptureRequests(requestBuilders.get(requestType), isMainCamera);
                break;
            case VIDEO_SNAP_SHOT:
                configuringCaptureRequests(requestBuilders.get(requestType), isMainCamera);
                configreEisValue(requestBuilders.get(requestType));
                break;
            case RECORDING:
                configreEisValue(requestBuilders.get(requestType));
                break;
            default:
                break;
            }
        }
        Log.i(TAG, "[configuringSessionRequests]- ");
    }

    private Size getCameraCaptureSize(String cameraId) {
        ISettingServant settingServant = mSettingCtroller.getSettingServant(cameraId);
        SettingItem captureSizeItem = settingServant.getSettingItem(SettingKeys.KEY_PICTURE_SIZE);
        if (captureSizeItem == null) {
            return null;
        } else {
            String captureSizeString = captureSizeItem.getValue();
            Log.i(TAG, "getCameraCaptureSize camera id:" + cameraId + " captureSizeString:" + captureSizeString);
            if (captureSizeString != null) {
                return Utils.getSize(captureSizeString);
            } else {
                return null;
            }
        }
    }
    
    private String getAnotherCameraId() {
        String currentCameraId = mSettingServant.getCameraId();
        if (SettingCtrl.BACK_CAMERA.equalsIgnoreCase(currentCameraId)) {
            return SettingCtrl.FRONT_CAMERA;
        }
        return SettingCtrl.BACK_CAMERA;
    }
    
    private void waitPreviewSurfaceReady() {
        Map<String,Surface> pipPreviewSurface = mPreviewController.getPreviewInputSurfaces();
        mBottomPreviewSurface = pipPreviewSurface.get(PipStreamController.BOTTOM_SURFACE_KEY);
        mTopPreviewSurface = pipPreviewSurface.get(PipStreamController.TOP_SURFACE_KEY);
    }

    private void configreEisValue(Builder requestBuilder) {
        String eisValue = mSettingServant.getSettingValue(SettingKeys.KEY_VIDEO_EIS);
        Log.i(TAG, "configuringRecordingRequests eisValue = " + eisValue);
        if ("on".equals(eisValue)) {
            requestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
        } else {
            requestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        }
    }

    private void configuringPreviewRequests(CaptureRequest.Builder requestBuilder, boolean bottomCamera) {
        if (bottomCamera) {
            if (mBottomPreviewSurface != null && mBottomPreviewSurface.isValid()) {
                requestBuilder.addTarget(mBottomPreviewSurface);
            }
        } else {
            if (mTopPreviewSurface != null && mTopPreviewSurface.isValid()) {
                requestBuilder.addTarget(mTopPreviewSurface);
            }
        }
    }

    private void configuringCaptureRequests(CaptureRequest.Builder requestBuilder, boolean bottomCamera) {
        if (bottomCamera) {
            if (mBottomCaptureSurface != null) {
                requestBuilder.addTarget(mBottomCaptureSurface);
            }
        } else {
            if (mTopCaptureSurface != null) {
                requestBuilder.addTarget(mTopCaptureSurface);
            }
        }
        requestBuilder.set(CaptureRequest.JPEG_QUALITY, JPEG_QUALITY);
    }

    private void photoShutterButtonClicked() {
        Log.i(TAG, "photoShutterButtonClicked");
        mAppUi.setAllCommonViewEnable(false);
        mAppUi.setSwipeEnabled(false);
        
        updatePictureSize();
        mModuleListener.requestChangeCaptureRequets(false, false, RequestType.STILL_CAPTURE, CaptureType.CAPTURE);
        
        updateCaptureSurfaces(true);
        mModuleListener.requestChangeSessionOutputs(/*sync = */true,/*main cam = */true);
        mModuleListener.requestChangeCaptureRequets(true, false, RequestType.STILL_CAPTURE, CaptureType.CAPTURE);
    }

    private void videoSnapshotShutterButtonClicked() {
        mAppUi.setShutterButtonEnabled(false, false/*photo shutter*/);
        mModuleListener.requestChangeCaptureRequets(true,RequestType.VIDEO_SNAP_SHOT,CaptureType.CAPTURE);
        mModuleListener.requestChangeCaptureRequets(false,RequestType.VIDEO_SNAP_SHOT,CaptureType.CAPTURE);
    }

    private void videoShutterButtonClicked() {
        Log.i(TAG, "videoShutterButtonClicked");
        if (mRecording) {
            stopRecording();
            mAppUi.stopShowCommonUI(false);
            mAppUi.switchShutterButtonImageResource(R.drawable.btn_video, 
                    true/**video shutter**/);
            mAppUi.setSwipeEnabled(true);
            mAppUi.showModeOptionsUi();
            mAppUi.showSettingUi();
            mAppUi.showIndicatorManagerUi();
            mAppUi.showPickerManagerUi();
            mAppUi.setThumbnailManagerEnable(true);
            mAppUi.setAllCommonViewEnable(true);
            Size pictureSize = getCaptureSize();
            String pictureFormat = pictureSize.getWidth() + "x" + pictureSize.getHeight() + "-superfine";
            mAppUi.showLeftCounts(Utils.getImageSize(pictureFormat), true);
        } else {
            mCameraServices.getSoundPlayback().play(SoundClips.START_VIDEO_RECORDING);
            startRecording();
            mAppUi.stopShowCommonUI(true);
            mAppUi.switchShutterButtonImageResource(R.drawable.btn_video_mask, 
                    true/**video shutter**/);
            mAppUi.setSwipeEnabled(false);
            mAppUi.hideModeOptionsUi();
            mAppUi.hideSettingUi();
            mAppUi.hideIndicatorManagerUi();
            mAppUi.hidePickerManagerUi();
            mAppUi.setThumbnailManagerEnable(false);
            long bytePerMs = ((mCameraCamcorderProfile.videoBitRate + 
                    mCameraCamcorderProfile.audioBitRate) >> 3) / 1000;
            mAppUi.showLeftTime(bytePerMs);
        }
    }

    private void startRecording() {
        Log.i(TAG, "[startRecording]+");
        mRecording = true;
        prepareRecording();
        updatePictureSize();
        updateCaptureSurfaces(true/**update main**/);
        updatePreviewSize(new PreviewSurfaceCallback() {
            @Override
            public void onPreviewSufaceIsReady(boolean surfaceChanged) {
                Log.i(TAG, "startRecording onPreviewSufaceIsReady");
                mModuleListener.requestChangeSessionOutputs(true);
                mSettingCtroller.doSettingChange(SettingKeys.KEY_VIDEO_PIP, "on", false);
                mModuleListener.requestChangeCaptureRequets(true/*sync*/,RequestType.RECORDING, CaptureType.REPEATING_REQUEST);
                mRecordController.startRecord();
                mAppController.enableKeepScreenOn(true);
            }
        });
    }

    private void stopRecording() {
        Log.i(TAG, "[stopRecording]+");
        mRecording = false;
        updatePictureSize();
        updatePreviewSize(new PreviewSurfaceCallback() {
            @Override
            public void onPreviewSufaceIsReady(boolean surfaceChanged) {
                Log.i(TAG, "stopRecording onPreviewSufaceIsReady");
                mBottomCaptureSurface = null;
                mTopCaptureSurface = null;
                mModuleListener.requestChangeSessionOutputs(true);
                mModuleListener.requestChangeCaptureRequets(true/*sync*/,RequestType.PREVIEW, CaptureType.REPEATING_REQUEST);
                try {
                    mRecordController.stopRecord();
                    mAppUi.showSavingProgress(mAppController.getActivity().getResources().getString(R.string.saving));
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    Log.e(TAG, "stopRecording with exception:" + e);
                    if (mVideoTempPath != null) {
                        deleteVideoFile(mVideoTempPath);
                        mVideoTempPath = null;
                    }
                    mCameraCamcorderProfile = null;
                } finally {
                    mCameraServices.getSoundPlayback().play(SoundClips.STOP_VIDEO_RECORDING);
                    mAppController.enableKeepScreenOn(false);
                    Log.i(TAG, "[stopRecording]-");
                }
            }
        });
        mAppController.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mIsCaptureIntent) {
                    //mAppUi.showReviewView(bitmap);
                    mAppUi.switchShutterButtonLayout(R.layout.camera_shutter_ok_cancel_v2);
                }
            }
        });
    }

    private void prepareRecording() {
        int cameraId = Integer.valueOf(mSettingServant.getCameraId());
        int videoQualityValue = Integer.valueOf(mSettingServant.getSettingValue(SettingKeys.KEY_VIDEO_QUALITY));
        mCameraCamcorderProfile = fetchProfile(videoQualityValue, cameraId);
        
        String mirc = mSettingServant.getSettingValue(SettingKeys.KEY_VIDEO_RECORD_AUDIO);
        boolean enableAudio = "on".equals(mirc);
        mRecordingRotation = Utils.getRecordingRotation(mCurrentOrientation, 
                Utils.getCameraCharacteristics(
                        mAppController.getActivity(), mSettingServant.getCameraId()));
        mVideoTempPath = Storage.DIRECTORY + '/' + "videorecorder" + 
                convertOutputFormatToFileExt(mCameraCamcorderProfile.fileFormat) + ".tmp";
        
        Log.i(TAG, "prepareRecording enableAudio = " + enableAudio);
        mRecordController.setRecordingProfile(mCameraCamcorderProfile);
        mRecordController.enalbeAudioRecording(enableAudio);
        mRecordController.setOutputFile(mVideoTempPath);
        mRecordController.setOrientationHint(mRecordingRotation);
        mRecordController.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mRecordController.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mRecordController.prepareRecord();
        
        mRecordController.getRecordInputSurface();
    }

    private void updateCaptureSurfaces(boolean updateMain) {
        Map<String,Surface> pipCaptureSurface = mCaptureController.getCaptureInputSurface();
        if (updateMain) {
            mBottomCaptureSurface = pipCaptureSurface.get(PipStreamController.BOTTOM_SURFACE_KEY);
        } else {
            mTopCaptureSurface = pipCaptureSurface.get(PipStreamController.TOP_SURFACE_KEY);
        }
    }
    
    private CamcorderProfile fetchProfile(int quality, int cameraId) {
        Log.i(TAG, "[fetchProfile](" + quality + ", " + " cameraId = " + cameraId + ")");
        mCameraCamcorderProfile = Utils.getVideoProfile(cameraId, quality);
        if (mCameraCamcorderProfile != null) {
            Log.i(TAG, "[fetchProfile()] mProfile.videoFrameRate=" + mCameraCamcorderProfile.videoFrameRate
                    + ", mProfile.videoFrameWidth=" + mCameraCamcorderProfile.videoFrameWidth
                    + ", mProfile.videoFrameHeight=" + mCameraCamcorderProfile.videoFrameHeight
                    + ", mProfile.audioBitRate=" + mCameraCamcorderProfile.audioBitRate
                    + ", mProfile.videoBitRate=" + mCameraCamcorderProfile.videoBitRate + ", mProfile.quality="
                    + mCameraCamcorderProfile.quality + ", mProfile.duration=" + mCameraCamcorderProfile.duration);
        }
        return mCameraCamcorderProfile;
    }
   
    
    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                mAppController.getActivity().getString(R.string.video_file_name_format));

        return dateFormat.format(date);
    }
    
    private String convertOutputFormatToMimeType(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return "video/mp4";
        }
        return "video/3gpp";
    }
    
    private String convertOutputFormatToFileExt(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            Log.i(TAG, "[convertOutputFormatToFileExt] return .mp4");
            return ".mp4";
        }
        Log.i(TAG, "[convertOutputFormatToFileExt] return .3gp");
        return ".3gp";
    }
    
    private void deleteVideoFile(String fileName) {
        File f = new File(fileName);
        if (!f.delete()) {
            Log.i(TAG, "[deleteVideoFile] Could not delete " + fileName);
        }
    }
    
    /**
     * when file have saved ,need notify Manager
     */
    private OnFileSavedListener mMediaSavedListener = new OnFileSavedListener() {
        @Override
        public void onMediaSaved(Uri uri) {
            Log.i(TAG, "onMediaSaved uri = " + uri);
            // only video saving need to dismiss saving dialog. 
            mAppUi.dismissSavingProgress();
            mAppController.notifyNewMedia(uri);
        }
    };
    
    private void updateCaptureContentValues(int width, int height, int orientation) {
        mCapContentValues = new ContentValues();
        long dateTaken = System.currentTimeMillis();
        String title = Utils.createJpegName(dateTaken);
        
        String filename = title + ".jpg";
        String mime = "image/jpeg";
        String path = Storage.DIRECTORY + '/' + filename;
        String tmpPath = path + ".tmp";
        
        mCapContentValues.put(ImageColumns.DATE_TAKEN, dateTaken);
        mCapContentValues.put(ImageColumns.TITLE, title);
        mCapContentValues.put(ImageColumns.DISPLAY_NAME, filename);
        mCapContentValues.put(ImageColumns.DATA, path);
        mCapContentValues.put(ImageColumns.MIME_TYPE, mime);
        
        mCapContentValues.put(ImageColumns.WIDTH, width);
        mCapContentValues.put(ImageColumns.HEIGHT,height);
        mCapContentValues.put(ImageColumns.ORIENTATION, orientation);

        mLocation = mLocationManager.getCurrentLocation();
        if (mLocation != null) {
            mCapContentValues.put(ImageColumns.LATITUDE, mLocation.getLatitude());
            mCapContentValues.put(ImageColumns.LONGITUDE, mLocation.getLongitude());
        }
        Log.i(TAG, "updateCaptureContentValues orientation: " + orientation);
    }
    
    private void updateVideoContentValues() {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        String filename = title + convertOutputFormatToFileExt(mCameraCamcorderProfile.fileFormat);
        String mime = convertOutputFormatToMimeType(mCameraCamcorderProfile.fileFormat);
        String path = Storage.DIRECTORY + '/' + filename;
        
        mVideoContentValues = new ContentValues();
        mVideoContentValues.put(Video.Media.TITLE, title);
        mVideoContentValues.put(Video.Media.DISPLAY_NAME, filename);
        mVideoContentValues.put(Video.Media.DATE_TAKEN, dateTaken);
        mVideoContentValues.put(MediaColumns.DATE_MODIFIED, dateTaken / 1000);
        mVideoContentValues.put(Video.Media.MIME_TYPE, mime);
        mVideoContentValues.put(Video.Media.DATA, path);
        mVideoContentValues.put(Video.Media.WIDTH, mCameraCamcorderProfile.videoFrameWidth);
        mVideoContentValues.put(Video.Media.HEIGHT, mCameraCamcorderProfile.videoFrameHeight);
        mVideoContentValues.put(Video.Media.ORIENTATION, mRecordingRotation);
        mVideoContentValues.put(Video.Media.RESOLUTION, 
                Integer.toString(mCameraCamcorderProfile.videoFrameWidth) + "x"
                + Integer.toString(mCameraCamcorderProfile.videoFrameHeight));
        mVideoContentValues.put(Video.Media.SIZE, new File(mVideoTempPath).length());

        mLocation = mLocationManager.getCurrentLocation();
        if (mLocation != null) {
            mVideoContentValues.put(ImageColumns.LATITUDE,
                    mLocation.getLatitude());
            mVideoContentValues.put(ImageColumns.LONGITUDE,
                    mLocation.getLongitude());
        }
    }
    
    private ModeGestureListener mModeGestureListener = new ModeGestureListener() {
        @Override
        public boolean onUp() {
            if (mPipStreamController != null) {
                return mPipStreamController.onUp();
            }
            return false;
        }
        
        @Override
        public boolean onSingleTapUp(float x, float y) {
            if (mPipStreamController != null && mPipViewController != null && !mPipViewController.onTouchEvent()) {
                return mPipStreamController.onSingleTapUp(x, y);
            }
            return false;
        }
        
        @Override
        public boolean onScroll(float dx, float dy, float totalX, float totalY) {
            if (mPipStreamController != null) {
                return mPipStreamController.onScroll(dx, dy, totalX, totalY);
            }
            return false;
        }
        
        @Override
        public boolean onLongPress(float x, float y) {
            if (mPipStreamController != null) {
                return mPipStreamController.onLongPress(x, y);
            }
            return false;
        }
        
        @Override
        public boolean onDown(float x, float y) {
            if (mPipStreamController != null) {
                return mPipStreamController.onDown(x, y);
            }
            return false;
        }
    };
    
    private class PipViewCallbackImpl implements PipViewCallback {
        @Override
        public void onTemplateChanged(int rearResId, int frontResId,
                int highlightResId, int editBtnResId) {
            mPipStreamController.onTemplateChanged(rearResId, frontResId, highlightResId, editBtnResId);
        }
    }
    private class ModePipStreamCallback implements PipStreamCallback {
        private final Handler mHandler;
        public ModePipStreamCallback(Handler handler) {
            mHandler = handler;
        }
        
        @Override
        public void onOpened() {
            
        }
        
        @Override
        public void onClosed() {
            
        }
        
        @Override
        public void onPaused() {
            
        }
        
        @Override
        public void onResumed() {
            
        }
        
        @Override
        public void onTopGraphicTouched() {
            
        }
        
        @Override
        public void onSwitchPipEventReceived() {
            mHandler.removeCallbacks(mCamPickerBtnClieckRunnale);
            mHandler.post(mCamPickerBtnClieckRunnale);
        }
        
        private Runnable mCamPickerBtnClieckRunnale = new Runnable() {
            
            @Override
            public void run() {
                mAppUi.performCameraPickerBtnClick();
            }
            
        };
    }
}