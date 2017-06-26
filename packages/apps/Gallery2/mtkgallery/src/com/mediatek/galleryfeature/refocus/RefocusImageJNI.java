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

package com.mediatek.galleryfeature.refocus;

class RefocusImageJNI {

    static {
        // The runtime will add "lib" on the front and ".o" on the end of
        // the name supplied to loadLibrary.
        System.loadLibrary("jni_image_refocus");
    }

    //image refocus JNI api
    static native void imageRefocus(int jpsWidth, int jpsHeight, int maskWidth, int maskHeight, int posX, int posY, 
            int viewWidth, int viewHeight, int orientation, int mainCamPos, int touchCoordX1st, int touchCoordY1st);
    static native boolean initRefocusNoDepthMapTest(String targetFilePath, String depthmapSourcePath, String maskFilePath, int outImgWidth, int outImgHeight, int imgOrientation,
             int inStereoImgWidth, int inStereoImgHeight);
    static native boolean initRefocusNoDepthMapRealFileTest(String testSourceFilePath, String sourceFilePath, String jpsFilePath, int outImgWidth, int outImgHeight, int imgOrientation,
            byte[] jpsBuffer, int jpsBufferSize, int inStereoImgWidth, int inStereoImgHeight, 
            byte[] maskBuffer, int maskBufferSize, int maskWidth, int maskHeight);
    static native boolean initRefocusNoDepthMap(String sourceFilePath, int outImgWidth, int outImgHeight, int imgOrientation,
            byte[] jpsBuffer, int jpsBufferSize, int inStereoImgWidth, int inStereoImgHeight, 
            byte[] maskBuffer, int maskBufferSize, int maskWidth, int maskHeight);
    static native boolean initRefocusWithDepthMap(String targetFilePath, int outImgWidth, int outImgHeight, int imgOrientation,
            byte[] depthMapBuffer, int depthMapBufferSize, int inStereoImgWidth, int inStereoImgHeight);
    static native boolean generateRefocusImage(byte[] resultBuffer, int touchX, int touchY, int depthOfField);
    static native int getDepthBufferSize();
    static native int getDepthBufferWidth();
    static native int getDepthBufferHeight();
    static native int getXMPDepthBufferSize();
    static native int getXMPDepthBufferWidth();
    static native int getXMPDepthBufferHeight();
    static native void saveDepthMapInfo(byte[] depthBuffer, byte[] xmpDepthBuffer);
    static native void saveRefocusImage(String saveFileName, int inSampleSize);
    static native void release();
}
