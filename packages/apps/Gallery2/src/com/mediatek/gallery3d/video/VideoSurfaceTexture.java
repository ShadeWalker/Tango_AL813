/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.gallery3d.video;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.os.HandlerThread;
import android.os.SystemProperties;

import com.android.gallery3d.common.ApiHelper;

import com.mediatek.galleryframework.gl.MExtTexture;
import com.mediatek.galleryframework.gl.MGLCanvas;

public abstract class VideoSurfaceTexture implements SurfaceTexture.OnFrameAvailableListener {
    @SuppressWarnings("unused")
    private static final String TAG = "Gallery2/VideoSurfaceTexture";
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;

    protected VideoFrameTexture mExtTexture;
    private SurfaceTexture mSurfaceTexture;
    protected int mWidth, mHeight;
    private float[] mTransform = new float[16];
    private boolean mHasTexture = false;
    protected static final int INTERVALS = 60;
    protected int mDebugFlag = SystemProperties.getInt("cam.debug", 0);
    protected boolean mDebug = false;
    protected boolean mDebugLevel2 = false;
    protected int mDrawFrameCount = 0;
    protected int mRequestCount = 0;
    protected long mRequestStartTime = 0;
    protected long mDrawStartTime = 0;

    private class VideoFrameTexture extends MExtTexture {
        public VideoFrameTexture(MGLCanvas canvas, int target) {
            super(canvas, target, true);
        }

        public void setSize(int width, int height) {
            super.setSize(width, height);
        }

        public void draw(MGLCanvas canvas, int x, int y, int width, int height) {
            VideoSurfaceTexture.this.draw(canvas, 0, 0, width, height);
        }
    }

    public VideoSurfaceTexture() {
        mDebug = mDebugFlag > 0;
        mDebugLevel2 = mDebugFlag > 1;
    }

    public void acquireSurfaceTexture(MGLCanvas canvas) {
        mExtTexture = new VideoFrameTexture(canvas, GL_TEXTURE_EXTERNAL_OES);
        mExtTexture.setSize(mWidth, mHeight);
        mSurfaceTexture = new SurfaceTexture(mExtTexture.getId());
        setDefaultBufferSize(mSurfaceTexture, mWidth, mHeight);
        mSurfaceTexture.setOnFrameAvailableListener(this);
        synchronized (this) {
            mHasTexture = true;
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private static void setDefaultBufferSize(SurfaceTexture st, int width, int height) {
        if (ApiHelper.HAS_SET_DEFALT_BUFFER_SIZE) {
            st.setDefaultBufferSize(width, height);
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static void releaseSurfaceTexture(SurfaceTexture st) {
        st.setOnFrameAvailableListener(null);
        if (ApiHelper.HAS_RELEASE_SURFACE_TEXTURE) {
            st.release();
        }
    }

    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    public void releaseSurfaceTexture(boolean needReleaseExtTexture) {
        if (needReleaseExtTexture) {
            synchronized (this) {
                mHasTexture = false;
            }
            mExtTexture.recycle();
            mExtTexture = null;
        }
        releaseSurfaceTexture(mSurfaceTexture);
        mSurfaceTexture = null;
    }

    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public void resizeTexture() {
        if (mExtTexture != null) {
            mExtTexture.setSize(mWidth, mHeight);
            setDefaultBufferSize(mSurfaceTexture, mWidth, mHeight);
        }
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public boolean draw(MGLCanvas canvas, int x, int y, int width, int height) {
        synchronized (this) {
            if (!mHasTexture) {
                return false;
            }
            mSurfaceTexture.updateTexImage();
            mSurfaceTexture.getTransformMatrix(mTransform);

            // Flip vertically.
            canvas.save(MGLCanvas.SAVE_FLAG_MATRIX);
            int cx = x + width / 2;
            int cy = y + height / 2;
            canvas.translate(cx, cy);
            canvas.scale(1, -1, 1);
            canvas.translate(-cx, -cy);
            updateTransformMatrix(mTransform);

            canvas.drawTexture(mExtTexture, mTransform, x, y, width, height);
            canvas.restore();
            if (mDebug) {
                mDrawFrameCount++;
                if (mDrawFrameCount % INTERVALS == 0) {
                    long currentTime = System.currentTimeMillis();
                    int intervals = (int) (currentTime - mDrawStartTime);
                    mDrawStartTime = currentTime;
                    mDrawFrameCount = 0;
                }
            }
            return true;
        }
    }

    protected void updateTransformMatrix(float[] matrix) {
    }

    @Override
    abstract public void onFrameAvailable(SurfaceTexture surfaceTexture);
}
