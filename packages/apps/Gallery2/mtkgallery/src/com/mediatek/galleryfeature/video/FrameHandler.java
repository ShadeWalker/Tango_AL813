package com.mediatek.galleryfeature.video;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.mediatek.galleryframework.util.MtkLog;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.Image;
import android.media.Image.Plane;
import android.os.Environment;
import android.os.StatFs;

/*
 * handle frame saving and loading(partial work) strategy (storing path etc.)
 */
public class FrameHandler {
    private static final String TAG = "MtkGallery2/FrameHandler";
    private static final String GALLERY_ISSUE = "/.SlideVideo/";
    private static final String BITMAP_DUMP_PATH = Environment.getExternalStorageDirectory().toString() + GALLERY_ISSUE;
    private static final int MIN_STORAGE_SPACE = 5 * 1024 * 1024;

    public static String getFrameSavingPath(String filePath) {
        String path = BITMAP_DUMP_PATH + filePath.hashCode() + ".png";
        return path;
    }

    public static boolean saveFrameImage(Image image, int frameW, int frameH,
            String filePath) {
        ByteBuffer imageBuffer;
        MtkLog.d(TAG, "<saveFrameImage> image : width = " + image.getWidth() + " height = "
                + image.getHeight() + " format = " + image.getFormat());
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth(),
                image.getHeight(), Bitmap.Config.ARGB_8888);
        Plane plane = image.getPlanes()[0];
        imageBuffer = plane.getBuffer();
        bitmap.copyPixelsFromBuffer(imageBuffer);
        MtkLog.d(TAG, "<saveFrameImage> video : width = " + frameW + " height = " + frameH);
        Bitmap bmp = Bitmap.createBitmap(bitmap, 0, image.getHeight() - frameH, frameW,
                frameH, null, false);
        MtkLog.d(TAG, "<saveFrameImage> bmp : width = " + bmp.getWidth() + " height = "
                + bmp.getHeight());
        bitmap.recycle();
        bitmap = bmp;
        boolean result = saveBitmap(bitmap, String.valueOf(filePath.hashCode()));
        bitmap.recycle();
        bitmap = null;
        imageBuffer = null;
        image.close();
        return result;
    }

    private static boolean saveBitmap(Bitmap bitmap, String string) {
        if (!isStorageEnoughForSaving(Environment.getExternalStorageDirectory().toString())) {
            return false;
        }
        boolean isSuccess = false;
        String fileName = string + ".png";
        File videoSnapshotSavePath = new File(BITMAP_DUMP_PATH);
        if (!videoSnapshotSavePath.exists()) {
            MtkLog.d(TAG, "<saveBitmap> create  videoSnapshotSavePath");
            isSuccess = videoSnapshotSavePath.mkdir();
            if (!isSuccess) {
                MtkLog.d(TAG, "<saveBitmap> create videoSnapshotSavePath fail");
                return false;
            }
        }
        File file = new File(BITMAP_DUMP_PATH, fileName);
        OutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
        } catch (IOException e) {
            e.printStackTrace();
            MtkLog.d(TAG, "<saveBitmap> cannot create fos with file name " + fileName);
            return false;
        }
        isSuccess = bitmap.compress(CompressFormat.PNG, 100, fos);
        MtkLog.d(TAG, "<saveBitmap> bitmap compress result " + isSuccess);
        return isSuccess;
    }

    private static boolean isStorageEnoughForSaving(String dirPath) {
        StatFs stat  = new StatFs(dirPath);
        long spaceLeft = (long) (stat.getAvailableBlocks()) * stat.getBlockSize();
        MtkLog.d(TAG, "<isStorageEnoughForSaving> storage available in this volume is: " + spaceLeft);
        if (spaceLeft < MIN_STORAGE_SPACE) {
            return false;
        }
        return true;
    }
}
