package com.mediatek.galleryfeature.livephoto;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.mediatek.galleryframework.gl.MGLView;
import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.util.MtkLog;

import com.mediatek.galleryfeature.SlideVideo.SlideVideoPlayer;

public class LivePhotoLayer extends Layer {
    private final static String TAG = "MtkGallery2/LivePhotoLayer";
    private boolean mIsInFilmMode = false;
    private SlideVideoPlayer mPlayer;
    private Activity mActivity;

    @Override
    public void onCreate(Activity activity, ViewGroup root) {
        mActivity = activity;
    }

    @Override
    public void onResume(boolean isFilmMode) {
        MtkLog.i(TAG, "<onResume> isFilmMode " + isFilmMode);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        onFilmModeChange(isFilmMode);
    }

    @Override
    public void onPause() {
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public void setData(MediaData data) {
    }

    @Override
    public void setPlayer(Player player) {
        mPlayer = (SlideVideoPlayer) player;
        onFilmModeChange(mIsInFilmMode);
    }

    @Override
    public View getView() {
        return null;
    }

    @Override
    public MGLView getMGLView() {
        return null;
    }

    @Override
    public void onChange(Player player, int message, int arg, Object data) {
    }

    @Override
    public void onFilmModeChange(boolean isFilmMode) {
        MtkLog.i(TAG, "onFilmModeChange isFilmMode = " + isFilmMode);
        mIsInFilmMode = isFilmMode;
        if (mPlayer != null) {
            mPlayer.onFilmModeChange(isFilmMode);
        }
    }
}