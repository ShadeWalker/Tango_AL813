
package com.mediatek.contacts.list.service;


import android.content.ContentResolver;
import android.net.Uri;
import android.os.PowerManager;
import android.os.Process;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import com.android.contacts.common.vcard.ProcessorBase;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.TimingStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class DeleteProcessor extends ProcessorBase {
    private static final String TAG = "DeleteProcessor";

    private final MultiChoiceService mService;
    private final ContentResolver mResolver;
    private final List<MultiChoiceRequest> mRequests;
    private final int mJobId;
    private final MultiChoiceHandlerListener mListener;

    private PowerManager.WakeLock mWakeLock;

    private volatile boolean mCanceled;
    private volatile boolean mDone;
    private volatile boolean mIsRunning;

    private static final int MAX_OP_COUNT_IN_ONE_BATCH = 100;

    // change max count and max count in one batch for special operator
    private static final int MAX_COUNT = 1551;
    private static final int MAX_COUNT_IN_ONE_BATCH = 50;

    public DeleteProcessor(final MultiChoiceService service,
            final MultiChoiceHandlerListener listener, final List<MultiChoiceRequest> requests,
            final int jobId) {
        mService = service;
        mResolver = mService.getContentResolver();
        mListener = listener;

        mRequests = requests;
        mJobId = jobId;

        final PowerManager powerManager = (PowerManager) mService.getApplicationContext()
                .getSystemService("power");
        mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                | PowerManager.ON_AFTER_RELEASE, TAG);
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        LogUtils.d(TAG, "[cancel]received cancel request,mDone = " + mDone
                + ",mCanceled = " + mCanceled + ",mIsRunning = " + mIsRunning);
        if (mDone || mCanceled) {
            return false;
        }

        mCanceled = true;
        if (!mIsRunning) {
            mService.handleFinishNotification(mJobId, false);
            mListener.onCanceled(MultiChoiceService.TYPE_DELETE, mJobId, -1, -1, -1);
        } else {
            /*
             * Bug Fix by Mediatek Begin.
             *   Original Android's code:
             *     xxx
             *   CR ID: ALPS00249590
             *   Descriptions:
             */
            mService.handleFinishNotification(mJobId, false);
            mListener.onCanceling(MultiChoiceService.TYPE_DELETE, mJobId);
            /*
             * Bug Fix by Mediatek End.
             */
        }

        return true;
    }

    @Override
    public int getType() {
        return MultiChoiceService.TYPE_DELETE;
    }

    @Override
    public synchronized boolean isCancelled() {
        return mCanceled;
    }

    @Override
    public synchronized boolean isDone() {
        return mDone;
    }

    @Override
    public void run() {
        try {
            mIsRunning = true;
            mWakeLock.acquire();
            Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
            runInternal();
        } finally {
            synchronized (this) {
                mDone = true;
            }
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
    }

    private void runInternal() {
        if (isCancelled()) {
            LogUtils.i(TAG, "[runInternal]Canceled before actually handling");
            return;
        }

        boolean succeessful = true;
        int totalItems = mRequests.size();
        int successfulItems = 0;
        int currentCount = 0;
        int iBatchDel = MAX_OP_COUNT_IN_ONE_BATCH;
        if (totalItems > MAX_COUNT) {
            iBatchDel = MAX_COUNT_IN_ONE_BATCH;
            LogUtils.i(TAG, "[runInternal]iBatchDel = " + iBatchDel);
        }
        long startTime = System.currentTimeMillis();
        final ArrayList<Long> contactIdsList = new ArrayList<Long>();
        int times = 0;
        boolean simServiceStarted = false;

        int subId = SubInfoUtils.getInvalidSubId();
        HashMap<Integer, Uri> delSimUriMap = new HashMap<Integer, Uri>();
        TimingStatistics iccProviderTiming = new TimingStatistics(DeleteProcessor.class.getSimpleName());
        TimingStatistics contactsProviderTiming = new TimingStatistics(DeleteProcessor.class.getSimpleName());
        for (MultiChoiceRequest request : mRequests) {
            if (mCanceled) {
                LogUtils.d(TAG, "[runInternal] run: mCanceled = true, break looper");
                break;
            }
            currentCount++;

            mListener.onProcessed(MultiChoiceService.TYPE_DELETE, mJobId, currentCount, totalItems,
                    request.mContactName);
            LogUtils.d(TAG, "[runInternal]Indicator: " + request.mIndicator);
            // delete contacts from sim card
            if (request.mIndicator > 0) {
                subId = request.mIndicator;
                if (!isReadyForDelete(subId)) {
                    LogUtils.d(TAG, "[runInternal] run: isReadyForDelete(" + subId + ") = false");
                    succeessful = false;
                    continue;
                }

                /// M: change for SIM Service refactoring
                if (simServiceStarted || !simServiceStarted && SIMServiceUtils.isServiceRunning(subId)) {
                    LogUtils.d(TAG, "[runInternal]run: sim service is running, we should skip all of sim contacts");
                    simServiceStarted = true;
                    succeessful = false;
                    continue;
                }

                Uri delSimUri = null;
                if (delSimUriMap.containsKey(subId)) {
                    delSimUri = delSimUriMap.get(subId);
                } else {
                    delSimUri = SubInfoUtils.getIccProviderUri(subId);
                    delSimUriMap.put(subId, delSimUri);
                }

                String where = ("index = " + request.mSimIndex);

                iccProviderTiming.timingStart();
                int deleteCount = mResolver.delete(delSimUri, where, null);
                iccProviderTiming.timingEnd();
                if (deleteCount <= 0) {
                    LogUtils.d(TAG, "[runInternal] run: delete the sim contact failed");
                    succeessful = false;
                } else {
                    successfulItems++;
                    contactIdsList.add(Long.valueOf(request.mContactId));
                }
            } else {
                successfulItems++;
                contactIdsList.add(Long.valueOf(request.mContactId));
            }

            // delete contacts from database
            if (contactIdsList.size() >= iBatchDel) {
                contactsProviderTiming.timingStart();
                actualBatchDelete(contactIdsList);
                contactsProviderTiming.timingEnd();
                LogUtils.i(TAG, "[runInternal]the " + (++times) + " times iBatchDel = " + iBatchDel);
                contactIdsList.clear();
                if ((totalItems - currentCount) <= MAX_COUNT) {
                    iBatchDel = MAX_OP_COUNT_IN_ONE_BATCH;
                }
            }
        }

        if (contactIdsList.size() > 0) {
            contactsProviderTiming.timingStart();
            actualBatchDelete(contactIdsList);
            contactsProviderTiming.timingEnd();
            contactIdsList.clear();
        }

        LogUtils.i(TAG, "[runInternal]totaltime: " + (System.currentTimeMillis() - startTime));

        if (mCanceled) {
            LogUtils.d(TAG, "[runInternal]run: mCanceled = true, return");
            succeessful = false;
            mService.handleFinishNotification(mJobId, false);
            mListener.onCanceled(MultiChoiceService.TYPE_DELETE, mJobId, totalItems,
                    successfulItems, totalItems - successfulItems);
            return;
        }
        mService.handleFinishNotification(mJobId, succeessful);
        if (succeessful) {
            mListener.onFinished(MultiChoiceService.TYPE_DELETE, mJobId, totalItems);
        } else {
            mListener.onFailed(MultiChoiceService.TYPE_DELETE, mJobId, totalItems,
                    successfulItems, totalItems - successfulItems);
        }

        iccProviderTiming.log("runInternal():IccProviderTiming");
        contactsProviderTiming.log("runInternal():ContactsProviderTiming");
    }

    private int actualBatchDelete(ArrayList<Long> contactIdList) {
        LogUtils.d(TAG, "[actualBatchDelete]");
        if (contactIdList == null || contactIdList.size() == 0) {
            LogUtils.w(TAG, "[actualBatchDelete]input error,contactIdList = " + contactIdList);
            return 0;
        }

        final StringBuilder whereBuilder = new StringBuilder();
        final ArrayList<String> whereArgs = new ArrayList<String>();
        final String[] questionMarks = new String[contactIdList.size()];
        for (long contactId : contactIdList) {
            whereArgs.add(String.valueOf(contactId));
        }
        Arrays.fill(questionMarks, "?");
        whereBuilder.append(Contacts._ID + " IN (").
                append(TextUtils.join(",", questionMarks)).
                append(")");

        int deleteCount = mResolver.delete(Contacts.CONTENT_URI.buildUpon().appendQueryParameter(
                "batch", "true").build(), whereBuilder.toString(), whereArgs.toArray(new String[0]));
        LogUtils.d(TAG, "[actualBatchDelete]deleteCount:" + deleteCount + " Contacts");
        return deleteCount;
    }

    private boolean isReadyForDelete(int subId) {
        return SimCardUtils.isSimStateIdle(subId);
    }
}
