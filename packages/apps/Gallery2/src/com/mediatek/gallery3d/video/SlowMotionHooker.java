package com.mediatek.gallery3d.video;

import java.util.Arrays;
import java.util.Locale;

import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;
import com.mediatek.gallery3d.ext.IMovieItem;
import com.mediatek.galleryframework.util.MtkLog;

public class SlowMotionHooker extends MovieHooker {
    private static final String TAG = "Gallery2/SlowMotionHooker";

    private static final int MENU_SLOW_MOTION = 1;
    private static final String KEY_SPEED_16X = "16";
    private static int KEY_SLOW_MOTION_SPEED = 1800;

    private IMtkVideoController mVideoView;
    private MenuItem mMenuSlowMotion;
    private int mCurrentSpeed;
    private int mNextSpeed;

    private boolean mIs16XEnabled;
    private String mSupportSpeedRange;
    private int mCurrentSpeedIndex;
    private int[] mCurrentSpeedRange;
    private int[] mSpeedResources = SlowMotionItem.SPEED_ICON_RESOURCE;

    private SlowMotionItem mSlowMotionItem;

    @Override
    public void setParameter(String key, Object value) {
        // TODO Auto-generated method stub
        super.setParameter(key, value);
        MtkLog.v(TAG, "setParameter(" + key + ", " + value + ")");
        if (value instanceof IMtkVideoController) {
            mVideoView = (IMtkVideoController) value;
            mVideoView.setSlowMotionSpeed(mCurrentSpeed);
            //When get a new videoview, should update current speed range.
            updateCurrentSpeedRange();
        }
    }
    
    private void updateCurrentSpeedRange() {
        mSupportSpeedRange = getSupportSpeedRange();
        if (mSupportSpeedRange != null) {
            mIs16XEnabled = isSupport16X(mSupportSpeedRange);
            if (mIs16XEnabled) {
                mCurrentSpeedRange = SlowMotionItem.SPEED_RANGE_16X;
            } else {
                mCurrentSpeedRange = SlowMotionItem.SPEED_RANGE;
            }
            mCurrentSpeedIndex = SlowMotionItem.getCurrentSpeedIndex(
                    mCurrentSpeedRange, mCurrentSpeed);
        }
    }

    private void refreshSlowMotionSpeed(final int speed) {
        if (getMovieItem() != null) {
            mSlowMotionItem.updateItemUri(getMovieItem().getUri());
            mSlowMotionItem.setSpeed(speed);
            mSlowMotionItem.updateItemToDB();
        }
    }

    private boolean isSupport16X(String support) {
        if (MtkVideoFeature.isSlowMotion16xSupported() && support != null) {
            boolean sup = support.toLowerCase(Locale.ENGLISH).contains(KEY_SPEED_16X);
            MtkLog.v(TAG, "isSupport16X sup " + sup);
            return sup;
        } else {
            return false;
        }
    }

    private String getSupportSpeedRange() {
        String support = null;
        if (mVideoView != null) {
            support = mVideoView.getStringParameter(KEY_SLOW_MOTION_SPEED);
        }
        MtkLog.v(TAG, "getSupportSpeedRange " + support);
        return support;
    }

    private void getCurrentSpeedIndex() {
        mSupportSpeedRange = getSupportSpeedRange();
        if (mSupportSpeedRange != null) {
            mIs16XEnabled = isSupport16X(mSupportSpeedRange);
            if (mIs16XEnabled) {
                mCurrentSpeedRange = SlowMotionItem.SPEED_RANGE_16X;
            } else {
                mCurrentSpeedRange = SlowMotionItem.SPEED_RANGE;
            }
            mCurrentSpeedIndex = SlowMotionItem.getCurrentSpeedIndex(mCurrentSpeedRange,
                    mCurrentSpeed);
            updateSlowMotionIcon(mCurrentSpeedIndex);
        } else {
            //If can not get support speeds from native now, should use speed value from DB to 
            //update menu icon.
            int index = SlowMotionItem.getCurrentSpeedIndex(
                    SlowMotionItem.SPEED_RANGE_16X, mCurrentSpeed);
            if (index >= 0 && index < SlowMotionItem.SPEED_RANGE_16X.length) {
                int speedResource = SlowMotionItem.SPEED_ICON_RESOURCE[index];
                mMenuSlowMotion.setIcon(speedResource);
                if (mVideoView != null) {
                    mVideoView.setSlowMotionSpeed(mCurrentSpeed);
                }
            }
            mIs16XEnabled = false;
        }
    }

    private void updateSlowMotionIcon(int index) {
        if (index < 0 || index > mCurrentSpeedRange.length) {
            MtkLog.v(TAG, "updateSlowMotionIcon index is invalide index = " + index);
            return;
        }
        int speed = mCurrentSpeedRange[index];
        int speedResource = mSpeedResources[index];
        MtkLog.v(TAG, "updateSlowMotionIcon(" + index + ")" + "speed " + speed
                + " speedResource " + speedResource);
        if (mMenuSlowMotion != null) {
            if (mSlowMotionItem.isSlowMotionVideo()) {
                mMenuSlowMotion.setIcon(speedResource);
                refreshSlowMotionSpeed(speed);
                mVideoView.setSlowMotionSpeed(speed);
                mMenuSlowMotion.setVisible(true);
            } else {
                mMenuSlowMotion.setVisible(false);
            }
        }
        mCurrentSpeed = speed;
    }

    private void initialSlowMotionIcon(final int speed) {
        MtkLog.v(TAG, "initialSlowMotionIcon() speed " + speed);
        if (mMenuSlowMotion != null) {
            mCurrentSpeed = speed;
            if (mCurrentSpeed != 0) {
                getCurrentSpeedIndex();
            } else {
                mMenuSlowMotion.setVisible(false);
            }

        }
    }

    @Override
    public void onMovieItemChanged(final IMovieItem item) {
        MtkLog.v(TAG, "onMovieItemChanged() " + mMenuSlowMotion);
        if (mMenuSlowMotion != null) {
            if (mSlowMotionItem == null) {
                mSlowMotionItem = new SlowMotionItem(getContext(),
                        item.getUri());
            } else {
                mSlowMotionItem.updateItemUri(item.getUri());
            }
            initialSlowMotionIcon(mSlowMotionItem.getSpeed());
        }
    }


    @Override
    public void setVisibility(boolean visible) {
        if (mMenuSlowMotion != null && mSlowMotionItem != null
                && mSlowMotionItem.isSlowMotionVideo()
                && mSupportSpeedRange != null) {
            mMenuSlowMotion.setVisible(visible);
            MtkLog.v(TAG, "setVisibility() visible=" + visible);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        MtkLog.v(TAG, "onCreateOptionsMenu()");
        mMenuSlowMotion = menu.add(MENU_HOOKER_GROUP_ID, getMenuActivityId(MENU_SLOW_MOTION), 0,
                R.string.slow_motion_speed);
        mMenuSlowMotion.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        if (getMovieItem() != null) {
            mSlowMotionItem = new SlowMotionItem(getContext(), getMovieItem().getUri());
            if (MtkVideoFeature.isForceAllVideoAsSlowMotion()) {
                if (mSlowMotionItem.getSpeed() == 0) {
                    initialSlowMotionIcon(SlowMotionItem.SLOW_MOTION_QUARTER_SPEED);
                    
                } else {
                    initialSlowMotionIcon(mSlowMotionItem.getSpeed());
                }
            } else {
                initialSlowMotionIcon(mSlowMotionItem.getSpeed());
            }
        } else {
            mMenuSlowMotion.setVisible(false);
        }
        return true;
    }
    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MtkLog.v(TAG, "onPrepareOptionsMenu()");
        if(mSlowMotionItem != null && !mSlowMotionItem.isSlowMotionVideo()) {
            mMenuSlowMotion.setVisible(false);
        } else if(mSupportSpeedRange == null){
            getCurrentSpeedIndex();
        }
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (getMenuOriginalId(item.getItemId())) {
        case MENU_SLOW_MOTION:
            MtkLog.v(TAG, "onOptionsItemSelected()");
            if (mSupportSpeedRange == null) {
                getCurrentSpeedIndex();
            }
            mCurrentSpeedIndex++;
            int index = mCurrentSpeedIndex % mCurrentSpeedRange.length;
            updateSlowMotionIcon(index);
            return true;
        default:
            return false;
        }
    }
}
