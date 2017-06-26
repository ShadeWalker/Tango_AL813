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

//public class BluetoothPreference extends ListPreference {
public class WifiPreference extends Preference 
         implements PreferenceManager.OnActivityStopListener{
    private static final String LOG_TAG = "WifiPreference";

    private Context mContext;
    private TextView mPrefStatusView;
    private final IntentFilter mIntentFilter;
    //add by wanghui for al812 unknown ssid
    private final String   defaultString = "<unknown ssid>";
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
	    Log.d(LOG_TAG, "onReceive action=" + action);
            updateWifiStatus(mPrefStatusView);
        }
 
    };


    public WifiPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mIntentFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        final LayoutInflater layoutInflater =
            (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        View viewGroup = layoutInflater.inflate(R.layout.preference_commonsettings_item, null);
        LinearLayout frame = (LinearLayout) viewGroup.findViewById(R.id.frame);
        mPrefStatusView = (TextView) frame.findViewById(R.id.pref_status);
        mContext.registerReceiver(mReceiver, mIntentFilter);


        return frame;
    }

	/* HQ_ChenWenshuai 2015-11-05 modified for HQ01454819 begin */
	public void unregisterReceiver(){
		if (mReceiver != null && mContext!=null) {
            mContext.unregisterReceiver(mReceiver);
        }
	}
	/*HQ_ChenWenshuai 2015-11-05 modified end */

    public void onActivityStop() {
        if (mReceiver != null && mContext!=null) {
            mContext.unregisterReceiver(mReceiver);
        }
     }

   @Override
   protected void onBindView(View view) {
      super.onBindView(view);
        updateWifiStatus(mPrefStatusView);       
    }

     private void updateWifiStatus(TextView prefStatusTextView) {
        WifiManager manager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        final int state = manager.getWifiState();
        WifiInfo wifiInfo = manager.getConnectionInfo();
        /*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid */
        ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiWorkInfo = connManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        boolean isWifiConnSucc = (null != wifiWorkInfo)
                && (wifiWorkInfo.isAvailable()) && (wifiWorkInfo.isConnected());
        Log.d(LOG_TAG, "updateWifiStatus, isWifiConnSucc = " + isWifiConnSucc);
        handleWifiStateChanged(state, wifiInfo, prefStatusTextView, isWifiConnSucc);
        /* HQ_yangfengqing 2015-9-22 modified end*/
     }

    /*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid */
    private void handleWifiStateChanged(int state, WifiInfo wifiInfo, TextView prefStatusTextView, boolean isWifiConnSucc) {
        Log.d(LOG_TAG, "handleWifiStateChanged, state = " + state);
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
            case WifiManager.WIFI_STATE_ENABLED:
                /*HQ_yangfengqing 2015-9-22 modified for wifi is connected and network enabled show ssid */
                if(wifiInfo != null && isWifiConnSucc) {
                    String ssid = wifiInfo.getSSID();
                    String ssidStr = ssid.replaceAll("\"","");
                    Log.d(LOG_TAG, "handleWifiStateChanged, ssid = " + ssidStr);
                    if (ssidStr != null && (!"0x".equals(ssidStr))&&(!ssidStr.equals(defaultString))) {
                        prefStatusTextView.setText(ssidStr);
                    }else {
                        prefStatusTextView.setText(R.string.wifi_setup_not_connected);
                    }
                } else {
                    prefStatusTextView.setText(R.string.wifi_setup_not_connected);
                }
                break;
            case WifiManager.WIFI_STATE_DISABLING:
            case WifiManager.WIFI_STATE_DISABLED:
            default:
                 prefStatusTextView.setText(R.string.switch_off_text);
        }
    }

    @Override
    protected void onClick() {
        // Ignore this until an explicit call to click()
    }

    public void click() {
        super.onClick();
    }
}

