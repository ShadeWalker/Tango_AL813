/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

import static com.android.server.wm.WindowManagerService.DEBUG_TASK_MOVEMENT;
import static com.android.server.wm.WindowManagerService.TAG;
import static com.android.server.wm.WindowManagerService.DEBUG_STACK;

import android.graphics.Rect;
import android.os.Debug;
import android.util.EventLog;
import android.util.Slog;
import android.util.TypedValue;
import com.android.server.EventLogTags;

import java.io.PrintWriter;
import java.util.ArrayList;

/// M: BMW. @{
import android.content.pm.ActivityInfo;
import android.view.Surface;
import android.view.DisplayInfo;
import com.mediatek.multiwindow.MultiWindowProxy;
/// @}
public class TaskStack {
    /** Amount of time in milliseconds to animate the dim surface from one value to another,
     * when no window animation is driving it. */
    private static final int DEFAULT_DIM_DURATION = 200;

    /** Unique identifier */
    final int mStackId;

    /** The service */
    private final WindowManagerService mService;

    /** The display this stack sits under. */
    private DisplayContent mDisplayContent;

    /** The Tasks that define this stack. Oldest Tasks are at the bottom. The ordering must match
     * mTaskHistory in the ActivityStack with the same mStackId */
    private final ArrayList<Task> mTasks = new ArrayList<Task>();

    /** For comparison with DisplayContent bounds. */
    private Rect mTmpRect = new Rect();

    /** Content limits relative to the DisplayContent this sits in. */
    private Rect mBounds = new Rect();

    /** Whether mBounds is fullscreen */
    private boolean mFullscreen = true;

    /** Used to support {@link android.view.WindowManager.LayoutParams#FLAG_DIM_BEHIND} */
    private DimLayer mDimLayer;

    /** The particular window with FLAG_DIM_BEHIND set. If null, hide mDimLayer. */
    WindowStateAnimator mDimWinAnimator;

    /** Support for non-zero {@link android.view.animation.Animation#getBackgroundColor()} */
    DimLayer mAnimationBackgroundSurface;

    /** The particular window with an Animation with non-zero background color. */
    WindowStateAnimator mAnimationBackgroundAnimator;

    /** Set to false at the start of performLayoutAndPlaceSurfaces. If it is still false by the end
     * then stop any dimming. */
    boolean mDimmingTag;

    /** Application tokens that are exiting, but still on screen for animations. */
    final AppTokenList mExitingAppTokens = new AppTokenList();

    /** Detach this stack from its display when animation completes. */
    boolean mDeferDetach;

    TaskStack(WindowManagerService service, int stackId) {
        mService = service;
        mStackId = stackId;
        // TODO: remove bounds from log, they are always 0.
        EventLog.writeEvent(EventLogTags.WM_STACK_CREATED, stackId, mBounds.left, mBounds.top,
                mBounds.right, mBounds.bottom);
    }

    DisplayContent getDisplayContent() {
        return mDisplayContent;
    }

    ArrayList<Task> getTasks() {
        return mTasks;
    }

    void resizeWindows() {
        final boolean underStatusBar = mBounds.top == 0;

        final ArrayList<WindowState> resizingWindows = mService.mResizingWindows;
        for (int taskNdx = mTasks.size() - 1; taskNdx >= 0; --taskNdx) {
            final ArrayList<AppWindowToken> activities = mTasks.get(taskNdx).mAppTokens;
            for (int activityNdx = activities.size() - 1; activityNdx >= 0; --activityNdx) {
                final ArrayList<WindowState> windows = activities.get(activityNdx).allAppWindows;
                for (int winNdx = windows.size() - 1; winNdx >= 0; --winNdx) {
                    final WindowState win = windows.get(winNdx);
                    if (!resizingWindows.contains(win)) {
                        if (WindowManagerService.DEBUG_RESIZE) Slog.d(TAG,
                                "setBounds: Resizing " + win);
                        resizingWindows.add(win);
                    }
                    win.mUnderStatusBar = underStatusBar;
                }
            }
        }
    }

    boolean setBounds(Rect bounds) {
        boolean oldFullscreen = mFullscreen;
        /// M: BMW. @{
        if (DEBUG_STACK) {
            Slog.d(TAG, "[BMW]setBounds bound = " + bounds + ", stackId = " + mStackId, new Throwable("setBounds"));
        }
        if (MultiWindowProxy.getInstance() != null
                && MultiWindowProxy.getInstance().isFloatingStack(mStackId)) {
            verifyStackBounds( bounds);
        }
        /// @}
        if (mDisplayContent != null) {
            mDisplayContent.getLogicalDisplayRect(mTmpRect);
            mFullscreen = mTmpRect.equals(bounds);
        }

        if (mBounds.equals(bounds) && oldFullscreen == mFullscreen) {
            return false;
        }

        mDimLayer.setBounds(bounds);
        mAnimationBackgroundSurface.setBounds(bounds);
        /// M: BMW. Add BackgroundSurface for floating stack @{
        if (MultiWindowProxy.isFeatureSupport() 
                    && mStackBackgroundSurface != null){
            mStackBackgroundSurface.setBounds(bounds);
        }
        /// M: [ALPS01876704] Need add to mResizingWindows 
        /// when float stack bounds change
        resizeWindows();
        /// @}
        
        mBounds.set(bounds);

        return true;
    }

    void getBounds(Rect out) {
        out.set(mBounds);
    }

    void updateDisplayInfo() {
        if (mFullscreen && mDisplayContent != null) {
            mDisplayContent.getLogicalDisplayRect(mTmpRect);
            setBounds(mTmpRect);
        }
    }

    boolean isFullscreen() {
        return mFullscreen;
    }

    boolean isAnimating() {
        for (int taskNdx = mTasks.size() - 1; taskNdx >= 0; --taskNdx) {
            final ArrayList<AppWindowToken> activities = mTasks.get(taskNdx).mAppTokens;
            for (int activityNdx = activities.size() - 1; activityNdx >= 0; --activityNdx) {
                final ArrayList<WindowState> windows = activities.get(activityNdx).allAppWindows;
                for (int winNdx = windows.size() - 1; winNdx >= 0; --winNdx) {
                    final WindowStateAnimator winAnimator = windows.get(winNdx).mWinAnimator;
                    if (winAnimator.isAnimating() || winAnimator.mWin.mExiting) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Put a Task in this stack. Used for adding and moving.
     * @param task The task to add.
     * @param toTop Whether to add it to the top or bottom.
     */
    void addTask(Task task, boolean toTop) {
        int stackNdx;
        if (!toTop) {
            stackNdx = 0;
        } else {
            stackNdx = mTasks.size();
            if (!mService.isCurrentProfileLocked(task.mUserId)) {
                // Place the task below all current user tasks.
                while (--stackNdx >= 0) {
                    if (!mService.isCurrentProfileLocked(mTasks.get(stackNdx).mUserId)) {
                        break;
                    }
                }
                // Put it above first non-current user task.
                ++stackNdx;
            }
        }
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG, "addTask: task=" + task + " toTop=" + toTop
                + " pos=" + stackNdx);
        mTasks.add(stackNdx, task);

        task.mStack = this;
        /// M: BMW. Find the 1st appwindow token and init the stack size @{
        /// M. BMW. [ALPS02058853]. Avoid apptoken size is zero since 
        /// ams removeAppToken before remove task in removeActivityFromHistoryLocked  
        if (MultiWindowProxy.isFeatureSupport()
                && mTasks.size() == 1
                && task.mAppTokens.size() > 0) {
            AppWindowToken wtoken = task.mAppTokens.get(0);

            /// Camera dynamically changes the rotation. However, we hope the
            /// floating mode must keep the portrait layout. Therefore, hard
            /// code to keep the orienation value.
            if (wtoken.toString().contains("com.android.camera.CameraLauncher")) {
                initFloatStackSize(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                initFloatStackSize(wtoken.requestedOrientation);
            }
        }
        /// @}   
        mDisplayContent.moveStack(this, true);
        EventLog.writeEvent(EventLogTags.WM_TASK_MOVED, task.taskId, toTop ? 1 : 0, stackNdx);
    }

    void moveTaskToTop(Task task) {
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG, "moveTaskToTop: task=" + task + " Callers="
                + Debug.getCallers(6));
        mTasks.remove(task);
        addTask(task, true);
    }

    void moveTaskToBottom(Task task) {
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG, "moveTaskToBottom: task=" + task);
        mTasks.remove(task);
        addTask(task, false);
    }

    /**
     * Delete a Task from this stack. If it is the last Task in the stack, move this stack to the
     * back.
     * @param task The Task to delete.
     */
    void removeTask(Task task) {
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG, "removeTask: task=" + task);
        mTasks.remove(task);
        if (mDisplayContent != null) {
            if (mTasks.isEmpty()) {
                mDisplayContent.moveStack(this, false);
            }
            mDisplayContent.layoutNeeded = true;
        }
    }

    void attachDisplayContent(DisplayContent displayContent) {
        if (mDisplayContent != null) {
            throw new IllegalStateException("attachDisplayContent: Already attached");
        }

        mDisplayContent = displayContent;
        mDimLayer = new DimLayer(mService, this, displayContent);
        mAnimationBackgroundSurface = new DimLayer(mService, this, displayContent);
        /// M: BMW. Add black background for floating stack @{
        if (MultiWindowProxy.isFeatureSupport() 
                    && mMultiWindowProxy != null 
                    && mMultiWindowProxy.isStackBackgroundEnabled()
                    && mMultiWindowProxy.isFloatingStack(mStackId)){
            mStackBackgroundEnabled = true;   
            mStackBackgroundSurface = new StackBackgroundSurface(mService, this, displayContent);
            mStackBackgroundSurface.prepareSurface(); 
        }
        /// @}
        updateDisplayInfo();
    }

    void detachDisplay() {
        EventLog.writeEvent(EventLogTags.WM_STACK_REMOVED, mStackId);

        boolean doAnotherLayoutPass = false;
        for (int taskNdx = mTasks.size() - 1; taskNdx >= 0; --taskNdx) {
            final AppTokenList appWindowTokens = mTasks.get(taskNdx).mAppTokens;
            for (int appNdx = appWindowTokens.size() - 1; appNdx >= 0; --appNdx) {
                final WindowList appWindows = appWindowTokens.get(appNdx).allAppWindows;
                for (int winNdx = appWindows.size() - 1; winNdx >= 0; --winNdx) {
                    mService.removeWindowInnerLocked(null, appWindows.get(winNdx));
                    doAnotherLayoutPass = true;
                }
            }
        }
        if (doAnotherLayoutPass) {
            mService.requestTraversalLocked();
        }

        mAnimationBackgroundSurface.destroySurface();
        mAnimationBackgroundSurface = null;
        mDimLayer.destroySurface();
        mDimLayer = null;
        /// M: BMW. Disable background for floating stack @{
        if (MultiWindowProxy.isFeatureSupport() 
                    && mStackBackgroundSurface != null) {
            mStackBackgroundSurface.destroySurface();
            mStackBackgroundSurface = null;
        }
        /// @}
        mDisplayContent = null;
    }

    void resetAnimationBackgroundAnimator() {
        mAnimationBackgroundAnimator = null;
        mAnimationBackgroundSurface.hide();
    }

    private long getDimBehindFadeDuration(long duration) {
        TypedValue tv = new TypedValue();
        mService.mContext.getResources().getValue(
                com.android.internal.R.fraction.config_dimBehindFadeDuration, tv, true);
        if (tv.type == TypedValue.TYPE_FRACTION) {
            duration = (long)tv.getFraction(duration, duration);
        } else if (tv.type >= TypedValue.TYPE_FIRST_INT && tv.type <= TypedValue.TYPE_LAST_INT) {
            duration = tv.data;
        }
        return duration;
    }

    boolean animateDimLayers() {
        final int dimLayer;
        final float dimAmount;
        if (mDimWinAnimator == null) {
            dimLayer = mDimLayer.getLayer();
            dimAmount = 0;
        } else {
            dimLayer = mDimWinAnimator.mAnimLayer - WindowManagerService.LAYER_OFFSET_DIM;
            dimAmount = mDimWinAnimator.mWin.mAttrs.dimAmount;
        }
        final float targetAlpha = mDimLayer.getTargetAlpha();
        if (targetAlpha != dimAmount) {
            if (mDimWinAnimator == null) {
                mDimLayer.hide(DEFAULT_DIM_DURATION);
            } else {
                long duration = (mDimWinAnimator.mAnimating && mDimWinAnimator.mAnimation != null)
                        ? mDimWinAnimator.mAnimation.computeDurationHint()
                        : DEFAULT_DIM_DURATION;
                if (targetAlpha > dimAmount) {
                    duration = getDimBehindFadeDuration(duration);
                }
                /// M: BMW. [ALPS01891760]. Disable dimlayer if 
                /// mDimWinAnimator is the bottom window @{
                if ( MultiWindowProxy.isFeatureSupport() 
                        && ((mDimWinAnimator != null && mDimWinAnimator.mWin == getBottomWindow())
                             || getBottomWindow() == null)) {
                    if (DEBUG_STACK) {
                        Slog.v(TAG,"[BMW]mDimWinAnimator is the bottom window, do not show dimlayer");
                    }
                }
                else
                    mDimLayer.show(dimLayer, dimAmount, duration);
                /// @}
            }
        } else if (mDimLayer.getLayer() != dimLayer) {
            mDimLayer.setLayer(dimLayer);
        }
        if (mDimLayer.isAnimating()) {
            if (!mService.okToDisplay()) {
                // Jump to the end of the animation.
                /// M: BMW. [ALPS01891760]. Disable dimlayer if 
                /// mDimWinAnimator is the bottom window @{
                if ( MultiWindowProxy.isFeatureSupport() 
                        && ((mDimWinAnimator != null && mDimWinAnimator.mWin == getBottomWindow())
                            || getBottomWindow() == null)) {
                    if (DEBUG_STACK) {
                        Slog.v(TAG,"[BMW]mDimWinAnimator is the bottom window, do not show dimlayer");
                    }
                }
                else
                    mDimLayer.show();
                /// @}
            } else {
                return mDimLayer.stepAnimation();
            }
        }
        return false;
    }

    void resetDimmingTag() {
        mDimmingTag = false;
    }

    void setDimmingTag() {
        mDimmingTag = true;
    }

    boolean testDimmingTag() {
        return mDimmingTag;
    }

    boolean isDimming() {
        return mDimLayer.isDimming();
    }

    boolean isDimming(WindowStateAnimator winAnimator) {
        return mDimWinAnimator == winAnimator && mDimLayer.isDimming();
    }

    void startDimmingIfNeeded(WindowStateAnimator newWinAnimator) {
        // Only set dim params on the highest dimmed layer.
        final WindowStateAnimator existingDimWinAnimator = mDimWinAnimator;
        // Don't turn on for an unshown surface, or for any layer but the highest dimmed layer.
        if (newWinAnimator.mSurfaceShown && (existingDimWinAnimator == null
                || !existingDimWinAnimator.mSurfaceShown
                || existingDimWinAnimator.mAnimLayer < newWinAnimator.mAnimLayer)) {
            mDimWinAnimator = newWinAnimator;
        }
    }

    void stopDimmingIfNeeded() {
        if (!mDimmingTag && isDimming()) {
            mDimWinAnimator = null;
        }
    }

    void setAnimationBackground(WindowStateAnimator winAnimator, int color) {
        int animLayer = winAnimator.mAnimLayer;
        if (mAnimationBackgroundAnimator == null
                || animLayer < mAnimationBackgroundAnimator.mAnimLayer) {
            mAnimationBackgroundAnimator = winAnimator;
            animLayer = mService.adjustAnimationBackground(winAnimator);
            mAnimationBackgroundSurface.show(animLayer - WindowManagerService.LAYER_OFFSET_DIM,
                    ((color >> 24) & 0xff) / 255f, 0);
        }
    }

    void switchUser(int userId) {
        int top = mTasks.size();
        for (int taskNdx = 0; taskNdx < top; ++taskNdx) {
            Task task = mTasks.get(taskNdx);
            if (mService.isCurrentProfileLocked(task.mUserId)) {
                mTasks.remove(taskNdx);
                mTasks.add(task);
                --top;
            }
        }
    }

    void close() {
        mDimLayer.mDimSurface.destroy();
        mAnimationBackgroundSurface.mDimSurface.destroy();
        /// M: BMW. Destroy black background for floating stack @{
        if (MultiWindowProxy.isFeatureSupport() 
                    && mStackBackgroundSurface != null) {
            mStackBackgroundSurface.mSurfaceControl.destroy();
        }
        /// @}
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.print(prefix); pw.print("mStackId="); pw.println(mStackId);
        pw.print(prefix); pw.print("mDeferDetach="); pw.println(mDeferDetach);
        for (int taskNdx = 0; taskNdx < mTasks.size(); ++taskNdx) {
            pw.print(prefix); pw.println(mTasks.get(taskNdx));
        }
        if (mAnimationBackgroundSurface.isDimming()) {
            pw.print(prefix); pw.println("mWindowAnimationBackgroundSurface:");
            mAnimationBackgroundSurface.printTo(prefix + "  ", pw);
        }
        if (mDimLayer.isDimming()) {
            pw.print(prefix); pw.println("mDimLayer:");
            mDimLayer.printTo(prefix, pw);
            pw.print(prefix); pw.print("mDimWinAnimator="); pw.println(mDimWinAnimator);
        }
        if (!mExitingAppTokens.isEmpty()) {
            pw.println();
            pw.println("  Exiting application tokens:");
            for (int i=mExitingAppTokens.size()-1; i>=0; i--) {
                WindowToken token = mExitingAppTokens.get(i);
                pw.print("  Exiting App #"); pw.print(i);
                pw.print(' '); pw.print(token);
                pw.println(':');
                token.dump(pw, "    ");
            }
        }
        /// M: BMW. Dump more info about multi window@{
        if (MultiWindowProxy.isFeatureSupport()) {
            dumpOthers(prefix, pw);
        }
        /// @}
    }

    @Override
    public String toString() {
        return "{stackId=" + mStackId + " tasks=" + mTasks + "}";
    }

    /// M: BMW. Add for multi window @{
    
    /// M: BMW. Add for multi window floating layout policy @{
    private boolean initFloatStackSize(int orientation) {
        Slog.d(TAG, "[BMW]initFloatStackSize orientation = " + orientation);

        if (mInited) {
            Slog.e(TAG, "[BMW]Floating stack had been inited!");
            return false;
        }
            
        if (MultiWindowProxy.getInstance() == null) {
            Slog.e(TAG, "[BMW]Multi Window Service not ready!");
            return false;
        }
            
        if (!MultiWindowProxy.getInstance().isFloatingStack(mStackId)) {
            Slog.e(TAG, "[BMW]Non floating stack did the function initFloatStackSize");
            return false;
        }

        /// ALPS01496763 : When connecting to HDMI, the stack
        /// bounds will be null. Therefore, avoid the situation
        /// first.
        Rect bounds = new Rect();
        getBounds(bounds);
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                orientation ==
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
            mOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }

        bounds = computeStackSize(mOrientation);
        setBounds(bounds);
        Slog.d(TAG, "[BMW]initFloatStackSize mBounds = " + mBounds);
        mDisplayRotation = mDisplayContent.getDisplayInfo().rotation;
        mXOffset = 0;
        mYOffset = 0;
        mInited = true;
        return true;
    }

    private int deltaRotation(int rotation) {
        int delta = rotation - mDisplayRotation;
        if (delta < 0) delta += 4;
        return delta;
    }


    private void rotateBounds(int rotation, int displayWidth, int displayHeight){
        int pivotX = mBounds.centerX();
        int pivotY = mBounds.centerY();
        int tmpX = 0, tmpY = 0;
        Rect tmpbounds = new Rect();

        switch (deltaRotation(rotation)) {
            case Surface.ROTATION_90:
                // Calculate the pivot point after rotating 90 degree
                tmpX = pivotY;
                tmpY = displayHeight - pivotX;

                // Calculate the top point after rotating 90 degree
                tmpX -= mBounds.width()/2;
                tmpY -= mBounds.height()/2;
                break;
            case Surface.ROTATION_180:
                // Calculate the pivot point after rotating 180 degree
                tmpX = displayWidth - pivotX;
                tmpY = displayHeight - pivotY;
                // Calculate the top point after rotating 180 degree
                tmpX -= mBounds.width()/2;
                tmpY -= mBounds.height()/2;
                break;
            case Surface.ROTATION_270:
                // Calculate the pivot point after rotating 270 degree
                tmpX = displayWidth - pivotY;
                tmpY = pivotX;
                // Calculate the top point after rotating 270 degree
                tmpX -= mBounds.width()/2;
                tmpY -= mBounds.height()/2;
                break;
            case Surface.ROTATION_0:
            default:
                Slog.e(TAG, "[BMW]rotateBounds exception, rotation = " + rotation
                                + ", mDisplayRotation = " + mDisplayRotation);
                break;

        }
        /// M: use tmpbounds replace mBounds.fix set mBounds too early 
        ///then dimlayer can't be seted when rotate[ALPS01882560]
        tmpbounds.set(mBounds);
        tmpbounds.offsetTo(tmpX, tmpY);

        verifyStackBounds(tmpbounds);
        setBounds(tmpbounds);
        /// DimLayer control can be better in future.
        if (mDimLayer.isDimming()) {
            mDimLayer.hide();
            mDimLayer.show();
        }
        mDisplayRotation = rotation;

    }

    private void computeBoundaryLimit() {
        DisplayInfo displayInfo = mDisplayContent.getDisplayInfo();
        int stackBoundsMarginDp = 220; //mMultiWindowProxy.getStackBoundsMarginDp();
        int stacksOffsetDp = 50;//mMultiWindowProxy.getStacksOffsetDp();
        int floatDecorHeightDp = 44;//mMultiWindowProxy.getFloatDecorHeightDp();
            
        mLeftBoundLimit = stackBoundsMarginDp * displayInfo.logicalDensityDpi / 160;
        mRightBoundLimit = displayInfo.appWidth - mLeftBoundLimit;
        
        //ALPS01669346 Compute top bound and bottom bound limit @{
        Rect tempContent = new Rect();
        mDisplayContent.mService.mPolicy.getContentRectLw(tempContent);
        mTopBoundLimit = tempContent.top;
        mBottomBoundLimit = tempContent.bottom
                - floatDecorHeightDp * displayInfo.logicalDensityDpi / 160;

        mRightBoundLimitFirstLaunch = displayInfo.appWidth;
        mBottomBoundLimitFirstLaunch = tempContent.bottom;
        
        WindowState win;
        mTopFloatStack = null;
        MultiWindowProxy multiWindowProxy = MultiWindowProxy.getInstance();
        for (int i = mDisplayContent.getWindowList().size() - 1 ; i >= 0 ; i--) {
            win = mDisplayContent.getWindowList().get(i);
            if (multiWindowProxy == null)
                break;
            
            if (mTopFloatStack == null && win.isWinVisibleLw()
                    && multiWindowProxy.isFloatingStack(win.getStack().mStackId)
                    && win.mAppToken != null 
                    && !multiWindowProxy.isInMiniMax(win.mAppToken.groupId)) {
                mTopFloatStack = win.getStack();
                break;
            }
        }
        
        /// @}
        mStacksOffset = stacksOffsetDp * displayInfo.logicalDensityDpi /160;
        if (DEBUG_STACK) {
            Slog.d(TAG, "[BMW]computeBoundaryLimit mTopBoundLimit = "
                                + mTopBoundLimit
                                + ", mBottomBoundLimit = " + mBottomBoundLimit
                                + ", mTopFloatStack = " + mTopFloatStack
                                + ", mStacksOffset = " + mStacksOffset);
        }
    }
    
    private Rect computeStackSize(int orientation) {
        
        Rect stackSize = new Rect();
        DisplayInfo displayInfo = mDisplayContent.getDisplayInfo();

        if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                || orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
            if (mFloatStackPortWidth == 0 && mFloatStackPortHeight == 0) {
                if (mDisplayContent.mInitialDisplayWidth < mDisplayContent.mInitialDisplayHeight) {
                    mFloatStackPortHeight = mDisplayContent.mInitialDisplayHeight/2;
                } else {
                    mFloatStackPortHeight = mDisplayContent.mInitialDisplayWidth/2;
                }
                mFloatStackPortWidth = (mFloatStackPortHeight*3)/4;
            }
            stackSize.set(0, 0, mFloatStackPortWidth, mFloatStackPortHeight);
            stackSize.offsetTo((displayInfo.logicalWidth-mFloatStackPortWidth)/2,
                    (displayInfo.logicalHeight-mFloatStackPortHeight)/2);
        } else {
            if (mFloatStackLandWidth == 0 && mFloatStackLandHeight == 0) {
                if (mDisplayContent.mInitialDisplayWidth < mDisplayContent.mInitialDisplayHeight) {
                    mFloatStackLandWidth = mDisplayContent.mInitialDisplayHeight/2;
                } else {
                    mFloatStackLandWidth = mDisplayContent.mInitialDisplayWidth/2;
                }
                mFloatStackLandHeight = (mFloatStackLandWidth*3)/4;
            }
            stackSize.set(0, 0, mFloatStackLandWidth, mFloatStackLandHeight);
            stackSize.offsetTo((displayInfo.logicalWidth-mFloatStackLandWidth)/2,
                    (displayInfo.logicalHeight-mFloatStackLandHeight)/2);
        }


        /// decide the proper the top position.
        computeBoundaryLimit();

        if (mTopFloatStack != null) {
            Rect bound = new Rect();
            mTopFloatStack.getBounds(bound);

            int left, top;
            left = bound.left + mStacksOffset;
            top = bound.top + mStacksOffset;
            if (left > mRightBoundLimit || 
                (!mInited && (left + stackSize.width()) > mRightBoundLimitFirstLaunch)) {
                left = 0;
            }
            if (top > mBottomBoundLimit || 
                (!mInited && (top + stackSize.height()) > mBottomBoundLimitFirstLaunch)) {
                top = mTopBoundLimit;
            }
            stackSize.offsetTo(left, top);
        }
        if (DEBUG_STACK) {
            Slog.d(TAG, "[BMW]computeStackSize boxSize = " + stackSize);
        }

        return stackSize;
    }

    // The api is designed for the StackBox
    // if the founds is outside the display scope,
    // it should be back to the display content.
    // Based on the UX spec, the left and right margin
    // is 220 dp
    private void verifyStackBounds(Rect bounds) {
    
        DisplayInfo displayInfo = mDisplayContent.getDisplayInfo();
        computeBoundaryLimit();

        if (bounds.right < mLeftBoundLimit) {
            bounds.offsetTo(bounds.left + (mLeftBoundLimit - bounds.right), bounds.top);
        }

        if (bounds.left > mRightBoundLimit) {
            bounds.offsetTo(mRightBoundLimit, bounds.top);
        }

        if (bounds.top < mTopBoundLimit) {
            bounds.offsetTo(bounds.left, mTopBoundLimit);
        }

        if (bounds.top > mBottomBoundLimit) {
            bounds.offsetTo(bounds.left, mBottomBoundLimit);
        }

        if (bounds.width() > displayInfo.appWidth) {
            bounds.right -= bounds.width() - displayInfo.appWidth;
        }

        if (bounds.height() > displayInfo.appHeight) {
            bounds.bottom -= bounds.height() - displayInfo.appHeight;
        }
    }
    /// M: BMW. private function @{

    public Rect getStackBounds(int rotation, int displayWidth, int displayHeight) {
        if (mDisplayRotation == rotation) {
            return mBounds;
        }

        /// Need to rotate the mBounds
        rotateBounds(rotation, displayWidth, displayHeight);

        return mBounds;

    }

    public void adjustFloatingRect(int xOffset, int yOffset) {
        if (mXOffset != xOffset || mYOffset != yOffset) {
            Rect bounds = new Rect();
            bounds.set(mBounds);
            bounds.left += xOffset;
            bounds.right += xOffset;
            bounds.top += yOffset;
            bounds.bottom += yOffset;
            mDimLayer.setBounds(bounds);
        }
        mXOffset = xOffset;
        mYOffset = yOffset;
    }

    /// M: @{
    public void getStackOffsets(int[] offsets){
        if (offsets == null || offsets.length < 2) {
            throw new IllegalArgumentException("offsets must be an array of two integers");
        }
        offsets[0] = mXOffset;
        offsets[1] = mYOffset;
    }
    /// @}
    
    public void dumpOthers(String prefix, PrintWriter pw) {
        pw.print(prefix); pw.print("FLOATING LAYOUT POLICY INFO:");
        pw.print(" "); pw.print("mDisplayRotation="); pw.print(mDisplayRotation);
        pw.print(", "); pw.print("Launch Mode=");
        if (mOrientation ==
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                || mOrientation ==
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
            pw.print("Portrait");
        } else {
            pw.print("Landscape");
        }
        pw.print(", "); pw.print("mXOffset=");pw.print(mXOffset);
        pw.print(", "); pw.print("mYOffset=");pw.print(mYOffset);
        pw.print(", "); pw.print("mFloatStackPortWidth="); pw.print(mFloatStackPortWidth);
        pw.print(", "); pw.print("mFloatStackPortHeight="); pw.print(mFloatStackPortHeight);
        pw.print(", "); pw.print("mFloatStackLandWidth="); pw.print(mFloatStackLandWidth);
        pw.print(", "); pw.print("mFloatStackLandHeight="); pw.print(mFloatStackLandHeight);
        pw.print(", "); pw.print("mTopBoundLimit="); pw.print(mTopBoundLimit);
        pw.print(", "); pw.print("mBottomBoundLimit="); pw.print(mBottomBoundLimit);
        pw.print(", "); pw.print("mRightBoundLimit="); pw.print(mRightBoundLimit);
        pw.print(", "); pw.print("mLeftBoundLimit="); pw.print(mLeftBoundLimit);
        pw.print(", "); pw.print("mStacksOffset="); pw.print(mStacksOffset);
        pw.print(", "); pw.print("mTopFloatStack="); pw.println(mTopFloatStack);
        pw.println();

    }
    /// M: BMW. Add for multi window floating layout policy @}

    /// M: BMW. Add black background for floating stack. @{
    void setStackBackground(boolean forceShow) {
        if (!needShowStackBackground()){
            resetStackBackgroundAnimator();
            return;
        }        
        WindowStateAnimator winAnimator = adjustStackBackgroundAnimator();
        if (DEBUG_STACK) Slog.v(TAG, "[BMW]setStackBackground winAnimator = " + winAnimator);
        if (winAnimator == null){
            resetStackBackgroundAnimator();
            return;
        }
        boolean needUpdata = false;
        if (mStackBackgroundAnimator== null 
                || winAnimator != mStackBackgroundAnimator) {
            mStackBackgroundAnimator = winAnimator;
            needUpdata = true;
        }
        int animLayer = adjustStackBackgroundLayer();
        if (animLayer < 0) {
            animLayer = mStackBackgroundAnimator.mAnimLayer;
        }
        mStackBackgroundSurface.setLayer(animLayer-4);
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]setStackBackground mStackBackgroundAnimator = " + mStackBackgroundAnimator
             + ", animLayer:" + animLayer);
        }
        if (forceShow)
            mStackBackgroundSurface.show();
    }
    
     /// M: BMW. Find a WindowStateAnimator for 
     /// controlling the size, pos, matrix of mStackBackgroundSurface
     private WindowStateAnimator adjustStackBackgroundAnimator() {
        WindowList windows = mDisplayContent.getWindowList();
        for (int winNdx = windows.size() - 1; winNdx >= 0; winNdx--) {
            final WindowState win = windows.get(winNdx);
            if (win.mAppToken == null)
                continue;
            Task task = mService.mTaskIdToTask.get(win.mAppToken.groupId);
            if (task == null || (task != null && task.mStack == null))
                continue;
            int stackId = task.mStack.mStackId;
            if (win.isFullFloatWindow() && stackId == mStackId && win.isVisibleOrBehindKeyguardLw()) {
                if (DEBUG_STACK) Slog.v(TAG, "[BMW]adjustStackBackgroundAnimator WinAnimator:" 
                            + win.mWinAnimator);
                return win.mWinAnimator;    
            }
        }
        return null;
    }

    /// M: BMW. Compute the layer of mStackBackgroundSurface
    private int adjustStackBackgroundLayer() {
        WindowList windows = mDisplayContent.getWindowList();
        for (int winNdx = 0; winNdx < windows.size(); winNdx++) {
            final WindowState win = windows.get(winNdx);
            if (win.mAppToken == null)
                continue;
            Task task = mService.mTaskIdToTask.get(win.mAppToken.groupId);
            if (task == null || (task!=null && task.mStack == null))
                continue;
            int stackId = task.mStack.mStackId;
            if (win.isVisibleNow() && stackId == mStackId){
                if (DEBUG_STACK) Slog.v(TAG, "[BMW]adjustStackBackgroundLayer AnimLayer:" 
                            + win.mWinAnimator.mAnimLayer);
                return win.mWinAnimator.mAnimLayer; 
            }
        }
        return -1;
    }

    /// M: BMW. 
    void resetStackBackgroundAnimator() {
        if (mStackBackgroundSurface != null)
            mStackBackgroundSurface.hide();
    }
    
    /// M: BMW. Temp Solution. Check if the TaskStack need to show black background
    private boolean needShowStackBackground(){
        if (MultiWindowProxy.getInstance() == null 
                || !mStackBackgroundEnabled
                || !MultiWindowProxy.getInstance().isFloatingStack(mStackId)){
            return false;
        }
        for (int taskNdx = mTasks.size() - 1; taskNdx >= 0; --taskNdx) {
            final AppTokenList tokens = mTasks.get(taskNdx).mAppTokens;
            for (int tokenNdx = tokens.size() - 1; tokenNdx >= 0; --tokenNdx) {
                final WindowList windows = tokens.get(tokenNdx).allAppWindows;
                for (int winNdx = windows.size() - 1; winNdx >= 0; --winNdx) {
                    final WindowState win = windows.get(winNdx);
                    if (win.mAttrs.getTitle() != null 
                            && win.mAttrs.getTitle().toString().contains("SurfaceView")
                            && win.isVisibleOrBehindKeyguardLw()) {
                         return true;
                    }
                }
            }
        }
        return false;
    }
        
    /// M: BMW. mStackBackgroundSurface will be updated synchronously
    /// when mStackBackgroundAnimator's position changed.
    void onWinPositionChanged(WindowStateAnimator winAnimator, float left, float top){
    
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]onSurfacePositionChanged winAnimator:"+winAnimator
                        + ", left:" + left + ", top:" + top);
        }
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
    
        if (winAnimator == mStackBackgroundAnimator){
            mStackBackgroundSurface.setPosition(left, top);
        }
    }

    /// M: BMW. mStackBackgroundSurface will be updated synchronously
    /// when mStackBackgroundAnimator's size changed.
    void onWinSizeChanged(WindowStateAnimator winAnimator, int w, int h){
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]onSurfaceSizeChanged winAnimator:"+winAnimator
                        + ", w:" + w + ", h:" + h);
        }
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
        if (winAnimator == mStackBackgroundAnimator){
            mStackBackgroundSurface.setSize(w, h);
        }
    }
    /// M: BMW. mStackBackgroundSurface will be updated synchronously
    /// when mStackBackgroundAnimator's matrix changed.
    void onWinMatrixChanged(WindowStateAnimator winAnimator, 
                    float dsdx, float dtdx, float dsdy, float dtdy){
        
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]onSurfaceMatrixChanged winAnimator:" + winAnimator
                        + ", dsdx:" + dsdx + ", dtdx:" + dtdx
                        + ", dsdy:" + dsdy + ", dtdy:" + dtdy);
        }
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
        if (winAnimator == mStackBackgroundAnimator){
            mStackBackgroundSurface.setMatrix( dsdx,  dtdx,  dsdy,  dtdy);
        }
    }
    /// M: BMW. mStackBackgroundSurface will be updated synchronously
    /// when mStackBackgroundAnimator's windowCrop changed.
    void onWinCropChanged(WindowStateAnimator winAnimator, Rect crop){
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]onSurfaceCropChanged winAnimator:" + winAnimator
                        + ", crop:" + crop);
        }
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
        if (winAnimator == mStackBackgroundAnimator){
            mStackBackgroundSurface.setWindowCrop(crop);
        }
    }
    
    /// M: BMW. mStackBackgroundSurface will be show synchronously
    /// when mStackBackgroundAnimator has shown
    void onWinShown(WindowStateAnimator winAnimator) {
        if (DEBUG_STACK) Slog.v(TAG, "[BMW]onSurfaceShown winAnimator:"+winAnimator);
        setStackBackground(true);
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
        if (winAnimator == mStackBackgroundAnimator){			
            /// M: BMW. [ALPS02018327] Update Background position/size/matix before show {@
            mStackBackgroundSurface.setPosition(mStackBackgroundAnimator.mSurfaceX, mStackBackgroundAnimator.mSurfaceY);
            mStackBackgroundSurface.setSize((int)mStackBackgroundAnimator.mSurfaceW, (int)mStackBackgroundAnimator.mSurfaceH);
            mStackBackgroundSurface.setMatrix(
                            mStackBackgroundAnimator.mDsDx * mStackBackgroundAnimator.mWin.mHScale,
                            mStackBackgroundAnimator.mDtDx * mStackBackgroundAnimator.mWin.mVScale,
                            mStackBackgroundAnimator.mDsDy * mStackBackgroundAnimator.mWin.mHScale, 
                            mStackBackgroundAnimator.mDtDy * mStackBackgroundAnimator.mWin.mVScale);
            /// @}
            mStackBackgroundSurface.show();
        }
    }

    /// M: BMW. mStackBackgroundSurface will be hide synchronously
    /// when mStackBackgroundAnimator has hiden
    void onWinHiden(WindowStateAnimator winAnimator) {
        if (DEBUG_STACK) Slog.v(TAG, "[BMW]onSurfaceHiden winAnimator:"+winAnimator);
        setStackBackground(false);
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
        if (winAnimator == mStackBackgroundAnimator){           
            mStackBackgroundSurface.hide();
        }
    }
    ///M: add black background for floating stack. @}


    /// M: BMW. [ALPS01891760]. Add getBottomWindow for disable dimlayer {@
    WindowState getBottomWindow() {
         WindowState win = null; 
         if (mTasks.size() > 0 ) {
             Task task = mTasks.get(0);
             for (int tokenNdx = 0; tokenNdx < task.mAppTokens.size(); tokenNdx++) {
                AppWindowToken token = task.mAppTokens.get(tokenNdx);
                for (int winNdx = 0; winNdx < token.allAppWindows.size(); winNdx++) {
                    win = token.allAppWindows.get(winNdx);
                    if (win != null) {
                        if (DEBUG_STACK) Slog.v(TAG,"[BMW]getBottomWindow win:" + win);
                        return win;
                    }
                }
             }           
         }       
         return win;
    }
    ///@}

    /// M: BMW. Assume floating stack is at the portrait mode.
    private int mOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    private int mDisplayRotation = Surface.ROTATION_0;
    private int mXOffset, mYOffset;

    private TaskStack mTopFloatStack = null;
    private int mFloatStackPortWidth = 0;
    private int mFloatStackPortHeight = 0;
    private int mFloatStackLandWidth = 0;
    private int mFloatStackLandHeight = 0;
    private int mTopBoundLimit = 0, mBottomBoundLimit = 0;
    private int mRightBoundLimit = 0, mLeftBoundLimit = 0;
    private int mRightBoundLimitFirstLaunch = 0, mBottomBoundLimitFirstLaunch = 0;
    private int mStacksOffset = 0;
    private boolean mInited = false;
/*
    final private static int STACK_BOUNDS_MARGIN_DP = 220;
    final private static int STACKS_OFFSET_MARGIN_DP = 50;
    final private static int FLOAT_CONTROL_BAR_HEIGHT_DP = 44;
*/
    /// M: BMW. Add black background for TaskStack(Floating) @{
    WindowStateAnimator mStackBackgroundAnimator;
    StackBackgroundSurface mStackBackgroundSurface;
    MultiWindowProxy mMultiWindowProxy = MultiWindowProxy.getInstance();
    private boolean mStackBackgroundEnabled = false; 
    /// @}
    
    /// M: add for BMW @}

}