package com.mediatek.gallery3d.adapter;

import android.content.Context;
import android.graphics.Rect;
import com.mediatek.galleryframework.util.MtkLog;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;

import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.R;
import com.mediatek.galleryfeature.mav.IconView;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MResourceTexture;

public class MavIconView extends GLView implements IconView {
    private static final String TAG = "MtkGallery2/MavIconView";

    private Icon mIcon;
    private int mLength;
    private int mContentWidth;
    private int mContentHight;
    private int mMeasureWidth;
    private int mMeasureHight;
    private int mViewWidth;
    private int mViewHeight;
    private int mFocusButtion;
    private boolean mEnable;
    private Context mContext;

    public MavIconView(Context context) {
        mContext = context;
        mEnable = true;
        mIcon = new Icon(0, context, R.drawable.m_mav_overlay, 0);
    }

    @Override
    protected void render(GLCanvas canvas) {
        if (!mEnable)
            return;
        draw(canvas.getMGLCanvas());
    }

    public void draw(MGLCanvas canvas, int x, int y) {
        mIcon.draw(canvas, x, y);
    }

    public void draw(MGLCanvas canvas) {
        draw(canvas, 0, 0);
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredSize(mMeasureWidth, mMeasureHight);
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        return false;
    }

    public void setVisibility(boolean visible) {
        super.setVisibility(visible ? GLView.VISIBLE : GLView.INVISIBLE);
        if (mIcon != null)
            mIcon.setVisible(visible);
    }

    public boolean isVisible() {
        return super.getVisibility() == GLView.VISIBLE ? true : false;
    }

    public void setEnable(boolean enable) {
        mEnable = enable;
    }

    public int getFocusButton() {
        return mFocusButtion;
    }

    @Override
    public void onDetachFromRoot() {
        super.onDetachFromRoot();
        mIcon.releaseTexture();
    }

    private class Icon {
        public boolean mVisible;
        public boolean mFocus;
        private int mFocusResID;
        private int mNormalResID;
        private int mGap;
        public int mName;
        private Rect mContentRect;
        private MResourceTexture mCurrTexture;

        public Icon(int name, Context context, int focusResID, int gap) {
            mName = name;
            mFocusResID = focusResID;
            mGap = gap;
            mCurrTexture = new MResourceTexture(context, focusResID);
            mVisible = true;
        }

        public void releaseTexture() {
            if (mCurrTexture != null) {
                mCurrTexture.recycle();
                mCurrTexture = null;
                mVisible = false;
            }
        }

        public void draw(MGLCanvas canvas, int x, int y) {
            if (!mVisible || mCurrTexture == null)
                return;
            mCurrTexture.draw(canvas, x - mCurrTexture.getWidth() / 2, y
                    - mCurrTexture.getHeight() / 2);
        }

        public void setVisible(boolean visible) {
            this.mVisible = visible;
        }

        public void setPosition(int x, int y) {
            mContentRect.offsetTo(x, y);
        }

        public int getWidth() {
            return mContentRect.width();
        }

        public int getHeight() {
            return mContentRect.height();
        }

        public boolean pressed(int x, int y) {
            return mContentRect.contains(x, y);
        }

        public void setPress(boolean focus) {
            this.mFocus = focus;
        }
    }

    @Override
    public void doDraw(MGLCanvas canvas, int width, int height) {
        draw(canvas, width / 2 - getMeasuredWidth() / 2,
                mViewHeight / 2 - getMeasuredHeight() / 2);
    }

    @Override
    public void doLayout(boolean changeSize, int left, int top, int right,
            int bottom) {
        MtkLog.i(TAG, "<onLayout> changeSize = " + changeSize + ", left = "
                + left + ", top = " + top + ", right = " + right
                + ", bottom = " + bottom + " MeasureSpec.UNSPECIFIED="
                + MeasureSpec.UNSPECIFIED);
        mViewWidth = right - left;
        mViewHeight = bottom - top;
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        layout(left, top, right, bottom);
    }

    @Override
    public Object getComponent() {
        return this;
    }

    @Override
    public void addComponent(Object obj) {
        // TODO Auto-generated method stub

    }

    @Override
    public void removeComponent(Object obj) {
        // TODO Auto-generated method stub

    }
}
