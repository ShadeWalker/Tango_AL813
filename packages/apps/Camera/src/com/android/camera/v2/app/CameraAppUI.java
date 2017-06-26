/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.v2.app;

import com.android.camera.R;
import com.android.camera.v2.app.AppController.OkCancelClickListener;
import com.android.camera.v2.app.AppController.PlayButtonClickListener;
import com.android.camera.v2.app.AppController.RetakeButtonClickListener;
import com.android.camera.v2.app.AppController.ShutterEventsListener;
import com.android.camera.v2.app.PreviewManager.SurfaceCallback;
import com.android.camera.v2.app.SettingAgent.SettingChangedListener;
import com.android.camera.v2.ui.PreviewStatusListener;
import com.android.camera.v2.ui.PreviewStatusListener.OnGestureListener;
import com.android.camera.v2.ui.PreviewStatusListener.OnPreviewTouchedListener;
import com.android.camera.v2.uimanager.IndicatorManager;
import com.android.camera.v2.uimanager.InfoManager;
import com.android.camera.v2.uimanager.ModePicker;
import com.android.camera.v2.uimanager.ModePicker.OnModeChangedListener;
import com.android.camera.v2.uimanager.PickerManager;
import com.android.camera.v2.uimanager.RemainingManager;
import com.android.camera.v2.uimanager.ReviewManager;
import com.android.camera.v2.uimanager.ReviewManager.OnPlayButtonClickListener;
import com.android.camera.v2.uimanager.ReviewManager.OnRetakeButtonClickListener;
import com.android.camera.v2.uimanager.RotateDialog;
import com.android.camera.v2.uimanager.RotateProgress;
import com.android.camera.v2.uimanager.SettingManager;
import com.android.camera.v2.uimanager.PickerManager.OnPickedListener;
import com.android.camera.v2.uimanager.SettingManager.OnSettingChangedListener;
import com.android.camera.v2.uimanager.SettingManager.OnSettingStatusListener;
import com.android.camera.v2.uimanager.ShutterManager;
import com.android.camera.v2.uimanager.ShutterManager.OnOkCancelButtonClickListener;
import com.android.camera.v2.uimanager.ShutterManager.OnShutterButtonListener;
import com.android.camera.v2.uimanager.ThumbnailManager;
import com.android.camera.v2.uimanager.ThumbnailManager.OnThumbnailClickListener;
import com.android.camera.v2.uimanager.preference.PreferenceManager;
import com.android.camera.v2.util.CameraUtil;
import com.android.camera.v2.util.SettingKeys;

import java.lang.Override;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;


/**
 * CameraAppUI centralizes control of views shared across modules. Whereas module
 * specific views will be handled in each Module UI. For example, we can now
 * bring the flash animation and capture animation up from each module to app
 * level, as these animations are largely the same for all modules.
 *
 * This class also serves to disambiguate touch events. It recognizes all the
 * swipe gestures that happen on the preview by attaching a touch listener to
 * a full-screen view on top of preview TextureView. Since CameraAppUI has knowledge
 * of how swipe from each direction should be handled, it can then redirect these
 * events to appropriate recipient views.
 */
public class CameraAppUI implements SurfaceCallback {
    private static final String      TAG = "CameraAppUI";
    private final AppController      mAppController;
    private Activity                 mCameraActivity;
    private FrameLayout              mModuleUI;
    private MainHandler              mMainHandler;

    
    // Preview is fully visible.
    public static final int          VISIBILITY_VISIBLE = 0;
    // Preview is covered by e.g. the transparent mode drawer. */
    public static final int          VISIBILITY_COVERED = 1;
    // Preview is fully hidden, e.g. by the filmstrip / gallery. */
    public static final int          VISIBILITY_HIDDEN = 2;
    private int                      mPreviewVisibility = VISIBILITY_HIDDEN;
    
    // ui managers parent view groups
    private ViewGroup                mViewLayerBottom;
    private ViewGroup                mViewLayerNormal;
    private ViewGroup                mViewLayerTop;
    private ViewGroup                mViewLayerShutter;
    private ViewGroup                mViewLayerSetting;
    private ViewGroup                mViewLayerOverlay;
    
    private int                      mOrientation;
    private int                      mOrientationCompensation;
    // gestures
    private final GestureManager     mGestureManager;
    private boolean                  mSwipeEnabled = true;
    private boolean                  mStopShowCommonUi = false;
    
    private static final int         DELAY_MSG_SHOW_INDICATOR_UI = 3000;
    private static final int         DOWN_SAMPLE_FACTOR = 4;
    // InfoManager, RemainingManager,IndicatorManager
    // interact MSG
    private static final int         MSG_SHOW_INFO_UI         = 1000;
    private static final int         MSG_SHOW_INDICATOR_UI    = 1001;
    private static final int         MSG_SHOW_REMAINING_UI    = 1002;
    private static final int         MSG_HIDE_INFO_UI         = 1003;
    // default on screen view show time duration
    private static final int         MSG_DEFAULT_SHOW_ONSCREEN_VIEW_DURATION = 3 * 1000;
    // Ui Managers
    private ShutterManager           mShutterManager;
    private ShutterEventsListener    mPhotoShutterListener;
    private SettingManager           mSettingManager;
    private PickerManager            mPickerManager;
    private ModePicker               mModePicker;
    private IndicatorManager         mIndicatorManager;
    private RemainingManager         mRemainingManager;
    private ThumbnailManager         mThumbnailManager;
    private ReviewManager            mReviewManager;
    private RotateDialog             mRotateDialog;
    private RotateProgress           mRotateProgress;
    private PreferenceManager        mPreferenceManager;
    private InfoManager              mInfoManager;
    
    private SettingAgent             mSettingAgent;
    
    private OnShutterButtonListener  mPhotoShutterCallback = new OnShutterButtonListener() {
        @Override
        public void onLongPressed() {
            if (mPhotoShutterListener != null) {
                mPhotoShutterListener.onShutterLongPressed();
            }
        }
        @Override
        public void onFocused(boolean pressed) {
            if (pressed) {
                if (mPhotoShutterListener != null) {
                    mPhotoShutterListener.onShutterPressed();
                }
            } else {
                if (mPhotoShutterListener != null) {
                    mPhotoShutterListener.onShutterReleased();
                }
            }
        }
        @Override
        public void onPressed() {
            if (mSettingManager != null) {
                mSettingManager.collapse(true);
            }
            if (mPhotoShutterListener != null) {
                mPhotoShutterListener.onShutterClicked();
            }
        }
    };
    
    private ShutterEventsListener    mVideoShutterListener;
    private OnShutterButtonListener  mVideoShutterCallback = new OnShutterButtonListener() {
        @Override
        public void onLongPressed() {
            if (mVideoShutterListener != null) {
                mVideoShutterListener.onShutterLongPressed();
            }
        }
        @Override
        public void onFocused(boolean pressed) {
            if (pressed) {
                if (mVideoShutterListener != null) {
                    mVideoShutterListener.onShutterPressed();
                }
            } else {
                if (mVideoShutterListener != null) {
                    mVideoShutterListener.onShutterReleased();
                }
            }
        }
        @Override
        public void onPressed() {
            if (mVideoShutterListener != null) {
                mVideoShutterListener.onShutterClicked();
            }
            if (mSettingManager != null) {
                mSettingManager.collapse(true);
            }
        }
    };
    
    private OkCancelClickListener         mOkCancelClickListener;
    private OnOkCancelButtonClickListener mOnOkCancelButtonClickListener = 
            new OnOkCancelButtonClickListener() {
        
        @Override
        public void onOkClick() {
            Log.i(TAG, "[onOkClick]...");
            mOkCancelClickListener.onOkClick();
        }
        
        @Override
        public void onCancelClick() {
            Log.i(TAG, "[onCancelClick]...");
            mOkCancelClickListener.onCancelClick();
        }
    };
    
    private OnPreviewTouchedListener mOnPreviewTouchedListener = new OnPreviewTouchedListener() {
        
        @Override
        public boolean onPreviewTouched() {
            return mSettingManager.collapse(false);
        }
    }; 
    
    
    private OnSettingChangedListener mSettingChangedListener = new OnSettingChangedListener() {
        
        @Override
        public void onSettingChanged(String key, String value) {
            Log.i(TAG, "[onSettingChanged], key:" + key + ", value:" + value);
            mSettingAgent.doSettingChange(key, value);
        }
        
        @Override
        public void onSettingRestored() {
            Runnable runnable = new Runnable() {
                
                @Override
                public void run() {
                    if (mSettingManager != null) {
                        mSettingManager.collapse(true);
                    }
                    
                    if (mModePicker != null) {
                        mModePicker.restoreToNormalMode();
                    }
                    
                    if (mPreferenceManager != null) {
                        mPreferenceManager.restoreSetting();
                    }
                }
            };
            if (mRotateDialog != null) {
                mRotateDialog.showAlertDialog(null, mCameraActivity.getString(R.string.confirm_restore_message),
                        mCameraActivity.getString(android.R.string.cancel), null, 
                        mCameraActivity.getString(android.R.string.ok),
                        runnable);
            }
        }
    };
    
    private OnSettingStatusListener mOnSettingStatusListener = new OnSettingStatusListener() {

        @Override
        public void onShown() {
            Log.i(TAG, "[onShown]...");
            if (mModePicker != null) {
                mModePicker.hide();
            }
            
            if (mPickerManager != null) {
                mPickerManager.hide();
            }
            
            if (mThumbnailManager != null) {
                mThumbnailManager.hide();
            }
            
            mPreviewVisibility = VISIBILITY_COVERED;
            mAppController.onPreviewVisibilityChanged(mPreviewVisibility);
        }

        @Override
        public void onHidden() {
            Log.i(TAG, "[onHidden]...");
            if (mModePicker != null) {
                mModePicker.show();
            }
            
            if (mPickerManager != null) {
                mPickerManager.show();
            }
            
            if (mThumbnailManager != null) {
                mThumbnailManager.show();
            }
            
            mPreviewVisibility = VISIBILITY_VISIBLE;
            mAppController.onPreviewVisibilityChanged(mPreviewVisibility);
        }
    };
    
    private OnPickedListener mOnPickedListener = new OnPickedListener() {
        
        @Override
        public void onPicked(String key, String value) {
            Log.i(TAG, "[onPicked], key:" + key + ", value:" + value);
            // hdr is designed as a mode.
            if (SettingKeys.KEY_HDR.equals(key)) {
                Map<String, String> changedSettings = new HashMap<String, String>();
                changedSettings.put(key, value);
                mAppController.onModeChanged(changedSettings);
                mSettingAgent.doSettingChange(key, value);
            } else if (SettingKeys.KEY_CAMERA_ID.equals(key)) {
                mSettingManager.collapseImmediately();
                mIndicatorManager.hide();
                setAllCommonViewEnable(false);
                mAppController.onCameraPicked(value);
            } else {
                mSettingAgent.doSettingChange(key, value);
            }
        }
    };

    private OnModeChangedListener mOnModeChangedListener = new OnModeChangedListener() {
        
        @Override
        public void onModeChanged(Map<String, String> changedModes) {
            Log.i(TAG, "[onModeChanged], changedModes:" + changedModes);
            mAppController.onModeChanged(changedModes);
            mSettingAgent.doSettingChange(changedModes);
        }
        
        @Override
        public void onRestoreToNomalMode(Map<String, String> changedModes) {
            Log.i(TAG, "[onModeChanged], changedModes:" + changedModes);
            mAppController.onModeChanged(changedModes);
        }
    };
    
    private OnThumbnailClickListener mOnThumbnailClickListener = new OnThumbnailClickListener() {
        
        @Override
        public void onThumbnailClick() {
            mAppController.gotoGallery();
        }
    };
    
    private PlayButtonClickListener   mPlayButtonClickListener;
    private OnPlayButtonClickListener mOnPlayButtonClickListener = 
            new OnPlayButtonClickListener() {
        
        @Override
        public void onPlayButtonClick() {
            Log.i(TAG, "[onPlayButtonClick]...");
            mPlayButtonClickListener.onPlay();
        }
    };
    
    private RetakeButtonClickListener   mRetakeButtonClickListener;
    private OnRetakeButtonClickListener mOnRetakeButtonClickListener = 
            new OnRetakeButtonClickListener() {
        
        @Override
        public void onRetakeButtonClick() {
            Log.i(TAG, "[onRetakeButtonClick]...");
            mRetakeButtonClickListener.onRetake();
        }
    };
    
    // preview 
    private PreviewManager                         mPreviewManager;
    private PreviewStatusListener                  mPreviewStatusListener;
    private Surface                                mPreviewSurface;
    private int                                    mPreviewSurfaceWidth;
    private int                                    mPreviewSurfaceHeight;

    public CameraAppUI(AppController controller) {
        Log.i(TAG, "[CameraAppUI]+");
        mAppController      = controller;
        mCameraActivity     = mAppController.getActivity();
        mPreferenceManager  = mAppController.getPreferenceManager();
        mGestureManager     = mAppController.getGestureManager();
        mGestureManager.registerPreviewTouchListener(mOnPreviewTouchedListener);
        mPreviewManager     = mAppController.getPreviewManager();
        mMainHandler        = new MainHandler(mCameraActivity.getMainLooper());
    }

    @Override
    public void surfaceAvailable(Surface surface, int width, int height) {
        if (mPreviewStatusListener != null) {
            mPreviewStatusListener.surfaceAvailable(surface, width, height);
        }
    }

    @Override
    public void surfaceDestroyed(Surface surface) {
        if (mPreviewStatusListener != null) {
            mPreviewStatusListener.surfaceDestroyed(surface);
        }
    }

    @Override
    public void surfaceSizeChanged(Surface surface, int width, int height) {
        if (mPreviewStatusListener != null) {
            mPreviewStatusListener.surfaceSizeChanged(surface, width, height);
        }
    }

    /**********************************life cycle manager*********************************/
    public void init(View root, boolean isSecureCamera, boolean isCaptureIntent) {
        Log.i(TAG, "[init]+");
        initializeCommonUIManagers();
    }
    
    public void onCreate() {
        
    }
    
    public void onResume() {
        if (mThumbnailManager != null) {
            mThumbnailManager.onResume();
        }
    }
    
    public void onPause() {
        if (mThumbnailManager != null) {
            mThumbnailManager.onPause();
        }
        if (mSettingManager != null) {
            mSettingManager.collapse(true);
        }
        if (mIndicatorManager != null) {
            mIndicatorManager.hide();
        }
        if (mInfoManager != null) {
            mInfoManager.hide();
        }
    }
    
    public void onDestroy() {
        if (mThumbnailManager != null) {
            mThumbnailManager.onDestroy();
        }
    }
    
    public void onConfigurationChanged(Configuration newConfig) {
        if (mModePicker != null) {
            mModePicker.reInflate();
        }
        
        if (mShutterManager != null) {
            mShutterManager.reInflate();
        }
        
        if (mSettingManager != null) {
            mSettingManager.reInflate();
        }
        
        if (mPickerManager != null) {
            mPickerManager.reInflate();
        }
        
        if (mThumbnailManager != null) {
            mThumbnailManager.reInflate();
        }
        
        if (mIndicatorManager != null) {
            mIndicatorManager.reInflate();
        }
        
        if (mInfoManager != null) {
            mInfoManager.reInflate();
        }
        
        if (mRemainingManager != null) {
            mRemainingManager.reInflate();
        }
        mOrientationCompensation = (mOrientation + 
                CameraUtil.getDisplayRotation(mCameraActivity)) % 360;
        rotateLayers(mOrientationCompensation);
    }

    public void setSettingAgent(SettingAgent settingAgent) {
        mSettingAgent = settingAgent;
        mSettingAgent.registerSettingChangedListener(new SettingChangedListener() {
            // callback setting result after setting changed. 
            @Override
            public void onSettingResult(Map<String, String> values, 
                    Map<String, String> overrideValues) {
                if (mPreferenceManager != null) {
                    mPreferenceManager.updateSettingResult(values, overrideValues);
                }
                
                mMainHandler.post(new Runnable() {
                    
                    @Override
                    public void run() {
                        if (mSettingManager != null) {
                            mSettingManager.refresh();
                        }
                        
                        if (mIndicatorManager != null) {
                            mIndicatorManager.refresh();
                        }
                        
                        if (mPickerManager != null) {
                            mPickerManager.refresh();
                        }
                    }
                });
            }
        });
    }

    /**
     * Called when the back key is pressed.
     *
     * @return Whether the UI responded to the key event.
     */
    public boolean onBackPressed() {
        //call view manager's onBackPressed() order is need to considered.
        return mRotateProgress.onBackPressed() 
                || mRotateDialog.onBackPressed() || mSettingManager.onBackPressed();
    }

    public void setSwipeEnabled(boolean enabled) {
        //TODO consider remove this variable
        mSwipeEnabled = enabled;
        if (mGestureManager != null) {
            mGestureManager.setScrollEnable(enabled);
        }
    }
    
    //FIXME consider all common ui manager
    public void setAllCommonViewEnable(boolean enable) {
        Log.i(TAG, "[setAllCommonViewEnable], enable:" + enable);
        mModePicker.setEnable(enable);
        mSettingManager.setEnable(enable);
        mPickerManager.setEnable(enable);
        mShutterManager.setEnable(enable);
        if (!(mStopShowCommonUi && enable)) {
            // stop to make thumbnail manager clickable.
            mThumbnailManager.setEnable(enable);
        }
    }
    
    public void setAllCommonViewButShutterVisible(boolean visible) {
        if (visible) {
            mSettingManager.show();
            mModePicker.show();
            mIndicatorManager.show();
            mPickerManager.show();
            mThumbnailManager.show();
        } else {
            mSettingManager.hide();
            mModePicker.hide();
            mIndicatorManager.hide();
            mPickerManager.hide();
            mThumbnailManager.hide();
        }
    }
    
    public int getPreviewVisibility() {
        Log.i(TAG, "getPreviewVisibility : " + mPreviewVisibility);
        return mPreviewVisibility;
    }
    
    /**********************************module ui manager*********************************/

    /**
     * Called indirectly from each module in their initialization to get a view group
     * to inflate the module specific views in.
     *
     * @return a view group for modules to attach views to
     */
    public FrameLayout getModuleRootView() {
        Log.i(TAG, "[getModuleRootView]+");
        return mModuleUI;
    }

    /**
     * This inflates generic_module layout, which contains all the shared views across
     * modules. Then each module inflates their own views in the given view group.
     */
    public void prepareModuleUI() {
        Log.i(TAG, "[prepareModuleUI]+");
        mPreviewManager.setSurfaceCallback(this);
    }

    /**
     * Remove all the module specific views.
     */
    public void clearModuleUI() {
        Log.i(TAG, "[clearModuleUI]+");
        
    }

    /**********************************preview status*********************************/
    public void setPreviewStatusListener(PreviewStatusListener previewStatusListener) {
        mPreviewStatusListener = previewStatusListener;
        if (mPreviewStatusListener != null) {
            onPreviewListenerChanged();
        }
    }
    
    public View getPreviewView() {
        return mPreviewManager.getPreviewView();
    }
    
    public void setOnLayoutChangedListener(OnLayoutChangeListener listener) {
        mPreviewManager.setOnLayoutChangeListener(listener);
    }

    public void addPreviewAreaSizeChangedListener(
            PreviewStatusListener.OnPreviewAreaChangedListener listener) {
        mPreviewManager.addPreviewAreaSizeChangedListener(listener);
    }

    public void removePreviewAreaSizeChangedListener(
            PreviewStatusListener.OnPreviewAreaChangedListener listener) {
        mPreviewManager.removePreviewAreaSizeChangedListener(listener);
    }

    public void updatePreviewSize(int previewWidth, int previewHeight) {
        mPreviewManager.updatePreviewSize(previewWidth, previewHeight);
    }

    public void onPreviewStarted() {
        mPreviewManager.onPreviewStarted();
    }
    
    /**********************************shutter manager*********************************/

    public void setShutterEventListener(ShutterEventsListener listener, boolean videoShutter) {
        if (videoShutter) {
            mVideoShutterListener = listener;
            mShutterManager.addShutterButtonListener(mVideoShutterCallback, true);
        } else {
            mPhotoShutterListener = listener;
            mShutterManager.addShutterButtonListener(mPhotoShutterCallback, false);
        }
    }
    
    public void setOkCancelClickListener(OkCancelClickListener listener) {
        mOkCancelClickListener = listener;
        mShutterManager.setOnOkCancelButtonClickListener(mOnOkCancelButtonClickListener);
    }
    
    public void setShutterButtonEnabled(boolean enabled, boolean videoShutter) {
        if (mShutterManager != null) {
            mShutterManager.setShutterButtonEnabled(enabled, videoShutter);
        }
    }

    public boolean isShutterButtonEnabled(boolean videoShutter) {
        if (mShutterManager != null) {
            return mShutterManager.isShutterButtonEnabled(videoShutter);
        }
        return false;
    }
    
    /**
     * Trigger video / photo shutter button's click event
     * @param clickVideoButton true, click video button; false, click photo button.
     */
    public void performShutterButtonClick(boolean clickVideoButton) {
        mShutterManager.performShutterButtonClick(clickVideoButton);
    }
    
    public void switchShutterButtonImageResource(int imageResourceId, boolean isVideoButton) {
        mShutterManager.switchShutterButtonImageResource(imageResourceId, isVideoButton);
    }

    public void switchShutterButtonLayout(int layoutId) {
        mShutterManager.switchShutterButtonLayout(layoutId);
    }
    
    public void onOrientationChanged(int orientation) {
        int newOrientation = CameraUtil.roundOrientation(orientation, mOrientation);
        if (newOrientation == mOrientation) {
            return;
        }
        
        mOrientation = newOrientation;
        mGestureManager.onOrientationChanged(mOrientation);
        mOrientationCompensation = (mOrientation + 
                CameraUtil.getDisplayRotation(mCameraActivity)) % 360;
        Log.i(TAG, "[onOrientationChanged], mOrientation:" + mOrientation + ", " +
                "mOrientationCompensation:" + mOrientationCompensation);
        rotateLayers(mOrientationCompensation);
    }

    public void onAfterFullScreenChanged(boolean full) {
        mPreviewVisibility = full? VISIBILITY_VISIBLE : VISIBILITY_HIDDEN;
        mAppController.onPreviewVisibilityChanged(mPreviewVisibility);
    }
    
    public void setPlayButtonClickListener(PlayButtonClickListener listener) {
        mPlayButtonClickListener = listener;
        mReviewManager.setOnPlayButtonClickListener(mOnPlayButtonClickListener);
    }
    
    public void setRetakeButtonClickListener(RetakeButtonClickListener listener) {
        mRetakeButtonClickListener = listener;
        mReviewManager.setOnRetakeButtonClickListener(mOnRetakeButtonClickListener);
    }
    
    public void stopShowCommonUI(boolean stop) {
        Log.i(TAG, "[stopShowCommonUI], stop:" + stop);
        mStopShowCommonUi = stop;
    }

    /*************************************Setting UI*************************************************/

    public boolean isSettingViewShowing() {
        if (mSettingManager != null) {
            //TODO how to check setting view is showing
        }
        return false;
    }

    public void showSettingUi() {
        if (mSettingManager != null) {
            mSettingManager.show();
        }
    }

    public void hideSettingUi() {
        if (mSettingManager != null) {
            mSettingManager.collapse(true);
            mSettingManager.hide();
        }
    }
    
    /*************************************Mode    UI*************************************************/
    public void showModeOptionsUi() {
        if (mModePicker != null) {
            mModePicker.show();
        }
    }
    
    public void hideModeOptionsUi() {
        if (mModePicker != null) {
            mModePicker.hide();
        }
    }

    /**************************************Picke  UI*************************************************/
    public void showPickerManagerUi() {
        if (mPickerManager != null) {
            mPickerManager.show();
        }
    }

    public void hidePickerManagerUi() {
        if (mPickerManager != null) {
            mPickerManager.hide();
        }
    }
    
    public void performCameraPickerBtnClick() {
        if (mPickerManager != null) {
            mPickerManager.performCameraPickerBtnClick();
        }
        
    }
    
    public void showThumbnailManagerUi() {
        if (mThumbnailManager != null) {
            mThumbnailManager.show();
        }
    }
    
    public void setThumbnailManagerEnable(boolean enable) {
        if (mThumbnailManager != null) {
            mThumbnailManager.setEnable(enable);
        }
    }
    
    public void hideThumbnailManagerUi() {
        if (mThumbnailManager != null) {
            mThumbnailManager.hide();
        }
    }
    
    public void showIndicatorManagerUi() {
        if (mIndicatorManager != null) {
            mIndicatorManager.show();
        }
    }

    public void hideIndicatorManagerUi() {
        if (mIndicatorManager != null) {
            mIndicatorManager.hide();
        }
    }
    
    public void showInfo(final String text) {
        showInfo(text, MSG_DEFAULT_SHOW_ONSCREEN_VIEW_DURATION/** default show time 3000ms**/);
    }

    public void showInfo(final CharSequence text, int showMs) {
        Log.i(TAG, "[showInfo], text:" + text + ", showMs:" + showMs);
        mMainHandler.removeMessages(MSG_SHOW_INFO_UI);
        // if remaining manager is showing, show info after 1000ms
        // else show info directly
        if (mRemainingManager.isShowing()) {
            Message msg = mMainHandler.obtainMessage(MSG_SHOW_INFO_UI,
                    showMs, 0, text);
            mMainHandler.sendMessageDelayed(msg, 1000);
        } else {
            doShowInfo(text, showMs);
        }
    }
    
    /**
     * Dismiss info. If parameter onlyDismissInfo is true, only need to dismiss info view, otherwise, 
     * besides of dismissing info view, other views needs to show if it is possible.
     * @param onlyDismiss
     */
    public void dismissInfo(boolean onlyDismissInfo) {
        if (mInfoManager != null) {
            mMainHandler.removeMessages(MSG_SHOW_INFO_UI);
            mMainHandler.removeMessages(MSG_HIDE_INFO_UI);
            doDismissInfo(onlyDismissInfo);
        }
    }
    
    public void notifyPreferenceReady() {
        Log.i(TAG, "[notifyPreferenceReady]...");
        if (!mStopShowCommonUi && !mReviewManager.isShowing()) {
            // avoid to ui will be shown when switch camera during recording, like pip recording.
            if (mShutterManager != null) {
                mShutterManager.show();
            }
            if (mSettingManager != null) {
                mSettingManager.show();
            }
            if (mPickerManager != null) {
                mPickerManager.notifyPreferenceReady();
                mPickerManager.show();
            }
            if (mModePicker != null) {
                mModePicker.show();
            }
            
            if (mRemainingManager != null) {
                mRemainingManager.show();
                mInfoManager.hide();
                mMainHandler.sendEmptyMessageDelayed(MSG_SHOW_INDICATOR_UI, 
                        DELAY_MSG_SHOW_INDICATOR_UI);
            }
        }
        setAllCommonViewEnable(true);
        rotateLayers(mOrientationCompensation);
    }
    
    public void notifyMediaSaved(Uri uri) {
        if (mThumbnailManager != null) {
            mThumbnailManager.notifyFileSaved(uri);
        }
    }
    
    /**
     * Show left counts of image can be taken.
     * @param bytePerCount The bytes one image occupy.
     * @param showAlways If this is true, always show left counts of image can be taken. If 
     *        this is false, only show left counts when counts less than 100L.
     */
    public void showLeftCounts(final int bytePerCount, final boolean showAlways) { 
        Log.i(TAG, "[showLeftCounts], bytePerCount:" + bytePerCount + ", showAlways:" + showAlways);
        mCameraActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRemainingManager != null) {
                    mMainHandler.removeMessages(MSG_SHOW_INDICATOR_UI);
                    mIndicatorManager.hide();
                    mInfoManager.hide();
                    mRemainingManager.showLeftCounts(bytePerCount, showAlways);
                    mMainHandler.sendEmptyMessageDelayed(MSG_SHOW_INDICATOR_UI, 
                            DELAY_MSG_SHOW_INDICATOR_UI);
                }
            }
        });
    }
    
    /**
     * Show left times can be recorded
     * @param bytePerMs The bytes of one millisecond recording
     */
    public void showLeftTime(long bytePerMs) { 
        Log.i(TAG, "[showLeftTime], bytePerMs:" + bytePerMs);
        if (mRemainingManager != null) {
            mMainHandler.removeMessages(MSG_SHOW_INDICATOR_UI);
            mRemainingManager.showLeftTime(bytePerMs);
            mMainHandler.sendEmptyMessageDelayed(MSG_SHOW_INDICATOR_UI, 
                    DELAY_MSG_SHOW_INDICATOR_UI);
        }
    }
    
    public void updateAsdDetectedScene(final String scene) {
        if (mIndicatorManager != null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    mIndicatorManager.updateAsdDetectedScene(scene);
                }
            });
           
        }
    } 
    
    /**
     * Show saving dialog
     * @param msg The content dialog show.
     */
    public void showSavingProgress(String msg) {
        if (mRotateProgress != null) {
            mRotateProgress.showProgress(msg);
        }
    }
    
    /**
     * Dismiss saving dialog
     */
    public void dismissSavingProgress() {
        if (mRotateProgress != null) {
            mRotateProgress.hide();
        }
    }
    
    /**
     * Set review image to ReviewManager and show.
     * @param bitmap 
     */
    public void showReviewManager(Bitmap bitmap) {
        if (mReviewManager != null) {
            mReviewManager.setReviewImage(bitmap);
        }
        if (mPickerManager != null) {
            mPickerManager.hide();
        }
        
        if (mSettingManager != null) {
            mSettingManager.hide();
        }
    }
    
    public void showReviewManager(byte[] jpagData,int orientation) {
        DecodeTask decodeTask = new DecodeTask(jpagData,orientation,false);
        decodeTask.execute();

    }
    
    public void hideReviewManager() {
        if (mReviewManager != null) {
            mReviewManager.hide();
        }
        if (mPickerManager != null) {
            mPickerManager.show();
        }
        
        if (mSettingManager != null) {
            mSettingManager.show();
        }
    }

    private void initializeViewGroup() {
        mViewLayerBottom  = (ViewGroup) mCameraActivity.findViewById(R.id.view_layer_bottom);
        mViewLayerNormal  = (ViewGroup) mCameraActivity.findViewById(R.id.view_layer_normal);
        mViewLayerTop     = (ViewGroup) mCameraActivity.findViewById(R.id.view_layer_top);
        mViewLayerShutter = (ViewGroup) mCameraActivity.findViewById(R.id.view_layer_shutter);
        mViewLayerSetting = (ViewGroup) mCameraActivity.findViewById(R.id.view_layer_setting);
        mViewLayerOverlay = (ViewGroup) mCameraActivity.findViewById(R.id.view_layer_overlay);
        mModuleUI         = (FrameLayout) mCameraActivity.findViewById(R.id.view_layer_module);
    }

    private void initializeCommonUIManagers() {
        initializeViewGroup();
        
        mShutterManager = new ShutterManager(mCameraActivity, mViewLayerShutter);
        
        mSettingManager = new SettingManager(mCameraActivity, mViewLayerSetting, mPreferenceManager);
        mSettingManager.setSettingChangedListener(mSettingChangedListener);
        mSettingManager.setSettingStatusListener(mOnSettingStatusListener);
        
        mPickerManager = new PickerManager(mCameraActivity, mViewLayerNormal, mPreferenceManager);
        mPickerManager.setOnPickedListener(mOnPickedListener);
        
        mModePicker = new ModePicker(mCameraActivity, mViewLayerNormal, mPreferenceManager);
        mModePicker.setOnModeChangedListener(mOnModeChangedListener);
        
        mIndicatorManager = new IndicatorManager(mCameraActivity, mViewLayerNormal, mPreferenceManager);
        
        mRemainingManager = new RemainingManager(mCameraActivity, mViewLayerNormal, mPreferenceManager);
        
        mThumbnailManager = new ThumbnailManager(mCameraActivity, mViewLayerNormal);
        mThumbnailManager.setOnThumbnailClickListener(mOnThumbnailClickListener);
        mThumbnailManager.show();
        
        mReviewManager = new ReviewManager(mCameraActivity, mViewLayerBottom);
        
        mRotateDialog = new RotateDialog(mCameraActivity, mViewLayerOverlay);
        mRotateProgress = new RotateProgress(mCameraActivity, mViewLayerOverlay);
        mInfoManager = new InfoManager(mCameraActivity, mViewLayerNormal);
        
        rotateLayers(mOrientation);
    }

    private void unInitializeCommonUIManagers() {
        
    }
    
    private void rotateLayers(int orientation) {
        // set orientation as tag, so its child views can get orientation from this tag. 
        mViewLayerBottom.setTag(orientation);
        mViewLayerNormal.setTag(orientation);
        mViewLayerTop.setTag(orientation);
        mViewLayerShutter.setTag(orientation);
        mViewLayerSetting.setTag(orientation);
        mViewLayerOverlay.setTag(orientation);
        
        CameraUtil.setOrientation(mViewLayerBottom, orientation, true);
        CameraUtil.setOrientation(mViewLayerNormal, orientation, true);
        CameraUtil.setOrientation(mViewLayerTop, orientation, true);
        CameraUtil.setOrientation(mViewLayerShutter, orientation, true);
        CameraUtil.setOrientation(mViewLayerSetting, orientation, true);
        CameraUtil.setOrientation(mViewLayerOverlay, orientation, true);
    }

    /**
     * When the PreviewStatusListener changes, listeners need to be
     * set on the following app ui elements:
     */
    private void onPreviewListenerChanged() {
        OnGestureListener gestureListener = 
                mPreviewStatusListener.getGestureListener();
        OnPreviewTouchedListener touchListener = 
                mPreviewStatusListener.getTouchListener();
        if (mGestureManager != null) {
            mGestureManager.setPreviewGestureListener(gestureListener);
            mGestureManager.registerPreviewTouchListener(touchListener);
        }
    }
    
    private final class MainHandler extends Handler {
        
        public MainHandler(Looper mainLooper) {
            super(mainLooper);
        }
        
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "msg id=" + msg.what);
            switch (msg.what) {
            case MSG_SHOW_INDICATOR_UI:
                doShowIndicator();
                break;
            
            case MSG_SHOW_INFO_UI:
                doShowInfo((CharSequence)msg.obj, msg.arg1);
                break;
                
            case MSG_SHOW_REMAINING_UI:
                break;
                
            case MSG_HIDE_INFO_UI:
                doDismissInfo(false);
                break;
            default:
                break;
            }
        }
    }
    
    private void doShowIndicator() {
        if (mRemainingManager != null) {
            mRemainingManager.hide();
        }
        
        if (mIndicatorManager != null) {
            mIndicatorManager.show();
        }
    }
    
    private void doShowInfo(CharSequence text, int showViewMs) {
        // hide remaining manager, picker manager, indicator manager
        mRemainingManager.hide();
        mPickerManager.hide();
        mIndicatorManager.hide();
        // show info manager
        mInfoManager.showText(text);
        // hide info manager after showViewMs(ms)
        if (showViewMs > 0) {
            mMainHandler.sendMessageDelayed(mMainHandler.obtainMessage(MSG_HIDE_INFO_UI), 
                    showViewMs);
        }
    }
    
    /**
     * Dismiss info. If parameter onlyDimiss is true, only need to dismiss info view, otherwise, 
     * besides of dismissing info view, other views needs to show if it is possible.
     * @param onlyDismiss
     */
    private void doDismissInfo(boolean onlyDismissInfo) {
        mInfoManager.hide();
        
        if (!onlyDismissInfo && !mStopShowCommonUi) {
            mPickerManager.show();
            mIndicatorManager.show();
        }
        
    }
   //********* add for mms or 3th ap user camera take picture
    private class DecodeTask extends AsyncTask<Void,Void,Bitmap> {
        private final byte [] mData;
        private final int mOrientation;
        private final boolean mMirror;
        public DecodeTask(byte []data,int orientation,boolean mirror) {
            mData = data;
            mOrientation = orientation;
            mMirror = mirror;

        }
        @Override
        protected  Bitmap doInBackground(Void... params) {
            //decode image in background
            final BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = DOWN_SAMPLE_FACTOR;
            Bitmap bitmap = BitmapFactory.decodeByteArray(mData,0,mData.length,opts);
            if(mOrientation != 0 || mMirror){
                Matrix m = new Matrix();
                if(mMirror){
                    //flip horzontally
                    m.setScale(-1f,1f);
                }
                m.preRotate(mOrientation);
                return Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,false);

            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            showReviewManager(bitmap);
        }
    }
}
