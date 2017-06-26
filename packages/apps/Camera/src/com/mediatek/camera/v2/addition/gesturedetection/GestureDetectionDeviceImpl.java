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
 * MediaTek Inc. (C) 2015. All rights reserved.
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
package com.mediatek.camera.v2.addition.gesturedetection;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Builder;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Log;

import com.mediatek.camera.v2.addition.IAdditionCaptureObserver;
import com.mediatek.camera.v2.addition.IAdditionManager.IAdditionListener;
import com.mediatek.camera.v2.addition.gesturedetection.GestureDetection.GestureDetectionListener;
import com.mediatek.camera.v2.module.ModuleListener;
import com.mediatek.camera.v2.module.ModuleListener.CaptureType;
import com.mediatek.camera.v2.module.ModuleListener.RequestType;
import com.mediatek.camera.v2.vendortag.TagMetadata;
import com.mediatek.camera.v2.vendortag.TagRequest;
import com.mediatek.camera.v2.vendortag.TagResult;

public class GestureDetectionDeviceImpl implements IGestureDetectionDevice {
    private static final String TAG = "CAM_API2/GestureDetectionDeviceImpl";

    private GestureDetectionListener mListener;
    private IAdditionListener mAdditionListener;
    private CaptureObserver mCaptureObserver = new CaptureObserver();
    private static final int GESTURE_RESULT_SIMPLE = 1;

    public GestureDetectionDeviceImpl(IAdditionListener additionListener) {
        mAdditionListener = additionListener;
    }

    @Override
    public void startGestureDetection() {
        Log.i(TAG, "startGestureDetection");
        mAdditionListener.requestChangeCaptureRequets(false,mAdditionListener.getRepeatingRequestType(), CaptureType.REPEATING_REQUEST);
    }

    @Override
    public void stopGestureDetection() {
        Log.i(TAG, "stopGestureDetection");
        mAdditionListener.requestChangeCaptureRequets(false,mAdditionListener.getRepeatingRequestType(), CaptureType.REPEATING_REQUEST);
    }

    @Override
    public void setGestureDetectionListener(GestureDetectionListener listener) {
        mListener = listener;
    }

    @Override
    public IAdditionCaptureObserver getCaptureObserver() {
        return mCaptureObserver;
    }

    private class CaptureObserver implements IAdditionCaptureObserver {
        @Override
        public void configuringRequests(CaptureRequest.Builder requestBuilder, RequestType requestType) {
            Log.i(TAG, "configuringRequests");
            requestBuilder.set(TagRequest.STATISTICS_GESTURE_MODE,
                    TagMetadata.MTK_FACE_FEATURE_GESTURE_MODE_SIMPLE);
        }

        @Override
        public void onCaptureStarted(CaptureRequest request, long timestamp, long frameNumber) {
            Log.i(TAG, "onCaptureStarted");
        }

        @Override
        public void onCaptureCompleted(CaptureRequest request, TotalCaptureResult result) {
            Integer gsResult = result.get(TagResult.STATISTICS_GESTURE_DETECTED_RESULT);
            Log.i(TAG, "onCaptureCompleted gsResult is " + gsResult);
            if (gsResult != null && gsResult == GESTURE_RESULT_SIMPLE) {
                mListener.onGesture();
            }
        }
    }
}
