package com.mediatek.galleryfeature.mav;

import java.util.ArrayList;

import android.graphics.Bitmap;

import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.ExtItem.SupportOperation;
import com.mediatek.galleryframework.base.ExtItem.Thumbnail;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryfeature.mav.MavPlayer.Params;
import com.mediatek.galleryframework.util.BitmapUtils;
import com.mediatek.galleryframework.util.MtkLog;

public class MavItem extends ExtItem {
    private final static String TAG = "MtkGallery2/MavItem";
    private boolean mIsCancel = false;
    private MediaData mData;

    // below is intended for MPO feature
    public static final int INCLUDE_MPO_MAV = (1 << 6);
    public static final int INCLUDE_MPO_3D = (1 << 7);
    public static final int INCLUDE_MPO_3D_PAN = (1 << 8);
    public static final int INCLUDE_MPO_UNKNOWN = (1 << 9);
    public static final int ALL_MPO_MEDIA = INCLUDE_MPO_UNKNOWN
            | INCLUDE_MPO_3D_PAN | INCLUDE_MPO_3D | INCLUDE_MPO_MAV;
    public static final int EXCLUDE_MPO_MAV = (1 << 16);

    public MavItem(MediaData md) {
        super(md);
        mData = md;
    }

    public MediaData.MediaType getType() {
        return MediaData.MediaType.MAV;
    }

    public ArrayList<SupportOperation> getSupportedOperations() {
        ArrayList<SupportOperation> res = new ArrayList<SupportOperation>();
        res.add(SupportOperation.EXPORT);
        return res;
    }

    public ArrayList<SupportOperation> getNotSupportedOperations() {
        ArrayList<SupportOperation> res = new ArrayList<SupportOperation>();
        res.add(SupportOperation.FULL_IMAGE);
        res.add(SupportOperation.EDIT);
        return res;
    }

    public Thumbnail getThumbnail(ThumbType thumbType) {
        MtkLog.d(TAG, "<getThumbnail>, thumbType=" + thumbType);
        Params params = new Params();
        params.inType = thumbType;
        params.inThumbnailTargetSize = thumbType.getTargetSize();
        params.inPQEnhance = true;
        MpoDecoderWrapper mpoDecoderWrapper = MpoDecoderWrapper
                .createMpoDecoderWrapper(mMediaData.filePath);
        try {
            Bitmap bitmap = MpoHelper.retrieveThumbData(mIsCancel, params,
                    mpoDecoderWrapper);
            return new Thumbnail(bitmap, false);
        } finally {
            // we must close mpo wrapper manually.
            if (null != mpoDecoderWrapper) {
                mpoDecoderWrapper.close();
            }
        }
    }

    public Bitmap getOriginRatioThumbnailFromBuffer(byte[] buffer,
            int targetSize) {
        Params params = new Params();
        params.inType = ThumbType.MIDDLE;
        params.inThumbnailTargetSize = targetSize;
        params.inPQEnhance = true;
        MpoDecoderWrapper mpoDecoderWrapper = MpoDecoderWrapper
                .createMpoDecoderWrapper(buffer);
        try {
            // retrieve bitmap;
            Bitmap bitmap = null;
            if (null != mpoDecoderWrapper) {
                bitmap = MpoHelper.retrieveThumbData(mIsCancel, params,
                        mpoDecoderWrapper);
                bitmap = BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
            }

            return bitmap;
        } finally {
            // we must close mpo wrapper manually.
            if (null != mpoDecoderWrapper) {
                mpoDecoderWrapper.close();
            }
        }
    }
}
