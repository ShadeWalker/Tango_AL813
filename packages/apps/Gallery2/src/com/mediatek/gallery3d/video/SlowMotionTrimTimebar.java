package com.mediatek.gallery3d.video;


import com.android.gallery3d.app.Log;
import com.android.gallery3d.app.TimeBar;
import com.android.gallery3d.app.TimeBar.Listener;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;

import com.android.gallery3d.R;
import com.mediatek.galleryframework.util.MtkLog;

public class SlowMotionTrimTimebar extends TimeBar {

    public static final int SCRUBBER_NONE = 0;
    public static final int SCRUBBER_START = 1;
    public static final int SCRUBBER_CURRENT = 2;
    public static final int SCRUBBER_END = 3;

    private static final int BAR_WIDTH = 4;
    private static final int DASHED_LINE_COLOR = 0xFF808080;
    private static final float[] DASHED_LINE_INTERVALS = new float[]{3, 7};

    private int mPressedThumb = SCRUBBER_NONE;
    private TrimLayoutExt mLayoutExt;
    // On touch event, the setting order is Scrubber Position -> Time ->
    // PlayedBar. At the setTimes(), activity can update the Time directly, then
    // PlayedBar will be updated too.
    private int mTrimStartScrubberLeft;
    private int mTrimEndScrubberLeft;

    private int mTrimStartScrubberTop;
    private int mTrimEndScrubberTop;

    private int mTrimStartTime;
    private int mTrimEndTime;

    private Uri mUri;

    private final Paint mDashedLinePaint;
    private final Paint mPlayedDashedLinePaint;

    private Context mContext;
    private int mSlowMotionStartTime;
    private int mSlowMotionEndTime;
    private final int mSlowMotionSpeed;

    private final Rect mRealLeftBar;
    private final Rect mRealRightBar;
    private final Rect mDashBar;
    private final Rect mPlayedDashBar;
    private final Rect mPlayedRealLeftBar;
    private final Rect mPlayedRealRightBar;
    private final Path mDashPath;
    private final Path mPlayedDashPath;

    private final Bitmap mTrimStartScrubber;
    private final Bitmap mTrimEndScrubber;

    private final MTKVideoView mVideoView;
    private SlowMotionItem mSlowMotionItem;

    private static final String TAG = "Gallery2/SlowMotionTrimTimebar";

    public SlowMotionTrimTimebar(Context context, Listener listener) {
        super(context, listener);
        MtkLog.v(TAG, "TrimTimeBar init");
        mTrimStartTime = 0;
        mTrimEndTime = 0;
        mTrimStartScrubberLeft = 0;
        mTrimEndScrubberLeft = 0;
        mTrimStartScrubberTop = 0;
        mTrimEndScrubberTop = 0;

        mTrimStartScrubber = BitmapFactory.decodeResource(getResources(),
                R.drawable.text_select_handle_left);
        mTrimEndScrubber = BitmapFactory.decodeResource(getResources(),
                R.drawable.text_select_handle_right);
        // Increase the size of this trimTimeBar, but minimize the scrubber
        // touch padding since we have 3 scrubbers now.
        mScrubberPadding = 0;
        mVPaddingInPx = mVPaddingInPx * 3 / 2;
        mLayoutExt = new TrimLayoutExt();

        mContext = context;
        mDashedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDashedLinePaint.setStyle(Style.STROKE);
        mDashedLinePaint.setColor(DASHED_LINE_COLOR);
        mDashedLinePaint.setStrokeWidth(BAR_WIDTH);
        PathEffect effects = new DashPathEffect(DASHED_LINE_INTERVALS, 1);
        mDashedLinePaint.setPathEffect(effects);

        mPlayedDashedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPlayedDashedLinePaint.setStyle(Style.STROKE);
        mPlayedDashedLinePaint.setColor(Color.WHITE);
        mPlayedDashedLinePaint.setStrokeWidth(BAR_WIDTH);
        mPlayedDashedLinePaint.setPathEffect(effects);

        mUri = ((Activity) mContext).getIntent().getData();
        mSlowMotionItem = new SlowMotionItem(mContext, mUri);

        mSlowMotionStartTime = mSlowMotionItem.getSectionStartTime();
        mSlowMotionEndTime = mSlowMotionItem.getSectionEndTime();
        mSlowMotionSpeed = mSlowMotionItem.getSpeed();

        mRealLeftBar = new Rect();
        mRealRightBar = new Rect();
        mDashBar = new Rect();
        mPlayedDashBar = new Rect();
        mPlayedRealLeftBar = new Rect();
        mPlayedRealRightBar = new Rect();
        mDashPath = new Path();
        mPlayedDashPath = new Path();

        View rootView = ((Activity) mContext).findViewById(R.id.trim_view_root);
        mVideoView = (MTKVideoView) rootView.findViewById(R.id.surface_view);
        mVideoView.setSlowMotionSpeed(mSlowMotionSpeed);

    }

    private int getBarPosFromTime(int time) {
        MtkLog.v(TAG, "getBarPosFromTime time is " + time);
        return mProgressBar.left
                + (int) ((mProgressBar.width() * (long) time) / mTotalTime);
    }

    private int trimStartScrubberTipOffset() {
        return mTrimStartScrubber.getWidth() * 3 / 4;
    }

    private int trimEndScrubberTipOffset() {
        return mTrimEndScrubber.getWidth() / 4;
    }

    // Based on all the time info (current, total, trimStart, trimEnd), we
    // decide the playedBar size.
    private void updatePlayedBarAndScrubberFromTime() {
        MtkLog.v(TAG, "updatePlayedBarAndScrubberFromTime()");
        // According to the Time, update the Played Bar
        mPlayedBar.set(mProgressBar);

        // Init played bar Rect.
        mPlayedDashBar.setEmpty();
        mPlayedRealLeftBar.setEmpty();
        mPlayedRealRightBar.setEmpty();
        // end

        if (mTotalTime > 0) {
            // set playedBar according to the trim time.
            mPlayedBar.left = getBarPosFromTime(mTrimStartTime);
            mPlayedBar.right = getBarPosFromTime(mCurrentTime);
            if (!mScrubbing) {
                mScrubberLeft = mPlayedBar.right - mScrubber.getWidth() / 2;
                mTrimStartScrubberLeft = mPlayedBar.left
                        - trimStartScrubberTipOffset();
                mTrimEndScrubberLeft = getBarPosFromTime(mTrimEndTime)
                        - trimEndScrubberTipOffset();
            }
            // maybe should optimize.
            if (mPlayedBar.left < mRealLeftBar.right) {
                // If in the RealLeftBar Section
                mPlayedRealLeftBar.set(mRealLeftBar);
                mPlayedRealLeftBar.left = mPlayedBar.left;
            } else if (mPlayedBar.left < mRealRightBar.left) {
                // If in the slow motion section.
                mPlayedDashBar.set(mDashBar);
                mPlayedDashBar.left = round10(mPlayedBar.left);
            } else {
                // If in the RealRightBar Section.
                mPlayedRealRightBar.set(mRealRightBar);
                mPlayedRealRightBar.left = mPlayedBar.left;
                mPlayedRealRightBar.right = mPlayedBar.left;
            }

            if (mPlayedBar.right < mRealLeftBar.right) {
                // If in the RealLeftBar Section
                mPlayedRealLeftBar.right = mPlayedBar.right;
            } else if (mPlayedBar.right < mRealRightBar.left) {
                 //If in the slow motion section.
                 if (mPlayedDashBar.bottom == 0) {
                     //If it is not initial, set default value.
                     mPlayedDashBar.set(mDashBar);
                 }
                 mPlayedDashBar.right = round10(mPlayedBar.right);
            } else {
                // If in the RealRightBar Section.
                if (mPlayedRealRightBar.bottom == 0) {
                    // If it is not initial, set default value.
                    mPlayedRealRightBar.set(mRealRightBar);
                }
                if (mPlayedDashBar.bottom == 0) {
                    //If it is not initial, set default value.
                    mPlayedDashBar.set(mDashBar);
                }
                mPlayedRealRightBar.right = mPlayedBar.right;
            }
        } else {
            // If the video is not prepared, just show the scrubber at the end
            // of progressBar
            mPlayedBar.right = mProgressBar.left;
            mPlayedDashBar.right = mProgressBar.left;
            mScrubberLeft = mProgressBar.left - mScrubber.getWidth() / 2;
            mTrimStartScrubberLeft = mProgressBar.left
                    - trimStartScrubberTipOffset();
            mTrimEndScrubberLeft = mProgressBar.right
                    - trimEndScrubberTipOffset();
        }
    }

    private void initTrimTimeIfNeeded() {
        if (mTotalTime > 0 && mTrimEndTime == 0) {
            mTrimEndTime = mTotalTime;
        }
    }

    private void initTrimTimeBarIfNeeded() {
        int duration = 0;
        if (mTotalTime > 0) {
            duration = mTotalTime;
        } else {
            duration = mSlowMotionItem.getDuration();
        }

        if (mSlowMotionEndTime == 0) {
            //Calculate total seconds.
            int totalSeconds =  (int) duration / 1000;

            if (totalSeconds >= 3) {
                //if total seconds >= 3s, the default section is 1/5  slow motion bar in the center.
                mSlowMotionStartTime = duration * 2 / 5;
                mSlowMotionEndTime = duration * 3 / 5;
            } else {
              //if total seconds < 3s, default section is the whole slow motion bar.
                mSlowMotionStartTime = 0;
                mSlowMotionEndTime = duration;
            }
         // update calculate result to db.
            if (mSlowMotionItem.isSlowMotionVideo()) {
                refreshSlowMotionSection(mSlowMotionStartTime, mSlowMotionEndTime);
            }
        }

        mRealLeftBar.set(mProgressBar);
        mRealLeftBar.right = round10(mProgressBar.left
                + (int) ((mProgressBar.width() * (long) mSlowMotionStartTime) / duration));
        mRealRightBar.set(mProgressBar);
        mRealRightBar.left = round10(mProgressBar.left
                + (int) ((mProgressBar.width() * (long) mSlowMotionEndTime) / duration));
        mDashBar.set(mProgressBar);
        mDashBar.left = mRealLeftBar.right;
        mDashBar.right = mRealRightBar.left;

    }

    private void refreshSlowMotionSection(final int startTime, final int endTime) {
        if (mSlowMotionItem != null) {
            mSlowMotionItem.setSectionStartTime(startTime);
            mSlowMotionItem.setSectionEndTime(endTime);
            mSlowMotionItem.updateItemToDB();
        }
    }

    private void update() {
        MtkLog.v(TAG, "update()");
        initTrimTimeIfNeeded();
        initTrimTimeBarIfNeeded();
        updatePlayedBarAndScrubberFromTime();
        invalidate();
    }

    @Override
    public void setTime(int currentTime, int totalTime, int trimStartTime,
            int trimEndTime) {
        MtkLog.v(TAG, "setTime() currentTime " + currentTime + ", totalTime "
                + totalTime + ", trimStartTime " + trimStartTime
                + ", trimEndTime " + trimEndTime);
        if (mCurrentTime == currentTime && mTotalTime == totalTime
                && mTrimStartTime == trimStartTime
                && mTrimEndTime == trimEndTime) {
            return;
        }
      //If currentTime is in the slow motion section, should set slow motion speed.
        if (currentTime > mSlowMotionStartTime && currentTime < mSlowMotionEndTime) {
            mVideoView.enableSlowMotionSpeed();
        } else {
            mVideoView.disableSlowMotionSpeed();
        }
        mCurrentTime = currentTime;
        mTotalTime = totalTime;
        mTrimStartTime = trimStartTime;
        mTrimEndTime = trimEndTime;
        update();
    }

    private int whichScrubber(float x, float y) {
        if (inScrubber(x, y, mTrimStartScrubberLeft, mTrimStartScrubberTop,
                mTrimStartScrubber)) {
            return SCRUBBER_START;
        } else if (inScrubber(x, y, mTrimEndScrubberLeft, mTrimEndScrubberTop,
                mTrimEndScrubber)) {
            return SCRUBBER_END;
        } else if (inScrubber(x, y, mScrubberLeft, mScrubberTop, mScrubber)) {
            return SCRUBBER_CURRENT;
        }
        return SCRUBBER_NONE;
    }

    private boolean inScrubber(float x, float y, int startX, int startY,
            Bitmap scrubber) {
        int scrubberRight = startX + scrubber.getWidth();
        int scrubberBottom = startY + scrubber.getHeight();
        return startX < x && x < scrubberRight && startY < y
                && y < scrubberBottom;
    }

    private int clampScrubber(int scrubberLeft, int offset, int lowerBound,
            int upperBound) {
        int max = upperBound - offset;
        int min = lowerBound - offset;
        return Math.min(max, Math.max(min, scrubberLeft));
    }

    private int getScrubberTime(int scrubberLeft, int offset) {
        return (int) ((long) (scrubberLeft + offset - mProgressBar.left)
                * mTotalTime / mProgressBar.width());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        MtkLog.v(TAG, "onLayout()");
        int w = r - l;
        int h = b - t;
        if (!mShowTimes && !mShowScrubber) {
            mProgressBar.set(0, 0, w, h);
        } else {
            int margin = mScrubber.getWidth() / 3;
            if (mShowTimes) {
                margin += mTimeBounds.width();
            }
            int progressY = h / 4 + mLayoutExt.getProgressOffset(h);
            int scrubberY = progressY - mScrubber.getHeight() / 2 + 1;
            mScrubberTop = scrubberY;
            mTrimStartScrubberTop = progressY;
            mTrimEndScrubberTop = progressY;
            mProgressBar.set(getPaddingLeft() + margin, progressY, w
                    - getPaddingRight() - margin, progressY + 4);
        }
        update();
    }

    @Override
    public int getPreferredHeight() {
        Log.i(TAG, "getPreferredHeight mTrimStartScrubber "
                + mTrimStartScrubber.getHeight());
        return mLayoutExt.getPreferredHeight(super.getPreferredHeight());
    }

    @Override
    public int getBarHeight() {
        return getPreferredHeight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        MtkLog.v(TAG, "onDraw()");
        if (mPlayedBar.left <= mPlayedBar.right) {
            if (mPlayedDashBar.bottom != 0) {
                mPlayedDashPath.reset(); // clear last draw.
                mPlayedDashPath.moveTo(mPlayedDashBar.left, mDashBar.top + 2);
                mPlayedDashPath
                        .lineTo(mPlayedDashBar.right, mDashBar.top + 2);
                canvas.drawPath(mPlayedDashPath, mPlayedDashedLinePaint);

                mDashPath.reset();
                mDashPath.moveTo(mRealLeftBar.right, mRealLeftBar.top + 2);
                mDashPath.lineTo(mPlayedDashBar.left, mRealLeftBar.top + 2);
                mDashPath.moveTo(mPlayedDashBar.right, mRealLeftBar.top + 2);
                mDashPath.lineTo(mRealRightBar.left, mRealLeftBar.top + 2);
            } else {
                mDashPath.reset();
                mDashPath.moveTo(mRealLeftBar.right, mRealLeftBar.top + 2);
                mDashPath.lineTo(mRealRightBar.left, mRealLeftBar.top + 2);
            }
            canvas.drawPath(mDashPath, mDashedLinePaint);
        } else {
            mPlayedDashPath.reset(); // clear last draw.
            canvas.drawPath(mPlayedDashPath, mPlayedDashedLinePaint);
            mDashPath.reset();
            mDashPath.moveTo(mRealLeftBar.right, mDashBar.top + 2);
            mDashPath.lineTo(mRealRightBar.left, mDashBar.top + 2);
            canvas.drawPath(mDashPath, mDashedLinePaint);
        }
        // Gray Real line
        canvas.drawRect(mRealLeftBar, mProgressPaint);
        canvas.drawRect(mRealRightBar, mProgressPaint);
        // White played real line.
        canvas.drawRect(mPlayedRealLeftBar, mPlayedPaint);
        canvas.drawRect(mPlayedRealRightBar, mPlayedPaint);

        if (mShowTimes) {
            canvas.drawText(stringForTime(mCurrentTime), mTimeBounds.width()
                    / 2 + getPaddingLeft() * 3, mTimeBounds.height() / 2
                    + mTrimStartScrubberTop + mLayoutExt.getTimeOffset(),
                    mTimeTextPaint);
            canvas.drawText(stringForTime(mTotalTime), getWidth()
                    - getPaddingRight() * 3 - mTimeBounds.width() / 2,
                    mTimeBounds.height() / 2 + mTrimStartScrubberTop
                            + mLayoutExt.getTimeOffset(), mTimeTextPaint);
        }

        // draw extra scrubbers
        if (mShowScrubber) {
            canvas.drawBitmap(mScrubber, mScrubberLeft, mScrubberTop, null);
            canvas.drawBitmap(mTrimStartScrubber, mTrimStartScrubberLeft,
                    mTrimStartScrubberTop, null);
            canvas.drawBitmap(mTrimEndScrubber, mTrimEndScrubberLeft,
                    mTrimEndScrubberTop, null);
        }
    }

    private void updateTimeFromPos() {
        mCurrentTime = getScrubberTime(mScrubberLeft, mScrubber.getWidth() / 2);
        mTrimStartTime = getScrubberTime(mTrimStartScrubberLeft,
                trimStartScrubberTipOffset());
        mTrimEndTime = getScrubberTime(mTrimEndScrubberLeft,
                trimEndScrubberTipOffset());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        MtkLog.v(TAG, "onTouchEvent() event.getAction()" + event.getAction());
        if (mShowScrubber) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mPressedThumb = whichScrubber(x, y);
                switch (mPressedThumb) {
                case SCRUBBER_NONE:
                    break;
                case SCRUBBER_CURRENT:
                    mScrubbing = true;
                    mScrubberCorrection = x - mScrubberLeft;
                    break;
                case SCRUBBER_START:
                    mScrubbing = true;
                    mScrubberCorrection = x - mTrimStartScrubberLeft;
                    break;
                case SCRUBBER_END:
                    mScrubbing = true;
                    mScrubberCorrection = x - mTrimEndScrubberLeft;
                    break;
                }
                if (mScrubbing == true) {
                    mListener.onScrubbingStart();
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mScrubbing) {
                    int seekToTime = -1;
                    int lowerBound = mTrimStartScrubberLeft
                            + trimStartScrubberTipOffset();
                    int upperBound = mTrimEndScrubberLeft
                            + trimEndScrubberTipOffset();
                    switch (mPressedThumb) {
                    case SCRUBBER_CURRENT:
                        mScrubberLeft = x - mScrubberCorrection;
                        mScrubberLeft = clampScrubber(mScrubberLeft,
                                mScrubber.getWidth() / 2, lowerBound,
                                upperBound);
                        seekToTime = getScrubberTime(mScrubberLeft,
                                mScrubber.getWidth() / 2);
                        break;
                    case SCRUBBER_START:
                        mTrimStartScrubberLeft = x - mScrubberCorrection;
                        // Limit start <= end
                        if (mTrimStartScrubberLeft > mTrimEndScrubberLeft) {
                            mTrimStartScrubberLeft = mTrimEndScrubberLeft;
                        }
                        lowerBound = mProgressBar.left;
                        mTrimStartScrubberLeft = clampScrubber(
                                mTrimStartScrubberLeft,
                                trimStartScrubberTipOffset(), lowerBound,
                                upperBound);
                        seekToTime = getScrubberTime(mTrimStartScrubberLeft,
                                trimStartScrubberTipOffset());
                        break;
                    case SCRUBBER_END:
                        mTrimEndScrubberLeft = x - mScrubberCorrection;
                        upperBound = mProgressBar.right;
                        mTrimEndScrubberLeft = clampScrubber(
                                mTrimEndScrubberLeft,
                                trimEndScrubberTipOffset(), lowerBound,
                                upperBound);
                        seekToTime = getScrubberTime(mTrimEndScrubberLeft,
                                trimEndScrubberTipOffset());
                        break;
                    }
                    updateTimeFromPos();
                    updatePlayedBarAndScrubberFromTime();
                    if (seekToTime != -1) {
                        mListener.onScrubbingMove(seekToTime);
                    }
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mScrubbing) {
                    int seekToTime = 0;
                    switch (mPressedThumb) {
                    case SCRUBBER_CURRENT:
                        seekToTime = getScrubberTime(mScrubberLeft,
                                mScrubber.getWidth() / 2);
                        break;
                    case SCRUBBER_START:
                        seekToTime = getScrubberTime(mTrimStartScrubberLeft,
                                trimStartScrubberTipOffset());
                        mScrubberLeft = mTrimStartScrubberLeft
                                + trimStartScrubberTipOffset()
                                - mScrubber.getWidth() / 2;
                        break;
                    case SCRUBBER_END:
                        seekToTime = getScrubberTime(mTrimEndScrubberLeft,
                                trimEndScrubberTipOffset());
                        mScrubberLeft = mTrimEndScrubberLeft
                                + trimEndScrubberTipOffset()
                                - mScrubber.getWidth() / 2;
                        break;
                    }
                    updateTimeFromPos();
                    mListener.onScrubbingEnd(
                            seekToTime,
                            getScrubberTime(mTrimStartScrubberLeft,
                                    trimStartScrubberTipOffset()),
                            getScrubberTime(mTrimEndScrubberLeft,
                                    trimEndScrubberTipOffset()));
                    mScrubbing = false;
                    mPressedThumb = SCRUBBER_NONE;
                    // /M: add for fix google issue,when long press trimbar to
                    // trim maybe currenttime position don't sync whit trimbar
                    update();
                    return true;
                }
                break;
            }
        }
        return false;
    }

    private int round10(int value) {
        return (value + 5) / 10 * 10;
    }

    // / M: @{
    private class TrimLayoutExt {

        public int getPreferredHeight(int originalPreferredHeight) {
            return originalPreferredHeight + mTrimStartScrubber.getHeight();
        }

        public int getProgressOffset(int height) {
            return mTimeBounds.height() / 2 + height / 4;
        }

        public int getTimeOffset() {
            return -mVPaddingInPx / 2;
        }
    }
    // / @}

}