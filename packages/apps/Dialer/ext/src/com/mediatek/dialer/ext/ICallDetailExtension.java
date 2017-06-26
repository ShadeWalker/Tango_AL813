package com.mediatek.dialer.ext;

import com.android.dialer.PhoneCallDetails;

import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public interface ICallDetailExtension {

    /**
     * for OP09
     * called when CallDetailHistoryAdapter init
     * @param context
     * @param phoneCallDetails  the detail info for calls
     */
    public void initForCallDetailHistory(Context context, PhoneCallDetails[] phoneCallDetails);

    /**
     * for OP09
     * called when CallDetailHistoryAdapter list getViewTypeCount, plug-in should change
     * the count base on the current host view type count
     * @param currentViewTypeCount the host view type count
     * @return changed view type count
     */
    public int getViewTypeCountForCallDetailHistory(int currentViewTypeCount);

    /**
     * for OP09
     * called when CallDetailHistoryAdapter list getView, plug-in should custom based on
     * the current convertView
     * @param position the current position of the view
     * @param convertView the host view
     * @param parent the parent viewGroup
     * @return a customized view by plug-in
     */
    public View getViewPostForCallDetailHistory(int position, View convertView, ViewGroup parent);

    /**
     * for OP09
     * @param Context context
     * @param PhoneAccountHandle phoneAccountHandle
     */
    public void setCallAccountForCallDetail(Context context, PhoneAccountHandle phoneAccountHandle);

    /**
     * for op01
     * called when updating call list, plug-in should customize the duration view if needed
     * @param durationView the duration text
     */
    public void setDurationViewVisibility(TextView durationView);

    /**
     * for op01
     * called when updating call list, plug-in should customize the date view if needed
     * @param context
     * @param dateView the date text
     * @param date calldetail date
     */
    public void setDateView(Context context, TextView dateView, long date);
}
