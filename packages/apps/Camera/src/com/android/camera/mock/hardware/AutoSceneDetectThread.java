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

import android.os.Handler;
import android.os.Message;

import com.android.camera.Log;

import java.util.ArrayList;
import java.util.Random;

public class AutoSceneDetectThread extends Thread {
    
    private static final String TAG = "AutoSceneDetectThread";
    private static final int DETECTINGTIME = 1000;
    private static final int SCENENUM = 9;
    private static final int MAGICNUM = 23;
    private Handler mHandler;
    private boolean mQuit = false;
    private Random mRandom = new Random();
    private ArrayList<Integer> mSupportedMode = new ArrayList<Integer>();
    
    public AutoSceneDetectThread(Handler handler) {
        mHandler = handler;
        // Current supported mode
        mSupportedMode.add(0);
        mSupportedMode.add(1);
        mSupportedMode.add(2);
        mSupportedMode.add(3);
        mSupportedMode.add(4);
        mSupportedMode.add(6);
        mSupportedMode.add(8);
    }
    
    public void quit() {
        mQuit = true;
        this.interrupt();
    }
    
    public void run() {
        int nextScheduleTime = DETECTINGTIME;
        while (true) {
            if (mQuit) {
                break;
            }
            int seed = mRandom.nextInt(100);
            seed %= MAGICNUM;
            if (seed > MAGICNUM / 2) {
                int scene = mRandom.nextInt(seed);
                scene %= SCENENUM;
                while (mSupportedMode.indexOf(scene) == -1) {
                    scene = mRandom.nextInt(seed);
                    scene %= SCENENUM;
                }
                nextScheduleTime = DETECTINGTIME * scene;
                Message msg = mHandler.obtainMessage(MessageEnum.MTK_CAMERA_MSG_EXT_NOTIFY,
                        MessageEnum.MTK_CAMERA_MSG_EXT_NOTIFY_ASD, scene);
                mHandler.sendMessageDelayed(msg, 100);
            } else {
                nextScheduleTime = DETECTINGTIME;
            }
            try {
                sleep(nextScheduleTime);
            } catch (InterruptedException e) {
                Log.i(TAG, "break from Idle");
            }
        }
    }
}
