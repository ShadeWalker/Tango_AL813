package com.mediatek.deskclock.ext;

import android.content.Context;

/**
 * M: interface of DeskClock definition to control the alarm
 */
public interface IAlarmControllerExt {

    /**
     * Control the alarm to vibrate when the user is in call
     * @param context
     */
    void vibrate(Context context);
}
