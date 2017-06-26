package com.mediatek.galleryfeature.panorama;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.BitmapFactory.Options;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.util.BitmapUtils;
import com.mediatek.galleryframework.util.Utils;

public class PanoramaHelper {
    private static final String TAG = "MtkGallery2/PanoramaHelper";

    public static final int PANORAMA_ASPECT_RATIO_RESIZE = 4;
    public static final float PANORAMA_ASPECT_RATIO_MIN = 2.5f;
    public static final float PANORAMA_ASPECT_RATIO_MAX = 10.f;
    public static final float MAX_HEIGHT_DEGREE = 50.f;
    public static final int MESH_RADIUS = 4;
    public static final int FRAME_TIME_GAP = 50;
    public static final int FRAME_DEGREE_GAP = 1;

    public static final float PANORAMA_P80_WIDTHPERCENT = 0.8f;
    public static final float PANORAMA_MIN_WIDTHPERCENT = 0.5f;

    private static final int MICRO_THUMBNAIL_TARGET_SIZE = 512;
    private static final int FANCY_THUMBNAIL_TARGET_SIZE = 512;
    private static final int MID_THUMBNAIL_TARGET_SIZE = 1024;

    private static final float MICRO_THUMB_ANTIALIAS_SCALE = 2.0f;
    private static final float FANCY_THUMB_ANTIALIAS_SCALE = 2.0f;
    private static final float MID_THUMB_ANTIALIAS_SCALE = 2.0f;

    private static int mMiddleThumbWidth;
    private static int mMiddleThumbHeight;

    public static void initialize(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        setMiddleThumbSize(metrics.widthPixels,
                metrics.heightPixels);
    }

    private static void setMiddleThumbSize(int w, int h) {
        mMiddleThumbWidth = w > h ? w : h;
        mMiddleThumbHeight = h > w ? w : h;
        MtkLog.i(TAG, "<setMiddleThumbSize> mMiddleThumbWidth = "
                + mMiddleThumbWidth + ", mMiddleThumbHeight = "
                + mMiddleThumbHeight);
    }

    public static int getDecodeTargetSize(ThumbType thumbType) {
        switch (thumbType) {
        case MICRO:
            return MICRO_THUMBNAIL_TARGET_SIZE;
        case FANCY:
            return FANCY_THUMBNAIL_TARGET_SIZE;
        default:
            return MID_THUMBNAIL_TARGET_SIZE;
        }
    }

    public static float getAntialiasScale(ThumbType thumbType) {
        switch (thumbType) {
        case MICRO:
            return MICRO_THUMB_ANTIALIAS_SCALE;
        case FANCY:
            return FANCY_THUMB_ANTIALIAS_SCALE;
        default:
            return MID_THUMB_ANTIALIAS_SCALE;
        }
    }

    public static int getMiddleThumbWidth() {
        return mMiddleThumbWidth;
    }

    public static int getMiddleThumbHeight() {
        return mMiddleThumbHeight;
    }

    public static Bitmap resizeBitmapToProperRatio(Bitmap bitmap, boolean recycle) {
        if (bitmap == null) {
            MtkLog.i(TAG, "<resizeBitmapToProperRatio> bitmap == null, return null");
            return null;
        }
        int newWidth = getProperRatioBitmapWidth(bitmap.getWidth(), bitmap.getHeight());
        if (newWidth == bitmap.getWidth())
            return bitmap;

        Bitmap target = Bitmap.createBitmap(newWidth, bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(target);
        canvas.scale((float) newWidth / (float) bitmap.getWidth(), 1.0f);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        if (recycle)
            bitmap.recycle();
        MtkLog.i(TAG, "<resizeBitmapToProperRatio> resize to w = " + target.getWidth() + ", h = "
                + target.getHeight());
        return target;
    }

    public static int getProperRatioBitmapWidth(int width, int height) {
        if ((float) width / (float) height > PANORAMA_ASPECT_RATIO_RESIZE) {
            return width;
        }
        return height * PANORAMA_ASPECT_RATIO_RESIZE;
    }

    private static Bitmap decode(FileDescriptor fd, int targetSize) {
        if (fd == null)
            return null;
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);

        int w = options.outWidth;
        int h = options.outHeight;

        float scale = (float) targetSize / Math.max(w, h);
        options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        options.inJustDecodeBounds = false;
        BitmapUtils.setOptionsMutable(options);
        Bitmap result = BitmapFactory.decodeFileDescriptor(fd, null, options);
        if (result == null) {
            MtkLog.i(TAG, "<decode> BitmapFactory.decodeFileDescriptor return null");
            return null;
        }
        scale = (float) targetSize
                / Math.max(result.getWidth(), result.getHeight());
        if (scale <= 0.5)
            result = BitmapUtils.resizeBitmapByScale(result, scale, true);
        return BitmapUtils.ensureGLCompatibleBitmap(result);
    }

    public static Bitmap decodePanoramaBitmap(MediaData md, int targetSize, Context context) {
        String filePath = md.filePath;
        FileDescriptor fd;
        if (filePath != null && !filePath.equals("")) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(filePath);
                fd = fis.getFD();
                return decode(fd, targetSize);
            } catch (FileNotFoundException e) {
                MtkLog.w(TAG, "<decodePanoramaBitmap>, FileNotFoundException", e);
                return null;
            } catch (IOException e) {
                MtkLog.w(TAG, "<decodePanoramaBitmap>, IOException", e);
                return null;
            } finally {
                Utils.closeSilently(fis);
            }
        } else if (md.uri != null && context != null) {
            ParcelFileDescriptor pfd = null;
            try {
                pfd = context.getContentResolver().openFileDescriptor(md.uri, "r");
                fd = pfd.getFileDescriptor();
                return decode(fd, targetSize);
            } catch (FileNotFoundException e) {
                MtkLog.w(TAG, "<decodePanoramaBitmap>, FileNotFoundException", e);
                return null;
            } finally {
                Utils.closeSilently(pfd);
            }
        }
        MtkLog.w(TAG, "<decodePanoramaBitmap> return null at the end");
        return null;
    }

    private static FileDescriptor getFileDescripter(MediaData md, ContentResolver cr) {
        String filePath = md.filePath;
        if (filePath != null && !filePath.equals("")) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(filePath);
                return fis.getFD();
            } catch (FileNotFoundException e) {
                MtkLog.w(TAG, "<getFileDescripter>, FileNotFoundException", e);
                return null;
            } catch (IOException e) {
                MtkLog.w(TAG, "<getFileDescripter>, IOException", e);
                return null;
            } finally {
                Utils.closeSilently(fis);
            }
        } else if (md.uri != null && cr != null) {
            ParcelFileDescriptor pfd = null;
            try {
                pfd = cr.openFileDescriptor(md.uri, "r");
                return pfd.getFileDescriptor();
            } catch (FileNotFoundException e) {
                MtkLog.w(TAG, "<getFileDescripter>, FileNotFoundException", e);
                return null;
            } finally {
                Utils.closeSilently(pfd);
            }
        }
        return null;
    }

    public static class PanoramaEntry {
        public Bitmap mBitmap;
        public PanoramaConfig mConfig;
    }

    public static PanoramaEntry getThumbEntry(MediaData md, ThumbType thumbnailType) {
        return getThumbEntry(md, thumbnailType, null);
    }

    public static PanoramaEntry getThumbEntry(MediaData md, ThumbType thumbnailType, Context context) {
        PanoramaEntry entry = new PanoramaEntry();
        // decode bitmap
        entry.mBitmap = PanoramaHelper.decodePanoramaBitmap(md, PanoramaHelper
                .getDecodeTargetSize(thumbnailType), context);
        if (entry.mBitmap == null) {
            MtkLog.i(TAG, "<getThumbEntry> entry.mBitmap == null, return null 1");
            return null;
        }
        entry.mBitmap = BitmapUtils.rotateBitmap(entry.mBitmap, md.orientation, true);
        entry.mBitmap = PanoramaHelper.resizeBitmapToProperRatio(entry.mBitmap, true);
        // prepare configuration
        int canvasWidth = thumbnailType.getTargetSize();
        int canvasHeight = (int) ((float) canvasWidth
                * (float) PanoramaHelper.getMiddleThumbHeight()
                / (float) PanoramaHelper.getMiddleThumbWidth());
        if (entry.mBitmap == null) {
            MtkLog.i(TAG, "<getThumbEntry> entry.mBitmap == null, return null 2");
            return null;
        }
        entry.mConfig = new PanoramaConfig(entry.mBitmap.getWidth(),
                entry.mBitmap.getHeight(), canvasWidth, canvasHeight,
                PanoramaHelper.getAntialiasScale(thumbnailType));
        return entry;
    }

    public static float getWidthPercent(int width, int height) {
        float ratio = (float) width / (float) height;
        float widthPercent = 1.f - ratio * 0.04f;
        //widthPercent = widthPercent > PANORAMA_P80_WIDTHPERCENT ? PANORAMA_P80_WIDTHPERCENT
        //        : widthPercent;
        widthPercent = widthPercent < PANORAMA_MIN_WIDTHPERCENT ? PANORAMA_MIN_WIDTHPERCENT
                : widthPercent;
        return widthPercent;
    }

    public static PanoramaTexture newPlaceholderPanoramaTexture(MediaData md, int color) {
        PanoramaConfig config = null;
        int width;
        int height;
        if (md.orientation == 90 || md.orientation == 270) {
            height = md.width;
            width = md.height;
        } else {
            height = md.height;
            width = md.width;
        }
        width = PanoramaHelper.getProperRatioBitmapWidth(width, height);
        config = new PanoramaConfig(width, height, PanoramaHelper.getMiddleThumbWidth(),
                PanoramaHelper.getMiddleThumbHeight());
        return new PanoramaTexture(color, config, md.orientation);
    }
}