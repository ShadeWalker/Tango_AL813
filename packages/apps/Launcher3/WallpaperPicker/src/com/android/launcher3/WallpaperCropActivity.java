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

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifInterface;
import com.android.photos.BitmapRegionTileSource;
import com.android.photos.BitmapRegionTileSource.BitmapSource;
/// M: ALPS01624446, Modify screen rotation.
import android.content.pm.ActivityInfo;
/// M.
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.SecurityException;
import com.mediatek.multiwindow.MultiWindowProxy;

public class WallpaperCropActivity extends Activity {
    private static final String LOGTAG = "Launcher3.CropActivity";

    protected static final String WALLPAPER_WIDTH_KEY = "wallpaper.width";
    protected static final String WALLPAPER_HEIGHT_KEY = "wallpaper.height";
    private static final int DEFAULT_COMPRESS_QUALITY = 90;
    /**
     * The maximum bitmap size we allow to be returned through the intent.
     * Intents have a maximum of 1MB in total size. However, the Bitmap seems to
     * have some overhead to hit so that we go way below the limit here to make
     * sure the intent stays below 1MB.We should consider just returning a byte
     * array instead of a Bitmap instance to avoid overhead.
     */
    public static final int MAX_BMAP_IN_INTENT = 750000;
    private static final float WALLPAPER_SCREENS_SPAN = 2f;

    //M. ALPS01885181, sync with gallery, check the image size.
    private final static String MIME_GIF = "image/gif";
    private final static String MIME_BMP = "image/bmp";
    private final static String MIME_JPEG = "image/jpeg";
    // GIF: None LCA:20MB, LCA:10MB
    private final static int MAX_GIF_FILE_SIZE_NONE_LCA = 20 * 1024 * 1024;
    private final static int MAX_GIF_FILE_SIZE_LCA = 10 * 1024 * 1024;

    private final static long MAX_GIF_FRAME_PIXEL_SIZE = (long) (1.5f * 1024 * 1024); // 1.5MB

    // BMP & WBMP: NONE-LCA file size < 52MB, LCA file size < 6MB
    private final static int MAX_BMP_FILE_SIZE_NONE_LCA = 52 * 1024 * 1024;
    private final static int MAX_BMP_FILE_SIZE_LCA = 6 * 1024 * 1024;

    // JPGE: Height < 8192, Width < 8192
    private final static int MAX_JPEG_DECODE_LENGTH = 8192;
    /// M.

    protected static Point sDefaultWallpaperSize;

    protected CropView mCropView;
    protected Uri mUri;
    protected View mSetWallpaperButton;

    protected boolean isFromGallery = false;
	
	private static final boolean mIsOmaDrmSupport =
			(SystemProperties.getInt("ro.mtk_oma_drm_support", 0) == 1) ? true : false;

    ///M. ALPS2008466, add this varable for save the wall to database.
    private SavedWallpaperImages mSavedWallpaper;
    ///M.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		
        Log.i(LOGTAG, "WallpaperCropActivity.onCreate");
        if (!enableRotation()) {
            setRequestedOrientation(Configuration.ORIENTATION_PORTRAIT);
        }
        init();
    }
    
    @Override
    protected void onStop() {
        Log.i(LOGTAG, "WallpaperCropActivity.onStop");
        // be aware of mCropView.destroy do finish tile decoder
        //mCropView.destroy();
        super.onStop();
    }

    protected void init() {
        setContentView(R.layout.wallpaper_cropper);

        mCropView = (CropView) findViewById(R.id.cropView);

        Intent cropIntent = getIntent();
        final Uri imageUri = cropIntent.getData();

        if (imageUri == null) {
            Log.e(LOGTAG, "No URI passed in intent, exiting WallpaperCropActivity");
            finish();
            return;
        }

        // Action bar
        // Show the custom action bar view
        final ActionBar actionBar = getActionBar();
        actionBar.setCustomView(R.layout.actionbar_set_wallpaper);
        actionBar.getCustomView().setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ///M. ALPS02022237, set the button can't be clicked.
                    mSetWallpaperButton.setEnabled(false);
                    ///M.

                    boolean finishActivityWhenDone = true;

                    ///M. ALPS2008466, add this handle for save the wall to database.
                    OnBitmapCroppedHandler h = new OnBitmapCroppedHandler() {
                        public void onBitmapCropped(byte[] imageBytes) {
                            Point thumbSize = getDefaultThumbnailSize(WallpaperCropActivity.this.getResources());
                            // rotation is set to 0 since imageBytes has already been correctly rotated
                            Bitmap thumb = createThumbnail(
                                    thumbSize, null, null, imageBytes, null, 0, 0, true);
                            mSavedWallpaper.writeImage(thumb, imageBytes);
                        }
                    };

                    cropImageAndSetWallpaper(imageUri, h, finishActivityWhenDone);
                    ///M.
                }
            });
        mSetWallpaperButton = findViewById(R.id.set_wallpaper_button);

        // Load image in background
        final BitmapRegionTileSource.UriBitmapSource bitmapSource =
                new BitmapRegionTileSource.UriBitmapSource(this, imageUri, 1024);
        mSetWallpaperButton.setEnabled(false);
        Runnable onLoad = new Runnable() {
            public void run() {
                if (bitmapSource.getLoadingState() != BitmapSource.State.LOADED) {
                    Toast.makeText(WallpaperCropActivity.this,
                            getString(R.string.wallpaper_load_fail),
                            Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    mSetWallpaperButton.setEnabled(true);
                }
            }
        };
        setCropViewTileSource(bitmapSource, true, false, onLoad);

        ///M. ALPS2008466, add this varable for save the wall to database.
        mSavedWallpaper = new SavedWallpaperImages(this);
        ///M.
        isFromGallery = true;
        Log.d(LOGTAG, "WallpaperCropActivity, init(), isFromGallery = " + isFromGallery);
    }

    @Override
    protected void onDestroy() {
        Log.i(LOGTAG, "WallpaperCropActivity.onDestroy");
        if (mCropView != null) {
            mCropView.destroy();
        }
        super.onDestroy();
    }

    // M:ALPS02006062  to avoid asynchronous timing issue if change wallpaper tile quickly
    protected boolean mIsFinishLoadBitmapTask = true;
    protected View mProgressView;
    public void setCropViewTileSource(
            final BitmapRegionTileSource.BitmapSource bitmapSource, final boolean touchEnabled,
            final boolean moveToLeft, final Runnable postExecute) {
        final Context context = WallpaperCropActivity.this;
        mProgressView = findViewById(R.id.loading);
        final AsyncTask<Void, Void, Void> loadBitmapTask = new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void...args) {
                if (!isCancelled()) {
                    try {
                        bitmapSource.loadInBackground();
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
                    }
                }
                return null;
            }
            protected void onPostExecute(Void arg) {
                if (!isCancelled() && (isFromGallery || !mIsFinishLoadBitmapTask)) {
                    mProgressView.setVisibility(View.INVISIBLE);
                    if (bitmapSource.getLoadingState() == BitmapSource.State.LOADED) {
                        mCropView.setTileSource(
                                new BitmapRegionTileSource(context, bitmapSource), null);
                        mCropView.setTouchEnabled(touchEnabled);
                        if (moveToLeft) {
                            mCropView.moveToLeft();
                        }
                    }
                    Log.d(LOGTAG, "setCropViewTileSource.onPostExecute(),  set mIsFinishLoadBitmapTask = true");
                    mIsFinishLoadBitmapTask = true;
                }
                if (postExecute != null) {
                    postExecute.run();
                }
            }
        };
        // We don't want to show the spinner every time we load an image, because that would be
        // annoying; instead, only start showing the spinner if loading the image has taken
        // longer than 1 sec (ie 1000 ms)
        mProgressView.postDelayed(new Runnable() {
            public void run() {
                if (loadBitmapTask.getStatus() != AsyncTask.Status.FINISHED && !mIsFinishLoadBitmapTask) {
                    Log.d(LOGTAG, "mProgressView.setVisibility(View.VISIBLE);");
                    mProgressView.setVisibility(View.VISIBLE);
                }
            }
        }, 1000);
        loadBitmapTask.execute();
    }

    public boolean enableRotation() {
        return getResources().getBoolean(R.bool.allow_rotation);
    }

    public static String getSharedPreferencesKey() {
        return LauncherFiles.WALLPAPER_CROP_PREFERENCES_KEY;
    }

    // As a ratio of screen height, the total distance we want the parallax effect to span
    // horizontally
    private static float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16/10f;
        final float ASPECT_RATIO_PORTRAIT = 10/16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
            (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
            (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    static protected Point getDefaultWallpaperSize(Resources res, WindowManager windowManager) {
        if (sDefaultWallpaperSize == null) {
            Point minDims = new Point();
            Point maxDims = new Point();
            windowManager.getDefaultDisplay().getCurrentSizeRange(minDims, maxDims);

            int maxDim = Math.max(maxDims.x, maxDims.y);
            int minDim = Math.max(minDims.x, minDims.y);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Point realSize = new Point();
                windowManager.getDefaultDisplay().getRealSize(realSize);
                maxDim = Math.max(realSize.x, realSize.y);
                minDim = Math.min(realSize.x, realSize.y);
				
				Log.i(LOGTAG, "getDefaultWallpaperSize, realSize: " + realSize);
            }
			Log.i(LOGTAG, "getDefaultWallpaperSize, maxDim: " + maxDim + ", minDim: " + minDim);

            // We need to ensure that there is enough extra space in the wallpaper
            // for the intended parallax effects
            final int defaultWidth, defaultHeight;
            if (isScreenLarge(res)) {
                defaultWidth = (int) (maxDim * wallpaperTravelToScreenWidthRatio(maxDim, minDim));
                defaultHeight = maxDim;
				
				Log.i(LOGTAG, "getDefaultWallpaperSize, wallpaperTravelToScreenWidthRatio(maxDim, minDim): " + 
					wallpaperTravelToScreenWidthRatio(maxDim, minDim));
            } else {
                defaultWidth = Math.max((int) (minDim * WALLPAPER_SCREENS_SPAN), maxDim);
                defaultHeight = maxDim;
            }
            sDefaultWallpaperSize = new Point(defaultWidth, defaultHeight);
        }
		
		Log.i(LOGTAG, "getDefaultWallpaperSize, sDefaultWallpaperSize: " + sDefaultWallpaperSize);
        return sDefaultWallpaperSize;
    }

    public static int getRotationFromExif(String path) {
        return getRotationFromExifHelper(path, null, 0, null, null);
    }

    public static int getRotationFromExif(Context context, Uri uri) {
        return getRotationFromExifHelper(null, null, 0, context, uri);
    }

    public static int getRotationFromExif(Resources res, int resId) {
        return getRotationFromExifHelper(null, res, resId, null, null);
    }

    private static int getRotationFromExifHelper(
            String path, Resources res, int resId, Context context, Uri uri) {
        ExifInterface ei = new ExifInterface();
        InputStream is = null;
        BufferedInputStream bis = null;
        try {
            if (path != null) {
                ei.readExif(path);
            } else if (uri != null) {
                is = context.getContentResolver().openInputStream(uri);
                bis = new BufferedInputStream(is);
                ei.readExif(bis);
            } else {
                is = res.openRawResource(resId);
                bis = new BufferedInputStream(is);
                ei.readExif(bis);
            }
            Integer ori = ei.getTagIntValue(ExifInterface.TAG_ORIENTATION);
            if (ori != null) {
                return ExifInterface.getRotationForOrientationValue(ori.shortValue());
            }
        } catch (IOException e) {
            Log.w(LOGTAG, "Getting exif data failed", e);
        } catch (NullPointerException e) {
            // Sometimes the ExifInterface has an internal NPE if Exif data isn't valid
            Log.w(LOGTAG, "Getting exif data failed", e);
        } finally {
            Utils.closeSilently(bis);
            Utils.closeSilently(is);
        }
        return 0;
    }

    protected void setWallpaper(Uri uri, final boolean finishActivityWhenDone) {
        int rotation = getRotationFromExif(this, uri);
        BitmapCropTask cropTask = new BitmapCropTask(
                this, uri, null, rotation, 0, 0, true, false, null);
        final Point bounds = cropTask.getImageBounds();
        Runnable onEndCrop = new Runnable() {
            public void run() {
                updateWallpaperDimensions(bounds.x, bounds.y);
                if (finishActivityWhenDone) {
                    setResult(Activity.RESULT_OK);
                    finish();
                }
            }
        };
        cropTask.setCropSize(bounds.x, bounds.y);
        cropTask.setOnEndRunnable(onEndCrop);
        cropTask.setNoCrop(true);
        cropTask.execute();
    }

    protected void cropImageAndSetWallpaper(
            Resources res, int resId, final boolean finishActivityWhenDone) {
        // crop this image and scale it down to the default wallpaper size for
        // this device
        int rotation = getRotationFromExif(res, resId);
        Point inSize = mCropView.getSourceDimensions();
        Point outSize = getDefaultWallpaperSize(getResources(),
                getWindowManager());
        RectF crop = getMaxCropRect(
                inSize.x, inSize.y, outSize.x, outSize.y, false);
        Runnable onEndCrop = new Runnable() {
            public void run() {
                // Passing 0, 0 will cause launcher to revert to using the
                // default wallpaper size
                updateWallpaperDimensions(0, 0);
                if (finishActivityWhenDone) {
                    setResult(Activity.RESULT_OK);
                    finish();
                }
            }
        };
        BitmapCropTask cropTask = new BitmapCropTask(this, res, resId,
                crop, rotation, outSize.x, outSize.y, true, false, onEndCrop);
        cropTask.setCropSize(outSize.x, outSize.y);
        cropTask.execute();
    }

    private static boolean isScreenLarge(Resources res) {
        Configuration config = res.getConfiguration();
        return config.smallestScreenWidthDp >= 720;
    }

    protected void cropImageAndSetWallpaper(Uri uri,
            OnBitmapCroppedHandler onBitmapCroppedHandler, final boolean finishActivityWhenDone) {
        boolean centerCrop = getResources().getBoolean(R.bool.center_crop);
        // Get the crop
        boolean ltr = mCropView.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;

        Display d = getWindowManager().getDefaultDisplay();

        Point displaySize = new Point();
        d.getSize(displaySize);
        boolean isPortrait = displaySize.x < displaySize.y;

        Point defaultWallpaperSize = getDefaultWallpaperSize(getResources(),
                getWindowManager());
        // Get the crop
        RectF cropRect = mCropView.getCrop();

        Point inSize = mCropView.getSourceDimensions();

        int cropRotation = mCropView.getImageRotation();
        float cropScale = mCropView.getWidth() / (float) cropRect.width();


        Matrix rotateMatrix = new Matrix();
        rotateMatrix.setRotate(cropRotation);
        float[] rotatedInSize = new float[] { inSize.x, inSize.y };
        rotateMatrix.mapPoints(rotatedInSize);
        rotatedInSize[0] = Math.abs(rotatedInSize[0]);
        rotatedInSize[1] = Math.abs(rotatedInSize[1]);


        // due to rounding errors in the cropview renderer the edges can be slightly offset
        // therefore we ensure that the boundaries are sanely defined
        cropRect.left = Math.max(0, cropRect.left);
        cropRect.right = Math.min(rotatedInSize[0], cropRect.right);
        cropRect.top = Math.max(0, cropRect.top);
        cropRect.bottom = Math.min(rotatedInSize[1], cropRect.bottom);

        // ADJUST CROP WIDTH
        // Extend the crop all the way to the right, for parallax
        // (or all the way to the left, in RTL)
        float extraSpace;
        if (centerCrop) {
            extraSpace = 2f * Math.min(rotatedInSize[0] - cropRect.right, cropRect.left);
        } else {
            extraSpace = ltr ? rotatedInSize[0] - cropRect.right : cropRect.left;
        }
        // Cap the amount of extra width
        float maxExtraSpace = defaultWallpaperSize.x / cropScale - cropRect.width();
        extraSpace = Math.min(extraSpace, maxExtraSpace);

        if (centerCrop) {
            cropRect.left -= extraSpace / 2f;
            cropRect.right += extraSpace / 2f;
        } else {
            if (ltr) {
                cropRect.right += extraSpace;
            } else {
                cropRect.left -= extraSpace;
            }
        }

        // ADJUST CROP HEIGHT
        if (MultiWindowProxy.isFeatureSupport()
                    && MultiWindowProxy.getInstance() != null
                    && MultiWindowProxy.getInstance().getFloatingState()) {
            // LANDSCAPE  in floating mode
            float extraPortraitHeight =
                    defaultWallpaperSize.y / cropScale - cropRect.height();
            float expandHeight =
                    Math.min(Math.min(rotatedInSize[1] - cropRect.bottom, cropRect.top),
                            extraPortraitHeight / 2);
            cropRect.top -= expandHeight;
            cropRect.bottom += expandHeight;
        } else {
            if (isPortrait) {
                cropRect.bottom = cropRect.top + defaultWallpaperSize.y / cropScale;
            } else { // LANDSCAPE
                float extraPortraitHeight =
                        defaultWallpaperSize.y / cropScale - cropRect.height();
                float expandHeight =
                        Math.min(Math.min(rotatedInSize[1] - cropRect.bottom, cropRect.top),
                                extraPortraitHeight / 2);
                cropRect.top -= expandHeight;
                cropRect.bottom += expandHeight;
            }
        }
        final int outWidth = (int) Math.round(cropRect.width() * cropScale);
        final int outHeight = (int) Math.round(cropRect.height() * cropScale);

        Runnable onEndCrop = new Runnable() {
            public void run() {
                updateWallpaperDimensions(outWidth, outHeight);
                if (finishActivityWhenDone) {
                    setResult(Activity.RESULT_OK);
                    finish();
                }
            }
        };
        BitmapCropTask cropTask = new BitmapCropTask(this, uri,
                cropRect, cropRotation, outWidth, outHeight, true, false, onEndCrop);
        cropTask.setCropSize(outWidth, outHeight);
        if (onBitmapCroppedHandler != null) {
            cropTask.setOnBitmapCropped(onBitmapCroppedHandler);
        }
        cropTask.execute();
    }

    public interface OnBitmapCroppedHandler {
        public void onBitmapCropped(byte[] imageBytes);
    }

    /// M: revised to public
    public static class BitmapCropTask extends AsyncTask<Void, Void, Boolean> {
        Uri mInUri = null;
        Context mContext;
        String mInFilePath;
        byte[] mInImageBytes;
        int mInResId = 0;
        RectF mCropBounds = null;
        int mOutWidth, mOutHeight;
        int mRotation;
        String mOutputFormat = "jpg"; // for now
        boolean mSetWallpaper;
        boolean mSaveCroppedBitmap;
        Bitmap mCroppedBitmap;
        Runnable mOnEndRunnable;
        Resources mResources;
        OnBitmapCroppedHandler mOnBitmapCroppedHandler;
        boolean mNoCrop;
        int mCropWidth;
        int mCropHeight;
        static final int WALLPAPER_CROP_REGION_SIZE_LIMIT = 2048;

        public BitmapCropTask(Context c, String filePath,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mContext = c;
            mInFilePath = filePath;
            init(cropBounds, rotation,
                    outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
        }

        public BitmapCropTask(byte[] imageBytes,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mInImageBytes = imageBytes;
            init(cropBounds, rotation,
                    outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
        }

        public BitmapCropTask(Context c, Uri inUri,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mContext = c;
            mInUri = inUri;
            init(cropBounds, rotation,
                    outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
        }

        public BitmapCropTask(Context c, Resources res, int inResId,
                RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mContext = c;
            mInResId = inResId;
            mResources = res;
            init(cropBounds, rotation,
                    outWidth, outHeight, setWallpaper, saveCroppedBitmap, onEndRunnable);
        }

        private void init(RectF cropBounds, int rotation, int outWidth, int outHeight,
                boolean setWallpaper, boolean saveCroppedBitmap, Runnable onEndRunnable) {
            mCropBounds = cropBounds;
            mRotation = rotation;
            mOutWidth = outWidth;
            mOutHeight = outHeight;
            mSetWallpaper = setWallpaper;
            mSaveCroppedBitmap = saveCroppedBitmap;
            mOnEndRunnable = onEndRunnable;
            mCropWidth = 0;
            mCropHeight = 0;
        }

        public void setOnBitmapCropped(OnBitmapCroppedHandler handler) {
            mOnBitmapCroppedHandler = handler;
        }

        public void setNoCrop(boolean value) {
            mNoCrop = value;
        }

        public void setOnEndRunnable(Runnable onEndRunnable) {
            mOnEndRunnable = onEndRunnable;
        }

        public void setCropSize(int width, int height) {
            mCropWidth = width;
            mCropHeight = height;
        }

        // Helper to setup input stream
        private InputStream regenerateInputStream() {
            if (mInUri == null && mInResId == 0 && mInFilePath == null && mInImageBytes == null) {
                Log.w(LOGTAG, "cannot read original file, no input URI, resource ID, or " +
                        "image byte array given");
            } else {
                try {
                    if (mInUri != null) {
						// M: DRM file
                        if (mIsOmaDrmSupport && BitmapRegionTileSource.isDrmFormat(mContext, mInUri) == true) {
                            String filePath = BitmapRegionTileSource.getDrmFilePath(mContext, mInUri);
                            if(filePath != null) {
                                byte[] buffer = BitmapRegionTileSource.forceDecryptFile(filePath, false);
                                Bitmap bitmap = null;
                                if(buffer != null) {
                                    bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.length, null);
                                } else {
                                    Log.w(LOGTAG, "buffer is null");
                                }
                                if(bitmap != null) {
                                    return BitmapRegionTileSource.getByteArrayInputStream(bitmap);
                                } else {
                                    Log.w(LOGTAG, "bitmap is null");
                                    return new BufferedInputStream(
                                        mContext.getContentResolver().openInputStream(mInUri));
                                }
                            } else {
                                Log.w(LOGTAG, "file path is null");
                            }
                        }else{
                        return new BufferedInputStream(
                                mContext.getContentResolver().openInputStream(mInUri));
                        }
                    } else if (mInFilePath != null) {
                        return mContext.openFileInput(mInFilePath);
                    } else if (mInImageBytes != null) {
                        return new BufferedInputStream(new ByteArrayInputStream(mInImageBytes));
                    } else {
                        return new BufferedInputStream(mResources.openRawResource(mInResId));
                    }
                } catch (FileNotFoundException e) {
                    Log.w(LOGTAG, "cannot read file: " + mInUri.toString(), e);
                } catch (SecurityException e) {
                    Log.w(LOGTAG, "security exception: " + mInUri.toString(), e);
                }
            }
            return null;
        }

        public Point getImageBounds() {
            InputStream is = regenerateInputStream();
            if (is != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, options);
                Utils.closeSilently(is);
                if (options.outWidth != 0 && options.outHeight != 0) {
                    return new Point(options.outWidth, options.outHeight);
                }
            }
            return null;
        }

        public void setCropBounds(RectF cropBounds) {
            mCropBounds = cropBounds;
        }

        /// M.
        public int computeSampleSize(int scale) {
            return scale <= 8 ? Utils.prevPowerOf2(scale) : scale / 8 * 8;
        }

        public Bitmap getCroppedBitmap() {
            return mCroppedBitmap;
        }
        public boolean cropBitmap() {
            boolean failure = false;

            //M. ALPS01885181, sync with gallery, check the image size.
            if (isOutOfSpecLimit()) {
                Log.i(LOGTAG, "cropBitmap,image out of spec limit, mInUri:" + mInUri);
                return failure;
            }
            ///M.

            WallpaperManager wallpaperManager = null;
            if (mSetWallpaper) {
                wallpaperManager = WallpaperManager.getInstance(mContext.getApplicationContext());
            }


            if (mSetWallpaper && mNoCrop) {
                try {
                    InputStream is = regenerateInputStream();
                    if (is != null) {
                        wallpaperManager.setStream(is);
                        Utils.closeSilently(is);
                    }
                } catch (IOException e) {
                    Log.w(LOGTAG, "cannot write stream to wallpaper", e);
                    failure = true;
                }
                return !failure;
            } else {
                // Find crop bounds (scaled to original image size)
                Rect roundedTrueCrop = new Rect();
                Matrix rotateMatrix = new Matrix();
                Matrix inverseRotateMatrix = new Matrix();

                Point bounds = getImageBounds();
                if (mRotation > 0) {
                    rotateMatrix.setRotate(mRotation);
                    inverseRotateMatrix.setRotate(-mRotation);

                    mCropBounds.roundOut(roundedTrueCrop);
                    mCropBounds = new RectF(roundedTrueCrop);

                    if (bounds == null) {
                        Log.w(LOGTAG, "cannot get bounds for image");
                        failure = true;
                        return false;
                    }

                    float[] rotatedBounds = new float[] { bounds.x, bounds.y };
                    rotateMatrix.mapPoints(rotatedBounds);
                    rotatedBounds[0] = Math.abs(rotatedBounds[0]);
                    rotatedBounds[1] = Math.abs(rotatedBounds[1]);

                    mCropBounds.offset(-rotatedBounds[0]/2, -rotatedBounds[1]/2);
                    inverseRotateMatrix.mapRect(mCropBounds);
                    mCropBounds.offset(bounds.x/2, bounds.y/2);

                }

                mCropBounds.roundOut(roundedTrueCrop);

                if (roundedTrueCrop.width() <= 0 || roundedTrueCrop.height() <= 0) {
                    Log.w(LOGTAG, "crop has bad values for full size image");
                    failure = true;
                    return false;
                }

                ///M. ALPS02021242, mOutWidth and mOutHeight can't be zero.
                if (mOutWidth <= 0) {
                    Log.w(LOGTAG, "mOutWidth is zero, mOutWidth:" + mOutWidth);
                    mOutWidth = 1;
                }

                if (mOutHeight <= 0) {
                    Log.w(LOGTAG, "mOutHeight is zero, mOutHeight:" + mOutHeight);
                    mOutHeight = 1;
                }
                ///M.

                // See how much we're reducing the size of the image
                int scaleDownSampleSize = Math.max(1, Math.min(roundedTrueCrop.width() / mOutWidth,
                        roundedTrueCrop.height() / mOutHeight));
                // Attempt to open a region decoder
                BitmapRegionDecoder decoder = null;
                InputStream is = null;
                Bitmap crop = null;
                if(roundedTrueCrop.width() * roundedTrueCrop.height() <= 
                    WALLPAPER_CROP_REGION_SIZE_LIMIT * WALLPAPER_CROP_REGION_SIZE_LIMIT) {
	                try {
	                    is = regenerateInputStream();
	                    if (is == null) {
	                        Log.w(LOGTAG, "cannot get input stream for uri=" + mInUri.toString());
	                        failure = true;
	                        return false;
	                    }
	                    decoder = BitmapRegionDecoder.newInstance(is, false);
	                    Utils.closeSilently(is);
	                } catch (IOException e) {
	                    Log.w(LOGTAG, "cannot open region decoder for file: " + mInUri.toString(), e);
	                } finally {
	                   Utils.closeSilently(is);
	                   is = null;
	                }
              
	                if (decoder != null) {
	                    // Do region decoding to get crop bitmap
	                    BitmapFactory.Options options = new BitmapFactory.Options();
	                    if (scaleDownSampleSize > 1) {
	                        options.inSampleSize = scaleDownSampleSize;
	                    }
	                    crop = decoder.decodeRegion(roundedTrueCrop, options);
	                    decoder.recycle();
	                }
                } else {
                    Log.w(LOGTAG, "crop region is too large: " + roundedTrueCrop);
                }

                if (crop == null) {
                    // BitmapRegionDecoder has failed, try to crop in-memory
                    is = regenerateInputStream();
                    Bitmap fullSize = null;
                    if (is != null) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        if (scaleDownSampleSize > 1) {
                             /// M: it need to be power of 2
                            scaleDownSampleSize = computeSampleSize(scaleDownSampleSize);
                            options.inSampleSize = scaleDownSampleSize;
                        }

                        /// M. for large size image.
                        try {
                            fullSize = BitmapFactory.decodeStream(is, null, options);
                        } catch (OutOfMemoryError e) {
                            Log.w(LOGTAG, "Can't decode large size image");
                            failure = true;
                            return false;
                        }
                        ///M.

                        Utils.closeSilently(is);
                    }
                    if (fullSize != null) {
                        // Find out the true sample size that was used by the decoder
                        scaleDownSampleSize = bounds.x / fullSize.getWidth();
                        /**M: The scaleDownSampleSize SHOULD NOT be 0.@{**/
                        if(scaleDownSampleSize == 0) {
                            scaleDownSampleSize = 1;
                        }
                        /**@}**/
                        mCropBounds.left /= scaleDownSampleSize;
                        mCropBounds.top /= scaleDownSampleSize;
                        mCropBounds.bottom /= scaleDownSampleSize;
                        mCropBounds.right /= scaleDownSampleSize;
                        mCropBounds.roundOut(roundedTrueCrop);

                        if(roundedTrueCrop.left < 0) {
                            roundedTrueCrop.left = 0;
                        }
                        if(roundedTrueCrop.top < 0) {
                            roundedTrueCrop.top = 0;
                        }

                        /**M: removed the wrong solution from Google and replaced with below MTK solution. ALPS01669050. @{**/
                        /*
                        // Adjust values to account for issues related to rounding
                        if (roundedTrueCrop.width() > fullSize.getWidth()) {
                            // Adjust the width
                            roundedTrueCrop.right = roundedTrueCrop.left + fullSize.getWidth();
                        }
                        if (roundedTrueCrop.right > fullSize.getWidth()) {
                            // Adjust the left value
                            int adjustment = roundedTrueCrop.left -
                                    Math.max(0, roundedTrueCrop.right - roundedTrueCrop.width());
                            roundedTrueCrop.left -= adjustment;
                            roundedTrueCrop.right -= adjustment;
                        }
                        if (roundedTrueCrop.height() > fullSize.getHeight()) {
                            // Adjust the height
                            roundedTrueCrop.bottom = roundedTrueCrop.top + fullSize.getHeight();
                        }
                        if (roundedTrueCrop.bottom > fullSize.getHeight()) {
                            // Adjust the top value
                            int adjustment = roundedTrueCrop.top -
                                    Math.max(0, roundedTrueCrop.bottom - roundedTrueCrop.height());
                            roundedTrueCrop.top -= adjustment;
                            roundedTrueCrop.bottom -= adjustment;
                        }*/
                        /**@}**/

                        /**M: added to resolve the issue ALPS01669050.@{**/
                        if(roundedTrueCrop.left + roundedTrueCrop.width() > fullSize.getWidth()) {
                            roundedTrueCrop.right -= roundedTrueCrop.left + roundedTrueCrop.width() - fullSize.getWidth();
                        }
                        if(roundedTrueCrop.top + roundedTrueCrop.height() > fullSize.getHeight()) {
                            roundedTrueCrop.bottom -= roundedTrueCrop.top + roundedTrueCrop.height() - fullSize.getHeight();
                        }
                        /**@}**/

                        crop = Bitmap.createBitmap(fullSize, roundedTrueCrop.left,
                                roundedTrueCrop.top, roundedTrueCrop.width(),
                                roundedTrueCrop.height());
                    }
                }

                if (crop == null) {
                    Log.w(LOGTAG, "cannot decode file: " + mInUri.toString());
                    failure = true;
                    return false;
                }
                if (mOutWidth > 0 && mOutHeight > 0 || mRotation > 0) {
                    float[] dimsAfter = new float[] { crop.getWidth(), crop.getHeight() };
                    rotateMatrix.mapPoints(dimsAfter);
                    dimsAfter[0] = Math.abs(dimsAfter[0]);
                    dimsAfter[1] = Math.abs(dimsAfter[1]);

                    if (!(mOutWidth > 0 && mOutHeight > 0)) {
                        mOutWidth = Math.round(dimsAfter[0]);
                        mOutHeight = Math.round(dimsAfter[1]);
                    }

                    RectF cropRect = new RectF(0, 0, dimsAfter[0], dimsAfter[1]);
                    RectF returnRect = new RectF(0, 0, mOutWidth, mOutHeight);

                    Matrix m = new Matrix();
                    if (mRotation == 0) {
                        m.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);
                    } else {
                        Matrix m1 = new Matrix();
                        m1.setTranslate(-crop.getWidth() / 2f, -crop.getHeight() / 2f);
                        Matrix m2 = new Matrix();
                        m2.setRotate(mRotation);
                        Matrix m3 = new Matrix();
                        m3.setTranslate(dimsAfter[0] / 2f, dimsAfter[1] / 2f);
                        Matrix m4 = new Matrix();
                        m4.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);

                        Matrix c1 = new Matrix();
                        c1.setConcat(m2, m1);
                        Matrix c2 = new Matrix();
                        c2.setConcat(m4, m3);
                        m.setConcat(c2, c1);
                    }

                    try {
                        Bitmap tmp = Bitmap.createBitmap((int) returnRect.width(),
                                (int) returnRect.height(), Bitmap.Config.ARGB_8888);
                        if (tmp != null) {
                            Canvas c = new Canvas(tmp);
                            Paint p = new Paint();
                            p.setFilterBitmap(true);
                            c.drawBitmap(crop, m, p);
                            crop = tmp;
                        }
                    } catch (OutOfMemoryError e) {
                        Log.w(LOGTAG, "Can't create large bitmap, width = " + returnRect.width()
                                + ", height = " + returnRect.height());
                        failure = true;
                        return false;
                    }
                }

                if (mSaveCroppedBitmap) {
                    mCroppedBitmap = crop;
                }

                // Get output compression format
                CompressFormat cf =
                        convertExtensionToCompressFormat(getFileExtension(mOutputFormat));

                // Compress to byte array
                ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
                if (crop.compress(cf, DEFAULT_COMPRESS_QUALITY, tmpOut)) {
                    // If we need to set to the wallpaper, set it
                    if (mSetWallpaper && wallpaperManager != null) {
                        try {
                            byte[] outByteArray = tmpOut.toByteArray();
                            wallpaperManager.setStream(new ByteArrayInputStream(outByteArray));
                            if (mOnBitmapCroppedHandler != null) {
                                mOnBitmapCroppedHandler.onBitmapCropped(outByteArray);
                            }
                        } catch (IOException e) {
                            Log.w(LOGTAG, "cannot write stream to wallpaper", e);
                            failure = true;
                        }
                    }
                } else {
                    Log.w(LOGTAG, "cannot compress bitmap");
                    failure = true;
                }
            }
            return !failure; // True if any of the operations failed
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return cropBitmap();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (mOnEndRunnable != null) {
                mOnEndRunnable.run();
            }
			Log.w(LOGTAG, "onPostExecute:result:" + result);
			if (!result) {
				Toast.makeText(mContext, mContext.getString(R.string.wallpaper_load_fail),
					Toast.LENGTH_LONG).show();
			}

			
        }

        //M. ALPS01885181, sync with gallery, check the image size.
        protected boolean isOutOfSpecLimit() {
            InputStream inputStream = regenerateInputStream();

            if (inputStream == null) {
               return true;
            }

            // get file size
            int fileSize;
            try {
                fileSize = inputStream.available();
            } catch (IOException e) {
                Utils.closeSilently(inputStream);
                return true;
            }

            // get MimeType & width & height
            BitmapFactory.Options option = new BitmapFactory.Options();
            option.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, option);
            String mimeType = option.outMimeType;
            long imageWidth = (long) option.outWidth;
            long imageHeight = (long) option.outHeight;
            long framePixelSize = (long) option.outWidth * (long) option.outHeight;

            if (option.outWidth == -1 || option.outHeight == -1) {
                BitmapRegionDecoder decoder = null;
                try {
                    decoder = BitmapRegionDecoder.newInstance(inputStream, false);
                } catch (IOException e) {
                    Log.w(LOGTAG, "BitmapRegionDecoder failed", e);
                    return true;
                }

                if (decoder != null) {
                    imageWidth = (long) decoder.getWidth();
                    imageHeight = (long) decoder.getHeight();
                    framePixelSize = imageWidth * imageHeight;
                }
            }


            boolean isLcaDevice;
            if (mContext != null) {
                isLcaDevice = ((ActivityManager) mContext
                .getSystemService("activity")).isLowRamDevice();
            } else {
                isLcaDevice = false;
            }
            Log.w(LOGTAG, "isOutOfSpecLimit:isLcaDevice:" + isLcaDevice
                + ", mContext:" + mContext);

            Utils.closeSilently(inputStream);
            if (mimeType != null) {
                if (mimeType.equals(MIME_GIF)) {
                    int maxGifFileSize = isLcaDevice ?
                        MAX_GIF_FILE_SIZE_LCA : MAX_GIF_FILE_SIZE_NONE_LCA;
                    if (fileSize > maxGifFileSize || framePixelSize > MAX_GIF_FRAME_PIXEL_SIZE) {
                        return true;
                    }
                } else if (mimeType.equals(MIME_BMP)) {
                    int maxBmpFileSize = isLcaDevice ?
                        MAX_BMP_FILE_SIZE_LCA : MAX_BMP_FILE_SIZE_NONE_LCA;
                    if (fileSize > maxBmpFileSize) {
                        return true;
                    }
                }
            }
            return false;
        }
        ///M.
    }

    protected void updateWallpaperDimensions(int width, int height) {
        String spKey = getSharedPreferencesKey();
        SharedPreferences sp = getSharedPreferences(spKey, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor editor = sp.edit();
        if (width != 0 && height != 0) {
            editor.putInt(WALLPAPER_WIDTH_KEY, width);
            editor.putInt(WALLPAPER_HEIGHT_KEY, height);
        } else {
            editor.remove(WALLPAPER_WIDTH_KEY);
            editor.remove(WALLPAPER_HEIGHT_KEY);
        }
        editor.commit();

        suggestWallpaperDimension(getResources(),
                sp, getWindowManager(), WallpaperManager.getInstance(this), true);
    }

    static public void suggestWallpaperDimension(Resources res,
            final SharedPreferences sharedPrefs,
            WindowManager windowManager,
            final WallpaperManager wallpaperManager, boolean fallBackToDefaults) {
        final Point defaultWallpaperSize = getDefaultWallpaperSize(res, windowManager);
        // If we have saved a wallpaper width/height, use that instead

        int savedWidth = sharedPrefs.getInt(WALLPAPER_WIDTH_KEY, -1);
        int savedHeight = sharedPrefs.getInt(WALLPAPER_HEIGHT_KEY, -1);
        Log.i(LOGTAG, "getDefaultWallpaperSize, savedWidth: " + savedWidth +", savedHeight: " + savedHeight 
			+ ", wallpaperManager.getDesiredMinimumWidth(): " + wallpaperManager.getDesiredMinimumWidth()
			+", wallpaperManager.getDesiredMinimumHeight(): " + wallpaperManager.getDesiredMinimumHeight());
        if (savedWidth == -1 || savedHeight == -1) {
            if (!fallBackToDefaults) {
                return;
            } else {
                savedWidth = defaultWallpaperSize.x;
                savedHeight = defaultWallpaperSize.y;
            }
        }

        if (savedWidth != wallpaperManager.getDesiredMinimumWidth() ||
                savedHeight != wallpaperManager.getDesiredMinimumHeight()) {
			Log.i(LOGTAG, "setWallpaperDimension: savedWidth = " + savedWidth 
				    + ", savedHeight = " + savedHeight);
            wallpaperManager.suggestDesiredDimensions(savedWidth, savedHeight);
        }
    }

    protected static RectF getMaxCropRect(
            int inWidth, int inHeight, int outWidth, int outHeight, boolean leftAligned) {
        RectF cropRect = new RectF();
        // Get a crop rect that will fit this
        if (inWidth / (float) inHeight > outWidth / (float) outHeight) {
             cropRect.top = 0;
             cropRect.bottom = inHeight;
             cropRect.left = (inWidth - (outWidth / (float) outHeight) * inHeight) / 2;
             cropRect.right = inWidth - cropRect.left;
             if (leftAligned) {
                 cropRect.right -= cropRect.left;
                 cropRect.left = 0;
             }
        } else {
            cropRect.left = 0;
            cropRect.right = inWidth;
            cropRect.top = (inHeight - (outHeight / (float) outWidth) * inWidth) / 2;
            cropRect.bottom = inHeight - cropRect.top;
        }
        return cropRect;
    }

    protected static CompressFormat convertExtensionToCompressFormat(String extension) {
        return extension.equals("png") ? CompressFormat.PNG : CompressFormat.JPEG;
    }

    protected static String getFileExtension(String requestFormat) {
        String outputFormat = (requestFormat == null)
                ? "jpg"
                : requestFormat;
        outputFormat = outputFormat.toLowerCase();
        return (outputFormat.equals("png") || outputFormat.equals("gif"))
                ? "png" // We don't support gif compression.
                : "jpg";
    }


    ///M. ALPS2008466, MOVE getDefaultThumbnailSize(), createThumbnail() to WallpaperCropperActivity.
    public static Point getDefaultThumbnailSize(Resources res) {
        return new Point(res.getDimensionPixelSize(R.dimen.wallpaperThumbnailWidth),
                res.getDimensionPixelSize(R.dimen.wallpaperThumbnailHeight));

    }

    public static Bitmap createThumbnail(Point size, Context context, Uri uri, byte[] imageBytes,
            Resources res, int resId, int rotation, boolean leftAligned) {
        int width = size.x;
        int height = size.y;

        BitmapCropTask cropTask;
        if (uri != null) {
            cropTask = new BitmapCropTask(
                    context, uri, null, rotation, width, height, false, true, null);
        } else if (imageBytes != null) {
            cropTask = new BitmapCropTask(
                    imageBytes, null, rotation, width, height, false, true, null);
        }  else {
            cropTask = new BitmapCropTask(
                    context, res, resId, null, rotation, width, height, false, true, null);
        }
        Point bounds = cropTask.getImageBounds();
        if (bounds == null || bounds.x == 0 || bounds.y == 0) {
            return null;
        }

        Matrix rotateMatrix = new Matrix();
        rotateMatrix.setRotate(rotation);
        float[] rotatedBounds = new float[] { bounds.x, bounds.y };
        rotateMatrix.mapPoints(rotatedBounds);
        rotatedBounds[0] = Math.abs(rotatedBounds[0]);
        rotatedBounds[1] = Math.abs(rotatedBounds[1]);

        RectF cropRect = WallpaperCropActivity.getMaxCropRect(
                (int) rotatedBounds[0], (int) rotatedBounds[1], width, height, leftAligned);
        cropTask.setCropBounds(cropRect);

        if (cropTask.cropBitmap()) {
            return cropTask.getCroppedBitmap();
        } else {
            return null;
        }
    }
    ///M.
}
