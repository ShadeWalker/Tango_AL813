package com.mediatek.gallery3d.ext;

import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.ui.PositionController;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.TileImageViewAdapter;
import com.android.gallery3d.ui.PhotoView.Picture;
import com.mediatek.galleryframework.base.MediaData.MediaType;

public interface IImageOptionsExt {
    /**
     * set MediaItem to SlideShowView
     *
     * @param mediaItem current mediaItem
     * @return void
     */
    public void setMediaItem(MediaItem mediaItem);

    /**
     * update initScale when doing SlideShow Animation
     *
     * @param initScale slide show default initScale
     * @return updated initScale
     */
    public float getImageDisplayScale(float initScale);

    /**
     * get scale limit by mediaType
     *
     * @param mediaType mediaType of current mediaItem
     * @param scale     default scale
     * @return minimal scale limit
     */
    public float getMinScaleLimit(MediaType mediaType, float scale);

    /**
     * update width and height of TileProvider with sceenNail size
     *
     * @param adapter    TileImageViewAdapter
     * @param screenNail current screenNail
     * @return void
     */
    public void updateTileProviderWithScreenNail(TileImageViewAdapter adapter,
            ScreenNail screenNail);

    /**
     * update mediaType of FullPicture or ScreenNailPicture with screenNail
     *
     * @param picture    current Picture
     * @param screenNail current screenNail
     * @return void
     */
    public void updateMediaType(Picture picture, ScreenNail screenNail);

    /**
     * update certain box's mediaType
     *
     * @param controller PositionController
     * @param index      the index of box
     * @param mediaType  input mediaType
     * @return void
     */
    public void updateBoxMediaType(PositionController controller, int index,
            MediaType mediaType);
}
