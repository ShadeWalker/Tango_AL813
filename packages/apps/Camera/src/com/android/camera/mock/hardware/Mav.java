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
import android.os.Handler;
import android.os.Message;

import com.android.camera.Log;

import com.android.gallery3d.R;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Mav extends Thread {
    private static final String TAG = "Mav";
    
    private static final int CAPTURE_INTERVAL = 100;
    private int mMavCaptureNum = 15;
    private int mCurrentNum = 0;
    private boolean mInCapture = false;
    private boolean mMerge;
    private Handler mHandler;
    private Context mContext;
    private String mCapturePath;
    
    public Mav(Handler handler) {
        mHandler = handler;
    }
    
    public synchronized void startMav(int num) {
        Log.i(TAG, "startMav");
        mMavCaptureNum = num;
        mInCapture = true;
    }
    
    public synchronized void stopMav(int merge) {
        Log.i(TAG, "stopMav");
        mInCapture = false;
        mMerge = merge > 0;
        this.interrupt();
    }
    
    public void run() {
        while (mInCapture && mCurrentNum < mMavCaptureNum) {
            try {
                sleep(CAPTURE_INTERVAL);
            } catch (InterruptedException e) {
                Log.i(TAG, "get Notify");
            }
            if (!mInCapture) {
                break;
            }
            if (mHandler != null) {
                sendFrameMsg();
            }
        }
        if (mMerge) {
            Log.i(TAG, "Save mpo file");
            if (mContext != null) {
                onPictureCreate();
            }
            sendFrameMsg();
            mMerge = false;
        } else {
            Log.i(TAG, "clear frame buff");
        }
    }
    
    private void onPictureCreate() {
        InputStream inputStream = mContext.getResources().openRawResource(R.raw.dsc00058);
        FileOutputStream out = null;
        byte[] b = new byte[1024];
        int size = -1;
        try {
            out = new FileOutputStream(mCapturePath);
            while ((size = inputStream.read(b)) != -1) {
                out.write(b, 0, size);
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File: " + mCapturePath + " not found!");
        } catch (IOException ioe) {
            Log.i(TAG, "read blank.jpg in raw reault in error");
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                Log.i(TAG, "close inputStream fail");
            }
        }
    }
    
    public void sendFrameMsg() {
        Message msg = mHandler.obtainMessage(MessageEnum.MTK_CAMERA_MSG_EXT_NOTIFY,
                MessageEnum.MTK_CAMERA_MSG_EXT_NOTIFY_MAV, 0);
        mHandler.sendMessage(msg);
    }
    
    public void setCapturePath(String path) {
        mCapturePath = path;
    }
    
    public void setContext(Context context) {
        mContext = context;
    }
}
