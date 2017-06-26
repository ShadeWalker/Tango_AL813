/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.provider.Settings.Global;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSTile;

import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.systemui.ext.BehaviorSet;
import com.mediatek.systemui.ext.PluginFactory;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;

/** Quick settings tile: Airplane mode **/
public class AirplaneModeTile extends QSTile<QSTile.BooleanState> {
    /// M: For debug
    private static final String TAG = "AirplaneModeTile";
    private static final boolean DEBUG = true;

    private final AnimationIcon mEnable =
            new AnimationIcon(R.drawable.ic_signal_airplane_enable_animation);
    private final AnimationIcon mDisable =
            new AnimationIcon(R.drawable.ic_signal_airplane_disable_animation);
    private final GlobalSetting mSetting;

    private boolean mListening;
    /// M: add flightMode sync for CT @{
    private boolean mAirplaneMode;
    private boolean mAirplaneModeReceived;
    /// M: add flightMode sync for CT @}
    // / M: ALPS02085793 Refuse user's operating if airplane mode doesn't switch done@{
    private static final String INTENT_ACTION_AIRPLANE_CHANGE_DONE =
            "com.mediatek.intent.action.AIRPLANE_CHANGE_DONE";
    private static final String EXTRA_AIRPLANE_MODE = "airplaneMode";
    private boolean mRealAirplaneMode;
    private boolean mAirplaneModeChangedDone;
    private boolean mClicked;
    // @}
    public AirplaneModeTile(Host host) {
        super(host);

        mSetting = new GlobalSetting(mContext, mHandler, Global.AIRPLANE_MODE_ON) {
            @Override
            protected void handleValueChanged(int value) {
                if (DEBUG) {
                     Log.d(TAG, "handleValueChanged: " + value);
                }
                /// M: refreshState just receive intent, add flightMode sync for CT, @{
                if (PluginFactory.getStatusBarPlugin(mContext).customizeBehaviorSet()
                        == BehaviorSet.OP09_BS) {
                    Log.d(TAG, "handleValueChanged, op flow, not refresh and return");
                    return;
                }
                /// M: add flightMode sync for CT @}
                /// M: Fix potential race condition
                refreshState();
                //handleRefreshState(value);
            }
        };
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        // / M: ALPS02085793 Refuse user's operating if airplane mode doesn't switch done@{
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            boolean isAllowAirplaneModeChange = TelephonyManagerEx.getDefault()
                    .isAllowAirplaneModeChange();
            if (!isAllowAirplaneModeChange || mClicked) {
                Log.d(TAG, "handleClick() isnot allow airplane mode change, isAllowAirplaneModeChange: " + isAllowAirplaneModeChange);
                return;
            }
        }
        // @}
        setEnabled(!mState.value);
        mEnable.setAllowAnimation(true);
        mDisable.setAllowAnimation(true);

        // / M: ALPS02085793 Refuse user's operating if airplane mode doesn't switch done@{
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            mClicked = true;
            refreshState();
        }
        // @}

    }

    private void setEnabled(boolean enabled) {
        final ConnectivityManager mgr =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mgr.setAirplaneMode(enabled);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer)arg : mSetting.getValue();
        boolean airplaneMode = value != 0;
        if (DEBUG) {
            Log.d(TAG, "handleUpdateState: " + airplaneMode + ", " + value + ", " + arg);
        }
        /// M: add flightMode sync for CT @{
        if (PluginFactory.getStatusBarPlugin(mContext).customizeBehaviorSet()
                == BehaviorSet.OP09_BS && mAirplaneModeReceived) {
            Xlog.d(TAG, "handleUpdateState(), op flow, mAirplaneMode: " + mAirplaneMode);
            state.value = mAirplaneMode;
            airplaneMode = mAirplaneMode;
            mAirplaneModeReceived = false;
        } else {
            Xlog.d(TAG, "handleUpdateState(), common flow");
        /// M: add flightMode sync for CT @}
        state.value = airplaneMode;
        }

        // / M: ALPS02085793 Refuse user's operating if airplane mode doesn't switch done@{
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && mAirplaneModeChangedDone) {
            Xlog.d(TAG, "handleUpdateState(), mRealAirplaneMode: " + mRealAirplaneMode);
            state.value = mRealAirplaneMode;
            airplaneMode = mRealAirplaneMode;
            mAirplaneModeChangedDone = false;
            mClicked = false;
        } else {
            Xlog.d(TAG, "handleUpdateState(), common flow airplaneMode: " + airplaneMode);
        // @}
        state.value = airplaneMode;
        }

        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_airplane_mode_label);
        if (airplaneMode) {
            state.icon = mEnable;
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_airplane_on);
        } else {
            state.icon = mDisable;
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_airplane_off);
        }
        // / M: ALPS02085793 Refuse user's operating if airplane mode doesn't switch done@{
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            boolean isAllowAirplaneModeChange = TelephonyManagerEx.getDefault()
                    .isAllowAirplaneModeChange();
            if (!isAllowAirplaneModeChange || mClicked) {
                Log.d(TAG, "handleUpdateState: isnot allow airplane mode change, isAllowAirplaneModeChange: " + isAllowAirplaneModeChange);
                state.icon = ResourceIcon.get(R.drawable.ic_signal_airplane_swiching);
            }
        }
        // @}
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_airplane_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_airplane_changed_off);
        }
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            // / M: ALPS02085793 Refuse user's operating if airplane mode doesn't switch done@{
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                filter.addAction(INTENT_ACTION_AIRPLANE_CHANGE_DONE);
                mClicked = false;
            }
            // @}
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mContext.unregisterReceiver(mReceiver);
        }
        mSetting.setListening(listening);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
                /// M: add flightMode sync for CT @{
                Xlog.d(TAG, "onReceive(), intent: " + intent.getAction());
                mAirplaneMode = intent.getBooleanExtra("state", false);
                mAirplaneModeReceived = true;
                Xlog.d(TAG, "updateAirplaneMode: intent state= " + mAirplaneMode);
                /// M: add flightMode sync for CT @}
                refreshState();
            } else if (INTENT_ACTION_AIRPLANE_CHANGE_DONE.equals(intent.getAction())) {
                // / M: ALPS02085793 Refuse user's operating if airplane mode doesn't switch done@{
                mRealAirplaneMode = intent.getBooleanExtra(EXTRA_AIRPLANE_MODE, false);
                mAirplaneModeChangedDone = true;
                Log.d(TAG, "onReceive() AIRPLANE_CHANGE_DONE,  mRealAirplaneMode= " + mRealAirplaneMode);
                refreshState();
                // @}
            }
        }
    };
}
