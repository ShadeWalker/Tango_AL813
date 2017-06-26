package com.mediatek.gallery3d.util;

import com.android.gallery3d.app.GalleryAppImpl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.mediatek.galleryframework.util.MtkLog;

public class StorageReceiver extends BroadcastReceiver {

    private static final String TAG = "MtkGallery2/StorageReceiver";
        @Override
        public void onReceive(Context context, Intent intent) {
            MtkLog.i(TAG, "StorageReceiver onReceive Intent = " + intent);
            String action = intent.getAction();
            if (Intent.ACTION_MEDIA_UNSHARED.equals(action)) {
                Intent widgetService = new Intent(context, com.android.gallery3d.gadget.WidgetService.class);
                context.startService(widgetService);
            }
        }

    }