package com.mediatek.galleryfeature.dynamic;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewGroup;

import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.LayerManager;
import com.mediatek.galleryframework.base.MediaCenter;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.MediaData.MediaType;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MGLView;
import com.mediatek.galleryframework.util.MtkLog;

public class LayerManagerImpl implements LayerManager {
    private static final String TAG = "MtkGallery2/LayerManagerImpl";

    private final LinkedHashMap<MediaType, Layer> mLayers;
    private Player mCurrentPlayer;
    private Layer mCurrentLayer;
    private ViewGroup mRootView;
    private MGLView mGLRootView;
    private PlayGestureRecognizer mGesureRecognizer;
    private Activity mActivity;
    private Menu mOptionsMenu;
    private boolean mIsFilmMode;

    public LayerManagerImpl(Activity activity, MediaCenter center) {
        mLayers = center.getAllLayer();
        mActivity = activity;
        mGesureRecognizer = new PlayGestureRecognizer(activity
                .getApplicationContext(), new GestureListener());
    }

    @Override
    public boolean onTouch(MotionEvent event) {
        return mGesureRecognizer.onTouch(event);
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (mCurrentLayer != null) {
            mCurrentLayer.onKeyEvent(event);
        }
    }

    @Override
    public void onFilmModeChange(boolean isFilmMode) {
        if (isFilmMode == mIsFilmMode)
            return;
        mIsFilmMode = isFilmMode;
        if (mCurrentLayer != null) {
            mCurrentLayer.onFilmModeChange(mIsFilmMode);
        }
    }

    @Override
    public void init(ViewGroup rootView, MGLView glRootView) {
        mRootView = rootView;
        mGLRootView = glRootView;
        Iterator<Entry<MediaType, Layer>> itr = mLayers.entrySet().iterator();
        while (itr.hasNext()) {
            Layer layer = itr.next().getValue();
            if (layer != null) {
                layer.onCreate(mActivity, mRootView);
            }
        }
    }

    @Override
    public void resume() {
        Iterator<Entry<MediaType, Layer>> itr = mLayers.entrySet().iterator();
        while (itr.hasNext()) {
            Layer layer = itr.next().getValue();
            if (layer != null) {
                if (mRootView != null && layer.getView() != null) {
                    mRootView.addView(layer.getView());
                }
                if (mGLRootView != null && layer.getMGLView() != null) {
                    mGLRootView.addComponent(layer.getMGLView());
                }
                layer.onActivityResume();
            }
        }
    }

    @Override
    public void pause() {
        Iterator<Entry<MediaType, Layer>> itr = mLayers.entrySet().iterator();
        while (itr.hasNext()) {
            Layer layer = itr.next().getValue();
            if (layer != null) {
                layer.onActivityPause();
            }
        }
        unbind();
        itr = mLayers.entrySet().iterator();
        while (itr.hasNext()) {
            Layer layer = itr.next().getValue();
            if (layer != null) {
                if (mRootView != null && layer.getView() != null) {
                    mRootView.removeView(layer.getView());
                }
                if (mGLRootView != null && layer.getMGLView() != null) {
                    mGLRootView.removeComponent(layer.getMGLView());
                }
            }
        }
    }

    @Override
    public void destroy() {
        Iterator<Entry<MediaType, Layer>> itr = mLayers.entrySet().iterator();
        while (itr.hasNext()) {
            Layer layer = itr.next().getValue();
            if (layer != null) {
                layer.onDestroy();
            }
        }
    }

    @Override
    public void switchLayer(Player player, MediaData data) {
        Layer layer = null;
        if (data != null) {
            layer = mLayers.get(data.mediaType);
            if (mCurrentPlayer == player && mCurrentLayer == layer) {
                MtkLog.i(TAG, "<switchLayer> same layer and player, return");
                return;
            }
        }
        unbind();
        if (player == null || data == null) {
            MtkLog.i(TAG, "<switchLayer> null player or data, return");
            return;
        }
        if (layer != null) {
            bind(player, layer, data);
        }
    }

    @Override
    public void drawLayer(MGLCanvas canvas, int width, int height) {
        if (mCurrentLayer != null && mCurrentLayer.getMGLView() != null)
            mCurrentLayer.getMGLView().doDraw(canvas, width, height);
    }

    @Override
    public void onLayout(boolean changeSize, int left, int top, int right,
            int bottom) {
        Iterator<Entry<MediaType, Layer>> itr = mLayers.entrySet().iterator();
        while (itr.hasNext()) {
            Layer layer = itr.next().getValue();
            if (layer != null && layer.getMGLView() != null) {
                layer.getMGLView().doLayout(changeSize, left, top, right,
                        bottom);
            }
        }
    }

    private void bind(Player player, Layer layer, MediaData data) {
        assert (player != null && layer != null && data != null);
        mCurrentLayer = layer;
        mCurrentPlayer = player;
        mCurrentLayer.setPlayer(player);
        mCurrentLayer.setData(data);
        mCurrentPlayer.registerPlayListener(mCurrentLayer);
        mCurrentLayer.onPrepareOptionsMenu(mOptionsMenu);
        mCurrentLayer.onResume(mIsFilmMode);
    }

    private void unbind() {
        assert (!(mCurrentLayer == null ^ mCurrentPlayer == null));
        if (mCurrentLayer == null)
            return;
        mCurrentPlayer.unRegisterPlayListener(mCurrentLayer);
        mCurrentLayer.onPause();
        mCurrentLayer.setPlayer(null);
        mCurrentLayer.setData(null);
        mCurrentPlayer = null;
        mCurrentLayer = null;
    }

    private class GestureListener implements PlayGestureRecognizer.Listener {
        public boolean onSingleTapUp(float x, float y) {
            if (mCurrentLayer != null) {
                return mCurrentLayer.onSingleTapUp(x, y);
            }
            return false;
        }

        public boolean onDoubleTap(float x, float y) {
            if (mCurrentLayer != null)
                return mCurrentLayer.onDoubleTap(x, y);
            return false;
        }

        public boolean onScroll(float dx, float dy, float totalX, float totalY) {
            if (mCurrentLayer != null) {
                return mCurrentLayer.onScroll(dx, dy, totalX, totalY);
            }
            return false;
        }

        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {
            if (mCurrentLayer != null) {
                return mCurrentLayer.onFling(e1, e2, velocityX, velocityY);
            }
            return false;
        }

        public boolean onScaleBegin(float focusX, float focusY) {
            if (mCurrentLayer != null)
                return mCurrentLayer.onScaleBegin(focusX, focusY);
            return false;
        }

        public boolean onScale(float focusX, float focusY, float scale) {
            if (mCurrentLayer != null) {
                return mCurrentLayer.onScale(focusX, focusY, scale);
            }
            return false;
        }

        public void onScaleEnd() {
            if (mCurrentLayer != null) {
                mCurrentLayer.onScaleEnd();
            }
        }

        public void onDown(float x, float y) {
            if (mCurrentLayer != null) {
                mCurrentLayer.onDown(x, y);
            }
        }

        public void onUp() {
            if (mCurrentLayer != null) {
                mCurrentLayer.onUp();
            }
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        Iterator<Entry<MediaType, Layer>> itr = mLayers.entrySet().iterator();
        Layer layer;
        while (itr.hasNext()) {
            layer = itr.next().getValue();
            if (layer != null) {
                layer.onCreateOptionsMenu(menu);
            }
        }
        mOptionsMenu = menu;
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mCurrentLayer != null) {
            mCurrentLayer.onPrepareOptionsMenu(menu);
        }
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (mCurrentLayer != null) {
            mCurrentLayer.onOptionsItemSelected(item);
        }
        return true;
    }

    public boolean onActionBarVisibilityChange(boolean newVisibility) {
        boolean shouldBarControlledByLayerManager = false;
        if (mCurrentLayer != null) {
            shouldBarControlledByLayerManager = mCurrentLayer
                    .onActionBarVisibilityChange(newVisibility);
        }
        return shouldBarControlledByLayerManager;
    }

    public void setBackwardController(
            LayerManager.IBackwardContoller backwardControllerForLayer) {
        Iterator<Entry<MediaType, Layer>> itr = mLayers.entrySet().iterator();
        while (itr.hasNext()) {
            Layer layer = itr.next().getValue();
            if (layer != null) {
                layer.setBackwardController(backwardControllerForLayer);
            }
        }
    }
}
