package com.mediatek.camera.v2.vendortag;

import android.hardware.camera2.CameraCharacteristics.Key;

public class TagMetadata {

    //gesture shot
   public static final int MTK_FACE_FEATURE_GESTURE_MODE_OFF = 0;
   public static final int MTK_FACE_FEATURE_GESTURE_MODE_SIMPLE = 1;

   //smile shot
   public static final int MTK_FACE_FEATURE_SMILE_MODE_OFF = 0;
   public static final int MTK_FACE_FEATURE_SMILE_MODE_SIMPLE = 1;

   //ASD
   public static final int MTK_FACE_FEATURE_ASD_MODE_OFF = 0;
   public static final int MTK_FACE_FEATURE_ASD_MODE_SIMPLE = 1;
   //full will return HDR info
   public static final int MTK_FACE_FEATURE_ASD_MODE_FULL = 2;

   public static final Key<int[]> GESTURE_AVAILABLE_MODES =
           new Key<int[]>("com.mediatek.facefeature.availablegesturemodes", int[].class);
           
   public static final Key<int[]> SMILE_AVAILABLE_MODES =
           new Key<int[]>("com.mediatek.facefeature.availablesmiledetectmodes", int[].class);
                   
   public static final Key<int[]> ASD_AVAILABLE_MODES =
           new Key<int[]>("com.mediatek.facefeature.availableasdmodes", int[].class);

}
