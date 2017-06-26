package com.mediatek.galleryfeature.gif;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.util.LinkedHashMap;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import com.mediatek.galleryfeature.drm.DrmHelper;
import com.mediatek.galleryfeature.platform.PlatformHelper;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.gl.MBitmapTexture;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MTexture;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.util.BitmapUtils;
import com.mediatek.galleryframework.util.SimpleThreadPool;
import com.mediatek.galleryframework.util.Utils;
import com.mediatek.galleryframework.base.Work;

public class GifPlayer extends Player {
    private static final String TAG = "MtkGallery2/GifPlayer";
    // same as setting in FancyHelper
    public static final float FANCY_CROP_RATIO = (5.0f / 2.0f);
    public static final float FANCY_CROP_RATIO_LAND = (2.0f / 5.0f);
    private static final int MAX_CACHE_NUM = 5;
    private ThumbType mThumbType;
    private GifDecoderWrapper mGifDecoderWrapper;
    private ParcelFileDescriptor mFD;
    private int mFrameCount;
    private int mCurrentFrameDuration;
    private int mCurrentFrameIndex;
    private int mWidth;
    private int mHeight;
    private boolean mIsPlaying = false;

    private Bitmap mTempBitmap;
    private Bitmap mNextBitmap;
    private MBitmapTexture mTexture;
    private DecodeJob mCurrentJob;

    public GifPlayer(Context context, MediaData data, OutputType outputType,
            ThumbType type) {
        super(context, data, outputType);
        mWidth = mMediaData.width;
        mHeight = mMediaData.height;
        mThumbType = type;
    }

    @Override
    protected boolean onPrepare() {
        MtkLog.i(TAG, "<onPrepare> caption = " + mMediaData.caption);
        if (mMediaData.isDRM != 0) {
            byte[] buffer = DrmHelper.forceDecryptFile(mMediaData.filePath,
                    false);
            if (buffer == null) {
                MtkLog.i(TAG, "<onPrepare> buffer == null, return false");
                return false;
            }
            mGifDecoderWrapper = GifDecoderWrapper.createGifDecoderWrapper(
                    buffer, 0, buffer.length);
        } else if (mMediaData.filePath != null
                && !mMediaData.filePath.equals("")) {
            mGifDecoderWrapper = GifDecoderWrapper
                    .createGifDecoderWrapper(mMediaData.filePath);
        } else if (mMediaData.uri != null) {
            try {
                mFD = mContext.getContentResolver().openFileDescriptor(
                        mMediaData.uri, "r");
                FileDescriptor fd = mFD.getFileDescriptor();
                mGifDecoderWrapper = GifDecoderWrapper
                        .createGifDecoderWrapper(fd);
            } catch (FileNotFoundException e) {
                MtkLog.w(TAG, "<onPrepare> FileNotFoundException", e);
                Utils.closeSilently(mFD);
                mFD = null;
                return false;
            }
        }
        if (mGifDecoderWrapper == null) {
            MtkLog.i(TAG,
                    "<onPrepare> mGifDecoderWrapper == null, return false");
            return false;
        }
        mWidth = mGifDecoderWrapper.getWidth();
        mHeight = mGifDecoderWrapper.getHeight();
        mFrameCount = getGifTotalFrameCount();
        PlatformHelper.submitJob(new DecodeJob(0));
        return true;
    }

    @Override
    protected synchronized void onRelease() {
        MtkLog.i(TAG, "<onRelease> caption = " + mMediaData.caption);
        removeAllMessages();
        if (mGifDecoderWrapper != null)
            mGifDecoderWrapper.close();
        if (mNextBitmap != null)
            mNextBitmap.recycle();
        if (mTempBitmap != null)
            mTempBitmap.recycle();
        if (mTexture != null)
            mTexture.recycle();
        if (mFD != null)
            Utils.closeSilently(mFD);
        mGifDecoderWrapper = null;
        mNextBitmap = null;
        mTempBitmap = null;
        mTexture = null;
        mFD = null;
    }

    @Override
    protected boolean onStart() {
        MtkLog.i(TAG, "<onStart> caption = " + mMediaData.caption);
        mIsPlaying = true;
        mCurrentFrameIndex = 0;
        mCurrentFrameDuration = getGifFrameDuration(mCurrentFrameIndex);
        sendFrameAvailable();
        sendPlayFrameDelayed(0);
        return true;
    }

    @Override
    protected synchronized boolean onPause() {
        MtkLog.i(TAG, "<onPause> caption = " + mMediaData.caption);
        mIsPlaying = false;
        removeAllMessages();
        if (mCurrentJob != null) {
            mCurrentJob.cancel();
            mCurrentJob = null;
        }
        return true;
    }

    @Override
    protected boolean onStop() {
        MtkLog.i(TAG, "<onStop> caption = " + mMediaData.caption);
        removeAllMessages();
        mCurrentFrameIndex = 0;
        mCurrentFrameDuration = getGifFrameDuration(mCurrentFrameIndex);
        return true;
    }

    public int getOutputWidth() {
        if (mTexture != null)
            return mWidth;
        return 0;
    }

    public int getOutputHeight() {
        if (mTexture != null)
            return mHeight;
        return 0;
    }

    protected synchronized void onPlayFrame() {
        if (!mIsPlaying)
            return;
        mNextBitmap = mTempBitmap;
        mTempBitmap = null;
        sendFrameAvailable();
        sendPlayFrameDelayed(mCurrentFrameDuration);

        if (mCurrentJob != null)
            return;

        mCurrentFrameIndex++;
        if (mCurrentFrameIndex >= mFrameCount) {
            mCurrentFrameIndex = 0;
        }
        mCurrentFrameDuration = getGifFrameDuration(mCurrentFrameIndex);
        mCurrentJob = new DecodeJob(mCurrentFrameIndex);
        PlatformHelper.submitJob(mCurrentJob);
    }

    public synchronized MTexture getTexture(MGLCanvas canvas) {
        if (mNextBitmap != null) {
            if (mTexture != null) {
                mTexture.recycle();
            }
            mTexture = new MBitmapTexture(mNextBitmap);
            mNextBitmap = null;
        }
        return mTexture;
    }

    private int getGifTotalFrameCount() {
        if (mGifDecoderWrapper == null)
            return 0;
        return mGifDecoderWrapper.getTotalFrameCount();
    }

    private int getGifFrameDuration(int frameIndex) {
        if (mGifDecoderWrapper == null)
            return 0;
        return mGifDecoderWrapper.getFrameDuration(frameIndex);
    }

    class DecodeJob implements Work<Bitmap> {
        private int mIndex;
        private boolean mCanceled = false;

        public DecodeJob(int index) {
            mIndex = index;
        }

        @Override
        public Bitmap run() {
            Bitmap bitmap = null;
            synchronized(GifPlayer.this) {
                if (isCanceled() || mGifDecoderWrapper == null) {
                    MtkLog.i(TAG, "<DecodeJob.onDo> isCanceled() = "
                            + isCanceled() + ",mGifDecoderWrapper = "
                            + mGifDecoderWrapper + ", return");
                    onDoFinished(bitmap);
                    return null;
                }
                bitmap = mGifDecoderWrapper.getFrameBitmap(mIndex);
            }
            float ratio = (float) mHeight / (float) mWidth;
            int targetSize = mThumbType.getTargetSize();
            if (mThumbType == ThumbType.MICRO) {
                bitmap = BitmapUtils.resizeAndCropCenter(bitmap, targetSize, true);
            } else if (mThumbType == ThumbType.FANCY && ratio > FANCY_CROP_RATIO) {
                bitmap = BitmapUtils.resizeAndCropByScale(bitmap, targetSize, Math.round(targetSize
                        * FANCY_CROP_RATIO), true, true);
            } else if (mThumbType == ThumbType.FANCY && ratio < FANCY_CROP_RATIO_LAND) {
                bitmap = BitmapUtils.resizeAndCropByScale(bitmap, targetSize, Math.round(targetSize
                        * FANCY_CROP_RATIO_LAND), false, true);
            } else {
                bitmap = BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
            }
            bitmap = BitmapUtils.replaceBackgroundColor(bitmap,
                    GifItem.GIF_BACKGROUND_COLOR, true);
            onDoFinished(bitmap);
            return bitmap;
        }

        private void onDoFinished(Bitmap bitmap) {
            synchronized (GifPlayer.this) {
                if (mTempBitmap != null) {
                    mTempBitmap.recycle();
                }
                mTempBitmap = bitmap;
                mCurrentJob = null;
            }
        }

        @Override
        public boolean isCanceled() {
            return mCanceled;
        }

        public void cancel() {
            mCanceled = true;
        }
    }
}
