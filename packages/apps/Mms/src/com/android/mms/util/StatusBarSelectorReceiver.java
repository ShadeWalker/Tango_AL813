package com.android.mms.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony.Mms;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.mms.MmsApp;
import android.app.Activity;

public class StatusBarSelectorReceiver extends BroadcastReceiver {
    private static final String TAG = "[StatusBarSelectorCreator]StatusBarSelectorReceiver";
    public static final String ACTION_MMS_ACCOUNT_CHANGED = "com.android.mms.ui.ACTION_MMS_ACCOUNT_CHANGED";
    private Activity mActivity;

    public StatusBarSelectorReceiver(Activity activity) {
        mActivity = activity;
    }
    @Override
    public void onReceive(Context context, Intent intent) {

        if (StatusBarSelectorCreator.ACTION_MMS_ACCOUNT_CHANGED.equals(intent.getAction())) {
            int currentSubId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            Log.d(TAG, "onReceive, currentSubId = " + currentSubId);
            SubscriptionManager.from(MmsApp.getApplication()).setDefaultSmsSubId(currentSubId);
            StatusBarSelectorCreator creator = StatusBarSelectorCreator.getInstance(mActivity);
            creator.updateStatusBarData();
            creator.hideNotification();
        }
    }

}
