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

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Key;

import com.mediatek.camera.v2.util.SettingKeys;
import com.mediatek.camera.v2.vendortag.TagRequest;

import java.util.HashMap;
import java.util.Map;

public class SettingConvertor{
    
    private static Map<String, Key<?>> nativeKeys= new HashMap<String, Key<?>>();
    static {
        nativeKeys.put(SettingKeys.KEY_CAMERA_FACE_DETECT, CaptureRequest.STATISTICS_FACE_DETECT_MODE);
        nativeKeys.put(SettingKeys.KEY_EXPOSURE,           CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
        nativeKeys.put(SettingKeys.KEY_SCENE_MODE,         CaptureRequest.CONTROL_SCENE_MODE);
        nativeKeys.put(SettingKeys.KEY_WHITE_BALANCE,      CaptureRequest.CONTROL_AWB_MODE);
        nativeKeys.put(SettingKeys.KEY_COLOR_EFFECT,       CaptureRequest.CONTROL_EFFECT_MODE);
        nativeKeys.put(SettingKeys.KEY_ISO,                CaptureRequest.SENSOR_SENSITIVITY);
        nativeKeys.put(SettingKeys.KEY_VIDEO_EIS,          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE);
        nativeKeys.put(SettingKeys.KEY_SMILE_SHOT,         TagRequest.STATISTICS_SMILE_MODE);
        nativeKeys.put(SettingKeys.KEY_ASD,                TagRequest.STATISTICS_ASD_MODE);
        nativeKeys.put(SettingKeys.KEY_GESTURE_SHOT,       TagRequest.STATISTICS_GESTURE_MODE);
    }

    private enum FaceDetectMode {
        OFF(0),
        ON(1);
        
        private int value = 0;
        private FaceDetectMode(int value) {
            this.value = value;
        }
        
        public int value() {
            return this.value;
        }
    }
    
    public enum SceneMode {
        AUTO(0),
        FACE_PORTRAIT(1),
        ACTION(2),
        PORTRAIT(3),
        LANDSCAPE(4),
        NIGHT(5),
        NIGHT_PORTRAIT(6),
        THEATRE(7),
        BEACH(8),
        SNOW(9),
        SUNSET(10),
        STEADYPHOTO(11),
        FIREWORKS(12),
        SPORTS(13),
        PARTY(14),
        CANDLELIGHT(15),
        BARCODE(16),
        HIGH_SPEED_VIDEO(17),
        HDR(18);
        
        private int value = 0;
        private SceneMode(int value) {
            this.value = value;
        }
        
        public int value() {
            return this.value;
        }
    }
    
    private enum AWBMode {
        OFF(0),
        AUTO(1),
        INCANDESCENT(2),
        FLUORESCENT(3),
        WARM_FLUORESCENT(4),
        DAYLIGHT(5),
        CLOUDY_DAYLIGHT(6),
        TWILIGHT(7),
        SHADE(8);
     
        private int value = 0;
        private AWBMode(int value) {
            this.value = value;
        }
        
        public int value() {
            return this.value;
        }
    }
    
    private enum EffectMode {
        NONE(0),
        MONO(1),
        NEGATIVE(2),
        SOLARIZE(3),
        SEPIA(4),
        POSTERIZE(5),
        WHITEBOARD(6),
        BLACKBOARD(7),
        AQUA(8);
       
        private int value = 0;
        private EffectMode(int value) {
            this.value = value;
        }
        
        public int value() {
            return this.value;
        }
    }
    
    private enum EISMode {
        OFF(0),
        ON(1);
        
        private int value = 0;
        private EISMode(int value) {
            this.value = value;
        }
        
        public int value() {
            return this.value;
        }
    }
    
    private enum SmileDetectMode {
        OFF(0),
        ON(1);
        
        private int value = 0;
        private SmileDetectMode(int value) {
            this.value = value;
        }
        
        public int value() {
            return this.value;
        }
    }
    
    private enum GestureDetectMode {
        OFF(0),
        ON(1);
        
        private int value = 0;
        private GestureDetectMode(int value) {
            this.value = value;
        }
        
        public int value() {
            return this.value;
        }
    }
    
    private enum ASDDetectMode {
        OFF(0),
        ON(1);
        
        private int value = 0;
        private ASDDetectMode(int value) {
            this.value = value;
        }
        
        public int value() {
            return this.value;
        }
    }
    
    public static Key<?> getNativeKey(String key) {
        return nativeKeys.get(key);
    }
    
    public static int getNativeValue(String key, String value) {
        int settingId = SettingKeys.getSettingId(key);
        int nativeValue = 0;
        switch (settingId) {
        case SettingKeys.ROW_SETTING_CAMERA_FACE_DETECT:
            nativeValue = getFaceDetectMode(value);
            break;
        
        case SettingKeys.ROW_SETTING_EXPOSURE:
            nativeValue = Integer.parseInt(value);
            break;
            
        case SettingKeys.ROW_SETTING_SCENCE_MODE:
            nativeValue = getSceneMode(value);
            break;
            
        case SettingKeys.ROW_SETTING_WHITE_BALANCE:
            nativeValue = getAWBMode(value);
            break;
            
        case SettingKeys.ROW_SETTING_COLOR_EFFECT:
            nativeValue = getEffectMode(value);
            break;
    
        case SettingKeys.ROW_SETTING_ISO:
            nativeValue = Integer.parseInt(value);
            break;
    
        case SettingKeys.ROW_SETTING_VIDEO_STABLE:
            nativeValue = getEISMode(value);
            break;
            
        case SettingKeys.ROW_SETTING_SMILE_SHOT:
            nativeValue = getSmileDetectMode(value);
            break;
            
        case SettingKeys.ROW_SETTING_GESTURE_SHOT:
            nativeValue = getGestureDetectMode(value);
            break;
            
        case SettingKeys.ROW_SETTING_ASD:
            nativeValue = getASDDetectMode(value);
            break;
            
        default:
            break;
        }
        return nativeValue;
    }
    
    public static String convertSceneEnumToString(int enumValue) {
        SceneMode[] values = SceneMode.values();
        String scene = SceneMode.AUTO.toString().toLowerCase();
        for (SceneMode mode : values) {
            if (enumValue == mode.value()) {
                scene = mode.toString().replace('_', '-').toLowerCase();
                break;
            }
        }
        
        return scene;
    }

    private static int getFaceDetectMode(String value) {
        int selectedMode = 0;
        FaceDetectMode[] values = FaceDetectMode.values();
        for (FaceDetectMode mode : values) {
            if (mode.toString().equalsIgnoreCase(value)) {
                selectedMode = mode.value();
                break;
            }
        }
        return selectedMode;
    }
    
    private static int getSceneMode(String value) {
        int selectedMode = 0;
        SceneMode[] values = SceneMode.values();
        for (SceneMode mode : values) {
            if ((mode.toString().replace('_', '-')).equalsIgnoreCase(value)) {
                selectedMode = mode.value();
                break;
            }
        }
        return selectedMode;
    }
    
    private static int getAWBMode(String value) {
        int selectedMode = 0;
        AWBMode[] values = AWBMode.values();
        for (AWBMode mode : values) {
            if ((mode.toString().replace('_', '-')).equalsIgnoreCase(value)) {
                selectedMode = mode.value();
                break;
            }
        }
        return selectedMode;
    }
    
    private static int getEffectMode(String value) {
        int selectedMode = 0;
        EffectMode[] values = EffectMode.values();
        for (EffectMode mode : values) {
            if ((mode.toString().replace('_', '-')).equalsIgnoreCase(value)) {
                selectedMode = mode.value();
                break;
            }
        }
        return selectedMode;
    }
    
    private static int getEISMode(String value) {
        int selectedMode = 0;
        EISMode[] values = EISMode.values();
        for (EISMode mode : values) {
            if (mode.toString().equalsIgnoreCase(value)) {
                selectedMode = mode.value();
                break;
            }
        }
        return selectedMode;
    }
    
    private static int getSmileDetectMode(String value) {
        int selectedMode = 0;
        SmileDetectMode[] values = SmileDetectMode.values();
        for (SmileDetectMode mode : values) {
            if (mode.toString().equalsIgnoreCase(value)) {
                selectedMode = mode.value();
                break;
            }
        }
        return selectedMode;
    }
    
    private static int getGestureDetectMode(String value) {
        int selectedMode = 0;
        GestureDetectMode[] values = GestureDetectMode.values();
        for (GestureDetectMode mode : values) {
            if (mode.toString().equalsIgnoreCase(value)) {
                selectedMode = mode.value();
                break;
            }
        }
        return selectedMode;
    }
    
    private static int getASDDetectMode(String value) {
        int selectedMode = 0;
        ASDDetectMode[] values = ASDDetectMode.values();
        for (ASDDetectMode mode : values) {
            if (mode.toString().equalsIgnoreCase(value)) {
                selectedMode = mode.value();
                break;
            }
        }
        return selectedMode;
    }
}
