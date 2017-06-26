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


import java.io.File;
import java.lang.ref.WeakReference;

import android.annotation.TargetApi;
import android.app.ActionBar.OnMenuVisibilityListener;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateBeamUrisCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.StatFs;
import android.support.v4.print.PrintHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ActivityChooserModel;
import android.widget.ActivityChooserModel.OnChooseActivityListener;
import android.widget.RelativeLayout;
import android.widget.ShareActionProvider;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.MuteVideo.MuteDoneListener;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.data.ClusterAlbum;
import com.android.gallery3d.data.ComboAlbum;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.EmptyAlbumImage;
import com.android.gallery3d.data.FilterDeleteSet;
import com.android.gallery3d.data.FilterSource;
import com.android.gallery3d.data.ImageCacheService;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalMediaItem;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaObject.PanoramaSupportCallback;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.SecureAlbum;
import com.android.gallery3d.data.SecureSource;
import com.android.gallery3d.data.SnailAlbum;
import com.android.gallery3d.data.SnailItem;
import com.android.gallery3d.data.SnailSource;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.DetailsHelper.DetailsSource;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.ui.GestureRecognizer.Listener;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.MenuExecutor;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.Log;
import com.android.gallery3d.util.UsageStatistics;

import com.mediatek.gallery3d.adapter.ContainerPage;
import com.mediatek.gallery3d.adapter.FeatureHelper;
import com.mediatek.gallery3d.adapter.PhotoPlayFacade;
import com.mediatek.galleryfeature.pq.ImageDC;
import com.mediatek.galleryfeature.pq.ImageDCActivity;
import com.mediatek.galleryfeature.pq.PictureQualityActivity;
import com.mediatek.galleryfeature.animshare.AnimatedContentSharer;
import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryfeature.drm.DrmHelper;
import com.mediatek.galleryfeature.platform.PlatformHelper;
import com.mediatek.galleryfeature.refocus.RefocusActivity;
import com.mediatek.galleryfeature.refocus.RefocusMember;
import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.LayerManager;
import com.mediatek.galleryframework.base.MediaData;

public abstract class PhotoPage extends ActivityState implements
        /// M: [BEHAVIOR.ADD] @{
        OrientationManager.Listener,
        /// @}
        PhotoView.Listener, AppBridge.Server, 
        /// M: [FEATURE.MODIFY] @{
        /*ShareActionProvider.OnShareTargetSelectedListener,*/
        ActivityChooserModel.OnChooseActivityListener,
        /// @}
        PhotoPageBottomControls.Delegate, 
        GalleryActionBar.OnAlbumModeSelectedListener {
    private static final String TAG = "Gallery2/PhotoPage";

    private static final int MSG_HIDE_BARS = 1;
    /// M: [FEATURE.ADD] camera active @{
    private static final int MSG_LOCK_ORIENTATION = 2;
    private static final int MSG_UNLOCK_ORIENTATION = 3;
    /// @}
    private static final int MSG_ON_FULL_SCREEN_CHANGED = 4;
    private static final int MSG_UPDATE_ACTION_BAR = 5;
    private static final int MSG_UNFREEZE_GLROOT = 6;
    private static final int MSG_WANT_BARS = 7;
    private static final int MSG_REFRESH_BOTTOM_CONTROLS = 8;
    private static final int MSG_ON_CAMERA_CENTER = 9;
    private static final int MSG_ON_PICTURE_CENTER = 10;
    private static final int MSG_REFRESH_IMAGE = 11;
    private static final int MSG_UPDATE_PHOTO_UI = 12;
    private static final int MSG_UPDATE_DEFERRED = 14;
    private static final int MSG_UPDATE_SHARE_URI = 15;
    private static final int MSG_UPDATE_PANORAMA_UI = 16;

    private static final int HIDE_BARS_TIMEOUT = 3500;
    private static final int UNFREEZE_GLROOT_TIMEOUT = 250;

    private static final int REQUEST_SLIDESHOW = 1;
    private static final int REQUEST_CROP = 2;
    private static final int REQUEST_CROP_PICASA = 3;
    private static final int REQUEST_EDIT = 4;
    /// M:[FEATURE.MODIFY] {
    // private static final int REQUEST_PLAY_VIDEO = 5;
    public static final int REQUEST_PLAY_VIDEO = 5;
    /// @}
    private static final int REQUEST_TRIM = 6;
    /// M: [FEATURE.ADD] @{
    private static final int REQUEST_PQ = 7;
    // added for Image refocus.
    private static final int REQUEST_REFOCUS = 8;
    /// @}
    public static final String KEY_MEDIA_SET_PATH = "media-set-path";
    public static final String KEY_MEDIA_ITEM_PATH = "media-item-path";
    public static final String KEY_INDEX_HINT = "index-hint";
    public static final String KEY_OPEN_ANIMATION_RECT = "open-animation-rect";
    public static final String KEY_APP_BRIDGE = "app-bridge";
    public static final String KEY_TREAT_BACK_AS_UP = "treat-back-as-up";
    public static final String KEY_START_IN_FILMSTRIP = "start-in-filmstrip";
    public static final String KEY_RETURN_INDEX_HINT = "return-index-hint";
    public static final String KEY_SHOW_WHEN_LOCKED = "show_when_locked";
    public static final String KEY_IN_CAMERA_ROLL = "in_camera_roll";
    public static final String KEY_READONLY = "read-only";

    public static final String KEY_ALBUMPAGE_TRANSITION = "albumpage-transition";
    public static final int MSG_ALBUMPAGE_NONE = 0;
    public static final int MSG_ALBUMPAGE_STARTED = 1;
    public static final int MSG_ALBUMPAGE_RESUMED = 2;
    public static final int MSG_ALBUMPAGE_PICKED = 4;

    public static final String ACTION_NEXTGEN_EDIT = "action_nextgen_edit";
    public static final String ACTION_SIMPLE_EDIT = "action_simple_edit";

    private GalleryApp mApplication;
    private SelectionManager mSelectionManager;

    private PhotoView mPhotoView;
    private PhotoPage.Model mModel;
    private DetailsHelper mDetailsHelper;
    private boolean mShowDetails;

    // mMediaSet could be null if there is no KEY_MEDIA_SET_PATH supplied.
    // E.g., viewing a photo in gmail attachment
    private FilterDeleteSet mMediaSet;

    // The mediaset used by camera launched from secure lock screen.
    private SecureAlbum mSecureAlbum;

    private int mCurrentIndex = 0;
    private Handler mHandler;
    private boolean mShowBars = true;
    private volatile boolean mActionBarAllowed = true;
    private GalleryActionBar mActionBar;
    private boolean mIsMenuVisible;
    private boolean mHaveImageEditor;
    private PhotoPageBottomControls mBottomControls;
    private MediaItem mCurrentPhoto = null;
    private MenuExecutor mMenuExecutor;
    private boolean mIsActive;
    private boolean mShowSpinner;
    private String mSetPathString;
    // This is the original mSetPathString before adding the camera preview item.
    private boolean mReadOnlyView = false;
    private String mOriginalSetPathString;
    private AppBridge mAppBridge;
    private SnailItem mScreenNailItem;
    private SnailAlbum mScreenNailSet;
    private OrientationManager mOrientationManager;
    private boolean mTreatBackAsUp;
    private boolean mStartInFilmstrip;
    private boolean mHasCameraScreennailOrPlaceholder = false;
    /// M: [BUG.MODIFY] @{
    /*private boolean mRecenterCameraOnResume = true;*/
    private boolean mRecenterCameraOnResume = false;
    /// @}

    // These are only valid after the panorama callback
    private boolean mIsPanorama;
    private boolean mIsPanorama360;

    private long mCameraSwitchCutoff = 0;
    private boolean mSkipUpdateCurrentPhoto = false;
    private static final long CAMERA_SWITCH_CUTOFF_THRESHOLD_MS = 300;

    private static final long DEFERRED_UPDATE_MS = 250;
    private boolean mDeferredUpdateWaiting = false;
    private long mDeferUpdateUntil = Long.MAX_VALUE;

    // The item that is deleted (but it can still be undeleted before commiting)
    private Path mDeletePath;
    private boolean mDeleteIsFocus;  // whether the deleted item was in focus

    private Uri[] mNfcPushUris = new Uri[1];
    /// M: [FEATURE.ADD] camera active @{
    private float[] mPhotoPageBackgroundColor = new float[] { 0, 0, 0, 0 };
    private boolean mCanSlideToPrePicture = true;
    /// @}

    private final MyMenuVisibilityListener mMenuVisibilityListener =
            new MyMenuVisibilityListener();

    private int mLastSystemUiVis = 0;
    
    /// M: [PERF.ADD] @{
    private boolean mDisableBarChanges = false;
    /// @}
    /// M: [BUG.ADD] for NFC @{
    private Uri mShareUriFromChooserView = null;
    /// @}
    // / M: [BUG.ADD] @{
    // Google bug fix,mute dialog should be dismiss before gallery activity
    // destroyed.
    private MuteVideo mMuteVideo;
    // / @}

    private final PanoramaSupportCallback mUpdatePanoramaMenuItemsCallback = new PanoramaSupportCallback() {
        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                boolean isPanorama360) {
            if (mediaObject == mCurrentPhoto) {
                mHandler.obtainMessage(MSG_UPDATE_PANORAMA_UI, isPanorama360 ? 1 : 0, 0,
                        mediaObject).sendToTarget();
            }
        }
    };

    private final PanoramaSupportCallback mRefreshBottomControlsCallback = new PanoramaSupportCallback() {
        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                boolean isPanorama360) {
            if (mediaObject == mCurrentPhoto) {
                mHandler.obtainMessage(MSG_REFRESH_BOTTOM_CONTROLS, isPanorama ? 1 : 0, isPanorama360 ? 1 : 0,
                        mediaObject).sendToTarget();
            }
        }
    };

    private final PanoramaSupportCallback mUpdateShareURICallback = new PanoramaSupportCallback() {
        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                boolean isPanorama360) {
            if (mediaObject == mCurrentPhoto) {
                mHandler.obtainMessage(MSG_UPDATE_SHARE_URI, isPanorama360 ? 1 : 0, 0, mediaObject)
                        .sendToTarget();
            }
        }
    };

    public static interface Model extends PhotoView.Model {
        public void resume();
        public void pause();
        public boolean isEmpty();
        public void setCurrentPhoto(Path path, int indexHint);
        /// M: [FEATURE.ADD] @{
        public boolean isCamera(int offset);
        public void setSourceAndItem(MediaSet mediaSet, Path itemPath);
        /// @}
        /// M: [BUG.ADD] @{
        public int getLoadingState(int offset);
        /// @}
    }

    private class MyMenuVisibilityListener implements OnMenuVisibilityListener {
        @Override
        public void onMenuVisibilityChanged(boolean isVisible) {
            mIsMenuVisible = isVisible;
            refreshHidingMessage();
        }
    }

    @Override
    protected int getBackgroundColorId() {
        /// M: [FEATURE.MODIFY] @{
        /*
        return R.color.photo_background;
        */
        return -1;
        /// @}
    }

    private final GLView mRootPane = new GLView() {
        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {
            mPhotoView.layout(0, 0, right - left, bottom - top);
            if (mShowDetails) {
                mDetailsHelper.layout(left, mActionBar.getHeight(), right, bottom);
            }
        }
    };

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        mActionBar = mActivity.getGalleryActionBar();
        mSelectionManager = new SelectionManager(mActivity, false);
        mMenuExecutor = new MenuExecutor(mActivity, mSelectionManager);

        mPhotoView = new PhotoView(mActivity);
        mPhotoView.setListener(this);
        mRootPane.addComponent(mPhotoView);
        mApplication = (GalleryApp) ((Activity) mActivity).getApplication();
        mOrientationManager = mActivity.getOrientationManager();
        /// M: [BUG.ADD] @{
        mOrientationManager.addListener(this);
        /// @}
        /// M: [BUG.MARK] remove to onResume @{
        /*mActivity.getGLRoot().setOrientationSource(mOrientationManager);*/
        /// @}

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_HIDE_BARS: {
                        /// M: [BUG.MODIFY] @{
                        /* hideBars(); */
                        if (mIsActive) {
                            hideBars();
                        } else {
                            Log.i(TAG, "<mHandler.MSG_HIDE_BARS> mIsActive = "
                                + mIsActive + ", not hideBars");
                        }
                        /// @}
                        break;
                    }
                    /// M: [FEATURE.ADD] camera active @{
                    case MSG_LOCK_ORIENTATION: {
                        mOrientationManager.lockOrientation();
                        break;
                    }
                    case MSG_UNLOCK_ORIENTATION: {
                        mOrientationManager.unlockOrientation();
                        break;
                    }
                    /// @}
                    case MSG_REFRESH_BOTTOM_CONTROLS: {
                        if (mCurrentPhoto == message.obj && mBottomControls != null) {
                            mIsPanorama = message.arg1 == 1;
                            mIsPanorama360 = message.arg2 == 1;
                            mBottomControls.refresh();
                        }
                        break;
                    }
                    case MSG_ON_FULL_SCREEN_CHANGED: {
                        if (mAppBridge != null) {
                            /// M: [BUG.MODIFY] @{
                            /*mAppBridge.onFullScreenChanged(message.arg1 == 1);*/
                            if (mIsAppBridgeFullScreenChangeEnabled) {
                                mAppBridge.onFullScreenChanged(message.arg1 == 1,
                                        message.arg2);
                            }
                            mIsAppBridgeFullScreenChangeEnabled = true;
                            /// @}
                        }
                        break;
                    }
                    case MSG_UPDATE_ACTION_BAR: {
                        updateBars();
                        break;
                    }
                    case MSG_WANT_BARS: {
                        wantBars();
                        break;
                    }
                    case MSG_UNFREEZE_GLROOT: {
                        mActivity.getGLRoot().unfreeze();
                        break;
                    }
                    case MSG_UPDATE_DEFERRED: {
                        long nextUpdate = mDeferUpdateUntil - SystemClock.uptimeMillis();
                        if (nextUpdate <= 0) {
                            mDeferredUpdateWaiting = false;
                            updateUIForCurrentPhoto();
                        } else {
                            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DEFERRED, nextUpdate);
                        }
                        break;
                    }
                    case MSG_ON_CAMERA_CENTER: {
                        mSkipUpdateCurrentPhoto = false;
                        boolean stayedOnCamera = false;
                        if (!mPhotoView.getFilmMode()) {
                            stayedOnCamera = true;
                        } else if (SystemClock.uptimeMillis() < mCameraSwitchCutoff &&
                                mMediaSet.getMediaItemCount() > 1) {
                            mPhotoView.switchToImage(1);
                        } else {
                            if (mAppBridge != null) mPhotoView.setFilmMode(false);
                            stayedOnCamera = true;
                        }

                        if (stayedOnCamera) {
                            if (mAppBridge == null && mMediaSet.getTotalMediaItemCount() > 1) {
                                launchCamera();
                                /// M: [FEATURE.ADD] @{
                                mPhotoView.stopUpdateEngineData();
                                /// @}
                                /* We got here by swiping from photo 1 to the
                                   placeholder, so make it be the thing that
                                   is in focus when the user presses back from
                                   the camera app */
                                mPhotoView.switchToImage(1);
                            } else {
                                updateBars();
                                /// M: [BUG.MODIFY] getMediaItem(0) may be null, fix JE @{
                                /*updateCurrentPhoto(mModel.getMediaItem(0));*/
                                MediaItem photo = mModel.getMediaItem(0);
                                if (photo != null) updateCurrentPhoto(photo);
                                /// @}
                            }
                        }
                        break;
                    }
                    case MSG_ON_PICTURE_CENTER: {
                        if (!mPhotoView.getFilmMode() && mCurrentPhoto != null
                                && (mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_ACTION) != 0) {
                            /// M: [BUG.MODIFY] @{
                            /*mPhotoView.setFilmMode(true);*/
                            showEmptyAlbumToast(Toast.LENGTH_SHORT);
                            /// @}
                        }
                        break;
                    }
                    case MSG_REFRESH_IMAGE: {
                        final MediaItem photo = mCurrentPhoto;
                        mCurrentPhoto = null;
                        updateCurrentPhoto(photo);
                        break;
                    }
                    case MSG_UPDATE_PHOTO_UI: {
                        updateUIForCurrentPhoto();
                        break;
                    }
                    case MSG_UPDATE_SHARE_URI: {
                        /// M: [BUG.MARK] @{
                        // No matter what message.obj is, we update share intent for current photo
                        /* if (mCurrentPhoto == message.obj) {*/
                        /// @}
                            /// M: [BUG.ADD] fix UnsupportedOperationException JE @{
                            if (mCurrentPhoto instanceof SnailItem) break;
                            /// @}
                            boolean isPanorama360 = message.arg1 != 0;
                            Uri contentUri = mCurrentPhoto.getContentUri();
                            Intent panoramaIntent = null;
                            if (isPanorama360) {
                                panoramaIntent = createSharePanoramaIntent(contentUri);
                            }
                            Intent shareIntent = createShareIntent(mCurrentPhoto);

                            mActionBar.setShareIntents(panoramaIntent, shareIntent, PhotoPage.this);
                            setNfcBeamPushUri(contentUri);
                        /// M: [BUG.MARK] @{
                        // }
                        /// @}
                        break;
                    }
                    case MSG_UPDATE_PANORAMA_UI: {
                        if (mCurrentPhoto == message.obj) {
                            boolean isPanorama360 = message.arg1 != 0;
                            updatePanoramaUI(isPanorama360);
                        }
                        break;
                    }
                    default: throw new AssertionError(message.what);
                }
            }
        };

        mSetPathString = data.getString(KEY_MEDIA_SET_PATH);
        /// M: [BUG.MODIFY] @{
        // if there is mSetPathString, view is not read only, enable edit
        /*mReadOnlyView = data.getBoolean(KEY_READONLY);*/
        mReadOnlyView = data.getBoolean(KEY_READONLY)
                && (mSetPathString == null || mSetPathString.equals(""));
        Log.i(TAG, "<onCreate> mSetPathString = " + mSetPathString + ", mReadOnlyView = "
                + mReadOnlyView);
        /// @}
        mOriginalSetPathString = mSetPathString;
        setupNfcBeamPush();
        String itemPathString = data.getString(KEY_MEDIA_ITEM_PATH);
        Path itemPath = itemPathString != null ?
                Path.fromString(data.getString(KEY_MEDIA_ITEM_PATH)) :
                    null;
        mTreatBackAsUp = data.getBoolean(KEY_TREAT_BACK_AS_UP, false);
        mStartInFilmstrip = data.getBoolean(KEY_START_IN_FILMSTRIP, false);
        boolean inCameraRoll = data.getBoolean(KEY_IN_CAMERA_ROLL, false);
        mCurrentIndex = data.getInt(KEY_INDEX_HINT, 0);
        if (mSetPathString != null) {
            mShowSpinner = true;
            mAppBridge = (AppBridge) data.getParcelable(KEY_APP_BRIDGE);
            if (mAppBridge != null) {
                mShowBars = false;
                mHasCameraScreennailOrPlaceholder = true;
                mAppBridge.setServer(this);
                /// M: [FEATURE.ADD] camera active @{
                mOrientationManager.lockOrientation();
                /// @}
                // Get the ScreenNail from AppBridge and register it.
                int id = SnailSource.newId();
                Path screenNailSetPath = SnailSource.getSetPath(id);
                /// M: [BUG.ADD] fix bug: slideshow doesn't play again
                // when finish playing the last picture@{
                mSnailSetPath = screenNailSetPath;
                /// @}
                Path screenNailItemPath = SnailSource.getItemPath(id);
                mScreenNailSet = (SnailAlbum) mActivity.getDataManager()
                        .getMediaObject(screenNailSetPath);
                mScreenNailItem = (SnailItem) mActivity.getDataManager()
                        .getMediaObject(screenNailItemPath);
                mScreenNailItem.setScreenNail(mAppBridge.attachScreenNail());

                if (data.getBoolean(KEY_SHOW_WHEN_LOCKED, false)) {
                    // Set the flag to be on top of the lock screen.
                    mFlags |= FLAG_SHOW_WHEN_LOCKED;
                }

                // Don't display "empty album" action item for capture intents.
                if (!mSetPathString.equals("/local/all/0")) {
                    // Check if the path is a secure album.
                    if (SecureSource.isSecurePath(mSetPathString)) {
                        mSecureAlbum = (SecureAlbum) mActivity.getDataManager()
                                .getMediaSet(mSetPathString);
                        mShowSpinner = false;
                    }
                    mSetPathString = "/filter/empty/{"+mSetPathString+"}";
                }

                // Combine the original MediaSet with the one for ScreenNail
                // from AppBridge.
                mSetPathString = "/combo/item/{" + screenNailSetPath +
                        "," + mSetPathString + "}";

                // Start from the screen nail.
                /// M: [BUG.MODIFY] @{
                // sometimes we don't like to directly display camera preview
                // e.g. after mute in slide video, or after export motion track
                // itemPath = screenNailItemPath;
                if (data.getBoolean(KEY_FORCE_IN_CAMERA_PREVIEW, true)) {
                    itemPath = screenNailItemPath;
                } else {
                    mIsAppBridgeFullScreenChangeEnabled = false;
                }
                data.remove(KEY_FORCE_IN_CAMERA_PREVIEW); //recover bundle
                /// @}
            } else if (inCameraRoll && GalleryUtils.isCameraAvailable(mActivity)) {
                mSetPathString = "/combo/item/{" + FilterSource.FILTER_CAMERA_SHORTCUT +
                        "," + mSetPathString + "}";
                mCurrentIndex++;
                mHasCameraScreennailOrPlaceholder = true;
            }

            MediaSet originalSet = mActivity.getDataManager()
                    .getMediaSet(mSetPathString);
            if (mHasCameraScreennailOrPlaceholder && originalSet instanceof ComboAlbum) {
                // Use the name of the camera album rather than the default
                // ComboAlbum behavior
                ((ComboAlbum) originalSet).useNameOfChild(1);
            }
            /// M: [BUG.ADD] @{
            // tell PhotoView whether this album is cluster
            if (originalSet != null && originalSet instanceof ClusterAlbum) {
                mPhotoView.setIsCluster(true);
            } else {
                mPhotoView.setIsCluster(false);
            }
            /// @}
            mSelectionManager.setSourceMediaSet(originalSet);
            mSetPathString = "/filter/delete/{" + mSetPathString + "}";
            mMediaSet = (FilterDeleteSet) mActivity.getDataManager()
                    .getMediaSet(mSetPathString);
            if (mMediaSet == null) {
                Log.w(TAG, "failed to restore " + mSetPathString);
            }
            if (itemPath == null) {
                int mediaItemCount = mMediaSet.getMediaItemCount();
                if (mediaItemCount > 0) {
                    if (mCurrentIndex >= mediaItemCount) mCurrentIndex = 0;
                    itemPath = mMediaSet.getMediaItem(mCurrentIndex, 1)
                        .get(0).getPath();
                } else {
                    // Bail out, PhotoPage can't load on an empty album
                    return;
                }
            }
            PhotoDataAdapter pda = new PhotoDataAdapter(
                    mActivity, mPhotoView, mMediaSet, itemPath, mCurrentIndex,
                    mAppBridge == null ? -1 : 0,
                    mAppBridge == null ? false : mAppBridge.isPanorama(),
                    mAppBridge == null ? false : mAppBridge.isStaticCamera());
            mModel = pda;
            mPhotoView.setModel(mModel);

            pda.setDataListener(new PhotoDataAdapter.DataListener() {

                @Override
                public void onPhotoChanged(int index, Path item) {
                    int oldIndex = mCurrentIndex;
                    mCurrentIndex = index;
                    if (mHasCameraScreennailOrPlaceholder) {
                        if (mCurrentIndex > 0) {
                            mSkipUpdateCurrentPhoto = false;
                        }

                        /// M: [FEATURE.MODIFY] @{
                        /*if (oldIndex == 0 && mCurrentIndex > 0
                         && !mPhotoView.getFilmMode()) {
                         mPhotoView.setFilmMode(true);*/
                        if (oldIndex == 0 && mCurrentIndex > 0) {
                            onActionBarAllowed(true);
                            mPhotoView.setFilmMode(false);
                        /// @}
                            if (mAppBridge != null) {
                                UsageStatistics.onEvent("CameraToFilmstrip",
                                        UsageStatistics.TRANSITION_SWIPE, null);
                            }
                        } else if (oldIndex == 2 && mCurrentIndex == 1) {
                            mCameraSwitchCutoff = SystemClock.uptimeMillis() +
                                    CAMERA_SWITCH_CUTOFF_THRESHOLD_MS;
                            mPhotoView.stopScrolling();
                        } else if (oldIndex >= 1 && mCurrentIndex == 0) {
                            mPhotoView.setWantPictureCenterCallbacks(true);
                            mSkipUpdateCurrentPhoto = true;
                        }
                    }
                    if (!mSkipUpdateCurrentPhoto) {
                        if (item != null) {
                            MediaItem photo = mModel.getMediaItem(0);
                            if (photo != null) updateCurrentPhoto(photo);
                        }
                        updateBars();
                    }
                    // Reset the timeout for the bars after a swipe
                    /// M: [DEBUG.ADD] @{
                    Log.i(TAG, "<onPhotoChanged> refreshHidingMessage");
                    /// @}
                    refreshHidingMessage();
                }

                @Override
                public void onLoadingFinished(boolean loadingFailed) {
                    /// M: [BUG.ADD] @{
                    mLoadingFinished = true;
                    // Refresh bottom controls when data loading done
                    refreshBottomControlsWhenReady();
                    /// @}
                    if (!mModel.isEmpty()) {
                        MediaItem photo = mModel.getMediaItem(0);
                        if (photo != null) updateCurrentPhoto(photo);
                    } else if (mIsActive) {
                        // We only want to finish the PhotoPage if there is no
                        // deletion that the user can undo.
                        if (mMediaSet.getNumberOfDeletions() == 0) {
                            /// M: [BUG.ADD] pause PhotoView before finish PhotoPage @{
                            mPhotoView.pause();
                            /// @}
                            mActivity.getStateManager().finishState(
                                    PhotoPage.this);
                        }
                    }

                    /// M: [BUG.ADD] @{
                    // it has camera screenNail, we will finish the photopage if
                    // delete all image.
                    if (mMediaSet.getMediaItemCount() <= 1
                            && mAppBridge == null
                            && mHasCameraScreennailOrPlaceholder) {
                        mPhotoView.pause();
                        mActivity.getStateManager().finishState(PhotoPage.this);
                    }
                    /// @}
                }

                @Override
                public void onLoadingStarted() {
                    /// M: [BUG.ADD] @{
                    mLoadingFinished = false;
                    /// @}
                }
            });
        } else {
            // Get default media set by the URI
            MediaItem mediaItem = (MediaItem)
                    mActivity.getDataManager().getMediaObject(itemPath);
            /// M: [BUG.ADD] fix JE when mediaItem is deleted@{
            if (mediaItem == null) {
                Toast.makeText(((Activity) mActivity), R.string.no_such_item,
                        Toast.LENGTH_LONG).show();
                mPhotoView.pause();
                mActivity.getStateManager().finishState(this);
                return;
            }
            /// @}
            /// M: [BUG.ADD] @{
            // no PhotoDataAdapter style loading in SinglePhotoDataAdapter
            mLoadingFinished = true;
            /// @}
            mModel = new SinglePhotoDataAdapter(mActivity, mPhotoView, mediaItem);
            mPhotoView.setModel(mModel);
            updateCurrentPhoto(mediaItem);
            mShowSpinner = false;
        }

        mPhotoView.setFilmMode(mStartInFilmstrip && mMediaSet.getMediaItemCount() > 1);
        /// M: [BUG.MODIFY] @{
        /*RelativeLayout galleryRoot = (RelativeLayout) ((Activity) mActivity)
         .findViewById(mAppBridge != null ? R.id.content : R.id.gallery_root);*/
        ViewGroup galleryRoot = (ViewGroup) ((Activity) mActivity).findViewById(R.id.gallery_root);
        if (galleryRoot == null) {
            galleryRoot = (ViewGroup) ((Activity) mActivity).findViewById(R.id.camera_root);
        }
        /// @}
        if (galleryRoot != null) {
            if (mSecureAlbum == null) {
                mBottomControls = new PhotoPageBottomControls(this, mActivity, galleryRoot);
            }
        }

        ((GLRootView) mActivity.getGLRoot()).setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
                @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        int diff = mLastSystemUiVis ^ visibility;
                        mLastSystemUiVis = visibility;
                        if ((diff & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
                                && (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                            /// M: [BUG.MODIFY] Don't need show bars in camera preview. @{
                            /*showBars();*/
                            wantBars();
                            /// @}
                        }
                    }
                });
        /// M: [FEATURE.ADD] VTSP: share as video @{
        initAnimatedContentSharer();
        /// @}
        /// M: [FEATURE.ADD] add backward controller for layer @{
        mPhotoView.setBackwardControllerForLayerManager(mBackwardContollerForLayer);
        /// @}
    }

    @Override
    public void onPictureCenter(boolean isCamera) {
        isCamera = isCamera || (mHasCameraScreennailOrPlaceholder && mAppBridge == null);
        mPhotoView.setWantPictureCenterCallbacks(false);
        mHandler.removeMessages(MSG_ON_CAMERA_CENTER);
        mHandler.removeMessages(MSG_ON_PICTURE_CENTER);
        mHandler.sendEmptyMessage(isCamera ? MSG_ON_CAMERA_CENTER : MSG_ON_PICTURE_CENTER);
    }

    @Override
    public boolean canDisplayBottomControls() {
        return mIsActive && !mPhotoView.canUndo();
    }

    @Override
    public boolean canDisplayBottomControl(int control) {
        if (mCurrentPhoto == null) {
            return false;
        }
        switch(control) {
            /// M: [FEATURE.ADD] Image refocus:added for display the button for enter refocus activity on bottom@{
            case R.id.photopage_bottom_control_imageRefocus:
                if (FeatureConfig.supportRefocus && mCurrentPhoto != null
                        && mCurrentPhoto.getMediaData() != null) {
                    return mShowBars && !mPhotoView.getFilmMode()
                            && mCurrentPhoto.getMediaData().isRefocus;
                }
                return false;
            /// @}
            case R.id.photopage_bottom_control_edit:
                return mHaveImageEditor && mShowBars && !mReadOnlyView
                        && !mPhotoView.getFilmMode()
                        && (mCurrentPhoto.getSupportedOperations() & MediaItem.SUPPORT_EDIT) != 0
                        && mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE;
            case R.id.photopage_bottom_control_panorama:
                return mIsPanorama;
            case R.id.photopage_bottom_control_tiny_planet:
                return mHaveImageEditor && mShowBars
                        && mIsPanorama360 && !mPhotoView.getFilmMode();
            default:
                return false;
        }
    }

    @Override
    public void onBottomControlClicked(int control) {
        switch(control) {
            /// M: [FEATURE.ADD] Image reofucs:added for refocus image.@{
            case R.id.photopage_bottom_control_imageRefocus:
                launchRefocusActivity();
                return;
            /// @}
            case R.id.photopage_bottom_control_edit:
                /// M: [BUG.ADD] disable editing photo when sdcard is full. @{
                if (mModel == null) return;

                MediaItem current = mModel.getMediaItem(0);
                if (current == null) return;

                File srcFile = new File(current.getFilePath());
                if (!isSpaceEnough(srcFile)) {
                    Log.i(TAG, "<onBottomControlClicked> abort editing photo when no enough space!");
                    Toast.makeText(mActivity,
                            mActivity.getString(R.string.storage_not_enough),
                            Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                /// @}
                launchPhotoEditor();
                return;
            case R.id.photopage_bottom_control_panorama:
                mActivity.getPanoramaViewHelper()
                        .showPanorama(mCurrentPhoto.getContentUri());
                return;
            case R.id.photopage_bottom_control_tiny_planet:
                launchTinyPlanet();
                return;
            default:
                return;
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void setupNfcBeamPush() {
        if (!ApiHelper.HAS_SET_BEAM_PUSH_URIS) return;

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mActivity);
        if (adapter != null) {
            adapter.setBeamPushUris(null, mActivity);
            /// M: [BEHAVIOR.MODIFY] nfc @{
            if (FeatureConfig.supportMtkBeamPlus) {
                adapter.setMtkBeamPushUrisCallback(
                        new CreateBeamUrisCallback() {
                            @Override
                            public Uri[] createBeamUris(NfcEvent event) {
                                return mNfcPushUris;
                            }
                        }, mActivity);
            } else {
                adapter.setBeamPushUrisCallback(new CreateBeamUrisCallback() {
                    @Override
                    public Uri[] createBeamUris(NfcEvent event) {
                        return mNfcPushUris;
                    }
                }, mActivity);
            }
            /*adapter.setBeamPushUrisCallback(new CreateBeamUrisCallback() {
             @Override
             public Uri[] createBeamUris(NfcEvent event) {
             return mNfcPushUris;
             }
             }, mActivity);*/
            /// @}
        }
    }

    private void setNfcBeamPushUri(Uri uri) {
        /// M: [BUG.MODIFY] @{
        /*mNfcPushUris[0] = uri;*/
        if (mShareUriFromChooserView != null) {
            mNfcPushUris[0] = mShareUriFromChooserView;
            mShareUriFromChooserView = null;
        } else {
            mNfcPushUris[0] = uri;
        }
        Log.d(TAG, "<setNfcBeamPushUri> uri " + mNfcPushUris[0]);
        /// @}
    }

    private static Intent createShareIntent(MediaObject mediaObject) {
        int type = mediaObject.getMediaType();
        return new Intent(Intent.ACTION_SEND)
                .setType(MenuExecutor.getMimeType(type))
                .putExtra(Intent.EXTRA_STREAM, mediaObject.getContentUri())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private static Intent createSharePanoramaIntent(Uri contentUri) {
        return new Intent(Intent.ACTION_SEND)
                .setType(GalleryUtils.MIME_TYPE_PANORAMA360)
                .putExtra(Intent.EXTRA_STREAM, contentUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private void overrideTransitionToEditor() {
        ((Activity) mActivity).overridePendingTransition(android.R.anim.fade_in,
                android.R.anim.fade_out);
    }

    private void launchTinyPlanet() {
        // Deep link into tiny planet
        MediaItem current = mModel.getMediaItem(0);
        Intent intent = new Intent(FilterShowActivity.TINY_PLANET_ACTION);
        intent.setClass(mActivity, FilterShowActivity.class);
        intent.setDataAndType(current.getContentUri(), current.getMimeType())
            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN,
                mActivity.isFullscreen());
        mActivity.startActivityForResult(intent, REQUEST_EDIT);
        overrideTransitionToEditor();
    }

    private void launchCamera() {
        mRecenterCameraOnResume = false;
        GalleryUtils.startCameraActivity(mActivity);
        /// M: [BUG.ADD] not allowed slide to previous picture after launch camera @{
        mCanSlideToPrePicture = false;
        /// @}
    }

    private void launchPhotoEditor() {
        /// M: [BUG.ADD] abort editing photo if loading fail @{
        if (mModel != null
                && mModel.getLoadingState(0) == PhotoView.Model.LOADING_FAIL) {
            Log.i(TAG, "<launchPhotoEditor> abort editing photo if loading fail!");
            Toast.makeText(mActivity,
                    mActivity.getString(R.string.cannot_load_image),
                    Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        /// @}
        MediaItem current = mModel.getMediaItem(0);
        if (current == null || (current.getSupportedOperations()
                & MediaObject.SUPPORT_EDIT) == 0) {
            return;
        }

        Intent intent = new Intent(ACTION_NEXTGEN_EDIT);

        /// M: [BUG.MODIFY] create new task when launch photo editor from camera 
        // gallery and photo editor use same task stack @{
        /*intent.setDataAndType(current.getContentUri(), current.getMimeType())
                  .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);*/
        intent.setDataAndType(current.getContentUri(), current.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
        /// @}
        if (mActivity.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() == 0) {
            intent.setAction(Intent.ACTION_EDIT);
        }
        intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN,
                mActivity.isFullscreen());
        /// M: [FEATURE.ADD] @{
        // for special image, no need to delete origin image when save, such as continuous shot
        ExtItem extItem = current.getExtItem();
        if (extItem != null && !extItem.isDeleteOriginFileAfterEdit()) {
            // if current photo is last image in continuous shot group, not
            // set NEED_SAVE_AS as true
            if (mModel instanceof PhotoDataAdapter) {
                int size = ((PhotoDataAdapter) mModel).getTotalCount();
                MediaData md = current.getMediaData();
                if (size == 1 && md.mediaType == MediaData.MediaType.NORMAL
                        && md.subType == MediaData.SubType.CONSHOT) {
                    intent.putExtra(FilterShowActivity.NEED_SAVE_AS, false);
                    Log.i(TAG, "<launchPhotoEditor> edit the last image in continuous shot group,"
                            + " not set NEED_SAVE_AS as true");
                } else {
                    intent.putExtra(FilterShowActivity.NEED_SAVE_AS, true);
                }
            } else {
                intent.putExtra(FilterShowActivity.NEED_SAVE_AS, true);
            }
        }
        /// @}
        /// M: [FEATURE.ADD] clear refocus Exif and db column when edit photo @{
        if (current.getMediaData() != null
                && current.getMediaData().isRefocus) {
            intent.putExtra(FilterShowActivity.NEED_CLEAR_REFOCUS_EXIF_DB, true);
        }
        /// @}
        ((Activity) mActivity).startActivityForResult(Intent.createChooser(intent, null),
                REQUEST_EDIT);
        overrideTransitionToEditor();
    }

    private void launchSimpleEditor() {
        MediaItem current = mModel.getMediaItem(0);
        if (current == null || (current.getSupportedOperations()
                & MediaObject.SUPPORT_EDIT) == 0) {
            return;
        }

        Intent intent = new Intent(ACTION_SIMPLE_EDIT);

        intent.setDataAndType(current.getContentUri(), current.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (mActivity.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() == 0) {
            intent.setAction(Intent.ACTION_EDIT);
        }
        intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN,
                mActivity.isFullscreen());
        ((Activity) mActivity).startActivityForResult(Intent.createChooser(intent, null),
                REQUEST_EDIT);
        overrideTransitionToEditor();
    }

    private void requestDeferredUpdate() {
        mDeferUpdateUntil = SystemClock.uptimeMillis() + DEFERRED_UPDATE_MS;
        if (!mDeferredUpdateWaiting) {
            mDeferredUpdateWaiting = true;
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DEFERRED, DEFERRED_UPDATE_MS);
        }
    }

    private void updateUIForCurrentPhoto() {
        if (mCurrentPhoto == null) return;

        // If by swiping or deletion the user ends up on an action item
        // and zoomed in, zoom out so that the context of the action is
        // more clear
        if ((mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_ACTION) != 0
                && !mPhotoView.getFilmMode()) {
            mPhotoView.setWantPictureCenterCallbacks(true);
        }
        /// M: [BUG.ADD] @{
        // To avoid share old file, set share intent here
        if (!(mCurrentPhoto instanceof SnailItem || mCurrentPhoto instanceof EmptyAlbumImage)) {
            Intent shareIntent = createShareIntent(mCurrentPhoto);
            mActionBar.setShareIntents(null, shareIntent, PhotoPage.this);
        }
        /// @}
        updateMenuOperations();
        refreshBottomControlsWhenReady();
        if (mShowDetails) {
            mDetailsHelper.reloadDetails();
        }
        if ((mSecureAlbum == null)
                && (mCurrentPhoto.getSupportedOperations() & MediaItem.SUPPORT_SHARE) != 0) {
            mCurrentPhoto.getPanoramaSupport(mUpdateShareURICallback);
        }
        /// M: [BEHAVIOR.ADD] @{
        updateScaleGesture();
        /// @}
    }

    private void updateCurrentPhoto(MediaItem photo) {
        /// M: [BUG.MODIFY] @{
        /*if (mCurrentPhoto == photo) return;*/
        // Modify for update support operation menu display
        // if photo.getDataVersion() != mCurrentVersion, means the mediaItem has been updated
        if (mCurrentPhoto == photo && photo.getDataVersion() == mCurrentVersion) return;
        mCurrentVersion = photo.getDataVersion();
        /// @}
        mCurrentPhoto = photo;
        if (mPhotoView.getFilmMode()) {
            requestDeferredUpdate();
        } else {
            updateUIForCurrentPhoto();
        }
    }

    private void updateMenuOperations() {
        Menu menu = mActionBar.getMenu();

        // it could be null if onCreateActionBar has not been called yet
        if (menu == null) return;

        MenuItem item = menu.findItem(R.id.action_slideshow);
        if (item != null) {
            item.setVisible((mSecureAlbum == null) && canDoSlideShow());
        }
        if (mCurrentPhoto == null) return;

        int supportedOperations = mCurrentPhoto.getSupportedOperations();
        if (mReadOnlyView) {
            /// M: [BUG.MODIFY] @{
            /*supportedOperations ^= MediaObject.SUPPORT_EDIT;*/
            // when mReadOnlyView == true, set SUPPORT_EDIT as false
            supportedOperations &= ~MediaObject.SUPPORT_EDIT;
            /// @}
        }
        if (mSecureAlbum != null) {
            supportedOperations &= MediaObject.SUPPORT_DELETE;
        } else {
            mCurrentPhoto.getPanoramaSupport(mUpdatePanoramaMenuItemsCallback);
            if (!mHaveImageEditor) {
                supportedOperations &= ~MediaObject.SUPPORT_EDIT;
            }
        }
        /// M: [FEATURE.ADD] @{
        if (MediaObject.MEDIA_TYPE_IMAGE == mCurrentPhoto.getMediaType()) {
            if (true == FeatureConfig.supportPQ) {
                supportedOperations |= MediaObject.SUPPORT_PQ;
            }
            if (true == FeatureConfig.supportImageDCEnhance) {
                supportedOperations |= MediaObject.SUPPORT_DC;
                MenuItem dcItem = menu.findItem(R.id.m_action_image_dc);
                ImageDC.setMenuItemTile((Context)mActivity, dcItem);
                
            }
            
        }
        /// @}
        /// M: [BUG.ADD] KK native judge mime type, no need AP judge @{
        if (mCurrentPhoto.getMimeType() == null) {
            supportedOperations &= ~MediaObject.SUPPORT_TRIM;
        }
        /// @}
        /// M: [BUG.ADD] @{
        // reget print system operation 
        PrintHelper printHelper = new PrintHelper(mActivity.getAndroidContext());
        if (!printHelper.systemSupportsPrint()) {
            supportedOperations &= ~MediaObject.SUPPORT_PRINT;
        }
        /// @}
        /// M: [BUG.ADD] can not set as wallpaper when no thumbnail @{
        mSupportedOperations = supportedOperations;
        /// @}
        MenuExecutor.updateMenuOperation(menu, supportedOperations);

        /// M: [FEATURE.ADD] HotKnot @{
        boolean canShareByHotKnot = (supportedOperations & MediaObject.SUPPORT_SHARE) != 0;
        mActivity.getHotKnot().updateMenu(menu, R.id.action_share, R.id.action_hotknot, canShareByHotKnot);
        /// @}
        /// M: [BUG.ADD] supported operations is zero(camera preview), close menu @{
        if (supportedOperations == 0) {
            menu.close();
        }
        /// @}
    }

    private boolean canDoSlideShow() {
        if (mMediaSet == null || mCurrentPhoto == null) {
            return false;
        }
        if (mCurrentPhoto.getMediaType() != MediaObject.MEDIA_TYPE_IMAGE) {
            return false;
        }
        return true;
    }

    //////////////////////////////////////////////////////////////////////////
    //  Action Bar show/hide management
    //////////////////////////////////////////////////////////////////////////

    private void showBars() {
        /// M: [PERF.ADD] for performance auto test@{
        if (mDisableBarChanges) {
            return;
        }
        /// @}
        /// M: [BUG.ADD] automatic layer visibility change @{
        onActionBarVisibilityChange(true);
        /// @}
        if (mShowBars) return;
        mShowBars = true;
        mOrientationManager.unlockOrientation();
        mActionBar.show();
        mActivity.getGLRoot().setLightsOutMode(false);
        /// M: [FEATURE.MODIFY] automatic layer visibility change @{
        // refreshHidingMessage();
        if (mAllowAutoHideByHost) {
            refreshHidingMessage();
        }
        /// @}
        refreshBottomControlsWhenReady();
    }

    private void hideBars() {
        /// M: [DEBUG.ADD] for performance auto test@{
        if (mDisableBarChanges) {
            return;
        }
        /// @}
        /// M: [FEATURE.ADD] automatic layer visibility change @{
        onActionBarVisibilityChange(false);
        /// @}
        if (!mShowBars) return;
        mShowBars = false;
        mActionBar.hide();
        mActivity.getGLRoot().setLightsOutMode(true);
        mHandler.removeMessages(MSG_HIDE_BARS);
        refreshBottomControlsWhenReady();
    }

    private void refreshHidingMessage() {
        mHandler.removeMessages(MSG_HIDE_BARS);
        if (!mIsMenuVisible && !mPhotoView.getFilmMode()) {
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_BARS, HIDE_BARS_TIMEOUT);
        }
    }

    private boolean canShowBars() {
        // No bars if we are showing camera preview.
        if (mAppBridge != null && mCurrentIndex == 0
                && !mPhotoView.getFilmMode()) return false;

        // No bars if it's not allowed.
        if (!mActionBarAllowed) return false;

        Configuration config = mActivity.getResources().getConfiguration();
        if (config.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH) {
            return false;
        }

        return true;
    }

    private void wantBars() {
        if (canShowBars()) showBars();
    }

    private void toggleBars() {
        if (mShowBars) {
            hideBars();
        } else {
            if (canShowBars()) showBars();
        }
    }

    private void updateBars() {
        if (!canShowBars()) {
            hideBars();
        }
        /// M: [BEHAVIOR.ADD] @{
        // Show title at the action bar
        updateActionBarTitle();
        /// @}
    }

    @Override
    protected void onBackPressed() {
        /// M: [BUG.MODIFY] don't need show bars in camera preview @{
        /*showBars();*/
        wantBars();
        /// @}
        if (mShowDetails) {
            hideDetails();
        } else if (mAppBridge == null || !switchWithCaptureAnimation(-1)) {
            // We are leaving this page. Set the result now.
            setResult();
            if (mStartInFilmstrip && !mPhotoView.getFilmMode()) {
                mPhotoView.setFilmMode(true);
            } else if (mTreatBackAsUp) {
                onUpPressed();
            } else {
                super.onBackPressed();
            }
        }
    }

    private void onUpPressed() {
        if ((mStartInFilmstrip || mAppBridge != null)
                && !mPhotoView.getFilmMode()) {
            mPhotoView.setFilmMode(true);
            return;
        }

        if (mActivity.getStateManager().getStateCount() > 1) {
            setResult();
            super.onBackPressed();
            return;
        }

        if (mOriginalSetPathString == null) return;

        if (mAppBridge == null) {
            // We're in view mode so set up the stacks on our own.
            Bundle data = new Bundle(getData());
            data.putString(AlbumPage.KEY_MEDIA_PATH, mOriginalSetPathString);
            data.putString(AlbumPage.KEY_PARENT_MEDIA_PATH,
                    mActivity.getDataManager().getTopSetPath(
                            DataManager.INCLUDE_ALL));
            /// M: [FEATURE.ADD] @{
            if (null == mCurrentPhoto) {
                mActivity.getStateManager().switchState(this, AlbumPage.class, data);
                return;
            }
            MediaData md = mCurrentPhoto.getMediaData();
            if (md.mediaType == MediaData.MediaType.NORMAL
                    && md.subType == MediaData.SubType.CONSHOT) {
                PlatformHelper.switchToContainerPage(mActivity, md, false, data);
                return;
            } else
            /// @}
                mActivity.getStateManager().switchState(this, AlbumPage.class, data);
        } else {
            GalleryUtils.startGalleryActivity(mActivity);
        }
    }

    private void setResult() {
        Intent result = null;
        result = new Intent();
        result.putExtra(KEY_RETURN_INDEX_HINT, mCurrentIndex);
        setStateResult(Activity.RESULT_OK, result);
    }

    //////////////////////////////////////////////////////////////////////////
    //  AppBridge.Server interface
    //////////////////////////////////////////////////////////////////////////

    @Override
    public void setCameraRelativeFrame(Rect frame) {
        mPhotoView.setCameraRelativeFrame(frame);
    }

    @Override
    public boolean switchWithCaptureAnimation(int offset) {
        /// M: [BUG.ADD] from camera to gallery first box must be ready @{
        Log.d(TAG, "<switchWithCaptureAnimation> offset=" + offset
                + ", orig set path=" + mOriginalSetPathString);
        if (1 == offset && !mPhotoView.isFirstBoxReady()) {
            return false;
        }
        /// @}
        return mPhotoView.switchWithCaptureAnimation(offset);
    }

    @Override
    public void setSwipingEnabled(boolean enabled) {
        mPhotoView.setSwipingEnabled(enabled);
    }

    @Override
    public void notifyScreenNailChanged() {
        mScreenNailItem.setScreenNail(mAppBridge.attachScreenNail());
        mScreenNailSet.notifyChange();
    }

    @Override
    public void addSecureAlbumItem(boolean isVideo, int id) {
        mSecureAlbum.addMediaItem(isVideo, id);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        mActionBar.createActionBarMenu(R.menu.photo, menu);
        /// M: [FEATURE.ADD] menu extension @{
        mPhotoView.onCreateOptionsMenu(menu);
        /// @}
        mHaveImageEditor = GalleryUtils.isEditorAvailable(mActivity, "image/*");
        /// M: [FEATURE.ADD] HotKnot @{
        mActivity.getHotKnot().updateMenu(menu, R.id.action_share, R.id.action_hotknot, false);
        /// @}
        updateMenuOperations();
        /// M: [BUG.MODIFY] @{
        /* mActionBar.setTitle(mMediaSet != null ? mMediaSet.getName() : ""); */
        // Show title at the action bar
        updateActionBarTitle();
        /// @}
        return true;
    }

    private MenuExecutor.ProgressListener mConfirmDialogListener =
            new MenuExecutor.ProgressListener() {
        @Override
        public void onProgressUpdate(int index) {}

        @Override
        public void onProgressComplete(int result) {}

        @Override
        public void onConfirmDialogShown() {
            mHandler.removeMessages(MSG_HIDE_BARS);
        }

        @Override
        public void onConfirmDialogDismissed(boolean confirmed) {
            refreshHidingMessage();
        }

        @Override
        public void onProgressStart() {}
    };

    private void switchToGrid() {
        /// M: [BUG.MODIFY] @{
        // For case 1: AlbumSetPage >> AlbumPage >> AlbumSetPage >> (AlbumPage , PhotoPage)
        // For case 2: ContainerPage >> PhotoPage >> film mode >> grid mode
        /*  if (mActivity.getStateManager().hasStateClass(AlbumPage.class)) {
         */
        if (mActivity.getStateManager().hasStateClassInNearPosition(AlbumPage.class)
                || mActivity.getStateManager().hasStateClassInNearPosition(ContainerPage.class)) {
        /// @}
            onUpPressed();
        } else {
            if (mOriginalSetPathString == null) return;
            Bundle data = new Bundle(getData());
            data.putString(AlbumPage.KEY_MEDIA_PATH, mOriginalSetPathString);
            data.putString(AlbumPage.KEY_PARENT_MEDIA_PATH,
                    mActivity.getDataManager().getTopSetPath(
                            DataManager.INCLUDE_ALL));

            // We only show cluster menu in the first AlbumPage in stack
            // TODO: Enable this when running from the camera app
            boolean inAlbum = mActivity.getStateManager().hasStateClass(AlbumPage.class);
            data.putBoolean(AlbumPage.KEY_SHOW_CLUSTER_MENU, !inAlbum
                    && mAppBridge == null);

            data.putBoolean(PhotoPage.KEY_APP_BRIDGE, mAppBridge != null);

            // Account for live preview being first item
            mActivity.getTransitionStore().put(KEY_RETURN_INDEX_HINT,
                    mAppBridge != null ? mCurrentIndex - 1 : mCurrentIndex);

            if (mHasCameraScreennailOrPlaceholder && mAppBridge != null) {
                mActivity.getStateManager().startState(AlbumPage.class, data);
            } else {
                mActivity.getStateManager().switchState(this, AlbumPage.class, data);
            }
        }
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        if (mModel == null) return true;
        refreshHidingMessage();
        MediaItem current = mModel.getMediaItem(0);

        // This is a shield for monkey when it clicks the action bar
        // menu when transitioning from filmstrip to camera
        if (current instanceof SnailItem) return true;
        // TODO: We should check the current photo against the MediaItem
        // that the menu was initially created for. We need to fix this
        // after PhotoPage being refactored.
        if (current == null) {
            // item is not ready, ignore
            return true;
        }
        int currentIndex = mModel.getCurrentIndex();
        Path path = current.getPath();

        DataManager manager = mActivity.getDataManager();
        int action = item.getItemId();
        /// M: [BUG.ADD] show toast before PhotoDataAdapter finishing loading to avoid JE @{
        if (action != android.R.id.home && !mLoadingFinished && mSetPathString != null) {
            Toast.makeText(mActivity, mActivity.getString(R.string.please_wait),
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        /// @}
        String confirmMsg = null;
        switch (action) {
            case android.R.id.home: {
                onUpPressed();
                return true;
            }
            case R.id.action_slideshow: {
                Bundle data = new Bundle();
                /// M: [BUG.MODIFY] fix bug: slideshow doesn't play again
                // when finish playing the last picture @{
                String mediaSetPath = mMediaSet.getPath().toString();
                if (mSnailSetPath != null) {
                    mediaSetPath = mediaSetPath.replace(mSnailSetPath + ",", "");
                    Log.i(TAG, "<onItemSelected> action_slideshow | mediaSetPath: "
                            + mediaSetPath);
                }
                /*data.putString(SlideshowPage.KEY_SET_PATH, mMediaSet.getPath().toString());*/
                data.putString(SlideshowPage.KEY_SET_PATH, mediaSetPath);
                /// @}
                data.putString(SlideshowPage.KEY_ITEM_PATH, path.toString());
                /// M: [BUG.ADD] currentIndex-- if it is in camera folder @{
                if (mHasCameraScreennailOrPlaceholder) {
                    currentIndex--;
                }
                /// @}

                data.putInt(SlideshowPage.KEY_PHOTO_INDEX, currentIndex);
                data.putBoolean(SlideshowPage.KEY_REPEAT, true);
                mActivity.getStateManager().startStateForResult(
                        SlideshowPage.class, REQUEST_SLIDESHOW, data);
                return true;
            }
            case R.id.action_crop: {
                /// M: [BUG.ADD] disable cropping photo when sdcard is full. @{
                File srcFile = new File(current.getFilePath());
                if (!isSpaceEnough(srcFile)) {
                    Log.i(TAG, "<onItemSelected> abort cropping photo when no enough space!");
                    Toast.makeText(mActivity,
                            mActivity.getString(R.string.storage_not_enough),
                            Toast.LENGTH_SHORT)
                            .show();
                    return true;
                }
                /// @}
                Activity activity = mActivity;
                Intent intent = new Intent(CropActivity.CROP_ACTION);
                intent.setClass(activity, CropActivity.class);
                intent.setDataAndType(manager.getContentUri(path), current.getMimeType())
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivityForResult(intent, PicasaSource.isPicasaImage(current)
                        ? REQUEST_CROP_PICASA
                        : REQUEST_CROP);
                return true;
            }
            case R.id.action_trim: {
                Intent intent = new Intent(mActivity, TrimVideo.class);
                intent.setData(manager.getContentUri(path));
                // We need the file path to wrap this into a RandomAccessFile.
                intent.putExtra(KEY_MEDIA_ITEM_PATH, current.getFilePath());
                /// M: [FEATURE.ADD] SlideVideo@{
                if (FeatureConfig.supportSlideVideoPlay) {
                    intent.putExtra(TrimVideo.KEY_COME_FROM_GALLERY, true);
                }
                /// @}
                mActivity.startActivityForResult(intent, REQUEST_TRIM);
                return true;
            }
            case R.id.action_mute: {
                /// M: [BUG.ADD] disable muting video when sdcard is full. @{
                File srcFile = new File(current.getFilePath());
                if (!isSpaceEnough(srcFile)) {
                    Log.i(TAG, "<onItemSelected> abort muting video when no enough space!");
                    Toast.makeText(mActivity,
                            mActivity.getString(R.string.storage_not_enough),
                            Toast.LENGTH_SHORT)
                            .show();
                    return true;
                }
                /// @}
                mMuteVideo = new MuteVideo(current.getFilePath(),
                        manager.getContentUri(path), mActivity);
                mMuteVideo.muteInBackground();
                /// M: [FEATURE.ADD] SlideVideo@{
                mMuteVideo.setMuteDoneListener(new MuteDoneListener() {
                    public void onMuteDone(Uri uri) {
                        redirectCurrentMedia(uri, false);
                    }
                });
                /// @}
                return true;
            }
            case R.id.action_edit: {
                /// M: [BUG.ADD] disable editing photo when sdcard is full. @{
                File srcFile = new File(current.getFilePath());
                if (!isSpaceEnough(srcFile)) {
                    Log.i(TAG, "<onItemSelected> abort editing photo when no enough space!");
                    Toast.makeText(mActivity,
                            mActivity.getString(R.string.storage_not_enough),
                            Toast.LENGTH_SHORT)
                            .show();
                    return true;
                }
                /// @}
                launchPhotoEditor();
                return true;
            }
            /// M: [FEATURE.ADD] @{
            case R.id.m_action_picture_quality: {
                Activity activity = (Activity) mActivity;
                Intent intent = new Intent(PictureQualityActivity.ACTION_PQ);
                intent.setClass(activity, PictureQualityActivity.class);
                intent.setData(manager.getContentUri(path));
                Bundle pqBundle = new Bundle();
                pqBundle.putString("PQUri", manager.getContentUri(path).toString());
                pqBundle.putString("PQMineType", current.getMimeType());
                pqBundle.putInt("PQViewWidth", mPhotoView.getWidth());
                pqBundle.putInt("PQViewHeight", mPhotoView.getHeight());
                intent.putExtras(pqBundle);
                Log.i(TAG, "<onItemSelected>startActivity PQ");
                activity.startActivityForResult(intent, REQUEST_PQ);
                return true;
            }
            case R.id.m_action_image_dc: {
                /*
                item.setTitle(title);
                Activity activity = (Activity) mActivity;
                Intent intent = new Intent();
                intent.setClass(activity, ImageDCActivity.class);
                Log.i(TAG, "<onItemSelected>startActivity ImageDC");
                activity.startActivityForResult(intent, ImageDCActivity.REQUEST_DC);
                */
                ImageDC.resetStatus((Context)mActivity);
                ImageDC.setMenuItemTile((Context)mActivity, item);
                path.clearObject();
                mActivity.getDataManager().forceRefreshAll();
                Log.d(TAG, "< onStateResult > forceRefreshAll~~");
                return true;
            }
            /// @}
            case R.id.action_simple_edit: {
                launchSimpleEditor();
                return true;
            }
            case R.id.action_details: {
                if (mShowDetails) {
                    hideDetails();
                } else {
                    showDetails();
                }
                return true;
            }
            case R.id.print: {
                mActivity.printSelectedImage(manager.getContentUri(path));
                return true;
            }
            case R.id.action_delete:
                confirmMsg = mActivity.getResources().getQuantityString(
                        R.plurals.delete_selection, 1);
            case R.id.action_setas:
            case R.id.action_rotate_ccw:
            case R.id.action_rotate_cw:
            case R.id.action_show_on_map:
                mSelectionManager.deSelectAll();
                mSelectionManager.toggle(path);
                mMenuExecutor.onMenuClicked(item, confirmMsg, mConfirmDialogListener);
                return true;
            /// M: [FEATURE.ADD] DRM & HotKnot @{
            case R.id.m_action_protect_info:
                Log.d(TAG, "<onItemSelected> ProtectionInfo: do action_protection_info");
                DrmHelper.showProtectionInfoDialog((Activity)mActivity,
                        manager.getContentUri(path));
                return true;
            case R.id.action_hotknot:
                Log.d(TAG, "<onItemSelected> HotKnot: do action_hotknot");
                // for conshot & motion, may share a group image, so getContentUris()
                Uri[] uris = mCurrentPhoto.getExtItem().getContentUris();
                if (uris != null) {
                    mActivity.getHotKnot().sendZip(uris);
                } else {
                    Uri contentUri = mCurrentPhoto.getContentUri();
                    mActivity.getHotKnot().sendUri(contentUri, mCurrentPhoto.getMimeType());
                }
                return true;
            /// @}
            /// M: [FEATURE.ADD] entry to export as video @{
            case R.id.action_export:
                mAnimatedContentSharer.exportCurrentPhoto();
                return true;
            /// @}
            /// M: [FEATURE.ADD] Support BlueTooth print feature.@{
            case R.id.action_print:
                mSelectionManager.deSelectAll();
                mSelectionManager.toggle(path);
                mMenuExecutor.onMenuClicked(item, confirmMsg, mConfirmDialogListener);
                return true;
            /// @}
            default :
                /// M: [FEATURE.ADD] menu extension @{
                // return false;
                return mPhotoView.onOptionsItemSelected(item);
                /// @}
        }
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
    }

    private void showDetails() {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, mRootPane, new MyDetailsSource());
            mDetailsHelper.setCloseListener(new CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.show();
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Callbacks from PhotoView
    ////////////////////////////////////////////////////////////////////////////
    @Override
    /// M: [BUG.MODIFY] @{
    /* public void onSingleTapUp(int x, int y) {*/
    public void onSingleTapConfirmed(int x, int y) {
        Log.i(TAG, "<onSingleTapConfirmed>");
    /// @}
        if (mAppBridge != null) {
            if (mAppBridge.onSingleTapUp(x, y)) return;
        }

        MediaItem item = mModel.getMediaItem(0);
        if (item == null || item == mScreenNailItem) {
            // item is not ready or it is camera preview, ignore
            return;
        }

        int supported = item.getSupportedOperations();
        /// M: [BUG.MARK] play video in onSingleTapUp @{
        /* boolean playVideo = ((supported & MediaItem.SUPPORT_PLAY) != 0); */
        /// @}
        boolean unlock = ((supported & MediaItem.SUPPORT_UNLOCK) != 0);
        boolean goBack = ((supported & MediaItem.SUPPORT_BACK) != 0);
        boolean launchCamera = ((supported & MediaItem.SUPPORT_CAMERA_SHORTCUT) != 0);

        /// M: [BUG.MARK] play video in onSingleTapUp @{
        /*
        if (playVideo) {
            // determine if the point is at center (1/6) of the photo view.
            // (The position of the "play" icon is at center (1/6) of the photo)
            int w = mPhotoView.getWidth();
            int h = mPhotoView.getHeight();
            playVideo = (Math.abs(x - w / 2) * 12 <= w)
                && (Math.abs(y - h / 2) * 12 <= h);
        }

        if (playVideo) {
            if (mSecureAlbum == null) {
                playVideo(mActivity, item.getPlayUri(), item.getName());
            } else {
                mActivity.getStateManager().finishState(this);
            }
        } else 
        */
        /// @}
        if (goBack) {
            onBackPressed();
        } else if (unlock) {
            Intent intent = new Intent(mActivity, GalleryActivity.class);
            /// M:[BUG.MARK]secret camera come here, use new task @{
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            /// @}
            intent.putExtra(GalleryActivity.KEY_DISMISS_KEYGUARD, true);
            mActivity.startActivity(intent);
        } else if (launchCamera) {
            /// M: [BUG.MODIFY] to resolve problem that can't exit camera @{ 
            /*launchCamera();*/
            onPictureCenter(true);
            /// }@
        } else {
            /// M: [BUG.MODIFY] @{
            /*toggleBars();*/
            // if we are starting video player, give up toggleBars() to avoid view junk
            if (!mIsStartingVideoPlayer) {
                toggleBars();
            }
            /// @}
        }
    }

    @Override
    public void onActionBarAllowed(boolean allowed) {
        mActionBarAllowed = allowed;
        mHandler.sendEmptyMessage(MSG_UPDATE_ACTION_BAR);
    }

    @Override
    public void onActionBarWanted() {
        mHandler.sendEmptyMessage(MSG_WANT_BARS);
    }

    @Override
    public void onFullScreenChanged(boolean full) {
        Message m = mHandler.obtainMessage(
                MSG_ON_FULL_SCREEN_CHANGED, full ? 1 : 0, 0);
        m.sendToTarget();
    }

    // How we do delete/undo:
    //
    // When the user choose to delete a media item, we just tell the
    // FilterDeleteSet to hide that item. If the user choose to undo it, we
    // again tell FilterDeleteSet not to hide it. If the user choose to commit
    // the deletion, we then actually delete the media item.
    @Override
    public void onDeleteImage(Path path, int offset) {
        onCommitDeleteImage();  // commit the previous deletion
        mDeletePath = path;
        mDeleteIsFocus = (offset == 0);
        /// M: [BUG.MODIFY] @{
        // mCurrentIndex would always be 0 if you never slide medias after you enter
        // Gallery by clicking one media from other applications like file manager.
        // Another example can be found in ALPS00419381
        // mMediaSet.addDeletion(path, mCurrentIndex + offset);
        mMediaSet.addDeletion(path, mModel.getCurrentIndex() + offset);
        /// @}
    }

    @Override
    public void onUndoDeleteImage() {
        if (mDeletePath == null) return;
        // If the deletion was done on the focused item, we want the model to
        // focus on it when it is undeleted.
        if (mDeleteIsFocus) mModel.setFocusHintPath(mDeletePath);
        mMediaSet.removeDeletion(mDeletePath);
        mDeletePath = null;
    }

    @Override
    public void onCommitDeleteImage() {
        if (mDeletePath == null) return;
        mMenuExecutor.startSingleItemAction(R.id.action_delete, mDeletePath);
        mDeletePath = null;
    }

    public void playVideo(Activity activity, Uri uri, String title) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "video/*")
                    .putExtra(Intent.EXTRA_TITLE, title)
                    .putExtra(MovieActivity.KEY_TREAT_UP_AS_BACK, true)
            /// M: [BUG.ADD] @{
                    .putExtra(MovieActivity.KEY_COME_FROM_CAMERA, mAppBridge != null);
            intent.putExtra(FeatureHelper.EXTRA_ENABLE_VIDEO_LIST, true);
            /// @}
            activity.startActivityForResult(intent, REQUEST_PLAY_VIDEO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.video_err),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void setCurrentPhotoByIntent(Intent intent) {
        if (intent == null) return;
        Path path = mApplication.getDataManager()
                .findPathByUri(intent.getData(), intent.getType());
        if (path != null) {
            Path albumPath = mApplication.getDataManager().getDefaultSetOf(path);
            if (albumPath == null) {
                return;
            }
            if (!albumPath.equalsIgnoreCase(mOriginalSetPathString)) {
                // If the edited image is stored in a different album, we need
                // to start a new activity state to show the new image
                Bundle data = new Bundle(getData());
                data.putString(KEY_MEDIA_SET_PATH, albumPath.toString());
                data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, path.toString());
                mActivity.getStateManager().startState(SinglePhotoPage.class, data);
                return;
            }
            mModel.setCurrentPhoto(path, mCurrentIndex);
            /// M: [BUG.ADD] add picture, need update camera thumbnail @{
            mActivity.getDataManager().broadcastUpdatePicture();
            /// @}
        }
    }

    @Override
    protected void onStateResult(int requestCode, int resultCode, Intent data) {
        /// M: [BUG.ADD] mark we are not starting video player, to re-enable toggleBars() @{
        mIsStartingVideoPlayer = false;
        /// @}
        /// M: [BUG.ADD] send broadcast to camera to update thumbnail
        // without check resultCode cause AMS can not pass resultCode
        // if start FilterShowActivity in new task @{
        if (requestCode == REQUEST_EDIT) {
            mActivity.getDataManager().broadcastUpdatePicture();
            Log.i(TAG, "<onStateResult> send broadcast to camera to update thumbnail");
        }
        /// @}
        /// M: [BUG.MODIFY] @{
        /*if (resultCode == Activity.RESULT_CANCELED) {*/
        if (resultCode == Activity.RESULT_CANCELED && requestCode != REQUEST_PLAY_VIDEO) {
        /// @}
            // This is a reset, not a canceled
            return;
        }
        mRecenterCameraOnResume = false;
        switch (requestCode) {
            case REQUEST_EDIT:
                setCurrentPhotoByIntentEx(data);
                break;
            /// M: [FEATURE.ADD] SlideVideo@{
            case REQUEST_TRIM:
                if (data != null) {
                    redirectCurrentMedia(data.getData(), false);
                }
                break;
            /// @}
            case REQUEST_CROP:
                if (resultCode == Activity.RESULT_OK) {
                    setCurrentPhotoByIntentEx(data);
                }
                break;
            case REQUEST_CROP_PICASA: {
                if (resultCode == Activity.RESULT_OK) {
                    Context context = mActivity.getAndroidContext();
                    String message = context.getString(R.string.crop_saved,
                            context.getString(R.string.folder_edited_online_photos));
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case REQUEST_SLIDESHOW: {
                if (data == null) break;
                String path = data.getStringExtra(SlideshowPage.KEY_ITEM_PATH);
                int index = data.getIntExtra(SlideshowPage.KEY_PHOTO_INDEX, 0);
                if (path != null) {
                    mModel.setCurrentPhoto(Path.fromString(path), index);
                }
            }
            /// M: [FEATURE.ADD] added for Image refocus.@{
            case REQUEST_REFOCUS: {
                if (resultCode == Activity.RESULT_OK) {
                    setRefocusCurrentPhotoByIntent(data);
                }
            }
            /// @}
            /// M: [FEATURE.ADD] @{
            case ImageDCActivity.REQUEST_DC :{
                if (resultCode == Activity.RESULT_OK) {
                    mActivity.getDataManager().forceRefreshAll();
                    Log.d(TAG, "< onStateResult > forceRefreshAll~~");
                }
            }
            /// @}
            /// M: [FEATURE.ADD] @{
            default: {
                if (mExtActivityResultListener != null) {
                mExtActivityResultListener.onActivityResult(requestCode,
                        resultCode, data);
                mExtActivityResultListener = null;
                }
            }
            /// @}
        }
    }

    @Override
    public void onPause() {
        /// M: [DEBUG.ADD] @{
        Log.i(TAG, "<onPause> begin");
        /// @}
        super.onPause();
        mIsActive = false;
        if (mMuteVideo != null) {
            mMuteVideo.setMuteHasPaused(true);
        }
        /// M: [FEATURE.ADD] VTSP: share as video @{
        if (mAnimatedContentSharer != null) {
            // mAnimatedContentSharer == null if onCreate() returned before
            // call initAnimatedContentSharer()
            mAnimatedContentSharer.onPause();
        }
        /// @}

        mActivity.getGLRoot().unfreeze();
        mHandler.removeMessages(MSG_UNFREEZE_GLROOT);

        DetailsHelper.pause();
        // Hide the detail dialog on exit
        if (mShowDetails) hideDetails();
        if (mModel != null) {
            mModel.pause();
        }
        mPhotoView.pause();
        mHandler.removeMessages(MSG_HIDE_BARS);
        mHandler.removeMessages(MSG_REFRESH_BOTTOM_CONTROLS);
        refreshBottomControlsWhenReady();
        mActionBar.removeOnMenuVisibilityListener(mMenuVisibilityListener);
        if (mShowSpinner) {
            mActionBar.disableAlbumModeMenu(true);
        }
        onCommitDeleteImage();
        mMenuExecutor.pause();
        if (mMediaSet != null) {
            mMediaSet.clearDeletion();
            /// M: [BUG.ADD] @{
            // ContentListener had been removed,so should reset deletion
            mMediaSet.resetDeletion();
            /// @}
        }
        /// M: [DEBUG.ADD] @{
        Log.i(TAG, "<onPause> end");
        /// @}
    }

    @Override
    public void onCurrentImageUpdated() {
        mActivity.getGLRoot().unfreeze();
    }

    @Override
    public void onFilmModeChanged(boolean enabled) {
        refreshBottomControlsWhenReady();
        if (mShowSpinner) {
            if (enabled) {
                mActionBar.enableAlbumModeMenu(
                        GalleryActionBar.ALBUM_FILMSTRIP_MODE_SELECTED, this);
            } else {
                mActionBar.disableAlbumModeMenu(true);
            }
        }
        if (enabled) {
            mHandler.removeMessages(MSG_HIDE_BARS);
            UsageStatistics.onContentViewChanged(
                    UsageStatistics.COMPONENT_GALLERY, "FilmstripPage");
        } else {
            refreshHidingMessage();
            if (mAppBridge == null || mCurrentIndex > 0) {
                UsageStatistics.onContentViewChanged(
                        UsageStatistics.COMPONENT_GALLERY, "SinglePhotoPage");
            } else {
                UsageStatistics.onContentViewChanged(
                        UsageStatistics.COMPONENT_CAMERA, "Unknown"); // TODO
            }
        }
        /// M: [BEHAVIOR.ADD] @{
        // Show title at the action bar
        updateActionBarTitle();
        /// @}
    }

    private void transitionFromAlbumPageIfNeeded() {
        TransitionStore transitions = mActivity.getTransitionStore();

        int albumPageTransition = transitions.get(
                KEY_ALBUMPAGE_TRANSITION, MSG_ALBUMPAGE_NONE);

        if (albumPageTransition == MSG_ALBUMPAGE_NONE && mAppBridge != null
                && mRecenterCameraOnResume) {
            // Generally, resuming the PhotoPage when in Camera should
            // reset to the capture mode to allow quick photo taking
            mCurrentIndex = 0;
            mPhotoView.resetToFirstPicture();
        } else {
            int resumeIndex = transitions.get(KEY_INDEX_HINT, -1);
            if (resumeIndex >= 0) {
                if (mHasCameraScreennailOrPlaceholder) {
                    // Account for preview/placeholder being the first item
                    resumeIndex++;
                }
                if (resumeIndex < mMediaSet.getMediaItemCount()) {
                    mCurrentIndex = resumeIndex;
                    mModel.moveTo(mCurrentIndex);
                }
            }
        }

        if (albumPageTransition == MSG_ALBUMPAGE_RESUMED) {
            mPhotoView.setFilmMode(mStartInFilmstrip || mAppBridge != null);
        } else if (albumPageTransition == MSG_ALBUMPAGE_PICKED) {
            mPhotoView.setFilmMode(false);
        }
    }

    @Override
    protected void onResume() {
        /// M: [DEBUG.ADD] @{
        Log.i(TAG, "<onResume> begin");
        /// @}
        super.onResume();
        if (mMuteVideo != null) {
            mMuteVideo.setMuteHasPaused(false);
            mMuteVideo.needPlayMuteVideo();
        }
        /// M: [BUG.ADD] @{
        mCanSlideToPrePicture = true;
        mActivity.getGLRoot().setOrientationSource(mOrientationManager);
        /// @}
        if (mModel == null) {
            /// M: [BUG.ADD] pause PhotoView before finish PhotoPage @{
            mPhotoView.pause();
            /// @}
            mActivity.getStateManager().finishState(this);
            return;
        }
        transitionFromAlbumPageIfNeeded();

        mActivity.getGLRoot().freeze();
        mIsActive = true;
        /// M: [FEATURE.ADD] VTSP: share as video @{
        mAnimatedContentSharer.onResume();
        /// @}
        setContentPane(mRootPane);

        mModel.resume();
        mPhotoView.resume();
        /// M: [BUG.MARK] @{
        /*mActionBar.setDisplayOptions(
         ((mSecureAlbum == null) && (mSetPathString != null)), false);*/
        /// @}
        mActionBar.addOnMenuVisibilityListener(mMenuVisibilityListener);
        /// M: [BEHAVIOR.ADD] @{
        // Show title at the action bar
        updateActionBarTitle();
        /// @}
        /// M: [BUG.MARK] do this in onLoadingFinished @{
        /* refreshBottomControlsWhenReady();*/
        /// @}
        if (mShowSpinner && mPhotoView.getFilmMode()) {
            mActionBar.enableAlbumModeMenu(
                    GalleryActionBar.ALBUM_FILMSTRIP_MODE_SELECTED, this);
        }
        if (!mShowBars) {
            mActionBar.hide();
            /// M: [BUG.MODIFY] @{
            /*mActivity.getGLRoot().setLightsOutMode(true);*/
            if (mAppBridge != null && mCurrentIndex == 0
                    && !mPhotoView.getFilmMode()) {
                mActivity.getGLRoot().setLightsOutMode(false);
            } else {
                mActivity.getGLRoot().setLightsOutMode(true);
            }
            /// @}
        }
        boolean haveImageEditor = GalleryUtils.isEditorAvailable(mActivity, "image/*");
        if (haveImageEditor != mHaveImageEditor) {
            mHaveImageEditor = haveImageEditor;
            updateMenuOperations();
        }

        /// M: [BUG.MODIFY] @{
        /*mRecenterCameraOnResume = true;*/
        mRecenterCameraOnResume = false;
        /// @}
        mHandler.sendEmptyMessageDelayed(MSG_UNFREEZE_GLROOT, UNFREEZE_GLROOT_TIMEOUT);

        /// M: [BUG.ADD] allow action bar auto-hide @{
        refreshHidingMessage();
        /// @}
        /// M: [DEBUG.ADD] @{
        Log.i(TAG, "<onResume> end");
        /// @}
    }

    @Override
    protected void onDestroy() {
        if (mAppBridge != null) {
            mAppBridge.setServer(null);
            mScreenNailItem.setScreenNail(null);
            mAppBridge.detachScreenNail();
            mAppBridge = null;
            mScreenNailSet = null;
            mScreenNailItem = null;
        }
        /// M: [BUG.ADD] @{
        // avoid PhotoPage memory leak, remove listener of OrientationManager
        mOrientationManager.removeListener(this);
        /// @}
        mActivity.getGLRoot().setOrientationSource(null);
        if (mBottomControls != null) mBottomControls.cleanup();

        // Remove all pending messages.
        /// M: [BUG.MODIFY] @{
        //mHandler.removeCallbacksAndMessages(null);
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        /// @}
        /// M: [FEATURE.ADD] @{
        mPhotoView.destroy();
        /// @}
        /// M: [BUG.ADD] clear mNfcPushUris[0] for NFC when leave photo page @{
        mNfcPushUris[0] = null;
        /// @}
        // / M: [BUG.ADD] @{
        // Google bug fix,mute dialog should be dismiss before gallery activity
        // destroyed.
        if (mMuteVideo != null) {
            mMuteVideo.cancelMute();
        }
        // / @}
        super.onDestroy();
    }

    private class MyDetailsSource implements DetailsSource {

        @Override
        public MediaDetails getDetails() {
            return mModel.getMediaItem(0).getDetails();
        }

        @Override
        public int size() {
            /// M: [FEATURE.MODIFY] do not count camera item when show detail @{
            //return mMediaSet != null ? mMediaSet.getMediaItemCount() : 1;
            return mMediaSet != null ? (mHasCameraScreennailOrPlaceholder ? mMediaSet.getMediaItemCount() - 1 : mMediaSet.getMediaItemCount()) : 1;
            /// @}
        }

        @Override
        public int setIndex() {
            /// M: [FEATURE.MODIFY] do not count camera item when show detail @{
            //return mModel.getCurrentIndex();
            return mHasCameraScreennailOrPlaceholder ? mModel.getCurrentIndex()-1 : mModel.getCurrentIndex();
            /// @}
        }
    }

    @Override
    public void onAlbumModeSelected(int mode) {
        if (mode == GalleryActionBar.ALBUM_GRID_MODE_SELECTED) {
            switchToGrid();
        }
    }

    @Override
    public void refreshBottomControlsWhenReady() {
        if (mBottomControls == null) {
            return;
        }
        MediaObject currentPhoto = mCurrentPhoto;
        if (currentPhoto == null) {
            mHandler.obtainMessage(MSG_REFRESH_BOTTOM_CONTROLS, 0, 0, currentPhoto).sendToTarget();
        } else {
            currentPhoto.getPanoramaSupport(mRefreshBottomControlsCallback);
        }
    }

    private void updatePanoramaUI(boolean isPanorama360) {
        Menu menu = mActionBar.getMenu();

        // it could be null if onCreateActionBar has not been called yet
        if (menu == null) {
            return;
        }

        MenuExecutor.updateMenuForPanorama(menu, isPanorama360, isPanorama360);

        if (isPanorama360) {
            MenuItem item = menu.findItem(R.id.action_share);
            if (item != null) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                item.setTitle(mActivity.getResources().getString(R.string.share_as_photo));
            }
        } else if ((mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_SHARE) != 0) {
            MenuItem item = menu.findItem(R.id.action_share);
            if (item != null) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                item.setTitle(mActivity.getResources().getString(R.string.share));
            }
        }
    }

    @Override
    public void onUndoBarVisibilityChanged(boolean visible) {
        refreshBottomControlsWhenReady();
    }

/// M: [FEATURE.MARK] @{
/*    @Override
    public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
        final long timestampMillis = mCurrentPhoto.getDateInMs();
        final String mediaType = getMediaTypeString(mCurrentPhoto);
        UsageStatistics.onEvent(UsageStatistics.COMPONENT_GALLERY,
                UsageStatistics.ACTION_SHARE,
                mediaType,
                        timestampMillis > 0
                        ? System.currentTimeMillis() - timestampMillis
                        : -1);
        /// M: [FEATURE.ADD] VTSP: share as video @{
        mAnimatedContentSharer.onShareTargetSelected(intent);
        /// @}
        return false;
    }*/
/// @}

    private static String getMediaTypeString(MediaItem item) {
        if (item.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO) {
            return "Video";
        } else if (item.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE) {
            return "Photo";
        } else {
            return "Unknown:" + item.getMediaType();
        }
    }

    //********************************************************************
    //*                              MTK                                 *
    //********************************************************************

    public boolean mLoadingFinished = false;
    private long mCurrentVersion;

    public void disableBarChanges(boolean disable) {
        mDisableBarChanges = disable;
    }

    private void updateScaleGesture() {
        if (mCurrentPhoto == null) {
            return;
        }
        // when current photo is video & slide video is disabled, disable scale
        if (mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO
                && !FeatureConfig.supportSlideVideoPlay) {
            mPhotoView.setScalingEnabled(false);
        } else {
            mPhotoView.setScalingEnabled(true);
        }
    }

    /// M: [FEATURE.ADD] VTSP: share as video @{
    AnimatedContentSharer mAnimatedContentSharer;

    private void initAnimatedContentSharer() {
        // animation sharer should be null here. here just for test
        if (mAnimatedContentSharer == null) {
            mAnimatedContentSharer = new AnimatedContentSharer(mActivity,
                    PhotoPlayFacade.getMediaCenter(),
                    new AnimatedContentSharer.IMediaDataGetter() {
                        public MediaData getMediaData() {
                            if (mCurrentPhoto != null) {
                                return mCurrentPhoto.getMediaData();
                            }
                            return null;
                        }

                        public String getLocalizedFolderName() {
                            String albumName = "";
                            if (mCurrentPhoto instanceof LocalMediaItem) {
                                albumName = LocalAlbum.getLocalizedName(
                                        mActivity.getResources(),
                                        ((LocalMediaItem) mCurrentPhoto).bucketId,
                                        mMediaSet.getName());
                            }
                            return albumName;
                        }

                        public void redirectCurrentMedia(Uri uri, boolean fromActivityResult) {
                            PhotoPage.this.redirectCurrentMedia(uri, fromActivityResult);
                        }

                        /// M: [BUG.ADD] @{
                        public void setShareUri(Uri uri) {
                            Log.d(TAG, "<initAnimatedContentSharer> setShareUri() uri " + uri);
                            mShareUriFromChooserView = uri;
                        }
                        /// @}
                    });
        }
    }
    /// @}

    protected void onSaveState(Bundle outState) {
        // keep record of current index and current photo
        mData.putInt(KEY_INDEX_HINT, mCurrentIndex);
        if (mCurrentPhoto != null) {
            Path photoPath = mCurrentPhoto.getPath();
            if (photoPath != null) {
                mData.putString(KEY_MEDIA_ITEM_PATH, photoPath.toString());
            }
        }
    }

    public void redirectCurrentMedia(Uri uri, boolean fromActivityResult) {
        Log.d(TAG, "<redirectCurrentMedia> uri=" + uri + ", fromActivity=" + fromActivityResult);
        if (uri == null) {
            Log.e(TAG, "<redirectCurrentMedia> redirect current media, null uri");
            return;
        }
        final Intent intent = new Intent().setData(uri);

        if (fromActivityResult) {
            setCurrentPhotoByIntentEx(intent);
        } else {
            // switch photo to avoid un-predicated jump error
            // the switch method need sth. about camera, so I just wait camera's patch first
            Path path = mApplication.getDataManager().findPathByUri(
                    intent.getData(), intent.getType());
            Log.d(TAG, "<redirectCurrentMedia> type=" + intent.getType() + ", path=" + path);
            if (path != null) {
                mData.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, path.toString());
                mData.putBoolean(PhotoPage.KEY_FORCE_IN_CAMERA_PREVIEW, false);
                mData.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP, mPhotoView.getFilmMode());
                mData.putInt(PhotoPage.KEY_INDEX_HINT, mCurrentIndex);
                /// M: [BUG.ADD] add picture, need update camera thumbnail @{
                mActivity.getDataManager().broadcastUpdatePicture();
                /// @}
                Log.d(TAG, "<redirectCurrentMedia> mSetPathString=" + mSetPathString);
                mHandler.post(new Runnable() {
                    public void run() {
                        StateManager stateManager = mActivity.getStateManager();
                        // the following condition is logically same to say
                        // if (!PhotoPage.this.isDestroyed()) {
                        if ((stateManager.getStateCount() > 0)
                                && (stateManager.getTopState() == PhotoPage.this)) {
                            mActivity.getStateManager().switchState(
                                    PhotoPage.this, SinglePhotoPage.class,
                                    mData);
                        }
                    }
                });
            }
        }
    }

    private LayerManager.IBackwardContoller.IOnActivityResultListener
        mExtActivityResultListener;

    private boolean mIsBackwardToggle;
    private boolean mAllowAutoHideByHost = true;

    private LayerManager.IBackwardContoller mBackwardContollerForLayer =
 new LayerManager.IBackwardContoller() {
        public void toggleBars(boolean visibility) {
            mAllowAutoHideByHost = !visibility;
            mIsBackwardToggle = true;
            if (visibility) {
                mHandler.removeMessages(MSG_HIDE_BARS);
                if (canShowBars()) {
                    showBars();
                }
            } else {
                hideBars();
            }
            mIsBackwardToggle = false;
        }

        public void redirectCurrentMedia(Uri uri, boolean fromActivityResult) {
            PhotoPage.this.redirectCurrentMedia(uri, fromActivityResult);
        }

        public void startActivityForResult(Intent intent, int requestCode,
                IOnActivityResultListener resultListener) {
            mExtActivityResultListener = resultListener;
            mActivity.startActivityForResult(intent, requestCode);
        }

        public void notifyDataChange(MediaData mediaData) {
            if (mModel instanceof PhotoDataAdapter) {
                ((PhotoDataAdapter)(mModel)).notifyDataChange(mediaData);
            }
        }
    };

    public boolean onActionBarVisibilityChange(boolean newVisibility) {
        if (mIsBackwardToggle) {
            return false;
        }
        if (newVisibility) {
            return mPhotoView.onActionBarVisibilityChange(newVisibility);
        } else {
            mPhotoView.onActionBarVisibilityChange(false);
            return false;
        }
    }

    /// M: [BUG.ADD] Slideshow doesn't play again
    // when finish playing the last picture @{
    private Path mSnailSetPath;
    /// @}

    /// M: [BUG.ADD] disable mute when sdcard is full. @{
    /**
     * get available space which storage source video is in.
     * 
     * @return the available sapce size, -1 means max storage size.
     */
    private long getAvailableSpace(String path) {
        try {
            // Here just use one directory to stat fs.
            StatFs stat = new StatFs(path);
            long availableSize = stat.getAvailableBlocks() * (long) stat.getBlockSize();
            Log.i(TAG, "<getAvailableSpace> path " + path
                    + ", availableSize(MB) " + (availableSize / 1024 /1024));
            return availableSize;
        } catch (Exception e) {
            Log.i(TAG, "<getAvailableSpace> Fail to access external storage", e);
        }
        return -1;
    }

    /**
     * calculate the space for video muted is enough or not lowStorageThreshold
     * is reserve space. ram optimize projec is 9M, the others is 48M.
     */
    private boolean isSpaceEnough(File srcFile) {
        long spaceNeed;
        long lowStorageThreshold;
        if (FeatureConfig.isGmoRamOptimize) {
            lowStorageThreshold = 9 * 1024 * 1024;
        } else {
            lowStorageThreshold = 48 * 1024 * 1024;
        }
        spaceNeed = srcFile.length() + lowStorageThreshold;
        if (getAvailableSpace(srcFile.getPath()) < spaceNeed) {
            Log.i(TAG, "<isSpaceEnough> space is not enough!!!");
            return false;
        } else {
            return true;
        }
    }
    /// @}

    // Show photo name on ActionBar when not film mode
    // and show album name onActionBar when film mode
    private void updateActionBarTitle() {
        if (mPhotoView == null || mActionBar == null) {
            return;
        }

        /// M: [BUG.ADD] @{
        try {
            if (mActivity.getStateManager().getTopState() != this) {
                return;
            }
        } catch (AssertionError e) {
            Log.v(TAG, "no state in State Manager when updates actionbar title");
            return;
        }
        /// @}

        if (mPhotoView.getFilmMode()) {
            mActionBar.setDisplayOptions(((mSecureAlbum == null) && (mSetPathString != null)),
                    false);
            mActionBar.setTitle(mMediaSet != null ? mMediaSet.getName() : "");
            /// M: [BUG.ADD] call to reload spinner @{
            if (mShowSpinner) {
                mActionBar.enableAlbumModeMenu(
                        GalleryActionBar.ALBUM_FILMSTRIP_MODE_SELECTED, this);
            }
            /// @}
        } else {
            mActionBar
                    .setDisplayOptions(((mSecureAlbum == null) && (mSetPathString != null)), true);
            mActionBar.setTitle(mCurrentPhoto != null ? mCurrentPhoto.getName() : "");
        }
    }

    @Override
    public void onSingleTapUp(int x, int y) {
        Log.i(TAG, "<onSingleTapUp>");
        if (mAppBridge != null) {
            if (mAppBridge.onSingleTapUp(x, y))
                return;
        }
        MediaItem item = mModel.getMediaItem(0);
        if (item == null || item == mScreenNailItem) {
            // item is not ready or it is camera preview, ignore
            return;
        }
        int supported = item.getSupportedOperations();
        boolean playVideo = ((supported & MediaItem.SUPPORT_PLAY) != 0);

        if (playVideo) {
            // determine if the point is at center (1/6) of the photo view.
            // (The position of the "play" icon is at center (1/6) of the photo)
            int w = mPhotoView.getWidth();
            int h = mPhotoView.getHeight();
            playVideo = (Math.abs(x - w / 2) * 12 <= w)
                    && (Math.abs(y - h / 2) * 12 <= h);
        }

        if (playVideo) {
            if (mSecureAlbum == null) {
                /// M: [FEATURE.MODIFY] slide video @{
                // playVideo(mActivity, item.getPlayUri(), item.getName());
                if (!FeatureConfig.supportSlideVideoPlay) {
                    // mark we are starting video player
                    // to avoid calling toggleBars() which leads to view junk
                    mIsStartingVideoPlayer = true;
                    playVideo(mActivity, item.getPlayUri(), item.getName());
                }
                /// @}
            } else {
                mPhotoView.pause();
                mActivity.getStateManager().finishState(this);
            }
        }
    }

    // roll back to JB4.2.1
    // enter camera will lock orientation, so need update orientation
    // compensation
    // and request layout
    public void onOrientationCompensationChanged() {
        mActivity.getGLRoot().requestLayoutContentPane();
    }

    private static final String KEY_FORCE_IN_CAMERA_PREVIEW = "force_in_camera_preview";
    
    /// M: [FEATURE.ADD] @{
    public void setCameraPath(String setPath) {
        if (mAppBridge == null) {
            return;
        }
        if (mModel != null) {
            mModel.pause();
        }
        if (mModel != null && !mModel.isCamera(0)) {
            Log.d(TAG, "<setCameraPath> storage path changed.");
            mPhotoView.setStoragePathChanged(true);
        }
        Path itemPath = null; 
        mSetPathString = setPath;

        // In order to redirect to right position after export, save new set path in data
        mData.putString(PhotoPage.KEY_MEDIA_SET_PATH, setPath);

        // Get the ScreenNail from AppBridge and register it.
        Path screenNailSetPath = mScreenNailSet.getPath();
        Path screenNailItemPath = mScreenNailItem.getPath();
        mScreenNailItem.setScreenNail(mAppBridge.attachScreenNail());

            // Don't display "empty album" action item for capture intents.
        if (!mSetPathString.equals("/local/all/0")) {
                // Check if the path is a secure album.
            if (SecureSource.isSecurePath(mSetPathString)) {
                mSecureAlbum = (SecureAlbum) mActivity.getDataManager()
                            .getMediaSet(mSetPathString);
                mShowSpinner = false;
            }
            mSetPathString = "/filter/empty/{"+mSetPathString+"}";
        }

        // Combine the original MediaSet with the one for ScreenNail
        mSetPathString = "/combo/item/{" + screenNailSetPath +
                    "," + mSetPathString + "}";
        // Start from the screen nail.
        itemPath = screenNailItemPath;
        MediaSet originalSet= mActivity.getDataManager().getMediaSet(mSetPathString);
        if (mHasCameraScreennailOrPlaceholder && originalSet instanceof ComboAlbum) {
                // Use the name of the camera album rather than the default
                // ComboAlbum behavior
            ((ComboAlbum) originalSet).useNameOfChild(1);
        }
        mSelectionManager.setSourceMediaSet(originalSet);
        mSetPathString = "/filter/delete/{" + mSetPathString + "}";
        mMediaSet = (FilterDeleteSet) mActivity.getDataManager().getMediaSet(mSetPathString);
        Log.d(TAG, "<setCameraPath> mSetPathString " + mSetPathString);

        if (mMediaSet == null) {
            Log.w(TAG, "<setCameraPath> failed to restore " + mSetPathString);
        }
        if (itemPath == null) {
            int mediaItemCount = mMediaSet.getMediaItemCount();
            if (mediaItemCount > 0) {
                if (mCurrentIndex >= mediaItemCount) mCurrentIndex = 0;
                itemPath = mMediaSet.getMediaItem(mCurrentIndex, 1)
                        .get(0).getPath();
            } else {
                    // Bail out, PhotoPage can't load on an empty album
                    return;
            }
        }

        if (mModel != null) {
            mModel.setSourceAndItem(mMediaSet, itemPath);
            // If PhotoPage is paused now, no need to resume model
            if (mIsActive)
                mModel.resume();
        }
    }
    /// @}

    /// M: [FEATURE.ADD] Add for Camera Feature Object Tracking @{
    @Override
    public void onLongPress(int x, int y) {
        if (mAppBridge != null) {
            mAppBridge.onLongPress(x, y);
        }
    }
    /// @}

    /// M: [FEATURE.ADD] @{
    @Override
    public void onFullScreenChanged(boolean full, int type) {
        Message m = mHandler.obtainMessage(MSG_ON_FULL_SCREEN_CHANGED, full ? 1
                : 0, type);
        m.sendToTarget();
    }
    /// @}
    
    /// M: [FEATURE.ADD] launch Image Refocus.@{
    private void launchRefocusActivity() {
        Log.i(TAG, "launchRefocusActivity");
        Activity activity = mActivity;
        Intent intent= new Intent(RefocusActivity.REFOCUS_ACTION);
        intent.setClass(activity, RefocusActivity.class);
        Path path = mCurrentPhoto.getPath();
        Uri uri = mActivity.getDataManager().getContentUri(path);
        intent.setDataAndType(uri, mCurrentPhoto.getMimeType());
        Bundle bundle = new Bundle();
        Log.i(TAG, "REFOCUS_IMAGE_WIDTH = " + mCurrentPhoto.getWidth() + "REFOCUS_IMAGE_HEIGHT = " + mCurrentPhoto.getHeight());
        bundle.putInt(RefocusActivity.REFOCUS_IMAGE_WIDTH, mCurrentPhoto.getWidth());
        bundle.putInt(RefocusActivity.REFOCUS_IMAGE_HEIGHT, mCurrentPhoto.getHeight());
        bundle.putInt(RefocusActivity.REFOCUS_IMAGE_ORIENTATION, mCurrentPhoto.getRotation());
        bundle.putString(RefocusActivity.REFOCUS_IMAGE_NAME, mCurrentPhoto.getName());
        intent.putExtras(bundle);
        activity.startActivityForResult(intent, REQUEST_REFOCUS);
    }
    /// @}
    
    /// M: [FEATURE.ADD] added for ImageRefocus.@{
    private void setRefocusCurrentPhotoByIntent(Intent intent) {
        if (intent == null) return;
        Path path = mApplication.getDataManager().findPathByUri(intent.getData(), intent.getType());
        if (null != path) {
            String string = path.toString();
            if (null != string) {
                mModel.setCurrentPhoto(Path.fromString(string), mCurrentIndex);
            }
        }
    }
    /// @}

    private boolean isLockCameraOritation = true;
    public void setOritationTag(boolean lock, int oritationNum) {
        if(lock == true) {
            mOrientationManager.lockOrientation();
        } else {
            mOrientationManager.unlockOrientation();
        }
        isLockCameraOritation = lock;
    }

    public void lockOrientation() {
        if(isLockCameraOritation == true) {
            mHandler.sendEmptyMessage(MSG_LOCK_ORIENTATION);
        }
    }

    public void unlockOrientation() {
        if(isLockCameraOritation == true) {
            mHandler.sendEmptyMessage(MSG_UNLOCK_ORIENTATION);
        }
    }

    @Override
    public int getSecureAlbumCount() {
        return mSecureAlbum != null ? mSecureAlbum.getExistingItemCount()
                - mMediaSet.getNumberOfDeletions() : 0;
    }

    @Override
    public Listener setGestureListener(Listener listener) {
        return mPhotoView.setGestureListener(listener);
    }

    @Override
    protected float[] getBackgroundColor() {
        if (mModel != null && mModel.isCamera(0)) {
            mPhotoPageBackgroundColor[0] = 0.0f;
        } else {
            mPhotoPageBackgroundColor[0] = 1.0f;
        }
        mBackgroundColor = mPhotoPageBackgroundColor;
        return mPhotoPageBackgroundColor;
    }

    public boolean canSlideToPrePicture() {
        return mCanSlideToPrePicture;
    }

    private void setCurrentPhotoByIntentEx(Intent intent) {
        if (intent == null) return;
            Path photoEditPath = mApplication.getDataManager()
                .findPathByUri(intent.getData(), intent.getType());
        if (photoEditPath != null) {
            String string = photoEditPath.toString();
            if (string != null) {
                // mark the cache for edited image out of date
                ImageCacheService.sForceObsoletePath = string;
                mModel.setCurrentPhoto(Path.fromString(string), mCurrentIndex);
                // add picture, need update camera thumbnail
                mActivity.getDataManager().broadcastUpdatePicture();
            }
        }
        Log.d(TAG, "<setCurrentPhotoByIntentEx> intent.getData()="+intent.getData());
        Intent shareIntent = createShareIntent(intent.getData(), MediaObject.MEDIA_TYPE_IMAGE);
        if (mActionBar != null) {
            mActionBar.setShareIntents(null, shareIntent, PhotoPage.this);
        }
    }

    private static Intent createShareIntent(Uri contentUri, int type) {
        return new Intent(Intent.ACTION_SEND)
                .setType(MenuExecutor.getMimeType(type))
                .putExtra(Intent.EXTRA_STREAM, contentUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    /// M: [BUG.ADD] can not set as wallpaper when no thumbnail@{
    // note: mSupportedOperations should only be set by updateMenuOperations()
    // and only be get in onPrepareOptionsMenu()
    private int mSupportedOperations = 0;

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mSupportedOperations == 0) {
            return super.onPrepareOptionsMenu(menu);
        }
        if (mModel != null && mModel.getLoadingState(0) == PhotoView.Model.LOADING_FAIL) {
            int supportedOperations = mSupportedOperations;
            supportedOperations &= ~(MediaObject.SUPPORT_SETAS
                    | MediaObject.SUPPORT_CROP | MediaObject.SUPPORT_EDIT
                    | MediaObject.SUPPORT_PRINT | MediaObject.SUPPORT_ROTATE);
            MenuExecutor.updateMenuOperation(mActionBar.getMenu(), supportedOperations);
        }
        mPhotoView.onPrepareOptionsMenu(menu);
        return super.onPrepareOptionsMenu(menu);
    }
    /// @}
    
    @Override
    public boolean onChooseActivity(ActivityChooserModel host, Intent intent) {
        Log.d(TAG, "<onChooseActivity>");
        /// M: [BUG.ADD] show toast before PhotoDataAdapter finishing loading @{
        if (!mLoadingFinished) {
            Toast.makeText(mActivity, mActivity.getString(R.string.please_wait),
                    Toast.LENGTH_SHORT).show();
            Log.i(TAG, "<onChooseActivity> not finish loading, show toast, return");
            return true;
        }
        /// @}
        final long timestampMillis = mCurrentPhoto.getDateInMs();
        final String mediaType = getMediaTypeString(mCurrentPhoto);
        UsageStatistics.onEvent(UsageStatistics.COMPONENT_GALLERY,
                UsageStatistics.ACTION_SHARE,
                mediaType,
                        timestampMillis > 0
                        ? System.currentTimeMillis() - timestampMillis
                        : -1);
        /// M: [BUG.ADD] Motion and ConShot can not share as video and gif @{
        if (intent.getComponent() != null
                && intent.getComponent().getPackageName().indexOf("hotknot") != -1) {
            return false;
        }
        /// @}
        /// M: [FEATURE.ADD] VTSP: share as video @{
        String errMsg = getErrMsgWhenStartActivityFail(host, intent);
        mAnimatedContentSharer.setErrMsgWhenStartActivityFail(errMsg);
        mAnimatedContentSharer.onShareTargetSelected(intent);
        /// @}
        return true;
    }

    private String getErrMsgWhenStartActivityFail(ActivityChooserModel host, Intent intent) {
        int count = host.getActivityCount();
        for (int i = 0; i < count; i++) {
            ResolveInfo rInfo = host.getActivity(i);
            if (rInfo != null
                    && rInfo.activityInfo != null
                    && rInfo.activityInfo.name != null
                    && rInfo.activityInfo.name.equals(intent.getComponent().getClassName())) {
                String res = mActivity.getString(
                        com.android.internal.R.string.activitychooserview_choose_application_error,
                        rInfo.loadLabel(mActivity.getPackageManager()));
                Log.i(TAG, "<getErrMsgWhenStartActivityFail> return " + res);
                return res;
            }
        }
        Log.i(TAG, "<getErrMsgWhenStartActivityFail> return null");
        return null;
    }

    // variable for a temporary solution to handle cases when we enter/return a PhotoPage
    // with camera item at the head but we never intend to see the camera at the "entering" time
    // e.g. save an edited photo; mute a slide video; export as video/gif
    // note that this variable should only be used in UI thread
    private boolean mIsAppBridgeFullScreenChangeEnabled = true;
    private WeakReference<Toast> mEmptyAlbumToast = null;

    private void showEmptyAlbumToast(int toastLength) {
        Toast toast;
        if (mEmptyAlbumToast != null) {
            toast = mEmptyAlbumToast.get();
            if (toast != null) {
                toast.show();
                return;
            }
        }
        toast = Toast.makeText(mActivity, R.string.empty_album, 500);
        mEmptyAlbumToast = new WeakReference<Toast>(toast);
        toast.show();
    }

    // whether we are starting video player
    private boolean mIsStartingVideoPlayer;
}
