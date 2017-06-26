
package com.mediatek.contacts.list.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import com.mediatek.contacts.ExtensionManager;
import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity;

import com.mediatek.contacts.util.ContactsIntent;
import com.mediatek.contacts.util.ErrorCause;
import com.mediatek.contacts.util.LogUtils;

import com.mediatek.contacts.ext.ICtExtension.StringName;

import android.os.Build;

public class MultiChoiceHandlerListener {
    private static final String TAG = "MultiChoiceHandlerListener";

    /**
     * the key used for {@link #onFinished(int, int, int)} called time.
     * This is help the test case to get the really time finished.
     */

    public static final String KEY_FINISH_TIME = "key_finish_time";

    static final String DEFAULT_NOTIFICATION_TAG = "MultiChoiceServiceProgress";

    static final String FAILURE_NOTIFICATION_TAG = "MultiChoiceServiceFailure";

    private final NotificationManager mNotificationManager;

    // context should be the object of MultiChoiceService
    private final Service mContext;

    public MultiChoiceHandlerListener(Service service) {
        mContext = service;
        mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }

    synchronized void onProcessed(final int requestType, final int jobId, final int currentCount,
            final int totalCount, final String contactName) {

        if (currentCount % 10 != 0 && currentCount != 1 && currentCount != totalCount) {
            Log.d(TAG, "onProcessed return");
            return;
        }

        final String totalCountString = String.valueOf(totalCount);
        final String tickerText;
        final String description;
        int statIconId = 0;
        if (requestType == MultiChoiceService.TYPE_DELETE) {
            tickerText = mContext.getString(R.string.notifier_progress_delete_message, String
                    .valueOf(currentCount), totalCountString, contactName);
            if (totalCount == -1) {
                description = mContext.getString(R.string.notifier_progress__delete_will_start_message);
            } else {
                description = mContext.getString(R.string.notifier_progress_delete_description,
                        contactName);
            }
            statIconId = android.R.drawable.ic_menu_delete;
        } else {
            tickerText = mContext.getString(R.string.notifier_progress_copy_message, String
                    .valueOf(currentCount), totalCountString, contactName);
            if (totalCount == -1) {
                description = mContext.getString(R.string.notifier_progress__copy_will_start_message);
            } else {
                description = mContext.getString(R.string.notifier_progress_copy_description,
                        contactName);
            }
            statIconId = R.drawable.mtk_ic_menu_copy_holo_dark;
        }
		if(Build.TYPE.equals("eng")) {
			Log.d(TAG, "onProcessed notify DEFAULT_NOTIFICATION_TAG1: " + description);
		}
        final Notification notification = constructProgressNotification(mContext
                .getApplicationContext(), requestType, description, tickerText, jobId, totalCount,
                currentCount, statIconId);
        mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
		if(Build.TYPE.equals("eng")) {
			Log.d(TAG, "onProcessed notify DEFAULT_NOTIFICATION_TAG2: " + description);
		}
    }

    synchronized void onFinished(final int requestType, final int jobId, final int total) {
        long currentTimeMillis = System.currentTimeMillis();
        LogUtils.d(TAG, "[onFinished] jobId = " + jobId + " total = " + total + " requestType = "
                + requestType);
        long endTime = System.currentTimeMillis();
        LogUtils.d(TAG, "[CMCC Performance test][Contacts] delete 1500 contacts end [" + endTime + "]");
        // Dismiss MultiChoiceConfirmActivity
        Intent i = new Intent().setAction(ContactsIntent.MULTICHOICE.ACTION_MULTICHOICE_PROCESS_FINISH);
        i.putExtra(KEY_FINISH_TIME, currentTimeMillis);
        mContext.sendBroadcast(i);
        i = null;

        final String title;
        final String description;
        final int statIconId;

        if (requestType == MultiChoiceService.TYPE_DELETE) {
            // A good experience is to cache the resource.
            title = mContext.getString(R.string.notifier_finish_delete_title);
            description = mContext.getString(R.string.notifier_finish_delete_content, total);
            // statIconId = R.drawable.ic_stat_delete;
            statIconId = android.R.drawable.ic_menu_delete;
        } else {
            title = mContext.getString(R.string.notifier_finish_copy_title);
            description = mContext.getString(R.string.notifier_finish_copy_content, total);
            statIconId = R.drawable.mtk_ic_menu_copy_holo_dark;
        }

        final Intent intent = new Intent(mContext, PeopleActivity.class);
        final Notification notification = constructFinishNotification(mContext, title, description,
                intent, statIconId);
        mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
        Log.d(TAG, "onFinished notify DEFAULT_NOTIFICATION_TAG");
    }

    synchronized void onFailed(final int requestType, final int jobId, final int total, final int succeeded,
            final int failed) {
        LogUtils.d(TAG, "[onFailed] requestType =" + requestType + " jobId = " + jobId + " total = "
                + total + " succeeded = " + succeeded + " failed = " + failed);
        final int titleId;
        final int contentId;
        if (requestType == MultiChoiceService.TYPE_DELETE) {
            titleId = R.string.notifier_fail_delete_title;
            contentId = R.string.notifier_multichoice_process_report;
        } else {
            titleId = R.string.notifier_fail_copy_title;
            contentId = R.string.notifier_multichoice_process_report;
        }
        /*
         * Bug Fix by Mediatek Begin.
         *   Original Android's code:
         *     xxx
         *   CR ID: ALPS00251890
         *   Descriptions:
         */
        /**
         * M: fixed CR ALPS00783536 @{
         */
        ReportDialogInfo reportDialogInfo = new ReportDialogInfo();
        reportDialogInfo.setmTitleId(titleId);
        reportDialogInfo.setmContentId(contentId);
        reportDialogInfo.setmJobId(jobId);
        reportDialogInfo.setmTotalNumber(total);
        reportDialogInfo.setmSucceededNumber(succeeded);
        reportDialogInfo.setmFailedNumber(failed);
        /** @} */
        final Notification notification = constructReportNotification(mContext, reportDialogInfo);
        /*
         * Bug Fix by Mediatek End.
         */
        mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
        Log.d(TAG, "onFailed onProcessed notify DEFAULT_NOTIFICATION_TAG");

    }

    synchronized void onFailed(final int requestType, final int jobId, final int total, final int succeeded,
            final int failed, final int errorCause) {
        LogUtils.d(TAG, "[onFailed] requestType =" + requestType + " jobId = " + jobId + " total = "
                + total + " succeeded = " + succeeded + " failed = " + failed + " errorCause = "
                + errorCause + " ");
        int titleId;
        final int contentId;
        if (requestType == MultiChoiceService.TYPE_DELETE) {
            titleId = R.string.notifier_fail_delete_title;
            contentId = R.string.notifier_multichoice_process_report;
        } else {
            titleId = R.string.notifier_fail_copy_title;
            if (errorCause == ErrorCause.SIM_NOT_READY) {
                int notifierFailureSimNotready = R.string.notifier_failure_sim_notready;
                contentId = notifierFailureSimNotready;
            } else if (errorCause == ErrorCause.SIM_STORAGE_FULL) {
                int notifierFailureBySimFull = R.string.notifier_failure_by_sim_full;
                contentId = notifierFailureBySimFull;
            } else if (errorCause == ErrorCause.ERROR_USIM_EMAIL_LOST) {
                if (failed == 0) {
                    titleId = R.string.notifier_finish_copy_title;
                }
                contentId = R.string.error_import_usim_contact_email_lost;
            } else {
                contentId = R.string.notifier_multichoice_process_report;
            }
        }
        /*
         * Bug Fix by Mediatek Begin.
         *   Original Android's code:
         *     xxx
         *   CR ID: ALPS00251890
         *   Descriptions:
         */
        /**
         * M: fixed CR ALPS00783536 @{
         */
        ReportDialogInfo reportDialogInfo = new ReportDialogInfo();
        reportDialogInfo.setmTitleId(titleId);
        reportDialogInfo.setmContentId(contentId);
        reportDialogInfo.setmJobId(jobId);
        reportDialogInfo.setmTotalNumber(total);
        reportDialogInfo.setmSucceededNumber(succeeded);
        reportDialogInfo.setmFailedNumber(failed);
        reportDialogInfo.setmErrorCauseId(errorCause);
        /** @} */
        final Notification notification = constructReportNotification(mContext, reportDialogInfo);
        /*
         * Bug Fix by Mediatek End.
         */
        mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
        Log.d(TAG, "onFailed onProcessed notify DEFAULT_NOTIFICATION_TAG");

    }

    synchronized void onCanceled(final int requestType, final int jobId, final int total, final int succeeded,
            final int failed) {
        LogUtils.d(TAG, "[onCanceled] requestType =" + requestType + " jobId = " + jobId + " total = "
                + total + " succeeded = " + succeeded + " failed = " + failed);
        final int titleId;
        final int contentId;
        if (requestType == MultiChoiceService.TYPE_DELETE) {
            titleId = R.string.notifier_cancel_delete_title;
        } else {
            titleId = R.string.notifier_cancel_copy_title;
        }
        if (total != -1) {
            contentId = R.string.notifier_multichoice_process_report;
        } else {
            contentId = -1;
        }
        /*
         * Bug Fix by Mediatek Begin.
         *   Original Android's code:
         *     xxx
         *   CR ID: ALPS00251890
         *   Descriptions:
         */

        /**
         * M: fixed CR ALPS00783536 @{
         */
        ReportDialogInfo reportDialogInfo = new ReportDialogInfo();
        reportDialogInfo.setmTitleId(titleId);
        reportDialogInfo.setmContentId(contentId);
        reportDialogInfo.setmJobId(jobId);
        reportDialogInfo.setmTotalNumber(total);
        reportDialogInfo.setmSucceededNumber(succeeded);
        reportDialogInfo.setmFailedNumber(failed);
        /** @} */
        final Notification notification = constructReportNotification(mContext, reportDialogInfo);
        /*
         * Bug Fix by Mediatek End.
         */
        mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
		if(Build.TYPE.equals("eng")) {
			Log.d(TAG, "onCanceled onProcessed notify DEFAULT_NOTIFICATION_TAG: " + mContext.getString(titleId));
		}
    }
    /*
     * Bug Fix by Mediatek Begin.
     *   Original Android's code:
     *     xxx
     *   CR ID: ALPS00249590
     *   Descriptions:
     */
    synchronized void onCanceling(final int requestType, final int jobId) {
        LogUtils.i(TAG, "[onCanceling] requestType : " + requestType + " | jobId : " + jobId);
        final String description;
        int statIconId = 0;
        if (requestType == MultiChoiceService.TYPE_DELETE) {
            description = mContext.getString(R.string.multichoice_confirmation_title_delete);
            statIconId = android.R.drawable.ic_menu_delete;
        } else {
            description = "";
        }

        final Notification notification = constructCancelingNotification(mContext, description,
                jobId, statIconId);
        mNotificationManager.notify(DEFAULT_NOTIFICATION_TAG, jobId, notification);
        //Log.d(TAG, "onCanceling onProcessed notify DEFAULT_NOTIFICATION_TAG: " + description);
    }
    /*
     * Bug Fix by Mediatek End.
     */
    /**
     * Constructs a Notification telling users the process is finished.
     *
     * @param context
     * @param title
     * @param description Content of the Notification
     * @param intent Intent to be launched when the Notification is clicked. Can
     *            be null.
     * @param statIconId
     */
    public static Notification constructFinishNotification(Context context, String title,
            String description, Intent intent, final int statIconId) {
        return new Notification.Builder(context).setAutoCancel(true).setSmallIcon(statIconId)
                .setContentTitle(title).setContentText(description).setTicker(
                        title + "\n" + description).setContentIntent(
                        PendingIntent.getActivity(context, 0, (intent != null ? intent
                                : new Intent()), 0)).getNotification();
    }

    /**
     * Constructs a {@link Notification} showing the current status of
     * import/export. Users can cancel the process with the Notification.
     *
     * @param context The service of MultichoiceService
     * @param requestType delete
     * @param description Content of the Notification.
     * @param tickerText
     * @param jobId
     * @param totalCount The number of vCard entries to be imported. Used to
     *            show progress bar. -1 lets the system show the progress bar
     *            with "indeterminate" state.
     * @param currentCount The index of current vCard. Used to show progress
     *            bar.
     * @param statIconId
     */
    public static Notification constructProgressNotification(Context context, int requestType,
            String description, String tickerText, int jobId, int totalCount, int currentCount,
            int statIconId) {

        Intent cancelIntent = new Intent(context, MultiChoiceConfirmActivity.class);
        cancelIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        cancelIntent.putExtra(MultiChoiceConfirmActivity.JOB_ID, jobId);
        cancelIntent.putExtra(MultiChoiceConfirmActivity.ACCOUNT_INFO, "TODO finish");
        cancelIntent.putExtra(MultiChoiceConfirmActivity.TYPE, requestType);

        final Notification.Builder builder = new Notification.Builder(context);
//        builder.setOngoing(true).setProgress(totalCount, currentCount, totalCount == -1).setTicker(
//                tickerText).setContentTitle(description).setSmallIcon(statIconId).setContentIntent(
//                PendingIntent.getActivity(context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        builder.setOngoing(true).setProgress(totalCount, currentCount, totalCount == -1)
                .setContentTitle(description).setSmallIcon(statIconId).setContentIntent(
                        PendingIntent.getActivity(context, jobId, cancelIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT));
        if (totalCount > 0) {
            builder.setContentText(context.getString(R.string.percentage, String
                    .valueOf(currentCount * 100 / totalCount)));
        }
        return builder.getNotification();
    }

    /**
     * Constructs a Notification telling users the process is canceled.
     *
     * @param context
     * @param description Content of the Notification
     */
    /*
     * Bug Fix by Mediatek Begin.
     *   Original Android's code:
     *     xxx
     *   CR ID: ALPS00251890
     *   Descriptions: add int jobId
     */

    public static Notification constructReportNotification(Context context, ReportDialogInfo reportDialogInfo) {
        Intent reportIntent = new Intent(context, MultiChoiceConfirmActivity.class);
        reportIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        reportIntent.putExtra(MultiChoiceConfirmActivity.REPORTDIALOG, true);
        reportIntent.putExtra(MultiChoiceConfirmActivity.REPORT_DIALOG_INFO, reportDialogInfo);

        /**
         * M: fixed CR ALPS00783536 @{
         */
        String title;
        String content;
        int titleId = reportDialogInfo.mTitleId;
        int contentId = reportDialogInfo.mContentId;
        int totalNumber = reportDialogInfo.mTotalNumber;
        int succeededNumber = reportDialogInfo.mSucceededNumber;
        int failedNumber = reportDialogInfo.mFailedNumber;
        int jobIdNumber = reportDialogInfo.mJobId;
        int errorCauseId = reportDialogInfo.mErrorCauseId;

        if ((errorCauseId == ErrorCause.ERROR_USIM_EMAIL_LOST) && (failedNumber == 0)) {
            title = context.getString(titleId);
        } else {
            title = context.getString(titleId, totalNumber);
        }

        if (contentId == -1) {
            content = "";
        } else {
            content = context.getString(contentId, succeededNumber, failedNumber);
        }
        /** @} */

        ///M:[for op09] shown different shtring in op09 project@{
        String ctString = null;
        if (contentId == R.string.notifier_failure_sim_notready) {
            ctString = ExtensionManager.getInstance().getCtExtension()
                    .getString(StringName.NOTIFIER_FAILURE_SIM_NOTREADY);
        } else if (contentId == R.string.notifier_failure_by_sim_full) {
            ctString = ExtensionManager.getInstance().getCtExtension()
                    .getString(StringName.NOTIFIER_FAILURE_BY_SIM_FULL);
        }
        if (!TextUtils.isEmpty(ctString)) {
            content = ctString;
        }
        ///@}

        if (content == null || content.isEmpty()) {
            return new Notification.Builder(context).setAutoCancel(true).setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle(title).setTicker(title).setContentIntent(
                            PendingIntent.getActivity(context, jobIdNumber, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT))
                    .getNotification();
        } else {
            return new Notification.Builder(context).setAutoCancel(true).setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle(title).setContentText(content).setTicker(title + "\n" + content).setContentIntent(
                            PendingIntent.getActivity(context, jobIdNumber, reportIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                    .getNotification();
        }
    }
    /*
     * Bug Fix by Mediatek End.
     */
    /*
     * Bug Fix by Mediatek Begin.
     *   Original Android's code:
     *     xxx
     *   CR ID: ALPS00249590
     *   Descriptions:
     */
    public static Notification constructCancelingNotification(Context context, String description,
            int jobId, int statIconId) {

        final Notification.Builder builder = new Notification.Builder(context);
        builder.setOngoing(true).setProgress(-1, -1, true).setContentTitle(description)
                .setSmallIcon(statIconId).setContentIntent(
                        PendingIntent.getActivity(context, jobId, new Intent(),
                                PendingIntent.FLAG_UPDATE_CURRENT));

        return builder.getNotification();
    }
    /*
     * Bug Fix by Mediatek End.
     */

    //visible for test
    public void  cancelAllNotifition() {
        mNotificationManager.cancelAll();
        Log.d(TAG, "mNotificationManager.cancelAll()");
    }

    /**
     * M: fixed CR ALPS00783536 @{
     */
    public static class ReportDialogInfo implements Parcelable {

        private int mTitleId;
        private int mContentId;
        private int mJobId;
        private int mErrorCauseId = -1;
        private int mTotalNumber;
        private int mSucceededNumber;
        private int mFailedNumber;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mTitleId);
            dest.writeInt(mContentId);
            dest.writeInt(mJobId);
            dest.writeInt(mErrorCauseId);
            dest.writeInt(mTotalNumber);
            dest.writeInt(mSucceededNumber);
            dest.writeInt(mFailedNumber);
        }

        public static final Parcelable.Creator<ReportDialogInfo> CREATOR = new Parcelable.Creator<ReportDialogInfo>() {
            public ReportDialogInfo createFromParcel(Parcel in) {
                final ReportDialogInfo values = new ReportDialogInfo();
                values.mTitleId = in.readInt();
                values.mContentId = in.readInt();
                values.mJobId = in.readInt();
                values.mErrorCauseId = in.readInt();
                values.mTotalNumber = in.readInt();
                values.mSucceededNumber = in.readInt();
                values.mFailedNumber = in.readInt();
                return values;
            }

            @Override
            public ReportDialogInfo[] newArray(int size) {
                return new ReportDialogInfo[size];
            }
        };

        public int getmTitleId() {
            return mTitleId;
        }

        public void setmTitleId(int titleId) {
            this.mTitleId = titleId;
        }

        public int getmContentId() {
            return mContentId;
        }

        public void setmContentId(int contentId) {
            this.mContentId = contentId;
        }

        public int getmJobId() {
            return mJobId;
        }

        public void setmJobId(int jobId) {
            this.mJobId = jobId;
        }

        public int getmErrorCauseId() {
            return mErrorCauseId;
        }

        public void setmErrorCauseId(int errorCauseId) {
            this.mErrorCauseId = errorCauseId;
        }

        public int getmTotalNumber() {
            return mTotalNumber;
        }

        public void setmTotalNumber(int totalNumber) {
            this.mTotalNumber = totalNumber;
        }

        public int getmSucceededNumber() {
            return mSucceededNumber;
        }

        public void setmSucceededNumber(int succeededNumber) {
            this.mSucceededNumber = succeededNumber;
        }

        public int getmFailedNumber() {
            return mFailedNumber;
        }

        public void setmFailedNumber(int failedNumber) {
            this.mFailedNumber = failedNumber;
        }
    }
    /** @ */
}
