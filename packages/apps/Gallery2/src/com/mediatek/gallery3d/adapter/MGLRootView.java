package com.mediatek.gallery3d.adapter;

import com.android.gallery3d.ui.GLView;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MGLView;

public class MGLRootView implements MGLView {

    GLView mRootView;

    public MGLRootView(GLView view) {
        mRootView = view;
    }

    @Override
    public void doDraw(MGLCanvas canvas, int width, int height) {
        // TODO Auto-generated method stub

    }

    @Override
    public void doLayout(boolean changeSize, int left, int top, int right,
            int bottom) {
        // TODO Auto-generated method stub

    }

    @Override
    public Object getComponent() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void addComponent(Object obj) {
        if (obj instanceof MGLView) {
            mRootView.addComponent((GLView) ((MGLView) obj).getComponent());
        }
    }

    @Override
    public void removeComponent(Object obj) {
        if (obj instanceof MGLView) {
            mRootView.removeComponent((GLView) ((MGLView) obj).getComponent());
        }
    }

}
