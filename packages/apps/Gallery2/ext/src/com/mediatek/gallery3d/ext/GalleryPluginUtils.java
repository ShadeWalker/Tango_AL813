package com.mediatek.gallery3d.ext;

import android.content.Context;

import com.mediatek.common.MPlugin;
import com.mediatek.galleryframework.util.MtkLog;

public class GalleryPluginUtils {
    private static final String TAG = "Gallery2/GalleryPluginUtils";
    private static IImageOptionsExt sImageOptions;
    private static boolean sIsImageOptionsPrepared = false;
    private static Context sContext = null;

    public static void initialize(Context context) {
        sContext = context;
    }

    private static void prepareImageOptions(Context context) {
        if (context == null) {
            return;
        }
        if (sImageOptions == null) {
            sImageOptions = (IImageOptionsExt) MPlugin.createInstance(
                    IImageOptionsExt.class.getName(), context.getApplicationContext());
            MtkLog.i(TAG, "<prepareImageOptions> sImageOptions = " + sImageOptions);

            if (sImageOptions == null) {
                MtkLog.i(TAG, "<prepareImageOptions> create DefaultImageOptionsExt!");
                sImageOptions = new DefaultImageOptionsExt();
            }
            sIsImageOptionsPrepared = true;
        }
    }

    public static IImageOptionsExt getImageOptionsPlugin() {
        if (!sIsImageOptionsPrepared) {
            prepareImageOptions(sContext);
        }
        return (sImageOptions != null ? sImageOptions : new DefaultImageOptionsExt());
    }
}
