package com.mediatek.galleryfeature.dynamic;

import android.os.SystemClock;

import com.mediatek.gallery3d.util.TraceHelper;
import com.mediatek.galleryframework.base.LayerManager;
import com.mediatek.galleryframework.base.MediaCenter;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.PlayEngine;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryfeature.dynamic.PlayList.Entry;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MTexture;
import com.mediatek.galleryframework.util.DebugUtils;
import com.mediatek.galleryframework.util.MtkLog;

public class PhotoPlayEngine extends PlayEngine implements Player.OnFrameAvailableListener,
        PlayList.EntryFilling {
    private static final String TAG = "MtkGallery2/PhotoPlayEngine";
    private enum Status {
        NEW, RESUMED, PAUSED
    }

    private Status mStatus = Status.NEW;
    private MediaCenter mMediaCenter;
    private OnFrameAvailableListener mFrameAvailableListener;
    private PlayList mPlayList;
    private PlayThreads mPlayThreads;
    private LayerManager mLayerManager;
    private int mWorkThreadNum;
    private int mTotalCount;
    private int mPlayCount;
    // play from [mPlayRangeBegin,mPlayRangeEnd]
    // prepare from [0, mPlayRangeBegin) and (mPlayRangeEnd, mTotalCount)
    private int mPlayRangeBegin;
    private int mPlayRangeEnd;
    private int mMiddleIndex;
    private int[] mSubmitTaskOrder;
    private ThumbType mThumbType;

    public PhotoPlayEngine(MediaCenter center, int totalCount, int playCount, int workThreadNum,
            ThumbType thumbType) {
        TraceHelper.traceBegin(">>>>PhotoPlayEngine-new");
        assert(playCount > 0 && totalCount >= playCount && workThreadNum > 0);
        mMediaCenter = center;
        mTotalCount = totalCount;
        mPlayCount = playCount;
        mPlayRangeBegin = (mTotalCount - mPlayCount) / 2;
        mPlayRangeEnd = mPlayRangeBegin + mPlayCount - 1;
        mMiddleIndex = mTotalCount / 2;
        mWorkThreadNum = workThreadNum;
        mPlayList = new PlayList(mTotalCount, this);
        initTaskOrder();
        mThumbType = thumbType;
        mPlayThreads = new PlayThreads(mWorkThreadNum);
        mPlayThreads.start();
        logAfterNew();
        TraceHelper.traceEnd();
    }

    public void setOnFrameAvailableListener(OnFrameAvailableListener lis) {
        mFrameAvailableListener = lis;
    }

    public void setLayerManager(LayerManager lm) {
        mLayerManager = lm;
    }

    public synchronized void resume() {
        TraceHelper.traceBegin(">>>>PhotoPlayEngine-resume");
        long begin = SystemClock.uptimeMillis();
        mPlayThreads.start();
        mStatus = Status.RESUMED;
        long end = SystemClock.uptimeMillis();
        MtkLog.i(TAG, "<resume> cost " + (end - begin) + " ms");
        TraceHelper.traceEnd();
    }

    public synchronized void pause() {
        TraceHelper.traceBegin(">>>>PhotoPlayEngine-pause");
        long begin = SystemClock.uptimeMillis();
        updateData(new MediaData[mTotalCount]);
        mPlayThreads.stop();
        mStatus = Status.PAUSED;
        long end = SystemClock.uptimeMillis();
        MtkLog.i(TAG, "<pause> cost " + (end - begin) + " ms");
        TraceHelper.traceEnd();
    }

    public synchronized void updateData(MediaData[] data) {
        TraceHelper.traceBegin(">>>>PhotoPlayEngine-updateData");
        // Not update data when this engine not in RESUMED status
        if (mStatus != Status.RESUMED) {
            MtkLog.i(TAG,
                    "<updateData> not Status.RESUMED, no need to update data, mStatus = "
                            + mStatus);
            TraceHelper.traceEnd();
            return;
        }
        logMediaDataArray("<updateData> Input data", data);
        long begin = SystemClock.uptimeMillis();
        // update entry list
        mPlayList.update(data);
        // clear all cmd in play threads except release
        mPlayThreads.clearAllCmds();
        // release all player in release list
        Entry[] needRelease = mPlayList.getReleaseList();
        if (needRelease != null) {
            for (int i = 0; i < needRelease.length; i++) {
                if (needRelease[i] != null && needRelease[i].data != null
                        && needRelease[i].player != null) {
                    mPlayThreads.submit(needRelease[i], Player.State.RELEASED);
                }
            }
        }
        // submit new task to play thread
        for (int i = 0; i <  mTotalCount; i++) {
            int index = mSubmitTaskOrder[i];
            if (mPlayList.get(index) == null || mPlayList.get(index).data == null
                    || mPlayList.get(index).player == null)
                continue;
            
            if (index >= mPlayRangeBegin && index <= mPlayRangeEnd) {
                mPlayThreads.submit(mPlayList.get(index),
                        Player.State.PLAYING);
            } else {
                mPlayThreads.submit(mPlayList.get(index),
                        Player.State.PREPARED);
            }
        }
        long end = SystemClock.uptimeMillis();
        MtkLog.i(TAG, "<updateData> cost " + (end - begin) + " ms");
        mPlayThreads.logCmdsWaitToRun("<Cmds queue after updateData>");
        TraceHelper.traceEnd();
    }

    public synchronized boolean draw(MediaData data, int index, MGLCanvas canvas, int width,
            int height) {
        TraceHelper.traceBegin(">>>>PhotoPlayEngine-draw");
        if (index < 0 || index >= mTotalCount || mPlayList.get(index) == null
                || mPlayList.get(index).data == null
                || mPlayList.get(index).player == null || !mPlayList.get(index).data.equals(data)) {
            TraceHelper.traceEnd();
            return false;
        }
        MTexture t = mPlayList.get(index).player.getTexture(canvas);
        if (t != null) {
            t.draw(canvas, 0, 0, width, height);
            if (DebugUtils.DEBUG_PLAY_RENDER)
                canvas.fillRect(0, 0, width, height, 0x4400FF00);
            TraceHelper.traceEnd();
            return true;
        }
        TraceHelper.traceEnd();
        return false;
    }

    public synchronized void onFrameAvailable(Player player) {
        if (mFrameAvailableListener == null)
            return;
        for (int i = 0; i < mTotalCount; i++) {
            if (mPlayList.get(i).player == player) {
                mFrameAvailableListener.onFrameAvailable(i);
                break;
            }
        }
    }

    public int getPlayWidth(int index, MediaData data) {
        if (index < 0 || index >= mTotalCount || mPlayList.get(index) == null
                || mPlayList.get(index).data == null
                || mPlayList.get(index).player == null
                || !mPlayList.get(index).data.equals(data))
            return 0;
        return mPlayList.get(index).player.getOutputWidth();
    }

    public int getPlayHeight(int index, MediaData data) {
        if (index < 0 || index >= mTotalCount || mPlayList.get(index) == null
                || mPlayList.get(index).data == null
                || mPlayList.get(index).player == null
                || !mPlayList.get(index).data.equals(data))
            return 0;
        return mPlayList.get(index).player.getOutputHeight();
    }

    public boolean isSkipAnimationWhenUpdateSize(int index) {
        if (index < 0 || index >= mTotalCount || mPlayList.get(index) == null
                || mPlayList.get(index).data == null
                || mPlayList.get(index).player == null)
            return false;
        return mPlayList.get(index).player.isSkipAnimationWhenUpdateSize();
    }

    public void fillEntry(int index, Entry entry) {
        entry.player = mMediaCenter.getPlayer(entry.data, mThumbType);
        if (entry.player != null) {
            entry.player .setOnFrameAvailableListener(PhotoPlayEngine.this);
        }
        if (index == mMiddleIndex && mLayerManager != null) {
            mLayerManager.switchLayer(entry.player, entry.data);
        }
    }

    public void updateEntry(int index, Entry entry) {
        if (index == mMiddleIndex && mLayerManager != null) {
            mLayerManager.switchLayer(entry.player, entry.data);
        }
    }

    private void initTaskOrder() {
        mSubmitTaskOrder = new int[mTotalCount];
        int index = 0;
        for (int i = mPlayRangeBegin; i <= mPlayRangeEnd; i++) {
            mSubmitTaskOrder[index] = i;
            index++;
        }
        int i = mPlayRangeBegin - 1;
        int j = mPlayRangeEnd + 1;
        for (; i >= 0 || j < mTotalCount; i--, j++) {
            if (i >= 0) {
                mSubmitTaskOrder[index] = i;
                index++;
            }
            if (j < mTotalCount) {
                mSubmitTaskOrder[index] = j;
                index++;
            }
        }
    }

    private void logMediaDataArray(String tag, MediaData[] data) {
        if (!DebugUtils.DEBUG_PLAY_ENGINE)
            return;
        MtkLog.i(TAG, tag + " begin -----------------------------------------");
        for (int i = 0; i < data.length; i++) {
            if (data[i] != null) {
                MtkLog.i(TAG, tag + " [" + i + "] "
                        + data[i].filePath);
            } else {
                MtkLog.i(TAG, tag + " [" + i + "] " + data[i]);
            }
        }
        MtkLog.i(TAG,  tag + " end   -----------------------------------------");
    }

    private void logAfterNew() {
        StringBuilder sb = new StringBuilder();
        sb.append("<new> mThumbType = " + mThumbType
                + ", mTotalCount = " + mTotalCount 
                + ", mPlayCount = " + mPlayCount 
                + ", mPlayRangeBegin = " + mPlayRangeBegin
                + ", mPlayRangeEnd = " + mPlayRangeEnd
                + ", mMiddleIndex = " + mMiddleIndex
                + ", mWorkThreadNum = " + mWorkThreadNum
                + ", mSubmitTaskOrder = [");
        for (int i = 0; i < mSubmitTaskOrder.length; i++)
            if (i == mSubmitTaskOrder.length - 1) {
                sb.append(mSubmitTaskOrder[i] + "]");
            } else {
                sb.append(mSubmitTaskOrder[i] + ", ");
            }
        MtkLog.i(TAG, sb.toString());
    }
}
