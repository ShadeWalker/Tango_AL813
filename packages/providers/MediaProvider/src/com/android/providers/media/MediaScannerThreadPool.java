/* //device/content/providers/media/src/com/android/providers/media/MediaScannerService.java
 **
 ** Copyright 2007, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

package com.android.providers.media;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaFile;
import android.media.MediaFile.MediaFileType;
import android.media.MediaInserter;
import android.media.MediaScanner;
import android.mtp.MtpConstants;
import android.net.Uri;
import android.os.Handler;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;

public class MediaScannerThreadPool extends ThreadPoolExecutor {
    private static final String TAG = "MediaScannerThreadPool";

    /// MediaScanner thread pool default setting
    private static final int CORE_POOL_SIZE = 3;
    private static final int MAXIMUM_POOL_SIZE = CORE_POOL_SIZE;
    private static final int KEEP_ALIVE_TIME = 10;
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
    private static final PriorityBlockingQueue<Runnable> sWorkQueue =
            new PriorityBlockingQueue<Runnable>(64, getTaskComparator());
    private static final AtomicInteger sCount = new AtomicInteger(1);
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        public Thread newThread(Runnable r) {
            return new Thread(r, "Scan-thread#" + sCount.getAndIncrement());
        }
    };

    /// Use to mark special scan path, we will scan these single file which left after split
    /// folder and empty folder one by one with scan single file method in mediascanner.
    private static final String PREFIX_SINGLEFILE = "singlefile_";

    /// External and internal volume
    private static final String EXTERNAL_VOLUME = "external";
    private static final String INTERNAL_VOLUME = "internal";

    /// Scan priority
    private static final int PRIORITY_LOW       = 10000;
    private static final int PRIORITY_NORMAL    = PRIORITY_LOW * 2;
    private static final int PRIORITY_HIGH      = PRIORITY_LOW * 3;
    private static final int PRIORITY_EXTREME   = PRIORITY_LOW * 4;
    private static final int PRIORITY_MAX       = Integer.MAX_VALUE;
    private static final int MORE_PRIOR         = PRIORITY_LOW / 10;
    private static final int LESS_PRIOR         = PRIORITY_LOW / 10 * -1;

    /// Folder or file types when parse scan unit
    private static final int NO_MEDIA_PATH      = 1 << 0;
    private static final int NORMAL_FILE        = 1 << 1;
    private static final int AUDIO_FILE         = 1 << 2;
    private static final int IMAGE_FILE         = 1 << 3;
    private static final int VIDEO_FILE         = 1 << 4;
    private static final int AUDIO_VIDEO_FILE   = AUDIO_FILE | VIDEO_FILE;
    private static final int MULTI_MEDIA_FILE   = AUDIO_FILE | IMAGE_FILE | VIDEO_FILE;

    /// Parse task and split scan unit refer auxiliary list, we will store folder structure
    /// in this hashmap and can speed up parse task and split task next time.
    private static final ConcurrentHashMap<String, FolderStructure> mFolderMap;
    static {
        mFolderMap = new ConcurrentHashMap<String, FolderStructure>(128);
    }

    /// Every scanner scan finish will return scanned playlist files path and store here, when scan finish
    /// we process them in postScanAll.
    private final ArrayList<String> mPlaylistFilePathList = new ArrayList<String>();

    /// Scan Audio/video, image and normal file separate
    private final PriorityBlockingQueue<String> mAudioVideoQueue = new PriorityBlockingQueue<String>(64, getQueueComparator());
    private final PriorityBlockingQueue<String> mImageQueue      = new PriorityBlockingQueue<String>(64, getQueueComparator());
    private final PriorityBlockingQueue<String> mNormalFileQueue = new PriorityBlockingQueue<String>(64, getQueueComparator());
    private final Vector<String> mSingleFileList = new Vector<String>(64);

    /// Latch service thread when parse scan unit
    private CountDownLatch mParseLatch;

    /// Need initialized parameters
    private final Context mContext;
    private final String[] mDirectories;
    private final Handler mServiceHandler;
    private final Handler mInsertHanlder;

    /// When has executed all task, need not executed again.
    private boolean mHasExecutedAllTask = false;

    public MediaScannerThreadPool(Context context, String[] directories, Handler serviceHandler, Handler insertHandler) {
        super(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, sWorkQueue, sThreadFactory);
        mContext = context;
        mDirectories = directories;
        mServiceHandler = serviceHandler;
        mInsertHanlder = insertHandler;
        sCount.set(1);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        /// when SingleTypeScanTask finish, check whether image or audio/video has scan finish, if yes
        /// we need execute all task.
        if ((r instanceof SingleTypeScanTask) && !mHasExecutedAllTask
                && (mAudioVideoQueue.isEmpty() || mImageQueue.isEmpty())) {
            MtkLog.v(TAG, "Audio/video or image singleScanTask has finish, execute all tasks to threadpool");
            executeAllTask();
        }
    }

    @Override
    protected void terminated() {
        MtkLog.v(TAG, "All task(" + getTaskCount() +  ") scan finish, send message to insert all to database.");
        mInsertHanlder.sendEmptyMessage(MediaInserter.MSG_INSERT_ALL);
        super.terminated();
    }

    /// Define comparator
    /**
     * Comparator for all task in threadpool(order work queue).
     */
    private static Comparator<Runnable> getTaskComparator() {
        return new Comparator<Runnable>() {
            public int compare(Runnable old, Runnable latest) {
                if (((Task) latest).mPriority > ((Task) old).mPriority) {
                    return 1;
                }
                return -1;
            };
        };
    }

    /**
     * Comparator for mAudioVideoQueue, mImageQueue and mNormalFileQueue
     */
    private Comparator<String> getQueueComparator() {
        return new Comparator<String>() {
            public int compare(String old, String latest) {
                if (old == null || latest == null) {
                    return -1;
                }
                FolderStructure oldFolder = mFolderMap.get(old);
                FolderStructure latestFolder = mFolderMap.get(latest);
                int oldSize = getTotalSize(oldFolder);
                int latestSize = getTotalSize(latestFolder);
                int maxScanTaskSize = getMaxScanTaskSizeByType(getFolderFileType(oldFolder));
                if (latestSize < maxScanTaskSize && oldSize < maxScanTaskSize) {
                    return getFolderSerialNum(latestFolder) > getFolderSerialNum(oldFolder) ? 1 : -1;
                }
                return latestSize == oldSize ? -1 : latestSize - oldSize;
            };
        };
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////// Public method to control ThreadPool //////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Get scanned out playlist lists to do post scan.
     *
     * @return playlist lists
     */
    public ArrayList<String> getPlaylistFilePaths() {
        return mPlaylistFilePathList;
    }

    /**
     * Stop scan in threadpool, this may happen when sdcard eject while scanning
     */
    public synchronized void stopScan() {
        MtkLog.w(TAG, "stopScan in threadpool, clear work queue, stop insert and mark all task executed");
        mHasExecutedAllTask = true;
        mInsertHanlder.sendEmptyMessage(MediaInserter.MSG_STOP_INSERT);
        sWorkQueue.clear();
        mAudioVideoQueue.clear();
        mImageQueue.clear();
        mNormalFileQueue.clear();
        mSingleFileList.clear();
        shutdownNow();
        /// Clear latch to free ParseScanTask thread(MediaScannerService thread)
        if (mParseLatch != null) {
            while (mParseLatch.getCount() > 0) {
                mParseLatch.countDown();
            }
        }
    }

    /**
     * Update folder map cache, remove need parse folders and size equal to 0 folders when sdcard
     * eject, so that next scan can get real value from file system.
     */
    public static void updateFolderMap() {
        MtkLog.v(TAG, "updateFolderMap: clear folder map");
        mFolderMap.clear();
        /*Iterator<Entry<String, FolderStructure>> iterator = mFolderMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, FolderStructure> entry = iterator.next();
            FolderStructure folderStructure = entry.getValue();
            if (folderStructure != null && folderStructure.mNeedParse) {
                folderStructure.setSubFileList(null);
            }
            if (folderStructure != null && folderStructure.mTotalSize == 0) {
                MtkLog.d(TAG, "updateFolderMap 0 size folder: " + entry.getKey());
                iterator.remove();
            }
        }*/
    }

    /**
     * Parse scan task from given directories and execute them to threadpool to do scan.
     */
    public void parseScanTask() {
        MtkLog.v(TAG, "parseScanTask>>>");
        long start = System.currentTimeMillis();
        List<String> singlefileList = new ArrayList<String>();
        List<String> folderList = new ArrayList<String>();
        if (mDirectories != null && mDirectories.length > 0) {
            for (String directory : mDirectories) {
                File file = new File(directory);
                File[] subFiles = file.listFiles();
                if (subFiles != null) {
                    for (File subFile : subFiles) {
                        if (subFile.isDirectory()) {
                            String[] fileList = subFile.list();
                            if (fileList != null && fileList.length > 0) {
								// HQ_pangxuhui 20150925 for HQ01331800 add by mtk begin
								String path = subFile.getPath();
                                folderList.add(path);
								
								// If mFolderMap cache this path as empty folder, need remove from
                                // cache to avoid scan this folder as single file and don't scan
                                // sub files below it
                                FolderStructure folder = mFolderMap.get(path);
                                if (folder != null && folder.mTotalSize == 0) {
                                    mFolderMap.remove(path);
                                    MtkLog.d("[MTK debug]", "remove no emtypy folder " + path);
                                }
								// HQ_pangxuhui 20150925 add by mtk End
                            } else {
                                /// Empty folder in root directory only need insert to db
                                FolderStructure folder = new FolderStructure(
                                        new int[] { 0, 0, 0, 0, NORMAL_FILE, 0 },
                                        subFile.lastModified() / 1000,
                                        false,
                                        subFile.getPath());
                                mFolderMap.put(subFile.getPath(), folder);
                            }
                        } else {
                            /// Scan all single file in root directory in database first, so that MTP can show faster
                            singlefileList.add(subFile.getPath());
                        }
                    }
                }
            }
        }
        /// Execute all single file scan task and parse folder task
        executeParseTask(folderList, singlefileList);

        /// only when all folder finish parse(time out after 3min), we free service thread
        try {
            mParseLatch.await(3, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            MtkLog.w(TAG, "parseScanTask with InterruptedException", e);
        }
        long end = System.currentTimeMillis();
        MtkLog.v(TAG, "parseScanTask<<< cost " + (end - start) + "ms");
    }

    private synchronized void executeParseTask(List<String> folderList, List<String> singlefileList) {
        if (mHasExecutedAllTask) {
            mParseLatch = new CountDownLatch(0);
            MtkLog.w(TAG, "executeParseTask with all task have been executed, it may happen when stopScan");
            return;
        }
        /// 1. parse all folder in thread pool, store order as serial num in ParseTask, so that can scan all parsed
        /// folder one by one as save order(serial num more large mean store front in root directory)
        mParseLatch = new CountDownLatch(folderList.size());
        if (!folderList.isEmpty()) {
            int serialNum = 1;
            for (String folder : folderList) {
                execute(new ParseTask(folder, EXTERNAL_VOLUME, PRIORITY_MAX, serialNum++));
            }
        }

        /// 2. insert folder entries when all folder parse finish
        execute(new InsertFolderTask());

        /// 3. scan single file in root directory first
        if (!singlefileList.isEmpty()) {
            String singlefile = singlefileList.toString();
            singlefile = PREFIX_SINGLEFILE + singlefile.substring(1, singlefile.length() - 1);
            execute(new ScanTask(singlefile, EXTERNAL_VOLUME, PRIORITY_MAX + LESS_PRIOR, singlefileList.size()));
        }

        /// 4. execute SingleTypeScanTask(all audio/video(image) scan in same thread after parse task finish.
        //execute(new SingleTypeScanTask(mAudioVideoQueue, PRIORITY_EXTREME + MORE_PRIOR));
        //execute(new SingleTypeScanTask(mImageQueue, PRIORITY_EXTREME));
        //execute(new SingleTypeScanTask(mNormalFileQueue, PRIORITY_EXTREME + LESS_PRIOR));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////// All Task Define(Parse and Scan) ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Default abstract task, extend for ParseTask, SingleTypeScanTask, ScanTask
     *
     * @author mtk54154
     */
    private abstract class Task implements Runnable {
        final int mPriority;
        final int mSize;
        public Task(int priority, int size) {
            mPriority = priority;
            mSize = size;
        }
    }

    /// 1. ParseTask
    /**
     * Scan Task limit size, folder size large than it must execute as a single whole task.
     */
    private static final int TASK_LIMIT_SIZE = 100;

    /**
     * Interval for check folder when parse task, if large than treble of it, quit parse and only
     * check layer one sub files.
     */
    private static final int FOLDER_CHECK_LIMIT_INTERVAL = 100;

    private class ParseTask extends Task {
        private final String mPath;
        private final String mVolume;
        private final int mSerialNum;
        private int mCheckInterval = FOLDER_CHECK_LIMIT_INTERVAL;
        private int mQuitParseLayerSize = 255; /// default set to max folder layer size
        public ParseTask(String parseFolderPath, String volume, int priority, int serialNum) {
            super(priority, 1);
            mPath = parseFolderPath;
            mVolume = volume;
            mSerialNum = serialNum;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_LESS_FAVORABLE);
            long start = System.currentTimeMillis();
            List<String> singleList = new ArrayList<String>();
            parseFolder(mPath, singleList);
            /// If have single file or empty folder, scan as single file.
            mSingleFileList.addAll(singleList);
            mParseLatch.countDown();
            long end = System.currentTimeMillis();
            MtkLog.v(TAG, "parse finsih in " + Thread.currentThread().getName() + ": folder = " + mPath
                    + ", cost = " + (end - start) + "ms");
        }

        private void parseFolder(String folderPath, List<String> singleList) {
            if (mHasExecutedAllTask) {
                MtkLog.w(TAG, "parseFolder with all task have been executed, it may happen stop scan");
                return;
            }
            FolderStructure folder = getFolderStructure(folderPath);
            if (isNeedParse(folder)) {
                String[] subFileList = getSubFileList(folder, folderPath);
                for (String fileName : subFileList) {
                    File subFile = new File(folderPath, fileName);
                    if (subFile.isDirectory()) {
                        parseFolder(subFile.getPath(), singleList);
                    } else {
                        singleList.add(subFile.getPath());
                    }
                }
            } else if (isEmptyFolder(folder)) {
                singleList.add(folderPath);
            } else {
                addToQueueByType(folderPath, getFolderFileType(folder));
            }
        }

        private FolderStructure getFolderStructure(String folderPath) {
            if (folderPath == null) {
                return null;
            }
            FolderStructure folder = mFolderMap.get(folderPath);
            if (folder != null) {
                return folder;
            }

            /// 1.Define folder structure parameters(6)
            int totalSize = 0;
            int folderSize = 0;
            int subTotalSize = 0;
            int subFolderSize = 0;
            /// Add "/" to check whether a .nomedia file in current folder.
            boolean isNoMediaPath = MediaScanner.isNoMediaPath(folderPath + "/");
            int fileType = isNoMediaPath ? NO_MEDIA_PATH : NORMAL_FILE;
            /// Get current layer, when big than mQuitParseLayerSize, quit parse
            int layerSize = getLayerSizeByPath(folderPath, mPath);
            /// Store the max and second big folder's size below current folder which are used to
            /// check whether need parse current folder.
            int firstBigSize = 0;
            int secondBigSize = 0;
            boolean isNeedParse = false;

            /// 2. Parse current folder structure with recursion algorithm
            File file = new File(folderPath);
            String[] subFileList = file.list();
            if (subFileList != null) {
                subTotalSize = subFileList.length;
                totalSize += subTotalSize;
                if (subTotalSize >= TASK_LIMIT_SIZE || layerSize >= mQuitParseLayerSize) {
                    for (String subFileName : subFileList) {
                        if (isNoMediaPath || isMultiMediaPath(fileType)) {
                            break;
                        }
                        fileType = getFileTypeByName(subFileName);
                    }
                } else {
                    int totalSizeForSubFolder;
                    for (String subFileName : subFileList) {
                        File subFile = new File(folderPath, subFileName);
                        if (subFile.isDirectory()) {
                            subFolderSize++;
                            folderSize++;
                            folder = getFolderStructure(subFile.getPath());
                            totalSizeForSubFolder = getTotalSize(folder);
                            totalSize += totalSizeForSubFolder;
                            /// Store first big and second big folder size
                            if (totalSizeForSubFolder > firstBigSize) {
                                secondBigSize = firstBigSize;
                                firstBigSize = totalSizeForSubFolder;
                            } else if (totalSizeForSubFolder > secondBigSize) {
                                secondBigSize = totalSizeForSubFolder;
                            }
                            fileType |= getFolderFileType(folder);
                            folderSize += getFolderSize(folder);
                        } else if (!isNoMediaPath && !isMultiMediaPath(fileType)) {
                            fileType |= getFileTypeByName(subFileName);
                        }
                        /// When folder size is bigger than limit(mCheckInterval), increase limit size and set quit
                        /// parse layer size(5,3,1).
                        if (folderSize > mCheckInterval) {
                            mQuitParseLayerSize = 7 - 2 * mCheckInterval / FOLDER_CHECK_LIMIT_INTERVAL;
                            MtkLog.d(TAG, "Parse folder num over limit(" + mCheckInterval
                                    + "), so set mQuitParseLayerSize to " + mQuitParseLayerSize + " in " + folderPath);
                            mCheckInterval += FOLDER_CHECK_LIMIT_INTERVAL;
                        }
                        /// If current layer size is bigger than limit, break loop.
                        if (layerSize > mQuitParseLayerSize) {
                            break;
                        }
                    }
                    /// Only when current folder structure match below condition we need parse it:
                    /// 1. total size big than max(media=100, other=2000) and single files size in this folder must
                    /// less than 100
                    /// 2. files in this folder must be uniform distribution(more than ten percent(>10%) files store
                    /// in second big sub folder or less than eighty percent(<80%) store in max sub folder.
                    final int maxScanTaskSize = isMultiMediaPath(fileType) ? TASK_LIMIT_SIZE : 2000;
                    if ((totalSize > maxScanTaskSize && (subTotalSize - subFolderSize) < TASK_LIMIT_SIZE)
                            && (secondBigSize > totalSize * 10 / 100 || firstBigSize < totalSize * 80 / 100)) {
                        isNeedParse = true;
                    }
                }
            }

            /// 3. Store current folder structure in hash map(first parameter order must be same as define in class,
            /// like totalSize, folderSize, subTotalSize, subFolderSize, fileType and serialNum)
            folder = new FolderStructure(
                            new int[] { totalSize, folderSize, subTotalSize, subFolderSize, fileType, mSerialNum },
                            file.lastModified() / 1000,
                            isNeedParse,
                            mPath);
            folder.setSubFileList(isNeedParse ? subFileList : null);
            mFolderMap.put(folderPath, folder);
            return folder;
        }
    }

    /**
     * Get layer size, first layer is below store path in ParseTask(such as
     * /storage/sdcard0/Music is first layer).
     *
     * @param folderPath used to get it's layer size
     * @return current layer size
     */
    private int getLayerSizeByPath(String folderPath, String prefixPath) {
        if (prefixPath != null) {
            folderPath = folderPath.substring(prefixPath.lastIndexOf("/"));
        }
        String[] paths = null;
        if (folderPath != null) {
            paths = folderPath.split("/");
        }
        return paths != null ? paths.length : 0;
    }

    private void addToQueueByType(String path, int fileType) {
        if ((fileType & AUDIO_VIDEO_FILE) > 0) {
            mAudioVideoQueue.add(path);
        } else if ((fileType & IMAGE_FILE) > 0) {
            mImageQueue.add(path);
        } else {
            mNormalFileQueue.add(path);
        }
    }

    /**
     * Execute single type scan task to threadpool;
     */
    private synchronized void executeSingleTypeScanTask() {
        if (mHasExecutedAllTask) {
            MtkLog.w(TAG, "executeSingleTypeScanTask with all task have been executed, it may happen when stopScan");
            return;
        }
        execute(new SingleTypeScanTask(mAudioVideoQueue, PRIORITY_EXTREME + MORE_PRIOR));
        execute(new SingleTypeScanTask(mImageQueue, PRIORITY_EXTREME));
        execute(new SingleTypeScanTask(mNormalFileQueue, PRIORITY_EXTREME + LESS_PRIOR));
    }

    private synchronized void executeAllTask() {
        if (mHasExecutedAllTask) {
            MtkLog.w(TAG, "executeAllTask with all task have been executed, it may happen when stopScan");
            return;
        }
        mHasExecutedAllTask = true;
        String path;
        FolderStructure folder;
        int serialNum = 1;
        int size;
        /// 1. Audio and Video(folder)
        while (!mAudioVideoQueue.isEmpty()) {
            path = mAudioVideoQueue.poll();
            /// Check get path whether null for multi-thread modify
            if (path == null) {
                continue;
            }
            folder = mFolderMap.get(path);
            size = getTotalSize(folder);
            execute(new ScanTask(path, EXTERNAL_VOLUME, PRIORITY_HIGH - serialNum++, size));
        }
        serialNum = 1;
        /// 2. Image(folder)
        while (!mImageQueue.isEmpty()) {
            path = mImageQueue.poll();
            /// Check get path whether null for multi-thread modify
            if (path == null) {
                continue;
            }
            folder = mFolderMap.get(path);
            size = getTotalSize(folder);
            execute(new ScanTask(path, EXTERNAL_VOLUME, PRIORITY_HIGH - serialNum++, size));
        }
        ArrayList<String> list = new ArrayList<String>();
        int total = 0;
        serialNum = 1;
        /// 3. Normal file(folder)
        while (!mNormalFileQueue.isEmpty()) {
            path = mNormalFileQueue.poll();
            /// Check get path whether null for multi-thread modify
            if (path == null) {
                continue;
            }
            folder = mFolderMap.get(path);
            size = getTotalSize(folder);
            /// Combine small folder to normal one and create task when them files size more than 100;
            total += size;
            list.add(path);
            if (size >= TASK_LIMIT_SIZE || total >= TASK_LIMIT_SIZE || mNormalFileQueue.isEmpty()) {
                path = list.toString();
                path = path.substring(1, path.length() - 1);
                execute(new ScanTask(path, EXTERNAL_VOLUME, PRIORITY_LOW - serialNum++, total));
                total = 0;
                list.clear();
            }
        }
        /// 4. Single file or empty folder
        size = mSingleFileList.size();
        if (size > 0) {
            String single = mSingleFileList.toString();
            single = PREFIX_SINGLEFILE + single.substring(1, single.length() - 1);
            execute(new ScanTask(single, EXTERNAL_VOLUME, PRIORITY_NORMAL, size));
            mSingleFileList.clear();
        }
        /// all task have been executed, shutdown threadpool.
        if (mParseLatch.getCount() == 0) {
            mServiceHandler.removeMessages(MediaInserter.MSG_SHUTDOWN_THREADPOOL);
            mServiceHandler.sendEmptyMessage(MediaInserter.MSG_SHUTDOWN_THREADPOOL);
        }
    }

    private int getTotalSize(FolderStructure folder) {
        return folder != null ? folder.mTotalSize : 0;
    }

    private int getFolderSize(FolderStructure folder) {
        return folder != null ? folder.mFolderSize : 0;
    }

    private int getSubTotalSize(FolderStructure folder) {
        return folder != null ? folder.mSubTotalSize : 0;
    }

    private int getSubFolderSize(FolderStructure folder) {
        return folder != null ? folder.mSubFolderSize : 0;
    }

    private int getFolderFileType(FolderStructure folder) {
        return folder != null ? folder.mFileType : 0;
    }

    private int getFolderSerialNum(FolderStructure folder) {
        return folder != null ? folder.mSerialNum : 0;
    }

    private String[] getSubFileList(FolderStructure folder, String folderPath) {
        String[] subFileList = folder != null ? folder.mSubFileList : null;
        if (subFileList == null) {
            subFileList = new File(folderPath).list();
        }
        return subFileList;
    }

    private boolean isNeedParse(FolderStructure folder) {
        return folder != null ? folder.mNeedParse : false;
    }

    private boolean isEmptyFolder(FolderStructure folder) {
        return getSubTotalSize(folder) == 0;
    }

    private boolean isMultiMediaPath(FolderStructure folder) {
        return folder != null ? (folder.mFileType & MULTI_MEDIA_FILE) > 0 : false;
    }

    private boolean isMultiMediaPath(int fileType) {
        return (fileType & MULTI_MEDIA_FILE) > 0;
    }

    private boolean isNoMediaPath(FolderStructure folder) {
        return folder != null ? (folder.mFileType & NO_MEDIA_PATH) > 0 : false;
    }

    private boolean isRootFolder(String checkPath, FolderStructure folder) {
        return folder != null ? folder.mRootFolderPath.equals(checkPath) : false;
    }

    private int getMaxScanTaskSizeByType(int fileType) {
        return isMultiMediaPath(fileType) ? 500 : 2000;
    }

    private int getFileTypeByName(String fileName) {
        MediaFileType mediaType = MediaFile.getFileType(fileName);
        int fileType = mediaType == null ? 0 : mediaType.fileType;
        if (MediaFile.isAudioFileType(fileType) || MediaFile.isVideoFileType(fileType)) {
            return AUDIO_VIDEO_FILE;
        } else if (MediaFile.isImageFileType(fileType)) {
            return IMAGE_FILE;
        } else {
            return NORMAL_FILE;
        }
    }

    private class FolderStructure {
        private int mTotalSize;
        private int mFolderSize;
        private int mSubTotalSize;
        private int mSubFolderSize;
        private int mFileType;
        private int mSerialNum;
        private long mLastModified; /// second
        private boolean mNeedParse;
        private String mRootFolderPath;
        private String[] mSubFileList;

        public FolderStructure(int[] folderAttr, long lastModified, boolean needParse, String rootFolderPath) {
            mTotalSize      = folderAttr[0];
            mFolderSize     = folderAttr[1];
            mSubTotalSize   = folderAttr[2];
            mSubFolderSize  = folderAttr[3];
            mFileType       = folderAttr[4];
            mSerialNum      = folderAttr[5];

            mLastModified = lastModified;
            mNeedParse = needParse;
            mRootFolderPath = rootFolderPath;
            mSubFileList = null;
        }
        public void setSubFileList(String[] subFileList) {
            mSubFileList = subFileList;
        }
    }

    /// 2. ScanTask
    private class ScanTask extends Task {
        private final String mPath;
        private final String mVolume;

        public ScanTask(String scanPath, String volume, int priority, int size) {
            super(priority, size);
            mPath = scanPath;
            mVolume = volume;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_LESS_FAVORABLE);
            scan(mPath, mVolume, mPriority, mSize);
        }
    }

    /**
     * Only scan single type file in this task, there are three type: audio/video, image, normal file
     * @author mtk54154
     *
     */
    private class SingleTypeScanTask extends Task {
        private PriorityBlockingQueue<String> mQueue;

        public SingleTypeScanTask(PriorityBlockingQueue<String> queue, int priority) {
            super(priority, 0);
            mQueue = queue;
        }

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_LESS_FAVORABLE);
            MtkLog.v(TAG, "begin  SingleTypeScanTask in " + Thread.currentThread().getName() + ": " +  mQueue);
            long latchCount = mParseLatch.getCount();
            while (!mQueue.isEmpty() || latchCount > 0) {
                if (latchCount > 0 && mQueue.isEmpty()) {
                    try {
                        MtkLog.d(TAG, "Sleep 1000ms to wait parse finish to do SingleTypeScanTask");
                        mParseLatch.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        MtkLog.w(TAG, "Wait parse finish in SingleTypeScanTask with InterruptedException", e);
                    }
                }
                if (!mQueue.isEmpty()) {
                    String path = mQueue.poll();
                    /// Check get path whether null for multi-thread modify
                    if (path == null) {
                        continue;
                    }
                    FolderStructure folder = mFolderMap.get(path);
                    int size = getTotalSize(folder);
                    scan(path, MediaProvider.EXTERNAL_VOLUME, mPriority - getFolderSerialNum(folder), size);
                }
                latchCount = mParseLatch.getCount();
            }
            long end = System.currentTimeMillis();
            MtkLog.v(TAG, "finish SingleTypeScanTask in " + Thread.currentThread().getName()
                    + " cost " + (end - start) + "ms");
        }
    }

    private void scan(String path, String volume, int priority, int size) {
        MtkLog.v(TAG, "scan  start in " + Thread.currentThread().getName() + ": size = " + size
                + ", priority = " + priority + "(" + path + "");
        long start = System.currentTimeMillis();
        try {
            MediaScanner scanner = createMediaScanner();
            boolean isSingelFile = false;
            if (path.startsWith(PREFIX_SINGLEFILE)) {
                path = path.substring(PREFIX_SINGLEFILE.length());
                isSingelFile = true;
            }
            String[] scanPath = path.split(", ");
            ArrayList<String> playlist = scanner.scanFolders(mInsertHanlder, scanPath, volume, isSingelFile);
            if (!playlist.isEmpty()) {
                synchronized (mPlaylistFilePathList) {
                    mPlaylistFilePathList.addAll(playlist);
                }
            }
       } catch (Exception e) {
            Log.e(TAG, "exception in MediaScanner scan " + path, e);
       }
       long end = System.currentTimeMillis();
       MtkLog.v(TAG, "scan finsih in " + Thread.currentThread().getName() + ": size = " + size
               + ", priority = " + priority + ", cost = " + (end - start) + "ms (" + path + ")");
    }

    private MediaScanner createMediaScanner() {
        MediaScanner scanner = new MediaScanner(mContext);
        Locale locale = mContext.getResources().getConfiguration().locale;
        if (locale != null) {
            String language = locale.getLanguage();
            String country = locale.getCountry();
            if (language != null) {
                if (country != null) {
                    scanner.setLocale(language + "_" + country);
                } else {
                    scanner.setLocale(language);
                }
            }
        }
        return scanner;
    }

    private class InsertFolderTask extends Task {
        private ContentResolver mContentResolver;

        public InsertFolderTask() {
            super(PRIORITY_MAX + LESS_PRIOR, 0);
            mContentResolver = mContext.getContentResolver();
        }
         // HQ_pangxuhui 20150925 for HQ01331800 add by mtk begin
		private boolean isExistInFileSystem(String folderPath) {
		   return new File(folderPath).exists();
		}
		// HQ_pangxuhui 20150925 add by mtk end
        @Override
        public void run() {
            long start = System.currentTimeMillis();
            int size = mFolderMap.size();
            List<ContentValues> folderList = new ArrayList<ContentValues>(size);
            Iterator<Entry<String, FolderStructure>> iterator = mFolderMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<String, FolderStructure> entry = iterator.next();
                String folderPath = entry.getKey();
                FolderStructure folderStructure = entry.getValue();
                /// Root folder path(store in each directory) have been insert while begin scanning
                if (!isExistInDatabase(folderPath)) {
					// add by mtk begin
					/// If folder have been deleted right now, remove from map and do next check.
					if (!isExistInFileSystem(folderPath)) {
						iterator.remove();
						continue;
					}
					// add by mtk end
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Files.FileColumns.TITLE, MediaFile.getFileTitle(folderPath));
                    values.put(MediaStore.Files.FileColumns.FORMAT, MtpConstants.FORMAT_ASSOCIATION);
                    values.put(MediaStore.Files.FileColumns.DATA, folderPath);
                    values.put(MediaStore.Files.FileColumns.DATE_MODIFIED, folderStructure.mLastModified);
                    values.put(MediaStore.Files.FileColumns.SIZE, 0);
                    values.put(MediaStore.Files.FileColumns.IS_DRM, 0);
                    folderList.add(values);

                    if (folderList.size() >= 500) {
                        sortByPath(folderList);
                        flush(MediaScannerInserter.FILE_URI, folderList);
                        folderList.clear();
                    }
                }
            }
            if (!folderList.isEmpty()) {
                sortByPath(folderList);
                flush(MediaScannerInserter.FILE_URI, folderList);
            }
            /// Insert task finish, execute single type scan task.
            executeSingleTypeScanTask();
            long end = System.currentTimeMillis();
            MtkLog.v(TAG, "Insert all folder entries finsih in " + Thread.currentThread().getName() + ": folder size = "
                    + mFolderMap.size() + ", insert num = " + folderList.size() + ", cost = " + (end - start) + "ms");
        }

        private void sortByPath(List<ContentValues> list) {
            Collections.sort(list, new Comparator<ContentValues>() {
                public int compare(ContentValues old, ContentValues latest) {
                    String oldPath = old.getAsString(MediaStore.Files.FileColumns.DATA);
                    String latestPath = latest.getAsString(MediaStore.Files.FileColumns.DATA);
                    if (latestPath != null && oldPath != null) {
                        return oldPath.compareTo(latestPath);
                    }
                    return 0;
                };
            });
        }

        private boolean isExistInDatabase(String folderPath) {
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(MediaScannerInserter.FILE_URI, null,
                                MediaStore.Files.FileColumns.DATA + "=?", new String[] {folderPath}, null, null);
                return cursor != null && cursor.moveToFirst();
            } catch (Exception e) {
                MtkLog.e(TAG, "Check isExistInDatabase with Exception for " + folderPath, e);
                return true;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        private void flush(Uri tableUri, List<ContentValues> list) {
            long start = System.currentTimeMillis();
            int size = list.size();
            ContentValues[] valuesArray = new ContentValues[size];
            valuesArray = list.toArray(valuesArray);
            try {
                mContentResolver.bulkInsert(tableUri, valuesArray);
            } catch (Exception e) {
                MtkLog.e(TAG, "bulkInsert with Exception for " + tableUri, e);
            }
            long end = System.currentTimeMillis();
            MtkLog.d(TAG, "flush " + tableUri + " with size " + size + " which cost " + (end - start) + "ms");
        }
    }
}
