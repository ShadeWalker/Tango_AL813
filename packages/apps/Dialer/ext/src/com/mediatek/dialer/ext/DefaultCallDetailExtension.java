package com.mediatek.dialer.ext;


import com.android.dialer.PhoneCallDetails;

import android.content.Context;
import android.util.Log;
import android.telecom.PhoneAccountHandle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class DefaultCallDetailExtension implements ICallDetailExtension {
    private static final String TAG = "DefaultCallDetailExtension";

    /**
     * for OP09
     * called when CallDetailHistoryAdapter init
     * @param context
     * @param phoneCallDetails
     */
    public void initForCallDetailHistory(Context context, PhoneCallDetails[] phoneCallDetails) {
        log("init");
    }

    /**
     * for OP09
     * called when CallDetailHistoryAdapter list getViewTypeCount
     * @param currentViewTypeCount
     */
    public int getViewTypeCountForCallDetailHistory(int currentViewTypeCount) {
        log("getViewTypeCount");
        return currentViewTypeCount;
    }


    /**
     * for OP09
     * called when CallDetailHistoryAdapter list getView
     * @param position
     * @param convertView
     * @param parent
     * @return
     */
    public View getViewPostForCallDetailHistory(int position, View convertView, ViewGroup parent) {
        log("getViewPost");
        return convertView;
    }

    /**
     * for OP09
     * @param Context context
     * @param PhoneAccountHandle phoneAccountHandle
     */
    public void setCallAccountForCallDetail(Context context, PhoneAccountHandle phoneAccountHandle) {
        log("setCallAccountForCallDetail");
    }

    /**
     * for op01
     * @param durationView the duration text
     */
    @Override
    public void setDurationViewVisibility(TextView durationView) {
        log("setDurationViewVisibility");
    }

    /**
     * for op01
     * called when updating call list, plug-in should customize the date view if needed
     * @param context
     * @param dateView the date text
     * @param date calldetail date
     */
    public void setDateView(Context context, TextView dateView, long date) {
        log("setDateView");
    }

    private void log(String msg) {
        Log.d(TAG, msg + " default");
    }
}
