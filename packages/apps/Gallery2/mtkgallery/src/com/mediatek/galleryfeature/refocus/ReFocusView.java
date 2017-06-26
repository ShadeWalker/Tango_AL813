
package com.mediatek.galleryfeature.refocus;

import java.io.File;
import java.io.FileDescriptor;
import java.text.DecimalFormat;

import javax.microedition.khronos.opengles.GL10;

import android.R.dimen;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;

import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.ngin3d.Color;
import com.mediatek.ngin3d.Image;
import com.mediatek.ngin3d.Layer;
import com.mediatek.ngin3d.Point;
import com.mediatek.ngin3d.Rotation;
import com.mediatek.ngin3d.Scale;
import com.mediatek.ngin3d.Stage;
import com.mediatek.ngin3d.Text;
import com.mediatek.ngin3d.android.StageView;
import com.android.gallery3d.R;
import com.mediatek.ngin3d.Dimension;

@SuppressLint("SdCardPath")
public class ReFocusView extends StageView {
    private static final String TAG = "Gallery2/Refocus/ReFocusView";
    private static int sTotalNewCount = 0;
    private int mViewID;

    private final Context mContext;
    private int mWidth = 1;
    private int mHeight = 1;
    private float mDownX;
    private float mDownY;

    private float mLocationX = 0.5f;
    private float mLocationY = 0.5f;
    private GestureDetectorCompat mGestureDetector;

    private MyScaleGestureListener mZoomGestureListener;
    private ScaleGestureDetector mZoomGestureDetector;
    private float mScaleFit;
    private float mScaleCurrent;

    private float mXOffset;
    private float mYOffset;
    private Bitmap mBitmapNew;
    private Bitmap mBitmap;
    private Image mImageActor;
    private Image mFocusActor;

    private Image mDepthActor;
    private float mDepthLocationX = 0.1f;
    private float mDepthLocationY = 0.2f;

    private void logD(String msg) {
        Log.d(TAG, "[" + mViewID + "] " + msg);
    }

    private void logI(String msg) {
        Log.i(TAG, "[" + mViewID + "] " + msg);
    }

    private void logW(String msg) {
        Log.w(TAG, "[" + mViewID + "] " + msg);
    }

    private void logE(String msg) {
        Log.e(TAG, "[" + mViewID + "] " + msg);
    }

    @Override
    public String toString() {
        return "[" + mViewID + "] " + super.toString();
    }

    private void removeFolder(String path) {
        removeFolder(path, 0);
    }

    private void removeFolder(String path, int level) {
        File folder = new File(path);
        if (!folder.exists()) {
            logI("[" + level + "] folder doesn't exist! : " + path);
            return;
        }

        File[] files = folder.listFiles();
        for (File thefile : files) {
            logW("[" + level + "] Remove: " + thefile.getPath());
            if (thefile.isDirectory()) {
                removeFolder(thefile.getPath(), level + 1);
            } else {
                if (!thefile.delete()) {
                    logW("[" + level + "] file deletion failed! : " + thefile);
                }
            }
        }

        if (!folder.delete()) {
            logW("[" + level + "] folder deletion failed! : " + folder);
        } else {
            logI("[" + level + "] folder deletion ok : " + folder);
        }
    }
    public class MyScaleGestureListener extends GestureDetector.SimpleOnGestureListener implements OnScaleGestureListener {
        private float mScaleFactor;
        private float startScale;
        private float endScale;
        private boolean isScaling = false;

        private ReFocusView mTheView;
        private MotionEvent mEvent;

        public void setMotionEvent(ReFocusView v, MotionEvent event) {
            mEvent = event;
            mTheView = v;
        }

        public boolean isScaling() {
            return isScaling;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            endScale = detector.getScaleFactor();

            // this condition does not pass. not > or < succeeds
            if (startScale > endScale) {
                logI("onScaleEnd() Pinch Dection");
            } else if (startScale < endScale) {
                logI("onScaleEnd() Zoom Dection");
            }
            isScaling = false;
            setTouchMode(getTouchMode() & (~ReFocusView.TOUCH_MODE_TWO_FINGER_SCROLL));

            moveBackCheck();
        }

        private float mPreFocusX = 0;
        private float mPreFocusY = 0;
        private float mLocationXtmp = 0;
        private float mLocationYtmp = 0;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            startScale = mScaleCurrent;
            logW("onScaleBegin() startScale:" + startScale);
            isScaling = true;
            mPreFocusX = detector.getFocusX();
            mPreFocusY = detector.getFocusY();
            mLocationXtmp = mLocationX;
            mLocationYtmp = mLocationY;
            setTouchMode(getTouchMode() | ReFocusView.TOUCH_MODE_TWO_FINGER_SCROLL);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if ((getTouchMode() & ReFocusView.TOUCH_MODE_TWO_FINGER_SCROLL) > 0) {
                mScaleFactor = detector.getScaleFactor();
                if ((startScale * mScaleFactor > mScaleFit) && ((startScale * mScaleFactor) / mScaleFit <= 4.0f)) {
                    mTheView.setImageActorViewSizeScale(startScale * mScaleFactor);
                }

                float k = mScaleCurrent / startScale;
                final float x = mLocationXtmp * (float) mWidth - mPreFocusX;
                final float y = mLocationYtmp * (float) mHeight - mPreFocusY;

                mLocationX = ((detector.getFocusX() + (k * x)) / ((float) mWidth));
                mLocationY = ((detector.getFocusY() + (k * y)) / ((float) mHeight));

                if (mImageActor != null) {
                    mImageActor.setPosition(new Point(mLocationX, mLocationY, true));
                }
            }
            return false;
        }

        // double tap is the first event than down, so we shall ignore the following down
        private boolean isDoubleTap = false;

        @Override
        public boolean onDown(MotionEvent event) {
            logI("onDown() " + event.getX() + ", " + event.getY());
            if (event.getAction() == MotionEvent.ACTION_DOWN && isDoubleTap == false) {
                moveBackStop();
            }
            isDoubleTap = false;
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (mScaleCurrent <= mScaleFit) {
                return false;
            }
            if (getTouchMode() == TOUCH_MODE_NOTHING || getTouchMode() == TOUCH_MODE_ONE_FINGER_SCROLL) {
                float backupX = mLocationX;
                float backupY = mLocationY;
                boolean isXok = false;
                boolean isYok = false;
                mLocationX = mLocationX - (distanceX / (float) mWidth);
                mLocationY = mLocationY - (distanceY / (float) mHeight);
                if (mImageActor != null) {
                    mImageActor.setPosition(new Point(mLocationX, mLocationY, true));
                }
                if (getTouchMode() == TOUCH_MODE_NOTHING) {
                    setTouchMode(getTouchMode() | TOUCH_MODE_ONE_FINGER_SCROLL);
                }
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            logW("onDoubleTap() " + e.getX() + ", " + e.getY() + ", c:" + e.getPointerCount());
            isDoubleTap = true;

            Back_isTransitionPlaying = true;
            Back_isScaleBack = true;
            Back_TargetX = 0.5f;
            Back_TargetY = 0.5f;
            setTouchMode(getTouchMode() | TOUCH_MODE_BACK_EFFECT);
            return true;
        }
    };

    public ReFocusView(Context context) {
        this(context, null);
    }

    public ReFocusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mViewID = sTotalNewCount;
        sTotalNewCount++;
        logD("new " + this.getClass().getSimpleName() + "()");
        setup();

        mZoomGestureListener = new MyScaleGestureListener();
        mZoomGestureDetector = new ScaleGestureDetector(mContext, mZoomGestureListener);
        mGestureDetector = new GestureDetectorCompat(mContext, mZoomGestureListener);
    }

    @Override
    protected void finalize() throws Throwable {
        logD("~ " + this.getClass().getSimpleName() + "()");
        this.onDetachedFromWindow();
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
        if (mBitmapNew != null) {
            mBitmapNew.recycle();
            mBitmapNew = null;
        }
        super.finalize();
    }

    public static Bitmap getBitmapFromResource(Resources res, int r) {
        BitmapFactory.Options bfoOptions = new BitmapFactory.Options();
        bfoOptions.inScaled = false;
        return BitmapFactory.decodeResource(res, r, bfoOptions);
    }

    public static Bitmap getBitmapFromURI(ContentResolver cr, Uri uri) {
        BitmapFactory.Options bfoOptions = new BitmapFactory.Options();
        bfoOptions.inScaled = false;
        ParcelFileDescriptor pfd = null;
        FileDescriptor fd = null;
        try {
            pfd = cr.openFileDescriptor(uri, "r");
            fd = pfd.getFileDescriptor();
        } catch (Exception e) {
            //
        }
        return BitmapFactory.decodeFileDescriptor(fd);
    }

    private void setup() {
        logI("setup() is called : Configuration : " + getResources().getConfiguration());
        mStage.setProjection(Stage.UI_PERSPECTIVE, 500.0f, 5000.0f, -1111.0f);

        mImageActor = Image.createEmptyImage();
        mImageActor.setPosition(new Point(mLocationX, mLocationY, true));
        mImageActor.setMaterial("speed.mat");
        mImageActor.enableMipmap(true);
        mImageActor.setMaterialProperty("", "M_LEVEL", 1.0f);
        mStage.add(mImageActor);

        mFocusActor = Image.createFromResource(getResources(), R.drawable.m_refocus_refocus);
        mFocusActor.setVisible(false);
        mStage.add(mFocusActor);

        mDepthActor = Image.createEmptyImage();
        mDepthActor.setPosition(new Point(mDepthLocationX, mDepthLocationY, -3.0f, true));
        mDepthActor.setSize(new Dimension(10f, 10f));
        mDepthActor.setVisible(false);
        mStage.add(mDepthActor);
    }

    public void setDepthActor(byte[] data, int offset, int pixelsize, int width, int height) {
        if (data != null) {
            Log.v(TAG, "setDepthActor(" + width + "x" + height + ") (" + pixelsize + ") data.len:" + data.length + " offset:" + offset);
            if (data.length >= width * height * pixelsize + offset) {
                Bitmap img = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                int x = 0;
                int y = 0;
                for (y = 0 ; y < height ; y ++) {
                    for (x = 0 ; x < width ; x ++) {
                        int rgb = 0x000000ff & ((int) data[(y * width + x) * pixelsize + offset]);
                        img.setPixel(x, y, (0xff000000 | (rgb << 16) | (rgb << 8) | rgb));
                    }
                }
                setDepthActor(img);
            }
        }
    }

    public void setDepthActor(Bitmap pic) {
        Log.v(TAG, "setDepthActor() " + pic);
        if (pic != null) {
            mDepthActor.setImageFromBitmap(pic);
            mDepthActor.setSize(new Dimension(pic.getWidth(), pic.getHeight()));
            setImageActorViewSize(mImageActor, mBitmap, mWidth, mHeight);
            mDepthActor.setVisible(true);
        } else {
            mDepthActor.setVisible(false);
        }
    }

    private void setImageActorViewSize(Image img, Bitmap bmp, final float ViewWidth, final float ViewHeight) {

        if (bmp == null) return;
        final float BitmapWidth = bmp.getWidth();
        final float BitmapHeight = bmp.getHeight();

        final float ViewAspectRatio = ViewWidth / ViewHeight;
        final float BitmapAspectRatio = BitmapWidth / BitmapHeight;

        float scale = 0.0f;
        if (ViewAspectRatio <= BitmapAspectRatio) {
            scale = ViewWidth / BitmapWidth;
            mXOffset = 0.0f;
            mYOffset = (ViewHeight - BitmapHeight * scale) / 2.0f;
        } else {
            scale = ViewHeight / BitmapHeight;
            mXOffset = (ViewWidth - BitmapWidth * scale) / 2.0f;
            mYOffset = 0.0f;
        }

        logD("setImage(View__) " + ViewWidth + " x " + ViewHeight + " [" + ViewAspectRatio + "]");
        logD("setImage(Bitmap) " + BitmapWidth + " x " + BitmapHeight + " [" + BitmapAspectRatio + "]");
        logD("setImage() scale: " + scale + ", Offset: (" + mXOffset + ", " + mYOffset + ")");

        mScaleFit = scale;
        setImageActorViewSizeScale(img, mScaleFit);

        // Depth Actor
        float depthW = ViewWidth / 5.0f;
        Dimension dim = mDepthActor.getSize();
        float ratio = depthW / dim.width;
        mDepthActor.setScale(new Scale(ratio, ratio));
    }

    public void setImageActorViewSizeScale(float scale) {
        setImageActorViewSizeScale(mImageActor, scale);
    }

    private void setImageActorViewSizeScale(Image img, float scale){
        mScaleCurrent = scale;
        img.setScale(new Scale(scale, scale));
    }

    private synchronized void setImageActor(Bitmap pic, boolean recycle) {
        long begin = System.currentTimeMillis();
        long spentTime = 0;
        if (pic != null) {
            if (mBitmap != null && recycle) {
                mBitmap.recycle();
                MtkLog.i(TAG, "********* mBitmap.recycle()************");
            }
            spentTime = System.currentTimeMillis() - begin;
            MtkLog.i(TAG, "setImageActor mBitmap.recycle SpentTime = " + spentTime);
            mBitmap = pic;
            begin = System.currentTimeMillis();
            mImageActor.setImageFromBitmap(mBitmap);
            spentTime = System.currentTimeMillis() - begin;
            MtkLog.i(TAG, "mImageActor.setImageFromBitmap spentTime = " + spentTime);
            mImageActor.setSize(new Dimension(mBitmap.getWidth(),mBitmap.getHeight()));
            MtkLog.i(TAG, " mImageActor.setImageFromBitmap(mBitmap)");
            mImageActor.setMaterialProperty("", "M_NEW_TEXTURE", mBitmapNew);
            mImageActor.setMaterialProperty("", "M_STEP", 0.0f);
            mImageActor.setMaterialProperty("", "M_FADEOUTSTEP", 1.0f);
        }
    }

    public synchronized void setImageActor(Bitmap pic, final float ViewWidht, final float ViewHeight) {
        setImageActor(pic, true);
        if (ViewWidht > 0 && ViewHeight > 0) {
            setImageActorViewSize(mImageActor, mBitmap, mWidth, mHeight);
        }
    }

    public synchronized void setImageActorNew(Bitmap pic, boolean recycle) {
        logD("setImageActorNew(" + recycle + ") pic:" + pic);
        if (pic != null) {
            if (mBitmapNew != null && recycle) {
                mBitmapNew.recycle(); 
            }
            mBitmapNew = pic;
            mImageActor.setMaterialProperty("", "M_NEW_TEXTURE", mBitmapNew);
        }
    }

    public synchronized void setImageActorNew(Bitmap pic) {
        setImageActorNew(pic, true);
    }

    private synchronized void setImageActorSwap() {
        logD("setImageActorSwap() to " + mBitmapNew);
        Bitmap tmp = mBitmapNew;
        mBitmapNew = mBitmap;
        setImageActor(tmp, false);
        setImageActorNew(mBitmapNew, false);
        float fit = mScaleCurrent;
        setImageActorViewSize(mImageActor, mBitmap, mWidth, mHeight);
        setImageActorViewSizeScale(fit);
    }

    private boolean StageFadeIn_isGetFirstFrameTime;
    private long StageFadeIn_mFirstFrameTime;
    private boolean StageFadeIn_isFadeInDone;
    private final float StageFadeIn_durationTime = 300.0f;

    private void onDrawFrameStageFadeIn() {
        if (StageFadeIn_isGetFirstFrameTime == false) {
            logD("onDrawFrame() Stage Fade-In Start !");
            StageFadeIn_mFirstFrameTime = SystemClock.elapsedRealtime();
            StageFadeIn_isGetFirstFrameTime = true;
        }
        if (StageFadeIn_isFadeInDone == false) {
            long currentTime = SystemClock.elapsedRealtime();
            int value = 1 + (int) (254.0f * Math.min((float) (currentTime - StageFadeIn_mFirstFrameTime)
                / StageFadeIn_durationTime, 1.0f));
            mStage.setOpacity(value);
            if (value >= 255) {
                StageFadeIn_isFadeInDone = true;
            }
        }
    }

    private boolean mIsShowImage_isTransitionPlaying;
    private boolean mIsShowImage_isGetFirstFrameTime;
    private long mShowImage_mFirstFrameTime;
    private float mShowImage_TotalDurationTime = 600.0f;
    private float mShowImage_FirstDurationTime = 300.0f;

    public synchronized void setTransitionTime(float total, float firstpart) {
        logD("setTransitionTime() total:" + total + ", firstpart:" + firstpart);
        if (mIsShowImage_isTransitionPlaying == false) {
            if (total > firstpart) {
                mShowImage_TotalDurationTime = total;
                mShowImage_FirstDurationTime = firstpart;
            }
        }
    }

    private void onDrawFrameImageReset(boolean reloadBitmap) {
        mIsShowImage_isTransitionPlaying = false;
        mIsShowImage_isGetFirstFrameTime = false;
        if (reloadBitmap) {
            mImageActor.setImageFromBitmap(mBitmap);
            mImageActor.setSize(new Dimension(mBitmap.getWidth(),mBitmap.getHeight()));
            mImageActor.setMaterialProperty("", "M_NEW_TEXTURE", mBitmapNew);
        }
        mImageActor.setMaterialProperty("", "M_STEP", 0.0f);
        mImageActor.setMaterialProperty("", "M_FADEOUTSTEP", 1.0f);
        mImageActor.setMaterialProperty("", "M_LEVEL", 1.0f);
        mFocusActor.setVisible(false);
    }

    private void onDrawFrameImage() {
        Log.i(TAG, "onDrawFrameImage");
        boolean isEnd = false;
        if (mIsShowImage_isGetFirstFrameTime == false) {
            mShowImage_mFirstFrameTime = SystemClock.elapsedRealtime();
            mIsShowImage_isGetFirstFrameTime = true;
        }

        long localTick = SystemClock.elapsedRealtime() - mShowImage_mFirstFrameTime;
        float fadeoutstep = 1.0f;
        if (localTick > mShowImage_TotalDurationTime) {
            isEnd = true;
        }

        float step = 0.0f;
        if (localTick <= mShowImage_FirstDurationTime) {
            step = localTick / mShowImage_FirstDurationTime;
        } else if (localTick > mShowImage_FirstDurationTime) {
            fadeoutstep = (mShowImage_TotalDurationTime - localTick)
                / (mShowImage_TotalDurationTime - mShowImage_FirstDurationTime);
            fadeoutstep = (fadeoutstep > 0.0f) ? fadeoutstep : 0.0f;
            mImageActor.setMaterialProperty("", "M_FADEOUTSTEP", fadeoutstep);

            localTick = (long) (mShowImage_TotalDurationTime - localTick);
            step = (float) localTick / (mShowImage_TotalDurationTime - mShowImage_FirstDurationTime);
        }
        step = (step > 0.0f) ? step : 0.0f;
        mImageActor.setMaterialProperty("", "M_STEP", step);
        mImageActor.setMaterialProperty("", "M_LEVEL", 25.0f);

        if (isEnd) {
            onDrawFrameImageReset(true);
            setImageActorSwap();
            mImageActor.setMaterialProperty("", "M_LEVEL", 1.0f);
            setTouchMode(getTouchMode() & (~TOUCH_MODE_TRANSITION_EFFECT));
            logD("onDrawFrameImage() done");
            requestRender();
        }
    }

    private boolean Back_isTransitionPlaying = false;
    private boolean Back_isGetFirstFrameTime = false;
    private long Back_mFirstFrameTime;
    private float Back_TotalDurationTime = 250.0f;
    private float Back_LocationX;
    private float Back_LocationY;
    private float Back_ScaleCurrent;
    private boolean Back_isScaleBack = false;
    private float Back_TargetX = 0.5f;
    private float Back_TargetY = 0.5f;

    private void onDrawFrameBack() {
        boolean isEnd = false;
        if (Back_isGetFirstFrameTime == false) {
            Back_mFirstFrameTime = SystemClock.elapsedRealtime();
            Back_isGetFirstFrameTime = true;
            Back_LocationX = mLocationX;
            Back_LocationY = mLocationY;
            Back_ScaleCurrent = mScaleCurrent;
        }

        long localTick = SystemClock.elapsedRealtime() - Back_mFirstFrameTime;
        if (localTick > Back_TotalDurationTime) {
            isEnd = true;
        }

        float step = 0.0f;
        if (localTick <= Back_TotalDurationTime) {
            step = localTick / Back_TotalDurationTime;
            mLocationX = Back_LocationX * (1 - step) + Back_TargetX * step;
            mLocationY = Back_LocationY * (1 - step) + Back_TargetY * step;
            if (mImageActor != null) {
                mImageActor.setPosition(new Point(mLocationX, mLocationY, true));
            }
            if (Back_isScaleBack) {
                float scale = Back_ScaleCurrent * (1 - step) + mScaleFit * step;
                setImageActorViewSizeScale(scale);
            }
        }

        if (isEnd) {
            Back_isTransitionPlaying = false;
            Back_isGetFirstFrameTime = false;
            mLocationX = Back_TargetX;
            mLocationY = Back_TargetY;
            if (mImageActor != null) {
                mImageActor.setPosition(new Point(mLocationX, mLocationY, true));
            }
            if (Back_isScaleBack) {
                setImageActorViewSizeScale(mScaleFit);
            }
            Back_isScaleBack = false;
            setTouchMode(getTouchMode() & (~TOUCH_MODE_BACK_EFFECT));
            logD("onDrawFrameBack() done");
            requestRender();
        }
    }

    private synchronized void moveBackStop() {
        logW("onDrawFrameBackStop() " + Back_isTransitionPlaying + ", " + Back_isScaleBack);
        Back_isTransitionPlaying = false;
        Back_isGetFirstFrameTime = false;
        Back_isScaleBack = false;
        setTouchMode(getTouchMode() & (~TOUCH_MODE_BACK_EFFECT));
        requestRender();
    }

    @Override
    public synchronized void onDrawFrame(GL10 gl) {
//        onDrawFrameStageFadeIn(); 

        if (mIsShowImage_isTransitionPlaying) {
            onDrawFrameImage();
        }

        if (Back_isTransitionPlaying) {
            onDrawFrameBack();
        }

        super.onDrawFrame(gl);
    }

    static final int TOUCH_MODE_NOTHING = 0;
    static final int TOUCH_MODE_ONE_FINGER_SCROLL = 0x00000001;
    static final int TOUCH_MODE_TWO_FINGER_SCROLL = 0x00000002;
    static final int TOUCH_MODE_TRANSITION_EFFECT = 0x00000004;
    static final int TOUCH_MODE_BACK_EFFECT       = 0x00000008;

    private int mTouchMode = TOUCH_MODE_NOTHING;

    private synchronized void setTouchMode(int mode) {
        logI("setTouchMode() " + mTouchMode + " => " + mode);
        mTouchMode = mode;
        requestRender();
    }

    private synchronized int getTouchMode() {
        return mTouchMode;
    }
    
    @Override
    public synchronized boolean onTouchEvent (MotionEvent event) {
        super.onTouchEvent(event);
        //logI("onTouchEvent()" + event.getX() + "," + event.getY() + ")");
        boolean bProc = false;

        if (mZoomGestureDetector != null) {
            mZoomGestureListener.setMotionEvent(this, event);
            bProc |= mZoomGestureDetector.onTouchEvent(event);
        }

        if (mGestureDetector != null) {
            bProc |= mGestureDetector.onTouchEvent(event);
        }

        if (getTouchMode() == TOUCH_MODE_NOTHING) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    mDownX = event.getX();
                    mDownY = event.getY();
                    logI("onTouchEvent() @View(" + event.getX() + "," + event.getY() + ")");

                    if (mImageActor.hitTest(new Point(mDownX, mDownY)) != null) {
                        MtkLog.i(TAG, "mDownX = " + mDownX + "|| mDownY = " + mDownY);

                        //setImageActorNew(refocusBitmap);
                        float k = mScaleCurrent / mScaleFit;
                        float mDownRX = 0.5f;
                        float mDownRY = 0.5f;

                        mDownRX += (mDownX - mLocationX * (float) mWidth) / (((float) mWidth - mXOffset * 2.0f) * k);
                        mDownRY += (mDownY - mLocationY * (float) mHeight) / (((float) mHeight - mYOffset * 2.0f) * k);
                        logI("onTouchEvent() @Image(" + mDownRX + "," + mDownRY + ") k:" + k);
                        mRefocusListener.setRefocusImage(mDownRX, mDownRY);
                        onDrawFrameImageReset(false);
                        mImageActor.setMaterialProperty("", "M_X", mDownRX);
                        mImageActor.setMaterialProperty("", "M_Y", mDownRY);
                        mIsShowImage_isTransitionPlaying = true;

                        mFocusActor.setVisible(true);
                        mFocusActor.setPosition(new Point(mDownX, mDownY, -1.0f, false));
                        setTouchMode(getTouchMode() | TOUCH_MODE_TRANSITION_EFFECT);
                        requestRender();
                    } else {
                        moveBackCheck();
                    }
                    bProc |= true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP && event.getPointerCount() <= 1) {
            setTouchMode(TOUCH_MODE_NOTHING);
            moveBackCheck();
        }
        return bProc;
    }

    public void getImageActorPosition(int point[]) {
        if (point == null || point.length < 4) {
            return;
        }
        float k = mScaleCurrent / mScaleFit;
        float img_w = ((float) mWidth - mXOffset * 2.0f);
        float img_h = ((float) mHeight - mYOffset * 2.0f);
        float w = img_w / 2.0f;
        float h = img_h / 2.0f;
        float centerX = mLocationX * mWidth;
        float centerY = mLocationY * mHeight;
        point[0] = (int) (centerX - w * k);
        point[1] = (int) (centerX + w * k);
        point[2] = (int) (centerY - h * k);
        point[3] = (int) (centerY + h * k);
    }

    private void moveBackCheck() {
        int pXXYY[] = new int[4];
        getImageActorPosition(pXXYY);
        boolean needMove = false;

        if (Back_isTransitionPlaying == false && (getTouchMode() & TOUCH_MODE_BACK_EFFECT) != TOUCH_MODE_BACK_EFFECT) {

            Back_TargetX = mLocationX;
            Back_TargetY = mLocationY;

            // check X
            if ((0 < pXXYY[0] && pXXYY[0] < mWidth) && (mWidth < pXXYY[1])) {
                needMove = true;
                Back_TargetX = mLocationX - (float) (((float) pXXYY[0]) / (float) mWidth);
                if ((pXXYY[1] - pXXYY[0]) < mWidth) {
                    Back_TargetX += ((float) mWidth - (float) (pXXYY[1] - pXXYY[0])) / (2.0f * (float) mWidth);
                }
            } else if ((0 < pXXYY[1] && pXXYY[1] < mWidth) && (0 > pXXYY[0])) {
                needMove = true;
                Back_TargetX = mLocationX + (float) ((float) (mWidth - pXXYY[1]) / (float) mWidth);
                if ((pXXYY[1] - pXXYY[0]) < mWidth) {
                    Back_TargetX -= ((float) mWidth - (float) (pXXYY[1] - pXXYY[0])) / (2.0f * (float) mWidth);
                }
            } else if(pXXYY[0] < 0 && pXXYY[1] < 0) {
                needMove = true;
                Back_TargetX = mLocationX + (1.0f - ((float)pXXYY[1] / (float)mWidth));
                if ((pXXYY[1] - pXXYY[0]) < mWidth) {
                    Back_TargetX -= ((float) mWidth - (float) (pXXYY[1] - pXXYY[0])) / (2.0f * (float) mWidth);
                }
            } else if(pXXYY[0] > mWidth && pXXYY[1] > mWidth) {
                needMove = true;
                Back_TargetX = mLocationX - ((float)pXXYY[0] / (float)mWidth);
                if ((pXXYY[3] - pXXYY[2]) < mWidth) {
                    Back_TargetX += ((float) mWidth - (float) (pXXYY[1] - pXXYY[0])) / (2.0f * (float) mWidth);
                }
            }

            // check Y
            if ((0 < pXXYY[2] && pXXYY[2] < mHeight) && (mHeight < pXXYY[3])) {
                needMove = true;
                Back_TargetY = mLocationY - (float) (((float) pXXYY[2]) / (float) mHeight);
                if ((pXXYY[3] - pXXYY[2]) < mHeight) {
                    Back_TargetY += ((float) mHeight - (float) (pXXYY[3] - pXXYY[2])) / (2.0f * (float) mHeight);
                }
            } else if ((0 < pXXYY[3] && pXXYY[3] < mHeight) && (0 > pXXYY[2])) {
                needMove = true;
                Back_TargetY = mLocationY + (float) ((float) (mHeight - pXXYY[3]) / (float) mHeight);
                if ((pXXYY[3] - pXXYY[2]) < mHeight) {
                    Back_TargetY -= ((float) mHeight - (float) (pXXYY[3] - pXXYY[2])) / (2.0f * (float) mHeight);
                }
            } else if(pXXYY[2] < 0 && pXXYY[3] < 0) {
                needMove = true;
                Back_TargetY = mLocationY + (1.0f - ((float)pXXYY[3] / (float)mHeight));
                if ((pXXYY[3] - pXXYY[2]) < mHeight) {
                    Back_TargetY -= ((float) mHeight - (float) (pXXYY[3] - pXXYY[2])) / (2.0f * (float) mHeight);
                }
            } else if(pXXYY[2] > mHeight && pXXYY[3] > mHeight) {
                needMove = true;
                Back_TargetY = mLocationY - ((float)pXXYY[2] / (float)mHeight);
                if ((pXXYY[3] - pXXYY[2]) < mHeight) {
                    Back_TargetY += ((float) mHeight - (float) (pXXYY[3] - pXXYY[2])) / (2.0f * (float) mHeight);
                }
            }

            if (needMove) {
                Back_isTransitionPlaying = true;
                Back_isScaleBack = false;
                setTouchMode(getTouchMode() | TOUCH_MODE_BACK_EFFECT);
            }
        }
    }

    public boolean checkBoundary(int isY) {
        boolean result = false;
        int pX1X2Y1Y2[] = new int[4];
        getImageActorPosition(pX1X2Y1Y2);

        switch (isY) {
            case 0:
                if ((0 < pX1X2Y1Y2[0] && pX1X2Y1Y2[0] < mWidth) || (0 < pX1X2Y1Y2[1] && pX1X2Y1Y2[1] < mWidth)) {
                    result = false;
                } else {
                    result = true;
                }
                break;

            case 1:
                if ((0 < pX1X2Y1Y2[2] && pX1X2Y1Y2[2] < mHeight) || (0 < pX1X2Y1Y2[3] && pX1X2Y1Y2[3] < mHeight)) {
                    result = false;
                } else {
                    result = true;
                }
                break;
        }
        logE(result + " isY:" + isY + " Point:" + String.format("(%d, %d) (%d, %d)", pX1X2Y1Y2[0], pX1X2Y1Y2[1], pX1X2Y1Y2[2], pX1X2Y1Y2[3]));
        return result;
    }

    @Override
    protected synchronized void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        logW("onSizeChanged() " + w + "x" + h + " <= " + oldw + "x" + oldh);
        mWidth = w;
        mHeight = h;
        setImageActorViewSize(mImageActor, mBitmap, w, h);
    }

    @Override
    protected synchronized void onAttachedToWindow() {
        logI("onAttachedToWindow()");
        super.onAttachedToWindow();
    }

    @Override
    protected synchronized void onDetachedFromWindow() {
        logI("onDetachedFromWIndow()");
        onPause();
        mStage.unrealize();
        mStage.removeAll();
        super.onDetachedFromWindow();
    }
    
    private RefocusListener mRefocusListener;
    public interface RefocusListener {
        public void setRefocusImage(float x, float y);

    }
    
    public void setRefocusListener (RefocusListener refocusListener) {
        mRefocusListener = refocusListener;
    }
}
