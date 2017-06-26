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

package com.android.gallery3d.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemProperties;
import android.support.v4.print.PrintHelper;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.ui.Log;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.util.PanoramaViewHelper;
import com.android.gallery3d.util.ThreadPool;
import com.android.photos.data.GalleryBitmapPool;

import com.mediatek.gallery3d.adapter.FeatureHelper;
import com.mediatek.gallery3d.adapter.PhotoPlayFacade;
import com.mediatek.galleryfeature.hotknot.HotKnot;
import com.mediatek.galleryfeature.pq.ImageDC;
import com.mediatek.galleryframework.base.MediaFilter;
import com.mediatek.galleryframework.base.MediaFilterSetting;

import java.io.FileNotFoundException;

import com.mediatek.galleryfeature.drm.DeviceMonitor;
import com.mediatek.galleryfeature.drm.DeviceMonitor.ConnectStatus;
import com.mediatek.galleryfeature.drm.DeviceMonitor.DeviceConnectListener;

public class AbstractGalleryActivity extends Activity implements GalleryContext, DeviceMonitor.DeviceConnectListener {
    private static final String TAG = "Gallery2/AbstractGalleryActivity";
    private GLRootView mGLRootView;
    private StateManager mStateManager;
    private GalleryActionBar mActionBar;
    private OrientationManager mOrientationManager;
    private TransitionStore mTransitionStore = new TransitionStore();
    private boolean mDisableToggleStatusBar;
    private PanoramaViewHelper mPanoramaViewHelper;

    private AlertDialog mAlertDialog = null;
    /// M: [BUG.ADD] sign gallery status. @{
    private volatile boolean hasPausedActivity;
    /// @}
    private BroadcastReceiver mMountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /// M: [BUG.MODIFY] we don't care about SD card content;
            // As long as the card is mounted, dismiss the dialog @{
            /* if (getExternalCacheDir() != null) onStorageReady();
            */
            onStorageReady();
        }
            /// @}
    };
    /// M: [BUG.MODIFY] @{
    /*private IntentFilter mMountFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);*/
    private IntentFilter mMountFilter = null;
    /// @}

    /// M: [FEATURE.ADD] HotKnot @{
    private HotKnot mHotKnot;
    /// @}

    /// M: [BUG.ADD] another device plug@{
    private DeviceMonitor mDeviceMonitor;
    private boolean mIsSmartBookConnected = false;
    /// @}
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mOrientationManager = new OrientationManager(this);
        toggleStatusBarByOrientation();
        getWindow().setBackgroundDrawable(null);
        mPanoramaViewHelper = new PanoramaViewHelper(this);
        mPanoramaViewHelper.onCreate();
        doBindBatchService();
        /// M: [FEATURE.ADD] @{
        initializeMediaFilter();
        /// @}
        /// M: [FEATURE.ADD] HotKnot @{
        mHotKnot = new HotKnot(this);
        /// @}
        /// M: [BUG.ADD] leave selection mode when plug out sdcard. @{
        registerStorageReceiver();
        /// @}
        
        /// M: [FEATURE.ADD] Image DC @{
        ImageDC.resetImageDC(((Context)this));
        /// @}
        /// M: [BUG.ADD] @{
        mDeviceMonitor = new DeviceMonitor(this);
        mDeviceMonitor.setConnectListener(this);
        /// @}
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mGLRootView.lockRenderThread();
        try {
            super.onSaveInstanceState(outState);
            getStateManager().saveState(outState);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mStateManager.onConfigurationChange(config);
        getGalleryActionBar().onConfigurationChanged();
        invalidateOptionsMenu();
        toggleStatusBarByOrientation();
        /// M: [BUG.ADD] @{
        //the picture show abnormal after rotate device to landscape mode,
        //lock device, rotate device to portrait mode, 
        //unlock device, to resolve this problem, let it show dark color
        if (hasPausedActivity) {
            mGLRootView.setVisibility(View.GONE);
        }
        /// @}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return getStateManager().createOptionsMenu(menu);
    }

    @Override
    public Context getAndroidContext() {
        return this;
    }

    @Override
    public DataManager getDataManager() {
        return ((GalleryApp) getApplication()).getDataManager();
    }

    @Override
    public ThreadPool getThreadPool() {
        return ((GalleryApp) getApplication()).getThreadPool();
    }

    public synchronized StateManager getStateManager() {
        if (mStateManager == null) {
            mStateManager = new StateManager(this);
        }
        return mStateManager;
    }

    public GLRoot getGLRoot() {
        return mGLRootView;
    }

    public OrientationManager getOrientationManager() {
        return mOrientationManager;
    }

    @Override
    public void setContentView(int resId) {
        super.setContentView(resId);
        mGLRootView = (GLRootView) findViewById(R.id.gl_root_view);
        /// M: [FEATURE.ADD] @{
        PhotoPlayFacade.registerMedias(this.getAndroidContext(), 
                mGLRootView.getGLIdleExecuter());
        /// @}
    }

    protected void onStorageReady() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
            unregisterReceiver(mMountReceiver);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        /// M: [BUG.ADD] if we're viewing a non-local file/uri, do NOT check storage@{
        // or pop up "No storage" dialog
        Log.d(TAG, "<onStart> mShouldCheckStorageState = " + mShouldCheckStorageState);
        if (!mShouldCheckStorageState) {
            return;
        }
        /// @}
        /// M: [BUG.MODIFY] @{
        /*if (getExternalCacheDir() == null) {*/
        if (FeatureHelper.getExternalCacheDir(this) == null
                && (!FeatureHelper.isDefaultStorageMounted(this))) {
        /// @}
            OnCancelListener onCancel = new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            };
            OnClickListener onClick = new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.no_external_storage_title)
                    .setMessage(R.string.no_external_storage)
                    .setNegativeButton(android.R.string.cancel, onClick)
                    .setOnCancelListener(onCancel);
            if (ApiHelper.HAS_SET_ICON_ATTRIBUTE) {
                setAlertDialogIconAttribute(builder);
            } else {
                builder.setIcon(android.R.drawable.ic_dialog_alert);
            }
            mAlertDialog = builder.show();
            /// M: [BUG.ADD] @{
            if (mMountFilter == null) {
                mMountFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
                mMountFilter.addDataScheme("file");
            }
            /// @}
            registerReceiver(mMountReceiver, mMountFilter);
        }
        mPanoramaViewHelper.onStart();
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private static void setAlertDialogIconAttribute(
            AlertDialog.Builder builder) {
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAlertDialog != null) {
            unregisterReceiver(mMountReceiver);
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        mPanoramaViewHelper.onStop();
        /// M: [BUG.ADD] @{
        mGLRootView.setVisibility(View.GONE);
        /// @}
    }

    @Override
    protected void onResume() {
        /// M: [DEBUG.ADD] @{
        if (SystemProperties.getInt("gallery.debug.renderlock", 0) == 1) {
            mDebugRenderLock = true;
            mGLRootView.startDebug();
        }
        /// @}
        super.onResume();
        /// M: [FEATURE.ADD] @{
        restoreFilter();
        PhotoPlayFacade.registerMedias(this.getAndroidContext(), 
                mGLRootView.getGLIdleExecuter());
        /// @}
        mGLRootView.lockRenderThread();
        /// M: [BUG.ADD] @{
        // when default storage has been changed, we should refresh bucked id, 
        // or else the icon showing on the album set slot can not update
        MediaSetUtils.refreshBucketId();
        /// @}
        try {
            getStateManager().resume();
            getDataManager().resume();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        mGLRootView.onResume();
        mOrientationManager.resume();
        /// M: [BUG.ADD] save activity status. @{
        hasPausedActivity = false;
        /// @}
        /// M: [BUG.ADD] fix abnormal screen @{
        mGLRootView.setVisibility(View.VISIBLE);
        /// @}
        /// M: [BUG.ADD] @{
        mDeviceMonitor.start();
        /// @}
    }

    @Override
    protected void onPause() {
        super.onPause();
        mOrientationManager.pause();
        mGLRootView.onPause();
        mGLRootView.lockRenderThread();
        try {
            getStateManager().pause();
            getDataManager().pause();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        GalleryBitmapPool.getInstance().clear();
        MediaItem.getBytesBufferPool().clear();
        /// M: [BUG.ADD] save activity status. @{
        // the picture show abnormal after rotate device
        // to landscape mode,lock device, rotate device to portrait
        // mode, unlock device. to resolve this problem, let it show dark color
        hasPausedActivity = true;
        /// @}
        /// M: [DEBUG.ADD] @{
        if (mDebugRenderLock) {
            mGLRootView.stopDebug();
            mDebugRenderLock = false;
        }
        /// @}
        /// M: [BUG.ADD] @{
        mDeviceMonitor.stop();
        /// @}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGLRootView.lockRenderThread();
        try {
            getStateManager().destroy();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        doUnbindBatchService();
        /// M: [FEATURE.ADD] @{
        removeFilter();
        /// @}
        /// M: [BUG.ADD] leave selection mode when plug out sdcard @{
        unregisterReceiver(mStorageReceiver);
        /// @}
        /// M: [BUG.ADD] @{
        mDeviceMonitor = null;
        /// @}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mGLRootView.lockRenderThread();
        try {
            getStateManager().notifyActivityResult(
                    requestCode, resultCode, data);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public void onBackPressed() {
        // send the back event to the top sub-state
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            getStateManager().onBackPressed();
        } finally {
            root.unlockRenderThread();
        }
    }

    public GalleryActionBar getGalleryActionBar() {
        if (mActionBar == null) {
            mActionBar = new GalleryActionBar(this);
        }
        return mActionBar;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            return getStateManager().itemSelected(item);
        } finally {
            root.unlockRenderThread();
        }
    }

    protected void disableToggleStatusBar() {
        mDisableToggleStatusBar = true;
    }

    // Shows status bar in portrait view, hide in landscape view
    private void toggleStatusBarByOrientation() {
        if (mDisableToggleStatusBar) return;

        Window win = getWindow();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public TransitionStore getTransitionStore() {
        return mTransitionStore;
    }

    public PanoramaViewHelper getPanoramaViewHelper() {
        return mPanoramaViewHelper;
    }

    protected boolean isFullscreen() {
        return (getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
    }

    private BatchService mBatchService;
    private boolean mBatchServiceIsBound = false;
    private ServiceConnection mBatchServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBatchService = ((BatchService.LocalBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mBatchService = null;
        }
    };

    private void doBindBatchService() {
        bindService(new Intent(this, BatchService.class), mBatchServiceConnection, Context.BIND_AUTO_CREATE);
        mBatchServiceIsBound = true;
    }

    private void doUnbindBatchService() {
        if (mBatchServiceIsBound) {
            // Detach our existing connection.
            unbindService(mBatchServiceConnection);
            mBatchServiceIsBound = false;
        }
    }

    public ThreadPool getBatchServiceThreadPoolIfAvailable() {
        if (mBatchServiceIsBound && mBatchService != null) {
            return mBatchService.getThreadPool();
        } else {
            throw new RuntimeException("Batch service unavailable");
        }
    }

    public void printSelectedImage(Uri uri) {
        if (uri == null) {
            return;
        }
        String path = ImageLoader.getLocalPathFromUri(this, uri);
        if (path != null) {
            Uri localUri = Uri.parse(path);
            path = localUri.getLastPathSegment();
        } else {
            path = uri.getLastPathSegment();
        }
        PrintHelper printer = new PrintHelper(this);
        try {
            printer.printBitmap(path, uri);
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, "Error printing an image", fnfe);
        }
    }


    //********************************************************************
    //*                              MTK                                 *
    //********************************************************************

    public boolean mShouldCheckStorageState = true;
    private boolean mDebugRenderLock = false;

    private MediaFilter mMediaFilter;
    private String mDefaultPath;

    private void initializeMediaFilter() {
        mMediaFilter = new MediaFilter();
        mMediaFilter.setFlagFromIntent(getIntent());
        boolean isFilterSame = MediaFilterSetting.setCurrentFilter(this, mMediaFilter);
        if (!isFilterSame) {
            Log.i(TAG, "<initializeMediaFilter> forceRefreshAll~");
            getDataManager().forceRefreshAll();
        }
    }

    private void restoreFilter() {
        boolean isFilterSame = MediaFilterSetting.restoreFilter(this);
        boolean isFilePathSame = !isDefaultPathChange();
        Log.i(TAG, "<restoreFilter> isFilterSame = " + isFilterSame
                + ", isFilePathSame = " + isFilePathSame);
        if (!isFilterSame || !isFilePathSame) {
            Log.i(TAG, "<restoreFilter> forceRefreshAll");
            getDataManager().forceRefreshAll();
        }
    }

    private void removeFilter() {
        MediaFilterSetting.removeFilter(this);
    }

    public boolean hasPausedActivity () {
        return hasPausedActivity;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // TODO Auto-generated method stub
        mGLRootView.dispatchKeyEventView(event);
        return super.dispatchKeyEvent(event);
    }

    /// M: [FEATURE.ADD] HotKnot @{
    public HotKnot getHotKnot() {
        return mHotKnot;
    }
    /// @}

    /// M: [BUG.ADD] leave selection mode when plug out sdcard @{
    private EjectListener mEjectListener;
    public interface EjectListener {
        public void onEjectSdcard();
    }
    public void setEjectListener(EjectListener listener) {
        mEjectListener = listener;
    }

    private BroadcastReceiver mStorageReceiver;
    private void registerStorageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addDataScheme("file");
        mStorageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_MEDIA_EJECT.equals(action)) {
                    if (mEjectListener != null) {
                        mEjectListener.onEjectSdcard();
                    }
                }
            }
        };
        registerReceiver(mStorageReceiver, filter);
    }
    /// @}

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return getStateManager().onPrepareOptionsMenu(menu);
    }

    private boolean isDefaultPathChange() {
        if (!mShouldCheckStorageState)
            return false;
        String newPath = FeatureHelper.getDefaultPath();
        boolean res = (mDefaultPath != null && !mDefaultPath.equals(newPath));
        mDefaultPath = newPath;
        Log.i(TAG, "<isDefaultPathChange> mDefaultPath = " + mDefaultPath);
        return res;
    }

    /// M: [BUG.ADD] for smart book @{
    // activity and service will be restarted when plug in or plug out smart book.
    // once plugged in smart book, we will set mIsSmartBookConnected is true
    @Override
    public void onDeviceConnected(ConnectStatus status) {
        Log.d(TAG, "<onDeviceConnected> status " + status);
        if (status == ConnectStatus.SMARTBOOK_CONNECTD) {
            mIsSmartBookConnected = true;
            FeatureHelper.refreshResource(this.getAndroidContext());
        }
    }

    public boolean getIsSmartBookConnected() {
        return mIsSmartBookConnected;
    }
    /// @}
}
