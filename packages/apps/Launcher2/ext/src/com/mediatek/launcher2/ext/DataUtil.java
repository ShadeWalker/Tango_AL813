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
 * MediaTek Inc. (C) 2010. All rights reserved.
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

package com.mediatek.launcher2.ext;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.android.launcher2.ItemInfo;

/**
 * M: DataUtil mainly support LauncherExt class to visit LauncherCom class.
 */
public class DataUtil {
    private static final String TAG = "DataUtil";

    private static DataUtil sDataUtil = null;

    /**
     * Returns a reference to a DataUtil instance.
     * @return DataUtil object.
     */
    public static synchronized DataUtil getInstance() {
        if (sDataUtil == null) {
            try {
                sDataUtil = (DataUtil) Class.forName("com.android.launcher2.LauncherDataUtil")
                        .newInstance();
            } catch (ClassNotFoundException e) {
                LauncherLog.d(TAG, "LauncherDataUtil Class not found!");
            } catch (InstantiationException e) {
                LauncherLog.d(TAG, "LauncherDataUtil Instantiation Exception!");
            } catch (IllegalAccessException e) {
                LauncherLog.d(TAG, "LauncherDataUtil IllegalAccess Exception!");
            }
        }
        LauncherLog.d(TAG, "sDataUtil = " + sDataUtil);
        return sDataUtil;
    }

    /**
     * Check all the data is consistent.
     *
     * @param info ItemInfo to check.
     */
    public void checkItemInfo(ItemInfo info) {
        LauncherLog.d(TAG, "DataUtil CheckItemInfo!");
    }

    /**
     * Returns a bitmap suitable for the all apps view. Used to convert pre-ICS
     * icon bitmaps that are stored in the database (which were 74x74 pixels at
     * hdpi size) to the proper size (48dp).
     *
     * @param icon Icon resource.
     * @param context A Context object.
     * @return Bitmap created.
     */
    public Bitmap createIconBitmap(Drawable icon, Context context) {
        LauncherLog.d(TAG, "DataUtil createIconBitmap!");
        return null;
    }

    /**
     * Get Component Name from Resolve Info for use.
     *
     * @param info ResolveInfo for use.
     * @return ComponentName in ResolveInfo.
     */
    public ComponentName getComponentNameFromResolveInfo(ResolveInfo info) {
        LauncherLog.d(TAG, "DataUtil getComponentNameFromResolveInfo!");
        return null;
    }
}