package com.mediatek.galleryfeature.mav;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.WindowManager;
import com.mediatek.galleryframework.util.MtkLog;

public class GyroSensorEx {
    private static final String TAG = "MtkGallery2/GyroSensorEx";
    private Context mContext;
    protected SensorManager mSensorManager;
    protected Sensor mGyroSensor;
    protected boolean mHasGyroSensor;
    protected Object mSyncObj = new Object();
    protected GyroPositionListener mListener = null;
    public static final float UNUSABLE_ANGLE_VALUE = -1;
    private PositionListener mPositionListener = new PositionListener();

    public interface GyroPositionListener {
        public void onCalculateAngle(long newTimestamp, float eventValues0,
                float eventValues1, int newRotation);

        public boolean calculate(long newTimestamp, float eventValues0,
                float eventValues1, int newRotation);
    }

    public GyroSensorEx(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) mContext
                .getSystemService(Context.SENSOR_SERVICE);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mHasGyroSensor = (mGyroSensor != null);
        if (!mHasGyroSensor) {
            MtkLog.d(TAG, "<GyroSensorEx>not has gyro sensor");
        }
    }

    private void registerGyroSensorListener() {
        if (mHasGyroSensor) {
            MtkLog.d(TAG, "<registerGyroSensorListener> gyro sensor listener");
            mSensorManager.registerListener(mPositionListener, mGyroSensor,
                    SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void unregisterGyroSensorListener() {
        if (mHasGyroSensor) {
            MtkLog.d(TAG,
                    "<unregisterGyroSensorListener>unregister gyro listener");
            mSensorManager.unregisterListener(mPositionListener);
        }
    }

    public class PositionListener implements SensorEventListener {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            onGyroSensorChanged(event);
        }
    }

    public void setGyroPositionListener(
            GyroPositionListener gyroPositionListener) {
        synchronized (mSyncObj) {
            registerGyroSensorListener();
            mListener = gyroPositionListener;
        }
    }

    public void onGyroSensorChanged(SensorEvent event) {
        synchronized (mSyncObj) {
            if (mListener != null) {
                WindowManager w = (WindowManager) mContext
                        .getSystemService(Context.WINDOW_SERVICE);
                int newRotation = w.getDefaultDisplay().getRotation();
                mListener.calculate(event.timestamp, event.values[0],
                        event.values[1], newRotation);
            }
        }
    }

}
