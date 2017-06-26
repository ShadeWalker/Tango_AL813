package com.mediatek.gallery3d.layout;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory.Options;
import android.util.DisplayMetrics;

import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.util.ThreadPool.CancelListener;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.Path;

import com.mediatek.galleryfeature.mav.MavPlayer;
import com.mediatek.galleryfeature.livephoto.LivePhotoToVideoGenerator;
import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.util.MtkLog;

public class FancyHelper {
    private static final String TAG = "MtkGallery2/FancyHelper";
    private static final boolean ENABLE_FANCY = FeatureConfig.supportFancyHomePage;
    public static final float FANCY_CROP_RATIO = (5.0f / 2.0f);
    public static final float FANCY_CROP_RATIO_LAND = (2.0f / 5.0f);
    public static final float FANCY_CROP_RATIO_CAMERA = (16.0f / 9.0f);
    public static final int ALBUMSETPAGE_COL_LAND = 4;
    public static final int ALBUMSETPAGE_COL_PORT = 2;
    public static final int ALBUMPAGE_COL_LAND = 4;
    public static final int ALBUMPAGE_COL_PORT = 3;
    public static final int LIVEPHOTO_FANCYTHUMB_SIZE = LivePhotoToVideoGenerator.LIVEPHOTO_CROP_WIDTH;
    public static final int MAV_FANCYTHUMB_SIZE = MavPlayer.MAV_THUMBNAIL_SIZE;
    public static final float FANCY_CAMERA_ICON_SIZE_RATE = (1.0f / 9.5f);
    public static final float FANCY_CONTAINER_VIDEO_TARGET_SIZE = 2.0f;

    // for video overlay
    public static final float VIDEO_OVERLAY_RECT_HEIGHT = 20.0f;
    public static final float VIDEO_OVERLAY_LEFT_OFFSET = 10.0f;
    public static final float VIDEO_OVERLAY_RECT_GAP = 27.0f; // Gap:rectHeight ~= 4:3
    public static final int VIDEO_OVERLAY_COLOR = 0xff323232;

    public static boolean isFancyLayoutSupported() {
        return ENABLE_FANCY;
    }

    public static final boolean isLandItem(MediaItem item) {
        if (item == null) return false;

        int rotation = item.getRotation();
        if (rotation == 90 || rotation == 270) {
            return (item.getWidth() >= item.getHeight() ? false : true);
        } else {
            return (item.getHeight() >= item.getWidth() ? false : true);
        }
    }

    public static Path getMediaSetPath(LocalMediaItem item) {
        if (item == null) return null;

        int bucketId = item.getBucketId();
        return Path.fromString("/local/all/" + bucketId);
    }

    public static Path getMediaSetPath(int bucketId) {
        return Path.fromString("/local/all/" + bucketId);
    }

    private static int sHeightPixels = -1;
    private static int sWidthPixels = -1;

    public static int getHeightPixels() {
        return sHeightPixels;
    }

    public static int getWidthPixels() {
        return sWidthPixels;
    }

    public static int getScreenWidthAtFancyMode() {
        return Math.min(sHeightPixels, sWidthPixels);
    }

    public static void doFancyInitialization(int widthPixel, int heightPixel) {
        // just update screen width
        int screenWidth = Math.min(widthPixel, heightPixel);
        if (sHeightPixels > sWidthPixels) {
            sWidthPixels = screenWidth;
        } else {
            sHeightPixels = screenWidth;
        }
        MtkLog.i(TAG, "<doFancyInitialization> <Fancy> w x h: " + sWidthPixels + ", " + sHeightPixels);
        MediaItem.setFancyThumbnailSizes(Math.min(sHeightPixels, sWidthPixels) / 3);
    }

    public static void initializeFancyThumbnailSizes(DisplayMetrics metrics) {
        MediaItem.setFancyThumbnailSizes(Math.min(metrics.heightPixels,
                metrics.widthPixels) / 3);
        sHeightPixels = metrics.heightPixels;
        sWidthPixels = metrics.widthPixels;
    }

    /////////////////////////////////////////////////////////////
    // resize related
    public static Bitmap resizeByWidthOrLength(Bitmap bitmap, int targetSize,
            boolean resizeByWidth, boolean recycle) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int targetWidth = w;
        int targetHeight = h;
        float scale = 1.0f;

        if (resizeByWidth) {
            if (w == targetSize)
                return bitmap;
            scale = (float) targetSize / w;
            targetWidth = targetSize;
            targetHeight = Math.round(scale * h);
        } else {
            if (h == targetSize)
                return bitmap;
            scale = (float) targetSize / h;
            targetHeight = targetSize;
            targetWidth = Math.round(scale * w);
        }

        Bitmap target = Bitmap.createBitmap(targetWidth, targetHeight,
                getConfig(bitmap));
        Canvas canvas = new Canvas(target);
        canvas.scale(scale, scale);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        if (recycle)
            bitmap.recycle();
        return target;
    }

    public static Bitmap resizeAndCropCenter(Bitmap bitmap, int targetWidth,
            int targetHeight, boolean scaleByWidth, boolean recycle) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w == targetWidth && h == targetHeight)
            return bitmap;

        float scaleX = (float) targetWidth / w;
        float scaleY = (float) targetHeight / h;
        float scale = scaleByWidth ? scaleX : scaleY;

        Bitmap target = Bitmap.createBitmap(targetWidth, targetHeight,
                getConfig(bitmap));
        w = Math.round(scale * bitmap.getWidth());
        h = Math.round(scale * bitmap.getHeight());
        Canvas canvas = new Canvas(target);
        canvas.translate((targetWidth - w) / 2f, (targetHeight - h) / 2f);
        canvas.scale(scale, scale);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        if (recycle)
            bitmap.recycle();
        return target;
    }

    private static Bitmap.Config getConfig(Bitmap bitmap) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == null) {
            config = Bitmap.Config.ARGB_8888;
        }
        return config;
    }

    /////////////////////////////////////////////////////////////
    // move DecodeUtils to here
    public static Bitmap decodeThumbnail(
            JobContext jc, String filePath, Options options, int targetSize, int type) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(filePath);
            FileDescriptor fd = fis.getFD();
            return decodeThumbnail(jc, fd, options, targetSize, type);
        } catch (FileNotFoundException e) {
            MtkLog.i(TAG, "<decodeThumbnail> FileNotFoundException ", e);
            return null;
        } catch (IOException e) {
            MtkLog.i(TAG, "<decodeThumbnail> IOException ", e);
            return null;
        } finally {
            Utils.closeSilently(fis);
        }
    }

    private static Bitmap decodeThumbnail(
            JobContext jc, FileDescriptor fd, Options options, int targetSize, int type) {
        if (type != MediaItem.TYPE_FANCYTHUMBNAIL) return null;

        if (options == null) options = new Options();
        jc.setCancelListener(new DecodeCanceller(options));

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        if (jc.isCancelled()) return null;

        int w = options.outWidth;
        int h = options.outHeight;

        if (((float) w / (float) h >= FancyHelper.FANCY_CROP_RATIO
                        || (float) h / (float) w >= FancyHelper.FANCY_CROP_RATIO)) {
            int halfScreenWidth = getScreenWidthAtFancyMode() / 2;
            float scale = (float) halfScreenWidth / Math.min(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        } else {
            // For screen nail, we only want to keep the longer side >= targetSize.
            float scale = (float) targetSize / Math.max(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        }

        options.inJustDecodeBounds = false;
        setOptionsMutable(options);

        Bitmap result = BitmapFactory.decodeFileDescriptor(fd, null, options);
        if (result == null) return null;
        return ensureGLCompatibleBitmap(result);
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    public static void setOptionsMutable(Options options) {
        if (ApiHelper.HAS_OPTIONS_IN_MUTABLE) options.inMutable = true;
    }

    // TODO: This function should not be called directly from
    // DecodeUtils.requestDecode(...), since we don't have the knowledge
    // if the bitmap will be uploaded to GL.
    public static Bitmap ensureGLCompatibleBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.getConfig() != null) return bitmap;
        Bitmap newBitmap = bitmap.copy(Config.ARGB_8888, false);
        bitmap.recycle();
        return newBitmap;
    }

    private static class DecodeCanceller implements CancelListener {
        Options mOptions;

        public DecodeCanceller(Options options) {
            mOptions = options;
        }

        @Override
        public void onCancel() {
            mOptions.requestCancelDecode();
        }
    }
}
