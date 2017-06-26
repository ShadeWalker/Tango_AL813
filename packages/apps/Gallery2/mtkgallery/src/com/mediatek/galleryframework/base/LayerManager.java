package com.mediatek.galleryframework.base;

import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewGroup;

import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MGLView;

public interface LayerManager {
    public void init(ViewGroup rootView, MGLView glRootView);
    public void resume();

    public void pause();

    public void destroy();

    public void switchLayer(Player player, MediaData data);

    public void drawLayer(MGLCanvas canvas, int width, int height);

    public boolean onTouch(MotionEvent event);

    public void onLayout(boolean changeSize, int left, int top, int right,
            int bottom);

    public void onKeyEvent(KeyEvent event);

    public boolean onCreateOptionsMenu(Menu menu);
    public boolean onPrepareOptionsMenu(Menu menu);
    public boolean onOptionsItemSelected(MenuItem item);

    // call back the caller(Gallery) to do sth.
    // keep in mind: run on UI thread
    public interface IBackwardContoller {
        public interface IOnActivityResultListener {
            public boolean onActivityResult(int requestCode, int resultCode, Intent data);
        }
        /**
         * toggle visibility of host(Gallery)'s ActionBar
         * @param visibility the visibility to arrive at
         * @param allowAutoHideByHost whether to allow host(Gallery) to auto hide ActionBar
         */
        public void toggleBars(boolean visibility);
        public void redirectCurrentMedia(Uri uri, boolean fromActivityResult);
        public void startActivityForResult(Intent intent, int requestCode,
                IOnActivityResultListener resultListener);
        public void notifyDataChange(MediaData mediaData);
    }

    public boolean onActionBarVisibilityChange(boolean newVisibility);

    public void onFilmModeChange(boolean isFilmMode);

    public void setBackwardController(
            LayerManager.IBackwardContoller backwardControllerForLayer);
}