/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2014. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */
package com.android.camera.v2;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.camera.CameraActivity;
import com.android.camera.ICameraActivityBridge;
import com.android.camera.R;
import com.android.camera.v2.app.AppController;
import com.android.camera.v2.app.CameraAppUI;
import com.android.camera.v2.app.GestureManager;
import com.android.camera.v2.app.GestureManagerImpl;
import com.android.camera.v2.app.ModuleManager;
import com.android.camera.v2.app.ModuleManagerImpl;
import com.android.camera.v2.app.OrientationManager;
import com.android.camera.v2.app.OrientationManagerImpl;
import com.android.camera.v2.app.PreviewManager;
import com.android.camera.v2.app.PreviewManagerImpl;
import com.android.camera.v2.app.SettingAgent;
import com.android.camera.v2.app.AppController.OkCancelClickListener;
import com.android.camera.v2.app.SettingAgent.SettingChangedListener;
import com.android.camera.v2.app.location.LocationManager;
import com.android.camera.v2.bridge.AppContextAdapter;
import com.android.camera.v2.bridge.AppControllerAdapter;
import com.android.camera.v2.bridge.ModeChangeAdapter;
import com.android.camera.v2.bridge.ModuleControllerAdapter;
import com.android.camera.v2.bridge.SettingAdapter;
import com.android.camera.v2.module.ModuleController;
import com.android.camera.v2.module.ModulesInfo;
import com.android.camera.v2.ui.PreviewStatusListener;
import com.android.camera.v2.ui.PreviewStatusListener.OnPreviewAreaChangedListener;
import com.android.camera.v2.uimanager.preference.PreferenceManager;
import com.android.camera.v2.util.CameraUtil;
import com.android.camera.v2.util.SettingKeys;
import com.android.camera.v2.util.Storage;

public class CameraActivityBridge implements ICameraActivityBridge, 
            AppController,OrientationManager.OnOrientationChangeListener {
    private static final String                TAG = CameraActivityBridge.class.getSimpleName();
    private final CameraActivity               mCameraActivity;
    private static final int                   MSG_NOTIFY_PREFERENCES_READY = 0;
    private static final int                   MSG_CLEAR_SCREEN_ON_FLAG = 1;
    private static final long                  SCREEN_DELAY_MS = 2 * 60 * 1000; // 2 mins.
    private final Context                      mAppContext;
    private final AppControllerAdapter         mAppControllerAdapter;
    private final AppContextAdapter            mAppContextAdapter;
    
    private boolean                            mPaused;
    private Handler                            mMainHandler;
    // module related
    private ModuleManager                      mModuleManager;
    private int                                mCurrentModuleIndex;
    private String                             mCurrentModeKey;
    private CameraModule                       mCurrentModule;
    private ModeChangeAdapter                  mModeChangeAdapter;
    // UI/Preview related
    private CameraAppUI                        mCameraAppUI;
    private PreferenceManager                  mPreferenceManager;
    private PreviewManagerImpl                 mPreviewManager;
    private SettingAgent                       mSettingAgent;
    // orientation
    private OrientationManager                 mOrientationManager;
    private int                                mLastRawOrientation;
    
    // gesture
    private GestureManagerImpl                 mGestureManagerImpl;
    
    /**
     * Whether the screen is kept turned on.
     */
    private boolean                             mKeepScreenOn;
    private int                                 mCurrentCameraId = 0;
    
    private LocationManager mLocationManager;
    public CameraActivityBridge(CameraActivity activity) {
        mCameraActivity = activity;
        mAppContext = mCameraActivity.getApplication().getBaseContext();
        // App controller adapter
        mAppControllerAdapter = new AppControllerAdapter(this);
        mAppContextAdapter    = new AppContextAdapter(mAppControllerAdapter);
    }

    /************************************** Camera Activity ************************************/

    @Override
    public void onCreate(Bundle icicle) {
        Log.i(TAG, "[onCreate]...");

        mAppContextAdapter.onCreate();
        
        // activity setup
        mMainHandler = new MainHandler(mCameraActivity, mCameraActivity.getMainLooper());
        mCameraActivity.setContentView(R.layout.camera_activity);
        //TODO consider refactor, remove this
        // initialize storage
        Storage.initializeStorageState();
        mCameraActivity.createCameraScreenNail(true/*getPictures*/);
        
        mSettingAgent = new SettingAdapter(mAppControllerAdapter);
        // clear modes' value in sharedPreferences.
        mSettingAgent.clearSharedPreferencesValue(SettingKeys.MODE_KEYS, 
                String.valueOf(mCurrentCameraId));
        mPreferenceManager = new PreferenceManager(getActivity(), mSettingAgent);
        
        // common ui
        mCameraAppUI = new CameraAppUI(this);
        mCameraAppUI.setOnLayoutChangedListener(mCameraActivity);
        mCameraAppUI.setSettingAgent(mSettingAgent);
        View viewRoot = mCameraActivity.findViewById(R.id.camera_view_container);
        viewRoot.bringToFront(); // bring to the top of surface view
        mCameraAppUI.init(viewRoot, false/*secure camera*/, isCaptureIntent());
        mCameraAppUI.prepareModuleUI();

        mOrientationManager = new OrientationManagerImpl(mCameraActivity);
        mOrientationManager.addOnOrientationChangeListener(mMainHandler, this);

        // LocationManager
        mLocationManager = new LocationManager(mAppContext);
        mSettingAgent
                .registerSettingChangedListener(mLocationSettingChangedListener);

        // module
        mModuleManager = new ModuleManagerImpl();
        ModulesInfo.setupModules(mAppContext, mModuleManager);
        mCurrentModeKey = SettingKeys.KEY_NORMAL;
        setModuleFromModeIndex(ModuleControllerAdapter.CAMERA_MODULE_INDEX);
        mCurrentModule.init(mCameraActivity, false/*secure camera*/ , isCaptureIntent());
    }

    @Override
    public void onRestart() {
        Log.i(TAG, "[onRestart]...");
        Storage.updateDefaultDirectory();
    }

    @Override
    public void onResume() {
        Log.i(TAG, "[onResume]...");
        mPaused = false;
        initializePreferences(mCurrentCameraId);
        mAppContextAdapter.onResume();
        mCameraAppUI.onResume();
        installIntentFilter();
        updateStorageSpaceAndHint();
        
        keepScreenOnForAWhile();
        
        mOrientationManager.resume();
        if (mGestureManagerImpl != null) {
            mGestureManagerImpl.resume();
        }
        mCurrentModule.resume();
    }

    @Override
    public void onPause() {
        Log.i(TAG, "[onPause]...");
        mPaused = true;
        
        mAppContextAdapter.onPause();
        mCameraAppUI.onPause();
        uninstallIntentFilter();
        
        mOrientationManager.pause();
        if (mGestureManagerImpl != null) {
            mGestureManagerImpl.pause();
        }
        
        resetScreenOn();
        
        mCurrentModule.pause();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "[onDestroy]...");
        
        mAppContextAdapter.onDestroy();
        mCameraAppUI.onDestroy();
        if (mOrientationManager != null) {
            mOrientationManager.removeOnOrientationChangeListener(mMainHandler, this);
            mOrientationManager = null;
        }
        
        mCurrentModule.destroy();
        mPreferenceManager.clearSharedPreferencesValue();
        closeGpsLocation();
    }

    /**
     * return true will pass onBackPressed to super class, app will exit.
     */
    @Override
    public boolean onBackPressed() {
        Log.i(TAG, "[onBackPressed]...");
        if (mCameraAppUI.onBackPressed() || mCurrentModule.onBackPressed()) {
            // not call super.onBackPressed
            return false;
        }
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(TAG, "[onConfigurationChanged]... newConfig = " + newConfig);
        
        mCameraAppUI.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        
    }
    
    @Override
    public void onAfterFullScreeChanged(boolean full) {
        if (mGestureManagerImpl != null) {
            mGestureManagerImpl.onFullScreenChanged(full);
        }
        mCameraAppUI.onAfterFullScreenChanged(full);
    }
    
    @Override
    public String getCameraFolderPath() {
        return Storage.getCameraScreenNailPath();
    }
    
    @Override
    public boolean onUserInteraction() {
        if (!mCameraActivity.isFinishing()) {
            keepScreenOnForAWhile();
        }
        return false;
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }
    
    /************************************** App Controller  ************************************/
    
    @Override
    public Activity getActivity() {
        return mCameraActivity;
    }

    @Override
    public Context getAndroidContext() {
        return mAppContext;
    }

    @Override
    public String getModuleScope() {
        return null;
    }

    @Override
    public String getCameraScope() {
        return null;
    }

    @Override
    public void launchActivityByIntent(Intent intent) {
        
    }

    @Override
    public void openContextMenu(View view) {
        
    }

    @Override
    public void registerForContextMenu(View view) {
        
    }

    @Override
    public boolean isPaused() {
        return mCameraActivity.isActivityOnpause();
    }

    @Override
    public ModuleController getCurrentModuleController() {
        return null;
    }

    @Override
    public int getCurrentModuleIndex() {
        return mCurrentModuleIndex;
    }

    @Override
    public String getCurrentModeIndex() {
        return mCurrentModeKey;
    }

    @Override
    public int getQuickSwitchToModuleId(int currentModuleIndex) {
        return 0;
    }

    @Override
    public int getPreferredChildModeIndex(int modeIndex) {
        return 0;
    }

    @Override
    public void onModeChanged(Map<String, String> changedModes) {
        Log.i(TAG, "onModeChanged changedModes = " + changedModes);

        // compute new mode
        Set<String> set = changedModes.keySet();
        Iterator<String> iterator = set.iterator();
        List<String> modeKeys = new ArrayList<String>();
        while (iterator.hasNext()) {
            String modeKey = iterator.next();
            modeKeys.add(modeKey);
        }
        int size = modeKeys.size();
        String key = modeKeys.get(size - 1);
        String value = changedModes.get(key);
        // if value of the changedModes's last element is off, it means change to normal mode.  
        // if value of the changedModes's last element is on, it means change to current mode. 
        mCurrentModeKey = key;
        if ("off".equals(value)) {
            mCurrentModeKey = SettingKeys.KEY_NORMAL;
        }
        
        // check whether module change or mode change
        String pipValue = changedModes.get(SettingKeys.KEY_PHOTO_PIP);
        if (pipValue != null) {
            // change module
            int moduleIndex = ModuleControllerAdapter.CAMERA_MODULE_INDEX;
            if ("on".equals(pipValue)) {
                mCurrentModeKey = SettingKeys.KEY_PHOTO_PIP;
                moduleIndex = ModuleControllerAdapter.DUAL_CAMERA_MODULE_INDEX;
            }
            if (moduleIndex != mCurrentModuleIndex) {
                closeModule(mCurrentModule);
                setModuleFromModeIndex(moduleIndex);
//                mCameraAppUI.resetBottomControls(mCurrentModule, modeIndex);
//                mCameraAppUI.addShutterListener(mCurrentModule);
                openModule(mCurrentModule);
            }
        } else if (mModeChangeAdapter != null) {
            // change mode
            mModeChangeAdapter.onModeChanged(mCurrentModeKey);
        }
    }

    @Override
    public void setModeChangeListener(ModeChangeAdapter modeAdapter) {
        mModeChangeAdapter = modeAdapter;
    }

    @Override
    public void onSettingsSelected() {
        
    }

    @Override
    public void onPreviewVisibilityChanged(int visibility) {
        mCurrentModule.onPreviewVisibilityChanged(visibility);
    }
    
    @Override
    public PreviewManager getPreviewManager() {
        if (mPreviewManager == null) {
            mPreviewManager = new PreviewManagerImpl(getActivity());
        }
        return mPreviewManager;
    }

    @Override
    public void freezeScreenUntilPreviewReady() {
        
    }

    @Override
    public SurfaceTexture getPreviewBuffer() {
        return null;
    }

    @Override
    public void onPreviewReadyToStart() {
        
    }

    @Override
    public void onPreviewStarted() {
        mCameraAppUI.onPreviewStarted();
    }

    @Override
    public void setupOneShotPreviewListener() {
        
    }

    @Override
    public void updatePreviewAspectRatio(float aspectRatio) {
        
    }

    @Override
    public void updatePreviewTransformFullscreen(Matrix matrix,
            float aspectRatio) {
        
    }

    @Override
    public RectF getFullscreenRect() {
        return null;
    }

    @Override
    public void updatePreviewTransform(Matrix matrix) {
        
    }

    @Override
    public void setPreviewStatusListener(
            PreviewStatusListener previewStatusListener) {
        mCameraAppUI.setPreviewStatusListener(previewStatusListener);
    }

    @Override
    public void updatePreviewAreaChangedListener(
            OnPreviewAreaChangedListener listener, boolean isAddListener) {
        if (isAddListener) {
            mCameraAppUI.addPreviewAreaSizeChangedListener(listener);
        } else {
            mCameraAppUI.removePreviewAreaSizeChangedListener(listener);
        }
    }

    @Override
    public void updatePreviewSize(int previewWidth, int previewHeight) {
        mCameraAppUI.updatePreviewSize(previewWidth, previewHeight);
    }

    @Override
    public FrameLayout getModuleLayoutRoot() {
        return mCameraAppUI.getModuleRootView();
    }

    @Override
    public void lockOrientation() {
        if (mOrientationManager != null) {
            mOrientationManager.lockOrientation();
        }
    }

    @Override
    public void unlockOrientation() {
        if (mOrientationManager != null) {
            mOrientationManager.unlockOrientation();
        }
    }

    @Override
    public void setShutterButtonEnabled(boolean enabled, boolean videoShutter) {
        mCameraAppUI.setShutterButtonEnabled(enabled, videoShutter);
    }

    @Override
    public void setShutterEventListener(ShutterEventsListener eventListener,
            boolean videoShutter) {
        mCameraAppUI.setShutterEventListener(eventListener, videoShutter);
    }
    
    @Override
    public void setOkCancelClickListener(OkCancelClickListener listener) {
        mCameraAppUI.setOkCancelClickListener(listener);
    }

    @Override
    public boolean isShutterButtonEnabled(boolean videoShutter) {
        return mCameraAppUI.isShutterButtonEnabled(videoShutter);
    }

    @Override
    public void performShutterButtonClick(boolean clickVideoButton) {
        mCameraAppUI.performShutterButtonClick(clickVideoButton);
    }

    @Override
    public void startPreCaptureAnimation(boolean shortFlash) {
        
    }

    @Override
    public void startPreCaptureAnimation() {
        
    }

    @Override
    public void cancelPreCaptureAnimation() {
        
    }

    @Override
    public void startPostCaptureAnimation() {
        
    }

    @Override
    public void startPostCaptureAnimation(Bitmap thumbnail) {
        
    }

    @Override
    public void cancelPostCaptureAnimation() {
        
    }

    @Override
    public void notifyNewMedia(Uri uri) {
        Log.i(TAG, "notifyNewMedia uri = " + uri);
        if (mCameraAppUI != null) {
            mCameraAppUI.notifyMediaSaved(uri);
        }
        updateStorageSpaceAndHint();
        ContentResolver cr = mCameraActivity.getContentResolver();
        String mimeType = cr.getType(uri);
        if (CameraUtil.isMimeTypeVideo(mimeType)) {
            CameraUtil.broadcastNewVideo(mAppContext, uri);
        } else if (CameraUtil.isMimeTypeImage(mimeType)) {
            CameraUtil.broadcastNewPicture(mAppContext, uri);
        } else {
            Log.w(TAG, "Unknown new media with MIME type:" + mimeType + ", uri:" + uri);
        }
    }

    @Override
    public void onCameraPicked(String newCameraId) {
        mCurrentCameraId = Integer.parseInt(newCameraId);
        initializePreferences(mCurrentCameraId);
        mCurrentModule.onCameraPicked(newCameraId);
    }

    @Override
    public void enableKeepScreenOn(boolean enabled) {
        if (mPaused) {
            return;
        }

        mKeepScreenOn = enabled;
        if (mKeepScreenOn) {
            mMainHandler.removeMessages(MSG_CLEAR_SCREEN_ON_FLAG);
            mCameraActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            keepScreenOnForAWhile();
        }
    }

    @Override
    public OrientationManager getOrientationManager() {
        return mOrientationManager;
    }

    @Override
    public GestureManager getGestureManager() {
        if (mGestureManagerImpl == null) {
            mGestureManagerImpl = new GestureManagerImpl(this);
        }
        return mGestureManagerImpl;
    }

    @Override
    public PreferenceManager getPreferenceManager() {
        return mPreferenceManager;
    }

    @Override
    public CameraAppUI getCameraAppUI() {
        return mCameraAppUI;
    }

    @Override
    public void showErrorAndFinish(int messageId) {
        
    }

    @Override
    public AppControllerAdapter getAppControllerAdapter() {
        return mAppControllerAdapter;
    }

    @Override
    public void gotoGallery() {
        mCameraActivity.gotoGallery();
    }
    
    @Override
    public void setResultExAndFinish(int resultCode) {
        mCameraActivity.setResultExAndFinish(resultCode);
    }
    
    @Override
    public void setResultExAndFinish(int resultCode, Intent data) {
        mCameraActivity.setResultExAndFinish(resultCode, data);
    }
    
    public void setPlayButtonClickListener(PlayButtonClickListener listener) {
        mCameraAppUI.setPlayButtonClickListener(listener);
    }
    
    public void setRetakeButtonClickListener(RetakeButtonClickListener listener) {
        mCameraAppUI.setRetakeButtonClickListener(listener);
    }

    
    @Override
    public LocationManager getLocationManager() {
        return mLocationManager;
    }

    /************************************** App Controller  ************************************/

    @Override
    public void onOrientationChanged(int orientation) {
        if (orientation != mLastRawOrientation) {
            Log.i(TAG, "orientation changed (from:to) " + mLastRawOrientation +
                    ":" + orientation);
        }
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationManager.ORIENTATION_UNKNOWN) {
            return;
        }
        mLastRawOrientation = orientation;
        if (mCurrentModule != null) {
            mCurrentModule.onOrientationChanged(orientation);
        }
        
        if (mCameraAppUI != null) {
            mCameraAppUI.onOrientationChanged(orientation);
        }
    }
    
    private void keepScreenOnForAWhile() {
        if (mKeepScreenOn) {
            return;
        }
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_ON_FLAG);
        mCameraActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mMainHandler.sendEmptyMessageDelayed(MSG_CLEAR_SCREEN_ON_FLAG, SCREEN_DELAY_MS);
    }

    private void resetScreenOn() {
        mKeepScreenOn = false;
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_ON_FLAG);
        mCameraActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    private void openModule(CameraModule module) {
        module.init(mCameraActivity, false/* isSecureCamera()*/, isCaptureIntent());
        module.resume();
    }

    private void closeModule(CameraModule module) {
        module.pause();
        module.destroy();
        mCameraAppUI.clearModuleUI();
    }
    
    private void initializePreferences(final int cameraId) {
        Log.i(TAG, "[initializePreferences]...");
        Thread t = new Thread(new Runnable() {
            
            @Override
            public void run() {
                mPreferenceManager.initializePreferences(R.xml.camera_preferences_v2, cameraId);
                mMainHandler.sendEmptyMessage(MSG_NOTIFY_PREFERENCES_READY);
            }
        }, "initialize-preferences-thread");
        
        t.start();
    }
    
    private class MainHandler extends Handler {
        final WeakReference<CameraActivity> mActivity;

        public MainHandler(CameraActivity activity, Looper looper) {
            super(looper);
            mActivity = new WeakReference<CameraActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraActivity activity = mActivity.get();
            if (activity == null) {
                return;
            }
            switch (msg.what) {
            case MSG_NOTIFY_PREFERENCES_READY:
                if (mCameraAppUI != null) {
                    mCameraAppUI.notifyPreferenceReady();
                }
                break;
            case MSG_CLEAR_SCREEN_ON_FLAG:
                if (!mPaused) {
                    mCameraActivity.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                break;
            default:
                break;
            }
        }
    }

    /**
     * Sets the mCurrentModuleIndex, creates a new module instance for the given
     * index an sets it as mCurrentModule.
     */
    private void setModuleFromModeIndex(int modeIndex) {
        ModuleManagerImpl.ModuleAgent agent = mModuleManager.getModuleAgent(modeIndex);
        if (agent == null) {
            return;
        }
//        if (!agent.requestAppForCamera()) {
//            mCameraController.closeCamera(true);
//        }
        mCurrentModuleIndex = agent.getModuleId();
        mCurrentModule = (CameraModule) agent.createModule(this);
    }

    private boolean isCaptureIntent() {
        if (MediaStore.ACTION_VIDEO_CAPTURE.equals(mCameraActivity.getIntent().getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE.equals(mCameraActivity.getIntent().getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(mCameraActivity.getIntent().getAction())) {
            return true;
        } else {
            return false;
        }
    }
    
    private void updateStorageSpaceAndHint() {
        /*
         * We execute disk operations on a background thread in order to
         * free up the UI thread.  Synchronizing on the lock below ensures
         * that when getStorageSpaceBytes is called, the main thread waits
         * until this method has completed.
         *
         * However, .execute() does not ensure this execution block will be
         * run right away (.execute() schedules this AsyncTask for sometime
         * in the future. executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
         * tries to execute the task in parellel with other AsyncTasks, but
         * there's still no guarantee).
         * e.g. don't call this then immediately call getStorageSpaceBytes().
         * Instead, pass in an OnStorageUpdateDoneListener.
         */
        (new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void ... arg) {
                Log.i(TAG, "updateStorageSpaceAndHint doInBackground");
//                synchronized (mStorageSpaceLock) {
//                    mStorageSpaceBytes = Storage.getAvailableSpace();
//                    return mStorageSpaceBytes;
//                }
                return Storage.getAvailableSpace();
            }

            @Override
            protected void onPostExecute(Long bytes) {
//                updateStorageHint(bytes);
//                // This callback returns after I/O to check disk, so we could be
//                // pausing and shutting down. If so, don't bother invoking.
//                if (callback != null && !mPaused) {
//                    callback.onStorageUpdateDone(bytes);
//                } else {
//                    Log.v(TAG, "ignoring storage callback after activity pause");
//                }
            }
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void installIntentFilter() {
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
        intentFilter.addDataScheme("file");
        mCameraActivity.registerReceiver(mReceiver, intentFilter);
    }

    private void uninstallIntentFilter() {
        mCameraActivity.unregisterReceiver(mReceiver);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "mReceiver.onReceive(" + intent + ")");
            String action = intent.getAction();
            if (action != null && action.equals(Intent.ACTION_MEDIA_EJECT)) {
            } else if (action != null && action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
            } else if (action != null && action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
            } else if (action != null && action.equals(Intent.ACTION_MEDIA_CHECKING)) {
            } else if (action != null && action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
            } else if (action != null && action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
            }
        }
    };
    

    private SettingChangedListener mLocationSettingChangedListener = new SettingChangedListener() {

        @Override
        public void onSettingResult(final Map<String, String> values,
                Map<String, String> overrideValues) {
            String isOn = values.get(SettingKeys.KEY_RECORD_LOCATION);
            Log.i(TAG, "[onSettingResult], loaction is : " + isOn);
            // if the GPS location have not change from normal mode to PIP/CFB,
            // the value will be null,so we don't need do only action.
            if (isOn != null) {
                mCameraActivity.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        mLocationManager.recordLocation("on"
                                .equalsIgnoreCase((values
                                        .get(SettingKeys.KEY_RECORD_LOCATION))));
                    }
                });
            }
        }
    };

    private void closeGpsLocation() {
        if (mLocationManager != null) {
            mLocationManager.recordLocation(false);
        }
    }
}
