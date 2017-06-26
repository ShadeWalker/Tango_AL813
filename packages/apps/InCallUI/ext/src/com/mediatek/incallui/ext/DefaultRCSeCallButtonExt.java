package com.mediatek.incallui.ext;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;

import java.util.HashMap;


public class DefaultRCSeCallButtonExt implements IRCSeCallButtonExt {
    @Override
    public void onViewCreated(Context context, View rootView) {
        // do nothing
    }

    @Override
    public void onStateChange(android.telecom.Call call, HashMap<String, android.telecom.Call> callMap) {
        // do nothing
    }

    @Override
    public boolean handleMenuItemClick(MenuItem menuItem) {
        return false;
    }
}
