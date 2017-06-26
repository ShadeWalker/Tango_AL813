/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.deskclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;

import com.android.deskclock.alarms.AlarmStateManager;
import com.android.deskclock.timer.TimerObj;

public class AlarmInitReceiver extends BroadcastReceiver {

    // A flag that indicates that switching the volume button default was done
    private static final String PREF_VOLUME_DEF_DONE = "vol_def_done";
    ///M: Power off alarm for IPO boot@{
    private static final String IPO_BOOT_ACTION = "android.intent.action.ACTION_BOOT_IPO";
    private static Boolean sIPOBOOT = false;
    ///@}
    /**
     * Sets alarm on ACTION_BOOT_COMPLETED.  Resets alarm on
     * TIME_SET, TIMEZONE_CHANGED
     */
    @Override
    public void onReceive(final Context context, Intent intent) {
        final String action = intent.getAction();
        LogUtils.v("AlarmInitReceiver " + action);

        /* M: Power off alarm
         * Note: Never call updateNextAlarm when the device is boot from power off
         * alarm, since it would make the power off alarm dismiss the wrong alarm. @{
         */
        if (IPO_BOOT_ACTION.equals(action)) {
            LogUtils.v("Receive android.intent.action.ACTION_BOOT_IPO intent.");
            setIPOBootValue(true);
            return;
        }
        //The TIME_SET broadcast is send by IPO, ACTION_TIME_CHANGED = TIME_SET
        if (getIPOBootValue() && action.equals(Intent.ACTION_TIME_CHANGED)) {
            LogUtils.v("IPO boot time changed return");
            return;
        }
        ///@}

        final PendingResult result = goAsync();
        final WakeLock wl = AlarmAlertWakeLock.createPartialWakeLock(context);
        wl.acquire();

        // We need to increment the global id out of the async task to prevent
        // race conditions
        AlarmStateManager.updateGlobalIntentId(context);
        AsyncHandler.post(new Runnable() {
            @Override public void run() {
                // Remove the snooze alarm after a boot.
                if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                    setIPOBootValue(false);
                    // Clear stopwatch and timers data
                    SharedPreferences prefs =
                            PreferenceManager.getDefaultSharedPreferences(context);
                    LogUtils.v("AlarmInitReceiver - Reset timers and clear stopwatch data");
                    TimerObj.resetTimersInSharedPrefs(prefs);
                    Utils.clearSwSharedPref(prefs);

                    if (!prefs.getBoolean(PREF_VOLUME_DEF_DONE, false)) {
                        // Fix the default
                        LogUtils.v("AlarmInitReceiver - resetting volume button default");
                        switchVolumeButtonDefault(prefs);
                    }
                }

                // Update all the alarm instances on time change event
                AlarmStateManager.fixAlarmInstances(context);

                result.finish();
                LogUtils.v("AlarmInitReceiver finished");
                wl.release();
            }
        });
    }

    private void switchVolumeButtonDefault(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(SettingsActivity.KEY_VOLUME_BEHAVIOR,
            SettingsActivity.DEFAULT_VOLUME_BEHAVIOR);

        // Make sure we do it only once
        editor.putBoolean(PREF_VOLUME_DEF_DONE, true);
        editor.apply();
    }

    /// M: get the value of IPO boot @{
    private boolean getIPOBootValue() {
        return sIPOBOOT;
    }
    /// @}

    /// M: set the value of IPO boot @{
    private void setIPOBootValue(boolean mValue) {
        sIPOBOOT = mValue;
    }
    /// @}
}
