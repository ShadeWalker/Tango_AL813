package com.mediatek.settings.sim;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

public class MsimRadioValueObserver {

    private static final String TAG = "MsimRadioValueObserver";
    private Context mContext;
    private ContentResolver mContentObserver;
    private Listener mListener;
    private ContentObserver mMsimModeValue = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            int mSimMode = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.MSIM_MODE_SETTING, -1);
            Log.d(TAG, "mMsimModeValue... mSimMode: " + mSimMode);
            if (mListener != null) {
                mListener.onChange(mSimMode, selfChange);
            } else {
                Log.d(TAG, "mListener has been ungistered");
            }
        };
    };

    public MsimRadioValueObserver(Context context) {
        mContext = context;
        mContentObserver = mContext.getContentResolver();
    }

    public void registerMsimObserver(Listener listener) {
        mListener = listener;
        registerContentObserver();
    }

    public void ungisterMsimObserver() {
        mListener = null;
        unregisterContentObserver();
    }

    private void registerContentObserver() {
        if (mContentObserver != null) {
            mContentObserver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.MSIM_MODE_SETTING),
                    false, mMsimModeValue);
        } else {
            Log.d(TAG, "observer is null");
        }
    }

    private void unregisterContentObserver() {
        if (mContentObserver != null) {
            mContentObserver.unregisterContentObserver(mMsimModeValue);
        } else {
            Log.d(TAG, "observer is null");
        }
    }

    public interface Listener {
        public void onChange(int msimModevalue, boolean selfChange);
    }
}
