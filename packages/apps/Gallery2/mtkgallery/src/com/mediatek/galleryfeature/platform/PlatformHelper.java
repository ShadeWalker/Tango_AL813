package com.mediatek.galleryfeature.platform;


import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.mediatek.gallery3d.ext.IActivityHooker;
import com.mediatek.galleryfeature.SlideVideo.IVideoController;
import com.mediatek.galleryfeature.SlideVideo.IVideoHookerCtl;
import com.mediatek.galleryfeature.SlideVideo.IVideoPlayer;
import com.mediatek.galleryfeature.mav.IconView;
import com.mediatek.galleryfeature.panorama.SwitchBarView;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Work;

public class PlatformHelper {
    private static final String TAG = "MtkGallery2/PlatformHelper";

    private static Platform sPlatform;

    public static void setPlatform(Platform platform) {
        sPlatform = platform;
    }

    public static boolean isOutOfDecodeSpec(long fileSize, int width, int height,
            String mimeType) {
        if (sPlatform != null)
            return sPlatform.isOutOfDecodeSpec(fileSize, width, height, mimeType);
        else
            return false;
    }

    public static void enterContainerPage(Activity activity, MediaData data,
            boolean getContent, Bundle bundleData) {
        if (sPlatform != null)
            sPlatform.enterContainerPage(activity, data, getContent, bundleData);
    }

    public static void switchToContainerPage(Activity activity, MediaData data,
            boolean getContent, Bundle bundleData) {
        if (sPlatform != null)
            sPlatform.switchToContainerPage(activity, data, getContent, bundleData);
    }

    public static SwitchBarView createPanoramaSwitchBarView(Activity activity) {
        if (sPlatform != null)
            return sPlatform.createPanoramaSwitchBarView(activity);
        return null;
    }

    public static IconView createMavIconView(Context context) {
        if (sPlatform != null)
            return sPlatform.createMavIconView(context);
        return null;
    }

    public static IVideoPlayer createSVExtension(Context context, MediaData data) {
        if (sPlatform != null) {
            return sPlatform.createSVExtension(context, data);
        }
        return null;
    }

    public static IVideoController createController(Context context) {
        if (sPlatform != null) {
            return sPlatform.createController(context);
        }
        return null;
    }

    public static IVideoHookerCtl createHooker(Activity activity,
            IActivityHooker rewindAndForwardHooker) {
        if (sPlatform != null) {
            return sPlatform.createHooker(activity, rewindAndForwardHooker);
        }
        return null;
    }

    public static void submitJob(Work work) {
        if (sPlatform != null) sPlatform.submitJob(work);
    }
}
