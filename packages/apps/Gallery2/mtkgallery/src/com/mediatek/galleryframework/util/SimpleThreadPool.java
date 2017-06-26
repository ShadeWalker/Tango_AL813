package com.mediatek.galleryframework.util;

import java.util.ArrayList;
import java.util.Iterator;

import com.mediatek.galleryframework.util.MtkLog;

public class SimpleThreadPool {
    private static final String TAG = "MtkGallery2/SimpleThreadPool";
    private static final int THREAD_NUM = 2;
    private ArrayList<WorkThread> mThreadList;
    private ArrayList<Job> mJobList = new ArrayList<Job>();
    private static SimpleThreadPool sThreadPool = null;

    private SimpleThreadPool() {
    }

    public interface Job {
        public void onDo();

        public boolean isCanceled();
    }

    public static SimpleThreadPool getInstance() {
        if (sThreadPool == null) {
            sThreadPool = new SimpleThreadPool();
            sThreadPool.startWork(THREAD_NUM);
        }
        return sThreadPool;
    }

    private void startWork(int threadNum) {
        MtkLog.i(TAG, "<startWork> threadNum = " + threadNum);
        if (mThreadList != null)
            return;
        mThreadList = new ArrayList<WorkThread>();
        for (int i = 0; i < threadNum; i++) {
            WorkThread t = new WorkThread(this);
            t.setName("SimpleThreadPool-" + threadNum);
            t.start();
            mThreadList.add(t);
        }
    }

    private void stopWork() {
        MtkLog.i(TAG, "<stopWork>");
        if (mThreadList == null)
            return;
        int threadNum = mThreadList.size();
        for (int i = 0; i < threadNum; i++) {
            mThreadList.get(i).exit();
            mThreadList.set(i, null);
        }
        mThreadList.clear();
        mThreadList = null;
    }

    public synchronized void submitAsyncJob(Job job) {
        mJobList.add(job);
        Iterator<WorkThread> itr = mThreadList.iterator();
        while (itr.hasNext()) {
            WorkThread thread = itr.next();
            synchronized (thread) {
                thread.notifyAll();
            }
        }
    }

    public static void doSyncJob(Job job) {
        if (job != null && job.isCanceled() == false)
            job.onDo();
    }

    private synchronized Job getOneJob() {
        if (mJobList.size() == 0)
            return null;
        Job job = mJobList.get(0);
        mJobList.remove(0);
        return job;
    }

    class WorkThread extends Thread {
        private static final String TAG = "MtkGallery2/WorkThread";
        private SimpleThreadPool mJobGetter;
        private boolean mActive = true;

        public WorkThread(SimpleThreadPool jobGetter) {
            super();
            mJobGetter = jobGetter;
        }

        @Override
        public void run() {
            MtkLog.i(TAG, "<run> begin");
            while (mActive) {
                Job job = mJobGetter.getOneJob();
                synchronized (WorkThread.this) {
                    if (job == null) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            MtkLog.i(TAG, "<run> InterruptedException");
                        }
                    }
                }
                if (job != null && !job.isCanceled()) {
                    doSyncJob(job);
                }
            }
            MtkLog.i(TAG, "<run> exit");
        }

        public synchronized void exit() {
            mActive = false;
            notifyAll();
        }
    }
}