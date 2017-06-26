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

package com.mediatek.gallery3d.adapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.ActivityState;
import com.android.gallery3d.app.AlbumDataLoader;
import com.android.gallery3d.app.Config;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.LoadingListener;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.app.SinglePhotoPage;
import com.android.gallery3d.app.SlideshowPage;
import com.android.gallery3d.app.TransitionStore;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.FadeTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.ui.ActionModeHandler;
import com.android.gallery3d.ui.ActionModeHandler.ActionModeListener;
import com.android.gallery3d.ui.AlbumSlotRenderer;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.MenuExecutor;
import com.android.gallery3d.ui.PhotoFallbackEffect;
import com.android.gallery3d.ui.RelativePosition;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SlotView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;
import com.android.gallery3d.app.GalleryActionBar;
import com.mediatek.galleryframework.util.RotateProgressFragment;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryfeature.container.BestShotSelector;
import com.mediatek.galleryfeature.container.MotionTrack;
import com.mediatek.galleryframework.util.MtkLog;

import com.mediatek.gallery3d.layout.FancyHelper;

public class ContainerPage extends ActivityState implements
        SelectionManager.SelectionListener{
    private static final String TAG = "MtkGallery2/ContainerPage";

    public static final String KEY_MEDIA_PATH = "media-path";
    public static final String KEY_PARENT_MEDIA_PATH = "parent-media-path";
    public static final String KEY_SET_CENTER = "set-center";
    public static final String KEY_AUTO_SELECT_ALL = "auto-select-all";
    public static final String KEY_SHOW_CLUSTER_MENU = "cluster-menu";
    public static final String KEY_EMPTY_ALBUM = "empty-album";
    public static final String KEY_RESUME_ANIMATION = "resume_animation";

    private static final int REQUEST_SLIDESHOW = 1;
    private static final int REQUEST_PHOTO = 2;
    private static final int REQUEST_DO_ANIMATION = 3;
    private static final int MSG_PICK_PHOTO = 0;
    private static final int BIT_LOADING_RELOAD = 1;
    private static final float USER_DISTANCE_METER = 0.3f;

    private boolean mIsActive = false;
    private boolean mShowDetails;
    private boolean mGetContent;
    private int mFocusIndex = 0;
    private int mLoadingBits = 0;
    private float mUserDistance; // in pixel
    private AlbumSlotRenderer mAlbumView;
    private Path mMediaSetPath;
    private String mParentMediaSetString;
    private SlotView mSlotView;
    private AlbumDataLoader mAlbumDataAdapter;
    protected SelectionManager mSelectionManager;
    private ActionModeHandler mActionModeHandler;
    private DetailsHelper mDetailsHelper;
    private MyDetailsSource mDetailsSource;
    private MediaSet mMediaSet;
    private RelativePosition mOpenCenter = new RelativePosition();
    private Handler mHandler;
    private PhotoFallbackEffect mResumeEffect;
    // Save selection for onPause/onResume
    private boolean mNeedUpdateSelection = false;
    // Flag to specify whether mSelectionManager.restoreSelection task has done
    private boolean mRestoreSelectionDone;
    // Flag to specify whether initialize data fail
    // If mFinishStateWhenResume is true,
    // we need finish current ActivityState at the right time
    private boolean mFinishStateWhenResume = false;

    private PhotoFallbackEffect.PositionProvider mPositionProvider = new PhotoFallbackEffect.PositionProvider() {
        @Override
        public Rect getPosition(int index) {
            Rect rect = mSlotView.getSlotRect(index);
            Rect bounds = mSlotView.bounds();
            rect.offset(bounds.left - mSlotView.getScrollX(), bounds.top
                    - mSlotView.getScrollY());
            return rect;
        }

        @Override
        public int getItemIndex(Path path) {
            int start = mSlotView.getVisibleStart();
            int end = mSlotView.getVisibleEnd();
            for (int i = start; i < end; ++i) {
                MediaItem item = mAlbumDataAdapter.get(i);
                if (item != null && item.getPath() == path)
                    return i;
            }
            return -1;
        }
    };

    @Override
    protected int getBackgroundColorId() {
        return R.color.album_background;
    }

    private final GLView mRootPane = new GLView() {
        private final float mMatrix[] = new float[16];

        @Override
        protected void onLayout(boolean changed, int left, int top, int right,
                int bottom) {

            int slotViewTop = mActivity.getGalleryActionBar().getHeight();
            int slotViewBottom = bottom - top;
            int slotViewRight = right - left;

            if (mShowDetails) {
                mDetailsHelper.layout(left, slotViewTop, right, bottom);
            } else {
                mAlbumView.setHighlightItemPath(null);
            }

            // Set the mSlotView as a reference point to the open animation
            mOpenCenter.setReferencePosition(0, slotViewTop);
            mSlotView.layout(0, slotViewTop, slotViewRight, slotViewBottom);
            GalleryUtils.setViewPointMatrix(mMatrix, (right - left) / 2,
                    (bottom - top) / 2, -mUserDistance);
        }

        @Override
        protected void render(GLCanvas canvas) {
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            canvas.multiplyMatrix(mMatrix, 0);
            super.render(canvas);

            if (mResumeEffect != null) {
                boolean more = mResumeEffect.draw(canvas);
                if (!more) {
                    mResumeEffect = null;
                    mAlbumView.setSlotFilter(null);
                }
                // We want to render one more time even when no more effect
                // required. So that the animated thumbnails could be draw
                // with declarations in super.render().
                invalidate();
            }
            canvas.restore();
        }
    };

    @Override
    protected void onBackPressed() {
        if (mShowDetails) {
            hideDetails();
        } else if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        } else {
            onUpPressed();
        }
    }

    private void onUpPressed() {
        if (mActivity.getStateManager().getStateCount() > 1) {
            super.onBackPressed();
        } else if (!mInMotrackMode) {
            Bundle data = new Bundle(getData());

            // item path
            // get first item of this continuous shot group
            ArrayList<MediaItem> itemArray = mMediaSet.getMediaItem(0, 1);
            if (itemArray == null || itemArray.size() == 0)
                return;
            MediaItem firstItemThisGroup = itemArray.get(0);
            if (firstItemThisGroup == null || firstItemThisGroup.getMediaData() == null)
                return;
            long id = firstItemThisGroup.getMediaData().id;
            // get MediaItem of this group in PhotoPage
            ArrayList<Integer> ids = new ArrayList<Integer>();
            ids.add(new Integer((int) id));
            MediaItem[] items = LocalAlbum.getMediaItemById(
                    (GalleryApp) mActivity.getApplication(), true, ids);
            if (items == null || items.length == 0)
                return;
            data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, items[0].getPath().toString());

            // set path
            DataManager dm = mActivity.getDataManager();
            Path setPath = Path.fromString(dm.getTopSetPath(DataManager.INCLUDE_LOCAL_ALL_ONLY))
                    .getChild(((LocalImage)items[0]).getBucketId());
            data.putString(PhotoPage.KEY_MEDIA_SET_PATH, setPath.toString());

            MtkLog.i(TAG, "<onUpPressed> setPath = " + setPath
                    + ", itemPath = " + items[0].getPath());
            mActivity.getStateManager().switchState(this, SinglePhotoPage.class, data);
        }
    }

    private void onDown(int index) {
        mAlbumView.setPressedIndex(index);
    }

    private void onUp(boolean followedByLongPress) {
        if (followedByLongPress) {
            // Avoid showing press-up animations for long-press.
            mAlbumView.setPressedIndex(-1);
        } else {
            mAlbumView.setPressedUp();
        }
    }

    private void onSingleTapUp(int slotIndex) {
        if (!mIsActive)
            return;

        if (mSelectionManager.inSelectionMode()) {
            MediaItem item = mAlbumDataAdapter.get(slotIndex);
            if (item == null)
                return; // Item not ready yet, ignore the click

            if (mRestoreSelectionDone) {
                boolean needToggle = true;
                if (mMotrack != null) {
                    needToggle = mMotrack.singleTapUp(item, slotIndex);
                }
                if (needToggle)
                    mSelectionManager.toggle(item.getPath());
                mSlotView.invalidate();
            } else {
                Toast.makeText(mActivity, com.android.internal.R.string.wait, Toast.LENGTH_SHORT)
                        .show();
            }
        } else {
            // Render transition in pressed state
            mAlbumView.setPressedIndex(slotIndex);
            mAlbumView.setPressedUp();
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_PHOTO,
                    slotIndex, 0), FadeTexture.DURATION);
        }
    }

    private void pickPhoto(int slotIndex) {
        pickPhoto(slotIndex, false);
    }

    private void pickPhoto(int slotIndex, boolean startInFilmstrip) {
        if (!mIsActive) {
            MtkLog.i(TAG, "<pickPhoto> Not active, ignore the click");
            return;
        }
        MediaItem item = mAlbumDataAdapter.get(slotIndex);
        if (item == null) {
            MtkLog.i(TAG, "<pickPhoto> Item not ready yet, ignore the click");
            return;
        }
        if (mGetContent) {
            onGetContent(item);
        } else {
            Bundle data = new Bundle();
            data.putInt(PhotoPage.KEY_INDEX_HINT, slotIndex);
            data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT, mSlotView
                    .getSlotRect(slotIndex, mRootPane));
            data.putString(PhotoPage.KEY_MEDIA_SET_PATH, mMediaSetPath
                    .toString());
            data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, item.getPath()
                    .toString());
            data.putInt(PhotoPage.KEY_ALBUMPAGE_TRANSITION,
                    PhotoPage.MSG_ALBUMPAGE_STARTED);
            data.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP, startInFilmstrip);
            data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, mMediaSet
                    .isCameraRoll());
            mActivity.getStateManager().startStateForResult(
                    SinglePhotoPage.class, REQUEST_PHOTO, data);
        }
    }

    private void onGetContent(final MediaItem item) {
        DataManager dm = mActivity.getDataManager();
        Activity activity = mActivity;
        if (mData.getString(GalleryActivity.EXTRA_CROP) != null) {
            Uri uri = dm.getContentUri(item.getPath());
            Intent intent = new Intent(CropActivity.CROP_ACTION, uri).addFlags(
                    Intent.FLAG_ACTIVITY_FORWARD_RESULT).putExtras(getData());
            if (mData.getParcelable(MediaStore.EXTRA_OUTPUT) == null) {
                intent.putExtra(CropExtras.KEY_RETURN_DATA, true);
            }
            activity.startActivity(intent);
            activity.finish();
        } else {
            Intent intent = new Intent(null, item.getContentUri())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.setResult(Activity.RESULT_OK, intent);
            activity.finish();
        }
    }

    public void onLongTap(int slotIndex) {
        if (mGetContent)
            return;

        if (mInMotrackMode)
            return;

        MediaItem item = mAlbumDataAdapter.get(slotIndex);
        if (item == null)
            return;
        mSelectionManager.setAutoLeaveSelectionMode(true);
        mSelectionManager.toggle(item.getPath());
        mSlotView.invalidate();
    }

    @Override
    protected void onCreate(Bundle data, Bundle restoreState) {
        MtkLog.i(TAG, "<onCreate>");
        super.onCreate(data, restoreState);
        mUserDistance = GalleryUtils.meterToPixel(USER_DISTANCE_METER);
        initializeViews();
        initializeData(data);
        mGetContent = data.getBoolean(GalleryActivity.KEY_GET_CONTENT, false);
        mDetailsSource = new MyDetailsSource();
        Context context = mActivity.getAndroidContext();

        if (data.getBoolean(KEY_AUTO_SELECT_ALL)) {
            mSelectionManager.selectAll();
        }

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                case MSG_PICK_PHOTO: {
                    pickPhoto(message.arg1);
                    break;
                }
                case MSG_UP_PRESS:
                    onUpPressed();
                    break;
                case MSG_INTO_MOTION_PREVIEW:
                    mSelectionManager.leaveSelectionMode();
                    onUpPressed();
                    break;
                case MSG_UPDATE_MENU:
                    if (mConShots != null) {
                        mConShots.updateMenu(mMenu);
                    }
                    mActivity.getGLRoot().requestRender();
                    break;
                default:
                    throw new AssertionError(message.what);
                }
            }
        };
    }

    @Override
    protected void onResume() {
        MtkLog.i(TAG, "<onResume>");
        super.onResume();
        if (mFinishStateWhenResume) {
            MtkLog.i(TAG, "<onResume> mFinishStateWhenResume, finishState, return");
            mActivity.getStateManager().finishState(this);
            return;
        }
        mIsActive = true;

        mResumeEffect = mActivity.getTransitionStore()
                .get(KEY_RESUME_ANIMATION);
        if (mResumeEffect != null) {
            mAlbumView.setSlotFilter(mResumeEffect);
            mResumeEffect.setPositionProvider(mPositionProvider);
            mResumeEffect.start();
        }

        setContentPane(mRootPane);

        boolean enableHomeButton = (mActivity.getStateManager().getStateCount() > 1)
                | mParentMediaSetString != null;
        mActivity.getGalleryActionBar().setDisplayOptions(enableHomeButton, false);

        // Set the reload bit here to prevent it exit this page in
        // clearLoadingBit().
        setLoadingBit(BIT_LOADING_RELOAD);
        
        // need to update selection manager if in selection mode when resume @{
        if (mSelectionManager != null && mSelectionManager.inSelectionMode()) {
            mNeedUpdateSelection = true;
            // set mRestoreSelectionDone as false if we need to retore selection
            mRestoreSelectionDone = false;
        } else {
            // set mRestoreSelectionDone as true there is no need to retore selection
            mRestoreSelectionDone = true;
        }
        /// @}

        mAlbumDataAdapter.resume();

        mAlbumView.resume();
        mAlbumView.setPressedIndex(-1);
        mActionModeHandler.resume();

        if (mMotrack != null) {
            mMotrack.resume();
        }
    }

    @Override
    protected void onPause() {
        MtkLog.i(TAG, "<onPause>");
        super.onPause();
        if (mFinishStateWhenResume) {
            MtkLog.i(TAG, "<onPause> mFinishStateWhenResume, return");
            return;
        }
        mIsActive = false;

        mAlbumView.setSlotFilter(null);
        mActionModeHandler.pause();
        mActivity.getGalleryActionBar().setLogo(R.mipmap.ic_launcher_gallery);

        if (mAlbumDataAdapter != null)
            mAlbumDataAdapter.pause();

        mAlbumView.pause();
        DetailsHelper.pause();
        if (!mGetContent) {
            mActivity.getGalleryActionBar().disableAlbumModeMenu(true);
        }

        if (mMotrack != null) {
            mMotrack.pause();
        }

        if (mSelectionManager != null && mSelectionManager.inSelectionMode()) {
            mSelectionManager.saveSelection();
            mNeedUpdateSelection = false;
        }
    }

    @Override
    protected void onDestroy() {
        MtkLog.i(TAG, "<onDestroy>");
        super.onDestroy();
        if (mAlbumDataAdapter != null) {
            mAlbumDataAdapter.setLoadingListener(null);
        }
        mActionModeHandler.destroy();
        if (mSelectionManager != null && mSelectionManager.inSelectionMode()) {
            MtkLog.i(TAG, "<onDestroy> leaveSelectionMode when destroy");
            mSelectionManager.leaveSelectionMode();
        }
        if (mMotrack != null) {
            mMotrack.destory();
        }
    }

    private void initializeViews() {
        mSelectionManager = new SelectionManager(mActivity, false);
        mSelectionManager.setSelectionListener(this);
        Config.AlbumPage config = Config.AlbumPage.get(mActivity);
        mSlotView = new SlotView(mActivity, config.slotViewSpec);
        /// M: [FEATURE.ADD] fancy layout @{
        if (FancyHelper.isFancyLayoutSupported()) {
            mSlotView.switchLayout(SlotView.DEFAULT_LAYOUT);
        }
        /// @}
        mAlbumView = new AlbumSlotRenderer(mActivity, mSlotView,
                mSelectionManager, config.placeholderColor);
        mSlotView.setSlotRenderer(mAlbumView);
        mRootPane.addComponent(mSlotView);
        mSlotView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onDown(int index) {
                ContainerPage.this.onDown(index);
            }

            @Override
            public void onUp(boolean followedByLongPress) {
                ContainerPage.this.onUp(followedByLongPress);
            }

            @Override
            public void onSingleTapUp(int slotIndex) {
                ContainerPage.this.onSingleTapUp(slotIndex);
            }

            @Override
            public void onLongTap(int slotIndex) {
                ContainerPage.this.onLongTap(slotIndex);
            }
        });
        mActionModeHandler = new ActionModeHandler(mActivity, mSelectionManager);
        mActionModeHandler.setActionModeListener(new ActionModeListener() {
            @Override
            public boolean onActionItemClicked(MenuItem item) {
                return onItemSelected(item);
            }

            public boolean onPopUpItemClicked(int itemId) {
                return mRestoreSelectionDone;
            }
        });

    }

    private void initializeData(Bundle data) {
        mMediaSetPath = Path.fromString(data.getString(KEY_MEDIA_PATH));
        mParentMediaSetString = data.getString(KEY_PARENT_MEDIA_PATH);
        mMediaSet = mActivity.getDataManager().getMediaSet(mMediaSetPath);
        if (mMediaSet == null || mMediaSet.getMediaItemCount() == 0) {
            // How to go into this case?
            // 1.Enter ContainerPage 2.Press home key to exit 
            // 3.Delete this group of continuous shot image in FileManager
            // 4.Kill com.android.gallery3d process 5.Launch gallery
            MtkLog.w(TAG, "<initializeData> mMediaSet = " + mMediaSet + ", Path = " + mMediaSetPath
                    + ", finishState when onResume");
            mFinishStateWhenResume = true;
            return;
        }
        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumDataAdapter = new AlbumDataLoader(mActivity, mMediaSet);
        mAlbumDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumView.setModel(mAlbumDataAdapter);

        mInMotrackMode = data.getBoolean(KEY_IN_MOTRACK_MODE, false);
        if (mInMotrackMode) {
            mMotrack = new Motrack();
        } else {
            mConShots = new ConShots();
        }
        MtkLog.d(TAG, "<initializeData> mInMotrackMode:" + mInMotrackMode);
    }

    private void showDetails() {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mActivity, mRootPane,
                    mDetailsSource);
            mDetailsHelper.setCloseListener(new CloseListener() {
                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.show();
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
        mAlbumView.setHighlightItemPath(null);
        mSlotView.invalidate();
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        GalleryActionBar actionBar = mActivity.getGalleryActionBar();
        MenuInflater inflator = getSupportMenuInflater();
        actionBar.setTitle(null);
        showConShotsIcon();
        if (mGetContent) {
            actionBar.createActionBarMenu(R.menu.pickup, menu);
        } else {
            actionBar.createActionBarMenu(R.menu.m_conshotsdetail, menu);
        }
        mMenu = menu;
        /// set best shot menu as invisible, it will be updated in updateMenu() @{
        MenuItem item = mMenu.findItem(R.id.m_action_best_shots);
        if (item != null) {
            item.setVisible(false);
        }
        // After rotate screen, ActionBar will recreate, but updateMenu will not run again,
        // so send message here to updateMenu
        mHandler.sendEmptyMessage(MSG_UPDATE_MENU);
        /// @}
        actionBar.setSubtitle(null);
        if (!mGetContent) {
            actionBar.disableAlbumModeMenu(true);
        }
        return true;
    }

    private void showConShotsIcon() {
        GalleryActionBar actionBar = mActivity.getGalleryActionBar();
        if (actionBar != null) {
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setLogo(R.drawable.m_conshots_folder);
        }
    }

    private void prepareAnimationBackToFilmstrip(int slotIndex) {
        if (mAlbumDataAdapter == null || !mAlbumDataAdapter.isActive(slotIndex))
            return;
        MediaItem item = mAlbumDataAdapter.get(slotIndex);
        if (item == null)
            return;
        TransitionStore transitions = mActivity.getTransitionStore();
        transitions.put(PhotoPage.KEY_INDEX_HINT, slotIndex);
        transitions.put(PhotoPage.KEY_OPEN_ANIMATION_RECT, mSlotView
                .getSlotRect(slotIndex, mRootPane));
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home: {
            onUpPressed();
            return true;
        }
        case R.id.action_cancel:
            mActivity.getStateManager().finishState(this);
            return true;
        case R.id.action_select:
            mSelectionManager.setAutoLeaveSelectionMode(false);
            mSelectionManager.enterSelectionMode();
            return true;
        case R.id.action_details: {
            if (mShowDetails) {
                hideDetails();
            } else {
                showDetails();
            }
            return true;
        }
        case R.id.m_action_best_shots: {
            if (mConShots != null) {
                mConShots.doExtrack(item);
            }
            return true;
        }
        case R.id.m_action_motion_preview: {
            if (mMotrack != null) {
                mMotrack.blend();
            }
            return true;
        }
        default:
            return false;
        }
    }

    @Override
    protected void onStateResult(int request, int result, Intent data) {
        switch (request) {
        case REQUEST_SLIDESHOW: {
            // data could be null, if there is no images in the album
            if (data == null)
                return;
            mFocusIndex = data.getIntExtra(SlideshowPage.KEY_PHOTO_INDEX, 0);
            mSlotView.setCenterIndex(mFocusIndex);
            break;
        }
        case REQUEST_PHOTO: {
            if (data == null)
                return;
            mFocusIndex = data.getIntExtra(PhotoPage.KEY_RETURN_INDEX_HINT, 0);
            mSlotView.makeSlotVisible(mFocusIndex);
            break;
        }
        case REQUEST_DO_ANIMATION: {
            mSlotView.startRisingAnimation();
            break;
        }
        }
    }

    @Override
    public void onSelectionModeChange(int mode) {
        switch (mode) {
        case SelectionManager.ENTER_SELECTION_MODE: {
            if (mInMotrackMode) {
                MtkLog.d(TAG, "<onSelectionModeChange> startActionModeForMotion");
                mActionModeHandler.startActionModeForMotion();
                break;
            }
            mActionModeHandler.startActionMode();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            break;
        }
        case SelectionManager.LEAVE_SELECTION_MODE: {
            mActionModeHandler.finishActionMode();
            mRootPane.invalidate();
            if (mInMotrackMode) {
                mHandler
                        .sendMessage(mHandler.obtainMessage(MSG_UP_PRESS, 0, 0));
            }
            break;
        }
        case SelectionManager.DESELECT_ALL_MODE:
        case SelectionManager.SELECT_ALL_MODE: {
            mActionModeHandler.updateSupportedOperation();
            mRootPane.invalidate();
            break;
        }
        }
    }

    @Override
    public void onSelectionChange(Path path, boolean selected) {
        int count = mSelectionManager.getSelectedCount();
        String format = mActivity.getResources().getQuantityString(
                R.plurals.number_of_items_selected, count);
        mActionModeHandler.setTitle(String.format(format, count));
        mActionModeHandler.updateSupportedOperation(path, selected);
    }

    private void setLoadingBit(int loadTaskBit) {
        mLoadingBits |= loadTaskBit;
    }

    private void clearLoadingBit(int loadTaskBit) {
        mLoadingBits &= ~loadTaskBit;
        if (mLoadingBits == 0 && mIsActive) {
            if (mConShots != null && mAlbumDataAdapter.size() <= 1) {
                Toast.makeText((Context) mActivity, R.string.m_empty_conshots,
                        Toast.LENGTH_SHORT).show();
                if (mAlbumDataAdapter.size() == 1)
                    mMediaSet.getMediaItem(0, 1);
                mActivity.getStateManager().finishState(this);
                return;
            }
            if (mAlbumDataAdapter.size() == 0) {
                Intent result = new Intent();
                result.putExtra(KEY_EMPTY_ALBUM, true);
                setStateResult(Activity.RESULT_OK, result);
                mActivity.getStateManager().finishState(this);
            }
        }
    }

    private class MyLoadingListener implements LoadingListener {
        @Override
        public void onLoadingStarted() {
            setLoadingBit(BIT_LOADING_RELOAD);
        }

        @Override
        public void onLoadingFinished(boolean loadingFailed) {
            MtkLog.i(TAG, "<onLoadingFinished> loadingFailed = " + loadingFailed);
            clearLoadingBit(BIT_LOADING_RELOAD);

            boolean inSelectionMode = (mSelectionManager != null && mSelectionManager
                    .inSelectionMode());
            int itemCount = mMediaSet != null ? mMediaSet.getMediaItemCount()
                    : 0;
            MtkLog.i(TAG, "<onLoadingFinished> itemCount = " + itemCount);
            mSelectionManager.onSourceContentChanged();
            boolean restore = false;
            if (itemCount > 0 && inSelectionMode) {
                if (mNeedUpdateSelection) {
                    mNeedUpdateSelection = false;
                    restore = true;
                    mSelectionManager.restoreSelection();
                }
                mActionModeHandler.updateSupportedOperation();
                mActionModeHandler.updateSelectionMenu();
            }
            if (!restore) {
                mRestoreSelectionDone = true;
            }

            if (mConShots != null) {
                mConShots.markBestShotItems();
            }
        }
    }

    private class MyDetailsSource implements DetailsHelper.DetailsSource {
        private int mIndex;

        @Override
        public int size() {
            return mAlbumDataAdapter.size();
        }

        @Override
        public int setIndex() {
            ArrayList<Path> selectedPath = mSelectionManager.getSelected(false);
            if (selectedPath == null || selectedPath.size() == 0) {
                MtkLog.i(TAG, "<MyDetailsSource.setIndex> selectedPath = " + selectedPath
                        + ", return -1");
                return -1;
            }
            Path id = selectedPath.get(0);
            mIndex = mAlbumDataAdapter.findItem(id);
            return mIndex;
        }

        @Override
        public MediaDetails getDetails() {
            // this relies on setIndex() being called beforehand
            MediaObject item = mAlbumDataAdapter.get(mIndex);
            if (item != null) {
                mAlbumView.setHighlightItemPath(item.getPath());
                return item.getDetails();
            } else {
                return null;
            }
        }
    }

    //********************************************************************
    //*                              MTK                                 *
    //********************************************************************

    // 
    public static final String KEY_IN_MOTRACK_MODE = "in_motrack_mode";

    private static final int MSG_UP_PRESS = 2;
    private static final int MSG_INTO_MOTION_PREVIEW = 3;
    private static final int MSG_UPDATE_MENU = 4;

    private boolean mInMotrackMode = false;
    private Motrack mMotrack = null;
    private ConShots mConShots = null;

    private boolean mIsNeedExtract = false;
    private Menu mMenu;

    class Motrack {
        private static final String TAG = "MtkGallery2/Motrack";
        private static final int MOTION_MANUAL_EDIT_MIN_PIC = 2;
        private static final int MOTION_MANUAL_EDIT_MAX_PIC = 8;

        private AlertDialog mAlertDialog;
        private MotionTrack mMotionTrack;

        private boolean mMotrackActive = false;
        private boolean mNeedShowPrevState;
        private BroadcastReceiver mStorageReceiver;
        private boolean mIsInBlending = false;
        private int[] mSelectedIndexes;
        private int mSelectedNum;

        public Motrack() {
            MtkLog.i(TAG, "<new>");
            mNeedShowPrevState = true;
            mSelectedIndexes = new int[8];
            mSelectedNum = 0;
            registerStorageReceiver();
        }

        public void resume() {
            MtkLog.i(TAG, "<resume>");
            mSelectionManager.setAutoLeaveSelectionMode(false);
            mSelectionManager.enterSelectionMode();
            if (mMediaSet.getTotalMediaItemCount() == 0) {
                MtkLog.i(TAG, "<resume> mMediaSet.getTotalMediaItemCount() = 0, return");
                mHandler.sendMessage(mHandler.obtainMessage(
                        MSG_INTO_MOTION_PREVIEW, 0, 0));
                return;
            }

            mMotionTrack = new MotionTrack();
            int itemCount = mMediaSet.getTotalMediaItemCount();
            ArrayList<MediaItem> items = mMediaSet.getMediaItem(0, itemCount);
            MediaItem mediaItem = items.get(0);

            File file = new File(mediaItem.getFilePath());
            File pFile = file.getParentFile();
            if (pFile == null) {
                MtkLog.i(TAG, "<resume> pFile = null return");
                mHandler.sendMessage(mHandler.obtainMessage(MSG_INTO_MOTION_PREVIEW, 0, 0));
                return;
            }
            File ppFile = pFile.getParentFile();
            if (ppFile == null) {
                MtkLog.i(TAG, "<resume> ppFile = null return");
                mHandler.sendMessage(mHandler.obtainMessage(MSG_INTO_MOTION_PREVIEW, 0, 0));
                return;
            }
            String workPath = ppFile.getParent();

            mMotionTrack.init(workPath, mMediaSet.getName(), mediaItem
                    .getWidth(), mediaItem.getHeight(), 20, mediaItem
                    .getWidth(), mediaItem.getHeight());
            if (mNeedShowPrevState) {
                mNeedShowPrevState = false;
                restorePrevState();
            }
            mMotrackActive = true;
        }

        public void pause() {
            MtkLog.i(TAG, "<pause>");
            mMotrackActive = false;
            if (!mIsInBlending && mMotionTrack != null) {
                mMotionTrack.release();
                mMotionTrack = null;
            }
        }

        public void destory() {
            MtkLog.i(TAG, "<destory>");
            mMotrackActive = false;
            unregisterStorageReceiver();
        }

        public synchronized void blend() {
            // in some special case, for example quickly select then do, 
            // AlertDialog and genProgressDialog will be overlap  
            if (mAlertDialog != null && mAlertDialog.isShowing()) {
                MtkLog.i(TAG, "<blend> mAlertDialog.isShowing(), return");
                return;
            }
            if (!mMotrackActive) {
                MtkLog.i(TAG, "<blend> not active, return");
                return;
            }
            if (mIsInBlending) {
                MtkLog.i(TAG, "<blend> mIsInBlending = true, return");
                return;
            } else {
                mIsInBlending = true;
                MtkLog.i(TAG, "<blend> set mIsInBlending = true");
            }
            if (mSelectionManager.getSelectedCount() < MOTION_MANUAL_EDIT_MIN_PIC) {
                String confirmMsg;
                confirmMsg = mActivity.getResources().getString(
                        R.string.m_motion_at_least,
                        MOTION_MANUAL_EDIT_MIN_PIC + "");
                mAlertDialog = new AlertDialog.Builder(mActivity
                        .getAndroidContext()).setMessage(confirmMsg)
                        .setPositiveButton(R.string.ok, null).create();
                mAlertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        synchronized(Motrack.this) {
                            mIsInBlending = false;
                            MtkLog.i(TAG, "<blend> After AlertDialog dismiss, set mIsInBlending = false");
                        }
                    }
                });
                mAlertDialog.show();
                return;
            }

            final DialogFragment genProgressDialog = RotateProgressFragment
                    .newInstance(mActivity.getResources().getString(
                            R.string.m_generate_animation));
            genProgressDialog.setCancelable(false);
            genProgressDialog.show(mActivity.getFragmentManager(), "");
            new Thread() {
                public void run() {
                    long beginTime = System.currentTimeMillis();
                    ArrayList<Path> paths = mSelectionManager
                            .getSelected(false);
                    if (paths != null) {
                        boolean loadSelectedSuccess = loadSelected(paths);
                        if (loadSelectedSuccess) {
                            mMotionTrack.doBlend();
                            // update new blending image size
                            MediaItem blendImage = ((ContainerSet) mMediaSet).getParentItem();
                            if (blendImage != null) {
                                MtkLog.i(TAG, "<blend> before update <" + blendImage.getContentUri()
                                        + ">, w = " + blendImage.getWidth() + ", h = "
                                        + blendImage.getHeight());
                                // prepare new w & h
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inJustDecodeBounds = true;
                                BitmapFactory.decodeFile(blendImage.getFilePath(), options);
                                // update media database
                                ContentValues cv = new ContentValues();
                                cv.put(MediaColumns.WIDTH, options.outWidth);
                                cv.put(MediaColumns.HEIGHT, options.outHeight);
                                int res = mActivity.getContentResolver().update(
                                        blendImage.getContentUri(), cv, null, null);
                                MtkLog.i(TAG, "<blend> update <" + blendImage.getContentUri()
                                        + ">, new w = " + options.outWidth + ", h = "
                                        + options.outHeight);
                            }
                        } else {
                            MtkLog.i(TAG, "<blend> loadSelectedSuccess = " + loadSelectedSuccess
                                    + ", not blend, end");
                        }
                    }
                    genProgressDialog.dismissAllowingStateLoss();
                    mHandler.sendMessage(mHandler.obtainMessage(
                            MSG_INTO_MOTION_PREVIEW, 0, 0));
                    synchronized(Motrack.this) {
                        mIsInBlending = false;
                        MtkLog.i(TAG, "<blend> After mMotionTrack.doBlend() done, set mIsInBlending = false");
                    }
                    long endTime = System.currentTimeMillis();
                    MtkLog.d(TAG, "MM_PROFILE blend time:"
                            + (endTime - beginTime));
                }
            }.start();
        }

        public boolean singleTapUp(MediaItem item, int slotIndex) {
            MtkLog.i(TAG, "<singleTapUp>");
            if (!item.getExtItem().isEnable()) {
                MtkLog.i(TAG, "<singleTapUp> !item.getExtItem().isEnable(), return false");
                return false;
            }
            if (mAlertDialog != null && mAlertDialog.isShowing()) {
                MtkLog.i(TAG, "<singleTapUp> mAlertDialog.isShowing(), return false");
                return false;
            }
            if (mIsInBlending) {
                MtkLog.i(TAG, "<singleTapUp> mIsInBlending, return false");
                return false;
            }
            if (mSelectionManager.getSelectedCount() >= MOTION_MANUAL_EDIT_MAX_PIC
                    && !mSelectionManager.contains(item.getPath())) {
                String confirmMsg;
                confirmMsg = mActivity.getResources().getString(
                        R.string.m_motion_at_most, MOTION_MANUAL_EDIT_MAX_PIC);
                if (mAlertDialog != null && mAlertDialog.isShowing()) {
                    MtkLog.i(TAG, "<singleTapUp> click so quickly, dialog, hide you");
                    mAlertDialog.dismiss();
                }
                mAlertDialog = new AlertDialog.Builder(mActivity
                        .getAndroidContext()).setMessage(confirmMsg)
                        .setPositiveButton(R.string.ok, null).create();
                mAlertDialog.show();
                return false;
            } else if (mSelectionManager.getSelectedCount() == 0) {
                setItemDisable(slotIndex);
            } else if (mSelectionManager.getSelectedCount() == 1
                    && mSelectionManager.contains(item.getPath())) {
                setAllItemEnable();
            }
            return true;
        }

        private void setItemDisable(int slotIndex) {
            int count = mMediaSet.getMediaItemCount();
            int[] refImage;
            refImage = mMotionTrack.getDisableArray(slotIndex);
            ArrayList<MediaItem> mediaItemList = mMediaSet.getMediaItem(0,
                    count);
            for (int i = 0; i < count; i++) {
                if (refImage[i] == 0) {
                    mediaItemList.get(i).getExtItem().setEnable(false);
                }

            }
        }

        private void setAllItemEnable() {
            int count = mMediaSet.getMediaItemCount();
            ArrayList<MediaItem> mediaItemList = mMediaSet.getMediaItem(0,
                    count);
            for (int i = 0; i < count; i++) {
                mediaItemList.get(i).getExtItem().setEnable(true);
            }
        }

        private void restorePrevState() {
            int[] prevFocus;
            int[] prevDisable;
            int itmeCount = mMediaSet.getTotalMediaItemCount();
            ArrayList<MediaItem> items = mMediaSet.getMediaItem(0, itmeCount);

            // show prev focus item
            prevFocus = mMotionTrack.getPrevFocusArray();
            for (int i = 0; i < itmeCount; i++) {
                if (prevFocus[i] == 1) {
                    mSelectionManager.toggle(items.get(i).getPath());
                }
            }
            // show prev disable item
            prevDisable = mMotionTrack.getPrevDisableArray();
            for (int i = 0; i < itmeCount; i++) {
                items.get(i).getExtItem().setEnable(true);
                if (prevDisable[i] == 0 && prevFocus[i] != 1) {
                    items.get(i).getExtItem().setEnable(false);
                }
            }
        }

        private boolean loadSelected(ArrayList<Path> paths) {
            Collections.sort(paths, new pathComparator());
            mSelectedNum = paths.size();
            MtkLog.d(TAG, "<loadSelected> mSelectedNum = " + mSelectedNum);
            if (mSelectedNum == 0 || mSelectedNum > 8) {
                MtkLog.d(TAG, "<loadSelected> mSelectedNum is out of limit, return");
                return false;
            }

            for (int i = 0; i < mSelectedNum; i++) {
                String path = paths.get(i).toString();
                int start = path.lastIndexOf("MT");
                String sid = path.substring(start + 2, start + 4);
                MtkLog.d(TAG, "<loadSelected> sid = " + sid);
                try {
                    int iid = Integer.parseInt(sid) - 1;
                    MtkLog.d(TAG, "select id:" + iid);
                    mSelectedIndexes[i] = iid;
                } catch (NumberFormatException NFE) {
                    MtkLog.d(TAG, "<loadSelected> format error!");
                    mSelectedNum--;
                }
            }

            mMotionTrack.setManualIndexes(mSelectedIndexes, mSelectedNum);
            return true;
        }

        private class pathComparator implements Comparator<Path> {

            @Override
            public int compare(Path path1, Path paht2) {
                return path1.toString().compareTo(paht2.toString());
            }
        }

        private void registerStorageReceiver() {
            MtkLog.d(TAG, "<registerStorageReceiver>");
            // register BroadcastReceiver for SD card mount/unmount broadcast
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_EJECT);
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addDataScheme("file");
            mStorageReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_MEDIA_EJECT.equals(action)) {
                        MtkLog.d(TAG, "<onReceive> storage is unmount");
                        if (mMediaSet.getTotalMediaItemCount() == 0) {
                            mHandler.sendMessage(mHandler.obtainMessage(
                                    MSG_INTO_MOTION_PREVIEW, 0, 0));
                        }
                    }
                }
            };
            mActivity.registerReceiver(mStorageReceiver, filter);
        }

        private void unregisterStorageReceiver() {
            MtkLog.d(TAG, "<unregisterStorageReceiver>");
            if (mStorageReceiver != null) {
                mActivity.unregisterReceiver(mStorageReceiver);
            }
        }
    }

    class ConShots {
        private static final String TAG = "MtkGallery2/ConShots";
        private MenuExecutor mMenuExecutor;

        public ConShots() {
            MtkLog.d(TAG, "<new>");
            mMenuExecutor = new MenuExecutor(mActivity, mSelectionManager);
        }

        public void updateMenu(Menu menu) {
            if (menu == null)
                return;
            MenuItem item = menu.findItem(R.id.m_action_best_shots);
            if (item == null)
                return;

            if (mIsNeedExtract) {
                MtkLog.d(TAG, "<updateMenu> set extrack menu visible");
                item.setVisible(true);
            } else {
                MtkLog.d(TAG, "<updateMenu> set extrack menu invisible");
                item.setVisible(false);
            }

        }

        public void markBestShotItems() {
            MtkLog.d(TAG, "<markBestShotItems> begin");
            if (mMediaSet.getMediaItemCount() <= 1) {
                MtkLog.i(TAG, "<markBestShot> media item count <= 1, return");
                return;
            }
            mActivity.getThreadPool().submit(new BestShotSelectJob(), null);
        }

        public void doExtrack(MenuItem item) {
            String confirmMsg = mActivity.getResources().getString(
                    R.string.m_best_shots_confirm);
            mSelectionManager.setPrepared(mConShots.getNotBestShotPaths());
            mMenuExecutor.onMenuClicked(item, confirmMsg, null);
        }

        private class BestShotSelectJob implements Job<Void> {
            private ArrayList<MediaData> mMediaDataList = null;

            public BestShotSelectJob() {
                mMediaDataList = getDataList(mMediaSet);
            }

            @Override
            public Void run(JobContext jc) {
                MtkLog.i(TAG, "<BestShotSelectJob.run> begin");
                if (!(mMediaSet instanceof ContainerSet)) {
                    MtkLog.i(TAG, "<BestShotSelectJob.run> fail"
                            + ", mMediaSet is not container");
                    return null;
                }

                BestShotSelector selector = new BestShotSelector(mActivity,
                        mMediaDataList);
                selector.markBestShot();
                mIsNeedExtract = selector.isNeedExtract();
                mHandler.sendEmptyMessage(MSG_UPDATE_MENU);
                return null;
            }
        }

        public ArrayList<Path> getNotBestShotPaths() {
            ArrayList<Path> notBestShot = new ArrayList<Path>();
            int total = mMediaSet.getMediaItemCount();
            ArrayList<MediaItem> list = mMediaSet.getMediaItem(0, total);
            for (MediaItem item : list) {
                if (item.getMediaData().bestShotMark != MediaData.BEST_SHOT_MARK_TRUE) {
                    Path id = item.getPath();
                    notBestShot.add(id);
                }
            }
            return notBestShot;
        }
    }

    private static ArrayList<MediaData> getDataList(MediaSet set) {
        ArrayList<MediaItem> itemList = set.getMediaItem(0, set
                .getMediaItemCount());
        ArrayList<MediaData> dataList = new ArrayList<MediaData>();
        for (MediaItem item : itemList) {
            dataList.add(item.getMediaData());
        }
        return dataList;
    }

    @Override
    public void onSelectionRestoreDone() {
        if (!mIsActive) return;
        mRestoreSelectionDone = true;
        // because SelectionManager.restoreSelection() has been changed to async
        // so we would update menu when restore done
        mActionModeHandler.updateSupportedOperation();
        mActionModeHandler.updateSelectionMenu();
    }
}