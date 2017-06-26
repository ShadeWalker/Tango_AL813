package com.mediatek.galleryfeature.panorama;


import android.content.Context;
import android.graphics.Bitmap;

import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryfeature.panorama.PanoramaHelper.PanoramaEntry;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MTexture;
import com.mediatek.galleryframework.util.MtkLog;

public class PanoramaPlayer extends Player {
    private static final String TAG = "MtkGallery2/PanoramaPlayer";
    public static final int PANORAMA_MODE_NORMAL = 1;
    public static final int PANORAMA_MODE_3D = 2;

    public static final int MSG_MODE_NORMAL = 1;
    public static final int MSG_MODE_3D = 2;
    public static final int MSG_UPDATE_CURRENT_FRAME = 3;
    public static final int MSG_START = 4;
    public static final int MSG_STOP = 5;

    private static long sLastFrameAvailableTime;

    private PanoramaTexture mTexture;
    private PanoramaTexture mColorTexture;

    private int mCurrentFrame;
    private float mCurrentDegree;
    private int mCurrentMode = PANORAMA_MODE_3D;
    private boolean mSkipAnimationNextTime = false;
    private boolean mForward = true;

    private int mFrameCount;
    private int mFrameTimeGap;
    private float mFrameDegreeGap;
    private ThumbType mThumbType;
    private boolean mIsPlaying = false;

    public PanoramaPlayer(Context context, MediaData md, OutputType outputType,
            ThumbType thumbType) {
        super(context, md, outputType);
        mThumbType = thumbType;
        newPlaceHolderTexture();
    }

    public boolean onPrepare() {
        MtkLog.i(TAG, "<onPrepare>");
        PanoramaEntry entry = PanoramaHelper.getThumbEntry(mMediaData,
                mThumbType, mContext);
        if (entry == null) {
            MtkLog.i(TAG, "<onPrepare> entry == null, return false");
            return false;
        }
        mTexture = new PanoramaTexture(entry.mBitmap, entry.mConfig,
                mMediaData.orientation);
        mFrameCount = entry.mConfig.mFrameTotalCount;
        mFrameTimeGap = entry.mConfig.mFrameTimeGap;
        mFrameDegreeGap = entry.mConfig.mFrameDegreeGap;
        mCurrentFrame = 0;
        return true;
    }

    public void onRelease() {
        MtkLog.i(TAG, "<onRelease>");
        if (mColorTexture != null) {
            mColorTexture.recycle();
            mColorTexture = null;
        }
        if (mTexture != null) {
            mTexture.recycle();
            mTexture = null;
        }
    }

    public boolean onStart() {
        MtkLog.i(TAG, "<onStart>");
        startPlayback();
        sendNotify(MSG_START, mFrameCount, null);
        return true;
    }

    public boolean onPause() {
        MtkLog.i(TAG, "<onPause>");
        switchMode(PANORAMA_MODE_3D, false);
        return true;
    }

    public boolean onStop() {
        MtkLog.i(TAG, "<onStop>");
        stopPlayback();
        sendNotify(MSG_STOP, 0, null);
        return true;
    }

    public MTexture getTexture(MGLCanvas canvas) {
        assert (mOutputType == Player.OutputType.TEXTURE);
        if (mCurrentMode == PANORAMA_MODE_NORMAL)
            return null;
        if (mTexture == null && mThumbType == ThumbType.MIDDLE)
            return (MTexture) mColorTexture;
        else
            return (MTexture) mTexture;
    }

    public Bitmap getBitmap() {
        assert (mOutputType == Player.OutputType.BITMAP);
        return null;
    }

    public int getOutputWidth() {
        if (mCurrentMode == PANORAMA_MODE_3D) {
            PanoramaHelper.getMiddleThumbHeight();
            if (mMediaData.orientation == 90 || mMediaData.orientation == 270)
                return PanoramaHelper.getMiddleThumbHeight();
            else
                return PanoramaHelper.getMiddleThumbWidth();
        } else
            return 0;
    }

    public int getOutputHeight() {
        if (mCurrentMode == PANORAMA_MODE_3D)
            if (mMediaData.orientation == 90 || mMediaData.orientation == 270)
                return PanoramaHelper.getMiddleThumbWidth();
            else
                return PanoramaHelper.getMiddleThumbHeight();
        else
            return 0;
    }

    public boolean isSkipAnimationWhenUpdateSize() {
        if (mSkipAnimationNextTime) {
            mSkipAnimationNextTime = false;
            return true;
        }
        return false;
    }

    public int getFrameCount() {
        return mFrameCount;
    }

    public int getCurrentMode() {
        return mCurrentMode;
    }

    public void setCurrentFrame(int frame) {
        assert (frame >= 0 && frame < mFrameCount);
        mForward = (frame > mCurrentFrame);
        mCurrentFrame = frame;
        mCurrentDegree = mCurrentFrame * mFrameDegreeGap;
        if (mTexture != null)
            mTexture.setDegree((float) mCurrentDegree);
        sendNotify(MSG_UPDATE_CURRENT_FRAME, mCurrentFrame, null);
        sendFrameAvailable();
    }

    public void stopPlayback() {
        mIsPlaying = false;
    }

    public void startPlayback() {
        mIsPlaying = true;
        sendPlayFrameDelayed(0);
    }

    public void switchMode(int mode, boolean fromUser) {
        if (mCurrentMode == mode)
            return;
        mCurrentMode = mode;
        switch (mode) {
        case PANORAMA_MODE_3D:
            mSkipAnimationNextTime = fromUser;
            startPlayback();
            sendNotify(MSG_MODE_3D);
            break;
        case PANORAMA_MODE_NORMAL:
            mSkipAnimationNextTime = fromUser;
            stopPlayback();
            sendNotify(MSG_MODE_NORMAL);
            break;
        default:
            return;
        }
        sendFrameAvailable();
    }

    protected void onPlayFrame() {
        if (!mIsPlaying)
            return;
        if (mForward) {
            mCurrentFrame++;
            if (mCurrentFrame >= mFrameCount) {
                mCurrentFrame -= 2;
                mForward = false;
            }
        } else {
            mCurrentFrame--;
            if (mCurrentFrame < 0) {
                mCurrentFrame += 2;
                mForward = true;
            }
        }
        mCurrentDegree = mCurrentFrame * mFrameDegreeGap;
        if (mTexture != null)
            mTexture.setDegree(mCurrentDegree);
        long now = System.currentTimeMillis();
        if (mThumbType != ThumbType.MIDDLE
                && now - sLastFrameAvailableTime > mFrameTimeGap) {
            sendFrameAvailable();
            sLastFrameAvailableTime = now;
        } else {
            sendFrameAvailable();
        }
        sendPlayFrameDelayed(mFrameTimeGap);
        sendNotify(MSG_UPDATE_CURRENT_FRAME, mCurrentFrame, null);
    }

    private void newPlaceHolderTexture() {
        if (mColorTexture != null || mThumbType != ThumbType.MIDDLE)
            return;
        PanoramaConfig config = null;
        int width;
        int height;
        if (mMediaData.orientation == 90 || mMediaData.orientation == 270) {
            height = mMediaData.width;
            width = mMediaData.height;
        } else {
            height = mMediaData.height;
            width = mMediaData.width;
        }
        width = PanoramaHelper.getProperRatioBitmapWidth(width, height);
        config = new PanoramaConfig(width, height, PanoramaHelper
                .getMiddleThumbWidth(), PanoramaHelper
                .getMiddleThumbHeight());
        mColorTexture = new PanoramaTexture(0x1A1A1A, config,
                mMediaData.orientation);
    }
}