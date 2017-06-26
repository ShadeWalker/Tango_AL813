package com.mediatek.galleryfeature.container;

import android.app.ActionBar;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.android.gallery3d.R;
import com.mediatek.galleryframework.base.Layer;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.Player;
import com.mediatek.galleryframework.gl.MGLView;
import com.mediatek.galleryfeature.platform.PlatformHelper;
import com.mediatek.galleryframework.util.MtkLog;

public class ContainerLayer extends Layer {

    private static final String TAG = "MtkGallery2/ContainerLayer";

    private static final int MSG_ENTER_CONTAINER_PAGE = 0;

    private ViewGroup mContainer;
    private Activity mActivity;
    private View mConShotIcon;
    private View mMotionIcon;
    private MediaData mMediaData;
    private Player mPlayer;

    private boolean mActionBarVisibility;
    private boolean mIsFilmMode;

    @Override
    public void onCreate(Activity activity, ViewGroup root) {
        MtkLog.i(TAG, "<onCreate>");
        mActivity = activity;
        LayoutInflater flater = LayoutInflater.from(activity);
        mContainer = (ViewGroup) flater.inflate(
                R.layout.m_container_bottom_controls, null, false);

        mConShotIcon = mContainer.getChildAt(0);
        mMotionIcon = mContainer.getChildAt(1);
        assert(mConShotIcon != null);
        assert(mMotionIcon != null);

        mContainer.setVisibility(View.INVISIBLE);

        mConShotIcon.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                MtkLog.i(TAG, "<onClick> click conshot detail icon");
                if (mMediaData == null) {
                    MtkLog.i(TAG, "<onClick> mMediaData is null, do nothing, return");
                    return;
                }
                PlatformHelper.enterContainerPage(mActivity, mMediaData, false, null);
            }

        });

        mMotionIcon.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                MtkLog.i(TAG, "<onClick> click motion track detail icon");
                if (mMediaData == null) {
                    MtkLog.i(TAG, "<onClick> mMediaData is null, do nothing, return");
                    return;
                }
                PlatformHelper.enterContainerPage(mActivity, mMediaData, false, null);
            }

        });
    }

    @Override
    public void onChange(Player player, int what, int arg, Object obj) {
    }

    @Override
    public void setData(MediaData data) {
        mMediaData = data;
    }

    @Override
    public void setPlayer(Player player) {
        mPlayer = player;
    }

    @Override
    public void onResume(boolean isFilmMode) {
        MtkLog.i(TAG, "<onResume>");
        if (mMediaData == null) {
            mContainer.setVisibility(View.INVISIBLE);
            MtkLog.i(TAG, "<onResume> mMediaData is null");
            return;
        }
        ActionBar actionBar = mActivity.getActionBar();
        mActionBarVisibility =  mActivity.getActionBar().isShowing();
        mIsFilmMode = isFilmMode;
        updateLeftBottomIconVisibility();
    }

    @Override
    public void onPause() {
        MtkLog.i(TAG, "<onPause>");
        mContainer.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onDestroy() {
        MtkLog.i(TAG, "<onDestroy>");
    }

    @Override
    public View getView() {
        return mContainer;
    }

    @Override
    public MGLView getMGLView() {
        return null;
    }

    @Override
    public boolean onDoubleTap(float x, float y) {
        return true;
    }

    @Override
    public boolean onScaleBegin(float focusX, float focusY) {
        return true;
    }

    @Override
    public boolean onScale(float focusX, float focusY, float scale) {
        return true;
    }

    @Override
    public boolean onActionBarVisibilityChange(boolean newVisibility) {
        mActionBarVisibility = newVisibility;
        updateLeftBottomIconVisibility();
        return false;
    }

    @Override
    public void onFilmModeChange(boolean isFilmMode) {
        mIsFilmMode = isFilmMode;
        updateLeftBottomIconVisibility();
    }

    private void updateLeftBottomIconVisibility() {
        if (mIsFilmMode || !mActionBarVisibility) {
            hideLeftBottomIcon();
        } else {
            showLeftBottomIcon();
        }
    }
    private void showLeftBottomIcon() {
        mContainer.setVisibility(View.VISIBLE);
        if (mMediaData.subType == MediaData.SubType.CONSHOT) {
            mConShotIcon.setVisibility(View.VISIBLE);
            mMotionIcon.setVisibility(View.INVISIBLE);
        } else if (mMediaData.subType == MediaData.SubType.MOTRACK) {
            mConShotIcon.setVisibility(View.INVISIBLE);
            mMotionIcon.setVisibility(View.VISIBLE);
        }
    }

    private void hideLeftBottomIcon() {
        mContainer.setVisibility(View.INVISIBLE);
        mConShotIcon.setVisibility(View.INVISIBLE);
        mMotionIcon.setVisibility(View.INVISIBLE);
    }
}
