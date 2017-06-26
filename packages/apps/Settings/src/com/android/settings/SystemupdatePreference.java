/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.settings;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.preference.ListPreference;
import android.preference.Preference;
import android.widget.LinearLayout;
import android.util.Log;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceManager;
import android.content.BroadcastReceiver;
/*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid */
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
/* HQ_yangfengqing 2015-9-22 modified end*/
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.content.ContentResolver;
import android.provider.Settings;
//public class BluetoothPreference extends ListPreference {
public class SystemupdatePreference extends Preference {
    private static final String LOG_TAG = "SystemupdatePreference";

    private Context mContext;
    private TextView mPrefStatusView;


    public SystemupdatePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        final LayoutInflater layoutInflater =
            (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ContentResolver mResolver = mContext.getContentResolver();
	int hwNewSystemUpdate = Settings.System.getInt(mResolver, "hw_new_system_update", 0);
        Log.i("maolikui CommonSettings onCreate hwNewSystemUpdate",hwNewSystemUpdate + "");
        View viewGroup = layoutInflater.inflate(R.layout.preference_commonsettings_item, null);
        LinearLayout frame = (LinearLayout) viewGroup.findViewById(R.id.frame);
        mPrefStatusView = (TextView) frame.findViewById(R.id.pref_status);
        if(hwNewSystemUpdate != 0){
	   mPrefStatusView.setBackgroundResource(R.drawable.ic_notification);
        }
	
        return frame;
    }
   
    @Override
    protected void onClick() {
        // Ignore this until an explicit call to click()
    }

    public void click() {
        super.onClick();
    }
}

