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
package com.mediatek.camera.setting.rule;

import com.mediatek.camera.ICameraContext;
import com.mediatek.camera.ISettingCtrl;
import com.mediatek.camera.ISettingRule;
import com.mediatek.camera.ISettingRule.MappingFinder;
import com.mediatek.camera.platform.ICameraDeviceManager;
import com.mediatek.camera.platform.ICameraDeviceManager.ICameraDevice;
import com.mediatek.camera.platform.Parameters;
import com.mediatek.camera.setting.ParametersHelper;
import com.mediatek.camera.setting.SettingConstants;
import com.mediatek.camera.setting.SettingItem;
import com.mediatek.camera.setting.SettingItem.Record;
import com.mediatek.camera.setting.SettingUtils;
import com.mediatek.camera.setting.preference.ListPreference;
import com.mediatek.camera.util.Log;

import java.util.ArrayList;
import java.util.List;

public class RuleContainer {

    private static final String TAG = "RuleContainer";
    private long PICTURE_SIZE_4M = 4000000l;
    private ICameraDeviceManager mICameraDeviceManager;
    private ICameraContext mICameraContext;
    private ISettingCtrl mISettingCtrl;
    private SettingItem mPictureSetting;

    public RuleContainer(ISettingCtrl settingCtrl, ICameraContext cameraContext) {
        mISettingCtrl = settingCtrl;
        mICameraDeviceManager = cameraContext.getCameraDeviceManager();
        mICameraContext = cameraContext;
    }

    public void addRule() {
        if (!mICameraContext.getFeatureConfig().isLowRamOptSupport()) {
            return;
        }
        LowRamPictureRule hdrLowRam = new LowRamPictureRule(SettingConstants.KEY_HDR);
        mISettingCtrl.addRule(SettingConstants.KEY_HDR,
                SettingConstants.KEY_PICTURE_SIZE, hdrLowRam);

        LowRamPictureRule aisLowRam = new LowRamPictureRule(SettingConstants.KEY_CAMERA_AIS);
        mISettingCtrl.addRule(SettingConstants.KEY_CAMERA_AIS,
                SettingConstants.KEY_PICTURE_SIZE, aisLowRam);

        LowRamPictureRule nightMode = new LowRamPictureRule(SettingConstants.KEY_SCENE_MODE);
        mISettingCtrl.addRule(SettingConstants.KEY_SCENE_MODE,
                SettingConstants.KEY_PICTURE_SIZE, nightMode);
    }

    private class LowRamPictureRule implements ISettingRule {
        private SettingItem mCurrentSettingItem;
        private String mSettingKey;
        public LowRamPictureRule(String settingkey) {
            mSettingKey = settingkey;
            mCurrentSettingItem = mISettingCtrl.getSetting(mSettingKey);
            Log.i("iniTAG","mCurrentSettingItem  = " + mCurrentSettingItem);
        }
        @Override
        public void execute() {
            if (!mICameraContext.getFeatureConfig().isLowRamOptSupport()) {
                return;
            }
            mPictureSetting = mISettingCtrl.getSetting(SettingConstants.KEY_PICTURE_SIZE);
            String resultValue = mPictureSetting.getValue();
            String currentValue = mCurrentSettingItem.getValue();
            int type = mPictureSetting.getType();
            if ("on".equals(currentValue) || "night".equals(currentValue) || "ais".equals(currentValue)) {
                ListPreference pref = mPictureSetting.getListPreference();
                CharSequence[] entryValues = pref.getEntryValues();
                List<String> overValues = new ArrayList<String>();
                String near4MSize = null;
                long near4M = 0l;
                int index;
                int width;
                int height;
                // here get entry values from listPreference and then remove
                // sizes which is bigger than 4M
                for (int i = 0; i < entryValues.length; i++) {
                    index = entryValues[i].toString().indexOf('x');
                    width = Integer.parseInt(entryValues[i].toString().substring(0, index));
                    height = Integer.parseInt(entryValues[i].toString().substring(index + 1));
                    if (PICTURE_SIZE_4M >= width * height) {
                        // remember the maximum size which is not bigger than 4M
                        if (near4M < width * height) {
                            near4M = width * height;
                            near4MSize = "" + width + "x" + height;
                        }
                        overValues.add("" + width + "x" + height);
                    }
                }
                // if resultValue is not bigger than 4M use it or use near4MSize
                if (0 > overValues.indexOf(resultValue)) {
                    resultValue = near4MSize;
                }

                String[] values = new String[overValues.size()];
                String overrideValue = SettingUtils.buildEnableList(
                        overValues.toArray(values), resultValue);
                if (mPictureSetting.isEnable()) {
                    setResultSettingValue(type, resultValue, overrideValue,
                            true, mPictureSetting);
                }
                Record record = mPictureSetting.new Record(resultValue,overrideValue);
                mPictureSetting.addOverrideRecord(mSettingKey,record);


            } else {
                // restore picture size after set hdr off
                int overrideCount = mPictureSetting.getOverrideCount();
                Record record = mPictureSetting.getOverrideRecord(mSettingKey);
                if (record == null) {
                    return;
                }
                Log.i(TAG, "overrideCount:" + overrideCount);
                mPictureSetting.removeOverrideRecord(mSettingKey);
                overrideCount--;

                if (overrideCount > 0) {
                    Record topRecord = mPictureSetting.getTopOverrideRecord();
                    if (topRecord != null) {
                        if (mPictureSetting.isEnable()) {
                            String value = topRecord.getValue();
                            String overrideValue = topRecord.getOverrideValue();
                            // may be the setting's value is changed, the value
                            // in record is old.
                            ListPreference pref = mPictureSetting
                                    .getListPreference();
                            if (pref != null
                                    && SettingUtils.isBuiltList(overrideValue)) {
                                pref.setEnabled(true);
                                String prefValue = pref.getValue();
                                List<String> list = SettingUtils
                                        .getEnabledList(overrideValue);
                                if (list.contains(prefValue)) {
                                    if (!prefValue.equals(value)) {
                                        String[] values = new String[list
                                                .size()];
                                        overrideValue = SettingUtils
                                                .buildEnableList(
                                                        list.toArray(values),
                                                        prefValue);
                                    }
                                    value = prefValue;
                                }
                            }
                            setResultSettingValue(type, value, overrideValue,
                                    true, mPictureSetting);
                        }
                    }
                } else {
                    mISettingCtrl.executeRule(SettingConstants.KEY_PICTURE_RATIO, SettingConstants.KEY_PICTURE_SIZE);
                }
            }
        }

        @Override
        public void addLimitation(String condition, List<String> result,
                MappingFinder mappingFinder) {

        }
    }

    // now setResultSettingValue is only use for hdr ram optimize rule ,if use to another
    // rule should reference to common rule setResultSettingValue
    private void setResultSettingValue(int settingType, String value,
            String overrideValue, boolean restoreSupported, SettingItem item) {
        int currentCameraId = mICameraDeviceManager.getCurrentCameraId();
        ICameraDevice cameraDevice = mICameraDeviceManager
                .getCameraDevice(currentCameraId);
        Parameters parameters = cameraDevice.getParameters();
        item.setValue(value);
        ListPreference pref = item.getListPreference();
        if (pref != null) {
             pref.setOverrideValue(overrideValue, restoreSupported);
        }
        ParametersHelper.setParametersValue(parameters, currentCameraId,
                    item.getKey(), value);

    }
}