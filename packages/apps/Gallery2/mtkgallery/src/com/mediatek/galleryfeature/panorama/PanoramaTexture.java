package com.mediatek.galleryfeature.panorama;

import android.graphics.Bitmap;

import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MRawTexture;
import com.mediatek.galleryframework.gl.MTexture;

public class PanoramaTexture implements MTexture {
    private static final String TAG = "MtkGallery2/PanoramaTexture";

    private int mColor;
    private boolean mColorPanorama = false;
    private MRawTexture mTexture;
    private PanoramaDrawer mPanoramaDrawer;
    private PanoramaConfig mConfig;
    private float mLastDegree = -1;
    private float mNewDegree = 0;
    private int mRotation = 0;

    public PanoramaTexture(Bitmap bitmap, PanoramaConfig config, int orientation) {
        mConfig = config;
        mPanoramaDrawer = new PanoramaDrawer(bitmap, mConfig);
        mRotation = orientation;
    }

    public PanoramaTexture(int color, PanoramaConfig config, int orientation) {
        mColor = color;
        mColorPanorama = true;
        mConfig = config;
        mPanoramaDrawer = new PanoramaDrawer(color, mConfig);
        mRotation = orientation;
    }

    public boolean isColorPanorma() {
        return mColorPanorama;
    }

    public int getWidth() {
        return (int) (mConfig.mCanvasWidth / mConfig.mCanvasScale);
    }

    public int getHeight() {
        return (int) (mConfig.mCanvasHeight / mConfig.mCanvasScale);
    }

    public boolean isOpaque() {
        return false;
    }

    public void recycle() {
        if (mTexture != null) {
            mTexture.recycle();
            mTexture = null;
        }
        if (mPanoramaDrawer != null) {
            mPanoramaDrawer.freeResources();
        }
        mLastDegree = -1;
    }

    public void draw(MGLCanvas canvas, int x, int y, int width, int height) {
        if (mTexture == null) {
            mTexture = new MRawTexture(mConfig.mCanvasWidth,
                    mConfig.mCanvasHeight, false);
        }
        if (mLastDegree != mNewDegree) {
            mPanoramaDrawer.drawOnTexture(canvas, mTexture, mNewDegree);
            mLastDegree = mNewDegree;
        }
        if (mRotation == 90 || mRotation == 270) {
            canvas.save(MGLCanvas.SAVE_FLAG_MATRIX);
            canvas.translate(width / 2, height / 2);
            canvas.rotate(-mRotation, 0, 0, 1);
            canvas.translate(-height / 2, -width / 2);
            drawInternal(canvas, x, y, height, width);
            canvas.restore();
        } else if (mRotation == 180) {
            canvas.save(MGLCanvas.SAVE_FLAG_MATRIX);
            canvas.translate(width / 2, height / 2);
            canvas.rotate(-mRotation, 0, 0, 1);
            canvas.translate(-width / 2, -height / 2);
            drawInternal(canvas, x, y, width, height);
            canvas.restore();
        } else {
            drawInternal(canvas, x, y, width, height);
        }
    }

    private void drawInternal(MGLCanvas canvas, int x, int y, int width, int height) {
        if (width == height) {
            float newHeight = (float) width / (float) mTexture.getWidth()
                    * (float) mTexture.getHeight();
            float newY = (height - newHeight) / 2.f;
            mTexture.draw(canvas, x, (int) newY, width, (int) newHeight);
        } else {
            mTexture.draw(canvas, x, y, width, height);
        }
    }

    public void draw(MGLCanvas canvas, int x, int y) {
        draw(canvas, x, y, getWidth(), getHeight());
    }

    public void setBitmap(Bitmap bitmap) {
        mColorPanorama = false;
        mLastDegree = -1;
        mPanoramaDrawer.setBitmap(bitmap);
    }

    public void setDegree(float degree) {
        mNewDegree = degree;
    }
}
