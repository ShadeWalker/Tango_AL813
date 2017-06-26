package com.mediatek.galleryframework.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import com.mediatek.galleryframework.base.MediaData;

public class DecodeUtils {
    private static final String TAG = "MtkGallery2/DecodeUtils";

    public static Bitmap decodeSquareThumbnail(MediaData data, int targetSize) {
        Bitmap bitmap = decode(data, targetSize);
        if (bitmap == null)
            return null;
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        return BitmapUtils.resizeAndCropCenter(bitmap, size, true);
    }

    public static Bitmap decodeOriginRatioThumbnail(MediaData data, int targetSize) {
        Bitmap bitmap = decode(data, targetSize);
        if (bitmap == null)
            return null;
        return BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
    }

    public static Bitmap decodeOriginRatioThumbnail(String filePath, int targetSize) {
        Bitmap bitmap = decode(filePath, targetSize);
        if (bitmap == null)
            return null;
        return BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
    }

    public static Bitmap decodeSquareThumbnail(byte[] buffer, int targetSize) {
        Bitmap bitmap = decode(buffer, targetSize);
        if (bitmap == null)
            return null;
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        return BitmapUtils.resizeAndCropCenter(bitmap, size, true);
    }

    public static Bitmap decodeOriginRatioThumbnail(byte[] buffer, int targetSize) {
        Bitmap bitmap = decode(buffer, targetSize);
        if (bitmap == null)
            return null;
        return BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
    }

    private static Bitmap decode(MediaData data, int targetSize) {
        if (data == null) {
            MtkLog.i(TAG, "<decode> error args, return null");
            return null;
        }
        return decode(data.filePath, targetSize);
    }

    private static Bitmap decode(String filePath, int targetSize) {
        if (filePath == null || filePath.equals("")) {
            MtkLog.i(TAG, "<decode> error args, return null");
            return null;
        }
        Bitmap bitmap;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        options.inSampleSize = BitmapUtils.computeSampleSizeLarger(
                options.outWidth, options.outHeight, targetSize);

        options.inJustDecodeBounds = false;
        BitmapUtils.setOptionsMutable(options);
        bitmap = BitmapFactory.decodeFile(filePath, options);
        return bitmap;
    }

    private static Bitmap decode(byte[] buffer, int targetSize) {
        if (buffer == null) {
            MtkLog.i(TAG, "<decode> buffer == null, error args, return null");
            return null;
        }
        Bitmap bitmap;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(buffer, 0, buffer.length, options);
        options.inSampleSize = BitmapUtils.computeSampleSizeLarger(
                options.outWidth, options.outHeight, targetSize);

        options.inJustDecodeBounds = false;
        BitmapUtils.setOptionsMutable(options);
        bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.length, options);
        return bitmap;
    }


    public static Bitmap decodeVideoThumbnail(String filePath) {
        // MediaMetadataRetriever is available on API Level 8
        // but is hidden until API Level 10
        Class<?> clazz = null;
        Object instance = null;
        try {
            clazz = Class.forName("android.media.MediaMetadataRetriever");
            instance = clazz.newInstance();

            Method method = clazz.getMethod("setDataSource", String.class);
            method.invoke(instance, filePath);

            // The method name changes between API Level 9 and 10.
            if (Build.VERSION.SDK_INT <= 9) {
                return (Bitmap) clazz.getMethod("captureFrame").invoke(instance);
            } else {
                byte[] data = (byte[]) clazz.getMethod("getEmbeddedPicture").invoke(instance);
                if (data != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bitmap != null) return bitmap;
                }
                return (Bitmap) clazz.getMethod("getFrameAtTime").invoke(instance);
            }
        } catch (IllegalArgumentException e) {
            MtkLog.e(TAG, "<createVideoThumbnail>", e);
        } catch (InstantiationException e) {
            MtkLog.e(TAG, "<createVideoThumbnail>", e);
        } catch (InvocationTargetException e) {
            MtkLog.e(TAG, "<createVideoThumbnail>", e);
        } catch (ClassNotFoundException e) {
            MtkLog.e(TAG, "<createVideoThumbnail>", e);
        } catch (NoSuchMethodException e) {
            MtkLog.e(TAG, "<createVideoThumbnail>", e);
        } catch (IllegalAccessException e) {
            MtkLog.e(TAG, "<createVideoThumbnail>", e);
        } finally {
            try {
                if (instance != null) {
                    clazz.getMethod("release").invoke(instance);
                }
            } catch (IllegalAccessException e) {
                MtkLog.e(TAG, "<createVideoThumbnail> release", e);
            } catch (IllegalArgumentException e) {
                MtkLog.e(TAG, "<createVideoThumbnail> release", e);
            } catch (InvocationTargetException e) {
                MtkLog.e(TAG, "<createVideoThumbnail> release", e);
            } catch (NoSuchMethodException e) {
                MtkLog.e(TAG, "<createVideoThumbnail> release", e);
            }
        }
        return null;
    }
}
