package com.mediatek.galleryframework.base;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.mediatek.galleryframework.base.Player.PlayListener;
import com.mediatek.galleryframework.gl.MGLView;

public abstract class Layer implements PlayListener {
    public abstract void onCreate(Activity activity, ViewGroup root);

    /**
     * The call back of resume layer, it will be called in UI thread
     * @param isFilmMode If current mode is film mode
     */
    public abstract void onResume(boolean isFilmMode);

    public abstract void onPause();

    public abstract void onDestroy();

    public abstract void setData(MediaData data);

    public abstract void setPlayer(Player player);

    public abstract View getView();

    public abstract MGLView getMGLView();

    // activity life cycle call back
    public void onActivityResume() {
    }

    public void onActivityPause() {
    }

    // gesture related callback start
    public boolean onSingleTapUp(float x, float y) {
        return false;
    }

    public boolean onDoubleTap(float x, float y) {
        return false;
    }

    public boolean onScroll(float dx, float dy, float totalX, float totalY) {
        return false;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    public boolean onScaleBegin(float focusX, float focusY) {
        return false;
    }

    public boolean onScale(float focusX, float focusY, float scale) {
        return false;
    }

    public void onScaleEnd() {
    }

    public void onDown(float x, float y) {
    }

    public void onUp() {
    }
    // gesture related callback end

    // menu related callback start
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        return true;
    }
    // menu related callback end

    // key event related callback start
    public void onKeyEvent(KeyEvent event) {
    }
    // key event related callback end

    /**
     * called when the action bar changes its visibility
     * @param newVisibility new action bar visibility
     * @return whether the action bar's visibility should be controlled by Layer
     */
    public boolean onActionBarVisibilityChange(boolean newVisibility) {
        return false;
    }

    public void setBackwardController(
            LayerManager.IBackwardContoller backwardControllerForLayer) {
        mBackwardContoller = backwardControllerForLayer;
    }

    protected LayerManager.IBackwardContoller mBackwardContoller;

    /**
     * Called when film mode has been changed
     * @param isFilmMode new film mode status
     */
    public void onFilmModeChange(boolean isFilmMode) {
    }
}
