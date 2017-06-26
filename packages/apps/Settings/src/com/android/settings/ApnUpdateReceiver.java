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

package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.ContentResolver;
import android.os.SystemProperties;
import com.mediatek.settings.sim.Log;


public class ApnUpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "ApnUpdateReceiver";
    private Context mContext;
    private static boolean mRestoreDefaultApnMode;
    public static final String RESTORE_CARRIERS_URI =
            "content://telephony/carriers/restore";//subId
    private static final Uri DEFAULTAPN_URI = Uri.parse(RESTORE_CARRIERS_URI);
    private  Thread mRestoreDefaultApnThread;
    /// @}
    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        Log.d(TAG, "onReceive()... action: " + intent.getAction());
        otaUpdateApn(mContext, intent.getAction());
    }

    private void otaUpdateApn(Context context,String action){

        if (action.equals("android.intent.action.BOOT_COMPLETED")) {
            SharedPreferences prefs = context.getSharedPreferences("apnota", Context.MODE_WORLD_READABLE);
            String mOtaDefaultVersion= prefs.getString("persist.ota.apn.version", "0000");
            String mOtaCurrentVersion=SystemProperties.get("ro.build.cust.id", "0000"); //replace ro.build.version.incremental

            boolean isOtaUpdate =false;
            Log.d(TAG,"hugo1:getVersion:- mOtaDefaultVersion" + mOtaDefaultVersion + "====mOtaCurrentVersion==>" + mOtaCurrentVersion);
            if (mOtaDefaultVersion.equals("0000")){
                //prefs.edit().putString("persist.ota.apn.version", mOtaCurrentVersion).commit();
                isOtaUpdate = true;
                Log.d(TAG,"hugo:comein 1 ");
            }else{
                if(!mOtaCurrentVersion.equals(mOtaDefaultVersion)){
                    //prefs.edit().putString("persist.ota.apn.version", mOtaCurrentVersion).commit();
                    isOtaUpdate = true;
                    Log.d(TAG,"hugo:comein 2 ");
                }else{
                    Log.d(TAG,"hugo:comein 3 ");
                }
            }


            if(isOtaUpdate){
                if(loadVersionByOta(mOtaCurrentVersion)){
                    restoreDefaultApnEx(context);
                    prefs.edit().putString("persist.ota.apn.version", mOtaCurrentVersion).commit();
                }else{
                    prefs.edit().putString("persist.ota.apn.version", mOtaCurrentVersion).commit();
                }
            }

        }
    }

    private void restoreDefaultApnEx(Context context) {
            Log.d(TAG,"hugo:comein restoreDefaultApnEx ");
            //String sourceType = "";
            final String where = "sourceType = \'0\'";
            mRestoreDefaultApnMode = true;
            mContext = context;
            mRestoreDefaultApnThread = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    Log.d(TAG,"hugo:comein delete start");
                    ContentResolver resolver = mContext.getContentResolver();
                    resolver.delete(DEFAULTAPN_URI, where, null);
                    Log.d(TAG, "hugo:comein delete end");
                }
            });
            mRestoreDefaultApnThread.start();
    }

    public static boolean loadVersionByOta(String keyword) {
        String version;
        try{
            ApnUpdateConfigOverride mOverride =ApnUpdateConfigOverride.getInstance();
            ApnUpdateConfigOverride.MmsConfigBean bean = mOverride.getMmsConfigWithSim("1");
            version = bean.mVersion;
        }catch (Exception e){
            return false;
        }
        if(version != null && version.equals(keyword)){
            return true;
        }else {
            return false;
        }
    }

}
