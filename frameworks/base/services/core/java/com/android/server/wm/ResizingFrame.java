/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.wm;

import static com.android.server.wm.WindowManagerService.DEBUG_STACK;
import static com.android.server.wm.WindowManagerService.DEBUG_SURFACE_TRACE;
import static com.android.server.wm.WindowManagerService.DEBUG_SCREENSHOT;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.Region;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Surface.OutOfResourcesException;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceSession;


import com.android.server.wm.WindowStateAnimator.SurfaceTrace;

import java.util.ArrayList;

class ResizingFrame {
    private static final String TAG = "ResizingFrame";
    private static final int THICKNESS = 10;
    private static final float ALPHA = 0.5f;

    private final WindowManagerService mService;
    private SurfaceControl mSurfaceControl = null;
    private final Surface mSurface = new Surface();
    private final Rect mBounds = new Rect();
    private TaskStack mStack = null;
    /// temp @{
    static boolean DEBUG_STACK = true;

    public static final int RESIZE_NONE  = 0;
    public static final int RESIZE_UP    = 1 << 0;
    public static final int RESIZE_DOWN  = 1 << 1;
    public static final int RESIZE_LEFT  = 1 << 2;
    public static final int RESIZE_RIGHT = 1 << 3;
    //public static final int RESIZE_COLOR = 0xFFF39A1E; /// MediaTek Gold

    private static final int MAX_SCREENSHOT_RETRIES = 3;

    private boolean mEnableMotion = false;

    float mDsDx=1.0f, mDtDx=0.0f, mDsDy=0.0f, mDtDy=1.0f;

    boolean mVisible = false;

    Display mDisplay;
    SurfaceSession mSession;
    Context mContext;
    WindowState mCurrentWin;

    public ResizingFrame(WindowManagerService service, Display display,
                SurfaceSession session, Context context) {
        mService = service;
        mDisplay = display;
        mSession = session;
        mContext = context;
    }

    private boolean createSurfaceLocked(){
        if (mSurfaceControl != null) {
            Slog.e(TAG, "[BMW]createSurfaceLocked mSurfaceControl is not null."
                            + " Copy the original surface to mSurface.");
            mSurface.copyFrom(mSurfaceControl);
            return true;
        }
        SurfaceControl ctrl = null;
        try {
            if (DEBUG_SURFACE_TRACE) {
                ctrl = new SurfaceTrace(mSession, "ResizingFrame",
                    1, 1, PixelFormat.TRANSLUCENT, SurfaceControl.HIDDEN);
            } else {
                ctrl = new SurfaceControl(mSession, "ResizingFrame",
                    1, 1, PixelFormat.TRANSLUCENT, SurfaceControl.HIDDEN);
            }
            ctrl.setLayerStack(mDisplay.getLayerStack());
            ctrl.setAlpha(ALPHA);
            mSurface.copyFrom(ctrl);
        } catch (OutOfResourcesException e) {
            Slog.e(TAG, "[BMW]createSurfaceLocked OutOfResourcesException" + e);
            return false;
        }
        mSurfaceControl = ctrl;

        Canvas c = null;
        try {
            Slog.i(TAG, "[BMW]drawBitmap: " + "lockCanvas");
            c = mSurface.lockCanvas(null);

        } catch (IllegalArgumentException e) {
        } catch (Surface.OutOfResourcesException e) {
        }
        if (c == null) {
            return false;
        }

        Slog.i(TAG, "[BMW]drawBitmap: " + "Canvas height=" + c.getHeight()
                        + ", width = " + c.getWidth());

        //c.drawColor(mContext.getResources().getColor(com.mediatek.internal.R.color.mw_resize_color));

		c.drawColor(0xfff39a1e);
        mSurface.unlockCanvasAndPost(c);
        return true;
    }

    private boolean destroySurfaceLocked(){
        SurfaceControl ctrl = null;
        try {
            mSurface.release();
            if (mSurfaceControl != null) {
                mSurfaceControl.destroy();
                mSurfaceControl = null;
            }
        } catch (OutOfResourcesException e) {
            Slog.e(TAG, "[BMW]destroySurfaceLocked OutOfResourcesException" + e);
            return false;
        }
        return true;
    }

    private void positionSurface(Rect bounds) {
        if (DEBUG_STACK) Slog.i(TAG, "[BMW]positionSurface: bounds=" + bounds.toShortString());

        if (mSurfaceControl == null) return;

        mSurfaceControl.setPosition(bounds.left, bounds.top);

        mDsDx = (float)bounds.width();
        mDtDy = (float)bounds.height();
        if (DEBUG_STACK) Slog.i(TAG, "[BMW]updateSurface: setMatrix "
                                + " matrix=[" + mDsDx + "," + mDtDx
                                + "][" + mDsDy + "," + mDtDy + "]");
        mSurfaceControl.setMatrix(
            mDsDx, mDtDx, mDsDy, mDtDy);
    }

    private void cropSurface(Rect crop) {
        if (DEBUG_STACK) Slog.i(TAG, "[BMW]cropSurface: crop=" + crop.toShortString());
        if (crop == null || mSurfaceControl == null) return;
        mSurfaceControl.setWindowCrop(crop);
    }

    // Note: caller responsible for being inside
    // Surface.openTransaction() / closeTransaction()
    public void setVisibility(boolean on) {
        if (DEBUG_STACK) Slog.i(TAG, "[BMW]setVisibility: on=" + on +
                " mBounds=" + mBounds.toShortString());
        if (mSurfaceControl == null) {
            return;
        }
        if (on) {
            mSurfaceControl.show();
            mVisible = true;
        } else {
            mSurfaceControl.hide();
            mBounds.setEmpty();
            mCurrentWin = null;

            /// reset the matrix as Identity matrix
            mDsDx = mDtDy = 1;
            mDtDx = mDsDy = 0;
            mSurfaceControl.setMatrix(mDsDx, mDtDx, mDsDy, mDtDy);
            mVisible = false;
        }
    }

    public void setLayer(int layer) {
        if (mSurfaceControl == null) return;

        mSurfaceControl.setLayer(layer);
    }

    public Rect getBounds() {
        return new Rect(mBounds);
    }

    public void updateBoundary(Rect bounds) {
        if (DEBUG_STACK) Slog.i(TAG, "[BMW]updateBoundary: bounds=" + bounds);
        mBounds.set(bounds.left, bounds.top,
                        bounds.right, bounds.bottom);
    }

    public void updateSurface() {
        positionSurface(mBounds);
    }

    public void initBounds(WindowState win, TaskStack stack) {
        if (DEBUG_STACK) Slog.i(TAG, "[BMW]initBounds: win=" + win +", stack=" + stack);

        if (stack == null) return;

        mStack = stack;
        mBounds.set(win.getFrameLw());
        mCurrentWin = win;
        
        createSurfaceLocked();

        positionSurface(mBounds);

        if (DEBUG_STACK) Slog.i(TAG, "[BMW]initBounds: mBounds=" + mBounds.toShortString());
        /// temp
    }

    public void copyBounds2Box(){
        if (mStack == null) return;

        mStack.setBounds(mBounds);
        mStack.getDisplayContent().layoutNeeded = true;
        if (DEBUG_STACK) Slog.i(TAG, "[BMW]copyBounds2Box: copyBounds2Box = " + mBounds);

    }

    public boolean isVisible() {
        return mVisible;
    }

    public void enableMotion(boolean enable) {
        mEnableMotion = enable;
    }

    public boolean isEnableMotion() {
        return mEnableMotion;
    }

    public boolean isFocusWinChanged(WindowState currentFocus){
        if(currentFocus != mCurrentWin)
            return true;
        return false;
    }

}
