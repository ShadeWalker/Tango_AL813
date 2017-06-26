package com.mediatek.galleryfeature.video;

import android.graphics.Bitmap;

import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.util.BitmapUtils;
import com.mediatek.galleryframework.util.DecodeUtils;
import com.mediatek.galleryframework.util.MtkLog;

public class VideoItem extends ExtItem {
    private static final String TAG = "MtkGallery2/VideoItem";

    public VideoItem(MediaData data) {
        super(data);
    }

    public Thumbnail getThumbnail(ThumbType thumbType) {
        if (thumbType != ThumbType.MIDDLE) {
            return null;
        }
        MtkLog.d(TAG, "<getThumbnail> begin");
        String path = FrameHandler.getFrameSavingPath(mMediaData.filePath);
        int targetSize = thumbType.getTargetSize();
        Bitmap bitmap = DecodeUtils.decodeOriginRatioThumbnail(path, targetSize);
        MtkLog.d(TAG, "<getThumbnail> end");
        return new Thumbnail(bitmap, true);
    }

    public Bitmap getSquareThumbnailFromBuffer(byte[] buffer, int targetSize) {
        // video drm can not decode from buffer, origin decode way
        Bitmap bitmap = DecodeUtils.decodeVideoThumbnail(mMediaData.filePath);
        bitmap = BitmapUtils.resizeAndCropCenter(bitmap, targetSize, true);
        return bitmap;
    }

    public Bitmap getOriginRatioThumbnailFromBuffer(byte[] buffer, int targetSize) {
        // video drm can not decode from buffer, origin decode way
        Bitmap bitmap = DecodeUtils.decodeVideoThumbnail(mMediaData.filePath);
        bitmap = BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
        return bitmap;
    }

    public boolean isNeedToCacheThumb(ThumbType thumbType) {
        if (thumbType == ThumbType.MICRO) {
            return true;
        } else if (thumbType == ThumbType.MIDDLE) {
            return !FeatureConfig.supportSlideVideoPlay;
        } else {
            return super.isNeedToCacheThumb(thumbType);
        }
    }

    public boolean isNeedToGetThumbFromCache(ThumbType thumbType) {
        if (thumbType == ThumbType.MICRO) {
            return true;
        } else if (thumbType == ThumbType.MIDDLE) {
            return !FeatureConfig.supportSlideVideoPlay;
        } else {
            return super.isNeedToCacheThumb(thumbType);
        }
    }
}