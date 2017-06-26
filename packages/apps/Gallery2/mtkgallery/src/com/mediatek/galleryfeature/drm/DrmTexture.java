package com.mediatek.galleryfeature.drm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.mediatek.galleryframework.gl.MBitmapTexture;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MResourceTexture;
import com.mediatek.galleryframework.gl.MStringTexture;
import com.mediatek.galleryframework.gl.MTexture;

public class DrmTexture implements MTexture {
    private static final String TAG = "MtkGallery2/DrmTexture";

    private MBitmapTexture mTexture;
    private MResourceTexture mResourceTexture;
    private MStringTexture mStringTexture;

    public DrmTexture(Context context, Bitmap bitmap, int iconResourceId, String strShowCenter) {
        mTexture = new MBitmapTexture(bitmap);
        if (iconResourceId != 0) {
            mResourceTexture = new MResourceTexture(context, iconResourceId);
            mResourceTexture.setOpaque(false);
        }
        if (strShowCenter != null && !strShowCenter.equals("")) {
            mStringTexture = MStringTexture.newInstance(strShowCenter, 20, Color.WHITE);
        }
    }

    public int getWidth() {
        if (mResourceTexture == null)
            return mTexture.getWidth();
        else
            return Math.max(mTexture.getWidth(), mResourceTexture.getWidth());
    }

    public int getHeight() {
        if (mResourceTexture == null)
            return mTexture.getHeight();
        else
            return Math.max(mTexture.getHeight(), mResourceTexture.getHeight());
    }

    public void draw(MGLCanvas canvas, int x, int y) {
        draw(canvas, x, y, getWidth(), getHeight());
    }

    public void draw(MGLCanvas canvas, int x, int y, int w, int h) {
        mTexture.draw(canvas, x, y, w, h);
        drawDrmIcon(canvas, x, y, w, h);
        drawStringAtCenter(canvas, x, y, w, h);
    }

    public boolean isOpaque() {
        return mTexture.isOpaque();
    }

    public void recycle() {
        mTexture.recycle();
        if (mResourceTexture != null)
            mResourceTexture.recycle();
        if (mStringTexture != null)
            mStringTexture.recycle();
    }

    private void drawDrmIcon(MGLCanvas canvas, int x, int y, int width,
            int height) {
        if (mResourceTexture == null)
            return;
        int texWidth = (int) ((float) mResourceTexture.getWidth());
        int texHeight = (int) ((float) mResourceTexture.getHeight());
        mResourceTexture.draw(canvas, x + width - texWidth, y + height
                - texHeight, texWidth, texHeight);
    }

    private void drawStringAtCenter(MGLCanvas canvas, int x, int y, int w, int h) {
        if (mStringTexture == null)
            return;
        int cx = x + w / 2 - mStringTexture.getWidth() / 2;
        int cy = y + h / 2 - mStringTexture.getHeight() / 2;
        mStringTexture.draw(canvas, cx, cy);
    }
}