	package com.huawei.android.telephony;

	import android.provider.Settings;
	import android.telephony.SubscriptionInfo;
	import android.telephony.SubscriptionManager;
	import android.telephony.TelephonyManager;

	import android.content.Context;
	import android.util.Log;

	public class MSimTelephonyManagerCustEx 
	{
	    private static int masterSubId;
		private static int masterSlotId;

	    public static int getUserDefaultSubscription(Context context) 
	    {
	    	masterSubId = SubscriptionManager.getDefaultDataSubId();
	    	masterSlotId = SubscriptionManager.getSlotId(masterSubId);
			Log.i("xionghaifeng","masterSubId: " + masterSubId + "masterSimId: " + masterSlotId);

			if (SubscriptionManager.isValidSubscriptionId(masterSubId) 
				&& SubscriptionManager.isValidSlotId(masterSlotId))
			{
				return masterSlotId;
			}
			else
			{
				return 0;
			}
	    }

		public static int getNetworkmode(Context context, int slotId)
		{
			return TelephonyManagerCustEx.getNetworkType(slotId); 
		}
		
		public static String getPesn(int slotId)
		{
			return ""; 
		}
		
		public static void setNetworkmode(Context context, int slotId, int nwMode)
		{
		}
	}
