package com.mediatek.galleryfeature.mav;

import android.app.Activity;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.android.gallery3d.R;
import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.gl.MGLView;
import com.mediatek.galleryfeature.mav.MavPlayer.MavListener;
import com.mediatek.galleryfeature.platform.PlatformHelper;
import com.mediatek.galleryframework.util.MtkLog;

public class MavLayer extends Layer {
    private final static String TAG = "MtkGallery2/MavLayer";
    private RelativeLayout mRootLayout;
    private SeekBar mMavSeekBar;
    private MavPlayer mPlayer;
    private MavListener mMavListener;
    private Handler mHandler;
    private IconView mMavIconView;
    private boolean mInDown;
    private boolean mIsFilmMode;

    @Override
    public View getView() {
        return mRootLayout;
    }

    @Override
    public boolean onDoubleTap(float x, float y) {
        return true;
    }

    public boolean onScaleBegin(float focusX, float focusY) {
        return true;
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

    private void updateVisibility() {
        if (null != mMavSeekBar) {
            if (mIsFilmMode) {
                mMavSeekBar.setVisibility(View.INVISIBLE);
            } else if (mInDown) {
                mMavSeekBar.setVisibility(View.INVISIBLE);
            } else {
                mMavSeekBar.setVisibility(View.VISIBLE);
            }
        }
    }
    
    @Override
    public void onCreate(Activity activity, ViewGroup root) {
        MtkLog.d(TAG, " <onCreate>");
        LayoutInflater flater = LayoutInflater.from(activity);
        mRootLayout = (RelativeLayout) flater.inflate(R.layout.m_mav_seekbar,
                root, false);
        mMavIconView = PlatformHelper.createMavIconView(activity);
        if (mMavIconView != null) {
            mMavIconView.setVisibility(true);
        }
        mMavSeekBar = (SeekBar) mRootLayout.findViewById(R.id.m_seekbar_mav);
        mMavSeekBar.setVisibility(View.GONE);
        if (null != mMavSeekBar) {
            mMavSeekBar
                    .setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

                        public void onProgressChanged(SeekBar seekBar,
                                int progress, boolean fromUser) {
                            MtkLog.d(TAG, "<onCreate>fromUser=" + fromUser
                                    + " mPlayer=" + mPlayer
                                    + " mPlayer.mAnimation="
                                    + mPlayer.mAnimation);
                            if (fromUser && mPlayer != null
                                    && mPlayer.mAnimation != null) {
                                mPlayer.initAnimation(progress,
                                        AnimationEx.TYPE_ANIMATION_INTERVAL);
                                MavPlayer.sRenderThreadEx
                                        .setRenderRequester(true);
                            }
                        }

                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });
        }
    }

    @Override
    public void onDestroy() {
        mRootLayout = null;
        mMavSeekBar = null;
    }

    @Override
    public void onPause() {
        if (null != mMavSeekBar) {
            mMavSeekBar.setProgress(MavSeekBar.INVALID_PROCESS);
            mMavSeekBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume(boolean isFilmMode) {
        mInDown = false;
        mIsFilmMode = isFilmMode;
        onFilmModeChange(isFilmMode);
    }

    @Override
    public void setData(MediaData data) {

    }

    @Override
    public void setPlayer(Player player) {
        MtkLog.d(TAG, "<setPlayer>  player=" + player);
        if (player == null) {
            mPlayer = null;
        } else if (player instanceof MavPlayer) {
            mPlayer = (MavPlayer) player;
        } else {
            throw new IllegalArgumentException("<setPlayer>, wrong Player type");
        }
    }

    public void onChange(Player player, int what, int arg, Object obj) {
        switch (what) {
        case MavPlayer.MSG_START:
            if (null != mMavSeekBar && null != mMavIconView) {
                mMavIconView.setVisibility(true);
            }
            break;
        case MavPlayer.MSG_STOP:
            if (null != mMavSeekBar && null != mMavIconView) {
                mMavSeekBar.setVisibility(View.GONE);
                mMavIconView.setVisibility(false);
            }
            break;
        case MavPlayer.MSG_UPDATE_MAV_PROGRESS:
            MtkLog.d(TAG, "<onChange>  MSG_UPDATE_MAV_PROGRESS  arg=" + arg);
            if (null != mMavSeekBar && arg > 0) {
                mMavSeekBar.setProgress(arg);
            }
            break;
        case MavPlayer.MSG_UPDATE_MAV_SET_STATUS: {
            MtkLog.d(TAG, " <onChange> MSG_UPDATE_MAV_SET_STATUS setEnabled="
                    + Boolean.parseBoolean(obj.toString()));
            if (null != mMavSeekBar && null != mMavIconView) {
                mMavSeekBar.setEnabled(Boolean.parseBoolean(obj
                        .toString()));
            }
            break;
        }
        case MavPlayer.MSG_UPDATE_MAV_SET_SEEKBAR: {
            MtkLog.d(TAG, " <onChange> MSG_UPDATE_MAV_SET_SEEKBAR max=" + what
                    + "   progress=" + arg);
            if (null != mMavSeekBar && null != mMavIconView) {
                mMavSeekBar.setEnabled(true);
                mMavSeekBar.setMax(arg);
                boolean isFinishedLoading = Boolean.parseBoolean(obj.toString());
                mMavSeekBar.setProgress(isFinishedLoading ? arg / 2 : -1);
                if (isFinishedLoading) mMavIconView.setVisibility(false);
            }
            break;
        }
        default:
            throw new IllegalArgumentException(
                    "<onChange>, message not define, messge = " + what);
        }
    }

    @Override
    public MGLView getMGLView() {
        return mMavIconView;
    }
    
    @Override
    public void onFilmModeChange(boolean isFilmMode) {
        mIsFilmMode = isFilmMode;
        updateVisibility();
    }
}
