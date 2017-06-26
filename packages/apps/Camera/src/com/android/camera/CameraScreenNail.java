/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.Matrix;
import android.os.Trace;

import com.android.camera.manager.MMProfileManager;
import com.android.camera.R;

import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.GLPaint;
import com.android.gallery3d.glrenderer.NinePatchTexture;
import com.android.gallery3d.glrenderer.RawTexture;
import com.android.gallery3d.ui.SurfaceTextureScreenNail;

/*
 * This is a ScreenNail which can displays camera preview.
 */
public class CameraScreenNail extends SurfaceTextureScreenNail {
    private static final String TAG = "CameraScreenNail";
    private static final int ANIM_NONE = 0;
    // Capture animation is about to start.
    private static final int ANIM_CAPTURE_START = 1;
    // Capture animation is running.
    private static final int ANIM_CAPTURE_RUNNING = 2;
    // Switch camera animation needs to copy texture.
    private static final int ANIM_SWITCH_COPY_TEXTURE = 3;
    // Switch camera animation shows the initial feedback by darkening the
    // preview.
    private static final int ANIM_SWITCH_DARK_PREVIEW = 4;
    // Switch camera animation is waiting for the first frame.
    private static final int ANIM_SWITCH_WAITING_FIRST_FRAME = 5;
    // Switch camera animation is about to start.
    private static final int ANIM_SWITCH_START = 6;
    // Switch camera animation is running.
    private static final int ANIM_SWITCH_RUNNING = 7;
    // M:Add for launch camera trace profile
    private static final int END_TRACE = 2;
    
    private boolean mVisible;
    // True if first onFrameAvailable has been called. If screen nail is drawn
    // too early, it will be all white.
    private boolean mFirstFrameArrived;
    // M:Add for launch camera trace profile
    private int mLaunchCameraTrace = 0;
    private Listener mListener;
    private final float[] mTextureTransformMatrix = new float[16];
    private PreviewEffectListener mPreviewEffectListener;
    
    // Animation.
    private CaptureAnimManager mCaptureAnimManager;
    private SwitchAnimManager mSwitchAnimManager = new SwitchAnimManager();
    private int mAnimState = ANIM_NONE;
    private RawTexture mAnimTexture;
    // Some methods are called by GL thread and some are called by main thread.
    // This protects mAnimState, mVisible, and surface texture. This also makes
    // sure some code are atomic. For example, requestRender and setting
    // mAnimState.
    private Object mLock = new Object();
    private boolean mDrawable = true;
    private boolean mLayoutChanged = true;
    private int mX;
    private int mY;
    // when do switch actor animation keep last actor's preview size
    private int mWidth;
    private int mHeight;
    // if layout not changed, hold the size and set it when layout changed
    private int mHoldScreenNailWidth;
    private int mHoldScreenNailHeight;
    // keep the preview size
    private int mOldWidth;
    private int mOldHeight;
    // add for full screen preview, current preview render size
    private int mRenderWidth;
    private int mRenderHeight;
    private float mScaleX = 1f, mScaleY = 1f;
    private float mLastScaleX = 1f, mLastScaleY = 1f;
    private boolean mEnableAspectRatioClamping = false;
    
    // Add for MAV New UI
    private NinePatchTexture mCenterTexture;
    
    private RawTexture mOriginSizeTexture;
    private int mSwitchActorState = ANIM_SIZE_CHANGE_NONE;
    private static final int ANIM_SIZE_CHANGE_NONE = 0;
    private static final int ANIM_SIZE_CHANGE_START = 1;
    private static final int ANIM_SIZE_CHANGE_RUNNING = 2;
    private boolean mAcquireTexture = false;
    private CameraActivity mContext;
    private final DrawClient mDefaultDraw = new DrawClient() {
        @Override
        public void onDraw(GLCanvas canvas, int x, int y, int width, int height) {
            CameraScreenNail.super.draw(canvas, x, y, width, height);
        }
        
        @Override
        public boolean requiresSurfaceTexture() {
            return true;
        }
        
        @Override
        public RawTexture copyToTexture(GLCanvas c, RawTexture texture, int w, int h) {
            // We shouldn't be here since requireSurfaceTexture() returns true.
            return null;
        }
    };
    private DrawClient mDraw = mDefaultDraw;
    
    public void setPreviewEffectListener(PreviewEffectListener previewEffectListener) {
        synchronized (mLock) {
            if (previewEffectListener == null) {
                if (mPreviewEffectListener != null) {
                    mPreviewEffectListener.setPreviewListenerNull();
                }
                if (mCenterTexture != null) {
                    mCenterTexture.recycle();
                    mCenterTexture = null;
                }
            } else {
                mCenterTexture = new NinePatchTexture(mContext, R.drawable.mask);
            }
            mPreviewEffectListener = previewEffectListener;
        }
    }
    
    public interface Listener {
        void requestRender();
        
        void onPreviewTextureCopied();
        
        void restoreSwitchCameraState();
        
        void onStateChanged(int state);
        
        void onPreviewSizeChanged(int width, int height);
        
        void onFirstFrameArrived();
    }
    
    public interface PreviewEffectListener {
        void addPreviewEffect(GLCanvas canvas, int x, int y, int width, int height,
                NinePatchTexture centerTexture);
        
        void setPreviewListenerNull();
    }
    
    public interface DrawClient {
        void onDraw(GLCanvas canvas, int x, int y, int width, int height);
        
        boolean requiresSurfaceTexture();
        
        // The client should implement this if requiresSurfaceTexture() is
        // false;
        RawTexture copyToTexture(GLCanvas c, RawTexture texture, int width, int height);
    }
    
    public CameraScreenNail(Listener listener, Context ctx) {
        mContext = (CameraActivity) ctx;
        mListener = listener;
        mCaptureAnimManager = new CaptureAnimManager(ctx);
    }
    
    @Override
    public void acquireSurfaceTexture() {
        synchronized (mLock) {
            mFirstFrameArrived = false;
            super.acquireSurfaceTexture();
            mAnimTexture = new RawTexture(getTextureWidth(), getTextureHeight(), true);
            mOriginSizeTexture = new RawTexture(getTextureWidth(), getTextureHeight(), true);
        }
    }
    
    @Override
    public void releaseSurfaceTexture(boolean needReleaseExtTexture) {
        Log.i(TAG, "releaseSurfaceTexture");
        synchronized (mLock) {
            if (mAcquireTexture) {
                mAcquireTexture = false;
                mLock.notifyAll();
            } else {
                if (super.getSurfaceTexture() != null) {
                    super.releaseSurfaceTexture(needReleaseExtTexture);
                }
                mAnimState = ANIM_NONE; // stop the animation
            }
        }
    }
    
    public void copyTexture() {
        synchronized (mLock) {
            mListener.requestRender();
            mAnimState = ANIM_SWITCH_COPY_TEXTURE;
        }
    }
    
    public void copyOriginSizeTexture() {
        Log.i(TAG, "copyOriginSizeTexture mFirstFrameArrived = " + mFirstFrameArrived);
        synchronized (mLock) {
            // run copyOriginSizeTexture is a new start,
            // mSwitchActorState whatever it is should be set
            // ANIM_SIZE_CHANGE_START.
            if (mFirstFrameArrived) {
                mListener.requestRender();
                if (mSwitchActorState != ANIM_SIZE_CHANGE_NONE) {
                    mSwitchActorState = ANIM_SIZE_CHANGE_START;
                }
            }
        }
    }
    
    public void stopSwitchActorAnimation() {
        Log.i(TAG, "stopSwitchActorAnimation");
        synchronized (mLock) {
            if (mSwitchActorState != ANIM_SIZE_CHANGE_NONE) {
                mSwitchActorState = ANIM_SIZE_CHANGE_NONE;
                mListener.requestRender();
            }
        }
    }
    
    public void animateSwitchCamera() {
        Log.d(TAG, "animateSwitchCamera");
        MMProfileManager.startProfileAnimateSwitchCamera();
        synchronized (mLock) {
            if (mAnimState == ANIM_SWITCH_DARK_PREVIEW) {
                // Do not request render here because camera has been just
                // started. We do not want to draw black frames.
                mAnimState = ANIM_SWITCH_WAITING_FIRST_FRAME;
            }
        }
    }
    
    public void animateCapture(int animOrientation) {
        MMProfileManager.startProfileAnimateCapture();
        synchronized (mLock) {
            mCaptureAnimManager.setOrientation(animOrientation);
            mCaptureAnimManager.animateFlashAndSlide();
            mListener.requestRender();
            mAnimState = ANIM_CAPTURE_START;
        }
    }
    
    public void animateFlash(int displayRotation) {
        synchronized (mLock) {
            mCaptureAnimManager.setOrientation(displayRotation);
            mCaptureAnimManager.animateFlash();
            mListener.requestRender();
            mAnimState = ANIM_CAPTURE_START;
        }
    }
    
    public void animateSlide() {
        synchronized (mLock) {
            mCaptureAnimManager.animateSlide();
            mListener.requestRender();
        }
    }
    
    public RawTexture getAnimationTexture() {
        return mAnimTexture;
    }
    
    public void directDraw(GLCanvas canvas, int x, int y, int width, int height) {
        if (mSwitchActorState == ANIM_SIZE_CHANGE_RUNNING) {
            mOriginSizeTexture.draw(canvas, x, y, width, height);
        } else {
            DrawClient draw;
            synchronized (mLock) {
                draw = mDraw;
            }
            draw.onDraw(canvas, x, y, width, height);
            synchronized (mLock) {
                if (mPreviewEffectListener != null) {
                    mPreviewEffectListener.addPreviewEffect(canvas, x, y, width, height,
                            mCenterTexture);
                }
            }
        }
    }
    
    public void setDraw(DrawClient draw) {
        synchronized (mLock) {
            if (draw == null) {
                mDraw = mDefaultDraw;
            } else {
                mDraw = draw;
            }
        }
        mListener.requestRender();
    }
    
    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        MMProfileManager.startProfileDrawScreenNail();
        if (mOldWidth != width || mOldHeight != height) {
            if (mListener != null) {
                Log.i(TAG, "onPreviewSizeChanged, width:" + width + ", height:" + height);
                mListener.onPreviewSizeChanged(width, height);
                mOldWidth = width;
                mOldHeight = height;
            }
        }
        
        synchronized (mLock) {
            if (!mVisible) {
                mVisible = true;
            }
            SurfaceTexture surfaceTexture = getSurfaceTexture();
            if (mDraw.requiresSurfaceTexture() && (surfaceTexture == null || !mFirstFrameArrived)) {
                MMProfileManager.stopProfileDrawScreenNail();
                return;
            }
            if (mAnimState == ANIM_NONE && mSwitchActorState == ANIM_SIZE_CHANGE_NONE) {
                MMProfileManager.triggerSuperDrawNoAnimate();
                directDraw(canvas, x, y, width, height);
                MMProfileManager.stopProfileDrawScreenNail();
                if (mListener != null) {
                    mListener.onStateChanged(ANIM_SIZE_CHANGE_NONE);
                }
                return;
            }
            switch (mAnimState) {
            case ANIM_SWITCH_COPY_TEXTURE:
                copyPreviewTexture(canvas);
                mSwitchAnimManager.setReviewDrawingSize(width, height);
                mListener.onPreviewTextureCopied();
                mAnimState = ANIM_SWITCH_DARK_PREVIEW;
                // The texture is ready. Fall through to draw darkened
                // preview.
            case ANIM_SWITCH_DARK_PREVIEW:
            case ANIM_SWITCH_WAITING_FIRST_FRAME:
                // Consume the frame. If the buffers are full,
                // onFrameAvailable will not be called. Animation state
                // relies on onFrameAvailable.
                surfaceTexture.updateTexImage();
                mSwitchAnimManager.drawDarkPreview(canvas, x, y, width, height, mAnimTexture);
                return;
            case ANIM_SWITCH_START:
                mSwitchAnimManager.startAnimation();
                mAnimState = ANIM_SWITCH_RUNNING;
                break;
            case ANIM_CAPTURE_START:
                copyPreviewTexture(canvas);
                mCaptureAnimManager.startAnimation();
                mAnimState = ANIM_CAPTURE_RUNNING;
                break;
            }
            if (mAnimState == ANIM_CAPTURE_RUNNING || mAnimState == ANIM_SWITCH_RUNNING) {
                boolean drawn;
                if (mAnimState == ANIM_CAPTURE_RUNNING) {
                    drawn = mCaptureAnimManager.drawAnimation(canvas, this, mAnimTexture, x, y,
                            width, height);
                } else {
                    drawn = mSwitchAnimManager.drawAnimation(canvas, x, y, width, height, this,
                            mAnimTexture);
                }
                if (drawn) {
                    mListener.requestRender();
                } else {
                    // Continue to the normal draw procedure if the animation is
                    // not drawn.
                    if (mAnimState == ANIM_CAPTURE_RUNNING) {
                        MMProfileManager.stopProfileAnimateCapture();
                    } else if (mAnimState == ANIM_SWITCH_RUNNING) {
                        MMProfileManager.stopProfileAnimateSwitchCamera();
                    }
                    mAnimState = ANIM_NONE;
                    // draw origin frame when size changed
                    if (mSwitchActorState == ANIM_SIZE_CHANGE_NONE) {
                        MMProfileManager.triggerSuperDrawOriginFrame();
                        directDraw(canvas, x, y, width, height);
                    }
                }
            }
            switch (mSwitchActorState) {
            case ANIM_SIZE_CHANGE_START:
                copyOriginSizePreviewTexture(canvas);
                mX = x;
                mY = y;
                mWidth = width;
                mHeight = height;
                mSwitchActorState = ANIM_SIZE_CHANGE_RUNNING;
                break;
            case ANIM_SIZE_CHANGE_RUNNING:
                if (mDrawable && (mWidth != width || mHeight != height)
                        && (mAnimState != ANIM_CAPTURE_RUNNING)) {
                    mSwitchActorState = ANIM_SIZE_CHANGE_NONE;
                    MMProfileManager.triggerSuperDrawSizeChange();
                    directDraw(canvas, x, y, width, height);
                }
                break;
            }
            if (mAnimState == ANIM_NONE && mSwitchActorState == ANIM_SIZE_CHANGE_RUNNING) {
                mOriginSizeTexture.draw(canvas, mX, mY, mWidth, mHeight);
            }
        } // mLock
        MMProfileManager.stopProfileDrawScreenNail();
    }
    
    private void copyPreviewTexture(GLCanvas canvas) {
        // For Mock Camera, do not copy texture, as SW OpenGL solution
        // does not support some function.
        if (com.android.camera.FrameworksClassFactory.isMockCamera()) {
            return;
        }
        if (!mDraw.requiresSurfaceTexture()) {
            mAnimTexture = mDraw.copyToTexture(canvas, mAnimTexture, getTextureWidth(),
                    getTextureHeight());
        } else {
            mAnimTexture.setSize((int) (mExtTexture.getWidth() * mLastScaleX),
                    (int) (mExtTexture.getHeight() * mLastScaleY));
            int width = mAnimTexture.getWidth();
            int height = mAnimTexture.getHeight();
            canvas.beginRenderTarget(mAnimTexture);
            // Flip preview texture vertically. OpenGL uses bottom left point
            // as the origin (0, 0).
            canvas.translate(0, height);
            canvas.scale(1, -1, 1);
            getSurfaceTexture().getTransformMatrix(mTextureTransformMatrix);
            calculateTransformMatrix(mTextureTransformMatrix, mLastScaleX, mLastScaleY);
            canvas.drawTexture(mExtTexture, mTextureTransformMatrix, 0, 0, width, height);
            canvas.endRenderTarget();
        }
    }
    
    private void copyOriginSizePreviewTexture(GLCanvas canvas) {
        mOriginSizeTexture.setSize((int) (mExtTexture.getWidth() * mLastScaleX),
                (int) (mExtTexture.getHeight() * mLastScaleY));
        int width = mOriginSizeTexture.getWidth();
        int height = mOriginSizeTexture.getHeight();
        canvas.beginRenderTarget(mOriginSizeTexture);
        // Flip preview texture vertically. OpenGL uses bottom left point
        // as the origin (0, 0).
        canvas.translate(0, height);
        canvas.scale(1, -1, 1);
        getSurfaceTexture().getTransformMatrix(mTextureTransformMatrix);
        calculateTransformMatrix(mTextureTransformMatrix, mLastScaleX, mLastScaleY);
        canvas.drawTexture(mExtTexture, mTextureTransformMatrix, 0, 0, width, height);
        getSurfaceTexture().updateTexImage();
        canvas.endRenderTarget();
    }
    
    @Override
    public void noDraw() {
        synchronized (mLock) {
            mVisible = false;
            mListener.restoreSwitchCameraState();
            mAnimState = ANIM_NONE;
        }
    }
    
    @Override
    public void recycle() {
        synchronized (mLock) {
            mVisible = false;
        }
    }
    
    private long mLastFrameArriveTime;
    
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        //Log.i(TAG, "onFrameAvailable");
        MMProfileManager.triggerFrameAvailable();
        // M: add for launch camera trace profile
        if (mLaunchCameraTrace <= END_TRACE) {
            mLaunchCameraTrace++;
            if (mLaunchCameraTrace == END_TRACE) {
                Trace.traceCounter(Trace.TRACE_TAG_CAMERA, "AppUpdate", 1);
            }
        }
        if (mDebugLevel2) {
            Log.d(TAG, "[Preview] onFrameAvailable");
        }
        setDrawable(true);
        if (!mFirstFrameArrived) {
            mListener.onFirstFrameArrived();
        }
        if (!mFirstFrameArrived) {
            MMProfileManager.triggerFirstFrameAvailable();
            if (!mVisible) {
                // We need to ask for re-render if the SurfaceTexture receives a
                // new frame.
                MMProfileManager.triggerRequestRender();
                mListener.requestRender();
            }
            // M: Add for CMCC camera capture test case
            Log.i(TAG,
                    "[CMCC Performance test][Launcher][Camera] Start Camera end ["
                            + System.currentTimeMillis() + "]");
            Log.i(TAG, "onFrameAvailable is called(first time) " + this);
        }
        synchronized (mLock) {
            if (mDebug && !mFirstFrameArrived) {
                mRequestStartTime = System.currentTimeMillis() - 1; // avoid
                                                                    // divide by
                                                                    // zero
                mLastFrameArriveTime = mRequestStartTime;
                mDrawStartTime = mRequestStartTime;
                mRequestCount = 0;
                mDrawFrameCount = 0;
            }
            mFirstFrameArrived = true;
            if (mVisible) {
                if (mAnimState == ANIM_SWITCH_WAITING_FIRST_FRAME) {
                    mAnimState = ANIM_SWITCH_START;
                }
                if (mDebug) {
                    long currentTime = 0;
                    if (mDebugLevel2) {
                        currentTime = System.currentTimeMillis();
                        int frameInterval = (int) (currentTime - mLastFrameArriveTime);
                        if (frameInterval > 50) {
                            Log.d(TAG,
                                    "[Preview] onFrameAvailable, request render interval too long = "
                                            + frameInterval);
                        }
                        mLastFrameArriveTime = currentTime;
                    }
                    mRequestCount++;
                    if (mRequestCount % INTERVALS == 0) {
                        if (!mDebugLevel2) {
                            currentTime = System.currentTimeMillis();
                        }
                        int intervals = (int) (currentTime - mRequestStartTime);
                        Log.d(TAG, "[Preview] Request render, fps = " + (mRequestCount * 1000.0f)
                                / intervals + " in last " + intervals + " millisecond.");
                        mRequestStartTime = currentTime;
                        mRequestCount = 0;
                    }
                }
                // We need to ask for re-render if the SurfaceTexture receives a
                // new
                // frame.
                MMProfileManager.triggerRequestRender();
                mListener.requestRender();
            }
            
        }
        // M: add for launch camera trace profile
        if (mLaunchCameraTrace == END_TRACE) {
            Trace.traceCounter(Trace.TRACE_TAG_CAMERA, "AppUpdate", 0);
            mLaunchCameraTrace = mLaunchCameraTrace + 1;
        }
    }
    
    @Override
    public int getWidth() {
        Log.i(TAG, "getWidth = " + (mEnableAspectRatioClamping ? mRenderWidth : getTextureWidth())
                + " mEnableAspectRatioClamping = " + mEnableAspectRatioClamping);
        return mEnableAspectRatioClamping ? mRenderWidth : getTextureWidth();
    }
    
    @Override
    public int getHeight() {
        Log.i(TAG, "getHeight = "
                + (mEnableAspectRatioClamping ? mRenderHeight : getTextureHeight())
                + " mEnableAspectRatioClamping = " + mEnableAspectRatioClamping);
        return mEnableAspectRatioClamping ? mRenderHeight : getTextureHeight();
    }
    
    public int getTextureWidth() {
        return super.getWidth();
    }
    
    public int getTextureHeight() {
        return super.getHeight();
    }
    
    // We need to keep track of the size of preview frame on the screen because
    // it's needed when we do switch-camera animation. See comments in
    // SwitchAnimManager.java. This is based on the natural orientation, not the
    // view system orientation.
    public void setPreviewFrameLayoutSize(int width, int height) {
        synchronized (mLock) {
            mSwitchAnimManager.setPreviewFrameLayoutSize(width, height);
            mRenderWidth = width;
            mRenderHeight = height;
            // full screen case, should update renderer size
            if (mEnableAspectRatioClamping) {
                updateRenderSize();
            }
        }
    }
    
    public boolean enableDebug() {
        return mDebug;
    }
    
    public void setDrawable(boolean drawable) {
        mDrawable = drawable;
    }
    
    // only when relative frame ready,
    // new size can set to SurfaceTextureScreenNail during swtich Actor
    public void setOnLayoutChanged(boolean changed) {
        Log.i(TAG, "setOnLayoutChanged changed = " + changed);
        mLayoutChanged = changed;
        if (mLayoutChanged) {
            setSize(mHoldScreenNailWidth, mHoldScreenNailHeight);
            updateRenderSize();
        }
    }
    
    public boolean setScreenNailSize(int width, int height) {
        Log.d(TAG, "setScreenNailSize width = " + width + " height =" + height);
        mHoldScreenNailWidth = width;
        mHoldScreenNailHeight = height;
        // during switch actor, should hold new size
        // when layout changed, set the new size to SurfaceTextureScreenNail
        if (mDrawable || !mFirstFrameArrived) {
            setSize(mHoldScreenNailWidth, mHoldScreenNailHeight);
            return true;
        }
        return false;
    }
    
    /**
     * Tells the ScreenNail to override the default aspect ratio scaling and
     * instead perform custom scaling to basically do a centerCrop instead of
     * the default centerInside
     * 
     * Note that calls to setScreenNailSize will disable this
     */
    public void enableAspectRatioClamping(boolean enable) {
        Log.d(TAG, "enableAspectRatioClamping() enable = " + enable);
        mEnableAspectRatioClamping = enable;
        updateRenderSize();
    }
    
    private void updateRenderSize() {
        Log.i(TAG, "updateRenderSize() mRenderWidth =" + mRenderWidth + " mRenderHeight ="
                + mRenderHeight + " getTextureWidth =" + getTextureWidth() + " getTextureHeight ="
                + getTextureHeight());
        
        if (!mEnableAspectRatioClamping) {
            mRenderWidth = mHoldScreenNailWidth;
            mRenderHeight = mHoldScreenNailHeight;
            mScaleX = mScaleY = 1f;
            Log.i(TAG, "aspect ratio clamping disabled, surfaceTexture scale:" + mScaleX + ", "
                    + mScaleY);
            return;
        }
        
        float aspectRatio;
        if (getTextureWidth() > getTextureHeight()) {
            aspectRatio = (float) getTextureWidth() / (float) getTextureHeight();
        } else {
            aspectRatio = (float) getTextureHeight() / (float) getTextureWidth();
        }
        float scaledTextureWidth, scaledTextureHeight;
        if (mRenderWidth > mRenderHeight) {
            scaledTextureWidth = Math.max(mRenderWidth, (int) (mRenderHeight * aspectRatio));
            scaledTextureHeight = Math.max(mRenderHeight, (int) (mRenderWidth / aspectRatio));
        } else {
            scaledTextureWidth = Math.max(mRenderWidth, (int) (mRenderHeight / aspectRatio));
            scaledTextureHeight = Math.max(mRenderHeight, (int) (mRenderWidth * aspectRatio));
        }
        mScaleX = mRenderWidth / scaledTextureWidth;
        mScaleY = mRenderHeight / scaledTextureHeight;
        Log.i(TAG, "aspect ratio clamping enabled, surfaceTexture scale: " + mScaleX + ", "
                + mScaleY);
    }
    
    @Override
    protected void updateTransformMatrix(float[] matrix) {
        super.updateTransformMatrix(matrix);
        //Log.d(TAG, "updateTransformMatrix(), surfaceTexture scale: " + mScaleX + ", " + mScaleY);
        calculateTransformMatrix(matrix, mScaleX, mScaleY);
        updatePreviewParameters();
    }
    
    private void calculateTransformMatrix(float[] matrix, float scaleX, float scaleY) {
        Matrix.translateM(matrix, 0, .5f, .5f, 0);
        Matrix.scaleM(matrix, 0, scaleX, scaleY, 1f);
        Matrix.translateM(matrix, 0, -.5f, -.5f, 0);
    }
    
    private void updatePreviewParameters() {
        mLastScaleX = mScaleX;
        mLastScaleY = mScaleY;
    }
}
