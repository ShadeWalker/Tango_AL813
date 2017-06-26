/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import android.content.Context;
import android.os.BatteryStats;
import android.os.SystemClock;
import android.preference.Preference;
import com.android.settings.Utils;
import android.os.SystemProperties;

import com.android.settings.R;

/**
 * Custom preference for displaying power consumption as a bar and an icon on the left for the
 * subsystem/app type.
 *
 */
public class BatteryStatusPreference extends Preference {
    
    public BatteryStatusPreference(Context context, BatteryStats status) {
        super(context);
        setLayoutResource(R.layout.preference_status);
        long uSecTime = status.computeBatteryRealtime((SystemClock.elapsedRealtime() * 1000), 
                BatteryStats.STATS_SINCE_CHARGED);
        String duration = Utils.formatElapsedTime(context, ((double)uSecTime / 1000.0), true);
        String batteryStatusTitle = context.getString(R.string.battery_stats_on_battery, 
                new Object[] {duration});
        setTitle(batteryStatusTitle);

	if(SystemProperties.get("ro.hq.hide.battery_infor").equals("1")) return;
        setWidgetLayoutResource(R.layout.preference_widget_arrow);
    }

}
