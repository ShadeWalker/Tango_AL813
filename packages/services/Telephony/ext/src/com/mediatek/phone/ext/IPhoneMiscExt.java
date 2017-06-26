package com.mediatek.phone.ext;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.Connection;

public interface IPhoneMiscExt {

    /**
     * plugin should check the hostReceiver class, to check whether this
     * API call belongs to itself
     * if (hostReceiver.getClass().getSimpleName() == "PhoneGlobalsBroadcastReceiver") {}
     *
     * @param context the received context
     * @param intent the received intent
     * @return true if plug-in decide to skip host execution
     */
    boolean onPhoneGlobalsBroadcastReceive(Context context, Intent intent);

    /**
     * called in NetworkQueryService.onBind()
     * google default behavior defined a LocalBinder to prevent  NetworkQueryService
     * being accessed by outside components.
     * but, there is a situation that plug-in need the mBinder. LocalBinder can't be
     * accessed by plug-in.
     * it would be risky if plug-in really returns true directly without any security check.
     * if this happen, other 3rd party component can access this binder, too.
     *
     * @return true if Plug-in need to get the binder
     */
    boolean publishBinderDirectly();

    /**
     * called when the NetworkSelect notification is about to show.
     * plugin can customize the notification text or PendingIntent
     *
     * @param notification the notification to be shown
     * @param titleText the title
     * @param expandedText the expanded text
     * @param pi the PendingIntent when click the notification
     */
    void customizeNetworkSelectionNotification(Notification notification, String titleText, String expandedText, PendingIntent pi);

    /**
     * called when an incoming call, need to check whether it is a black number.
     * @param context
     * @param connection
     * @return true if it is a incoming black number.
     */
    boolean shouldBlockNumber(Context context, Connection connection);

    /**
     * called when an incoming call, add reject CallLog to db
     * @param ci
     * @param phoneAccountHandle
     */
    void addRejectCallLog(CallerInfo ci, PhoneAccountHandle phoneAccountHandle);

    /**
     * Whether need to remove "Ask First" item from call with selection list.
     *
     * @return true if need to remove it.
     */
    boolean needRemoveAskFirstFromSelectionList();
}
