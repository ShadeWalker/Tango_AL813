
package com.mediatek.incallui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.incallui.Log;
import com.android.incallui.StatusBarNotifier;

public class InCallBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION_UPDATE_UI_FORCED = "com.android.incallui.ACTION_UPDATE_UI_FORCED";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.i(this, "Broadcast from Telecom: " + action);

        if (action.equals(ACTION_UPDATE_UI_FORCED)) {
            StatusBarNotifier.clearInCallNotification(context);
        } else {
            Log.d(this, "Unkown type action. ");
        }
    }
}
