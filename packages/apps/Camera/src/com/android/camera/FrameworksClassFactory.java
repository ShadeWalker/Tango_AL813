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
package com.android.camera;

import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.SystemProperties;
import android.util.Log;

import com.android.camera.mock.hardware.MockCamera;
import com.android.camera.mock.media.MockCamcorderProfileHelper;
import com.android.camera.mock.media.MockMediaRecorder;

import com.mediatek.camcorder.CamcorderProfileEx;
import com.android.camera.FeatureSwitcher;

public class FrameworksClassFactory {
    private static final String TAG = "FrameworksClassFactory";
    //TODO will remove when native support cam hal version 1.0 legacy mode
    private static int sTrySwitchToLegacyMode = SystemProperties.getInt("mtk.camera.app.legacy", 0);
    private static final boolean LOG = true;
    private static final boolean MOCK_CAMERA;
    
    static {
        MOCK_CAMERA = FeatureSwitcher.isEmulatorSupported();
    }
    
    public static boolean isMockCamera() {
        return MOCK_CAMERA;
    }
    
    public static ICamera openCamera(int cameraId) {
        if (MOCK_CAMERA) {
            return MockCamera.open(cameraId);
        } else {
            Camera camera = null;
            if (sTrySwitchToLegacyMode > 0) {
                   // choose legacy mode in order to enter cam hal 1.0
                   camera = Camera.openLegacy(cameraId, Camera.CAMERA_HAL_API_VERSION_1_0);
               } else {
                   camera = Camera.open(cameraId);
               }
            if (null == camera) {
                Log.e(TAG, "openCamera:got null hardware camera!");
                return null;
            }
            // wrap it with ICamera
            return new AndroidCamera(camera);
        }
    }
    
    public static MediaRecorder getMediaRecorder() {
        if (MOCK_CAMERA) {
            return new MockMediaRecorder();
        } else {
            return new MediaRecorder();
        }
    }
    
    public static CamcorderProfile getMtkCamcorderProfile(int cameraId, int quality) {
        if (MOCK_CAMERA) {
            return MockCamcorderProfileHelper.getMtkCamcorderProfile(cameraId, quality);
        } else {
            return CamcorderProfileEx.getProfile(cameraId, quality);
        }
    }
    
    public static int getNumberOfCameras() {
        if (MOCK_CAMERA) {
            return MockCamera.getNumberOfCameras();
        } else {
            return Camera.getNumberOfCameras();
        }
    }
    
    public static void getCameraInfo(int cameraId, CameraInfo cameraInfo) {
        if (MOCK_CAMERA) {
            MockCamera.getCameraInfo(cameraId, cameraInfo);
        } else {
            Camera.getCameraInfo(cameraId, cameraInfo);
        }
    }
    
}
