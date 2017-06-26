package com.mediatek.email.ext;

import android.content.Context;

/**
 * Interface definition of sending notification plugin.
 */
public interface ISendNotification {
    public void showSendingNotification(Context context, long accountId, int eventType, int messageCount);

    public void suspendSendFailedNotification(long accountId);

    public void cancelSendingNotification();
}
