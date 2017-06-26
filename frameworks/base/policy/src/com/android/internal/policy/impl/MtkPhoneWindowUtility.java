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
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.Slog;
import android.view.IWindowManager;
import android.view.WindowManager;
import android.view.WindowManagerPolicy.WindowState;
import android.view.WindowManagerPolicy.WindowManagerFuncs;

//import com.mediatek.common.featureoption.FeatureOption;

import static android.view.WindowManager.LayoutParams.*;

public class MtkPhoneWindowUtility{
    static final String TAG = "WindowManager";

    static boolean DEBUG = false;
    static boolean DEBUG_LAYOUT = true;

    private static final int MSG_MTK_POLICY = 1000;
    private static final int MSG_ENABLE_FLOATING_MONITOR = MSG_MTK_POLICY + 0;
    private static final int MSG_DISABLE_FLOATING_MONITOR = MSG_MTK_POLICY + 1;

    Handler mHandler = new MtkPolicyHandler();
    WindowState mFocusedWindow;
    Context mContext;

    WindowManagerFuncs mWindowManagerFuncs;

    public MtkPhoneWindowUtility(Context context,
                WindowManagerFuncs windowManagerFuncs) {
        mContext = context;
        mWindowManagerFuncs = windowManagerFuncs;
    }

    public void updateRect(int left, int top, int right, int bottom) {
        if (mFloatingMonitorEventListener != null) {
            mFloatingMonitorEventListener.updateMonitorRect(left,
                    top, right, bottom);
        }
    }

    private class MtkPolicyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                /// M : Handle Messages "MSG_ENABLE_FLOATING_MONITOR" @{
                case MSG_ENABLE_FLOATING_MONITOR:
                    enableFloatingMonitor();
                    break;
                /// @}
                /// M : Handle Messages "MSG_DISABLE_FLOATING_MONITOR" @{
                case MSG_DISABLE_FLOATING_MONITOR:
                    disableFloatingMonitor();
                    break;
                /// @}
            }
        }
    }


    /// M: Init mFloatingMonitorEventListener as null
    FloatingMonitorPointerEventListener mFloatingMonitorEventListener = null;

    /// M: enable the floating window monitor @{
    private void enableFloatingMonitor() {
        if (mFloatingMonitorEventListener != null) {
            /// do nothing
        } else {
            mFloatingMonitorEventListener =
                new FloatingMonitorPointerEventListener(mContext, mWindowManagerFuncs);
            mWindowManagerFuncs.registerPointerEventListener(mFloatingMonitorEventListener);
        }
        mFloatingMonitorEventListener.updatFocusWindow(mFocusedWindow);

    }
    /// @}

    /// M: disable the floating window monitor @{
    private void disableFloatingMonitor() {
        if (mFloatingMonitorEventListener != null) {
            mWindowManagerFuncs.unregisterPointerEventListener(
            mFloatingMonitorEventListener);
            mFloatingMonitorEventListener = null;
        }
    }
    /// @}

    /// M: When the focus window is belong to the floating window, enable
    /// the floating monitor. Otherwise, disable it. @{
    void updateFocus2FloatMonitor(WindowState focusWindow) {
        mFocusedWindow = focusWindow;
        if (mFocusedWindow != null
                && mFocusedWindow.getAttrs().type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION
                && mFocusedWindow.isFullFloatWindow() ) {
            mHandler.sendEmptyMessage(MSG_ENABLE_FLOATING_MONITOR);
        } else {
            mHandler.sendEmptyMessage(MSG_DISABLE_FLOATING_MONITOR);
        }
    }
    /// @}
}
