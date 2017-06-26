package com.mediatek.galleryfeature.gif;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.mediatek.galleryfeature.platform.PlatformHelper;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.ExtItem.SupportOperation;
import com.mediatek.galleryframework.base.ExtItem.Thumbnail;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.util.BitmapUtils;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.util.Utils;

public class GifItem extends ExtItem {
    private static final String TAG = "MtkGallery2/GifItem";
    public static final int GIF_BACKGROUND_COLOR = 0xFFFFFFFF;
    private Context mContext;

    public GifItem(MediaData data, Context context) {
        super(data);
        mContext = context;
    }

    public MediaData.MediaType getType() {
        return MediaData.MediaType.GIF;
    }

    public Thumbnail getThumbnail(ThumbType thumbType) {
        if (PlatformHelper.isOutOfDecodeSpec(mMediaData.fileSize, mMediaData.width, 
                mMediaData.height, mMediaData.mimeType)) {
            MtkLog.i(TAG, "<getThumbnail> " + mMediaData.filePath
                    + ", out of spec limit, abort generate thumbnail!");
            return new Thumbnail(null, false);
        }
        Bitmap bitmap = null;
        if (mMediaData.filePath != null && !mMediaData.filePath.equals(""))
            bitmap = decodeGifThumbnail(mMediaData.filePath);
        else if (mMediaData.uri != null)
            bitmap = decodeGifThumbnail(mMediaData.uri);
        bitmap = BitmapUtils.replaceBackgroundColor(bitmap,
                GIF_BACKGROUND_COLOR, true);
        // if decodeGifThumbnail return null, then return null directly,
        // then decode thumbnail with google default decode routine
        return new Thumbnail(bitmap, true);
    }

    public ArrayList<SupportOperation> getNotSupportedOperations() {
        ArrayList<SupportOperation> res = new ArrayList<SupportOperation>();
        res.add(SupportOperation.FULL_IMAGE);
        res.add(SupportOperation.EDIT);
        return res;
    }

    public Bitmap getSquareThumbnailFromBuffer(byte[] buffer, int targetSize) {
        if (buffer == null)
            return null;
        Bitmap bitmap = decodeGifThumbnail(buffer);
        bitmap = BitmapUtils.resizeAndCropCenter(bitmap, targetSize, true);
        bitmap = BitmapUtils.replaceBackgroundColor(bitmap,
                GIF_BACKGROUND_COLOR, true);
        return bitmap;
    }

    public Bitmap getOriginRatioThumbnailFromBuffer(byte[] buffer,
            int targetSize) {
        if (buffer == null)
            return null;
        Bitmap bitmap = decodeGifThumbnail(buffer);
        bitmap = BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
        bitmap = BitmapUtils.replaceBackgroundColor(bitmap,
                GIF_BACKGROUND_COLOR, true);
        return bitmap;
    }

    private static Bitmap decodeGifThumbnail(String filePath) {
        GifDecoderWrapper gifDecoderWrapper = null;
        try {
            gifDecoderWrapper = GifDecoderWrapper
                    .createGifDecoderWrapper(filePath);
            if (gifDecoderWrapper == null)
                return null;
            Bitmap bitmap = gifDecoderWrapper.getFrameBitmap(0);
            return bitmap;
        } finally {
            if (null != gifDecoderWrapper) {
                gifDecoderWrapper.close();
            }
        }
    }

    private static Bitmap decodeGifThumbnail(byte[] buffer) {
        GifDecoderWrapper gifDecoderWrapper = null;
        try {
            gifDecoderWrapper = GifDecoderWrapper.createGifDecoderWrapper(
                    buffer, 0, buffer.length);
            if (gifDecoderWrapper == null)
                return null;
            Bitmap bitmap = gifDecoderWrapper.getFrameBitmap(0);
            return bitmap;
        } finally {
            if (null != gifDecoderWrapper) {
                gifDecoderWrapper.close();
            }
        }
    }

    private Bitmap decodeGifThumbnail(Uri uri) {
        GifDecoderWrapper gifDecoderWrapper = null;
        ParcelFileDescriptor pfd = null;
        try {
            pfd = mContext.getContentResolver()
                    .openFileDescriptor(uri, "r");
            if (pfd == null) {
                MtkLog.w(TAG, "<decodeGifThumbnail>, pdf is null");
                return null;
            }
            FileDescriptor fd = pfd.getFileDescriptor();
            if (fd == null) {
                MtkLog.w(TAG, "<decodeGifThumbnail>, fd is null");
                return null;
            }
            gifDecoderWrapper = GifDecoderWrapper.createGifDecoderWrapper(fd);
            if (gifDecoderWrapper == null)
                return null;
            Bitmap bitmap = gifDecoderWrapper.getFrameBitmap(0);
            return bitmap;
        } catch (FileNotFoundException e) {
            MtkLog.w(TAG, "<decodeGifThumbnail>, FileNotFoundException", e);
            return null;
        } finally {
            Utils.closeSilently(pfd);
            if (null != gifDecoderWrapper) {
                gifDecoderWrapper.close();
            }
        }
    }
}