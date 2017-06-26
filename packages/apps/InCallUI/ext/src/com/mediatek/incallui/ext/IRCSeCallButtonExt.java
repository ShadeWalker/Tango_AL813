package com.mediatek.incallui.ext;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;

import java.util.HashMap;


public interface IRCSeCallButtonExt {
    /**
     * called when CallButtonFragment view created.
     * customize this view
     * @param context host Context
     * @param rootView the CallButtonFragment view
     */
    void onViewCreated(Context context, View rootView);

    /**
     * called when call state changed
     * notify the foreground call to plug-in
     * @param call current foreground call
     * @param callMap a mapping of callId -> call for all current calls
     */
    void onStateChange(android.telecom.Call call, HashMap<String, android.telecom.Call> callMap);

    /**
     * called when popup menu item in CallButtonFragment clicked.
     * involved popup menus such as audio mode, vt
     * @param menuItem the clicked menu item
     * @return true if this menu event has already handled by plugin
     */
    boolean handleMenuItemClick(MenuItem menuItem);
}
