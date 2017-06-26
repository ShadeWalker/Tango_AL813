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
package com.mediatek.camera.mode.pip.pipwrapping;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import com.android.camera.Util;
import com.mediatek.camera.mode.pip.pipwrapping.PIPOperator.PIPCustomization;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Managers SurfaceTextures, creates SurfaceTexture and TexureRender Objects,
 * and do SW Sync that ensure two SurfaceTextures are sync.
 */
public class RendererManager {

    private static final String   TAG = "RendererManager";
    private Activity              mActivity;
    private static final int      EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    
    /**********************common part********************/
    private EGLConfig             mEglConfig;
    private EGLDisplay            mEglDisplay;
    private EGLContext            mEglContext;
    private EGLSurface            mEglSurface;
    private EGL10                 mEgl;
    
    private Object                mRenderLock = new Object();
    private ConditionVariable     mEglThreadBlockVar = new ConditionVariable();
    private HandlerThread         mEglThread;
    private EglHandler            mEglHandler;
    private static HandlerThread  sFrameListener = new HandlerThread("PIP-STFrameListener");
    private Handler               mSurfaceTextureHandler;

    private int                   mFboTexId = -12345;
    private ScreenRenderer        mScreenRenderer; // for preview
    private BottomGraphicRenderer mBottomGraphicRenderer;
    private TopGraphicRenderer    mTopGraphicRenderer;
    private int                   mCurrentOrientation; // this orientation is for g-sensor
    /**********************preview textures********************/
    private int[]                 mPreviewTextures;
    private FrameBuffer           mPreviewFrameBuffer;
    private int                   mPreviewBottomTexId = -12345;
    private int                   mPreviewTopTexId = -12345;
    private SurfaceTexture        mPreviewBottomSurfaceTexture;
    private SurfaceTexture        mPreviewTopSurfaceTexture;
    private int                   mPreviewTexWidth = -1; // bottom/top use the same preview texture size
    private int                   mPreviewTexHeight = -1;
    private boolean               isBottomHasHighFrameRate = true;
    private boolean               isFirstSetUpTexture = false;
    
    /**********************picture textures********************/
    private int                   mPictureFboTexId = -12345;
    private CaptureRenderer       mCaptureRenderer; // for capture and video snap shot
    private Surface               mCaptureSurface;
    private int[]                 mPictureTextures;
    private FrameBuffer           mPictureFrameBuffer;
    private int                   mPictureBottomTexId = -12345;
    private int                   mPictureTopTexId = -12345;
    private Bitmap                mPictureBottomData;
    private int                   mPictureBottomTexWidth;
    private int                   mPictureBottomTexHeight;
    private boolean               mPictureBottomNeedRelaod = false;
    private Bitmap                mPictureTopData;
    private int                   mPictureTopTexWidth;
    private int                   mPictureTopTexHeight;
    private boolean               mPictureTopNeedRelaod = false;
    
    /**********************top graphic animation********************/
    private AnimationRect         mPreviewTopGraphicRect = null;
    private AnimationRect         mPictureTopGraphicRect = null;
    
    /**********************video recording*************************/
    private RecorderRenderer      mRecorderRenderer; // for video recording
    private Surface               mRecordingSurface;
    private Object mObject = new Object();
    
    // effect resource id
    /**********************pip effect template resource id********************/
    private int mBackTempResource = 0;
    private int mFrontTempResource = 0;
    private int mFrontHighlightTempResource = 0;
    private int mEditButtonRecourdId = 0;
    // switch pip
    private boolean mPIPSwitched = false; // false: main camera in bottom
                                            // graphic and sub camera in top
                                            // graphic

    public RendererManager(Activity activity) {
        mActivity = activity;
    }

    /**
     * new a handler thread and create EGL Context in it.
     * We called this thread to "PIP GL Thread".
     * <p>
     * Note: if "PIP GL Thread" exits, skip initialization
     */
    public void init() {
        Log.i(TAG, "init");
        if (mEglHandler == null) {
            initializePreviewRendererThread();
        }
        mScreenRenderer = new ScreenRenderer(mActivity);
        // init screen renderer
        initSubRenderer();
    }

    /**
     * release surface textures, related renderers and "PIP GL Thread"
     */
    public void unInit() {
        Log.i(TAG, "unInit");
        synchronized (mRenderLock) {
            if (mEglHandler != null) {
                // remove all previous messages and resume mEglThreadBlockVar
                mEglHandler.removeCallbacksAndMessages(null);
                mEglThreadBlockVar.open();
                mEglHandler.sendMessageSync(EglHandler.MSG_RELEASE);
            }
            mRenderLock.notifyAll();
        }
    }

    /**
     * update pip template resource.
     * Note: if resource id is the same with previous, call this function has no use.
     * @param backResourceId bottom graphic template
     * @param frontResourceId top graphic template
     * @param effectFrontHighlightId top graphic highlight template
     * @param editButtonResourceId top graphic edit template
     */
    public void updateEffectTemplates(int backResourceId, int frontResourceId, 
            int effectFrontHighlightId, int editButtonResourceId) {
        Log.i(TAG, "updateEffectTemplates");
        if ((mBackTempResource) == backResourceId && (mFrontTempResource == frontResourceId)
                && (mFrontHighlightTempResource == effectFrontHighlightId)) {
            Log.i(TAG, "no need to update effect");
            return;
        }
        // when switch pip template quickly, skip pending update template
        // and do not block ui thread.
        if (mEglHandler != null) {
            mBackTempResource = backResourceId;
            mFrontTempResource = frontResourceId;
            mFrontHighlightTempResource = effectFrontHighlightId;
            mEditButtonRecourdId = editButtonResourceId;
            mEglHandler.removeMessages(EglHandler.MSG_UPDATE_TEMPLATE);
            mEglHandler.obtainMessage(EglHandler.MSG_UPDATE_TEMPLATE).sendToTarget();
        }
    }

    /**
     * Set pip preview texture's size
     * <p>
     * Note: pip bottom and top texture's size must be the same for switch pip
     * @param width bottom/top texture's width
     * @param height bottom/top texture's height
     */
    public void setPreviewTextureSize(int width, int height) {
        Log.i(TAG, "setPreviewTextureSize width = " + width 
                + " height = " + height);
        if (mPreviewTexWidth == width && mPreviewTexHeight == height) {
            Log.i(TAG, "setPreviewTextureSize same size set, ignore!");
            return;
        }
        if (mEglHandler != null) {
            mEglHandler.sendMessageSync(EglHandler.MSG_UPDATE_RENDERER_SIZE, width, height, null);
        }
    }

    /**
     * create two surface textures, switch pip by needSwitchPIP
     * <p>
     * By default, bottom surface texture is drawn in bottom graphic.
     * top surface texture is drawn in top graphic.
     */
    public void setUpSurfaceTextures() {
        boolean needUpdate = false;
        // press home key exit pip and resume again, template update action will not happen
        // here call update template for this case.
        // update template should not block ui thread
        if (mEglHandler != null && !mEglHandler.hasMessages(EglHandler.MSG_UPDATE_TEMPLATE)) {
            needUpdate = true;
        }
        setupPIPTextures();
        if (needUpdate && mEglHandler != null) {
            mEglHandler.obtainMessage(EglHandler.MSG_UPDATE_TEMPLATE).sendToTarget();
        }
    }

    /**
     * Set preview surface to receive pip preview buffer
     * Note: this must be called after setPreviewTextureSize
     * @param surface
     */
    public void setPreviewSurface(Surface surface) {
        updatePreviewSurface(surface);
    }
    
    /**
     * Set preview surface to receive pip preview buffer
     * Note: this must be called after setPreviewTextureSize
     * @param surface
     */
    public void notifySurfaceViewDestroyed(Surface surface) {
        if (mScreenRenderer != null) {
            mScreenRenderer.notifySurfaceStatus(surface);
        }
    }

    /**
     * Get bottom surface texture
     * @return bottom graphic surface texture
     */
    public SurfaceTexture getBottomSurfaceTexture() {
        Log.i(TAG, "getBottomSurfaceTexture mIsPIPSwitched = " + mPIPSwitched);
        return mPIPSwitched ? _getTopSurfaceTexture() : _getBottomSurfaceTexture();
    }

    /**
     * Get top surface texture
     * @return top graphic surface texture
     */
    public SurfaceTexture getTopSurfaceTexture() {
        Log.i(TAG, "getTopSurfaceTexture mIsPIPSwitched = " + mPIPSwitched);
        return mPIPSwitched ? _getBottomSurfaceTexture() : _getTopSurfaceTexture();
    }

    /**
     * update top graphic's position
     * @param topGraphic
     */
    public void updateTopGraphic(AnimationRect topGraphic) {
        mPreviewTopGraphicRect = topGraphic;
    }
    
    /**
     * when G-sensor's orientation changed, should update it to PIPOperator
     * @param newOrientation G-sensor's new orientation
     */
    public void updateGSensorOrientation(int newOrientation) {
        Log.i(TAG, "updateOrientation newOrientation = " + newOrientation);
        mCurrentOrientation = newOrientation;
    }

    /**
     * Get the preview surface texture's width
     * @return preview surface texture's width
     */
    public int getPreviewTextureWidth() {
        return mPreviewTexWidth;
    }

    /**
     * Get the preview surface texture's height
     * @return preview surface texture's height
     */
    public int getPreviewTextureHeight() {
        return mPreviewTexHeight;
    }
    
    /**
     * Set recording surface to receive pip buffer
     * Note: this must be called after setPreviewTextureSize
     * @param surface
     */
    public void setRecordingSurface(Surface surface) {
        mRecordingSurface = surface;
        updateRecordingSurface();
    }

    public TopGraphicRenderer getTopGraphicRenderer() {
        return mTopGraphicRenderer;
    }
    
    public ScreenRenderer getScreenRenderer() {
        return mScreenRenderer;
    }

    public void prepareRecording() {
        if (mRecorderRenderer == null && mEglHandler != null) {
            mEglHandler.removeMessages(EglHandler.MSG_SETUP_VIDEO_RENDER);
            mEglHandler.sendMessageSync(EglHandler.MSG_SETUP_VIDEO_RENDER);
            synchronized (mRenderLock) {
                if (mRecorderRenderer == null) {
                    try {
                        mRenderLock.wait();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "unexpected interruption");
                    }
                }
            }
        }
    }

    public void startRecording() {
        if (mRecorderRenderer != null) {
            mRecorderRenderer.startRecording();
        }
    }
    
    public void pauseRecording() {
        if (mRecorderRenderer != null) {
            mRecorderRenderer.pauseVideoRecording();
        }
    }
    
    public void resumeRecording() {
        if (mRecorderRenderer != null) {
            mRecorderRenderer.resumeVideoRecording();
        }
    }
    
    public void stopRecording() {
        if (mRecorderRenderer != null && mEglHandler != null) {
            mRecorderRenderer.stopRecording();
            mEglHandler.removeMessages(EglHandler.MSG_RELEASE_VIDEO_RENDER);
            mEglHandler.sendMessageSync(EglHandler.MSG_RELEASE_VIDEO_RENDER);
        }
    }
    
    public void switchPIP() {
        Log.i(TAG, "switchPIP");
        if (mEglHandler != null && mPreviewTopSurfaceTexture != null && mPreviewBottomSurfaceTexture != null) {
            mEglHandler.sendMessageSync(EglHandler.MSG_SWITCH_PIP);
        }
    }

    /**
     * Take a video snap shot by orientation
     * @param orientation video snap shot orientation
     */
    public void takeVideoSnapShot(int orientation,boolean isBackBottom) {
        Log.i(TAG, " orientation = " + orientation);
        if (orientation % 180 == 0) {
            // width < height
            mPictureTopNeedRelaod = (mPictureTopTexWidth != mPreviewTexWidth)
                    || (mPictureTopTexHeight != mPreviewTexHeight);
            mPictureBottomNeedRelaod = (mPictureBottomTexWidth != mPreviewTexWidth)
                    || (mPictureBottomTexHeight != mPreviewTexHeight);
            mPictureTopTexWidth = mPreviewTexWidth;
            mPictureTopTexHeight = mPreviewTexHeight;
            mPictureBottomTexWidth = mPreviewTexWidth;
            mPictureBottomTexHeight = mPreviewTexHeight;
        } else {
            // width > height
            mPictureTopNeedRelaod = (mPictureTopTexWidth != mPreviewTexHeight)
                    || (mPictureTopTexHeight != mPreviewTexWidth);
            mPictureBottomNeedRelaod = (mPictureBottomTexWidth != mPreviewTexHeight)
                    || (mPictureBottomTexHeight != mPreviewTexWidth);
            mPictureTopTexWidth = mPreviewTexHeight;
            mPictureTopTexHeight = mPreviewTexWidth;
            mPictureBottomTexWidth =  mPreviewTexHeight;
            mPictureBottomTexHeight = mPreviewTexWidth;
        }
        synchronized (mRenderLock) {
            if (mEglHandler != null) {
                mEglHandler.removeMessages(EglHandler.MSG_TAKE_VSS);
                mEglHandler.sendMessageSync(EglHandler.MSG_TAKE_VSS, orientation,0,isBackBottom);
            }
        }
    }

    /**
     * Set picture surface ,this must be set before take picture
     * @param surface a surface used to receive pip picture buffer
     */
    public void setCaptureSurface (Surface surface) {
        mCaptureSurface = surface;
    }

    public void takePicture(Bitmap bottomData, Bitmap topData, int bottomWidth, int bottomHeight,
            int topWidth, int topHeight, int topOrientation, AnimationRect captureTopGraphicRect) {
        synchronized (mRenderLock) {
            Log.i(TAG, "takePicture bottomData = " + bottomData + " bottomWidth = " + bottomWidth
                    + " bottomHeight = " + bottomHeight + " topData = " + topData + " topWidth = "
                    + topWidth + " topHeight = " + topHeight);
            if (bottomData == null || topData == null || bottomWidth <= 0 || bottomHeight <= 0
                    || topWidth <= 0 || topHeight <= 0 || captureTopGraphicRect == null || mEglHandler == null) {
                return;
            }
            mPictureTopGraphicRect = captureTopGraphicRect;
            mPictureBottomNeedRelaod = (mPictureBottomTexWidth != bottomWidth)
                    || (mPictureBottomTexHeight != bottomHeight) || isFirstSetUpTexture;
            mPictureBottomData = Bitmap.createBitmap(bottomData);
            mPictureBottomTexWidth = bottomWidth;
            mPictureBottomTexHeight = bottomHeight;
            
            mPictureTopNeedRelaod = (mPictureTopTexWidth != topWidth)
                    || (mPictureTopTexHeight != topHeight) || isFirstSetUpTexture;
            isFirstSetUpTexture = false;
            mPictureTopData = Bitmap.createBitmap(topData);
            mPictureTopTexWidth = topWidth;
            mPictureTopTexHeight = topHeight;
            if (mPictureTopTexWidth == mPictureTopData.getWidth()) {
                // if top graphic jpeg is rotated, there is no need rotate in GPU
                topOrientation = 0;
            }
            if (mEglHandler != null) {
                mEglHandler.sendMessageSync(EglHandler.MSG_TAKE_PICTURE, topOrientation);
            }
        }
    }
    
    public void recycleBitmap() {
        synchronized (mObject) {
            if (mPictureBottomData != null && !mPictureBottomData.isRecycled()) {
                mPictureBottomData.recycle();
                mPictureBottomData = null;
            }
            if (mPictureTopData != null && !mPictureTopData.isRecycled()) {
                mPictureTopData.recycle();
                mPictureTopData = null;
            }
        }
    }
    

    private EglHandler initializePreviewRendererThread() {
        synchronized (mRenderLock) {
            mEglThread = new HandlerThread("PIP-PreviewRealtimeRenderer");
            mEglThread.start();
            Looper looper = mEglThread.getLooper();
            if (looper == null) {
                throw new RuntimeException("why looper is null?");
            }
            mEglHandler = new EglHandler(looper);
            initialize();
        }
        return mEglHandler;
    }

    private void initSubRenderer() {
        if (mEglHandler != null) {
            mEglHandler.sendMessageSync(EglHandler.MSG_INIT_SUB_RENDERER);
        }
    }

    private void setupPIPTextures() {
        Log.i(TAG, "setupPIPTextures");
        if (mEglHandler != null) {
            // here should not remove frame message, must consume all frames
            // otherwise frame will not come to ap if previous frames are not consumed.
            mEglHandler.sendMessageSync(EglHandler.MSG_SETUP_PIP_TEXTURES);
            isFirstSetUpTexture = true;
        }
    }
 
    private void updatePreviewSurface(Surface surface) {
        if (mEglHandler != null && surface != null) {
            mEglHandler.sendMessageSync(EglHandler.MSG_SET_PREVIEW_SURFACE, surface);
        }
    }

    private void updateRecordingSurface() {
        if (mEglHandler != null) {
            mEglHandler.sendMessageSync(EglHandler.MSG_SET_RECORDING_SURFACE);
        }
    }

    private SurfaceTexture _getBottomSurfaceTexture() {
        synchronized (mRenderLock) {
            if (mPreviewBottomSurfaceTexture == null && mEglHandler != null) {
                try {
                    mRenderLock.wait();
                } catch (InterruptedException e) {
                    Log.w(TAG, "unexpected interruption");
                }
            }
            Log.i(TAG, "_getBottomSurfaceTexture mPreviewBottomSurfaceTexture = " + mPreviewBottomSurfaceTexture
                    +" mEglHandler = " + mEglHandler);
            return mPreviewBottomSurfaceTexture;
        }
    }

    private SurfaceTexture _getTopSurfaceTexture() {
        synchronized (mRenderLock) {
            if (mPreviewTopSurfaceTexture == null && mEglHandler != null) {
                try {
                    mRenderLock.wait();
                } catch (InterruptedException e) {
                    Log.w(TAG, "unexpected interruption");
                }
            }
            Log.i(TAG, "_getTopSurfaceTexture mPreviewTopSurfaceTexture = " + mPreviewTopSurfaceTexture
                    +" mEglHandler = " + mEglHandler);
            return mPreviewTopSurfaceTexture;
        }
    }

    private void releaseTopSurfaceTexture() {
        if (mPreviewTopSurfaceTexture != null) {
            mPreviewTopSurfaceTexture.setOnFrameAvailableListener(null);
            mPreviewTopSurfaceTexture.release();
            mPreviewTopSurfaceTexture = null;
        }
        mPreviewTexWidth = -1;
        mPreviewTexHeight = -1;
    }

    private void releaseBottomSurfaceTexture() {
        if (mPreviewBottomSurfaceTexture != null && mEglHandler != null) {
            mPreviewBottomSurfaceTexture.setOnFrameAvailableListener(null);
            mPreviewBottomSurfaceTexture.release();
        }
        mPreviewBottomSurfaceTexture = null;
        mPreviewTexWidth = -1;
        mPreviewTexWidth = -1;
    }


    private class EglHandler extends Handler{
        public static final int MSG_SETUP_PIP_TEXTURES = 0;
        public static final int MSG_NEW_BOTTOM_FRAME_ARRIVED = 1;
        public static final int MSG_NEW_TOP_FRAME_ARRIVED = 2;
        public static final int MSG_TAKE_PICTURE = 3;
        public static final int MSG_TAKE_VSS = 4;
        public static final int MSG_SETUP_VIDEO_RENDER = 5;
        public static final int MSG_RELEASE_VIDEO_RENDER = 6;
        public static final int MSG_UPDATE_TEMPLATE = 7;
        public static final int MSG_SWITCH_PIP = 8;
        public static final int MSG_RELEASE_PIP_TEXTURES = 9;
        public static final int MSG_RELEASE = 10;
        public static final int MSG_INIT_SUB_RENDERER = 11;
        public static final int MSG_UPDATE_RENDERER_SIZE = 12;
        public static final int MSG_SET_PREVIEW_SURFACE = 13;
        public static final int MSG_SET_RECORDING_SURFACE = 14;
        private float[] mBottomTransformMatrix = new float[16];
        private float[] mTopCamTransformMatrix = new float[16];
        private long mLatestBottomCamTimeStamp = 0l;
        private long mLatestTopCamTimeStamp = 0l;
        private long mBottomCamTimeStamp = 0l;
        private long mTopCamTimeStamp = 0l;
        
        // M: debug info for draw preview.
        private static final int INTERVALS = 300;
        private int mBottomDrawFrameCount = 0;
        private long mBottomDrawStartTime = 0;
        private SurfaceTexture.OnFrameAvailableListener mBottomCamFrameAvailableListener = new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//                Log.i(TAG, "Main Camera onFrameAvailable surfaceTexture = " + surfaceTexture);
                sendEmptyMessage(EglHandler.MSG_NEW_BOTTOM_FRAME_ARRIVED);
                // debug preview frame rate
                mBottomDrawFrameCount++;
                if (mBottomDrawFrameCount % INTERVALS == 0) {
                    long currentTime = System.currentTimeMillis();
                    int intervals = (int) (currentTime - mBottomDrawStartTime);
                    Log.i(TAG, "[Camera-->AP][Bottom][Preview] Drawing frame, fps = "
                            + (mBottomDrawFrameCount * 1000.0f) / intervals + " in last "
                            + intervals + " millisecond.");
                    mBottomDrawStartTime = currentTime;
                    mBottomDrawFrameCount = 0;
                }
            }
        };
        
        private int mTopDrawFrameCount = 0;
        private long mTopDrawStartTime = 0;
        private SurfaceTexture.OnFrameAvailableListener mTopCamFrameAvailableListener = new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//                Log.i(TAG, "Sub Camera onFrameAvailable surfaceTexture = " + surfaceTexture);
                sendEmptyMessage(EglHandler.MSG_NEW_TOP_FRAME_ARRIVED);
                // debug preview frame rate
                mTopDrawFrameCount++;
                if (mTopDrawFrameCount % INTERVALS == 0) {
                    long currentTime = System.currentTimeMillis();
                    int intervals = (int) (currentTime - mTopDrawStartTime);
                    Log.i(TAG, "[Camera-->AP][Top][Preview] Drawing frame, fps = "
                            + (mTopDrawFrameCount * 1000.0f) / intervals + " in last " + intervals
                            + " millisecond.");
                    mTopDrawStartTime = currentTime;
                    mTopDrawFrameCount = 0;
                }
            }
        };
        
        public EglHandler(Looper looper) {
            super(looper);
        }
        
        @Override
        public void handleMessage(Message msg) {
//            Log.i(TAG, "handleMessage msg.what = " + msg.what);
            switch (msg.what) {
            case MSG_INIT_SUB_RENDERER:
                doInitSubRenderer();
                mEglThreadBlockVar.open();
                break;
            case MSG_UPDATE_RENDERER_SIZE:
                mPreviewTexWidth = (Integer)msg.arg1;
                mPreviewTexHeight = (Integer)msg.arg2;
                doUpdateRenderSize();
                mEglThreadBlockVar.open();
                break;
            case MSG_SETUP_PIP_TEXTURES:
                doSetupPIPTextures();
                mEglThreadBlockVar.open();
                break;
            case MSG_SET_PREVIEW_SURFACE:
                doUpdatePreviewSurface((Surface)msg.obj);
                mEglThreadBlockVar.open();
                break;
            case MSG_SET_RECORDING_SURFACE:
                doUpdateRecordingSurface();
                mEglThreadBlockVar.open();
                break;
            case MSG_NEW_BOTTOM_FRAME_ARRIVED:
                doUpdateBottomCamTimeStamp();
                draw();
                mEglThreadBlockVar.open();
                break;
            case MSG_NEW_TOP_FRAME_ARRIVED:
                doUpdateTopCamTimeStamp();
                draw();
                mEglThreadBlockVar.open();
                break;
            case MSG_TAKE_PICTURE:
                doTakePicture((Integer) msg.obj);
                mEglThreadBlockVar.open();
                break;
            case MSG_TAKE_VSS:
                doVideoSnapShot((Integer) msg.arg1,(Boolean) msg.obj );
                mEglThreadBlockVar.open();
                break;
            case MSG_SETUP_VIDEO_RENDER:
                doSetUpRenderForTakeVideo();
                mEglThreadBlockVar.open();
                break;
            case MSG_RELEASE_VIDEO_RENDER:
                doReleaseRenderForTakeVideo();
                mEglThreadBlockVar.open();
                break;
            case MSG_UPDATE_TEMPLATE:
                doUpdateTemplate();
                break;
            case MSG_SWITCH_PIP:
                doSwitchPIP();
                mEglThreadBlockVar.open();
                break;
            case MSG_RELEASE:
                doRelease();
                mEglThreadBlockVar.open();
                break;
            }
        }

        private void doInitSubRenderer() {
            // initialize screen renderer and related sub renderer
            mScreenRenderer.init();
        }

        private void doSetupPIPTextures() {
            Log.i(TAG, "doInitiSurfaceTextures mPreviewFrameBuffer = " + mPreviewFrameBuffer);
            resetTimeStamp();
            synchronized (mRenderLock) {
                if (mPreviewFrameBuffer == null) {
                    // start frame available thread
                    if (!sFrameListener.isAlive()) {
                        sFrameListener.start();
                    }
                    mSurfaceTextureHandler = new Handler(sFrameListener.getLooper());
                    // generate and bind 2 textures for preview
                    mPreviewTextures = new int[2];
                    mPreviewTextures = GLUtil.generateTextureIds(2);
                    mPreviewBottomTexId = mPreviewTextures[0];
                    GLUtil.bindPreviewTexure(mPreviewBottomTexId);
                    mPreviewTopTexId = mPreviewTextures[1];
                    GLUtil.bindPreviewTexure(mPreviewTopTexId);
                    // initialize bottom surface texture
                    mPreviewBottomSurfaceTexture = new SurfaceTexture(mPreviewBottomTexId);
                    mPreviewBottomSurfaceTexture.setOnFrameAvailableListener(
                            mBottomCamFrameAvailableListener, mSurfaceTextureHandler);
                    // initialize top surface texture
                    mPreviewTopSurfaceTexture = new SurfaceTexture(mPreviewTopTexId);
                    mPreviewTopSurfaceTexture.setOnFrameAvailableListener(
                            mTopCamFrameAvailableListener, mSurfaceTextureHandler);
                    // initialize preview frame buffer
                    mPreviewFrameBuffer = new FrameBuffer();
                    mFboTexId = mPreviewFrameBuffer.getFboTexId();
                    // initialize bottom graphic renderer
                    mBottomGraphicRenderer = new BottomGraphicRenderer(mActivity);
                    // initialize top graphic renderer
                    mTopGraphicRenderer = new TopGraphicRenderer(mActivity);
                    // in pip mode press home key to exit camera, and enter
                    // again should restore pip state
                    if (mPIPSwitched) {
                        doSwitchTextures();
                    }
                    // initialize capture
                    mCaptureRenderer = new CaptureRenderer(mActivity);
                    mCaptureRenderer.init();
                }
                isBottomHasHighFrameRate =  Util.isBottomHasHighFrameRate(mActivity);
                mRenderLock.notifyAll();
            }
        }

        private void doUpdateRenderSize() {
            Log.i(TAG, "doUpdateRenderSize mPreviewTexWidth = " + 
                    mPreviewTexWidth + " mPreviewTexHeight = " + mPreviewTexHeight);
            if (mPreviewFrameBuffer != null) {
                mPreviewFrameBuffer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight);
            }
            if (mBottomGraphicRenderer != null) {
                mBottomGraphicRenderer.setRendererSize(mPreviewTexWidth,
                        mPreviewTexHeight, false);
            }
            if (mTopGraphicRenderer != null) {
                mTopGraphicRenderer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight);
            }
            if (mScreenRenderer != null) {
                mScreenRenderer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight);
            }
            if (mCaptureRenderer != null) {
                mCaptureRenderer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight);
            }
            if (mRecorderRenderer != null) {
                mRecorderRenderer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight);
            }
        }

        private void doUpdatePreviewSurface(Surface surface) {
            if (mScreenRenderer != null) {
                mScreenRenderer.setSurface(surface, true, true);
            }
        }
        
        private void doReleasePIPTexturesAndRenderers() {
            Log.i(TAG, "doReleasePIPSurfaceTextures");
            _doReleasePIPTextures();
            releasePIPRenderers();
        }
        
        private void setUpTexturesForPicture(boolean isVSS, boolean needReloadBottomTex,
                boolean needReloadTopTex) {
            Log.d(TAG, "setUpTexturesForPicture needReloadBottomTex = " + needReloadBottomTex
                    + " needReloadTopTex = " + needReloadTopTex);
            // generate and bind 2 textures for picture
            if (!isVSS && mPictureTextures == null) {
                mPictureTextures = new int[2];
                mPictureTextures = GLUtil.generateTextureIds(2);
                mPictureBottomTexId = mPictureTextures[0];
                mPictureTopTexId = mPictureTextures[1];
                Log.d(TAG, "mPictureBottomTexId = " + mPictureBottomTexId + ", mPictureTopTexId = " + mPictureTopTexId);
            }
            // reload bottom texture for new size
            if (needReloadBottomTex && mPictureBottomTexId > 0) {
                GLUtil.bindTexture(mPictureBottomTexId);
                if (PipEGLConfigWrapper.getInstance().getPixelFormat() == PixelFormat.RGBA_8888) {
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                            mPictureBottomTexWidth, mPictureBottomTexHeight, 0, GLES20.GL_RGBA,
                            GLES20.GL_UNSIGNED_BYTE, null);
                } else if (PipEGLConfigWrapper.getInstance().getPixelFormat() == PixelFormat.RGB_565) {
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB,
                            mPictureBottomTexWidth, mPictureBottomTexHeight, 0, GLES20.GL_RGB,
                            GLES20.GL_UNSIGNED_SHORT_5_6_5, null);
                }
            }
            // reload top texture for new size
            if (needReloadTopTex && mPictureTopTexId > 0) {
                GLUtil.bindTexture(mPictureTopTexId);
                if (PipEGLConfigWrapper.getInstance().getPixelFormat() == PixelFormat.RGBA_8888) {
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mPictureTopTexWidth,
                            mPictureTopTexHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                } else if (PipEGLConfigWrapper.getInstance().getPixelFormat() == PixelFormat.RGB_565) {
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, mPictureTopTexWidth,
                            mPictureTopTexHeight, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, null);
                }
            }
            // initialize picture frame buffer , video snap shot and take
            // picture need it
            if (mPictureFrameBuffer == null) {
                mPictureFrameBuffer = new FrameBuffer();
                mPictureFboTexId = mPictureFrameBuffer.getFboTexId();
            }
        }
        
        private void releaseTexturesForPicture() {
            // delete picture textures
            if (mPictureTextures != null) {
                GLUtil.deleteTextures(mPictureTextures);
                mPictureTextures = null;
                mPictureBottomTexId = -12345;
                mPictureTopTexId = -12345;
            }
            // release frame buffer
            if (mPictureFrameBuffer != null) {
                mPictureFrameBuffer.release();
                mPictureFrameBuffer = null;
                mPictureFboTexId = -12345;
            }
        }

        private void doSetUpRenderForTakeVideo() {
            synchronized (mRenderLock) {
                mRecorderRenderer = new RecorderRenderer(mActivity);
                mRecorderRenderer.init();
                mRenderLock.notifyAll();
            }
        }

        private void doUpdateRecordingSurface() {
            synchronized (mRenderLock) {
                mRecorderRenderer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight);
                mRecorderRenderer.setRecrodingSurface(mRecordingSurface);
                mRenderLock.notifyAll();
            }
        }

        private void doReleaseRenderForTakeVideo() {
            if (mRecorderRenderer != null) {
                mRecorderRenderer.releaseSurface();
                mRecorderRenderer = null;
            }
        }

        private void releasePIPRenderers() {
            Log.i(TAG, "releasePIPRenderers");
            releaseTexturesForPicture();
            mPictureBottomTexWidth = 0;
            mPictureBottomTexHeight = 0;
            mPictureBottomNeedRelaod = false;
            mPictureTopTexWidth = 0;
            mPictureTopTexHeight = 0;
            mPictureTopNeedRelaod = false;
            mBottomGraphicRenderer = null;
            if (mTopGraphicRenderer != null) {
                mTopGraphicRenderer.release();
                mTopGraphicRenderer = null;
            }
            if (mCaptureRenderer != null) {
                mCaptureRenderer.release();
                mCaptureRenderer = null;
            }
            if (mRecorderRenderer != null) {
                mRecorderRenderer.releaseSurface();
                mRecorderRenderer = null;
            }
            // disconnect buffer queue
            if (mScreenRenderer != null) {
                mScreenRenderer.release();
                mScreenRenderer = null;
            }
        }

        private void doVideoSnapShot(int orientation,boolean isBackBottom) {
            Log.i(TAG, "doVideoSnapShot mPictureBottomTexWidth = " + mPictureBottomTexWidth
                    + " mPictureBottomTexHeight = " + mPictureBottomTexHeight
                    + " mPictureTopTexWidth = " + mPictureTopTexWidth
                    + " mPictureTopTexHeight = " + mPictureTopTexHeight
                    + " orientation = " + orientation + "isBackBottom = " + isBackBottom);
            if (mPictureBottomTexWidth <= 0 || mPictureBottomTexHeight <= 0) {
                return;
            }
            // initialize capture
            setUpTexturesForPicture(true, mPictureBottomNeedRelaod, mPictureTopNeedRelaod);
            // set to picture render size
            mPictureFrameBuffer.setRendererSize(mPictureBottomTexWidth, mPictureBottomTexHeight);
            mBottomGraphicRenderer.setRendererSize(mPictureBottomTexWidth, mPictureBottomTexHeight,false);
            mTopGraphicRenderer.setRendererSize(mPictureBottomTexWidth, mPictureBottomTexHeight);
            mCaptureRenderer.setRendererSize(mPictureBottomTexWidth, mPictureBottomTexHeight);
            mCaptureRenderer.setCaptureSurface(mCaptureSurface);
            // draw to FBO
            mPictureFrameBuffer.setupFrameBufferGraphics(mPictureBottomTexWidth,
                    mPictureBottomTexHeight);
            float[] texRotateMtxByOrientation = GLUtil.createIdentityMtx();
            // landscape should rotate preview texture, because preview texture is portrait
            if (orientation % 180 != 0) {
                boolean bottomIsMainCamera = isBackBottom;
                android.opengl.Matrix.translateM(texRotateMtxByOrientation, 0,
                        texRotateMtxByOrientation, 0, .5f, .5f, 0);
                android.opengl.Matrix.rotateM(texRotateMtxByOrientation, 0,
                        bottomIsMainCamera ? -orientation : orientation, 0, 0, 1);
                android.opengl.Matrix.translateM(texRotateMtxByOrientation, 0, -.5f, -.5f, 0);
            }
            AnimationRect vssTopGraphicRect = mPreviewTopGraphicRect.copy();
            vssTopGraphicRect.changeCooridnateSystem(mPictureBottomTexWidth, mPictureBottomTexHeight, 360-orientation);
            mBottomGraphicRenderer.draw(mPreviewBottomTexId, mBottomTransformMatrix,
                    texRotateMtxByOrientation, false);
            mTopGraphicRenderer.draw(mPreviewTopTexId, mTopCamTransformMatrix,
                    texRotateMtxByOrientation, vssTopGraphicRect, 
                    (orientation % 180 != 0) ? 180 : 0,
                    false);
            mPictureFrameBuffer.setScreenBufferGraphics();
            // draw to take VSS
            if (mCaptureRenderer != null) {
                mCaptureRenderer.draw(mPictureFboTexId);
            }
            // back to preview render size
            mBottomGraphicRenderer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight,false);
            mCaptureRenderer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight);
            mTopGraphicRenderer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight);
        }
        
        private void doTakePicture(int topGrapihcRotateOrientation) {
            Log.i(TAG, "doTakePicture mPictureBottomData = " + mPictureBottomData
                    + " mPictureTopData = " + mPictureTopData);
            synchronized(mObject) {
                if (mPictureBottomData == null || mPictureTopData == null) {
                    return;
                }
                setUpTexturesForPicture(false, mPictureBottomNeedRelaod, mPictureTopNeedRelaod);
                // put bottom picture to GPU
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mPictureBottomTexId);
                // reuse bottom texture memory
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, mPictureBottomData);
                // mPictureBottomData will be reuse in
                // ImageReaderWrapper.onImageAvailable,
                // it will be recycled and gc in here
                // put top picture to GPU
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mPictureTopTexId);
                // reuse bottom texture memory
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, mPictureTopData);
                if (mPictureTopData != null) {
                    Log.i(TAG, "mPictureTopData recycle!!!!!!");
                    mPictureTopData.recycle();
                    mPictureTopData = null;
                    System.gc();
                }
            }
            // set to picture render size
            mPictureFrameBuffer.setRendererSize(mPictureBottomTexWidth, mPictureBottomTexHeight);
            mBottomGraphicRenderer.setRendererSize(mPictureBottomTexWidth, mPictureBottomTexHeight,true);
            mCaptureRenderer.setRendererSize(mPictureBottomTexWidth, mPictureBottomTexHeight);
            mCaptureRenderer.setCaptureSurface(mCaptureSurface);
            mTopGraphicRenderer.setRendererSize(mPictureBottomTexWidth, mPictureBottomTexHeight);
            // draw to FBO
            mPictureFrameBuffer.setupFrameBufferGraphics(mPictureBottomTexWidth,mPictureBottomTexHeight);
            float[] texRotateMtxByOrientation = GLUtil.createIdentityMtx();
            // landscape should rotate picture texture, because picture texture is portrait
            if (topGrapihcRotateOrientation % 180 != 0) {
                android.opengl.Matrix.translateM(texRotateMtxByOrientation, 0,
                        texRotateMtxByOrientation, 0, .5f, .5f, 0);
                android.opengl.Matrix.rotateM(texRotateMtxByOrientation, 0,
                        -topGrapihcRotateOrientation, 0, 0, 1);
                android.opengl.Matrix.translateM(texRotateMtxByOrientation, 0, -.5f, -.5f, 0);
            }
            // sub camera may need mirror
            boolean bottomIsMainCamera = Util.bottomGraphicIsMainCamera(mActivity);
            boolean bottomNeedMirror = (!bottomIsMainCamera)
                    && PIPCustomization.SUB_CAMERA_NEED_HORIZONTAL_FLIP;
            boolean topNeedMirror = bottomIsMainCamera
                    && PIPCustomization.SUB_CAMERA_NEED_HORIZONTAL_FLIP;
            mBottomGraphicRenderer.draw(mPictureBottomTexId, null, GLUtil.createIdentityMtx(),bottomNeedMirror);
            mTopGraphicRenderer.draw(mPictureTopTexId, null, texRotateMtxByOrientation,mPictureTopGraphicRect, -1, topNeedMirror);
            mPictureFrameBuffer.setScreenBufferGraphics();
            // draw to take picture
            if (mCaptureRenderer != null) {
                mCaptureRenderer.draw(mPictureFboTexId);
            }
            // back to preview render size
            mBottomGraphicRenderer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight,false);
            mTopGraphicRenderer.setRendererSize(mPreviewTexWidth, mPreviewTexHeight);
            mPictureTopGraphicRect = null;
            Log.i(TAG, "doTakePicture end");
        }

        private void doUpdateTemplate() {
            Log.i(TAG, "doUpdateTemplate backResourceId = " + mBackTempResource
                    + " frontResourceId = " + mFrontTempResource + " fronthighlight = "
                    + mFrontHighlightTempResource);
            if (mTopGraphicRenderer != null) {
                mTopGraphicRenderer.initTemplateTexture(mBackTempResource, mFrontTempResource);
            }
            if (mScreenRenderer != null) {
                mScreenRenderer.updateScreenEffectTemplate(mFrontHighlightTempResource, mEditButtonRecourdId);
            }
            Log.i(TAG, "doUpdateTemplate end");
        }

        private void doSwitchPIP() {
            synchronized (mRenderLock) {
                // switch pip textures
                doSwitchTextures();
                mPIPSwitched = !mPIPSwitched;
            }
        }

        private void doSwitchTextures() {
            // switch texture id, we no need to switch surface texture
            int texId = mPreviewBottomTexId;
            mPreviewBottomTexId = mPreviewTopTexId;
            mPreviewTopTexId = texId;
            // switch matrix
            float[] matrix = mTopCamTransformMatrix;
            mTopCamTransformMatrix = mBottomTransformMatrix;
            mBottomTransformMatrix = matrix;
        }

        private void doRelease() {
            Log.i(TAG, "doRelease");
            // release surface textures
            releaseTopSurfaceTexture();
            releaseBottomSurfaceTexture();
            // release pip textures and renderers
            doReleasePIPTexturesAndRenderers();
            // release EGL context
            release();
            // release thread
            Looper looper = Looper.myLooper();
            if (looper != null) {
                looper.quit();
            }
            mEglHandler = null;
        }

        private void _doReleasePIPTextures() {
            Log.i(TAG, "_doReleasePIPTextures");
            if (mPreviewFrameBuffer != null) {
                mPreviewFrameBuffer.release();
                mPreviewFrameBuffer = null;
                mFboTexId = -12345;
            }
            if (mPreviewTextures != null) {
                GLUtil.deleteTextures(mPreviewTextures);
                mPreviewTextures = null;
                mPreviewBottomTexId = -12345;
                mPreviewTopTexId = -12345;
            }
            // sFrameListener.stop();
        }
        
        private boolean doTimestampSync() {
            Log.i(TAG, "doTimestampSync");
            if (mBottomCamTimeStamp == 0l && mPreviewBottomSurfaceTexture != null) {
                mPreviewBottomSurfaceTexture.updateTexImage();
                mBottomCamTimeStamp = mPreviewBottomSurfaceTexture.getTimestamp();
                mPreviewBottomSurfaceTexture.getTransformMatrix(mBottomTransformMatrix);
            }
            if (mPreviewTopSurfaceTexture == null) {
                mTopCamTimeStamp = mBottomCamTimeStamp;
            } else if (mTopCamTimeStamp == 0l) {
                mPreviewTopSurfaceTexture.updateTexImage();
                mTopCamTimeStamp = mPreviewTopSurfaceTexture.getTimestamp();
                mPreviewTopSurfaceTexture.getTransformMatrix(mTopCamTransformMatrix);
            }
            if (mTopCamTimeStamp != 0l && mBottomCamTimeStamp != 0l) {
                mTopCamTimeStamp = 0l;
                mBottomCamTimeStamp = 0l;
                return true;
            }
            return false;
        }
        
        private void doUpdateTopCamTimeStamp() {
//            Log.v(TAG, "doUpdateTopCamTimeStamp");
            if (mPreviewTopSurfaceTexture == null) {
                return;
            }
            mPreviewTopSurfaceTexture.updateTexImage();
            mLatestTopCamTimeStamp = mPreviewTopSurfaceTexture.getTimestamp();
            mPreviewTopSurfaceTexture.getTransformMatrix(mPIPSwitched ? mBottomTransformMatrix
                    : mTopCamTransformMatrix);
        }
        
        private void doUpdateBottomCamTimeStamp() {
//            Log.v(TAG, "doUpdateBottomCamTimeStamp");
            if (mPreviewBottomSurfaceTexture == null) {
                return;
            }
            mPreviewBottomSurfaceTexture.updateTexImage();
            mLatestBottomCamTimeStamp = mPreviewBottomSurfaceTexture.getTimestamp();
            mPreviewBottomSurfaceTexture.getTransformMatrix(mPIPSwitched ? mTopCamTransformMatrix
                    : mBottomTransformMatrix);
        }
        
        private void resetTimeStamp() {
            Log.i(TAG, "resetPipStatus");
            mBottomCamTimeStamp = 0l;
            mLatestBottomCamTimeStamp = 0l;
            mTopCamTimeStamp = 0l;
            mLatestTopCamTimeStamp = 0;
        }
        
        private boolean _doTimestampSync() {
//            Log.v(TAG, "_doTimestampSync mLatestBottomCamTimeStamp = " + mLatestBottomCamTimeStamp
//                    + " mBottomCamTimeStamp = " + mBottomCamTimeStamp
//                    + " mLatestTopCamTimeStamp = " + mLatestTopCamTimeStamp);
            // non-pip mode
            if (mPreviewTopSurfaceTexture == null) {
                return true;
            }
            // pip mode, sync first frame
            if (mLatestBottomCamTimeStamp == 0l || mLatestTopCamTimeStamp == 0l) {
                return false;
            }
            // after sync first frame, when high fps's surface texture new frame
            // arrives, draw the frame
            // bottom is the high frame
            if (isBottomHasHighFrameRate && (mBottomCamTimeStamp != mLatestBottomCamTimeStamp)) {
                mBottomCamTimeStamp = mLatestBottomCamTimeStamp;
                mTopCamTimeStamp = mLatestTopCamTimeStamp;
                return true;
            }
            // top is the high frame
            if (!isBottomHasHighFrameRate && (mTopCamTimeStamp != mLatestTopCamTimeStamp)) {
                mBottomCamTimeStamp = mLatestBottomCamTimeStamp;
                mTopCamTimeStamp = mLatestTopCamTimeStamp;
                return true;
            }
            
            return false;
        }
        
        private int mDrawDrawFrameCount = 0;
        private long mDrawDrawStartTime = 0;
        
        private void draw() {
            if (_doTimestampSync()) {
//                Log.i(TAG, "draw");
                long time = System.currentTimeMillis();
                // draw to FBO
                mPreviewFrameBuffer.setupFrameBufferGraphics(mPreviewTexWidth,
                        mPreviewTexHeight);
                mBottomGraphicRenderer.draw(mPreviewBottomTexId, mBottomTransformMatrix,
                        GLUtil.createIdentityMtx(), false);
                if (mTopGraphicRenderer != null) {
                    mTopGraphicRenderer.draw(mPreviewTopTexId, mTopCamTransformMatrix,
                            GLUtil.createIdentityMtx(), mPreviewTopGraphicRect.copy(),
                            mCurrentOrientation, false);
                }
                mPreviewFrameBuffer.setScreenBufferGraphics();
                // draw preview buffer to MediaCodec's Surface
                if (mRecorderRenderer != null) {
                    mRecorderRenderer.draw(mFboTexId, mPreviewBottomSurfaceTexture.getTimestamp());
                }
                // draw to screen
                mScreenRenderer.draw(mTopGraphicRenderer == null ? null : mPreviewTopGraphicRect.copy(),
                        mFboTexId, mPreviewTopGraphicRect.getHighLightStatus());
                // preview frame rate
                mDrawDrawFrameCount++;
                if (mDrawDrawFrameCount % INTERVALS == 0) {
                    long currentTime = System.currentTimeMillis();
                    int intervals = (int) (currentTime - mDrawDrawStartTime);
                    Log.i(TAG, "[AP-->Wrapping][Preview] Drawing frame, fps = "
                            + (mDrawDrawFrameCount * 1000.0f) / intervals + " in last " + intervals
                            + " millisecond.");
                    mDrawDrawStartTime = currentTime;
                    mDrawDrawFrameCount = 0;
                }
//                Log.i(TAG, "draw end");
            }
        }
        
        // Should be called from other thread.
        public void sendMessageSync(int msg) {
            mEglThreadBlockVar.close();
            sendEmptyMessage(msg);
            mEglThreadBlockVar.block();
        }

        public void sendMessageSync(int msg, int arg1) {
            mEglThreadBlockVar.close();
            obtainMessage(msg,arg1).sendToTarget();
            mEglThreadBlockVar.block();
        }

        public void sendMessageSync(int msg, int arg1,int arg2 ,Object obj) {
            mEglThreadBlockVar.close();
            obtainMessage(msg,arg1,arg2,obj).sendToTarget();
            mEglThreadBlockVar.block();
        }
        public void sendMessageSync(int msg, Object obj) {
            mEglThreadBlockVar.close();
            obtainMessage(msg, obj).sendToTarget();
            mEglThreadBlockVar.block();
        }
    }

    /**********************GL environment related**********************************************/
    private void initialize() {
        mEglHandler.post(new Runnable() {
            @Override
            public void run() {
                mEgl = (EGL10) EGLContext.getEGL();
                mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
                if (mEglDisplay == EGL10.EGL_NO_DISPLAY) {
                    throw new RuntimeException("eglGetDisplay failed");
                }
                int[] version = new int[2];
                if (!mEgl.eglInitialize(mEglDisplay, version)) {
                    throw new RuntimeException("eglInitialize failed");
                } else {
                    Log.v(TAG, "<initialize> EGL version: " + version[0] + '.' + version[1]);
                }
                int[] attribList = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
                mEglConfig = PipEGLConfigWrapper.getInstance().getEGLConfigChooser().chooseConfig(mEgl, mEglDisplay);
                mEglContext = mEgl.eglCreateContext(mEglDisplay, mEglConfig, EGL10.EGL_NO_CONTEXT,
                        attribList);
                if (mEglContext == null || mEglContext == EGL10.EGL_NO_CONTEXT) {
                    throw new RuntimeException("failed to createContext");
                } else {
                    Log.v(TAG, "<initialize> EGL context: create success");
                }
                mEglSurface = mEgl.eglCreatePbufferSurface(mEglDisplay, mEglConfig, null);
                if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) {
                    Log.i(TAG, "createWindowSurface error eglError = " + mEgl.eglGetError());
                    throw new RuntimeException("failed to createWindowSurface mEglSurface = "
                            + mEglSurface + " EGL_NO_SURFACE = " + EGL10.EGL_NO_SURFACE);
                } else {
                    Log.v(TAG, "<initialize> EGL surface: create success");
                }
                if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
                    throw new RuntimeException("failed to eglMakeCurrent");
                } else {
                    Log.v(TAG, "<initialize> EGL make current: success");
                }
            }
        });
    }

    // this method must be called in GL Thread
    private void release() {
        Log.i(TAG, "<release> begin");
        mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
        mEgl.eglDestroyContext(mEglDisplay, mEglContext);
        mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT);
        mEgl.eglTerminate(mEglDisplay);
        mEglSurface = null;
        mEglContext = null;
        mEglDisplay = null;
        Log.i(TAG, "<release> end");
    }
}
