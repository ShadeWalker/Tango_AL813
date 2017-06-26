package com.mediatek.incallui.wfc;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.telecom.TelecomManager;

import com.android.incallui.Call;
import com.android.incallui.CallList;
import com.android.incallui.Call.State;
import com.android.incallui.InCallPresenter;
import com.android.incallui.Log;


public class InCallUiWfcUtils {
    private static AlertDialog sGeneralDialog;
    private static Context sContext;
    private static final String KEY_IS_FIRST_WIFI_CALL = "key_first_wifi_call";
    private static final String LOG_TAG = "InCallUiWfcUtils";
    private static final Handler mHandler = new Handler();

    public static boolean isWfcEnabled(Context context) {
        boolean isWfcEnabled = (TelephonyManager.WifiCallingChoices.ALWAYS_USE ==
                Settings.System.getInt(context.getContentResolver(),
                Settings.System.WHEN_TO_MAKE_WIFI_CALLS, TelephonyManager.WifiCallingChoices.NEVER_USE));
        Log.i(LOG_TAG, "[WFC] isWfcEnabled " + isWfcEnabled);
        return isWfcEnabled;
    }

    public static void maybeShowWfcError(Context context, CharSequence label,
            CharSequence description) {
        Log.i(LOG_TAG, "[WFC]maybeShowWfcError");
        sContext = context;
        final Intent intent = new Intent(sContext, WfcDialogActivity.class);
        intent.putExtra(WfcDialogActivity.SHOW_WFC_CALL_ERROR_POPUP, true);
        intent.putExtra(WfcDialogActivity.WFC_ERROR_LABEL, label);
        intent.putExtra(WfcDialogActivity.WFC_ERROR_DECRIPTION, description);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sContext.startActivity(intent);
    }

    public static void maybeShowCongratsPopup(Context context) {
        sContext = context;
        SharedPreferences  pref = PreferenceManager.getDefaultSharedPreferences(context);
        int TIMER = 500;
        Log.i(LOG_TAG, "[WFC]maybeShowCongratsPopup InCallPresenter.isWifiCapable()"
                + InCallPresenter.getWifiCapability());
        Log.i(LOG_TAG, "[WFC]maybeShowCongratsPopup pref.getBoolean(KEY_IS_FIRST_WIFI_CALL"
                + pref.getBoolean(KEY_IS_FIRST_WIFI_CALL, true));
        if (pref.getBoolean(KEY_IS_FIRST_WIFI_CALL, true) && (InCallPresenter.getWifiCapability())) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    Log.i(LOG_TAG, "[WFC]CongratsPopup shown");
                    final Intent intent = new Intent(sContext, WfcDialogActivity.class);
                    intent.putExtra(WfcDialogActivity.SHOW_CONGRATS_POPUP, true);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    sContext.startActivity(intent);
                }
            };
            mHandler.postDelayed(runnable, TIMER);
        }
    }

    private static void onDialogDismissed() {
        if (sGeneralDialog != null) {
            sGeneralDialog.dismiss();
            sGeneralDialog = null;
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    public static class RoveOutReceiver extends BroadcastReceiver {
        private Context mContext;
        private AlertDialog mDialog = null;
        private int mCount = 0;
        private Message mMsg = null;
        private ConnectivityManager mConMgr;
        private static final int COUNT_TIMES = 3;
        private static final String LOG_TAG = "RoveOutReceiver";
        private static final int EVENT_RESET_TIMEOUT = 1;
        private static final int CALL_ROVE_OUT_TIMER = 1800000;

        private TelecomManager getTelecomManager() {
           return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
       }

       public RoveOutReceiver(Context context) {
           mContext = context;
       }

       public void register(Context context) {
           IntentFilter filter = new IntentFilter();
           filter.addAction(ConnectivityManager.ROVE_OUT_ALERT);
           context.registerReceiver(this, filter);
      }

       public void unregister(Context context) {
           context.unregisterReceiver(this);
           WfcDialogActivity.sCount = 0;
           if (mMsg != null) {
               mHandler.removeMessages(mMsg.what);
           }
       }

       @Override
       public void onReceive(Context context, Intent intent) {
           String action = intent.getAction();
           Log.d(LOG_TAG, "[WFC]action: " + action);
           if (action.equals(ConnectivityManager.ROVE_OUT_ALERT)) {
               if (InCallPresenter.checkWifiCapability(CallList.getInstance())
                       && (WfcDialogActivity.sCount < COUNT_TIMES)
                       && !WfcDialogActivity.sIsShowing) {
                   final Intent intent1 = new Intent(mContext, WfcDialogActivity.class);
                   intent1.putExtra(WfcDialogActivity.SHOW_WFC_ROVE_OUT_POPUP, true);
                   intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                   mContext.startActivity(intent1);
                   if (WfcDialogActivity.sCount == 0) {
                       mMsg = mHandler.obtainMessage(EVENT_RESET_TIMEOUT);
                       mHandler.removeMessages(mMsg.what);
                       mHandler.sendMessageDelayed(mMsg, CALL_ROVE_OUT_TIMER);
                       Log.i(LOG_TAG, "[WFC]in WfcSignalReceiver sendMessageDelayed ");
                   }
               }
           }
       }
       private Handler mHandler = new Handler() {
           @Override
           public void handleMessage(Message msg) {
               switch (msg.what) {
                   case EVENT_RESET_TIMEOUT:
                       Log.i(LOG_TAG, "[WFC] in WfcSignalReceiver EVENT_RESET_TIMEOUT ");
                       WfcDialogActivity.sCount = 0;
                       break;
                   default:
                       Log.i(LOG_TAG, "[WFC]Message not expected: ");
                       break;
               }
           }
       };
  }
}
