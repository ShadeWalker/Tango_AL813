package com.mediatek.galleryfeature.mav;

import com.mediatek.galleryframework.util.MtkLog;

public class AnimationEx {
    private static final String TAG = "MtkGallery2/AnimationEx";
    public static final int TYPE_ANIMATION_CONTINUOUS = 1;
    public static final int TYPE_ANIMATION_PLAYBACK = 2;
    public static final int TYPE_ANIMATION_INTERVAL = 5;
    public static final int TYPE_ANIMATION_SINGLEIMAGE = 3;
    private int mCurrentMavFrameIndex = Integer.MAX_VALUE;

    private int mTargetMavFrameIndex = Integer.MAX_VALUE;
    public int mType = 0;
    private int  mFrameCout = 25;
    private boolean mIsDisabled = false;
    private boolean mIsInTransitionMode = false;
    private long mBeginTime;
    private long mEndTime;
    private int mIntervalTime = 0;

    public AnimationEx(int frameCount, int currentMavFrameIndex,
            int targetFrame, int type, int intervalTime) {
        mIntervalTime = intervalTime;
        initAnimation(frameCount, currentMavFrameIndex, targetFrame, type);
    }

    public AnimationEx(int currentMavFrameIndex, int type) {
        initAnimation(currentMavFrameIndex, type);
    }

    public void initAnimation(int frameCount, int currentMavFrameIndex,
            int targetFrame, int type) {
        mFrameCout = frameCount;
        mTargetMavFrameIndex = targetFrame;
        mType = type;
        mCurrentMavFrameIndex = currentMavFrameIndex;
    }

    public void initAnimation(int lastIndex, int type) {
        if (mFrameCout <= lastIndex) {
            lastIndex = mFrameCout - 1;
        }
        RenderThreadEx.TYPE = type;
        mType = type;
        if (mCurrentMavFrameIndex == Integer.MAX_VALUE && lastIndex != Integer.MAX_VALUE) {
            mCurrentMavFrameIndex = lastIndex;
        }
        mTargetMavFrameIndex = lastIndex;
    }

    public boolean isInTranslateMode() {
        return mIsInTransitionMode;
    }

    public boolean mIsDisabled() {
        return mIsDisabled == true;
    }

    public void resetAnimation(int type) {
        RenderThreadEx.TYPE = type;
        if (type == TYPE_ANIMATION_PLAYBACK) {
            if (isRightDirection()) {
                initAnimation(mFrameCout - 1, TYPE_ANIMATION_PLAYBACK);
            } else {
                initAnimation(0, TYPE_ANIMATION_PLAYBACK);
            }
        } else if (type == TYPE_ANIMATION_INTERVAL) {
            mType = type;
            mBeginTime = System.currentTimeMillis();
            mEndTime = mBeginTime + mIntervalTime;
        }
    }

    public boolean advanceAnimation() {
        MtkLog.d(TAG, "<advanceAnimation>||||| mCurrentMavFrameIndex="
                + mCurrentMavFrameIndex + "   mTargetMavFrameIndex=="
                + mTargetMavFrameIndex + "   mType=" + mType
                + "   mFrameCout=" + mFrameCout + " mEndTime=" + mEndTime
                + " System.currentTimeMillis()=" + System.currentTimeMillis()
                + " mIsInTransitionMode=" + mIsInTransitionMode + " " + this);
        if (mCurrentMavFrameIndex == 0xFFFF || mTargetMavFrameIndex == 0xFFFF) {
            return true;
        }
        if (mType == TYPE_ANIMATION_INTERVAL) {
            mCurrentMavFrameIndex = mTargetMavFrameIndex;
            return true;
        }

        int dValue = mCurrentMavFrameIndex - mTargetMavFrameIndex;
        mCurrentMavFrameIndex = dValue > 0 ? mCurrentMavFrameIndex - 1
                : (dValue < 0 ? mCurrentMavFrameIndex + 1
                        : mCurrentMavFrameIndex);
        if (mType == TYPE_ANIMATION_PLAYBACK && dValue == 0) {
            mTargetMavFrameIndex = (mFrameCout - 1) - mTargetMavFrameIndex;
        }
        return isFinished();
    }

    public boolean isFinished() {
        if (mType == TYPE_ANIMATION_PLAYBACK) {
            return false;
        } else if (mType == TYPE_ANIMATION_INTERVAL) {
            long currentTime = System.currentTimeMillis();
            return currentTime >= mEndTime;
        } else if (mType == TYPE_ANIMATION_CONTINUOUS) {
            return mCurrentMavFrameIndex == mTargetMavFrameIndex;
        }
        return false;
    }

    public int getType() {
        return mType;
    }

    public boolean isRightDirection() {
        return mCurrentMavFrameIndex < mTargetMavFrameIndex;
    }

    public int getCurrentFrame() {
        return mCurrentMavFrameIndex;
    }
    public void nextStepAnimation() {
        if (!isFinished())
            return;
        if (getType() == AnimationEx.TYPE_ANIMATION_CONTINUOUS) {
            resetAnimation(AnimationEx.TYPE_ANIMATION_INTERVAL);
        } else if (getType() == AnimationEx.TYPE_ANIMATION_INTERVAL) {
            resetAnimation(AnimationEx.TYPE_ANIMATION_PLAYBACK);
        }
    }
}
