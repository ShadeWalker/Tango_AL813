// Copyright 2012 Google Inc. All Rights Reserved.

package com.android.server.wm;

import static com.android.server.wm.WindowManagerService.DEBUG_DIM_LAYER;

import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.SurfaceControl;
import java.io.PrintWriter;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Surface;

/**
 * M: Add for BMW.
 * In some cases, apps(such as, Gallery2) has no background color, 
 * or has a transparent color (such as, a SurfaceView punches a hole 
 * in its window to allow its surface to be displayed).
 * If there is nothing to show behind the window, 
 * the background will be filled with black by the underlying graphic module.
 * 
 * But, in Floating mode, the underlying graphic module will not fill black color for it,
 * because there is full-screen window behind the flaoting windows.
 *
 * So, we add a background surface for each floating stack, if needed.
 */
public class StackBackgroundSurface {
    private String TAG = "StackBackgroundSurface";
    private static final boolean DEBUG = true;

    /** Reference to the owner of this object. */
    final DisplayContent mDisplayContent;

    /** Actual surface that dims */
    SurfaceControl mSurfaceControl;
    
    private final Surface mSurface = new Surface();

    /** Last value passed to setAlpha() */
    float mAlpha = 0;

    /** Last value passed to setLayer() */
    int mLayer = -1;

    /** True after show() has been called, false after hide(). */
    private boolean mShowing = false;

    /** Owning stack */
    final TaskStack mStack;
    private final WindowManagerService mService;
    boolean needUpdate = false;
    float mLeft, mTop;
    int mWidth, mHeight;
    
    StackBackgroundSurface(WindowManagerService service, TaskStack stack, DisplayContent displayContent) {
        mStack = stack;
        mDisplayContent = displayContent;
        mService = service;
    }

    void prepareSurface() {
        final int displayId = mDisplayContent.getDisplayId();
        if (DEBUG_DIM_LAYER) Slog.v(TAG, "Ctor: displayId=" + displayId);
        SurfaceControl.openTransaction();
        String surfaceName;
        surfaceName = "StackBackgroundSurface-" + mStack.mStackId;
        TAG = surfaceName;
        try {
            if (WindowManagerService.DEBUG_SURFACE_TRACE) {
                mSurfaceControl = new WindowStateAnimator.SurfaceTrace(mService.mFxSession,
                    surfaceName,
                    16, 16, PixelFormat.OPAQUE,
                    SurfaceControl.FX_SURFACE_DIM | SurfaceControl.HIDDEN);
            } else {
                mSurfaceControl = new SurfaceControl(mService.mFxSession,
                    surfaceName,
                    16, 16, PixelFormat.OPAQUE,
                    SurfaceControl.FX_SURFACE_DIM | SurfaceControl.HIDDEN);
            }
            if (WindowManagerService.SHOW_TRANSACTIONS ||
                    WindowManagerService.SHOW_SURFACE_ALLOC) Slog.i(TAG,
                            "  BLACK " + mSurfaceControl + ": CREATE");
            mSurfaceControl.setLayerStack(displayId);
            mSurface.copyFrom(mSurfaceControl);
        } catch (Exception e) {
            Slog.e(WindowManagerService.TAG, "Exception creating Dim surface", e);
        } finally {
            SurfaceControl.closeTransaction();
        }
    }
    
    /// NOTE: Must be called with Surface transaction open.
    void setLayer(int layer) {
        if (mSurfaceControl == null)
            return;
        if (mLayer != layer) {
            mLayer = layer;
            mSurfaceControl.setLayer(layer);
        }
    }

    int getLayer() {
        return mLayer;
    }

    /// NOTE: Must be called with Surface transaction open.
    void setAlpha(float alpha) {
        if (mSurfaceControl == null)
            return;
        mAlpha = alpha;
        mSurfaceControl.setAlpha(alpha);
    }

    /// NOTE: Must be called with Surface transaction open.
    void setPosition(float left, float top) {
        if (mSurfaceControl == null)
            return;
        mLeft = left;
        mTop = top;
        mSurfaceControl.setPosition(left, top);
    }

    /// NOTE: Must be called with Surface transaction open.
    void setWindowCrop(Rect crop) {
        if (mSurfaceControl == null)
            return;
        mSurfaceControl.setWindowCrop(crop);
    }
    
    /// NOTE: Must be called with Surface transaction open.
    void setSize(int w, int h) {
        if (mSurfaceControl == null)
            return;
        mWidth = w;
        mHeight = h;
        mSurfaceControl.setSize(w, h);
    }
    
    /// NOTE: Must be called with Surface transaction open.
    void setMatrix(float dsdx, float dtdx, float dsdy, float dtdy) {
        if (mSurfaceControl == null)
            return;
        mSurfaceControl.setMatrix(dsdx, dtdx, dsdy, dtdy);
    }
    
    void setBounds(Rect bounds) {
        //mBounds.set(bounds);
        mLeft = bounds.left;
        mTop = bounds.top;
        mWidth = bounds.width();
        mHeight = bounds.height();
        needUpdate = true;
    }
    
    /// NOTE: Must be called with Surface transaction open.
    void show() {
        if (mSurfaceControl == null)
            return;
        if (needUpdate){
            setPosition(mLeft, mTop);
            setSize(mWidth, mHeight);
            mSurfaceControl.setLayer(mLayer);
            needUpdate = false;
        }
        if (!mShowing) {
            mSurfaceControl.show();
            mShowing = true;
        }
    }
    
    /// NOTE: Must be called with Surface transaction open.
    void hide() {
        if (mSurfaceControl == null)
            return;
        if (needUpdate) {
            setPosition(mLeft, mTop);
            setSize(mWidth, mHeight);
            mSurfaceControl.setLayer(mLayer);
            needUpdate = false;
        }
        if(mShowing){
            mSurfaceControl.hide();
            mShowing = false;
        }
    }

    /** Cleanup */
    void destroySurface() {
        if (DEBUG_DIM_LAYER) Slog.v(TAG, "destroySurface.");
        if (mSurfaceControl != null) {
            mSurfaceControl.destroy();
            mSurfaceControl = null;
        }
    }

    public void printTo(String prefix, PrintWriter pw) {
        pw.print(prefix); pw.print(" StackBackgroundSurface="); pw.print(mSurfaceControl);
				pw.print(" mStack="); pw.print(mStack);
		pw.print(prefix); pw.print(" mShowing="); pw.print(mShowing);
                pw.print(" mLayer="); pw.print(mLayer);
                pw.print(" mAlpha="); pw.print(mAlpha);
        		pw.print(" left="); pw.print(mLeft);
				pw.print(" top="); pw.print(mTop);
				pw.print(" w="); pw.print(mWidth);
                pw.print(" h="); pw.print(mHeight);
                pw.print(" needUpdate="); pw.println(needUpdate);
    }

}



