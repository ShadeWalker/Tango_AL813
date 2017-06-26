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

package com.android.launcher2;

import android.content.Context;

import com.mediatek.common.MPlugin;
import com.mediatek.launcher2.ext.DefaultAllAppsListExt;
import com.mediatek.launcher2.ext.DefaultDataLoader;
import com.mediatek.launcher2.ext.DefaultOperatorChecker;
import com.mediatek.launcher2.ext.DefaultSearchButtonExt;
import com.mediatek.launcher2.ext.IAllAppsListExt;
import com.mediatek.launcher2.ext.IDataLoader;
import com.mediatek.launcher2.ext.IOperatorChecker;
import com.mediatek.launcher2.ext.ISearchButtonExt;
import com.mediatek.launcher2.ext.LauncherLog;

/**
 * M: LauncherExtPlugin class used to Get AllAppsListExt.
 */
public class LauncherExtPlugin {
    private IAllAppsListExt mAllAppsListExt = null;
    private ISearchButtonExt mSearchButtonExt = null;
    private IOperatorChecker mOperatorCheckerExt = null;
    private IDataLoader mLoadDataExt = null;
    private static LauncherExtPlugin sLauncherExtPluginInstance = new LauncherExtPlugin();

    private LauncherExtPlugin() {
    }

    public static LauncherExtPlugin getInstance() {
        return sLauncherExtPluginInstance;
    }

    /**
     * Returns a reference to a IAllAppsListExt instance.
     *
     * @param context A Context object
     * @return IAllAppsListExt object
     */
    public synchronized IAllAppsListExt getAllAppsListExt(final Context context) {
        if (mAllAppsListExt == null) {
            mAllAppsListExt = MPlugin.createInstance(IAllAppsListExt.class.getName(), context);
            if (mAllAppsListExt == null) {
                mAllAppsListExt = new DefaultAllAppsListExt();
            }
        }
        LauncherLog.d("AllAppsListExt", "getAllAppsListExt: context = " + context
                + ", mAllAppsListExt = " + mAllAppsListExt);
        return mAllAppsListExt;
    }

    /**
     * Returns a reference to a ISearchButtonExt instance.
     *
     * @param context A Context object
     * @return ISearchButtonExt object
     */
    public synchronized ISearchButtonExt getSearchButtonExt(final Context context) {
        if (mSearchButtonExt == null) {
            mSearchButtonExt = MPlugin.createInstance(ISearchButtonExt.class.getName(), context);
            if (mSearchButtonExt == null) {
                mSearchButtonExt = new DefaultSearchButtonExt();
            }
        }
        LauncherLog.d("SearchButtonExt", "getSearchButtonExt: context = " + context
                + ", mSearchButtonExt = " + mSearchButtonExt);
        return mSearchButtonExt;
    }

    /**
     * Returns a reference to a IOperatorChecker instance.
     *
     * @param context A Context object
     * @return IOperatorChecker object
     */
    public synchronized IOperatorChecker getOperatorCheckerExt(final Context context) {
        if (mOperatorCheckerExt == null) {
            mOperatorCheckerExt = MPlugin.createInstance(IOperatorChecker.class.getName(), context);
            if (mOperatorCheckerExt == null) {
                mOperatorCheckerExt = new DefaultOperatorChecker();
            }
        }
        LauncherLog.d("OperatorChecker", "getOperatorCheckerExt: context = " + context
                + ", mOperatorCheckerExt = " + mOperatorCheckerExt);
        return mOperatorCheckerExt;
    }

    /**
     * Returns a reference to a IDataLoader instance.
     *
     * @param context A Context object
     * @return IDataLoader object
     */
    public synchronized IDataLoader getLoadDataExt(final Context context) {
        if (mLoadDataExt == null) {
            mLoadDataExt = MPlugin.createInstance(IDataLoader.class.getName(), context);
            if (mLoadDataExt == null) {
                mLoadDataExt = new DefaultDataLoader(context);
            }
        }
        LauncherLog.d("LoadDataExt", "getLoadDataExt: context = " + context
                + ", mLoadDataExt = " + mLoadDataExt);
        return mLoadDataExt;
    }
}
