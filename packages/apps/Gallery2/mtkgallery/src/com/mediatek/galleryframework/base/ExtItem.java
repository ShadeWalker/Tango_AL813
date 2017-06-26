package com.mediatek.galleryframework.base;

import com.mediatek.galleryfeature.pq.ImageDC;
import com.mediatek.galleryframework.util.DecodeUtils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import java.util.ArrayList;

public class ExtItem {
    private static final String TAG = "MtkGallery2/ExtItem";

    public enum SupportOperation {
        DELETE, ROTATE, SHARE, CROP, SHOW_ON_MAP, SETAS, 
        FULL_IMAGE, PLAY, CACHE, EDIT, INFO, TRIM, UNLOCK, 
        BACK, ACTION, CAMERA_SHORTCUT, MUTE, PRINT, EXPORT,
        PROTECTION_INFO
    }

    public class Thumbnail {
        public Bitmap mBitmap;
        public boolean mStillNeedDecode;

        // if new Thumbnail(null, true), it will still decode thumbnail with google flow
        // if new Thumbnail(null, false), it will not decode thumbnail, display as no thumbnail
        public Thumbnail(Bitmap b, boolean stillNeedDecode) {
            mBitmap = b;
            mStillNeedDecode = stillNeedDecode;
        }
    }

    protected MediaData mMediaData;
    private ImageDC mImageDC;
    protected boolean mIsEnable = true;

    public ExtItem(MediaData md) {
        mMediaData = md;
        mImageDC= new ImageDC(mMediaData.filePath, mMediaData.orientation, mMediaData.mimeType);
    }

    public synchronized void updateMediaData(MediaData md) {
        mMediaData = md;
    }

    public Thumbnail getThumbnail(ThumbType thumbType) {
        return null;
    }

    public Bitmap getSquareThumbnailFromBuffer(byte[] buffer, int targetSize) {
        if (buffer == null)
            return null;
        return DecodeUtils.decodeSquareThumbnail(buffer, targetSize);
    }

    public Bitmap getOriginRatioThumbnailFromBuffer(byte[] buffer,
            int targetSize) {
        if (buffer == null)
            return null;
        return DecodeUtils.decodeOriginRatioThumbnail(buffer, targetSize);
    }

    public ArrayList<SupportOperation> getSupportedOperations() {
        return null;
    }

    public ArrayList<SupportOperation> getNotSupportedOperations() {
        return null;
    }

    public void setEnable(boolean isEnable) {
        mIsEnable = isEnable;
    }

    public boolean isEnable() {
        return mIsEnable;
    }

    public void delete() {
    }

    public boolean isNeedToCacheThumb(ThumbType thumbType) {
        return true;
    }

    public boolean isNeedToGetThumbFromCache(ThumbType thumbType) {
        return true;
    }

    public boolean isAllowPQWhenDecodeCache(ThumbType thumbType) {
        return true;
    }

    public Uri[] getContentUris() {
        return null;
    }

    // The index and string must match with MediaDetails.INDEX_XXX - 1
    public String[] getDetails() {
        return null;
    }

    public boolean isDeleteOriginFileAfterEdit() {
        if (mMediaData.subType == MediaData.SubType.CONSHOT) {
            return false;
        }
        return true;
    }

    public void decodeBounds(Options options) {
    }

    public boolean hasHistorgram () {
        if (mImageDC != null) {
            return mImageDC.hasHistorgram();
        } else {
            return false;
        }
    }

    public int [] getHist () {
        if (mImageDC != null) {
            return mImageDC.getHist();
        } else {
            return null;
        }

    }

    public boolean generateHistogram(Bitmap bitmap) {
        if (mImageDC != null) {
            return mImageDC.generateHistogram(bitmap);
        } else {
            return false;
        }
    }

    public boolean needHistogram () {
        if (mImageDC != null) {
            return mImageDC.isNeedHistogram();
        } else {
            return false;
        }
    }
    public void addHistFlag (BitmapFactory.Options option) {
        if (mImageDC != null) {
            mImageDC.addFlag(option);
        }
    }
    
    public void clearHistFlag (BitmapFactory.Options option) {
        if (mImageDC != null) {
            mImageDC.clearFlag(option);
        }
    }
    
    public static int[] getHist(String filePath) {
        return ImageDC.getHist(filePath);
    }
    
    public String getImageDCFilePath () {
        if (mImageDC != null) {
            return mImageDC.getFilePath();
        } else {
            return null;
        }
    } 
}
