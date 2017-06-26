/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.galleryfeature.container;

public class MotionTrack {
    private static final String TAG = "MtkGallery2/MotionTrack";

    static {
        // The runtime will add "lib" on the front and ".o" on the end of
        // the name supplied to loadLibrary.
        System.loadLibrary("jni_motion_track");
    }

    //motion track JNI api
    static public native void init(String workPath, String prefixName,
            int inImgWidth, int inImgHeight, int inImgNum, int outImgWidth, int outImgHeight);
    static public native int[] getPrevFocusArray();
    static public native int[] getPrevDisableArray();
    static public native int[] getDisableArray(int firstIndex);
    static public native void setManualIndexes(int[] indexs, int number);
    static public native void doBlend();
    static public native void release();
}
