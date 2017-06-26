package com.mediatek.settings.ext;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import com.mediatek.widget.AccountViewAdapter.AccountElements;
//import com.mediatek.telephony.SimInfoManager;
//import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DefaultSimManagementExt implements ISimManagementExt {
    private static final String TAG = "DefaultSimManagementExt";

    /**
     * update the preference screen of sim management
     * @param parent parent preference
     */
    public void updateSimManagementPref(PreferenceGroup parent) {
        Xlog.d(TAG, "updateSimManagementPref()");
        PreferenceScreen pref3GService = null;
        PreferenceScreen prefWapPush = null;
        PreferenceScreen prefStatus = null;
        if (pref3GService != null) {
            Xlog.d(TAG, "updateSimManagementPref()---remove pref3GService");
            parent.removePreference(pref3GService);
        }
        if (prefStatus != null) {
            Xlog.d(TAG, "updateSimManagementPref()---remove prefStatus");
            parent.removePreference(prefStatus);
        }
    }

    public void updateSimEditorPref(PreferenceFragment pref) {
        return;
    }
    public void dealWithDataConnChanged(Intent intent, boolean isResumed) {
        return;
    }

    @Override
    public void updateDefaultSIMSummary(Preference pref, int subId) {
    }

    public boolean isNeedsetAutoItem() {
        return false;
    }
    public void showChangeDataConnDialog(PreferenceFragment prefFragment, boolean isResumed) {
        Xlog.d(TAG, "showChangeDataConnDialog");

        return;
    }

    public void setToClosedSimSlot(int simSlot) {
        return;
    }

    public void customizeSimColorEditPreference(PreferenceFragment pref, String key) {
    }

    @Override
    public void customizeVoiceChoiceArray(List<AccountElements> voiceList, boolean voipAvailable) {

    }

    @Override
    public void customizeSmsChoiceArray(List<AccountElements> smsList) {

    }

    @Override
    public void customizeSmsChoiceValueArray(List<Long> smsValueList) {

    }
    
    @Override
    public void customizeSmsChoiceValue(List<Object> smsValueList) {

    }

    @Override
    public void updateDefaultSettingsItem(PreferenceGroup prefGroup) {
    }

    @Override
    public boolean enableSwitchForSimInfoPref() {
        return true;
    }

    @Override
    public void updateSimNumberMaxLength(EditTextPreference editTextPreference, int slotId) {
    }

    @Override
    public void hideSimEditorView(View view, Context context) {
    }

    @Override
    public Drawable getSmsAutoItemIcon(Context context) {
        return null;
    }

    @Override
    public int getDefaultSmsSubIdForAuto() {
        return 0;
    }

    @Override
    public void initAutoItemForSms(ArrayList<String> list,
            ArrayList<SubscriptionInfo> smsSubInfoList) {
    }

    @Override
    public void registerObserver() {
    }

    @Override
    public void unregisterObserver() {
    }

    @Override
    public boolean switchDefaultDataSub(Context context, int subId) {
        return false;
    }
    
    @Override
    public void customizeListArray(List<String> strings){

    }
    
    @Override
    public void customizeSubscriptionInfoArray(List<SubscriptionInfo> subscriptionInfo){
    	
    }

	@Override
	public int customizeValue(int value) {
		return value;
	}
    
	@Override
	public SubscriptionInfo setDefaultSubId(Context context, SubscriptionInfo sir, int type) {
	    return sir;
	}
    
    @Override
    public PhoneAccountHandle setDefaultCallValue(PhoneAccountHandle phoneAccount){
        return phoneAccount;
    }

    /**
     * finds a record with slotId.
     * Since the number of SIMs are few, an array is fine.
     */
     private SubscriptionInfo findRecordBySlotId(Context context, final int slotId) {
        final List<SubscriptionInfo> subInfoList =
                SubscriptionManager.from(context).getActiveSubscriptionInfoList();
        if (subInfoList != null) {
            final int subInfoLength = subInfoList.size();

            for (int i = 0; i < subInfoLength; ++i) {
                final SubscriptionInfo sir = subInfoList.get(i);
                if (sir.getSimSlotIndex() == slotId) {
                    //Right now we take the first subscription on a SIM.
                    return sir;
                }
            }
        }

        return null;
    }

    private PhoneAccountHandle subscriptionIdToPhoneAccountHandle(Context context, final int subId) {
        final TelecomManager telecomManager = TelecomManager.from(context);
        final Iterator<PhoneAccountHandle> phoneAccounts =
                telecomManager.getCallCapablePhoneAccounts().listIterator();

        while (phoneAccounts.hasNext()) {
            final PhoneAccountHandle phoneAccountHandle = phoneAccounts.next();
            final PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
            final String phoneAccountId = phoneAccountHandle.getId();

            if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                    && TextUtils.isDigitsOnly(phoneAccountId)
                    && Integer.parseInt(phoneAccountId) == subId){
                return phoneAccountHandle;
            }
        }

        return null;
    }

    private void setUserSelectedOutgoingPhoneAccount(Context context, PhoneAccountHandle phoneAccount) {
        final TelecomManager telecomManager = TelecomManager.from(context);
        telecomManager.setUserSelectedOutgoingPhoneAccount(phoneAccount);
    }

    private static void setDefaultSmsSubId(final Context context, final int subId) {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        subscriptionManager.setDefaultSmsSubId(subId);
    }

    @Override
    public void setToClosedSimSlotSwitch(int simSlot, Context context) {
        Xlog.d(TAG, "setToClosedSimSlot = " + simSlot);

	    SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
		TelephonyManager mTelephonyManager;
		int subId = 0;
		int subId_close;
		int subId_t;
		int simCount;
		Boolean result;

        subId_close = subscriptionManager.getSubIdUsingPhoneId(simSlot);
        //subId_close = SubscriptionManager.getSubIdUsingSlotId(simSlot);
        mTelephonyManager = TelephonyManager.from(context);
		boolean enable_before = mTelephonyManager.getDataEnabled();
		subId = subscriptionManager.getDefaultDataSubId();
		Log.d(TAG, "setToClosedSimSlot: subId = " + subId + "subId_close=" + subId_close);

		simCount = mTelephonyManager.getSimCount();
		Log.d(TAG, "setToClosedSimSlot: simCount = " + simCount);
        for (int i = 0; i < simCount; i++) {
            final SubscriptionInfo sir = findRecordBySlotId(context, i);
            if (sir != null) {
                subId_t = sir.getSubscriptionId();
                Log.d(TAG, "setToClosedSimSlot: sir subId_t = " + subId_t);
                if (subId_t != subId_close) {
                     setDefaultSmsSubId(context, subId_t);
                     PhoneAccountHandle phoneAccountHandle =
                             subscriptionIdToPhoneAccountHandle(context, subId_t);
                     setUserSelectedOutgoingPhoneAccount(context, phoneAccountHandle);
                     break;
                }

            }
        }

	    if (subId_close != subId) {
		    return;
	    }

        for (int i = 0; i < simCount; i++) {
            final SubscriptionInfo sir = findRecordBySlotId(context, i);
            if (sir != null) {
                subId_t = sir.getSubscriptionId();
                Log.d(TAG, "setToClosedSimSlot: sir subId_t = " + subId_t);
                if (subId_t != subId) {
                     subscriptionManager.setDefaultDataSubId(subId_t);
                     if (enable_before) {
                         mTelephonyManager.setDataEnabled(subId_t, true);
                         //yanqing add for HQ01473896
                         mTelephonyManager.setDataEnabled(subId_close, false);
                     } else {
                         mTelephonyManager.setDataEnabled(subId_t, false);
                     }
                }

            }
        }

    }
    
	@Override
    public void setDataState(int subid) {
		return;
	}

    @Override
    public void setDataStateEnable(int subid){
        return;
    }
}
