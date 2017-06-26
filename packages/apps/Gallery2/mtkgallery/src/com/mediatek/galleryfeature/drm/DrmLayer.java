package com.mediatek.galleryfeature.drm;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.mediatek.drm.OmaDrmClient;
import com.mediatek.drm.OmaDrmStore;
import com.mediatek.drm.OmaDrmUiUtils;

import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.MediaCenter;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.gl.MGLView;
import com.mediatek.galleryframework.util.MtkLog;

import com.mediatek.galleryfeature.drm.DeviceMonitor.ConnectStatus;

public class DrmLayer extends Layer implements
        DeviceMonitor.DeviceConnectListener {
    private static final String TAG = "MtkGallery2/DrmLayer";

    private Activity mActivity;
    private boolean mIsFilmMode;
    private Menu mOptionsMenu;
    private MediaData mMediaData;
    private MediaCenter mMediaCenter;
    private boolean mIsFristTimeOnResume = true;
    private boolean mOnSingleTapUpAfterPrepared = false;

    private Layer mRealLayer;
    private Player mRealPlayer = null;
    private DrmPlayer mPlayer;

    // save consume status
    private int mSaveConsumeStatus;
    private MediaData mSaveDrmData;
    private boolean mNeedRestore = false;

    // another device plug
    private DeviceMonitor mDeviceMonitor;

    public DrmLayer(MediaCenter mc) {
        mMediaCenter = mc;
    }

    public void onCreate(Activity activity, ViewGroup root) {
        mActivity = activity;
        mDeviceMonitor = new DeviceMonitor(activity);
        mDeviceMonitor.setConnectListener(this);
    }

    public void onResume(boolean isFilmMode) {
        mIsFilmMode = isFilmMode;
        if (mIsFristTimeOnResume) {
            if (mPlayer != null && mPlayer.getState() == Player.State.PREPARED)
                onSingleTapUp(0, 0);
            else
                mOnSingleTapUpAfterPrepared = true;
            mIsFristTimeOnResume = false;
        }
        tryToRestoreConsumeStatus();
    }

    public void onPause() {
        unbindRealLayer();
    }

    public void onActivityResume() {
        if (mDeviceMonitor != null) {
            mDeviceMonitor.start();
        }
    }

    public void onActivityPause() {
        if (mDeviceMonitor != null) {
            mDeviceMonitor.stop();
        }
        saveConsumeStatus();
    }

    public void onDestroy() {
        mDeviceMonitor = null;
    }

    public void setData(MediaData data) {
        mMediaData = data;
        if (mMediaData == null)
            unbindRealLayer();
    }

    public void setPlayer(Player player) {
        mPlayer = (DrmPlayer) player;
        if (mPlayer == null)
            unbindRealLayer();
        onDeviceConnected(mDeviceMonitor.getConnectedStatus());
    }

    public void onDeviceConnected(ConnectStatus status) {
        if (mPlayer != null)
            mPlayer.setDrmDisplayLimit(status);
        if (status != ConnectStatus.DISCONNECTED) {
            bindRealLayer();
        } else {
            unbindRealLayer();
        }
    }

    public void onFilmModeChange(boolean isFilmMode) {
        if (isFilmMode == mIsFilmMode)
            return;
        mIsFilmMode = isFilmMode;
        if (mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_CONSUMED
                && mRealLayer != null)
            mRealLayer.onFilmModeChange(isFilmMode);
    }

    public View getView() {
        return null;
    }

    public MGLView getMGLView() {
        return null;
    }

    public boolean onSingleTapUp(float x, float y) {
        // if other device connected, do nothing
        if (mDeviceMonitor.getConnectedStatus() != ConnectStatus.DISCONNECTED)
            return false;
        // if has consumed, do nothing
        if (mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_CONSUMED) {
            if (mRealLayer != null)
                return mRealLayer.onSingleTapUp(x,y);
            return false;
        }
        // if not consume, show dialog
        return showDrmDialog();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        return true;
    }

    public void onChange(Player player, int message, int arg, Object data) {
        switch (message) {
        case DrmPlayer.MSG_PREPARED:
            if (mOnSingleTapUpAfterPrepared) {
                onSingleTapUp(0, 0);
                mOnSingleTapUpAfterPrepared = false;
            }
            break;
        case DrmPlayer.MSG_CONSUMED:
            bindRealLayer();
            break;
        default:
            throw new IllegalArgumentException(
                    "<onChange>, message not define, messge = " + message);
        }
    }

    public boolean onDoubleTap(float x, float y) {
        // if other device connected, no right to zoom in & zoom out
        if (mDeviceMonitor.getConnectedStatus() != ConnectStatus.DISCONNECTED) {
            return true;
        }
        if (mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_CONSUMED
                && mRealLayer != null) {
            return mRealLayer.onDoubleTap(x, y);
        }
        // not enable zoom in & zoom out for video
        if (mMediaData.isVideo)
            return true;
        return false;
    }

    public boolean onScroll(float dx, float dy, float totalX, float totalY) {
        // if other device connected, do nothing
        if (mDeviceMonitor.getConnectedStatus() != ConnectStatus.DISCONNECTED)
            return false;
        if (mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_CONSUMED
                && mRealLayer != null) {
            return mRealLayer.onScroll(dx, dy, totalX, totalY);
        }
        return false;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        // if other device connected, do nothing
        if (mDeviceMonitor.getConnectedStatus() != ConnectStatus.DISCONNECTED)
            return false;
        if (mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_CONSUMED
                && mRealLayer != null) {
            return mRealLayer.onFling(e1, e2, velocityX, velocityY);
        }
        return false;
    }

    public boolean onScaleBegin(float focusX, float focusY) {
        // if other device connected, no right to zoom in & zoom out
        if (mDeviceMonitor.getConnectedStatus() != ConnectStatus.DISCONNECTED) {
            return true;
        }
        if (mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_CONSUMED
                && mRealLayer != null) {
            return mRealLayer.onScaleBegin(focusX, focusY);
        }
        // not enable zoom in & zoom out for video
        if (mMediaData.isVideo)
            return true;
        return false;
    }

    public boolean onScale(float focusX, float focusY, float scale) {
        // if other device connected, no right to zoom in & zoom out
        if (mDeviceMonitor.getConnectedStatus() != ConnectStatus.DISCONNECTED) {
            return true;
        }
        if (mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_CONSUMED
                && mRealLayer != null) {
            return mRealLayer.onScale(focusX, focusY, scale);
        }
        // not enable zoom in & zoom out for video
        if (mMediaData.isVideo)
            return true;
        return false;
    }

    public void onScaleEnd() {
        // if other device connected, no right to zoom in & zoom out
        if (mDeviceMonitor.getConnectedStatus() != ConnectStatus.DISCONNECTED) {
            return;
        }
        if (mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_CONSUMED
                && mRealLayer != null) {
            mRealLayer.onScaleEnd();
        }
    }

    public void onDown(float x, float y) {
        // if other device connected, do nothing
        if (mDeviceMonitor.getConnectedStatus() != ConnectStatus.DISCONNECTED)
            return;
        if (mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_CONSUMED
                && mRealLayer != null) {
            mRealLayer.onDown(x, y);
        }
    }

    public void onUp() {
        // if other device connected, do nothing
        if (mDeviceMonitor.getConnectedStatus() != ConnectStatus.DISCONNECTED)
            return;
        if (mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_CONSUMED
                && mRealLayer != null) {
            mRealLayer.onUp();
        }
    }

    // if show dialog, return true, else return false
    private boolean showDrmDialog() {
        if (mMediaData.isVideo) {
            MtkLog.i(TAG, "<showDrmDialog> Current media is video, ignore");
            return false;
        }
        int rights = DrmHelper.checkRightsStatus(mActivity,
                mMediaData.filePath, OmaDrmStore.Action.DISPLAY);
        final OmaDrmClient drmManagerClient = DrmHelper
                .getOmaDrmClient(mActivity);

        if (OmaDrmStore.RightsStatus.RIGHTS_VALID == rights) {
            OmaDrmUiUtils.showConsumeDialog(mActivity,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (DialogInterface.BUTTON_POSITIVE == which) {
                                drmManagerClient.consumeRights(
                                        mMediaData.filePath,
                                        OmaDrmStore.Action.DISPLAY);
                                mPlayer.consumeRights();
                            }
                            dialog.dismiss();
                        }
                    }, new DialogInterface.OnDismissListener() {
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
        } else {
            if (OmaDrmStore.RightsStatus.SECURE_TIMER_INVALID == rights) {
                OmaDrmUiUtils.showSecureTimerInvalidDialog(mActivity,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                            }
                        }, new DialogInterface.OnDismissListener() {
                            public void onDismiss(DialogInterface dialog) {
                            }
                        });
            } else {
                OmaDrmUiUtils.showRefreshLicenseDialog(drmManagerClient,
                        mActivity, mMediaData.filePath);
            }
        }
        return true;
    }

    private void saveConsumeStatus() {
        if (mPlayer == null
                || mPlayer.getConsumeStatus() == DrmPlayer.RIGHTS_NOT_CONSUME) {
            MtkLog.i(TAG, "<saveConsumeStatus> fail, no need to save, return");
            return;
        }
        mSaveConsumeStatus = mPlayer.getConsumeStatus();
        mSaveDrmData = mMediaData;
        mNeedRestore = true;
        MtkLog.i(TAG, "<saveConsumeStatus> success");
    }

    private void restoreConsumeStatus() {
        boolean isTimeInterval = DrmHelper.isTimeIntervalMedia(mActivity,
                mMediaData);
        if (!isTimeInterval && DrmHelper.hasRightsToShow(mActivity, mMediaData)) {
            MtkLog.i(TAG, "<restoreConsumeStatus> success");
            mPlayer.consumeRightsAfterPrepared();
            return;
        }
        MtkLog.i(TAG, "<restoreConsumeStatus> fail, time limit, no right now");
    }

    private void tryToRestoreConsumeStatus() {
        MtkLog.i(TAG, "<tryToRestoreConsumeStatus> mNeedRestore = "
                + mNeedRestore);
        if (!mNeedRestore)
            return;
        mNeedRestore = false;
        if (mSaveDrmData != null && !mSaveDrmData.equals(mMediaData)
                || mPlayer == null) {
            MtkLog.i(TAG, "<tryToRestoreConsumeStatus> fail");
            return;
        }
        MtkLog.i(TAG, "<tryToRestoreConsumeStatus> success");
        restoreConsumeStatus();
        mSaveDrmData = null;
    }

    private void bindRealLayer() {
        // when mPlayer == null, return
        if (mPlayer == null) {
            MtkLog.i(TAG, "<bindRealLayer> mPlayer == null, return");
            return;
        }

        // get real layer and real player
        mRealPlayer = mPlayer.getRealPlayer();
        if (mRealPlayer == null) {
            MtkLog.i(TAG, "<bindRealLayer> mRealPlayer == null, return");
            return;
        }
        mRealLayer = mMediaCenter.getRealLayer(mMediaData);
        if (mRealLayer == null) {
            MtkLog.i(TAG, "<bindRealLayer> mRealLayer == null, return");
            return;
        }

        // bind
        mRealLayer.setPlayer(mRealPlayer);
        mRealLayer.setData(mMediaData);
        mRealPlayer.registerPlayListener(mRealLayer);
        mRealLayer.onPrepareOptionsMenu(mOptionsMenu);
        mRealLayer.onResume(mIsFilmMode);
    }

    private void unbindRealLayer() {
        if (mRealPlayer != null)
            mRealPlayer.unRegisterPlayListener(mRealLayer);
        if (mRealLayer != null) {
            mRealLayer.onPause();
            mRealLayer.setPlayer(null);
            mRealLayer.setData(null);
        }
        mRealPlayer = null;
        mRealLayer = null;
    }
}
