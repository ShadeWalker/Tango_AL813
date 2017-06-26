package com.mediatek.settings.sim;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;

import com.android.internal.telephony.TelephonyIntents;

import java.util.Arrays;

public class SimHotSwapHandler {

    private static final String TAG = "SimHotSwapHandler";
    private SubscriptionManager mSubscriptionManager;
    private Activity mActivity;
    private int[] mSubscriptionIdListCache;
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mSubReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleHotSwap();
        }
    };
    public static SimHotSwapHandler newInstance(Activity activity) {
        return new SimHotSwapHandler(activity);
    }

    private SimHotSwapHandler(Activity activity) {
        mActivity = activity;
        mSubscriptionManager = SubscriptionManager.from(activity);
        mSubscriptionIdListCache = mSubscriptionManager.getActiveSubscriptionIdList();
        mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        print("Cache list: ", mSubscriptionIdListCache);
    }

    public void registerOnSubscriptionsChangedListener() {
        Log.d(TAG, "register...");
        if (mActivity != null) {
            mActivity.registerReceiver(mSubReceiver, mIntentFilter);
        }
        //mSubscriptionManager.addOnSubscriptionsChangedListener(mSubscriptionListener);
    }

    public void unregisterOnSubscriptionsChangedListener() {
        Log.d(TAG, "removeOnSubscriptionsChangedListener");
        if (mActivity != null) {
            mActivity.unregisterReceiver(mSubReceiver);
        }
        //mSubscriptionManager.removeOnSubscriptionsChangedListener(mSubscriptionListener);
    }

    private void handleHotSwap() {
        if (mActivity == null) {
            Log.d(TAG, "activiyt is null");
            return;
        }
        int[] subscriptionIdListCurrent = mSubscriptionManager.getActiveSubscriptionIdList();
        print("current subId list: ", subscriptionIdListCurrent);
        boolean isEqual = true;
        isEqual = Arrays.equals(mSubscriptionIdListCache, subscriptionIdListCurrent);
        Log.d(TAG, "isEqual: " + isEqual);
        if (!isEqual) {
            mActivity.finish();
        }
    }

    private final OnSubscriptionsChangedListener mSubscriptionListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            Log.d(TAG, "onSubscriptionsChanged()");
            handleHotSwap();
        }
    };

    private void print(String msg, int[] lists) {
        if (lists != null) {
            for (int i : lists) {
                Log.d(TAG, msg + i);
            }
        } else {
            Log.d(TAG, msg + "is null");
        }
    }
}
