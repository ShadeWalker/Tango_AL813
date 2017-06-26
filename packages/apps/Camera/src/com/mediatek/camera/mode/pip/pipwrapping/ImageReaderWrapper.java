/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2014. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */
package com.mediatek.camera.mode.pip.pipwrapping;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.os.ConditionVariable;
import android.os.Debug.MemoryInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ImageReaderWrapper {
    private static final String TAG = "ImageReaderWrapper";
    public static final short SOI = (short) 0xFFD8;
    public static final short EOI = (short) 0xFFD9;
    public static final short APP0 = (short) 0xFFE0;
    public static final short APP1 = (short) 0xFFE1;
    public static final short APP2 = (short) 0xFFE2;
    public static final short APP3 = (short) 0xFFE3;
    public static final short APP4 = (short) 0xFFE4;
    public static final short APP5 = (short) 0xFFE5;
    public static final short APP6 = (short) 0xFFE6;
    public static final short APP7 = (short) 0xFFE7;
    public static final short APP8 = (short) 0xFFE8;
    public static final short APP9 = (short) 0xFFE9;
    public static final short APP10 = (short) 0xFFEA;
    public static final short APP11 = (short) 0xFFEB;
    public static final short APP12 = (short) 0xFFEC;
    public static final short APP13 = (short) 0xFFED;
    public static final short APP14 = (short) 0xFFEE;
    public static final short APP15 = (short) 0xFFEF;
    /**
     * SOF (start of frame). All value between SOF0 and SOF15 is SOF marker
     * except for DHT, JPG, and DAC marker.
     */
    public static final short SOF0 = (short) 0xFFC0;
    public static final short SOF15 = (short) 0xFFCF;
    public static final short DHT = (short) 0xFFC4;
    public static final short JPG = (short) 0xFFC8;
    public static final short DAC = (short) 0xFFCC;
    
    private static final int TOTAL_PICTURE_BUFFER = 2;
    private HandlerThread mImageHandlerThread;
    private ImageReader mImageReader;
    private Surface mSurface;
    private int mPictureBufferAdded = 0;
    private Bitmap mPictureBottomBitmap;
    private int mPictureBottomWidth;
    private int mPictureBottomHeight;
    private Bitmap mPictureTopBitmap;
    private int mPictureTopWidth;
    private int mPictureTopHeight;
    private ImageReaderCallback mPIPPictureCallback;
    private Object mImageReaderSynObject = new Object();
    // when exit pip mode, release will be called,
    // should wait capture complete, 5000ms timeout
    private ConditionVariable mReleaseConditionVariable = new ConditionVariable();
    // when do take picture continuously, should wait previous capture end
    private ConditionVariable mCaptureConditionVariable = new ConditionVariable();
    
    // background process jpeg decode
    private HandlerThread                   mImageProcessThread;
    private Handler                         mImageProcessHandler;
    private ConcurrentLinkedQueue<byte[]>   mJpegHeaderList
                            = new ConcurrentLinkedQueue<byte[]>();

    public interface ImageReaderCallback {
        void onPIPPictureTaken(byte[] jpegData);
        void canDoStartPreview();
        RendererManager getRendererManager();
        AnimationRect getPreviewAnimationRect();
    }

    public ImageReaderWrapper(Context context, ImageReaderCallback listener) {
        mPIPPictureCallback = listener;
    }

    // take video snap shot
    public void takeVideoSnapshot(int width, int height) {
        Log.d(TAG, "takeVideoSnapshot width = " + width + " height = " + height);
        setUpImageReaderRelated(width, height);
    }

    public Surface getSurface() {
        Log.d(TAG, "getSurface mSurface " + mSurface);
        synchronized (mImageReaderSynObject) {
            return mSurface;
        }
    }

    // take picture, must be called in ui thread
    public void offerRawData(byte[] rawData, int width, int height, boolean isBottomCamera, int captureOrientation) {
        Log.i(TAG, "offerRawData---->");
        byte[] header = null;
        if (mImageProcessThread == null || mImageProcessHandler == null) {
            mImageProcessThread = new HandlerThread("PipImageProcess");
            mImageProcessThread.start();
            Looper looper = mImageProcessThread.getLooper();
            if (looper == null) {
                throw new RuntimeException("why looper is null?");
            }
            mImageProcessHandler = new ImageHandler(looper);
        }
        if (isBottomCamera) {
            try {
                header = readJpegHeader(rawData);
            } catch (Exception e) {
                Log.e(TAG, "readJpegHeader,exceptioin " + e.toString());
                return;
            }
        }
        mImageProcessHandler.obtainMessage(ImageHandler.MSG_NEW_JPEG_ARRIVED,
                new PipJpegWrapper(rawData,header, isBottomCamera, width, height, captureOrientation)).sendToTarget();
    }
    
    // take picture
    private void offerRawData(byte[] rawData, byte[] header, int width, int height, 
            boolean isBottomCamera, int captureOrientation) {
        Log.i(TAG, "offerRawData rawData = " + rawData + " width = " + width + " height = "
                + height + " isBottomCamera = " + isBottomCamera + " mPictureBufferAdded = "
                + mPictureBufferAdded);
        if (rawData == null || width <= 0 || height <= 0) {
            Log.i(TAG, "offerRawData error : rawData = " + rawData + " width = " + width
                    + " height = " + height);
            return;
        }
        
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = PipEGLConfigWrapper.getInstance().getBitmapConfig();
        if (isBottomCamera) {
            if (mPictureBottomBitmap != null) {
                Log.i(TAG, "wait for bitmap process begin");
                mCaptureConditionVariable.block(5000);
                Log.i(TAG, "wait for bitmap process end");
            }
            mJpegHeaderList.add(header);
            mPictureBottomBitmap = BitmapFactory.decodeByteArray(rawData, 0, rawData.length, options);
            mPictureBottomWidth = width;
            mPictureBottomHeight = height;
            mPictureBufferAdded++;
        } else {
            mPictureTopBitmap = BitmapFactory.decodeByteArray(rawData, 0, rawData.length, options);
            mPictureTopWidth = width;
            mPictureTopHeight = height;
            mPictureBufferAdded++;
        }
        rawData = null;
        Log.i(TAG, "offerRawData decode jpeg end");
        if (mPictureBufferAdded == TOTAL_PICTURE_BUFFER) {
            logMemory(">>>>>>>> takePicture");
            if (mPictureBottomWidth <= 0 || mPictureBottomHeight <= 0) {
                if (mPictureBottomBitmap != null) {
                    mPictureBottomBitmap.recycle();
                    mPictureBottomBitmap = null;
                }
                if (mPictureTopBitmap != null) {
                    mPictureTopBitmap.recycle();
                    mPictureTopBitmap = null;
                }
                mPIPPictureCallback.canDoStartPreview();
                return;
            }
            mPIPPictureCallback.canDoStartPreview();
            Log.i(TAG, "notify canDoStartPreview ");
            // update animation rect for capture
            AnimationRect pictureTopGraphicRect = mPIPPictureCallback.getPreviewAnimationRect();
            pictureTopGraphicRect.changeCooridnateSystem(mPictureBottomWidth, mPictureBottomHeight, 360-captureOrientation);
            // do take picture
            mPIPPictureCallback.getRendererManager().takePicture(mPictureBottomBitmap, mPictureTopBitmap,
                    mPictureBottomWidth, mPictureBottomHeight, mPictureTopWidth, mPictureTopHeight,
                    getFrontCameraInfoOrientation(),pictureTopGraphicRect);
            // when put capture data to GPU ,should close conditionvariable  in order to block when release occurs
            mReleaseConditionVariable.close();
            mCaptureConditionVariable.close();
            mPictureBufferAdded = 0;
            if (mPictureBottomBitmap != null) {
                Log.i(TAG,
                        "ImageRader mPictureBottomBitmap isRecycled = "
                                + mPictureBottomBitmap.isRecycled());
                // After swap to GPU, it will be reused in encode jpeg
            }
            if (mPictureTopBitmap != null) {
                Log.i(TAG,
                        "ImageRader mPictureTopBitmap isRecycled = "
                                + mPictureTopBitmap.isRecycled());
                // when swap to GPU, it will be recycled in
                // RendererManager.doTakePicture
                mPictureTopBitmap = null;
            }
            logMemory("<<<<<<<<< takePicture");
        }
    }

    public void release() {
        Log.i(TAG, "release");
        if (mImageReader != null) {
            mReleaseConditionVariable.block(5000);
        }
        //TODO this is a dirty workaround during gpu processing bitmap while here recycle it.
        //we will refactor this
        mPIPPictureCallback.getRendererManager().recycleBitmap();
        if (mPictureBottomBitmap != null && !mPictureBottomBitmap.isRecycled()) {
            mPictureBottomBitmap.recycle();
        }
        if (mPictureTopBitmap != null && !mPictureTopBitmap.isRecycled()) {
            mPictureTopBitmap.recycle();
        }
        mPictureBottomBitmap = null;
        mPictureTopBitmap = null;
        mPictureBufferAdded = 0;
        releaseImageReaderRelated();
    }

    // CaptureRenderer will swap buffer to ImageReader's Surface
    private class ImageListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            logMemory(">>>>>> onImageAvailable");
            Log.i(TAG, "onImageAvailable thread name = " + Thread.currentThread().getName());
            Image image = null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuffer imageBuffer = null;
            try {
                image = reader.acquireNextImage();
                logMemory("After reader.acquireNextImage");
                if (image == null) {
                    throw new RuntimeException("why image is null ?");
                }
                Log.i(TAG, "image : width = " + image.getWidth() + " height = " + image.getHeight()
                        + " format = " + image.getFormat());
                Bitmap bitmap = null;
                if (mPictureBottomBitmap != null && !mPictureBottomBitmap.isRecycled()) {
                    bitmap = Bitmap.createBitmap(mPictureBottomBitmap);
                } else {
                    bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(),
                            PipEGLConfigWrapper.getInstance().getBitmapConfig());
                }
                logMemory("After createBitmap");
                if ((image.getPlanes()[0].getPixelStride() * image.getWidth()) != image.getPlanes()[0]
                        .getRowStride()) {
                    Log.i(TAG, "getPixelStride = " + image.getPlanes()[0].getPixelStride()
                            + " getRowStride = " + image.getPlanes()[0].getRowStride());
                    // buffer is not placed continuously, should remove buffer
                    // position again
                    byte[] bytes = getContinuousRGBADataFromImage(image);
                    imageBuffer = ByteBuffer.allocateDirect(bytes.length);
                    imageBuffer.put(bytes);
                    imageBuffer.rewind();
                    bytes = null;
                } else {
                    // continuous buffer, read directly
                    imageBuffer = image.getPlanes()[0].getBuffer();
                }
                System.gc();
                logMemory("After image.getPlanes()[0].getBuffer");
                Log.i(TAG, "bitmap = " + bitmap + "imageBuffer = " + imageBuffer);
                bitmap.copyPixelsFromBuffer(imageBuffer);
                imageBuffer.clear();
                imageBuffer = null;
                bitmap.compress(CompressFormat.JPEG, 95, out);
                bitmap.recycle();
                bitmap = null;
                Log.i(TAG, "bitmap recycle end");
                System.gc();
                byte[] jpegHeader = mJpegHeaderList.poll();
                mCaptureConditionVariable.open();
                logMemory("After recycle bitmap");
                byte[] jpeg = out.toByteArray();
                // VSS needs not to write specific jpeg header
                // this is only be useful for pip capture
                if (jpegHeader != null) {
                    jpeg = writeJpegHeader(jpeg, jpegHeader);
                }
                image.close();
                image = null;
                if (mPIPPictureCallback != null) {
                    mPIPPictureCallback.onPIPPictureTaken(jpeg);
                }
                logMemory("After onPIPPictureTaken");
                Log.i(TAG, "out.size = " + out.size());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mReleaseConditionVariable.open();
                mCaptureConditionVariable.open();
                if (out != null) {
                    try {
                        out.close();
                        out = null;
                    } catch (IOException e) {
                        Log.e(TAG, "saveRequest()", e);
                    }
                }
                logMemory("<<<<< onImageAvailable");
            }
        }
    }

    public void setUpImageReaderRelated(int width, int height) {
        synchronized (mImageReaderSynObject) {
            Log.d(TAG, "setUpImageReaderRelated mImageReader = " + mImageReader);
            if (mImageReader != null && mImageReader.getWidth() == width
                    && mImageReader.getHeight() == height) {
                Log.i(TAG, "reuse old imagereader width = " + width + " height = " + height);
                return;
            }
            if (mImageReader != null) {
                mImageReader.close();
            }
            if (mImageHandlerThread == null) {
                mImageHandlerThread = new HandlerThread("ImageListener");
                mImageHandlerThread.start();
            }
            Looper looper = mImageHandlerThread.getLooper();
            if (looper == null) {
                throw new RuntimeException("why looper is null ?");
            }
            mImageReader = ImageReader.newInstance(width, height, PipEGLConfigWrapper.getInstance().getPixelFormat(), 2);
            mImageReader.setOnImageAvailableListener(new ImageListener(), new Handler(looper));
            mReleaseConditionVariable.open();
            mSurface = mImageReader.getSurface();
        }
    }

    public class CountedDataInputStream extends FilterInputStream {
        private int mCount = 0;
        
        // allocate a byte buffer for a long value;
        private final byte mByteArray[] = new byte[8];
        private final ByteBuffer mByteBuffer = ByteBuffer.wrap(mByteArray);
        
        public CountedDataInputStream(InputStream in) {
            super(in);
        }
        
        public int getReadByteCount() {
            return mCount;
        }
        
        @Override
        public int read(byte[] b) throws IOException {
            int r = in.read(b);
            mCount += (r >= 0) ? r : 0;
            return r;
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int r = in.read(b, off, len);
            mCount += (r >= 0) ? r : 0;
            return r;
        }
        
        @Override
        public int read() throws IOException {
            int r = in.read();
            mCount += (r >= 0) ? 1 : 0;
            return r;
        }
        
        @Override
        public long skip(long length) throws IOException {
            long skip = in.skip(length);
            mCount += skip;
            return skip;
        }
        
        public void skipOrThrow(long length) throws IOException {
            if (skip(length) != length)
                throw new EOFException();
        }
        
        public void skipTo(long target) throws IOException {
            long cur = mCount;
            long diff = target - cur;
            assert (diff >= 0);
            skipOrThrow(diff);
        }
        
        public void readOrThrow(byte[] b, int off, int len) throws IOException {
            int r = read(b, off, len);
            if (r != len)
                throw new EOFException();
        }
        
        public void readOrThrow(byte[] b) throws IOException {
            readOrThrow(b, 0, b.length);
        }
        
        public void setByteOrder(ByteOrder order) {
            mByteBuffer.order(order);
        }
        
        public ByteOrder getByteOrder() {
            return mByteBuffer.order();
        }
        
        public short readShort() throws IOException {
            readOrThrow(mByteArray, 0, 2);
            mByteBuffer.rewind();
            return mByteBuffer.getShort();
        }
        
        public int readUnsignedShort() throws IOException {
            return readShort() & 0xffff;
        }
        
        public int readInt() throws IOException {
            readOrThrow(mByteArray, 0, 4);
            mByteBuffer.rewind();
            return mByteBuffer.getInt();
        }
        
        public long readUnsignedInt() throws IOException {
            return readInt() & 0xffffffffL;
        }
        
        public long readLong() throws IOException {
            readOrThrow(mByteArray, 0, 8);
            mByteBuffer.rewind();
            return mByteBuffer.getLong();
        }
        
        public String readString(int n) throws IOException {
            byte buf[] = new byte[n];
            readOrThrow(buf);
            return new String(buf, "UTF8");
        }
        
        public String readString(int n, Charset charset) throws IOException {
            byte buf[] = new byte[n];
            readOrThrow(buf);
            return new String(buf, charset);
        }
    }
    
    private void releaseImageReaderRelated() {
        synchronized (mImageReaderSynObject) {
            if (mImageReader != null) {
                Log.i(TAG, "releaseImageReaderRelated");
                mImageReader.close();
                mImageReader = null;
                mSurface.release();
                mSurface = null;
                if (mImageHandlerThread.isAlive()) {
                    mImageHandlerThread.quit();
                    mImageHandlerThread = null;
                }
                System.gc();
                mImageReaderSynObject.notifyAll();
            }
            if (mImageProcessThread != null && mImageProcessThread.isAlive()) {
                mImageProcessThread.quit();
                mImageProcessThread = null;
            }
            Log.i(TAG, "release jpeg header size = " + mJpegHeaderList.size());
            mJpegHeaderList.clear();
        }
    }

    /**
     * Read continuous byte from image when rowStride != pixelStride * width
     */
    private static byte[] getContinuousRGBADataFromImage(Image image) {
        Log.i(TAG, "getContinuousRGBADataFromImage begin");
        if (image.getFormat() != PipEGLConfigWrapper.getInstance().getPixelFormat()) {
            Log.i(TAG, "error format = " + image.getFormat());
            return null;
        }
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride, pixelStride;
        byte[] data = null;
        Plane[] planes = image.getPlanes();
        if (format == PipEGLConfigWrapper.getInstance().getPixelFormat()) {
            PixelFormat pixelInfo = new PixelFormat();
            PixelFormat.getPixelFormatInfo(format, pixelInfo);
            ByteBuffer buffer = planes[0].getBuffer();
            rowStride = planes[0].getRowStride();
            pixelStride = planes[0].getPixelStride();
            data = new byte[width * height * pixelInfo.bitsPerPixel / 8];
            int offset = 0;
            int rowPadding = rowStride - pixelStride * width;
            // this format, pixelStride == bytesPerPixel, so read of the entire
            // row
            for (int y = 0; y < height; y++) {
                int length = width * pixelStride;
                buffer.get(data, offset, length);
                // Advance buffer the remainder of the row stride
                buffer.position(buffer.position() + rowPadding);
                offset += length;
            }
        }
        Log.i(TAG, "getContinuousRGBADataFromImage end");
        return data;
    }

    private static void logMemory(String title) {
        MemoryInfo mi = new MemoryInfo();
        android.os.Debug.getMemoryInfo(mi);
        String tagtitle = "logMemory() " + title;
        Log.i(TAG, tagtitle + "         PrivateDirty    Pss     SharedDirty");
        Log.i(TAG, tagtitle + " dalvik: " + mi.dalvikPrivateDirty + ", " + mi.dalvikPss + ", "
                + mi.dalvikSharedDirty + ".");
        Log.i(TAG, tagtitle + " native: " + mi.nativePrivateDirty + ", " + mi.nativePss + ", "
                + mi.nativeSharedDirty + ".");
        Log.i(TAG, tagtitle + " other: " + mi.otherPrivateDirty + ", " + mi.otherPss + ", "
                + mi.otherSharedDirty + ".");
        Log.i(TAG, tagtitle + " total: " + mi.getTotalPrivateDirty() + ", " + mi.getTotalPss()
                + ", " + mi.getTotalSharedDirty() + ".");
    }

    private static int getFrontCameraInfoOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int numOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numOfCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                return info.orientation;
            }
        }
        return -1;
    }
    
    private class ImageHandler extends Handler {
        public static final int MSG_NEW_JPEG_ARRIVED = 0;
        
        public ImageHandler(Looper looper) {
            super(looper);
        }
        
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "handleMessage msg = " + msg.what);
            switch (msg.what) {
            case MSG_NEW_JPEG_ARRIVED:
                PipJpegWrapper jpegWrapper = (PipJpegWrapper)msg.obj;
                offerRawData(jpegWrapper.getData(),jpegWrapper.getHeader(), jpegWrapper.getRequestWidth(),
                        jpegWrapper.getRequestHeight(), jpegWrapper.isBottomData(), jpegWrapper.getCaptureOrientation());
                break;
            default:
                break;
            }
        }
    }
    
    private byte[] readJpegHeader(byte[] jpeg) throws IllegalArgumentException, Exception {
        int jpegHeaderLength = readJpegHeaderLength(jpeg);
        InputStream inStream = new ByteArrayInputStream(jpeg);
        byte[] jpegHeader = new byte[jpegHeaderLength];
        int readLength = inStream.read(jpegHeader, 0, jpegHeaderLength);
        inStream.close();
        Log.d(TAG, "readJpegHeader jpegHeader length = " + jpegHeader.length + ",readLength = "
                + readLength + ",jpegHeaderLength = " + jpegHeaderLength);
        return jpegHeader;
    }
    
    private byte[] writeJpegHeader(byte[] jpeg,byte[] header) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream inStream = new ByteArrayInputStream(jpeg);
        try {
            int jpegHeaderLength = readJpegHeaderLength(jpeg);
            int jpegDataLength = jpeg.length - jpegHeaderLength;
            Log.d(TAG, "[writeJpegHeader]jpegHeaderLength = " + jpegHeaderLength
                    + " jpegDataLength = " + jpegDataLength);
            byte[] jpegdata = new byte[jpegDataLength];
            inStream.skip(jpegHeaderLength);
            int readLength = inStream.read(jpegdata);
            Log.d(TAG, "[writeJpegHeader]read raw jpage data length = " + jpegdata.length
                    + ",readLength = " + readLength);
            out.write(header);
            out.write(jpegdata);
            out.flush();
            jpegdata = null;
            System.gc();
        } catch (Exception e) {
            Log.e(TAG, "[writeJpegHeader]exceptioin " + e.toString());
        } finally {
            inStream.close();
            out.close();
        }
        return out.toByteArray();
    }
    
    @SuppressWarnings("resource")
    private int readJpegHeaderLength(byte[] jpeg) throws Exception {
        if (jpeg == null) {
            Log.e(TAG, "[readJpegHeaderLength]jpeg is null!");
            throw new IllegalArgumentException("Argument is null");
        }
        InputStream inStream = new ByteArrayInputStream(jpeg);
        CountedDataInputStream dataStream = new CountedDataInputStream(inStream);
        if (dataStream.readShort() != SOI) {
            Log.e(TAG, "[readJpegHeaderLength]Invalid Jpeg Format!");
            throw new Exception("Invalid Jpeg Format");
        }
        short marker = dataStream.readShort();
        int jpegHeaderLength = 2;// 2 bytes SOI
        while (marker != EOI && !isSofMarker(marker)) {
            int markerLength = dataStream.readUnsignedShort();
            if (!(marker == APP0 || marker == APP1 || marker == APP2 || marker == APP3
                    || marker == APP4 || marker == APP5 || marker == APP6 || marker == APP7
                    || marker == APP8 || marker == APP9 || marker == APP10 || marker == APP11
                    || marker == APP12 || marker == APP13 || marker == APP14 || marker == APP15)) {
                break;
            }
            jpegHeaderLength += (markerLength + 2);// 2 bytes marker
            Log.d(TAG, "Read marker = " + Integer.toHexString(marker) + " jpegHeaderLength = "
                    + jpegHeaderLength);
            if (markerLength < 2 || (markerLength - 2) != dataStream.skip(markerLength - 2)) {
                Log.e(TAG, "[readJpegHeaderLength]Invalid Marker Length = " + markerLength);
                throw new Exception("Invalid Marker Length = " + markerLength);
            }
            marker = dataStream.readShort();
        }
        inStream.close();
        dataStream.close();
        return jpegHeaderLength;
    }
    
    private boolean isSofMarker(short marker) {
        return marker >= SOF0 && marker <= SOF15 && marker != DHT && marker != JPG && marker != DAC;
    }
    
    private class PipJpegWrapper{
        private byte[] data;
        private byte[] header;
        private boolean isBottomData;
        private int requestWidth;
        private int requestHeight;
        private int captureOrientation;
        public PipJpegWrapper(byte[] data, byte[] header, boolean isBottomData,
                int width, int height, int orientation) {
            this.data = data;
            this.header = header;
            this.isBottomData = isBottomData;
            this.requestWidth = width;
            this.requestHeight = height;
            this.captureOrientation = orientation;
        }
        public byte[] getData() {
            return data;
        }
        public byte[] getHeader() {
            return header;
        }
        public boolean isBottomData() {
            return isBottomData;
        }
        public int getRequestWidth() {
            return requestWidth;
        }
        public int getRequestHeight() {
            return requestHeight;
        }
        public int getCaptureOrientation() {
            return captureOrientation;
        }
    }
}
