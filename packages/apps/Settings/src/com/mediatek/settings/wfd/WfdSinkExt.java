/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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

package com.mediatek.settings.wfd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.view.Surface;
import android.widget.Toast;

import com.android.settings.R;
import com.mediatek.settings.FeatureOption;
import com.mediatek.xlog.Xlog;

public class WfdSinkExt {
    private static final String TAG = "WfdSinkExt";

    private static final String ACTION_WFD_PORTRAIT = "com.mediatek.wfd.portrait";

    private Context mContext;
    private DisplayManager mDisplayManager;

    // WFD sink supported
    private static final String WFD_NAME = "wifi_display";
    private static final String KEY_WFD_SINK_GUIDE = "wifi_display_hide_guide";
    private WfdSinkSurfaceFragment mSinkFragment;
    private Toast mSinkToast;
    private int mPreWfdState = -1;
    private boolean mUiPortrait = false;

    public WfdSinkExt() {

    }
    public WfdSinkExt(Context context) {
        mContext = context;
        mDisplayManager = (DisplayManager) mContext
                .getSystemService(Context.DISPLAY_SERVICE);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Xlog.v(TAG, "receive action: " + action);
            if (DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED
                    .equals(action)) {
                handleWfdStatusChanged(mDisplayManager.getWifiDisplayStatus());
            } else if (ACTION_WFD_PORTRAIT.equals(action)) {
                mUiPortrait = true;
            }
        }
    };

    /**
     * Called when activity started
     */
    public void onStart() {
        Xlog.d(TAG, "onStart");
        if (FeatureOption.MTK_WFD_SINK_SUPPORT) {
            WifiDisplayStatus wfdStatus = mDisplayManager
                    .getWifiDisplayStatus();
            handleWfdStatusChanged(wfdStatus);
            IntentFilter filter = new IntentFilter();
            filter.addAction(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED);
            filter.addAction(ACTION_WFD_PORTRAIT);
            mContext.registerReceiver(mReceiver, filter);
        }
    }

    /**
     * Called when activity stopped
     */
    public void onStop() {
        Xlog.d(TAG, "onStop");
        if (FeatureOption.MTK_WFD_SINK_SUPPORT) {
            mContext.unregisterReceiver(mReceiver);
        }
    }

    /**
     * Setup WFD sink connection, called when WFD sink surface is available
     * @param surface
     */
    public void setupWfdSinkConnection(Surface surface) {
        Xlog.d(TAG, "setupWfdSinkConnection");
        setWfdMode(true);
        waitWfdSinkConnection(surface);
    }

    /**
     * Disconnect WFD sink connection, called when WFD sink surface will exit
     */
    public void disconnectWfdSinkConnection() {
        Xlog.d(TAG, "disconnectWfdSinkConnection");
        mDisplayManager.disconnectWifiDisplay();
        setWfdMode(false);
        Xlog.d(TAG, "after disconnectWfdSinkConnection");
    }

    public void registerSinkFragment(WfdSinkSurfaceFragment fragment) {
        mSinkFragment = fragment;
    }

    private void handleWfdStatusChanged(WifiDisplayStatus status) {
        boolean bStateOn = (status != null && status.getFeatureState() == WifiDisplayStatus.FEATURE_STATE_ON);
        Xlog.d(TAG, "handleWfdStatusChanged bStateOn: " + bStateOn);
        if (bStateOn) {
            int wfdState = status.getActiveDisplayState();
            Xlog.d(TAG, "handleWfdStatusChanged wfdState: " + wfdState);
            // if (mPreWfdState != wfdState) {
            handleWfdStateChanged(wfdState, isSinkMode());
            mPreWfdState = wfdState;
            // }
        } else {
            // if (mPreWfdState != -1) {
            handleWfdStateChanged(
                    WifiDisplayStatus.DISPLAY_STATE_NOT_CONNECTED,
                    isSinkMode());
            // }
            mPreWfdState = -1;
        }
    }

    private void handleWfdStateChanged(int wfdState, boolean sinkMode) {
        switch (wfdState) {
        case WifiDisplayStatus.DISPLAY_STATE_NOT_CONNECTED:
            if (sinkMode) {
                Xlog.d(TAG, "dismiss fragment");
                if (mSinkFragment != null) {
                    mSinkFragment.dismissAllowingStateLoss();
                }
                setWfdMode(false);
            }
            if (mPreWfdState == WifiDisplayStatus.DISPLAY_STATE_CONNECTED) {
                showToast(false);
            }
            mUiPortrait = false;
            break;
        case WifiDisplayStatus.DISPLAY_STATE_CONNECTING:
            break;
        case WifiDisplayStatus.DISPLAY_STATE_CONNECTED:
            if (sinkMode) {
                Xlog.d(TAG, "mUiPortrait: " + mUiPortrait);
                mSinkFragment.requestOrientation(mUiPortrait);
                SharedPreferences preferences = mContext.getSharedPreferences(WFD_NAME, Context.MODE_PRIVATE);
                boolean showGuide = preferences.getBoolean(KEY_WFD_SINK_GUIDE, true);
                if (showGuide) {
                    if (mSinkFragment != null) {
                        mSinkFragment.addWfdSinkGuide();
                        preferences.edit()
                                .putBoolean(KEY_WFD_SINK_GUIDE, false).commit();
                    }
                }
                if (mPreWfdState != WifiDisplayStatus.DISPLAY_STATE_CONNECTED) {
                    showToast(true);
                }
            }
            mUiPortrait = false;
            break;
        default:
            break;
        }
    }

    private void showToast(boolean connected) {
        if (mSinkToast != null) {
            mSinkToast.cancel();
        }
        mSinkToast = Toast.makeText(mContext,
                connected ? R.string.wfd_sink_toast_enjoy
                        : R.string.wfd_sink_toast_disconnect,
                connected ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mSinkToast.show();
    }

    private boolean isSinkMode() {
        return mDisplayManager.isSinkEnabled();
    }

    private void setWfdMode(boolean sink) {
        Xlog.d(TAG, "setWfdMode " + sink);
        mDisplayManager.enableSink(sink);
    }

    private void waitWfdSinkConnection(Surface surface) {
        mDisplayManager.waitWifiDisplayConnection(surface);
    }

    public void sendUibcEvent(String eventDesc) {
        mDisplayManager.sendUibcInputEvent(eventDesc);
    }

}
