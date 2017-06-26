package com.android.settings;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.os.AsyncTask;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.INetworkStatsService;
import android.net.INetworkStatsService.Stub;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.net.wifi.WifiManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.ChildMode;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;
import android.util.NtpTrustedTime;
import android.util.TrustedTime;
import android.net.INetworkPolicyManager;
//import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.widget.LockPatternView.Cell;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class NetTrafficUtils {
	private static String TAG = "byjlimit";

	public static void settraffic(Context context, boolean enable) {
		// get subscriptID
		NtpTrustedTime mTime = NtpTrustedTime.getInstance(context);
		String imsi1 = TelephonyManager.getDefault().getSubscriberId(0);
		String imsi2 = TelephonyManager.getDefault().getSubscriberId(1);
		Log.d(TAG, "imsi1=" + imsi1 +" imsi2="+imsi2);
		NetworkPolicy[] arrayOfNetworkPolicy;
		ArrayList<NetworkPolicy> arrayLocalworkPolicy = new ArrayList<NetworkPolicy>();

		NetworkTemplate template1 = new NetworkTemplate(
				NetworkTemplate.MATCH_MOBILE_ALL, imsi1, "childmode");
		NetworkTemplate template2 = new NetworkTemplate(
				NetworkTemplate.MATCH_MOBILE_ALL, imsi2, "childmode");

		arrayOfNetworkPolicy = ((NetworkPolicyManager) context
				.getSystemService("netpolicy")).getNetworkPolicies();
		Log.d(TAG, "policies =" + arrayOfNetworkPolicy.length);
		Log.d(TAG,"+++++SHOW CURRENT TABLE+++++");
        for (int i = 0; i < arrayOfNetworkPolicy.length; i++) {
			
            Log.d(TAG, "policy: index" + i + "   policy:"
					+ arrayOfNetworkPolicy[i].toString());
			arrayLocalworkPolicy.add(arrayOfNetworkPolicy[i]);
		}
        Log.d(TAG,"-----SHOW CURRENT TABLE-----");

		if (enable) {
			Time localTime = new Time();
			localTime.setToNow();
			INetworkStatsService localINetworkStatsService = null;

			localINetworkStatsService = INetworkStatsService.Stub
					.asInterface(ServiceManager.getService("netstats"));
			long currentbyte = 0;
			long currentbyte2 = 0;
			mTime.forceRefresh();
			long currTime1 =  System.currentTimeMillis();
			long currTime =  System.currentTimeMillis();
			
			try {
			currTime1 = System.currentTimeMillis();
			currTime = ((NetworkPolicyManager) context.getSystemService("netpolicy")).getCurrentTime();/*add by lihaizhou for HQ01402014 */
		        } catch (Exception e) {
                        Log.d(TAG,"get policy currenttime exception:",e);
		        }
			
			try {
				currentbyte = localINetworkStatsService.getNetworkTotalBytes(
						template1, 0L,
						currTime);
			} catch (RemoteException exception) {
				Log.d(TAG, " catch RemoteException", exception);
			}
            
      String strLimit =  ChildMode.getString(context.getContentResolver(),ChildMode.INTERNET_TRAFFIC_RESTRICTION);
			long limitbyte = (long) (1* 1024 * 1024);
			try{
           limitbyte = Long.parseLong(strLimit)*1024*1024;
      }catch(Exception e){
            Log.d(TAG, "format long",e);
      }
      Log.d(TAG, "currtime=" + currTime);      
      Log.d(TAG, "limitbyte=" + limitbyte);
			Log.d(TAG, "currentbyte =" + currentbyte+"  currentbyte2:"+currentbyte2);
            
			NetworkPolicy localNetworkPolicy1 = new NetworkPolicy(template1,
					1, localTime.timezone, limitbyte
							+ currentbyte, limitbyte + currentbyte, true);
			NetworkPolicy localNetworkPolicy2 = new NetworkPolicy(template2,
					1, localTime.timezone, limitbyte
							+ currentbyte2, limitbyte + currentbyte2, true);
			if(imsi1 != null){
			    Log.d(TAG,"ADD POLICY__sub1:"+localNetworkPolicy1);
          arrayLocalworkPolicy.add(localNetworkPolicy1);
          localNetworkPolicy1.starttime =currTime;//modified by lihaizhou
          Log.d("lhz","currTime1:"+currTime1);
          Log.d("lhz","localNetworkPolicy1.starttime:"+localNetworkPolicy1.starttime);
      }
      if(imsi2 != null){
      Log.d(TAG,"ADD POLICY__sub2:"+localNetworkPolicy2);
      arrayLocalworkPolicy.add(localNetworkPolicy2);
      }
			writeAsync(context, arrayLocalworkPolicy);
		} else {
			ArrayList<NetworkPolicy> arrayLocalworkPolicy_temp = new ArrayList<NetworkPolicy>();
			for (int i = 0; i < arrayLocalworkPolicy.size(); i++) {
				if (!(arrayLocalworkPolicy.get(i).template
						.equals(template1))  &&!(arrayLocalworkPolicy.get(i).template
						.equals(template2))) {
					arrayLocalworkPolicy_temp.add(arrayLocalworkPolicy.get(i));
				}else{
           Log.d(TAG,"remove  POLICY:"+arrayLocalworkPolicy.get(i));
        }
			}
            for (int i = 0; i < arrayLocalworkPolicy_temp.size(); i++) {
			Log.d(TAG, "policy_temp: index" + i + "   policy:"
					+ arrayLocalworkPolicy_temp.get(i).toString());
			}
			writeAsync(context, arrayLocalworkPolicy_temp);
		}
	}

	public static void writeAsync(final Context context,
			ArrayList<NetworkPolicy> paramArrayList) {
		// TODO: consider making more robust by passing through service
		final NetworkPolicy[] policies = (NetworkPolicy[]) paramArrayList
				.toArray(new NetworkPolicy[paramArrayList.size()]);

		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				write(context, policies);
				return null;
			}
		}.execute();
	}

	public static void write(Context context, NetworkPolicy[] policies) {
		try {
			((NetworkPolicyManager) context.getSystemService("netpolicy"))
					.setNetworkPolicies(policies);
		} catch (Exception e) {
            Log.d(TAG,"write policy exception:",e);
		}
	}
    
    public static void setTimeLimit(Context context, boolean enable){
    
        PendingIntent localPendingIntent;
        AlarmManager alarmmanager;
        alarmmanager = (AlarmManager)context.getSystemService("alarm"); 
        Intent localIntent = new Intent(NetworkControlRecevier.ACTION_NETWORK_TIME_CONTROL_ALERT);
        int requestcode =0;
        String strLimit =  ChildMode.getString(context.getContentResolver(),ChildMode.INTERNET_TIME_RESTRICTION);
			long limittime = (long) (1*60*1000);
			try{
                limittime = Long.parseLong(strLimit)*60*1000;
            }catch(Exception e){
                 Log.d(TAG, "format long",e);
            }
        Log.d(TAG, "limittime=" + limittime);   
        long alerttime = System.currentTimeMillis() + limittime;
        localIntent.putExtra(NetworkControlRecevier.ACTION_NETWORK_TIME_CONTROL_ALERT, alerttime);
        localPendingIntent = PendingIntent.getBroadcast(context, requestcode, localIntent,PendingIntent.FLAG_UPDATE_CURRENT);
        /*Modified By zhangjun fix internet time limit(1701) SW00115441 2015-3-3 begin*/
        if(enable){
            Log.d(TAG,"add alarmmanager");
            alarmmanager.set(AlarmManager.RTC_WAKEUP, alerttime, localPendingIntent);
            SharedPreferences.Editor editor =
                    PreferenceManager.getDefaultSharedPreferences(context).edit();
            editor.putLong("childmode_internet_alert_date", alerttime);
            editor.apply();
        }else{
            Log.d(TAG,"cancel alarmmanager");
            alarmmanager.cancel(localPendingIntent);
            SharedPreferences.Editor editor =
                    PreferenceManager.getDefaultSharedPreferences(context).edit();
            editor.putLong("childmode_internet_alert_date", 0);
            editor.apply();
        } 
    }
    /*Modified By zhangjun fix internet time limit(1701) SW00115441 2015-3-3 end*/
    public static void initnetwork(Context context){
			Log.d("byjlimit","initnetwork" );
			ChildMode.putString(context.getContentResolver(),"internet_traffic_restriction_limit" ,"0");
      ChildMode.putString(context.getContentResolver(),"internet_time_restriction_limit" ,"0");
      if(isInternetTimeSettings(context)){
		  	  Log.d("byj","setTimeLimit true" );
      		NetTrafficUtils.setTimeLimit(context,true);
        	ChildMode.putString(context.getContentResolver(),"internet_time_restriction_limit" ,"0");
      }
      if(isInternetTrafficLimits(context)){
       		Log.d("byj","new tracfficuit" );
       		NetTrafficUtils.settraffic(context,true);
       		ChildMode.putString(context.getContentResolver(),"internet_traffic_restriction_limit" ,"0");	
      }
		}
		
		public static void cancelnetwork(Context context){
			Log.d("byjlimit","cancelnetwork" );
			ChildMode.putString(context.getContentResolver(),"internet_traffic_restriction_limit" ,"0");
      ChildMode.putString(context.getContentResolver(),"internet_time_restriction_limit" ,"0");
      if(isInternetTimeSettings(context)){
		  	  Log.d("byj","setTimeLimit true" );
      		NetTrafficUtils.setTimeLimit(context,false);
        	ChildMode.putString(context.getContentResolver(),"internet_time_restriction_limit" ,"0");
      }
      if(isInternetTrafficLimits(context)){
       		Log.d("byj","new tracfficuit" );
       		NetTrafficUtils.settraffic(context,false);
       		ChildMode.putString(context.getContentResolver(),"internet_traffic_restriction_limit" ,"0");	
      }
		}
		
		public static boolean isInternetTimeSettings(Context context) {
		        String isOn = ChildMode.getString(context.getContentResolver(),
		       		ChildMode.INTERNET_TIME_RESTRICTION_ON );
		        if(isOn != null && "1".equals(isOn)){
		       	 return true;
		        }else {
		       	 return false;
				}	 
	  }
	  
	  public static boolean isInternetTrafficLimits(Context context) {
		       String isOn = ChildMode.getString(context.getContentResolver(),
		      		ChildMode.INTERNET_TRAFFIC_RESTRICTION_ON );
		       if(isOn != null && "1".equals(isOn)){
		      	 return true;
		       }else {
		      	 return false;
				}	 
		}
}
