package com.mediatek.galleryfeature.video;

import android.content.Context;

import com.mediatek.galleryfeature.SlideVideo.SlideVideoLayer;
import com.mediatek.galleryfeature.SlideVideo.SlideVideoPlayer;
import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.MediaMember;
import com.mediatek.galleryframework.base.Player.OutputType;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.gl.GLIdleExecuter;

public class VideoMember extends MediaMember {
    private final static String TAG = "MtkGallery2/VideoMember";
    private GLIdleExecuter mGLExecuter;

    public VideoMember(Context context, GLIdleExecuter exe) {
        super(context);
        mGLExecuter = exe;
    }

    @Override
    public boolean isMatching(MediaData md) {
        if (md.isVideo) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Layer getLayer() {
        if (FeatureConfig.supportSlideVideoPlay) {
            SlideVideoLayer layer = new SlideVideoLayer();
            return layer;
        }
        return null;
    }

    @Override
    public Player getPlayer(MediaData md, ThumbType type) {
        if (type == ThumbType.MIDDLE && FeatureConfig.supportSlideVideoPlay) {
            SlideVideoPlayer player = new SlideVideoPlayer(mContext, md,
                    OutputType.TEXTURE, ThumbType.MIDDLE);
            player.setGLIdleExecuter(mGLExecuter);
            return player;
        } else if (type == ThumbType.MICRO || type == ThumbType.FANCY) {
            if (!FeatureConfig.supportVideoThumbnailPlay
                    || FeatureConfig.isLowRamDevice)
                return null;
            ThumbnailVideoPlayer player = new ThumbnailVideoPlayer(mContext, md,
                    OutputType.TEXTURE, ThumbType.MICRO);
            player.setGenerator(this.getGenerator());
            player.setGLIdleExecuter(mGLExecuter);
            return player;
        }
        return null;
    }

    @Override
    public Generator getGenerator() {
        return new VideoToVideoGenerator();
    }

    @Override
    public ExtItem getItem(MediaData md) {
        return new VideoItem(md);
    }

    @Override
    public MediaData.MediaType getMediaType() {
        return MediaData.MediaType.VIDEO;
    }
}
