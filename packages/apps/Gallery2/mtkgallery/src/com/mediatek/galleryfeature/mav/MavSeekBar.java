package com.mediatek.galleryfeature.mav;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.widget.SeekBar;

import com.android.gallery3d.R;
import com.mediatek.galleryframework.util.MtkLog;

/**
 * The MavSeekBar is used to indicates loading progress and change view angle
 */
public class MavSeekBar extends SeekBar {

    private static final String TAG = "MtkGallery2/MavSeekBar";

    // current state of MavSeekBar
    // if the state is loading, it like a progress bar, no thumb, and unable
    // seek
    // if the state is sliding, it like a seek bar, show thumb, and enable seek
    public static final int STATE_LOADING = 0;
    public static final int STATE_SLIDING = 1;

    private static final int ALPHA_STEP = 10;
    private static final int DELAY_TIME = 1;
    private static final int ALPHA_MAX = 255;
    private int mState;
    private Drawable mThumb;
    private Drawable mProgressDrawableLoading;
    private Drawable mProgressDrawableSliding;
    public static final int INVALID_PROCESS = -1;
    Context mContext = null;
    private static final int MSG_UPDATE_THUMB_APHPA = 0;
    private int mAlpha = 0;

    public MavSeekBar(Context context) {
        super(context);
        MtkLog.v(TAG, "<MavSeekBar>constructor #1 called");
        mContext = context;
        initializeDrawable();
        init();
    }

    public MavSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        MtkLog.v(TAG, "<MavSeekBar>constructor #2 called");
        mContext = context;
        initializeDrawable();
        init();
    }

    public MavSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        MtkLog.v(TAG, "<MavSeekBar>constructor #3 called");
        mContext = context;
        initializeDrawable();
        init();
    }

    private void setState(int state) {
        mState = state;
        if (mState == STATE_LOADING) {
            MtkLog.v(TAG, "<setState>set MavSeekBar state as STATE_LOADING");
            // first remove "MSG_UPDATE_THUMB_APHPA" from message queue
            // to avoid MavSeekBbar display abnormally
            mHander.removeMessages(MSG_UPDATE_THUMB_APHPA);
            // hide slide thumb
            mThumb.setAlpha(0);
            setThumb(mThumb);
            setProgress(0);
            setProgressDrawable(mProgressDrawableLoading);
            setEnabled(false);
        } else if (mState == STATE_SLIDING) {
            MtkLog.d(TAG, "<setState>set MavSeekBar state as STATE_SLIDING");
            setProgress(getMax() / 2);
            // show slide thumb
            // mThumb.setAlpha(255);
            showThumb();
            // setThumb(mThumb);
            // to avoid NullPointException
            setProgressDrawable(mProgressDrawableSliding);
            setEnabled(true);
        }
    }

    // int mAlpha = 0;
    public void showThumb() {
        mAlpha = 0;
        mHander.sendEmptyMessage(MSG_UPDATE_THUMB_APHPA);
    }

    private Handler mHander = new Handler() {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_THUMB_APHPA:
                if (mThumb == null)
                    return;
                mThumb.setAlpha(mAlpha);
                setThumb(mThumb);
                mAlpha += ALPHA_STEP;
                if (mAlpha > ALPHA_MAX)
                    return;
                Message newMsg = obtainMessage(MSG_UPDATE_THUMB_APHPA);
                mHander.sendMessageDelayed(newMsg, DELAY_TIME);
                break;
            default:
                throw new AssertionError(msg.what);
            }
        }
    };

    public void setHandler(Handler handler) {
        mHander = handler;
    }

    @Override
    public synchronized void setProgress(int progress) {
        super.setProgress(progress == INVALID_PROCESS ? 0 : progress);
        if (mState == STATE_LOADING && progress >= getMax()) {
            MtkLog.d("TAG", "<setState>enter sliding mode, state: " + mState
                    + ", max: " + getMax() + ", progress: " + progress);
            setState(STATE_SLIDING);
        } else if (mState == STATE_SLIDING && progress == INVALID_PROCESS) {
            init();
        }
    }

    public void syncProgressByGyroSensor(int progress) {
        super.setProgress(progress);
    }

    public int getState() {
        return mState;
    }

    private void init() {
        setState(STATE_LOADING);
    }

    /**
     * restore default state
     */
    public void restore() {
        init();
    }

    private void initializeDrawable() {
        mThumb = getResources().getDrawable(
                R.drawable.m_mavseekbar_control_selector);
        mProgressDrawableLoading = getResources().getDrawable(
                R.drawable.m_mavseekbar_progress_loading);
        mProgressDrawableSliding = getResources().getDrawable(
                R.drawable.m_mavseekbar_progress_sliding);
        this.setSplitTrack(false);
    }

    @Override
    public void setVisibility(int v) {
        super.setVisibility(v);
    }

}
