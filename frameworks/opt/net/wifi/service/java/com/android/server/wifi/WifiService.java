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

package com.android.server.wifi;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.util.Log;
import com.android.server.SystemService;
/*add by lihaizhou for ChildMode by begin*/
import android.provider.ChildMode;
import android.net.wifi.WifiManager;
import android.database.ContentObserver;
import android.os.Handler;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.IWifiManager;
import com.android.server.wifi.WifiServiceImpl;
/*add by lihaizhou for ChildMode by end*/
import java.util.List;

public final class WifiService extends SystemService {

    private static final String TAG = "WifiService";
     private final Context mContext;//add by lihaizhou for ChildMode at 2015-07-26
    private ChildModeObserver mChildModeObserver;//add by lihaizhou for ChildMode at 2015-07-26
    final WifiServiceImpl mImpl;
    public WifiService(Context context) {     
        super(context);
        mContext = context;//add by lihaizhou for ChildMode at 2015-07-26
        mImpl = new WifiServiceImpl(context);
         /* add by lihaizhou for childmode at 2015-07-26 by begin*/
	mChildModeObserver = new ChildModeObserver(new Handler());
	ContentResolver cr = mContext.getContentResolver();
	cr.registerContentObserver( ChildMode.getUriFor(ChildMode.COMMON_CONTENT_URI, ChildMode.CHILD_MODE_ON), false, mChildModeObserver);
	cr.registerContentObserver( ChildMode.getUriFor(ChildMode.COMMON_CONTENT_URI, ChildMode.FORBID_WLAN), false, mChildModeObserver);
       /* add by lihaizhou for childmode at 2015-07-26 by end*/
    }

    @Override
    public void onStart() {
        Log.i(TAG, "Registering " + Context.WIFI_SERVICE);
        publishBinderService(Context.WIFI_SERVICE, mImpl);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            mImpl.checkAndStartWifi();
        }
    }
	private boolean isChildModeOn() 
	{
	        String isOn = ChildMode.getString(mContext.getContentResolver(),
	       		ChildMode.CHILD_MODE_ON);
	        if(isOn != null && "1".equals(isOn)){
	       	 return true;
	        }else {
	       	 return false;
			}
		       	 
	}
	private boolean isProhibitWlan()
	{
	     String isOn = ChildMode.getString(mContext.getContentResolver(),
	    		ChildMode.FORBID_WLAN );
	     if(isOn != null && "1".equals(isOn)){
	    	 return true;
	     }else {
	    	 return false;
		}	 
	}

	/* add by lihaizhou for forbid checked when child mode times up at 2015-03-18 by begin*/
         /** @hide */
	public boolean isWlanTimeUp()
	{
	     String isOn =  ChildMode.getString(mContext.getContentResolver(),
		 	"internet_time_restriction_limit");
	     if(isOn != null && "1".equals(isOn) && isChildModeOn()){
	    	 return true;
	     }else {
	    	 return false;
		}	 
	}
	/* add by lihaizhou for forbid checked when child mode times up at 2015-03-18 by end*/
         /** @hide */
	private class ChildModeObserver extends ContentObserver {
		public ChildModeObserver(Handler handler) 
		{ 
			super(handler);
		}
		@Override
			public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			/* add by lihaizhou for forbid checked when child mode times up at 2015-07-26 by begin*/
			if (isChildModeOn() && (isProhibitWlan() || isWlanTimeUp()))
			{
			        //ChildMode.putString(mContext.getContentResolver(),"internet_limit_time_up","1");
				int mApst = mImpl.getWifiApEnabledState();
				if( mApst == WifiManager.WIFI_AP_STATE_ENABLED || 
					mApst == WifiManager.WIFI_AP_STATE_ENABLING)
				{
					mImpl.setWifiApEnabled(null,false);
				}
				int mWifist = mImpl.getWifiEnabledState();
				if(mWifist==WifiManager.WIFI_STATE_ENABLED ||
					mWifist==WifiManager.WIFI_STATE_ENABLING)
				{
					mImpl.setWifiEnabled(false);
				}
                       //Log.i("lihaizhou", "internet_limit_time_up liahizhou" + ChildMode.getString(mContext.getContentResolver(),"internet_limit_time_up"));
                      /* add by lihaizhou for forbid checked when child mode times up at 2015-07-26 by end*/
			} 
                       //if (isChildModeOn()&&!isWlanTimeUp())
                        // {
                          // ChildMode.putString(mContext.getContentResolver(),"internet_limit_time_up","0");
                          //Log.i("lihaizhou", "internet_limit_time_up haizhou" + ChildMode.getString(mContext.getContentResolver(),"internet_limit_time_up"));
                         // }
		       }
	}
       /* add by lihaizhou for childmode at 2015-07-26 by end*/
}
