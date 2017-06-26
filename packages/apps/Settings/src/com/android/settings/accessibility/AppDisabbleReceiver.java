package com.android.settings.accessibility;
import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.NetworkControlRecevier;
import com.android.settings.R;

import android.preference.PreferenceManager;
import android.provider.ChildMode;

public class AppDisabbleReceiver extends BroadcastReceiver{
    static long lastToastTime = 0;
    @Override
    public void onReceive(final Context context, Intent intent) {
    // TODO Auto-generated method stub
    String action = intent.getAction();
    Log.d("AppDisabbleReceiver", "action:"+action);
    /*Modified By zhangjun fix internet time limit(1701) SW00115441 2015-3-4 begin*/
    if("com.settings.childmode.appdisable".equals(action)){
            if (System.currentTimeMillis() > lastToastTime+2000L) {
                lastToastTime = System.currentTimeMillis();
                Toast t = Toast.makeText(context, R.string.app_disabled, 0);
                ((TextView)t.getView().findViewById(com.android.internal.R.id.message)).setTextSize(12f);
                t.show();
             }
         // }
      }
    /*Modified By zhangjun fix internet time limit(1701) SW00115441 2015-3-3 begin*/
    if (("android.intent.action.BOOT_COMPLETED".equals(action))
     || ("android.intent.action.ACTION_BOOT_IPO".equals(action))) {
        if (ChildMode.isChildModeOn(context.getContentResolver())
                && ChildMode.ON.equals(ChildMode.getString(context.getContentResolver(),
                        ChildMode.INTERNET_TIME_RESTRICTION_ON))) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            String strLimit =  ChildMode.getString(context.getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION);
            long timelimit = Long.parseLong(strLimit)*60*1000;
            long alertdate = sp.getLong("childmode_internet_alert_date", 0);
            Log.d("AppDisabbleReceiver", "now:"+System.currentTimeMillis());
            Log.d("AppDisabbleReceiver", "alert date:"+alertdate);
            Log.d("AppDisabbleReceiver", "timelimit:"+timelimit);
            if (alertdate > System.currentTimeMillis() && alertdate <= System.currentTimeMillis()+timelimit) {
                Intent i = new Intent(NetworkControlRecevier.ACTION_NETWORK_TIME_CONTROL_ALERT);
                PendingIntent pi = PendingIntent.getBroadcast(context, 0, i,PendingIntent.FLAG_UPDATE_CURRENT);
                AlarmManager alarmmanager = (AlarmManager)context.getSystemService("alarm");
                alarmmanager.set(AlarmManager.RTC_WAKEUP, alertdate, pi);
            }
        }
        Intent i = new Intent("com.settings.childmode.appdisable.switch");
        if (ChildMode.isChildModeOn(context.getContentResolver()) 
             && ChildMode.isAppBlackListOn(context.getContentResolver())) {
            i.putExtra("enable", true);
        }else {
            i.putExtra("enable", false);
        }
        context.sendBroadcast(i);
        new Thread(new Runnable() {
                public void run() {
                    ArrayList<String> applList = new ArrayList<String>();
                    Cursor c = context.getContentResolver().query(
                            ChildMode.APP_CONTENT_URI, null, null, null, null);
                    if (c != null){
                        try {
                            int k = c.getColumnIndex("package_name");
                            while (c.moveToNext())
                                applList.add(c.getString(k));
                        } finally {
                            c.close();
                        }
                    Intent i = new Intent(
                            "com.settings.childmode.appdisable.add");
                    i.putStringArrayListExtra("package_list", applList);
                    context.sendBroadcast(i);
                    }
                }
        }).start();
    }  
    /*Modified By zhangjun fix internet time limit(1701) SW00115441 2015-3-3 end*/
    }
}
