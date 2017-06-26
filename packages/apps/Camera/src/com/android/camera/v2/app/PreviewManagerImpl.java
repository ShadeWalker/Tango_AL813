package com.android.camera.v2.app;

import junit.framework.Assert;
import android.app.Activity;
import android.graphics.Rect;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.View.OnLayoutChangeListener;
import android.widget.FrameLayout;

import com.android.camera.R;
import com.android.camera.v2.ui.FixedAspectSurfaceView;

/**
 *  A preview helper for SurfaceView.
 */
public class PreviewManagerImpl extends PreviewManager {
    private static final String              TAG = "PreviewManagerImpl";
    private final Activity                   mActivity;

    private FrameLayout                      mCurSurfaceViewLayout;
    private FrameLayout                      mLastSurfaceViewLayout;
    
    private FixedAspectSurfaceView           mSurfaceView;
    private boolean                          mSurfaceAvailable = false;
    private Surface                          mSurface;
    private GestureDetector                  mGestureDetector = null;
    private double                           mPreviewAspectRatio = 0.0d;
    private int                              mPreviewWidth = 0;
    private int                              mPreviewHeight = 0;

    private SurfaceHolder.Callback           mSurfaceViewCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Rect surfaceFrame = holder.getSurfaceFrame();
            Log.i(TAG, "surfaceCreated mPreviewWidth = " + surfaceFrame.width() 
                    + " mPreviewHeight = " + surfaceFrame.height());
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,int height) {
            Log.i(TAG, "surfaceChanged width = " + width + " height = " + height);
            if (mSurfaceCallback != null && width == mPreviewWidth && height == mPreviewHeight) {
                mSurface = holder.getSurface();
                if (mSurfaceAvailable) {
                    mSurfaceCallback.surfaceSizeChanged(mSurface, width, height);
                } else {
                    mSurfaceAvailable = true;
                    mSurfaceCallback.surfaceAvailable(mSurface, width, height);
                }
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surfaceDestroyed");
            if (mSurfaceCallback != null) {
                mSurfaceCallback.surfaceDestroyed(holder.getSurface());
            }
        }
    };

    private OnLayoutChangeListener          mOnLayoutChangeCallback = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            Log.i(TAG, "onLayoutChange left = " + left + 
                                       " top = " + top + 
                                       " width = " + (right - left) + 
                                       " height = " + (bottom - top));
            mPreviewArea.set(left, top, right, bottom);
            // This method can be called during layout pass. We post a Runnable so
            // that the callbacks won't happen during the layout pass.
            mSurfaceView.post(new Runnable() {
                @Override
                public void run() {
                    notifyPreviewAreaChanged();
                }
            });
            if (mOnLayoutChangeListener != null) {
                mOnLayoutChangeListener.onLayoutChange(v, left, top, right, bottom, 
                        oldLeft, oldTop,oldRight, oldBottom);
            }
        }
    };

    public PreviewManagerImpl(Activity activity) {
        mActivity = activity;
    }

    @Override
    public void updatePreviewSize(int width, int height) {
        Assert.assertTrue(width > 0 && height > 0);
        if (mPreviewWidth == width && mPreviewHeight == height) {
            Log.i(TAG, "setPreviewSize skip : width = " + width
                    + " height = " + height);
            return;
        }
        double newAspectRatio = (double) width / height;
        mSurfaceView = null;
        mPreviewWidth = width;
        mPreviewHeight = height;
        mPreviewAspectRatio = (double)mPreviewWidth / mPreviewHeight;
        // if aspect ration changed, attach a new surface view
        if (mSurfaceView == null) {
            attachSurfaceViewLayout();
            mSurfaceAvailable = false;
        }
        mSurfaceView.getHolder().setFixedSize(mPreviewWidth, mPreviewHeight);
        mSurfaceView.setAspectRatio(mPreviewAspectRatio);
    }

    @Override
    public View getPreviewView() {
        return mSurfaceView;
    }

    @Override
    public void setGestureListener(OnGestureListener gestureListener) {
        super.setGestureListener(gestureListener);
        if (gestureListener != null) {
            mGestureDetector = new GestureDetector(mActivity, gestureListener);
        }
    }

    @Override
    public void onPreviewStarted() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                detachSurfaceViewLayout();
            }
        });
    }

    private void attachSurfaceViewLayout() {
        Log.i(TAG, "[attachSurfaceViewLayout]...");
        if (mSurfaceView == null) {
            FrameLayout surfaceViewRoot = (FrameLayout) mActivity.findViewById(R.id.camera_preview_container);
            mLastSurfaceViewLayout = mCurSurfaceViewLayout;
            mCurSurfaceViewLayout = (FrameLayout) mActivity.getLayoutInflater().inflate(R.layout.camera_previewsurfaceview_layout, null);
            mSurfaceView = (FixedAspectSurfaceView) mCurSurfaceViewLayout.findViewById(R.id.previewsurfaceview);
            mSurfaceView.addOnLayoutChangeListener(mOnLayoutChangeCallback);
            SurfaceHolder surfaceHolder = mSurfaceView.getHolder();
            surfaceHolder.addCallback(mSurfaceViewCallback);
            surfaceViewRoot.addView(mCurSurfaceViewLayout);
            surfaceViewRoot.setVisibility(View.VISIBLE);
            mSurfaceView.setVisibility(View.VISIBLE);
        }
    }

    private void detachSurfaceViewLayout() {
        Log.i(TAG, "[detachSurfaceViewLayout] mLastSurfaceViewLayout = " + mLastSurfaceViewLayout);
        if (mLastSurfaceViewLayout != null) {
            FrameLayout surfaceViewRoot = (FrameLayout) mActivity.findViewById(R.id.camera_preview_container);
            surfaceViewRoot.removeViewInLayout(mLastSurfaceViewLayout);
            mLastSurfaceViewLayout.setVisibility(View.GONE);
            mLastSurfaceViewLayout = null;
        }
    }
}
