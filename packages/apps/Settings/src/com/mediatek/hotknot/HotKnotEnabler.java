package com.mediatek.settings.hotknot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.android.settings.R;
import com.mediatek.hotknot.HotKnotAdapter;
import com.android.settings.widget.SwitchBar;
import com.mediatek.xlog.Xlog;

public final class HotKnotEnabler implements SwitchBar.OnSwitchChangeListener {
    private static final String TAG = "HotKnotEnabler";
    private static final int STATE_ERROR = -1;
    private final Context mContext;
    private Switch mSwitch;
    private SwitchBar mSwitchBar;
    private boolean mValidListener;
    private final IntentFilter mIntentFilter;
    private boolean mUpdateStatusOnly = false;
    private HotKnotAdapter mAdapter;
    
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Broadcast receiver is always running on the UI thread here,
            // so we don't need consider thread synchronization.
            int state = intent.getIntExtra(HotKnotAdapter.EXTRA_ADAPTER_STATE, STATE_ERROR);
            Xlog.d(TAG, "HotKnot state changed to" + state);
            handleStateChanged(state);
        }
    };

    
    
    public HotKnotEnabler(Context context, SwitchBar switchBar) {
        mContext = context;
        mSwitchBar = switchBar;
        mSwitch = switchBar.getSwitch();
        
        mValidListener = false;
        mAdapter = HotKnotAdapter.getDefaultAdapter(mContext);
        if(mAdapter == null) {
            mSwitch.setEnabled(false);
        }
        setupSwitchBar();
        mIntentFilter = new IntentFilter(HotKnotAdapter.ACTION_ADAPTER_STATE_CHANGED);
    }

    public void setupSwitchBar() {
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.show();
    }

    public void teardownSwitchBar() {
        mSwitchBar.removeOnSwitchChangeListener(this);
        mSwitchBar.hide();
    }

    public void resume() {
        if (mAdapter == null) {
            mSwitch.setEnabled(false);
            return;
        }

        handleStateChanged(mAdapter.isEnabled() ? HotKnotAdapter.STATE_ENABLED : 
            HotKnotAdapter.STATE_DISABLED);

        mContext.registerReceiver(mReceiver, mIntentFilter);
        mValidListener = true;
    }

    public void pause() {
        if (mAdapter == null) {
            return;
        }

        mContext.unregisterReceiver(mReceiver);
        mValidListener = false;
    }


    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        Xlog.d(TAG, "onSwitchChanged to " + isChecked + ", mUpdateStatusOnly is " + mUpdateStatusOnly);
        if (mAdapter != null && !mUpdateStatusOnly) {
            if(isChecked) {
                mAdapter.enable();
            } else {
                mAdapter.disable();
            }
        }
        switchView.setEnabled(false);
    }

    void handleStateChanged(int state) {
        switch (state) {
            case HotKnotAdapter.STATE_ENABLED:
                mUpdateStatusOnly = true;
                Xlog.d(TAG, "Begin update status: set mUpdateStatusOnly to true");
                setSwitchChecked(true);
                mSwitch.setEnabled(true);               
                mUpdateStatusOnly = false;
                Xlog.d(TAG, "End update status: set mUpdateStatusOnly to false");
                break;
            case HotKnotAdapter.STATE_DISABLED:
                mUpdateStatusOnly = true;
                Xlog.d(TAG, "Begin update status: set mUpdateStatusOnly to true");
                setSwitchChecked(false);
                mSwitch.setEnabled(true);               
                mUpdateStatusOnly = false;
                Xlog.d(TAG, "End update status: set mUpdateStatusOnly to false");
                break;
            default:
                setSwitchChecked(false);
                mSwitch.setEnabled(true);
        }
    }

    private void setSwitchChecked(boolean isChecked) {
        if (isChecked != mSwitch.isChecked()) {
            mSwitch.setChecked(isChecked);

        }
    }
}
