
package com.mediatek.settings;

import android.content.Context;
import android.preference.Preference;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.settings.AirplaneModeEnabler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Add for bug fix ALPS01772247, to monitor all subscribers' power state, on or off
 * When deal with airplane mode state, can use this listener to know when all subscribers
 * power on / off.
 */
public class SubscriberPowerStateListener {

    private static final String TAG = "SubscriberPowerStateListener";
    private Context mContext;

    private PhoneStateListener mPhoneStateListener[];
    private TelephonyManager mTelephonyManager;
    private Map<Integer, Integer> mServiceState = new HashMap<Integer, Integer>();
    private onRadioPowerStateChangeListener mListener;
    private List<SubscriptionInfo> mSubInfoList = new ArrayList<SubscriptionInfo>();
    // / @}

    public SubscriberPowerStateListener(Context context) {
        mContext = context;
        mTelephonyManager = TelephonyManager.from(context);
    }
    
    public void setRadioPowerStateChangeListener(onRadioPowerStateChangeListener listener) {
        mListener = listener;
    }

    public interface onRadioPowerStateChangeListener {
        public void onAllPoweredOff();
        public void onAllPoweredOn();
    }
    /**
     * Start to register phone state listener for all active subscribers.
     */
    public void registerListener() {
        Log.d(TAG, "Start to register state listener");
        //To prevent memory leak, always unregister the listener first
        unRegisterListener();
        mSubInfoList = SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
        int size = 0;
        if (mSubInfoList != null) {
            size = mSubInfoList.size();
            // prepare listener based on active SubscriptionInfo
            mPhoneStateListener = new PhoneStateListener[size];
            mServiceState.clear();
            int index = 0;
            for (final SubscriptionInfo record : mSubInfoList) {
                mPhoneStateListener[index] = new PhoneStateListener(record.getSubscriptionId()) {
                    @Override
                    public void onServiceStateChanged(ServiceState serviceState) {
                        int state = serviceState.getState();
                        Log.d(TAG, "serviceState.getState() = " + state);
                        // Record the state relate to subId, when ever receive
                        // callback to check whether stop phone listener ,and enable
                        // switch
                        mServiceState.put(record.getSubscriptionId(), state);
                        stopPhoneRegisterListener();
                    }
                };
                mTelephonyManager.listen(mPhoneStateListener[index],
                        PhoneStateListener.LISTEN_SERVICE_STATE);
                index++;
            }
        }
        // When there is no active subsriber, call stopPhoneRegisterListener
        // immediate
        if (size == 0) {
            stopPhoneRegisterListener();
        }
    }

    /**
     * To update register if subscribers modified.
     */
    public void updateSubscribers() {
        if (mServiceState.size() != 0) {
            Log.d(TAG, "updateSubscribers since some cases cause sub modify, need to " +
                    "re-register listener for sub");
            List<SubscriptionInfo> data = SubscriptionManager.
                    from(mContext).getActiveSubscriptionInfoList();
            if (isSubscriberChanged(data)) {
                registerListener();
            }
        }
    }

    //TODO:: Temp way to find whether two subinfo list has difference, there may be better method
    private boolean isSubscriberChanged(List<SubscriptionInfo> data) {
        boolean isSubModified = false;
        if (mSubInfoList != null && data != null) {
            int oldSize = mSubInfoList.size();
            int newSize = data.size();
            Log.d(TAG, "oldSize = " + oldSize + " newSize = " + newSize);
            if (oldSize != newSize) {
                isSubModified = true;
            } else {
                for (int i = 0; i < mSubInfoList.size(); i++) {
                    if (mSubInfoList.get(i).getSubscriptionId() !=
                            data.get(i).getSubscriptionId()) {
                        isSubModified = true;
                    }
                }
            }
        } else {
            isSubModified = true;
        }
        Log.d(TAG, "isSubModified = " + isSubModified);
        return isSubModified;
    }
    /**
     * Unregister phone state listener to prevent memory leak.
     */
    public void unRegisterListener() {
        if (mPhoneStateListener != null) {
            Log.d(TAG, "unRegisterListener and clear Listener");
            for (int index = 0; index < mPhoneStateListener.length; index++) {
                mTelephonyManager.listen(mPhoneStateListener[index], PhoneStateListener.LISTEN_NONE);
            }
        }
        mServiceState.clear();
    }

    // To see whether a sub radio on, the state is in servie or out of service,
    // it means radio power on, else means no.
    private boolean isAllSimPowerOn() {
        boolean allPowerOn = true;
        int state = 0;
        for (Integer subId : mServiceState.keySet()) {
            state = mServiceState.get(subId);
            Log.d(TAG, "state = " + state + " subId = " + subId);
            if (state != ServiceState.STATE_IN_SERVICE && 
                state != ServiceState.STATE_OUT_OF_SERVICE &&
                !isSetRadioPowerOff(subId)) {
                allPowerOn = false;
            }
        }
        Log.d(TAG, "allPowerOn = " + allPowerOn);
        return allPowerOn;
    }

    private boolean isSetRadioPowerOff(int subId) {
        int slotId = SubscriptionManager.getSlotId(subId);
        int currentSimMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.MSIM_MODE_SETTING, -1);
        boolean isPowerOff = ((currentSimMode & (1 << slotId)) == 0) ? true : false;
        Log.d(TAG, "soltId: " + slotId + ", isPowerOff : " + isPowerOff);
        return isPowerOff;
    }

    // To see whether a sub radio power off, the state is power off, as long as
    // all
    // subscribers' radio power state is off, it means airplane mode turn on
    // completely
    private boolean isAllSimPowerOff() {
        boolean allPowerOff = true;
        int state = 0;
        for (Integer subId : mServiceState.keySet()) {
            state = mServiceState.get(subId);
            Log.d(TAG, "state = " + state + " subId = " + subId);
            if (state != ServiceState.STATE_POWER_OFF) {
                allPowerOff = false;
            }
        }
        Log.d(TAG, "allPowerOff = " + allPowerOff);
        return allPowerOff;
    }

    // when turn on/off airplane mode, need to check all subscribers power
    // off/on, then enable switch preference
    // and unregister phone state listener to avoid memory leak
    private void stopPhoneRegisterListener() {
        boolean airplaneModeEnabled = AirplaneModeEnabler.isAirplaneModeOn(mContext);
        Log.d(TAG, "stopPhoneRegisterListener airplaneModeEnabled = " + airplaneModeEnabled);
        if (airplaneModeEnabled) {
           if (isAllSimPowerOff()) {
               mListener.onAllPoweredOff();
               unRegisterListener();
           }
        } else {
            if (isAllSimPowerOn()) {
                mListener.onAllPoweredOn();
                unRegisterListener();
            }
        }
    }
}
