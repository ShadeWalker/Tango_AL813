package com.android.camera;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.android.camera.actor.CameraActor;
import com.android.camera.actor.PhotoActor;
import com.android.camera.actor.VideoActor;
import com.android.camera.bridge.CameraAppUiImpl;
import com.android.camera.bridge.CameraDeviceCtrl;
import com.android.camera.bridge.CameraDeviceManagerImpl;
import com.android.camera.bridge.FeatureConfigImpl;
import com.android.camera.bridge.FileSaverImpl;
import com.android.camera.bridge.SelfTimerManager;
import com.android.camera.externaldevice.ExternalDeviceManager;
import com.android.camera.externaldevice.IExternalDeviceCtrl;
import com.android.camera.manager.FrameManager;
import com.android.camera.manager.MMProfileManager;
import com.android.camera.manager.ModePicker;
import com.android.camera.manager.PickerManager;
import com.android.camera.manager.SettingManager;
import com.android.camera.manager.ViewManager;
import com.android.camera.ui.FrameView;
import com.android.camera.ui.PreviewFrameLayout;
import com.android.camera.ui.PreviewSurfaceView;
import com.android.camera.ui.RotateLayout;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Face;
import android.hardware.Camera.Parameters;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.mediatek.camera.ICameraMode.CameraModeType;
import com.mediatek.camera.ISettingCtrl;
import com.mediatek.camera.ModuleManager;
import com.mediatek.camera.ext.ExtensionHelper;
import com.mediatek.camera.platform.ICameraAppUi;
import com.mediatek.camera.platform.ICameraAppUi.CommonUiType;
import com.mediatek.camera.platform.ICameraAppUi.ViewState;
import com.mediatek.camera.platform.ICameraDeviceManager;
import com.mediatek.camera.platform.IFeatureConfig;
import com.mediatek.camera.platform.IFileSaver;
import com.mediatek.camera.platform.IModuleCtrl;
import com.mediatek.camera.platform.ISelfTimeManager;
import com.mediatek.camera.setting.ParametersHelper;
import com.mediatek.camera.setting.SettingConstants;
import com.mediatek.camera.setting.SettingUtils;
import com.mediatek.camera.setting.preference.ListPreference;
import com.mediatek.camera.setting.preference.RecordLocationPreference;
import com.mediatek.camera.util.IAppGuide;

/*
 * --ActivityBase
 * ----Camera //will control CameraActor, ModePicker, CameraPicker, SettingChecker, FocusManager, RemainingManager
 * --CameraActor
 * ------VideoActor
 * ------PhotoActor
 * --------NormalActor //contains continuous shot
 * --------HdrActor
 * --------FaceBeautyActor
 * --------AsdActor
 * --------SmileShotActor
 * --------PanaromaActor
 * --------MavActor
 */
public class CameraActivity extends ActivityBase implements
        PreviewFrameLayout.OnSizeChangedListener {
    private static final String TAG = "CameraActivity";
    
    public static final int UNKNOWN = -1;
    public static final int STATE_PREVIEW_STOPPED = 0;
    public static final int STATE_IDLE = 1; // preview is active
    // ocus is in progress. The exact focus state is in Focus.java.
    public static final int STATE_FOCUSING = 2;
    public static final int STATE_SNAPSHOT_IN_PROGRESS = 3;
    public static final int STATE_RECORDING_IN_PROGRESS = STATE_SNAPSHOT_IN_PROGRESS;
    public static final int STATE_SWITCHING_CAMERA = 4;
    
    private static final int MSG_CAMERA_PARAMETERS_READY = 2;
    private static final int MSG_CHECK_DISPLAY_ROTATION = 4;
    private static final int MSG_SWITCH_CAMERA = 5;
    private static final int MSG_CLEAR_SCREEN_DELAY = 7;
    private static final int MSG_APPLY_PARAMETERS_WHEN_IDEL = 12;
    private static final int MSG_SET_PREVIEW_ASPECT_RATIO = 15;
    private static final int MSG_DELAY_SHOW_ONSCREEN_INDICATOR = 16;
    private static final int MSG_UPDATE_SWITCH_ACTOR_STATE = 17;
    
    // add for stereo Camera features type
    private static final int DUAL_CAMERA_START = 1;
    private static final int DUAL_CAMERA_ENHANCE_ENABLE = 3;
    private static final int DUAL_CAMERA_ENHANCE_DISABLE = 4;
    private static final int DUAL_CAMERA_SWITCH_IN_REFOCUS = 2;
    private static final int ON = 0;
    private static final int OFF = 1;
    
    private static final int DELAY_MSG_SCREEN_SWITCH = 2 * 60 * 1000;
    private static final int SHOW_INFO_LENGTH_LONG = 5 * 1000;
    // This is the timeout to keep the camera in onPause for the first time
    // after screen on if the activity is started from secure lock screen.
    private static final int KEEP_CAMERA_TIMEOUT = 1000; // ms
    
    private int mDelayOtherMessageTime;
    private int mCameraState = STATE_PREVIEW_STOPPED;
    //private int mCameraId;
    //private int mTopCamId = -1;

    private int mNumberOfCameras;
    private int mPendingSwitchCameraId = UNKNOWN;
    
    private int mDisplayRotation;
    private int mOrientation = 0;
    private int mOrientationCompensation = 0;
    //TODO 
    private long mOnResumeTime;
    
    private boolean mIsModeChanged;
    private boolean mIsAppGuideFinished;
    private boolean mIsSwitchActorRunning = false;
    private boolean mNeedRestoreIfOpenFailed = false;
    //private boolean mIsSurfaceTextureReady = true;
    protected boolean mIsStereoMode = false;
    
    private ISettingCtrl mISettingCtrl;
    
    private CameraActor mCameraActor;
    private PreviewFrameLayout mPreviewFrameLayout;
    // / M: for listener and resumable convenient functions
    private List<OnFullScreenChangedListener> mFullScreenListeners = new CopyOnWriteArrayList<OnFullScreenChangedListener>();
    
    // private CameraStartUpThread mCameraStartUpThread;
    private MyOrientationEventListener mOrientationListener;
    private ModePicker mModePicker;
    private FileSaver mFileSaver;
    private FrameManager mFrameManager;
    private RotateLayout mFocusAreaIndicator;
    private ComboPreferences mPreferences;
    private LocationManager mLocationManager;
    
    private PowerManager mPowerManager;
    private Vibrator mVibrator;
    private CharSequence mDelayShowInfo;
    private CameraAppUiImpl mCameraAppUi;
    private ISelfTimeManager mISelfTimeManager;
    private ModuleManager mModuleManager;
    private CameraDeviceCtrl mCameraDeviceCtrl;
    private ExternalDeviceManager mOtherDeviceConectedManager;
    
    private int mNextMode = ModePicker.MODE_PHOTO;
    private int mPrevMode = ModePicker.MODE_PHOTO;
    private boolean mIsBackPressed = false;
    
    // PreviewFrameLayout size has changed.implement
    // PreviewFrameLayout.OnSizeChangedListener @{
    @Override
    public void onSizeChanged(int width, int height) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        mCameraDeviceCtrl.onSizeChanged(width, height);
    }
    
    // @}
    
    @Override
    protected ICameraActivityBridge getCameraActivityBridge() {
        if (mCameraActivityBridge == null) {
            mCameraActivityBridge = CameraActivityBridgeFactory.getCameraActivityBridge(this);
        }
        return mCameraActivityBridge;
    }
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onCreate(icicle);
            return;
        }
        
        Log.i(TAG, "[onCreate] begin");
        MMProfileManager.startProfileCameraOnCreate();
        
        mPreferences = new ComboPreferences(this);
        SettingUtils.resetCameraId(mPreferences.getGlobal());
        mCameraDeviceCtrl = new CameraDeviceCtrl(this, mPreferences);
        mCameraDeviceCtrl.openCamera();
        
        ModuleCtrlImpl moduleCtrl = new ModuleCtrlImpl(this);
        mCameraAppUi = new CameraAppUiImpl(this);
        mCameraAppUi.createCommonView();
        initializeCommonManagers();
        mCameraAppUi.initializeCommonView();
        
        // just set content view for preview
        MMProfileManager.startProfileCameraViewOperation();
        setContentView(R.layout.camera);
        ViewGroup appRoot = (ViewGroup) findViewById(R.id.camera_app_root);
        appRoot.bringToFront();
        MMProfileManager.stopProfileCameraViewOperation();
        mCameraDeviceCtrl.attachSurfaceViewLayout();
        
        mCameraDeviceCtrl.setCameraAppUi(mCameraAppUi);
        IFileSaver fileSaver = new FileSaverImpl(mFileSaver);
        IFeatureConfig featureConfig = new FeatureConfigImpl();
        ICameraDeviceManager deviceManager = new CameraDeviceManagerImpl(this, mCameraDeviceCtrl);
        mISelfTimeManager = new SelfTimerManager(this, mCameraAppUi);
        
        parseIntent();
        mModuleManager = new ModuleManager(this, fileSaver, mCameraAppUi, featureConfig,
                deviceManager, moduleCtrl, mISelfTimeManager);
        mISettingCtrl = mModuleManager.getSettingController();
        mCameraAppUi.setSettingCtrl(mISettingCtrl);
        if (isVideoCaptureIntent() || isVideoWallPaperIntent()) {
            mCameraActor = new VideoActor(this, mModuleManager, ModePicker.MODE_VIDEO);
        } else {
            mCameraActor = new PhotoActor(this, mModuleManager, ModePicker.MODE_PHOTO);
        }
        mCameraDeviceCtrl.setModuleManager(mModuleManager);
        mCameraDeviceCtrl.setSettingCtrl(mISettingCtrl);
        mCameraDeviceCtrl.setCameraActor(mCameraActor);
        mCameraDeviceCtrl.resumeStartUpThread();
        mFileSaver.bindSaverService();
        mOtherDeviceConectedManager = new ExternalDeviceManager(this);
        mOtherDeviceConectedManager.onCreate();
        mOtherDeviceConectedManager.addListener(mListener);
        
        // reset smile shot, hdr, asd sharepreference as off to avoid error
        // when kill camera through IPO or other abnormal ways.
        if (isNonePickIntent()) {
            SettingUtils.updateSettingCaptureModePreferences(mPreferences.getLocal());
        }
        // @}
        SettingUtils.upgradeGlobalPreferences(mPreferences.getGlobal(), CameraHolder.instance()
                .getNumberOfCameras());
        initializeStereo3DMode();
        mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        mDisplayRotation = Util.getDisplayRotation(this);
        
        // for photo view loading camera folder
        ExtensionHelper.getCameraFeatureExtension(this);
        Storage.initializeStorageState();
        
        createCameraScreenNail(isNonePickIntent());
        // only initialize some thing for open
        initializeForOpeningProcess();
        // mStartPreviewPrerequisiteReady.open();
        // may be we can lazy this function.
        mCameraDeviceCtrl.setCameraScreenNail(mCameraScreenNail);
        initializeAfterPreview();
        Log.i(TAG, "[onCreate] end, mISelfTimeManager = " + mISelfTimeManager);
        MMProfileManager.stopProfileCameraOnCreate();
        
    }
    
    @Override
    protected void onRestart() {
        super.onRestart();
        
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onRestart();
            return;
        }
        
        if (isNonePickIntent() && isMountPointChanged()) {
            finish();
            mForceFinishing = true;
            startActivity(getIntent());
        } else if (isMountPointChanged()) {
            Storage.updateDefaultDirectory();
        }
        Log.i(TAG, "onRestart() mForceFinishing=" + mForceFinishing);
    }
    
    @Override
    protected void onResume() {
        Log.i(TAG, "onResume() mForceFinishing=" + mForceFinishing);
        super.onResume();
        
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onResume();
            return;
        }
        
        mOtherDeviceConectedManager.onResume();
        if (mForceFinishing || mCameraDeviceCtrl.isOpenCameraFail()) {
            mNeedRestoreIfOpenFailed = false;
            return;
        }
        ViewGroup appRoot = (ViewGroup) findViewById(R.id.camera_app_root);
        int flag = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        appRoot.setSystemUiVisibility(flag);
        
        if (mModuleManager != null) {
            mModuleManager.resume();
        }
        mCameraDeviceCtrl.onResume();
        // For onResume()->onPause()->onResume() quickly case,
        // onFullScreenChanged(true) may be called after onPause() and before
        // onResume().
        // So, force update app view.
        updateCameraAppViewIfNeed();
        doOnResume();
        // for the case camera onPause and then edit pic in gallery, when resume
        // camera, thumbnail will not update to new.
        mCameraAppUi.forceThumbnailUpdate();
        mNeedRestoreIfOpenFailed = true;
        Util.enterCameraPQMode();
        if (!isCameraOpened()) {
            mCameraAppUi.setViewState(ViewState.VIEW_STATE_CAMERA_CLOSED);
            Log.i(TAG, "[onResume],camera device is opening,set view state.");
        }
    }
    
    @Override
    protected void onPause() {
        Log.i(TAG, "onPause() mForceFinishing=" + mForceFinishing);
        super.onPause();
        
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onPause();
            return;
        }
        
        if (mPendingSwitchCameraId != UNKNOWN) {
            mPendingSwitchCameraId = UNKNOWN;
        }
        mCameraDeviceCtrl.onPause();
        if (mForceFinishing || mCameraDeviceCtrl.isOpenCameraFail()) {
            // M: patch for Picutre quality enhance
            Log.i(TAG, "onPause(),release surface texture.");
            Util.exitCameraPQMode();
            //when camera is open fail,need notify otherDeviceConnectMananger
            mOtherDeviceConectedManager.onPause();
            return;
        }
        //when camera start up thread is after onPause(),
        //so the case :mCameraDeviceCtrl.isOpenCameraFail() is false
        //but when handler msg will receive the msg:open file.
        //in this case we not need do the MSG
        mNeedRestoreIfOpenFailed = false;
        mCameraAppUi.collapseViewManager(true);
        mModuleManager.pause();
        keepCameraForSecure();
        clearFocusAndFace();
        uninstallIntentFilter();
        callResumablePause();
        mOrientationListener.disable();
        mLocationManager.recordLocation(false);
        // when close camera, should reset mOnResumeTime
        mOnResumeTime = 0L;
        // make sure this field is false when resume camera next time
        // mIsNeedUpdateOrientationToParameters = false;
        mMainHandler.removeCallbacksAndMessages(null);
        // actor will set screen on if needed, here reset it.
        resetScreenOn();
        // setLoadingAnimationVisible(true);
        // M: patch for Picutre quality enhance
        Util.exitCameraPQMode();
        mOtherDeviceConectedManager.onPause();
    }
    
    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy() isChangingConfigurations()=" + isChangingConfigurations()
                + ", mForceFinishing=" + mForceFinishing);
        super.onDestroy();
        
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onDestroy();
            CameraActivityBridgeFactory.destroyCameraActivityBridge(this);
            return;
        }
        
        mNextMode = UNKNOWN;
        
        mModuleManager.dismissCameraAppguide();
        // we finish worker thread when current activity destroyed.
        callResumableFinish();
        if (mFileSaver != null) {
            mFileSaver.unBindSaverService();
        }
        if (mCameraActor != null) {
            mCameraActor.release();
        }
        if (mISelfTimeManager != null) {
            ((SelfTimerManager) mISelfTimeManager).releaseSelfTimer();
            mISelfTimeManager = null;
        }
        if (mForceFinishing) {
            return;
        }
        mModuleManager.destory();
        mCameraDeviceCtrl.onDestory();
        if (mIsBackPressed) {
            clearUserSettings();
            mIsBackPressed = false;
        }
        
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onActivityResult(requestCode, resultCode, data);
            return ;
        }
        mCameraActor.onActivityResult(requestCode, resultCode, data);
    }
    
    @Override
    public void onBackPressed() {
        Log.i(TAG, "onBackPressed()");
        if (FeatureSwitcher.isApi2Enable(this)) {
            if(getCameraActivityBridge().onBackPressed()) {
                super.onBackPressed();
                return;
            }
            return;
        }
        if (mPaused || mForceFinishing) {
            return;
        }
        if (mCameraDeviceCtrl.isOpenCameraFail()) {
            super.onBackPressed();
            return;
        }
        if ((!mCameraAppUi.collapseViewManager(false) && !mCameraActor.onBackPressed())) {
            super.onBackPressed();
        }
        mIsBackPressed = true;
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onConfigurationChanged(newConfig);
            return;
        }
        MMProfileManager.startProfileCameraOnConfigChange();
        Log.i(TAG, "onConfigurationChanged(" + newConfig + ")");
        // before the view collapse,get current camera view state
        boolean isSettingsViewState = mCameraAppUi.getViewState() == ViewState.VIEW_STATE_SETTING;
        Log.d(TAG, "mCameraState = " + mCameraAppUi.getViewState() + ",isSettingsView = "
                + isSettingsViewState);
        clearFocusAndFace();
        
        MMProfileManager.startProfileCameraViewOperation();
        ViewGroup appRoot = (ViewGroup) findViewById(R.id.camera_app_root);
        appRoot.removeAllViews();
        getLayoutInflater().inflate(R.layout.preview_frame, appRoot, true);
        getLayoutInflater().inflate(R.layout.view_layers, appRoot, true);
        
        mCameraAppUi.removeAllView();
        MMProfileManager.stopProfileCameraViewOperation();
        
        // unlock orientation for new config
        setOrientation(false, OrientationEventListener.ORIENTATION_UNKNOWN);
        mCameraDeviceCtrl.setDisplayOrientation();
        initializeForOpeningProcess();
        // Here we should update aspect ratio for reflate preview frame layout.
        mCameraDeviceCtrl.setPreviewFrameLayoutAspectRatio();
        updateFocusAndFace();
        mCameraAppUi.onConfigurationChanged();
        notifyOrientationChanged();
        MMProfileManager.stopProfileCameraOnConfigChange();
        mModuleManager.configurationChanged();
    }
    
    @Override
    protected void onSingleTapUp(View view, int x, int y) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        // Gallery use onSingleTapConfirmed() instead of onSingleTapUp().
        Log.i(TAG, "onSingleTapUp(" + view + ", " + x + ", " + y + ")");
        // we do nothing for dialog is showing
        boolean isPogressShowing = mCameraAppUi.getCameraView(CommonUiType.ROTATE_PROGRESS)
                .isShowing();
        boolean isDialogShowing = mCameraAppUi.getCameraView(CommonUiType.ROTATE_DIALOG)
                .isShowing();
        if (!isDialogShowing && !isPogressShowing) {
            
            // For tablet
            if (FeatureSwitcher.isSubSettingEnabled()) {
                mCameraAppUi.collapseSubSetting(true);// need to check it?
                
            }
            
            if (isCancelSingleTapUp()) {
                Log.i(TAG, "will cancel this singleTapUp event");
                return;
            }
            
            if (!mCameraAppUi.collapseSetting(true)) {
                if (mCameraActor.getonSingleTapUpListener() != null) {
                    mCameraActor.getonSingleTapUpListener().onSingleTapUp(view, x, y);
                }
            }
        }
    }
    
    @Override
    protected void onLongPress(View view, int x, int y) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.i(TAG, "OnLongPress(" + view + ", " + x + ", " + y + ")" + ",mCurrentViewState = "
                + mCameraAppUi.getViewState());
        if (mCameraAppUi.getViewState() == ViewState.VIEW_STATE_LOMOEFFECT_SETTING) {
            return;
        }
        // we do nothing for dialog is showing
        boolean isPogressShowing = mCameraAppUi.getCameraView(CommonUiType.ROTATE_PROGRESS)
                .isShowing();
        boolean isDialogShowing = mCameraAppUi.getCameraView(CommonUiType.ROTATE_DIALOG)
                .isShowing();
        if (!isDialogShowing && !isPogressShowing) {
            if (!mCameraAppUi.collapseSetting(true)) {
                if (mCameraActor.getonLongPressListener() != null) {
                    mCameraActor.getonLongPressListener().onLongPress(view, x, y);
                }
            }
        }
    }
    
    @Override
    protected void onSingleTapUpBorder(View view, int x, int y) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        // Just collapse setting if touch border
        boolean isPogressShowing = mCameraAppUi.getCameraView(CommonUiType.ROTATE_PROGRESS)
                .isShowing();
        boolean isDialogShowing = mCameraAppUi.getCameraView(CommonUiType.ROTATE_DIALOG)
                .isShowing();
        if (!isDialogShowing && !isPogressShowing) {
            mCameraAppUi.collapseSetting(true);
            // For tablet
            if (FeatureSwitcher.isSubSettingEnabled()) {
                mCameraAppUi.collapseSubSetting(true);
            }
        }
    }
    
    @Override
    public void onStateChanged(int state) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        // if MSG_UPDATE_SWITCH_ACTOR_STATE message is has been done in during
        // changing mode,
        // this message should not be executed no more.
        if (!mIsSwitchActorRunning) {
            return;
        }
        mMainHandler.sendEmptyMessage(MSG_UPDATE_SWITCH_ACTOR_STATE);
        mIsSwitchActorRunning = false;
    }
    
    // Preview texture has been copied. Now camera can be released and the
    // animation can be started.
    @Override
    protected void onPreviewTextureCopied() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.i(TAG, "onPreviewTextureCopied()");
        //TODO don't use mPendingSwitchCameraId
        mMainHandler.obtainMessage(MSG_SWITCH_CAMERA, mPendingSwitchCameraId, 0).sendToTarget();
    }
    
    @Override
    protected void restoreSwitchCameraState() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        // Go to gallery during swithCamera,switch camera will be blocked.
        // so we cancel switch camera
        Log.i(TAG, "restoreCameraState()");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isSwitchingCamera()) {
                    mPendingSwitchCameraId = UNKNOWN;
                    mCameraAppUi.restoreViewState();
                    setCameraState(STATE_IDLE);
                }
            }
        });
    }
    
    @Override
    public void onUserInteraction() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            if (!getCameraActivityBridge().onUserInteraction()) {
                super.onUserInteraction();
            }
            return;
        }
        if (!mCameraActor.onUserInteraction()) {
            super.onUserInteraction();
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            if(!getCameraActivityBridge().onKeyDown(keyCode, event)) {
                return super.onKeyDown(keyCode, event);
            }
            return true;
        }
        // Do not handle any key if the activity is paused.
        if (mPaused) {
            return true;
        }
        if (isFullScreen() && KeyEvent.KEYCODE_MENU == keyCode && event.getRepeatCount() == 0
                && mCameraAppUi.performSettingClick()) {
            return true;
        }
        if (!mCameraActor.onKeyDown(keyCode, event)) {
            return super.onKeyDown(keyCode, event);
        }
        return true;
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            if(!getCameraActivityBridge().onKeyUp(keyCode, event)) {
                return super.onKeyUp(keyCode, event);
            }
            return true;
        }
        if (mPaused) {
            return true;
        }
        if (!mCameraActor.onKeyUp(keyCode, event)) {
            return super.onKeyUp(keyCode, event);
        }
        return true;
    }
    
    public ISettingCtrl getISettingCtrl() {
        return mISettingCtrl;
    }
    
    public void resetScreenOn() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.d(TAG, "resetScreenOn()");
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    public void keepScreenOnAwhile() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.d(TAG, "keepScreenOnAwhile()");
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mMainHandler.sendEmptyMessageDelayed(MSG_CLEAR_SCREEN_DELAY, DELAY_MSG_SCREEN_SWITCH);
    }
    
    public void keepScreenOn() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.d(TAG, "keepScreenOn()");
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_DELAY);
        if (getWindow() != null) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
    
    public interface OnOrientationListener {
        void onOrientationChanged(int orientation);
    }
    
    public interface OnParametersReadyListener {
        void onCameraParameterReady();
    }
    
    public interface OnPreferenceReadyListener {
        void onPreferenceReady();
    }
    
    public interface Resumable {
        void begin();
        
        void resume();
        
        void pause();
        
        void finish();
    }
    
    public interface OnFullScreenChangedListener {
        void onFullScreenChanged(boolean full);
    }
    
    public interface OnSingleTapUpListener {
        void onSingleTapUp(View view, int x, int y);
    }
    
    public interface OnLongPressListener {
        void onLongPress(View view, int x, int y);
    }
    
    public Vibrator getVibrator() {
        return mVibrator;
    }
    
    public SharedPreferences getSharePreferences() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return null;
        }
        int cameraId = mCameraDeviceCtrl.getCameraId();
        return mPreferences.getSharedPreference((Context) CameraActivity.this, cameraId);
    }
    
    public SharedPreferences getSharePreferences(int cameraId) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return null;
        }
        return mPreferences.getSharedPreference((Context) CameraActivity.this, cameraId);
    }
    
    public void setCameraState(int state) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        Log.i(TAG, "setCameraState(" + state + ")");
        mCameraState = state;
    }
    
    public int getCameraState() {
        return mCameraState;
    }
    
    public boolean isCameraOpened() {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return false;
        }
        return mCameraDeviceCtrl.isCameraOpened();
    }
    
    public boolean isModeChanged() {
        return mIsModeChanged;
    }
    
    public int getNextMode() {
        return mNextMode;
    }
    
    public int getPrevMode() {
        return mPrevMode;
    }
    
    public int getCurrentRecordingFps(Parameters ps) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return -1;
        }
        Log.i(TAG, "getCurrentRecordingFps ");
        int value = -1;
        boolean isEISOn = ps.get(ParametersHelper.KEY_VIDEO_STABLILIZATION).equals(
                ParametersHelper.VIDEO_STABLILIZATION_ON);
        if (isEISOn) {
            String typeValueString = ps.get(ParametersHelper.KEY_VIDEO_RECORIND_FEATURE_MAX_FPS);
            Log.i(TAG, "getCurrentRecordingFps, typeValueString = " + typeValueString);
            if (typeValueString != null) {
                int index = typeValueString.indexOf("@");
                if (index >= 0) {
                    value = Integer.parseInt(typeValueString.substring(0, index));
                }
            }
        }
        Log.i(TAG, "getCurrentRecordingFps, value = " + value);
        return value;
    }
    
    public ISelfTimeManager getSelfTimeManager() {
        Log.i(TAG, "[getSelfTimeManager] mISelfTimeManager = " + mISelfTimeManager);
        return mISelfTimeManager;
    }
    
    public ICameraAppUi getCameraAppUI() {
        return mCameraAppUi;
    }
    
    public FrameView getFrameView() {
        return mFrameManager.getFrameView();
    }
    
    public FrameManager getFrameManager() {
        return mFrameManager;
    }
    
    public ComboPreferences getPreferences() { // not recommended
        return mPreferences;
    }
    
    public ListPreference getListPreference(int row) {
        String key = SettingConstants.getSettingKey(row);
        return getListPreference(key);
    }
    
    public ListPreference getListPreference(String key) {
        return mISettingCtrl.getListPreference(key);
    }
    
    public FileSaver getFileSaver() {
        return mFileSaver;
    }
    
    public LocationManager getLocationManager() {
        return mLocationManager;
    }
    
    public int getCurrentMode() {
        return mCameraActor.getMode();
    }
    
    public ModePicker getModePicker() {
        return mModePicker;
    }
    
    // activity
    public int getDisplayRotation() {
        return mDisplayRotation;
    }
    
    // sensor
    public int getOrietation() {
        return mOrientation;
    }
    
    // activity + sensor
    public int getOrientationCompensation() {
        return mOrientationCompensation;
    }
    
    public int getCameraCount() {
        return mNumberOfCameras;
    }
    
    public CameraActor getCameraActor() {
        return mCameraActor;
    }
    
    public ModuleManager getModuleManager() {
        return mModuleManager;
    }
    
    public int getPreviewFrameWidth() {
        return mCameraDeviceCtrl.getPreviewFrameWidth();
    }
    
    public int getPreviewFrameHeight() {
        return mCameraDeviceCtrl.getPreviewFrameHeight();
    }
    
    public View getPreviewFrameLayout() {
        return mPreviewFrameLayout;
    }
    
    public int getUnCropWidth() {
        return mCameraDeviceCtrl.getUnCropWidth();
    }
    
    public int getUnCropHeight() {
        return mCameraDeviceCtrl.getUnCropHeight();
    }
    
    public float[] computeVertex(float x, float y) {
        float[] vertexAndRect = new float[] { 0f, 0f, 0f, 0f };
        Rect rect = getCameraRelativeFrame();
        // map compensation firstly, because this compensation is based on
        // original activity's display rotation
        Matrix m = getGLRoot().getCompensationMatrix();
        Matrix inv = new Matrix();
        m.invert(inv);
        float[] pts = new float[] { x, y };
        inv.mapPoints(pts);
        x = pts[0];
        y = pts[1];
        Log.i(TAG, "after compensation x = " + x + " y = " + y);
        // map Camera relative frame (change to full screen or 4:3 screen)
        float[] vertex = getVertexRelativePreviewFrame(x, y);
        vertexAndRect[0] = vertex[0];
        vertexAndRect[1] = vertex[1];
        vertexAndRect[2] = rect.width();
        vertexAndRect[3] = rect.height();
        return vertexAndRect;
    }
    
    private float[] getVertexRelativePreviewFrame(float x, float y) {
        float[] vertex = new float[] { x, y };
        if (mPreviewFrameLayout == null || !mShowCameraAppView) {
            return vertex;
        }
        
        int[] relativeLocation = Util.getRelativeLocation((View) getGLRoot(), mPreviewFrameLayout);
        x -= relativeLocation[0];
        y -= relativeLocation[1];
        vertex[0] = x;
        vertex[1] = y;
        return vertex;
    }
    
    public void showBorder(boolean show) {
        if (FeatureSwitcher.isApi2Enable(this)) {
            return;
        }
        mPreviewFrameLayout.showBorder(show);
    }
    
    public int getCameraScreenNailWidth() {
        return mCameraScreenNail.getTextureWidth();
    }
    
    public int getCameraScreenNailHeight() {
        return mCameraScreenNail.getTextureHeight();
    }
    
    public View inflate(int layoutId, int layer) {
        return mCameraAppUi.inflate(layoutId, layer);
    }
    
    public void addView(View view, int layer) {
        mCameraAppUi.addView(view, layer);
    }
    
    public void removeView(View view, int layer) {
        mCameraAppUi.removeView(view, layer);
    }
    
    public boolean addOnFullScreenChangedListener(OnFullScreenChangedListener l) {
        if (!mFullScreenListeners.contains(l)) {
            return mFullScreenListeners.add(l);
        }
        return false;
    }
    
    public boolean removeOnFullScreenChangedListener(OnFullScreenChangedListener l) {
        return mFullScreenListeners.remove(l);
    }
    
    public boolean addOnPreferenceReadyListener(OnPreferenceReadyListener l) {
        if (!mPreferenceListeners.contains(l)) {
            return mPreferenceListeners.add(l);
        }
        return false;
    }
    
    public boolean addOnParametersReadyListener(OnParametersReadyListener l) {
        if (!mParametersListeners.contains(l)) {
            return mParametersListeners.add(l);
        }
        return false;
    }
    
    public boolean removeOnParametersReadyListener(OnParametersReadyListener l) {
        return mParametersListeners.remove(l);
    }
    
    public boolean addViewManager(ViewManager viewManager) {
        return mCameraAppUi.addViewManager(viewManager);
    }
    
    public boolean removeViewManager(ViewManager viewManager) {
        return mCameraAppUi.removeViewManager(viewManager);
    }
    
    public boolean addResumable(Resumable resumable) {
        if (!mResumables.contains(resumable)) {
            return mResumables.add(resumable);
        }
        return false;
    }
    
    public boolean removeResumable(Resumable resumable) {
        return mResumables.remove(resumable);
    }
    
    // Becasue SMB will need this
    public void changeOrientationTag(boolean lock, int orientationNum) {
        setOritationTag(lock, orientationNum);
    }
    
    protected void onAfterFullScreeChanged(final boolean full) {
        Log.d(TAG, "onAfterFullScreeChanged(" + full + ")");
        if (FeatureSwitcher.isApi2Enable(this)) {
            getCameraActivityBridge().onAfterFullScreeChanged(full);
            return;
        }
        if (full) {
            /*
             * M: MSG_CHECK_DISPLAY_ROTATION may get a wrong display rotation in
             * this case: Enter camera(protrait) --> change to landscape -->
             * click thumbnail. Then, when back to camera, mDisplayRotation will
             * be 270 instead of 90. So here we recheck it for this case. when
             * CameraStartUpThread done, we can check display rotation.
             */
            if (mOnResumeTime != 0L) {
                mMainHandler.sendEmptyMessage(MSG_CHECK_DISPLAY_ROTATION);
            }
        } else {
            mCameraAppUi.hideToast();
        }
        // Update gallery GLSurfaceView window's alpha value
        mCameraAppUi.updateSurfaceViewAlphaValue((SurfaceView)getGLRoot(),full);
        mCameraDeviceCtrl.onAfterFullScreeChanged(full);
        notifyOnFullScreenChanged(full);
    }
    
    private Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Log.i(TAG, "handleMessage(" + msg + ")");
            switch (msg.what) {
            case MSG_CAMERA_PARAMETERS_READY:
                notifyParametersReady();
                break;
            case MSG_CHECK_DISPLAY_ROTATION:
                // Set the display orientation if display rotation has changed.
                // Sometimes this happens when the device is held upside
                // down and camera app is opened. Rotation animation will
                // take some time and the rotation value we have got may be
                // wrong. Framework does not have a callback for this now.
                if (Util.getDisplayRotation(CameraActivity.this) != mDisplayRotation) {
                    mCameraDeviceCtrl.setDisplayOrientation();
                    mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
                    mCameraActor.onDisplayRotate();
                }
                if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
                    mMainHandler.sendEmptyMessageDelayed(MSG_CHECK_DISPLAY_ROTATION, 100);
                }
                notifyOrientationChanged();
                break;
            case MSG_SWITCH_CAMERA:
                mCameraDeviceCtrl.switchCamera(msg.arg1);
                break;
            case MSG_CLEAR_SCREEN_DELAY:
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                break;
            case MSG_APPLY_PARAMETERS_WHEN_IDEL:
                mCameraDeviceCtrl.applyParameters(false);
                break;
            case MSG_SET_PREVIEW_ASPECT_RATIO:
                mCameraDeviceCtrl.setPreviewFrameLayoutAspectRatio();
                break;
            case MSG_DELAY_SHOW_ONSCREEN_INDICATOR:
                // will handle the delay hide the remain when is show remain
                mCameraAppUi.showText(mDelayShowInfo);
                mCameraAppUi.showIndicator(mDelayOtherMessageTime);
                break;
            case MSG_UPDATE_SWITCH_ACTOR_STATE:
                mModePicker.setEnabled(true);
                break;
            default:
                break;
            }
        };
    };
    
    public void onCameraOpenFailed() {
        restoreWhenCameraOpenFailed();
    }
    
    public void onCameraOpenDone() {
        mPendingSwitchCameraId = UNKNOWN;
        mMainHandler.sendEmptyMessage(MSG_CHECK_DISPLAY_ROTATION);
    }
    
    public void onCameraPreferenceReady() {
        notifyPreferenceReady();
    }
    
    public void onCameraParametersReady() {
        notifyParametersReady();
    }
    
    private void restoreWhenCameraOpenFailed() {
        Log.i(TAG, "restoreWhenCameraOpenFailed(), mNeedRestoreIfOpenFailed:"
                + mNeedRestoreIfOpenFailed);
        // some setting should be restore when camera open failed, because they
        // may
        // be set in onResume() method.
        if (mNeedRestoreIfOpenFailed) {
            uninstallIntentFilter();
            callResumablePause();
            mCameraAppUi.collapseViewManager(true);
            mOrientationListener.disable();
            mLocationManager.recordLocation(false);
            mMainHandler.removeCallbacksAndMessages(null);
            // actor will set screen on if needed, here reset it.
            resetScreenOn();
            // setLoadingAnimationVisible(true);
            // M: patch for Picutre quality enhance
            Util.exitCameraPQMode();
        }
    }
    
    private void keepCameraForSecure() {
        // When camera is started from secure lock screen for the first time
        // after screen on, the activity gets
        // onCreate->onResume->onPause->onResume.
        // To reduce the latency, keep the camera for a short time so it does
        // not need to be opened again.
        if (isSecureCamera() && isFirstStartAfterScreenOn()) {
            resetFirstStartAfterScreenOn();
            int cameraId = mCameraDeviceCtrl.getCameraId();
            CameraHolder.instance().keep(KEEP_CAMERA_TIMEOUT, cameraId);
        }
    }
    
    private void initializeAfterPreview() {
        long start = System.currentTimeMillis();
        callResumableBegin();
        mCameraAppUi.initializeAfterPreview();
        if (isNonePickIntent() || isStereo3DImageCaptureIntent()) {
            // mModePicker.show(); //will do when parameters ready
            if (!mSecureCamera && FeatureSwitcher.isAppGuideEnable()) {
                mModuleManager.showCameraAppguide(IAppGuide.GUIDE_TYPE_CAMERA, onFinishListener);
            }
        }
        
        addIdleHandler();// why no flag to disable it after checked.
        long stop = System.currentTimeMillis();
        
        Log.v(TAG, "initializeAfterPreview() consume:" + (stop - start));
    }
    
    private final IAppGuide.OnGuideFinishListener onFinishListener = new IAppGuide.OnGuideFinishListener() {
        public void onGuideFinish() {
            setAppGuideFinished(true);
        }
    };
    
    // Here should be lightweight functions!!!
    private void initializeCommonManagers() {
        mModePicker = new ModePicker(this);
        mFileSaver = new FileSaver(this);
        mFrameManager = new FrameManager(this);
        mModePicker.setListener(mModeChangedListener);
        mCameraAppUi.setSettingListener(mSettingListener);
        mCameraAppUi.setPickerListener(mPickerListener);
        mCameraAppUi.addFileSaver(mFileSaver);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        Log.v(TAG, "getSystemService,mPowerManager =" + mPowerManager);
        // For tablet
        if (FeatureSwitcher.isSubSettingEnabled()) {
            mCameraAppUi.setSubSettingListener(mSettingListener);
        }
    }
    
    private void initializeForOpeningProcess() {
        MMProfileManager.startProfileInitOpeningProcess();
        mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();
        MMProfileManager.startProfileCameraViewOperation();
        mCameraAppUi.initializeViewGroup();
        
        // for focus manager used
        mFocusAreaIndicator = (RotateLayout) findViewById(R.id.focus_indicator_rotate_layout);
        
        // startPreview needs this.
        mPreviewFrameLayout = (PreviewFrameLayout) findViewById(R.id.frame);
        // Set touch focus listener.
        setSingleTapUpListener(mPreviewFrameLayout);
        // Set Long Press for Object Tracking listener.
        setLongPressListener(mPreviewFrameLayout);
        mPreviewFrameLayout.addOnLayoutChangeListener(this);
        mPreviewFrameLayout.setOnSizeChangedListener(this);
        
        // M: temp added for Mock Camera feature
        if (FrameworksClassFactory.isMockCamera()) {
            mPreviewFrameLayout.setBackgroundResource(R.drawable.mock_preview_rainbow);
        }
        MMProfileManager.stopProfileCameraViewOperation();
        // for other info.
        if (mLocationManager == null) {
            mLocationManager = new LocationManager(this, null);
        }
        if (mOrientationListener == null) {
            mOrientationListener = new MyOrientationEventListener(this);
        }
        Log.i(TAG, "initializeForOpeningProcess() mNumberOfCameras=" + mNumberOfCameras);
        MMProfileManager.stopProfileInitOpeningProcess();
    }
    
    private void updateFocusAndFace() {
        if (getFrameView() != null) {
            getFrameView().clear();
            getFrameView().setVisibility(View.VISIBLE);
            //getFrameView().setDisplayOrientation(mDisplayOrientation);
            int cameraId = mCameraDeviceCtrl.getCameraId();
            CameraInfo info = CameraHolder.instance().getCameraInfo()[cameraId];
            getFrameView().setMirror(info.facing == CameraInfo.CAMERA_FACING_FRONT);
            getFrameView().resume();
        }
        FocusManager focusManager = mCameraDeviceCtrl.getFocusManager();
        if (focusManager != null) {
            focusManager.setFocusAreaIndicator(mFocusAreaIndicator);
            View mFocusIndicator = mFocusAreaIndicator.findViewById(R.id.focus_indicator);
            // Set the length of focus indicator according to preview frame
            // size.
            int len = Math.min(getPreviewFrameWidth(), getPreviewFrameHeight()) / 4;
            ViewGroup.LayoutParams layout = mFocusIndicator.getLayoutParams();
            layout.width = len;
            layout.height = len;
        }
        if (mFrameManager != null && getFrameView() != null) {
            // these case(configuration change), OT is must be false.
            // default view is face view.
            mFrameManager.initializeFrameView(false);
        }
    }
    
    private void doOnResume() {
        long start = System.currentTimeMillis();
        mOrientationListener.enable();
        /*
         * when launch video camera with MMS open the flash, between the switch
         * camera a call is coming and then reject it,will found the camera view
         * is unclickable because the camera view state is :switch camera so
         * need to restore the view state at here
         * 
         * But we should not restore view state when ReviewManager is showing,
         * otherwise, setting/flash/switch camera icons will be shown on UI.
         */

        installIntentFilter();
        callResumableResume();
        mCameraAppUi.checkViewManagerConfiguration();
        // change the file saver listener for foreground Activity.
        long stop = System.currentTimeMillis();
        Log.d(TAG, "doOnResume() consume:" + (stop - start));
    }
    
    private void clearFocusAndFace() {
        if (getFrameView() != null) {
            getFrameView().clear();
        }
        FocusManager focusManager = mCameraDeviceCtrl.getFocusManager();
        if (focusManager != null) {
            focusManager.removeMessages();
        }
    }
    
    private boolean isCancelSingleTapUp() {
        if (mCameraAppUi.getViewState() == ViewState.VIEW_STATE_LOMOEFFECT_SETTING) {
            return true;
        }
        return false;
    }
    
    private void notifyOnFullScreenChanged(boolean full) {
        for (OnFullScreenChangedListener listener : mFullScreenListeners) {
            if (listener != null) {
                listener.onFullScreenChanged(full);
            }
        }
    }
    
    private List<OnPreferenceReadyListener> mPreferenceListeners = new CopyOnWriteArrayList<OnPreferenceReadyListener>();
    
    private void notifyPreferenceReady() {
        for (OnPreferenceReadyListener listener : mPreferenceListeners) {
            if (listener != null) {
                listener.onPreferenceReady();
            }
        }
    }
    
    private List<OnParametersReadyListener> mParametersListeners = new CopyOnWriteArrayList<OnParametersReadyListener>();
    
    private void notifyParametersReady() {
        ViewState curState = mCameraAppUi.getViewState();
        if ((isNonePickIntent() && mCameraActor.getMode() != ModePicker.MODE_VIDEO
                && mCameraActor.getMode() != ModePicker.MODE_VIDEO_PIP
                && curState != ViewState.VIEW_STATE_SETTING
                && curState != ViewState.VIEW_STATE_SUB_SETTING && curState != ViewState.VIEW_STATE_RECORDING)
                || isStereo3DImageCaptureIntent()) {
            mModePicker.show();
        }
        
        if (!isSecureCamera()) {
            mLocationManager.recordLocation(RecordLocationPreference.get(mPreferences,
                    getContentResolver()));
        }
        for (OnParametersReadyListener listener : mParametersListeners) {
            if (listener != null) {
                listener.onCameraParameterReady();
            }
        }
        mCameraAppUi.notifyParametersReady();
    }
    
    private List<Resumable> mResumables = new CopyOnWriteArrayList<Resumable>();
    
    private void callResumableBegin() {
        for (Resumable resumable : mResumables) {
            resumable.begin();
        }
    }
    
    private void callResumableResume() {
        for (Resumable resumable : mResumables) {
            resumable.resume();
        }
    }
    
    private void callResumablePause() {
        for (Resumable resumable : mResumables) {
            resumable.pause();
        }
    }
    
    private void callResumableFinish() {
        for (Resumable resumable : mResumables) {
            resumable.finish();
        }
    }
    
    // / @}
    
    // / M: mode change logic @{
    private boolean mIsFromRestore = false;
    private int mOriCameraId = UNKNOWN;
    private ModePicker.OnModeChangedListener mModeChangedListener = new ModePicker.OnModeChangedListener() {
        @Override
        public void onModeChanged(int newMode) {
            Log.i(TAG, "onModeChanged(" + newMode + ") current mode = " + mCameraActor.getMode()
                    + ", state=" + mCameraState);
            mPrevMode = mCameraActor.getMode();
            mNextMode = newMode;
            int oldMode = mPrevMode;
            
            if (mCameraActor.getMode() != newMode) {
                // when mode changed,remaining manager should check whether to
                // show
                mIsModeChanged = true;
                mIsSwitchActorRunning = true;
                // releaseCameraActor has change mLastMode
                String oldCameraMode = mISettingCtrl.getCameraMode(getModeSettingKey(oldMode));
                String newCameraMode = mISettingCtrl.getCameraMode(getModeSettingKey(newMode));
                // switch live photo to video,need do restart preview
                boolean needRestart = (!oldCameraMode.equals(newCameraMode))
                        || Parameters.CAMERA_MODE_MTK_VDO == Integer.parseInt(newCameraMode);
                Log.i(TAG, "needRestart = " + needRestart);
                // if need restart preview, should do stop preview in last mode
                if (needRestart) {
                    mCameraActor.stopPreview();
                }
                releaseCameraActor(oldMode, newMode);
                mModuleManager.setModeSettingValue(mCameraActor.getCameraModeType(oldMode), "off");
                if (isPIPModeSwitch(oldMode, newMode)) {
                    // when user does pip mode switch more quickly,
                    // here try to close camera, camera start up thread may not
                    // done,
                    // should wait to not disturb camera status
                    if(!isPIPMode(oldMode)) {
                        mOriCameraId = getCameraId();
                    }
                    mCameraDeviceCtrl.closeCamera();
                }
                if (isFastAfEnabled() && isRefocusSwitchVideo(oldMode, newMode)) {
                    if (newMode == ModePicker.MODE_VIDEO) {
                        mIsStereoToVideoMode = true;
                    }
                    initializeDualCamera();
                }
                switch (newMode) {
                case ModePicker.MODE_PHOTO:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                case ModePicker.MODE_FACE_BEAUTY:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                case ModePicker.MODE_PANORAMA:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                case ModePicker.MODE_MAV:
                    if (isNonePickIntent() && !mSecureCamera) {
                        mModuleManager.showCameraAppguide(IAppGuide.GUIDE_TYPE_MAV, onFinishListener);
                    }
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                case ModePicker.MODE_MOTION_TRACK:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                case ModePicker.MODE_PHOTO_PIP:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                case ModePicker.MODE_VIDEO:
                    mCameraActor = new VideoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                case ModePicker.MODE_LIVE_PHOTO:
                    mCameraActor = new VideoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                case ModePicker.MODE_VIDEO_PIP:
                    mCameraActor = new VideoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                case ModePicker.MODE_STEREO_CAMERA:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                default:
                    mCameraActor = new PhotoActor(CameraActivity.this, mModuleManager, newMode);
                    break;
                }
                // after apply settings, should change preview surface
                // immediately
                mCameraDeviceCtrl.setCameraActor(mCameraActor);
                // startup thread will apply these things after onResume
                if (mPaused || mCameraState == STATE_SWITCHING_CAMERA) {
                    mIsModeChanged = false;
                    Log.i(TAG, "onModeChanged return mPaused = " + mPaused);
                    return;
                }
                
                if (isPIPModeSwitch(oldMode, mCameraActor.getMode())) {
                    // the first time to update PickerManager when pip changed
                    // to other mode
                    doPIPModeChanged(mOriCameraId);
                    mIsModeChanged = false;
                    Log.i(TAG, "onModeChanged isPIPModeSwitch return");
                    return;
                }
                mModuleManager.setModeSettingValue(mCameraActor.getCameraModeType(newMode), "on");
                if (isRefocusSwitchNormal(oldMode, newMode)) {
                    initializeDualCamera();
                    mIsModeChanged = false;
                    Log.i(TAG, "onModeChanged isRefocusSwitchNormal return");
                    return;
                }
                if (isFastAfEnabled() && isRefocusSwitchVideo(oldMode, newMode)) {
                    Log.i(TAG, "onModeChanged isRefocusSwitchVideo return");
                    return;
                }
                // reset default focus modes.
                notifyOrientationChanged();
                mCameraDeviceCtrl.onModeChanged(needRestart);
                mIsModeChanged = false;
            }
        }
    };
    
    private String getModeSettingKey(int mode) {
        String key = null;
        switch (mode) {
        case ModePicker.MODE_PHOTO:
            key = SettingConstants.KEY_NORMAL;
            break;
        case ModePicker.MODE_MAV:
            key = SettingConstants.KEY_MAV;
            break;
        case ModePicker.MODE_PANORAMA:
            key = SettingConstants.KEY_PANORAMA;
            break;
        case ModePicker.MODE_VIDEO:
            key = SettingConstants.KEY_VIDEO;
            break;
        case ModePicker.MODE_LIVE_PHOTO:
            key = SettingConstants.KEY_LIVE_PHOTO;
            break;
        case ModePicker.MODE_VIDEO_PIP:
            key = SettingConstants.KEY_VIDEO_PIP;
            break;
        case ModePicker.MODE_PHOTO_PIP:
            key = SettingConstants.KEY_PHOTO_PIP;
            break;
        case ModePicker.MODE_FACE_BEAUTY:
            key = SettingConstants.KEY_FACE_BEAUTY;
            break;
        case ModePicker.MODE_MOTION_TRACK:
            key = SettingConstants.KEY_MOTION_TRACK;
            break;
        case ModePicker.MODE_STEREO_CAMERA:
            key = SettingConstants.KEY_REFOCUS;
            break;
        default:
            break;
        }
        return key;
    }
    
    private void releaseCameraActor(int oldMode, int newMode) {
        Log.i(TAG, "releaseCameraActor() mode=" + mCameraActor.getMode());
        boolean shouldHideSetting = false;
        shouldHideSetting = (newMode != ModePicker.MODE_ASD && newMode != ModePicker.MODE_HDR
                && oldMode != ModePicker.MODE_ASD && oldMode != ModePicker.MODE_HDR);
        if (shouldHideSetting) {
            mCameraAppUi.collapseViewManager(true);
        }
        
        mCameraActor.release();
        if (newMode == ModePicker.MODE_VIDEO || newMode == ModePicker.MODE_VIDEO_3D
                || newMode == ModePicker.MODE_VIDEO_PIP) {
            setSwipingEnabled(false);
            Log.i(TAG, "releaseCameraActor setSwipingEnabled(false)  newMode = " + newMode);
        }
    }
    
    public void onSettingChanged(String key, String value) {
        mISettingCtrl.onSettingChanged(key, value);
    }
    
    // Camera Decoupling --->begin
    /**
     * because current on Listener and cancel Listener is add on the shutter
     * button and video button so when we want show on and cancel button, first
     * need set the video and photo listener to null
     * 
     * @param okOnClickListener
     * @param cancelOnClickListener
     * @param retatekOnClickListener
     * @param playOnClickListener
     */
    public void applyReviewCallbacks(OnClickListener okOnClickListener,
            OnClickListener cancelOnClickListener, OnClickListener retatekOnClickListener,
            OnClickListener playOnClickListener) {
        mPlayListener = playOnClickListener;
        mRetakeListener = retatekOnClickListener;
    }
    
    private OnClickListener mPlayListener;
    private OnClickListener mRetakeListener;
    
    public OnClickListener getPlayListener() {
        return mPlayListener;
    }
    
    public OnClickListener getRetakeLisenter() {
        return mRetakeListener;
    }
    
    public void enableOrientationListener() {
        mOrientationListener.enable();
    }
    
    public void disableOrientationListener() {
        mOrientationListener.disable();
    }
    
    private SettingManager.SettingListener mSettingListener = new SettingManager.SettingListener() {
        @Override
        public void onSharedPreferenceChanged(ListPreference preference) {
            Log.d(TAG, "[onSharedPreferenceChanged]");
            if (preference != null) {
                String settingKey = preference.getKey();
                String value = preference.getValue();
                mISettingCtrl.onSettingChanged(settingKey, value);
            }
            mCameraDeviceCtrl.applyParameters(false);
        }
        
        @Override
        public void onRestorePreferencesClicked() {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "[onRestorePreferencesClicked.run]");
                    mIsFromRestore = true;
                    mCameraActor.onRestoreSettings();
                    mCameraAppUi.collapseViewManager(true);
                    mCameraAppUi.resetSettings();
                    
                    SharedPreferences globalPref = mPreferences.getGlobal();
                    SharedPreferences.Editor editor = globalPref.edit();
                    editor.clear();
                    editor.apply();
                    SettingUtils.upgradeGlobalPreferences(globalPref, CameraHolder.instance()
                            .getNumberOfCameras());
                    SettingUtils.writePreferredCameraId(globalPref, mCameraDeviceCtrl.getCameraId());
                    
                    int backCameraId = CameraHolder.instance().getBackCameraId();
                    SettingUtils.restorePreferences((Context) CameraActivity.this,
                            getSharePreferences(backCameraId),
                            mCameraDeviceCtrl.getParametersExt(), isNonePickIntent());

                    // restore front camera setting
                    int frontCameraId = CameraHolder.instance().getFrontCameraId();
                    SettingUtils.restorePreferences((Context) CameraActivity.this,
                            getSharePreferences(frontCameraId),
                            mCameraDeviceCtrl.getParametersExt(), isNonePickIntent());

                    SettingUtils.initialCameraPictureSize((Context) CameraActivity.this,
                            mCameraDeviceCtrl.getParametersExt(), getSharePreferences());

                    mISettingCtrl.restoreSetting(backCameraId);
                    mISettingCtrl.restoreSetting(frontCameraId);
                    
                    mCameraAppUi.resetZoom();
                    // we should apply parameters if mode is default too.
                    if (ModePicker.MODE_PHOTO == mCameraActor.getMode() || !isNonePickIntent()
                            || ModePicker.MODE_PHOTO_SGINLE_3D == mCameraActor.getMode()
                            || ModePicker.MODE_PHOTO_3D == mCameraActor.getMode()) {
                        if (ModePicker.MODE_VIDEO == mCameraActor.getMode() && !isNonePickIntent()) {
                            mISettingCtrl.onSettingChanged(SettingConstants.KEY_VIDEO,"on");
                        }
                        mCameraDeviceCtrl.applyParameters(false);
                    } else {
                        mModePicker.setModePreference(null);
                        mModePicker.setCurrentMode(ModePicker.MODE_PHOTO);
                    }
                    mIsFromRestore = false;
                }
            };
            mCameraAppUi.showAlertDialog(null, getString(R.string.confirm_restore_message),
                    getString(android.R.string.cancel), null, getString(android.R.string.ok),
                    runnable);
        }
        
        @Override
        public void onSettingContainerShowing(boolean show) {
            mModuleManager.onSettingContainerShowing(show);
            if (show) {
            } else {
                if (isFaceBeautyEnable() && getCurrentMode() == ModePicker.MODE_FACE_BEAUTY
                        && !mIsFromRestore) {
                    // when face is detected will show the icon
                    // otherwise don't show
                    Log.i(TAG,
                            "onSettingContainerShowing, will set modify icon stautes true,and show FB icon");
                    // mLomoEffectsManager.show();
                }
            }
        }
        
        @Override
        public void onVoiceCommandChanged(int commandId) {
            mModuleManager.onVoiceCommandNotify(commandId);
        }
        
        @Override
        public void onStereoCameraPreferenceChanged(ListPreference preference, int type) {
            if (preference != null
                    && preference.getKey().equals(SettingConstants.KEY_DUAL_CAMERA_MODE)) {
                Log.i(TAG, "onStereoCameraPreferenceChanged, type = " + type);
                if (getCurrentMode() == ModePicker.MODE_STEREO_CAMERA) {
                    if (type == DUAL_CAMERA_ENHANCE_ENABLE) {
                        enableDualCameraExtras();
                    }
                    if (type == DUAL_CAMERA_ENHANCE_DISABLE) {
                        disableDualCameraExtras();
                    }
                    if (type == DUAL_CAMERA_START) {
                        singleDualCameraExtras();
                        mCameraDeviceCtrl.applyParameters(false);
                        return;
                    }
                    mCameraDeviceCtrl.applyParameters(false);
                    return;
                } else {
                    if (type == DUAL_CAMERA_ENHANCE_ENABLE) {
                        enableDualCameraExtras();
                    }
                    if (type == DUAL_CAMERA_ENHANCE_DISABLE) {
                        disableDualCameraExtras();
                    }
                    if (type == DUAL_CAMERA_START) {
                        singleDualCameraExtras();
                        initializeDualCamera();
                        return;
                    }
                    if (type == DUAL_CAMERA_SWITCH_IN_REFOCUS) {
                        singleDualCameraExtras();
                        mCameraDeviceCtrl.applyParameters(false);
                        return;
                    }
                    initializeDualCamera();
                    return;
                }
            }
        }
    };
    
    private void enableDualCameraExtras() {
        getListPreference(SettingConstants.ROW_SETTING_DISTANCE).setValueIndex(ON);
        getListPreference(SettingConstants.ROW_SETTING_FAST_AF).setValueIndex(ON);
        onSettingChanged(SettingConstants.KEY_FAST_AF, "on");
        onSettingChanged(SettingConstants.KEY_DISTANCE, "on");
    }
    
    private void disableDualCameraExtras() {
        getListPreference(SettingConstants.ROW_SETTING_DISTANCE).setValueIndex(OFF);
        getListPreference(SettingConstants.ROW_SETTING_FAST_AF).setValueIndex(OFF);
        onSettingChanged(SettingConstants.KEY_FAST_AF, "off");
        onSettingChanged(SettingConstants.KEY_DISTANCE, "off");
    }
    
    private void singleDualCameraExtras() {
        if ("off".equals(getListPreference(SettingConstants.KEY_FAST_AF).getValue())) {
            onSettingChanged(SettingConstants.KEY_FAST_AF, "off");
        } else {
            onSettingChanged(SettingConstants.KEY_FAST_AF, "on");
        }
        if ("off".equals(getListPreference(SettingConstants.KEY_DISTANCE).getValue())) {
            onSettingChanged(SettingConstants.KEY_DISTANCE, "off");
        } else {
            onSettingChanged(SettingConstants.KEY_DISTANCE, "on");
        }
    }
    
    public void notifyPreferenceChanged(ListPreference preference) {
        mSettingListener.onSharedPreferenceChanged(preference);
        mCameraAppUi.getCameraView(CommonUiType.SETTING).refresh();
    }
    
    // / M: for photo info @{
    public String getSelfTimer() {
        String seflTimer = mISettingCtrl.getSettingValue(SettingConstants.KEY_SELF_TIMER);
        Log.d(TAG, "getSelfTimer() return " + seflTimer);
        return seflTimer;
    }
    
    // / M: orientation case @{
    // / M: for listener and resumable convenient functions @{
    private List<OnOrientationListener> mOrientationListeners = new CopyOnWriteArrayList<OnOrientationListener>();
    
    public boolean addOnOrientationListener(OnOrientationListener l) {
        if (!mOrientationListeners.contains(l)) {
            return mOrientationListeners.add(l);
        }
        return false;
    }
    
    public boolean removeOnOrientationListener(OnOrientationListener l) {
        return mOrientationListeners.remove(l);
    }
    
    public void setOrientation(boolean lock, int orientation) {
        // Log.i(TAG, "setOrientation orientation=" + orientation +
        // " mOrientationListener.getLock()="
        // + mOrientationListener.getLock() + " lock = " + lock);
        mOrientationListener.setLock(false);
        if (lock) {
            mOrientationListener.onOrientationChanged(orientation);
            mOrientationListener.setLock(true);
            
        } else {
            mOrientationListener.restoreOrientation();
        }
    }
    
    public void animateCapture() {
        // video -> image will resize preview size, copy texture to avoid
        // stretch preview frame.
        if (mModePicker.getModeIndex(mCameraActor.getMode()) == ModePicker.MODE_VIDEO) {
            mCameraScreenNail.copyOriginSizeTexture();
        }
        boolean rotationLocked = (1 != Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0));
        int rotation = 0;
        if (rotationLocked) {
            // mCameraScreenNail.animateCapture(rotation);
            mCameraScreenNail.animateCapture(rotation);
        } else {
            rotation = (mOrientationCompensation - mDisplayRotation + 360) % 360;
            // mCameraScreenNail.animateCapture(rotation);
            mCameraScreenNail.animateCapture(rotation);
        }
        Log.d(TAG, "animateCapture() rotation=" + rotation + ", mDisplayRotation="
                + mDisplayRotation + ", mOrientationCompensation=" + mOrientationCompensation
                + ", rotationLocked=" + rotationLocked);
    }
    
    private PickerManager.PickerListener mPickerListener = new PickerManager.PickerListener() {
        @Override
        public boolean onCameraPicked(int cameraId) {
            Log.i(TAG, "onCameraPicked(" + cameraId + ") mPaused=" + mPaused
                    + " mPendingSwitchCameraId=" + mPendingSwitchCameraId);
            if (mPaused
                    || mPendingSwitchCameraId != UNKNOWN
                    || !ModeChecker.getModePickerVisible(CameraActivity.this, cameraId,
                            getCurrentMode()) || !mCameraDeviceCtrl.isCameraOpened()) {
                return false;
            }
            
            // Here we always return false for switchCamera will change
            // preference after real switching.
            int frontCameraId = CameraHolder.instance().getFrontCameraId();
            if (!mModuleManager.switchDevice() && isDualCameraDeviceEnable() 
                    && frontCameraId != UNKNOWN) {
                return false;
            }
            
            // Disable all camera controls.
            //setCameraState(STATE_SWITCHING_CAMERA);
            // We need to keep a preview frame for the animation before
            // releasing the camera. This will trigger onPreviewTextureCopied.
            // mRendererManager.copyTexture();
            //mMainHandler.sendEmptyMessage(MSG_SWITCH_CAMERA);
            mCameraAppUi.setViewState(ViewState.VIEW_STATE_CAMERA_CLOSED);
            mMainHandler.obtainMessage(MSG_SWITCH_CAMERA, cameraId, 0).sendToTarget();
            mPendingSwitchCameraId = cameraId;
            return false;
        }
        
        @Override
        public boolean onSlowMotionPicked(String turnon) {
            mCameraAppUi.resetSettings();
            mISettingCtrl.onSettingChanged(SettingConstants.KEY_SLOW_MOTION, turnon);
            mMainHandler.sendEmptyMessage(MSG_APPLY_PARAMETERS_WHEN_IDEL);
            return true;
        }
        
        @Override
        public boolean onHdrPicked(String value) {
            Log.i(TAG, "[onHdrPicked], value:" + value);
            mCameraActor.stopPreview();
            mISettingCtrl.onSettingChanged(SettingConstants.KEY_HDR, value);
            if ("on".equals(value)) {
                mCameraAppUi.showInfo(getString(R.string.hdr_guide_capture), SHOW_INFO_LENGTH_LONG);
            }
            // open/close hdr need to stop/start preview.
            mCameraDeviceCtrl.applyParameters(true);
            return true;
        }
        
        @Override
        public boolean onGesturePicked(String value) {
            Log.i(TAG, "[onGesturePicked], value:" + value);
            mISettingCtrl.onSettingChanged(SettingConstants.KEY_GESTURE_SHOT, value);
            mCameraDeviceCtrl.applyParameters(false);
            return true;
        }
        
        @Override
        public boolean onSmilePicked(String value) {
            Log.i(TAG, "[onGesturePicked], value:" + value);
            mISettingCtrl.onSettingChanged(SettingConstants.KEY_SMILE_SHOT, value);
            mCameraDeviceCtrl.applyParameters(false);
            return true;
        }
        
        @Override
        public boolean onFlashPicked(String flashMode) {
            mISettingCtrl.onSettingChanged(SettingConstants.KEY_FLASH, flashMode);
            mMainHandler.sendEmptyMessage(MSG_APPLY_PARAMETERS_WHEN_IDEL);
            return true;
        }
        
        @Override
        public boolean onStereoPicked(boolean stereoType) {
            // in n3d mode, press home key to exit camera, press camera icon to
            // launch camera and click 2d/3d switch icon quickly
            if (mPaused || mPendingSwitchCameraId != UNKNOWN ) {
                return false;
            }
            // switch 2d <---> 3d mode
            mIsStereoMode = !SettingUtils.readPreferredCamera3DMode(mPreferences).equals(
                    SettingUtils.STEREO3D_ENABLE);
            SettingUtils.writePreferredCamera3DMode(mPreferences,
                    mIsStereoMode ? SettingUtils.STEREO3D_ENABLE : SettingUtils.STEREO3D_DISABLE);
            
            if (mModePicker != null) {
                mModePicker.setCurrentMode(getCurrentMode());
            }
            mCameraAppUi.refreshModeRelated();
            // mSettingManager.reInflate();
            
            return true;
        }
        
        @Override
        public boolean onModePicked(int mode, String value, ListPreference preference) {
            mModePicker.setModePreference(preference);
            // when click hdr, smile shot, the ModePicker view should be
            // disabled during changed mode
            mModePicker.setEnabled(false);
            if (getCurrentMode() == mode) {
                // if current mode is HDR or Smile shot, then should change to
                // photo mode.
                mModePicker.setCurrentMode(ModePicker.MODE_PHOTO);
            } else {
                // if current mode is not HDR or Smile shot, it must be photo
                // mode, then
                // set current mode as HDR or Smile shot.
                mModePicker.setCurrentMode(mode);
            }
            return true;
        }
    };
    
    public boolean isSwitchingCamera() {
        return mPendingSwitchCameraId != UNKNOWN;
    }
    
    public boolean isNonePickIntent() {
        return PICK_TYPE_NORMAL == mPickType;
    }
    
    public boolean isImageCaptureIntent() {
        return PICK_TYPE_PHOTO == mPickType;
    }
    
    public boolean isVideoCaptureIntent() {
        return PICK_TYPE_VIDEO == mPickType;
    }
    
    public boolean isVideoWallPaperIntent() {
        return PICK_TYPE_WALLPAPER == mPickType;
    }
    
    public boolean isStereo3DImageCaptureIntent() {
        return PICK_TYPE_STEREO3D == mPickType;
    }
    
    public float getWallpaperPickAspectio() {
        return mWallpaperAspectio;
    }
    
    public boolean isQuickCapture() {
        return mQuickCapture;
    }
    
    public Uri getSaveUri() {
        return mSaveUri;
    }
    
    @Override
    protected PreviewSurfaceView getPreviewSurfaceView() {
        return (mCameraDeviceCtrl == null)? null : mCameraDeviceCtrl.getSurfaceView();
    }
    
    public String getCropValue() {
        return mCropValue;
    }
    
    public boolean isDualCameraDeviceEnable() {
        return isPIPMode(getCurrentMode());
    }
    
    public void setResultExAndFinish(int resultCode) {
        setResultEx(resultCode);
        finish();
        clearUserSettings();
    }
    
    public void setResultExAndFinish(int resultCode, Intent data) {
        setResultEx(resultCode, data);
        finish();
        clearUserSettings();
    }
    
    public boolean isVideoMode() {
        Log.d(TAG, "isVideoMode() getCurrentMode()=" + getCurrentMode());
        return (ModePicker.MODE_VIDEO == getCurrentMode()
                || ModePicker.MODE_VIDEO_3D == getCurrentMode() || ModePicker.MODE_VIDEO_PIP == getCurrentMode());
    }
    
    private void notifyOrientationChanged() {
        Log.i(TAG, "[notifyOrientationChanged] mOrientationCompensation="
                + mOrientationCompensation + ", mDisplayRotation=" + mDisplayRotation);
        for (OnOrientationListener listener : mOrientationListeners) {
            if (listener != null) {
                listener.onOrientationChanged(mOrientationCompensation);
            }
        }
    }
    
    private class MyOrientationEventListener extends OrientationEventListener {
        private boolean mLock = false;
        private int mRestoreOrientation;
        
        public MyOrientationEventListener(Context context) {
            super(context);
        }
        
        public void setLock(boolean lock) {
            mLock = lock;
        }
        
        public void restoreOrientation() {
            onOrientationChanged(mRestoreOrientation);
        }
        
        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) {
                Log.w(TAG, "[onOrientationChanged]orientation is ORIENTATION_UNKNOWN,return.");
                return;
            }
            int newOrientation = Util.roundOrientation(orientation, mRestoreOrientation);
            
            // Need Check the if
            if (FeatureSwitcher.isSmartBookEnabled() && isAppGuideFinished() && isFullScreen()) {
                if (isLandScape()) {
                    newOrientation = 270;
                }
                mOtherDeviceConectedManager.onOrientationChanged(newOrientation);
            }
            
            if (!mLock) {
                updateOrientation(newOrientation);
            }
            
            if (mRestoreOrientation != newOrientation) {
                mRestoreOrientation = newOrientation;
                mModuleManager.onOrientationChanged(mRestoreOrientation);
            }
            
        }
        
        private void updateOrientation(int orientation) {
            int newDisplayRotation = Util.getDisplayRotation(CameraActivity.this);
            if (mOrientation == orientation && newDisplayRotation == mDisplayRotation) {
                return;
            }
            Log.d(TAG, "[updateOrientation]orientation:" + orientation + ",mOrientation:"
                    + mOrientation + ",newDisplayRotation:" + newDisplayRotation
                    + ",mDisplayRotation:" + mDisplayRotation);
            if (newDisplayRotation != mDisplayRotation) {
                mDisplayRotation = newDisplayRotation;
                mCameraDeviceCtrl.setDisplayOrientation();
            }
            
            mOrientation = orientation;
            updateCompensation(mOrientation);
            mCameraDeviceCtrl.onOrientationChanged(mOrientation);

        }
        
        private void updateCompensation(int orientation) {
            int orientationCompensation = (orientation + Util
                    .getDisplayRotation(CameraActivity.this)) % 360;
            if (mOrientationCompensation != orientationCompensation) {
                Log.d(TAG, "[updateCompensation] mCompensation:" + mOrientationCompensation
                        + ", compensation:" + orientationCompensation);
                mOrientationCompensation = orientationCompensation;
                mModuleManager.onCompensationChanged(mOrientationCompensation);
                notifyOrientationChanged();
            }
        }
    }
    
    // / @}
    
    // / M: for pick logic @{
    /**
     * An unpublished intent flag requesting to start recording straight away
     * and return as soon as recording is stopped. TODO: consider publishing by
     * moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE = "android.intent.extra.quickCapture";
    private static final String EXTRA_VIDEO_WALLPAPER_IDENTIFY = "identity";// String
    private static final String EXTRA_VIDEO_WALLPAPER_RATION = "ratio";// float
    private static final String EXTRA_VIDEO_WALLPAPER_IDENTIFY_VALUE = "com.mediatek.vlw";
    private static final String EXTRA_PHOTO_CROP_VALUE = "crop";
    private static final String ACTION_STEREO3D = "android.media.action.IMAGE_CAPTURE_3D";
    private static final float WALLPAPER_DEFAULT_ASPECTIO = 1.2f;
    
    private static final int PICK_TYPE_NORMAL = 0;
    private static final int PICK_TYPE_PHOTO = 1;
    private static final int PICK_TYPE_VIDEO = 2;
    private static final int PICK_TYPE_WALLPAPER = 3;
    private static final int PICK_TYPE_STEREO3D = 4;
    private int mPickType;
    private boolean mQuickCapture;
    private float mWallpaperAspectio;
    private Uri mSaveUri;
    private long mLimitedSize;
    private String mCropValue;
    private int mLimitedDuration;
    
    // add for MMS ->VideoCamera->playVideo,and then pause the
    // video,at this time ,we don't want share the video
    public boolean mCanShowVideoShare = true;
    public static final String CAN_SHARE = "CanShare";
    
    private void parseIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        if (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action)) {
            mPickType = PICK_TYPE_PHOTO;
        } else if (EXTRA_VIDEO_WALLPAPER_IDENTIFY_VALUE.equals(intent
                .getStringExtra(EXTRA_VIDEO_WALLPAPER_IDENTIFY))) {
            mWallpaperAspectio = intent.getFloatExtra(EXTRA_VIDEO_WALLPAPER_RATION,
                    WALLPAPER_DEFAULT_ASPECTIO);
            intent.putExtra(EXTRA_QUICK_CAPTURE, true);
            mPickType = PICK_TYPE_WALLPAPER;
        } else if (MediaStore.ACTION_VIDEO_CAPTURE.equals(action)) {
            mPickType = PICK_TYPE_VIDEO;
        } else if (ACTION_STEREO3D.equals(action)) {
            mPickType = PICK_TYPE_STEREO3D;
        } else {
            mPickType = PICK_TYPE_NORMAL;
        }
        if (mPickType != PICK_TYPE_NORMAL) {
            mQuickCapture = intent.getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
            mSaveUri = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
            mLimitedSize = intent.getLongExtra(MediaStore.EXTRA_SIZE_LIMIT, 0L);
            mCropValue = intent.getStringExtra(EXTRA_PHOTO_CROP_VALUE);
            mLimitedDuration = intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
            mIsAppGuideFinished = true;
        }
        Log.i(TAG, "parseIntent() mPickType=" + mPickType + ", mQuickCapture=" + mQuickCapture
                + ", mSaveUri=" + mSaveUri + ", mLimitedSize=" + mLimitedSize + ", mCropValue="
                + mCropValue + ", mLimitedDuration=" + mLimitedDuration);
        if (true) {
            Log.d(TAG, "parseIntent() action=" + intent.getAction());
            Bundle extra = intent.getExtras();
            if (extra != null) {
                mCanShowVideoShare = extra.getBoolean(CAN_SHARE, true);
                for (String key : extra.keySet()) {
                    Log.v(TAG, "parseIntent() extra[" + key + "]=" + extra.get(key));
                }
            }
            if (intent.getCategories() != null) {
                for (String key : intent.getCategories()) {
                    Log.v(TAG, "parseIntent() getCategories=" + key);
                }
            }
            Log.v(TAG, "parseIntent() data=" + intent.getData());
            Log.v(TAG, "parseIntent() flag=" + intent.getFlags());
            Log.v(TAG, "parseIntent() package=" + intent.getPackage());
            Log.v(TAG, "mCanShowVideoShare = " + mCanShowVideoShare);
        }
    }
    
    // / M: We will restart activity for changed default path. @{
    private boolean mForceFinishing; // forcing finish
    
    private boolean isMountPointChanged() {
        boolean changed = false;
        String mountPoint = Storage.getMountPoint();
        Storage.updateDefaultDirectory();
        if (!mountPoint.equals(Storage.getMountPoint())) {
            changed = true;
        }
        Log.d(TAG, "isMountPointChanged() old=" + mountPoint + ", new=" + Storage.getMountPoint()
                + ", return " + changed);
        return changed;
    }
    
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "mReceiver.onReceive(" + intent + ")");
            String action = intent.getAction();
            if (action == null) {
                Log.d(TAG, "[mReceiver.onReceive] action is null");
                return;
            }
            
            switch (action) {
            
            case Intent.ACTION_MEDIA_EJECT:
                if (isSameStorage(intent)) {
                    Storage.setStorageReady(false);
                    mCameraActor.onMediaEject();
                }
                break;
            
            case Intent.ACTION_MEDIA_UNMOUNTED:
                if (isSameStorage(intent)) {
                    String internal = Storage.getInternalVolumePath();
                    if (internal != null) {
                        if (!Storage.updateDirectory(internal)) {
                            setPath(Storage.getCameraScreenNailPath());
                        }
                    } 
                } else {
                    if (!FeatureSwitcher.is2SdCardSwapSupport()) {
                        updateStorageDirectory();
                    }
                }
                mCameraAppUi.clearRemainAvaliableSpace();
                mCameraAppUi.showRemainHint();
                break;
            
            case Intent.ACTION_MEDIA_MOUNTED:
                updateStorageDirectory();
                if (isSameStorage(intent)) {
                    Storage.setStorageReady(true);
                    mCameraAppUi.clearRemainAvaliableSpace();
                    mCameraAppUi.showRemainHint();
                }
                break;
            
            case Intent.ACTION_MEDIA_CHECKING:
                if (isSameStorage(intent)) {
                    mCameraAppUi.clearRemainAvaliableSpace();
                    mCameraAppUi.showRemainHint();
                }
                break;
            
            case Intent.ACTION_MEDIA_SCANNER_STARTED:
                if (!FeatureSwitcher.is2SdCardSwapSupport()) {
                    updateStorageDirectory();
                }
                if (isSameStorage(intent.getData())) {
                    mCameraAppUi.showToast(R.string.wait);
                }
                break;
            
            case Intent.ACTION_MEDIA_SCANNER_FINISHED:
                if (isSameStorage(intent.getData())) {
                    mCameraAppUi.clearRemainAvaliableSpace();
                    mCameraAppUi.showRemainHint();
                    mCameraAppUi.forceThumbnailUpdate();
                }
                break;
            
            default:
                break;
            }
        }
    };
    
    private void updateStorageDirectory() {
        if (!Storage.updateDefaultDirectory()) {
            setPath(Storage.getCameraScreenNailPath());
        }
    }
    
    private boolean isSameStorage(Intent intent) {
        StorageVolume storage = (StorageVolume) intent
                .getParcelableExtra(StorageVolume.EXTRA_STORAGE_VOLUME);
        
        boolean same = false;
        String mountPoint = null;
        String intentPath = null;
        if (storage != null) {
            mountPoint = Storage.getMountPoint();
            intentPath = storage.getPath();
            if (mountPoint != null && mountPoint.equals(intentPath)) {
                same = true;
            }
        }
        Log.d(TAG, "isSameStorage() mountPoint=" + mountPoint + ", intentPath=" + intentPath
                + ", return " + same);
        return same;
    }
    
    private boolean isSameStorage(Uri uri) {
        if (!Storage.updateDefaultDirectory()) {
            Log.i(TAG, "isSameStorage(uri)/same= updateDefaultDirectory");
            setPath(Storage.getCameraScreenNailPath());
        }
        boolean same = false;
        String mountPoint = null;
        String intentPath = null;
        if (uri != null) {
            mountPoint = Storage.getMountPoint();
            intentPath = uri.getPath();
            if (mountPoint != null && mountPoint.equals(intentPath)) {
                same = true;
            }
        }
        Log.d(TAG, "isSameStorage(" + uri + ") mountPoint=" + mountPoint + ", intentPath="
                + intentPath + ", return " + same);
        mCameraAppUi.forceThumbnailUpdate();
        return same;
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
        registerReceiver(mReceiver, intentFilter);
    }
    
    private void uninstallIntentFilter() {
        unregisterReceiver(mReceiver);
    }
    
    private void clearUserSettings() {
        Log.d(TAG, "clearUserSettings() isFinishing()=" + isFinishing());
        if (mISettingCtrl != null && isFinishing()) {
            mISettingCtrl.resetSetting();
        }
    }
    
    // / M: for other things @{
    private void addIdleHandler() {
        MessageQueue queue = Looper.myQueue();
        queue.addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                Storage.ensureOSXCompatible();
                return false;
            }
        });
    }
    
    // / @}
    
    private boolean isAppGuideFinished() {
        return mIsAppGuideFinished;
    }
    
    private void setAppGuideFinished(boolean mAppGuideFinished) {
        this.mIsAppGuideFinished = mAppGuideFinished;
    }
    
    // 3D Related
    public boolean isStereoMode() {
        return mIsStereoMode;
    }
    
    // SurfaceView For Native 3D
    // For SurfaceTexture need GPU,Native Need GPU,Then GPU crushed!
    
    private void initializeStereo3DMode() {
        if (isStereo3DImageCaptureIntent()) {
            mIsStereoMode = true;
            SettingUtils.writePreferredCamera3DMode(mPreferences, SettingUtils.STEREO3D_ENABLE);
        } else {
            // when initialize we always write disable state
            mIsStereoMode = false;
            SettingUtils.writePreferredCamera3DMode(mPreferences, SettingUtils.STEREO3D_DISABLE);
        }
    }
    
    private boolean isFaceBeautyEnable() {
        String facebautyMode = getPreferences().getString(SettingConstants.KEY_MULTI_FACE_BEAUTY,
                getResources().getString(R.string.face_beauty_single_mode));
        // Log.i(TAG, "facebautyMode = " + facebautyMode);
        return !getResources().getString(R.string.pref_face_beauty_mode_off).equals(facebautyMode);
    }
    
    private IExternalDeviceCtrl.Listener mListener = new IExternalDeviceCtrl.Listener() {
        
        @Override
        public void onStateChanged(boolean enabled) {
            Log.i(TAG, "[onStateChanged] enable = " + enabled);
            mModuleManager.setVideoRecorderEnable(!enabled);
        }
    };
    
    //**********************************************************TODO PIP and stereo
    private boolean mIsStereoToVideoMode;
    public boolean isNeedOpenStereoCamera() {
        boolean enable = false;
        enable = (SettingUtils.readPreferredStereoCamera(mPreferences))
                .equals(SettingUtils.STEREO_CAMERA_ON)
                || getCurrentMode() == ModePicker.MODE_STEREO_CAMERA;
        Log.d(TAG, "[isNeedOpenStereoCamera] mIsStereoToVideoMode:" + mIsStereoToVideoMode
                + ",mode:" + getCurrentMode() + ",enable:" + enable);
        if (mIsModeChanged
                && getListPreference(SettingConstants.ROW_SETTING_FAST_AF) != null
                && "on".equals(getListPreference(SettingConstants.ROW_SETTING_FAST_AF).getValue())) {
            enable = true;
        }
        if (mIsStereoToVideoMode) {
            enable = false;
            mIsStereoToVideoMode = false;
        }
        if (getCurrentMode() == ModePicker.MODE_PHOTO_PIP) {
            enable = false;
        }
        enable = enable && isNonePickIntent();
        Log.i(TAG, "needOpenStereoCamera enable = " + enable);
        return enable;
    }
    
    private boolean isPIPMode(int mode) {
        return mode == ModePicker.MODE_VIDEO_PIP || mode == ModePicker.MODE_PHOTO_PIP;
    }
    
    private boolean isPIPModeSwitch(int lastMode, int newMode) {
        return (isPIPMode(lastMode) && !isPIPMode(newMode))
                || (!isPIPMode(lastMode) && isPIPMode(newMode));
    }
    
    private boolean isRefocusSwitchNormal(int lastMode, int newMode) {
        return (lastMode == ModePicker.MODE_STEREO_CAMERA && newMode != ModePicker.MODE_VIDEO)
                || (lastMode != ModePicker.MODE_STEREO_CAMERA
                && newMode == ModePicker.MODE_STEREO_CAMERA);
    }
    
    private boolean isRefocusSwitchVideo(int lastMode, int newMode) {
        return lastMode == ModePicker.MODE_STEREO_CAMERA && newMode == ModePicker.MODE_VIDEO;
    }
    
    private boolean isFastAfEnabled() {
        return ParametersHelper.isDepthAfSupported(mCameraDeviceCtrl.getParametersExt())
                && "off".equals(getListPreference(SettingConstants.KEY_FAST_AF).getValue());
    }
    
    private void initializeDualCamera() {
        Log.i(TAG, "initializeDualCamera");
        int cameraId = CameraHolder.instance().getBackCameraId();
        mCameraDeviceCtrl.openStereoCamera(cameraId);
        mPendingSwitchCameraId = cameraId;
    }
    
    private void doPIPModeChanged(int cameraId) {
        Log.i(TAG, "doPIPModeChanged");
        // when switch to open main and sub camera, should close first
        mCameraAppUi.collapseViewManager(true);
        clearFocusAndFace();
        mCameraDeviceCtrl.unInitializeFocusManager();

        // here set these variables null to initialize them again.
        mPreferences.setLocalId(CameraActivity.this, cameraId);
        SettingUtils.upgradeLocalPreferences(mPreferences.getLocal());
        SettingUtils.writePreferredCameraId(mPreferences, cameraId);
        
        mCameraDeviceCtrl.openCamera(cameraId);
    }
    
    private boolean isLandScape() {
        if (FeatureSwitcher.isSmartBookEnabled()) {
            return ModePicker.MODE_MOTION_TRACK == getCurrentMode()
                    || ModePicker.MODE_MAV == getCurrentMode();
        } else {
            return false;
        }
    }
    
    //****************************************************************
    public CameraManager.CameraProxy getCameraDevice() {
        return mCameraDeviceCtrl.getCameraDevice();
    }
    
    public FocusManager getFocusManager() {
        return mCameraDeviceCtrl.getFocusManager();
    }
    
    public Parameters getParameters() {
        return mCameraDeviceCtrl.getParameters();
    }
    
    public Parameters getTopParameters() {
        return mCameraDeviceCtrl.getTopParameters();
    }
    
    public int getCameraId() {
        return mCameraDeviceCtrl.getCameraId();
    }
    
    //for PIP
    public int getOriCameraId() {
        return mOriCameraId;
    }
    
    public int getTopCameraId() {
        return mCameraDeviceCtrl.getTopCameraId();
    }
    
    public void applyParameterForCapture(final SaveRequest request) {
        mCameraDeviceCtrl.applyParameterForCapture(request);
    }
    
    public void applyParameterForFocus(final boolean setArea) {
        mCameraDeviceCtrl.applyParameterForFocus(setArea);
    }
    
    public int getDisplayOrientation() {
        return mCameraDeviceCtrl.getDisplayOrientation();
    }
    
    public void startAsyncZoom(final int zoomValue) {
        mCameraDeviceCtrl.startAsyncZoom(zoomValue);
    }
    
    public boolean isCameraIdle() {
        return mCameraDeviceCtrl.isCameraIdle();
    }

    private class ModuleCtrlImpl implements IModuleCtrl {
        private CameraActivity mCamera;
        
        public ModuleCtrlImpl(CameraActivity camera) {
            mCamera = camera;
        }
        
        @Override
        public boolean applyFocusParameters(boolean setArea) {
            mCameraDeviceCtrl.applyParameterForFocus(setArea);
            return true;
        }
        
        @Override
        public int getOrientation() {
            return mOrientation;
        }
        
        @Override
        public int getDisplayOrientation() {
            return mCameraDeviceCtrl.getDisplayOrientation();
        }
        
        @Override
        public int getDisplayRotation() {
            return mDisplayRotation;
        }
        
        @Override
        public int getOrientationCompensation() {
            return mOrientationCompensation;
        }
        
        @Override
        public boolean lockOrientation() {
            Log.d(TAG, "[lockOrientation]...");
            mCamera.setOrientation(true, OrientationEventListener.ORIENTATION_UNKNOWN);
            return true;
        }
        
        @Override
        public boolean unlockOrientation() {
            Log.d(TAG, "[unlockOrientation]...");
            mCamera.setOrientation(false, OrientationEventListener.ORIENTATION_UNKNOWN);
            return true;
        }
        
        @Override
        public void setOrientation(boolean lock, int orientation) {
            Log.d(TAG, "[setOrientation] lock = " + lock + ", orientation = "
                    + orientation);
            mCamera.setOrientation(lock, orientation);
        }
        
        @Override
        public boolean enableOrientationListener() {
            mOrientationListener.enable();
            return true;
        }
        
        @Override
        public boolean disableOrientationListener() {
            mOrientationListener.disable();
            return true;
        }
        
        public Location getLocation() {
            return mLocationManager.getCurrentLocation();
        }
        
        public Uri getSaveUri() {
            return mSaveUri;
        }
        
        public String getCropValue() {
            return mCropValue;
        }
        
        public void setResultAndFinish(int resultCode) {
            mCamera.setResultExAndFinish(resultCode);
        }
        
        public void setResultAndFinish(int resultCode, Intent data) {
            mCamera.setResultExAndFinish(resultCode, data);
        }
        
        public boolean isSecureCamera() {
            return mSecureCamera;
        }
        
        public boolean isImageCaptureIntent() {
            return PICK_TYPE_PHOTO == mPickType;
        }
        
        @Override
        public void startFaceDetection() {
            mCameraActor.startFaceDetection();
        }
        
        @Override
        public void stopFaceDetection() {
            mCameraActor.stopFaceDetection();
        }
        
        @Override
        public boolean isVideoCaptureIntent() {
            return PICK_TYPE_VIDEO == mPickType;
        }
        
        @Override
        public boolean isNonePickIntent() {
            return PICK_TYPE_NORMAL == mPickType;
        }
        
        @Override
        public Intent getIntent() {
            return mCamera.getIntent();
        }
        
        @Override
        public boolean isQuickCapture() {
            return mQuickCapture;
        }
        
        @Override
        public void backToLastMode() {
            mCameraDeviceCtrl.waitCameraStartUpThread(false);
            mModePicker.setCurrentMode(mPrevMode);
        }
        
        @Override
        public void backToCallingActivity(int resultCode, Intent data) {
            setResultExAndFinish(resultCode, data);
        }
        
        @Override
        public boolean getSurfaceTextureReady() {
            //TODO
            return true;
        }
        
        @Override
        public ComboPreferences getComboPreferences() {
            return mCamera.getPreferences();
        }
        
        @Override
        public void switchCameraDevice() {
            mCameraDeviceCtrl.doSwitchCameraDevice();
        }
        
        @Override
        public CameraModeType getNextMode() {
            return mCameraActor.getCameraModeType(mNextMode);
        }
        
        @Override
        public CameraModeType getPrevMode() {
            return mCameraActor.getCameraModeType(mPrevMode);
        }
        
        @Override
        public boolean setFaceBeautyEnalbe(boolean isEnable) {
            mFrameManager.enableFaceBeauty(isEnable);
            return isEnable;
        }
        
        @Override
        public boolean initializeFrameView(boolean isEnableObject) {
            mFrameManager.initializeFrameView(isEnableObject);
            return false;
        }
        
        @Override
        public boolean clearFrameView() {
            getFrameView().clear();
            return true;
        }
        
        @Override
        public void setFaces(Face[] faces) {
            getFrameView().setFaces(faces);
        }
        
        @Override
        public Surface getPreviewSurface() {
            return mCameraDeviceCtrl.getSurfaceView().getHolder().getSurface();
        }
    }
}
