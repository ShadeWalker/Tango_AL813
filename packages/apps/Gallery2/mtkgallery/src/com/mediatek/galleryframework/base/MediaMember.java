package com.mediatek.galleryframework.base;

import android.content.Context;

public class MediaMember {
    protected Context mContext;

    public MediaMember(Context context) {
        mContext = context;
    }

    public boolean isMatching(MediaData md) {
        return true;
    }

    public Player getPlayer(MediaData md, ThumbType type) {
        return null;
    }

    public Generator getGenerator() {
        return null;
    }

    public Layer getLayer() {
        return null;
    }

    public ExtItem getItem(MediaData md) {
        return new ExtItem(md);
    }

    public MediaData.MediaType getMediaType() {
        return MediaData.MediaType.NORMAL;
    }
}