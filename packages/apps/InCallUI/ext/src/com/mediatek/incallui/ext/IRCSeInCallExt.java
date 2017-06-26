package com.mediatek.incallui.ext;

import android.app.Activity;
import android.os.Bundle;

public interface IRCSeInCallExt {

    /**
     * called when onCreate(), notify plugin to do initialization.
     * @param icicle the Bundle InCallActivity got
     * @param inCallActivity the InCallActivity instance
     * @param IInCallScreenExt the call back interface for UI updating
     */
    void onCreate(Bundle icicle, Activity inCallActivity, IInCallScreenExt iInCallScreenExt);

    /**
     * called when onDestroy()
     * @param inCallActivity the InCallActivity instance
     */
    void onDestroy(Activity inCallActivity);
}
