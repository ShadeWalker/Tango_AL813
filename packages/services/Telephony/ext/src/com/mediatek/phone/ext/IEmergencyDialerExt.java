package com.mediatek.phone.ext;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Telecom account registry extension plugin for op09.
*/
public interface IEmergencyDialerExt {
    /**
     * Called when need oncreate dial buttons.
     *
     * @param view need to update.
     */
    void onCreate(Activity activity, IEmergencyDialer emergencyDialer);

    /**
     * Called when destroy emergency dialer.
     *
     */
    void onDestroy();	
}
