package com.mediatek.settings.sim;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.settings.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhoneServiceStateHandler {

    private static final String TAG = "PhoneServiceStateHandler";
    private List<Integer> mSubLists;
    private Context mContext;
    private TelephonyManager mTelephonyManager;
    private Listener mListenerCallBack;
    private int mSubId;
    private Map<Integer, PhoneStateListener> mListeners =
            new ConcurrentHashMap<Integer, PhoneStateListener>();

    /**
     * listen all selectable subInfos.
     * @param context context
     */
    public PhoneServiceStateHandler(Context context) {
        this(context, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        initSelctableSubInfos();
    }

    /**
     * listen specific subId.
     * @param context context
     * @param subId subId
     */
    public PhoneServiceStateHandler(Context context, int subId) {
        mContext = context;
        mTelephonyManager = TelephonyManager.from(context);
        mSubId = subId;
    }

    private void initSelctableSubInfos() {
        final int numSlots = mTelephonyManager.getSimCount();
        mSubLists = new ArrayList<Integer>();
        for (int i = 0; i < numSlots; ++i) {
            final SubscriptionInfo sir = Utils.findRecordBySlotId(mContext, i);
            if (sir != null) {
                mSubLists.add(sir.getSubscriptionId());
            }
        }
    }

    public void addPhoneServiceStateListener(Listener listener) {
        mListenerCallBack = listener;
        if (SubscriptionManager.isValidSubscriptionId(mSubId)) {
            registerPhoneStateListener(mSubId);
        } else {
            registerPhoneStateListener();
        }
    }

    public void removePhoneServiceSateListener() {
        mListenerCallBack = null;
        if (SubscriptionManager.isValidSubscriptionId(mSubId)) {
            unregisterPhoneStateListener(mSubId);
        } else {
            unregisterPhoneStateListener();
        }
    }

    private void registerPhoneStateListener() {
        for (Integer subId : mSubLists) {
            registerPhoneStateListener(subId);
        }
     }

    private void registerPhoneStateListener(int subId) {
        Log.d(TAG, "Register PhoneStateListener, subId : " +  subId);
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            PhoneStateListener phoneStateListener = getPhoneStateListener(subId);
            mListeners.put(subId, phoneStateListener);
            mTelephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        } else {
            Log.d(TAG, "invalid subId: " + subId);
        }
     }

    private PhoneStateListener getPhoneStateListener(final int subId) {
         return new PhoneStateListener(subId) {
             @Override
             public void onServiceStateChanged(ServiceState state) {
                 Log.d(TAG, "PhoneStateListener:onServiceStateChanged: subId: " + subId
                         + ", state: " + state);
                 if (mListenerCallBack != null) {
                     mListenerCallBack.onServiceStateChanged(state, subId);
                }
             }
         };
     }

    private void unregisterPhoneStateListener() {
        for (int subId : mListeners.keySet()) {
            unregisterPhoneStateListener(subId);
        }
     }

    private void unregisterPhoneStateListener(int subId) {
         Log.d(TAG, "Register unregisterPhoneStateListener subId : " + subId);
         if (SubscriptionManager.isValidSubscriptionId(subId)) {
             mTelephonyManager.listen(mListeners.get(subId), PhoneStateListener.LISTEN_NONE);
             mListeners.remove(subId);
         } else {
             Log.d(TAG, "invalid subId: " + subId);
        }
    }

     public interface Listener {
         public void onServiceStateChanged(ServiceState state, int subId);
     }
}
