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
package com.mediatek.camera.v2.setting;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest.Key;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.util.FloatMath;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.SurfaceHolder;

import com.mediatek.camcorder.CamcorderProfileEx;
import com.mediatek.camera.v2.util.SettingKeys;
import com.mediatek.camera.v2.util.Utils;
import com.mediatek.camera.v2.vendortag.TagMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingCharacteristics {
    private static final String          TAG = "SettingCharacteristics";
    private Map<String, List<String>>    mSupportedValuesMap = new HashMap<String, List<String>>();
    private List<Size>                   mSupportedPreviewSize;
    private CameraCharacteristics        mCameraCharacteristics;
    private Context                      mContext;
    private String                       mCameraId;
    
    private static Map<Integer, String>  mColorEffectMapping = new HashMap<Integer, String>();
    static {
        mColorEffectMapping.put(0, "none");
        mColorEffectMapping.put(1, "mono");
        mColorEffectMapping.put(2, "negative");
        mColorEffectMapping.put(3, "solarize");
        mColorEffectMapping.put(4, "sepia");
        mColorEffectMapping.put(5, "posterize");
        mColorEffectMapping.put(6, "whiteboard");
        mColorEffectMapping.put(7, "blackboard");
        mColorEffectMapping.put(8, "aqua");
    }
    
    private static Map<Integer, String>  mSceneModeMapping = new HashMap<Integer, String>();
    static {
        mSceneModeMapping.put(0, "auto");
        mSceneModeMapping.put(1, "face-priority");
        mSceneModeMapping.put(2, "action");
        mSceneModeMapping.put(3, "portrait");
        mSceneModeMapping.put(4, "landscape");
        mSceneModeMapping.put(5, "night");
        mSceneModeMapping.put(6, "night-portrait");
        mSceneModeMapping.put(7, "theatre");
        mSceneModeMapping.put(8, "beach");
        mSceneModeMapping.put(9, "snow");
        mSceneModeMapping.put(10, "sunset");
        mSceneModeMapping.put(11, "steadyphoto");
        mSceneModeMapping.put(12, "fireworks");
        mSceneModeMapping.put(13, "sports");
        mSceneModeMapping.put(14, "party");
        mSceneModeMapping.put(15, "candlelight");
        mSceneModeMapping.put(16, "barcode");
        mSceneModeMapping.put(17, "high-speed-video");
        mSceneModeMapping.put(18, "hdr");
    }
    
    private static Map<Integer, String>  mAWBModeMapping = new HashMap<Integer, String>(); 
    static {
        mAWBModeMapping.put(0, "off");
        mAWBModeMapping.put(1, "auto");
        mAWBModeMapping.put(2, "incandescent");
        mAWBModeMapping.put(3, "fluorescent");
        mAWBModeMapping.put(4, "warm-fluorescent");
        mAWBModeMapping.put(5, "daylight");
        mAWBModeMapping.put(6, "cloudy-daylight");
        mAWBModeMapping.put(7, "twilight");
        mAWBModeMapping.put(8, "shade");
    }
    
    private static Map<Integer, String>  mAntibandingMapping = new HashMap<Integer, String>(); 
    static {
        mAntibandingMapping.put(0, "off");
        mAntibandingMapping.put(1, "50hz");
        mAntibandingMapping.put(2, "60hz");
        mAntibandingMapping.put(3, "auto");
    }
    
    private static Map<Integer, String>  mGestureModeMapping = new HashMap<Integer, String>(); 
    static {
        mGestureModeMapping.put(0, "off");
        mGestureModeMapping.put(1, "on");
    }
    
    private static Map<Integer, String>  mSmileModeMapping = new HashMap<Integer, String>(); 
    static {
        mSmileModeMapping.put(0, "off");
        mSmileModeMapping.put(1, "on");
    }
    
    private static Map<Integer, String>  mASDModeMapping = new HashMap<Integer, String>(); 
    static {
        mASDModeMapping.put(0, "off");
        mASDModeMapping.put(1, "on");
    }
    
    private static final String VIDEO_QUALITY_LOW = Integer.
            toString(CamcorderProfileEx.QUALITY_LOW);
    private static final String VIDEO_QUALITY_MEDIUM = Integer
            .toString(CamcorderProfileEx.QUALITY_MEDIUM);
    private static final String VIDEO_QUALITY_HIGH = Integer
            .toString(CamcorderProfileEx.QUALITY_HIGH);
    private static final String VIDEO_QUALITY_FINE = Integer
            .toString(CamcorderProfileEx.QUALITY_FINE);
    
    public SettingCharacteristics(CameraCharacteristics characteristics, String cameraId, 
            Context context) {
        mCameraCharacteristics = characteristics;
        mCameraId = cameraId;
        mContext = context;
    }
    
    public List<String> getSupportedValues(String key) {
        int settingIndex = SettingKeys.getSettingId(key);
        List<String> supportedValues = null;
        switch(settingIndex) {
        case SettingKeys.ROW_SETTING_COLOR_EFFECT:
            int[] effects = mCameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
            supportedValues = new ArrayList<String>(effects.length);
            for (int effect : effects) {
                supportedValues.add(mColorEffectMapping.get(effect));
            }
            break;
            
        case SettingKeys.ROW_SETTING_MULTI_FACE_MODE:
            break;
            
        case SettingKeys.ROW_SETTING_ISO:
            Range<Integer> isoRange = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (isoRange != null) {
                int minIso = isoRange.getLower();
                int maxIso = isoRange.getUpper();
                Log.i(TAG, "minIso:" + minIso + ", maxIso:" + maxIso);
            }
            
            break;
        case SettingKeys.ROW_SETTING_EXPOSURE:
            Range<Integer> ecRange = mCameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            int minExposureCompensation = ecRange.getLower();
            int maxExposureCompensation = ecRange.getUpper();

            Rational ecStep = mCameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            float exposureCompensationStep = (float) ecStep.getNumerator() / ecStep.getDenominator();
            Log.i(TAG, "minExposureCompensation:" + minExposureCompensation + ", " +
                    "maxExposureCompensation:" + maxExposureCompensation + ", " +
                    "exposureCompensationStep:" + exposureCompensationStep);
            supportedValues = getSupportedExposureCompensation(maxExposureCompensation,
                    minExposureCompensation, exposureCompensationStep);
            break;
            
        case SettingKeys.ROW_SETTING_SCENCE_MODE:
            int[] scenes = mCameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
            // If no scene modes are supported by the camera device, only DISABLED list 
            // in scenes. Otherwise DISABLED will not be listed.
            if (scenes.length == 1 && scenes[0] == CameraMetadata.CONTROL_SCENE_MODE_DISABLED) {
                // do nothing.
            } else {
                supportedValues = new ArrayList<String>(scenes.length);
                for (int scene : scenes) {
                    supportedValues.add(mSceneModeMapping.get(scene));
                }
                
                if (!supportedValues.contains("auto")) {
                    supportedValues.add("auto");
                }
            }
            break;
            
        case SettingKeys.ROW_SETTING_WHITE_BALANCE:
            int[] awbModes = mCameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
            supportedValues = new ArrayList<String>(awbModes.length);
            for (int awbMode : awbModes) {
                supportedValues.add(mAWBModeMapping.get(awbMode));
            }
            break;
            
        case SettingKeys.ROW_SETTING_SHARPNESS:
            break;
            
        case SettingKeys.ROW_SETTING_HUE:
            break;
            
        case SettingKeys.ROW_SETTING_SATURATION:
            break;
            
        case SettingKeys.ROW_SETTING_BRIGHTNESS:
            break;
            
        case SettingKeys.ROW_SETTING_CONTRAST:
            break;
            
        case SettingKeys.ROW_SETTING_ANTI_FLICKER:
            int[] antibandingModes = mCameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES);
            supportedValues = new ArrayList<String>(antibandingModes.length);
            for (int antibingMode : antibandingModes) {
                supportedValues.add(mAntibandingMapping.get(antibingMode));
            }
            break;
            
        case SettingKeys.ROW_SETTING_ZSD:
            break;
            
        case SettingKeys.ROW_SETTING_AIS:
            break;
            
        case SettingKeys.ROW_SETTING_CAMERA_FACE_DETECT:
            int faceCount = mCameraCharacteristics.get(
                    CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
            Log.i(TAG, "faceCount:" + faceCount);
            if (faceCount > 0) {
                supportedValues = new ArrayList<String>(2);
                supportedValues.add("off");
                supportedValues.add("on");
            }
            break;
            
        case SettingKeys.ROW_SETTING_GESTURE_SHOT:
            int[] gestureModes = mCameraCharacteristics.get(TagMetadata.GESTURE_AVAILABLE_MODES);
            supportedValues = new ArrayList<String>(gestureModes.length);
            for (int i = 0; i < gestureModes.length; i++) {
                supportedValues.add(mGestureModeMapping.get(i));
            }
            break;
            
        case SettingKeys.ROW_SETTING_SMILE_SHOT:
            int[] smileModes = mCameraCharacteristics.get(TagMetadata.SMILE_AVAILABLE_MODES);
            supportedValues = new ArrayList<String>(smileModes.length);
            for (int i = 0; i < smileModes.length; i++) {
                supportedValues.add(mSmileModeMapping.get(i));
            }
            break;
            
        case SettingKeys.ROW_SETTING_ASD:
            int[] asdModes = mCameraCharacteristics.get(TagMetadata.ASD_AVAILABLE_MODES);
            supportedValues = new ArrayList<String>(asdModes.length);
            for (int i = 0; i < asdModes.length; i++) {
                supportedValues.add(mASDModeMapping.get(i));
            }
            break;
            
        case SettingKeys.ROW_SETTING_PICTURE_RATIO:
            supportedValues = getSupportedPictureRatio();
            break;
            
        case SettingKeys.ROW_SETTING_PICTURE_SIZE:
            StreamConfigurationMap s = mCameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = s.getOutputSizes(ImageFormat.JPEG);
            supportedValues = new ArrayList<String>(sizes.length);
            for (Size size : sizes) {
                int width = size.getWidth();
                int height = size.getHeight();
                String sizeString = String.valueOf(width) + "x" + String.valueOf(height);
                supportedValues.add(sizeString);
            }
            break;

        case SettingKeys.ROW_SETTING_3DNR:
            break;
            
        case SettingKeys.ROW_SETTING_VIDEO_STABLE:
            supportedValues = new ArrayList<String>(2);
            supportedValues.add("off");
            supportedValues.add("on");
            break;
            
        case SettingKeys.ROW_SETTING_VIDEO_QUALITY:
            supportedValues = getSupportedVideoQuality();
            break;
            
        case SettingKeys.ROW_SETTING_PHOTO_PIP:
            supportedValues = new ArrayList<String>(2);
            supportedValues.add("off");
            supportedValues.add("on");
            break;
            
        case SettingKeys.ROW_SETTING_FACE_BEAUTY:
            supportedValues = new ArrayList<String>(2);
            supportedValues.add("off");
            supportedValues.add("on");
            break;
            
        case SettingKeys.ROW_SETTING_HDR:
            break;
            
        case SettingKeys.ROW_SETTING_FACEBEAUTY_SMOOTH:
        case SettingKeys.ROW_SETTING_FACEBEAUTY_SKIN_COLOR:
            supportedValues = new ArrayList<String>(3);
            supportedValues.add("-4");
            supportedValues.add("0");
            supportedValues.add("4");
            break;
            
        case SettingKeys.ROW_SETTING_FACEBEAUTY_SHARP:
            break;
            
        case SettingKeys.ROW_SETTING_FLASH:
            Boolean flashInfo = mCameraCharacteristics.get(
                    CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (flashInfo) {
                supportedValues = new ArrayList<String>(3);
                supportedValues.add("auto");
                supportedValues.add("on");
                supportedValues.add("off");
            }
            break;
        default:
            break;
        }
        Log.i(TAG, "key:" + key + ", supportedValues:" + supportedValues);
        return supportedValues;
    }
    
    public Map<Key<?>, ?> convertToMetadata(Map<String, String> changedSettings) {
        return null;
    }
    
    public List<Size> getSupportedPreviewSize() {
        if (mSupportedPreviewSize != null) {
            return mSupportedPreviewSize;
        }
        
        StreamConfigurationMap s = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = s.getOutputSizes(SurfaceHolder.class);
        mSupportedPreviewSize = new ArrayList<Size>(sizes.length);
        for (Size size : sizes) {
            mSupportedPreviewSize.add(size);
        }
        
        return mSupportedPreviewSize;
    }
    
    private List<String> getSupportedVideoQuality() {
        ArrayList<String> supported = new ArrayList<String>();
        int cameraId = Integer.parseInt(mCameraId);
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfileEx.QUALITY_LOW)) {
            supported.add(VIDEO_QUALITY_LOW);
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfileEx.QUALITY_MEDIUM)) {
            supported.add(VIDEO_QUALITY_MEDIUM);
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfileEx.QUALITY_HIGH)) {
            supported.add(VIDEO_QUALITY_HIGH);
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfileEx.QUALITY_FINE)) {
            supported.add(VIDEO_QUALITY_FINE);
        }
        
        return supported;
    }
    
    private List<String> getSupportedPictureRatio() {
        List<String> pictureRatios = new ArrayList<String>();
        StreamConfigurationMap s = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = s.getOutputSizes(SurfaceHolder.class);
        
        double fullRatio = Utils.findFullscreenRatio(mContext);
        if (mSupportedPreviewSize == null) {
            mSupportedPreviewSize = new ArrayList<Size>(sizes.length);
        }
        for (Size size : sizes) {
            mSupportedPreviewSize.add(size);
        }
        
        Size fullSize = Utils.getOptimalPreviewSize(mContext, mSupportedPreviewSize, fullRatio);
        
        // if fullSize is not null, it means that this chip support full ratio preview size.
        if (fullSize != null) {
            if (fullRatio != 1.3333) {
                pictureRatios.add(String.valueOf(fullRatio));
            }
        }

        pictureRatios.add("1.3333");
        
        return pictureRatios;
    }
    
    private List<String> getSupportedExposureCompensation(int max, int min, float step) {
        int maxValue = (int) FloatMath.floor(max * step);
        int minValue = (int) FloatMath.ceil(min * step);
        ArrayList<String> supportedValues = new ArrayList<String>();
        for (int i = minValue; i <= maxValue; ++i) {
            String value = Integer.toString(Math.round(i / step));
            supportedValues.add(String.valueOf(value));
        }
        
        return supportedValues;
    }
}
