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

public interface Platform {
    public boolean isOutOfDecodeSpec(long fileSize, int width, int height, String mimeType);

    public void enterContainerPage(Activity activity, MediaData data, boolean getContent,
            Bundle bundleData);

    public void switchToContainerPage(Activity activity, MediaData data, boolean getContent,
            Bundle bundleData);

    public SwitchBarView createPanoramaSwitchBarView(Activity activity);

    public IconView createMavIconView(Context context);

    public IVideoPlayer createSVExtension(Context context, MediaData data);

    public IVideoController createController(Context context);
    
    public IVideoHookerCtl createHooker(Activity activity, IActivityHooker rewindAndForwardHooker);
    
    public void submitJob(Work work);
}
