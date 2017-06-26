/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
package com.mediatek.contacts.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import com.mediatek.storage.StorageManagerEx;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountSelectionUtil.AccountSelectedListener;
import com.android.contacts.common.vcard.ImportVCardActivity;
import com.android.contacts.common.vcard.NotificationImportExportListener;
import com.android.contacts.common.vcard.VCardService;

public class VcardUtils {

    private static final String LOG_TAG = "VcardUtils";

    public static void setDestSelection(VCardService service, Activity activity) {
        service.setQuerySelection(activity.getIntent().getStringExtra("exportselection"));
        String destStoragePath = activity.getIntent().getStringExtra("dest_path");
        if (destStoragePath != null) {
            LogUtils.d(LOG_TAG, "The destination storage path is " + destStoragePath);
            service.setDestStoragePath(destStoragePath);
        }
    }

    /**
     * Get the description path which is a description of the file system path.
     * eg: "Phone Storage" is stand for "/storage/sdcard0"
     * CR: ALPS00384104 @{
     */
    public static String getSaveFilePathDescription(String path, Activity activity) {
        StorageManager mSM = (StorageManager) activity.getApplicationContext().getSystemService(
                Context.STORAGE_SERVICE);
        if (null == mSM) {
            LogUtils.e(LOG_TAG, "Failed to get StorageManager");
            return path;
        }
        StorageVolume volumes[] = mSM.getVolumeList();
        for (StorageVolume storageVolume : volumes) {
            String saveFilePath = storageVolume.getPath();
            String saveFilePathDescription = storageVolume.getDescription(activity);
            LogUtils.d(LOG_TAG, "path: " + saveFilePath + " , " +
                    "description: " + saveFilePathDescription);
            if (path.startsWith(saveFilePath)) {
                return path.replace(saveFilePath, saveFilePathDescription);
            }
        }
        LogUtils.e(LOG_TAG, "Not found volume for path: " + path);
        return path;
    }

    /** Error handling */
    public static void showErrorInfo(final int resId, final Activity activity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MtkToast.toast(activity.getApplicationContext(), resId);
                activity.finish();
            }
        });
    }

    private static String sSimAccountType = AccountTypeUtils.ACCOUNT_TYPE_SIM;

    private static String sUsimAccountType = AccountTypeUtils.ACCOUNT_TYPE_USIM;
    //UIM
    private static String sUimAccountType = AccountTypeUtils.ACCOUNT_TYPE_UIM;
    private static String sCsimAccountType = AccountTypeUtils.ACCOUNT_TYPE_CSIM;
    //UIM
    //public static final String ACCOUNT_TYPE_SERVICE = "contactAccountTypes";
    /// @}

    public static List<AccountWithDataSet> addNonSimAccount(
            final List<AccountWithDataSet> accountList) {
        List<AccountWithDataSet> myAccountlist = new ArrayList<AccountWithDataSet>();
        int k = 0;
        for (int i = 0; i < accountList.size(); i++) {
            AccountWithDataSet account1 = accountList.get(i);
            // UIM
            if (!account1.type.equals(sSimAccountType) && !account1.type.equals(sUsimAccountType)
                    && !account1.type.equals(sUimAccountType)
                    && !account1.type.equals(sCsimAccountType)) {
                // UIM
                LogUtils.d("sdcard", "account1.type : " + account1.type);
                myAccountlist.add(accountList.get(i));
            }
        }
        LogUtils.d("sdcard", "accountlist1.size() : " + myAccountlist.size());
        return myAccountlist;
    }

    public static Dialog getSelectAccountDialog(Context context, int resId,
            DialogInterface.OnClickListener onClickListener,
            DialogInterface.OnCancelListener onCancelListener) {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(context);
        final List<AccountWithDataSet> writableAccountList = accountTypes.getAccounts(true);

        LogUtils.d(LOG_TAG, "***The number of available accounts: " + writableAccountList.size());
        int k = 0;
        List<AccountWithDataSet> accountlist1 = new ArrayList<AccountWithDataSet>();
        for (int i = 0; i < writableAccountList.size(); i++) {
            AccountWithDataSet account1 = writableAccountList.get(i);
            // UIM
            if (!account1.type.equals(sSimAccountType) && !account1.type.equals(sUsimAccountType)
                    && !account1.type.equals(sUimAccountType)
                    && !account1.type.equals(sCsimAccountType)) {
                // UIM
                LogUtils.d("sdcard", "account1.type : " + account1.type);
                accountlist1.add(writableAccountList.get(i));
            }
        }
        // Assume accountList.size() > 1

        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(context, android.R.style.Theme_Light);
        final LayoutInflater dialogInflater = (LayoutInflater) dialogContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ArrayAdapter<AccountWithDataSet> accountAdapter =
            new ArrayAdapter<AccountWithDataSet>(
                context, R.layout.mtk_selectaccountactivity, accountlist1) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(R.layout.mtk_selectaccountactivity,
                            parent, false);
                }

                // TODO: show icon along with title
                final TextView text1 = (TextView) convertView.findViewById(android.R.id.text1);
                final TextView text2 = (TextView) convertView.findViewById(android.R.id.text2);

                final AccountWithDataSet account = this.getItem(position);
                final AccountType accountType = accountTypes.getAccountType(account.type,
                        account.dataSet);
                final Context context = getContext();

                text1.setText(account.name);
                text2.setText(accountType.getDisplayLabel(context));

                return convertView;
            }
        };

        if (onClickListener == null) {
            AccountSelectedListener accountSelectedListener = new AccountSelectedListener(context,
                    writableAccountList, resId);
            onClickListener = accountSelectedListener;
        }
        if (onCancelListener == null) {
            onCancelListener = new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    dialog.dismiss();
                }
            };
        }
        return new AlertDialog.Builder(context).setTitle(R.string.dialog_new_contact_account)
                .setSingleChoiceItems(accountAdapter, 0, onClickListener).setOnCancelListener(
                        onCancelListener).create();
    }

    public static File getDirectory(String path, String defaultPath) {
        LogUtils.d("getDirectory", "path : " + path);
        return path == null ? new File(defaultPath) : new File(path);
    }
    
    /** M: Bug Fix, CR ID: ALPS00301464 */
    public static String getPath(String sourcePath) {
        String path = null;
        if (sourcePath != null) {
            path = sourcePath;
        } else {
            path = StorageManagerEx.getDefaultPath();
        }
        return path;
    }
    
    /**
     * M: New Feature, CR ID: ALPS00276020
     * Descriptions: Support multiple storages for Contacts import/export function
     */
    public static String getVolumeName(String path, Activity activity) {
        StorageManager sm = (StorageManager) activity.getApplicationContext().getSystemService(Context.STORAGE_SERVICE);
        StorageVolume volumes[] = sm.getVolumeList();
        String vName = null;
        
        if (volumes != null) {
            for (StorageVolume volume : volumes) {
                if (path != null && volume.getPath().equals(path)) {
                    vName = volume.getDescription(activity);
                    LogUtils.d(LOG_TAG, "[doScanExternalStorageAndImportVCard] mVolumeName : " + vName);
                }
            }
        }      
        return vName;
    }

    /**
     * Bug fix ALPS00598462.
     * @param context Context
     * @param reason String
     * @param displayName String
     * @param fileIndex int
     * @param handler Handler
     */
    public static void showFailureNotification(final Context context, String reason,
            String displayName, int fileIndex, Handler handler) {

        final String displayTitle = context.getString(R.string.vcard_import_failed_v2) + " " + displayName;
        final NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        final Notification notification = constructImportFailureNotification(context, reason,
                displayTitle);
        notificationManager.notify(NotificationImportExportListener.FAILURE_NOTIFICATION_TAG,
                fileIndex, notification);
        handler.post(new Runnable() {
            @Override
            public void run() {
                /// Modify the toast last a long time issue.
                MtkToast.toast(context, displayTitle, Toast.LENGTH_LONG);
            }
        });
    }

    /**
     * Bug fix ALPS00598462, this is a extension of
     * NotificationImportExportListener.constructImportFailureNotification.
     * @param context Context
     * @param reason String
     * @param title String
     * @return Notification
     */
    public static Notification constructImportFailureNotification(
            Context context, String reason , String title) {
        return new Notification.Builder(context)
                .setAutoCancel(true)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(reason)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(), 0))
                .getNotification();
    }
}
