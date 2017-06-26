package com.mediatek.galleryfeature.refocus;

import android.content.Context;

import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.MediaMember;
import com.mediatek.galleryframework.gl.GLIdleExecuter;
import com.mediatek.galleryframework.base.ExtItem;

public class RefocusMember extends MediaMember {
    private final static String TAG = "MtkGallery2/Refocus/RefocusMember";

    public RefocusMember(Context context) {
        super(context);
    }

    @Override
    public boolean isMatching(MediaData md) {
        return md.isRefocus;
    }

    @Override
    public ExtItem getItem(MediaData md) {
        return new RefocusItem(md);
    }

    @Override
    public MediaData.MediaType getMediaType() {
        return MediaData.MediaType.REFOCUS;
    }
}