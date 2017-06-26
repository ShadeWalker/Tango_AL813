package com.mediatek.galleryfeature.mav;

import com.mediatek.galleryfeature.mav.GyroSensorEx.GyroPositionListener;
import android.content.Context;
import android.os.Process;
import com.mediatek.galleryframework.util.MtkLog;
import android.view.Surface;

public class RenderThreadEx extends Thread implements GyroPositionListener {
    private static final String TAG = "MtkGallery2/RenderThreadEx";
    // private Animation mAnimation= null;
    private OnDrawMavFrameListener mOnDrawMavFrameListener = null;
    private Object mRenderLock = new Object();
    public boolean mRenderRequested = false;
    public boolean mIsActive = true;
    public static final int CONTINUOUS_FRAME_ANIMATION_CHANGE_THRESHOLD = 1;
    private GyroSensorEx mGyroSensor = null;
    private Context mContext;
    public static int TYPE = 1;

    private int mOrientation = -1;
    private float mValue = 0;
    private long mTimestamp = 0;
    private float mAngle[] = { 0, 0, 0 };
    private boolean mFirstTime = true;
    private static int mLastIndex = 0xFFFF;
    public static final float BASE_ANGLE = 6.5f;
    public static final float NS2S = 1.0f / 1000000000.0f;
    public static final float TH = 0.001f;
    public static final float OFFSET = 0.0f;
    public static final float UNUSABLE_ANGLE_VALUE = -1;
    long mNewTimestamp;
    float mEventValues0;
    float mEventValues1;
    int mNewRotation;

    public interface OnDrawMavFrameListener {
        public boolean advanceAnimation(int targetFrame, int type);
        public void drawMavFrame();
        public void initAnimation(int targetFrame, int type);
        public void changeAnimationType();
        public int getSleepTime();
        public int getFrameCount();
    }

    public RenderThreadEx(Context context, GyroSensorEx gyroSensor) {
        super("MavRenderThread");
        mContext = context;
        mGyroSensor = gyroSensor;
        if (mGyroSensor != null) {
            mGyroSensor.setGyroPositionListener(this);
        }
    }

    public void setRenderRequester(boolean request) {
        synchronized (mRenderLock) {
            mRenderRequested = request;
            mRenderLock.notifyAll();
        }
    }

    public void quit() {
        mIsActive = false;
        if (mGyroSensor != null) {
            mGyroSensor.unregisterGyroSensorListener();
            mGyroSensor = null;
        }
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        while (mIsActive && !Thread.currentThread().isInterrupted()) {
            MtkLog.d(TAG, "<run>~~~~~~~~~~~~~~~~~"
                    + Thread.currentThread().getId() + "    mRenderRequested=="
                    + mRenderRequested + " mContext=" + mContext);
            if (!mIsActive) {
                MtkLog.v(TAG, "<run>MavRenderThread:run: exit MavRenderThread");
                return;
            }
            boolean isFinished = false;
            synchronized (mRenderLock) {
                if (mRenderRequested && mOnDrawMavFrameListener != null) {
                    isFinished = mOnDrawMavFrameListener.advanceAnimation(
                            mLastIndex, AnimationEx.TYPE_ANIMATION_CONTINUOUS);
                    mRenderRequested = (!isFinished) ? true : false;
                    mOnDrawMavFrameListener.drawMavFrame();
                    mOnDrawMavFrameListener.changeAnimationType();
                } else {
                    try {
                        mRenderLock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
            if (!isFinished) {
                try {
                    if (mOnDrawMavFrameListener != null) {
                        Thread.sleep(mOnDrawMavFrameListener.getSleepTime());
                    }
                } catch (InterruptedException e) {
                    MtkLog.e(TAG, " <run> sleep have InterruptedException:" + e.getMessage());
                }
            }
        }
    }

    public void setOnDrawMavFrameListener(OnDrawMavFrameListener lisenter) {
        mOnDrawMavFrameListener = lisenter;
    }

    public boolean calculate(long mNewTimestamp, float eventValues0,
            float eventValues1, int newRotation) {
        // workaround for Gyro sensor HW limitation.
        // As sensor continues to report small movement, wrongly
        // indicating that the phone is slowly moving, we should
        // filter the small movement.
        final float xSmallRotateTH = 0.05f;
        // xSmallRotateTH indicating the threshold of max "small
        // rotation". This varible is determined by experiments
        // based on MT6575 platform. May be adjusted on other chips.
        float valueToUse = 0;
        if (mOrientation != newRotation) {
            // orientation has changed, reset calculations
            mOrientation = newRotation;
            mValue = 0;
            mAngle[0] = 0;
            mAngle[1] = 0;
            mAngle[2] = 0;
            mFirstTime = true;
        }
        switch (mOrientation) {
        case Surface.ROTATION_0:
            valueToUse = eventValues1;
            break;
        case Surface.ROTATION_90:
            // no need to re-map
            valueToUse = eventValues0;
            break;
        case Surface.ROTATION_180:
            // we do not have this rotation on our device
            valueToUse = -eventValues1;
            break;
        case Surface.ROTATION_270:
            valueToUse = -eventValues0;
            break;
        default:
            valueToUse = eventValues0;
        }
        mValue = valueToUse + OFFSET;
        if (mTimestamp != 0 && Math.abs(mValue) > TH) {
            final float dT = (mNewTimestamp - mTimestamp) * NS2S;

            mAngle[1] += mValue * dT * 180 / Math.PI;
            if (mFirstTime) {
                mAngle[0] = mAngle[1] - BASE_ANGLE;
                mAngle[2] = mAngle[1] + BASE_ANGLE;
                mFirstTime = false;
            } else if (mAngle[1] <= mAngle[0]) {
                mAngle[0] = mAngle[1];
                mAngle[2] = mAngle[0] + 2 * BASE_ANGLE;
            } else if (mAngle[1] >= mAngle[2]) {
                mAngle[2] = mAngle[1];
                mAngle[0] = mAngle[2] - 2 * BASE_ANGLE;
            }
        }
        float angle;
        if (mTimestamp != 0 && mOnDrawMavFrameListener != null && mOnDrawMavFrameListener.getFrameCount() != 0) {
            angle = mAngle[1] - mAngle[0];
        } else {
            angle = UNUSABLE_ANGLE_VALUE;
        }
        mTimestamp = mNewTimestamp;

        if (angle != UNUSABLE_ANGLE_VALUE) {
            return onGyroPositionChanged(angle);
        } else {
            return false;
        }
    }

    public void onCalculateAngle(long newTimestamp, float eventValues0,
            float eventValues1, int newRotation) {
        this.mNewTimestamp = newTimestamp;
        this.mEventValues0 = eventValues0;
        this.mEventValues1 = eventValues1;
        this.mNewRotation = newRotation;
    }

    public boolean onGyroPositionChanged(float angle) {
        boolean isChanged = false;
        if (mOnDrawMavFrameListener != null && mOnDrawMavFrameListener.getFrameCount() != 0) {
            int index = (int) (angle * mOnDrawMavFrameListener.getFrameCount() / (2 * BASE_ANGLE));
            if (index >= 0 && index < mOnDrawMavFrameListener.getFrameCount()) {
                if ((mLastIndex == 0xFFFF || mLastIndex != index)) {
                    if (MavPlayer.sRenderThreadEx != null)
                        MtkLog.d(TAG, "<onGyroPositionChanged> index==" + index
                                + " mLastIndex=" + mLastIndex + " size= "
                                + MavPlayer.sHashSet.size());
                    if (mOnDrawMavFrameListener != null && mLastIndex != 0xFFFF
                            && Math.abs(mLastIndex - index) > CONTINUOUS_FRAME_ANIMATION_CHANGE_THRESHOLD) {
                        mOnDrawMavFrameListener.initAnimation(index,
                                AnimationEx.TYPE_ANIMATION_CONTINUOUS);
                    }
                    mLastIndex = index;
                    isChanged = true;
                }
            }
        }
        return isChanged;
    }
}
