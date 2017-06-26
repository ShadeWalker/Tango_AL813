package com.android.settings;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.provider.ChildMode;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import android.telephony.TelephonyManager;
import com.android.settings.DataUsageSummary;
import java.util.HashMap;
import java.util.Map;
import android.telephony.SubscriptionManager;//add by lihaizhou at 2015-08-05 
/*Modified By lihaizhou for child mode traffic limit begin*/
public class NetworkControlRecevier extends BroadcastReceiver
{
    public static final String ACTION_NETWORK_TIME_CONTROL_ALERT = "com.childmode.networktimelimit_alert";
    public static final String ACTION_NETWORK_TRAFFIC_CONTROL_ALERT = "com.childmode.networktrafficlimit_alert";
    public static final String ACTION_NETWORK_TIME_CONTROL_CANCEL = "com.childmode.networktimelimit_canel";
    public static final String ACTION_APP_LIMIT_ALERT = "com.childmode.applimit_alert";
    public static final String ACTION_APP_DISABLE_ALERT = "com.childmode.disable_alert";
    public static final String ACTION_NETWORK_FORBID_ALERT = "com.childmode.network_forbid_alert";
    public static final String ACTION_NETWORK_TIME_LIMIT_DISABLE_ALERT = "com.childmode.network_time_limit_disable_alert";
    public static final String ACTION_NETWORK_TRAFFIC_LIMIT_DISABLE_ALERT = "com.childmode.network_traffic_limit_disable_alert";
    private static String TAG = "byjlimit";
    static long lastToastTime = 0;
    public void onReceive(Context context, Intent intent){
        String action = intent.getAction();
        Log.d(TAG, "onReceive:action = " + action);
        if(ACTION_NETWORK_TIME_CONTROL_ALERT.equals(action)){
            //CLOSE data connection
            //ConnectivityManager localConnectivityManager = (ConnectivityManager)context.getSystemService("connectivity");
            WifiManager localWifiManager = (WifiManager)context.getSystemService("wifi");
            //localConnectivityManager.setMobileDataEnabled(false);
            localWifiManager.setWifiEnabled(false);
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
            int subId = subscriptionManager.getDefaultDataSubId();
            telephonyManager.setDataEnabled (false); 
            telephonyManager.setDataEnabled (subId, false);        
            //add by lihaizhou for disable MobileData for ChildMode by begin
            ChildMode.putString(context.getContentResolver(),ChildMode.INTERNET_LIMIT_TIME_UP ,"1");
            //add by lihaizhou for disable MobileData for ChildMode by end   
            //disable 
            ChildMode.putString(context.getContentResolver(),"internet_time_restriction_limit" ,"1");
            showAlert(context, R.string.network_time_limit_alert);
        }
        if (ACTION_NETWORK_TRAFFIC_CONTROL_ALERT.equals(action)) {

            //ConnectivityManager localConnectivityManager = (ConnectivityManager)context.getSystemService("connectivity");
            //localConnectivityManager.setMobileDataEnabled(false);
            TelephonyManager telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
            final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
            int subId = subscriptionManager.getDefaultDataSubId();
            telephonyManager.setDataEnabled (false); 
            telephonyManager.setDataEnabled (subId, false);        
            //add by lihaizhou for disable MobileData for ChildMode by begin
            ChildMode.putString(context.getContentResolver(),ChildMode.INTERNET_LIMIT_TRAFFIC_UP ,"1");
            //add by lihaizhou for disable MobileData for ChildMode by end 
            if ("1".equals(ChildMode.getString(context.getContentResolver(),"internet_traffic_restriction_limit" ))) {
                if (System.currentTimeMillis() > lastToastTime+2000L) {
                    lastToastTime = System.currentTimeMillis();
                    Toast t = Toast.makeText(context, R.string.network_traffic_limit_alert, 0);
                    ((TextView)t.getView().findViewById(com.android.internal.R.id.message)).setTextSize(12f);
                    t.show();
                    }
                }else{
                    ChildMode.putString(context.getContentResolver(),"internet_traffic_restriction_limit" ,"1");
                    showAlert(context, R.string.network_traffic_limit_alert);
                }
            }
        if(ACTION_APP_DISABLE_ALERT.equals(action)){
            Toast.makeText(context, context.getResources().getString(R.string.childmode_disable_app_alert),Toast.LENGTH_LONG).show();
        }
        if(ACTION_NETWORK_FORBID_ALERT.equals(action)){
            Toast.makeText(context, context.getResources().getString(R.string.childmode_network_forbid_alert),Toast.LENGTH_LONG).show();
        }
        if(ACTION_NETWORK_TIME_LIMIT_DISABLE_ALERT.equals(action)){
            Toast.makeText(context, context.getResources().getString(R.string.childmode_network_time_limit_disable_alert),Toast.LENGTH_LONG).show();
        }
        if(ACTION_NETWORK_TRAFFIC_LIMIT_DISABLE_ALERT.equals(action)){
            Toast.makeText(context, context.getResources().getString(R.string.childmode_network_traffic_limit_disable_alert),Toast.LENGTH_LONG).show();
        }
    }

    private void showAlert(final Context c, int msgId) {
        AlertDialog mAlertDialog = new AlertDialog.Builder(c)
                .setTitle(R.string.network_alert_tip)
                .setMessage(msgId)
                .setPositiveButton(R.string.network_alert_ok, null)
                .create();
        mAlertDialog.getWindow().setType(
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        if (msgId == R.string.network_traffic_limit_alert) {
            mAlertDialog.setOnDismissListener(new OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    NetTrafficUtils.settraffic(c,false);
                }
            });
        }
        mAlertDialog.show();
    }
    /*Modified By lihaizhou for child mode traffic limit end*/
}
