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

package com.mediatek.galleryfeature.refocus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.ref.WeakReference;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.app.ActionBar;
import android.app.Activity;
import android.app.DialogFragment;
import android.net.Uri;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.ShareActionProvider;
import android.widget.ActivityChooserModel.OnChooseActivityListener;
import android.widget.ActivityChooserModel;
import android.widget.ActivityChooserView;
import android.widget.Toast;
import android.util.DisplayMetrics;

import com.android.gallery3d.R;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.util.GalleryUtils;

import com.mediatek.galleryframework.util.ProgressFragment;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.util.Utils;
import com.mediatek.galleryfeature.refocus.ReFocusView.RefocusListener;
import com.mediatek.galleryfeature.refocus.RefocusHelper;
import com.mediatek.galleryfeature.hotknot.HotKnot;

import com.mediatek.common.MPlugin;
import com.mediatek.gallery3d.ext.IAppGuideExt.OnGuideFinishListener;
import com.mediatek.gallery3d.ext.IAppGuideExt;
import com.mediatek.gallery3d.ext.AppGuideExt;

public class RefocusActivity extends Activity implements
        OnChooseActivityListener, RefocusListener,
        SeekBar.OnSeekBarChangeListener {
    private static final String TAG = "Gallery2/Refocus/RefocusActivity";

    private static final int SEEKBAR_PROCESS_INIT = 60;
    public static final String REFOCUS_ACTION = "com.android.gallery3d.action.REFOCUS";
    public static final String REFOCUS_IMAGE_WIDTH = "image-width";
    public static final String REFOCUS_IMAGE_HEIGHT = "image-height";
    public static final String REFOCUS_IMAGE_ORIENTATION = "image-orientation";
    public static final String REFOCUS_IMAGE_NAME = "image-name";
    public static final String MIME_TYPE = "image/*";
    public static final String REFOCUS_MIME_TYPE = "mimeType";

    private float mImageWidth;
    private float mImageHeight;
    private int mImageOrientation;
    private String mImageName;

    private static final int MSG_INIT_FINISH = 1;
    private static final int MSG_GENERATE_IMAGE = 2;
    private static final int MSG_GENERATE_DONE = 3;
    private static final int MSG_REFOCUS_ERROR = 4;
    
    private Bitmap mOriginalBitmap = null;
    private Bitmap mRefocusBitmap = null;
    private RectF mOriginalBounds = null;
    private Uri mSourceUri = null;
    private ReFocusView mRefocusView = null;
    private RefocusImage mRefocusImage = null;
    private View mSaveButton = null;
    private Handler mHandler;
    private LoadBitmapTask mLoadBitmapTask = null;
    private ShareActionProvider mShareActionProvider;
    private GeneRefocusImageTask mGeneRefocusImageTask;
    private SeekBar mRefocusSeekBar;
    private HotKnot mHotKnot;

    private String mFilePath = null;
    private long mTouchstartTime;
    private boolean mIsSetDepthOnly = false;
    private boolean mIsSharingImage = false;
    private boolean mIsSetPictureAs = false;
    private boolean mIsShareHotKnot = false;
    private boolean mIsCancelThread = false;

    private WeakReference<DialogFragment> mSavingProgressDialog;
    private WeakReference<DialogFragment> mLoadingProgressDialog;
    private File mSharedOutputFile = null;

    private int mShowImageTotalDurationTime = 1500;//default : 600.0f;
    private int mShowImageFirstDurationTime = 500;//default : 300.0f;

    private int mDepth = 24;
    private int[] mTouchBitmapCoord = new int[2];
    private Intent mIntent =  null;
    private Uri mInsertUri = null;

    private Intent mShareIntent;
    private MenuItem mShareMenuItem;
    private ActivityChooserModel mDataModel;

    @Override
    public void onCreate(Bundle bundle) {
        MtkLog.i(TAG, "<onCreate> begin");
        super.onCreate(bundle);
        mIntent = getIntent();
        setResult(RESULT_CANCELED, new Intent());
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.m_refocus_activity);
        mRefocusImage = new RefocusImage(this);
        mRefocusView = (ReFocusView) findViewById(R.id.refocus_view);
        mRefocusView.setRefocusListener(this);
        initRefocusSeekBar();
        toggleStatusBarByOrientation();
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.m_refocus_actionbar);
            mSaveButton = actionBar.getCustomView();
            mSaveButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    saveRefocusBitmap();
                }
            });
        }
        mRefocusView.setZOrderOnTop(false);
        mHotKnot = new HotKnot(this);
        if (null == mIntent.getData()) {
            MtkLog.i(TAG, "<onCreate> mSourceUri == null,so finish!!!");
            finish();
            return;
        }
        mSourceUri = mIntent.getData();
        MtkLog.i(TAG, "<onCreate> mSource = " + mSourceUri);
        startLoadBitmap(mSourceUri);
        mImageWidth = mIntent.getExtras().getInt(REFOCUS_IMAGE_WIDTH);
        mImageHeight = mIntent.getExtras().getInt(REFOCUS_IMAGE_HEIGHT);
        mImageName = mIntent.getExtras().getString(REFOCUS_IMAGE_NAME);
        mImageOrientation = mIntent.getExtras().getInt(
                REFOCUS_IMAGE_ORIENTATION);

        mHandler = new Handler() {
            @SuppressWarnings("unchecked")
            public void handleMessage(Message message) {
                switch (message.what) {
                case MSG_INIT_FINISH:
                    hideLodingProgress();
                    setSaveState(true);
                    return;
                case MSG_GENERATE_IMAGE:
                    if (mGeneRefocusImageTask != null) {
                        mGeneRefocusImageTask.notifyDirty();
                    }
                    return;
                case MSG_GENERATE_DONE:
                    setSaveState(true);
                    return;
                case MSG_REFOCUS_ERROR: 
                    errorHandleWhenRefocus();
                    return;
                default:
                    throw new AssertionError();
                }
            }
        };
        // For debug
        try {
            File file = new File(Environment.getExternalStorageDirectory(),
                    "REFOCUSANIMATION");
            boolean animationSetting = file.exists();
            if (animationSetting) {
                FileReader fileReader = new FileReader(file);
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                String animtaionTime = bufferedReader.readLine();
                MtkLog.i(TAG, "<onCreate> animtaionTime " + animtaionTime);
                mShowImageTotalDurationTime = Integer.valueOf(animtaionTime);
            }
        } catch (Exception e) {
            MtkLog.d(TAG, "<onCreate> debug refocus animation exception");
            e.printStackTrace();
        }
        MtkLog.i(TAG, "<onCreate> end");
    }

    // Shows status bar in portrait view, hide in landscape view
    private void toggleStatusBarByOrientation() {
        Window win = getWindow();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    /// APP guide {@
    private static IAppGuideExt sAppGuideExt;

    public static void showAppGuide(Activity activity, String type,
            OnGuideFinishListener onFinishListener) {
        if (sAppGuideExt == null) {
            sAppGuideExt = (IAppGuideExt) MPlugin.createInstance(
                    IAppGuideExt.class.getName(), activity);
            if (sAppGuideExt == null) {
                sAppGuideExt = new AppGuideExt();
            }
        }
        if (sAppGuideExt != null) {
            sAppGuideExt.showGalleryGuide(activity, type, onFinishListener);
        }
    }

    private IAppGuideExt.OnGuideFinishListener onFinishListener = new IAppGuideExt.OnGuideFinishListener() {
        public void onGuideFinish() {
            MtkLog.i(TAG, "Refocus Image Guide show finish!");
        }
    };
    /// }@

    public void initRefocusSeekBar() {
        MtkLog.i(TAG, "<initRefocusSeekBar>");
        mRefocusSeekBar = (SeekBar)this.findViewById(R.id.refocusSeekBar);
        mRefocusSeekBar.setVisibility(View.VISIBLE);
        mRefocusSeekBar.setProgress(SEEKBAR_PROCESS_INIT);
        mRefocusSeekBar.setOnSeekBarChangeListener(this);
    }

    //set the depth info depend on the progress of seek bar.
    @Override
    public void onProgressChanged(SeekBar mRefocusSeekBar, int progress, boolean fromuser) {
        MtkLog.i(TAG,"<onProgressChanged>");
    }

    @Override
    public void onStartTrackingTouch(SeekBar mRefocusSeekBar) {
        MtkLog.i(TAG,"<onStartTrackingTouch>");
    }

    @Override
    public void onStopTrackingTouch(SeekBar mRefocusSeekBar) {
        MtkLog.i(TAG,"<onStartTrackingTouch>");
        mIsSetDepthOnly = true;
        mIsCancelThread = false;
        mDepth = (mRefocusSeekBar.getProgress()/15) * 3;
        if (mDepth == 0) {
            mDepth = 1;
        }
        MtkLog.i(TAG, "<onStartTrackingTouch> Seekbar reset mDepth = "
                + mDepth);
        Message msg = Message.obtain(mHandler, MSG_GENERATE_IMAGE);
        mHandler.sendMessage(msg);
        return;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.m_refocus_activity_menu, menu);
        mHotKnot.updateMenu(menu, R.id.menu_refocus_share, R.id.action_hotknot,
                true);
        mShareMenuItem = menu.findItem(R.id.menu_refocus_share);
        initShareActionProvider();
        return true;
    }

    @Override
    public boolean onChooseActivity(ActivityChooserModel host, Intent intent) {
        MtkLog.i(TAG, "<onChooseActivity> enter, intent " + intent);
        mShareIntent = intent;
        mIsSharingImage = true;
        showSavingProgress(mImageName);
        startSaveBitmap(mSourceUri);
        /* return true: send shareIntent by RefocusActivity
           return false: sent shareIntent by ShareActionProvider
        */
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_setas:
                mIsSetPictureAs = true;
                showSavingProgress(mImageName);
                startSaveBitmap(mSourceUri);
                return true;
            case R.id.action_hotknot:
                mIsShareHotKnot = true;
                showSavingProgress(mImageName);
                startSaveBitmap(mSourceUri);
                return true;
        }
        return false;
    }

    private void setSaveState(boolean enable) {
        if (mSaveButton != null) {
            mSaveButton.setEnabled(enable);
        }
    }

    @Override
    protected void onDestroy() {
        MtkLog.i(TAG, "<onDestroy>");
        if (mLoadBitmapTask != null) {
            mLoadBitmapTask.cancel(false);
            mLoadBitmapTask = null;
        }
        if(mRefocusImage != null) {
            mRefocusImage.refocusRelease();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private int getScreenPixel() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics reMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(reMetrics);
        return Math.max(reMetrics.widthPixels, reMetrics.heightPixels);
    }

    private void startLoadBitmap(Uri uri) {
        if (uri != null) {
            setSaveState(false);
            showLoadingProgress();
            mLoadBitmapTask = new LoadBitmapTask();
            mLoadBitmapTask.execute(uri);
        } else {
            showImageLoadFailToast();
            finish();
        }
    }

    private void showImageLoadFailToast() {
        CharSequence text = getString(R.string.cannot_load_image);
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    private class LoadBitmapTask extends AsyncTask<Uri, Void, Bitmap> {
        Context mContext;

        public LoadBitmapTask() {
            mContext = getApplicationContext();
        }

        @Override
        protected Bitmap doInBackground(Uri... params) {
            long beginTime = System.currentTimeMillis();
            Uri sourceUri = params[0];
            long decodeTimestart = System.currentTimeMillis();
            Bitmap bitmap = RefocusHelper.decodeBitmap(sourceUri, mContext);
            long decodeTime = System.currentTimeMillis() - decodeTimestart;
            MtkLog.i(TAG, "decode time = " + decodeTime);
            long spendTime = System.currentTimeMillis() - beginTime;
            MtkLog.i(TAG, "doInbackground time = " + spendTime);
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            long beginTime = System.currentTimeMillis();
            mOriginalBitmap = result;
            if (mOriginalBitmap != null && mImageWidth != 0
                    && mImageHeight != 0) {
                long beginTime1 = System.currentTimeMillis();

                mRefocusView.setImageActor(mOriginalBitmap, mOriginalBitmap
                        .getWidth(), mOriginalBitmap.getHeight());
                Bitmap bmp = mOriginalBitmap.copy(mOriginalBitmap.getConfig(),
                        false);
                mRefocusView.setImageActorNew(bmp);
                long needtime = System.currentTimeMillis() - beginTime1;
                MtkLog.i(TAG, "setImageActor time = " + needtime);
                long beginTime2 = System.currentTimeMillis();
                needtime = System.currentTimeMillis() - beginTime2;
                MtkLog.i(TAG, "setTransitionTime time = " + needtime);
                long spendTime = System.currentTimeMillis() - beginTime1;
                MtkLog.i(TAG, "onPostExecute time = " + spendTime);
                initRefocusImages();
            } else {
                MtkLog.w(TAG, "could not load image for Refocus!!");
                mOriginalBitmap.recycle();
                mOriginalBitmap = null;
                showImageLoadFailToast();
                setResult(RESULT_CANCELED, new Intent());
                finish();
            }
        }
    }

    protected void saveRefocusBitmap() {
        setSaveState(false);
        File saveDir = RefocusHelper.getFinalSaveDirectory(this, mSourceUri);
        int bucketId = GalleryUtils.getBucketId(saveDir.getPath());
        String albumName = LocalAlbum.getLocalizedName(getResources(),
                bucketId, null);
        showSavingProgress(albumName);
        startSaveBitmap(mSourceUri);
    }

    private void startSaveBitmap(Uri sourceUri) {
        SaveBitmapTask saveTask = new SaveBitmapTask(sourceUri);
        saveTask.execute();
    }

    private class SaveBitmapTask extends AsyncTask<Bitmap, Void, Boolean> {
        Uri mSourceUri;

        public SaveBitmapTask(Uri sourceUri) {
            mSourceUri = sourceUri;
        }

        @Override
        protected Boolean doInBackground(Bitmap... params) {
            if(mSourceUri == null) {
                return false;
            }
            MtkLog.d(TAG, "<SaveBitmapTask> start");
            mInsertUri = mRefocusImage.saveRefocusImage(mSourceUri);
            MtkLog.d(TAG, "<SaveBitmapTask> end");
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            finishSaveBitmap(mInsertUri);
        }
    }

    private void finishSaveBitmap(Uri destUri) {
        MtkLog.i(TAG, "<finishSaveBitmap> destUri:" + destUri);
        if (destUri == null) {
            showSaveFailToast();
            MtkLog.i(TAG, "<finishSaveBitmap> saving fail");
            return;
        }
        MtkLog.i(TAG, "<finishSaveBitmap> saving finish");
        if (mIsSharingImage) {
            MtkLog.i(TAG, "<finishSaveBitmap> normal share");
            mShareIntent.removeExtra(Intent.EXTRA_STREAM);
            mShareIntent.putExtra(Intent.EXTRA_STREAM, destUri);
            startActivity(mShareIntent);
            MtkLog.i(TAG, "<finishSaveBitmap> start share intent done");
        } else if (mIsSetPictureAs) {
            MtkLog.i(TAG, "<finishSaveBitmap> set picture as");
            Intent intent = new Intent(Intent.ACTION_ATTACH_DATA)
                    .setDataAndType(destUri, MIME_TYPE).addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(REFOCUS_MIME_TYPE, intent.getType());
            startActivity(Intent.createChooser(intent,
                    getString(R.string.set_as)));
        } else if (mIsShareHotKnot) {
            MtkLog.i(TAG, "<finishSaveBitmap> share via hotnot");
            Uri contentUri = destUri;
            mHotKnot.sendUri(contentUri, MIME_TYPE);
        }
        setResult(RESULT_OK, new Intent().setData(destUri));
        MtkLog.i(TAG, "<finishSaveBitmap> set result and finish activity");
        finish();
    }

    private void initRefocusImages() {
        mFilePath = RefocusHelper.getRealFilePathFromURI(
                getApplicationContext(), mSourceUri);
        long RefocusImageInitTimestart = System.currentTimeMillis();
        boolean initResult = mRefocusImage.initRefocusImage(mFilePath, (int) mImageWidth,
                (int) mImageHeight);
        if (!initResult) {
            MtkLog.i(TAG, "<initRefocusImages> error, abort init");
            mHandler.sendEmptyMessage(MSG_REFOCUS_ERROR);
            return;
        }
        long RefocusImageInitSpentTime = System.currentTimeMillis()
                - RefocusImageInitTimestart;
        MtkLog.i(TAG, "performance RefocusImageInitSpent time = "
                + RefocusImageInitSpentTime);
        int dbg = SystemProperties.getInt("debug.gallery.enable", 0);
        MtkLog.i(TAG, "debug.gallery.enable = " + dbg);
        if (dbg == 1) {
            int depBufWidth = mRefocusImage.getDepBufWidth();
            int depBufHeight = mRefocusImage.getDepBufHeight();
            MtkLog.i(TAG, "depBufWidth = " + depBufWidth + ", depBufHeight = "
                    + depBufHeight);
            mRefocusView.setDepthActor(mRefocusImage.getDepthBuffer(), 0, 1,
                    depBufWidth, depBufHeight);
        }
        mHandler.sendEmptyMessage(MSG_INIT_FINISH);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        MtkLog.i(TAG, "<onBackPressed>");
        if (mGeneRefocusImageTask != null) {
            mGeneRefocusImageTask.terminate();
            mGeneRefocusImageTask = null;
        }
    }

    @Override
    protected void onPause() {
        MtkLog.i(TAG, "<OnPause>");
        super.onPause();
        hideSavingProgress();
        if (mGeneRefocusImageTask != null) {
            mGeneRefocusImageTask.terminate();
            mGeneRefocusImageTask = null;
        }
        if (mDataModel != null) {
            mDataModel.setOnChooseActivityListener(null);
            MtkLog.i(TAG, "<OnPause> clear OnChooseActivityListener");
        }
    }

    @Override
    protected void onResume() {
        MtkLog.i(TAG, "<onResume>");
        super.onResume();
        mGeneRefocusImageTask = new GeneRefocusImageTask();
        mGeneRefocusImageTask.start();
        if (mDataModel != null) {
            mDataModel.setOnChooseActivityListener(this);
            MtkLog.i(TAG, "<onResume> setOnChooseActivityListener ");
        }
    }

    private void showSavingProgress(String albumName) {
        DialogFragment fragment;

        if (mSavingProgressDialog != null) {
            fragment = mSavingProgressDialog.get();
            if (fragment != null) {
                fragment.show(getFragmentManager(), null);
                return;
            }
        }
        String progressText;
        if (albumName == null) {
            progressText = getString(R.string.saving_image);
        } else {
            progressText = getString(R.string.m_refocus_saving_image, albumName);
        }
        final DialogFragment genProgressDialog = new ProgressFragment(
                progressText);
        genProgressDialog.setCancelable(false);
        genProgressDialog.show(getFragmentManager(), null);
        genProgressDialog.setStyle(R.style.RefocusDialog, genProgressDialog
                .getTheme());
        mSavingProgressDialog = new WeakReference<DialogFragment>(
                genProgressDialog);
    }

    private void showLoadingProgress() {
        DialogFragment fragment;
        if (mLoadingProgressDialog != null) {
            fragment = mLoadingProgressDialog.get();
            if (fragment != null) {
                fragment.show(getFragmentManager(), null);
                return;
            }
        }
        final DialogFragment genProgressDialog = new ProgressFragment(
                R.string.loading_image);
        genProgressDialog.setCancelable(false);
        genProgressDialog.show(getFragmentManager(), null);
        genProgressDialog.setStyle(R.style.RefocusDialog, genProgressDialog
                .getTheme());
        mLoadingProgressDialog = new WeakReference<DialogFragment>(
                genProgressDialog);
    }

    private void hideLodingProgress(){
        if(mLoadingProgressDialog != null) {
            DialogFragment fragment = mLoadingProgressDialog.get();
            if(fragment != null) {
                MtkLog.i(TAG, "show refocus Guide");
                showAppGuide(this, "refocus_image_guide", onFinishListener);
                fragment.dismiss();
            }
        }
    }

    private void hideSavingProgress() {
        if (mSavingProgressDialog != null) {
            DialogFragment progress = mSavingProgressDialog.get();
            if (progress != null)
                progress.dismissAllowingStateLoss();
        }
    }

    private void showSaveFailToast() {
        CharSequence text = getString(R.string.m_refocus_save_fail);
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public void setRefocusImage(float x, float y) {
        MtkLog.i(TAG, "setRefocusImage x " + x + " y " + y
                + " mShowImagekTotalDurationTime "
                + mShowImageTotalDurationTime);
        setSaveState(false);
        mRefocusView.setTransitionTime(mShowImageTotalDurationTime,
                mShowImageFirstDurationTime);
        mTouchBitmapCoord[0] = (int) (x * mImageWidth);
        mTouchBitmapCoord[1] = (int) (y * mImageHeight);
        mIsSetDepthOnly = false;
        MtkLog.i(TAG, "mTouchBitmapCoord[0] = " + mTouchBitmapCoord[0]
                + " mTouchBitmapCoord[1] = " + mTouchBitmapCoord[1]);
        mGeneRefocusImageTask.notifyDirty();
    }

    private class GeneRefocusImageTask extends Thread {
        private volatile boolean mDirty = false;
        private volatile boolean mActive = true;
        private Context mContext;

        public GeneRefocusImageTask() {
            setName("GeneRefocusImageTask");
            mContext = getApplicationContext();
        }

        @Override
        public void run() {
            while (mActive) {
                synchronized (this) {
                    if (!mDirty && mActive) {
                        MtkLog.i(TAG, "GeneRefocusImageTask wait");
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                }
                mDirty = false;
                MtkLog.i(TAG, "GeneRefocusImageTask  start");
                if (mIsCancelThread) {
                    MtkLog.i(TAG, "cancel generate task.");
                    continue;
                } else {
                    mRefocusImage.generateRefocusImage(mTouchBitmapCoord[0], mTouchBitmapCoord[1], mDepth);
                    MtkLog.i(TAG, "GeneRefocusImageTask  mDepth = " + mDepth);
                    Bitmap newBitmap = mRefocusImage.getBitmap();
                   //MtkUtils.dumpBitmap(newBitmap, "Generate_RefocusImage");
                    if (mIsSetDepthOnly) {
                        mRefocusView.setImageActor(newBitmap, -1, -1);
                        mIsSetDepthOnly = false;
                    } else {
                        mRefocusView.setImageActorNew(newBitmap);
                    }
                }
                Message msg = Message.obtain(mHandler, MSG_GENERATE_DONE);
                mHandler.sendMessage(msg);
            }
        }
        
        public synchronized void notifyDirty() {
            MtkLog.i(TAG, "GeneRefocusImageTask notifyDirty");
            mDirty = true;
            notifyAll();
        }

        public synchronized void terminate() {
            mActive = false;
            notifyAll();
        }
    }

    private void initShareActionProvider() {
        MtkLog.i(TAG, "<initShareActionProvider> begin");
        mShareActionProvider = (ShareActionProvider) mShareMenuItem
                .getActionProvider();
        mDataModel = ActivityChooserModel.get(this,
                ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);
        Intent tempIntent = new Intent(Intent.ACTION_SEND);
        tempIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        tempIntent.setType(GalleryUtils.MIME_TYPE_IMAGE);
        tempIntent.putExtra(Intent.EXTRA_STREAM, mSourceUri);
        if (mShareActionProvider != null) {
            MtkLog.i(TAG, "<initShareActionProvider> setShareIntent");
            mShareActionProvider.setShareIntent(tempIntent);
        }
        if (mDataModel != null) {
            mDataModel.setOnChooseActivityListener(this);
            MtkLog.i(TAG,
                    "<initShareActionProvider> setOnChooseActivityListener ");
        }
        MtkLog.i(TAG, "<initShareActionProvider> end");
    }

    private void errorHandleWhenRefocus() {
        Toast.makeText(this, "error occurs when refocusing, app will quit",
                Toast.LENGTH_LONG).show();
        finish();
    }
}