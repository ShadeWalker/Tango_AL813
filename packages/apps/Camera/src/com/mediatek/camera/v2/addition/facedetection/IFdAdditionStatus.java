package com.mediatek.camera.v2.addition.facedetection;

import android.graphics.Point;
import android.graphics.Rect;

public interface IFdAdditionStatus {
    
    public void onFdStarted();
    
    public void onFdStoped();

    public void onFaceDetected(int[] ids, int[] landmarks, Rect[] rectangles, byte[] scores,
            Point[][] pointsInfo, Rect cropRegion);
}
