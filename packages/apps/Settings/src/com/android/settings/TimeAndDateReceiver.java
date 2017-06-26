
package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.app.AlarmManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import com.android.internal.telephony.TelephonyProperties;

/** Broadcast receiver to set the time and date in first boot
 */
public class TimeAndDateReceiver extends BroadcastReceiver {

    private static final String TAG = "TimeAndDateReceiver";
    private int mTipSwitch;
    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if ((Intent.ACTION_BOOT_COMPLETED).equals(action)) {
            Log.d(TAG, "onReceive: action is " + action);
            String simOperator = SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_IMSI);
            if (TextUtils.isEmpty(simOperator) && SystemProperties.get("ro.hq.tigo.default.timezone").equals("1")
                    && SystemProperties.get("persist.sys.hq.firstboot").equals("1")) {
                AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarm.setTimeZone("America/Costa_Rica");
                SystemProperties.set("persist.sys.hq.firstboot", "0");
                Log.d(TAG, "timezone set to America/Costa_Rica");
            }
            //HQ_hushunli 2015-11-30 add for HQ01454910 begin
            //zhouyoukun fix HQ01543874
            if (TextUtils.isEmpty(simOperator) && SystemProperties.get("ro.hq.telcel.default.timezone").equals("1")
                    && SystemProperties.get("persist.sys.hq.firstboot").equals("1")) {
                AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarm.setTimeZone("America/Mexico_City");
                SystemProperties.set("persist.sys.hq.firstboot", "0");
                Log.d(TAG, "timezone set to America/Costa_Rica");
            }
            if (TextUtils.isEmpty(simOperator) && SystemProperties.get("ro.hq.att.default.timezone").equals("1")
                    && SystemProperties.get("persist.sys.hq.firstboot").equals("1")) {
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.setTimeZone("America/Mexico_City");
                SystemProperties.set("persist.sys.hq.firstboot", "0");
                Log.d(TAG, "timezone set to America/Mexico_City");
            }
            try {
                mTipSwitch = Settings.System.getInt(context.getContentResolver(), Settings.System.DATA_COST_TIP_SWITCH);
            } catch (SettingNotFoundException snfe) {
                mTipSwitch = 0;
                Log.d(TAG, "not found DATA_COST_TIP_SWITCH");
            }
            //an quan hong xian not need log please remove it 
            //Log.d(TAG, "simOperator is " + simOperator + ", mTipSwitch is " + mTipSwitch);
            if (!TextUtils.isEmpty(simOperator) && SystemProperties.get("ro.hq.show.datacostdip").equals("1") && mTipSwitch == 1) {//HQ_hushunli 2015-11-30 add for HQ01454910
                Intent dialogintent = new Intent();
                dialogintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                dialogintent.setAction("com.android.settings.DataCostTipDialog");
                dialogintent.putExtra("mcc", simOperator.substring(0,3));
                context.startActivity(dialogintent);
            }
            //HQ_hushunli 2015-11-30 add for HQ01454910 end
        }
    }
}
