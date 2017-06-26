package com.mediatek.galleryfeature.drm;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import com.android.gallery3d.R;
import com.mediatek.galleryframework.util.BitmapUtils;
import com.mediatek.galleryframework.util.MtkLog;

import com.mediatek.drm.OmaDrmStore;

import com.mediatek.galleryfeature.drm.DeviceMonitor.ConnectStatus;
import com.mediatek.galleryframework.base.MediaCenter;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.Player.OutputType;
import com.mediatek.galleryframework.base.Player.State;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.gl.MBitmapTexture;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MTexture;

public class DrmPlayer extends Player implements Player.OnFrameAvailableListener {
    private static final String TAG = "MtkGallery2/DrmPlayer";
    public static final String PLACE_HOLDER_COLOR = "#333333";
    public static final int RIGHTS_CONSUMED = 1;
    public static final int RIGHTS_NOT_CONSUME = 2;

    public static final int MSG_PREPARED = 0;
    public static final int MSG_CONSUMED = 1;
    private MediaCenter mMediaCenter;
    private DrmItem mDrmItem;
    private Player mRealPlayer;
    private int mConsumeStatus = RIGHTS_NOT_CONSUME;
    private Bitmap mThumbnailBeforeConsume;
    private Bitmap mThumbnailAfterConsume;
    private DrmTexture mTextureBeforeConsume;
    private MBitmapTexture mTextureAfterConsume;

    // using to restore consume right status
    private boolean mConsumeRightAfterPrepared = false;

    // when other device plug in, limit the display of drm
    private ConnectStatus mDrmDisplayLimit = ConnectStatus.DISCONNECTED;
    private DrmProtectTexture sDrmDisplayLimitTexture;

    public DrmPlayer(Context context, MediaData md, OutputType outputType,
            MediaCenter mc) {
        super(context, md, outputType);
        mMediaCenter = mc;
    }

    public boolean onPrepare() {
        mDrmItem = (DrmItem) mMediaCenter.getItem(mMediaData);
        if (mDrmItem == null) {
            MtkLog.i(TAG, "<onPrepare> mDrmItem == null, return false");
            return false;
        }
        prepareConsumeStatus();
        // When RIGHTS_CONSUMED, there is no need to
        // prepare before consume bitmap & texture
        boolean prepareSuccess = true;
        if (mConsumeStatus == RIGHTS_NOT_CONSUME) {
            prepareSuccess = prepareBeforeConsume();
        }
        prepareSuccess = prepareAfterConsume();
        MtkLog.d(TAG, " <onPrepare> prepareSuccess = "+prepareSuccess+" rights = "+DrmHelper.checkRightsStatus(mContext,
                mMediaData.filePath, OmaDrmStore.Action.DISPLAY));
        prepareDisplayLimit();
        if (mConsumeRightAfterPrepared)
            consumeRights();
        sendFrameAvailable();
        sendNotify(MSG_PREPARED);
        return true;
    }

    public boolean onStart() {
        sendFrameAvailable();
        if (mConsumeStatus == RIGHTS_CONSUMED) {
            sendNotify(MSG_CONSUMED);
            if (mRealPlayer != null)
                return mRealPlayer.start();
        }
        return true;
    }

    public boolean onPause() {
        boolean success = true;
        if (mRealPlayer != null && mRealPlayer.getState() == State.PLAYING) {
            success = mRealPlayer.pause();
        }
        prepareConsumeStatus();
        return success;
    }

    public boolean onStop() {
        if (mRealPlayer != null && mRealPlayer.getState() == State.PLAYING) {
            return mRealPlayer.stop();
        }
        return true;
    }

    public void onRelease() {
        if (mTextureAfterConsume != null) {
            mTextureAfterConsume.recycle();
            mTextureAfterConsume = null;
        }
        if (mTextureBeforeConsume != null) {
            mTextureBeforeConsume.recycle();
            mTextureBeforeConsume = null;
        }
        if (mThumbnailBeforeConsume != null) {
            mThumbnailBeforeConsume.recycle();
            mThumbnailBeforeConsume = null;
        }
        if (mThumbnailAfterConsume != null) {
            mThumbnailAfterConsume.recycle();
            mThumbnailAfterConsume = null;
        }
        if (mRealPlayer != null) {
            mRealPlayer.release();
            mRealPlayer = null;
        }
        mConsumeStatus = RIGHTS_NOT_CONSUME;
        mDrmItem = null;
    }

    public int getOutputWidth() {
        if (mDrmDisplayLimit != ConnectStatus.DISCONNECTED) {
            if (sDrmDisplayLimitTexture != null)
                return sDrmDisplayLimitTexture.getWidth();
        } else if (mConsumeStatus == RIGHTS_NOT_CONSUME
                && mTextureBeforeConsume != null) {
            return mTextureBeforeConsume.getWidth();
        } else if (mRealPlayer != null) {
            return mRealPlayer.getOutputWidth();
        } else if (mTextureAfterConsume != null) {
            if (mDrmItem != null && mDrmItem.getOriginalImageWidth() != 0) {
                return mDrmItem.getOriginalImageWidth();
            } else {
                return mTextureAfterConsume.getWidth();
            }
        }
        return 0;
    }

    public int getOutputHeight() {
        if (mDrmDisplayLimit != ConnectStatus.DISCONNECTED) {
            if (sDrmDisplayLimitTexture != null)
                return sDrmDisplayLimitTexture.getHeight();
        } else if (mConsumeStatus == RIGHTS_NOT_CONSUME
                && mTextureBeforeConsume != null) {
            return mTextureBeforeConsume.getHeight();
        } else if (mRealPlayer != null) {
            return mRealPlayer.getOutputHeight();
        } else if (mTextureAfterConsume != null) {
            if (mDrmItem != null && mDrmItem.getOriginalImageHeight() != 0) {
                return mDrmItem.getOriginalImageHeight();
            } else {
                return mTextureAfterConsume.getHeight();
            }
        }
        return 0;
    }

    public MTexture getTexture(MGLCanvas canvas) {
        if (mDrmDisplayLimit != ConnectStatus.DISCONNECTED) {
            if (sDrmDisplayLimitTexture != null)
                sDrmDisplayLimitTexture.setProtectStatus(mDrmDisplayLimit);
            // MtkLog.i(TAG, "<getTexture> display limit");
            return sDrmDisplayLimitTexture;
        } else if (mConsumeStatus == RIGHTS_NOT_CONSUME) {
            // MtkLog.i(TAG, "<getTexture> mTextureBeforeConsume = "
            // + mTextureBeforeConsume);
            return mTextureBeforeConsume;
        } else if (mRealPlayer != null) {
            // MtkLog.i(TAG, "<getTexture> mRealPlayer.getTexture(canvas) = "
            // + mRealPlayer.getTexture(canvas));
            return mRealPlayer.getTexture(canvas);
        } else {
            // MtkLog.i(TAG, "<getTexture> mTextureAfterConsume = "
            // + mTextureAfterConsume);
            return mTextureAfterConsume;
        }
    }

    public void onFrameAvailable(Player player) {
        if (mFrameAvailableListener != null) {
            mFrameAvailableListener.onFrameAvailable(this);
        }
    }

    public void consumeRights() {
        if (mConsumeStatus == RIGHTS_CONSUMED)
            return;
        mConsumeStatus = RIGHTS_CONSUMED;
        sendNotify(MSG_CONSUMED);
        if (mRealPlayer != null) {
            mRealPlayer.start();
        }
        sendFrameAvailable();
        MtkLog.i(TAG, "<consumeRights>");
    }

    public void consumeRightsAfterPrepared() {
        if (getState() != Player.State.PREPARED)
            mConsumeRightAfterPrepared = true;
        else
            consumeRights();
    }

    public int getConsumeStatus() {
        return mConsumeStatus;
    }

    public void setDrmDisplayLimit(ConnectStatus status) {
        mDrmDisplayLimit = status;
        if (getState() == Player.State.PLAYING)
            sendFrameAvailable();
    }

    public Player getRealPlayer() {
        return mRealPlayer;
    }

    private boolean prepareBeforeConsume() {
        mThumbnailBeforeConsume = mDrmItem.getDrmThumbnail(ThumbType.MIDDLE,
                true);
        String showString = null;
        if (mThumbnailBeforeConsume == null) {
            mThumbnailBeforeConsume = preparePlaceHolderForNoThumb();
            // if current file is fl, and no thumbnail, need show no thumb string
            if (mMediaData.drmMethod == OmaDrmStore.DrmMethod.METHOD_FL)
                showString = mContext.getString(R.string.no_thumbnail);
        }
        //when bitmap's config be null(such as .wbmp),reset the config :Config.ARGB_8888.
        mThumbnailBeforeConsume = BitmapUtils.ensureGLCompatibleBitmap(mThumbnailBeforeConsume);
        mTextureBeforeConsume = new DrmTexture(mContext,
                mThumbnailBeforeConsume, DrmHelper.getDrmIconResourceID(
                        mContext, mMediaData), showString);
        return true;
    }

    private boolean prepareAfterConsume() {
        // Gallery is not responsible for drm video play, but video player.
        // So if current MediaData is video, do not get real player.
        if (!mMediaData.isVideo) {
            mRealPlayer = mMediaCenter.getRealPlayer(mMediaData, ThumbType.MIDDLE);
            if (mRealPlayer != null) {
                mRealPlayer.setOnFrameAvailableListener(this);
                return mRealPlayer.prepare();
            }
        }
        mThumbnailAfterConsume = mDrmItem.getDrmThumbnail(ThumbType.HIGHQUALITY,
                false);
        if (mThumbnailAfterConsume == null)
            return true;
        //when bitmap's config be null(such as .wbmp),reset the config :Config.ARGB_8888.
        mThumbnailAfterConsume = BitmapUtils.ensureGLCompatibleBitmap(mThumbnailAfterConsume);
        mTextureAfterConsume = new MBitmapTexture(mThumbnailAfterConsume);
        return true;
    }

    private void prepareDisplayLimit() {
        if (sDrmDisplayLimitTexture == null) {
            sDrmDisplayLimitTexture = new DrmProtectTexture(mContext);
        }
    }

    private void prepareConsumeStatus() {
        boolean hasRightsToShow = DrmHelper.hasRightsToShow(mContext,
                mMediaData);
        if (mMediaData.drmMethod == OmaDrmStore.DrmMethod.METHOD_FL
                && hasRightsToShow) {
            mConsumeStatus = RIGHTS_CONSUMED;
        } else {
            mConsumeStatus = RIGHTS_NOT_CONSUME;
        }
    }

    private Bitmap preparePlaceHolderForNoThumb() {
        int dstWidth, dstHeight;
        int maxLength = ThumbType.MICRO.getTargetSize();
        int srcWidth = mMediaData.width == 0 ? maxLength : mMediaData.width;
        int srcHeight = mMediaData.height == 0 ? maxLength : mMediaData.height;
        float scale = Math.min((float) maxLength / srcWidth, (float) maxLength
                / srcHeight);
        if (scale >= 1.0f) {
            dstWidth = srcWidth;
            dstHeight = srcHeight;
        } else {
            dstWidth = Math.round(srcWidth * scale);
            dstHeight = Math.round(srcHeight * scale);
        }
        Bitmap bitmap = Bitmap.createBitmap(dstWidth, dstHeight,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.parseColor(PLACE_HOLDER_COLOR));
        return bitmap;
    }
}
