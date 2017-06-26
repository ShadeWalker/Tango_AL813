package com.mediatek.dialer.ext;

import android.util.Log;

public class DefaultSelectAccountExtension implements ISelectAccountExtension {
    private static final String TAG = "DefaultSelectAccountExtension";

    /**
     * for select default account
     *
     * @param iconId: The id of the always_ask_account icon.
     */
    public int getAlwaysAskAccountIcon(int iconId) {
        log("getAlwaysAskAccountIcon");

        return iconId;
    }

    private void log(String msg) {
        Log.d(TAG, msg + " default");
    }
}
