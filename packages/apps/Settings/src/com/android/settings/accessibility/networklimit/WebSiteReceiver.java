package com.android.settings.accessibility.networklimit;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.ChildMode;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.android.settings.R;

import java.util.List;

/**
 * Created by Administrator on 15-3-16.
 */
public class WebSiteReceiver extends BroadcastReceiver{
    private static final String TAG = "WebSiteReceiver";
    private static long lastToastTime = 0;
    
    private static final String ACTION_DISABLE_3RDBROWSER = "com.settings.childmode.disable.3rdBrowser";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        
        if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
            if (ChildMode.isChildModeOn(context.getContentResolver())
                    && ChildMode.isUrlWhteListOn(context.getContentResolver())) {
                
                Intent disableintent = new Intent(ACTION_DISABLE_3RDBROWSER);
                disableintent.putExtra("enable", true);
                context.sendBroadcast(disableintent);
                
                forceStop3rdBrowserApp(context);
            }
        } else if ("childmode.3rdBrowser.restr.done".equals(action)) {
            if (System.currentTimeMillis() > lastToastTime+2000L) {
                lastToastTime = System.currentTimeMillis();
                Toast t = Toast.makeText(context, R.string.website_limit_disable_app_tips, 0);
                ((TextView)t.getView().findViewById(com.android.internal.R.id.message)).setTextSize(12f);
                t.show();
            }
        }
    }

    private void forceStop3rdBrowserApp(Context context){
        
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setData(Uri.parse("http://"));

        List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.GET_INTENT_FILTERS);

        if ((null != list) && (list.size() > 0)) {
            for (ResolveInfo resolveInfo : list) {
                String packageStr = resolveInfo.activityInfo.packageName;
                String acitivtyStr = resolveInfo.activityInfo.name;
                logd("forceStop3rdBrowserApp: packag=" + packageStr + ", acitivty=" + acitivtyStr);
                if (!("com.android.browser".equals(packageStr) && ("com.android.browser.BrowserActivity").equals(acitivtyStr))
                     && !("com.taobao.taobao".equals(packageStr) && ("com.taobao.tao.BrowserActivity").equals(acitivtyStr))) {
                    //The browser is not google browser and no taobao app.
                    logd("forceStop3rdBrowserApp force stop " + packageStr);
                    ActivityManager am = (ActivityManager)context.getSystemService("activity");
                    am.forceStopPackage(packageStr);
                }
             }
         }
    }

    private void logd(String msg) {
        Log.d(TAG, msg);
    }
}
