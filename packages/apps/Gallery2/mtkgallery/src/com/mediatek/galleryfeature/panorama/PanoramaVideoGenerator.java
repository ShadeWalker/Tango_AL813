package com.mediatek.galleryfeature.panorama;

import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryfeature.btovgenerator.BitmapStreamToVideoGenerator;
import com.mediatek.galleryfeature.btovgenerator.BitmapStreamToVideoGenerator.VideoConfig;
import com.mediatek.galleryfeature.config.ShareConfig;

import android.content.Context;
import android.graphics.Bitmap;

public class PanoramaVideoGenerator extends BitmapStreamToVideoGenerator {
    private static final String TAG = "MtkGallery2/PanoramaVideoGenerator";
    private static final int MAX_FRAME_COUNT = 45;

    private MediaData mData;
    private PanoramaThumbGetter mThumbGetter;
    private Context mContext;

    public PanoramaVideoGenerator(Context context) {
        super();
        mContext = context;
    }

    public void init(MediaData data, int videoType, VideoConfig config) {
        assert (data != null && config != null);
        mThumbGetter = new PanoramaThumbGetter(data, ThumbType.MIDDLE,
                ShareConfig.PANORAMA_SHAREVIDEO_TARGETSIZE, mContext);
        mThumbGetter.setFrameCount(MAX_FRAME_COUNT);
        config.bitRate = ShareConfig.PANORAMA_SHAREVIDEO_BITRATE;
        config.frameInterval = 1000 / ShareConfig.PANORAMA_SHAREVIDEO_FPS;
        config.frameCount = MAX_FRAME_COUNT;
        config.frameWidth = mThumbGetter.getThumbnailWidth();
        config.frameHeight = mThumbGetter.getThumbnailHeight();
    }

    public void deInit(MediaData data, int videoType) {
        if (mThumbGetter != null) {
            mThumbGetter.recycle();
            mThumbGetter = null;
        }
    }

    public Bitmap getBitmapAtFrame(MediaData item, int videoType, int frameIndex) {
        return mThumbGetter.getThumbnail(frameIndex);
    }

    public void onCancelRequested(MediaData item, int videoType) {

    }
}
