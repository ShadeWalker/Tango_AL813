/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2013. All rights reserved.
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


package com.android.internal.policy.impl;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.WindowManagerPolicy.PointerEventListener;
import android.view.WindowManagerPolicy.WindowManagerFuncs;
import android.view.WindowManagerPolicy.WindowState;

import static android.view.WindowManagerPolicy.WindowManagerFuncs.RESIZE_NONE;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.RESIZE_UP;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.RESIZE_DOWN;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.RESIZE_LEFT;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.RESIZE_RIGHT;
import com.mediatek.multiwindow.MultiWindowProxy;
/*
 * Listens for floating window input gestures
 * @hide
 */
public class FloatingMonitorPointerEventListener
        implements PointerEventListener {
    static final String TAG = "FloatingMonitor";
    static boolean DEBUG = false;

    private static final int MOTION_MODE_NONE = 0;
    private static final int MOTION_MODE_DRAG = 1;

    private static final int RESIZE_DIRECTION_ALL = 16;

    int mMotionMode = MOTION_MODE_NONE;
    int mResizeDirect = RESIZE_NONE;

    int[] mEnabledResizeDirections;

    private Context mContext;
    private GestureDetector mGestureDetector;
    private Rect mCtrlBarRect = new Rect();       /// Control Bar Rect
    private Rect mTopBarRect = new Rect();
    private Rect mBottomBarRect = new Rect();
    private Rect mRightBarRect = new Rect();
    private Rect mLeftBarRect = new Rect();
    private Rect mFocusRect = new Rect();
    private Rect mMonitorRect = new Rect();
    private WindowManagerFuncs mWindowFuncs;
    private WindowState mFocusWindow;

    private int mCtrlBarHeight = 0, mCtrlBarBtnWidth = 0;

    final Object mFocusWindowLock = new Object();
    //MultiWindowProxy mMultiWindowProxy = MultiWindowProxy.getInstance();

    private final GestureDetector.OnGestureListener mOnGestureListener =
            new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                float distanceX, float distanceY) {
            if (DEBUG) {
                Slog.d(TAG, "[BMW]onScroll e1=" + e1 + ", e2 =" + e2 + ",dX = "
                        + distanceX + ",dY = " + distanceY);
            }
            
            MultiWindowProxy multiWindowProxy = MultiWindowProxy.getInstance();
            if (multiWindowProxy == null){
                Slog.v(TAG, "[BMW]Multi Window Service not ready!");
                return false;
            }
            if (mMotionMode == MOTION_MODE_DRAG) {
                multiWindowProxy.moveFloatingWindow((int)distanceX,
                    (int)distanceY);
            } else if (mResizeDirect != RESIZE_NONE) {
                multiWindowProxy.resizeFloatingWindow(mResizeDirect,
                    (int)distanceX,(int)distanceY);
            }

            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            int motionX, motionY;
            motionX = (int)e.getX(0);
            motionY = (int)e.getY(0);

            if (DEBUG) {
                Slog.d(TAG, "[BMW]onDown e=" + e
                            + ", mFocusWindow = " + mFocusWindow
                            + ", focusFrame =" + mFocusWindow.getFrameLw());
            }

            synchronized(mMonitorRect) {
                if (!mMonitorRect.contains(motionX, motionY)) {
                    Slog.d(TAG, "[BMW]Outside of the monitor region." +
                        "Skip the motion event. " + e);
                    return false;
                }
            }

            synchronized (mFocusWindowLock) {
                mFocusRect.set(mFocusWindow.getFrameLw());
                mCtrlBarRect.set(mFocusWindow.getFrameLw());
            }

            int resizeMargin = 30;
            // Set resize insets for better user experience.
            // Can also resize window, when user touch the inside edge of the floating window.
            int resizeInsets = 0;
            mFocusRect.set(mFocusRect.left + resizeInsets, mFocusRect.top, 
                mFocusRect.right - resizeInsets, mFocusRect.bottom - resizeInsets);
            
            mCtrlBarRect.set(mCtrlBarRect.left, mCtrlBarRect.top,
                mCtrlBarRect.right - mCtrlBarBtnWidth, mCtrlBarRect.top + mCtrlBarHeight);

            mTopBarRect.set(mFocusRect.left - resizeMargin, mFocusRect.top - resizeMargin,
                                mFocusRect.right + resizeMargin , mFocusRect.top);
            mBottomBarRect.set(mFocusRect.left - resizeMargin, mFocusRect.bottom,
                                mFocusRect.right + resizeMargin, mFocusRect.bottom + resizeMargin);
            mRightBarRect.set(mFocusRect.right, mFocusRect.top - resizeMargin,
                                mFocusRect.right + resizeMargin, mFocusRect.bottom + resizeMargin);
            mLeftBarRect.set(mFocusRect.left - resizeMargin, mFocusRect.top - resizeMargin,
                                mFocusRect.left, mFocusRect.bottom + resizeMargin);

            if (mCtrlBarRect.contains(motionX, motionY)) {
                mMotionMode = MOTION_MODE_DRAG;
            } else if (!mFocusRect.contains(motionX, motionY)) {
                if (mTopBarRect.contains(motionX, motionY)) {
                    mResizeDirect |= RESIZE_UP;
                    if (motionX < mTopBarRect.left + 2 * resizeMargin) {
                        mResizeDirect |= RESIZE_LEFT;
                    }
                    if (motionX > mTopBarRect.right - 2 * resizeMargin) {
                        mResizeDirect |= RESIZE_RIGHT;
                    }
                } else if (mBottomBarRect.contains(motionX, motionY)) {
                    mResizeDirect |= RESIZE_DOWN;
                    if (motionX < mBottomBarRect.left + 2 * resizeMargin) {
                        mResizeDirect |= RESIZE_LEFT;
                    }
                    if (motionX > mBottomBarRect.right - 2 * resizeMargin) {
                        mResizeDirect |= RESIZE_RIGHT;
                    }
                } else if (mLeftBarRect.contains(motionX, motionY)) {
                    mResizeDirect |= RESIZE_LEFT;
                    if (motionY < mLeftBarRect.top + 2 * resizeMargin) {
                        mResizeDirect |= RESIZE_UP;
                    }
                    if (motionY > mBottomBarRect.bottom - 2 * resizeMargin) {
                        mResizeDirect |= RESIZE_DOWN;
                    }
                } else if (mRightBarRect.contains(motionX, motionY)) {
                    mResizeDirect |= RESIZE_RIGHT;
                    if (motionY < mRightBarRect.top + 2 * resizeMargin) {
                        mResizeDirect |= RESIZE_UP;
                    }
                    if (motionY > mRightBarRect.bottom - 2 * resizeMargin) {
                        mResizeDirect |= RESIZE_DOWN;
                    }
                }
            }
            /// M: check if the current resize direction is enabled
            if (!checkResizeDirectionEnabled(mResizeDirect))
                mResizeDirect = RESIZE_NONE;

            if (mResizeDirect != RESIZE_NONE | mMotionMode == MOTION_MODE_DRAG) {
                if (DEBUG) {
                    Slog.d(TAG, "[BMW]enableFocusedFrame mResizeDirect =" + mResizeDirect
                                + ", mMotionMode = " + mMotionMode);
                }
                if (MultiWindowProxy.getInstance() == null ){
                    Slog.v(TAG, "[BMW]Multi Window Service not ready!");
                    return false;
                }
                MultiWindowProxy.getInstance().enableFocusedFrame(true);
            }

            return true;
        }
    };

    public FloatingMonitorPointerEventListener(
            Context context, WindowManagerFuncs windowManagerFuncs) {
        mContext = context;
        mWindowFuncs = windowManagerFuncs;

        mGestureDetector = new GestureDetector(mContext,
            mOnGestureListener);
        // [ALPS01684303]:Disable long press, or cannot move window when long press then control bar.
        mGestureDetector.setIsLongpressEnabled(false);

        computeCtrlBarRegion();
        
        mEnabledResizeDirections = mContext.getResources().getIntArray(
            com.mediatek.internal.R.array.config_enabled_resize_directions);
    }

    public void updatFocusWindow(WindowState focusWindow) {
        synchronized (mFocusWindowLock) {
            mFocusWindow = focusWindow;
        }
    }

    public void onPointerEvent(MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        if (DEBUG) {
            Slog.d(TAG, "[BMW]Input Motion Event=" + motionEvent);
        }
        
        if (MultiWindowProxy.getInstance() == null ){
            Slog.v(TAG, "[BMW]Multi Window Service not ready!");
            return ;
        }
        
        // Don't support multiple fingers
        if (action == MotionEvent.ACTION_UP
            || motionEvent.getPointerCount() > 1) {
            if (mResizeDirect != RESIZE_NONE
                    || mMotionMode != MOTION_MODE_NONE) {
                MultiWindowProxy.getInstance().enableFocusedFrame(false);
            }
            mMotionMode = MOTION_MODE_NONE;
            mResizeDirect = RESIZE_NONE;
        } else {
            if (action == MotionEvent.ACTION_DOWN) {
                /// Before handling the donw event,
                /// Must clear the status.
                mMotionMode = MOTION_MODE_NONE;
                mResizeDirect = RESIZE_NONE;
            }
            mGestureDetector.onTouchEvent(motionEvent);
        }
    }

    private void computeCtrlBarRegion() {
        DisplayManager displayManager;
        DisplayInfo displayInfo = new DisplayInfo();
        displayManager = (DisplayManager)
                mContext.getSystemService(Context.DISPLAY_SERVICE);

        displayManager.getDisplay(Display.DEFAULT_DISPLAY).getDisplayInfo(displayInfo);

        /// 130 & 40 are hard code. In future, it should be got by resource.
        mCtrlBarBtnWidth = 130 * displayInfo.logicalDensityDpi / 160;
        mCtrlBarHeight = 44 * displayInfo.logicalDensityDpi / 160;

    }

    public void updateMonitorRect(int left, int top, int right, int bottom) {
        if (DEBUG) {
            Slog.d(TAG, "[BMW]updateMonitorRect [" + left + ", " + top + "]"
                    + " [" + right + ", " + bottom + "]");
        }

        synchronized(mMonitorRect) {
            mMonitorRect.set(left, top, right, bottom);
        }
    }

    private boolean checkResizeDirectionEnabled(int currentResizeDirect){
        for (int i:mEnabledResizeDirections){
            if (i == RESIZE_DIRECTION_ALL || i == currentResizeDirect )
                return true;
        }
        return false;
    }
}


