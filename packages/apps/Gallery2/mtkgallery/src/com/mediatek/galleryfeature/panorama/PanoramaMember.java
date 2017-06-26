package com.mediatek.galleryfeature.panorama;

import android.content.Context;

import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.MediaMember;
import com.mediatek.galleryframework.base.ThumbType;

public class PanoramaMember extends MediaMember {
    public PanoramaMember(Context context) {
        super(context);
    }

    @Override
    public boolean isMatching(MediaData md) {
        if (md.isVideo || md.height <= 0 || md.width <= 0)
            return false;
        float ratio = 0.f;
        if (md.orientation == 90 || md.orientation == 270) {
            ratio = (float) md.height / (float) md.width;
        } else {
            ratio = (float) md.width / (float) md.height;
        }
        boolean match = (ratio >= PanoramaHelper.PANORAMA_ASPECT_RATIO_MIN
                && ratio <= PanoramaHelper.PANORAMA_ASPECT_RATIO_MAX
                && !md.mimeType.equals("image/gif")
                && !md.mimeType.equals("image/mpo") && !md.mimeType
                .equals("image/x-jps"));
        return match;
    }

    @Override
    public Player getPlayer(MediaData md, ThumbType type) {
        return new PanoramaPlayer(mContext, md, Player.OutputType.TEXTURE, type);
    }

    @Override
    public Generator getGenerator() {
        return new PanoramaVideoGenerator(mContext);
    }

    @Override
    public ExtItem getItem(MediaData md) {
        return new PanoramaItem(md);
    }

    @Override
    public Layer getLayer() {
        return new PanoramaLayer();
    }

    public MediaData.MediaType getMediaType() {
        return MediaData.MediaType.PANORAMA;
    }
}