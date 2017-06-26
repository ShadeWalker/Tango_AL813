/*
 * Copyright (C) 2011 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.settings.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.mediatek.xlog.Xlog;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.telephony.TelephonyManager;
import android.provider.Settings;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import java.util.List;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import com.android.internal.telephony.Phone;
import android.telephony.SubscriptionManager;
import android.telephony.RadioAccessFamily;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.preference.Preference;
import android.preference.ListPreference;
import com.android.settings.R;
import android.view.WindowManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.wifi.WifiInfo;
import android.net.NetworkInfo;
import java.util.Timer;
import java.util.TimerTask;
import android.os.Handler;
import android.os.Message;
import android.widget.CheckBox;
import android.view.LayoutInflater;
import android.view.View;

public class WifiStateChangeReceiver extends BroadcastReceiver {
    private static final String TAG ="wifiStateChangeReceiver";
    private static final String mRemindValue = "2";
    private static final String mAutoValue = "3";
    private static final String mIgnoreValue = "4";
    private static AlertDialog mAlertDialog;
    private static Timer time = null;
    private static final String action = "com.android.telephony.USER_ACTION";
    private TelephonyManager telephonyMgr = null;
    private Context mContext;
    private CheckBox mCheckbox;
    private WifiManager mwifiManager;
    private WifiInfo   wifiInfo;
    private boolean mDataEnable = false;
	//add by wanghui for al812_tl
	private static boolean mIsLastWifiOn = false;
	private static boolean mIsCurrentWifiOn = false;
	

    @Override
    public void onReceive(Context context,Intent intent) {
        //abortBroadcast();
        if(!(SystemProperties.get("ro.hq.wifi.disconnect.reminder").equals("1"))){
                return;
            }
        mContext = context.getApplicationContext();
        String switch_mode="";
        Log.d("TAG", "yi shou dao guangbo");
        String settingValue = Settings.System.getString(mContext.getContentResolver(), "switch_mode_key");
		//add by wanghui for defaultvalue design for HQ01533601
        if(settingValue == null) {
			if(SystemProperties.get("ro.hq.wifi.switch.default").equals("1")){
			//add by wanghui  for al813 default four
                switch_mode ="2"; 
			}else if(SystemProperties.get("ro.hq.latin.wifi.switch.default").equals("1")||SystemProperties.get("ro.product.name").equals("TAG-L03")
			         ||SystemProperties.get("ro.product.name").equals("TAG-L21")
				 ||SystemProperties.get("ro.product.name").equals("TAG-L22")) {  //add by chiguoqing
                   switch_mode ="3"; //add by wanghui for HQ01557482
			}else{
				   switch_mode ="4"; 
			}
        } else {
           switch_mode = settingValue;
        }
        Log.d("TAG","switch_mode=" + switch_mode);
        String action = intent.getAction();
        if(telephonyMgr  == null) {
            telephonyMgr = TelephonyManager.from(mContext);
        }
        if(mwifiManager  == null) {
            mwifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        }
        if((WifiManager.NETWORK_STATE_CHANGED_ACTION).equals(action)) {
           boolean isdataenabled = telephonyMgr.getDataEnabled();
           NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO); 
		   mIsLastWifiOn = mIsCurrentWifiOn;
		   //mIsCurrentWifiClose = info.getDetailedState().equals(NetworkInfo.DetailedState.DISCONNECTED);
		   mIsCurrentWifiOn = info.getDetailedState().equals(NetworkInfo.DetailedState.CONNECTED);		   
           //add by wanghui for movistar requirement
           boolean ismovistar = mContext.getResources().getBoolean(R.bool.wifi_leave_pop);
		   //add by wanghui for wifi connect
           if(!ismovistar&&!isdataenabled){
		       return;
		   }
		   Log.d("TAG","isdataenabled="+isdataenabled + "mIsLastWifiOn = " + mIsLastWifiOn + ",mIsCurrentWifiClose="+mIsCurrentWifiOn);
           //if(info.getDetailedState().equals(NetworkInfo.DetailedState.DISCONNECTED)&&info.isPreStateConnected()) {
		   if( !mIsCurrentWifiOn && mIsLastWifiOn ) {		   
		   Log.d("TAG","shoul show dialog");
           if(switch_mode.equals(mRemindValue)||(!isdataenabled&&ismovistar) ) {
               Log.d("TAG", "remind resume");
               //if (!isdataenabled) return;
               if(true) {
                  //telephonyMgr.setDataEnabled(false);
                  Intent intentactivity = new Intent();
				  intentactivity.setClass(context,WifiRemindActivity.class);
				  intentactivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				  context.startActivity(intentactivity);
				  Log.d("wanghui", "chuxian");
               }
            } else if(switch_mode.equals(mAutoValue)&&isdataenabled) {
               Log.d("TAG", "auto resume");
               return;
            } else if(switch_mode.equals(mIgnoreValue)&&isdataenabled) {
               Log.d("TAG", "not resume");
               if(!isdataenabled) return ;
               else telephonyMgr.setDataEnabled(false);
            }
        }  else {
               Log.d("TAG", "not full");
               return;
        }
      }
    }
}
















