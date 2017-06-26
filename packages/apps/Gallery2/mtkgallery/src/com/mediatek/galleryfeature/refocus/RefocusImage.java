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

import java.io.BufferedOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.text.SimpleDateFormat;

import com.mediatek.galleryframework.util.Utils;
import com.mediatek.galleryframework.util.MtkLog;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

public class RefocusImage {
    private static final String TAG = "Gallery2/Refocus/RefocusImage";
    private final Bitmap.Config mConfig = Bitmap.Config.ARGB_8888;
    private static final String TIME_STAMP_NAME = "'IMG'_yyyyMMdd_HHmmss";

    private final Activity mActivity;
    private Bitmap mBitmap = null;
    private ByteBuffer mBitmapBuf;
    private int width;
    private int height;
    private int mDepthBufferWidth;
    private int mDepthBufferHeight;
    private byte[] mDepthBuffer= null;

    // In order to improve loading and generate performance, Image will be rescale size
    public static int INSAMPLESIZE = 2;
    public RefocusImage(Activity activity){
        mActivity = activity;
    }
    
    public boolean initRefocusImage(String targetFilePath, int inTargetImgWidth, int inTargetImgHeight){
        width = inTargetImgWidth / INSAMPLESIZE;
        height = inTargetImgHeight / INSAMPLESIZE;
        XmpUtils.initialize(targetFilePath);
        Log.d(TAG, "initRefocusImage targetFilePath " + targetFilePath + " width: " + width + " height " + height);
        XmpUtils.JpsConfigInfo jpsConfigInfo = XmpUtils.getJpsConfigInfoFromFile(targetFilePath); 
        Log.d(TAG, "jpsConfigInfo:" + jpsConfigInfo.toString());
        RefocusImageJNI.imageRefocus(jpsConfigInfo.jpsWidth, jpsConfigInfo.jpsHeight, jpsConfigInfo.maskWidth, jpsConfigInfo.maskHeight, 
                jpsConfigInfo.posX, jpsConfigInfo.posY, jpsConfigInfo.viewWidth, jpsConfigInfo.viewHeight, jpsConfigInfo.orientation,
                jpsConfigInfo.mainCamPos, jpsConfigInfo.touchCoordX1st, jpsConfigInfo.touchCoordY1st);
        byte [] jpsBuffer = XmpUtils.getJpsDataFromJpgFile(targetFilePath);
        int jpsBufferSize = 0;
        if (jpsBuffer != null) {
            jpsBufferSize = jpsBuffer.length;
        }
        Log.d(TAG, "jpsBufferSize:" + jpsBufferSize);
        byte [] maskBuffer = XmpUtils.getJpsMaskFromJpgFile(targetFilePath);
        int maskBufferSize = 0;
        if (maskBuffer != null) {
            maskBufferSize = maskBuffer.length;
        }
        Log.d(TAG, "maskBufferSize:" + maskBufferSize);
        XmpUtils.DepthBufferInfo depthBufferInfo = XmpUtils.getDepthBufferInfoFromFile(targetFilePath);
        byte [] depthBuffer = null;
        byte [] xmpDepthBuffer = null;
        if (depthBufferInfo != null) {
            depthBuffer = depthBufferInfo.depthData;
            xmpDepthBuffer = depthBufferInfo.xmpDepthData;
            setDepBufHeight(depthBufferInfo.depthBufferHeight);
            setDepBufWidth(depthBufferInfo.depthBufferWidth);
            setDepthBuffer(depthBuffer);
        }
        if (depthBuffer == null || (new File(Environment.getExternalStorageDirectory(),
                "FORCEORIBUFFER")).exists()) {
            Log.d(TAG, "initRefocusNoDepthMap");
            boolean initResult = RefocusImageJNI.initRefocusNoDepthMap(targetFilePath, width, height, jpsConfigInfo.orientation,
                    jpsBuffer, jpsBufferSize, jpsConfigInfo.jpsWidth, jpsConfigInfo.jpsHeight, 
                    maskBuffer, maskBufferSize, jpsConfigInfo.maskWidth, jpsConfigInfo.maskHeight);
            if (!initResult) {
                Log.d(TAG, "<initRefocusImage> initRefocusNoDepthMap error!!");
                return false;
            }
            int depthBufferSize = RefocusImageJNI.getDepthBufferSize();
            int xmpDepthBufferSize = RefocusImageJNI.getXMPDepthBufferSize();
            Log.d(TAG, "depthBufferSize " + depthBufferSize + " xmpDepthBufferSize " + xmpDepthBufferSize);
            int depthBufferWidth = RefocusImageJNI.getDepthBufferWidth();
            int depthBufferHeight = RefocusImageJNI.getDepthBufferHeight();
            int xmpDepthBufferWidth = RefocusImageJNI.getXMPDepthBufferWidth();
            int xmpDepthBufferHeight = RefocusImageJNI.getXMPDepthBufferHeight();
            Log.d(TAG, "depthBufferWidth " + depthBufferWidth + " depthBufferHeight " + depthBufferHeight);
            Log.d(TAG, "xmpDepthBufferWidth " + xmpDepthBufferWidth + " xmpDepthBufferHeight " + xmpDepthBufferHeight);
            if (depthBufferSize < 0) depthBufferSize = 0;
            if (xmpDepthBufferSize < 0) xmpDepthBufferSize = 0;
            byte[] byteArray = new byte[depthBufferSize];
            byte[] xmpByteArray = new byte[xmpDepthBufferSize];
            XmpUtils.DepthBufferInfo generateDepthBufferInfo = new XmpUtils.DepthBufferInfo();
            RefocusImageJNI.saveDepthMapInfo(byteArray, xmpByteArray);
            generateDepthBufferInfo.depthData = byteArray;
            generateDepthBufferInfo.xmpDepthData = xmpByteArray;
            generateDepthBufferInfo.depthBufferHeight = depthBufferHeight;
            generateDepthBufferInfo.depthBufferWidth = depthBufferWidth;
            generateDepthBufferInfo.xmpDepthHeight = xmpDepthBufferHeight;
            generateDepthBufferInfo.xmpDepthWidth = xmpDepthBufferWidth;
            XmpUtils.writeDepthBufferToJpg(targetFilePath, generateDepthBufferInfo, true);
            setDepBufWidth(depthBufferWidth);
            setDepBufHeight(depthBufferHeight);
            setDepthBuffer(byteArray);
        } else {
            Log.d(TAG, "initRefocusDepthMap");
            RefocusImageJNI.initRefocusWithDepthMap(targetFilePath, width, height, jpsConfigInfo.orientation, 
                    depthBuffer, depthBuffer.length, jpsConfigInfo.jpsWidth, jpsConfigInfo.jpsHeight);
        }
        Log.d(TAG, "initRefocusImage end");
        return true;
    }
    
    public void setDepthBuffer(byte[] depthBuffer) {
        mDepthBuffer = depthBuffer;
    }
    
    public void setDepBufWidth(int depBufWidth) {
        mDepthBufferWidth = depBufWidth;
    }
    public void setDepBufHeight(int depBufHeight) {
        mDepthBufferHeight = depBufHeight;
    }
    
    public byte[] getDepthBuffer() {
        return mDepthBuffer;
    }
    
    public int getDepBufWidth() {
        return mDepthBufferWidth;
    }
    public int getDepBufHeight() {
        return mDepthBufferHeight;
    }
    
    public void generateRefocusImage(int xCoord, int yCoord, int depthofFiled) {
        xCoord = xCoord / INSAMPLESIZE;
        yCoord = yCoord / INSAMPLESIZE;
        long createBmpStart = System.currentTimeMillis();
        int length = width * height * 4;
        mBitmapBuf = ByteBuffer.allocate(length);
        long ssCreatBmp = System.currentTimeMillis();
        mBitmap = Bitmap.createBitmap(width, height, mConfig);
        MtkLog.i(TAG,"ssCreatBmp Time =    " + (System.currentTimeMillis() - ssCreatBmp) + "|| width = " + width + "  *  height = " + height);
        //MtkUtils.dumpBitmap(mBitmap,"no depthofFiled Bitmap = ");
        byte [] byteArray = new byte[length];
        MtkLog.i(TAG, "Performance Create Bitmap Time =    " + (System.currentTimeMillis() - createBmpStart));
        Log.d(TAG, "generateRefocusImage,begin  x " + xCoord + " y " + yCoord + " depthoffiled " + depthofFiled);
        long generStartTime = System.currentTimeMillis();
        RefocusImageJNI.generateRefocusImage(byteArray, xCoord, yCoord, depthofFiled);
        MtkLog.i(TAG,"Performance  RefocusImageJNI.generateRefocusImage pend time =     " + (System.currentTimeMillis() - generStartTime));
        ///BGRA  -> RGBA
        /*for(int i=0; i<length/4; i++) {
            byte temp = byteArray[i*4];
            byteArray[i*4] = byteArray[i*4+2];
            byteArray[i*4+2] = temp;
        }*/
        Log.d(TAG, "generateRefocusImage byteArray start");
        long bmpRewindStart = System.currentTimeMillis();
        
        mBitmapBuf.put(byteArray);
        mBitmapBuf.rewind();
        mBitmap.copyPixelsFromBuffer(mBitmapBuf);
        
        MtkLog.i(TAG,"Performance bmpRewind Spend Time =     " + (System.currentTimeMillis() - bmpRewindStart));
        Log.d(TAG, "mBitmap " + mBitmap);
        //MtkUtils.dumpBitmap(mBitmap, "imagerefocus");
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void saveRefocusDepthBuffer() {
        int depthBufferSize = RefocusImageJNI.getDepthBufferSize();
        int xmpDepthBufferSize = RefocusImageJNI.getXMPDepthBufferSize();
        Log.d(TAG, "depthBufferSize " + depthBufferSize + " xmpDepthBufferSize " + xmpDepthBufferSize);
        byte[] byteArray = new byte[depthBufferSize];
        byte[] xmpByteArray = new byte[xmpDepthBufferSize];
        RefocusImageJNI.saveDepthMapInfo(byteArray, xmpByteArray);
        RefocusHelper.saveDepthBufferToFileForTest(byteArray, RefocusHelper.DEPTH_PATH);
        RefocusHelper.saveDepthBufferToFileForTest(xmpByteArray, RefocusHelper.XMP_DEPTH_PATH);
    }

    public Uri saveRefocusImage(Uri sourceUri) {
        //saveRefocusDepthBuffer();
        String filename = new SimpleDateFormat(TIME_STAMP_NAME).format(new Date(
                System.currentTimeMillis()));
        File file = RefocusHelper.getNewFile(mActivity.getApplicationContext(), sourceUri, filename);
        RefocusImageJNI.saveRefocusImage(file.getAbsolutePath(), INSAMPLESIZE);
        return RefocusHelper.insertContent(mActivity.getApplicationContext(), sourceUri, file, filename);
 
    }

    public void refocusRelease() {
        Log.i(TAG, "refocusRelease");
        XmpUtils.deInitialize();
        RefocusImageJNI.release();
    }
}
