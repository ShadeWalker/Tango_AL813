package com.mediatek.dialer.ext;

import android.content.Context;
import android.database.Cursor;
import android.telecom.PhoneAccountHandle;
import android.util.Log;
import android.view.View;

public class DefaultDialerSearchExtension implements IDialerSearchExtension {

    private static final String TAG = "DefaultDialerSearchExtension";

    /**
     * for OP09
     * @param view
     * @param context
     * @param cursor
     */
    public void bindCallLogViewPost(View view, Context context, Cursor cursor) {
        log("bindCallLogViewPost");
    }

    /**
     * for OP09
     * @param view
     * @param context
     * @param cursor
     */
    public void bindContactCallLogViewPost(View view, Context context, Cursor cursor) {
        log("bindContactCallLogViewPost");
    }

    /**
     * for OP09
     * @param Context context
     * @param View view
     * @param PhoneAccountHandle phoneAccountHandle
     */
    public void setCallAccountForDialerSearch(Context context, View view, PhoneAccountHandle phoneAccountHandle) {
        log("setCallAccountForDialerSearch");
    }

    private void log(String msg) {
        Log.d(TAG, msg + " default");
    }
}
