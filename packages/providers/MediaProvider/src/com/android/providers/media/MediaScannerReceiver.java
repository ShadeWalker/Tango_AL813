/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/* //device/content/providers/media/src/com/android/providers/media/MediaScannerReceiver.java
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
import java.io.IOException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.MediaStore;

public class MediaScannerReceiver extends BroadcastReceiver
{
    private final static String TAG = "MediaScannerReceiver";
    /// M: handle scan before all storage mounted issue(may cause delete these unmounted storage's entries in db
    /// when do prescan), wait all storage mounted only if check time out(TIMEOUT_VALUE).
    private static final int MSG_CHECK_ALL_STORAGE_MOUNTED = 10;
    private static final int CHECK_INTERVAL = 1000;
    private static final int TIMEOUT_VALUE = 5000;
    private static Handler sHandler;
    /// M: When not boot complete should not scan external storage.
    private static boolean sIsBootComplete = false;
    /// M: When device shutdown, we need do nothing(when shutdown request, sys.shutdown.requested
    /// has value, otherwise no this property).
    private static final String ACTION_SHUTDOWN_IPO = "android.intent.action.ACTION_SHUTDOWN_IPO";
    static boolean sIsShutdown = !"def_value".equals(SystemProperties.get(
            "sys.shutdown.requested", "def_value"));
    /*Modified by zhouyoukun for HQ01265012 20150804 begin*/
    private static final String ACTION_MEDIA_SCANNER_SCAN_FOLDER = 
   		 "android.intent.action.MEDIA_SCANNER_SCAN_FOLDER";
    /*Modified by zhouyoukun for HQ01265012 20150804 end*/
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
		if(action == null) return;  //modify by majian for null intent fatal error
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            MtkLog.v(TAG, "onReceive BOOT_COMPLETED, begin to scan internal and external storage.");
            // Scan both internal and external storage
            scan(context, MediaProvider.INTERNAL_VOLUME);
            /// M: only do scan external until all storages have been mounted or check time out.
            scanUntilAllStorageMounted(context);
            sIsBootComplete = true;
            sIsShutdown = false;
        } else if (action.equals(Intent.ACTION_SHUTDOWN) || action.equals(ACTION_SHUTDOWN_IPO)) {
            /// M: When device shutdown, MediaProvider need not respond any action.
            MtkLog.v(TAG, "onReceive " + action + ", not respond any action from now!");
            sIsShutdown = true;
        } else if (!sIsShutdown) {
            final Uri uri = intent.getData();
            if (uri != null && uri.getScheme().equals("file")) {
                // handle intents related to external storage
                String path = uri.getPath();
                String externalStoragePath = Environment.getExternalStorageDirectory().getPath();
                String legacyPath = Environment.getLegacyExternalStorageDirectory().getPath();

                try {
                    path = new File(path).getCanonicalPath();
                } catch (IOException e) {
                    MtkLog.e(TAG, "couldn't canonicalize " + path);
                    return;
                }
                if (path.startsWith(legacyPath)) {
                    path = externalStoragePath + path.substring(legacyPath.length());
                }

                MtkLog.d(TAG, "onReceive: action = " + action + ", path = " + path);
                if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                    /// M: in tablet multiple users case,  User proccess don't need to scan external 
                    /// sdcard except primary external sdcard @{
                    if(("1").equals(SystemProperties.get("ro.mtk_owner_sdcard_support"))
                       && UserHandle.myUserId() != UserHandle.USER_OWNER) {
                       StorageManager storageManager = (StorageManager)context.getSystemService(Context.STORAGE_SERVICE);
                       String[] directories = storageManager.getVolumePaths();
                       if(directories != null && path != null && !path.equals(directories[0])) {
                          MtkLog.d(TAG, "the current proccess is not owner and path is not primary external sdcard!");
                          return;
                       }
                    }
                    /// @}
                    
                    /// M: Do not scan external storage before internal storage was scanned. Use two variables
                    /// to make sure we scan external storage after boot complete. When media process be kill
                    /// sIsBootComplete will be restore to default value and need use mount service send
                    /// parameter first_boot_mounted in intent to check whether boot complete
                    boolean isMountedBeforeBoot = intent.getBooleanExtra("first_boot_mounted", false);
                    if (isMountedBeforeBoot && !sIsBootComplete) {
                        MtkLog.v(TAG, "Mounted before boot completed with path: " + path);
                        return;
                    }
                    /// M: only do scan until all storage have mounted or check time out.
                    scanUntilAllStorageMounted(context);
                   					
                }
				//modify by HQ_pangxuhi  20150925 for HQ01331800
				/*
				 else if ((Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action) && isInScanDirectory(context, path)) || ACTION_MEDIA_SCANNER_SCAN_FOLDER.equals(action)) {
                            /// M: only scan these files store in devices avail external storage
	                	  scan(context, MediaProvider.EXTERNAL_VOLUME);
	                      scanFile(context, path);                    
                           }*/                 
			     else if ((Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action) && isInScanDirectory(context, path)) ) {
                     /// M: only scan these files store in devices avail external storage
                      scanFile(context, path);
					  MtkLog.d(TAG, "onReceive: scanFile action = " + action + ", path = " + path);
                 } else if(ACTION_MEDIA_SCANNER_SCAN_FOLDER.equals(action)) {
                      scan(context, MediaProvider.EXTERNAL_VOLUME);
                      //scanFile(context, path);
					   MtkLog.d(TAG, "onReceive: scan action = " + action + ", path = " + path);
                 }
                 //modify by HQ_pangxuhi  End
					else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
                    /// M: Call MediaProvider with method ACTION_MEDIA_UNMOUNTED to trigger MediaProvider handle
                    /// unmount intent(detach volume or delete entries)
                    StorageVolume storage = (StorageVolume) intent.getParcelableExtra(StorageVolume.EXTRA_STORAGE_VOLUME);
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(StorageVolume.EXTRA_STORAGE_VOLUME, storage);
                    bundle.putBoolean("mount_unmount_all", intent.getBooleanExtra("mount_unmount_all", false));
                    context.getContentResolver().call(MediaStore.Files.getContentUri(MediaProvider.EXTERNAL_VOLUME),
                            MediaUtils.ACTION_MEDIA_UNMOUNTED, null, bundle);
                    /// M: When unmount storage need update folder cache in MediaScannerThreadpool, so that next scan
                    /// could use latest list get from file system.
                    MediaScannerThreadPool.updateFolderMap();
                }
            }
        }
    }

    private void scan(Context context, String volume) {
        Bundle args = new Bundle();
        args.putString("volume", volume);
        context.startService(
                new Intent(context, MediaScannerService.class).putExtras(args));
    }

    private void scanFile(Context context, String path) {
        Bundle args = new Bundle();
        args.putString("filepath", path);
        context.startService(
                new Intent(context, MediaScannerService.class).putExtras(args));
    }

    /// M: handle scan before all storage mounted issue(may cause delete these unmounted storage's entries in db
    /// when do prescan), wait all storage mounted only if check time out(TIMEOUT_VALUE). {@
    /**
     * M: scan external storage until all storages have been mounted or check time out.
     *
     * @param context
     */
    private void scanUntilAllStorageMounted(Context context) {
        Handler handler = getHandler();
        handler.removeCallbacksAndMessages(null);
        Message msg = handler.obtainMessage(MSG_CHECK_ALL_STORAGE_MOUNTED, 0, 0, context);
        msg.sendToTarget();
    }

    /**
     * M: Check the given path whether in all exist directories, only yes we need do scan.
     *
     * @param context context
     * @param path path given to check
     * @return true if is in any directories, otherwise false.
     */
    private boolean isInScanDirectory(Context context, String path) {
        if (path == null) {
            MtkLog.w(TAG, "scan path is null");
            return false;
        }
        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        String[] directories = storageManager.getVolumePaths();
        if (directories == null || directories.length == 0) {
            MtkLog.w(TAG, "there are no valid directores");
            return false;
        }
        for (String directory : directories) {
            if (path.startsWith(directory)) {
                return true;
            }
        }
        MtkLog.w(TAG, "invalid scan path " + path + ", not in any directories");
        return false;
    }

    /**
     * M: check whether all storages are mounted, if yes we can do scan external storages.
     *
     * @param context
     * @return
     */
    private boolean isAllStorageMounted(Context context) {
        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        StorageVolume[] ExternalVolumeList = storageManager.getVolumeList();
        for (StorageVolume storageVolume : ExternalVolumeList) {
            String path = storageVolume.getPath();
            String state = storageVolume.getState();
            MtkLog.v(TAG, "isAllStorageMounted: path = " + path + ", state = " + state);
            /// Don't need check usbotg storage
            if (Environment.DIRECTORY_USBOTG.equals(path)) {
                continue;
            }
            if (Environment.MEDIA_UNMOUNTED.equals(state) || Environment.MEDIA_CHECKING.equals(state)) {
                return false;
            }
        }
        return true;
    }

    /// M: get main handler to do check whether all storage have been mounted every CHECK_INTERVAL time.
    private Handler getHandler() {
        if (sHandler == null) {
            sHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    Context context = (Context) msg.obj;
                    int waitTime = msg.arg1;
                    MtkLog.v(TAG, "Check whether all storage mounted, have waited " + waitTime + "ms");
                    if (MSG_CHECK_ALL_STORAGE_MOUNTED == msg.what) {
                        /// When all storage mounted or check time out, begin to scan
                        if (waitTime > TIMEOUT_VALUE || isAllStorageMounted(context)) {
                            MtkLog.v(TAG, "All storages have mounted or check time out, begin to scan.");
                            scan(context, MediaProvider.EXTERNAL_VOLUME);
                            removeCallbacksAndMessages(null);
                            sHandler = null;
                        } else {
                            MtkLog.v(TAG, "Some storage has not been mounted, wait it mounted until time out.");
                            Message next = obtainMessage(msg.what, waitTime + CHECK_INTERVAL, -1, msg.obj);
                            sendMessageDelayed(next, CHECK_INTERVAL);
                        }
                    }
                };
            };
        }
        return sHandler;
    }
    /// @}
}
