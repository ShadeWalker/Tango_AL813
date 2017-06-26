package com.mediatek.camera.v2.addition.facedetection;

import android.app.Activity;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import junit.framework.Assert;

public class FdAddition implements IFdControl {

    private static final String TAG = "CAM_APP/FdAddition";
    private static final boolean DEBUG = true;
    private Activity mActivity;
    private static final int ON_FACE_STARTED = 1;
    private static final int ON_FACE_STOPPED =2;


    /**
     * ***********************Notice*********************************
     * mIFdAdditionStatus maybe null,such as user don't want fdAddition notify
     * to view
     */
    private IFdAdditionStatus mIFdAdditionStatus;
    private IFdDevice mIFaceDetectionDevice;
    private FaceDetectionListener mFaceDetectionListener = new FaceDetectionListenerImpl();
    private MainHandler mMainHandler;
    
    public interface FaceDetectionListener {
        //id/landmarks maybe null
        public void onFaceDetected(int[] ids, int[] landmarks, Rect[] rectangles, byte[] scores,
                Point[][] pointsInfo, Rect cropRegion);
    }

    public FdAddition(
            Activity mActivity, IFdDevice fdDevice) {
        Log.i(TAG, "[FaceDetectionMode]");
        this.mActivity = mActivity;
        Assert.assertNotNull(fdDevice);
        mIFaceDetectionDevice = fdDevice;
        mMainHandler = new MainHandler(this.mActivity.getMainLooper());
        mIFaceDetectionDevice.setFaceDetectionListener(mFaceDetectionListener);
    }

    @Override
    public void startFaceDetection() {
        Log.i(TAG, "startFaceDetection ");
        mIFaceDetectionDevice.setFaceDetectionListener(mFaceDetectionListener);
        mIFaceDetectionDevice.startFaceDetection();
        mMainHandler.sendEmptyMessage(ON_FACE_STARTED);
    }


    @Override
    public void stopFaceDetection() {
        Log.i(TAG, "[stopFaceDetection]");
        mIFaceDetectionDevice.stopFaceDetection();
        mMainHandler.sendEmptyMessage(ON_FACE_STOPPED);
    }

    public void setFdAdditionStatusCallback(IFdAdditionStatus callback) {
        if (DEBUG) {
            Log.d(TAG, "setFdAdditionStatusCallback:" + callback);
        }
        mIFdAdditionStatus = callback;
    }

    private class MainHandler extends Handler {
        public MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case ON_FACE_STARTED:
                if (mIFdAdditionStatus != null) {
                    mIFdAdditionStatus.onFdStarted();
                }
                break;
                
            case ON_FACE_STOPPED:
                if (mIFdAdditionStatus != null) {
                    mIFdAdditionStatus.onFdStoped();
                }
                break;
            default:
                break;
            }
        }

    }
    
    private class FaceDetectionListenerImpl implements FaceDetectionListener {
        @Override
        public void onFaceDetected(int[] ids, int[] landmarks, Rect[] rectangles, byte[] scores,
                Point[][] pointsInfo, Rect cropRegion) {
            if (mIFdAdditionStatus != null) {
                mIFdAdditionStatus.onFaceDetected(ids, landmarks, rectangles, scores, pointsInfo, cropRegion);
            }
        }
        
    }
}
