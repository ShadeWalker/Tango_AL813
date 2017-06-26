/*
 * Copyright Statement:
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2013. All rights reserved.
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
 */

package com.android.deskclock.alarms;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.text.TextUtils;

import com.android.deskclock.LogUtils;
import com.android.deskclock.provider.AlarmInstance;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("PMD")
public class PowerOffAlarm {

    /**M: @{
     * Whether this boot is from power off alarm or schedule power on or normal boot.
     * @return
     */
    static boolean bootFromPoweroffAlarm() {
        String bootReason = SystemProperties.get("sys.boot.reason");
        boolean ret = (bootReason != null && bootReason.equals("1")) ? true : false;
        return ret;
    }

    /**M: @{
     * copy ringtone music file to local from sd-card, to avoid power-off alarm
     * could not load the user set ringtone music. if have existed not the same
     * ringtone based on the file name then delete and copy the new one
     * there.
     */
    @SuppressWarnings("PMD")
    static void backupRingtoneForPoweroffAlarm(final Context ctx, final AlarmInstance nextAlarm) {
        LogUtils.v("backupRingtoneForPoweroffalarm ...... ");
        new Thread() {
            public void run() {
                String filepath = null;
                File existedRingtone = null;
                File files = ctx.getFilesDir();
                String nextRingtone = null;
                nextRingtone = getNearestAlarmWithExternalRingtone(ctx, nextAlarm);
                LogUtils.v("nextRingtone: " + nextRingtone);
                if (!TextUtils.isEmpty(nextRingtone) && null != files) {
                    String nextRingtoneName = getBackupFilename(ctx, nextAlarm.mRingtone);
                    // Judge if the alarm has already backup
                    if (bootFromPoweroffAlarm() && files.list().length > 0) {
                        for (File f : files.listFiles()) {
                            if (null != nextRingtoneName && nextRingtoneName.equals(f.getName())) {
                                LogUtils.v("The file already exist: " + f.getName());
                                return;
                            }
                        }
                    }

                    // Clean the unused backup ringtone
                    if (!bootFromPoweroffAlarm() && files.list().length > 1) {
                        cleanUnusedRingtone(files.listFiles(), nextRingtoneName);
                    }

                    if (files.isDirectory() && files.list().length == 1) {
                        for (File item : files.listFiles()) {
                            existedRingtone = item;
                        }
                    }
                    String existedRingtoneName = existedRingtone == null
                            ? null : existedRingtone.getName();
                    LogUtils.v("existedRingtoneName: " + existedRingtoneName
                            + " ,nextRingtoneName: " + nextRingtoneName);

                    if (!TextUtils.isEmpty(nextRingtoneName)
                            && !nextRingtoneName.equals(existedRingtoneName)
                            || existedRingtone == null || existedRingtone.length() == 0) {
                        if (!bootFromPoweroffAlarm() && existedRingtone != null
                                && !existedRingtone.delete()) {
                            LogUtils.v("delete existedRingtone error");
                        }
                        filepath = getRingtonePath(ctx, nextRingtone);
                        if (filepath != null) {
                            // copy from sd-card to local files directory.
                            String target = files.getAbsolutePath() + File.separator
                                    + nextRingtoneName;
                            try {
                                LogUtils.v("copy ringtone frome SD card to data/data");
                                copyFile(filepath, target);
                            } catch (IOException ex) {
                                if (existedRingtone != null && !bootFromPoweroffAlarm()) {
                                    if (existedRingtone.delete()) {
                                        LogUtils.v("Delete existedRingtone OK");
                                    } else {
                                        LogUtils.v("Delete existedRingtone error");
                                    }
                                }
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            }
        } .start();
    }

    /**M: @{
     * get the next will play alarm, whose ringtone is from external storage
     */
    public static String getNearestAlarmWithExternalRingtone(Context context, AlarmInstance nextAlarm) {
        String alert = null;
        if (nextAlarm != null && nextAlarm.mRingtone != null
                    && nextAlarm.mRingtone.toString().contains("external")) {
                alert = nextAlarm.mRingtone.toString();
            }
        return alert;
    }

    /**M: @{
     * get RingtonePath
     */
    public static String getRingtonePath(final Context mContext, final String alarmRingtone) {
        final ContentResolver cr = mContext.getContentResolver();
        String filepath = null;
        LogUtils.v("alarmRingtone: " + alarmRingtone);
        if (!TextUtils.isEmpty(alarmRingtone)) {
                Cursor c = null;
                try {
                    c = cr.query(Uri.parse(alarmRingtone), null,
                            null, null, null);
                    if (c != null && c.moveToFirst()) {
                        filepath = c.getString(1);
                    }
                } catch (SQLiteException e) {
                    LogUtils.v("database operation error: " + e.getMessage());
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
        }
        return filepath;
    }

    /**M: @{
     * copy one file from source to target
     * @param from source
     * @param to   target
     */
    private static int copyFile(String from, String to) throws IOException {
        LogUtils.v("source: " + from + "  target: " + to);
        int result = 0;
        if (TextUtils.isEmpty(from) || TextUtils.isEmpty(to)) {
            result = -1;
        }
        LogUtils.v("media mounted: " + Environment.getExternalStorageState());
        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            java.io.InputStream fis = null;
            java.io.OutputStream fos = null;
            try {
                fos = new java.io.FileOutputStream(to);
                try {
                    fis = new java.io.FileInputStream(from);
                    byte bt[] = new byte[1024];
                    int c;
                    while ((c = fis.read(bt)) > 0) {
                        fos.write(bt, 0, c);
                    }
                    fos.flush();
                } finally {
                    if (fis != null) {
                        fis.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                LogUtils.v("copy ringtone file error: " + e.toString());
                result = -1;
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }
        }
        return result;
    }

    public static void deleteRingtone(final Context context, final AlarmInstance currentInstance) {
        AsyncTask<Void, Void, Void> deleteRingTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                // double check if current instance exist
                if (null == currentInstance) {
                    LogUtils.v("DeleteRingtone but current instance is not exist");
                    return null;
                }

                File fileDir = context.getFilesDir();
                AlarmInstance nearestAlarm = AlarmStateManager.getNearestAlarm(context);
                String nearestAlarmUri = getNearestAlarmWithExternalRingtone(context, nearestAlarm);

                String nearestAlarmName = null;
                if (null != nearestAlarmUri) {
                    nearestAlarmName = getBackupFilename(context, nearestAlarm.mRingtone);
                }

                if (fileDir.isDirectory() && fileDir.list().length > 0) {
                    cleanUnusedRingtone(fileDir.listFiles(), nearestAlarmName);
                }
                return null;
            }
        };
        deleteRingTask.execute();
    }

    // delete all the backup ringtone except the filter one
    private static void cleanUnusedRingtone(File[] files, String fileNameFilter) {
        for (File file : files) {
            if (null != fileNameFilter && fileNameFilter.equals(file.getName())) {
                LogUtils.v("Do not delete the file: " + file);
                continue;
            }
            if (file.delete()) {
                LogUtils.v("Delete all file OK: " + file);
            } else {
                LogUtils.v("Delete all file FAILED: " + file);
            }
        }
    }

    private static IMountService getMountService() {
        final IBinder service = ServiceManager.getService("mount");
        if (service != null) {
            return IMountService.Stub.asInterface(service);
        }
        return null;
    }

    // Determines the type of the encryption password
    public static int getPasswordType() {
        int type = StorageManager.CRYPT_TYPE_PASSWORD;
        IMountService service = getMountService();
        try {
            type = service.getPasswordType();
        } catch (RemoteException e) {
            LogUtils.e("Error getPasswordType " + e);
        }
        return type;
    }

    // Whether the device was Unencrypted.
    static boolean deviceUnencrypted() {
        return "unencrypted".equals(SystemProperties.get("ro.crypto.state"));
    }

    /**
     * Only the following two conditions should enable power off alarm:
     * 1.If the device is tablet and the user is tablet owner, enable power off alarm.
     * 2.If property ro.crypto.state is unencrypted or getPasswordType() == CRYPT_TYPE_DEFAULT.
     *
     * Other conditions should disable power off alarm.
     */
    public static boolean canEnablePowerOffAlarm() {
        boolean enabled = (UserHandle.myUserId() == UserHandle.USER_OWNER)
                && (deviceUnencrypted() || StorageManager.CRYPT_TYPE_DEFAULT == getPasswordType());
        LogUtils.v("Power Off Alarm enabled: " + enabled);
        return enabled;
    }

    public static String getBackupFilename(Context context, Uri uri) {
        if (null == uri) {
            LogUtils.i("Uri is null");
            return null;
        }

        String backupFile = null;
        String externalUri = uri.toString().contains("external") ? uri.toString() : null;
        if (!TextUtils.isEmpty(externalUri)) {
            backupFile = externalUri.substring(externalUri.lastIndexOf(File.separator) + 1);
        }

        LogUtils.i("getBackupFilename: " + backupFile);
        return backupFile;
    }
}
