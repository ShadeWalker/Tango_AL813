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
 * limitations under the License.
 */

package com.mediatek.nfc.addon;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.mediatek.nfc.addon.NfcRuntimeOptions;

import android.os.SystemProperties;

public class BootCompleteReceiver extends BroadcastReceiver {

    static final String TAG = "NfcService.BootCompleteReceiver";

    static boolean sIsIpoBoot = false;
    Context mContext;

    public BootCompleteReceiver() {
        Log.d(TAG, "BootCompleteReceiver()");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        mContext = context;

        if(action == null){
            Log.e(TAG, "onReceive() action == null");
            return;
        } 

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d(TAG, "receive ACTION_BOOT_COMPLETED  sIsIpoBoot:" + sIsIpoBoot);

            if (sIsIpoBoot == false) {
                //normal boot::
                //Create EvtTransactionHandle and bind inside
                //not check whether Nfc chip is initialized
                bindSmartCardOnly();
                //sendIntent();
            } else {

                //IPO boot flow::
                //bind only, BOOT_COMPLETED & IPO_DELAY_DONE(Chip init)
                bindSmartCardOnly();
            }

        } else if (action.equals("android.intent.action.ACTION_BOOT_IPO")) {

            Log.d(TAG, "Rec. ACTION_BOOT_IPO, set sIsIpoBoot  = true");
            sIsIpoBoot = true;

        } else
            Log.e(TAG, "mReceiver: error Receiver case");

    }

/*
    void sendIntent(){
        Intent bootCompleteIntent = new Intent("com.mediatek.nfc.CreateEvtTransactionHandle");
        mContext.sendBroadcast(bootCompleteIntent);
    }
*/


/*
    void createAccessCheckImpl(Context context){
        Log.d(TAG, " ACTION_BOOT_COMPLETED , createAccessCheckImpl , no Check NFC chip initialized");
        EvtTransactionHandle.getInstance.bindSmartCardService(context);
    }
*/

    void bindSmartCardOnly() {

        boolean gsmaEvtBroadcastEnable = SystemProperties.get("ro.mtk_nfc_gsma_support").equals("1");
        
        Log.d(TAG, " ro.mtk_nfc_gsma_support: "+gsmaEvtBroadcastEnable);
        
        if (gsmaEvtBroadcastEnable) {
            Log.d(TAG, "!!!! bindSmartCardOnly !!!!  ");
            Intent bindSCSvcIntent = new Intent("android.intent.action.NFC_BindSmartCardService");
            mContext.sendBroadcast(bindSCSvcIntent);
        }else{
            Log.d(TAG, "!! not bind SmartCardService !!");

        }
            
    }



}




