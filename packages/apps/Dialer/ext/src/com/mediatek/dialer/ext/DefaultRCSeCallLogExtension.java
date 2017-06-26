package com.mediatek.dialer.ext;


import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

public class DefaultRCSeCallLogExtension implements IRCSeCallLogExtension {
    private static final String TAG = "DefaultRCSeCallLogExtension";

    /**
     * for RCSe
     * Bind the plugin views for call log list. This method called
     * while the call log list adapter bind its item view.
     * @param context the Context
     * @param viewGroup the item view of call log list
     * @param number the number of this call
     */
    public void bindPluginViewForCallLogList(Context context, ViewGroup viewGroup, String number) {
        log("bindPluginViewForCallLogList");
    }

    private void log(String msg) {
        Log.d(TAG, msg + " default");
    }
}
