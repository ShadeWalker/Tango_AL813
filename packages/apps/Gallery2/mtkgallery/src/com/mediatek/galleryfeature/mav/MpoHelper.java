package com.mediatek.galleryfeature.mav;


import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Files.FileColumns;
import android.util.FloatMath;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory.Options;
import android.graphics.drawable.Drawable;

import com.mediatek.mpodecoder.MpoDecoder;

import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.base.Player.TaskCanceller;
import com.mediatek.galleryfeature.mav.MavPlayer.MavListener;
import com.mediatek.galleryframework.util.BitmapUtils;
import com.mediatek.galleryframework.util.MtkLog;

public class MpoHelper {

    private static final String TAG = "MtkGallery2/MpoHelper";

    public static final String MPO_MIME_TYPE = "image/mpo";
    public static final String MPO_VIEW_ACTION = "com.mediatek.action.VIEW_MPO";

    private static final int MAX_PIXEL_COUNT = 640000; // 400x1600
    private static Drawable sMavOverlay = null;
    public static final int TARGET_DISPLAY_WIDTH[] = {/* 1920, */1280, 1280,
            960, 800, 640, 480 };
    public static final int TARGET_DISPLAY_HEIGHT[] = {/* 1080, */800, 720,
            540, 480, 480, 320 };
    private static final int MAX_BITMAP_DIM = 8000;

    public static MpoDecoder createMpoDecoder(String filePath) {
        MtkLog.i(TAG, "<createMpoDecoder>:filepath:" + filePath);
        if (null == filePath)
            return null;
        MpoDecoder mpoDecoder = MpoDecoder.decodeFile(filePath);
        if (mpoDecoder != null) {
            return mpoDecoder;
        }
        return null;
    }

    public static MpoDecoder createMpoDecoder(ContentResolver cr, Uri uri) {
        MtkLog.i(TAG, "<createMpoDecoder>:uri:" + uri);
        if (null == cr || null == uri)
            return null;
        MpoDecoder mpoDecoder = MpoDecoder.decodeUri(cr, uri);
        if (mpoDecoder != null) {
            return mpoDecoder;
        }
        return null;
    }

    public static MpoDecoder createMpoDecoder(byte[] buffer) {
        MtkLog.i(TAG, "<createMpoDecoder> create from buffer");
        if (null == buffer)
            return null;
        MpoDecoder mpoDecoder = MpoDecoder.decodeByteArray(buffer, 0,
                buffer.length);
        if (mpoDecoder != null) {
            return mpoDecoder;
        }
        return null;
    }

    public static void playMpo(Activity activity, Uri uri) {
        try {
            Intent i = new Intent(MPO_VIEW_ACTION);
            i.setDataAndType(uri, MPO_MIME_TYPE);
            activity.startActivity(i);
        } catch (ActivityNotFoundException e) {
            MtkLog.e(TAG, "<playMpo> Unable to open mpo file: ", e);
        }
    }

    public static int getInclusionFromData(Bundle data) {
        return MavItem.EXCLUDE_MPO_MAV;
    }

    public static String getMavWhereClause(int mavInclusion) {
        String whereClause = null;
        if ((mavInclusion & MavItem.EXCLUDE_MPO_MAV) != 0) {
            whereClause = FileColumns.MIME_TYPE + "!='" + MPO_MIME_TYPE + "'";
        }
        return whereClause;
    }

    public static int calculateSampleSizeByType(int width, int height,
            ThumbType type, int targetSize) {
        int sampleSize = 1;
        if (type == ThumbType.MICRO) {
            // We center-crop the original image as it's micro thumbnail.
            // In this case, we want to make sure the shorter side >=
            // "targetSize".
            float scale = (float) targetSize / Math.min(width, height);
            sampleSize = BitmapUtils.computeSampleSizeLarger(scale);

            // For an extremely wide image, e.g. 300x30000, we may got OOM
            // when decoding it for TYPE_MICROTHUMBNAIL. So we add a max
            // number of pixels limit here.
            if ((width / sampleSize) * (height / sampleSize) > MAX_PIXEL_COUNT) {
                sampleSize = BitmapUtils.computeSampleSize(FloatMath
                        .sqrt((float) MAX_PIXEL_COUNT / (width * height)));
            }
        } else {
            // For screen nail, we only want to keep the longer side >=
            // targetSize.
            float scale = (float) targetSize / Math.max(width, height);
            sampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        }
        return sampleSize;
    }

    private static Bitmap[] retrieveMicroMpoFrames(TaskCanceller taskCanceller,
            MavPlayer.Params params, MpoDecoderWrapper mpoDecoderWrapper) {
        if (taskCanceller != null && taskCanceller.isCancelled()) {
            MtkLog.v(TAG, "<retrieveMicroMpoFrames> job cancelled");
            return null;
        }
        int frameCount = mpoDecoderWrapper.frameCount();
        Bitmap[] mpoFrames = new Bitmap[mpoDecoderWrapper.frameCount()];

        Options options = new Options();
        initOption(options, mpoDecoderWrapper, params);
        for (int i = 0; i < frameCount; i++) {
            Bitmap mBitmap = decodeFrameSafe(false, mpoDecoderWrapper, i, options);
            if (mBitmap == null) {
                return null;
            }
            mpoFrames[i] = postScaleDown(mBitmap, params.inType,
                    params.inThumbnailTargetSize);
            if(taskCanceller != null && taskCanceller.isCancelled()) {
                for (int k = i; k >= 0; k--) {
                    mpoFrames[k].recycle();
                }
                return null;
            }
        }
        return mpoFrames;
    }

    
    
    public static Bitmap[] decodeMpoFrames(TaskCanceller taskCanceller,
            MavPlayer.Params params, MpoDecoderWrapper mpoDecoderWrapper,
            MavListener listener) {
        if (null == mpoDecoderWrapper || (taskCanceller != null && taskCanceller.isCancelled())) {
            MtkLog.e(TAG, "<decodeMpoFrames> null decoder or params!");
            return null;
        }
        Bitmap[] mpoFrames = null;
        int frameCount = mpoDecoderWrapper.frameCount();
        int frameWidth = mpoDecoderWrapper.width();
        int frameHeight = mpoDecoderWrapper.height();
        if (params.inType == ThumbType.MICRO) {
            mpoFrames = retrieveMicroMpoFrames(taskCanceller, params,
                    mpoDecoderWrapper);
            int middleFrame = (int) (frameCount / 2);
            return mpoFrames;
        }
        if (listener != null && !taskCanceller.isCancelled()) {
            listener.setSeekBar(frameCount - 1, MavSeekBar.INVALID_PROCESS);
        }
        MtkLog.d(TAG, "<decodeMpoFrames> mpo frame width: " + frameWidth
                + ", frame height: " + frameHeight);
        // now as paramters are all valid, we start to decode mpo frames
        try {
            mpoFrames = tryDecodeMpoFrames(taskCanceller, mpoDecoderWrapper,
                    params, params.inTargetDisplayWidth, params.inTargetDisplayHeight, listener);
            if (listener != null) {
                int middleFrame = (int) (frameCount / 2);
                listener.setSeekBar(frameCount - 1, middleFrame);

            }
        } catch (OutOfMemoryError e) {
            MtkLog.w(TAG, "<decodeMpoFrames> out of memory");
            e.printStackTrace();
            // when out of memory happend, we decode smaller mpo frames
            // we try smaller display size
            int targetDisplayPixelCount = params.inTargetDisplayWidth
                    * params.inTargetDisplayHeight;
            for (int i = 0; i < TARGET_DISPLAY_WIDTH.length; i++) {
                int pixelCount = TARGET_DISPLAY_WIDTH[i]
                        * TARGET_DISPLAY_HEIGHT[i];
                if (pixelCount >= targetDisplayPixelCount) {
                    continue;
                } else {
                    if (taskCanceller != null && taskCanceller.isCancelled()) {
                        MtkLog.v(TAG, "<decodeMpoFrames> job cancelled");
                        break;
                    }
                    MtkLog.i(TAG, "<decodeMpoFrames> try display ("
                            + TARGET_DISPLAY_WIDTH[i] + " x "
                            + TARGET_DISPLAY_HEIGHT[i] + ")");
                    try {
                        mpoFrames = tryDecodeMpoFrames(taskCanceller,
                                mpoDecoderWrapper, params,
                                TARGET_DISPLAY_WIDTH[i],
                                TARGET_DISPLAY_HEIGHT[i], listener);
                    } catch (OutOfMemoryError oom) {
                        MtkLog.w(TAG, "<decodeMpoFrames> out of memory again:"
                                + oom);
                        continue;
                    }
                    MtkLog.d(TAG, "<decodeMpoFrame> finished decoding process");
                    break;
                }
            }
        }
        if (taskCanceller != null && taskCanceller.isCancelled()) {
            MtkLog.d(TAG, "<decodeMpoFrame> job cancelled, recycle decoded");
            recycleBitmapArray(mpoFrames);
            return null;
        }
        return mpoFrames;
    }
    
    public static void initOption(Options options, MpoDecoderWrapper mpoDecoderWrapper, MavPlayer.Params params) {
        if (params.inType == ThumbType.MIDDLE) {
            int initTargetSize = params.inTargetDisplayWidth > params.inTargetDisplayHeight ? params.inTargetDisplayWidth
                    : params.inTargetDisplayHeight;
            float scale = (float) initTargetSize
                    / Math.max(mpoDecoderWrapper.width(), mpoDecoderWrapper.height());
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        } else {
            options.inSampleSize = calculateSampleSizeByType(mpoDecoderWrapper
                    .width(), mpoDecoderWrapper.height(), params.inType,
                    params.inThumbnailTargetSize);
        }
        options.inPostProc = params.inPQEnhance;
    }
    
    public static Bitmap decodeBitmap(boolean isCancelled,
            MpoDecoderWrapper mpoDecoderWrapper, int frameIndex, Options options, int targetDisplayWidth, int targetDisplayHeight) {
        Bitmap bitmap = decodeFrame(isCancelled, mpoDecoderWrapper, frameIndex,
                options);
        if (null == bitmap) {
            MtkLog.e(TAG, "<tryDecodeMpoFrames> got null frame");
            return null;
        }
        float scaleDown = largerDisplayScale(bitmap.getWidth(), bitmap
                .getHeight(), targetDisplayWidth, targetDisplayHeight);
        if (scaleDown < 1.0f) {
           return resizeBitmap(bitmap, scaleDown, true);
        } else {
            return bitmap;
        }
    }
    
    public static Bitmap[] tryDecodeMpoFrames(TaskCanceller taskCanceller,
            MpoDecoderWrapper mpoDecoderWrapper, MavPlayer.Params params,int targetDisplayWidth, int targetDisplayHeight, MavListener listener) {
        // we believe all the parameters are valid
        int frameCount = mpoDecoderWrapper.frameCount();
        int frameWidth = mpoDecoderWrapper.width();
        int frameHeight = mpoDecoderWrapper.height();
        Options options = new Options();
        initOption(options, mpoDecoderWrapper, params);
        Bitmap[] mpoFrames = new Bitmap[frameCount];
        boolean decodeFailed = false;
        try {
            for (int i = 0; i < frameCount; i++) {
                mpoFrames[i] = decodeBitmap(false, mpoDecoderWrapper, i, options, targetDisplayWidth, targetDisplayHeight);
                if (null != mpoFrames[i]) {
                    MtkLog.v(TAG, "<tryDecodeMpoFrames> got mpoFrames[" + i
                            + "]:[" + mpoFrames[i].getWidth() + "x"
                            + mpoFrames[i].getHeight() + "]");
                }
                if(taskCanceller != null && taskCanceller.isCancelled()) {
                    for (int k = i; k >= 0; k--) {
                        mpoFrames[k].recycle();
                    }
                    return null;
                }
                // update progress
                if (listener != null && !taskCanceller.isCancelled()) {
                    MtkLog.d(TAG, "update mav progress: " + i);
                    listener.setProgress(i);
                }
            }
        } catch (OutOfMemoryError e) {
            MtkLog.w(TAG, "<tryDecodeMpoFrames> out of memory, recycle decoded");
            recycleBitmapArray(mpoFrames);
            throw e;
        }
        if ((taskCanceller != null && taskCanceller.isCancelled()) || decodeFailed) {
            MtkLog.d(TAG, "<tryDecodeMpoFrames> job cancelled or decode failed, recycle decoded");
            recycleBitmapArray(mpoFrames);
            return null;
        }
        return mpoFrames;
    }

    public static void recycleBitmapArray(Bitmap[] bitmapArray) {
        if (null == bitmapArray) {
            return;
        }
        for (int i = 0; i < bitmapArray.length; i++) {
            if (null == bitmapArray[i]) {
                continue;
            }
            bitmapArray[i].recycle();
        }
    }

    public static Bitmap decodeFrame(boolean iscancelled,
            MpoDecoderWrapper mpoDecoderWrapper, int frameIndex, Options options) {
        if (null == mpoDecoderWrapper || frameIndex < 0 || null == options) {
            MtkLog.w(TAG, "<decodeFrame> invalid paramters");
            return null;
        }
        Bitmap bitmap = mpoDecoderWrapper.frameBitmap(frameIndex, options);
        if (iscancelled && null != bitmap) {
            bitmap.recycle();
            bitmap = null;
        }
        return bitmap;
    }

    public static Bitmap decodeFrameSafe(boolean iscancelled,
            MpoDecoderWrapper mpoDecoderWrapper, int frameIndex, Options options) {
        if (null == mpoDecoderWrapper || frameIndex < 0 || null == options) {
            MtkLog.w(TAG, "<decodeFrameSafe> invalid paramters");
            return null;
        }
        // As there is a chance no enough dvm memory for decoded Bitmap,
        // Skia will return a null Bitmap. In this case, we have to
        // downscale the decoded Bitmap by increase the options.inSampleSize
        Bitmap bitmap = null;
        final int maxTryNum = 8;
        for (int i = 0; i < maxTryNum && (!iscancelled); i++) {
            // we increase inSampleSize to expect a smaller Bitamp
            MtkLog.v(TAG, "<decodeFrameSafe> try for sample size "
                    + options.inSampleSize + " frameIndex=" + frameIndex);
            try {
                bitmap = MpoHelper.decodeFrame(iscancelled, mpoDecoderWrapper,
                        frameIndex, options);
            } catch (OutOfMemoryError e) {
                MtkLog.w(TAG, "<decodeFrameSafe> out of memory when decoding:"
                        + e);
            }
            if (null != bitmap)
                break;
            options.inSampleSize *= 2;
        }
        return bitmap;
    }

    public static Bitmap retrieveThumbData(boolean isCancelled,
            MavPlayer.Params params, MpoDecoderWrapper mpoDecoderWrapper) {
        if (isCancelled) {
            MtkLog.v(TAG, "<retrieveThumbData> job cancelled");
            return null;
        }
        boolean isMav = false;
        if (mpoDecoderWrapper.getMtkMpoType() == MpoDecoder.TYPE_MAV) {
            isMav = true;
            MtkLog.d(TAG, "<retrieveThumbData> isMav: " + isMav);
        }

        int frameCount = mpoDecoderWrapper.frameCount();
        Options options = new Options();
        options.inSampleSize = calculateSampleSizeByType(mpoDecoderWrapper
                .width(), mpoDecoderWrapper.height(), params.inType,
                params.inThumbnailTargetSize);
        options.inPostProc = params.inPQEnhance;
        Bitmap bitmap = null;
        int frameIndex = getMpoFrameIndex(frameCount);
        bitmap = MpoHelper.decodeFrameSafe(isCancelled, mpoDecoderWrapper,
                frameIndex, options);
        bitmap = postScaleDown(bitmap, params.inType, params.inThumbnailTargetSize);
        return bitmap;
    }

    private static int getMpoFrameIndex(int frameCount) {
        int frameIndex = frameCount / 2;
        return frameIndex;
    }

    public static Bitmap postScaleDown(Bitmap bitmap, ThumbType type,
            int targetSize) {
        if (null == bitmap)
            return null;
        // scale down according to type
        if (type == ThumbType.MICRO) {
            bitmap = BitmapUtils.resizeAndCropCenter(bitmap, targetSize, true);
        } else {
            bitmap = BitmapUtils.resizeDownBySideLength(bitmap, targetSize,
                    true);
        }
        bitmap = ensureGLCompatibleBitmap(bitmap);
        return bitmap;
    }

    public static Bitmap ensureGLCompatibleBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.getConfig() != null)
            return bitmap;
        Bitmap newBitmap = bitmap.copy(Config.ARGB_8888, false);
        bitmap.recycle();
        return newBitmap;
    }

    public static float largerDisplayScale(int frameWidth, int frameHeight,
            int targetDisplayWidth, int targetDisplayHeight) {
        if (targetDisplayWidth <= 0 || targetDisplayHeight <= 0
                || frameWidth <= 0 || frameHeight <= 0) {
            MtkLog.w(TAG, "<largerDisplayScale> invalid parameters");
            return 1.0f;
        }
        float initRate = 1.0f;
        initRate = Math.min((float) targetDisplayWidth / frameWidth,
                (float) targetDisplayHeight / frameHeight);
        initRate = Math.max(initRate, Math.min((float) targetDisplayWidth
                / frameHeight, (float) targetDisplayHeight / frameWidth));
        initRate = Math.min(initRate, 1.0f);
        return initRate;
    }

    public static Bitmap resizeBitmap(Bitmap source, float scale,
            boolean recycleInput) {
        if (null == source || scale <= 0.0f) {
            MtkLog.e(TAG, "<resizeBitmap> invalid parameters");
            return source;
        }
        if (scale == 1.0f) {
            // no bother to scale down
            return source;
        }

        int newWidth = Math.round((float) source.getWidth() * scale);
        int newHeight = Math.round((float) source.getHeight() * scale);
        if (newWidth > MAX_BITMAP_DIM || newHeight > MAX_BITMAP_DIM) {
            MtkLog.w(TAG, "<resizeBitmap> too large new Bitmap for scale:"
                    + scale);
            return source;
        }

        Bitmap target = Bitmap.createBitmap(newWidth, newHeight,
                Bitmap.Config.ARGB_8888);
        // draw source bitmap onto it
        Canvas canvas = new Canvas(target);
        Rect src = new Rect(0, 0, source.getWidth(), source.getHeight());
        RectF dst = new RectF(0, 0, (float) newWidth, (float) newHeight);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(source, src, dst, paint);
        if (recycleInput) {
            source.recycle();
        }
        return target;
    }
}
