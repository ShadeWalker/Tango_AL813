/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.am;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.util.Slog;
import android.view.WindowManager;
///M:for low storage feature @{
import com.android.server.LocalServices;
import com.android.server.storage.DeviceStorageMonitorInternal;
import android.content.Intent;
import android.provider.Settings;
///@}

/// M: BMW. Add error handling mechanism @{
import com.mediatek.multiwindow.MultiWindowProxy;

final class AppErrorDialog extends BaseErrorDialog {
    private final ActivityManagerService mService;
    private final static String TAG = "AppErrorDialog";
    private final AppErrorResult mResult;
    private final ProcessRecord mProc;

    // Event 'what' codes
    static final int FORCE_QUIT = 0;
    static final int FORCE_QUIT_AND_REPORT = 1;
    private static final int FREE_SPACE = 10;

    private Context mContext;

    /// M: ALPS00944212 prevent binder wait forever until 5mins timeout @{
    private int mResultType = FORCE_QUIT;
    /// @}

    // 5-minute timeout, then we automatically dismiss the crash dialog
    static final long DISMISS_TIMEOUT = 1000 * 60 * 5;
    ///M:For low storage, do not show target process error,when Peocess crash @{
    private boolean mTargetProcess = false;
    ///@}
    public AppErrorDialog(Context context, ActivityManagerService service,
            AppErrorResult result, ProcessRecord app) {
        super(context);
        mContext = context;
        Resources res = context.getResources();
        
        mService = service;
        mProc = app;
        mResult = result;
        CharSequence name;
        ///M:For low storage, show the difference dialog,when APP&Peocess crash @{
        CharSequence message;
        
        /// M: BMW. Add error handling mechanism @{
        int errHandlingRes = MultiWindowProxy.ERR_HANDLING_NONE;
        MultiWindowProxy mMWProxy = MultiWindowProxy.getInstance();
        if (MultiWindowProxy.isFeatureSupport() 
                && mMWProxy!= null && mMWProxy.isAppErrorHandlingEnabled()) {
            boolean isFloatingApp = false;
            if (app.activities.size() > 0)
                isFloatingApp = (mMWProxy.isFloatingWindow(app.activities.get(0).intent));

            if (isFloatingApp) {
                errHandlingRes = mMWProxy.appErrorHandling(app.info.packageName, app.inMaxOrRestore);
            }
        }
        /// @}
        final DeviceStorageMonitorInternal
                    dsm = LocalServices.getService(DeviceStorageMonitorInternal.class);
        boolean criticalLow = dsm.isMemoryCriticalLow();
        if ((app.pkgList.size() == 1) &&
                (name=context.getPackageManager().getApplicationLabel(app.info)) != null) {
                mTargetProcess = false;
                if (criticalLow == true) {
                    message = res.getString(
                            com.mediatek.internal.R.string.aerr_application_lowstorage,
                            name.toString(), app.info.processName);

                } else {
                    message = res.getString(
                        com.android.internal.R.string.aerr_application,
                        name.toString(), app.info.processName);
                }
                /// M: BMW. Add error handling mechanism @{
                if (MultiWindowProxy.isFeatureSupport() 
                        && mMWProxy!= null && mMWProxy.isAppErrorHandlingEnabled()) {
                    message = buildErrHandlingMessage(message, errHandlingRes, app.info.packageName);
                }
                /// @}
                setMessage(message);
        } else {
            name = app.processName;
            //these process will restart when killed
            if (((name.toString().indexOf("com.mediatek.bluetooth")) != -1) ||
                ((name.toString().indexOf("android.process.acore")) != -1)) {
                Slog.v(TAG, "got target error process");
                mTargetProcess = true;
            } else {
                mTargetProcess = false;
            }

            if (criticalLow == true) {
                message = res.getString(
                        com.mediatek.internal.R.string.aerr_process_lowstorage,
                        name.toString());
            } else {
                message = res.getString(
                        com.android.internal.R.string.aerr_process,
                        name.toString());
            }
            /// M: BMW. Add error handling mechanism @{
            if (MultiWindowProxy.isFeatureSupport()
                    && mMWProxy!= null && mMWProxy.isAppErrorHandlingEnabled()) {
                message = buildErrHandlingMessage(message, errHandlingRes, app.info.packageName);
            }
            /// @}
            setMessage(message);
        }
        ///@}
        setCancelable(false);

        setButton(DialogInterface.BUTTON_POSITIVE,
                res.getText(com.android.internal.R.string.force_close),
                mHandler.obtainMessage(FORCE_QUIT));

        if (app.errorReportReceiver != null) {
            setButton(DialogInterface.BUTTON_NEGATIVE,
                    res.getText(com.android.internal.R.string.report),
                    mHandler.obtainMessage(FORCE_QUIT_AND_REPORT));
        }
        ///M:For Low storage,add button in error dialog @{
        if (criticalLow == true) {
            setButton(DialogInterface.BUTTON_NEUTRAL,
                       res.getText(com.mediatek.R.string.free_memory_btn),
                       mHandler.obtainMessage(FREE_SPACE));
        }

        ///@}
        setTitle(res.getText(com.android.internal.R.string.aerr_title));
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.setTitle("Application Error: " + app.info.processName);
        attrs.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SYSTEM_ERROR
                | WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        getWindow().setAttributes(attrs);
        if (app.persistent) {
            getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
        }


        if ((criticalLow == true) && (mTargetProcess)) {
           Slog.v(TAG, "do not show the error dialog!");
           mHandler.sendMessageDelayed(
                mHandler.obtainMessage(FORCE_QUIT),
                0);
        } else {
        // After the timeout, pretend the user clicked the quit button
            mHandler.sendMessageDelayed(
                mHandler.obtainMessage(FORCE_QUIT),
                DISMISS_TIMEOUT);
        }

    /// M: ALPS00944212 prevent binder wait forever until 5mins timeout @{
    this.setOnDismissListener(mDismissListener);
    /// @}
    }
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == FREE_SPACE) {
                Intent mIntent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
                mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(mIntent);
            }
            /// M: ALPS00944212 prevent binder wait forever until 5mins timeout @{
            //synchronized (mService) {
            //    if (mProc != null && mProc.crashDialog == AppErrorDialog.this) {
            //        mProc.crashDialog = null;
            //    }
            //}
            //mResult.set(msg.what);
            mResultType = msg.what;
            /// @}
            // Make sure we don't have time timeout still hanging around.
            removeMessages(FORCE_QUIT);

            // If this is a timeout we won't be automatically closed, so go
            // ahead and explicitly dismiss ourselves just in case.
            dismiss();
        }
    };

    /// M: ALPS00944212 prevent binder wait forever until 5mins timeout @{
    private final DialogInterface.OnDismissListener mDismissListener = new DialogInterface.OnDismissListener() {
        public void onDismiss(DialogInterface dialog) {
            synchronized (mService) {
                if (mProc != null && mProc.crashDialog == AppErrorDialog.this) {
                    mProc.crashDialog = null;
                }
            }
            mResult.set(mResultType);
        }
    };
    /// @}
    
    /// M: BMW. Add error handling mechanism @{
    private CharSequence buildErrHandlingMessage(CharSequence lastMessage, int errHandlingRes, String packageName) {
        if (errHandlingRes == MultiWindowProxy.ERR_HANDLING_CONFIG_NOT_CHANGE) {
            return "System detect start floating window error and auto fixed!\nPlease try again!";
        } else if (errHandlingRes == MultiWindowProxy.ERR_HANDLING_MINIMAX_RESTART_APP) {
            return "System detect Max/Restore error and auto fixed!\nPlease try again!";
        } else if (errHandlingRes == MultiWindowProxy.ERR_HANDLING_DISABLE_FLOAT) {
            return "This apk(" + packageName + ") can not running on Floating mode!";
        }
        return lastMessage;
    }
    /// @}
}
