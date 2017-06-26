package com.mediatek.deskclock.ext;

import android.content.Context;
import android.os.Vibrator;
import android.util.Log;

/**
 * M: Default implementation of Plug-in definition of Desk Clock.
 */
public class DefaultAlarmControllerExt implements IAlarmControllerExt {

    private static final String TAG = "DefaultAlarmControllerExt";
    private static final int VIBRATE_LENGTH = 500;

    @Override
    public void vibrate(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            Log.v(TAG, "Vibrator starts and vibrates: " + VIBRATE_LENGTH + " ms");
            vibrator.vibrate(VIBRATE_LENGTH);
        }
    }
}
