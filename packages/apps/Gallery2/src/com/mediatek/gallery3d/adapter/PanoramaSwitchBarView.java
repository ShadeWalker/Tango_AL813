package com.mediatek.gallery3d.adapter;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources.Theme;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View.MeasureSpec;

import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.R;
import com.mediatek.galleryfeature.panorama.SwitchBarView;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MResourceTexture;
import com.mediatek.galleryframework.gl.MTexture;
import com.mediatek.galleryframework.util.Utils;
import com.mediatek.galleryframework.util.MtkLog;

public class PanoramaSwitchBarView extends GLView implements SwitchBarView {
    private static final String TAG = "MtkGallery2/PanoramaSwitchBarView";
    private static final int GRAY = 0xFFAAAAAA;
    public static final int SWITCH_BUTTON_LENG = 2;
    public static final int SWITCH_BUTTON_GAP_HORIZONTAL = (int) Utils.dpToPixel(10);
    public static final int SWITCH_BUTTON_GAP_VERTICAL = (int) Utils.dpToPixel(1);
    public static final int INVILED_BUTTON = 0;

    private int mSwitchBarTopGap;
    private SwitchButton mSwitchButtons[];
    private int mLength;
    private int mContentWidth;
    private int mContentHight;
    private int mMeasureWidth;
    private int mMeasureHight;
    private int mViewWidth;
    private int mViewHeight;
    private int mFocusButtion;
    private SwitchBarView.OnClickListener mOnClickListener;
    private boolean mEnable;
    private Context mContext;

    public PanoramaSwitchBarView(Activity activity) {
        mContext = activity;

        // initialize top gap
        ActionBar actionbar = activity.getActionBar();
        if (actionbar != null && actionbar.getHeight() != 0) {
            mSwitchBarTopGap = actionbar.getHeight();
            MtkLog.i(TAG, "<new> from actionbar, mSwitchBarTopGap = " + mSwitchBarTopGap);
        } else {
            TypedValue tv = new TypedValue();
            Theme theme = activity.getTheme();
            if (theme != null && theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                mSwitchBarTopGap = TypedValue.complexToDimensionPixelSize(tv.data, activity
                        .getResources().getDisplayMetrics());
                MtkLog.i(TAG, "<new> from R.attr.actionBarSize, mSwitchBarTopGap = "
                        + mSwitchBarTopGap);
            } else {
                DisplayMetrics metrics = new DisplayMetrics();
                WindowManager wm = (WindowManager) activity
                        .getSystemService(Context.WINDOW_SERVICE);
                wm.getDefaultDisplay().getMetrics(metrics);
                // set heightPixels/10 as default gap
                mSwitchBarTopGap = metrics.heightPixels / 10;
                MtkLog.i(TAG, "<new> from DisplayMetrics.heightPixels, mSwitchBarTopGap = "
                        + mSwitchBarTopGap);
            }
        }

        mSwitchButtons = new SwitchButton[SWITCH_BUTTON_LENG];
        mEnable = true;
        addSwitchButton(new SwitchButton(SwitchBarView.BUTTON_NORMAL, mContext,
                R.drawable.m_panorama_pressed, R.drawable.m_panorama_normal,
                SWITCH_BUTTON_GAP_HORIZONTAL, SWITCH_BUTTON_GAP_VERTICAL));
        addSwitchButton(new SwitchButton(SwitchBarView.BUTTON_3D, mContext,
                R.drawable.m_panorama_3d_pressed, R.drawable.m_panorama_3d_normal,
                SWITCH_BUTTON_GAP_HORIZONTAL, SWITCH_BUTTON_GAP_VERTICAL));
        adjustButtonsPosition();
    }

    public void addSwitchButton(SwitchButton button) {
        mSwitchButtons[mLength] = button;
        mMeasureWidth = mMeasureWidth + button.getWidth();
        mMeasureHight = Math.max(mMeasureHight, button.getHeight());
        mLength++;
    }

    public void adjustButtonsPosition() {
        int begin = 0;
        mContentWidth = 0;
        for (int i = 0; i < mLength; i++) {
            if (mSwitchButtons[i].mVisible) {
                mContentWidth = mContentWidth + mSwitchButtons[i].getWidth();
            }
        }
        begin = (mMeasureWidth - mContentWidth) / 2;
        for (int i = 0; i < mLength; i++) {
            if (mSwitchButtons[i].mVisible) {
                mSwitchButtons[i].setPosition(begin, 0);
                begin = begin + mSwitchButtons[i].getWidth();
                mContentHight = mSwitchButtons[i].getHeight();
            }
        }
    }

    public void setOnClickListener(SwitchBarView.OnClickListener listener) {
        mOnClickListener = listener;
    }

    @Override
    protected void render(GLCanvas canvas) {
        if (!mEnable) return;
        draw(canvas.getMGLCanvas());
    }

    public void draw(MGLCanvas canvas, int x, int y) {
        for (int i = 0; i < mLength; i++) {
            mSwitchButtons[i].draw(canvas, x, y);
        }
    }

    public void draw(MGLCanvas canvas) {
        draw(canvas, 0, 0);
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredSize(mMeasureWidth, mMeasureHight);
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        MtkLog.d(TAG, "<onTouch> x:" + x);
        MtkLog.d(TAG, "<onTouch> y:" + y);
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mFocusButtion = pressDown((int) x, (int) y);
            break;
        case MotionEvent.ACTION_UP:
            if (mFocusButtion > INVILED_BUTTON && pressDown((int) x, (int) y) > INVILED_BUTTON) {
                setFocusButton(mFocusButtion, true);
                invalidate();
            }
            mFocusButtion = INVILED_BUTTON;
            break;
        case MotionEvent.ACTION_CANCEL:
            mFocusButtion = INVILED_BUTTON;
            break;
        default:
            break;
        }
        return true;
    }

    public void setFocusButton(int button, boolean fromUser) {
        if (button == INVILED_BUTTON)
            return;

        for (int i = 0; i < mLength; i++) {
            if (mSwitchButtons[i].mName == button) {
                mSwitchButtons[i].setPress(true);
            } else {
                mSwitchButtons[i].setPress(false);
            }
        }

        if (mOnClickListener != null && fromUser)
            mOnClickListener.onClick();
    }

    public void setVisibility(boolean visible) {
        super.setVisibility(visible ? GLView.VISIBLE : GLView.INVISIBLE);
    }

    public boolean isVisible() {
        return super.getVisibility() == GLView.VISIBLE ? true : false;
    }
    public void setEnable(boolean enable) {
        mEnable = enable;
    }

    public void setButtonVisible(int button, boolean visible) {
        SwitchButton b = getButton(button);
        if (b != null) {
            b.setVisible(visible);
            adjustButtonsPosition();
        }
    }

    private SwitchButton getButton(int button) {
        for (int i = 0; i < mLength; i++) {
            if (mSwitchButtons[i].mName == button) {
                return mSwitchButtons[i];
            }
        }
        return null;
    }

    public int getFocusButton() {
        return mFocusButtion;
    }

    private int pressDown(int x, int y) {
        for (int i = 0; i < mLength; i++) {
            if (mSwitchButtons[i].pressed(x, y)) {
                return mSwitchButtons[i].mName;
            }
        }
        return -1;
    }

    @Override
    public void onDetachFromRoot() {
        super.onDetachFromRoot();
        for (int i = 0; i < mSwitchButtons.length; i++)
            mSwitchButtons[i].releaseTexture();
    }

    private class SwitchButton {
        public boolean mVisible;
        public boolean mFocus;
        private int mFocusResID;
        private int mNormalResID;
        private int mGapV;
        private int mGapH;
        public int mName;
        private Rect mContentRect;
        private MResourceTexture mFocusTexture;
        private MResourceTexture mNormalTexture;
        private MResourceTexture mCurrTexture;

        public SwitchButton(int name, Context context, int focusResID, int normalResID, int gapH, int gapV) {
            mName = name;
            mFocusResID = focusResID;
            mNormalResID = normalResID;
            mGapV = gapV;
            mGapH = gapH;
            mFocusTexture = new MResourceTexture(context, focusResID);
            mNormalTexture = new MResourceTexture(context, normalResID);
            mVisible = true;
            mContentRect = new Rect(0, 0, mFocusTexture.getWidth() + mGapH * 2, mFocusTexture.getHeight() + mGapV * 2);
        }

        public void releaseTexture() {
            if (mFocusTexture != null) {
                mFocusTexture.recycle();
                mFocusTexture = null;
            }
            if (mNormalTexture != null) {
                mNormalTexture.recycle();
                mNormalTexture = null;
            }
        }

        public void draw(MGLCanvas canvas, int x, int y) {
            if (!mVisible)
                return;
            if (mFocus) {
                if (mFocusTexture == null)
                    mFocusTexture = new MResourceTexture(mContext, mFocusResID);
                mCurrTexture = mFocusTexture;
            } else {
                if (mNormalTexture == null)
                    mNormalTexture = new MResourceTexture(mContext, mNormalResID);
                mCurrTexture = mNormalTexture;
            }
            mCurrTexture.draw(canvas, mContentRect.left + mGapH + x, mContentRect.top + mGapV + y);
        }

        public void drawSplit(MGLCanvas canvas, MTexture texture, int x, int y) {
            if (texture == null) return;
            int h = mContentRect.height();
            texture.draw(canvas, mContentRect.right + x, mContentRect.top + y + h / 4, 1, h / 2);
        }

        public boolean isNeedSplit(int left, int right) {
            if (mContentRect.right >= right) return false;
            else return true;
        }

        public void draw(MGLCanvas canvas) {
            draw(canvas, 0, 0);
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
        if (!mEnable || !isVisible()) return;
        draw(canvas, width / 2 - getMeasuredWidth() / 2, mSwitchBarTopGap);
    }

    @Override
    public void doLayout(boolean changeSize, int left, int top, int right,
            int bottom) {
        MtkLog.i(TAG, "<onLayout> changeSize = " + changeSize + ", left = "
                + left + ", top = " + top + ", right = " + right
                + ", bottom = " + bottom);
        mViewWidth = right - left;
        mViewHeight = bottom - top;
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int leftGap = (right - left - getMeasuredWidth()) / 2;
        layout(left + leftGap, mSwitchBarTopGap, right - leftGap,
                getMeasuredHeight() + mSwitchBarTopGap);
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