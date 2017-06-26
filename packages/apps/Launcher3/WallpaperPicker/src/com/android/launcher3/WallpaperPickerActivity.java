/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

package com.android.launcher3;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.ActionMode;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.Toast;
import android.hardware.display.DisplayManager;
import android.content.SharedPreferences;

import com.android.photos.BitmapRegionTileSource;
import com.android.photos.BitmapRegionTileSource.BitmapSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.mediatek.launcher3.ext.LauncherLog;
// /M: Filter drm files @ {
import com.mediatek.drm.OmaDrmStore;
// /@}

public class WallpaperPickerActivity extends WallpaperCropActivity {
    static final String TAG = "Launcher.WallpaperPickerActivity";

    public static final int IMAGE_PICK = 5;
    public static final int PICK_WALLPAPER_THIRD_PARTY_ACTIVITY = 6;
    public static final int PICK_LIVE_WALLPAPER = 7;
    private static final String TEMP_WALLPAPER_TILES = "TEMP_WALLPAPER_TILES";
    private static final String SELECTED_INDEX = "SELECTED_INDEX";
    private static final String OLD_DEFAULT_WALLPAPER_THUMBNAIL_FILENAME = "default_thumb.jpg";
    private static final String DEFAULT_WALLPAPER_THUMBNAIL_FILENAME = "default_thumb2.jpg";
    private static final String SMARTBOOK_PLUGIN_KEY = "smartbook.plugin";
    private static final int FLAG_POST_DELAY_MILLIS = 200;

    private View mSelectedTile;
    private boolean mIgnoreNextTap;
    private OnClickListener mThumbnailOnClickListener;

    private LinearLayout mWallpapersView;
    private View mWallpaperStrip;

    private ActionMode.Callback mActionModeCallback;
    private ActionMode mActionMode;

    private View.OnLongClickListener mLongClickListener;

    ArrayList<Uri> mTempWallpaperTiles = new ArrayList<Uri>();
    private SavedWallpaperImages mSavedImages;
    private WallpaperInfo mLiveWallpaperInfoOnPickerLaunch;
    private int mSelectedIndex = -1;
    private WallpaperInfo mLastClickedLiveWallpaperInfo;

    ///M. ALPS02025663, aviod pick image can't be click twice.
    FrameLayout mPickImageTile;
    ///M.

    public static abstract class WallpaperTileInfo {
        protected View mView;
        public Drawable mThumb;

        public void setView(View v) {
            mView = v;
        }
        public void onClick(WallpaperPickerActivity a) {}
        public void onSave(WallpaperPickerActivity a) {}
        public void onDelete(WallpaperPickerActivity a) {}
        public boolean isSelectable() { return false; }
        public boolean isNamelessWallpaper() { return false; }
        public void onIndexUpdated(CharSequence label) {
            if (isNamelessWallpaper()) {
                mView.setContentDescription(label);
            }
        }
    }

    public static class PickImageInfo extends WallpaperTileInfo {
        @Override
        public void onClick(WallpaperPickerActivity a) {
            ///M. ALPS02025663, aviod pick image can't be click twice.
            a.mPickImageTile.setClickable(false);
            ///M.

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            // /M: Filter drm files @ {
            intent.putExtra(OmaDrmStore.DrmExtra.EXTRA_DRM_LEVEL,
                    OmaDrmStore.DrmExtra.LEVEL_FL);
            // /@}
            a.startActivityForResultSafely(intent, IMAGE_PICK);
        }
    }

    public static class UriWallpaperInfo extends WallpaperTileInfo {
        private Uri mUri;
        private boolean mFirstClick = true;
        private BitmapRegionTileSource.UriBitmapSource mBitmapSource;
        public UriWallpaperInfo(Uri uri) {
            mUri = uri;
        }
        @Override
        public void onClick(final WallpaperPickerActivity a) {
            final Runnable onLoad;
			Log.i(TAG, "UriWallpaperInfo.onClick");
            if (!a.mIsFinishLoadBitmapTask) {
                Log.i(TAG, "UriWallpaperInfo.onClick, mIsFinishLoadBitmapTask == false, return");
                return;
            }
            a.mIsFinishLoadBitmapTask = false;
            if (!mFirstClick) {
                onLoad = null;
            } else {
                mFirstClick = false;
                a.mSetWallpaperButton.setEnabled(false);
                onLoad = new Runnable() {
                    public void run() {
                        if (mBitmapSource != null &&
                                mBitmapSource.getLoadingState() == BitmapSource.State.LOADED) {
                            a.selectTile(mView);
                            a.mSetWallpaperButton.setEnabled(true);
                        } else {
                            ViewGroup parent = (ViewGroup) mView.getParent();
                            if (parent != null) {
                                parent.removeView(mView);
                                Toast.makeText(a,
                                        a.getString(R.string.image_load_fail),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                };
            }
            mBitmapSource = new BitmapRegionTileSource.UriBitmapSource(
                    a, mUri, BitmapRegionTileSource.MAX_PREVIEW_SIZE);
            a.setCropViewTileSource(mBitmapSource, true, false, onLoad);
        }
        @Override
        public void onSave(final WallpaperPickerActivity a) {
		    Log.i(TAG, "UriWallpaperInfo.onSave");
            boolean finishActivityWhenDone = true;
            OnBitmapCroppedHandler h = new OnBitmapCroppedHandler() {
                public void onBitmapCropped(byte[] imageBytes) {
                    Point thumbSize = getDefaultThumbnailSize(a.getResources());
                    // rotation is set to 0 since imageBytes has already been correctly rotated
                    Bitmap thumb = createThumbnail(
                            thumbSize, null, null, imageBytes, null, 0, 0, true);
                    a.getSavedImages().writeImage(thumb, imageBytes);
                }
            };
            a.cropImageAndSetWallpaper(mUri, h, finishActivityWhenDone);
            Log.i(TAG, "UriWallpaperInfo.onSave, end");
        }
        @Override
        public void onDelete(WallpaperPickerActivity a) {
            a.mTempWallpaperTiles.remove(mUri);
        }
        @Override
        public boolean isSelectable() {
            return true;
        }
        @Override
        public boolean isNamelessWallpaper() {
            return true;
        }
    }

    public static class FileWallpaperInfo extends WallpaperTileInfo {
        private File mFile;

        public FileWallpaperInfo(File target, Drawable thumb) {
            mFile = target;
            mThumb = thumb;
        }
        @Override
        public void onClick(WallpaperPickerActivity a) {
            ///M. ALPS02016403, the file wallpaperinfo also should set the mIsFinishLoadBitmapTask
            if (!a.mIsFinishLoadBitmapTask) {
                Log.i(TAG, "FileWallpaperInfo.onClick, mIsFinishLoadBitmapTask == false, return");
                return;
            }
            a.mIsFinishLoadBitmapTask = false;
            ///M.
            BitmapRegionTileSource.UriBitmapSource bitmapSource =
                    new BitmapRegionTileSource.UriBitmapSource(a, Uri.fromFile(mFile), 1024);
            a.setCropViewTileSource(bitmapSource, false, true, null);
        }
        @Override
        public void onSave(WallpaperPickerActivity a) {
            a.setWallpaper(Uri.fromFile(mFile), true);
        }
        @Override
        public boolean isSelectable() {
            return true;
        }
        @Override
        public boolean isNamelessWallpaper() {
            return true;
        }
    }

    public static class ResourceWallpaperInfo extends WallpaperTileInfo {
        private Resources mResources;
        private int mResId;

        public ResourceWallpaperInfo(Resources res, int resId, Drawable thumb) {
            mResources = res;
            mResId = resId;
            mThumb = thumb;
        }
        @Override
        public void onClick(WallpaperPickerActivity a) {
            BitmapRegionTileSource.ResourceBitmapSource bitmapSource =
                    new BitmapRegionTileSource.ResourceBitmapSource(
                            mResources, mResId, BitmapRegionTileSource.MAX_PREVIEW_SIZE);
            bitmapSource.loadInBackground();
            BitmapRegionTileSource source = new BitmapRegionTileSource(a, bitmapSource);
            CropView v = a.getCropView();
            v.setTileSource(source, null);
            Point wallpaperSize = WallpaperCropActivity.getDefaultWallpaperSize(
                    a.getResources(), a.getWindowManager());
            RectF crop = WallpaperCropActivity.getMaxCropRect(
                    source.getImageWidth(), source.getImageHeight(),
                    wallpaperSize.x, wallpaperSize.y, false);
            v.setScale(wallpaperSize.x / crop.width());
            v.setTouchEnabled(false);
            a.setSystemWallpaperVisiblity(false);
        }
        @Override
        public void onSave(WallpaperPickerActivity a) {
            boolean finishActivityWhenDone = true;
            a.cropImageAndSetWallpaper(mResources, mResId, finishActivityWhenDone);
        }
        @Override
        public boolean isSelectable() {
            return true;
        }
        @Override
        public boolean isNamelessWallpaper() {
            return true;
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static class DefaultWallpaperInfo extends WallpaperTileInfo {
        public DefaultWallpaperInfo(Drawable thumb) {
            mThumb = thumb;
        }
        @Override
        public void onClick(WallpaperPickerActivity a) {
            CropView c = a.getCropView();
			Log.i(TAG, "DefaultWallpaperInfo.onClick");

            Log.d(TAG, "DefaultWallpaperInfo.onClick(),  set mIsFinishLoadBitmapTask = true");
            a.mIsFinishLoadBitmapTask = true;
            if (a.mProgressView != null) {
                Log.d(TAG, "DefaultWallpaperInfo.onClick(),  a.mProgressView.setVisibility(View.INVISIBLE)");
                a.mProgressView.setVisibility(View.INVISIBLE);
            }

            Drawable defaultWallpaper = WallpaperManager.getInstance(a).getBuiltInDrawable(
                    c.getWidth(), c.getHeight(), false, 0.5f, 0.5f);

            if (defaultWallpaper == null) {
                Log.w(TAG, "Null default wallpaper encountered.");
                c.setTileSource(null, null);
                return;
            }

			DrawableTileSource source = new DrawableTileSource(a, 
				defaultWallpaper, DrawableTileSource.MAX_PREVIEW_SIZE);

			Point realSize = new Point();
			Display display = a.getWindowManager().getDefaultDisplay();
			display.getRealSize(realSize);
			float scale = 1f;
			
			if ((realSize.x / source.getImageWidth()) >  
				(realSize.y / source.getImageHeight())) {
				scale = (float)realSize.x / (float)source.getImageWidth();
			} else {
				scale = (float)realSize.y / (float)source.getImageHeight();
			}

            c.setTileSource(source, null);
            c.setScale(scale);

            c.setTouchEnabled(false);
            a.setSystemWallpaperVisiblity(false);
        }
        @Override
        public void onSave(WallpaperPickerActivity a) {
			Log.i(TAG, "DefaultWallpaperInfo.onsave");
			
            try {
                WallpaperManager.getInstance(a).clear();
                a.setResult(RESULT_OK);
            } catch (IOException e) {
                Log.w("Setting wallpaper to default threw exception", e);
            }
            a.finish();
			
			Log.i(TAG, "DefaultWallpaperInfo.onsave end");
        }
        @Override
        public boolean isSelectable() {
            return true;
        }
        @Override
        public boolean isNamelessWallpaper() {
            return true;
        }
    }

	///M: alps016011980, use AsyncTask, when get last thumbnail.
	protected static class GetLastThumbnailTask extends AsyncTask<Void,Void,Boolean> {
		Bitmap mLastPhoto;
		Context mContext;
		FrameLayout mPickImageTile;
				
		public GetLastThumbnailTask(Context c, FrameLayout pickImageTile) {
			mContext = c;
			mPickImageTile = pickImageTile;
		}
			
		protected Boolean doInBackground(Void... params) {
			Log.i(TAG, "GetLastThumbnailTask: doInBackground_in");
				
			Cursor cursor = MediaStore.Images.Media.query(mContext.getContentResolver(),
							MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
							new String[] { MediaStore.Images.ImageColumns._ID,
							MediaStore.Images.ImageColumns.DATE_TAKEN},
							null, null, MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT 1");
			if(cursor == null) {
				return false;
			}
				
			try {				
				if (cursor.moveToNext()) {
					int id = cursor.getInt(0);
					mLastPhoto = MediaStore.Images.Thumbnails.getThumbnail(mContext.getContentResolver(),
						id, MediaStore.Images.Thumbnails.MINI_KIND, null);
				}
			} finally { 			
				cursor.close();
			}
	
			Log.i(TAG, "GetLastThumbnailTask: out");
			return true;
		}
			
		protected void onPostExecute(Boolean result) {
			Log.i(TAG, "GetLastThumbnailTask: onPostExecute");
			if (mLastPhoto != null) {
				ImageView galleryThumbnailBg =
					(ImageView) mPickImageTile.findViewById(R.id.wallpaper_image);
				galleryThumbnailBg.setImageBitmap(mLastPhoto);
				int colorOverlay = mContext.getResources()
					.getColor(R.color.wallpaper_picker_translucent_gray);
				galleryThumbnailBg.setColorFilter(colorOverlay, PorterDuff.Mode.SRC_ATOP);
			}
		}
	}
	/// M.

    public void setWallpaperStripYOffset(float offset) {
        mWallpaperStrip.setPadding(0, 0, 0, (int) offset);
    }

    /**
     * shows the system wallpaper behind the window and hides the {@link
     * #mCropView} if visible
     * @param visible should the system wallpaper be shown
     */
    protected void setSystemWallpaperVisiblity(final boolean visible) {
        // hide our own wallpaper preview if necessary
        if(!visible) {
            mCropView.setVisibility(View.VISIBLE);
        } else {
            changeWallpaperFlags(visible);
        }
        // the change of the flag must be delayed in order to avoid flickering,
        // a simple post / double post does not suffice here
        mCropView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(!visible) {
                    changeWallpaperFlags(visible);
                } else {
                    mCropView.setVisibility(View.INVISIBLE);
                }
            }
        }, FLAG_POST_DELAY_MILLIS);
    }

    private void changeWallpaperFlags(boolean visible) {
        int desiredWallpaperFlag = visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0;
        int currentWallpaperFlag = getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        if (desiredWallpaperFlag != currentWallpaperFlag) {
            getWindow().setFlags(desiredWallpaperFlag,
                    WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
    }

    @Override
    public void setCropViewTileSource(BitmapSource bitmapSource,
                                      boolean touchEnabled,
                                      boolean moveToLeft,
                                      final Runnable postExecute) {
        // we also want to show our own wallpaper instead of the one in the background
        Runnable showPostExecuteRunnable = new Runnable() {
            @Override
            public void run() {
                if(postExecute != null) {
                    postExecute.run();
                }
                setSystemWallpaperVisiblity(false);
            }
        };
        super.setCropViewTileSource(bitmapSource,
                touchEnabled,
                moveToLeft,
                showPostExecuteRunnable);
    }
	// M:Wallpaper display black when rotating device,ALPS01804213
	public boolean isTablet() { 
        return getResources().getBoolean(R.bool.is_tablet);
    }
	// M:end

    protected void onResume() {
        super.onResume();
        ((AlphaDisableableButton)mSetWallpaperButton).setClickable(true);

        ///M. ALPS02025663, aviod pick image can't be click twice.
        mPickImageTile.setClickable(true);
        ///M.
    }
	
    // called by onCreate; this is subclassed to overwrite WallpaperCropActivity
    protected void init() {
        setContentView(R.layout.wallpaper_picker);

        mCropView = (CropView) findViewById(R.id.cropView);
        mCropView.setVisibility(View.INVISIBLE);

        mWallpaperStrip = findViewById(R.id.wallpaper_strip);
        mCropView.setTouchCallback(new CropView.TouchCallback() {
            ViewPropertyAnimator mAnim;
            @Override
            public void onTouchDown() {
                if (mAnim != null) {
                    mAnim.cancel();
                }
                if (mWallpaperStrip.getAlpha() == 1f) {
                    mIgnoreNextTap = true;
                }
                mAnim = mWallpaperStrip.animate();
                mAnim.alpha(0f)
                    .setDuration(150)
                    .withEndAction(new Runnable() {
                        public void run() {
                            mWallpaperStrip.setVisibility(View.INVISIBLE);
                        }
                    });
                mAnim.setInterpolator(new AccelerateInterpolator(0.75f));
                mAnim.start();
            }
            @Override
            public void onTouchUp() {
                mIgnoreNextTap = false;
            }
            @Override
            public void onTap() {
                boolean ignoreTap = mIgnoreNextTap;
                mIgnoreNextTap = false;
                if (!ignoreTap) {
                    if (mAnim != null) {
                        mAnim.cancel();
                    }
                    mWallpaperStrip.setVisibility(View.VISIBLE);
                    mAnim = mWallpaperStrip.animate();
                    mAnim.alpha(1f)
                         .setDuration(150)
                         .setInterpolator(new DecelerateInterpolator(0.75f));
                    mAnim.start();
                }
            }
        });

        mThumbnailOnClickListener = new OnClickListener() {
            public void onClick(View v) {
                if (mActionMode != null) {
                    // When CAB is up, clicking toggles the item instead
                    if (v.isLongClickable()) {
                        mLongClickListener.onLongClick(v);
                    }
                    return;
                }
                mSetWallpaperButton.setEnabled(true);
                WallpaperTileInfo info = (WallpaperTileInfo) v.getTag();
				
				/// M: ALPS01665621, info maybe null.
				if (info == null) {
					return;
				}
				/// M.
				
                if (info.isSelectable() && v.getVisibility() == View.VISIBLE) {
                    selectTile(v);
                }
                info.onClick(WallpaperPickerActivity.this);
            }
        };
        mLongClickListener = new View.OnLongClickListener() {
            // Called when the user long-clicks on someView
            public boolean onLongClick(View view) {
                CheckableFrameLayout c = (CheckableFrameLayout) view;
                c.toggle();

                if (mActionMode != null) {
                    mActionMode.invalidate();
                } else {
                    // Start the CAB using the ActionMode.Callback defined below
                    mActionMode = startActionMode(mActionModeCallback);

                    ///M. ALPS01952726, becuase of action mode changed in L1,
                    ///We should call invalidate after startActionMode. Or else,
                    ///onPrepareActionMode will not be called.
                    mActionMode.invalidate();
                    ///M.

                    int childCount = mWallpapersView.getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        mWallpapersView.getChildAt(i).setSelected(false);
                    }
                }
                return true;
            }
        };

        /// M: for smart book
        if(getSmartBookPrefs() != isSmartBookPluggedIn(this)) {
            deleteDefaultWallpaperThumbnail();
        }

        // Populate the built-in wallpapers
        ArrayList<WallpaperTileInfo> wallpapers = findBundledWallpapers();
        mWallpapersView = (LinearLayout) findViewById(R.id.wallpaper_list);
        SimpleWallpapersAdapter ia = new SimpleWallpapersAdapter(this, wallpapers);
        populateWallpapersFromAdapter(mWallpapersView, ia, false);

        // Populate the saved wallpapers
        mSavedImages = new SavedWallpaperImages(this);
        mSavedImages.loadThumbnailsAndImageIdList();
        populateWallpapersFromAdapter(mWallpapersView, mSavedImages, true);

        // Populate the live wallpapers
        final LinearLayout liveWallpapersView =
                (LinearLayout) findViewById(R.id.live_wallpaper_list);
        final LiveWallpaperListAdapter a = new LiveWallpaperListAdapter(this);
        a.registerDataSetObserver(new DataSetObserver() {
            public void onChanged() {
                liveWallpapersView.removeAllViews();
                populateWallpapersFromAdapter(liveWallpapersView, a, false);
                initializeScrollForRtl();
                updateTileIndices();
            }
        });

        // Populate the third-party wallpaper pickers
        final LinearLayout thirdPartyWallpapersView =
                (LinearLayout) findViewById(R.id.third_party_wallpaper_list);
        final ThirdPartyWallpaperPickerListAdapter ta =
                new ThirdPartyWallpaperPickerListAdapter(this);
        populateWallpapersFromAdapter(thirdPartyWallpapersView, ta, false);

        // Add a tile for the Gallery
        LinearLayout masterWallpaperList = (LinearLayout) findViewById(R.id.master_wallpaper_list);
        FrameLayout pickImageTile = (FrameLayout) getLayoutInflater().
                inflate(R.layout.wallpaper_picker_image_picker_item, masterWallpaperList, false);

        ///M. ALPS02025663, aviod pick image can't be click twice.
        mPickImageTile = pickImageTile;
        ///M.

        setWallpaperItemPaddingToZero(pickImageTile);
        masterWallpaperList.addView(pickImageTile, 0);

        ///* M alps01601180, use AsnycTask get last thumbnail.
        // Make its background the last photo taken on external storage
	    /*Bitmap lastPhoto = getThumbnailOfLastPhoto();
        if (lastPhoto != null) {
            ImageView galleryThumbnailBg =
                    (ImageView) pickImageTile.findViewById(R.id.wallpaper_image);
            galleryThumbnailBg.setImageBitmap(getThumbnailOfLastPhoto());
            int colorOverlay = getResources().getColor(R.color.wallpaper_picker_translucent_gray);
            galleryThumbnailBg.setColorFilter(colorOverlay, PorterDuff.Mode.SRC_ATOP);

	    }*/

	    GetLastThumbnailTask task = new GetLastThumbnailTask(this,pickImageTile);
	    task.execute();
	    /// M

        PickImageInfo pickImageInfo = new PickImageInfo();
        pickImageTile.setTag(pickImageInfo);
        pickImageInfo.setView(pickImageTile);
        pickImageTile.setOnClickListener(mThumbnailOnClickListener);
		
		// Select the first item; wait for a layout pass so that we initialize the dimensions of
        // cropView or the defaultWallpaperView first
        mCropView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if ((right - left) > 0 && (bottom - top) > 0) {
                    if (mSelectedIndex >= 0 && mSelectedIndex < mWallpapersView.getChildCount()) {
                        mThumbnailOnClickListener.onClick(
                                mWallpapersView.getChildAt(mSelectedIndex));
                        setSystemWallpaperVisiblity(false);
                    }
                    v.removeOnLayoutChangeListener(this);
                }
            }
        });

        updateTileIndices();

        // Update the scroll for RTL
        initializeScrollForRtl();

        // Create smooth layout transitions for when items are deleted
        final LayoutTransition transitioner = new LayoutTransition();
        transitioner.setDuration(200);
        transitioner.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transitioner.setAnimator(LayoutTransition.DISAPPEARING, null);
        mWallpapersView.setLayoutTransition(transitioner);

        // Action bar
        // Show the custom action bar view
        final ActionBar actionBar = getActionBar();
        actionBar.setCustomView(R.layout.actionbar_set_wallpaper);
        actionBar.getCustomView().setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((AlphaDisableableButton)mSetWallpaperButton).setClickable(false);
                        if (mSelectedTile != null) {
                            WallpaperTileInfo info = (WallpaperTileInfo) mSelectedTile.getTag();
                            info.onSave(WallpaperPickerActivity.this);
                        } else {
                            // no tile was selected, so we just finish the activity and go back
                            setResult(Activity.RESULT_OK);
                            finish();
                        }
                    }
                });
        mSetWallpaperButton = findViewById(R.id.set_wallpaper_button);

        // CAB for deleting items
        mActionModeCallback = new ActionMode.Callback() {
            // Called when the action mode is created; startActionMode() was called
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Inflate a menu resource providing context menu items
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.cab_delete_wallpapers, menu);
                return true;
            }

            private int numCheckedItems() {
                int childCount = mWallpapersView.getChildCount();
                int numCheckedItems = 0;
                for (int i = 0; i < childCount; i++) {
                    CheckableFrameLayout c = (CheckableFrameLayout) mWallpapersView.getChildAt(i);
                    if (c.isChecked()) {
                        numCheckedItems++;
                    }
                }
                return numCheckedItems;
            }

            // Called each time the action mode is shown. Always called after onCreateActionMode,
            // but may be called multiple times if the mode is invalidated.
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                int numCheckedItems = numCheckedItems();
                if (numCheckedItems == 0) {
                    mode.finish();
                    return true;
                } else {
                    mode.setTitle(getResources().getQuantityString(
                            R.plurals.number_of_items_selected, numCheckedItems, numCheckedItems));
                    return true;
                }
            }

            // Called when the user selects a contextual menu item
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.menu_delete) {
                    int childCount = mWallpapersView.getChildCount();
                    ArrayList<View> viewsToRemove = new ArrayList<View>();
                    boolean selectedTileRemoved = false;
                    for (int i = 0; i < childCount; i++) {
                        CheckableFrameLayout c =
                                (CheckableFrameLayout) mWallpapersView.getChildAt(i);
                        if (c.isChecked()) {
                            WallpaperTileInfo info = (WallpaperTileInfo) c.getTag();
                            info.onDelete(WallpaperPickerActivity.this);
                            viewsToRemove.add(c);
                            if (i == mSelectedIndex) {
                                selectedTileRemoved = true;
                            }
                        }
                    }
                    for (View v : viewsToRemove) {
                        mWallpapersView.removeView(v);
                    }
                    if (selectedTileRemoved) {
                        mSelectedIndex = -1;
                        mSelectedTile = null;
                        /// M:ALPS01901591, if it's delete the selected tile,
                        /// don't setSystemWallpaperVisiblity, or else the cropView can't be touched
                        //setSystemWallpaperVisiblity(true);
                        ///M.
                    }
                    updateTileIndices();
                    mode.finish(); // Action picked, so close the CAB

                    /// M: ALPS01901591, if it's delete the selected tile, click the first view.
                    if (selectedTileRemoved) {
                        mThumbnailOnClickListener.onClick(
                            mWallpapersView.getChildAt(0));
                    }
                    /// M.
                    return true;
                } else {
                    return false;
                }
            }

            // Called when the user exits the action mode
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                int childCount = mWallpapersView.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    CheckableFrameLayout c = (CheckableFrameLayout) mWallpapersView.getChildAt(i);
                    c.setChecked(false);
                }
                if (mSelectedTile != null) {
                    mSelectedTile.setSelected(true);
                }
                mActionMode = null;
            }
        };

		/// M: ALPS01663179, click it at end of init. 
		if(!isTablet()){
			if (mSelectedIndex >= 0 && mSelectedIndex < mWallpapersView.getChildCount()) {
	            mThumbnailOnClickListener.onClick(
	                    mWallpapersView.getChildAt(mSelectedIndex));
	        } else {
	        	mThumbnailOnClickListener.onClick(
	                    mWallpapersView.getChildAt(0));
	        }
		}
		/// M.	
        isFromGallery = false;
        Log.d(TAG, "WallpaperPickerActivity, init(), isFromGallery = " + isFromGallery);
    }

    private void selectTile(View v) {
        if (mSelectedTile != null) {
            mSelectedTile.setSelected(false);
            mSelectedTile = null;
        }
        mSelectedTile = v;
        v.setSelected(true);
        mSelectedIndex = mWallpapersView.indexOfChild(v);
        // TODO: Remove this once the accessibility framework and
        // services have better support for selection state.
        v.announceForAccessibility(
                getString(R.string.announce_selection, v.getContentDescription()));
    }

    private void initializeScrollForRtl() {
        final HorizontalScrollView scroll =
                (HorizontalScrollView) findViewById(R.id.wallpaper_scroll_container);

        if (scroll.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            final ViewTreeObserver observer = scroll.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    LinearLayout masterWallpaperList =
                            (LinearLayout) findViewById(R.id.master_wallpaper_list);
                    scroll.scrollTo(masterWallpaperList.getWidth(), 0);
                    scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }
    }

    protected Bitmap getThumbnailOfLastPhoto() {
        Cursor cursor = MediaStore.Images.Media.query(getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Images.ImageColumns._ID,
                    MediaStore.Images.ImageColumns.DATE_TAKEN},
                null, null, MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT 1");

        Bitmap thumb = null;
        if (cursor != null) {
            if (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                thumb = MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(),
                        id, MediaStore.Images.Thumbnails.MINI_KIND, null);
            }
            cursor.close();
        }
        return thumb;
    }

    protected void onStop() {
        super.onStop();
        mWallpaperStrip = findViewById(R.id.wallpaper_strip);
        if (mWallpaperStrip.getAlpha() < 1f) {
            mWallpaperStrip.setAlpha(1f);
            mWallpaperStrip.setVisibility(View.VISIBLE);
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
    	Log.d(TAG, "onSaveInstanceState");
        outState.putParcelableArrayList(TEMP_WALLPAPER_TILES, mTempWallpaperTiles);
        outState.putInt(SELECTED_INDEX, mSelectedIndex);
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
    	Log.d(TAG, "onRestoreInstanceState");
    	int restoreUriSize = 0;
        ArrayList<Uri> uris = savedInstanceState.getParcelableArrayList(TEMP_WALLPAPER_TILES);
    	restoreUriSize = uris.size();
    	///M: M:Wallpaper display black when rotating device,ALPS01804213
    	for (int i = 0;i<restoreUriSize;i++) {
            Uri uri = uris.get(i);
            if(i == restoreUriSize - 1){
    	    	addTemporaryWallpaperTile(uri, true,true);
            }else{
    	    	addTemporaryWallpaperTile(uri, true);
            }
        }
        mSelectedIndex = savedInstanceState.getInt(SELECTED_INDEX, -1);
    }

    private void populateWallpapersFromAdapter(ViewGroup parent, BaseAdapter adapter,
            boolean addLongPressHandler) {
        for (int i = 0; i < adapter.getCount(); i++) {
            FrameLayout thumbnail = (FrameLayout) adapter.getView(i, null, parent);
            parent.addView(thumbnail, i);
            WallpaperTileInfo info = (WallpaperTileInfo) adapter.getItem(i);
            thumbnail.setTag(info);
            info.setView(thumbnail);
            if (addLongPressHandler) {
                addLongPressHandler(thumbnail);
            }
            thumbnail.setOnClickListener(mThumbnailOnClickListener);
        }
    }

    private void updateTileIndices() {
        LinearLayout masterWallpaperList = (LinearLayout) findViewById(R.id.master_wallpaper_list);
        final int childCount = masterWallpaperList.getChildCount();
        final Resources res = getResources();

        // Do two passes; the first pass gets the total number of tiles
        int numTiles = 0;
        for (int passNum = 0; passNum < 2; passNum++) {
            int tileIndex = 0;
            for (int i = 0; i < childCount; i++) {
                View child = masterWallpaperList.getChildAt(i);
                LinearLayout subList;

                int subListStart;
                int subListEnd;
                if (child.getTag() instanceof WallpaperTileInfo) {
                    subList = masterWallpaperList;
                    subListStart = i;
                    subListEnd = i + 1;
                } else { // if (child instanceof LinearLayout) {
                    subList = (LinearLayout) child;
                    subListStart = 0;
                    subListEnd = subList.getChildCount();
                }

                for (int j = subListStart; j < subListEnd; j++) {
                    WallpaperTileInfo info = (WallpaperTileInfo) subList.getChildAt(j).getTag();
                       if (info !=null && info.isNamelessWallpaper()) {
                        if (passNum == 0) {
                            numTiles++;
                        } else {
                            CharSequence label = res.getString(
                                    R.string.wallpaper_accessibility_name, ++tileIndex, numTiles);
                            info.onIndexUpdated(label);
                        }
                    }
                }
            }
        }
    }

    ///M. ALPS2008466, MOVE getDefaultThumbnailSize(), createThumbnail() to WallpaperCropperActivity.
    ///M.

    private void addTemporaryWallpaperTile(final Uri uri, final boolean fromRestore) {
        addTemporaryWallpaperTile(uri,fromRestore,false);
    }
    private void addTemporaryWallpaperTile(final Uri uri, final boolean fromRestore,final boolean update) {
        mTempWallpaperTiles.add(uri);
        // Add a tile for the image picked from Gallery
        final FrameLayout pickedImageThumbnail = (FrameLayout) getLayoutInflater().
                inflate(R.layout.wallpaper_picker_item, mWallpapersView, false);
        pickedImageThumbnail.setVisibility(View.GONE);
        setWallpaperItemPaddingToZero(pickedImageThumbnail);
        mWallpapersView.addView(pickedImageThumbnail, 0);

        // Load the thumbnail
        final ImageView image = (ImageView) pickedImageThumbnail.findViewById(R.id.wallpaper_image);
        final Point defaultSize = getDefaultThumbnailSize(this.getResources());
        final Context context = this;

        // M:Wallpaper display black when rotating device,ALPS01804213
        
            new AsyncTask<Void, Bitmap, Bitmap>() {
                protected Bitmap doInBackground(Void... args) {
                    try {
                        int rotation = WallpaperCropActivity.getRotationFromExif(context, uri);
                        return createThumbnail(defaultSize, context, uri, null, null, 0, rotation,
                                false);
                    } catch (SecurityException securityException) {
                        if (isDestroyed()) {
                            // Temporarily granted permissions are revoked when the activity
                            // finishes, potentially resulting in a SecurityException here.
                            // Even though {@link #isDestroyed} might also return true in different
                            // situations where the configuration changes, we are fine with
                            // catching these cases here as well.
                            cancel(false);
                        } else {
                            // otherwise it had a different cause and we throw it further
                            throw securityException;
                        }
                        return null;
                    }
                }

                protected void onPostExecute(Bitmap thumb) {
                    if (!isCancelled() && thumb != null) {
                        image.setImageBitmap(thumb);
                        Drawable thumbDrawable = image.getDrawable();
                        thumbDrawable.setDither(true);
                        pickedImageThumbnail.setVisibility(View.VISIBLE);
                    } else {
                        // /M : add a toast when it is a broken pic.
                        Log.e(TAG, "Error loading thumbnail for uri=" + uri);
                        String str = getString(R.string.wallpaper_load_fail);
                        Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
                        mWallpapersView.removeViewAt(0);
                        mTempWallpaperTiles.remove(uri);
                        return;
                        // /M.
                    }
                    UriWallpaperInfo info = new UriWallpaperInfo(uri);
                    pickedImageThumbnail.setTag(info);
                    info.setView(pickedImageThumbnail);
                    addLongPressHandler(pickedImageThumbnail);
                    updateTileIndices();
                    pickedImageThumbnail.setOnClickListener(mThumbnailOnClickListener);
                    if (!fromRestore) {
                        mThumbnailOnClickListener.onClick(pickedImageThumbnail);
            	}else{
                    /// M:Wallpaper display black when rotating device,ALPS01804213
                    if (update && isTablet() && mSelectedIndex >= 0 && mSelectedIndex < mTempWallpaperTiles.size()) {
                        mThumbnailOnClickListener.onClick(
                        		mWallpapersView.getChildAt(mSelectedIndex));
                    } 
            	}
            }
    	}.execute();
        // M:end
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		
        Log.i(TAG, "onActivityResult: requestCode = " + requestCode
                + ", resultCode = " + resultCode + ", data = " + data);
		
        if (requestCode == IMAGE_PICK && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                addTemporaryWallpaperTile(uri, false);
            }
        } else if (requestCode == PICK_WALLPAPER_THIRD_PARTY_ACTIVITY) {
            setResult(RESULT_OK);
            finish();
        } else if (requestCode == PICK_LIVE_WALLPAPER) {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            final WallpaperInfo oldLiveWallpaper = mLiveWallpaperInfoOnPickerLaunch;
            final WallpaperInfo clickedWallpaper = mLastClickedLiveWallpaperInfo;
            WallpaperInfo newLiveWallpaper = wm.getWallpaperInfo();
            // Try to figure out if a live wallpaper was set;
            if (newLiveWallpaper != null &&
                    (oldLiveWallpaper == null
                            || !oldLiveWallpaper.getComponent()
                                    .equals(newLiveWallpaper.getComponent())
                            || clickedWallpaper.getComponent()
                                    .equals(oldLiveWallpaper.getComponent()))) {
                // Return if a live wallpaper was set
                setResult(RESULT_OK);
                finish();
            }
        }
    }

    static void setWallpaperItemPaddingToZero(FrameLayout frameLayout) {
        frameLayout.setPadding(0, 0, 0, 0);
        frameLayout.setForeground(new ZeroPaddingDrawable(frameLayout.getForeground()));
    }

    private void addLongPressHandler(View v) {
        v.setOnLongClickListener(mLongClickListener);
    }

    private ArrayList<WallpaperTileInfo> findBundledWallpapers() {
        final PackageManager pm = getPackageManager();
        final ArrayList<WallpaperTileInfo> bundled = new ArrayList<WallpaperTileInfo>(24);

        Partner partner = Partner.get(pm);
        if (partner != null) {
            final Resources partnerRes = partner.getResources();
            final int resId = partnerRes.getIdentifier(Partner.RES_WALLPAPERS, "array",
                    partner.getPackageName());
            if (resId != 0) {
                addWallpapers(bundled, partnerRes, partner.getPackageName(), resId);
            }

            // Add system wallpapers
            File systemDir = partner.getWallpaperDirectory();
            if (systemDir != null && systemDir.isDirectory()) {
                for (File file : systemDir.listFiles()) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String name = file.getName();
                    int dotPos = name.lastIndexOf('.');
                    String extension = "";
                    if (dotPos >= -1) {
                        extension = name.substring(dotPos);
                        name = name.substring(0, dotPos);
                    }

                    if (name.endsWith("_small")) {
                        // it is a thumbnail
                        continue;
                    }

                    File thumbnail = new File(systemDir, name + "_small" + extension);
                    Bitmap thumb = BitmapFactory.decodeFile(thumbnail.getAbsolutePath());
                    if (thumb != null) {
                        bundled.add(new FileWallpaperInfo(file, new BitmapDrawable(thumb)));
                    }
                }
            }
        }

        Pair<ApplicationInfo, Integer> r = getWallpaperArrayResourceId();
        if (r != null) {
            try {
                Resources wallpaperRes = getPackageManager().getResourcesForApplication(r.first);
                addWallpapers(bundled, wallpaperRes, r.first.packageName, r.second);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }

        if (partner == null || !partner.hideDefaultWallpaper()) {
            // Add an entry for the default wallpaper (stored in system resources)
            WallpaperTileInfo defaultWallpaperInfo =
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
                    ? getPreKKDefaultWallpaperInfo()
                    : getDefaultWallpaper();
            if (defaultWallpaperInfo != null) {
                bundled.add(0, defaultWallpaperInfo);
            }
        }
        return bundled;
    }

    private boolean writeImageToFileAsJpeg(File f, Bitmap b) {
        try {
            f.createNewFile();
            FileOutputStream thumbFileStream =
                    openFileOutput(f.getName(), Context.MODE_PRIVATE);
            b.compress(Bitmap.CompressFormat.JPEG, 95, thumbFileStream);
            thumbFileStream.close();
            ///M. for smartbook
            setSmartBookPrefs();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error while writing bitmap to file " + e);
            f.delete();
        }
        return false;
    }

    private File getDefaultThumbFile() {
        return new File(getFilesDir(), Build.VERSION.SDK_INT
                + "_" + LauncherFiles.DEFAULT_WALLPAPER_THUMBNAIL);
    }

    private boolean saveDefaultWallpaperThumb(Bitmap b) {
        // Delete old thumbnails.
        new File(getFilesDir(), LauncherFiles.DEFAULT_WALLPAPER_THUMBNAIL_OLD).delete();
        new File(getFilesDir(), LauncherFiles.DEFAULT_WALLPAPER_THUMBNAIL).delete();

        for (int i = Build.VERSION_CODES.JELLY_BEAN; i < Build.VERSION.SDK_INT; i++) {
            new File(getFilesDir(), i + "_" + LauncherFiles.DEFAULT_WALLPAPER_THUMBNAIL).delete();
        }
        return writeImageToFileAsJpeg(getDefaultThumbFile(), b);
    }

    private ResourceWallpaperInfo getPreKKDefaultWallpaperInfo() {
        Resources sysRes = Resources.getSystem();
        int resId = sysRes.getIdentifier("default_wallpaper", "drawable", "android");

        File defaultThumbFile = getDefaultThumbFile();
        Bitmap thumb = null;
        boolean defaultWallpaperExists = false;
        if (defaultThumbFile.exists()) {
            thumb = BitmapFactory.decodeFile(defaultThumbFile.getAbsolutePath());
            defaultWallpaperExists = true;
        } else {
            Resources res = getResources();
            Point defaultThumbSize = getDefaultThumbnailSize(res);
            int rotation = WallpaperCropActivity.getRotationFromExif(res, resId);
            thumb = createThumbnail(
                    defaultThumbSize, this, null, null, sysRes, resId, rotation, false);
            if (thumb != null) {
                defaultWallpaperExists = saveDefaultWallpaperThumb(thumb);
            }
        }
        if (defaultWallpaperExists) {
            return new ResourceWallpaperInfo(sysRes, resId, new BitmapDrawable(thumb));
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private DefaultWallpaperInfo getDefaultWallpaper() {
        File defaultThumbFile = getDefaultThumbFile();
        Bitmap thumb = null;
        boolean defaultWallpaperExists = false;
        if (defaultThumbFile.exists()) {
            thumb = BitmapFactory.decodeFile(defaultThumbFile.getAbsolutePath());
            defaultWallpaperExists = true;
        }

        ///M. ALPS2029155, maybe the defaultThumbFile exist, but the content is null.
        Log.i(TAG, "getDefaultWallpaper.thumb" + thumb
            + "defaultThumbFile.exists():" + defaultThumbFile.exists());
        if (thumb == null) {
        ///M.
            Resources res = getResources();
            Point defaultThumbSize = getDefaultThumbnailSize(res);
            Drawable wallpaperDrawable = WallpaperManager.getInstance(this).getBuiltInDrawable(
                    defaultThumbSize.x, defaultThumbSize.y, true, 0.5f, 0.5f);
            if (wallpaperDrawable != null) {
                thumb = Bitmap.createBitmap(
                        defaultThumbSize.x, defaultThumbSize.y, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(thumb);
                wallpaperDrawable.setBounds(0, 0, defaultThumbSize.x, defaultThumbSize.y);
                wallpaperDrawable.draw(c);
                c.setBitmap(null);
            }
            if (thumb != null) {
                defaultWallpaperExists = saveDefaultWallpaperThumb(thumb);
            }
            Log.i(TAG, "getDefaultWallpaper.thumb" + thumb
            + "wallpaperDrawable:" + wallpaperDrawable);
        }
        if (defaultWallpaperExists) {
            return new DefaultWallpaperInfo(new BitmapDrawable(thumb));
        }
        return null;
    }
    
    ///M.
    private void deleteDefaultWallpaperThumbnail() {
        ///M: ALPS01841789, the file name is wrong. please see getDefaultThumbFile
        File defaultThumbFile = new File(getFilesDir(), Build.VERSION.SDK_INT
                + "_" + DEFAULT_WALLPAPER_THUMBNAIL_FILENAME);
        ///M.

        if (defaultThumbFile.exists()) {
            defaultThumbFile.delete();
        }
    }

    public static boolean isSmartBookPluggedIn(Context mContext) {
        if (SystemProperties.get("ro.mtk_smartbook_support").equals("1")) {
            DisplayManager mDisplayManager = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
            return mDisplayManager.isSmartBookPluggedIn();
        } else {
            return false;
        }
    }

    private String getSmartBookPrefsFile() {
        return WallpaperCropActivity.class.getName();
    }

    private void setSmartBookPrefs() {
        String spKey = getSmartBookPrefsFile();
        SharedPreferences sp = getSharedPreferences(spKey, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(SMARTBOOK_PLUGIN_KEY, isSmartBookPluggedIn(this));
        editor.commit();
    }
    
    private boolean getSmartBookPrefs() {
        String spKey = getSmartBookPrefsFile();
        SharedPreferences sp = getSharedPreferences(spKey, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        return sp.getBoolean(SMARTBOOK_PLUGIN_KEY, false);
    }
    ///M.}

    public Pair<ApplicationInfo, Integer> getWallpaperArrayResourceId() {
        // Context.getPackageName() may return the "original" package name,
        // com.android.launcher3; Resources needs the real package name,
        // com.android.launcher3. So we ask Resources for what it thinks the
        // package name should be.
        final String packageName = getResources().getResourcePackageName(R.array.wallpapers);
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return new Pair<ApplicationInfo, Integer>(info, R.array.wallpapers);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void addWallpapers(ArrayList<WallpaperTileInfo> known, Resources res,
            String packageName, int listResId) {
        final String[] extras = res.getStringArray(listResId);
        for (String extra : extras) {
            int resId = res.getIdentifier(extra, "drawable", packageName);
            if (resId != 0) {
                final int thumbRes = res.getIdentifier(extra + "_small", "drawable", packageName);

                if (thumbRes != 0) {
                    ResourceWallpaperInfo wallpaperInfo =
                            new ResourceWallpaperInfo(res, resId, res.getDrawable(thumbRes));
                    known.add(wallpaperInfo);
                    // Log.d(TAG, "add: [" + packageName + "]: " + extra + " (" + res + ")");
                }
            } else {
                Log.e(TAG, "Couldn't find wallpaper " + extra);
            }
        }
    }

    public CropView getCropView() {
        return mCropView;
    }

    public SavedWallpaperImages getSavedImages() {
        return mSavedImages;
    }

    public void onLiveWallpaperPickerLaunch(WallpaperInfo info) {
        mLastClickedLiveWallpaperInfo = info;
        mLiveWallpaperInfoOnPickerLaunch = WallpaperManager.getInstance(this).getWallpaperInfo();
    }

    static class ZeroPaddingDrawable extends LevelListDrawable {
        public ZeroPaddingDrawable(Drawable d) {
            super();
            addLevel(0, 0, d);
            setLevel(0);
        }

        @Override
        public boolean getPadding(Rect padding) {
            padding.set(0, 0, 0, 0);
            return true;
        }
    }

    private static class SimpleWallpapersAdapter extends ArrayAdapter<WallpaperTileInfo> {
        private final LayoutInflater mLayoutInflater;

        SimpleWallpapersAdapter(Activity activity, ArrayList<WallpaperTileInfo> wallpapers) {
            super(activity, R.layout.wallpaper_picker_item, wallpapers);
            mLayoutInflater = activity.getLayoutInflater();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Drawable thumb = getItem(position).mThumb;
            if (thumb == null) {
                Log.e(TAG, "Error decoding thumbnail for wallpaper #" + position);
            }
            return createImageTileView(mLayoutInflater, convertView, parent, thumb);
        }
    }

    public static View createImageTileView(LayoutInflater layoutInflater,
            View convertView, ViewGroup parent, Drawable thumb) {
        View view;

        if (convertView == null) {
            view = layoutInflater.inflate(R.layout.wallpaper_picker_item, parent, false);
        } else {
            view = convertView;
        }

        setWallpaperItemPaddingToZero((FrameLayout) view);

        ImageView image = (ImageView) view.findViewById(R.id.wallpaper_image);

        if (thumb != null) {
            image.setImageDrawable(thumb);
            thumb.setDither(true);
        }

        return view;
    }

    // In Launcher3, we override this with a method that catches exceptions
    // from starting activities; didn't want to copy and paste code into here
    public void startActivityForResultSafely(Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode);
    }
}
