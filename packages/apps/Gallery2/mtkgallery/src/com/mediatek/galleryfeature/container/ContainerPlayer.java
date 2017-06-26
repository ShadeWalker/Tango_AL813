package com.mediatek.galleryfeature.container;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;

import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.gl.MBitmapTexture;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MTexture;
import com.mediatek.galleryframework.util.DecodeUtils;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.util.SimpleThreadPool;
import com.mediatek.galleryframework.util.SimpleThreadPool.Job;

public class ContainerPlayer extends Player {
    private static final String TAG = "MtkGallery2/ContainerPlayer";
    private static final int FRAME_GAP = 500;
    private static final int DECODE_THREAD_NUM = 2;

    private ArrayList<MediaData> mDataArray;
    private int mCurrentIndex;
    private ThumbType mThumbType;
    private MBitmapTexture mTexture;
    private MBitmapTexture mNextTexture;
    private Bitmap mNextBitmap;
    private Bitmap mTempBitmap;
    private DecodeJob mCurrentJob;

    public ContainerPlayer(Context context, MediaData data, ThumbType thumbType) {
        super(context, data, Player.OutputType.TEXTURE);
        mThumbType = thumbType;
    }

    @Override
    public boolean onPrepare() {
        MtkLog.i(TAG, "<onPrepare> caption = " + mMediaData.caption
                + ", this = " + this);
        mDataArray = ContainerHelper.getPlayData(mContext, mMediaData);
        return true;
    }

    @Override
    public boolean onStart() {
        MtkLog.i(TAG, "<onStart> caption = " + mMediaData.caption + ", this = "
                + this);
        mCurrentIndex = 0;
        sendPlayFrameDelayed(FRAME_GAP);
        return true;
    }

    @Override
    public synchronized boolean onPause() {
        MtkLog.i(TAG, "<onPause> caption = " + mMediaData.caption + ", this = "
                + this);
        removeAllMessages();
        if (mCurrentJob != null) {
            mCurrentJob.cancel();
            mCurrentJob = null;
        }
        return true;
    }

    @Override
    public boolean onStop() {
        MtkLog.i(TAG, "<onStop> caption = " + mMediaData.caption + ", this = "
                + this);
        removeAllMessages();
        mCurrentIndex = 0;
        return true;
    }

    @Override
    public synchronized void onRelease() {
        MtkLog.i(TAG, "<onRelease> caption = " + mMediaData.caption
                + ", this = " + this);
        removeAllMessages();
        if (mTexture != null) {
            mTexture.recycle();
            mTexture = null;
        }
        if (mNextTexture != null) {
            mNextTexture.recycle();
            mNextTexture = null;
        }
        if (mNextBitmap != null) {
            mNextBitmap.recycle();
            mNextBitmap = null;
        }
        if (mTempBitmap != null) {
            mTempBitmap.recycle();
            mTempBitmap = null;
        }
    }

    @Override
    public synchronized void onPlayFrame() {
        mNextBitmap = mTempBitmap;
        mTempBitmap = null;
        sendFrameAvailable();
        sendPlayFrameDelayed(FRAME_GAP);
        if (mCurrentJob != null)
            return;
        if (mDataArray != null) {
            mCurrentJob = new DecodeJob(mCurrentIndex, mDataArray.get(mCurrentIndex));
            SimpleThreadPool.getInstance().submitAsyncJob(mCurrentJob);
            mCurrentIndex++;
            if (mCurrentIndex >= mDataArray.size()) {
                mCurrentIndex = 0;
            }
        }
    }

    @Override
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

    @Override
    public Bitmap getBitmap() {
        return null;
    }

    class DecodeJob implements Job {
        private int mIndex;
        private boolean mCanceled = false;
        private MediaData mData;

        public DecodeJob(int index, MediaData data) {
            mIndex = index;
            mData = data;
        }

        @Override
        public void onDo() {
            Bitmap bitmap = null;
            if (mThumbType == ThumbType.MICRO) {
                bitmap = DecodeUtils.decodeSquareThumbnail(mData, mThumbType
                        .getTargetSize());
            } else {
                bitmap = DecodeUtils.decodeOriginRatioThumbnail(mData,
                        mThumbType.getTargetSize());
            }
            onDoFinished(bitmap);
        }

        private void onDoFinished(Bitmap bitmap) {
            synchronized (ContainerPlayer.this) {
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
