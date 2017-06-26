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
package com.android.camera.v2.uimanager;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

import com.android.camera.R;
import com.android.camera.v2.ui.PickerButton;
import com.android.camera.v2.uimanager.preference.IconListPreference;
import com.android.camera.v2.uimanager.preference.ListPreference;
import com.android.camera.v2.uimanager.preference.PreferenceManager;
import com.android.camera.v2.util.SettingKeys;

import java.util.HashMap;
import java.util.Map;

public class PickerManager extends AbstractUiManager implements PickerButton.Listener{
  
    private static final String                  TAG = "PickerManager";
    
    private static final int                     PICKER_BUTTON_NUM = 7;
    private static final int                     BUTTON_SMILE_SHOT = 0;
    private static final int                     BUTTON_HDR = 1;
    private static final int                     BUTTON_FLASH = 2;
    private static final int                     BUTTON_CAMERA = 3;
    private static final int                     BUTTON_STEREO = 4;
    private static final int                     BUTTON_SLOW_MOTION = 5;
    private static final int                     BUTTON_GESTURE_SHOT = 6;
    private static final int                     MAX_NUM_OF_SHOWEN = 4;
    
    private PreferenceManager                    mPreferenceManager;
    private PickerButton                         mSlowMotion;
    private PickerButton                         mGestureShot;
    private PickerButton                         mHdr;
    private PickerButton                         mSmileShot;
    private PickerButton                         mFlashPicker;
    private PickerButton                         mCameraPicker;
    private PickerButton                         mStereoPicker;
    private PickerButton[]                       mPickerButtons = new PickerButton[PICKER_BUTTON_NUM];
    private OnPickedListener                     mOnPickedListener;
    
    // picker button show order is defined or not.
    private boolean                              mOrderDefined = false;
    private int[]                                mButtonPriority = {
            BUTTON_SLOW_MOTION, 
            BUTTON_HDR, 
            BUTTON_FLASH, 
            BUTTON_CAMERA,
            BUTTON_STEREO,
            BUTTON_GESTURE_SHOT, 
            BUTTON_SMILE_SHOT
    };
    
    private static final Map<Integer, String>  mButtonKeys = 
            new HashMap<Integer, String>(PICKER_BUTTON_NUM);
    
    static {
        mButtonKeys.put(BUTTON_SMILE_SHOT,   SettingKeys.KEY_SMILE_SHOT);
        mButtonKeys.put(BUTTON_HDR,          SettingKeys.KEY_HDR);
        mButtonKeys.put(BUTTON_FLASH,        SettingKeys.KEY_FLASH);
        mButtonKeys.put(BUTTON_CAMERA,       SettingKeys.KEY_CAMERA_ID);
        mButtonKeys.put(BUTTON_STEREO,       SettingKeys.KEY_STEREO3D_MODE);
        mButtonKeys.put(BUTTON_SLOW_MOTION,  SettingKeys.KEY_SLOW_MOTION);
        mButtonKeys.put(BUTTON_GESTURE_SHOT, SettingKeys.KEY_GESTURE_SHOT);
    }
    
    public interface OnPickedListener {
        public void onPicked(String key, String value);
    }
    
    public PickerManager(Activity activity, ViewGroup parent, 
            PreferenceManager preferenceManager) {
        super(activity, parent);
        mPreferenceManager = preferenceManager;
    }

    @Override
    protected View getView() {
        // TODO Auto-generated method stub
        View view = inflate(R.layout.onscreen_pickers_v2);
        
        mSlowMotion   = (PickerButton) view.findViewById(R.id.onscreen_slow_motion_picker);
        mGestureShot  = (PickerButton) view.findViewById(R.id.onscreen_gesture_shot_picker);
        mSmileShot    = (PickerButton) view.findViewById(R.id.onscreen_smile_shot_picker);
        mHdr          = (PickerButton) view.findViewById(R.id.onscreen_hdr_picker);
        mFlashPicker  = (PickerButton) view.findViewById(R.id.onscreen_flash_picker);
        mCameraPicker = (PickerButton) view.findViewById(R.id.onscreen_camera_picker);
        mStereoPicker = (PickerButton) view.findViewById(R.id.onscreen_stereo3d_picker);
        
        mPickerButtons[BUTTON_SLOW_MOTION] = mSlowMotion;
        mPickerButtons[BUTTON_GESTURE_SHOT] = mGestureShot;
        mPickerButtons[BUTTON_SMILE_SHOT] = mSmileShot;
        mPickerButtons[BUTTON_HDR] = mHdr;
        mPickerButtons[BUTTON_FLASH] = mFlashPicker;
        mPickerButtons[BUTTON_CAMERA] = mCameraPicker;
        mPickerButtons[BUTTON_STEREO] = mStereoPicker;
        applyListeners();
        return view;
    }
    
    @Override
    protected void onRefresh() {
        Log.d(TAG, "[onRefresh], mOrderDefined:" + mOrderDefined);   
        if (!mOrderDefined) {
            defineButtonOrder();
        }
        mSlowMotion.initialize((IconListPreference) mPreferenceManager.getListPreference(
                SettingKeys.KEY_SLOW_MOTION));
        mGestureShot.initialize((IconListPreference) mPreferenceManager.getListPreference(
                SettingKeys.KEY_GESTURE_SHOT));
        mSmileShot.initialize((IconListPreference) mPreferenceManager.getListPreference(
                SettingKeys.KEY_SMILE_SHOT));
        mHdr.initialize((IconListPreference) mPreferenceManager.getListPreference(
                SettingKeys.KEY_HDR));
        mFlashPicker.initialize((IconListPreference) mPreferenceManager.getListPreference(
                SettingKeys.KEY_FLASH));
        mCameraPicker.initialize((IconListPreference) mPreferenceManager.getListPreference(
                SettingKeys.KEY_CAMERA_ID));
        mStereoPicker.initialize((IconListPreference) mPreferenceManager.getListPreference(
                SettingKeys.KEY_STEREO3D_MODE));
    }

    @Override
    public boolean onPicked(PickerButton button, ListPreference preference,
            String newValue) {
        if (mOnPickedListener != null) {
            mOnPickedListener.onPicked(preference.getKey(), newValue);
        }
        return true;
    }
    
    @Override
    public void setEnable(boolean enabled) {
        super.setEnable(enabled);
        if (mSlowMotion != null) {
            mSlowMotion.setEnabled(enabled);
            mSlowMotion.setClickable(enabled);
        }
        if (mGestureShot != null) {
            mGestureShot.setEnabled(enabled);
            mGestureShot.setClickable(enabled);
        }
        if (mSmileShot != null) {
            mSmileShot.setEnabled(enabled);
            mSmileShot.setClickable(enabled);
        }
        if (mFlashPicker != null) {
            mFlashPicker.setEnabled(enabled);
            mFlashPicker.setClickable(enabled);
        }
        if (mCameraPicker != null) {
            mCameraPicker.setEnabled(enabled);
            mCameraPicker.setClickable(enabled);
        }
        if (mStereoPicker != null) {
            mStereoPicker.setEnabled(enabled);
            mStereoPicker.setClickable(enabled);
        }
        if (mHdr != null) {
            mHdr.setEnabled(enabled);
            mHdr.setClickable(enabled);
        }
    }
    
    public void notifyPreferenceReady() {
        Log.i(TAG, "[notifyPreferenceReady]...");
        defineButtonOrder();
    }
    
    public void setOnPickedListener(OnPickedListener listener) {
        mOnPickedListener = listener;
    }
    
    public void performCameraPickerBtnClick() {
        if (mCameraPicker != null) {
            //mCameraPicker.performClick();
            ListPreference pref = mPreferenceManager.getListPreference(SettingKeys.KEY_CAMERA_ID);
            if (pref != null) {
                String value = pref.getValue();
                int index = pref.findIndexOfValue(value);
                CharSequence[] values = pref.getEntryValues();
                index = (index + 1) % values.length;
                String next = values[index].toString();
                if (mOnPickedListener != null) {
                    mOnPickedListener.onPicked(SettingKeys.KEY_CAMERA_ID, next);
                }
                pref.setValueIndex(index);
            }
        }
    }
    
    private void applyListeners() {
        for (PickerButton button : mPickerButtons) {
            if (button != null) {
                button.setListener(this);
            }
        }
    }
    
    private void clearListeners() {
        for (PickerButton button : mPickerButtons) {
            if (button != null) {
                button.setListener(null);
            }
        }
    }
    
    private void defineButtonOrder() {
        int count = 0;
        for (int i = 0; i < mButtonPriority.length; i++) {
            int buttonIndex = mButtonPriority[i];
            String key = mButtonKeys.get(buttonIndex);
            ListPreference pref = mPreferenceManager.getListPreference(key);
            if (pref != null && pref.isVisibled()) {
                pref.showInSetting(false);
                count ++;
            }
            
            if (count >= MAX_NUM_OF_SHOWEN) {
                break;
            }
        }
        mOrderDefined = true;
    }
}
