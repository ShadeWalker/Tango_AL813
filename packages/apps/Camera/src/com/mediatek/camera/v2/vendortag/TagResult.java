package com.mediatek.camera.v2.vendortag;

import android.hardware.camera2.CaptureResult.Key;

public class TagResult {
    public static final Key<Integer> STATISTICS_SMILE_MODE = new Key<Integer>(
            "com.mediatek.facefeature.smileshotmode", int.class);
    
    public static final Key<Integer> STATISTICS_SMILE_DETECTED_RESULT = new Key<Integer>(
            "com.mediatek.facefeature.smiledetectresult", int.class);
    
    public static final Key<Integer> STATISTICS_GESTURE_MODE = new Key<Integer>(
            "com.mediatek.facefeature.gesturemode", int.class);
    public static final Key<Integer> STATISTICS_GESTURE_DETECTED_RESULT = new Key<Integer>(
            "com.mediatek.facefeature.gestureresult", int.class);
    
    public static final Key<Integer> STATISTICS_ASD_MODE = new Key<Integer>(
            "com.mediatek.facefeature.asdmode", int.class);
    public static final Key<int[]> STATISTICS_ASD_RESULT = new Key<int[]>(
            "com.mediatek.facefeature.asdresult", int[].class);

}
