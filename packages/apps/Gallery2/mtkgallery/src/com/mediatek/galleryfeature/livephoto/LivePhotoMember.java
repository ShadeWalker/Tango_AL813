package com.mediatek.galleryfeature.livephoto;

import android.content.Context;

import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.gl.GLIdleExecuter;
import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryfeature.video.VideoMember;

public class LivePhotoMember extends VideoMember {
    private final static String TAG = "MtkGallery2/LivePhotoMember";

    public LivePhotoMember(Context context, GLIdleExecuter exe) {
        super(context, exe);
        MtkLog.i(TAG, "<LivePhotoMember> new LivePhotoMember");
    }

    @Override
    public boolean isMatching(MediaData md) {
        return md.isLivePhoto;
    }

    @Override
    public Layer getLayer() {
        return new LivePhotoLayer();
    }

    @Override
    public Generator getGenerator() {
        return new LivePhotoToVideoGenerator(mContext);
    }

    @Override
    public ExtItem getItem(MediaData md) {
        return new LivePhotoItem(md);
    }

    @Override
    public MediaData.MediaType getMediaType() {
        return MediaData.MediaType.LIVEPHOTO;
    }
}
