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
package com.android.camera.mock.hardware;

import android.content.Context;
import android.media.MediaActionSound;
import android.os.Handler;
import android.os.Message;
import com.android.gallery3d.app.Log;

import com.android.gallery3d.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class CaptureThread extends Thread {
    private static final String TAG = "CaptureThread";
    
    private static final int CAPTURE_TIME = 200;
    private static final int IDLE_TIME = 20000;
    private Handler mHandler;
    private Context mContext;
    private int mCapNum = 0;
    private int mCurrentCount = 0;
    private boolean mActCapture = false;
    private boolean mQuit = false;
    private MediaActionSound mCameraSound;
    
    public CaptureThread(Handler handler, Context context, MediaActionSound sound) {
        mHandler = handler;
        mContext = context;
        mCameraSound = sound;
    }
    
    public synchronized void setCaptureNum(int capNum) {
        Log.i(TAG, "setCaptureNum = " + capNum + " CurrentCount = " + mCurrentCount);
        if (mCurrentCount != 0) {
            return;
        }
        mCapNum = capNum;
    }
    
    public void doCapture() {
        byte[] data = null;
        if (mContext == null || mHandler == null) {
            Log.i(TAG, "doCapture not ready");
        } else {
            Log.i(TAG, "doCapture, CurrentCount = " + mCurrentCount);
            data = onPictureCreate();
        }
        Message msg = mHandler.obtainMessage(MessageEnum.CAMERA_MSG_COMPRESSED_IMAGE, data);
        mHandler.sendMessageDelayed(msg, CAPTURE_TIME);
        if (mCapNum == 1) {
            Log.i(TAG, "play shutter sound");
            mCameraSound.play(MediaActionSound.SHUTTER_CLICK);
        }
    }
    
    private static int sPictureCount = 0;
    
    private byte[] onPictureCreate() {
        if (null == mContext) {
            return null;
        }
        InputStream inputStream;
        sPictureCount++;
        sPictureCount %= 2;
        if (sPictureCount == 0) {
            inputStream = mContext.getResources().openRawResource(R.raw.blank);
        } else {
            inputStream = mContext.getResources().openRawResource(R.raw.test);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] b = new byte[1024];
        int size = -1;
        try {
            while ((size = inputStream.read(b)) != -1) {
                out.write(b, 0, size);
            }
        } catch (IOException ioe) {
            Log.i(TAG, "read blank.jpg in raw reault in error");
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                Log.i(TAG, "close inputStream fail");
            }
        }
        return out.toByteArray();
    }
    
    public synchronized void capture() {
        Log.i(TAG, "capture, CurrentCount = " + mCurrentCount);
        if (mCurrentCount != 0) {
            return;
        }
        mActCapture = true;
        this.interrupt();
    }
    
    public synchronized void cancelCapture() {
        Log.i(TAG, "cancelCapture, CurrentCount = " + mCurrentCount);
        if (mActCapture) {
            Message msg = mHandler.obtainMessage(MessageEnum.MTK_CAMERA_MSG_EXT_NOTIFY,
                    MessageEnum.MTK_CAMERA_MSG_EXT_NOTIFY_CONTINUOUS_END, mCurrentCount);
            mHandler.sendMessageDelayed(msg, 100);
            mCurrentCount = 0;
            mActCapture = false;
            this.interrupt();
        }
    }
    
    public synchronized void quit() {
        Log.i(TAG, "quit, CurrentCount = " + mCurrentCount);
        mActCapture = false;
        mQuit = true;
    }
    
    public void run() {
        while (true) {
            while (mActCapture) {
                synchronized (this) {
                    doCapture();
                    mCurrentCount++;
                    if (mCurrentCount == mCapNum) {
                        Log.i(TAG, "Reach count break, CurrentCount = " + mCurrentCount);
                        mActCapture = false;
                        mCurrentCount = 0;
                        break;
                    }
                }
                try {
                    Log.i(TAG, "Into capture time");
                    sleep(CAPTURE_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            synchronized (this) {
                if (mQuit) {
                    break;
                }
            }
            try {
                Log.i(TAG, "Into Idle time");
                sleep(IDLE_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
