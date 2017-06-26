package com.mediatek.galleryfeature.drm;

import java.util.ArrayList;

import com.mediatek.drm.OmaDrmStore;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.ExtItem.SupportOperation;
import com.mediatek.galleryframework.base.MediaCenter;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.util.BitmapUtils;
import com.mediatek.galleryframework.util.MtkLog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;

public class DrmItem extends ExtItem {
    private static final String TAG = "MtkGallery2/DrmItem";
    private Context mContext;
    private MediaCenter mMediaCenter;
    private ExtItem mRealItem;
    private int mOriginalImageWidth = 0;
    private int mOriginalImageHeight = 0;

    public DrmItem(Context context, MediaData data, MediaCenter center) {
        super(data);
        mContext = context;
        mMediaCenter = center;
        mRealItem = mMediaCenter.getRealItem(mMediaData);
        MtkLog.i(TAG, "<new> mRealItem = " + mRealItem + ", caption = "
                + mMediaData.caption);
    }

    public MediaData.MediaType getType() {
        return MediaData.MediaType.DRM;
    }

    public Thumbnail getThumbnail(ThumbType thumbType) {
        MtkLog.i(TAG, "<getThumbnail> caption = " + mMediaData.caption);
        if (mRealItem == null) {
            MtkLog.i(TAG, "<getThumbnail> mRealItem == null, return");
            return new Thumbnail(null, false);
        }

        boolean hasRightsToShow = DrmHelper.hasRightsToShow(mContext,
                mMediaData);
        if (!hasRightsToShow) {
            MtkLog.i(TAG, "<getThumbnail> no rights to show, return");
            return new Thumbnail(null, false);
        }

        byte[] buffer = DrmHelper.forceDecryptFile(mMediaData.filePath, false);
        if (buffer == null) {
            MtkLog.i(TAG, "<getThumbnail> buffer == null, return");
            return new Thumbnail(null, false);
        }

        Bitmap bitmap = null;
        if (thumbType == ThumbType.MICRO) {
            bitmap = mRealItem.getSquareThumbnailFromBuffer(buffer, thumbType
                    .getTargetSize());
        } else if (thumbType == ThumbType.FANCY) {
            bitmap = mRealItem.getOriginRatioThumbnailFromBuffer(buffer,
                    thumbType.getTargetSize());
        } else if (thumbType == ThumbType.MIDDLE) {
            bitmap = getDrmThumbnail(thumbType, true);
        }

        switch (thumbType) {
        case MICRO:
        case FANCY:
        case MIDDLE:
            bitmap = BitmapUtils.ensureGLCompatibleBitmap(bitmap);
            return new Thumbnail(bitmap, bitmap == null && mMediaData.isVideo);
        default:
            MtkLog.i(TAG, "<getThumbnail> invalid thumb type " + thumbType
                    + ", return");
            return new Thumbnail(null, false);
        }
    }

    public ArrayList<SupportOperation> getSupportedOperations() {
        ArrayList<SupportOperation> res = new ArrayList<SupportOperation>();
        if ((!mMediaData.isVideo) && DrmHelper.checkRightsStatusValid(mContext, mMediaData.filePath,
                OmaDrmStore.Action.WALLPAPER)) {
            res.add(SupportOperation.SETAS);
        }
        if (DrmHelper.checkRightsStatusValid(mContext, mMediaData.filePath,
                OmaDrmStore.Action.TRANSFER)) {
            res.add(SupportOperation.SHARE);
        }
        if ((!mMediaData.isVideo) && DrmHelper.checkRightsStatusValid(mContext, mMediaData.filePath,
                OmaDrmStore.Action.PRINT)) {
            res.add(SupportOperation.PRINT);
        }
        res.add(SupportOperation.PROTECTION_INFO);
        return res;
    }

    public ArrayList<SupportOperation> getNotSupportedOperations() {
        ArrayList<SupportOperation> res = new ArrayList<SupportOperation>();
        res.add(SupportOperation.FULL_IMAGE);
        res.add(SupportOperation.EDIT);
        res.add(SupportOperation.CROP);
        res.add(SupportOperation.ROTATE);
        res.add(SupportOperation.MUTE);
        res.add(SupportOperation.TRIM);
        if (mMediaData.isVideo || !DrmHelper.checkRightsStatusValid(mContext, mMediaData.filePath,
                OmaDrmStore.Action.WALLPAPER)) {
            res.add(SupportOperation.SETAS);
        }
        if (!DrmHelper.checkRightsStatusValid(mContext, mMediaData.filePath,
                OmaDrmStore.Action.TRANSFER)) {
            res.add(SupportOperation.SHARE);
        }
        if (mMediaData.isVideo || !DrmHelper.checkRightsStatusValid(mContext, mMediaData.filePath,
                OmaDrmStore.Action.PRINT)) {
            res.add(SupportOperation.PRINT);
        }
        return res;
    }

    public boolean isNeedToCacheThumb(ThumbType thumbType) {
        if (mMediaData.drmMethod == OmaDrmStore.DrmMethod.METHOD_FL)
            return true;
        return false;
    }

    public boolean isNeedToGetThumbFromCache(ThumbType thumbType) {
        if (mMediaData.drmMethod == OmaDrmStore.DrmMethod.METHOD_FL)
            return true;
        return false;
    }

    public Bitmap getDrmThumbnail(ThumbType thumbType, boolean beforeConsume) {
        if (mRealItem == null) {
            MtkLog.i(TAG, "<getDrmThumbnail> mRealItem == null, return");
            return null;
        }

        boolean hasRightsToShow = DrmHelper.hasRightsToShow(mContext,
                mMediaData);
        if (!hasRightsToShow) {
            MtkLog.i(TAG, "<getDrmThumbnail> no rights to show, return");
            return null;
        }

        byte[] buffer = DrmHelper.forceDecryptFile(mMediaData.filePath, false);
        if (buffer == null) {
            MtkLog.i(TAG, "<getDrmThumbnail> buffer == null, return");
            return null;
        }
        if (mOriginalImageWidth == 0) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(buffer, 0, buffer.length, options);
            mOriginalImageWidth = options.outWidth;
            mOriginalImageHeight = options.outHeight;
        }
        if (thumbType == ThumbType.MICRO) {
            return mRealItem.getSquareThumbnailFromBuffer(buffer,
                    ThumbType.MICRO.getTargetSize());
        } else if (thumbType == ThumbType.MIDDLE || thumbType == ThumbType.HIGHQUALITY) {
            if (mMediaData.drmMethod == OmaDrmStore.DrmMethod.METHOD_FL
                    || beforeConsume == false) {
                int targetSize = thumbType.getTargetSize();
                return mRealItem.getOriginRatioThumbnailFromBuffer(buffer,
                        targetSize);
            } else {
                return mRealItem.getOriginRatioThumbnailFromBuffer(buffer,
                        ThumbType.MICRO.getTargetSize());
            }
        }

        MtkLog.i(TAG, "<getDrmThumbnail> invalid thumb type " + thumbType
                + ", return");
        return null;
    }

    @Override
    public void decodeBounds(Options options) {
        DrmHelper.decodeBounds(mMediaData.filePath, options, false);
    }

    public int getOriginalImageWidth() {
        return mOriginalImageWidth;
    }
    
    public int getOriginalImageHeight() {
        return mOriginalImageHeight;
    }
}
