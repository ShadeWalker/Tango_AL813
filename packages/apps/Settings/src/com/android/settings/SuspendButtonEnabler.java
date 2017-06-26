package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.SwitchPreference;

public class SuspendButtonEnabler {
    protected Context mContext;
    protected SwitchPreference mSwitchPreference;
    private IntentFilter mIntentFilter;
    
    private OnPreferenceChangeListener mPreferenceChangedListener = 
            new OnPreferenceChangeListener() {
        public boolean onPreferenceChange(Preference preference, Object obj) {
            SuspendButtonEnabler.this.performCheck(((Boolean) obj).booleanValue());
            return true;
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent intent) {
            if ("com.huawei.android.FloatTasks.state_change"
                    .equals(intent.getAction())) {
                int state = intent.getIntExtra("float_task_state", 0);
                SuspendButtonEnabler.this.handleStateChanged(state);
            }
        }
    };

    public SuspendButtonEnabler(Context ctx, SwitchPreference switchPreference) {
        mContext = ctx;
        mSwitchPreference = switchPreference;
        initIntentFilter();
    }

    private void initIntentFilter() {
        mIntentFilter = new IntentFilter(
                "com.huawei.android.FloatTasks.state_change");
    }

    private boolean isFloatTaskRunning() {
        try {
            ContentResolver cr = mContext.getContentResolver();
            Uri uri = Uri.parse("content://com.huawei.android.FloatTasksContentProvider");
            Bundle bundle = cr.call(uri, "get", null, null);

            if (bundle != null) {
                int state = bundle.getInt("float_task_state", 0);
                if (state != 0) {
                    if (state != 1)
                        return false;
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void setFloatTaskEnabled(boolean enabled) {
        try {
            ContentResolver cr = mContext.getContentResolver();
            Uri uri = Uri.parse("content://com.huawei.android.FloatTasksContentProvider");
            if (enabled)
                cr.call(uri, "set", "1", null);
            else
                cr.call(uri, "set", "0", null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setSwitchChecked(boolean checked) {
        if (mSwitchPreference == null)
            return;
        if (checked != mSwitchPreference.isChecked()){
            mSwitchPreference.setChecked(checked);
        }
    }

    void handleStateChanged(int state) {
        switch (state) {
        case 0:
            setSwitchChecked(false);
            break;
        case 1:
            setSwitchChecked(true);
            break;
        default:
        	break;
        }
    }

    public void pause() {
        mContext.unregisterReceiver(this.mReceiver);
    }

    protected void performCheck(boolean checked) {
        mSwitchPreference.setEnabled(false);
        setFloatTaskEnabled(checked);
        mSwitchPreference.setEnabled(true);
    }

    public void resume() {
        mContext.registerReceiver(this.mReceiver, this.mIntentFilter,
                "com.huawei.android.FloatTasks.readPermission", null);
        boolean isRunning = isFloatTaskRunning();
        updateSwitchStatus(isRunning);
    }

    protected void updateSwitchStatus(boolean checked) {
        if (mSwitchPreference == null)
            return;
        mSwitchPreference.setEnabled(true);
        mSwitchPreference.setChecked(checked);
        mSwitchPreference.setOnPreferenceChangeListener(mPreferenceChangedListener);
    }
}