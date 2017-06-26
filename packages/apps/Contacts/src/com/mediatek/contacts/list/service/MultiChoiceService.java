
package com.mediatek.contacts.list.service;

import android.accounts.Account;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import com.android.contacts.common.vcard.ProcessorBase;
import com.mediatek.contacts.ContactsApplicationEx;
import com.mediatek.contacts.util.LogUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * The class responsible for handling all of multiple choice requests. This
 * Service creates one MultiChoiceRequest object (as Runnable) per request and
 * push it to {@link ExecutorService} with single thread executor. The executor
 * handles each request one by one, and notifies users when needed.
 */
public class MultiChoiceService extends Service {
    private static final String TAG = "MultiChoiceService";

    // Should be single thread, as we don't want to simultaneously handle import
    // and export requests.
    private final ExecutorService mExecutorService = ContactsApplicationEx.getContactsApplication()
            .getApplicationTaskService();

    // Stores all unfinished import/export jobs which will be executed by
    // mExecutorService. Key is jobId.
    private static final Map<Integer, ProcessorBase> RUNNINGJOBMAP = new HashMap<Integer, ProcessorBase>();

    public static final int TYPE_COPY = 1;
    public static final int TYPE_DELETE = 2;

    private static int sCurrentJobId;

    private MyBinder mBinder;

    public class MyBinder extends Binder {
        public MultiChoiceService getService() {
            return MultiChoiceService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBinder = new MyBinder();
        LogUtils.d(TAG, "[onCreate]Multi-choice Service is being created.");

        /// change for low_memory kill Contacts process CR.
        // startForeground(1,  new Notification());
    }

    /** M: change for low_memory kill Contacts process  @{
     * reference CR: ALPS00564966,ALPS00567689,ALPS00567905
     **/
    @Override
    public void onDestroy() {
        // stopForeground(true);
        super.onDestroy();
    }
    /** @} */

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        /// M: change START_STICKY to START_NOT_STICKY for Service slim
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Tries to call {@link ExecutorService#execute(Runnable)} toward a given
     * processor.
     *
     * @return true when successful.
     */
    private synchronized boolean tryExecute(ProcessorBase processor) {
        try {
            LogUtils.d(TAG, "[tryExecute]Executor service status: shutdown: " + mExecutorService.isShutdown()
                        + ", terminated: " + mExecutorService.isTerminated());
            mExecutorService.execute(processor);
            RUNNINGJOBMAP.put(sCurrentJobId, processor);
            return true;
        } catch (RejectedExecutionException e) {
            LogUtils.w(TAG, "[tryExecute]Failed to excetute a job:" + e);
            return false;
        }
    }

    public synchronized void handleDeleteRequest(List<MultiChoiceRequest> requests,
            MultiChoiceHandlerListener listener) {
        sCurrentJobId++;
        if (tryExecute(new DeleteProcessor(this, listener, requests, sCurrentJobId))) {
            if (listener != null) {
                listener.onProcessed(TYPE_DELETE, sCurrentJobId, 0, -1, requests
                        .get(0).mContactName);
            }
        }
    }

    public synchronized void handleCopyRequest(List<MultiChoiceRequest> requests,
            MultiChoiceHandlerListener listener, final Account sourceAccount,
            final Account destinationAccount) {
        sCurrentJobId++;
        if (tryExecute(new CopyProcessor(this, listener, requests, sCurrentJobId, sourceAccount,
                destinationAccount))) {
            if (listener != null) {
                listener.onProcessed(TYPE_COPY, sCurrentJobId, 0, -1,
                        requests.get(0).mContactName);
            }
        }
    }

    public synchronized void handleCancelRequest(MultiChoiceCancelRequest request) {
        final int jobId = request.jobId;
        LogUtils.d(TAG, "[handleCancelRequest]" + String.format("Received cancel request. (id: %d)", jobId));
        final ProcessorBase processor = RUNNINGJOBMAP.remove(jobId);

        if (processor != null) {
            processor.cancel(true);
        } else {
            LogUtils.w(TAG, "[handleCancelRequest]" + String.format("Tried to remove unknown job (id: %d)", jobId));
        }
        stopServiceIfAppropriate();
    }

    /**
     * Checks job list and call {@link #stopSelf()} when there's no job and no
     * scanner connection is remaining. A new job (import/export) cannot be
     * submitted any more after this call.
     */
    private synchronized void stopServiceIfAppropriate() {
        if (RUNNINGJOBMAP.size() > 0) {
            for (final Map.Entry<Integer, ProcessorBase> entry : RUNNINGJOBMAP.entrySet()) {
                final int jobId = entry.getKey();
                final ProcessorBase processor = entry.getValue();
                if (processor.isDone()) {
                    RUNNINGJOBMAP.remove(jobId);
                } else {
                    LogUtils.i(TAG, "[stopServiceIfAppropriate]" + String.format("Found unfinished job (id: %d)", jobId));
                    return;
                }
            }
        }

        LogUtils.i(TAG, "[stopServiceIfAppropriate]No unfinished job. Stop this service.");
        //mExecutorService.shutdown();
        stopSelf();
    }

    public synchronized void handleFinishNotification(int jobId, boolean successful) {
        LogUtils.d(TAG, "[handleFinishNotification]" + String.format("Received handle finish notification (id: %d). "
                    + "Result: %s", jobId, (successful ? "success" : "failure")));
        if (RUNNINGJOBMAP.remove(jobId) == null) {
            LogUtils.w(TAG, "[handleFinishNotification]" + String.format("Tried to remove unknown job (id: %d)", jobId));
        }
        stopServiceIfAppropriate();
    }

    public static synchronized boolean isProcessing(int requestType) {
        if (RUNNINGJOBMAP.size() <= 0) {
            LogUtils.w(TAG, "[isProcessing] size is <=0,return false!");
            return false;
        }

        if (RUNNINGJOBMAP.size() > 0) {
            for (final Map.Entry<Integer, ProcessorBase> entry : RUNNINGJOBMAP.entrySet()) {
                final ProcessorBase processor = entry.getValue();
                if (processor.getType() == requestType) {
                    return true;
                }
            }
        }

        return false;
    }
}
