
package com.android.phone;


/////////////////////////zhouguanghui for 4G  enable
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.android.internal.telephony.PhoneFactory;
import android.provider.Settings;
import com.android.internal.telephony.Phone;
import android.telephony.SubscriptionManager;
import android.telephony.RadioAccessFamily;
import com.mediatek.settings.cdma.TelephonyUtilsEx;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteModeController;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteRatController;
import com.mediatek.internal.telephony.ltedc.LteDcPhoneProxy;
import android.telephony.TelephonyManager;
import android.content.Context;

public class MobileNetworkSettingsEx extends Activity{
	
	private static final String LOG_TAG = "xionghaifeng";
	private boolean isChecked;
	private int slotId;
	private int subId;
	private Phone mPhone;
	private static final int LTE_MODEM_ON = 9;
	private static final int LTE_MODEM_OFF = 3;
	//private static final int GSM_MODEM_ON = 1;
	private int preferredNetworkMode = Phone.PREFERRED_NT_MODE;
	private SubscriptionManager subscriptionManager;

  	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		subscriptionManager = SubscriptionManager.from(this);
	}

	@Override
	protected void onStart() {
		Log.i(LOG_TAG,"onStart");
		super.onStart();
		Intent intent = getIntent();
		try{
			isChecked = intent.getBooleanExtra("isChecked", false);
		}catch(RuntimeException e){
			e.printStackTrace();
		}		
		Log.i(LOG_TAG,"get flag isChecked : " + isChecked);
	    	slotId = intent.getIntExtra("slotId", 0);
		//subId = intent.getIntExtra("subId", 0);
		subId = getDefaultValidSubId();
	    	Log.i(LOG_TAG,"get slotId : " + slotId + ",subId = " + subId);
	    	getPhone(slotId);
	}

  @Override
	protected void onResume() {
		Log.i(LOG_TAG,"onResume");
		super.onResume();
		if (mPhone != null)
		{
			if (TelephonyUtilsEx.isCDMAPhone(mPhone) || TelephonyUtilsEx.isCTRoaming(mPhone))
			{
				setCdma4GServiceAbility(isChecked,subId);	
			}
			else
			{
				setLteServiceAbility(isChecked,subId);	
			}
			
			Log.i(LOG_TAG,"set4GServiceAbility == isChecked ====" + isChecked);
		}
		this.finish();
	}

	private  int getDefaultValidSubId()
	{
		int subId = SubscriptionManager.getDefaultDataSubId();
		//TelephonyManager tm = TelephonyManager.getDefault();
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		if (!SubscriptionManager.isValidSubscriptionId(subId))
		{
			Log.d("xionghaifeng", " Invalid subId = " + subId);

			if (tm.hasIccCard(0))
			{
			       subId = getSubIdBySlot(0);
				subscriptionManager.setDefaultDataSubId(subId);
			}
			else if (tm.hasIccCard(1))
			{
			        subId = getSubIdBySlot(0);
				subscriptionManager.setDefaultDataSubId(subId);
			}
			else
			{
				return -1;
			}
		}

		return subId;
	}
	
	/*HQ_xionghaifeng 20150825 modify for set data start*/
    private  int getSubIdBySlot(int slotId) {
        if (slotId < 0 || slotId > 1) {
            return -1;
        }
        int[] subids = SubscriptionManager.getSubId(slotId);
        int subid = -1;
        if (subids != null && subids.length >= 1) {
            subid = subids[0];
        }
        Log.d("xionghaifeng", "getSubIdBySlot: sub id = " + subid 
                + "sim Slot = " + slotId);
        return subid;
	}
	/*HQ_xionghaifeng 20150825 modify for set data end*/

	private void setLteServiceAbility(boolean isChecked, int subId)
	{
		// set preferred Network Type (set  Lte  on)
		if (isChecked)
		{
			android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
				android.provider.Settings.Global.PREFERRED_NETWORK_MODE, LTE_MODEM_ON);
			//add by liruihong
			android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
			      android.provider.Settings.Global.PREFERRED_NETWORK_MODE + subId , LTE_MODEM_ON);
			mPhone.setPreferredNetworkType(LTE_MODEM_ON, null);
		}
		else
		{
			android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
				android.provider.Settings.Global.PREFERRED_NETWORK_MODE, LTE_MODEM_OFF);
			//add by liruihong
			android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
			      android.provider.Settings.Global.PREFERRED_NETWORK_MODE + subId , LTE_MODEM_OFF);
			mPhone.setPreferredNetworkType(LTE_MODEM_OFF, null);
		}	
		Log.d(LOG_TAG,"set GSM 4G isChecked = " + isChecked);
	}

	private void setCdma4GServiceAbility(boolean isChecked,int mSubId) {
        int ratMode = isChecked ? TelephonyManagerEx.SVLTE_RAT_MODE_4G :
                               TelephonyManagerEx.SVLTE_RAT_MODE_3G;
        Log.d(LOG_TAG,"set CDMA 4G isChecked = " + isChecked + " ratMode = " + ratMode);
	 Log.d(LOG_TAG,"mSubId = " + mSubId);
        Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                Settings.Global.LTE_ON_CDMA_RAT_MODE, ratMode);
	//add by liruihong
	Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                Settings.Global.LTE_ON_CDMA_RAT_MODE + mSubId, ratMode);
	
        int networkType = isChecked ?
                (SvlteRatController.RAT_MODE_SVLTE_2G
                 | SvlteRatController.RAT_MODE_SVLTE_3G
                 | SvlteRatController.RAT_MODE_SVLTE_4G)
                 : (SvlteRatController.RAT_MODE_SVLTE_2G
                    | SvlteRatController.RAT_MODE_SVLTE_3G);
        switchSvlte(networkType);
    }

	private void getPhone(int slotId){
		//int subId[] = SubscriptionManager.getSubId(slotId);
		//int phoneId = SubscriptionManager.getPhoneId(subId[0]);
		int phoneId = SubscriptionManager.getPhoneId(subId);
		mPhone = PhoneFactory.getPhone(phoneId);
		Log.i(LOG_TAG,"slotId : " + slotId + " phone :" + mPhone);
	}

	private boolean isCapabilityPhone(Phone phone) {
		boolean result = phone != null ? ((phone.getRadioAccessFamily()
		& (RadioAccessFamily.RAF_UMTS | RadioAccessFamily.RAF_LTE)) > 0) : false;
		Log.i(LOG_TAG,"isCapabilityPhone: " + result);
		return result;
	}

	private void switchSvlte(int networkType) {
		Log.d(LOG_TAG, "value = " + networkType);
		LteDcPhoneProxy lteDcPhoneProxy = (LteDcPhoneProxy) mPhone;
		lteDcPhoneProxy.getSvlteRatController().setRadioTechnology(networkType, null);
	}
}
