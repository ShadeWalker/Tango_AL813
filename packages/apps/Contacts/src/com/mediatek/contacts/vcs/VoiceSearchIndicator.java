package com.mediatek.contacts.vcs;

import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.util.PhoneCapabilityTester;
import com.mediatek.contacts.util.LogUtils;

public class VoiceSearchIndicator {
    private static final String TAG = VoiceSearchIndicator.class.getSimpleName();

    // vcs msg seciton
    private static final int MSG_UPDATE_INDICATOR_ICON = 100;
    // reactivate the icon every 600ms
    private static final long DELAY_TIME_INDICATOR = 600;

    private int mIconDisable = com.android.contacts.R.drawable.ic_voice_search_off;
    private int mIconLight = com.android.contacts.R.drawable.ic_voice_search_holo_light;
    private int mIconDark = com.android.contacts.R.drawable.ic_voice_search_holo_dark;

    private MenuItem mMenuItem = null;
    private ImageView mImageView = null;
    private boolean mIsIndicatorEnable = false;
    private int mIndicatorIcon = mIconDisable;
    private TextView mTextView = null;

    public VoiceSearchIndicator(MenuItem item) {
        mMenuItem = item;
    }

    public VoiceSearchIndicator(ImageView view) {
        mImageView = view;
    }

    /** M: Bug Fix for ALPS01694037 @{ */
    public VoiceSearchIndicator(ImageView view, TextView textView) {
        mTextView = textView;
        mImageView = view;
    }
    /** @} */

    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_INDICATOR_ICON:
                updateIndicator();
                break;
            default:
                LogUtils.i(TAG, "[handleMessage] [vcs] default message.");
                break;
            }
        }
    };

    public void updateIndicator(boolean enable) {
        mIsIndicatorEnable = enable;
        updateIndicator();
    }

    public boolean isIndicatorEnable() {
        return mIsIndicatorEnable;
    }


    private void updateIndicator() {
        if (!isIndicatorEnable()) {
            LogUtils.i(TAG, "[updateIndicator] [vcs] Disable Indicator..");
            mHandler.removeMessages(MSG_UPDATE_INDICATOR_ICON);
            setIndicatorIcon(mIconDisable);
            return;
        }

        if (getIndicatorIcon() == mIconDark) {
            setIndicatorIcon(mIconLight);
        } else {
            setIndicatorIcon(mIconDark);
        }

        Message msg = Message.obtain();
        msg.what = MSG_UPDATE_INDICATOR_ICON;
        mHandler.sendMessageDelayed(msg, DELAY_TIME_INDICATOR);
    }

    private void setIndicatorIcon(int iconRes) {
        mIndicatorIcon = iconRes;
        if (mImageView != null) {
            mImageView.setImageResource(mIndicatorIcon);
        } else {
            mMenuItem.setIcon(mIndicatorIcon);
        }
    }

    private int getIndicatorIcon() {
        return mIndicatorIcon;
    }

    public void setOffIcon(int res) {
        mIconDisable = res;
    }

    public void setDrakIcon(int res) {
        mIconDark = res;
    }

    public void setLightIcon(int res) {
        mIconLight = res;
    }

    /** M: Bug Fix for ALPS01706025 @{ */
    public void removeHandel() {
        if (null != mHandler) {
            mHandler.removeMessages(MSG_UPDATE_INDICATOR_ICON);
        } else {
            LogUtils.i(TAG, "the mHandler is null");
        }
    }
    /** @} */
}
