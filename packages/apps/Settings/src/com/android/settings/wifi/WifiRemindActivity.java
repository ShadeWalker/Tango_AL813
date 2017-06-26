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
//this document is added by wanghui for al812
package com.android.settings.wifi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;
import android.os.Handler;
import android.os.Message;
import android.view.WindowManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.widget.CheckBox;
import android.view.LayoutInflater;
import android.view.View;
import android.telephony.TelephonyManager;
import android.provider.Settings;
import com.android.settings.R;
import android.content.BroadcastReceiver;
import android.net.wifi.WifiInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.content.IntentFilter;
import android.content.Context;
//add by wanghui for al812 HQ01474791 20151029
import android.view.Window;


/**
 * Activity for modifying a setting using the Voice Interaction API. This activity
 * MUST only modify the setting if the intent was sent using
 * {@link android.service.voice.VoiceInteractionSession#startVoiceActivity startVoiceActivity}.
 */
 public class WifiRemindActivity extends Activity {

    private static final String TAG = "WifiRemindActivity";
    private  AlertDialog mAlertDialog;
    private static Timer time = null;
    private static final String action = "com.android.telephony.USER_ACTION";
    private TelephonyManager telephonyMgr = null;
    private CheckBox mCheckbox;
    private static final String WIFI_STATE_CHANGE = "android.net.wifi.STATE_CHANGE";
    private IntentFilter mIntentFilter;
    private WifiInfo   wifiInfo;
    private WifiManager mwifiManager;
    private WifiStateReceiver  myreceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(telephonyMgr  == null) {
            telephonyMgr = TelephonyManager.from(this);
        }
        //add by wanghui for al812 HQ01474791 20151029
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        showRemindAlert();
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WIFI_STATE_CHANGE);
        myreceiver = new WifiStateReceiver();
        registerReceiver(myreceiver, mIntentFilter);
    }
    private Handler hand =new Handler() {
        public void handleMessage(Message msg){
            super.handleMessage(msg);
            if(msg.what ==  0x111) {
                if(mAlertDialog !=null)
                   mAlertDialog.dismiss();
                   WifiRemindActivity.this.finish();
				Log.d("wanghui", "timeover");
            }
        }
    };

    private TimerTask task = new TimerTask(){
        public void run() {
            Message msg = new Message();
            msg.what =  0x111;
            hand.sendMessage( msg);
        }
    };

    private void showRemindAlert() {
        int themeID = getResources().getIdentifier("androidhwext:style/Theme.Emui.Dialog.Alert",null,null); 
        AlertDialog.Builder builder = new  AlertDialog.Builder(this,themeID);
        builder.setTitle(R.string.wifi_is_disconnect);		                
        builder.setPositiveButton(R.string.open_confirm,new OpenDataConnectOKListener());
        builder.setNegativeButton(R.string.cancle_confirm, new CloseDataConnectOKListener());
        mAlertDialog = builder.create();
        final  View  layout = mAlertDialog.getLayoutInflater().inflate(R.layout.define_view_dialog,null);
        mCheckbox = (CheckBox) layout.findViewById(R.id.closeReminder);
        mAlertDialog.setView(layout);
        mAlertDialog.setCanceledOnTouchOutside(false);
        mAlertDialog.setCancelable(false);
        mAlertDialog.getWindow().setType(
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mAlertDialog.getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mAlertDialog.show();
        if(time != null){
            time.cancel();
        }
        time = new Timer();
        //add by wanghui for al812 HQ01398660 5min later
        time.schedule(task,5*60*1000);
    }

    private class OpenDataConnectOKListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {  
            Log.v(TAG, "TAG ok");
            telephonyMgr.setDataEnabled(true);
            if (mCheckbox.isChecked()) {
            Settings.System.putString(getContentResolver(),"switch_mode_key","3");
            Intent intent = new Intent(action);
            WifiRemindActivity.this.sendBroadcast(intent);
            WifiRemindActivity.this.finish();
            Settings.System.putString(getContentResolver(),"flag_state","1");
            Log.v("TAG", "save="+mCheckbox.isChecked());
        } else{
              //modify by wanghui for al812 activity not finish
              WifiRemindActivity.this.finish(); 
		  }
      }
    }
    private class CloseDataConnectOKListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {  
            Log.v(TAG, "TAG cancle");
            WifiRemindActivity.this.finish();
      }
    }

    class WifiStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO); 
            Log.d("wanghui: " , action);
               if(action.equals(WIFI_STATE_CHANGE)){
		         if(info.getState().equals(NetworkInfo.State.CONNECTED)&&(mAlertDialog!=null)) {
                     mAlertDialog.dismiss();
                     WifiRemindActivity.this.finish();
				     Log.d("wanghui", "reconnect");
                 }                    
            }
        }
    }
    
    @Override
    protected void onDestroy(){
		super.onDestroy();
		unregisterReceiver(myreceiver);
	}	
}		
		
		
		
		 
