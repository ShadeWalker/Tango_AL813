package com.mediatek.galleryfeature.pq;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.view.WindowManager;
import com.mediatek.galleryframework.util.MtkLog;
public class PQUtils {
    private static final String TAG = "MtkGallery2/PQUtils";

    public static int caculateInSampleSize(Context mContext, String mUri , int targetSize) {
        FileDescriptor fd = null;
        FileInputStream fis = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            fis = getFileInputStream(mContext, mUri);
            if (fis != null) {
                fd = fis.getFD();
                if (fd != null) {
                    BitmapFactory.decodeFileDescriptor(fd, null, options);
                }
            }
            } catch (FileNotFoundException e) {
                MtkLog.e(TAG, "<caculateInSampleSize>bitmapfactory decodestream fail");
            } catch (IOException e) {
                MtkLog.e(TAG, "<caculateInSampleSize>bitmapfactory decodestream fail");
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            float scale = 1;
            if (options.outWidth > 0 && options.outHeight > 0) {
                scale = (float) targetSize / Math.max(options.outWidth, options.outHeight);
            }
        options.inSampleSize = computeSampleSizeLarger(scale);
        MtkLog.d(TAG, "<caculateInSampleSize> options.inSampleSize==" + options.inSampleSize + " width==" + options.outWidth + " height==" + options.outHeight + "targetSize==" + targetSize);
        return options.inSampleSize;
    }

    public static int getRotation(Context mContext, String uri) {
        FileInputStream fis = null;
        // FileDescriptor fd = null;
        final int[] rotation = new int[1];
        MtkLog.d(TAG, "<getRotation> Uri.parse(mUri)==" + uri);
        if (ContentResolver.SCHEME_CONTENT.equals(Uri.parse(uri).getScheme())) {
            querySource(mContext, Uri.parse(uri),
                    new String[] { ImageColumns.ORIENTATION },
                    new ContentResolverQueryCallback() {
                        public void onCursorResult(Cursor cursor) {
                            // TODO Auto-generated method stub
                            rotation[0] = cursor.getInt(0);
                        }
                    });
        }
        return rotation[0];
    }

    public static FileInputStream getFileInputStream(Context mContext, String uri) {
        FileInputStream fis = null;
        //FileDescriptor fd = null;
        try {
            final String[] fullPath = new String[1];
            MtkLog.d(TAG, "<getFileInputStream> Uri.parse(mUri)==" + uri);
            if (ContentResolver.SCHEME_CONTENT.equals(Uri.parse(uri).getScheme())) {
                querySource(mContext,
                        Uri.parse(uri), new String[] { ImageColumns.DATA , ImageColumns.ORIENTATION},
                        new ContentResolverQueryCallback() {
                            public void onCursorResult(Cursor cursor) {
                                // TODO Auto-generated method stub
                                fullPath[0] = cursor.getString(0);
                            }
                        }
                );
            } else {
                fullPath[0] = uri;
            }
            MtkLog.d(TAG, "<getFileInputStream> fullPath[0]==" + fullPath[0]);
            fis = new FileInputStream(fullPath[0]);
            //fd = fis.getFD();
        } catch (FileNotFoundException e) {
            MtkLog.e(TAG, " <getFileInputStream>FileNotFoundException bitmapfactory decodestream fail");
        }
        return fis;
    }

    public static void querySource(Context context, Uri sourceUri, String[] projection,
            ContentResolverQueryCallback callback) {
        ContentResolver contentResolver = context.getContentResolver();
        querySourceFromContentResolver(contentResolver, sourceUri, projection, callback);
    }

    private static void querySourceFromContentResolver(
            ContentResolver contentResolver, Uri sourceUri, String[] projection,
            ContentResolverQueryCallback callback) {
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(sourceUri, projection, null, null,
                    null);
            if ((cursor != null) && cursor.moveToNext()) {
                callback.onCursorResult(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public interface ContentResolverQueryCallback {
        void onCursorResult(Cursor cursor);
    }

    public static boolean isSupportedByRegionDecoder(String mimeType) {
        if (mimeType == null) return false;
        mimeType = mimeType.toLowerCase();
        return mimeType.startsWith("image/") &&
                (!mimeType.equals("image/gif"));
    }

    public static Bitmap resizeBitmapByScale(
            Bitmap bitmap, float scale, boolean recycle) {
        int width = Math.round(bitmap.getWidth() * scale);
        int height = Math.round(bitmap.getHeight() * scale);
        /// M: fix certain wbmp no thumbnail issue.@{
        if (width < 1 || height < 1) {
            MtkLog.i(TAG, "<resizeBitmapByScale>scaled width or height < 1, no need to resize");
            return bitmap;
        }
        /// @}
        if (width == bitmap.getWidth()
                && height == bitmap.getHeight()) return bitmap;
        Bitmap target = Bitmap.createBitmap(width, height, PQUtils.getConfig(bitmap));
        Canvas canvas = new Canvas(target);
        canvas.scale(scale, scale);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        if (recycle) bitmap.recycle();
        return target;
    }


    private static Bitmap.Config getConfig(Bitmap bitmap) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == null) {
            config = Bitmap.Config.ARGB_8888;
        }
        return config;
    }

    public static int calculateCurrentLevel(float currentScale, int mLevelCount) {
        return clamp(floorLog2(1f / currentScale), 0, mLevelCount);
    }

    public static int floorLog2(float value) {
        int i;
        for (i = 0; i < 31; i++) {
            if ((1 << i) > value) break;
        }
        return i - 1;
    }

    public static int clamp(int x, int min, int max) {
        if (x > max) return max;
        if (x < min) return min;
        return x;
    }

    public static int computeSampleSizeLarger(float scale) {
        int initialSize = (int) FloatMath.floor(1f / scale);
        if (initialSize <= 1) return 1;

        return initialSize <= 8
                ? prevPowerOf2(initialSize)
                : initialSize / 8 * 8;
    }

    public static int prevPowerOf2(int n) {
        if (n <= 0) throw new IllegalArgumentException();
        return Integer.highestOneBit(n);
    }
    public static int calculateLevelCount(int mImageWidth, int mScreenNailWidth) {
        return Math.max(0, ceilLog2(
                (float) mImageWidth / mScreenNailWidth));
    }

    public static int ceilLog2(float value) {
        int i;
        for (i = 0; i < 31; i++) {
            if ((1 << i) >= value) break;
        }
        return i;
    }

    public static Bitmap rotateBitmap(Bitmap source, int rotation, boolean recycle) {
        if (rotation == 0) return source;
        int w = source.getWidth();
        int h = source.getHeight();
        Matrix m = new Matrix();
        m.postRotate(rotation);
        Bitmap bitmap = Bitmap.createBitmap(source, 0, 0, w, h, m, true);
        if (recycle) source.recycle();
        return bitmap;
    }

    public static boolean isHighResolution(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels > 2048 ||  metrics.widthPixels > 2048;
    }
}
