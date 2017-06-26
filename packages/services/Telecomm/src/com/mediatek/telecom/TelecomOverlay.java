package com.mediatek.telecom;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

import com.android.server.telecom.Log;

public class TelecomOverlay {
    private static final String TAG = "TelecomOverlay";

    /**
     * Power on/off device when connecting to smart book
     */
    public static void updatePowerForSmartBook(Context context, boolean onOff) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        Log.d(TAG, "SmartBook power onOff: " + onOff);
        if (onOff) {
            pm.wakeUpByReason(SystemClock.uptimeMillis(), PowerManager.WAKE_UP_REASON_SMARTBOOK);
        } else {
            pm.goToSleep(SystemClock.uptimeMillis(), PowerManager.GO_TO_SLEEP_REASON_SMARTBOOK, 0);
        }
    }
}
