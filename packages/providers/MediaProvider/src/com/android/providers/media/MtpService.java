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

package com.android.providers.media;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.mtp.MtpDatabase;
import android.mtp.MtpServer;
import android.mtp.MtpStorage;
import android.os.Environment;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.UEventObserver;
import android.util.Log;

import com.mediatek.storage.StorageManagerEx;

import java.io.File;
import java.util.HashMap;

public class MtpService extends Service {
    private static final String TAG = "MtpService";
    private static final boolean LOGD = true;

    // We restrict PTP to these subdirectories
    private static final String[] PTP_DIRECTORIES = new String[] {
        Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_PICTURES,
    };

    // Add for update Storage
    private boolean mIsSDExist = false;
    private static final String SD_EXIST = "SD_EXIST";
    private static final String ACTION_DYNAMIC_SD_SWAP = "com.mediatek.SD_SWAP";

    // Add for update Storage

    private void addStorageDevicesLocked() {
        if (mPtpMode) {
            // In PTP mode we support only primary storage
            final StorageVolume primary = StorageManager.getPrimaryVolume(mVolumes);
            final String path = primary.getPath();
            if (path != null) {
                String state = mStorageManager.getVolumeState(path);
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    addStorageLocked(mVolumeMap.get(path));
                }
            }
        } else {
            for (StorageVolume volume : mVolumeMap.values()) {
                addStorageLocked(volume);
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /// M: ALPS00120037, add log for support MTP debugging @{
            MtkLog.w(TAG, "ACTION_USER_PRESENT: BroadcastReceiver: onReceive: synchronized");

            final String action = intent.getAction();
            if (Intent.ACTION_USER_PRESENT.equals(action)) {
                // If the media scanner is running, it may currently be calling
                // sendObjectAdded/Removed, which also synchronizes on mBinder
                // (and in addition to that, all the native MtpServer methods
                // lock the same Mutex). If it happens to be in an mtp device
                // write(), it may block for some time, so process this broadcast
                // in a thread.
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mBinder) {
                            // Unhide the storage units when the user has unlocked the lockscreen
                            if (mMtpDisabled) {
                                addStorageDevicesLocked();
                                mMtpDisabled = false;
                            }
                        }
                    }}, "addStorageDevices").start();
            }
        }
    };


    private final BroadcastReceiver mBootCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /// M: ALPS00120037, add log for support MTP debugging @{
            MtkLog.w(TAG, "ACTION_USER_PRESENT: BroadcastReceiver: onReceive: synchronized");

            final String action = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                // If the media scanner is running, it may currently be calling
                // sendObjectAdded/Removed, which also synchronizes on mBinder
                // (and in addition to that, all the native MtpServer methods
                // lock the same Mutex). If it happens to be in an mtp device
                // write(), it may block for some time, so process this broadcast
                // in a thread.
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mBinder) {
                            // Unhide the storage units when the user has unlocked the lockscreen
                            if (mMtpDisabled) {
                                addStorageDevicesLocked();
                                mMtpDisabled = false;
                            }
                        }
                    }
                }, "addStorageDevices").start();
            }
        }
    };

    /// M: Added for Storage Update @{
    private final BroadcastReceiver mLocaleChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MtkLog.w(TAG, "ACTION_LOCALE_CHANGED: BroadcastReceiver: onReceive: synchronized");

            final String action = intent.getAction();
            if (Intent.ACTION_LOCALE_CHANGED.equals(action) && !mMtpDisabled) {
                synchronized (mBinder) {
                    MtkLog.w(TAG, "ACTION_LOCALE_CHANGED : BroadcastReceiver: onReceive: synchronized");

                    StorageVolume[] volumes = mStorageManager.getVolumeList();
                    mVolumes = volumes;

                    for (int i = 0; i < mVolumes.length; i++) {
                        StorageVolume volume = mVolumes[i];
                        updateStorageLocked(volume);
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mSDSwapReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MtkLog.w(TAG, "ACTION_DYNAMIC_SD_SWAP: BroadcastReceiver: onReceive: synchronized");

            final String action = intent.getAction();
            boolean swapSD;
            if (ACTION_DYNAMIC_SD_SWAP.equals(action) && !mMtpDisabled) {
                synchronized (mBinder) {
                    mIsSDExist = intent.getBooleanExtra(SD_EXIST, false);

                    MtkLog.w(TAG, "ACTION_DYNAMIC_SD_SWAP : BroadcastReceiver: swapSD = " + mIsSDExist);

                    StorageVolume[] volumes = mStorageManager.getVolumeList();
                    mVolumes = volumes;

                    for (int i = 0; i < mVolumes.length; i++) {
                        StorageVolume volume = mVolumes[i];
                        updateStorageLocked(volume);
                    }
                }
            }
        }
    };
    /// M: @}

    private final StorageEventListener mStorageEventListener = new StorageEventListener() {
        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            synchronized (mBinder) {
                Log.d(TAG, "onStorageStateChanged " + path + " " + oldState + " -> " + newState);
                if (Environment.MEDIA_MOUNTED.equals(newState)) {
                    // Modification for ALPS00365000, Scan mStorageMap for checking if there is the
                    // same storage under current StorageList
                    int isExist = 0;
                    for (MtpStorage storage : mStorageMap.values()) {
                        MtkLog.w(TAG, "onStorageStateChanged storage.getPath() = " + storage.getPath());
                        MtkLog.w(TAG, "onStorageStateChanged storage.getStorageId() = 0x"
                                + Integer.toHexString(storage.getStorageId()));

                        if (path.equals(storage.getPath()))
                            isExist = 1;
                        MtkLog.w(TAG, "onStorageStateChanged, isExist = " + isExist);
                    }
                    if (isExist == 0)
                        // Modification for ALPS00365000, Scan mStorageMap for checking if there is
                        // the same storage under current StorageList
                        volumeMountedLocked(path);
                } else if (Environment.MEDIA_MOUNTED.equals(oldState)) {
                    StorageVolume volume = mVolumeMap.remove(path);
                    if (volume != null) {
                        removeStorageLocked(volume);
                    }
                }
            }
        }
    };

    private MtpDatabase mDatabase;
    private MtpServer mServer;
    private StorageManager mStorageManager;
    /** Flag indicating if MTP is disabled due to keyguard */
    private boolean mMtpDisabled;
    private boolean mPtpMode;
    private final HashMap<String, StorageVolume> mVolumeMap = new HashMap<String, StorageVolume>();
    private final HashMap<String, MtpStorage> mStorageMap = new HashMap<String, MtpStorage>();
    private StorageVolume[] mVolumes;
    /// M: Usb configure
    private boolean mIsUsbConfigured;

    @Override
    public void onCreate() {
        registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        registerReceiver(mBootCompleteReceiver, new IntentFilter(Intent.ACTION_BOOT_COMPLETED));
        /// M: Added for Storage Update @{
        registerReceiver(mLocaleChangedReceiver, new IntentFilter(Intent.ACTION_LOCALE_CHANGED));

        if (MediaUtils.IS_SUPPORT_SDCARD_SWAP) {
            registerReceiver(mSDSwapReceiver, new IntentFilter(ACTION_DYNAMIC_SD_SWAP));
        }
        /// M: @}

        mStorageManager = StorageManager.from(this);
        synchronized (mBinder) {
            updateDisabledStateLocked();
            mStorageManager.registerListener(mStorageEventListener);
            StorageVolume[] volumes = mStorageManager.getVolumeList();
            mVolumes = volumes;
            /// M: ALPS00241636, add log for support MTP debugging
            MtkLog.w(TAG, "onCreate: volumes.length=" + volumes.length);
            for (int i = 0; i < volumes.length; i++) {
                String path = volumes[i].getPath();
                String state = mStorageManager.getVolumeState(path);
                /// M: ALPS00241636, add log for support MTP debugging @{
                MtkLog.w(TAG, "onCreate: path of volumes[" + i + "]=" + path);
                MtkLog.w(TAG, "onCreate: state of volumes[" + i + "]=" + state);
                /// M: @}
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    volumeMountedLocked(path);
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (mBinder) {
            updateDisabledStateLocked();
            mIsUsbConfigured = (intent == null ? false : intent.getBooleanExtra(UsbManager.USB_CONFIGURED, false));
            mPtpMode = (intent == null ? false : intent.getBooleanExtra(UsbManager.USB_FUNCTION_PTP, false));
            String[] subdirs = null;
            if (mPtpMode) {
                int count = PTP_DIRECTORIES.length;
                subdirs = new String[count];
                for (int i = 0; i < count; i++) {
                    File file = Environment.getInternalStoragePublicDirectory(PTP_DIRECTORIES[i]);//chenwenshuai modified for HQ01383238 
                    // make sure this directory exists
                    file.mkdirs();
                    subdirs[i] = file.getPath();
                }
            }
            /*Fix ALPS00444854
              MtpService process is killed by VOLD when user plug out SD card(USB will disconnect)
              ActivityManager: Scheduling restart of crashed service com.android.providers.media/.MtpService in 5000ms
              before new a Mtpserver, it need to check if USB is already connected and configure
              to avoid open mtp driver twice
              if MTP can transfer via WIFI, BT ...., must fix this
             */
            if (mIsUsbConfigured) {
                final StorageVolume primary = StorageManager.getPrimaryVolume(mVolumes);
                if (mDatabase != null) {
                    mDatabase.setServer(null);
                }
                mDatabase = new MtpDatabase(this, MediaProvider.EXTERNAL_VOLUME, primary.getPath(), subdirs);
                manageServiceLocked();
            }
        }

        return START_STICKY;
    }

    private void updateDisabledStateLocked() {
        final boolean isCurrentUser = UserHandle.myUserId() == ActivityManager.getCurrentUser();
        final KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        /// M: ALPS00525202
        if (LOGD) {
            Log.w(TAG, "updating state; keyguardManager.isKeyguardLocked()=" + keyguardManager.isKeyguardLocked()
                    + ", keyguardManager.isKeyguardSecure()=" + keyguardManager.isKeyguardSecure());
        }
        /// M: ALPS00525202
        mMtpDisabled = (keyguardManager.isKeyguardLocked() && keyguardManager.isKeyguardSecure()) || !isCurrentUser;
        if (LOGD) {
            Log.w(TAG, "updating state; isCurrentUser=" + isCurrentUser + ", mMtpDisabled=" + mMtpDisabled);
        }
    }

    /**
     * Manage {@link #mServer}, creating only when running as the current user.
     */
    private void manageServiceLocked() {
        final boolean isCurrentUser = UserHandle.myUserId() == ActivityManager.getCurrentUser();
        Log.w(TAG, "manageServiceLocked: starting MTP server in isCurrentUser: " + isCurrentUser);
        if (mServer == null && isCurrentUser) {
            Log.w(TAG, "starting MTP server in " + (mPtpMode ? "PTP mode" : "MTP mode"));
            Log.w(TAG, "starting MTP server in mMtpDisabled = " + mMtpDisabled);
            mServer = new MtpServer(mDatabase, mPtpMode);
            mDatabase.setServer(mServer);
            if (!mMtpDisabled) {
                addStorageDevicesLocked();
            }
            mServer.start();
        } else if (mServer != null && isCurrentUser) {
            /// M: ALPS00840636
            if (mServer != null && mServer.getStatus()) {
                Log.w(TAG, "manageServiceLocked: synchronized, mServer is not null but has been Endup!!");
                Log.w(TAG, "manageServiceLocked: synchronized, delete this one, wait for next startcommand");
                mServer = new MtpServer(mDatabase, mPtpMode);

                if (!mMtpDisabled) {
                    addStorageDevicesLocked();
                }
                mServer.start();
            }
        } else if (mServer != null && !isCurrentUser) {
            /// M: ALPS00840636
            Log.w(TAG, "no longer current user; shutting down MTP server");
            // Internally, kernel will close our FD, and server thread will
            // handle cleanup.
            mServer = null;
            mDatabase.setServer(null);
        } else {
            /// M: ALPS00840636
            Log.w(TAG, "manageServiceLocked: unprocess case");
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        unregisterReceiver(mBootCompleteReceiver);
        mStorageManager.unregisterListener(mStorageEventListener);
        if (mDatabase != null) {
            mDatabase.setServer(null);
        }
        /// M: Added for Storage Update @{
        unregisterReceiver(mLocaleChangedReceiver);

        if (MediaUtils.IS_SUPPORT_SDCARD_SWAP) {
            unregisterReceiver(mSDSwapReceiver);
        }
        /// M: @}
    }

    private final IMtpService.Stub mBinder =
            new IMtpService.Stub() {
        public void sendObjectAdded(int objectHandle) {
            synchronized (mBinder) {
                if (mServer != null) {
                    mServer.sendObjectAdded(objectHandle);
                }
            }
        }

        public void sendObjectRemoved(int objectHandle) {
            synchronized (mBinder) {
                if (mServer != null) {
                    mServer.sendObjectRemoved(objectHandle);
                }
            }
        }

        /// M: ALPS00289309, update Object @{
        public void sendObjectInfoChanged(int objectHandle) {
            synchronized (mBinder) {
                MtkLog.w(TAG, "mBinder: sendObjectInfoChanged, objectHandle = 0x" + Integer.toHexString(objectHandle));
                if (mServer != null) {
                    mServer.sendObjectInfoChanged(objectHandle);
                }
            }
        }
        /// M: @}

        /// M: Added for Storage Update @{
        public void sendStorageInfoChanged(MtpStorage storage) {
            synchronized (mBinder) {
                MtkLog.w(TAG, "mBinder: sendObjectInfoChanged, storage.getStorageId = 0x"
                        + Integer.toHexString(storage.getStorageId()));
                if (mServer != null) {
                    mServer.sendStorageInfoChanged(storage);
                }
            }
        }
        /// M: @}
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void volumeMountedLocked(String path) {
        /// M: Add for update Storage
        StorageVolume[] volumes = mStorageManager.getVolumeList();
        mVolumes = volumes;
        // Add for update Storage
        for (int i = 0; i < mVolumes.length; i++) {
            StorageVolume volume = mVolumes[i];
            if (volume.getPath().equals(path)) {
                mVolumeMap.put(path, volume);
                if (!mMtpDisabled) {
                    // In PTP mode we support only primary storage
                    if (volume.isPrimary() || !mPtpMode) {
                        addStorageLocked(volume);
                    }
                }
                break;
            }
        }
    }

    /// M: Add to check storage info and init with correct one
    private void checkMtpStorageInfoAndCorrectIt(MtpStorage storage) {

        String PATH_SDCARD0 = "/storage/sdcard0";
        String PATH_SDCARD1 = "/storage/sdcard1";

        // deal with /storage/sdcard1
        if (storage.getPath().equals(PATH_SDCARD0) && !storage.isRemovable()) {
            StorageVolume[] volumes = mStorageManager.getVolumeList();

            for (int i = 0; i < volumes.length; i++) {
                String description = volumes[i].getDescription(this);
                String path = volumes[i].getPath();
                boolean removable = volumes[i].isRemovable();
                int storageId = volumes[i].getStorageId();

                // this condition check will make sure that the things got wrong
                if (removable && path.equals(PATH_SDCARD1)) {
                    MtkLog.d(TAG, "volume info, " + ", storageID : " + storageId + ",Path : " + path + ",removable : "
                            + removable + ",Desc : " + description);
                    // only this two is hard value, others dynamic get by path, storage id is not
                    // affected by swap issue
                    storage.setRemovable(removable);
                    storage.setDescription(description);
                    break;
                }
            }
        }

        // deal with /storage/sdcard1
        if (storage.getPath().equals(PATH_SDCARD1) && storage.isRemovable()) {
            StorageVolume[] volumes = mStorageManager.getVolumeList();
            for (int i = 0; i < volumes.length; i++) {
                String description = volumes[i].getDescription(this);
                String path = volumes[i].getPath();
                boolean removable = volumes[i].isRemovable();
                int storageId = volumes[i].getStorageId();
                // apply right Desc, condition will make sure that the things got wrong
                if (!removable && volumes[i].getPath().equals(PATH_SDCARD0)) {
                    MtkLog.d(TAG, "volume info, " + ", storageID : " + storageId + ",Path : " + path + ",removable : "
                            + removable + ",Desc : " + description);

                    // only this two is hard value, others dynamic get by path, storage id is not
                    // affected by swap issue
                    storage.setRemovable(removable);
                    storage.setDescription(description);
                    break;
                }
            }
        }
    }

    private void addStorageLocked(StorageVolume volume) {
        /// M: ALPS00332280 @{
        if (volume == null) {
            MtkLog.e(TAG, "addStorageLocked: No storage was mounted!");
            return;
        }
        /// M: @}

        MtpStorage storage = new MtpStorage(volume, getApplicationContext());
        String path = storage.getPath();
        mStorageMap.put(path, storage);

        Log.d(TAG, "addStorageLocked " + storage.getStorageId() + " " + path);

        /// M:  ICUSB , we do not share the ICUSB storage 1 to PC @{
        String ICUSB_STORAGE_1_MNT_POINT = "/mnt/udisk/folder1" ;
        if (volume.getPath().equals(ICUSB_STORAGE_1_MNT_POINT)) {
            MtkLog.d(TAG, "addStorageLocked: meet icusb storage " + storage.getPath() + " , and make it unshared");
            return;
        }

        /// M: ALPS01251445: timing issue with MountService, check and correct mtpStorageinfo
        if (StorageManagerEx.getSdSwapState()) {
            checkMtpStorageInfoAndCorrectIt(storage);
        }

        if (mDatabase != null) {
            /// M: ALPS00241636, add log for support MTP debugging
            MtkLog.d(TAG, "addStorageLocked: add storage " + storage.getPath() + " into MtpDatabase");
            mDatabase.addStorage(storage);
        }
        if (mServer != null) {
            /// M: ALPS00241636, add log for support MTP debugging
            MtkLog.d(TAG, "addStorageLocked: add storage " + storage.getPath() + " into MtpServer");
            mServer.addStorage(storage);
        }
    }

    /// M: Added for Storage Update @{
    private void updateStorageLocked(StorageVolume volume) {
        MtpStorage storage = new MtpStorage(volume, getApplicationContext());
        MtkLog.w(TAG, "updateStorageLocked " + storage.getStorageId() + " = " + storage.getStorageId());

        if (mServer != null) {
            MtkLog.d(TAG, "updateStorageLocked: updateStorageLocked storage " + storage.getPath() + " into MtpServer");
            mServer.updateStorage(storage);
        }
    }
    /// M: @}

    private void removeStorageLocked(StorageVolume volume) {
        MtpStorage storage = mStorageMap.remove(volume.getPath());
        if (storage == null) {
            Log.e(TAG, "no MtpStorage for " + volume.getPath());
            return;
        }

        Log.d(TAG, "removeStorageLocked " + storage.getStorageId() + " " + storage.getPath());
        if (mDatabase != null) {
            mDatabase.removeStorage(storage);
        }
        if (mServer != null) {
            mServer.removeStorage(storage);
        }
    }
}
