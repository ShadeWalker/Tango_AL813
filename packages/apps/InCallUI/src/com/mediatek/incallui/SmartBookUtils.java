package com.mediatek.incallui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;

import com.android.incallui.Log;
import com.android.incallui.TelecomAdapter;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import java.util.Set;

public class SmartBookUtils {
    private final static String ONE = "1";
    private static final String LOG_TAG = "SmartBookUtils";
    private BroadcastReceiver mSmartBookReceiver;
    private DisplayManager mDisplayManager;
    private Context mContext;
    private Set<SmartBookListener> mListeners = Sets.newHashSet();

    public interface SmartBookListener {
        void onSmartBookPlugged();
    }

    public static boolean isMtkSmartBookSupport() {
        boolean isSupport = ONE.equals(SystemProperties.get("ro.mtk_smartbook_support")) ? true : false;
        Log.d(LOG_TAG, "isMtkSmartBookSupport(): " + isSupport);
        return isSupport;
    }

    public void setupForSmartBook(Context context) {
        if (FeatureOptionWrapper.MTK_SMARTBOOK_SUPPORT) {
            mContext = context;
            mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            registerSmartBookReceiver(context);
        }
    }

    public void tearDownForSmartBook() {
        if (FeatureOptionWrapper.MTK_SMARTBOOK_SUPPORT) {
            if (mSmartBookReceiver != null && mContext != null) {
                mContext.unregisterReceiver(mSmartBookReceiver);
            }
            if (mContext != null) {
                mContext = null;
            }
        }
    }

    public void addListener(SmartBookListener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.add(listener);
    }

    public void removeListener(SmartBookListener listener) {
        Preconditions.checkNotNull(listener);
        mListeners.remove(listener);
    }

    public boolean isSmartBookPlugged() {
        boolean isSmartBookPlugged = false;
        if (FeatureOptionWrapper.MTK_SMARTBOOK_SUPPORT && mDisplayManager != null) {
            isSmartBookPlugged = mDisplayManager.isSmartBookPluggedIn();
        }
        Log.d(this, "isSmartBookPlugged: " + isSmartBookPlugged);
        return isSmartBookPlugged;
    }

    public void screenOffForSmartBook() {
        if (FeatureOptionWrapper.MTK_SMARTBOOK_SUPPORT && isSmartBookPlugged()) {
            Log.d(this, "screenOffForSmartBook()...");
            /// The powermanager's the signature is platform, change method to Telecom.
            TelecomAdapter.getInstance().updatePowerForSmartBook(false);
        }
    }

    public void lightOnScreenForSmartBook() {
        if (FeatureOptionWrapper.MTK_SMARTBOOK_SUPPORT && isSmartBookPlugged()) {
            Log.d(this, "lightOnScreenForSmartBook()...");
            /// The powermanager's the signature is platform, change method to Telecom.
            TelecomAdapter.getInstance().updatePowerForSmartBook(true);
        }
    }

    private void registerSmartBookReceiver(Context context) {
        if (FeatureOptionWrapper.MTK_SMARTBOOK_SUPPORT) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_SMARTBOOK_PLUG);
            mSmartBookReceiver = new SmartBookBroadcastReceiver();
            context.registerReceiver(mSmartBookReceiver, intentFilter);
        }
    }

    private class SmartBookBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (FeatureOptionWrapper.MTK_SMARTBOOK_SUPPORT && Intent.ACTION_SMARTBOOK_PLUG.equals(action)) {
                boolean isSmartBookPlugged = intent.getBooleanExtra(Intent.EXTRA_SMARTBOOK_PLUG_STATE, false);
                Log.d(this, "SmartBookBroadcastReceiver -----isSmartBookPlugged = " + isSmartBookPlugged);
                if (isSmartBookPlugged) {
                    for (SmartBookListener listenr : mListeners) {
                        listenr.onSmartBookPlugged();
                    }
                }
            }
        }
   }

    /**
     * Power on/off device when connecting to smart book
     */
    public static void updatePowerForSmartBook(Context context, boolean onOff) {
        boolean isSmartBookPlugged = false;
        DisplayManager mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (FeatureOptionWrapper.MTK_SMARTBOOK_SUPPORT && mDisplayManager != null) {
            isSmartBookPlugged = mDisplayManager.isSmartBookPluggedIn();
        }
        if (!isSmartBookPlugged) {
            return;
        }
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        Log.d(LOG_TAG, "SmartBook power onOff: " + onOff);
        if (onOff) {
            pm.wakeUpByReason(SystemClock.uptimeMillis(),
                    PowerManager.WAKE_UP_REASON_SMARTBOOK);
        } else {
            pm.goToSleep(SystemClock.uptimeMillis(),
                    PowerManager.GO_TO_SLEEP_REASON_SMARTBOOK, 0);
        }
    }
}
