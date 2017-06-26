/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.audioprofile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.os.UserHandle;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.AudioManager;
import android.widget.RemoteViews;

import com.android.settings.R;


import com.mediatek.xlog.Xlog;

public class LosslessReceiver extends BroadcastReceiver {

    private static final String TAG = "LosslessReceiver";

    private static final String NOTIFICATION_TAG = "Lossless_notification";
    private static final int LOSSLESS_ICON_ID = SoundEnhancement.LOSSLESS_ICON_ID;
    private static final String SET_LOSSLESSBT_DISABLED = SoundEnhancement.SET_LOSSLESSBT_DISABLED;
    private static final String LOSSLESS_ADD = SoundEnhancement.LOSSLESS_ADD;
    private static final String LOSSLESS_CLOSE = SoundEnhancement.LOSSLESS_CLOSE;
    private static final String LOSSLESS_PLAYING = SoundEnhancement.LOSSLESS_PLAYING;
    private static final String LOSSLESS_STOP = SoundEnhancement.LOSSLESS_STOP;
    private static final String CLOSE_LOSSLESS_NOTIFICATION = SoundEnhancement.CLOSE_LOSSLESS_NOTIFICATION;
    private static final String LOSSLESS_NOT_SUPPORT = SoundEnhancement.LOSSLESS_NOT_SUPPORT;
    //private static final String LOSSLESS_STOP_MUSIC = AudioManager.ACTION_AUDIO_BECOMING_NOISY;

    @Override
    public void onReceive(final Context context, Intent intent) {

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String action = intent.getAction();

        Intent audioProfileIntent = new Intent();
        audioProfileIntent.setComponent(new ComponentName(
            "com.android.settings",
            "com.android.settings.Settings$SoundEnhancementActivity"));


        if (LOSSLESS_CLOSE.equals(action)) {
            Xlog.d(TAG, "close the lossless.");
            cancelNotification(mNotificationManager, LOSSLESS_ICON_ID);
        } else if (LOSSLESS_PLAYING.equals(action)) {
            Xlog.d(TAG, "playing the lossless.");
            createNotification(mNotificationManager, R.drawable.bt_audio_play, audioProfileIntent, context, true, R.string.lossless_playing);
        } else if (LOSSLESS_ADD.equals(action)) {
            Xlog.d(TAG, "open the lossless.");
            createNotification(mNotificationManager, R.drawable.bt_audio, audioProfileIntent, context, false, R.string.lossless_on);
        } else if (LOSSLESS_STOP.equals(action)) {
            Xlog.d(TAG, "stop the lossless.");
            createNotification(mNotificationManager, R.drawable.bt_audio, audioProfileIntent, context, false, R.string.lossless_volume_max);
        } else if (CLOSE_LOSSLESS_NOTIFICATION.equals(action)) {
            Xlog.d(TAG, "close the notification lossless.");
            ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).setParameters(SET_LOSSLESSBT_DISABLED);
        } else if (LOSSLESS_NOT_SUPPORT.equals(action)) {
        	Xlog.d(TAG, "cannot found the lossless device.");
        	createNotification(mNotificationManager, R.drawable.bt_audio, audioProfileIntent, context, false, R.string.lossless_cannot_found_device);
        }
    }

    private void createNotification(NotificationManager mNotificationManager, int icon,
        Intent audioProfileIntent, Context context, boolean iconChange, int textId) {
        PendingIntent pendingIntent = PendingIntent.getActivityAsUser(
            context, 0, audioProfileIntent, PendingIntent.FLAG_CANCEL_CURRENT,
            null, UserHandle.CURRENT);
        
        PendingIntent deleteIntent = PendingIntent.getBroadcast(context, 0,
        		new Intent(CLOSE_LOSSLESS_NOTIFICATION), 0);
        
        Notification.Builder builder = new Notification.Builder(context);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.lossless_notification);

        Notification notification = new Notification();
        notification.contentView = views;
        notification.icon = icon;
        notification.contentIntent = pendingIntent;
        notification.deleteIntent = deleteIntent;
        //notification.flags = Notification.FLAG_NO_CLEAR;

        if (iconChange) {
            views.setImageViewResource(R.id.icon, R.drawable.bt_audio_play);
        } else {
            views.setImageViewResource(R.id.icon, R.drawable.bt_audio);
        }

        views.setTextViewText(R.id.text, context.getResources().getText(textId));
        
        installNotification(mNotificationManager, LOSSLESS_ICON_ID, notification);
    }

    private void installNotification(NotificationManager mNotificationManager,
        final int notificationId, final Notification n) {
        mNotificationManager.notifyAsUser(NOTIFICATION_TAG, notificationId, n, UserHandle.CURRENT);
    }

    private void cancelNotification(NotificationManager mNotificationManager,
        int id) {
        mNotificationManager.cancelAsUser(NOTIFICATION_TAG, id, UserHandle.CURRENT);
    }
}
