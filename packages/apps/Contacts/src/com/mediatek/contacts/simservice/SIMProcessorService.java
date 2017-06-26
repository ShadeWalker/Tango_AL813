package com.mediatek.contacts.simservice;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.android.contacts.common.vcard.ProcessorBase;
import com.mediatek.contacts.simservice.SIMProcessorManager.ProcessorManagerListener;
import com.mediatek.contacts.util.LogUtils;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class SIMProcessorService extends Service {
    private final static String TAG = "SIMProcessorService";

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 20;
    private static final int KEEP_ALIVE_TIME = 10; // 10 seconds

    private SIMProcessorManager mProcessorManager;
    private AtomicInteger mNumber = new AtomicInteger();
    private final ExecutorService mExecutorService = createThreadPool(CORE_POOL_SIZE);

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtils.d(TAG, "onCreate()");
        mProcessorManager = new SIMProcessorManager(this, mListener);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        processIntent(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdown();
        LogUtils.d(TAG, "onDestroy()");
    }

    public void processIntent(Intent intent) {
        if (intent == null) {
            LogUtils.i(TAG, "[processIntent] intent is null.");
            return;
        }
        int subId = intent.getIntExtra(SIMServiceUtils.SERVICE_SUBSCRIPTION_KEY, 0);
        int workType = intent.getIntExtra(SIMServiceUtils.SERVICE_WORK_TYPE, -1);

        mProcessorManager.handleProcessor(getApplicationContext(), subId, workType, intent);
    }

    private SIMProcessorManager.ProcessorManagerListener mListener = new ProcessorManagerListener() {
        @Override
        public void addProcessor(long scheduleTime, ProcessorBase processor) {
            if (processor != null) {
                /// M: add try catch for seldom JE ALPS01006659
                try {
                    mExecutorService.execute(processor);
                } catch (RejectedExecutionException e) {
                    LogUtils.e(TAG, "[addProcessor] RejectedExecutionException: " + e.toString());
                }
            }
        }

        @Override
        public void onAllProcessorsFinished() {
            LogUtils.d(TAG, "stopServiceAndThreadPool()");
            stopSelf();
        }
    };

    private ExecutorService createThreadPool(int initPoolSize) {
        return new ThreadPoolExecutor(initPoolSize, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(), new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        String threadName = "SIM Service - " + mNumber.getAndIncrement();
                        LogUtils.d(TAG, "[createThreadPool]thread name:" + threadName);
                        return new Thread(r, threadName);
                    }
                });
    }
}
