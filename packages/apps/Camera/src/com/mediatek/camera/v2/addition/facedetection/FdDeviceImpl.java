package com.mediatek.camera.v2.addition.facedetection;


import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Builder;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.util.Log;

import com.mediatek.camera.v2.addition.IAdditionCaptureObserver;
import com.mediatek.camera.v2.addition.IAdditionManager.IAdditionListener;
import com.mediatek.camera.v2.addition.facedetection.FdAddition.FaceDetectionListener;
import com.mediatek.camera.v2.module.ModuleListener;
import com.mediatek.camera.v2.module.ModuleListener.CaptureType;
import com.mediatek.camera.v2.module.ModuleListener.RequestType;

import junit.framework.Assert;


public class FdDeviceImpl implements IFdDevice{
    private static final String TAG = "CAM_APP/FdDeviceImpl";
    private static final boolean DEBUG = true;
    
    private FaceDetectionListener mListener;
    private IAdditionListener mAdditionListener;
    
    private CaptureObserver mCaptureObserver = new CaptureObserver();
    private boolean         mIsFdRequestOpen = false;
    
    
    public FdDeviceImpl(IAdditionListener additionListener) {
        mAdditionListener = additionListener;
    }
    
    @Override
    public IAdditionCaptureObserver getCaptureObserver() {
        return mCaptureObserver;
    }
    
    @Override
    public void startFaceDetection() {
        Log.i(TAG, "startFaceDetection");
        mIsFdRequestOpen = true;
        mAdditionListener.requestChangeCaptureRequets(false,mAdditionListener.getRepeatingRequestType(), CaptureType.REPEATING_REQUEST);
    }

    @Override
    public void stopFaceDetection() {
        Log.i(TAG, "stopFaceDetection");
        mIsFdRequestOpen = false;
        mAdditionListener.requestChangeCaptureRequets(false,mAdditionListener.getRepeatingRequestType(), CaptureType.REPEATING_REQUEST);
    }

    @Override
    public void setFaceDetectionListener(FaceDetectionListener listener) {
        mListener = listener; 
    }

    private class CaptureObserver implements IAdditionCaptureObserver {

        @Override
        public void configuringRequests(CaptureRequest.Builder requestBuilder, RequestType requestType) {
            int fdMode = mIsFdRequestOpen? CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE:
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
            requestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,fdMode);
            Log.i(TAG, "configuringRequests done,fdMode = " + fdMode);
        }

        @Override
        public void onCaptureStarted(CaptureRequest request, long timestamp, long frameNumber) {
        }

        @Override
        public void onCaptureCompleted(CaptureRequest request, TotalCaptureResult result) {
            Assert.assertNotNull(result);
            Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
            int length = faces.length;
            int[] ids = result.get(CaptureResult.STATISTICS_FACE_IDS);
            int[] landmarks = result.get(CaptureResult.STATISTICS_FACE_LANDMARKS);
            Rect[] rectangles = result.get(CaptureResult.STATISTICS_FACE_RECTANGLES);
            byte[] scores = result.get(CaptureResult.STATISTICS_FACE_SCORES);
            Point[][] pointsInfo = new Point[length][3];
            Rect cropRegion = result.get(CaptureResult.SCALER_CROP_REGION);
            Log.i(TAG, "[onCaptureCompleted] faces's length = " + length);

            if (length != 0) {
                for (int i = 0; i < length; i++) {
                    pointsInfo[i][0] = faces[i].getLeftEyePosition();
                    pointsInfo[i][1] = faces[i].getRightEyePosition();
                    pointsInfo[i][2] = faces[i].getMouthPosition();
//                    Log.i(TAG, "pointsInfo[" + i + "][0]= " + pointsInfo[i][0]);
//                    Log.i(TAG, "pointsInfo[" + i + "][1]= " + pointsInfo[i][1]);
//                    Log.i(TAG, "pointsInfo[" + i + "][2]= " + pointsInfo[i][2]);
                }
            }

            if (DEBUG
                    && result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE) == CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL) {
                for (int i = 0; i < ids.length; i++) {
                    Log.i(TAG, "id[" + i + "]= " + ids[i]);
                }

                for (int i = 0; i < landmarks.length; i++) {
                    Log.i(TAG, "landmarks[" + i + "]= " + landmarks[i]);
                }

            }
            if (DEBUG && rectangles != null) {
                for (int i = 0; i < rectangles.length; i++) {
                    Log.i(TAG, "rectangles[" + i + "]= " + rectangles[i]);
                }
            }

            if (DEBUG && scores != null) {
                for (int i = 0; i < scores.length; i++) {
                    Log.i(TAG, "scores[" + i + "]= " + scores[i]);
                }
            }

            //notify to FdAddition
            Log.i(TAG, "mListener " + mListener);
            mListener.onFaceDetected(ids, landmarks, rectangles, scores, pointsInfo, cropRegion);
        }
    }
}
