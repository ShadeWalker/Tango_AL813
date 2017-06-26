package com.mediatek.galleryfeature.drm;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import com.android.gallery3d.R;
import com.mediatek.galleryfeature.drm.DeviceMonitor.ConnectStatus;
import com.mediatek.galleryframework.gl.MBitmapTexture;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MResourceTexture;
import com.mediatek.galleryframework.gl.MStringTexture;
import com.mediatek.galleryframework.gl.MTexture;
import com.mediatek.galleryframework.util.Utils;

public class DrmProtectTexture implements MTexture {
    private static final String TAG = "MtkGallery2/DrmProtectTexture";
    private static final float DEFAULT_TEXT_SIZE = Utils.dpToPixel(18);
    private static final int DEFAULT_COLOR = Color.WHITE;

    private MStringTexture mDrmProtectText1;
    private MStringTexture mDrmProtectText2;
    private MStringTexture mWfdProtectText;
    private MStringTexture mSmbProtectText;
    private MStringTexture mHdmiProtectText;
    private MResourceTexture mDrmLimitTeture;

    private ConnectStatus mLimit;

    private int mWidth;
    private int mHeight;

    public DrmProtectTexture(Context context) {
        mDrmProtectText1 = MStringTexture.newInstance(context
                .getString(R.string.m_drm_protected_warning1),
                DEFAULT_TEXT_SIZE, DEFAULT_COLOR);
        mDrmProtectText2 = MStringTexture.newInstance(context
                .getString(R.string.m_drm_protected_warning2),
                DEFAULT_TEXT_SIZE, DEFAULT_COLOR);
        mWfdProtectText = MStringTexture.newInstance(context
                .getString(R.string.m_wfd_protected_warning),
                DEFAULT_TEXT_SIZE, DEFAULT_COLOR);
        mSmbProtectText = MStringTexture.newInstance(context
                .getString(R.string.m_smb_protected_warning),
                DEFAULT_TEXT_SIZE, DEFAULT_COLOR);
        mHdmiProtectText = MStringTexture.newInstance(context
                .getString(R.string.m_hdmi_protected_warning),
                DEFAULT_TEXT_SIZE, DEFAULT_COLOR);

        mDrmLimitTeture = new MResourceTexture(context,
                R.drawable.m_ic_drm_img_disable);
        mDrmLimitTeture.setOpaque(false);

        mWidth = Math.max(mDrmProtectText1.getWidth(), mDrmProtectText2
                .getWidth());
        mWidth = Math.max(mWidth, mWfdProtectText.getWidth());
        mWidth = Math.max(mWidth, mSmbProtectText.getWidth());
        mWidth = Math.max(mWidth, mHdmiProtectText.getWidth());
        mWidth = Math.max(mWidth, mDrmLimitTeture.getWidth());

        mHeight = mDrmLimitTeture.getHeight() + mDrmProtectText1.getHeight()
                * 6;
    }

    public void setProtectStatus(ConnectStatus status) {
        mLimit = status;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public void draw(MGLCanvas canvas, int x, int y) {
        int textHeight = mDrmProtectText1.getHeight();

        canvas.save(MGLCanvas.SAVE_FLAG_MATRIX);

        canvas.translate(x, y);
        mDrmLimitTeture.draw(canvas, (mWidth - mDrmLimitTeture.getWidth()) / 2,
                0);

        canvas.translate(0, mDrmLimitTeture.getHeight() + textHeight);
        mDrmProtectText1.draw(canvas,
                (mWidth - mDrmProtectText1.getWidth()) / 2, 0);

        canvas.translate(0, mDrmProtectText1.getHeight() + textHeight);
        mDrmProtectText2.draw(canvas,
                (mWidth - mDrmProtectText2.getWidth()) / 2, 0);

        MTexture textureWaitToDraw = null;
        switch (mLimit) {
        case WFD_CONNECTED:
            textureWaitToDraw = mWfdProtectText;
            break;
        case HDMI_CONNECTD:
            textureWaitToDraw = mHdmiProtectText;
            break;
        case SMARTBOOK_CONNECTD:
            textureWaitToDraw = mSmbProtectText;
            break;
        default:
            break;
        }

        if (textureWaitToDraw != null) {
            canvas.translate(0, mDrmProtectText2.getHeight() + textHeight);
            textureWaitToDraw.draw(canvas, (mWidth - textureWaitToDraw
                    .getWidth()) / 2, 0);
        }
        canvas.restore();
        return;
    }

    public void draw(MGLCanvas canvas, int x, int y, int w, int h) {
        x += (w - getWidth()) / 2;
        y += (h - getHeight()) / 2;
        draw(canvas, x, y);
    }

    public boolean isOpaque() {
        return mDrmLimitTeture.isOpaque();
    }

    public void recycle() {
        mDrmProtectText1.recycle();
        mDrmProtectText2.recycle();
        mWfdProtectText.recycle();
        mSmbProtectText.recycle();
        mHdmiProtectText.recycle();
        mDrmLimitTeture.recycle();
    }

    private void drawDrmIcon(MGLCanvas canvas, int x, int y) {
    }

    private void drawDrmIcon(MGLCanvas canvas, int x, int y, int width,
            int height) {
    }
}