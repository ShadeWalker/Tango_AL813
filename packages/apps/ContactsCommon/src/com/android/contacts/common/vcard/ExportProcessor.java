/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.common.vcard;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.R;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.util.LogUtils;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Class for processing one export request from a user. Dropped after exporting requested Uri(s).
 * {@link VCardService} will create another object when there is another export request.
 */
public class ExportProcessor extends ProcessorBase {
    private static final String LOG_TAG = "VCardExport";
    private static final boolean DEBUG = VCardService.DEBUG;

    private final VCardService mService;
    private final ContentResolver mResolver;
    private final NotificationManager mNotificationManager;
    private final ExportRequest mExportRequest;
    private final int mJobId;
    private final String mCallingActivity;


    private volatile boolean mCanceled;
    private volatile boolean mDone;
    /** M: Change Feature Descriptions: handle cancel in the cancel function */
    private volatile boolean mIsRunning = false;
    /** M: New Feature All of process should not be stopped by PM. */
    private PowerManager.WakeLock mWakeLock;
    private static final BidiFormatter mBidiFormatter = BidiFormatter.getInstance();

    public ExportProcessor(VCardService service, ExportRequest exportRequest, int jobId,
            String callingActivity) {
        mService = service;
        mResolver = service.getContentResolver();
        mNotificationManager =
                (NotificationManager)mService.getSystemService(Context.NOTIFICATION_SERVICE);
        mExportRequest = exportRequest;
        mJobId = jobId;
        mCallingActivity = callingActivity;

        /** M: New Feature, all of process should not be stopped by PM. @{ */
        final PowerManager powerManager = (PowerManager) mService.getApplicationContext()
                .getSystemService("power");
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                | PowerManager.ON_AFTER_RELEASE, LOG_TAG);
        /** @} */
    }

    @Override
    public final int getType() {
        return VCardService.TYPE_EXPORT;
    }

    @Override
    public void run() {
        /** M: Change Feature, handle cancel in the cancel function */
        mIsRunning = true;
        /** M: New Feature, all of process should not be stopped by PM.*/
        mWakeLock.acquire();

        // ExecutorService ignores RuntimeException, so we need to show it here.
        try {
            runInternal();

            if (isCancelled()) {
                doCancelNotification();
            }
        } catch (OutOfMemoryError e) {
            Log.e(LOG_TAG, "OutOfMemoryError thrown during import", e);
            throw e;
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "RuntimeException thrown during export", e);
            throw e;
        } finally {
            synchronized (this) {
                mDone = true;
            }
            /** M: New Feature, all of process should not be stopped by PM. @{*/
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
            /** @} */
        }
    }

    private void runInternal() {
        if (DEBUG) Log.d(LOG_TAG, String.format("vCard export (id: %d) has started.", mJobId));
        final ExportRequest request = mExportRequest;
        VCardComposer composer = null;
        Writer writer = null;
        boolean successful = false;
        try {
            if (isCancelled()) {
                Log.i(LOG_TAG, "Export request is cancelled before handling the request");
                return;
            }
            final Uri uri = request.destUri;
            final OutputStream outputStream;
            try {
                outputStream = mResolver.openOutputStream(uri);
            } catch (FileNotFoundException e) {
                Log.w(LOG_TAG, "FileNotFoundException thrown", e);
                // Need concise title.

                final String errorReason =
                    mService.getString(R.string.fail_reason_could_not_open_file,
                            uri, e.getMessage());
                doFinishNotification(errorReason, null);
                return;
            }

            final String exportType = request.exportType;
            final int vcardType;
            if (TextUtils.isEmpty(exportType)) {
                vcardType = VCardConfig.getVCardTypeFromString(
                        mService.getString(R.string.config_export_vcard_type));
            } else {
                vcardType = VCardConfig.getVCardTypeFromString(exportType);
            }
            /** M: Add for import/export group infomations feature */
            /// M: [COMMD_FOR_OP09] @{
            composer = ExtensionManager.getInstance().getImportExportExtension()
                    .getVCardComposerExt(mService, vcardType, true);
            /// @}


            // for test
            // int vcardType = (VCardConfig.VCARD_TYPE_V21_GENERIC |
            //     VCardConfig.FLAG_USE_QP_TO_PRIMARY_PROPERTIES);
            // composer = new VCardComposer(ExportVCardActivity.this, vcardType, true);

            writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            final Uri contentUriForRawContactsEntity = RawContactsEntity.CONTENT_URI;
            // TODO: should provide better selection.
            /** M: New Feature @{
             * Description: The multiple contacts could be selected to export to SD card.
             */
            String querySelection = null;
            if (mService.getQuerySelection() != null && mService.getQuerySelection().length() != 0) {
                querySelection = mService.getQuerySelection();
            }
            if (!composer.init(Contacts.CONTENT_URI, new String[] {Contacts._ID},
                    querySelection, null, null, contentUriForRawContactsEntity)) {
            /** @} */
                final String errorReason = composer.getErrorReason();
                Log.e(LOG_TAG, "initialization of vCard composer failed: " + errorReason);
                final String translatedErrorReason =
                        translateComposerError(errorReason);
                final String title =
                        mService.getString(R.string.fail_reason_could_not_initialize_exporter,
                                translatedErrorReason);
                doFinishNotification(title, null);
                return;
            }

            final int total = composer.getCount();
            if (total == 0) {
                final String title =
                        mService.getString(R.string.fail_reason_no_exportable_contact);
                doFinishNotification(title, null);
                return;
            }

            int current = 1;  // 1-origin
            while (!composer.isAfterLast()) {
                if (isCancelled()) {
                    Log.i(LOG_TAG, "Export request is cancelled during composing vCard");
                    return;
                }
                try {
                    writer.write(composer.createOneEntry());
                    /// M:
                    writer.flush();
                   } catch (IOException e) {
                    final String errorReason = composer.getErrorReason();
                    /// M: @{
                    Log.e(LOG_TAG, "Failed to read a contact: " + errorReason);
                    final String ioError = e.getMessage();
                    LogUtils.e(LOG_TAG, "exception: " + ioError);
                    String des = null;
                    String title;
                    if (ioError != null && ioError.indexOf("ENOSPC") >= 0) {
                        title = mService.getResources().getString(R.string.storage_full);
                        des = mService.getString(R.string.notifier_multichoice_process_report,
                                current - 1, total - (current - 1));
                    } else {
                        final String translatedErrorReason = translateComposerError(errorReason);
                        title = mService.getString(
                                R.string.fail_reason_error_occurred_during_export,
                                translatedErrorReason);
                    }
                    doFinishNotification(title, des);
                    /// @}
                    return;
                }

                // vCard export is quite fast (compared to import), and frequent notifications
                // bother notification bar too much.
                if (current % 100 == 1) {
                    doProgressNotification(uri, total, current);
                }
                current++;
            }
            Log.i(LOG_TAG, "Successfully finished exporting vCard " + request.destUri);

            if (DEBUG) {
                Log.d(LOG_TAG, "Ask MediaScanner to scan the file: " + request.destUri.getPath());
            }
            mService.updateMediaScanner(request.destUri.getPath());

            successful = true;
            final String filename = uri.getLastPathSegment();
            final String title = mService.getString(R.string.exporting_vcard_finished_title,
            		filename);//modify by wangmingyue for 
            doFinishNotification(title, null);
        } finally {
            if (composer != null) {
                composer.terminate();
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.w(LOG_TAG, "IOException is thrown during close(). Ignored. " + e);
                }
            }
            mService.handleFinishExportNotification(mJobId, successful);
        }
    }

    private String translateComposerError(String errorMessage) {
        final Resources resources = mService.getResources();
        if (VCardComposer.FAILURE_REASON_FAILED_TO_GET_DATABASE_INFO.equals(errorMessage)) {
            return resources.getString(R.string.composer_failed_to_get_database_infomation);
        } else if (VCardComposer.FAILURE_REASON_NO_ENTRY.equals(errorMessage)) {
            return resources.getString(R.string.composer_has_no_exportable_contact);
        } else if (VCardComposer.FAILURE_REASON_NOT_INITIALIZED.equals(errorMessage)) {
            return resources.getString(R.string.composer_not_initialized);
        } else {
            /** M: Bug Fix for ALPS00413434 @{ */
            return resources.getString(R.string.generic_failure);
            /** @} */
        }
    }

    private void doProgressNotification(Uri uri, int totalCount, int currentCount) {
        final String displayName = uri.getLastPathSegment();
        final String description =
                mService.getString(R.string.exporting_contact_list_message, displayName);
        final String tickerText =
                mService.getString(R.string.exporting_contact_list_title);
        final Notification notification =
                NotificationImportExportListener.constructProgressNotification(mService,
                        VCardService.TYPE_EXPORT, description, tickerText, mJobId, displayName,
                        totalCount, currentCount);
        mNotificationManager.notify(NotificationImportExportListener.DEFAULT_NOTIFICATION_TAG,
                mJobId, notification);
    }

    private void doCancelNotification() {
        if (DEBUG) Log.d(LOG_TAG, "send cancel notification");
        final String description = mService.getString(R.string.exporting_vcard_canceled_title,
                mExportRequest.destUri.getLastPathSegment());
        final Notification notification =
                NotificationImportExportListener.constructCancelNotification(mService, description);
        mNotificationManager.notify(NotificationImportExportListener.DEFAULT_NOTIFICATION_TAG,
                mJobId, notification);
    }

    private void doFinishNotification(final String title, final String description) {
        if (DEBUG) Log.d(LOG_TAG, "send finish notification: " + title + ", " + description);
        final Intent intent = new Intent();
        intent.setClassName(mService, mCallingActivity);
        final Notification notification =
                /// M:
                NotificationImportExportListener.constructFinishNotification(VCardService.TYPE_EXPORT, mService, title,
                        description, intent);
        mNotificationManager.notify(NotificationImportExportListener.DEFAULT_NOTIFICATION_TAG,
                mJobId, notification);
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (DEBUG) Log.d(LOG_TAG, "received cancel request");
        if (mDone || mCanceled) {
            return false;
        }
        mCanceled = true;
        /** M: Change Feature @{
         *  Descriptions: handle cancel in the cancel function
         */
        if (!mIsRunning) {
            doCancelNotification();
        }
        /** @} */
        return true;
    }

    @Override
    public synchronized boolean isCancelled() {
        return mCanceled;
    }

    @Override
    public synchronized boolean isDone() {
        return mDone;
    }

    public ExportRequest getRequest() {
        return mExportRequest;
    }
}
