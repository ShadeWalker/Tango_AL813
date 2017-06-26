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
package com.mediatek.camera.addition.continuousshot;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;

import com.mediatek.camera.util.Log;

public class MemoryManager implements ComponentCallbacks2 {
    private static final String TAG = "MemoryManager";
    
    // Let's signal only 40% of max memory is allowed to be used by normal
    // continuous shots.
    private static final float SLOW_DOWN_THRESHHOLD = 0.4f;
    private static final float STOP_THRESHHOLD = 0.1f;
    private static final int LOW_SUITABLE_SPEED_FPS = 1;
    
    private final long BYTES_IN_KILOBYTE = 1024;
    private long mMaxMemory;
    private long mSlowDownThreshhold;
    private long mStopThreshhold;
    private long mLeftStorage;
    private long mUsedStorage;
    private long mPengdingSize;
    private long mStartTime;
    private int mCount;
    private int mSuitableSpeed;
    
    private MemoryActon mMemoryActon = MemoryActon.NORMAL;
    
    private Runtime mRuntime = Runtime.getRuntime();
    
    public enum MemoryActon {
        NORMAL, ADJSUT_SPEED, STOP,
    }
    
    public MemoryManager(Context context) {
        Log.i(TAG, "[MemoryManager]constructor...");
        context.registerComponentCallbacks(this);
    }
    
    @Override
    public void onLowMemory() {
        Log.i(TAG, "[onLowMemory]...");
        mMemoryActon = MemoryActon.STOP;
    }
    
    @Override
    public void onTrimMemory(int level) {
        Log.i(TAG, "[onTrimMemory]level: " + level);
        switch (level) {
        case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
        case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
        case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
            mMemoryActon = MemoryActon.ADJSUT_SPEED;
            break;
        
        case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
        case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            mMemoryActon = MemoryActon.STOP;
            break;
        
        default:
            mMemoryActon = MemoryActon.NORMAL;
            break;
        }
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // donothing
    }
    
    public void init(long leftStorage) {
        mMemoryActon = MemoryActon.NORMAL;
        mMaxMemory = mRuntime.maxMemory();
        mSlowDownThreshhold = (long) (SLOW_DOWN_THRESHHOLD * mMaxMemory);
        mStopThreshhold = (long) (STOP_THRESHHOLD * mMaxMemory);
        mLeftStorage = leftStorage;
        mUsedStorage = 0;
        mPengdingSize = 0;
        mCount = 0;
        Log.i(TAG, "[init]mMaxMemory=" + toMb(mMaxMemory) + " MB, mMemoryActon=" + mMemoryActon);
    }
    
    public void start() {
        mStartTime = System.currentTimeMillis();
    }
    
    public MemoryActon getMemoryAction(long pictureSize, long pendingSize) {
        Log.d(TAG, "[getMemoryAction]pictureSize=" + toMb(pictureSize) + " MB, pendingSize="
                + toMb(pendingSize) + " MB");
        mCount++;
        mUsedStorage += pictureSize;
        mPengdingSize = pendingSize;
        long timeDuration = System.currentTimeMillis() - mStartTime;
        long captureSpeed = mCount * BYTES_IN_KILOBYTE / timeDuration;
        long saveSpeed = (mUsedStorage - mPengdingSize) / timeDuration / BYTES_IN_KILOBYTE;
        Log.d(TAG, "[getMemoryAction]Capture speed=" + captureSpeed + " fps, Save speed="
                + saveSpeed + " MB/s");
        // remaining storage check.
        Log.d(TAG, "[getMemoryAction]mUsedStorage=" + toMb(mUsedStorage) + " MB, mLeftStorage="
                + toMb(mLeftStorage) + " MB");
        if (mUsedStorage >= mLeftStorage) {
            return MemoryActon.STOP;
        }
        mSuitableSpeed = (int) ((mUsedStorage - mPengdingSize) * mCount * BYTES_IN_KILOBYTE
                / timeDuration / mUsedStorage);
        Log.d(TAG, "[getMemoryAction]Suitable speed=" + mSuitableSpeed + " fps");
        // system memory status check;
        if (mMemoryActon != MemoryActon.NORMAL) {
            return mMemoryActon;
        }
        // application pending jpeg data size check;
        if (mPengdingSize >= mSlowDownThreshhold) {
            Log.d(TAG, "[getMemoryAction]Need slow down");
            return MemoryActon.ADJSUT_SPEED;
        }
        long total = mRuntime.totalMemory();
        long free = mRuntime.freeMemory();
        long realfree = mMaxMemory - (total - free);
        Log.d(TAG, "[getMemoryAction]total=" + toMb(total) + " MB, free=" + toMb(free)
                + " MB, real free=" + toMb(realfree) + " MB");
        // DVM memory check;
        if (realfree <= mStopThreshhold) {
            return MemoryActon.STOP;
        }
        return MemoryActon.NORMAL;
    }
    
    public int getSuitableContinuousShotSpeed() {
        if (mSuitableSpeed < LOW_SUITABLE_SPEED_FPS) {
            mSuitableSpeed = LOW_SUITABLE_SPEED_FPS;
            Log.i(TAG, "[getSuitableContinuousShotSpeed]Current performance is very poor!");
        }
        return mSuitableSpeed;
    }
    
    private long toMb(long in) {
        return in / BYTES_IN_KILOBYTE / BYTES_IN_KILOBYTE;
    }
}