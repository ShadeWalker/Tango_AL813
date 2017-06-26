package com.android.server.net;

import android.app.AlarmManager;
import android.app.ActivityManagerNative;
import android.app.IProcessObserver;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.INetworkManagementService;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseIntArray;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import android.os.SystemProperties;
import com.android.internal.telephony.TelephonyIntents;
import java.lang.Thread;

import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ApplicationInfo;
import java.util.Arrays;

/**
 * State tracker for lockdown mode. Watches for normal {@link NetworkInfo} to be
 * connected and kicks off VPN connection, managing any required {@code netd}
 * firewall rules.
 */

public class NetworkHttpMonitor {
    private static final String TAG = "NetworkHttpMonitor";
    private static final boolean DBG = true;

    private static final String ACTION_POLL =
        "com.android.server.net.NetworkHttpMonitor.action.POLL";

    private static final String ACTION_ROUTING_UPDATE =
        "com.android.server.net.NetworkHttpMonitor.action.routing";

    private static final String HTTP_FIREWALL_UID = "net.http.browser.uid";

    private static final String DEFAULT_SERVER = "connectivitycheck.android.com";

    /** Number of maximum Http Redirection. */
    private static final int MAX_REDIRECT_CONNECTION = 3;
    private static final int MOBILE = 0;
    private static final int EXPIRE_TIME = 60 * 1000 * 20; //20; // 20 minutes 
    private static final int KEEP_ALIVE_INTERVAL = 2 * 60 * 1000;
    private static final int SOCKET_TIMEOUT_MS = 10000;

    private AlarmManager mAlarmManager;
    private PendingIntent mPendingPollIntent;
    private static Context mContext;
    private static PackageManager mPackageManager;
    private static INetworkManagementService mNetd;

    private static final int EVENT_ENABLE_FIREWALL = 1;
    private static final int EVENT_DISABLE_FIREWALL = 2;    
    private static final int EVENT_KEEP_ALIVE = 3;
    /** Current Http Redirection count */
    private static int mHttpRedirectCount = 0;

	private static String WEB_LOCATION = "minternet.telcel.com";
    //private static String WEB_LOCATION = "securelogin.arubanetworks.com/upload/custom";

    final Object mRulesLock = new Object();
    private Handler mHandler;
    private String mServer;
    
    private static ArrayList<Integer> mBrowserAppUids = new ArrayList<Integer>();
    private static ArrayList<String> mBrowserAppList = new ArrayList<String>();

    private static boolean mIsFirewallEnabled = false;

    public NetworkHttpMonitor(Context context, INetworkManagementService netd) {
        mContext = context;
        mNetd = netd;
        mPackageManager = mContext.getPackageManager();
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        mPendingPollIntent = PendingIntent.getBroadcast(mContext, 0,
                             new Intent(ACTION_POLL), 0);

        registerForAlarms();
        registerForRougingUpdate();
	    registerForRoutingUpdate();
        registerWifiEvent();

        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mHandler = new MyHandler(thread.getLooper());

        mServer = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.CAPTIVE_PORTAL_SERVER);
        if (mServer == null) mServer = DEFAULT_SERVER;

    }

    /** Handler to do the network accesses on */
    private class MyHandler extends Handler {

        public MyHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message msg) {
            Slog.w(TAG, "msg:" + msg.what);

            switch (msg.what) {
            case EVENT_ENABLE_FIREWALL:
                enableFirewallPolicy();
                break;
            case EVENT_DISABLE_FIREWALL:
                disableFirewall();
                break;            
            case EVENT_KEEP_ALIVE:
                sendKeepAlive();
                break;
            }
        }
    }

    private void sendKeepAlive() {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    HttpURLConnection urlConnection = null;
                    String checkUrl = "http://" + mServer + "/generate_204";
                    Slog.w(TAG, "Checking:" + checkUrl);
                    try {
                        URL url = new URL(checkUrl);
                        urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.setInstanceFollowRedirects(false);
                        urlConnection.setConnectTimeout(SOCKET_TIMEOUT_MS);
                        urlConnection.setReadTimeout(SOCKET_TIMEOUT_MS);
                        urlConnection.setUseCaches(false);

                        urlConnection.getInputStream();
                        int status = urlConnection.getResponseCode();
                        boolean isConnected = status == 204;
                        Slog.w(TAG, "Checking status:" + status);
                        if (isConnected) {
                            if (isFirewallEnabled()) {
                                resetFirewallStatus();
                            }
                        } else if(status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_SEE_OTHER) {
                            String loc = urlConnection.getHeaderField("Location");
                            Slog.w(TAG, "new loc:" + loc);
                            if (loc.contains(getWebLocation())) {
                                if (!isFirewallEnabled()) {
                                    mHandler.obtainMessage(EVENT_ENABLE_FIREWALL).sendToTarget();
                                    mIsFirewallEnabled = true;
                                } else {
                                    mHandler.sendMessageDelayed(mHandler.obtainMessage(
                                        EVENT_KEEP_ALIVE), KEEP_ALIVE_INTERVAL);
                                }
                            }
                        }
                    } catch (IOException e) {
                        Slog.w(TAG, "ioe:" + e);
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(
                            EVENT_KEEP_ALIVE), KEEP_ALIVE_INTERVAL);
                    } finally {
                        if (urlConnection != null) {
                            urlConnection.disconnect();
                        }
                    }
                }
            }
        };
        Thread thread = new Thread(runnable);        
        thread.start();
    }


    public void clearFirewallRule() {
        resetFirewallStatus();
    }

    private void resetFirewallStatus() {
        synchronized (mRulesLock) {
            if (mIsFirewallEnabled) {
                Slog.w(TAG, "resetFirewallStatus");
                mIsFirewallEnabled = false;
                mHttpRedirectCount = 0;
                SystemProperties.set(HTTP_FIREWALL_UID, "");
				mAlarmManager.cancel(mPendingPollIntent);
                mHandler.obtainMessage(EVENT_DISABLE_FIREWALL).sendToTarget();
            }
        }
    }
	
    private void registerForAlarms() {
        mContext.registerReceiver(
        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
				Slog.w(TAG, "onReceive: registerForAlarms");
                resetFirewallStatus();								
            }
        }, new IntentFilter(ACTION_POLL));
    }
	
    private void registerForRoutingUpdate() {
        mContext.registerReceiver(
        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Slog.d(TAG, "onReceive: registerForRoutingUpdate");
                mAlarmManager.cancel(mPendingPollIntent);
                resetFirewallStatus();
            }
        }, new IntentFilter(ACTION_ROUTING_UPDATE));
    }

    private void registerForRougingUpdate() {
        mContext.registerReceiver(
        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Slog.w(TAG, "onReceive: registerForRougingUpdate");
				Bundle bundle = intent.getExtras();

				if (bundle != null) {
					int event_type = bundle.getInt(TelephonyIntents.EXTRA_EVENT_TYPE);

					if(event_type == 1){
		                mAlarmManager.cancel(mPendingPollIntent);
		                resetFirewallStatus();				
					}				
				}
            }
        }, new IntentFilter(TelephonyIntents.ACTION_NETWORK_EVENT));
    }

    private void registerWifiEvent() {
        mContext.registerReceiver(
        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = intent.getExtras();
                Slog.w(TAG, "onReceive: CONNECTIVITY_ACTION");

                if (bundle != null) {
                    final NetworkInfo info = (NetworkInfo)
                                             bundle.get(ConnectivityManager.EXTRA_NETWORK_INFO);

                    if (info != null) {
                        if (info.getType() == ConnectivityManager.TYPE_WIFI && info.isConnected() ) {
                            mAlarmManager.cancel(mPendingPollIntent);
                            Slog.w(TAG, "onReceive: resetFirewallStatus");
                            resetFirewallStatus();
                        }
                    }
                }
            }
        }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    public boolean isFirewallEnabled() {
        if (DBG) Slog.w(TAG, "isFirewallEnabled:" + mIsFirewallEnabled);

        return mIsFirewallEnabled;
    }

    public String getWebLocation() {
        String web = WEB_LOCATION;

        String testWeb = SystemProperties.get("net.http.web.location", "");

        if (testWeb.length() != 0) {
            web = testWeb;
        }

        if (DBG) Slog.w(TAG, "getWebLocation:" + web);

        return web;
    }

    public void monitorHttpRedirect(String location, int appUid) {
    	Slog.w(TAG, "monitorHttpRedirect:" + mHttpRedirectCount + ":" + appUid + "\r\nloc:" + location);
        if (DBG) Slog.w(TAG, "monitorHttpRedirect:" + mHttpRedirectCount + ":" + appUid + "\r\nloc:" + location);

		if("1".equals(SystemProperties.get("ro.mtk_pre_sim_wo_bal_support", "0"))) {
					
					Slog.w(TAG, "test 1");	
	        if (!location.contains(getWebLocation())) {
	            return;
	        }

	        if (mIsFirewallEnabled) {
	            Slog.w(TAG, "Http Firewall is enabled");
	            return;
	        }
	        
	        if (DBG)
	        Slog.w(TAG, "Non-app id:" + appUid);
	        Slog.w(TAG, "Non-app id:" + appUid);

	        if (appUid < Process.FIRST_APPLICATION_UID) {
	            Slog.w(TAG, "Non-app id:" + appUid);
	            return;
	        }

	        ArrayList appList = getBrowserAppList();

	        if (isBrowsrAppByUid(appUid)) {
	            return;
	        }

       		//add by guojianhui for telcel prepaid without balance
                if(isNoPaidApp(appUid)) {
		    return;
		}

	        mHttpRedirectCount++;
	        Slog.w(TAG, "mHttpRedirectCount add");

	        if (DBG) Slog.w(TAG, "mHttpRedirectCount add");

	        if (mHttpRedirectCount >= MAX_REDIRECT_CONNECTION) {
	            if (DBG) Slog.w(TAG, "Enable firewall");
	            synchronized (mRulesLock) {
	                mHandler.obtainMessage(EVENT_ENABLE_FIREWALL).sendToTarget();	                
	            }
	        }
		}
		Slog.w(TAG, "test 2");
    }

    private void enableFirewallWithUid(int appUid, boolean isEnabled) {
        try {
            mNetd.setFirewallUidRule(appUid, isEnabled);
            Slog.w(TAG, "Test:" + appUid + ":" + isEnabled);
        } catch (Exception e) {
            Slog.w(TAG, "e:" + e + "\r\n" + appUid + ":" + isEnabled);
        }
    }

    private void enableFirewall() {
        try {
            mNetd.setFirewallEnabled(true);
            mNetd.setFirewallUidRule(0, true);  //Root
            mNetd.setFirewallUidRule(1000, true);  //System
            mNetd.setFirewallEgressDestRule("0.0.0.0/0", 53, true);   //DNS
            mNetd.setFirewallEgressProtoRule("icmp", true);
            mNetd.setFirewallInterfaceRule("lo", true);
            mNetd.setFirewallEgressDestRule("0.0.0.0/0", 30017, true); //MD logger
            mNetd.setFirewallEgressDestRule("0.0.0.0/0", 5037, true); //ADB
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void disableFirewall() {
        try {
            mNetd.setFirewallEnabled(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
		Slog.w(TAG, "disableFirewall");
        sendFirewallIntent(false);

        if (mHandler.hasMessages(EVENT_KEEP_ALIVE)) {
            mHandler.removeMessages(EVENT_KEEP_ALIVE);
        }
		Slog.w(TAG, "Keep alive after the disableFirewall");
		mHandler.sendMessageDelayed(mHandler.obtainMessage(
                        EVENT_KEEP_ALIVE), KEEP_ALIVE_INTERVAL);
    }

    private void enableFirewallPolicy() {
        StringBuffer sb = new StringBuffer();
		Slog.w(TAG, "enableFirewallPolicy");

        enableFirewall();

        for ( int i = 0; i < mBrowserAppUids.size(); i++) {
            if (i != 0) {
                sb.append("," + mBrowserAppUids.get(i));
				Slog.w(TAG, "mBrowserAppUids.get 1");
            } else {
                sb.append(mBrowserAppUids.get(i));
				Slog.w(TAG, "mBrowserAppUids.get 2");
            }
            enableFirewallWithUid(mBrowserAppUids.get(i), true);
        }

        sendFirewallIntent(true);
		Slog.w(TAG, "new property:" + sb.toString());
        SystemProperties.set(HTTP_FIREWALL_UID, sb.toString());
		
		Slog.w(TAG, "start 20 minutes timer");
		mIsFirewallEnabled = true;
		long now = SystemClock.elapsedRealtime();
		long next = now + EXPIRE_TIME;
		mAlarmManager.set(AlarmManager.ELAPSED_REALTIME, next, mPendingPollIntent);

        if (!mHandler.hasMessages(EVENT_KEEP_ALIVE)) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(
                        EVENT_KEEP_ALIVE), KEEP_ALIVE_INTERVAL);
        }
    }

    private boolean isBrowsrAppByUid(int appUid) {
        for ( int i = 0; i < mBrowserAppUids.size(); i++) {
			Slog.w(TAG, "isBrowsrAppByUid");
            if (appUid == mBrowserAppUids.get(i)) {
                return true;
            }
        }

        return false;
    }

    private void sendFirewallIntent(boolean isEnabled) {
		Slog.w(TAG, "sendFirewallIntent");
        final long ident = Binder.clearCallingIdentity();
        Intent intent = new Intent(
                            "com.android.server.net.NetworkHttpMonitor.action.firewall");
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("isEnabled", isEnabled);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private ArrayList getBrowserAppList() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setData(Uri.parse("http://www.google.com"));
        Slog.w(TAG, "getBrowserAppList");

        List<ResolveInfo> infos = mPackageManager.queryIntentActivities (intent,
                                  PackageManager.GET_RESOLVED_FILTER);

        Slog.w(TAG, "getBrowserAppList:" + infos.size());
        mBrowserAppList.clear();
        mBrowserAppUids.clear();

        for (ResolveInfo info : infos) {
            mBrowserAppList.add(info.activityInfo.packageName);
            mBrowserAppUids.add(new Integer(info.activityInfo.applicationInfo.uid));
        }

        return mBrowserAppList;
    }

    //add by guojianhui for telcel prepaid without balance
    private List<String> noPaidAPPList = Arrays.asList(new String[] {"com.whatsapp", "com.facebook.katana", "com.twitter.android"});    

    private boolean isNoPaidApp(int uid) {	 
			
			for (String pkgName : noPaidAPPList) {
				try {
					ApplicationInfo ai = mPackageManager.getApplicationInfo(pkgName, PackageManager.GET_ACTIVITIES);
					if(ai != null && ai.uid != -1 && uid != -1) {
						Slog.w(TAG, "isNoPaidApp white paper no paid appUid: " + ai.uid + ", uid: " + uid);
						if(ai.uid == uid) {
							return true;
						}
					}
					
				} catch (NameNotFoundException e) {
					e.printStackTrace();
					continue;
				}
			}
	
			return false;
		}
}
