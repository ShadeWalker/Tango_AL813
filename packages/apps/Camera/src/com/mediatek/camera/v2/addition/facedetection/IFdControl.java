package com.mediatek.camera.v2.addition.facedetection;

public interface IFdControl {
    
    /**
     * control the face detection mode,you can close and open
     * @param mode the value can be one of follow:
     * @see #STATISTICS_FACE_DETECT_MODE_OFF
     * @see #STATISTICS_FACE_DETECT_MODE_SIMPLE
     * @see #STATISTICS_FACE_DETECT_MODE_FULL
     */
    
    public void startFaceDetection();
    
    public void stopFaceDetection();
}
