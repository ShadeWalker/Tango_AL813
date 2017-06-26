package com.mediatek.dialer.ext;

import android.content.Context;
import android.database.Cursor;
import android.telecom.PhoneAccountHandle;
import android.view.View;

public interface IDialerSearchExtension {

    /**
     * for OP09
     * Called when DialerSearch adapter bind CallLog type View, plug-in should customize
     * this view if needed
     * @param view the view is about to binded
     * @param context
     * @param cursor cursor for the adapter
     */
    public void bindCallLogViewPost(View view, Context context, Cursor cursor);

    /**
     * for OP09
     * Called when DialerSearch adapter bind contact type View, plug-in should customize
     * this view if needed
     * @param view the view is about to binded
     * @param context
     * @param cursor cursor for the adapter
     */
    public void bindContactCallLogViewPost(View view, Context context, Cursor cursor);

    /**
     * for OP09
     * @param Context context
     * @param View view
     * @param PhoneAccountHandle phoneAccountHandle
     */
    public void setCallAccountForDialerSearch(Context context, View view, PhoneAccountHandle phoneAccountHandle);
}
