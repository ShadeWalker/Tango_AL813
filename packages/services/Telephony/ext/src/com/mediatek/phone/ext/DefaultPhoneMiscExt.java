package com.mediatek.phone.ext;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.Connection;

public class DefaultPhoneMiscExt implements IPhoneMiscExt {

    @Override
    public boolean onPhoneGlobalsBroadcastReceive(Context context, Intent intent) {
        return false;
    }

    @Override
    public boolean publishBinderDirectly() {
        return false;
    }

    @Override
    public void customizeNetworkSelectionNotification(Notification notification, String titleText, String expandedText, PendingIntent pi) {
        // do nothing
    }

    @Override
    public boolean shouldBlockNumber(Context context, Connection connection) {
        return false;
    }

    @Override
    public void addRejectCallLog(CallerInfo ci, PhoneAccountHandle phoneAccountHandle) {
        // do nothing
    }

    /**
     * Whether need to remove "Ask First" item from call with selection list.
     *
     * @return true if need to remove it.
     */
    @Override
    public boolean needRemoveAskFirstFromSelectionList() {
        return false;
    }
}
