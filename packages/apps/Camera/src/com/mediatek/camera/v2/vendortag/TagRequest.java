package com.mediatek.camera.v2.vendortag;

import android.hardware.camera2.CaptureRequest.Key;

public class TagRequest {
    
    public static final Key<Integer> STATISTICS_SMILE_MODE = new Key<Integer>(
            "com.mediatek.facefeature.smiledetectmode", int.class);
    
    public static final Key<Integer> STATISTICS_GESTURE_MODE = new Key<Integer>(
            "com.mediatek.facefeature.gesturemode", int.class);
    
    public static final Key<Integer> STATISTICS_ASD_MODE = new Key<Integer>(
            "com.mediatek.facefeature.asdmode", int.class);
}
