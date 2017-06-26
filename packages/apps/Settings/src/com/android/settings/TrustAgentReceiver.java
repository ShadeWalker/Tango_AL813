package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by libeibei on 17-3-1 ,cause of HQ02067101
 * Used for receive boot complete to check
 * smartlock is remove from TrustAgents or not;
 */

public class TrustAgentReceiver extends BroadcastReceiver {

    private static final String TAG = "TrustAgentReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        String action =intent.getAction();

        if(Intent.ACTION_BOOT_COMPLETED.equals(action)){
            Log.d(TAG,"onReceive : "+action);
            TrustAgentUtils.checkTrustAgents(context);
            Log.d(TAG,"Remove Google SmartLock Function  successed ! !");

        }

    }
}
