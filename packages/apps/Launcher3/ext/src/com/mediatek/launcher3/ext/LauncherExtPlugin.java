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

package com.mediatek.launcher3.ext;

import android.content.Context;

import com.mediatek.common.MPlugin;

/**
 * M: LauncherExtPlugin class used to Get AllAppsListExt.
 */
public class LauncherExtPlugin {
    private static final String TAG = "LauncherExtPlugin";

    private ISearchButtonExt mSearchButtonExt = null;
    private IWorkspaceExt mWorkspaceExt = null;
    private IDataLoader mDataLoadExt = null;

    private static LauncherExtPlugin sLauncherExtPluginInstance = new LauncherExtPlugin();


    /**
     * Get a LauncherExtPlugin instance.
     * @return LauncherExtPlugin single object.
     */
    public static LauncherExtPlugin getInstance() {
        return sLauncherExtPluginInstance;
    }

    /**
     * Get an ISearchButtonExt instance.
     * @param context A Context object
     * @return ISearchButtonExt object.
     */
    public synchronized ISearchButtonExt getSearchButtonExt(final Context context) {
        if (mSearchButtonExt == null) {
            mSearchButtonExt = MPlugin.createInstance(ISearchButtonExt.class.getName(), context);
            if (mSearchButtonExt == null) {
                mSearchButtonExt = new DefaultSearchButton();
            }
        }
        LauncherLog.d(TAG, "getSearchButtonExt: context = " + context
                + ", mSearchButtonExt = " + mSearchButtonExt);
        return mSearchButtonExt;
    }

    /**
     * Get an IWorkspaceExt instance.
     * @param context A Context object
     * @return IWorkspaceExt object.
     */
    public synchronized IWorkspaceExt getWorkspaceExt(final Context context) {
        if (mWorkspaceExt == null) {
            mWorkspaceExt = MPlugin.createInstance(IWorkspaceExt.class.getName(), context);
            if (mWorkspaceExt == null) {
                mWorkspaceExt = new DefaultWorkspaceExt(context);
            }
        }
        LauncherLog.d(TAG, "getWorkspaceExt: context = " + context
                + ", mWorkspaceExt = " + mWorkspaceExt);
        return mWorkspaceExt;
    }

    /**
     * Get an IDataLoader instance.
     * @param context A Context object
     * @return IDataLoader object.
     */
    public synchronized IDataLoader getLoadDataExt(final Context context) {
        if (mDataLoadExt == null) {
            mDataLoadExt = MPlugin.createInstance(IDataLoader.class.getName(), context);
            if (mDataLoadExt == null) {
                mDataLoadExt = new DefaultDataLoader(context);
            }
        }
        LauncherLog.d(TAG, "getLoadDataExt: context = " + context
                + ", mDataLoadExt = " + mDataLoadExt);
        return mDataLoadExt;
    }
}
