package com.mediatek.galleryfeature.mav;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryfeature.btovgenerator.BitmapStreamToVideoGenerator;
import com.mediatek.galleryfeature.config.ShareConfig;
import com.mediatek.galleryframework.util.BitmapUtils;
import com.mediatek.galleryframework.util.MtkLog;

public class MavToVideoGenerator extends BitmapStreamToVideoGenerator {
    private static final String TAG = "MtkGallery2/MAVToVideoGenerator";
    private static MpoDecoderWrapper mpoDecoderWrapper;
    private int mTargetSize;
    private int mFrameCount;
    private static BitmapFactory.Options sDecodeOptions = new BitmapFactory.Options();

    public MavToVideoGenerator(Context context) {
        super();
    }

    @Override
    public void deInit(MediaData item, int videoType) {
        if (mpoDecoderWrapper != null) {
            mpoDecoderWrapper.close();
            mpoDecoderWrapper = null;
        }

    }

    @Override
    public Bitmap getBitmapAtFrame(MediaData item, int videoType, int frameIndex) {
        Bitmap outputBitmap = null;
        int curIndex;
        if (VTYPE_SHARE_GIF == videoType) {
            curIndex = (frameIndex * 2 <= mFrameCount) ? frameIndex * 2
                    : mFrameCount * 2 - frameIndex * 2;
        } else {
            curIndex = frameIndex < mFrameCount ? frameIndex
                    : (mFrameCount * 2 - 2 - frameIndex);
        }
        if (mpoDecoderWrapper == null) {
            return null;
        }
        Bitmap bitmap = mpoDecoderWrapper.frameBitmap(curIndex, sDecodeOptions);
        if (bitmap == null) {
            return null;
        }
        outputBitmap = BitmapUtils.resizeDownBySideLength(bitmap, mTargetSize,
                true);
        if (0 != item.orientation) {
            outputBitmap = BitmapUtils.rotateBitmap(outputBitmap, item.orientation,
                    true);
        }
        return outputBitmap;
    }

    @Override
    public void init(MediaData item, int videoType, VideoConfig config) {
        if (item != null && item.filePath != null) {
            mpoDecoderWrapper = MpoDecoderWrapper
                    .createMpoDecoderWrapper(item.filePath);
            if (mpoDecoderWrapper == null) {
                config.frameCount = -1;
                return;
            }
            mFrameCount = mpoDecoderWrapper.frameCount();
            config.frameCount = mFrameCount * 2 - 1;

            if (videoType == VTYPE_SHARE) {
                mTargetSize = ShareConfig.MAV_SHAREVIDEO_TARGETSIZE;
                config.frameInterval = 1000 / ShareConfig.MAV_SHAREVIDEO_FPS;
                config.bitRate = ShareConfig.MAV_SHAREVIDEO_BITRATE;
            } else if (videoType == VTYPE_SHARE_GIF) {
                mTargetSize = ShareConfig.MAV_SHAREGIF_TARGETSIZE;
                config.frameInterval = ShareConfig.MAV_SHAREGIF_INTERVAL;
                config.frameCount = mFrameCount;
            }

            sDecodeOptions.inSampleSize = BitmapUtils.computeSampleSizeLarger(
                    mpoDecoderWrapper.width(), mpoDecoderWrapper.height(),
                    mTargetSize);
            MtkLog.d(TAG, "<init> width:" + mpoDecoderWrapper.width()
                    + ",height:" + mpoDecoderWrapper.height() + ",targetSize:"
                    + mTargetSize);
        }
    }

    @Override
    public void onCancelRequested(MediaData item, int videoType) {

    }

}
