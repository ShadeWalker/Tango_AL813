package com.mediatek.camera.v2.addition.facedetection;

import com.mediatek.camera.v2.addition.IAdditionCaptureObserver;

public interface IFdDevice {

    public void startFaceDetection();
    
    public void stopFaceDetection();
    
    public void setFaceDetectionListener(FdAddition.FaceDetectionListener listener);
    /**
     * Get CaptureObserver.
     * 
     * @return The IAdditionCaptureObserver used to register
     */
    public IAdditionCaptureObserver getCaptureObserver();
}
