package com.mediatek.phone.ext;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.util.Log;

/**
 * Telecom account registry extension plugin for op09.
*/
public class DefaultEmergencyDialerExt implements IEmergencyDialerExt {

    /**
     * Called when need to update dial buttons.
     *
     * @param view need to update.
     */
    public void onCreate(Activity activity, IEmergencyDialer emergencyDialer) {
        Log.d("DefaultEmergencyDialerExt", "onCreate");
    }

    /**
     * Called when destory emergency dialer.
     *
     */
    public void onDestroy() {
        Log.d("DefaultEmergencyDialerExt", "onDestroy");
    }
}

