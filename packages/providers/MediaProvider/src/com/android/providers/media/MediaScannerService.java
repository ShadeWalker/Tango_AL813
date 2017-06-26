/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

import static android.media.MediaInserter.*;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.IMediaScannerListener;
import android.media.IMediaScannerService;
import android.media.MediaScanner;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.MediaStore;


public class MediaScannerService extends Service implements Runnable
{
    private static final String TAG = "MediaScannerService";
    private static final boolean LOG = true;

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;
    private PowerManager.WakeLock mWakeLock;
    private String[] mExternalStoragePaths;

    private void openDatabase(String volumeName) {
        try {
            ContentValues values = new ContentValues();
            values.put("name", volumeName);
            getContentResolver().insert(Uri.parse("content://media/"), values);
        } catch (IllegalArgumentException ex) {
            MtkLog.w(TAG, "failed to open media database");
        }
    }

    private MediaScanner createMediaScanner() {
        MediaScanner scanner = new MediaScanner(this);
        Locale locale = getResources().getConfiguration().locale;
        if (locale != null) {
            String language = locale.getLanguage();
            String country = locale.getCountry();
            String localeString = null;
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

    private void scan(String[] directories, String volumeName) {
        MtkLog.d(TAG, "scan>>>: volumeName = " + volumeName + ", directories = " + Arrays.toString(directories));
        Uri uri = Uri.parse("file://" + directories[0]);
        // don't sleep while scanning
        mWakeLock.acquire();

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MEDIA_SCANNER_VOLUME, volumeName);
            Uri scanUri = getContentResolver().insert(MediaStore.getMediaScannerUri(), values);

            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_STARTED, uri));

            try {
                if (volumeName.equals(MediaProvider.EXTERNAL_VOLUME)) {
                    openDatabase(volumeName);
                }

                MediaScanner scanner = createMediaScanner();
                scanner.scanDirectories(directories, volumeName);
                /// M: eject sdcard while scanning, need do prescan to delete entries
                /// which store on sdcard
                if (mNeedScanAgain) {
                    scanner.preScanAll(MediaProvider.EXTERNAL_VOLUME);
                    MtkLog.d(TAG, "do prescan after scan finish because sdcard eject while scanning");
                }
            } catch (Exception e) {
                MtkLog.e(TAG, "exception in MediaScanner.scan()", e);
            }

            getContentResolver().delete(scanUri, null, null);

        } catch (Exception ex) {
            MtkLog.e(TAG, "exception in MediaScanner.scan()", ex);
        } finally {
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_FINISHED, uri));
            mWakeLock.release();
        }
        MtkLog.d(TAG, "scan<<<");
    }

    /**
     * M: Scan given folder without do prescan.
     *
     * @param folders
     * @param volumeName
     */
    private void scanFolder(String[] folders, String volumeName) {
        MtkLog.d(TAG, "scanFolder>>>: volumeName = " + volumeName + ", folders = " + Arrays.toString(folders));
        /// don't sleep while scanning
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
        try {
            if (volumeName.equals(MediaProvider.EXTERNAL_VOLUME)) {
                openDatabase(volumeName);
            }
            MediaScanner scanner = createMediaScanner();
            scanner.scanFolders(folders, volumeName, false);
        } catch (Exception e) {
            MtkLog.e(TAG, "exception in scanFolder", e);
        } finally {
            if (mWakeLock != null && mWakeLock.isHeld() && mMediaScannerThreadPool == null) {
                mWakeLock.release();
            }
        }
        MtkLog.d(TAG, "scanFolder<<<");
    }

    @Override
    public void onCreate()
    {
        MtkLog.d(TAG, "onCreate: CpuCoreNum = " + getCpuCoreNum() + ", isLowRamDevice = " + isLowRamDevice());
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        StorageManager storageManager = (StorageManager)getSystemService(Context.STORAGE_SERVICE);
        mExternalStoragePaths = storageManager.getVolumePaths();

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.
        Thread thr = new Thread(null, this, "MediaScannerService");
        thr.start();

        /// M: Register a unmount receiver to make sure pre-scan again when sdcard unmount at scanning.
        IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_EJECT);
        filter.addDataScheme("file");
        filter.setPriority(100);
        registerReceiver(mUnmountReceiver, filter);

        mIsThreadPoolEnable = getCpuCoreNum() >= 4 && !isLowRamDevice();
        MtkLog.d(TAG, "onCreate : mIsThreadPoolEnable = " + mIsThreadPoolEnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        while (mServiceHandler == null) {
            synchronized (this) {
                try {
                    wait(100);
                } catch (InterruptedException e) {
                    MtkLog.e(TAG, "onStartCommand: InterruptedException!");
                }
            }
        }

        if (intent == null) {
            MtkLog.e(TAG, "Intent is null in onStartCommand: ",
                new NullPointerException());
            return Service.START_NOT_STICKY;
        }

        /// M: deliver different message for scan single file and directory
        Bundle arguments = intent.getExtras();
        int what;

		// /added by guofeiyao
		if ( arguments == null )
		return Service.START_NOT_STICKY;
		// /end
		
        if (arguments.getString("filepath") != null) {
            what = MSG_SCAN_SINGLE_FILE;
        } else {
            what = MSG_SCAN_DIRECTORY;
        }
        Message msg = mServiceHandler.obtainMessage(what, startId, -1, arguments);
        mServiceHandler.sendMessage(msg);

        // Try again later if we are killed before we can finish scanning.
        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy()
    {
        MtkLog.d(TAG, "onDestroy");
        // Make sure thread has started before telling it to quit.
        while (mServiceLooper == null) {
            synchronized (this) {
                try {
                    wait(100);
                } catch (InterruptedException e) {
                    MtkLog.e(TAG, "onDestroy: InterruptedException!");
                }
            }
        }
        mServiceLooper.quit();

        /// M: MediaScanner Performance turning {@
        /// If service has destroyed, we need release wakelock.
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
            MtkLog.w(TAG, "onDestroy: release wakelock when service destroy");
        }
        /// @}
        /// M: register at onCreate and unregister at onDestory
        unregisterReceiver(mUnmountReceiver);
    }

    public void run()
    {
        Looper.prepare();

        mServiceLooper = Looper.myLooper();
        mServiceHandler = new ServiceHandler();

        /// M: reduce thread priority after ServiceHandler have been created to avoid cpu starvation
        /// which may cause ANR because create service handler too slow.
        // reduce priority below other background threads to avoid interfering
        // with other services at boot time.
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_LESS_FAVORABLE);

        Looper.loop();
    }
   
    private Uri scanFile(String path, String mimeType) {
        String volumeName = MediaProvider.EXTERNAL_VOLUME;
        openDatabase(volumeName);
        MediaScanner scanner = createMediaScanner();
        try {
            // make sure the file path is in canonical form
            String canonicalPath = new File(path).getCanonicalPath();
            return scanner.scanSingleFile(canonicalPath, volumeName, mimeType);
        } catch (Exception e) {
            MtkLog.e(TAG, "bad path " + path + " in scanFile()", e);
            return null;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }
    
    private final IMediaScannerService.Stub mBinder = 
            new IMediaScannerService.Stub() {
        public void requestScanFile(String path, String mimeType, IMediaScannerListener listener)
        {
            MtkLog.d(TAG, "IMediaScannerService.scanFile: " + path + " mimeType: " + mimeType);
            Bundle args = new Bundle();
            args.putString("filepath", path);
            args.putString("mimetype", mimeType);
            if (listener != null) {
                args.putIBinder("listener", listener.asBinder());
            }
            startService(new Intent(MediaScannerService.this,
                    MediaScannerService.class).putExtras(args));
        }

        public void scanFile(String path, String mimeType) {
            requestScanFile(path, mimeType, null);
        }
    };

    private final class ServiceHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            /// M: MediaScanner Performance turning {@
            /// Add two message for shutdown threadpool
            /// and handle scan finish request.
            MtkLog.v(TAG, "handleMessage: what = " + msg.what + ", startId = " + msg.arg1 + ", arguments = " + msg.obj);
            switch (msg.what) {
                case MSG_SCAN_SINGLE_FILE:
                    handleScanSingleFile(msg);
                    break;

                case MSG_SCAN_DIRECTORY:
                    handleScanDirectory(msg);
                    break;

                case MSG_SHUTDOWN_THREADPOOL:
                    handleShutdownThreadpool();
                    break;

                case MSG_SCAN_FINISH_WITH_THREADPOOL:
                    handleScanFinish();
                    break;

                default:
                    MtkLog.w(TAG, "unsupport message " + msg.what);
                    break;
            }
            /// @}
        }
    };

    private void handleScanSingleFile(Message msg) {
        Bundle arguments = (Bundle) msg.obj;
        String filePath = arguments.getString("filepath");
        try {
            IBinder binder = arguments.getIBinder("listener");
            IMediaScannerListener listener =
                (binder == null ? null : IMediaScannerListener.Stub.asInterface(binder));
            Uri uri = null;
            try {
                /// M: If file path is a directory we need scan the folder, else just scan single file.{@
                File file = new File(filePath);
                if (file.isDirectory()) {
                    scanFolder(new String[] {filePath}, MediaProvider.EXTERNAL_VOLUME);
                } else {
                    uri = scanFile(filePath, arguments.getString("mimetype"));
                }
                /// @}
            } catch (Exception e) {
                MtkLog.e(TAG, "Exception scanning single file " + filePath, e);
            }
            if (listener != null) {
                listener.scanCompleted(filePath, uri);
            }
        } catch (Exception e) {
            MtkLog.e(TAG, "Exception in handleScanSingleFile", e);
        }

        /// M: MediaScanner Performance turning {@
        /// Only stop service when thread pool terminate
        if (mStartId != -1) {
            stopSelfResult(mStartId);
            mStartId = msg.arg1;
        } else {
            stopSelf(msg.arg1);
        }
        /// @}
    }

    private void handleScanDirectory(Message msg) {
        Bundle arguments = (Bundle) msg.obj;
        try {
            String volume = arguments.getString("volume");
            String[] directories = null;

            if (MediaProvider.INTERNAL_VOLUME.equals(volume)) {
                // scan internal media storage
                directories = new String[] {
                        Environment.getRootDirectory() + "/media",
                };
            } else if (MediaProvider.EXTERNAL_VOLUME.equals(volume)) {
                // scan external storage volumes
                directories = mExternalStoragePaths;
                /// M: in tablet multiple user case, User proccess don't need to scan external 
                /// sdcard except primary external sdcard @{
                if(("1").equals(SystemProperties.get("ro.mtk_owner_sdcard_support"))
                    && UserHandle.myUserId() != UserHandle.USER_OWNER) {
                    directories = new String[] {mExternalStoragePaths[0]};
                }
                /// @}
                
                /// M: MediaScanner Performance turning {@
                /// Thread pool enable, use threadpool to scan.
                if (mIsThreadPoolEnable) {
                    mStartId = msg.arg1;
                    if (mMediaScannerThreadPool == null) {
                        scanWithThreadPool(directories, volume);
                    } else {
                        mNeedScanAgain = true;
                    }
                    return;
                }
                /// @}
            }

            if (directories != null) {
                long start = System.currentTimeMillis();
                MtkLog.d(TAG, "start scanning volume " + volume + ": " + Arrays.toString(directories));
                scan(directories, volume);
                long end = System.currentTimeMillis();
                MtkLog.d(TAG, "done scanning volume " + volume + " cost " + (end - start) + "ms");
            }
        } catch (Exception e) {
            MtkLog.e(TAG, "Exception in handleScanDirectory", e);
        }

        /// M: MediaScanner Performance turning {@
        /// Only stop service when thread pool terminate
        if (mStartId != -1) {
            stopSelfResult(mStartId);
            mStartId = msg.arg1;
        } else {
            stopSelf(msg.arg1);
        }
        /// @}
    }

    /// M: MediaScanner Performance turning {@

    private MediaScannerThreadPool mMediaScannerThreadPool;
    private MediaScannerInserter mMediaScannerInserter;
    /// M: This MediaScanner use to do pre-scan before create thread pool and post-scan after
    /// thread pool terminate(finish scan).
    private MediaScanner mPreScanner;
    /// M: Only when device is not low ram device and it's cpu core num big than 4 need enable thread pool to scan.
    private boolean mIsThreadPoolEnable = false;
    /// M: Start mediascanner service id, when scan finish with thread pool we need stop
    /// service with this id.
    private int mStartId = -1;
    /// M: use them to restore scan times.
    private long mScanStartTime;
    private long mPreScanFinishTime;
    private long mScanFinishTime;
    private long mPostScanFinishTime;

    /// If true means when finish scan with threadpool we need do scan again because we may receive scan request
    /// at scanning with threadpool
    private boolean mNeedScanAgain = false;

    /// M: Stop scan and do scan later to avoid unmount sdcard fail when user unmount sdcard while scanning.
    private BroadcastReceiver mUnmountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (this) {
                /// When eject sdcard while scanning, we need stop current and do scan later. but
                /// need do with different case:
                /// 1. threadpool, stop current threadpool and send message to do scan later
                /// 2. default, mark mNeedScanAgain to true and do prescan after scan finish
                boolean isMediaScannerScanning = false;
                if (mMediaScannerThreadPool != null && !mMediaScannerThreadPool.isTerminated()) {
                    isMediaScannerScanning = true;
                } else if (!mIsThreadPoolEnable) {
                    Cursor cursor = null;
                    try {
                        cursor = getContentResolver().query(
                                MediaStore.getMediaScannerUri(),
                                new String[] { MediaStore.MEDIA_SCANNER_VOLUME },
                                null, null, null);
                        mNeedScanAgain = cursor != null && cursor.moveToFirst();
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                }

                if (Intent.ACTION_MEDIA_EJECT.equals(intent.getAction()) && isMediaScannerScanning) {
                    /// stop scan in threadpool if scan with threadpool
                    if (mIsThreadPoolEnable) {
                        mMediaScannerThreadPool.stopScan();
                        mNeedScanAgain = false;
                    }
                    /// if not unmount all, do scan later(10000ms)
                    boolean unmountAll = intent.getBooleanExtra("mount_unmount_all", false);
                    if (!unmountAll) {
                        /// store start id to avoid service destroy after stop threadpool.
                        int startId = mStartId;
                        mStartId = -1;
                        /// remove all scan directory message and send delay message to scan later
                        mServiceHandler.removeMessages(MSG_SCAN_DIRECTORY);
                        Bundle arguments = new Bundle();
                        arguments.putString("volume", MediaProvider.EXTERNAL_VOLUME);
                        Message msg = mServiceHandler.obtainMessage(MSG_SCAN_DIRECTORY, startId, -1, arguments);
                        mServiceHandler.sendMessageDelayed(msg, 10000);
                    }
                    MtkLog.v(TAG, "sdcard eject, stop scan to avoid sdcard unmount fail and do scan later(10s) if need");
                }
            }
        }
    };

    /**
     * M: Scan given directories with thread pool.
     *
     * @param directories need scan directories.
     * @param volume external or internal.
     */
    private void scanWithThreadPool(String[] directories, String volume) {
        MtkLog.v(TAG, "scanWithThreadPool>>> " + Arrays.toString(directories));
        mScanStartTime = System.currentTimeMillis();
        /// 1. Remove old scan directory message.
        mServiceHandler.removeMessages(MSG_SCAN_DIRECTORY);

        /// 2.Acquire wakelock to avoid sleep while scanning
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
            MtkLog.v(TAG, "acquire wakelock to avoid sleeping while scanning with threadpool");
        }

        /// 3.Prepare down provider to save scan out data
        ContentValues values = new ContentValues();
        values.put(MediaStore.MEDIA_SCANNER_VOLUME, volume);
        getContentResolver().insert(MediaStore.getMediaScannerUri(), values);
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_STARTED, Uri.parse("file://" + mExternalStoragePaths[0])));
        openDatabase(volume);

        /// 4.Initialize thread pool
        initializeThreadPool(directories, volume);

        /// 5.First pre scan all objects before scan all folders(post scan them when scan finish).
        /// then parse out all task and execute them to thread pool.
        try {
            mPreScanner.preScanAll(MediaProvider.EXTERNAL_VOLUME);
        } catch (Exception e) {
            MtkLog.e(TAG, "Exception in scanWithThreadPool do preScanAll", e);
        }
        mPreScanFinishTime = System.currentTimeMillis();
        mMediaScannerThreadPool.parseScanTask();

        MtkLog.v(TAG, "scanWithThreadPool finished execute all task!");
    }

    /**
     * M: initialize thread pool parameter
     * @param directories
     * @param volume
     */
    private void initializeThreadPool(String[] directories, String volume) {
        if (mMediaScannerThreadPool == null) {
            MtkLog.v(TAG, "initializeThreadPool with creating new one");
            mPreScanner = createMediaScanner();
            mMediaScannerInserter = new MediaScannerInserter(this, mServiceHandler);
            mMediaScannerThreadPool = new MediaScannerThreadPool(this, directories, mServiceHandler,
                    mMediaScannerInserter.getInsertHandler());
        }
    }

    private void releaseThreadPool() {
        synchronized (this) {
            MtkLog.v(TAG, "release resource: release threadpool, quit insert thread and relase pre scanner");
            mPreScanner.release();
            mMediaScannerInserter.release();
            mMediaScannerThreadPool = null;
            mPreScanner = null;
            mMediaScannerInserter = null;
            MtkLog.v(TAG, "release");
        }
    }

    private void handleShutdownThreadpool() {
        if (mMediaScannerThreadPool != null && !mMediaScannerThreadPool.isShutdown()) {
            MtkLog.v(TAG, "handleShutdownThreadpool..................");
            mMediaScannerThreadPool.shutdown();
        }
    }

    /**
     * M: Scan finish with thread pool, do pre scan again if need and post scan with preScanner, then update
     * provider and send broadcast to notify scan finish. If there is a scan request coming during scanning,
     * check right now and scan all files again if need, if no new scan request, release thread pool.
     */
    private void handleScanFinish() {
        /// 1.Scan finish, preScan if need, post scan to generate playlist files. Then send broadcast to
        /// to notify app and release wakelock.
        try {
            mScanFinishTime = System.currentTimeMillis();
            /// After scan finish we need postscan.
            mPreScanner.postScanAll(mMediaScannerThreadPool.getPlaylistFilePaths());
            MtkLog.d(TAG, "postScanAll with playlist files list " + mMediaScannerThreadPool.getPlaylistFilePaths());
            getContentResolver().delete(MediaStore.getMediaScannerUri(), null, null);
        } catch (Exception e) {
            MtkLog.e(TAG, "Exception in handleScanFinish", e);
        }
        mPostScanFinishTime = System.currentTimeMillis();
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_FINISHED, Uri.parse("file://" + mExternalStoragePaths[0])));
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        releaseThreadPool();

        MtkLog.d(TAG, " prescan time: " + (mPreScanFinishTime - mScanStartTime) + "ms\n");
        MtkLog.d(TAG, "    scan time: " + (mScanFinishTime - mPreScanFinishTime) + "ms\n");
        MtkLog.d(TAG, "postscan time: " + (mPostScanFinishTime - mScanFinishTime) + "ms\n");
        MtkLog.d(TAG, "scan exteranl with thread pool cost " + (mPostScanFinishTime - mScanStartTime) + "ms");
        MtkLog.v(TAG, "scanWithThreadPool<<< finish scan so release wakelock and send scan finish intent");

        /// 2.Check whether need scan again(scan request while scanning with thread pool), if not, release
        /// thread pool and stop service.
        if (mNeedScanAgain) {
            MtkLog.v(TAG, "Scan all file again in threadpool because storage mounted while scanning.");
            StorageManager storageManager = (StorageManager)getSystemService(Context.STORAGE_SERVICE);
            mExternalStoragePaths = storageManager.getVolumePaths();
            if (mExternalStoragePaths != null) {
                MtkLog.v(TAG, "Scan again mExternalStoragePaths: "+Arrays.toString(mExternalStoragePaths));
                scanWithThreadPool(mExternalStoragePaths, MediaProvider.EXTERNAL_VOLUME);
            }
            mNeedScanAgain = false;
        } else if (mStartId != -1) {
            stopSelfResult(mStartId);
            mStartId = -1;
        }
    }

    private int getCpuCoreNum() {
        return Runtime.getRuntime().availableProcessors();
    }

    private boolean isLowRamDevice() {
        final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        return am.isLowRamDevice();
    }
}
