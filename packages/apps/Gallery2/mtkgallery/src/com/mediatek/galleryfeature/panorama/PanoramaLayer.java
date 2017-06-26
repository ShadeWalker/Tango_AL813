package com.mediatek.galleryfeature.panorama;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.android.gallery3d.R;
import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryfeature.platform.PlatformHelper;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.gl.MGLView;

public class PanoramaLayer extends Layer {
    private static final String TAG = "MtkGallery2/PanoramaLayer";

    private PanoramaPlayer mPlayer;
    private SwitchBarView mPanoramaSwitchBar;
    private SeekBar mPanoramaSeekBar;
    private RelativeLayout mRootLayout;

    // variable about visibility
    private boolean mInDown;
    private boolean mIsFilmMode;
    private int mLatestMessage;

    @Override
    public void onCreate(Activity activity, ViewGroup root) {
        MtkLog.i(TAG, "<onCreate>");
        LayoutInflater flater = LayoutInflater.from(activity);
        mRootLayout = (RelativeLayout) flater.inflate(
                R.layout.m_panorama_seekbar, root, false);
        mPanoramaSeekBar = (SeekBar) (mRootLayout
                .findViewById(R.id.m_seekbar_panorama));
        mPanoramaSwitchBar = PlatformHelper
                .createPanoramaSwitchBarView(activity);
        assert(mPanoramaSeekBar != null);
        assert(mPanoramaSwitchBar != null);

        mPanoramaSwitchBar.setVisibility(false);
        mPanoramaSwitchBar.setOnClickListener(new SwitchBarView.OnClickListener() {
                    @Override
                    public void onClick() {
                        switch (mPanoramaSwitchBar.getFocusButton()) {
                        case SwitchBarView.BUTTON_NORMAL:
                            if (mPlayer != null)
                                mPlayer.switchMode(PanoramaPlayer.PANORAMA_MODE_NORMAL, true);
                            break;
                        case SwitchBarView.BUTTON_3D:
                            if (mPlayer != null)
                                mPlayer.switchMode(PanoramaPlayer.PANORAMA_MODE_3D, true);
                            break;
                        default:
                            break;
                        }
                    }
                });
        mPanoramaSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar arg0) {
                if (mPlayer != null)
                    mPlayer.startPlayback();
            }

            @Override
            public void onStartTrackingTouch(SeekBar arg0) {
                if (mPlayer != null)
                    mPlayer.stopPlayback();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mPlayer != null) {
                    mPlayer.setCurrentFrame(progress);
                }
            }
        });
    }

    @Override
    public void onResume(boolean isFilmMode) {
        MtkLog.i(TAG, "<onResume>");
        mIsFilmMode = isFilmMode;
        mInDown = false;
        mLatestMessage = -1;
    }

    @Override
    public void onPause() {
        MtkLog.i(TAG, "<onPause>");
        mPanoramaSwitchBar.setVisibility(false);
        mPanoramaSeekBar.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        MtkLog.i(TAG, "<onDestroy>");
        mPanoramaSwitchBar = null;
        mPanoramaSeekBar = null;
        mRootLayout = null;
        mPlayer = null;
    }

    @Override
    public void setData(MediaData data) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setPlayer(Player player) {
        if (player == null) {
            mPlayer = null;
        } else if (player == null || player instanceof PanoramaPlayer) {
            mPlayer = (PanoramaPlayer) player;
        } else {
            throw new IllegalArgumentException("<setPlayer>, wrong Player type");
        }
    }

    @Override
    public void onFilmModeChange(boolean isFilmMode) {
        if (isFilmMode == mIsFilmMode)
            return;
        mIsFilmMode = isFilmMode;
        updateVisibility();
    }

    public void onChange(Player player, int message, int arg, Object data) {
        mLatestMessage = message;
        switch (message) {
        case PanoramaPlayer.MSG_UPDATE_CURRENT_FRAME:
            mPanoramaSeekBar.setProgress(arg);
            MtkLog.i(TAG, "<onChange> mPanoramaSeekBar.setProgress(" + arg + "), player = " + player);
            break;
        case PanoramaPlayer.MSG_START:
            MtkLog.i(TAG, "<onChange> MSG_START");
            updateVisibility();
            mPanoramaSwitchBar.setFocusButton(SwitchBarView.BUTTON_3D, false);
            MtkLog.i(TAG, "<onChange> mPanoramaSeekBar.setMax(" + arg + ")");
            mPanoramaSeekBar.setMax(arg);
            break;
        case PanoramaPlayer.MSG_STOP:
            MtkLog.i(TAG, "<onChange> MSG_STOP");
            updateVisibility();
            mPanoramaSeekBar.setProgress(0);
            break;
        case PanoramaPlayer.MSG_MODE_NORMAL:
            MtkLog.i(TAG, "<onChange> MSG_MODE_NORMAL");
            updateVisibility();
            break;
        case PanoramaPlayer.MSG_MODE_3D:
            MtkLog.i(TAG, "<onChange> MSG_MODE_3D");
            updateVisibility();
            break;
        default:
            throw new IllegalArgumentException(
                    "<onChange>, message not define, messge = " + message);
        }
    }

    @Override
    public View getView() {
        return mRootLayout;
    }

    @Override
    public MGLView getMGLView() {
        return mPanoramaSwitchBar;
    }

    @Override
    public boolean onDoubleTap(float x, float y) {
        return isIgnoreGestureScale();
    }

    @Override
    public boolean onScaleBegin(float focusX, float focusY) {
        return isIgnoreGestureScale();
    }

    @Override
    public boolean onScale(float focusX, float focusY, float scale) {
        return isIgnoreGestureScale();
    }

    @Override
    public void onDown(float x, float y) {
        mInDown = true;
    }

    @Override
    public void onUp() {
        mInDown = false;
        updateVisibility();
    }

    @Override
    public boolean onScroll(float dx, float dy, float totalX, float totalY) {
        updateVisibility();
        return false;
    }

    @Override
    public boolean onSingleTapUp(float x, float y) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        updateVisibility();
        return false;
    }

    private void updateVisibility() {
        if (mIsFilmMode) {
            mPanoramaSeekBar.setVisibility(View.INVISIBLE);
            mPanoramaSwitchBar.setVisibility(false);
        } else if (mInDown) {
            mPanoramaSeekBar.setVisibility(View.INVISIBLE);
            mPanoramaSwitchBar.setVisibility(true);
        } else if (mLatestMessage == PanoramaPlayer.MSG_START
                || mLatestMessage == PanoramaPlayer.MSG_UPDATE_CURRENT_FRAME) {
            mPanoramaSeekBar.setVisibility(View.VISIBLE);
            mPanoramaSwitchBar.setVisibility(true);
        } else if (mLatestMessage == PanoramaPlayer.MSG_STOP) {
            mPanoramaSeekBar.setVisibility(View.INVISIBLE);
            mPanoramaSwitchBar.setVisibility(false);
        } else if (mLatestMessage == PanoramaPlayer.MSG_MODE_NORMAL) {
            mPanoramaSeekBar.setVisibility(View.INVISIBLE);
            mPanoramaSwitchBar.setVisibility(true);
        } else if (mLatestMessage == PanoramaPlayer.MSG_MODE_3D) {
            mPanoramaSeekBar.setVisibility(View.VISIBLE);
            mPanoramaSwitchBar.setVisibility(true);
        }
    }

    private boolean isIgnoreGestureScale() {
        if (mPlayer != null
                && mPlayer.getCurrentMode() == PanoramaPlayer.PANORAMA_MODE_3D) {
            return true;
        } else {
            return false;
        }
    }
}