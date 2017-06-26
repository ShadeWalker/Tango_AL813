package com.mediatek.galleryfeature.container;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryfeature.btovgenerator.BitmapStreamToVideoGenerator.VideoConfig;
import com.mediatek.galleryfeature.btovgenerator.BitmapStreamToVideoGenerator;
import com.mediatek.galleryfeature.config.ShareConfig;
import com.mediatek.galleryframework.util.DecodeUtils;
import com.mediatek.galleryframework.util.MtkLog;

public class ContainerVideoGenerator extends BitmapStreamToVideoGenerator {
    private static final String TAG = "MtkGallery2/ContainerVideoGenerator";

    private Context mContext;
    private ArrayList<MediaData> mSubDatas;
    private int mTargetSize;
    private int mWidth;
    private int mHeight;

    public ContainerVideoGenerator(Context context) {
        super();
        mContext = context;
    }

    public void init(MediaData data, int videoType, VideoConfig config) {
        assert (data != null && config != null);
        mSubDatas = ContainerHelper.getPlayData(mContext, data);

        config.frameCount = mSubDatas.size();
        if (videoType == Generator.VTYPE_SHARE) {
            config.bitRate = ShareConfig.CONTAINER_SHAREVIDEO_BITRATE;
            config.frameInterval = 1000 / ShareConfig.CONTAINER_SHAREVIDEO_FPS;
            mTargetSize = ShareConfig.CONTAINER_SHAREVIDEO_TARGETSIZE;
        } else if (videoType == Generator.VTYPE_SHARE_GIF) {
            config.frameInterval = 1000 / ShareConfig.CONTAINER_SHAREGIF_FPS;
            mTargetSize = ShareConfig.CONTAINER_SHAREGIF_TARGETSIZE;
        }
    }

    public void deInit(MediaData item, int videoType) {

    }

    @Override
    public Bitmap getBitmapAtFrame(MediaData data, int videoType, int frameIndex) {
        Bitmap bitmap;

        bitmap = DecodeUtils.decodeOriginRatioThumbnail(mSubDatas
                .get(frameIndex), mTargetSize);
        float rotation = data.orientation;
        if (rotation > 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);
        }

        if (bitmap == null) {
            MtkLog.d(TAG, "<getBitmapAtFrame> bitmap is null");
            return null;
        }

        if (frameIndex == 0) {
            mWidth = bitmap.getWidth();
            mHeight = bitmap.getHeight();
        } else if (bitmap.getWidth() > mWidth || bitmap.getHeight() > mHeight) {
            MtkLog.d(TAG, "<getBitmapAtFrame> width:" + bitmap.getWidth()
                    + " height:" + bitmap.getHeight());
            MtkLog.d(TAG,
                    "<getBitmapAtFrame> bitmap size is not same, return null");
            return null;
        }

        return bitmap;
    }

    public void onCancelRequested(MediaData data, int videoType) {

    }
}
