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

package com.mediatek.camera.ext;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.mediatek.camera.ext.DefaultAppGuideExt;
import com.mediatek.camera.ext.IAppGuideExt;
import com.mediatek.camera.ext.IAppGuideExt.OnGuideFinishListener;
import com.mediatek.camera.ext.ICameraFeatureExt;
import com.mediatek.camera.ext.DefaultCameraFeatureExt;
import com.mediatek.common.MPlugin;

public class ExtensionHelper {
    private static final String TAG = "CameraApp/ExtensionHelper";
    private static final boolean LOG = true;
    private static ICameraFeatureExt sCameraFeatureExtension;
    private static IAppGuideExt sAppGuideExt;
    public static final String GUIDE_TYPE_CAMERA = "CAMERA";
    public static final String GUIDE_TYPE_MAV = "CAMERA_MAV";

    // must pass real context when you use this method first.
    public static ICameraFeatureExt getCameraFeatureExtension(Context context) {
        if (sCameraFeatureExtension == null) {
            sCameraFeatureExtension = (ICameraFeatureExt) MPlugin.createInstance(
                    ICameraFeatureExt.class.getName(), context.getApplicationContext());
            if (sCameraFeatureExtension == null) {
                Log.i(TAG, "[getCameraFeatureExtension], use default camera feature");
                sCameraFeatureExtension = new DefaultCameraFeatureExt(context);
            }
        }
        Log.v(TAG, "getCameraFeatureExtension() sCameraFeatureExtension=" + sCameraFeatureExtension);
        return sCameraFeatureExtension;
    }

    public static void showAppGuide(Activity activity,  String type, OnGuideFinishListener onFinishListener) {
        if (sAppGuideExt == null) {
           sAppGuideExt = (IAppGuideExt)MPlugin.createInstance(IAppGuideExt.class.getName(), 
                   activity);
           if (sAppGuideExt == null) {
               Log.i(TAG, "[showAppGuide], use default app guide");
               sAppGuideExt = new DefaultAppGuideExt();
           }
        }
        if (sAppGuideExt != null) {
            sAppGuideExt.showCameraGuide(activity, type, onFinishListener);
        }
        if (LOG) {
            Log.v(TAG, "showAppGuide() sAppGuideExt=" + sAppGuideExt + ", type = " + type);
        }
    }
    
    
    public static void configurationChanged(Activity activity) {
        if (sAppGuideExt == null) {
            sAppGuideExt = (IAppGuideExt)MPlugin.createInstance(
                    IAppGuideExt.class.getName(), activity);
            if (sAppGuideExt == null) {
                Log.i(TAG, "[configurationChanged], use default app guide");
                sAppGuideExt = new DefaultAppGuideExt();
            }
        }
        if (sAppGuideExt != null) {
            sAppGuideExt.configurationChanged();
        }
        if (LOG) {
            Log.v(TAG, "configurationChanged() sAppGuideExt=" + sAppGuideExt);
        }
    }

    public static void dismissAppGuide() {
        if (LOG) {
            Log.v(TAG, "dismissAppGuide() sAppGuideExt=" + sAppGuideExt);
        }
        
        if (sAppGuideExt != null) {
            sAppGuideExt.dismiss();
            sAppGuideExt = null;
        }
    }
}
