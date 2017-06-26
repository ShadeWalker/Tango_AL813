/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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
import android.provider.Settings.Global;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.UsageTracker;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.KeyguardMonitor;

/** Quick settings tile: Hotspot **/
public class HotspotTile extends QSTile<QSTile.BooleanState> {
    private final AnimationIcon mEnable =
            new AnimationIcon(R.drawable.ic_hotspot_enable_animation);
    private final AnimationIcon mDisable =
            new AnimationIcon(R.drawable.ic_hotspot_disable_animation);
    private final HotspotController mController;
    private final Callback mCallback = new Callback();
    private final UsageTracker mUsageTracker;
    private final KeyguardMonitor mKeyguard;

    /// M: Support "Hotspot can not be turned on on Aeroplane mode". @{
    private final GlobalSetting mSetting;
    private boolean mListening = false;
    /// M: Support "Hotspot can not be turned on on Aeroplane mode". @}

    public HotspotTile(Host host) {
        super(host);
        mController = host.getHotspotController();
        mUsageTracker = newUsageTracker(host.getContext());
        mUsageTracker.setListening(true);
        mKeyguard = host.getKeyguardMonitor();

        /// M: Support "Hotspot can not be turned on on Aeroplane mode". @{
        mSetting = new GlobalSetting(mContext, mHandler, Global.AIRPLANE_MODE_ON) {
            @Override
            protected void handleValueChanged(int value) {
                handleRefreshState(value);
            }
        };
        /// M: Support "Hotspot can not be turned on on Aeroplane mode". @}
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mUsageTracker.setListening(false);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        /// M: Support "Hotspot can not be turned on on Aeroplane mode". @{
        if (mListening == listening) {
            return;
        }
        mListening = listening;
        if (listening) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mContext.unregisterReceiver(mReceiver);
        }
        mSetting.setListening(listening);
        /// M: Support "Hotspot can not be turned on on Aeroplane mode". @}

        if (listening) {
            mController.addCallback(mCallback);
        } else {
            mController.removeCallback(mCallback);
        }
    }

    @Override
    protected void handleClick() {
        /// M: Support "Hotspot can not be turned on on Aeroplane mode". @{
        final boolean airplaneMode = mSetting.getValue() != 0;
        Log.d(TAG, "handleClick(), airplaneMode= " + airplaneMode);
        if (airplaneMode) {
            refreshState();
            return;
        }
        /// M: Support "Hotspot can not be turned on on Aeroplane mode". @}

        final boolean isEnabled = (Boolean) mState.value;
        mController.setHotspotEnabled(!isEnabled);
        mEnable.setAllowAnimation(true);
        mDisable.setAllowAnimation(true);
    }

    @Override
    protected void handleLongClick() {
        if (mState.value) return;  // don't allow usage reset if hotspot is active
        final String title = mContext.getString(R.string.quick_settings_reset_confirmation_title,
                mState.label);
        mUsageTracker.showResetConfirmation(title, new Runnable() {
            @Override
            public void run() {
                refreshState();
            }
        });
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = mController.isHotspotSupported() && mUsageTracker.isRecentlyUsed();
        state.label = mContext.getString(R.string.quick_settings_hotspot_label);

        state.value = mController.isHotspotEnabled();
        /// M: Support "Hotspot can not be turned on on Aeroplane mode". @{
        final int airplaneModeValue = arg instanceof Integer ? (Integer) arg : mSetting.getValue();
        final boolean airplaneMode = airplaneModeValue != 0;
        if (DEBUG) {
            Log.d(TAG, "handleUpdateState(), airplaneMode= " + airplaneMode);
        }
        if (airplaneMode) {
            state.value = false;
        }
        /// M: Support "Hotspot can not be turned on on Aeroplane mode". @}

        state.icon = state.visible && state.value ? mEnable : mDisable;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_hotspot_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_hotspot_changed_off);
        }
    }

    private static UsageTracker newUsageTracker(Context context) {
        return new UsageTracker(context, HotspotTile.class, R.integer.days_to_show_hotspot_tile);
    }

    private final class Callback implements HotspotController.Callback {
        @Override
        public void onHotspotChanged(boolean enabled) {
            refreshState();
        }
    };

    /**
     * This will catch broadcasts for changes in hotspot state so we can show
     * the hotspot tile for a number of days after use.
     */
    public static class APChangedReceiver extends BroadcastReceiver {
        private UsageTracker mUsageTracker;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mUsageTracker == null) {
                mUsageTracker = newUsageTracker(context);
            }
            mUsageTracker.trackUsage();
        }
    }

    /// M: Support "Hotspot can not be turned on on Aeroplane mode". @{
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
                refreshState();
            }
        }
    };
    /// M: Support "Hotspot can not be turned on on Aeroplane mode". @}
}
