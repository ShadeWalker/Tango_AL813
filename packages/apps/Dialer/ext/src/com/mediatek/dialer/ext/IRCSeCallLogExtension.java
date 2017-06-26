package com.mediatek.dialer.ext;

import android.content.Context;
import android.view.ViewGroup;

public interface IRCSeCallLogExtension {
    /**
     * for RCSe
     * Bind the plugin views for call log list. This method called
     * while the call log list adapter bind its item view.
     * @param context the Context
     * @param viewGroup the item view of call log list
     * @param number the number of this call
     */
    void bindPluginViewForCallLogList(Context context, ViewGroup viewGroup, String number);

}
