package com.mediatek.contacts.simcontact;

import com.mediatek.contacts.simservice.SIMProcessorService;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.util.LogUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.*;
/**
 * 监测开机
 * @author tang
 *
 */
public class MyBootCmpReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		 if (!(SystemProperties.get("ro.hq.sdn").equals("1")||SystemProperties.get("ro.hq.por.sdn").equals("1"))) {
			 return;
		 }
		if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
	        LogUtils.d("tang", intent.getAction());
			return;
		}
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
		LogUtils.d("tang", "ACTION_BOOT_COMPLETED--------");
    	startSimService(context, -1, SIMServiceUtils.SERVICE_WORK_IMPORT_SDN_CONTACTS);
        LogUtils.d("tang", "ACTION_BOOT_COMPLETED isPhbReady==========");

	}
    private void startSimService(Context context, int subId, int workType) {
        Intent intent = null;
        intent = new Intent(context, SIMProcessorService.class);
        intent.putExtra(SIMServiceUtils.SERVICE_SUBSCRIPTION_KEY, subId);
        intent.putExtra(SIMServiceUtils.SERVICE_WORK_TYPE, workType);
        LogUtils.d("tang", "[startSimService]subId:" + subId + "|workType:" + workType);
        context.startService(intent);
    }

}
