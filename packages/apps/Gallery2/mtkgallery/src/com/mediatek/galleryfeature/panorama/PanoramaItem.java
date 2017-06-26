package com.mediatek.galleryfeature.panorama;

import android.graphics.Bitmap;

import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.ExtItem.Thumbnail;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ThumbType;

public class PanoramaItem extends ExtItem {
    private static final String TAG = "MtkGallery2/PanoramaItem";

    public PanoramaItem(MediaData data) {
        super(data);
    }

    public MediaData.MediaType getType() {
        return MediaData.MediaType.PANORAMA;
    }

    public Thumbnail getThumbnail(ThumbType thumbType) {
        if (thumbType == ThumbType.FANCY || thumbType == ThumbType.MICRO) {
            PanoramaThumbGetter getter = new PanoramaThumbGetter(mMediaData,
                    thumbType, thumbType.getTargetSize(), null);
            Bitmap bitmap = getter.getThumbnail(0);
            getter.recycle();
            return new Thumbnail(bitmap, false);
        } else
            return null;
    }
}