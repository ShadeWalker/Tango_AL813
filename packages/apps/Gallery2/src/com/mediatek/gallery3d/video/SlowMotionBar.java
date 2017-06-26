package com.mediatek.gallery3d.video;



import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.graphics.PathEffect;
import android.view.MotionEvent;
import android.view.View;

import com.android.gallery3d.app.Log;
import com.android.gallery3d.app.MovieActivity;
import com.android.gallery3d.app.TimeBar.Listener;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.R;
import com.mediatek.gallery3d.ext.IMovieItem;
import com.mediatek.galleryframework.util.MtkLog;

public class SlowMotionBar extends View {

    public interface Listener {
        void onScrubbingStart(int currentTime, int totalTime);

        void onScrubbingMove(int currentTime, int totalTime);

        void onScrubbingEnd(int currentTime, int totalTime);
    }

    private static final int SCRUBBER_NONE = 0;
    private static final int SCRUBBER_START = 1;
    private static final int SCRUBBER_END = 2;

    private static final int PORTRAIT_MINIMUM_SECTION = 20; //pixels.
    //Scrubber should response touch event within certain limits.
    private static final int SCRUBBER_RESPONSE_REGION = 80;

    private final Listener mListener;

    private int mPressedThumb = SCRUBBER_NONE;
    private int mScrubberCorrection;

    private int mStartScrubberLeft;
    private int mEndScrubberLeft;
    private int mStartScrubberTop;
    private int mEndScrubberTop;
    private int mStartTime;
    private int mEndTime;
    private int mTotalTime;
    private int mCurrentTime;
    private Context mContext;
    private boolean mScrubbing;
    private boolean mEnableScrubbing = true;

    private final Rect mProgressBar;
    private final Rect mSlowMotionLeftBar;
    private final Rect mSlowMotionRightBar;

    private final Bitmap mStartScrubber;
    private final Bitmap mEndScrubber;

    private final Paint mDashedLinePaint;
    private final Paint mRealLinePaint;
    private final Path mPath;

    private IMtkVideoController mVideoView;
    private int mCurrentSpeed = 1;
    private static final int NORMAL_SPEED = 1;

    private int mMiniSection;
    private final float mMiniRatio;

    private IMovieItem mMovieItem;

    private SlowMotionItem mSlowMotionItem;

    private static final String TAG = "Gallery2/SlowMotionBar";
    private static final int BAR_WIDTH = 4;
    private static final int V_PADDING_IN_DP = 30;

    private static final int START_SCRUBBER_WIDTH = 6;
    private static final int START_SCRUBBER_HEIGHT = 30;

    private static final float[] DASHED_LINE_INTERVALS = new float[]{3, 7};

    public static final int SCRUBBERING_START = 1;
    public static final int SCRUBBERING_END = 0;


    public SlowMotionBar(Context context, Listener listener, int speedViewPadding) {
        super(context);
        MtkLog.v(TAG, "SlowMotionBar init");
        mContext = context;
        mListener = Utils.checkNotNull(listener);

        mStartTime = 0;
        mEndTime = 0;
        mStartScrubberLeft = 0;
        mEndScrubberLeft = 0;
        mStartScrubberTop = 0;
        mEndScrubberTop = 0;

        //Initialize dashed line paint.
        mDashedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDashedLinePaint.setStyle(Style.STROKE);
        mDashedLinePaint.setColor(Color.WHITE);
        mDashedLinePaint.setStrokeWidth(BAR_WIDTH);
        PathEffect effects = new DashPathEffect(DASHED_LINE_INTERVALS, 1);
        mDashedLinePaint.setPathEffect(effects);
        //Initialize real line paint;
        mRealLinePaint = new Paint();
        mRealLinePaint.setColor(Color.WHITE);
        //Path is for drawing dashed line.
        mPath  = new Path();

        //ProgressBar is align time bar.
        mProgressBar = new Rect();
        mSlowMotionLeftBar = new Rect();
        mSlowMotionRightBar = new Rect();

        //Set padding align time bar.
        Bitmap scrubber; //for align time bar.
        scrubber = BitmapFactory.decodeResource(getResources(), R.drawable.scrubber_knob);
        int padding = scrubber.getWidth() / 2 + 1;
        setPadding(padding, 0, padding, 0);
        scrubber.recycle();

        //Init start & end scrubber.
        Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.m_slow_motion_bar);
        mStartScrubber = Bitmap.createScaledBitmap(bmp, START_SCRUBBER_WIDTH,
                padding, true);
        mEndScrubber = mStartScrubber;



        Point screenSize = new Point();
        ((Activity) mContext).getWindowManager().getDefaultDisplay().getRealSize(screenSize);
        MtkLog.v(TAG, "SlowMotionBar() screenSize = " + screenSize);
        int width = Math.min(screenSize.x, screenSize.y);
        width  = width - 2 * padding - speedViewPadding;
        mMiniRatio = (float) PORTRAIT_MINIMUM_SECTION / width;
        MtkLog.v(TAG, "SlowMotionBar() mMiniRatio = " + mMiniRatio);

        if (mContext instanceof MovieActivity) {
            mMovieItem = ((MovieActivity) mContext).getMovieItem();
            mSlowMotionItem = new SlowMotionItem(mContext, mMovieItem.getUri());
            if (!mSlowMotionItem.isSlowMotionVideo()) {
                setVisibility(GONE);
            }
            View rootView = ((Activity) mContext).findViewById(R.id.movie_view_root);
            mVideoView = (MTKVideoView) rootView.findViewById(R.id.surface_view);
        } else {
            setVisibility(GONE);
        }
    }

    private int startScrubberTipOffset() {
        return mStartScrubber.getWidth() * 1 / 2;
    }

    private int endScrubberTipOffset() {
        return mEndScrubber.getWidth() * 1 / 2;
    }

    private int getBarPosFromTime(int time) {
        MtkLog.v(TAG, "getBarPosFromTime time is " + time);
        return mProgressBar.left +
                (int) ((mProgressBar.width() * (long) time) / mTotalTime);
    }

    public void setScrubbing(boolean enable) {
        MtkLog.v(TAG, "setScrubbing(" + enable + ") scrubbing=" + mScrubbing);
        mEnableScrubbing = enable;
    }

    public boolean getScrubbing() {
        MtkLog.v(TAG, "mEnableScrubbing=" + mEnableScrubbing);
        return mEnableScrubbing;
    }

    public void clearInfo() {
        mTotalTime = 0;
        mStartTime = 0;
        mEndTime = 0;
    }

    public void refreshMovieInfo(IMovieItem info) {
        MtkLog.v(TAG, "refreshMovieInfo() info = " + info);
        clearInfo();
        mMovieItem = info;
        if (mSlowMotionItem != null) {
            mSlowMotionItem.updateItemUri(mMovieItem.getUri());
        } else {
            mSlowMotionItem = new SlowMotionItem(mContext, mMovieItem.getUri());
        }
        if (!mSlowMotionItem.isSlowMotionVideo()) {
            setVisibility(GONE);
        } else {
            update();
        }
    }

    public void setSlideVideoTexture(IMtkVideoController texture) {
        mVideoView = texture;
        int position = mVideoView.getCurrentPosition();
        int duration = mVideoView.getDuration();
        MtkLog.v(TAG, "setSlideVideoTexture(" + mStartTime + ", " + mEndTime + ")");
        if (position >= mStartTime && position <= mEndTime) {
            mVideoView.enableSlowMotionSpeed();
        } else {
            mVideoView.disableSlowMotionSpeed();
        }
        mVideoView.setSlowMotionSection(mStartTime + "x" + mEndTime);
    }

    private void updateSlowMotionBar() {
        MtkLog.v(TAG, "updateSlowMotionBar() mTotalTime = " + mTotalTime);
        int duration = 0;
        //get current video total time.
        if (mTotalTime > 0) {
            duration = mTotalTime;
        } else {
            //if mTotalTime is 0, get video duration from db.
            if (mSlowMotionItem != null) {
                duration = mSlowMotionItem.getDuration();
            }
        }

        if (!mScrubbing) {
            mStartScrubberLeft = mProgressBar.left
                    + (int) Math.round(((float) (mProgressBar.width() * (long) mStartTime) / duration)) - endScrubberTipOffset();
            mEndScrubberLeft = mProgressBar.left
                    + (int) Math.round(((float) (mProgressBar.width() * (long) mEndTime) / duration)) - endScrubberTipOffset();
        }

        mSlowMotionLeftBar.set(mProgressBar);
        mSlowMotionLeftBar.right = mStartScrubberLeft + startScrubberTipOffset();
        mSlowMotionRightBar.set(mProgressBar);
        mSlowMotionRightBar.left = mEndScrubberLeft + startScrubberTipOffset();
    }


    private void initTimeIfNeeded() {
        MtkLog.v(TAG, "initTimeIfNeeded() mEndTime " + mEndTime);
        if (mEndTime == 0 && mSlowMotionItem != null) {
            int duration = 0;
            //get current video total time.
            if (mTotalTime > 0) {
                duration = mTotalTime;
            } else {
                //if mTotalTime is 0, get video duration from db.
                duration = mSlowMotionItem.getDuration();
            }
            //Calculate total seconds.
            int totalSeconds =  (int) duration / 1000;
            //get slow motion bar time from db.
            int startTime = 0;
            int endTime = 0;
            // get slow motion section start time.
            startTime = mSlowMotionItem.getSectionStartTime();
            // get slow motion section end time.
            endTime = mSlowMotionItem.getSectionEndTime();
            //if end time is 0, the video is a new one, use default section for it.
            if (endTime == 0) {
                if (totalSeconds >= 3) {
                    //if total seconds >= 3s, the default section is 1/5  slow motion bar in the center.
                    mStartTime = duration * 2 / 5;
                    mEndTime = duration * 3 / 5;
                } else {
                  //if total seconds < 3s, default section is the whole slow motion bar.
                  mStartTime = 0;
                  mEndTime = duration;
                }
                // update calculate result to db.
                if (mSlowMotionItem.isSlowMotionVideo()) {
                    refreshSlowMotionSection(mStartTime, mEndTime);
                }
            } else {
                //if the db has value, use it.
                mStartTime = startTime;
                mEndTime = endTime;
            }
            //update slow motion section to native.
            if (mVideoView != null && mSlowMotionItem.isSlowMotionVideo()) {
                mVideoView.setSlowMotionSection(mStartTime + "x" + mEndTime);
            }
        }
    }

    private void update() {
        MtkLog.v(TAG, "update()");
        initTimeIfNeeded();
        updateSlowMotionBar();
        invalidate();
    }


    public void setTime(int currentTime, int totalTime) {
        if (mSlowMotionItem != null && !mSlowMotionItem.isSlowMotionVideo()) {
            return;
        }
        if (mEndTime == 0) {
            update();
            return;
        }

        //If currentTime is in the slow motion section, should set slow motion speed.
        if (currentTime >= mStartTime && currentTime <= mEndTime) {
            mVideoView.enableSlowMotionSpeed();
        } else {
            mVideoView.disableSlowMotionSpeed();
        }
        mCurrentTime = currentTime;
        mTotalTime = totalTime;
    }

    public boolean setBarVisibility(int visibility) {
        // TODO Auto-generated method stub
        if (MtkVideoFeature.isForceAllVideoAsSlowMotion()) {
            setVisibility(visibility);
            return true;
        }
        if (mSlowMotionItem != null && mSlowMotionItem.isSlowMotionVideo()) {
            //if it is slow motion video
            setVisibility(visibility);
            return true;
        } else {
            //if it is not slow motion video, always not show slow motion bar.
            setVisibility(GONE);
            return false;
        }
    }

    private int whichScrubber(float x, float y) {
        int leftRegion = SCRUBBER_RESPONSE_REGION;
        int rightRegion = 0;

        int distance = mEndScrubberLeft - mStartScrubberLeft - mStartScrubber.getWidth();
        if (distance > 2 * SCRUBBER_RESPONSE_REGION) {
            rightRegion = SCRUBBER_RESPONSE_REGION;
        } else {
            rightRegion = distance / 2;
        }

        if (inScrubber(x, y, mStartScrubberLeft - leftRegion, mStartScrubberTop
                - SCRUBBER_RESPONSE_REGION / 4, mStartScrubberLeft + rightRegion, mStartScrubberTop
                + SCRUBBER_RESPONSE_REGION / 4 + mStartScrubber.getHeight())) {
            return SCRUBBER_START;
        } else if (inScrubber(x, y, mEndScrubberLeft - rightRegion, mEndScrubberTop
                - SCRUBBER_RESPONSE_REGION / 4, mEndScrubberLeft + leftRegion, mEndScrubberTop
                + SCRUBBER_RESPONSE_REGION / 4 + mEndScrubber.getHeight())) {
            return SCRUBBER_END;
        }
        return SCRUBBER_NONE;
    }

    private boolean inScrubber(float x, float y, int startX, int startY, int endX, int endY) {
        return startX < x && x < endX && startY < y && y < endY;
    }

    private int clampScrubber(int scrubberLeft, int offset, int lowerBound, int upperBound) {
        int max = upperBound - offset;
        int min = lowerBound - offset;
        return Math.min(max, Math.max(min, scrubberLeft));
    }

    private int getScrubberTime(int scrubberLeft, int offset) {
        return (int) ((long) (scrubberLeft + offset - mProgressBar.left)
                * mTotalTime / mProgressBar.width());
    }

    private int mOrientation = 0;
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        MtkLog.v(TAG, "onLayout()");
        int w = r - l;
        int h = b - t;
        int progressY = h / 2;

        int ori = mContext.getResources().getConfiguration().orientation;
        MtkLog.v(TAG, "onLayout() l = " + l + " t = " + t + " r = " + r + " b " + b + " ori "
                + ori);
        if (ori != mOrientation) {
            if (ori == Configuration.ORIENTATION_PORTRAIT) {
                mMiniSection = PORTRAIT_MINIMUM_SECTION;
            } else if (ori == Configuration.ORIENTATION_LANDSCAPE) {
                mMiniSection = Math.round(mMiniRatio * (w - 2 * getPaddingLeft()));
                MtkLog.v(TAG, "onLayout() landscape mMiniSection " + mMiniSection);
            }
        }

        mStartScrubberTop = progressY - mStartScrubber.getHeight() / 2;
        mEndScrubberTop = progressY - mEndScrubber.getHeight() / 2;
        mProgressBar.set(getPaddingLeft(), progressY, w - getPaddingRight(), progressY + 4);
        update();
    }


    public int getPreferredHeight() {
        return V_PADDING_IN_DP + mStartScrubber.getHeight();
    }


    public int getBarHeight() {
        return getPreferredHeight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mSlowMotionItem != null && !mSlowMotionItem.isSlowMotionVideo()) {
            return;
        }
        MtkLog.v(TAG, "onDraw()");
        mPath.reset();
        mPath.moveTo(mProgressBar.left, mProgressBar.top + 2);
        mPath.lineTo(mProgressBar.right, mProgressBar.top + 2);

        //draw dashed line.
        canvas.drawPath(mPath, mDashedLinePaint);

        canvas.drawRect(mSlowMotionLeftBar, mRealLinePaint);
        canvas.drawRect(mSlowMotionRightBar, mRealLinePaint);

        canvas.drawBitmap(mStartScrubber, mStartScrubberLeft, mStartScrubberTop, null);
        canvas.drawBitmap(mEndScrubber, mEndScrubberLeft, mEndScrubberTop, null);
    }


    private void updateTimeFromPos() {
        mStartTime = getScrubberTime(mStartScrubberLeft, startScrubberTipOffset());
        mEndTime = getScrubberTime(mEndScrubberLeft, endScrubberTipOffset());
        Log.i(TAG, "updateTimeFromPos startTime " + mStartTime + " endTime " + mEndTime);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        MtkLog.v(TAG, "onTouchEvent() mEnableScrubbing = " + mEnableScrubbing);
        if (mEnableScrubbing) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mPressedThumb = whichScrubber(x, y);
                switch (mPressedThumb) {
                case SCRUBBER_NONE:
                    break;
                case SCRUBBER_START:
                    mScrubbing = true;
                    mScrubberCorrection = x - mStartScrubberLeft;
                    break;
                case SCRUBBER_END:
                    mScrubbing = true;
                    mScrubberCorrection = x - mEndScrubberLeft;
                    break;
                }
                if (mScrubbing == true) {
                    mListener.onScrubbingStart(mCurrentTime, mTotalTime);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mScrubbing) {
                    int seekToTime = -1;
                    int lowerBound = mStartScrubberLeft + startScrubberTipOffset();
                    int upperBound = mEndScrubberLeft + endScrubberTipOffset();
                    switch (mPressedThumb) {
                    case SCRUBBER_START:
                        mStartScrubberLeft = x - mScrubberCorrection;
                        lowerBound = mProgressBar.left;
                        upperBound -= mMiniSection;
                        mStartScrubberLeft = clampScrubber(mStartScrubberLeft,
                                startScrubberTipOffset(), lowerBound, upperBound);
                        seekToTime = getScrubberTime(mStartScrubberLeft, startScrubberTipOffset());
                        break;
                    case SCRUBBER_END:
                        mEndScrubberLeft = x - mScrubberCorrection;
                        lowerBound += mMiniSection;
                        upperBound = mProgressBar.right;
                        mEndScrubberLeft = clampScrubber(mEndScrubberLeft, endScrubberTipOffset(),
                                lowerBound, upperBound);
                        seekToTime = getScrubberTime(mEndScrubberLeft, endScrubberTipOffset());
                        break;
                    }
                    updateTimeFromPos();
                    updateSlowMotionBar();
                    if (seekToTime != -1) {
                        mListener.onScrubbingMove(seekToTime, mTotalTime);
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
                    case SCRUBBER_START:
                        seekToTime = getScrubberTime(mStartScrubberLeft, startScrubberTipOffset());
                        break;
                    case SCRUBBER_END:
                        seekToTime = getScrubberTime(mEndScrubberLeft, endScrubberTipOffset());
                        break;
                    }
                    updateTimeFromPos();
                    mListener.onScrubbingEnd(seekToTime, mTotalTime);
                    mScrubbing = false;
                    mPressedThumb = SCRUBBER_NONE;
//                    update();
                    refreshSlowMotionSection(mStartTime, mEndTime);
                    return true;
                }
                break;
            }
        }
        return true;
    }

    private void refreshSlowMotionSection(final int startTime, final int endTime) {
        if (mSlowMotionItem != null) {
            mSlowMotionItem.updateItemUri(mMovieItem.getUri());
            mSlowMotionItem.setSectionStartTime(startTime);
            mSlowMotionItem.setSectionEndTime(endTime);
            mSlowMotionItem.updateItemToDB();
        }
        //update slow motion section to native.
        if (mVideoView != null) {
            mVideoView.setSlowMotionSection(startTime + "x" + endTime);
        }
    }
}
