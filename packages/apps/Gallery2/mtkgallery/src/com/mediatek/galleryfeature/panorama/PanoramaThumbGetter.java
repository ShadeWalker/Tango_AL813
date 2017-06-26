package com.mediatek.galleryfeature.panorama;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.gl.BackgroundRenderer;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MRawTexture;
import com.mediatek.galleryframework.gl.BackgroundRenderer.BackgroundGLTask;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.util.BitmapUtils;

class PanoramaThumbGetter {
    private static final String TAG = "MtkGallery2/PanoramaThumbGetter";
    private static String BACK_GROUND_COLOR = "#1A1A1A";
    private MediaData mData;
    private ThumbType mThumbType;
    private Context mContext; // used to decode uri image

    private int mTargetWidth;
    private int mTargetHeight;
    private int mFrameCount;
    private float mFrameDegreeGap;
    private float mFrameSkip;

    private MRawTexture mTexture;
    private PanoramaDrawer mDrawer;
    private PanoramaConfig mConfig;
    private BackgroundRenderer mRenderer;

    private boolean mInitFail = false;

    public PanoramaThumbGetter(MediaData data, ThumbType thumbType,
            int targetWidth, Context context) {
        mData = data;
        mThumbType = thumbType;
        mContext = context;
        mTargetWidth = targetWidth;
        mTargetHeight = (int) ((float) PanoramaHelper.getMiddleThumbHeight()
                * (float) mTargetWidth / (float) PanoramaHelper.getMiddleThumbWidth());
        // prepare bitmap
        Bitmap bitmap = PanoramaHelper.decodePanoramaBitmap(mData,
                PanoramaHelper.getDecodeTargetSize(mThumbType), context);
        if (bitmap == null) {
            MtkLog.i(TAG, "<new> decode bitmap fail");
            mInitFail = true;
            return;
        }
        bitmap = BitmapUtils.rotateBitmap(bitmap, mData.orientation, true);
        bitmap = PanoramaHelper.resizeBitmapToProperRatio(bitmap, true);
        // prepare config
        if (thumbType == ThumbType.MIDDLE) {
            mConfig = new PanoramaConfig(bitmap.getWidth(), bitmap.getHeight(), mTargetWidth,
                    mTargetHeight, 1.0f);
        } else {
            mConfig = new PanoramaConfig(bitmap.getWidth(), bitmap.getHeight(), mTargetWidth,
                    mTargetHeight, PanoramaHelper.getAntialiasScale(thumbType));
        }
        // prepare render context
        mDrawer = new PanoramaDrawer(bitmap, mConfig);
        mTexture = new MRawTexture(mConfig.mCanvasWidth, mConfig.mCanvasHeight, false);
        mRenderer = BackgroundRenderer.getInstance();
        mFrameDegreeGap = mConfig.mFrameDegreeGap;
        mFrameSkip = 1;
        mFrameCount = mConfig.mFrameTotalCount;
    }

    public void setFrameCount(int count) {
        if (mFrameCount > count) {
            mFrameCount = count;
            mFrameSkip = (float) mFrameCount / (float) count;
        }
    }

    public int getThumbnailWidth() {
        return mTargetWidth;
    }

    public int getThumbnailHeight() {
        return mTargetHeight;
    }

    public void recycle() {
        if (mTexture != null) {
            mTexture.recycle();
            mTexture = null;
        }
        if (mDrawer != null) {
            mDrawer.freeResources();
        }
    }

    public Bitmap getThumbnail(int index) {
        if (mInitFail)
            return null;
        assert (index >= 0 && index < mFrameCount);
        PanoramaFrameTask task = new PanoramaFrameTask(index);
        mRenderer.addGLTask(task);
        MtkLog.i(TAG, "<getThumbnail> add BackgroundGLTask, task = " + task);
        mRenderer.requestRender();
        synchronized (task) {
            while (!task.isDone()) {
                try {
                    task.wait(200);
                } catch (InterruptedException e) {
                    MtkLog.i(TAG, "<getThumbnail> InterruptedException: "
                            + e.getMessage());
                }
            }
        }
        Bitmap bitmap = task.get();
        if (bitmap == null) {
            MtkLog.i(TAG, "<getThumbnail> task.get() == null, return");
            return null;
        }
        if (mThumbType == ThumbType.MICRO) {
            Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap
                    .getWidth(), bitmap.getConfig());
            Canvas canvas = new Canvas(newBitmap);
            canvas.drawBitmap(bitmap, 0, (bitmap.getWidth() - bitmap
                    .getHeight()) / 2, null);
            if (mData.orientation != 0) {
                newBitmap = BitmapUtils.rotateBitmap(newBitmap,
                        -mData.orientation, true);
            }
            newBitmap = BitmapUtils.replaceBackgroundColor(newBitmap, Color
                    .parseColor(BACK_GROUND_COLOR), true);
            return newBitmap;
        } else if (mThumbType == ThumbType.FANCY) {
            if (mData.orientation != 0) {
                bitmap = BitmapUtils.rotateBitmap(bitmap, -mData.orientation,
                        true);
            }
            bitmap = BitmapUtils.replaceBackgroundColor(bitmap, Color
                    .parseColor(BACK_GROUND_COLOR), true);
            return bitmap;
        }
        return bitmap;
    }

    class PanoramaFrameTask implements BackgroundGLTask {
        private Bitmap mFrame;
        private boolean mDone = false;
        private int mIndex;

        public PanoramaFrameTask(int index) {
            mIndex = index;
        }

        @Override
        public boolean run(MGLCanvas canvas) {
            MtkLog.i(TAG, "<PanoramaFrameTask.run> begin to run, task = "
                    + this);
            mFrame = mDrawer.drawOnBitmap(canvas, mTexture, mIndex
                    * mFrameDegreeGap);
            mDone = true;
            MtkLog.i(TAG, "<PanoramaFrameTask.run> end run, task = " + this
                    + ", mFrame = " + mFrame);
            synchronized (PanoramaFrameTask.this) {
                PanoramaFrameTask.this.notifyAll();
            }
            return false;
        }

        public Bitmap get() {
            return mFrame;
        }

        public boolean isDone() {
            return mDone;
        }
    }
}