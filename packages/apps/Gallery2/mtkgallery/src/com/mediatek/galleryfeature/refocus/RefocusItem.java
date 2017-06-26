package com.mediatek.galleryfeature.refocus;

import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.MediaData;

public class RefocusItem extends ExtItem {
    private static final String TAG = "MtkGallery2/Refocus/RefocusItem";
    
    public RefocusItem(MediaData mediadata) {
        super(mediadata);
    }
    
    public MediaData.MediaType getType() {
        return MediaData.MediaType.REFOCUS;
    }
    
}
