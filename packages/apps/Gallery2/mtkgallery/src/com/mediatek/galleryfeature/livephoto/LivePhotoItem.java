package com.mediatek.galleryfeature.livephoto;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.ExtItem.SupportOperation;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.util.MtkLog;

public class LivePhotoItem extends ExtItem {
    private static final String TAG = "MtkGallery2/LivePhotoItem";
    public LivePhotoItem(MediaData data) {
        super(data);
    }

    @Override
    public Thumbnail getThumbnail(ThumbType thumbType) {
        if (mMediaData == null || mMediaData.filePath == null) {
            return new Thumbnail(null, false);
        }
        Bitmap bitmap = createLivePhotoThumbnail(mMediaData.filePath);
        return new Thumbnail(bitmap, false);
    }

    @Override
    public ArrayList<SupportOperation> getNotSupportedOperations() {
        ArrayList<SupportOperation> nsp = new ArrayList<SupportOperation>();
        nsp.add(SupportOperation.MUTE);
        nsp.add(SupportOperation.TRIM);
        return nsp;
    }

    private Bitmap createLivePhotoThumbnail(String filePath) {
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
                MtkLog.i(TAG, "<createLivePhotoThumbnail>");
                return (Bitmap) clazz.getMethod("getFrameAtTime", long.class).invoke(instance, 0);
            }
        } catch (IllegalArgumentException e) {
            // Assume this is a corrupt video file
            MtkLog.e(TAG, "<createLivePhotoThumbnail>", e);
        } catch (RuntimeException e) {
            // Assume this is a corrupt video file.
            MtkLog.e(TAG, "<createLivePhotoThumbnail>", e);
        } catch (InstantiationException e) {
            MtkLog.e(TAG, "<createLivePhotoThumbnail>", e);
        } catch (InvocationTargetException e) {
            MtkLog.e(TAG, "<createLivePhotoThumbnail>", e);
        } catch (ClassNotFoundException e) {
            MtkLog.e(TAG, "<createLivePhotoThumbnail>", e);
        } catch (NoSuchMethodException e) {
            MtkLog.e(TAG, "<createLivePhotoThumbnail>", e);
        } catch (IllegalAccessException e) {
            MtkLog.e(TAG, "<createLivePhotoThumbnail>", e);
        } finally {
            try {
                if (instance != null) {
                    clazz.getMethod("release").invoke(instance);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
