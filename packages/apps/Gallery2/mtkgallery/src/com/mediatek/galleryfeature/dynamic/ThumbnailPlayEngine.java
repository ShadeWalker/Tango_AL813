package com.mediatek.galleryfeature.dynamic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemProperties;

import com.mediatek.galleryframework.base.GeneratorCoordinator;
import com.mediatek.galleryframework.base.LayerManager;
import com.mediatek.galleryframework.base.MediaCenter;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.PlayEngine;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.util.DebugUtils;
import com.mediatek.galleryframework.util.MtkLog;

public class ThumbnailPlayEngine extends PlayEngine {
    private static final String TAG = "MtkGallery2/ConstrainedPhotoPlayEngine";
    private static final int MAX_VIDEO_PLAYER = 8;
    private static final int LOOP_BATCH_SIZE = 1;

    public final TimeStampComparator TIME_STAMP_COMPARATOR = new TimeStampComparator();
    public final AbsTimeStampComparator ABS_TIME_STAMP_COMPARATOR = new AbsTimeStampComparator();

    private PlayerLoopSponser mPlayerLoopSponser;
    private PlayEngine mInnerPlayEngine;
    private MediaData[] mOriginalData;

    private final Object mLockOriginalData = new Object();

    // something optimized
    private volatile boolean mHasTooManyVideos;
    private final Object mLockToWaitTooManyVideos = new Object();

    private final Context mContext;
    private BroadcastReceiver mStorageReceiver;

    public ThumbnailPlayEngine(Context context, MediaCenter center, int totalCount, int playCount, int workThreadNum,
            ThumbType thumbType) {
        mContext = context;
        mInnerPlayEngine = new PhotoPlayEngine(center, totalCount, playCount, workThreadNum, thumbType);
    }

    @Override
    public boolean draw(MediaData data, int index, MGLCanvas canvas, int width, int height) {
        return mInnerPlayEngine.draw(data, index, canvas, width, height);
    }

    @Override
    public void pause() {
        GeneratorCoordinator.pause();
        mContext.unregisterReceiver(mStorageReceiver);
        mPlayerLoopSponser.interrupt();
        mInnerPlayEngine.pause();
    }

    @Override
    public void resume() {
        GeneratorCoordinator
                .setOnGeneratedListener(new GeneratorCoordinator.OnGeneratedListener() {
                    public void onGeneratedListen() {
                        innerUpdateData();
                    }
                });
        registerStorageReceiver();
        GeneratorCoordinator.start();
        mInnerPlayEngine.resume();
        mPlayerLoopSponser = new PlayerLoopSponser();
        mPlayerLoopSponser.start();
    }

    private void registerStorageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addDataScheme("file");
        mStorageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_MEDIA_EJECT.equals(action)) {
                    MtkLog.d(TAG, "storage ejection detected");
                    GeneratorCoordinator.cancelTranscodingForLostFile();
                }
            }
        };
        mContext.registerReceiver(mStorageReceiver, filter);
    }

    @Override
    public void setLayerManager(LayerManager lm) {
        mInnerPlayEngine.setLayerManager(lm);
    }

    @Override
    public void setOnFrameAvailableListener(OnFrameAvailableListener lis) {
        mInnerPlayEngine.setOnFrameAvailableListener(lis);
    }

    @Override
    public void updateData(MediaData[] data) {
        GeneratorCoordinator.cancelPendingTranscode();
        synchronized (mLockOriginalData) {
            mOriginalData = data;
            innerUpdateData();
        }
    }

    @Override
    public int getPlayWidth(int index, MediaData data) {
        return mInnerPlayEngine.getPlayWidth(index, data);
    }

    @Override
    public int getPlayHeight(int index, MediaData data) {
        return mInnerPlayEngine.getPlayHeight(index, data);
    }

    private void innerUpdateData() {
        synchronized (mLockOriginalData) {
            if (mOriginalData == null) {
                return;
            }
            MediaData[] data = transformData(mOriginalData);
            mInnerPlayEngine.updateData(data);
        }
    }

    private MediaData[] transformData(MediaData[] inData) {
        MediaData outData[] = new MediaData[inData.length];
        List<MediaData> items = new ArrayList<MediaData>();
        int videoCount = 0;
        for (int i = 0; i < outData.length; i++) {
            outData[i] = inData[i];
            if (outData[i] != null && outData[i].isVideo) {
                videoCount ++;
                // only videos need randomly playing
                items.add(outData[i]);
            }
        }

        // if video count <= max threshold, no need to randomly play
        if (videoCount <= MAX_VIDEO_PLAYER) {
            mHasTooManyVideos = false;
            return outData;
        }

        synchronized (mLockToWaitTooManyVideos) {
            mHasTooManyVideos = true;
            mLockToWaitTooManyVideos.notifyAll();
        }

        if (DebugUtils.DEBUG_THUMBNAIL_PLAY_ENGINE) {
            MtkLog.d(TAG, "inItems }");
            for (int i = 0; i < items.size(); i++) {
                MtkLog.d(TAG, "----  " + items.get(i).caption + ", timeStamp = "
                        + getTimeStamp(items.get(i).filePath));
            }
            MtkLog.d(TAG, "inItems }");
        }
        randomLoopPlayers(items);
        if (DebugUtils.DEBUG_THUMBNAIL_PLAY_ENGINE) {
            MtkLog.d(TAG, "outItems }");
            for (int i = 0; i < items.size(); i++) {
                MediaData data = items.get(i);
                if (data == null) {
                    continue;
                }
                MtkLog.d(TAG, "----  " + data.caption + ", timeStamp = "
                        + getTimeStamp(data.filePath));
            }
            MtkLog.d(TAG, "outItems }");
        }

        for (int i = 0; i < outData.length; i++) {
            if (outData[i] != null && outData[i].isVideo
                    && !items.contains(outData[i])) {
                outData[i] = null;
            }
        }
        return outData;
    }

    // TODO use template to get random effect
    // currently only OrdinaryPlayer has extra count limitation, so here goes the simple version
    private boolean randomLoopPlayers(
            final List<MediaData> items) {
        // the time stamp later, the prior the item is
        Collections.sort(items, TIME_STAMP_COMPARATOR);
        int mediaPlayerCount = 0;

        List<MediaData> candidateStopItems = new ArrayList<MediaData>();
        for (MediaData item : items) {
            if (getTimeStamp(item.filePath) > 0) {
                if (mediaPlayerCount < MAX_VIDEO_PLAYER
                        - LOOP_BATCH_SIZE) {
                    mediaPlayerCount++;
               } else {
                   candidateStopItems.add(item);
               }
            }
        }

        mediaPlayerCount = 0;
        MediaData item = null;
        List<MediaData> toStopPaths = new ArrayList<MediaData>();
        // stop item with earlier time stamp as soon as possible
        for (int i = candidateStopItems.size() - 1; i >=0; i--) {
            if (mediaPlayerCount < LOOP_BATCH_SIZE) {
                item = candidateStopItems.get(i);
                toStopPaths.add(item);
                // an approximation, and needs optimizing
                reverseTimeStamp(item.filePath);
                MtkLog.d(TAG, "PlayerLooper requests to stop " + item.caption);
                mediaPlayerCount++;
            } else {
                break;
            }
        }

        items.removeAll(candidateStopItems);
        // the absolute value of time stamp later, the prior the item is
        Collections.sort(items, ABS_TIME_STAMP_COMPARATOR);

        // an approximation, and needs optimizing
        mediaPlayerCount = 0;
        int listSize = items.size();
        for (int i = 0; i < listSize; i++) {
            item = (MediaData) (items.get(i));
            mediaPlayerCount++;
            if (mediaPlayerCount <= MAX_VIDEO_PLAYER) {
                long t = getTimeStamp(item.filePath);
                if (t <= 0) {
                    updateTimeStamp(item.filePath, System.currentTimeMillis());
                } else {
                    continue;
                }
            } else {
                items.set(i, null);
            }
        }

        return true;
    }

    private class TimeStampComparator implements
            Comparator<MediaData> {
        public int compare(MediaData item0, MediaData item1) {
            long t0 = getTimeStamp(item0.filePath);//item0.mTimeStamp;
            long t1 = getTimeStamp(item1.filePath);//item1.mTimeStamp;
            if (t1 < t0) {
                return -1;
            } else if (t0 == t1) {
                return 0;
            } else {
                return 1;
            }
        }
    }

    private class AbsTimeStampComparator implements
            Comparator<MediaData> {
        public int compare(MediaData item0, MediaData item1) {
            long t0 = Math.abs(getTimeStamp(item0.filePath));
            long t1 = Math.abs(getTimeStamp(item1.filePath));
            if (t0 < t1) {
                return -1;
            } else if (t0 == t1) {
                return 0;
            } else {
                return 1;
            }
        }
    }

    private static class DataInfo {
        public long timeStamp;
    }

    private Map<String, DataInfo> mDataInfos = new HashMap<String, DataInfo>();

    private void updateTimeStamp(String path, long t) {
        DataInfo dataInfo = mDataInfos.get(path);
        if (dataInfo != null) {
            dataInfo.timeStamp = t;
        } else {
            dataInfo = new DataInfo();
            dataInfo.timeStamp = t;
            mDataInfos.put(path, dataInfo);
        }
    }

    private void reverseTimeStamp(String path) {
        DataInfo dataInfo = mDataInfos.get(path);
        if (dataInfo != null) {
            dataInfo.timeStamp = -dataInfo.timeStamp;
        }
    }

    private long getTimeStamp(String path) {
        DataInfo dataInfo = mDataInfos.get(path);
        if (dataInfo == null) {
            return 0;
        } else {
            return dataInfo.timeStamp;
        }
    }

    private class PlayerLoopSponser extends Thread {
        private static final long FIRST_LOOP_DELAY = 8000;
        private static final long LOOP_PERIOD = 2500;
        public PlayerLoopSponser() {
            super("PlayerLoopSponser");
        }

        public void run() {
            android.os.Process
                    .setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            boolean isFirstPass = true;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    synchronized (mLockToWaitTooManyVideos) {
                        while (!mHasTooManyVideos) {
                            mLockToWaitTooManyVideos.wait();
                        }
                    }
                    if (isFirstPass) {
                        Thread.sleep(FIRST_LOOP_DELAY);
                        isFirstPass = false;
                    } else {
                        Thread.sleep(LOOP_PERIOD);
                    }
                    innerUpdateData();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }
}
