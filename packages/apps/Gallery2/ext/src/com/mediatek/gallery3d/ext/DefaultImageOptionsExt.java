package com.mediatek.gallery3d.ext;

import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.ui.PositionController;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.TileImageViewAdapter;
import com.android.gallery3d.ui.PhotoView.Picture;
import com.mediatek.galleryframework.base.MediaData.MediaType;

public class DefaultImageOptionsExt implements IImageOptionsExt {
    private static final String TAG = "Gallery2/DefaultImageOptionsExt";

    @Override
    public void setMediaItem(MediaItem mediaItem) {
    }

    @Override
    public float getImageDisplayScale(float initScale) {
        return Math.min(4.0f, initScale);
    }

    @Override
    public float getMinScaleLimit(MediaType mediaType, float scale) {
        return scale;
    }

    @Override
    public void updateTileProviderWithScreenNail(TileImageViewAdapter adapter,
            ScreenNail screenNail) {
    }

    @Override
    public void updateMediaType(Picture picture, ScreenNail screenNail) {
    }

    @Override
    public void updateBoxMediaType(PositionController controller, int index,
            MediaType mediaType) {
    }
}